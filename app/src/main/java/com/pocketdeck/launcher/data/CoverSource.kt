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
import com.pocketdeck.launcher.lan.WebFileSecurity
import com.pocketdeck.launcher.launch.GameLauncher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.Reader
import java.net.InetAddress
import java.net.URLDecoder
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
    private val indexDir = File(appCtx.cacheDir, "boxart_index")
    private val webCacheDir = File(appCtx.cacheDir, "web_covers")
    private val thumbDir = File(appCtx.cacheDir, "cover_thumbs_webp")
    private val thumbPathLru = LruCache<String, File>(512)
    private val thumbGenerating = ConcurrentHashMap<String, Boolean>()
    private val thumbValidatedPaths = ConcurrentHashMap.newKeySet<String>()
    @OptIn(ExperimentalCoroutinesApi::class)
    private val thumbDispatcher = Dispatchers.IO.limitedParallelism(2)
    // 缓存 BoxartIndex 匹配结果，避免 indexVersion 变化时主线程重复对屏上全部卡片跑分词/模糊匹配
    private val indexMatchCache: MutableMap<String, List<Pair<String, CoverMatchMode>>> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, List<Pair<String, CoverMatchMode>>>(128, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, List<Pair<String, CoverMatchMode>>>?,
                ): Boolean = size > 512
            },
        )

    /** 可选外部官方/标准英文名提供者（由 AppContainer 接入 MetadataRepository），供滑动窗口刮削复用已识别的标准名。 */
    @Volatile
    var searchNameResolver: ((String) -> String?)? = null

    private val resolvedUrls = ConcurrentHashMap<String, String>()
    private val resolvedOrigins = ConcurrentHashMap<String, Pair<String, CoverMatchMode>>()
    private val failedUntilMs = ConcurrentHashMap<String, Long>()
    /** 已确认全源未命中并持久化至 SQLite 的失败项 ID（区别于仅内存态的短时错误冷却）。 */
    private val persistedFailedIds = ConcurrentHashMap.newKeySet<String>()
    /** 本会话已完成离线 CRC 哈希封面探测的 gameId，防止未跑完哈希前提前写入 7 天负缓存。 */
    private val offlineHashCheckedIds = ConcurrentHashMap.newKeySet<String>()
    private val systemIndexes = ConcurrentHashMap<String, BoxartIndex>()
    private val systemIndexLastUsedMs = ConcurrentHashMap<String, Long>()
    private val systemLocks = ConcurrentHashMap<String, Mutex>()
    private val indexFailedUntilMs = ConcurrentHashMap<String, Long>()

    @Volatile
    private var preloadIndexesJob: Job? = null
    private val remoteIndexRefreshInFlight = ConcurrentHashMap<String, Boolean>()
    @Volatile
    private var githubIndexBlockedUntilMs: Long = 0L
    private val scrapingInFlight = ConcurrentHashMap<String, Boolean>()
    private val webCacheFilesById = ConcurrentHashMap<String, File>()
    /** `web_covers/` 目录下已存在文件名集合（冷启动仅一次 `dir.list()`，零 `stat()` 与零预哈希）。 */
    private val webCacheExistingNames = ConcurrentHashMap.newKeySet<String>()
    private val manualCoverIds = ConcurrentHashMap.newKeySet<String>()
    private val thumbExistingNames = ConcurrentHashMap.newKeySet<String>()
    private val diskWritesSincePrune = AtomicInteger(0)
    private val lastDiskPruneMs = AtomicLong(0L)

    /** 专用于 Web 端代下载的公网安全 OkHttpClient（复用共享连接池，强校验 DNS 解析 IP 拒绝私网/回环与跳转 SSRF）。 */
    private val safePublicHttp: OkHttpClient by lazy {
        client.http.newBuilder()
            .dns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        val all = Dns.SYSTEM.lookup(hostname)
                        val publicOnly = all.filter { WebFileSecurity.isPublicInetAddress(it) }
                        if (publicOnly.isEmpty()) {
                            throw UnknownHostException("Blocked non-public address for host: $hostname")
                        }
                        return publicOnly
                    }
                },
            )
            .addNetworkInterceptor { chain ->
                val url = chain.request().url
                if (!url.isHttps) {
                    throw IOException("Blocked non-HTTPS cover download URL")
                }
                chain.proceed(chain.request())
            }
            .build()
    }

    // 有界 LRU，避免上万游戏时候选来源映射无限增长
    private val candidateOrigins: MutableMap<String, Pair<String, CoverMatchMode>> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Pair<String, CoverMatchMode>>(512, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, Pair<String, CoverMatchMode>>?,
                ): Boolean = size > 1024
            },
        )

    /** Libretro 活跃镜像索引：1 = jsDelivr CDN（默认），2 = GitHub Raw，0 = thumbnails.libretro.com（仅兜底）。 */
    @Volatile
    private var activeLibretroMirror: Int = 1

    @Volatile
    private var nodeConfigs: List<SourceItemConfig> = ScraperCatalog.defaultCoverConfigs()

    @Volatile
    private var credentials: ScraperCredentials = ScraperCredentials()

    @Volatile
    private var configuredOnce = false

    private val _indexVersion = MutableStateFlow(0)
    val indexVersion: StateFlow<Int> = _indexVersion.asStateFlow()

    private val indexBumpLock = Any()
    private var indexBumpJob: Job? = null
    @Volatile
    private var fastScrollingPaused = false
    @Volatile
    private var pendingIndexBump = false
    private val initialCoversReady = CompletableDeferred<Unit>()

    suspend fun awaitInitialReady() {
        initialCoversReady.await()
    }

    /** 快滚期间暂停后台窗口刮削队列与 indexVersion 广播，停稳后再统一恢复，避免无封面卡片密集时抢占 CPU/IO 并引发全屏重组。 */
    fun setFastScrolling(scrolling: Boolean) {
        if (fastScrollingPaused == scrolling) return
        fastScrollingPaused = scrolling
        windowScrapeQueue.setPaused(scrolling)
        if (scrolling) {
            windowPrepareJob?.cancel()
            windowScrapeQueue.clearPending()
        } else if (pendingIndexBump) {
            pendingIndexBump = false
            bumpIndexVersion()
        }
    }

    private fun bumpIndexVersion(immediate: Boolean = false) {
        if (!immediate && fastScrollingPaused) {
            pendingIndexBump = true
            return
        }
        if (immediate) {
            synchronized(indexBumpLock) {
                indexBumpJob?.cancel()
                indexBumpJob = null
            }
            _indexVersion.update { it + 1 }
            return
        }
        synchronized(indexBumpLock) {
            if (indexBumpJob?.isActive == true) return
            indexBumpJob = scope.launch {
                delay(120L)
                synchronized(indexBumpLock) { indexBumpJob = null }
                if (fastScrollingPaused) {
                    pendingIndexBump = true
                    return@launch
                }
                _indexVersion.update { it + 1 }
            }
        }
    }

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
            try {
                indexDir.mkdirs()
            webCacheDir.mkdirs()
            thumbDir.mkdirs()
            runCatching { migrateLegacyPrefsIfNeeded() }
            runCatching {
                val metaPrefs = appCtx.getSharedPreferences("cover_matcher_meta", Context.MODE_PRIVATE)
                if (metaPrefs != null && metaPrefs.getInt("matcher_ver", 0) < MATCHER_VERSION) {
                    db.clearAllCoverFailures()
                    metaPrefs.edit().putInt("matcher_ver", MATCHER_VERSION).apply()
                }
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
                    if (origin.first == WEB_PICKER_PROVIDER || r.provider?.startsWith(MANUAL_PROVIDER_PREFIX) == true) {
                        manualCoverIds.add(r.gameId)
                    }
                    infoBatch[r.gameId] = CoverResolutionInfo(origin.first, origin.second, r.url)
                    loadedUrls++
                } else if (r.failedUntilMs > now) {
                    failedUntilMs[r.gameId] = r.failedUntilMs
                    persistedFailedIds.add(r.gameId)
                }
            }
            if (infoBatch.isNotEmpty()) {
                _coverInfo.update { cur ->
                    if (cur.isEmpty()) infoBatch
                    else HashMap<String, CoverResolutionInfo>(infoBatch.size + cur.size).apply {
                        putAll(infoBatch)
                        putAll(cur)
                    }
                }
            }
            // DB 封面加载完毕后立即通知首屏渲染（无需等待目录枚举与磁盘统计）
            if (loadedUrls > 0) {
                bumpIndexVersion(immediate = true)
            }
            // 纯 readdir 收集离线封面与缩略图文件名（0 次 stat()、0 次万级 SHA-256，按需懒解析）
            webCacheDir.list()?.forEach { name ->
                if (name.endsWith(".img")) {
                    webCacheExistingNames.add(name)
                }
            }
            thumbDir.list()?.forEach { name ->
                if (name.endsWith(".webp")) {
                    thumbExistingNames.add(name)
                }
            }
            if (loadedUrls == 0 && webCacheExistingNames.isNotEmpty()) {
                bumpIndexVersion(immediate = true)
            }
            } finally {
                initialCoversReady.complete(Unit)
            }
            // 启动后延迟检查一次离线封面与缩略图磁盘上限（512MB / 128MB），按 LRU 自动清理超额文件
            delay(15_000L)
            pruneDiskCachesIfNeeded(force = true)
        }

        scope.launch {
            var prev = NetworkStatus.online.value
            NetworkStatus.online.collect { now ->
                if (now && !prev) {
                    clearTransientFailures()
                    bumpIndexVersion()
                }
                prev = now
            }
        }
    }

    private fun isPersistedCoverFailureFresh(
        gameId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (gameId !in persistedFailedIds) return false
        val until = failedUntilMs[gameId]
        if (until == null || until <= nowMs) {
            persistedFailedIds.remove(gameId)
            if (until != null) failedUntilMs.remove(gameId, until)
            return false
        }
        return true
    }

    private fun clearTransientFailures() {
        val it = failedUntilMs.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in persistedFailedIds) {
                it.remove()
            }
        }
    }

    private fun clearAllFailuresInMemory() {
        failedUntilMs.clear()
        persistedFailedIds.clear()
    }

    private fun removeFailureInMemory(gameId: String): Boolean {
        persistedFailedIds.remove(gameId)
        return failedUntilMs.remove(gameId) != null
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

    private fun normalizeLegacyProvider(raw: String?): String? {
        val stripped = raw?.removePrefix(MANUAL_PROVIDER_PREFIX)
        return when (stripped) {
            ScraperCatalog.CoverIds.LEGACY_LIBRETRO_CDN,
            ScraperCatalog.CoverIds.LEGACY_LIBRETRO_JSDELIVR,
            ScraperCatalog.CoverIds.LEGACY_LIBRETRO_GITHUB -> ScraperCatalog.CoverIds.LIBRETRO_BOXARTS
            else -> stripped
        }
    }

    private class BoxartIndex(
        val byExact: Map<String, String>,
        val byCompact: Map<String, List<String>>,
        private val rawNames: List<String>,
    ) {
        @Volatile
        private var _byToken: Map<String, List<String>>? = null
        @Volatile
        var tokenBuildScheduled: Boolean = false

        fun isTokenIndexReady(): Boolean = _byToken != null

        fun ensureTokenIndexBuilt(): Map<String, List<String>> {
            _byToken?.let { return it }
            synchronized(this) {
                _byToken?.let { return it }
                val map = HashMap<String, MutableList<String>>(rawNames.size / 2 + 16)
                for (fileName in rawNames) {
                    val noExt = if (fileName.endsWith(".png", ignoreCase = true)) fileName.dropLast(4) else fileName
                    val norm = TitleNormalizer.normalizedTitle(noExt)
                    for (tok in TitleNormalizer.tokenize(norm)) {
                        map.getOrPut(tok) { ArrayList(4) }.add(fileName)
                    }
                }
                _byToken = map
                return map
            }
        }

        fun findMatches(queries: List<String>, allowBuildTokenIndex: Boolean = true): List<String> =
            findMatchesWithMode(queries, allowBuildTokenIndex).map { it.first }

        fun findMatchesWithMode(
            queries: List<String>,
            allowBuildTokenIndex: Boolean = true,
        ): List<Pair<String, CoverMatchMode>> {
            val preferredRegions = extractPreferredRegions(queries)
            val out = LinkedHashMap<String, CoverMatchMode>()
            for (q in queries) {
                if (q.isBlank()) continue
                byExact[TitleNormalizer.exactKey(q)]?.let {
                    out.putIfAbsent(it, CoverMatchMode.IndexExact)
                }
                val compact = TitleNormalizer.compactKey(q)
                if (compact.isNotEmpty()) {
                    byCompact[compact]?.let { list ->
                        val ordered = if (list.size <= 1) {
                            list
                        } else {
                            list.sortedBy { candidateScore(it, preferredRegions) }
                        }
                        ordered.take(3).forEach { out.putIfAbsent(it, CoverMatchMode.IndexFuzzy) }
                    }
                }
            }
            if (out.isNotEmpty()) {
                return out.entries.map { it.key to it.value }
            }

            val tokenMap = _byToken ?: if (allowBuildTokenIndex) {
                ensureTokenIndexBuilt()
            } else {
                return emptyList()
            }

            // 精确与紧凑键均未命中时，使用倒排词索引 + Token Jaccard/编辑距离模糊匹配（优先保留英文别名）
            val scored = ArrayList<Pair<String, Double>>(4)
            val evaluated = HashSet<String>(64)
            for (q in TitleNormalizer.selectFuzzyQueries(queries)) {
                val norm = TitleNormalizer.normalizedTitle(q)
                val tokens = TitleNormalizer.tokenize(norm)
                if (tokens.isEmpty()) continue
                val counts = HashMap<String, Int>(32)
                for (tok in tokens) {
                    val bucket = tokenMap[tok] ?: continue
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
                        .thenBy { candidateScore(it.first, preferredRegions) },
                )
                scored.take(3).forEach { (fn, _) ->
                    out.putIfAbsent(fn, CoverMatchMode.IndexFuzzy)
                }
            }
            return out.entries.map { it.key to it.value }
        }

        /** 手动搜索专用：先走精确/模糊匹配，不足时补充「关键词包含匹配」（按紧凑键去重，避免同游戏多区域刷屏）。 */
        fun findManualMatches(queries: List<String>, limit: Int = 12): List<String> {
            val preferredRegions = extractPreferredRegions(queries)
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
                val bestFile = if (files.size <= 1) files.first() else files.minBy { candidateScore(it, preferredRegions) }
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
                        .thenBy { candidateScore(it.second, preferredRegions) },
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
        val prevNodes = nodeConfigs
        val changed = normalized != prevNodes || cred != credentials
        nodeConfigs = normalized
        credentials = cred
        if (!configuredOnce) {
            configuredOnce = true
            return
        }
        if (changed) {
            // 仅清除负缓存与“被禁用节点”产出的封面，避免用户微调备用源或填 API Key 就把全库已有封面清空
            val prevEnabled = prevNodes.filter { it.enabled }.map { it.id }.toSet()
            val nextEnabled = normalized.filter { it.enabled }.map { it.id }.toSet()
            val disabledNodes = prevEnabled - nextEnabled

            clearAllFailuresInMemory()
            offlineHashCheckedIds.clear()
            candidateOrigins.clear()
            indexMatchCache.clear()

            val removedIds = if (disabledNodes.isNotEmpty()) {
                val ids = ArrayList<String>()
                for ((gid, origin) in resolvedOrigins.entries) {
                    if (gid !in manualCoverIds && origin.first in disabledNodes) {
                        ids += gid
                    }
                }
                for (gid in ids) {
                    resolvedUrls.remove(gid)
                    resolvedOrigins.remove(gid)
                    removeWebCacheEntry(gid)
                    scheduleCoverInfoRemove(gid)
                }
                ids
            } else {
                emptyList()
            }

            scope.launch(Dispatchers.IO) {
                db.clearAllCoverFailures()
                for (gid in removedIds) {
                    db.deleteCover(gid)
                    runCatching { webCacheFile(gid).delete() }
                }
            }
            bumpIndexVersion(immediate = true)
        }
    }

    /**
     * 系统内存吃紧时释放**非当前活跃平台**的封面索引缓存，保留 [keepSystems] 正在使用的平台。
     */
    fun trimMemory(keepSystems: Set<String> = OfflineGameDb.activeSystems) {
        val toRemove = systemIndexes.keys.filter { it !in keepSystems }
        for (sys in toRemove) {
            systemIndexes.remove(sys)
            systemIndexLastUsedMs.remove(sys)
        }
        indexMatchCache.clear()
        thumbPathLru.trimToSize(128)
    }

    private fun putSystemIndex(system: String, index: BoxartIndex) {
        systemIndexes[system] = index
        systemIndexLastUsedMs[system] = System.currentTimeMillis()
        indexMatchCache.clear()
        val keep = OfflineGameDb.activeSystems
        while (systemIndexes.size > MAX_CACHED_SYSTEM_INDEXES) {
            val victim = systemIndexes.keys
                .filter { it != system && it !in keep }
                .minByOrNull { systemIndexLastUsedMs[it] ?: 0L }
                ?: break
            systemIndexes.remove(victim)
            systemIndexLastUsedMs.remove(victim)
        }
    }

    fun clearCoverCache() {
        scope.launch { clearCoverCacheNow() }
    }

    private var windowPrepareJob: Job? = null

    /** 彻底清理内存与磁盘上的封面、缩略图、索引及 API 缓存，等待磁盘删除全部完成后再返回。 */
    suspend fun clearCoverCacheNow() {
        preloadIndexesJob?.cancel()
        preloadIndexesJob = null
        windowPrepareJob?.cancel()
        windowScrapeQueue.scheduleReplace(emptyList(), debounceMs = 0L)
        resolvedUrls.clear()
        resolvedOrigins.clear()
        webCacheFilesById.clear()
        webCacheExistingNames.clear()
        manualCoverIds.clear()
        clearAllFailuresInMemory()
        offlineHashCheckedIds.clear()
        candidateOrigins.clear()
        systemIndexes.clear()
        indexFailedUntilMs.clear()
        remoteIndexRefreshInFlight.clear()
        thumbPathLru.evictAll()
        thumbExistingNames.clear()
        thumbValidatedPaths.clear()
        indexMatchCache.clear()
        clearPendingCoverInfo()
        _coverInfo.value = emptyMap()
        runCatching { SingletonImageLoader.get(appCtx).memoryCache?.clear() }

        withContext(Dispatchers.IO) {
            db.clearCovers()
            runCatching { webCacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            runCatching { thumbDir.listFiles()?.forEach { it.deleteRecursively() } }
            runCatching { indexDir.listFiles()?.forEach { it.deleteRecursively() } }
            runCatching { SingletonImageLoader.get(appCtx).diskCache?.clear() }
            runCatching { File(appCtx.cacheDir, "covers").listFiles()?.forEach { it.deleteRecursively() } }
            client.clearApiCache()
        }
        bumpIndexVersion(immediate = true)
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
            offlineHashCheckedIds.remove(id)
            if (removeFailureInMemory(id)) {
                unlocked += id
            }
        }
        if (unlocked.isNotEmpty()) {
            scope.launch(Dispatchers.IO) { db.clearCoverFailures(unlocked) }
            bumpIndexVersion()
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

    /** 应用手动搜索刮削选中的封面 URL 并持久化至 SQLite 缓存（同步清理旧离线图，防止遮挡新封面）。 */
    fun applyManualCover(gameId: String, coverUrl: String?, providerId: String) {
        if (coverUrl.isNullOrBlank()) return
        val cleanProvider = providerId.removePrefix(MANUAL_PROVIDER_PREFIX)
        resolvedUrls[gameId] = coverUrl
        resolvedOrigins[gameId] = cleanProvider to CoverMatchMode.ApiSearch
        manualCoverIds.add(gameId)
        removeFailureInMemory(gameId)
        removeWebCacheEntry(gameId)
        scope.launch(Dispatchers.IO) {
            runCatching { webCacheFile(gameId).delete() }
            db.putCoverSuccess(gameId, coverUrl, "$MANUAL_PROVIDER_PREFIX$cleanProvider", CoverMatchMode.ApiSearch.name)
        }
        _coverInfo.update { it + (gameId to CoverResolutionInfo(cleanProvider, CoverMatchMode.ApiSearch, coverUrl)) }
        bumpIndexVersion(immediate = true)
    }

    private fun hasEnabledIndexNode(): Boolean = nodeConfigs.any {
        it.enabled && (
            it.id == ScraperCatalog.CoverIds.LIBRETRO_BOXARTS ||
                it.id == ScraperCatalog.CoverIds.LIBRETRO_SNAPS
            )
    }

    /**
     * 仅对存在待拉取封面（无本地封面、未命中缓存 URL 且未处于持久负缓存冷却期）的平台按需预载 Boxart 索引：
     * - 全库已有封面或仅剩冷却期无封面项时，冷启动零离线表 IO；
     * - 按焦点外扩顺序优先加载首屏附近平台，并将并发解析上限控制在 [MAX_CONCURRENT_PRELOAD_SYSTEMS]。
     */
    fun ensureIndexesLoadedIfNeeded(
        games: List<Game>,
        focusIdx: Int = 0,
        platformResolver: (Game) -> Platform,
    ) {
        preloadIndexesJob?.cancel()
        if (games.isEmpty() || !hasEnabledIndexNode()) {
            preloadIndexesJob = null
            return
        }
        preloadIndexesJob = scope.launch(Dispatchers.IO) {
            initialCoversReady.await()
            val now = System.currentTimeMillis()
            val neededSystems = LinkedHashSet<String>()
            OfflineGameDb.forEachFocusOutward(games.size, focusIdx) { idx ->
                val g = games[idx]
                if (g.id.startsWith(GameLauncher.APK_PREFIX)) return@forEachFocusOutward
                if (g.customCover != null || g.localCover != null) return@forEachFocusOutward
                if (lookupWebCacheFile(g.id) != null || resolvedUrls.containsKey(g.id)) return@forEachFocusOutward
                if (isPersistedCoverFailureFresh(g.id, now)) return@forEachFocusOutward
                val sys = platformResolver(g).libretroSystem
                if (!sys.isNullOrBlank() && !systemIndexes.containsKey(sys)) {
                    neededSystems.add(sys)
                }
            }
            if (neededSystems.isEmpty()) return@launch
            val systemList = neededSystems.toList()
            val nextIdx = AtomicInteger(0)
            val workerCount = minOf(MAX_CONCURRENT_PRELOAD_SYSTEMS, systemList.size)
            coroutineScope {
                repeat(workerCount) {
                    launch(Dispatchers.IO) {
                        while (true) {
                            val i = nextIdx.getAndIncrement()
                            if (i >= systemList.size) break
                            currentCoroutineContext().ensureActive()
                            val sys = systemList[i]
                            if (systemIndexes.containsKey(sys)) continue
                            val loaded = loadSystemIndex(sys)
                            if (loaded) {
                                clearTransientFailures()
                                bumpIndexVersion()
                            }
                        }
                    }
                }
            }
        }
    }

    /** 预加载指定平台的在线封面目录索引（首次联网拉取，之后读本地缓存）。 */
    fun ensureIndexLoaded(platform: Platform?) {
        val system = platform?.libretroSystem ?: return
        if (!hasEnabledIndexNode() || systemIndexes.containsKey(system)) return
        scope.launch {
            val loaded = loadSystemIndex(system)
            if (loaded) {
                clearTransientFailures()
                bumpIndexVersion()
            }
        }
    }

    /** 供全量刮削任务等待指定平台索引加载完成（避免 fire-and-forget 竞态）。 */
    suspend fun ensureIndexLoadedAwait(platform: Platform?): Boolean {
        val system = platform?.libretroSystem ?: return false
        if (!hasEnabledIndexNode()) return false
        if (systemIndexes.containsKey(system)) return true
        val loaded = loadSystemIndex(system)
        if (loaded) {
            clearTransientFailures()
            bumpIndexVersion()
        }
        return systemIndexes.containsKey(system)
    }

    private fun isLibretroBoxartsEnabled(): Boolean =
        nodeConfigs.any { it.enabled && it.id == ScraperCatalog.CoverIds.LIBRETRO_BOXARTS }

    /** 本地已加载的 Libretro 封面索引是否已直接命中该游戏（命中时直接拉图，无需再发逐游戏 API 请求）。 */
    private fun hasIndexCoverMatch(game: Game, platform: Platform?, officialName: String? = null): Boolean {
        if (!isLibretroBoxartsEnabled()) return false
        val system = platform?.libretroSystem ?: return false
        val index = systemIndexes[system] ?: return false
        val queries = TitleNormalizer.buildQueryVariants(game.baseName, game.title, officialName)
        return index.findMatchesWithMode(queries).isNotEmpty()
    }

    /**
     * 通过内置离线哈希表（CRC32 -> 标准英文名）结合本地 BoxartIndex 直接匹配封面，
     * 让中文名或改名 ROM 也能在零 API 请求下命中 Libretro 封面图。
     */
    private suspend fun tryResolveCoverViaOfflineHash(game: Game, platform: Platform?): Boolean {
        if (!isLibretroBoxartsEnabled()) return false
        val system = platform?.libretroSystem ?: return false
        val index = systemIndexes[system] ?: return false
        val crc = withContext(Dispatchers.IO) {
            client.computeRomCrc32(File(game.id), platform.id)
        } ?: return false
        val canonicalName = withContext(Dispatchers.IO) {
            OfflineGameDb.lookupCanonicalNameByCrc(appCtx, system, crc)
        } ?: return false
        val queries = TitleNormalizer.buildQueryVariants(canonicalName, canonicalName, null)
        val match = index.findMatchesWithMode(queries).firstOrNull() ?: return false
        val mirror = if (activeLibretroMirror == 2) 2 else 1
        val url = buildLibretroBoxartUrl(system, match.first, mirror)
        resolvedUrls[game.id] = url
        resolvedOrigins[game.id] = ScraperCatalog.CoverIds.LIBRETRO_BOXARTS to match.second
        removeFailureInMemory(game.id)
        scope.launch(Dispatchers.IO) {
            db.putCoverSuccess(game.id, url, ScraperCatalog.CoverIds.LIBRETRO_BOXARTS, match.second.name)
        }
        scheduleCoverInfoPut(game.id, CoverResolutionInfo(ScraperCatalog.CoverIds.LIBRETRO_BOXARTS, match.second, url))
        bumpIndexVersion()
        return true
    }

    private val windowScrapeQueue = ReplaceableTaskQueue<Pair<Game, Platform>>(
        scope = scope,
        maxConcurrency = 2,
        worker = { (g, plat) ->
            if (hasLocalOrCachedCover(g) || isPersistedCoverFailureFresh(g.id)) return@ReplaceableTaskQueue
            val system = plat.libretroSystem
            if (system != null && isLibretroBoxartsEnabled() && !systemIndexes.containsKey(system)) {
                val loaded = loadSystemIndex(system)
                if (loaded) {
                    clearTransientFailures()
                    bumpIndexVersion()
                }
            }
            val resolvedName = searchNameResolver?.invoke(g.id)
            if (hasIndexCoverMatch(g, plat, resolvedName)) return@ReplaceableTaskQueue
            if (tryResolveCoverViaOfflineHash(g, plat)) return@ReplaceableTaskQueue
            offlineHashCheckedIds.add(g.id)
            val gotCover = scrapeGameApiNodesInternal(g, plat, officialName = resolvedName)
            if (!gotCover && !hasLocalOrCachedCover(g) && !hasIndexCoverMatch(g, plat, resolvedName)) {
                rememberFailure(g.id, plat)
            }
        },
    )

    /**
     * 为当前可见窗口内的未聚焦游戏做低并发队列调度（常驻 2 Worker + 180ms 滑动防抖）：
     * - 正在执行中的 1~2 个在途请求保活跑完并写缓存，不浪费已发出的网络流量；
     * - 用户滑到新位置停下后，清空尚未开始执行的旧等待队列，将新窗口的最多 16 款游戏按焦点优先顺序插入队列。
     * - 即使未启用外部 API 节点，只要启用了 Libretro Boxarts 也会为索引未命中的游戏（如中文名 ROM）跑离线 CRC 识别。
     */
    fun ensureWindowCoversScraped(
        games: List<Game>,
        focusIdx: Int,
        platformResolver: (Game) -> Platform,
        windowSize: Int = 16,
    ) {
        // 快速滚动期间在主线程零计算、零磁盘 I/O：直接暂停调度，停稳后再切后台线程筛选并替换队列
        windowPrepareJob?.cancel()
        if (games.isEmpty() || fastScrollingPaused) return
        windowPrepareJob = scope.launch(Dispatchers.Default) {
            delay(180L)
            if (fastScrollingPaused) return@launch
            initialCoversReady.await()
            if (!NetworkStatus.isOnline(appCtx)) return@launch
            val enabledApiNodes = nodeConfigs.filter { it.enabled && it.id in API_COVER_NODES }
            if (enabledApiNodes.isEmpty() && !isLibretroBoxartsEnabled()) return@launch

            val now = System.currentTimeMillis()
            val ordered = ArrayList<Pair<String, Pair<Game, Platform>>>(windowSize.coerceAtMost(games.size))
            val safeFocus = focusIdx.coerceIn(0, games.lastIndex)
            var offset = 0
            while (ordered.size < windowSize && (safeFocus + offset <= games.lastIndex || safeFocus - offset >= 0)) {
                val indices = if (offset == 0) intArrayOf(safeFocus) else intArrayOf(safeFocus + offset, safeFocus - offset)
                for (idx in indices) {
                    if (idx !in games.indices || ordered.size >= windowSize) continue
                    val g = games[idx]
                    if (g.id.startsWith(GameLauncher.APK_PREFIX)) continue
                    if (!g.customCover.isNullOrBlank() || !g.localCover.isNullOrBlank()) continue
                    if (hasLocalOrCachedCover(g)) continue
                    if (isPersistedCoverFailureFresh(g.id, now)) continue
                    val plat = platformResolver(g)
                    val resolvedName = searchNameResolver?.invoke(g.id)
                    if (hasIndexCoverMatch(g, plat, resolvedName)) continue
                    ordered += g.id to (g to plat)
                }
                offset++
            }
            windowScrapeQueue.replacePending(ordered)
        }
    }

    private suspend fun scrapeGameApiNodesInternal(
        game: Game,
        platform: Platform?,
        officialName: String?,
    ): Boolean {
        val enabledApiNodes = nodeConfigs.filter { it.enabled && it.id in API_COVER_NODES }
        if (enabledApiNodes.isEmpty()) return false
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
        if (needNodes.isEmpty()) return false

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
            removeFailureInMemory(game.id)
            bumpIndexVersion()
        }
        return gotCover
    }

    /**
     * 加载指定平台的封面文件名索引：
     * 1. 立即加载 APK 内置只读底座（`assets/offline_db/boxart/`）并与本地缓存文件合并，零等待立刻可用；
     * 2. 若尚无远端缓存文件且联网，则异步在后台拉取 `api.github.com` 增量合并写入缓存，不阻塞当前封面加载，
     *    即使 GitHub 返回 403 也不影响正常出图。
     */
    private suspend fun loadSystemIndex(system: String): Boolean {
        if (systemIndexes.containsKey(system)) return false
        val lock = systemLocks.getOrPut(system) { Mutex() }
        return lock.withLock {
            if (systemIndexes.containsKey(system)) return@withLock false
            withContext(Dispatchers.IO) {
                val t0 = System.currentTimeMillis()
                val cacheFile = File(indexDir, "${sanitizeFileName(system)}.txt")
                val bundled = OfflineGameDb.loadBoxartNames(appCtx, system)
                val cached = if (cacheFile.isFile && cacheFile.length() > 0) {
                    runCatching { cacheFile.readLines().filter { it.isNotBlank() } }.getOrNull().orEmpty()
                } else {
                    emptyList()
                }
                val initial = OfflineGameDb.mergeBoxartNames(bundled, cached)
                val tRead = System.currentTimeMillis()
                if (initial.isNotEmpty()) {
                    putSystemIndex(system, buildIndex(initial))
                    val tBuild = System.currentTimeMillis()
                    runCatching {
                        android.util.Log.i(
                            "PocketDeckPerf",
                            "CoverSource.loadSystemIndex($system): n=${initial.size} read=${tRead - t0}ms build=${tBuild - tRead}ms",
                        )
                    }
                    indexFailedUntilMs.remove(system)
                    // 本地已就绪；若尚未拉过远端增量且联网，后台异步合并更新
                    if (cached.isEmpty() && NetworkStatus.isOnline(appCtx)) {
                        launchAsyncRemoteIndexMerge(system, cacheFile, bundled)
                    }
                    return@withContext true
                }
                // 无内置底座的冷门平台：退回在线拉取（带失败冷却）
                val now = System.currentTimeMillis()
                if ((indexFailedUntilMs[system] ?: 0L) > now || !NetworkStatus.isOnline(appCtx)) {
                    return@withContext false
                }
                val remote = fetchOnlineBoxartList(system, cacheFile)
                if (remote.isNotEmpty()) {
                    putSystemIndex(system, buildIndex(remote))
                    indexFailedUntilMs.remove(system)
                    true
                } else {
                    indexFailedUntilMs[system] = System.currentTimeMillis() + INDEX_FAIL_COOLDOWN_MS
                    false
                }
            }
        }
    }

    private fun launchAsyncRemoteIndexMerge(system: String, cacheFile: File, bundled: List<String>) {
        val now = System.currentTimeMillis()
        if ((indexFailedUntilMs[system] ?: 0L) > now || githubIndexBlockedUntilMs > now) return
        if (remoteIndexRefreshInFlight.putIfAbsent(system, true) != null) return
        scope.launch(Dispatchers.IO) {
            try {
                // 内置离线索引已立即可用：延迟 3 秒再做低优先级远端增量合并，避免切 Tab 瞬间抢占首屏封面资源
                delay(3_000L)
                val remote = fetchOnlineBoxartList(system, cacheFile)
                if (remote.isNotEmpty()) {
                    val merged = OfflineGameDb.mergeBoxartNames(bundled, remote)
                    writeIndexCache(cacheFile, merged)
                    if (merged.size > bundled.size) {
                        putSystemIndex(system, buildIndex(merged))
                        clearAllFailuresInMemory()
                        offlineHashCheckedIds.clear()
                        bumpIndexVersion()
                    }
                } else {
                    indexFailedUntilMs[system] = System.currentTimeMillis() + INDEX_FAIL_COOLDOWN_MS
                }
            } finally {
                remoteIndexRefreshInFlight.remove(system)
            }
        }
    }

    /** 读取指定平台的本地缓存/内置封面文件名列表（供 Web 端在 jsDelivr/GitHub 超限或限流时兜底）。 */
    suspend fun readSystemIndexText(system: String): String? = withContext(Dispatchers.IO) {
        val clean = system.trim()
        if (clean.isEmpty()) return@withContext null
        val cacheFile = File(indexDir, "${sanitizeFileName(clean)}.txt")
        if (cacheFile.isFile && cacheFile.length() > 0L) {
            return@withContext runCatching { cacheFile.readText() }.getOrNull()
        }
        val bundled = OfflineGameDb.loadBoxartNames(appCtx, clean)
        if (bundled.isNotEmpty()) {
            return@withContext bundled.joinToString("\n")
        }
        loadSystemIndex(clean)
        if (cacheFile.isFile && cacheFile.length() > 0L) {
            runCatching { cacheFile.readText() }.getOrNull()
        } else {
            null
        }
    }

    /**
     * 快速滚动期间使用的纯内存 O(1) 候选封面查询：仅返回 custom / 已缓存命中的 URL / local，
     * 不做任何正则表达式、索引查找或联网候选拼装，确保主线程零卡顿。
     */
    fun fastMemoryCandidates(game: Game): List<Any> {
        val custom = game.customCover
        val downloaded = lookupWebCacheFile(game.id)
        val cached = cachedUrl(game.id)
        val local = game.localCover
        if (custom == null && downloaded == null && cached == null && local == null) return emptyList()
        val out = ArrayList<Any>(3)
        if (custom != null) out += preferredThumbOrOriginal(File(custom))
        if (downloaded != null) out += downloaded
        else if (cached != null) out += cached
        if (local != null && local != custom) out += preferredThumbOrOriginal(File(local))
        return out
    }

    fun candidates(
        game: Game,
        platform: Platform?,
        officialName: String? = null,
    ): List<Any> {
        // O(1) 快路径：1. 用户自定义封面 → 2. 已下载离线缓存 → 3. 已解析/手动刮削在线 URL → 4. 本地媒体封面
        // （让手动刮削写入的 resolvedUrls 优先于原有 localCover，避免旧本地图永远遮挡新选封面）
        game.customCover?.let { custom ->
            val f = preferredThumbOrOriginal(File(custom))
            candidateOrigins["${game.id}::$f"] = "custom" to CoverMatchMode.CustomFile
            return listOf(f)
        }
        lookupWebCacheFile(game.id)?.let { wf ->
            val (prov, mode) = resolvedOrigins[game.id] ?: ("local" to CoverMatchMode.LocalFile)
            candidateOrigins["${game.id}::$wf"] = prov to mode
            val out = ArrayList<Any>(3)
            out += wf
            cachedUrl(game.id)?.let { cached ->
                val (cProv, cMode) = cachedOrigin(game.id, cached)
                candidateOrigins["${game.id}::$cached"] = cProv to cMode
                out += cached
            }
            game.localCover?.let { local ->
                val lf = preferredThumbOrOriginal(File(local))
                candidateOrigins["${game.id}::$lf"] = "local" to CoverMatchMode.LocalFile
                out += lf
            }
            return out
        }
        val cached = cachedUrl(game.id)
        if (cached != null) {
            val (prov, mode) = cachedOrigin(game.id, cached)
            candidateOrigins["${game.id}::$cached"] = prov to mode
            return listOf(cached)
        }
        game.localCover?.let { local ->
            val f = preferredThumbOrOriginal(File(local))
            candidateOrigins["${game.id}::$f"] = "local" to CoverMatchMode.LocalFile
            return listOf(f)
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
        val effectiveHint = officialName?.takeIf { it.isNotBlank() } ?: searchNameResolver?.invoke(game.id)
        // candidates() 在 Compose 主线程调用：优先复用 indexMatchCache，且绝不在主线程构建万级倒排分词索引
        val indexMatches = if (online && index != null) {
            val cacheKey = "$system|${game.baseName}|${game.title}|${effectiveHint.orEmpty()}|${index.isTokenIndexReady()}"
            indexMatchCache.get(cacheKey) ?: run {
                val queries = TitleNormalizer.buildQueryVariants(game.baseName, game.title, effectiveHint)
                val hits = index.findMatchesWithMode(queries, allowBuildTokenIndex = false)
                if (hits.isEmpty() && !index.isTokenIndexReady() && !index.tokenBuildScheduled) {
                    index.tokenBuildScheduled = true
                    scope.launch(Dispatchers.Default) {
                        index.ensureTokenIndexBuilt()
                        indexMatchCache.clear()
                        bumpIndexVersion()
                    }
                }
                indexMatchCache.put(cacheKey, hits)
                hits
            }
        } else {
            emptyList()
        }

        for (node in enabled) {
            when (node.id) {
                ScraperCatalog.CoverIds.LIBRETRO_BOXARTS -> if (online && system != null) {
                    val primary = if (activeLibretroMirror == 2) 2 else 1
                    val fallback = if (primary == 1) 2 else 1
                    indexMatches.forEach { (fileName, mode) ->
                        addUrl(buildLibretroBoxartUrl(system, fileName, primary), node.id, mode)
                    }
                    // 主镜像宕机或 404 时，为最匹配候选自动追加备用镜像（jsDelivr <-> GitHub Raw）
                    indexMatches.take(2).forEach { (fileName, mode) ->
                        addUrl(buildLibretroBoxartUrl(system, fileName, fallback), node.id, mode)
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

        // 兜底：若已启用 Wikidata 且刮削结果带有 Wikimedia Commons 图片（P18/P154），作为低优先级候选封面
        client.cachedHitInMemory(ScraperCatalog.MetaIds.WIKIDATA, game.id)?.coverUrls?.forEach {
            addUrl(it, ScraperCatalog.MetaIds.WIKIDATA, CoverMatchMode.ApiSearch)
        }

        game.localCover?.let { addCandidate(File(it), "local", CoverMatchMode.LocalFile) }
        return out
    }

    /** Edit 页面「在线抓取封面」：按优先级向全部已启用节点实时查询并返回验证通过的候选封面。 */
    suspend fun scrapeAllForPicker(
        game: Game,
        platform: Platform?,
        officialName: String? = null,
        includeCachedFirst: Boolean = true,
    ): List<ScrapedCoverCandidate> = coroutineScope {
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) return@coroutineScope emptyList()
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

        // 仅选图模式下把已有缓存放首位兜底；自动重新刮削时不优先复用旧缓存，确保按最新规则重选最佳封面
        if (includeCachedFirst) {
            cachedUrl(game.id)?.let { cached ->
                push(cached, cachedOrigin(game.id, cached).first)
            }
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
        client.cachedHitInMemory(ScraperCatalog.MetaIds.WIKIDATA, game.id)?.coverUrls?.forEach {
            push(it, ScraperCatalog.MetaIds.WIKIDATA)
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
        val now = System.currentTimeMillis()
        if (isPersistedCoverFailureFresh(key, now)) {
            return -1
        }
        val failUntil = failedUntilMs[key] ?: return null
        if (failUntil <= now) {
            failedUntilMs.remove(key, failUntil)
            return null
        }
        val system = platform?.libretroSystem
        val allApiCached = nodeConfigs.filter { it.enabled && it.id in API_COVER_NODES }
            .all { node ->
                val desc = ScraperCatalog.coverDescriptor(node.id)
                !ScraperCatalog.hasCredentials(desc?.credentialKind, credentials) ||
                    (node.id == ScraperCatalog.CoverIds.GAMETDB && !client.gameTdb.supportsPlatform(platform?.id)) ||
                    client.hasConfirmedCacheInMemory(node.id, key)
            }
        if (allApiCached && (system == null || systemIndexes.containsKey(system))) {
            return -1
        }
        return null
    }

    fun rememberSuccess(key: String, model: Any?) {
        val wasFailed = removeFailureInMemory(key)
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
            val rawProv = origin?.first
            val dbProv = if (key in manualCoverIds) {
                val clean = rawProv?.removePrefix(MANUAL_PROVIDER_PREFIX)?.takeIf { it.isNotBlank() } ?: "manual"
                "$MANUAL_PROVIDER_PREFIX$clean"
            } else {
                rawProv
            }
            scope.launch(Dispatchers.IO) {
                db.putCoverSuccess(key, httpUrl, dbProv, origin?.second?.name)
            }
        }
    }

    fun isWebCacheFile(key: String, file: File): Boolean =
        lookupWebCacheFile(key)?.let { it.path == file.path } == true

    internal fun preferredLocalCoverFile(path: String): File = preferredThumbOrOriginal(File(path))

    /** 离线封面文件损坏或被系统回收时摘除该缓存条目并删坏文件，保留 resolvedUrls/SQLite 供回退重试。 */
    fun invalidateWebCacheFile(key: String, file: File) {
        val removed = webCacheFilesById.remove(key, file)
        val removedName = webCacheExistingNames.remove(file.name)
        if (removed || removedName) {
            scope.launch(Dispatchers.IO) { runCatching { file.delete() } }
            bumpIndexVersion()
        }
    }

    fun rememberFailure(key: String, platform: Platform?) {
        // 1. 若刚失败的是已损坏或被系统回收的离线缓存文件，优先清理坏文件并回退到在线 URL/本地图，绝不误删 SQLite 记录
        val staleWf = lookupWebCacheFile(key)
        if (staleWf != null) {
            webCacheFilesById.remove(key, staleWf)
            webCacheExistingNames.remove(staleWf.name)
            scope.launch(Dispatchers.IO) { runCatching { staleWf.delete() } }
            bumpIndexVersion()
            return
        }
        // 2. 离线状态下绝不抹除已持久化的封面 URL（避免飞行/断网滑库把全库 cover_cache 与手动封面清空）
        if (!NetworkStatus.online.value) return
        // 3. 手动选定的封面不因单次弱网/超时失败自动从库中删除
        if (key in manualCoverIds) return

        val hadCachedUrl = resolvedUrls.remove(key)?.isNotEmpty() == true
        if (hadCachedUrl) {
            resolvedOrigins.remove(key)
            scheduleCoverInfoRemove(key)
            scope.launch(Dispatchers.IO) { db.deleteCover(key) }
            bumpIndexVersion()
            return
        }
        scheduleCoverInfoRemove(key)
        val now = System.currentTimeMillis()
        if (isPersistedCoverFailureFresh(key, now)) return

        val system = platform?.libretroSystem
        val allApiConfirmed = nodeConfigs.filter { it.enabled && it.id in API_COVER_NODES }
            .all { node ->
                val desc = ScraperCatalog.coverDescriptor(node.id)
                !ScraperCatalog.hasCredentials(desc?.credentialKind, credentials) ||
                    (node.id == ScraperCatalog.CoverIds.GAMETDB && !client.gameTdb.supportsPlatform(platform?.id)) ||
                    client.hasConfirmedCacheInMemory(node.id, key)
            }
        val offlineHashReady = !isLibretroBoxartsEnabled() || system == null || key in offlineHashCheckedIds
        val exhaustiveMiss = allApiConfirmed && (system == null || systemIndexes.containsKey(system)) && offlineHashReady
        if (!exhaustiveMiss) {
            val prevUntil = failedUntilMs[key]
            if (prevUntil != null && prevUntil > now) return
        }
        val ttl = if (exhaustiveMiss) ScrapeDatabase.TTL_MISS_MS else ScrapeDatabase.TTL_ERROR_MS
        val until = now + ttl
        failedUntilMs[key] = until
        if (exhaustiveMiss) {
            persistedFailedIds.add(key)
            scope.launch(Dispatchers.IO) { db.putCoverFailure(key, until) }
        }
    }

    fun forget(key: String) {
        resolvedUrls.remove(key)
        resolvedOrigins.remove(key)
        manualCoverIds.remove(key)
        removeFailureInMemory(key)
        offlineHashCheckedIds.remove(key)
        removeWebCacheEntry(key)
        scheduleCoverInfoRemove(key)
        scope.launch(Dispatchers.IO) {
            db.deleteCover(key)
            runCatching { webCacheFile(key).delete() }
        }
    }

    /** ROM 重命名或移动后，同步迁移内存封面索引及 `web_covers/` 离线图文件名。 */
    fun migrateGameId(oldId: String, newId: String) {
        if (oldId == newId) return
        resolvedUrls.remove(oldId)?.let { resolvedUrls[newId] = it }
        resolvedOrigins.remove(oldId)?.let { resolvedOrigins[newId] = it }
        if (manualCoverIds.remove(oldId)) manualCoverIds.add(newId)
        failedUntilMs.remove(oldId)?.let { failedUntilMs[newId] = it }
        if (persistedFailedIds.remove(oldId)) persistedFailedIds.add(newId)
        if (offlineHashCheckedIds.remove(oldId)) offlineHashCheckedIds.add(newId)
        val prevInfo = _coverInfo.value[oldId]
        if (prevInfo != null) {
            scheduleCoverInfoRemove(oldId)
            scheduleCoverInfoPut(newId, prevInfo)
        }
        val oldWf = lookupWebCacheFile(oldId)
        removeWebCacheEntry(oldId)
        if (oldWf != null) {
            val newWf = webCacheFile(newId)
            scope.launch(Dispatchers.IO) {
                if (oldWf.isFile) {
                    val renamed = runCatching {
                        if (newWf.exists()) newWf.delete()
                        oldWf.renameTo(newWf)
                    }.getOrDefault(false)
                    if (renamed) {
                        recordWebCacheFile(newId, newWf)
                        bumpIndexVersion()
                    }
                }
            }
        }
        bumpIndexVersion()
    }

    /** 目录重命名或移动后，迁移该前缀下全部内存封面缓存与离线图文件名。 */
    fun migratePathPrefix(oldDir: String, newDir: String) {
        val oldPrefix = if (oldDir.endsWith("/")) oldDir else "$oldDir/"
        val newPrefix = if (newDir.endsWith("/")) newDir else "$newDir/"
        if (oldPrefix == newPrefix) return
        val matchedIds = (resolvedUrls.keys + webCacheFilesById.keys + failedUntilMs.keys)
            .filter { it.startsWith(oldPrefix) }
            .distinct()
        for (oldId in matchedIds) {
            migrateGameId(oldId, newPrefix + oldId.removePrefix(oldPrefix))
        }
    }

    /** 目录被删除后，清理该前缀下全部内存封面缓存与离线图文件。 */
    fun forgetPathPrefix(dirPath: String) {
        val prefix = if (dirPath.endsWith("/")) dirPath else "$dirPath/"
        val matchedIds = (resolvedUrls.keys + webCacheFilesById.keys + failedUntilMs.keys)
            .filter { it.startsWith(prefix) }
            .distinct()
        for (gid in matchedIds) {
            forget(gid)
        }
    }

    /** 当用户替换某个本地/自定义封面文件时主动清理对应缩略图缓存，避免旧缩略图残留。 */
    fun invalidateThumbnail(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        thumbPathLru.remove(filePath)
        thumbValidatedPaths.remove(filePath)
        val fileName = "${sha1HexFast(filePath)}.webp"
        thumbExistingNames.remove(fileName)
        scope.launch(thumbDispatcher) {
            runCatching { File(thumbDir, fileName).delete() }
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
        val pendingCount = pendingCoverInfoPut.size + pendingCoverInfoRemove.size
        if (coverInfoFlushJob?.isActive == true && pendingCount < 64) return
        if (pendingCount >= 64) {
            coverInfoFlushJob?.cancel()
        }
        val flushDelayMs = when {
            pendingCount >= 64 -> 0L
            pendingCount >= 16 -> 600L
            else -> 150L
        }
        coverInfoFlushJob = scope.launch(Dispatchers.Default) {
            if (flushDelayMs > 0L) delay(flushDelayMs)
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
            _coverInfo.update { cur ->
                val next = HashMap<String, CoverResolutionInfo>(cur.size + puts.size)
                next.putAll(cur)
                for (k in removes) next.remove(k)
                next.putAll(puts)
                next
            }
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

    /** 判断该游戏是否已在本地磁盘有可用封面文件（自定义/本地媒体/已下载到本地缓存），用于增量离线补齐。 */
    fun hasDownloadedCover(game: Game): Boolean {
        if (!game.customCover.isNullOrBlank() && File(game.customCover).isFile) return true
        if (!game.localCover.isNullOrBlank() && File(game.localCover).isFile) return true
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) return true
        if (lookupWebCacheFile(game.id) != null) return true
        val wf = webCacheFile(game.id)
        if (wf.isFile && wf.length() > 0L) {
            recordWebCacheFile(game.id, wf)
            return true
        }
        return false
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

    /** 记录 Web 端「在线选图」同步到本地封面时的原始在线 URL，供下次 Web 列表继续优先直连外链。 */
    fun recordWebPickerSourceUrl(gameId: String, sourceUrl: String) {
        val url = sourceUrl.trim()
        if (!WebFileSecurity.isAllowedCoverSourceUrl(url)) return
        resolvedUrls[gameId] = url
        resolvedOrigins[gameId] = WEB_PICKER_PROVIDER to CoverMatchMode.ApiSearch
        manualCoverIds.add(gameId)
        removeFailureInMemory(gameId)
        scope.launch(Dispatchers.IO) {
            db.putCoverSuccess(gameId, url, WEB_PICKER_PROVIDER, CoverMatchMode.ApiSearch.name)
        }
    }

    /** 若当前游戏生效的是在线封面 URL（或由 Web 在线选图保存的带源链接封面），返回该 URL 供 Web 端直连，减轻掌机流量压力。 */
    fun publicCoverUrlForWeb(game: Game): String? {
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) return null
        if (!game.customCover.isNullOrBlank()) {
            val origin = resolvedOrigins[game.id]?.first
            return if (origin == WEB_PICKER_PROVIDER) cachedUrl(game.id) else null
        }
        if (!game.localCover.isNullOrBlank()) return null
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
            return "a-${game.lastModifiedMs}"
        }
        val url = cachedUrl(game.id)
        val wf = webCacheFile(game.id)
        val uPart = if (!url.isNullOrBlank()) url.hashCode().toUInt().toString(16) else "0"
        val wPart = if (wf.isFile) "${wf.lastModified()}-${wf.length()}" else "0"
        return if (uPart != "0" || wPart != "0") "u-$uPart-$wPart" else "0"
    }

    private fun readCoverFileBounded(file: File): Pair<String, ByteArray>? {
        val target = preferredThumbOrOriginal(file).takeIf { it.isFile && it.canRead() } ?: file
        val len = runCatching { target.length() }.getOrDefault(-1L)
        if (len !in 1L..(16L * 1024 * 1024)) return null
        val bytes = runCatching { target.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return mimeTypeForFile(target, defaultMime = "image/jpeg") to bytes
    }

    /**
     * 仅从掌机本地文件、应用图标、Coil 磁盘缓存或掌机本地 Web 缓存读取封面字节（不触发外部网络，Web 端绝不直连外网）。
     */
    suspend fun resolveWebCoverBytes(game: Game): Pair<String, ByteArray>? = withContext(Dispatchers.IO) {
        val custom = game.customCover?.let(::File)?.takeIf { it.isFile && it.canRead() }
        if (custom != null) {
            readCoverFileBounded(custom)?.let { return@withContext it }
        }
        val local = game.localCover?.let(::File)?.takeIf { it.isFile && it.canRead() }
        if (local != null) {
            readCoverFileBounded(local)?.let { return@withContext it }
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
                return@withContext (detectImageFormat(bytes)?.second ?: "image/jpeg") to bytes
            }
        }

        val url = cachedUrl(game.id) ?: return@withContext null
        readCoilDiskCache(url)?.let { bytes ->
            if (writeFileAtomically(cachedFile, bytes)) {
                recordWebCacheFile(game.id, cachedFile)
                maybeScheduleDiskPrune()
            }
            return@withContext (detectImageFormat(bytes)?.second ?: "image/jpeg") to bytes
        }

        if (NetworkStatus.isOnline(appCtx)) {
            val downloaded = downloadImageBytes(url)
            if (downloaded != null) {
                if (writeFileAtomically(cachedFile, downloaded.second)) {
                    recordWebCacheFile(game.id, cachedFile)
                    maybeScheduleDiskPrune()
                }
                return@withContext downloaded
            }
        }
        null
    }

    /**
     * 零网络本地快通：若该游戏已有已解析 URL 且 Coil 磁盘缓存中已存在图片字节，直接转存到 `web_covers/`，
     * 避免全量刮削把用户浏览过的封面全部重新走外网下载一遍。
     */
    fun saveCoilCachedCoverIfAvailable(game: Game): Boolean {
        if (hasDownloadedCover(game)) return true
        val url = cachedUrl(game.id)?.takeIf { it.startsWith("http") } ?: return false
        val bytes = readCoilDiskCache(url) ?: return false
        if (bytes.size < 32) return false
        val wf = webCacheFile(game.id)
        val saved = writeFileAtomically(wf, bytes)
        if (saved) {
            recordWebCacheFile(game.id, wf)
            maybeScheduleDiskPrune()
            return true
        }
        return false
    }

    /** 由 Web 管理端或全量刮削触发抓取并更新该游戏封面（优先复用 Coil 磁盘缓存，未命中再直连下载首个命中项）。 */
    suspend fun fetchWebCoverNow(
        game: Game,
        platform: Platform?,
        officialName: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) return@withContext false
        if (saveCoilCachedCoverIfAvailable(game)) return@withContext true
        if (!NetworkStatus.isOnline(appCtx)) return@withContext false
        val enabled = nodeConfigs.filter { it.enabled }
        val cred = credentials
        val system = platform?.libretroSystem
        if (system != null) {
            loadSystemIndex(system)
        }
        val index = system?.let { systemIndexes[it] }
        val queries = TitleNormalizer.buildQueryVariants(game.baseName, game.title, officialName)
        val indexMatches = index?.findMatches(queries).orEmpty()
            .ifEmpty { index?.findManualMatches(queries, limit = 4).orEmpty() }
        val directNames = if (index == null) buildDirectNames(game, officialName) else emptyList()

        fun persistWebBytes(url: String, bytes: ByteArray): Boolean {
            val wf = webCacheFile(game.id)
            val saved = writeFileAtomically(wf, bytes)
            if (saved) {
                recordWebCacheFile(game.id, wf)
                maybeScheduleDiskPrune()
            }
            rememberSuccess(game.id, url)
            bumpIndexVersion()
            return true
        }

        suspend fun tryDownloadAndSave(url: String): Boolean {
            readCoilDiskCache(url)?.takeIf { it.size >= 32 }?.let { cachedBytes ->
                return persistWebBytes(url, cachedBytes)
            }
            downloadImageBytes(url)?.let { downloaded ->
                return persistWebBytes(url, downloaded.second)
            }
            for ((mirrorIdx, altUrl) in libretroAlternativeMirrors(url)) {
                readCoilDiskCache(altUrl)?.takeIf { it.size >= 32 }?.let { cachedBytes ->
                    activeLibretroMirror = mirrorIdx
                    return persistWebBytes(altUrl, cachedBytes)
                }
                downloadImageBytes(altUrl)?.let { downloaded ->
                    activeLibretroMirror = mirrorIdx
                    return persistWebBytes(altUrl, downloaded.second)
                }
            }
            return false
        }

        val tried = HashSet<String>()
        // 若此前已解析出封面 URL 但尚未落盘到本地文件，优先直接下载该 URL 落盘
        cachedUrl(game.id)?.takeIf { it.startsWith("http") }?.let { knownUrl ->
            if (tried.add(knownUrl) && tryDownloadAndSave(knownUrl)) return@withContext true
        }
        for (node in enabled) {
            when (node.id) {
                ScraperCatalog.CoverIds.LIBRETRO_BOXARTS -> if (system != null) {
                    val mirror = activeLibretroMirror
                    val urls = buildList {
                        indexMatches.take(3).forEach { add(buildLibretroBoxartUrl(system, it, mirror)) }
                        if (indexMatches.isEmpty()) {
                            directNames.take(2).forEach { add(buildLibretroBoxartUrl(system, "$it.png", mirror)) }
                        }
                    }
                    for (u in urls) {
                        if (tried.add(u) && tryDownloadAndSave(u)) return@withContext true
                    }
                }
                ScraperCatalog.CoverIds.LIBRETRO_SNAPS -> if (system != null) {
                    val mirror = activeLibretroMirror
                    for (fn in indexMatches.take(2)) {
                        for (kind in listOf("Named_Titles", "Named_Snaps")) {
                            val u = buildLibretroThumbUrl(system, kind, fn, mirror)
                            if (tried.add(u) && tryDownloadAndSave(u)) return@withContext true
                        }
                    }
                }
                else -> if (node.id in API_COVER_NODES) {
                    if (node.id == ScraperCatalog.CoverIds.GAMETDB && !client.gameTdb.supportsPlatform(platform?.id)) {
                        continue
                    }
                    val desc = ScraperCatalog.coverDescriptor(node.id)
                    if (!ScraperCatalog.hasCredentials(desc?.credentialKind, cred)) continue
                    val hit = client.cachedHitInMemory(node.id, game.id)
                        ?: client.queryProvider(
                            providerId = node.id,
                            game = game,
                            platform = platform,
                            cred = cred,
                            officialNameHint = officialName,
                        )
                    for (u in hit?.coverUrls.orEmpty().take(3)) {
                        if (tried.add(u) && tryDownloadAndSave(u)) return@withContext true
                    }
                }
            }
        }
        val wikiHit = client.cachedHit(ScraperCatalog.MetaIds.WIKIDATA, game.id)
        for (u in wikiHit?.coverUrls.orEmpty().take(2)) {
            if (tried.add(u) && tryDownloadAndSave(u)) return@withContext true
        }
        offlineHashCheckedIds.add(game.id)
        rememberFailure(game.id, platform)
        false
    }

    /** 返回已缓存的非 Libretro 扩展封面（如 SteamGridDB/IGDB/Wikidata P18·P154），供 Web「在线选图」补充候选。 */
    suspend fun cachedExtraCoversForWeb(gameId: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val seen = HashSet<String>()
        val providers = listOf(
            ScraperCatalog.CoverIds.STEAMGRIDDB,
            ScraperCatalog.CoverIds.IGDB,
            ScraperCatalog.CoverIds.THEGAMESDB,
            ScraperCatalog.CoverIds.SCREENSCRAPER,
            ScraperCatalog.MetaIds.WIKIDATA,
        )
        for (pid in providers) {
            val hit = client.cachedHit(pid, gameId) ?: continue
            for (u in hit.coverUrls) {
                if (u.isNotBlank() && seen.add(u)) {
                    out.add(pid to u)
                }
            }
        }
        return out
    }

    suspend fun downloadImageBytes(
        urlStr: String,
        strictPublicCoverHost: Boolean = false,
    ): Pair<String, ByteArray>? = withContext(Dispatchers.IO) {
        if (strictPublicCoverHost && !WebFileSecurity.isAllowedCoverSourceUrl(urlStr)) {
            return@withContext null
        }
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        try {
            val req = Request.Builder()
                .url(urlStr)
                .header("User-Agent", USER_AGENT)
                .build()
            val httpClient = if (strictPublicCoverHost) safePublicHttp else client.http
            httpClient.newCall(req).execute().use { resp ->
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
                val maxBytes = 8 * 1024 * 1024
                val declaredLen = body.contentLength()
                if (declaredLen > maxBytes) {
                    ScrapeLogStore.log("COVER", ScrapeLogStore.nodeFromUrl(urlStr), "DOWNLOAD", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "Content-Length ${declaredLen}B exceeds 8MB limit")
                    return@withContext null
                }
                var overflow = false
                val bytes = body.byteStream().use { input ->
                    val baos = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > maxBytes) {
                            overflow = true
                            break
                        }
                        baos.write(buf, 0, n)
                    }
                    baos.toByteArray()
                }
                if (overflow) {
                    ScrapeLogStore.log("COVER", ScrapeLogStore.nodeFromUrl(urlStr), "DOWNLOAD", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "exceeds 8MB limit")
                    return@withContext null
                }
                val ct = resp.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
                if (bytes.size < 32) {
                    ScrapeLogStore.log("COVER", ScrapeLogStore.nodeFromUrl(urlStr), "DOWNLOAD", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "too small (${bytes.size}B)")
                    return@withContext null
                }
                val mime = if (ct.startsWith("image/")) ct else (detectImageFormat(bytes)?.second ?: "image/jpeg")
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
        val hash = sha256HexFast(gameId)
        return File(webCacheDir, "$hash.img")
    }

    /** 懒解析离线封面路径：首次命中仅做一次 SHA-256 查内存文件名集合（0 `stat()`），后续走 O(1) Map。 */
    private fun lookupWebCacheFile(gameId: String): File? {
        webCacheFilesById[gameId]?.let { return it }
        if (webCacheExistingNames.isEmpty()) return null
        val name = "${sha256HexFast(gameId)}.img"
        if (name in webCacheExistingNames) {
            val wf = File(webCacheDir, name)
            webCacheFilesById[gameId] = wf
            return wf
        }
        return null
    }

    private fun recordWebCacheFile(gameId: String, file: File) {
        webCacheExistingNames.add(file.name)
        webCacheFilesById[gameId] = file
    }

    private fun removeWebCacheEntry(gameId: String) {
        val removed = webCacheFilesById.remove(gameId)
        if (removed != null) {
            webCacheExistingNames.remove(removed.name)
        } else if (webCacheExistingNames.isNotEmpty()) {
            webCacheExistingNames.remove("${sha256HexFast(gameId)}.img")
        }
    }

    private fun maybeScheduleDiskPrune() {
        val count = diskWritesSincePrune.incrementAndGet()
        if (count < DISK_PRUNE_WRITE_INTERVAL) return
        val now = System.currentTimeMillis()
        val prev = lastDiskPruneMs.get()
        if (now - prev < DISK_PRUNE_MIN_INTERVAL_MS) return
        if (!lastDiskPruneMs.compareAndSet(prev, now)) return
        diskWritesSincePrune.set(0)
        scope.launch(Dispatchers.IO) {
            pruneDiskCachesIfNeeded(force = false)
        }
    }

    private fun pruneDiskCachesIfNeeded(force: Boolean) {
        if (force) {
            lastDiskPruneMs.set(System.currentTimeMillis())
        }
        pruneDirByLru(webCacheDir, WEB_CACHE_MAX_BYTES) { deletedName ->
            webCacheExistingNames.remove(deletedName)
            webCacheFilesById.entries.removeIf { it.value.name == deletedName }
        }
        pruneDirByLru(thumbDir, THUMB_CACHE_MAX_BYTES) { deletedName ->
            thumbExistingNames.remove(deletedName)
            val snap = thumbPathLru.snapshot()
            for ((origPath, cachedFile) in snap) {
                if (cachedFile.name == deletedName) {
                    thumbPathLru.remove(origPath)
                    thumbValidatedPaths.remove(origPath)
                }
            }
        }
    }

    /** 原子落盘离线封面（先写 `.tmp` 再 `renameTo`），避免进程强杀或并发写入留下半截损坏 `.img`。 */
    private fun writeFileAtomically(target: File, bytes: ByteArray): Boolean {
        val parent = target.parentFile ?: return false
        parent.mkdirs()
        val tmp = File(parent, "${target.name}.${System.nanoTime()}.tmp")
        return try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return false
                }
            }
            true
        } catch (_: Throwable) {
            runCatching { tmp.delete() }
            false
        }
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
            "wikimedia.org" in url -> ScraperCatalog.MetaIds.WIKIDATA
            else -> "custom"
        }
        val mode = when (prov) {
            ScraperCatalog.CoverIds.GAMETDB -> CoverMatchMode.SerialId
            ScraperCatalog.CoverIds.HASHEOUS, ScraperCatalog.CoverIds.SCREENSCRAPER -> CoverMatchMode.ApiHash
            ScraperCatalog.CoverIds.ARCADEITALIA,
            ScraperCatalog.CoverIds.STEAMGRIDDB,
            ScraperCatalog.CoverIds.THEGAMESDB,
            ScraperCatalog.CoverIds.IGDB,
            ScraperCatalog.MetaIds.WIKIDATA -> CoverMatchMode.ApiSearch
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
        // thumbnails.libretro.com 官方 Apache 目录已清空（全 404），直接走 GitHub Trees API 拉取索引；
        // 单张图片优先走 cdn.jsdelivr.net CDN（快且不受 GitHub 50MB/限流限制）
        val repo = Uri.encode(system.replace(' ', '_'))
        val githubUrl = "https://api.github.com/repos/libretro-thumbnails/$repo/git/trees/master?recursive=1"
        val githubNames = fetchGitHubBoxartList(system, githubUrl)
        if (githubNames.isNotEmpty()) {
            activeLibretroMirror = 1
            writeIndexCache(cacheFile, githubNames)
        }
        return githubNames
    }

    private fun fetchGitHubBoxartList(system: String, url: String): List<String> {
        val now = System.currentTimeMillis()
        if (githubIndexBlockedUntilMs > now) return emptyList()
        val t0 = now
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
                    if (code == 403 || code == 429) {
                        val resetMs = resp.header("X-RateLimit-Reset")?.toLongOrNull()?.times(1000L)
                        val cur = System.currentTimeMillis()
                        githubIndexBlockedUntilMs = if (resetMs != null && resetMs > cur) {
                            resetMs.coerceAtMost(cur + 60L * 60_000L)
                        } else {
                            cur + 15L * 60_000L
                        }
                    }
                    ScrapeLogStore.log("COVER", "github_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "HTTP $code")
                    return emptyList()
                }
                val body = resp.body ?: return emptyList()
                val names = body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                    parseGitHubTreeBoxarts(reader)
                }
                ScrapeLogStore.log("COVER", "github_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, result = "${names.size} entries (github failover)")
                names
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log("COVER", "github_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}")
            emptyList()
        }
    }

    private fun writeIndexCache(cacheFile: File, names: List<String>) {
        runCatching {
            indexDir.mkdirs()
            val tmp = File(indexDir, "${cacheFile.name}.tmp")
            tmp.writeText(names.joinToString("\n"))
            tmp.renameTo(cacheFile)
        }
    }

    private fun buildIndex(names: List<String>): BoxartIndex {
        val byExact = HashMap<String, String>(names.size)
        val grouped = HashMap<String, MutableList<String>>(names.size)
        for (fileName in names) {
            val noExt = if (fileName.endsWith(".png", ignoreCase = true)) fileName.dropLast(4) else fileName
            byExact.putIfAbsent(TitleNormalizer.exactKey(noExt), fileName)
            val compact = TitleNormalizer.compactKey(noExt)
            if (compact.isNotEmpty()) {
                grouped.getOrPut(compact) { ArrayList(2) }.add(fileName)
            }
        }
        // 不在建索引阶段对上万条分组逐一跑 candidateScore 正则排序，改为查询命中该组时按需排序（节省 ~1s）
        return BoxartIndex(byExact, grouped, names)
    }

    /**
     * 固定宽度 320px WebP 缩略图缓存 + 内存 LRU：
     * - 命中则 O(1) 返回磁盘 WebP 缩略图（零主线程 stat()、零 String.format）；
     * - 每次会话在受控双并发 [thumbDispatcher] 上异步核对一次原图 mtime，原地替换封面时自动失效重绘；
     * - 未命中则先返回原图并在受控并发队列异步生成 WebP 缩略图，防止海量本地封面引发内存尖峰与掉帧。
     */
    private fun preferredThumbOrOriginal(original: File): File {
        val key = original.path
        thumbPathLru.get(key)?.let { return it }
        val fileName = "${sha1HexFast(key)}.webp"
        val dest = File(thumbDir, fileName)
        if (fileName in thumbExistingNames) {
            thumbPathLru.put(key, dest)
            if (thumbValidatedPaths.add(key)) {
                scope.launch(thumbDispatcher) {
                    if (original.isFile && dest.isFile && original.lastModified() > dest.lastModified()) {
                        if (thumbGenerating.putIfAbsent(key, true) == null) {
                            try {
                                generateWebpThumb(original, dest)
                                if (dest.isFile && dest.length() > 0L) {
                                    bumpIndexVersion()
                                }
                            } finally {
                                thumbGenerating.remove(key)
                            }
                        }
                    }
                }
            }
            return dest
        }
        if (thumbGenerating.putIfAbsent(key, true) == null) {
            scope.launch(thumbDispatcher) {
                try {
                    if (!dest.isFile || dest.length() <= 0L || (original.isFile && original.lastModified() > dest.lastModified())) {
                        generateWebpThumb(original, dest)
                    }
                    if (dest.isFile && dest.length() > 0L) {
                        thumbExistingNames.add(fileName)
                        thumbValidatedPaths.add(key)
                        thumbPathLru.put(key, dest)
                        maybeScheduleDiskPrune()
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
            dest.parentFile?.mkdirs()
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

    internal companion object {
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
        private val SHA1_TL = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-1") }
        private val SHA256_TL = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
        private const val BOXARTS_TREE_PREFIX = "Named_Boxarts/"

        /**
         * 流式解析 GitHub Git Trees JSON 响应，仅提取 `"tree"` 数组中 `Named_Boxarts` 目录下的 `.png` 文件名，
         * 不缓存整段响应文本也不构建 `JSONObject`/`JSONArray`，避免超大平台仓库引发 40~80MB 堆尖峰。
         */
        internal fun parseGitHubTreeBoxarts(reader: Reader): List<String> {
            val buf = CharArray(8192)
            var pos = 0
            var len = 0

            fun ensureFilled(): Boolean {
                if (pos < len) return true
                len = reader.read(buf)
                pos = 0
                return len > 0
            }

            fun peek(): Int = if (ensureFilled()) buf[pos].code else -1
            fun nextChar(): Int = if (ensureFilled()) buf[pos++].code else -1

            fun skipWs() {
                while (true) {
                    val c = peek()
                    if (c == ' '.code || c == '\t'.code || c == '\n'.code || c == '\r'.code) {
                        pos++
                    } else {
                        return
                    }
                }
            }

            fun hexVal(c: Int): Int = when (c) {
                in '0'.code..'9'.code -> c - '0'.code
                in 'a'.code..'f'.code -> c - 'a'.code + 10
                in 'A'.code..'F'.code -> c - 'A'.code + 10
                else -> -1
            }

            fun readJsonString(out: StringBuilder): Boolean {
                skipWs()
                if (nextChar() != '"'.code) return false
                out.setLength(0)
                while (true) {
                    val c = nextChar()
                    if (c < 0) return false
                    if (c == '"'.code) return true
                    if (c == '\\'.code) {
                        val esc = nextChar()
                        if (esc < 0) return false
                        when (esc) {
                            '"'.code -> out.append('"')
                            '\\'.code -> out.append('\\')
                            '/'.code -> out.append('/')
                            'b'.code -> out.append('\b')
                            'f'.code -> out.append('\u000C')
                            'n'.code -> out.append('\n')
                            'r'.code -> out.append('\r')
                            't'.code -> out.append('\t')
                            'u'.code -> {
                                val h1 = hexVal(nextChar())
                                val h2 = hexVal(nextChar())
                                val h3 = hexVal(nextChar())
                                val h4 = hexVal(nextChar())
                                if (h1 < 0 || h2 < 0 || h3 < 0 || h4 < 0) return false
                                out.append(((h1 shl 12) or (h2 shl 8) or (h3 shl 4) or h4).toChar())
                            }
                            else -> out.append(esc.toChar())
                        }
                    } else {
                        out.append(c.toChar())
                    }
                }
            }

            fun skipJsonString() {
                if (nextChar() != '"'.code) return
                while (true) {
                    val c = nextChar()
                    if (c < 0 || c == '"'.code) return
                    if (c == '\\'.code) {
                        if (nextChar() < 0) return
                    }
                }
            }

            fun skipJsonValue() {
                skipWs()
                when (val c = peek()) {
                    -1 -> return
                    '"'.code -> skipJsonString()
                    '{'.code, '['.code -> {
                        val open = c
                        val close = if (c == '{'.code) '}'.code else ']'.code
                        nextChar()
                        var depth = 1
                        while (depth > 0) {
                            val ch = peek()
                            if (ch < 0) return
                            when (ch) {
                                '"'.code -> skipJsonString()
                                open -> {
                                    nextChar()
                                    depth++
                                }
                                close -> {
                                    nextChar()
                                    depth--
                                }
                                else -> nextChar()
                            }
                        }
                    }
                    else -> {
                        while (true) {
                            val ch = peek()
                            if (ch < 0 || ch == ','.code || ch == '}'.code || ch == ']'.code ||
                                ch == ' '.code || ch == '\t'.code || ch == '\n'.code || ch == '\r'.code
                            ) {
                                return
                            }
                            nextChar()
                        }
                    }
                }
            }

            fun startsWithPrefix(sb: StringBuilder, prefix: String): Boolean {
                if (sb.length < prefix.length) return false
                for (i in prefix.indices) {
                    if (sb[i] != prefix[i]) return false
                }
                return true
            }

            fun endsWithPng(sb: StringBuilder): Boolean {
                val n = sb.length
                if (n < 4) return false
                return sb[n - 4] == '.' &&
                    (sb[n - 3] == 'p' || sb[n - 3] == 'P') &&
                    (sb[n - 2] == 'n' || sb[n - 2] == 'N') &&
                    (sb[n - 1] == 'g' || sb[n - 1] == 'G')
            }

            val names = LinkedHashSet<String>(4096)
            val keyBuf = StringBuilder(32)
            val valBuf = StringBuilder(128)
            val prefixLen = BOXARTS_TREE_PREFIX.length

            skipWs()
            if (nextChar() != '{'.code) return emptyList()
            while (true) {
                skipWs()
                val c = peek()
                if (c < 0 || c == '}'.code) break
                if (c == ','.code) {
                    nextChar()
                    continue
                }
                if (!readJsonString(keyBuf)) break
                val isTree = keyBuf.length == 4 &&
                    keyBuf[0] == 't' && keyBuf[1] == 'r' && keyBuf[2] == 'e' && keyBuf[3] == 'e'
                skipWs()
                if (nextChar() != ':'.code) break
                skipWs()
                if (!isTree) {
                    skipJsonValue()
                    continue
                }
                if (nextChar() != '['.code) break
                while (true) {
                    skipWs()
                    val tc = peek()
                    if (tc < 0 || tc == ']'.code) {
                        if (tc == ']'.code) nextChar()
                        break
                    }
                    if (tc == ','.code) {
                        nextChar()
                        continue
                    }
                    if (tc != '{'.code) {
                        skipJsonValue()
                        continue
                    }
                    nextChar()
                    while (true) {
                        skipWs()
                        val fc = peek()
                        if (fc < 0 || fc == '}'.code) {
                            if (fc == '}'.code) nextChar()
                            break
                        }
                        if (fc == ','.code) {
                            nextChar()
                            continue
                        }
                        if (!readJsonString(keyBuf)) break
                        val isPath = keyBuf.length == 4 &&
                            keyBuf[0] == 'p' && keyBuf[1] == 'a' && keyBuf[2] == 't' && keyBuf[3] == 'h'
                        skipWs()
                        if (nextChar() != ':'.code) break
                        skipWs()
                        if (isPath && peek() == '"'.code) {
                            if (readJsonString(valBuf) &&
                                valBuf.length > prefixLen + 4 &&
                                startsWithPrefix(valBuf, BOXARTS_TREE_PREFIX) &&
                                endsWithPng(valBuf)
                            ) {
                                names += valBuf.substring(prefixLen)
                            }
                        } else {
                            skipJsonValue()
                        }
                    }
                }
                break
            }
            return names.toList()
        }

        internal fun bytesToHexFast(bytes: ByteArray): String {
            val chars = CharArray(bytes.size * 2)
            for (i in bytes.indices) {
                val v = bytes[i].toInt() and 0xFF
                chars[i * 2] = HEX_CHARS[v ushr 4]
                chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
            }
            return String(chars)
        }

        internal fun sha1HexFast(input: String): String {
            val md = SHA1_TL.get()!!
            md.reset()
            return bytesToHexFast(md.digest(input.toByteArray(Charsets.UTF_8)))
        }

        internal fun sha256HexFast(input: String): String {
            val md = SHA256_TL.get()!!
            md.reset()
            return bytesToHexFast(md.digest(input.toByteArray(Charsets.UTF_8)))
        }

        private const val WEB_PICKER_PROVIDER = "web_picker"
        private const val MANUAL_PROVIDER_PREFIX = "manual:"
        const val MATCHER_VERSION = 2
        const val THUMB_MAX_WIDTH_PX = 320
        const val USER_AGENT = "PocketDeck/0.4 (Android Handheld Launcher)"
        const val INDEX_FAIL_COOLDOWN_MS = 10L * 60 * 1000
        const val MAX_CACHED_SYSTEM_INDEXES = 6
        const val MAX_CONCURRENT_PRELOAD_SYSTEMS = 2

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

        fun sanitize(name: String) = name.replace(ILLEGAL, "_")
        fun sanitizeFileName(name: String) = sanitizeFileKey(name)

        val COMPILATION_REISSUE_REGEX = Regex(
            """\((Namcot Collection|Namco Museum|Virtual Console|Switch Online|Evercade|Arcade Archives|Classic Mini|Retro-Bit|Collector's Edition|Anniversary Collection|Legacy Collection|Cowabunga Collection)""",
            RegexOption.IGNORE_CASE,
        )

        private val PAREN_TAG_REGEX = Regex("""\(([^)]+)\)""")

        private fun mapRegionToken(token: String, allowSingleLetter: Boolean): String? {
            val clean = token.trim().lowercase()
            return when (clean) {
                "usa", "us", "美版" -> "USA"
                "japan", "jp", "jpn", "日版" -> "Japan"
                "europe", "eu", "eur", "欧版" -> "Europe"
                "world" -> "World"
                "asia", "as" -> "Asia"
                "china", "cn", "ch" -> "China"
                "taiwan", "tw", "台版" -> "Taiwan"
                "hong kong", "hk", "港版" -> "Hong Kong"
                "korea", "kr", "韩版" -> "Korea"
                "australia", "au" -> "Australia"
                "france" -> "France"
                "germany" -> "Germany"
                "spain" -> "Spain"
                "italy" -> "Italy"
                "brazil" -> "Brazil"
                "canada" -> "Canada"
                "netherlands" -> "Netherlands"
                "sweden" -> "Sweden"
                "russia" -> "Russia"
                "u" -> if (allowSingleLetter) "USA" else null
                "j" -> if (allowSingleLetter) "Japan" else null
                "e" -> if (allowSingleLetter) "Europe" else null
                "w" -> if (allowSingleLetter) "World" else null
                "k" -> if (allowSingleLetter) "Korea" else null
                "a" -> if (allowSingleLetter) "Australia" else null
                "f" -> if (allowSingleLetter) "France" else null
                "g" -> if (allowSingleLetter) "Germany" else null
                "s" -> if (allowSingleLetter) "Spain" else null
                "i" -> if (allowSingleLetter) "Italy" else null
                else -> null
            }
        }

        /** 从 ROM 文件名或 Boxart 候选名中提取规范化区域集合（兼容 No-Intro、GoodTools 与中文地区标）。 */
        internal fun extractRegionTags(text: String): Set<String> {
            if (text.isBlank()) return emptySet()
            var out: LinkedHashSet<String>? = null
            if ('(' in text) {
                for (m in PAREN_TAG_REGEX.findAll(text)) {
                    val inner = m.groupValues[1]
                    val parts = inner.split(',')
                    val singleToken = parts.size == 1
                    for (part in parts) {
                        val mapped = mapRegionToken(part, allowSingleLetter = singleToken)
                        if (mapped != null) {
                            (out ?: LinkedHashSet<String>(3).also { out = it }).add(mapped)
                        }
                    }
                }
            }
            if (out == null) {
                when {
                    "日版" in text -> out = linkedSetOf("Japan")
                    "美版" in text -> out = linkedSetOf("USA")
                    "欧版" in text -> out = linkedSetOf("Europe")
                    "韩版" in text -> out = linkedSetOf("Korea")
                    "港版" in text -> out = linkedSetOf("Hong Kong")
                    "台版" in text -> out = linkedSetOf("Taiwan")
                }
            }
            return out ?: emptySet()
        }

        private fun extractPreferredRegions(queries: List<String>): Set<String> {
            for (q in queries) {
                val regions = extractRegionTags(q)
                if (regions.isNotEmpty()) return regions
            }
            return emptySet()
        }

        fun candidateScore(name: String, preferredRegions: Set<String> = emptySet()): Int {
            var score = when {
                "(USA)" in name -> 10
                "(World)" in name -> 15
                "(USA, Europe)" in name -> 20
                "(Europe)" in name -> 30
                "(Japan)" in name -> 40
                else -> 50
            }
            if (preferredRegions.isNotEmpty()) {
                val candRegions = extractRegionTags(name)
                score += when {
                    candRegions == preferredRegions -> 0
                    candRegions.any { it in preferredRegions } -> 100
                    else -> 200
                }
            }
            if (COMPILATION_REISSUE_REGEX.containsMatchIn(name)) score += 300
            if ("[b" in name || "[h" in name || "[tr" in name || "[p" in name) score += 500
            return score
        }

        private const val WEB_CACHE_MAX_BYTES = 512L * 1024 * 1024
        private const val THUMB_CACHE_MAX_BYTES = 128L * 1024 * 1024
        private const val DISK_PRUNE_WRITE_INTERVAL = 64
        private const val DISK_PRUNE_MIN_INTERVAL_MS = 10L * 60 * 1000

        /** 按文件 `lastModified` 从旧到新淘汰超出 [maxBytes] 上限的磁盘缓存，防止万级游戏库无限膨胀。 */
        internal fun pruneDirByLru(
            dir: File,
            maxBytes: Long,
            onDeleted: (String) -> Unit = {},
        ): Int {
            if (!dir.isDirectory || maxBytes <= 0L) return 0
            val files = dir.listFiles()?.filter { it.isFile } ?: return 0
            val now = System.currentTimeMillis()
            var total = 0L
            for (f in files) {
                if (f.name.endsWith(".tmp")) {
                    // 仅清理超过 5 分钟的陈旧残留 .tmp，避免误删并发写入中的在途临时文件
                    if (now - f.lastModified() > 300_000L) {
                        runCatching { f.delete() }
                    }
                    continue
                }
                total += f.length()
            }
            if (total <= maxBytes) return 0
            val target = (maxBytes * 85L) / 100L
            var removed = 0
            val sorted = files
                .filter { !it.name.endsWith(".tmp") }
                .sortedBy { it.lastModified() }
            for (f in sorted) {
                if (total <= target) break
                val len = f.length()
                val name = f.name
                if (runCatching { f.delete() }.getOrDefault(false)) {
                    total -= len
                    removed++
                    onDeleted(name)
                }
            }
            return removed
        }
    }
}
