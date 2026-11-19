package com.pocketdeck.launcher.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.pocketdeck.launcher.data.model.GameMetadata
import org.json.JSONArray

/**
 * 统一的 WAL SQLite 刮削缓存库，取代：
 * - `cover_index_v3` SharedPreferences（消除每加载一张封面都全量重写 XML 的写放大）
 * - `scraper_api/` 下数万个小 JSON 文件（消除文件系统 inode 与遍历开销）
 * - 每次冷启动重复计算 ROM CRC32/SHA1/Serial 的 I/O
 */
class ScrapeDatabase(context: Context) : SQLiteOpenHelper(
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
                updated_at_ms INTEGER NOT NULL DEFAULT 0
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
                status INTEGER NOT NULL,
                expires_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                PRIMARY KEY (provider_id, game_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_scrape_cache_game ON scrape_cache(game_id)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rom_identity (
                path TEXT PRIMARY KEY NOT NULL,
                size INTEGER NOT NULL,
                mtime INTEGER NOT NULL,
                crc32 TEXT,
                sha1 TEXT,
                serial TEXT
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    // ───────────────────────── cover_cache ─────────────────────────

    fun loadAllCovers(): List<CoverRow> = runCatching {
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
    }.getOrDefault(emptyList())

    fun putCoverSuccess(gameId: String, url: String, provider: String?, matchMode: String?) {
        runCatching {
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

    fun putCoverFailure(gameId: String, failedUntilMs: Long) {
        runCatching {
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

    fun deleteCover(gameId: String) {
        runCatching {
            writableDatabase.delete("cover_cache", "game_id = ?", arrayOf(gameId))
        }
    }

    fun clearCoverFailures(gameIds: Collection<String>) {
        if (gameIds.isEmpty()) return
        runCatching {
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

    fun clearCovers() {
        runCatching { writableDatabase.delete("cover_cache", null, null) }
    }

    fun bulkImportLegacyCovers(rows: List<CoverRow>) {
        if (rows.isEmpty()) return
        runCatching {
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

    fun getScrapeHit(providerId: String, gameId: String, nowMs: Long = System.currentTimeMillis()): ScrapeCacheRow? =
        runCatching {
            readableDatabase.rawQuery(
                """
                SELECT official_name, release_date, genre, developer, publisher, cover_urls, status, expires_at_ms
                FROM scrape_cache
                WHERE provider_id = ? AND game_id = ?
                """.trimIndent(),
                arrayOf(providerId, gameId),
            ).use { c ->
                if (!c.moveToFirst()) return@runCatching null
                val expires = c.getLong(7)
                if (expires > 0L && expires < nowMs) return@runCatching null
                val urlsRaw = if (c.isNull(5)) null else c.getString(5)
                val urls = ArrayList<String>()
                if (!urlsRaw.isNullOrBlank()) {
                    val arr = JSONArray(urlsRaw)
                    for (i in 0 until arr.length()) {
                        val u = arr.optString(i)
                        if (u.isNotBlank()) urls += u
                    }
                }
                val hit = ScrapeHit(
                    officialName = if (c.isNull(0)) null else c.getString(0).takeIf { it.isNotBlank() },
                    meta = GameMetadata(
                        releaseDate = if (c.isNull(1)) null else c.getString(1).takeIf { it.isNotBlank() },
                        genre = if (c.isNull(2)) null else c.getString(2).takeIf { it.isNotBlank() },
                        developer = if (c.isNull(3)) null else c.getString(3).takeIf { it.isNotBlank() },
                        publisher = if (c.isNull(4)) null else c.getString(4).takeIf { it.isNotBlank() },
                    ),
                    coverUrls = urls,
                )
                ScrapeCacheRow(hit = hit, status = c.getInt(6), expiresAtMs = expires)
            }
        }.getOrNull()

    fun putScrapeHit(
        providerId: String,
        gameId: String,
        hit: ScrapeHit,
        status: Int,
        expiresAtMs: Long,
    ) {
        runCatching {
            val cv = ContentValues(11).apply {
                put("provider_id", providerId)
                put("game_id", gameId)
                put("official_name", hit.officialName)
                put("release_date", hit.meta.releaseDate)
                put("genre", hit.meta.genre)
                put("developer", hit.meta.developer)
                put("publisher", hit.meta.publisher)
                put("cover_urls", if (hit.coverUrls.isEmpty()) "" else JSONArray(hit.coverUrls).toString())
                put("status", status)
                put("expires_at_ms", expiresAtMs)
                put("updated_at_ms", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict("scrape_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun clearScrapeCache() {
        runCatching { writableDatabase.delete("scrape_cache", null, null) }
    }

    // ───────────────────────── rom_identity ─────────────────────────

    fun getRomIdentity(path: String, size: Long, mtime: Long): RomIdentityRow? = runCatching {
        readableDatabase.rawQuery(
            "SELECT size, mtime, crc32, sha1, serial FROM rom_identity WHERE path = ?",
            arrayOf(path),
        ).use { c ->
            if (!c.moveToFirst()) return@runCatching null
            if (c.getLong(0) != size || c.getLong(1) != mtime) return@runCatching null
            RomIdentityRow(
                crc32 = if (c.isNull(2)) null else c.getString(2),
                sha1 = if (c.isNull(3)) null else c.getString(3),
                serial = if (c.isNull(4)) null else c.getString(4),
            )
        }
    }.getOrNull()

    fun putRomIdentity(path: String, size: Long, mtime: Long, row: RomIdentityRow) {
        runCatching {
            val cv = ContentValues(6).apply {
                put("path", path)
                put("size", size)
                put("mtime", mtime)
                put("crc32", row.crc32)
                put("sha1", row.sha1)
                put("serial", row.serial)
            }
            writableDatabase.insertWithOnConflict("rom_identity", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun clearRomIdentities() {
        runCatching { writableDatabase.delete("rom_identity", null, null) }
    }

    companion object {
        private const val DB_NAME = "scrape_cache_v1.db"
        private const val DB_VERSION = 1

        const val STATUS_HIT = 1
        const val STATUS_MISS = 0
        const val STATUS_ERROR = -1

        const val TTL_HIT_MS = 30L * 24 * 60 * 60 * 1000L
        const val TTL_MISS_MS = 7L * 24 * 60 * 60 * 1000L
        const val TTL_ERROR_MS = 15L * 60 * 1000L
    }
}
