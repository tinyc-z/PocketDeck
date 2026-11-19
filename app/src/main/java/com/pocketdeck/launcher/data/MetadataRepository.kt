package com.pocketdeck.launcher.data

import android.content.Context
import android.net.Uri
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

enum class MetaMatchMode {
    Crc32Hash,
    ExactFilename,
    TitleMatch,
    ApiHash,
    ApiRomName,
    ApiNameSearch,
}

data class MetadataResolutionInfo(
    val providers: List<String> = emptyList(),
    val matchModes: List<MetaMatchMode> = emptyList(),
    val crc32: String? = null,
)

/**
 * 多服务商游戏元数据仓库（Release date / Genre / Developer / Publisher）。
 *
 * 支持按用户配置的优先级顺序查询并合并多个元数据服务商：
 * - Libretro DB (jsDelivr CDN / GitHub)
 * - Hasheous (ROM Hash)
 * - ArcadeItalia (MAME/FBNeo)
 * - Wikidata
 * - ScreenScraper.fr / TheGamesDB.net / IGDB.com
 */
class MetadataRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val client: ScraperClient,
) {
    private val appCtx = context.applicationContext
    private val cacheDir = File(appCtx.cacheDir, "metadata").apply { mkdirs() }
    private val systemTables = ConcurrentHashMap<String, SystemTable>()
    private val systemLocks = ConcurrentHashMap<String, Mutex>()
    private val gameCache = ConcurrentHashMap<String, GameMetadata>()
    private val focusApiResolved = ConcurrentHashMap<String, Boolean>()

    @Volatile
    private var providerConfigs: List<SourceItemConfig> = ScraperCatalog.defaultMetadataConfigs()

    @Volatile
    private var credentials: ScraperCredentials = ScraperCredentials()

    @Volatile
    private var configuredOnce = false

    private val _metadata = MutableStateFlow<Map<String, GameMetadata>>(emptyMap())
    val metadata: StateFlow<Map<String, GameMetadata>> = _metadata.asStateFlow()

    private val _officialNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val officialNames: StateFlow<Map<String, String>> = _officialNames.asStateFlow()

    private val _resolutionInfo = MutableStateFlow<Map<String, MetadataResolutionInfo>>(emptyMap())
    val resolutionInfo: StateFlow<Map<String, MetadataResolutionInfo>> = _resolutionInfo.asStateFlow()

    init {
        scope.launch {
            var prev = NetworkStatus.online.value
            NetworkStatus.online.collect { now ->
                if (now && !prev) {
                    focusApiResolved.clear()
                }
                prev = now
            }
        }
    }

    suspend fun computeCrc32(path: String): String? = withContext(Dispatchers.IO) {
        client.computeRomCrc32(File(path))
    }

    private class MutableEntry(
        var comment: String = "",
        var year: String? = null,
        var month: String? = null,
        var genre: String? = null,
        var developer: String? = null,
        var publisher: String? = null,
    ) {
        fun toMetadata(): GameMetadata {
            val date = formatReleaseDate(year, month)
            return GameMetadata(
                releaseDate = date,
                genre = genre?.takeIf { it.isNotBlank() },
                developer = developer?.takeIf { it.isNotBlank() },
                publisher = publisher?.takeIf { it.isNotBlank() },
            )
        }
    }

    private data class ResolvedRow(val officialName: String, val meta: GameMetadata)
    private data class TableHit(val row: ResolvedRow, val mode: MetaMatchMode)

    private class SystemTable(
        val byCrc: Map<String, ResolvedRow>,
        val byExact: Map<String, ResolvedRow>,
        val byTitle: Map<String, ResolvedRow>,
    ) {
        fun find(baseName: String, title: String, crc: String?): TableHit? {
            if (crc != null) {
                byCrc[crc]?.let { return TableHit(it, MetaMatchMode.Crc32Hash) }
            }
            byExact[normalizeKey(baseName)]?.let { return TableHit(it, MetaMatchMode.ExactFilename) }
            byTitle[normalizeTitle(baseName)]?.let { return TableHit(it, MetaMatchMode.TitleMatch) }
            byTitle[normalizeTitle(title)]?.let { return TableHit(it, MetaMatchMode.TitleMatch) }
            return null
        }
    }

    fun configure(providers: List<SourceItemConfig>, cred: ScraperCredentials) {
        val normalized = ScraperCatalog.normalizeMetadataConfigs(providers)
        val changed = normalized != providerConfigs || cred != credentials
        providerConfigs = normalized
        credentials = cred
        if (!configuredOnce) {
            configuredOnce = true
            return
        }
        if (changed) {
            gameCache.clear()
            focusApiResolved.clear()
            _metadata.value = emptyMap()
            _officialNames.value = emptyMap()
            _resolutionInfo.value = emptyMap()
        }
    }

    fun refreshAll(games: List<Game>, platformResolver: (Game) -> Platform?) {
        if (!NetworkStatus.isOnline(appCtx)) {
            ensureLoaded(games, platformResolver)
            return
        }
        gameCache.clear()
        focusApiResolved.clear()
        client.clearApiCache()
        _metadata.value = emptyMap()
        _officialNames.value = emptyMap()
        _resolutionInfo.value = emptyMap()
        ensureLoaded(games, platformResolver)
    }

    /** 预加载指定平台下可见游戏的元数据（先使用 DAT 与已缓存 API 结果，再按需补齐）。 */
    fun ensureLoaded(games: List<Game>, platformResolver: (Game) -> Platform?) {
        if (games.isEmpty()) return
        val enabled = providerConfigs.filter { it.enabled }
        if (enabled.isEmpty()) return
        scope.launch {
            val grouped = games.groupBy { platformResolver(it) }
            for ((platform, platGames) in grouped) {
                val system = platform?.libretroSystem
                if (!system.isNullOrBlank()) {
                    for (p in enabled) {
                        if (p.id == ScraperCatalog.MetaIds.LIBRETRO_DB_JSDELIVR ||
                            p.id == ScraperCatalog.MetaIds.LIBRETRO_DB_GITHUB
                        ) {
                            getOrFetchTable(p.id, system)
                        }
                    }
                }
                resolveGames(platGames, platform, queryApi = false)
            }
        }
    }

    private val inFlightFocus = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** 确保单个焦点游戏从全部已启用的服务商查询并合并元数据。 */
    fun resolveFocusGame(game: Game?, platform: Platform?) {
        val g = game ?: return
        if (focusApiResolved.containsKey(g.id)) return
        if (!inFlightFocus.add(g.id)) return
        val enabled = providerConfigs.filter { it.enabled }
        if (enabled.isEmpty()) {
            inFlightFocus.remove(g.id)
            return
        }
        val online = NetworkStatus.isOnline(appCtx)
        scope.launch {
            try {
                val system = platform?.libretroSystem
                if (!system.isNullOrBlank()) {
                    for (p in enabled) {
                        if (p.id == ScraperCatalog.MetaIds.LIBRETRO_DB_JSDELIVR ||
                            p.id == ScraperCatalog.MetaIds.LIBRETRO_DB_GITHUB
                        ) {
                            getOrFetchTable(p.id, system)
                        }
                    }
                }
                resolveGames(listOf(g), platform, queryApi = online)
                if (online) {
                    focusApiResolved[g.id] = true
                }
            } finally {
                inFlightFocus.remove(g.id)
            }
        }
    }

    /**
     * 强制刷新单个游戏的元数据。
     * 仅当远端/索引成功解析出至少一项有效元数据或官方名称时才覆盖本地数据，失败时保留原有本地数据。
     */
    suspend fun refreshGame(game: Game, platform: Platform?): Boolean = withContext(Dispatchers.IO) {
        if (!NetworkStatus.isOnline(appCtx)) return@withContext false
        val enabled = providerConfigs.filter { it.enabled }
        if (enabled.isEmpty()) return@withContext false
        val system = platform?.libretroSystem
        if (!system.isNullOrBlank()) {
            for (p in enabled) {
                if (p.id == ScraperCatalog.MetaIds.LIBRETRO_DB_JSDELIVR ||
                    p.id == ScraperCatalog.MetaIds.LIBRETRO_DB_GITHUB
                ) {
                    getOrFetchTable(p.id, system)
                }
            }
        }
        val res = resolveSingleGame(
            g = game,
            platform = platform,
            enabled = enabled,
            cred = credentials,
            queryApi = true,
            forceApiRefresh = true,
        )
        val hasResult = !res.meta.isEmpty || !res.officialName.isNullOrBlank()
        if (!hasResult) return@withContext false

        gameCache[game.id] = res.meta
        focusApiResolved[game.id] = true
        if (!res.meta.isEmpty) {
            _metadata.update { it + (game.id to res.meta) }
        }
        val off = res.officialName
        if (!off.isNullOrBlank()) {
            _officialNames.update { it + (game.id to off) }
        }
        res.info?.let { info ->
            _resolutionInfo.update { it + (game.id to info) }
        }
        true
    }

    private data class SingleResolution(
        val meta: GameMetadata,
        val officialName: String?,
        val info: MetadataResolutionInfo?,
    )

    private suspend fun resolveSingleGame(
        g: Game,
        platform: Platform?,
        enabled: List<SourceItemConfig>,
        cred: ScraperCredentials,
        queryApi: Boolean,
        forceApiRefresh: Boolean = false,
    ): SingleResolution {
        val system = platform?.libretroSystem
        var officialName: String? = null
        var releaseDate: String? = null
        var genre: String? = null
        var developer: String? = null
        var publisher: String? = null
        var cachedCrc: String? = null
        val usedProviders = LinkedHashSet<String>()
        val usedModes = LinkedHashSet<MetaMatchMode>()

        fun merge(providerId: String, mode: MetaMatchMode, name: String?, meta: GameMetadata) {
            var contributed = false
            if (officialName == null && !name.isNullOrBlank()) {
                officialName = name; contributed = true
            }
            if (releaseDate == null && !meta.releaseDate.isNullOrBlank()) {
                releaseDate = meta.releaseDate; contributed = true
            }
            if (genre == null && !meta.genre.isNullOrBlank()) {
                genre = meta.genre; contributed = true
            }
            if (developer == null && !meta.developer.isNullOrBlank()) {
                developer = meta.developer; contributed = true
            }
            if (publisher == null && !meta.publisher.isNullOrBlank()) {
                publisher = meta.publisher; contributed = true
            }
            if (contributed) {
                usedProviders += providerId
                usedModes += mode
            }
        }

        fun isComplete(): Boolean =
            officialName != null && releaseDate != null && genre != null && developer != null && publisher != null

        for (provider in enabled) {
            if (isComplete()) break
            when (provider.id) {
                ScraperCatalog.MetaIds.LIBRETRO_DB_JSDELIVR,
                ScraperCatalog.MetaIds.LIBRETRO_DB_GITHUB -> {
                    if (!system.isNullOrBlank()) {
                        val table = systemTables[tableCacheKey(provider.id, system)]
                        if (table != null) {
                            var hit = table.find(g.baseName, g.title, crc = cachedCrc)
                            val allowCrc = queryApi || g.id.endsWith(".zip", ignoreCase = true)
                            if (hit == null && cachedCrc == null && allowCrc) {
                                cachedCrc = client.computeRomCrc32(File(g.id))
                                if (cachedCrc != null) {
                                    hit = table.find(g.baseName, g.title, cachedCrc)
                                }
                            }
                            if (hit != null) {
                                merge(provider.id, hit.mode, hit.row.officialName, hit.row.meta)
                            }
                        }
                    }
                }
                else -> {
                    val hit = if (queryApi) {
                        client.queryProvider(provider.id, g, platform, cred, forceRefresh = forceApiRefresh)
                    } else {
                        client.cachedHit(provider.id, g.id)
                    }
                    if (hit != null && !hit.isEmpty) {
                        val apiMode = when (provider.id) {
                            ScraperCatalog.MetaIds.HASHEOUS -> MetaMatchMode.ApiHash
                            ScraperCatalog.MetaIds.ARCADEITALIA -> MetaMatchMode.ApiRomName
                            ScraperCatalog.MetaIds.SCREENSCRAPER -> {
                                if (cachedCrc == null) cachedCrc = client.computeRomCrc32(File(g.id))
                                if (cachedCrc != null) MetaMatchMode.ApiHash else MetaMatchMode.ApiNameSearch
                            }
                            else -> MetaMatchMode.ApiNameSearch
                        }
                        merge(provider.id, apiMode, hit.officialName, hit.meta)
                    }
                }
            }
        }

        val resolved = GameMetadata(
            releaseDate = releaseDate,
            genre = genre,
            developer = developer,
            publisher = publisher,
        )
        val info = if (usedProviders.isNotEmpty() || cachedCrc != null) {
            MetadataResolutionInfo(
                providers = usedProviders.toList(),
                matchModes = usedModes.toList(),
                crc32 = cachedCrc,
            )
        } else {
            null
        }
        return SingleResolution(resolved, officialName, info)
    }

    private suspend fun resolveGames(
        games: List<Game>,
        platform: Platform?,
        queryApi: Boolean,
    ) = withContext(Dispatchers.IO) {
        val enabled = providerConfigs.filter { it.enabled }
        if (enabled.isEmpty()) return@withContext
        val cred = credentials

        val updates = HashMap<String, GameMetadata>()
        val nameUpdates = HashMap<String, String>()
        val infoUpdates = HashMap<String, MetadataResolutionInfo>()

        for (g in games) {
            if (!queryApi && gameCache.containsKey(g.id)) continue
            val res = resolveSingleGame(g, platform, enabled, cred, queryApi = queryApi)
            gameCache[g.id] = res.meta
            if (!res.meta.isEmpty) {
                updates[g.id] = res.meta
            }
            val off = res.officialName
            if (!off.isNullOrBlank()) {
                nameUpdates[g.id] = off
            }
            res.info?.let { infoUpdates[g.id] = it }
        }

        if (updates.isNotEmpty()) {
            _metadata.update { it + updates }
        }
        if (nameUpdates.isNotEmpty()) {
            _officialNames.update { it + nameUpdates }
        }
        if (infoUpdates.isNotEmpty()) {
            _resolutionInfo.update { it + infoUpdates }
        }
    }

    private fun tableCacheKey(providerId: String, system: String): String = "$providerId::$system"

    private suspend fun getOrFetchTable(providerId: String, system: String): SystemTable? {
        val key = tableCacheKey(providerId, system)
        systemTables[key]?.let { return it }
        val lock = systemLocks.getOrPut(key) { Mutex() }
        return lock.withLock {
            systemTables[key]?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val prefix = if (providerId == ScraperCatalog.MetaIds.LIBRETRO_DB_JSDELIVR) "jsd" else "gh"
                val cacheFile = File(cacheDir, "v2_${prefix}_${sanitizeFileName(system)}.tsv")
                val table = if (cacheFile.isFile && cacheFile.length() > 0) {
                    readCachedTsv(cacheFile)
                } else if (NetworkStatus.isOnline(appCtx)) {
                    fetchFromLibretro(providerId, system, cacheFile)
                } else {
                    null
                }
                if (table != null) {
                    systemTables[key] = table
                }
                table
            }
        }
    }

    private suspend fun fetchFromLibretro(
        providerId: String,
        system: String,
        cacheFile: File,
    ): SystemTable? = coroutineScope {
        val encodedSys = Uri.encode(system)
        val baseUrl = if (providerId == ScraperCatalog.MetaIds.LIBRETRO_DB_JSDELIVR) {
            "https://cdn.jsdelivr.net/gh/libretro/libretro-database@master/metadat"
        } else {
            "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat"
        }
        val kinds = listOf("developer", "publisher", "genre", "releaseyear", "releasemonth")
        val parsed = kinds.map { kind ->
            async(Dispatchers.IO) {
                kind to downloadDat("$baseUrl/$kind/$encodedSys.dat")
            }
        }.awaitAll()

        if (parsed.all { it.second.isEmpty() }) return@coroutineScope null

        val merged = HashMap<String, MutableEntry>(4096)
        val byTitleTemp = HashMap<String, MutableEntry>(2048)
        for ((kind, entries) in parsed) {
            for (e in entries) {
                val target = merged.getOrPut(e.crc) { MutableEntry(comment = e.comment) }
                if (target.comment.isBlank() && e.comment.isNotBlank()) target.comment = e.comment
                when (kind) {
                    "developer" -> target.developer = e.value
                    "publisher" -> target.publisher = e.value
                    "genre" -> target.genre = e.value
                    "releaseyear" -> target.year = e.value
                    "releasemonth" -> target.month = e.value
                }
                val titleKey = normalizeTitle(e.comment)
                if (titleKey.isNotEmpty()) {
                    val agg = byTitleTemp.getOrPut(titleKey) { MutableEntry(comment = e.comment) }
                    when (kind) {
                        "developer" -> if (agg.developer == null) agg.developer = e.value
                        "publisher" -> if (agg.publisher == null) agg.publisher = e.value
                        "genre" -> if (agg.genre == null) agg.genre = e.value
                        "releaseyear" -> if (agg.year == null) agg.year = e.value
                        "releasemonth" -> if (agg.month == null) agg.month = e.value
                    }
                }
            }
        }
        if (merged.isEmpty()) return@coroutineScope null

        // 用同标题其他版本的字段补齐个别地区条目的缺失项
        for (entry in merged.values) {
            val agg = byTitleTemp[normalizeTitle(entry.comment)] ?: continue
            if (entry.developer == null) entry.developer = agg.developer
            if (entry.publisher == null) entry.publisher = agg.publisher
            if (entry.genre == null) entry.genre = agg.genre
            if (entry.year == null) entry.year = agg.year
            if (entry.month == null) entry.month = agg.month
        }

        val succeededKinds = parsed.count { it.second.isNotEmpty() }
        if (succeededKinds >= 3) {
            runCatching {
                val tmp = File(cacheDir, "${cacheFile.name}.tmp")
                tmp.bufferedWriter().use { out ->
                    for ((crc, entry) in merged) {
                        val m = entry.toMetadata()
                        if (m.isEmpty) continue
                        out.append(crc).append('\t')
                            .append(entry.comment.replace('\t', ' ')).append('\t')
                            .append((m.releaseDate ?: "").replace('\t', ' ')).append('\t')
                            .append((m.genre ?: "").replace('\t', ' ')).append('\t')
                            .append((m.developer ?: "").replace('\t', ' ')).append('\t')
                            .append((m.publisher ?: "").replace('\t', ' ')).append('\n')
                    }
                }
                tmp.renameTo(cacheFile)
            }
        }

        buildTable(merged.map { (crc, e) -> Triple(crc, e.comment, e.toMetadata()) })
    }

    private fun readCachedTsv(file: File): SystemTable? = runCatching {
        val rows = ArrayList<Triple<String, String, GameMetadata>>(2048)
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                val parts = line.split('\t')
                if (parts.size < 6) continue
                val meta = GameMetadata(
                    releaseDate = parts[2].takeIf { it.isNotEmpty() },
                    genre = parts[3].takeIf { it.isNotEmpty() },
                    developer = parts[4].takeIf { it.isNotEmpty() },
                    publisher = parts[5].takeIf { it.isNotEmpty() },
                )
                if (!meta.isEmpty) {
                    rows += Triple(parts[0], parts[1], meta)
                }
            }
        }
        if (rows.isEmpty()) null else buildTable(rows)
    }.getOrNull()

    private fun buildTable(rows: List<Triple<String, String, GameMetadata>>): SystemTable {
        val byCrc = HashMap<String, ResolvedRow>(rows.size)
        val byExact = HashMap<String, ResolvedRow>(rows.size)
        val byTitle = HashMap<String, ResolvedRow>(rows.size)
        for ((crc, comment, meta) in rows) {
            if (meta.isEmpty) continue
            val row = ResolvedRow(comment, meta)
            byCrc.putIfAbsent(crc, row)
            if (comment.isNotBlank()) {
                byExact.putIfAbsent(normalizeKey(comment), row)
                val titleKey = normalizeTitle(comment)
                if (titleKey.isNotEmpty()) {
                    val prev = byTitle[titleKey]
                    if (prev == null || fieldCount(meta) > fieldCount(prev.meta)) {
                        byTitle[titleKey] = row
                    }
                }
            }
        }
        return SystemTable(byCrc, byExact, byTitle)
    }

    private data class DatEntry(val comment: String, val value: String, val crc: String)

    private fun downloadDat(urlStr: String): List<DatEntry> {
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
            }
            code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET DAT", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "non-200")
                return emptyList()
            }
            val list = ArrayList<DatEntry>(2048)
            conn.inputStream.bufferedReader().use { reader ->
                var inGame = false
                var comment = ""
                var value = ""
                var crc = ""
                reader.forEachLine { raw ->
                    val line = raw.trim()
                    when {
                        line == "game (" -> {
                            inGame = true
                            comment = ""
                            value = ""
                            crc = ""
                        }
                        inGame && line == ")" -> {
                            if (crc.isNotEmpty() && value.isNotEmpty()) {
                                list += DatEntry(comment, value, crc.uppercase())
                            }
                            inGame = false
                        }
                        inGame && line.startsWith("comment ") -> {
                            comment = extractQuoted(line)
                        }
                        inGame && (line.startsWith("developer ") || line.startsWith("publisher ") ||
                            line.startsWith("genre ") || line.startsWith("releaseyear ") ||
                            line.startsWith("releasemonth ")) -> {
                            value = extractQuoted(line)
                        }
                        inGame && line.startsWith("rom (") -> {
                            val match = CRC_REGEX.find(line)
                            if (match != null) crc = match.groupValues[1]
                        }
                    }
                }
            }
            conn.disconnect()
            ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET DAT", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, result = "${list.size} entries")
            list
        } catch (t: Throwable) {
            ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET DAT", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}")
            emptyList()
        }
    }

    private companion object {
        val CRC_REGEX = Regex("""\bcrc\s+([0-9A-Fa-f]{8})\b""")
        val TAGS_REGEX = Regex("""\s*[(\[][^)\]]*[)\]]""")
        val NON_ALNUM = Regex("""[^a-z0-9一-鿿]+""")
        val MONTHS = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )

        fun extractQuoted(line: String): String {
            val start = line.indexOf('"')
            val end = line.lastIndexOf('"')
            return if (start >= 0 && end > start) line.substring(start + 1, end) else ""
        }

        fun normalizeKey(s: String): String =
            s.lowercase().replace('_', ' ').replace(NON_ALNUM, " ").trim()

        fun normalizeTitle(s: String): String {
            var clean = s.replace('_', ' ').replace(TAGS_REGEX, "").trim()
            val comma = clean.indexOf(", ")
            if (comma > 0) {
                val tail = clean.substring(comma + 2)
                if (tail.startsWith("The", ignoreCase = true) ||
                    tail.startsWith("A ", ignoreCase = true) ||
                    tail.equals("A", ignoreCase = true) ||
                    tail.startsWith("An ", ignoreCase = true) ||
                    tail.equals("An", ignoreCase = true)
                ) {
                    val article = tail.substringBefore(' ').substringBefore(':').substringBefore(" -")
                    val rest = tail.removePrefix(article)
                    clean = "$article ${clean.substring(0, comma)}$rest"
                }
            }
            return clean.lowercase().replace(NON_ALNUM, " ").trim()
        }

        fun formatReleaseDate(year: String?, month: String?): String? {
            val y = year?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val m = month?.trim()?.toIntOrNull()
            return if (m != null && m in 1..12) {
                "${MONTHS[m - 1]} $y"
            } else {
                y
            }
        }

        fun fieldCount(m: GameMetadata): Int =
            (if (m.releaseDate != null) 1 else 0) +
                (if (m.genre != null) 1 else 0) +
                (if (m.developer != null) 1 else 0) +
                (if (m.publisher != null) 1 else 0)

        fun sanitizeFileName(name: String): String =
            name.replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
    }
}
