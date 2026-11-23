package com.pocketdeck.launcher.data

import android.content.Context
import android.util.Xml
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * GameTDB 零密钥本地索引客户端：
 * - 覆盖任天堂（Wii / GameCube / NDS / DSi / 3DS / Wii U / Switch）与 PS3；
 * - 按需下载官方压缩 XML（如 `wiitdb.zip` / `dstdb.zip` / `3dstdb.zip` / `switchtdb.zip` / `ps3tdb.zip`），
 *   流式解析后以紧凑 TSV 缓存到本地，之后全离线秒级匹配；
 * - 支持 Serial/Game ID 精确匹配与标题/中文名匹配，同时返回中/英/日官方名、开发商、发行商、类型、发售日期与官方 2D 封面 CDN URL。
 */
class GameTdbClient(
    context: Context,
    private val httpProvider: () -> OkHttpClient,
    private val identityExtractor: RomIdentityExtractor,
) {
    constructor(
        context: Context,
        http: OkHttpClient,
        identityExtractor: RomIdentityExtractor,
    ) : this(context, { http }, identityExtractor)

    private val appCtx = context.applicationContext
    private val cacheDir = File(appCtx.cacheDir, "gametdb")
    private val tables = BoundedLruCache<String, TdbTable>(MAX_CACHED_TABLES)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val downloadFailedUntilMs = ConcurrentHashMap<String, Long>()

    data class TdbEntry(
        val id: String,
        val region: String,
        val titleEn: String,
        val titleZh: String?,
        val titleJa: String?,
        val developer: String?,
        val publisher: String?,
        val genre: String?,
        val releaseDate: String?,
        val titleId: String? = null,
    )

    data class TdbMatch(
        val entry: TdbEntry,
        val bySerial: Boolean,
        val hit: ScrapeHit,
    )

    private class TdbTable(
        val dbKey: String,
        val artSystem: String,
        val byId: Map<String, TdbEntry>,
        val byTitleKey: Map<String, TdbEntry>,
        val byCompact: Map<String, TdbEntry>,
        val byToken: Map<String, List<TdbEntry>>,
    ) {
        fun find(
            serial: String?,
            queries: List<String>,
            preferZh: Boolean,
            allowFuzzy: Boolean = true,
        ): TdbMatch? {
            if (!serial.isNullOrBlank()) {
                val up = serial.uppercase()
                val direct = byId[up]
                    ?: byId[up.replace("-", "")]
                    ?: (if (up.length == 4) byId.entries.firstOrNull { it.key.startsWith(up) }?.value else null)
                if (direct != null) {
                    return TdbMatch(direct, bySerial = true, hit = entryToHit(direct, artSystem, preferZh))
                }
            }

            // 标题精确/紧凑匹配（O(1)）
            for (q in queries) {
                if (q.isBlank()) continue
                val norm = TitleNormalizer.normalizedTitle(q)
                if (norm.isNotEmpty()) {
                    byTitleKey[norm]?.let {
                        return TdbMatch(it, bySerial = false, hit = entryToHit(it, artSystem, preferZh))
                    }
                }
                val comp = TitleNormalizer.compactKey(q)
                if (comp.isNotEmpty()) {
                    byCompact[comp]?.let {
                        return TdbMatch(it, bySerial = false, hit = entryToHit(it, artSystem, preferZh))
                    }
                }
            }

            if (!allowFuzzy) return null

            // 倒排词模糊匹配（优先保留英文别名与去重词元，防止细微标点或副标题差异漏掉）
            var best: TdbEntry? = null
            var bestScore = 0.78
            for (q in TitleNormalizer.selectFuzzyQueries(queries)) {
                val norm = TitleNormalizer.normalizedTitle(q)
                val tokens = TitleNormalizer.tokenize(norm)
                if (tokens.isEmpty()) continue
                val candidateCounts = HashMap<TdbEntry, Int>(32)
                for (tok in tokens) {
                    val bucket = byToken[tok] ?: continue
                    if (bucket.size > 400) continue
                    for (entry in bucket) {
                        candidateCounts[entry] = (candidateCounts[entry] ?: 0) + 1
                    }
                }
                for (entry in candidateCounts.keys) {
                    val sEn = TitleNormalizer.similarity(q, entry.titleEn)
                    val sZh = entry.titleZh?.let { TitleNormalizer.similarity(q, it) } ?: 0.0
                    val s = maxOf(sEn, sZh)
                    if (s > bestScore) {
                        bestScore = s
                        best = entry
                    }
                }
            }
            return best?.let { TdbMatch(it, bySerial = false, hit = entryToHit(it, artSystem, preferZh)) }
        }
    }

    fun supportsPlatform(platformId: String?): Boolean =
        platformId != null && PLATFORM_SPEC.containsKey(platformId.lowercase())

    fun clearCache() {
        tables.clear()
        downloadFailedUntilMs.clear()
        runCatching { cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
    }

    /** 释放非活跃平台的 GameTDB 内存表；aggressive=true（如退后台启动模拟器或内存极紧）时全量释放。 */
    fun trimMemory(keepDbKeys: Set<String> = emptySet(), aggressive: Boolean = false) {
        if (aggressive) {
            tables.clear()
        } else {
            tables.retainOnly(keepDbKeys)
        }
    }

    fun trimMemoryByLibretroSystems(
        keepSystems: Set<String> = OfflineGameDb.activeSystems,
        aggressive: Boolean = false,
    ) {
        val keepDbKeys = dbKeysForLibretroSystems(keepSystems)
        trimMemory(keepDbKeys = keepDbKeys, aggressive = aggressive)
    }

    internal fun cachedTableCount(): Int = tables.size

    internal fun isDownloadInCooldown(dbKey: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        (downloadFailedUntilMs[dbKey] ?: 0L) > nowMs

    /** 若该平台索引已在内存中，做 O(1) 纯内存精确查询（供 Compose 渲染线程调用，绝不做耗时模糊扫描/网络/磁盘 I/O）。 */
    fun lookupInMemory(game: Game, platform: Platform?): TdbMatch? {
        val spec = PLATFORM_SPEC[platform?.id?.lowercase()] ?: return null
        val table = tables[spec.dbKey] ?: return null
        val queries = TitleNormalizer.buildQueryVariants(game.baseName, game.title)
        return table.find(serial = null, queries = queries, preferZh = isChineseLocale(), allowFuzzy = false)
    }

    suspend fun lookup(game: Game, platform: Platform?, allowDownload: Boolean = true): TdbMatch? {
        val spec = PLATFORM_SPEC[platform?.id?.lowercase()] ?: return null
        val table = getOrLoadTable(spec, allowDownload) ?: return null
        val identity = withContext(Dispatchers.IO) {
            identityExtractor.extract(File(game.id), platform?.id, skipFullFileCrc = true)
        }
        val queries = TitleNormalizer.buildQueryVariants(game.baseName, game.title)
        return table.find(identity.serial, queries, preferZh = isChineseLocale(), allowFuzzy = true)
    }

    private suspend fun getOrLoadTable(spec: DbSpec, allowDownload: Boolean): TdbTable? {
        tables[spec.dbKey]?.let { return it }
        val lock = locks.getOrPut(spec.dbKey) { Mutex() }
        return lock.withLock {
            tables[spec.dbKey]?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val tsvFile = File(cacheDir, "${spec.dbKey}_v2.tsv")
                val entries = if (tsvFile.isFile && tsvFile.length() > 0L) {
                    readTsv(tsvFile)
                } else if (allowDownload && NetworkStatus.isOnline(appCtx)) {
                    val now = System.currentTimeMillis()
                    if ((downloadFailedUntilMs[spec.dbKey] ?: 0L) > now) {
                        emptyList()
                    } else {
                        val downloaded = downloadAndParseZip(spec, tsvFile)
                        if (downloaded.isEmpty()) {
                            downloadFailedUntilMs[spec.dbKey] = now + DOWNLOAD_FAIL_COOLDOWN_MS
                        } else {
                            downloadFailedUntilMs.remove(spec.dbKey)
                        }
                        downloaded
                    }
                } else {
                    emptyList()
                }
                if (entries.isEmpty()) return@withContext null
                val table = buildTable(spec, entries)
                tables.put(spec.dbKey, table, protectedKeys = dbKeysForLibretroSystems(OfflineGameDb.activeSystems))
                table
            }
        }
    }

    private fun downloadAndParseZip(spec: DbSpec, cacheFile: File): List<TdbEntry> {
        val url = "https://www.gametdb.com/${spec.dbKey}.zip"
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            httpProvider().newCall(req).execute().use { resp ->
                code = resp.code
                if (!resp.isSuccessful) {
                    ScrapeLogStore.log(
                        "META",
                        "gametdb",
                        "GET ${spec.dbKey}",
                        url = url,
                        status = code,
                        elapsedMs = System.currentTimeMillis() - t0,
                        error = "HTTP $code",
                    )
                    return emptyList()
                }
                val body = resp.body ?: return emptyList()
                val entries = ZipInputStream(body.byteStream()).use { zis ->
                    var parsed: List<TdbEntry> = emptyList()
                    while (true) {
                        val ze = zis.nextEntry ?: break
                        if (!ze.isDirectory && ze.name.endsWith(".xml", ignoreCase = true)) {
                            parsed = parseTdbXml(zis)
                            break
                        }
                    }
                    parsed
                }
                if (entries.isNotEmpty()) {
                    writeTsv(cacheFile, entries)
                }
                ScrapeLogStore.log(
                    "META",
                    "gametdb",
                    "GET ${spec.dbKey}",
                    url = url,
                    status = code,
                    elapsedMs = System.currentTimeMillis() - t0,
                    result = "${entries.size} entries",
                )
                entries
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log(
                "META",
                "gametdb",
                "GET ${spec.dbKey}",
                url = url,
                status = code,
                elapsedMs = System.currentTimeMillis() - t0,
                error = "${t.javaClass.simpleName}: ${t.message}",
            )
            emptyList()
        }
    }

    private fun parseTdbXml(input: InputStream): List<TdbEntry> {
        val out = ArrayList<TdbEntry>(4096)
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")

        var inGame = false
        var id = ""
        var titleId: String? = null
        var region = ""
        var nameAttr = ""
        var titleEn: String? = null
        var titleZhCn: String? = null
        var titleZhTw: String? = null
        var titleJa: String? = null
        var dev: String? = null
        var pub: String? = null
        var genre: String? = null
        var year: String? = null
        var month: String? = null
        var currentLocaleLang: String? = null
        var currentTag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    if (tag == "game") {
                        inGame = true
                        nameAttr = parser.getAttributeValue(null, "name").orEmpty()
                        id = ""
                        titleId = null
                        region = ""
                        titleEn = null
                        titleZhCn = null
                        titleZhTw = null
                        titleJa = null
                        dev = null
                        pub = null
                        genre = null
                        year = null
                        month = null
                        currentLocaleLang = null
                    } else if (inGame) {
                        currentTag = tag
                        when (tag) {
                            "locale" -> currentLocaleLang = parser.getAttributeValue(null, "lang")?.uppercase()
                            "date" -> {
                                year = parser.getAttributeValue(null, "year")?.takeIf { it.isNotBlank() }
                                month = parser.getAttributeValue(null, "month")?.takeIf { it.isNotBlank() }
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inGame && currentTag != null) {
                        val txt = parser.text?.trim().orEmpty()
                        if (txt.isNotEmpty()) {
                            when (currentTag) {
                                "id" -> id = txt.uppercase()
                                "titleid" -> titleId = if (titleId == null) txt.uppercase() else "$titleId,${txt.uppercase()}"
                                "region" -> region = txt.uppercase()
                                "developer" -> if (dev == null) dev = txt
                                "publisher" -> if (pub == null) pub = txt
                                "genre" -> if (genre == null) genre = txt.substringBefore(',').trim()
                                "title" -> when (currentLocaleLang) {
                                    "EN" -> if (titleEn == null) titleEn = txt
                                    "ZHCN" -> if (titleZhCn == null) titleZhCn = txt
                                    "ZHTW" -> if (titleZhTw == null) titleZhTw = txt
                                    "JA" -> if (titleJa == null) titleJa = txt
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name
                    if (tag == currentTag) currentTag = null
                    if (tag == "locale") currentLocaleLang = null
                    if (tag == "game" && inGame) {
                        val en = titleEn ?: nameAttr.takeIf { it.isNotBlank() } ?: titleJa ?: titleZhCn
                        if (id.isNotEmpty() && !en.isNullOrBlank()) {
                            val rel = formatReleaseDate(year, month)
                            out += TdbEntry(
                                id = id,
                                region = region,
                                titleEn = en,
                                titleZh = titleZhCn ?: titleZhTw,
                                titleJa = titleJa,
                                developer = dev,
                                publisher = pub,
                                genre = genre,
                                releaseDate = rel,
                                titleId = titleId,
                            )
                        }
                        inGame = false
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun writeTsv(file: File, entries: List<TdbEntry>) {
        runCatching {
            cacheDir.mkdirs()
            val tmp = File(cacheDir, "${file.name}.tmp")
            tmp.bufferedWriter().use { w ->
                for (e in entries) {
                    w.append(cleanTsv(e.id)).append('\t')
                        .append(cleanTsv(e.region)).append('\t')
                        .append(cleanTsv(e.titleEn)).append('\t')
                        .append(cleanTsv(e.titleZh)).append('\t')
                        .append(cleanTsv(e.titleJa)).append('\t')
                        .append(cleanTsv(e.developer)).append('\t')
                        .append(cleanTsv(e.publisher)).append('\t')
                        .append(cleanTsv(e.genre)).append('\t')
                        .append(cleanTsv(e.releaseDate)).append('\t')
                        .append(cleanTsv(e.titleId)).append('\n')
                }
            }
            tmp.renameTo(file)
        }
    }

    private fun readTsv(file: File): List<TdbEntry> = runCatching {
        val out = ArrayList<TdbEntry>(4096)
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                val p = line.split('\t')
                if (p.size < 9) continue
                out += TdbEntry(
                    id = p[0],
                    region = p[1],
                    titleEn = p[2],
                    titleZh = p[3].takeIf { it.isNotEmpty() },
                    titleJa = p[4].takeIf { it.isNotEmpty() },
                    developer = p[5].takeIf { it.isNotEmpty() },
                    publisher = p[6].takeIf { it.isNotEmpty() },
                    genre = p[7].takeIf { it.isNotEmpty() },
                    releaseDate = p[8].takeIf { it.isNotEmpty() },
                    titleId = p.getOrNull(9)?.takeIf { it.isNotEmpty() },
                )
            }
        }
        out
    }.getOrDefault(emptyList())

    private fun buildTable(spec: DbSpec, entries: List<TdbEntry>): TdbTable {
        val byId = HashMap<String, TdbEntry>(entries.size * 2)
        val byTitle = HashMap<String, TdbEntry>(entries.size * 2)
        val byCompact = HashMap<String, TdbEntry>(entries.size * 2)
        val byToken = HashMap<String, MutableList<TdbEntry>>(entries.size)

        fun regionRank(r: String): Int = when {
            "NTSC-U" in r || r == "US" -> 1
            "PAL" in r || r == "EN" -> 2
            "NTSC-J" in r || r == "JA" -> 3
            else -> 4
        }

        fun putBetter(map: MutableMap<String, TdbEntry>, key: String, e: TdbEntry) {
            if (key.isEmpty()) return
            val prev = map[key]
            if (prev == null || regionRank(e.region) < regionRank(prev.region)) {
                map[key] = e
            }
        }

        for (e in entries) {
            byId.putIfAbsent(e.id, e)
            e.titleId?.split(',')?.forEach { rawTid ->
                val cleanTid = rawTid.trim().uppercase()
                if (cleanTid.isNotEmpty()) {
                    byId.putIfAbsent(cleanTid, e)
                }
            }
            val normEn = TitleNormalizer.normalizedTitle(e.titleEn)
            putBetter(byTitle, normEn, e)
            putBetter(byCompact, TitleNormalizer.compactKey(e.titleEn), e)
            e.titleZh?.let { zh ->
                putBetter(byTitle, TitleNormalizer.normalizedTitle(zh), e)
                putBetter(byCompact, TitleNormalizer.compactKey(zh), e)
            }
            e.titleJa?.let { ja ->
                putBetter(byTitle, TitleNormalizer.normalizedTitle(ja), e)
                putBetter(byCompact, TitleNormalizer.compactKey(ja), e)
            }
            for (tok in TitleNormalizer.tokenize(normEn)) {
                byToken.getOrPut(tok) { ArrayList(4) }.add(e)
            }
        }
        return TdbTable(spec.dbKey, spec.artSystem, byId, byTitle, byCompact, byToken)
    }

    private data class DbSpec(val dbKey: String, val artSystem: String)

    companion object {
        private const val USER_AGENT = "PocketDeck/0.4 (Android Handheld Launcher)"
        internal const val MAX_CACHED_TABLES = 3
        private const val DOWNLOAD_FAIL_COOLDOWN_MS = 10L * 60 * 1000L
        private val MONTHS = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )

        private val LIBRETRO_SYSTEM_TO_DB_KEY: Map<String, String> = mapOf(
            "Nintendo - Wii" to "wiitdb",
            "Nintendo - GameCube" to "wiitdb",
            "Nintendo - Nintendo DS" to "dstdb",
            "Nintendo - Nintendo DSi" to "dstdb",
            "Nintendo - Nintendo 3DS" to "3dstdb",
            "Nintendo - Wii U" to "wiiutdb",
            "Nintendo - Nintendo Switch" to "switchtdb",
            "Sony - PlayStation 3" to "ps3tdb",
        )

        internal fun dbKeysForLibretroSystems(systems: Set<String>): Set<String> {
            if (systems.isEmpty()) return emptySet()
            val out = HashSet<String>(systems.size)
            for (sys in systems) {
                LIBRETRO_SYSTEM_TO_DB_KEY[sys]?.let { out += it }
            }
            return out
        }

        private val PLATFORM_SPEC: Map<String, DbSpec> = mapOf(
            "wii" to DbSpec("wiitdb", "wii"),
            "gamecube" to DbSpec("wiitdb", "wii"),
            "nds" to DbSpec("dstdb", "ds"),
            "dsi" to DbSpec("dstdb", "ds"),
            "3ds" to DbSpec("3dstdb", "3ds"),
            "wiiu" to DbSpec("wiiutdb", "wiiu"),
            "switch" to DbSpec("switchtdb", "switch"),
            "ps3" to DbSpec("ps3tdb", "ps3"),
        )

        private fun isChineseLocale(): Boolean =
            Locale.getDefault().language.equals("zh", ignoreCase = true)

        private fun cleanTsv(s: String?): String =
            s.orEmpty().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

        private fun formatReleaseDate(year: String?, month: String?): String? {
            val y = year?.trim()?.takeIf { it.isNotEmpty() && it != "0" } ?: return null
            val m = month?.trim()?.toIntOrNull()
            return if (m != null && m in 1..12) "${MONTHS[m - 1]} $y" else y
        }

        internal fun coverRegionCodes(entry: TdbEntry, artSystem: String = ""): List<String> {
            val out = LinkedHashSet<String>(4)
            val id = entry.id
            // 任天堂 Wii / GC / DS / 3DS / WiiU 的 4~6 位 ID 第 4 位代表发行区码；Switch 5 位码不遵循此规则
            if (artSystem != "switch" && id.length in 4..6) {
                when (id[3]) {
                    'E' -> out += "US"
                    'J' -> out += "JA"
                    'P', 'X', 'Y', 'Z' -> out += "EN"
                    'K' -> out += "KO"
                    'W', 'C' -> out += "ZH"
                }
            }
            when {
                "NTSC-U" in entry.region -> out += "US"
                "NTSC-J" in entry.region -> out += "JA"
                "PAL" in entry.region -> out += "EN"
            }
            out += "US"
            out += "EN"
            out += "JA"
            return out.toList()
        }

        internal fun entryToHit(entry: TdbEntry, artSystem: String, preferZh: Boolean): ScrapeHit {
            val official = if (preferZh) {
                entry.titleZh ?: entry.titleEn
            } else {
                entry.titleEn
            }
            val urls = ArrayList<String>(4)
            val regions = coverRegionCodes(entry, artSystem)
            val coverPath = when (artSystem) {
                "wii", "wiiu" -> "cover"
                "ds", "3ds", "switch", "ps3" -> "cover"
                else -> "cover"
            }
            val ext = if (artSystem == "switch" || artSystem == "ps3") "jpg" else "png"
            for (reg in regions.take(2)) {
                urls += "https://art.gametdb.com/$artSystem/$coverPath/$reg/${entry.id}.$ext"
            }
            if (ext == "png") {
                urls += "https://art.gametdb.com/$artSystem/$coverPath/${regions.first()}/${entry.id}.jpg"
            }
            return ScrapeHit(
                officialName = official,
                canonicalName = entry.titleEn,
                meta = GameMetadata(
                    releaseDate = entry.releaseDate,
                    genre = entry.genre,
                    developer = entry.developer,
                    publisher = entry.publisher,
                ),
                coverUrls = urls,
            )
        }
    }
}
