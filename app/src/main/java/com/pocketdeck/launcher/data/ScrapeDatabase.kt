package com.pocketdeck.launcher.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.pocketdeck.launcher.data.model.GameMetadata
import org.json.JSONArray
import org.json.JSONObject

/**
 * 统一的 WAL SQLite 刮削缓存库：
 * - `cover_cache`：封面 URL 与命中来源缓存（支持 `extra_json` 扩展列）
 * - `scrape_cache`：第三方 API 刮削结果缓存（核心字段独立列 + `extra_json` 扩展列，含 `matchedBySerial` 等）
 * - `rom_identity`：ROM 文件 CRC32 / SHA1 / Serial 指纹缓存（带 `updated_at_ms` 审计/清理时间戳）
 *
 * 升级与容错设计：
 * - 库名不带版本号（`scrape_cache.db`），通过 [DB_VERSION] + 渐进式 [onUpgrade] 演进；
 * - 覆写 [onDowngrade]，降级安装时作为纯缓存库安全清表重建，不崩溃；
 * - 路径迁移/删除 API 与 `UserStore` 同步，避免 ROM 移动/删库残留孤儿行；
 * - 异常写入 `Log.w` 留审计线索，不静默吞错。
 */
open class ScrapeDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION,
) {
    data class CoverRow(
        val gameId: String,
        val url: String?,
        val provider: String?,
        val matchMode: String?,
        val failedUntilMs: Long,
        val updatedAtMs: Long,
    )

    data class ScrapeCacheRow(
        val hit: ScrapeHit,
        val status: Int,
        val expiresAtMs: Long,
    )

    data class RomIdentityRow(
        val crc32: String? = null,
        val sha1: String? = null,
        val serial: String? = null,
    ) {
        val isEmpty: Boolean get() = crc32 == null && sha1 == null && serial == null
    }

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        runCatching { db.enableWriteAheadLogging() }
        runCatching { db.rawQuery("PRAGMA synchronous=NORMAL", null).close() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cover_cache (
                game_id TEXT PRIMARY KEY NOT NULL,
                url TEXT,
                provider TEXT,
                match_mode TEXT,
                failed_until_ms INTEGER NOT NULL DEFAULT 0,
                updated_at_ms INTEGER NOT NULL DEFAULT 0,
                extra_json TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scrape_cache (
                provider_id TEXT NOT NULL,
                game_id TEXT NOT NULL,
                official_name TEXT,
                release_date TEXT,
                genre TEXT,
                developer TEXT,
                publisher TEXT,
                cover_urls TEXT,
                extra_json TEXT,
                status INTEGER NOT NULL,
                expires_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (provider_id, game_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_scrape_cache_game ON scrape_cache(game_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_scrape_cache_expires ON scrape_cache(expires_at_ms)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rom_identity (
                path TEXT PRIMARY KEY NOT NULL,
                size INTEGER NOT NULL,
                mtime INTEGER NOT NULL,
                crc32 TEXT,
                sha1 TEXT,
                serial TEXT,
                updated_at_ms INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 后续版本在此按序追加渐进式 ALTER TABLE 迁移：
        // if (oldVersion < 2) { ... }
        Log.i(TAG, "Upgrading scrape_cache.db from v$oldVersion to v$newVersion")
        runCatching {
            onCreate(db)
        }.onFailure { err ->
            Log.w(TAG, "Upgrade failed (v$oldVersion -> v$newVersion), rebuilding cache tables", err)
            rebuildAllTables(db)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Downgrading scrape_cache.db from v$oldVersion to v$newVersion, rebuilding cache tables")
        rebuildAllTables(db)
    }

    private fun rebuildAllTables(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS cover_cache")
        db.execSQL("DROP TABLE IF EXISTS scrape_cache")
        db.execSQL("DROP TABLE IF EXISTS rom_identity")
        onCreate(db)
    }

    private inline fun <T> safeDb(op: String, fallback: T, block: () -> T): T =
        runCatching(block).onFailure { err ->
            Log.w(TAG, "SQLite $op failed: ${err.message}")
        }.getOrDefault(fallback)

    private inline fun safeDbUnit(op: String, block: () -> Unit) {
        runCatching(block).onFailure { err ->
            Log.w(TAG, "SQLite $op failed: ${err.message}")
        }
    }

    // ───────────────────────── cover_cache ─────────────────────────

    open fun loadAllCovers(): List<CoverRow> = safeDb("loadAllCovers", emptyList()) {
        val db = readableDatabase
        val out = ArrayList<CoverRow>(512)
        db.rawQuery(
            "SELECT game_id, url, provider, match_mode, failed_until_ms, updated_at_ms FROM cover_cache",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += CoverRow(
                    gameId = c.getString(0),
                    url = if (c.isNull(1)) null else c.getString(1),
                    provider = if (c.isNull(2)) null else c.getString(2),
                    matchMode = if (c.isNull(3)) null else c.getString(3),
                    failedUntilMs = c.getLong(4),
                    updatedAtMs = c.getLong(5),
                )
            }
        }
        out
    }

    open fun putCoverSuccess(gameId: String, url: String, provider: String?, matchMode: String?) {
        safeDbUnit("putCoverSuccess") {
            val cv = ContentValues(6).apply {
                put("game_id", gameId)
                put("url", url)
                put("provider", provider)
                put("match_mode", matchMode)
                put("failed_until_ms", 0L)
                put("updated_at_ms", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict("cover_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    open fun putCoverFailure(gameId: String, failedUntilMs: Long) {
        safeDbUnit("putCoverFailure") {
            val cv = ContentValues(6).apply {
                put("game_id", gameId)
                putNull("url")
                putNull("provider")
                putNull("match_mode")
                put("failed_until_ms", failedUntilMs)
                put("updated_at_ms", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict("cover_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    open fun deleteCover(gameId: String) {
        safeDbUnit("deleteCover") {
            writableDatabase.delete("cover_cache", "game_id = ?", arrayOf(gameId))
        }
    }

    open fun clearCoverFailures(gameIds: Collection<String>) {
        if (gameIds.isEmpty()) return
        safeDbUnit("clearCoverFailures") {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (id in gameIds) {
                    db.execSQL(
                        "UPDATE cover_cache SET failed_until_ms = 0 WHERE game_id = ? AND url IS NULL",
                        arrayOf(id),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    open fun clearAllCoverFailures() {
        safeDbUnit("clearAllCoverFailures") {
            writableDatabase.delete("cover_cache", "url IS NULL", null)
        }
    }

    open fun clearCovers() {
        safeDbUnit("clearCovers") {
            writableDatabase.delete("cover_cache", null, null)
        }
    }

    fun bulkImportLegacyCovers(rows: List<CoverRow>) {
        if (rows.isEmpty()) return
        safeDbUnit("bulkImportLegacyCovers") {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (r in rows) {
                    val cv = ContentValues(6).apply {
                        put("game_id", r.gameId)
                        if (r.url != null) put("url", r.url) else putNull("url")
                        if (r.provider != null) put("provider", r.provider) else putNull("provider")
                        if (r.matchMode != null) put("match_mode", r.matchMode) else putNull("match_mode")
                        put("failed_until_ms", r.failedUntilMs)
                        put("updated_at_ms", r.updatedAtMs)
                    }
                    db.insertWithOnConflict("cover_cache", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    // ───────────────────────── scrape_cache ─────────────────────────

    data class ManualMetaRow(
        val gameId: String,
        val providerId: String,
        val officialName: String?,
        val canonicalName: String?,
        val meta: GameMetadata,
        val status: Int = STATUS_HIT,
        val expiresAtMs: Long = 0L,
        val focusChecked: Boolean = false,
    )

    data class BatchScrapeEntry(
        val providerId: String,
        val gameId: String,
        val hit: ScrapeHit,
        val status: Int,
        val expiresAtMs: Long,
    )

    private fun parseExtraJson(raw: String?): JSONObject? =
        if (raw.isNullOrBlank()) null else runCatching { JSONObject(raw) }.getOrNull()

    private fun buildExtraJson(
        hit: ScrapeHit,
        manualProvider: String? = null,
        focusChecked: Boolean = false,
    ): String? {
        val hasCanonical = !hit.canonicalName.isNullOrBlank() && hit.canonicalName != hit.officialName
        val hasDesc = !hit.meta.description.isNullOrBlank()
        if (!hit.matchedBySerial && !hasCanonical && !hasDesc && manualProvider.isNullOrBlank() && !focusChecked) return null
        return JSONObject().apply {
            if (hit.matchedBySerial) put("matchedBySerial", true)
            if (hasCanonical) put("canonicalName", hit.canonicalName)
            if (hasDesc) put("description", hit.meta.description)
            if (!manualProvider.isNullOrBlank()) put("manualProvider", manualProvider)
            if (focusChecked) put("focusChecked", true)
        }.toString()
    }

    private fun buildScrapeContentValues(
        providerId: String,
        gameId: String,
        hit: ScrapeHit,
        status: Int,
        expiresAtMs: Long,
        manualProvider: String? = null,
        focusChecked: Boolean = false,
    ): ContentValues {
        val extraJson = buildExtraJson(hit, manualProvider, focusChecked)
        return ContentValues(12).apply {
            put("provider_id", providerId)
            put("game_id", gameId)
            put("official_name", hit.officialName)
            put("release_date", hit.meta.releaseDate)
            put("genre", hit.meta.genre)
            put("developer", hit.meta.developer)
            put("publisher", hit.meta.publisher)
            put("cover_urls", if (hit.coverUrls.isEmpty()) "" else JSONArray(hit.coverUrls).toString())
            if (extraJson != null) put("extra_json", extraJson) else putNull("extra_json")
            put("status", status)
            put("expires_at_ms", expiresAtMs)
            put("updated_at_ms", System.currentTimeMillis())
        }
    }

    open fun getScrapeHit(providerId: String, gameId: String, nowMs: Long = System.currentTimeMillis()): ScrapeCacheRow? =
        safeDb("getScrapeHit", null) {
            readableDatabase.rawQuery(
                """
                SELECT official_name, release_date, genre, developer, publisher, cover_urls, extra_json, status, expires_at_ms
                FROM scrape_cache
                WHERE provider_id = ? AND game_id = ?
                """.trimIndent(),
                arrayOf(providerId, gameId),
            ).use { c ->
                if (!c.moveToFirst()) return@safeDb null
                val expires = c.getLong(8)
                if (expires > 0L && expires < nowMs) return@safeDb null
                val urlsRaw = if (c.isNull(5)) null else c.getString(5)
                val urls = ArrayList<String>()
                if (!urlsRaw.isNullOrBlank()) {
                    val arr = JSONArray(urlsRaw)
                    for (i in 0 until arr.length()) {
                        val u = arr.optString(i)
                        if (u.isNotBlank()) urls += u
                    }
                }
                val extraObj = parseExtraJson(if (c.isNull(6)) null else c.getString(6))
                val matchedBySerial = extraObj?.optBoolean("matchedBySerial", false) ?: false
                val canonicalName = extraObj?.optString("canonicalName")?.takeIf { it.isNotBlank() }
                val description = extraObj?.optString("description")?.takeIf { it.isNotBlank() }
                val hit = ScrapeHit(
                    officialName = if (c.isNull(0)) null else c.getString(0).takeIf { it.isNotBlank() },
                    canonicalName = canonicalName,
                    meta = GameMetadata(
                        releaseDate = if (c.isNull(1)) null else c.getString(1).takeIf { it.isNotBlank() },
                        genre = if (c.isNull(2)) null else c.getString(2).takeIf { it.isNotBlank() },
                        developer = if (c.isNull(3)) null else c.getString(3).takeIf { it.isNotBlank() },
                        publisher = if (c.isNull(4)) null else c.getString(4).takeIf { it.isNotBlank() },
                        description = description,
                    ),
                    coverUrls = urls,
                    matchedBySerial = matchedBySerial,
                )
                ScrapeCacheRow(hit = hit, status = c.getInt(7), expiresAtMs = expires)
            }
        }

    open fun putScrapeHit(
        providerId: String,
        gameId: String,
        hit: ScrapeHit,
        status: Int,
        expiresAtMs: Long,
    ) {
        safeDbUnit("putScrapeHit") {
            val cv = buildScrapeContentValues(providerId, gameId, hit, status, expiresAtMs)
            writableDatabase.insertWithOnConflict("scrape_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** 单事务批量写入刮削缓存，显著降低批量预取与全量刮削的 SQLite WAL fsync 频次。 */
    open fun putScrapeHitsBatch(entries: List<BatchScrapeEntry>) {
        if (entries.isEmpty()) return
        safeDbUnit("putScrapeHitsBatch") {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (e in entries) {
                    val cv = buildScrapeContentValues(e.providerId, e.gameId, e.hit, e.status, e.expiresAtMs)
                    db.insertWithOnConflict("scrape_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /** 持久化用户手动刮削选中的元数据与官方名（永久有效，重启不丢失）。 */
    open fun putManualMetadata(
        gameId: String,
        providerId: String,
        officialName: String?,
        canonicalName: String?,
        meta: GameMetadata,
    ) {
        safeDbUnit("putManualMetadata") {
            val hit = ScrapeHit(
                officialName = officialName,
                canonicalName = canonicalName,
                meta = meta,
            )
            val cv = buildScrapeContentValues(
                providerId = MANUAL_META_PROVIDER,
                gameId = gameId,
                hit = hit,
                status = STATUS_HIT,
                expiresAtMs = 0L,
                manualProvider = providerId,
            )
            writableDatabase.insertWithOnConflict("scrape_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** 批量持久化自动刮削/离线匹配得到的元数据、官方名及未命中负缓存（TTL 过期前冷启动不再解压离线表）。 */
    open fun putResolvedMetadataBatch(rows: List<ManualMetaRow>) {
        if (rows.isEmpty()) return
        safeDbUnit("putResolvedMetadataBatch") {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (row in rows) {
                    val hit = ScrapeHit(
                        officialName = row.officialName,
                        canonicalName = row.canonicalName,
                        meta = row.meta,
                    )
                    val cv = buildScrapeContentValues(
                        providerId = RESOLVED_META_PROVIDER,
                        gameId = row.gameId,
                        hit = hit,
                        status = row.status,
                        expiresAtMs = row.expiresAtMs,
                        manualProvider = row.providerId,
                        focusChecked = row.focusChecked,
                    )
                    db.insertWithOnConflict("scrape_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /** 一次性加载所有未过期的自动刮削元数据与负缓存（冷启动后台秒读）。 */
    open fun loadAllResolvedMetadata(nowMs: Long = System.currentTimeMillis()): List<ManualMetaRow> =
        loadMetadataRowsByProvider("loadAllResolvedMetadata", RESOLVED_META_PROVIDER, nowMs)

    /** 一次性加载所有持久化的手动刮削元数据（冷启动后台恢复）。 */
    open fun loadAllManualMetadata(): List<ManualMetaRow> =
        loadMetadataRowsByProvider("loadAllManualMetadata", MANUAL_META_PROVIDER, nowMs = 0L)

    /** 清除自动刮削负缓存（保留有效命中与手动刮削记录），用于数据源/凭证变更或远端元数据表更新后重试。 */
    open fun clearResolvedMetadataMisses() {
        safeDbUnit("clearResolvedMetadataMisses") {
            writableDatabase.delete(
                "scrape_cache",
                "provider_id = ? AND status != ?",
                arrayOf(RESOLVED_META_PROVIDER, STATUS_HIT.toString()),
            )
        }
    }

    private fun loadMetadataRowsByProvider(
        op: String,
        providerKey: String,
        nowMs: Long,
    ): List<ManualMetaRow> = safeDb(op, emptyList()) {
        val out = ArrayList<ManualMetaRow>(64)
        readableDatabase.rawQuery(
            """
            SELECT game_id, official_name, release_date, genre, developer, publisher, extra_json, status, expires_at_ms
            FROM scrape_cache
            WHERE provider_id = ?
            """.trimIndent(),
            arrayOf(providerKey),
        ).use { c ->
            while (c.moveToNext()) {
                val status = c.getInt(7)
                val expiresAtMs = c.getLong(8)
                if (nowMs > 0L && expiresAtMs > 0L && expiresAtMs < nowMs) continue
                val extraObj = parseExtraJson(if (c.isNull(6)) null else c.getString(6))
                val prov = extraObj?.optString("manualProvider")?.takeIf { it.isNotBlank() } ?: providerKey
                val canonical = extraObj?.optString("canonicalName")?.takeIf { it.isNotBlank() }
                val desc = extraObj?.optString("description")?.takeIf { it.isNotBlank() }
                val focusChecked = extraObj?.optBoolean("focusChecked", false) ?: false
                val meta = GameMetadata(
                    releaseDate = if (c.isNull(2)) null else c.getString(2).takeIf { it.isNotBlank() },
                    genre = if (c.isNull(3)) null else c.getString(3).takeIf { it.isNotBlank() },
                    developer = if (c.isNull(4)) null else c.getString(4).takeIf { it.isNotBlank() },
                    publisher = if (c.isNull(5)) null else c.getString(5).takeIf { it.isNotBlank() },
                    description = desc,
                )
                out += ManualMetaRow(
                    gameId = c.getString(0),
                    providerId = prov,
                    officialName = if (c.isNull(1)) null else c.getString(1).takeIf { it.isNotBlank() },
                    canonicalName = canonical,
                    meta = meta,
                    status = status,
                    expiresAtMs = expiresAtMs,
                    focusChecked = focusChecked,
                )
            }
        }
        out
    }

    open fun clearScrapeCache() {
        safeDbUnit("clearScrapeCache") {
            writableDatabase.delete("scrape_cache", null, null)
        }
    }

    // ───────────────────────── rom_identity ─────────────────────────

    fun getRomIdentity(path: String, size: Long, mtime: Long): RomIdentityRow? = safeDb("getRomIdentity", null) {
        readableDatabase.rawQuery(
            "SELECT size, mtime, crc32, sha1, serial FROM rom_identity WHERE path = ?",
            arrayOf(path),
        ).use { c ->
            if (!c.moveToFirst()) return@safeDb null
            if (c.getLong(0) != size || c.getLong(1) != mtime) return@safeDb null
            RomIdentityRow(
                crc32 = if (c.isNull(2)) null else c.getString(2),
                sha1 = if (c.isNull(3)) null else c.getString(3),
                serial = if (c.isNull(4)) null else c.getString(4),
            )
        }
    }

    fun putRomIdentity(path: String, size: Long, mtime: Long, row: RomIdentityRow) {
        safeDbUnit("putRomIdentity") {
            val cv = ContentValues(7).apply {
                put("path", path)
                put("size", size)
                put("mtime", mtime)
                put("crc32", row.crc32)
                put("sha1", row.sha1)
                put("serial", row.serial)
                put("updated_at_ms", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict("rom_identity", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun clearRomIdentities() {
        safeDbUnit("clearRomIdentities") {
            writableDatabase.delete("rom_identity", null, null)
        }
    }

    // ───────────────────────── 路径迁移与过期清理 ─────────────────────────

    /** 单个 ROM 文件重命名或移动后，同步迁移 3 张表中的主键路径，避免残留孤儿行或重算哈希。 */
    fun migrateGameId(oldId: String, newId: String) {
        if (oldId == newId) return
        safeDbUnit("migrateGameId") {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM cover_cache WHERE game_id = ?", arrayOf(newId))
                db.execSQL("UPDATE cover_cache SET game_id = ? WHERE game_id = ?", arrayOf(newId, oldId))
                db.execSQL("DELETE FROM scrape_cache WHERE game_id = ?", arrayOf(newId))
                db.execSQL("UPDATE scrape_cache SET game_id = ? WHERE game_id = ?", arrayOf(newId, oldId))
                db.execSQL("DELETE FROM rom_identity WHERE path = ?", arrayOf(newId))
                db.execSQL("UPDATE rom_identity SET path = ? WHERE path = ?", arrayOf(newId, oldId))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /** 单个 ROM 文件被永久删除后，清理 3 张表中的关联缓存行。 */
    fun deleteGameData(gameId: String) {
        safeDbUnit("deleteGameData") {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("cover_cache", "game_id = ?", arrayOf(gameId))
                db.delete("scrape_cache", "game_id = ?", arrayOf(gameId))
                db.delete("rom_identity", "path = ?", arrayOf(gameId))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /** 文件夹重命名或移动后，批量迁移前缀匹配的缓存路径。 */
    fun migratePathPrefix(oldDir: String, newDir: String) {
        val oldPrefix = if (oldDir.endsWith("/")) oldDir else "$oldDir/"
        val newPrefix = if (newDir.endsWith("/")) newDir else "$newDir/"
        if (oldPrefix == newPrefix) return
        val prefixLenPlus1 = oldPrefix.length + 1
        val likeArg = escapeLike(oldPrefix) + "%"
        safeDbUnit("migratePathPrefix") {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.execSQL(
                    "UPDATE OR REPLACE cover_cache SET game_id = ? || substr(game_id, ?) WHERE game_id LIKE ? ESCAPE '\\'",
                    arrayOf<Any>(newPrefix, prefixLenPlus1, likeArg),
                )
                db.execSQL(
                    "UPDATE OR REPLACE scrape_cache SET game_id = ? || substr(game_id, ?) WHERE game_id LIKE ? ESCAPE '\\'",
                    arrayOf<Any>(newPrefix, prefixLenPlus1, likeArg),
                )
                db.execSQL(
                    "UPDATE OR REPLACE rom_identity SET path = ? || substr(path, ?) WHERE path LIKE ? ESCAPE '\\'",
                    arrayOf<Any>(newPrefix, prefixLenPlus1, likeArg),
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /** 文件夹被删除后，批量清理前缀匹配的缓存行。 */
    fun deletePathPrefix(dirPath: String) {
        val prefix = if (dirPath.endsWith("/")) dirPath else "$dirPath/"
        val likeArg = escapeLike(prefix) + "%"
        safeDbUnit("deletePathPrefix") {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM cover_cache WHERE game_id LIKE ? ESCAPE '\\'", arrayOf(likeArg))
                db.execSQL("DELETE FROM scrape_cache WHERE game_id LIKE ? ESCAPE '\\'", arrayOf(likeArg))
                db.execSQL("DELETE FROM rom_identity WHERE path LIKE ? ESCAPE '\\'", arrayOf(likeArg))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /** 定期清理已过期的 `scrape_cache` 行与长期未更新的 `rom_identity` 孤儿行。 */
    fun pruneExpired(nowMs: Long = System.currentTimeMillis()) {
        safeDbUnit("pruneExpired") {
            val db = writableDatabase
            db.delete("scrape_cache", "expires_at_ms > 0 AND expires_at_ms < ?", arrayOf(nowMs.toString()))
            val staleBefore = nowMs - TTL_ROM_IDENTITY_MS
            db.delete("rom_identity", "updated_at_ms > 0 AND updated_at_ms < ?", arrayOf(staleBefore.toString()))
        }
    }

    private fun escapeLike(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        private const val TAG = "ScrapeDatabase"
        private const val DB_NAME = "scrape_cache.db"
        private const val DB_VERSION = 1

        const val STATUS_HIT = 1
        const val STATUS_MISS = 0
        const val STATUS_ERROR = -1

        const val MANUAL_META_PROVIDER = "manual"
        const val RESOLVED_META_PROVIDER = "resolved"

        const val TTL_HIT_MS = 30L * 24 * 60 * 60 * 1000L
        const val TTL_MISS_MS = 7L * 24 * 60 * 60 * 1000L
        const val TTL_ERROR_MS = 15L * 60 * 1000L
        const val TTL_ROM_IDENTITY_MS = 90L * 24 * 60 * 60 * 1000L
    }
}
