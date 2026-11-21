package com.pocketdeck.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import coil3.SingletonImageLoader
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.launch.GameLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class ScrapedCoverCandidate(
    val url: String,
    val nodeId: String,
)

data class IndexCoverMatch(
    val title: String,
    val url: String,
    val fallbackUrls: List<String> = emptyList(),
)

enum class CoverMatchMode {
    CustomFile,
    LocalFile,
    IndexExact,
    IndexFuzzy,
    DirectProbe,
    SerialId,
    ApiHash,
    ApiSearch,
    AppIcon,
}

data class CoverResolutionInfo(
    val providerId: String,
    val matchMode: CoverMatchMode,
    val source: String? = null,
)

/**
 * 多节点联网封面刮削源（支持节点优先级排序、WAL SQLite 缓存、镜像故障转移、中英别名与模糊检索）。
 *
 * 优先级：
 * 1. 用户自定义封面（Edit 页面自选）
 * 2. 已命中的在线封面缓存（SQLite WAL 预加载至内存，O(1) 零主线程 I/O）
 * 3. 用户启用的封面刮削节点（Libretro Boxarts 镜像容灾 / GameTDB / Hasheous / ArcadeItalia / Titles&Snaps / SteamGridDB / ScreenScraper / TGDB / IGDB）
 * 4. 本地同名封面兜底
 */
class CoverSource(
    context: Context,
    private val scope: CoroutineScope,
    private val client: ScraperClient,
) {
    private val appCtx = context.applicationContext
    private val db = client.database
    private val indexDir = File(appCtx.cacheDir, "boxart_index").apply { mkdirs() }
    private val webCacheDir = File(appCtx.cacheDir, "web_covers").apply { mkdirs() }
    private val thumbDir = File(appCtx.cacheDir, "cover_thumbs_webp").apply { mkdirs() }
    private val thumbPathLru = LruCache<String, File>(512)
    private val thumbGenerating = ConcurrentHashMap<String, Boolean>()

    private val resolvedUrls = ConcurrentHashMap<String, String>()
    private val resolvedOrigins = ConcurrentHashMap<String, Pair<String, CoverMatchMode>>()
    private val failedUntilMs = ConcurrentHashMap<String, Long>()
    private val systemIndexes = ConcurrentHashMap<String, BoxartIndex>()
    private val systemLocks = ConcurrentHashMap<String, Mutex>()
    private val scrapingInFlight = ConcurrentHashMap<String, Boolean>()

    // 有界 LRU，避免上万游戏时候选来源映射无限增长
    private val candidateOrigins: MutableMap<String, Pair<String, CoverMatchMode>> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Pair<String, CoverMatchMode>>(512, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, Pair<String, CoverMatchMode>>?,
                ): Boolean = size > 1024
            },
        )

    /** Libretro 活跃镜像索引：0 = thumbnails.libretro.com，1 = jsDelivr，2 = GitHub Raw（仅网络/5xx 故障时自动切换）。 */
    @Volatile
    private var activeLibretroMirror: Int = 0

    @Volatile
    private var nodeConfigs: List<SourceItemConfig> = ScraperCatalog.defaultCoverConfigs()

    @Volatile
    private var credentials: ScraperCredentials = ScraperCredentials()

    @Volatile
    private var configuredOnce = false

    private val _indexVersion = MutableStateFlow(0)
    val indexVersion: StateFlow<Int> = _indexVersion.asStateFlow()

    private val _coverInfo = MutableStateFlow<Map<String, CoverResolutionInfo>>(emptyMap())
    val coverInfo: StateFlow<Map<String, CoverResolutionInfo>> = _coverInfo.asStateFlow()

    // 批量合并 _coverInfo 发射，避免每加载一张封面都整表 copy 触发下游重组
    private val pendingCoverInfoLock = Any()
    private val pendingCoverInfoPut = HashMap<String, CoverResolutionInfo>()
    private val pendingCoverInfoRemove = HashSet<String>()
    private var coverInfoFlushJob: Job? = null

    init {
        // 后台一次性加载 SQLite 封面缓存并平滑迁移旧版 cover_index_v3 SharedPreferences
        scope.launch(Dispatchers.IO) {
            migrateLegacyPrefsIfNeeded()
            val metaPrefs = appCtx.getSharedPreferences("cover_matcher_meta", Context.MODE_PRIVATE)
            if (metaPrefs.getInt("matcher_ver", 0) < MATCHER_VERSION) {
                db.clearAllCoverFailures()
                metaPrefs.edit().putInt("matcher_ver", MATCHER_VERSION).apply()
            }
            val rows = db.loadAllCovers()
            val now = System.currentTimeMillis()
            var loadedUrls = 0
            val infoBatch = HashMap<String, CoverResolutionInfo>(rows.size)
            for (r in rows) {
                if (!r.url.isNullOrBlank()) {
                    resolvedUrls[r.gameId] = r.url
                    val prov = normalizeLegacyProvider(r.provider)
                    val mode = r.matchMode?.let { runCatching { CoverMatchMode.valueOf(it) }.getOrNull() }
                    val origin = if (prov != null && mode != null) {
                        prov to mode
                    } else {
                        inferOrigin(r.url, r.url)
                    }
                    resolvedOrigins[r.gameId] = origin
                    infoBatch[r.gameId] = CoverResolutionInfo(origin.first, origin.second, r.url)
                    loadedUrls++
                } else if (r.failedUntilMs > now) {
                    failedUntilMs[r.gameId] = r.failedUntilMs
                }
            }
            if (infoBatch.isNotEmpty()) {
                _coverInfo.value = infoBatch
            }
            if (loadedUrls > 0) {
                _indexVersion.value += 1
            }
        }

        scope.launch {
            var prev = NetworkStatus.online.value
            NetworkStatus.online.collect { now ->
                if (now && !prev) {
                    failedUntilMs.clear()
                    _indexVersion.value += 1
                }
                prev = now
            }
        }
    }

    private fun migrateLegacyPrefsIfNeeded() {
        val prefs = appCtx.getSharedPreferences("cover_index_v3", Context.MODE_PRIVATE)
        val all = prefs.all
        if (all.isEmpty()) return
        val now = System.currentTimeMillis()
        val toImport = ArrayList<ScrapeDatabase.CoverRow>()
        for ((k, v) in all) {
            if (!k.startsWith("url_")) continue
            val url = (v as? String)?.takeIf { it.startsWith("http") } ?: continue
            val gameId = k.removePrefix("url_")
            val rawMeta = prefs.getString("meta_$gameId", null)
            var prov: String? = null
            var mode: String? = null
            if (!rawMeta.isNullOrBlank()) {
                val sep = rawMeta.indexOf('|')
                if (sep > 0) {
                    prov = normalizeLegacyProvider(rawMeta.substring(0, sep))
                    mode = rawMeta.substring(sep + 1)
                }
            }
            toImport += ScrapeDatabase.CoverRow(
                gameId = gameId,
                url = url,
                provider = prov,
                matchMode = mode,
                failedUntilMs = 0L,
                updatedAtMs = now,
            )
        }
        if (toImport.isNotEmpty()) {
            db.bulkImportLegacyCovers(toImport)
        }
        prefs.edit().clear().apply()
    }

    private fun normalizeLegacyProvider(raw: String?): String? = when (raw) {
        ScraperCatalog.CoverIds.LEGACY_LIBRETRO_CDN,
        ScraperCatalog.CoverIds.LEGACY_LIBRETRO_JSDELIVR,
        ScraperCatalog.CoverIds.LEGACY_LIBRETRO_GITHUB -> ScraperCatalog.CoverIds.LIBRETRO_BOXARTS
        else -> raw
    }

    private class BoxartIndex(
        val byExact: Map<String, String>,
        val byCompact: Map<String, List<String>>,
        val byToken: Map<String, List<String>>,
    ) {
        fun findMatches(queries: List<String>): List<String> =
            findMatchesWithMode(queries).map { it.first }

        fun findMatchesWithMode(queries: List<String>): List<Pair<String, CoverMatchMode>> {
            val out = LinkedHashMap<String, CoverMatchMode>()
            for (q in queries) {
                if (q.isBlank()) continue
                byExact[TitleNormalizer.exactKey(q)]?.let {
                    out.putIfAbsent(it, CoverMatchMode.IndexExact)
                }
                val compact = TitleNormalizer.compactKey(q)
                if (compact.isNotEmpty()) {
                    byCompact[compact]?.let { list ->
                        list.take(3).forEach { out.putIfAbsent(it, CoverMatchMode.IndexFuzzy) }
                    }
                }
            }
            if (out.isNotEmpty()) {
                return out.entries.map { it.key to it.value }
            }

            // 精确与紧凑键均未命中时，使用倒排词索引 + Token Jaccard/编辑距离模糊匹配
            val scored = ArrayList<Pair<String, Double>>(4)
            val evaluated = HashSet<String>(64)
            for (q in queries.take(4)) {
                val norm = TitleNormalizer.normalizedTitle(q)
                val tokens = TitleNormalizer.tokenize(norm)
                if (tokens.isEmpty()) continue
                val counts = HashMap<String, Int>(32)
                for (tok in tokens) {
                    val bucket = byToken[tok] ?: continue
                    if (bucket.size > 450) continue
                    for (fileName in bucket) {
                        counts[fileName] = (counts[fileName] ?: 0) + 1
                    }
                }
                val minOverlap = if (tokens.size <= 2) 1 else 2
                for ((fileName, cnt) in counts) {
                    if (cnt < minOverlap || !evaluated.add(fileName)) continue
                    val base = if (fileName.endsWith(".png", true)) fileName.dropLast(4) else fileName
                    val sim = TitleNormalizer.similarity(q, base)
                    if (sim >= 0.78) {
                        scored += fileName to sim
                    }
                }
            }
            if (scored.isNotEmpty()) {
                scored.sortWith(
                    compareByDescending<Pair<String, Double>> { it.second }
                        .thenBy { candidateScore(it.first) },
                )
                scored.take(3).forEach { (fn, _) ->
                    out.putIfAbsent(fn, CoverMatchMode.IndexFuzzy)
                }
            }
            return out.entries.map { it.key to it.value }
        }

        /** 手动搜索专用：先走精确/模糊匹配，不足时补充「关键词包含匹配」（按紧凑键去重，避免同游戏多区域刷屏）。 */
        fun findManualMatches(queries: List<String>, limit: Int = 12): List<String> {
            val picked = LinkedHashMap<String, String>(limit)
            for ((fn, _) in findMatchesWithMode(queries)) {
                val base = if (fn.endsWith(".png", true)) fn.dropLast(4) else fn
                val ck = TitleNormalizer.compactKey(base).ifEmpty { fn }
                picked.putIfAbsent(ck, fn)
                if (picked.size >= limit) return picked.values.toList()
            }

            val queryCompacts = queries
                .map { TitleNormalizer.compactKey(it) }
                .filter { it.length >= 3 }
                .distinct()
            val queryTokenSets = queries
                .map { TitleNormalizer.tokenize(TitleNormalizer.normalizedTitle(it)) }
                .filter { it.isNotEmpty() }
                .distinct()
            if (queryCompacts.isEmpty() && queryTokenSets.isEmpty()) {
                return picked.values.toList()
            }

            val containsScored = ArrayList<Triple<String, String, Double>>(24)
            for ((ck, files) in byCompact) {
                if (ck in picked || files.isEmpty()) continue
                val bestFile = files.first()
                val base = if (bestFile.endsWith(".png", true)) bestFile.dropLast(4) else bestFile
                val compactHit = queryCompacts.any { ck.contains(it) }
                val tokenHit = if (compactHit) {
                    true
                } else {
                    val titleTokens = TitleNormalizer.tokenize(TitleNormalizer.normalizedTitle(base)).toSet()
                    queryTokenSets.any { qToks -> qToks.all { qt -> titleTokens.any { it == qt || (qt.length >= 3 && it.contains(qt)) } } }
                }
                if (tokenHit) {
                    val bestSim = queries.maxOfOrNull { q -> TitleNormalizer.similarity(q, base) } ?: 0.0
                    containsScored += Triple(ck, bestFile, bestSim)
                }
            }

            if (containsScored.isNotEmpty()) {
                containsScored.sortWith(
                    compareByDescending<Triple<String, String, Double>> { it.third }
                        .thenBy { candidateScore(it.second) },
                )
                for ((ck, fn, _) in containsScored) {
                    picked.putIfAbsent(ck, fn)
                    if (picked.size >= limit) break
                }
            }
            return picked.values.toList()
        }

        /** 纯内存快速补全建议：按输入前缀/子串从当前平台索引中提取去标签的干净英文游戏名。 */
        fun suggestTitles(query: String, limit: Int = 8): List<String> {
            val qCompact = TitleNormalizer.compactKey(query)
            if (qCompact.length < 2) return emptyList()
            val out = LinkedHashSet<String>(limit)
            val prefixHits = ArrayList<String>()
            val containsHits = ArrayList<String>()
            for ((ck, files) in byCompact) {
                if (files.isEmpty()) continue
                if (ck == qCompact) continue
                val isPrefix = ck.startsWith(qCompact)
                if (!isPrefix && !ck.contains(qCompact)) continue
                val bestFile = files.first()
                val base = if (bestFile.endsWith(".png", true)) bestFile.dropLast(4) else bestFile
                val clean = TitleNormalizer.stripRomTags(base)
                if (clean.isBlank() || clean.equals(query.trim(), ignoreCase = true)) continue
                if (isPrefix) prefixHits += clean else containsHits += clean
            }
            prefixHits.sortBy { it.length }
            containsHits.sortBy { it.length }
            for (t in prefixHits) {
                out += t
                if (out.size >= limit) return out.toList()
            }
            for (t in containsHits) {
                out += t
                if (out.size >= limit) break
            }
            return out.toList()
        }
    }

    fun configure(nodes: List<SourceItemConfig>, cred: ScraperCredentials) {
        val normalized = ScraperCatalog.normalizeCoverConfigs(nodes)
        val changed = normalized != nodeConfigs || cred != credentials
        nodeConfigs = normalized
        credentials = cred
        if (!configuredOnce) {
            configuredOnce = true
            return
        }
        if (changed) {
            resolvedUrls.clear()
            resolvedOrigins.clear()
            failedUntilMs.clear()
            candidateOrigins.clear()
            clearPendingCoverInfo()
            _coverInfo.value = emptyMap()
            scope.launch(Dispatchers.IO) { db.clearCovers() }
            _indexVersion.value += 1
        }
    }

    /** 清空全部封面缓存：内存瞬时生效，磁盘/SQLite/Coil 缓存清理全异步在 IO 线程执行（绝不卡主线程）。 */
    fun clearCoverCache() {
        resolvedUrls.clear()
        resolvedOrigins.clear()
        failedUntilMs.clear()
        candidateOrigins.clear()
        systemIndexes.clear()
        thumbPathLru.evictAll()
        clearPendingCoverInfo()
        _coverInfo.value = emptyMap()
        runCatching { SingletonImageLoader.get(appCtx).memoryCache?.clear() }
        _indexVersion.value += 1

        scope.launch(Dispatchers.IO) {
            db.clearCovers()
            runCatching { webCacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            runCatching { thumbDir.listFiles()?.forEach { it.deleteRecursively() } }
            runCatching { indexDir.listFiles()?.forEach { it.deleteRecursively() } }
            runCatching { SingletonImageLoader.get(appCtx).diskCache?.clear() }
            runCatching { File(appCtx.cacheDir, "covers").listFiles()?.forEach { it.deleteRecursively() } }
            client.clearApiCache()
        }
    }

    /**
     * 当元数据仓库新解析出官方名称（例如通过 CRC32 / Hasheous / GameTDB 识别中文汉化 ROM）时，
     * 清除对应游戏先前的负缓存死锁并通知界面重新生成候选封面。
     */
    fun onOfficialNamesResolved(gameIds: Collection<String>) {
        if (gameIds.isEmpty()) return
        val unlocked = ArrayList<String>()
        for (id in gameIds) {
            val cached = resolvedUrls[id]
            if (!cached.isNullOrEmpty()) continue
            if (failedUntilMs.remove(id) != null) {
                unlocked += id
            }
        }
        if (unlocked.isNotEmpty()) {
            scope.launch(Dispatchers.IO) { db.clearCoverFailures(unlocked) }
            _indexVersion.value += 1
        }
    }

    /** 纯内存实时补全建议：结合本地中英别名表与当前平台已加载的 Libretro 索引（零网络、零阻塞）。 */
    fun suggestManualKeywords(keyword: String, platform: Platform?, limit: Int = 8): List<String> {
        val q = keyword.trim()
        if (q.length < 2) return emptyList()
        val out = LinkedHashSet<String>(limit)
        if (CjkAliases.containsCjk(q)) {
            for (s in CjkAliases.suggestKeywords(q, limit)) {
                out += s
                if (out.size >= limit) return out.toList()
            }
        } else {
            val index = platform?.libretroSystem?.let { systemIndexes[it] }
            if (index != null) {
                for (s in index.suggestTitles(q, limit)) {
                    out += s
                    if (out.size >= limit) return out.toList()
                }
            }
            for (s in CjkAliases.suggestKeywords(q, limit)) {
                out += s
                if (out.size >= limit) break
            }
        }
        return out.toList()
    }

    /** 手动搜索刮削：按关键词检索当前平台的 Libretro Boxart 索引，返回含三镜像兜底的候选列表。 */
    suspend fun searchIndexCandidates(keyword: String, platform: Platform?): List<IndexCoverMatch> {
        val system = platform?.libretroSystem ?: return emptyList()
        if (!systemIndexes.containsKey(system)) {
            loadSystemIndex(system)
        }
        val index = systemIndexes[system] ?: return emptyList()
        val queries = TitleNormalizer.buildQueryVariants(keyword, keyword, null)
        val matches = index.findManualMatches(queries, limit = 12)
        val primary = activeLibretroMirror
        val fb1 = (primary + 1) % 3
        val fb2 = (primary + 2) % 3
        return matches.map { fileName ->
            val cleanName = if (fileName.endsWith(".png", true)) fileName.dropLast(4) else fileName
            IndexCoverMatch(
                title = cleanName,
                url = buildLibretroBoxartUrl(system, fileName, primary),
                fallbackUrls = listOf(
                    buildLibretroBoxartUrl(system, fileName, fb1),
                    buildLibretroBoxartUrl(system, fileName, fb2),
                ),
            )
        }
    }

    /** 应用手动搜索刮削选中的封面 URL 并持久化至 SQLite 缓存。 */
    fun applyManualCover(gameId: String, coverUrl: String?, providerId: String) {
        if (coverUrl.isNullOrBlank()) return
        resolvedUrls[gameId] = coverUrl
        resolvedOrigins[gameId] = providerId to CoverMatchMode.ApiSearch
        failedUntilMs.remove(gameId)
        scope.launch(Dispatchers.IO) {
            db.putCoverSuccess(gameId, coverUrl, providerId, CoverMatchMode.ApiSearch.name)
        }
        _coverInfo.value = _coverInfo.value + (gameId to CoverResolutionInfo(providerId, CoverMatchMode.ApiSearch, coverUrl))
        _indexVersion.value += 1
    }

    /** 预加载指定平台的在线封面目录索引（首次联网拉取，之后读本地缓存）。 */
    fun ensureIndexLoaded(platform: Platform?) {
        val system = platform?.libretroSystem ?: return
        val needIndex = nodeConfigs.any {
            it.enabled && (
                it.id == ScraperCatalog.CoverIds.LIBRETRO_BOXARTS ||
                    it.id == ScraperCatalog.CoverIds.LIBRETRO_SNAPS
                )
        }
        if (!needIndex || systemIndexes.containsKey(system)) return
        scope.launch {
            val loaded = loadSystemIndex(system)
            if (loaded) {
                failedUntilMs.clear()
                _indexVersion.value += 1
            }
        }
    }

    /** 为指定游戏异步触发 API / 索引型封面节点查询（GameTDB / Hasheous / ArcadeItalia / SteamGridDB / ScreenScraper / TGDB / IGDB）。 */
    fun ensureGameScraped(game: Game, platform: Platform?, officialName: String? = null) {
        // 本地优先：已有自定义封面或本地 ES-DE/Daijisho/同名封面时零网络直出
        if (!game.customCover.isNullOrBlank() || !game.localCover.isNullOrBlank()) return
        if (!NetworkStatus.isOnline(appCtx)) return
        val enabledApiNodes = nodeConfigs.filter { it.enabled && it.id in API_COVER_NODES }
        if (enabledApiNodes.isEmpty()) return
        val cred = credentials
        val needNodes = enabledApiNodes.filter { node ->
            if (node.id == ScraperCatalog.CoverIds.GAMETDB && !client.gameTdb.supportsPlatform(platform?.id)) {
                return@filter false
            }
            val desc = ScraperCatalog.coverDescriptor(node.id)
            if (!ScraperCatalog.hasCredentials(desc?.credentialKind, cred)) return@filter false
            val key = "${node.id}::${game.id}"
            !client.hasFreshCacheInMemory(node.id, game.id) && scrapingInFlight.putIfAbsent(key, true) == null
        }
        if (needNodes.isEmpty()) return

        scope.launch {
            var gotCover = false
            withContext(Dispatchers.IO) {
                for (node in needNodes) {
                    val key = "${node.id}::${game.id}"
                    try {
                        val existing = client.cachedHit(node.id, game.id)
                        if (existing != null) {
                            if (existing.coverUrls.isNotEmpty()) gotCover = true
                            continue
                        }
                        val hit = client.queryProvider(
                            providerId = node.id,
                            game = game,
                            platform = platform,
                            cred = cred,
                            officialNameHint = officialName,
                        )
                        if (hit != null && hit.coverUrls.isNotEmpty()) {
                            gotCover = true
                        }
                    } finally {
                        scrapingInFlight.remove(key)
                    }
                }
            }
            if (gotCover) {
                failedUntilMs.remove(game.id)
                _indexVersion.value += 1
            }
        }
    }

    private suspend fun loadSystemIndex(system: String): Boolean {
        if (systemIndexes.containsKey(system)) return false
        val lock = systemLocks.getOrPut(system) { Mutex() }
        return lock.withLock {
            if (systemIndexes.containsKey(system)) return@withLock false
            withContext(Dispatchers.IO) {
                val cacheFile = File(indexDir, "${sanitizeFileName(system)}.txt")
                val names = if (cacheFile.isFile && cacheFile.length() > 0) {
                    runCatching { cacheFile.readLines().filter { it.isNotBlank() } }.getOrNull().orEmpty()
                } else if (NetworkStatus.isOnline(appCtx)) {
                    fetchOnlineBoxartList(system, cacheFile)
                } else {
                    emptyList()
                }
                if (names.isNotEmpty()) {
                    systemIndexes[system] = buildIndex(names)
                    true
                } else {
                    false
                }
            }
        }
    }

    /**
     * 快速滚动期间使用的纯内存 O(1) 候选封面查询：仅返回 custom / 已缓存命中的 URL / local，
     * 不做任何正则表达式、索引查找或联网候选拼装，确保主线程零卡顿。
     */
    fun fastMemoryCandidates(game: Game): List<Any> {
        val custom = game.customCover
        val local = game.localCover
        val cached = cachedUrl(game.id)
        if (custom == null && cached == null && local == null) return emptyList()
        val out = ArrayList<Any>(3)
        if (custom != null) out += preferredThumbOrOriginal(File(custom))
        if (local != null && local != custom) out += preferredThumbOrOriginal(File(local))
        if (cached != null) out += cached
        return out
    }

    fun candidates(
        game: Game,
        platform: Platform?,
        officialName: String? = null,
    ): List<Any> {
        // O(1) 快路径：1. 用户自定义封面 → 2. 本地媒体封面（零网络优先命中） → 3. 已解析命中的在线缓存
        game.customCover?.let { custom ->
            val f = preferredThumbOrOriginal(File(custom))
            candidateOrigins["${game.id}::$f"] = "custom" to CoverMatchMode.CustomFile
            return listOf(f)
        }
        game.localCover?.let { local ->
            val f = preferredThumbOrOriginal(File(local))
            candidateOrigins["${game.id}::$f"] = "local" to CoverMatchMode.LocalFile
            return listOf(f)
        }
        val cached = cachedUrl(game.id)
        if (cached != null) {
            val (prov, mode) = cachedOrigin(game.id, cached)
            candidateOrigins["${game.id}::$cached"] = prov to mode
            return listOf(cached)
        }

        val out = ArrayList<Any>(12)
        val seen = HashSet<String>()

        fun addCandidate(model: Any, providerId: String, mode: CoverMatchMode) {
            val key = model.toString()
            if (seen.add(key)) {
                out += model
                candidateOrigins["${game.id}::$key"] = providerId to mode
            }
        }

        fun addUrl(url: String, providerId: String, mode: CoverMatchMode) {
            addCandidate(url, providerId, mode)
        }

        val online = NetworkStatus.online.value
        val enabled = nodeConfigs.filter { it.enabled }
        val system = platform?.libretroSystem
        val index = system?.let { systemIndexes[it] }
        val queries = TitleNormalizer.buildQueryVariants(game.baseName, game.title, officialName)
        val indexMatches = if (online) index?.findMatchesWithMode(queries).orEmpty() else emptyList()
        // 仅当该平台 BoxartIndex 尚未加载时才生成少量 DirectProbe，索引已加载时绝不盲猜 404
        val directNames = if (online && index == null) buildDirectNames(game, officialName) else emptyList()

        for (node in enabled) {
            when (node.id) {
                ScraperCatalog.CoverIds.LIBRETRO_BOXARTS -> if (online && system != null) {
                    val primary = activeLibretroMirror
                    val fallback1 = (primary + 1) % 3
                    val fallback2 = (primary + 2) % 3
                    indexMatches.forEach { (fileName, mode) ->
                        addUrl(buildLibretroBoxartUrl(system, fileName, primary), node.id, mode)
                    }
                    // 主镜像宕机或 404 时，为最匹配候选自动追加其余两个镜像兜底（命中后会切换全局活跃镜像）
                    indexMatches.take(2).forEach { (fileName, mode) ->
                        addUrl(buildLibretroBoxartUrl(system, fileName, fallback1), node.id, mode)
                        addUrl(buildLibretroBoxartUrl(system, fileName, fallback2), node.id, mode)
                    }
                    directNames.take(6).forEach { n ->
                        addUrl(buildLibretroBoxartUrl(system, "$n.png", primary), node.id, CoverMatchMode.DirectProbe)
                    }
                }
                ScraperCatalog.CoverIds.LIBRETRO_SNAPS -> if (online && system != null) {
                    val mirror = activeLibretroMirror
                    indexMatches.take(2).forEach { (fileName, mode) ->
                        addUrl(buildLibretroThumbUrl(system, "Named_Titles", fileName, mirror), node.id, mode)
                        addUrl(buildLibretroThumbUrl(system, "Named_Snaps", fileName, mirror), node.id, mode)
                    }
                }
                ScraperCatalog.CoverIds.GAMETDB -> {
                    val hit = client.cachedHitInMemory(node.id, game.id)
                        ?: client.gameTdb.lookupInMemory(game, platform)?.hit
                    hit?.coverUrls?.forEach {
                        val mode = if (hit.matchedBySerial) CoverMatchMode.SerialId else CoverMatchMode.IndexFuzzy
                        addUrl(it, node.id, mode)
                    }
                }
                ScraperCatalog.CoverIds.HASHEOUS -> {
                    client.cachedHitInMemory(node.id, game.id)?.coverUrls?.forEach {
                        addUrl(it, node.id, CoverMatchMode.ApiHash)
                    }
                }
                ScraperCatalog.CoverIds.SCREENSCRAPER -> {
                    val hit = client.cachedHitInMemory(node.id, game.id)
                    hit?.coverUrls?.forEach {
                        val mode = if (hit.matchedBySerial) CoverMatchMode.SerialId else CoverMatchMode.ApiHash
                        addUrl(it, node.id, mode)
                    }
                }
                ScraperCatalog.CoverIds.ARCADEITALIA,
                ScraperCatalog.CoverIds.STEAMGRIDDB,
                ScraperCatalog.CoverIds.THEGAMESDB,
                ScraperCatalog.CoverIds.IGDB -> {
                    client.cachedHitInMemory(node.id, game.id)?.coverUrls?.forEach {
                        addUrl(it, node.id, CoverMatchMode.ApiSearch)
                    }
                }
            }
        }

        game.localCover?.let { addCandidate(File(it), "local", CoverMatchMode.LocalFile) }
        return out
    }

    /** Edit 页面「在线抓取封面」：按优先级向全部已启用节点实时查询并返回验证通过的候选封面。 */
    suspend fun scrapeAllForPicker(
        game: Game,
        platform: Platform?,
        officialName: String? = null,
    ): List<ScrapedCoverCandidate> = coroutineScope {
        if (!NetworkStatus.isOnline(appCtx)) return@coroutineScope emptyList()
        val enabled = nodeConfigs.filter { it.enabled }
        val cred = credentials
        val system = platform?.libretroSystem
        if (system != null) {
            loadSystemIndex(system)
        }

        val apiResults = enabled.filter { it.id in API_COVER_NODES }.map { node ->
            async(Dispatchers.IO) {
                val hit = client.queryProvider(
                    providerId = node.id,
                    game = game,
                    platform = platform,
                    cred = cred,
                    officialNameHint = officialName,
                )
                node.id to hit?.coverUrls.orEmpty()
            }
        }.awaitAll().toMap()

        val out = ArrayList<ScrapedCoverCandidate>()
        val seen = HashSet<String>()
        fun push(url: String, nodeId: String) {
            if (seen.add(url)) out += ScrapedCoverCandidate(url, nodeId)
        }

        val index = system?.let { systemIndexes[it] }
        val queries = TitleNormalizer.buildQueryVariants(game.baseName, game.title, officialName)
        val indexMatches = index?.findMatches(queries).orEmpty()
            .ifEmpty { index?.findManualMatches(queries, limit = 6).orEmpty() }
        val directNames = if (index == null) buildDirectNames(game, officialName) else emptyList()

        // 若当前游戏已有命中的在线封面缓存，优先放入首位，确保「在线选图」不会反而返回空
        cachedUrl(game.id)?.let { cached ->
            push(cached, cachedOrigin(game.id, cached).first)
        }

        for (node in enabled) {
            when (node.id) {
                ScraperCatalog.CoverIds.LIBRETRO_BOXARTS -> if (system != null) {
                    val mirror = activeLibretroMirror
                    indexMatches.take(6).forEach {
                        push(buildLibretroBoxartUrl(system, it, mirror), node.id)
                    }
                    if (indexMatches.isEmpty()) {
                        directNames.take(3).forEach {
                            push(buildLibretroBoxartUrl(system, "$it.png", mirror), node.id)
                        }
                    }
                }
                ScraperCatalog.CoverIds.LIBRETRO_SNAPS -> if (system != null) {
                    val mirror = activeLibretroMirror
                    indexMatches.take(2).forEach {
                        push(buildLibretroThumbUrl(system, "Named_Titles", it, mirror), node.id)
                        push(buildLibretroThumbUrl(system, "Named_Snaps", it, mirror), node.id)
                    }
                }
                else -> {
                    apiResults[node.id].orEmpty().forEach { push(it, node.id) }
                }
            }
        }
        // 并行验证候选图片 URL 确实可访问；Libretro 主镜像失败时自动尝试其余两个镜像兜底
        out.map { item ->
            async(Dispatchers.IO) {
                val workingUrl = resolveReachableUrl(item.url)
                workingUrl?.let { item.copy(url = it) }
            }
        }.awaitAll().filterNotNull().distinctBy { it.url }
    }

    private fun resolveReachableUrl(urlStr: String): String? {
        if (urlExists(urlStr)) return urlStr
        val altUrls = libretroAlternativeMirrors(urlStr)
        for ((mirrorIdx, alt) in altUrls) {
            if (urlExists(alt)) {
                activeLibretroMirror = mirrorIdx
                return alt
            }
        }
        return null
    }

    /** 若 [urlStr] 是 Libretro 任一镜像图片链接，返回其余两个镜像的等价链接列表 `(mirrorIdx, url)`。 */
    private fun libretroAlternativeMirrors(urlStr: String): List<Pair<Int, String>> {
        val m0 = Regex("""^https://thumbnails\.libretro\.com/([^/]+)/(Named_[^/]+)/(.+)$""").matchEntire(urlStr)
        val m1 = Regex("""^https://cdn\.jsdelivr\.net/gh/libretro-thumbnails/([^/@]+)@master/(Named_[^/]+)/(.+)$""").matchEntire(urlStr)
        val m2 = Regex("""^https://raw\.githubusercontent\.com/libretro-thumbnails/([^/]+)/master/(Named_[^/]+)/(.+)$""").matchEntire(urlStr)
        val (curMirror, rawSys, kind, encodedFile) = when {
            m0 != null -> listOf("0", URLDecoder.decode(m0.groupValues[1], "UTF-8"), m0.groupValues[2], m0.groupValues[3])
            m1 != null -> listOf("1", URLDecoder.decode(m1.groupValues[1], "UTF-8").replace('_', ' '), m1.groupValues[2], m1.groupValues[3])
            m2 != null -> listOf("2", URLDecoder.decode(m2.groupValues[1], "UTF-8").replace('_', ' '), m2.groupValues[2], m2.groupValues[3])
            else -> return emptyList()
        }
        val fileName = runCatching { URLDecoder.decode(encodedFile.replace("+", "%2B"), "UTF-8") }.getOrDefault(encodedFile)
        val cur = curMirror.toInt()
        return listOf((cur + 1) % 3, (cur + 2) % 3).map { idx ->
            idx to buildLibretroThumbUrl(rawSys, kind, fileName, idx)
        }
    }

    private fun urlExists(urlStr: String): Boolean {
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val req = Request.Builder()
                .url(urlStr)
                .head()
                .header("User-Agent", USER_AGENT)
                .build()
            client.http.newCall(req).execute().use { resp ->
                code = resp.code
                val ct = resp.header("Content-Type").orEmpty().lowercase()
                val ok = resp.isSuccessful && (ct.isEmpty() || ct.startsWith("image/") || ct == "application/octet-stream")
                ScrapeLogStore.log(
                    "COVER",
                    ScrapeLogStore.nodeFromUrl(urlStr),
                    "HEAD",
                    url = urlStr,
                    status = code,
                    elapsedMs = System.currentTimeMillis() - t0,
                    result = if (ok) "hit ($ct)" else "miss ($ct)",
                )
                ok
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log(
                "COVER",
                ScrapeLogStore.nodeFromUrl(urlStr),
                "HEAD",
                url = urlStr,
                status = code,
                elapsedMs = System.currentTimeMillis() - t0,
                error = "${t.javaClass.simpleName}: ${t.message}",
            )
            false
        }
    }

    /** 0 表示已有缓存/自定义可直接尝试首项；-1 表示当前负缓存窗口内已全部失败；null 表示正常按序试探。 */
    fun knownIndex(key: String, platform: Platform?): Int? {
        if (cachedUrl(key) != null) return 0
        val failUntil = failedUntilMs[key] ?: return null
        if (failUntil <= System.currentTimeMillis()) {
            failedUntilMs.remove(key)
            return null
        }
        val system = platform?.libretroSystem
        val allApiCached = nodeConfigs.filter { it.enabled && it.id in API_COVER_NODES }
            .all { node ->
                val desc = ScraperCatalog.coverDescriptor(node.id)
                !ScraperCatalog.hasCredentials(desc?.credentialKind, credentials) ||
                    (node.id == ScraperCatalog.CoverIds.GAMETDB && !client.gameTdb.supportsPlatform(platform?.id)) ||
                    client.hasFreshCacheInMemory(node.id, key)
            }
        if (allApiCached && (system == null || systemIndexes.containsKey(system))) {
            return -1
        }
        return null
    }

    fun rememberSuccess(key: String, model: Any?) {
        val wasFailed = failedUntilMs.remove(key) != null
        val modelStr = model?.toString()
        val origin = modelStr?.let { candidateOrigins["$key::$it"] }
            ?: modelStr?.let { inferOrigin(it, model) }
        if (origin != null) {
            resolvedOrigins[key] = origin
            scheduleCoverInfoPut(key, CoverResolutionInfo(origin.first, origin.second, modelStr))
        }
        val httpUrl = (model as? String)?.takeIf { it.startsWith("http") }
        // 若 Libretro 备用镜像成功出图，自动将全局活跃镜像切至该可用镜像，减少后续卡片 404 等待
        if (httpUrl != null) {
            val hitMirror = when {
                "cdn.jsdelivr.net/gh/libretro-thumbnails/" in httpUrl -> 1
                "raw.githubusercontent.com/libretro-thumbnails/" in httpUrl -> 2
                "thumbnails.libretro.com/" in httpUrl -> 0
                else -> -1
            }
            if (hitMirror >= 0 && hitMirror != activeLibretroMirror) {
                activeLibretroMirror = hitMirror
            }
        }
        val urlChanged = if (httpUrl != null) resolvedUrls.put(key, httpUrl) != httpUrl else false
        if ((wasFailed || urlChanged) && httpUrl != null) {
            scope.launch(Dispatchers.IO) {
                db.putCoverSuccess(key, httpUrl, origin?.first, origin?.second?.name)
            }
        }
    }

    fun rememberFailure(key: String, platform: Platform?) {
        val hadCachedUrl = resolvedUrls.remove(key)?.isNotEmpty() == true
        if (hadCachedUrl) {
            resolvedOrigins.remove(key)
            scheduleCoverInfoRemove(key)
            scope.launch(Dispatchers.IO) { db.deleteCover(key) }
            _indexVersion.value += 1
            return
        }
        scheduleCoverInfoRemove(key)
        if (!NetworkStatus.online.value) return
        val now = System.currentTimeMillis()
        val prevUntil = failedUntilMs[key]
        if (prevUntil != null && prevUntil > now) return

        val system = platform?.libretroSystem
        val allApiCached = nodeConfigs.filter { it.enabled && it.id in API_COVER_NODES }
            .all { node ->
                val desc = ScraperCatalog.coverDescriptor(node.id)
                !ScraperCatalog.hasCredentials(desc?.credentialKind, credentials) ||
                    (node.id == ScraperCatalog.CoverIds.GAMETDB && !client.gameTdb.supportsPlatform(platform?.id)) ||
                    client.hasFreshCacheInMemory(node.id, key)
            }
        val ttl = if (allApiCached && (system == null || systemIndexes.containsKey(system))) {
            ScrapeDatabase.TTL_MISS_MS
        } else {
            ScrapeDatabase.TTL_ERROR_MS
        }
        val until = now + ttl
        failedUntilMs[key] = until
        if (ttl == ScrapeDatabase.TTL_MISS_MS) {
            scope.launch(Dispatchers.IO) { db.putCoverFailure(key, until) }
        }
    }

    fun forget(key: String) {
        resolvedUrls.remove(key)
        resolvedOrigins.remove(key)
        failedUntilMs.remove(key)
        scheduleCoverInfoRemove(key)
        scope.launch(Dispatchers.IO) {
            db.deleteCover(key)
            runCatching { webCacheFile(key).delete() }
        }
    }

    private fun scheduleCoverInfoPut(key: String, info: CoverResolutionInfo) {
        if (_coverInfo.value[key] == info) return
        synchronized(pendingCoverInfoLock) {
            pendingCoverInfoRemove.remove(key)
            pendingCoverInfoPut[key] = info
            scheduleCoverInfoFlushLocked()
        }
    }

    private fun scheduleCoverInfoRemove(key: String) {
        if (!_coverInfo.value.containsKey(key)) return
        synchronized(pendingCoverInfoLock) {
            pendingCoverInfoPut.remove(key)
            pendingCoverInfoRemove.add(key)
            scheduleCoverInfoFlushLocked()
        }
    }

    private fun clearPendingCoverInfo() {
        synchronized(pendingCoverInfoLock) {
            pendingCoverInfoPut.clear()
            pendingCoverInfoRemove.clear()
            coverInfoFlushJob?.cancel()
            coverInfoFlushJob = null
        }
    }

    private fun scheduleCoverInfoFlushLocked() {
        if (coverInfoFlushJob?.isActive == true) return
        coverInfoFlushJob = scope.launch {
            delay(150L)
            val puts: Map<String, CoverResolutionInfo>
            val removes: Set<String>
            synchronized(pendingCoverInfoLock) {
                puts = HashMap(pendingCoverInfoPut)
                removes = HashSet(pendingCoverInfoRemove)
                pendingCoverInfoPut.clear()
                pendingCoverInfoRemove.clear()
                coverInfoFlushJob = null
            }
            if (puts.isEmpty() && removes.isEmpty()) return@launch
            val cur = _coverInfo.value
            val next = HashMap<String, CoverResolutionInfo>(cur.size + puts.size)
            next.putAll(cur)
            for (k in removes) next.remove(k)
            next.putAll(puts)
            _coverInfo.value = next
        }
    }

    fun resolveCoverInfo(game: Game): CoverResolutionInfo? {
        _coverInfo.value[game.id]?.let { return it }
        game.customCover?.let { return CoverResolutionInfo("custom", CoverMatchMode.CustomFile, it) }
        val cached = cachedUrl(game.id)
        if (cached != null) {
            val (prov, mode) = cachedOrigin(game.id, cached)
            return CoverResolutionInfo(prov, mode, cached)
        }
        game.localCover?.let { return CoverResolutionInfo("local", CoverMatchMode.LocalFile, it) }
        return null
    }

    /** 判断掌机本地或已缓存索引中是否存在该游戏的封面（供 Web 列表快速标记）。 */
    fun hasLocalOrCachedCover(game: Game): Boolean {
        if (!game.customCover.isNullOrBlank() && File(game.customCover).isFile) return true
        if (!game.localCover.isNullOrBlank() && File(game.localCover).isFile) return true
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) return true
        val url = cachedUrl(game.id)
        if (!url.isNullOrBlank()) return true
        return webCacheFile(game.id).isFile
    }

    /** 若当前游戏生效的是在线封面 URL（未被本地/自定义封面覆盖），返回该 URL 供 Web 端直连，减轻掌机流量压力。 */
    fun publicCoverUrlForWeb(game: Game): String? {
        if (!game.customCover.isNullOrBlank() || !game.localCover.isNullOrBlank()) return null
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) return null
        return cachedUrl(game.id)
    }

    /**
     * 为 Web 端生成单游戏级封面版本号：
     * 基于当前生效的封面文件 mtime/size（或在线 URL 哈希 / APK 更新时间），
     * 保证掌机本地换图或 Web 端改图时仅该游戏缓存立即失效，其余未改动封面继续命中浏览器强缓存。
     */
    fun coverVersionForWeb(game: Game): String {
        val custom = game.customCover?.let(::File)?.takeIf { it.isFile }
        if (custom != null) return "c-${custom.lastModified()}-${custom.length()}"
        val local = game.localCover?.let(::File)?.takeIf { it.isFile }
        if (local != null) return "l-${local.lastModified()}-${local.length()}"
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) {
            val pkg = game.id.removePrefix(GameLauncher.APK_PREFIX)
            val updated = runCatching { appCtx.packageManager.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
            return "a-$updated"
        }
        val url = cachedUrl(game.id)
        val wf = webCacheFile(game.id)
        val uPart = if (!url.isNullOrBlank()) url.hashCode().toUInt().toString(16) else "0"
        val wPart = if (wf.isFile) "${wf.lastModified()}-${wf.length()}" else "0"
        return if (uPart != "0" || wPart != "0") "u-$uPart-$wPart" else "0"
    }

    /**
     * 仅从掌机本地文件、应用图标、Coil 磁盘缓存或掌机本地 Web 缓存读取封面字节（不触发外部网络，Web 端绝不直连外网）。
     */
    suspend fun resolveWebCoverBytes(game: Game): Pair<String, ByteArray>? = withContext(Dispatchers.IO) {
        val custom = game.customCover?.let(::File)?.takeIf { it.isFile && it.canRead() }
        if (custom != null) {
            val f = preferredThumbOrOriginal(custom).takeIf { it.isFile && it.canRead() } ?: custom
            return@withContext mimeForFile(f) to f.readBytes()
        }
        val local = game.localCover?.let(::File)?.takeIf { it.isFile && it.canRead() }
        if (local != null) {
            val f = preferredThumbOrOriginal(local).takeIf { it.isFile && it.canRead() } ?: local
            return@withContext mimeForFile(f) to f.readBytes()
        }
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) {
            val pkg = game.id.removePrefix(GameLauncher.APK_PREFIX)
            val pngBytes = runCatching {
                val bmp = appCtx.packageManager.getApplicationIcon(pkg).toBitmap(192, 192)
                ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
            }.getOrNull()
            if (pngBytes != null && pngBytes.isNotEmpty()) {
                return@withContext "image/png" to pngBytes
            }
        }

        val cachedFile = webCacheFile(game.id)
        if (cachedFile.isFile && cachedFile.length() > 0L) {
            val bytes = runCatching { cachedFile.readBytes() }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                return@withContext detectImageMime(bytes) to bytes
            }
        }

        val url = cachedUrl(game.id) ?: return@withContext null
        readCoilDiskCache(url)?.let { bytes ->
            runCatching { cachedFile.writeBytes(bytes) }
            return@withContext detectImageMime(bytes) to bytes
        }

        if (NetworkStatus.isOnline(appCtx)) {
            val downloaded = downloadImageBytes(url)
            if (downloaded != null) {
                runCatching { cachedFile.writeBytes(downloaded.second) }
                return@withContext downloaded
            }
        }
        null
    }

    /** 由 Web 管理端触发掌机联网抓取并更新该游戏封面。 */
    suspend fun fetchWebCoverNow(
        game: Game,
        platform: Platform?,
        officialName: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!NetworkStatus.isOnline(appCtx)) return@withContext false
        val picked = scrapeAllForPicker(game, platform, officialName)
        for (item in picked) {
            val downloaded = downloadImageBytes(item.url) ?: continue
            val cacheFile = webCacheFile(game.id)
            runCatching { cacheFile.writeBytes(downloaded.second) }
            rememberSuccess(game.id, item.url)
            _indexVersion.value += 1
            return@withContext true
        }
        false
    }

    suspend fun downloadImageBytes(urlStr: String): Pair<String, ByteArray>? = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        try {
            val req = Request.Builder()
                .url(urlStr)
                .header("User-Agent", USER_AGENT)
                .build()
            client.http.newCall(req).execute().use { resp ->
                code = resp.code
                if (!resp.isSuccessful) {
                    ScrapeLogStore.log(
                        "COVER",
                        ScrapeLogStore.nodeFromUrl(urlStr),
                        "DOWNLOAD",
                        url = urlStr,
                        status = code,
                        elapsedMs = System.currentTimeMillis() - t0,
                        error = "HTTP $code",
                    )
                    return@withContext null
                }
                val body = resp.body ?: return@withContext null
                var overflow = false
                val bytes = body.byteStream().use { input ->
                    val baos = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > 10 * 1024 * 1024) {
                            overflow = true
                            break
                        }
                        baos.write(buf, 0, n)
                    }
                    baos.toByteArray()
                }
                if (overflow) {
                    ScrapeLogStore.log("COVER", ScrapeLogStore.nodeFromUrl(urlStr), "DOWNLOAD", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "exceeds 10MB limit")
                    return@withContext null
                }
                val ct = resp.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
                if (bytes.size < 32) {
                    ScrapeLogStore.log("COVER", ScrapeLogStore.nodeFromUrl(urlStr), "DOWNLOAD", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "too small (${bytes.size}B)")
                    return@withContext null
                }
                val mime = if (ct.startsWith("image/")) ct else detectImageMime(bytes)
                ScrapeLogStore.log("COVER", ScrapeLogStore.nodeFromUrl(urlStr), "DOWNLOAD", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, result = "${bytes.size}B ($mime)")
                mime to bytes
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log("COVER", ScrapeLogStore.nodeFromUrl(urlStr), "DOWNLOAD", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    private fun readCoilDiskCache(url: String): ByteArray? = runCatching {
        val loader = SingletonImageLoader.get(appCtx)
        val diskCache = loader.diskCache ?: return@runCatching null
        diskCache.openSnapshot(url)?.use { snap ->
            snap.data.toFile().takeIf { it.isFile && it.canRead() }?.readBytes()
        }
    }.getOrNull()

    private fun webCacheFile(gameId: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(gameId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(webCacheDir, "$hash.img")
    }

    private fun mimeForFile(f: File): String = when (f.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    private fun detectImageMime(bytes: ByteArray): String = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
        bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() -> "image/webp"
        bytes.size >= 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
        else -> "image/jpeg"
    }

    private fun cachedOrigin(key: String, url: String): Pair<String, CoverMatchMode> {
        resolvedOrigins[key]?.let { return it }
        return inferOrigin(url, url)
    }

    private fun inferOrigin(url: String, model: Any): Pair<String, CoverMatchMode> {
        if (model is File) {
            return if (url.contains("/covers/")) "custom" to CoverMatchMode.CustomFile
            else "local" to CoverMatchMode.LocalFile
        }
        val prov = when {
            "thumbnails.libretro.com" in url && ("Named_Titles" in url || "Named_Snaps" in url) -> ScraperCatalog.CoverIds.LIBRETRO_SNAPS
            "thumbnails.libretro.com" in url || "cdn.jsdelivr.net" in url || "raw.githubusercontent.com" in url -> ScraperCatalog.CoverIds.LIBRETRO_BOXARTS
            "gametdb.com" in url -> ScraperCatalog.CoverIds.GAMETDB
            "steamgriddb.com" in url -> ScraperCatalog.CoverIds.STEAMGRIDDB
            "hasheous.org" in url -> ScraperCatalog.CoverIds.HASHEOUS
            "arcadeitalia.net" in url -> ScraperCatalog.CoverIds.ARCADEITALIA
            "screenscraper" in url -> ScraperCatalog.CoverIds.SCREENSCRAPER
            "thegamesdb" in url -> ScraperCatalog.CoverIds.THEGAMESDB
            "igdb.com" in url -> ScraperCatalog.CoverIds.IGDB
            else -> "custom"
        }
        val mode = when (prov) {
            ScraperCatalog.CoverIds.GAMETDB -> CoverMatchMode.SerialId
            ScraperCatalog.CoverIds.HASHEOUS, ScraperCatalog.CoverIds.SCREENSCRAPER -> CoverMatchMode.ApiHash
            ScraperCatalog.CoverIds.ARCADEITALIA,
            ScraperCatalog.CoverIds.STEAMGRIDDB,
            ScraperCatalog.CoverIds.THEGAMESDB,
            ScraperCatalog.CoverIds.IGDB -> CoverMatchMode.ApiSearch
            else -> CoverMatchMode.IndexExact
        }
        return prov to mode
    }

    private fun cachedUrl(key: String): String? =
        resolvedUrls[key]?.takeIf { it.isNotEmpty() }

    private fun buildLibretroBoxartUrl(system: String, fileName: String, mirror: Int): String =
        buildLibretroThumbUrl(system, "Named_Boxarts", fileName, mirror)

    private fun buildLibretroThumbUrl(system: String, kind: String, fileName: String, mirror: Int): String = when (mirror) {
        1 -> "https://cdn.jsdelivr.net/gh/libretro-thumbnails/${Uri.encode(system.replace(' ', '_'))}@master/$kind/${Uri.encode(fileName)}"
        2 -> "https://raw.githubusercontent.com/libretro-thumbnails/${Uri.encode(system.replace(' ', '_'))}/master/$kind/${Uri.encode(fileName)}"
        else -> "https://thumbnails.libretro.com/${Uri.encode(system)}/$kind/${Uri.encode(fileName)}"
    }

    private fun buildDirectNames(game: Game, officialName: String?): List<String> {
        val directNames = LinkedHashSet<String>()
        if (!officialName.isNullOrBlank()) {
            directNames += sanitize(officialName)
        }
        directNames += sanitize(game.baseName)
        val stripped = TitleNormalizer.stripRomTags(game.baseName)
        if (stripped.isNotEmpty()) {
            for (region in REGIONS) directNames += sanitize("$stripped $region")
            directNames += sanitize(stripped)
        }
        for (alias in CjkAliases.expandEnglishAliases(game.baseName)) {
            for (region in REGIONS.take(2)) directNames += sanitize("$alias $region")
        }
        return directNames.toList()
    }

    private fun fetchOnlineBoxartList(system: String, cacheFile: File): List<String> {
        // 三级镜像容灾：1. thumbnails.libretro.com HTML → 2. jsDelivr flat API → 3. GitHub Trees API
        val primaryUrl = "https://thumbnails.libretro.com/${Uri.encode(system)}/Named_Boxarts/"
        val primaryNames = fetchLibretroHtmlListing(system, primaryUrl)
        if (!primaryNames.isNullOrEmpty()) {
            activeLibretroMirror = 0
            writeIndexCache(cacheFile, primaryNames)
            return primaryNames
        }
        val repo = Uri.encode(system.replace(' ', '_'))
        val jsDelivrUrl = "https://data.jsdelivr.com/v1/package/gh/libretro-thumbnails/$repo@master/flat"
        val jsDelivrNames = fetchJsDelivrBoxartList(system, jsDelivrUrl)
        if (jsDelivrNames.isNotEmpty()) {
            activeLibretroMirror = 1
            writeIndexCache(cacheFile, jsDelivrNames)
            return jsDelivrNames
        }
        val githubUrl = "https://api.github.com/repos/libretro-thumbnails/$repo/git/trees/master?recursive=1"
        val githubNames = fetchGitHubBoxartList(system, githubUrl)
        if (githubNames.isNotEmpty()) {
            activeLibretroMirror = 2
            writeIndexCache(cacheFile, githubNames)
        }
        return githubNames
    }

    private fun fetchLibretroHtmlListing(system: String, url: String): List<String>? {
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.http.newCall(req).execute().use { resp ->
                code = resp.code
                if (!resp.isSuccessful) {
                    ScrapeLogStore.log("COVER", "libretro_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "HTTP $code")
                    return null
                }
                val body = resp.body ?: return null
                val names = LinkedHashSet<String>(4096)
                body.charStream().buffered().useLines { lines ->
                    for (line in lines) {
                        val m = HREF_PNG_REGEX.find(line) ?: continue
                        val encodedHref = m.groupValues[1]
                        val decoded = runCatching {
                            URLDecoder.decode(encodedHref.replace("+", "%2B"), "UTF-8")
                        }.getOrDefault(encodedHref)
                        if (decoded.endsWith(".png", ignoreCase = true) && !decoded.startsWith("/")) {
                            names += decoded
                        }
                    }
                }
                ScrapeLogStore.log("COVER", "libretro_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, result = "${names.size} entries")
                names.toList()
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log("COVER", "libretro_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    private fun fetchJsDelivrBoxartList(system: String, url: String): List<String> {
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.http.newCall(req).execute().use { resp ->
                code = resp.code
                if (!resp.isSuccessful) {
                    ScrapeLogStore.log("COVER", "jsdelivr_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "HTTP $code")
                    return emptyList()
                }
                val text = resp.body?.string().orEmpty()
                val files = org.json.JSONObject(text).optJSONArray("files") ?: return emptyList()
                val names = LinkedHashSet<String>(4096)
                val prefix = "/Named_Boxarts/"
                for (i in 0 until files.length()) {
                    val path = files.optJSONObject(i)?.optString("name") ?: continue
                    if (path.startsWith(prefix) && path.endsWith(".png", ignoreCase = true)) {
                        names += path.removePrefix(prefix)
                    }
                }
                ScrapeLogStore.log("COVER", "jsdelivr_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, result = "${names.size} entries (failover)")
                names.toList()
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log("COVER", "jsdelivr_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}")
            emptyList()
        }
    }

    private fun fetchGitHubBoxartList(system: String, url: String): List<String> {
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.http.newCall(req).execute().use { resp ->
                code = resp.code
                if (!resp.isSuccessful) {
                    ScrapeLogStore.log("COVER", "github_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "HTTP $code")
                    return emptyList()
                }
                val text = resp.body?.string().orEmpty()
                val tree = org.json.JSONObject(text).optJSONArray("tree") ?: return emptyList()
                val names = LinkedHashSet<String>(4096)
                val prefix = "Named_Boxarts/"
                for (i in 0 until tree.length()) {
                    val path = tree.optJSONObject(i)?.optString("path") ?: continue
                    if (path.startsWith(prefix) && path.endsWith(".png", ignoreCase = true)) {
                        names += path.removePrefix(prefix)
                    }
                }
                ScrapeLogStore.log("COVER", "github_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, result = "${names.size} entries (github failover)")
                names.toList()
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log("COVER", "github_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}")
            emptyList()
        }
    }

    private fun writeIndexCache(cacheFile: File, names: List<String>) {
        runCatching {
            val tmp = File(indexDir, "${cacheFile.name}.tmp")
            tmp.writeText(names.joinToString("\n"))
            tmp.renameTo(cacheFile)
        }
    }

    private fun buildIndex(names: List<String>): BoxartIndex {
        val byExact = HashMap<String, String>(names.size)
        val grouped = HashMap<String, MutableList<String>>(names.size)
        val byToken = HashMap<String, MutableList<String>>(names.size)
        for (fileName in names) {
            val noExt = if (fileName.endsWith(".png", ignoreCase = true)) fileName.dropLast(4) else fileName
            byExact.putIfAbsent(TitleNormalizer.exactKey(noExt), fileName)
            val compact = TitleNormalizer.compactKey(noExt)
            if (compact.isNotEmpty()) {
                grouped.getOrPut(compact) { ArrayList(2) }.add(fileName)
            }
            val norm = TitleNormalizer.normalizedTitle(noExt)
            for (tok in TitleNormalizer.tokenize(norm)) {
                byToken.getOrPut(tok) { ArrayList(4) }.add(fileName)
            }
        }
        val sortedGrouped = grouped.mapValues { (_, list) ->
            list.sortedBy { candidateScore(it) }
        }
        return BoxartIndex(byExact, sortedGrouped, byToken)
    }

    /**
     * 固定宽度 320px WebP 缩略图缓存 + 内存 LRU：
     * 命中则 O(1) 返回磁盘 WebP 缩略图；未命中则先返回原图并在 IO 线程异步生成 WebP 缩略图，避免阻塞主线程。
     */
    private fun preferredThumbOrOriginal(original: File): File {
        val key = original.path
        thumbPathLru.get(key)?.let { cached ->
            if (cached.isFile) return cached
        }
        val hash = sha1Hex(key)
        val dest = File(thumbDir, "$hash.webp")
        if (dest.isFile && dest.length() > 0L) {
            thumbPathLru.put(key, dest)
            return dest
        }
        if (thumbGenerating.putIfAbsent(key, true) == null) {
            scope.launch(Dispatchers.IO) {
                try {
                    generateWebpThumb(original, dest)
                    if (dest.isFile && dest.length() > 0L) {
                        thumbPathLru.put(key, dest)
                    }
                } finally {
                    thumbGenerating.remove(key)
                }
            }
        }
        return original
    }

    private fun generateWebpThumb(original: File, dest: File) {
        if (!original.isFile || original.length() <= 0L) return
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(original.path, bounds)
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) return
            var sample = 1
            while (srcW / (sample * 2) >= THUMB_MAX_WIDTH_PX) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(original.path, decodeOpts) ?: return
            val target = if (decoded.width > THUMB_MAX_WIDTH_PX) {
                val targetH = ((decoded.height.toLong() * THUMB_MAX_WIDTH_PX) / decoded.width).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, THUMB_MAX_WIDTH_PX, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }
            val tmp = File(dest.parentFile, "${dest.name}.tmp")
            try {
                tmp.outputStream().buffered().use { os ->
                    val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    target.compress(fmt, 82, os)
                }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
            } finally {
                target.recycle()
                runCatching { if (tmp.exists()) tmp.delete() }
            }
        }
    }

    private fun sha1Hex(input: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MATCHER_VERSION = 2
        const val THUMB_MAX_WIDTH_PX = 320
        const val USER_AGENT = "PocketDeck/0.4 (Android Handheld Launcher)"

        val API_COVER_NODES = setOf(
            ScraperCatalog.CoverIds.GAMETDB,
            ScraperCatalog.CoverIds.HASHEOUS,
            ScraperCatalog.CoverIds.ARCADEITALIA,
            ScraperCatalog.CoverIds.STEAMGRIDDB,
            ScraperCatalog.CoverIds.SCREENSCRAPER,
            ScraperCatalog.CoverIds.THEGAMESDB,
            ScraperCatalog.CoverIds.IGDB,
        )
        val REGIONS = listOf("(USA)", "(World)", "(USA, Europe)", "(Europe)", "(Japan)")
        val ILLEGAL = Regex("""[&*/:`<>?\\|"]""")
        val HREF_PNG_REGEX = Regex("""href="([^"]+\.png)"""", RegexOption.IGNORE_CASE)
        val SAFE_FILE_REGEX = Regex("""[^a-zA-Z0-9._-]""")

        fun sanitize(name: String) = name.replace(ILLEGAL, "_")
        fun sanitizeFileName(name: String) = name.replace(SAFE_FILE_REGEX, "_")

        fun candidateScore(name: String): Int {
            var score = when {
                "(USA)" in name -> 10
                "(World)" in name -> 15
                "(USA, Europe)" in name -> 20
                "(Europe)" in name -> 30
                "(Japan)" in name -> 40
                else -> 50
            }
            if ("[b" in name || "[h" in name || "[tr" in name || "[p" in name) score += 100
            return score
        }
    }
}
