package com.pocketdeck.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil3.SingletonImageLoader
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.launch.GameLauncher
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class ScrapedCoverCandidate(
    val url: String,
    val nodeId: String,
)

enum class CoverMatchMode {
    CustomFile,
    LocalFile,
    IndexExact,
    IndexFuzzy,
    DirectProbe,
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
 * 多节点联网封面刮削源（支持节点优先级排序与启用/停用开关）。
 *
 * 优先级：
 * 1. 用户自定义封面（Edit 页面自选）
 * 2. 已命中的在线封面缓存
 * 3. 用户启用的封面刮削节点（按优先级顺序：Libretro CDN / jsDelivr / GitHub / Hasheous / ArcadeItalia / Titles&Snaps / ScreenScraper / TGDB / IGDB）
 * 4. 本地同名封面兜底
 */
class CoverSource(
    context: Context,
    private val scope: CoroutineScope,
    private val client: ScraperClient,
) {
    private val appCtx = context.applicationContext
    private val prefs = appCtx.getSharedPreferences("cover_index_v3", Context.MODE_PRIVATE)
    private val indexDir = File(appCtx.cacheDir, "boxart_index").apply { mkdirs() }
    private val webCacheDir = File(appCtx.cacheDir, "web_covers").apply { mkdirs() }
    private val resolvedUrls = ConcurrentHashMap<String, String>()
    private val failedKeys = ConcurrentHashMap<String, Boolean>()
    private val systemIndexes = ConcurrentHashMap<String, BoxartIndex>()
    private val systemLocks = ConcurrentHashMap<String, Mutex>()
    private val scrapingInFlight = ConcurrentHashMap<String, Boolean>()
    private val candidateOrigins = ConcurrentHashMap<String, Pair<String, CoverMatchMode>>()

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

    init {
        scope.launch {
            var prev = NetworkStatus.online.value
            NetworkStatus.online.collect { now ->
                if (now && !prev) {
                    failedKeys.clear()
                    _indexVersion.value += 1
                }
                prev = now
            }
        }
    }

    private class BoxartIndex(
        val byExact: Map<String, String>,
        val byCompact: Map<String, List<String>>,
    ) {
        fun findMatches(queries: List<String>): List<String> =
            findMatchesWithMode(queries).map { it.first }

        fun findMatchesWithMode(queries: List<String>): List<Pair<String, CoverMatchMode>> {
            val out = LinkedHashMap<String, CoverMatchMode>()
            for (q in queries) {
                if (q.isBlank()) continue
                byExact[q.lowercase()]?.let { out.putIfAbsent(it, CoverMatchMode.IndexExact) }
                val compact = compactKey(q)
                if (compact.isNotEmpty()) {
                    byCompact[compact]?.let { list ->
                        list.take(3).forEach { out.putIfAbsent(it, CoverMatchMode.IndexFuzzy) }
                    }
                }
            }
            return out.entries.map { it.key to it.value }
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
            failedKeys.clear()
            candidateOrigins.clear()
            _coverInfo.value = emptyMap()
            prefs.edit().clear().apply()
            _indexVersion.value += 1
        }
    }

    fun clearCoverCache() {
        resolvedUrls.clear()
        failedKeys.clear()
        candidateOrigins.clear()
        _coverInfo.value = emptyMap()
        prefs.edit().clear().apply()
        runCatching { webCacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        runCatching { indexDir.listFiles()?.forEach { it.deleteRecursively() } }
        runCatching {
            val loader = coil3.SingletonImageLoader.get(appCtx)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        runCatching { File(appCtx.cacheDir, "covers").listFiles()?.forEach { it.deleteRecursively() } }
        client.clearApiCache()
        _indexVersion.value += 1
    }

    /** 预加载指定平台的在线封面目录索引（首次联网拉取，之后读本地缓存）。 */
    fun ensureIndexLoaded(platform: Platform?) {
        val system = platform?.libretroSystem ?: return
        val needIndex = nodeConfigs.any {
            it.enabled && it.id in setOf(
                ScraperCatalog.CoverIds.LIBRETRO_CDN,
                ScraperCatalog.CoverIds.LIBRETRO_JSDELIVR,
                ScraperCatalog.CoverIds.LIBRETRO_GITHUB,
                ScraperCatalog.CoverIds.LIBRETRO_SNAPS,
            )
        }
        if (!needIndex || systemIndexes.containsKey(system)) return
        scope.launch {
            val loaded = loadSystemIndex(system)
            if (loaded) {
                failedKeys.clear()
                _indexVersion.value += 1
            }
        }
    }

    /** 为指定游戏异步触发 API 型封面节点查询（Hasheous / ArcadeItalia / ScreenScraper / TGDB / IGDB）。 */
    fun ensureGameScraped(game: Game, platform: Platform?) {
        if (!NetworkStatus.isOnline(appCtx)) return
        val enabledApiNodes = nodeConfigs.filter { it.enabled && it.id in API_COVER_NODES }
        if (enabledApiNodes.isEmpty()) return
        val cred = credentials
        val needNodes = enabledApiNodes.filter { node ->
            val key = "${node.id}::${game.id}"
            client.cachedHitInMemory(node.id, game.id) == null && scrapingInFlight.putIfAbsent(key, true) == null
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
                        val hit = client.queryProvider(node.id, game, platform, cred)
                        if (hit != null && hit.coverUrls.isNotEmpty()) {
                            gotCover = true
                        }
                    } finally {
                        scrapingInFlight.remove(key)
                    }
                }
            }
            if (gotCover) {
                failedKeys.remove(game.id)
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
        val cached = cachedUrl(game.id)
        val local = game.localCover
        if (custom == null && cached == null && local == null) return emptyList()
        val out = ArrayList<Any>(3)
        if (custom != null) out += File(custom)
        if (cached != null) out += cached
        if (local != null && local != custom) out += File(local)
        return out
    }

    fun candidates(
        game: Game,
        platform: Platform?,
        officialName: String? = null,
    ): List<Any> {
        // O(1) 快路径：已有自定义封面或已解析命中的缓存 URL 时直接返回，
        // 避免主线程滚动期间每次重组都执行 stripTags/compactKey/索引检索与 36 次 Uri.encode
        game.customCover?.let { custom ->
            val f = File(custom)
            candidateOrigins["${game.id}::$f"] = "custom" to CoverMatchMode.CustomFile
            return listOf(f)
        }
        val cached = cachedUrl(game.id)
        if (cached != null) {
            val (prov, mode) = cachedOrigin(game.id, cached)
            candidateOrigins["${game.id}::$cached"] = prov to mode
            return listOf(cached)
        }

        val out = ArrayList<Any>(16)
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
        val queries = buildQueryVariants(game, officialName)
        val indexMatches = if (online) index?.findMatchesWithMode(queries).orEmpty() else emptyList()
        // 仅当该平台 BoxartIndex 尚未加载时才生成盲猜 DirectProbe URL，避免已加载索引时产生大量必 404 的冗余构造与请求
        val directNames = if (online && index == null) buildDirectNames(game, officialName) else emptyList()

        for (node in enabled) {
            when (node.id) {
                ScraperCatalog.CoverIds.LIBRETRO_CDN -> if (online && system != null) {
                    val encSys = Uri.encode(system)
                    indexMatches.forEach { (fileName, mode) ->
                        addUrl("https://thumbnails.libretro.com/$encSys/Named_Boxarts/${Uri.encode(fileName)}", node.id, mode)
                    }
                    directNames.forEach { n ->
                        addUrl("https://thumbnails.libretro.com/$encSys/Named_Boxarts/${Uri.encode(n)}.png", node.id, CoverMatchMode.DirectProbe)
                    }
                }
                ScraperCatalog.CoverIds.LIBRETRO_JSDELIVR -> if (online && system != null) {
                    val encodedRepo = Uri.encode(system.replace(' ', '_'))
                    indexMatches.forEach { (fileName, mode) ->
                        addUrl("https://cdn.jsdelivr.net/gh/libretro-thumbnails/$encodedRepo@master/Named_Boxarts/${Uri.encode(fileName)}", node.id, mode)
                    }
                    directNames.forEach { n ->
                        addUrl("https://cdn.jsdelivr.net/gh/libretro-thumbnails/$encodedRepo@master/Named_Boxarts/${Uri.encode(n)}.png", node.id, CoverMatchMode.DirectProbe)
                    }
                }
                ScraperCatalog.CoverIds.LIBRETRO_GITHUB -> if (online && system != null) {
                    val encodedRepo = Uri.encode(system.replace(' ', '_'))
                    indexMatches.forEach { (fileName, mode) ->
                        addUrl("https://raw.githubusercontent.com/libretro-thumbnails/$encodedRepo/master/Named_Boxarts/${Uri.encode(fileName)}", node.id, mode)
                    }
                    directNames.forEach { n ->
                        addUrl("https://raw.githubusercontent.com/libretro-thumbnails/$encodedRepo/master/Named_Boxarts/${Uri.encode(n)}.png", node.id, CoverMatchMode.DirectProbe)
                    }
                }
                ScraperCatalog.CoverIds.LIBRETRO_SNAPS -> if (online && system != null) {
                    val encSys = Uri.encode(system)
                    indexMatches.take(2).forEach { (fileName, mode) ->
                        addUrl("https://thumbnails.libretro.com/$encSys/Named_Titles/${Uri.encode(fileName)}", node.id, mode)
                        addUrl("https://thumbnails.libretro.com/$encSys/Named_Snaps/${Uri.encode(fileName)}", node.id, mode)
                    }
                    directNames.take(3).forEach { n ->
                        addUrl("https://thumbnails.libretro.com/$encSys/Named_Titles/${Uri.encode(n)}.png", node.id, CoverMatchMode.DirectProbe)
                    }
                }
                ScraperCatalog.CoverIds.HASHEOUS -> {
                    client.cachedHitInMemory(node.id, game.id)?.coverUrls?.forEach {
                        addUrl(it, node.id, CoverMatchMode.ApiHash)
                    }
                }
                ScraperCatalog.CoverIds.SCREENSCRAPER -> {
                    client.cachedHitInMemory(node.id, game.id)?.coverUrls?.forEach {
                        addUrl(it, node.id, CoverMatchMode.ApiHash)
                    }
                }
                ScraperCatalog.CoverIds.ARCADEITALIA,
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

    /** Edit 页面「在线抓取封面」：按优先级向全部已启用节点实时查询并返回候选封面。 */
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
                node.id to (client.queryProvider(node.id, game, platform, cred)?.coverUrls.orEmpty())
            }
        }.awaitAll().toMap()

        val out = ArrayList<ScrapedCoverCandidate>()
        val seen = HashSet<String>()
        fun push(url: String, nodeId: String) {
            if (seen.add(url)) out += ScrapedCoverCandidate(url, nodeId)
        }

        val index = system?.let { systemIndexes[it] }
        val queries = buildQueryVariants(game, officialName)
        val indexMatches = index?.findMatches(queries).orEmpty()
        val directNames = buildDirectNames(game, officialName)

        for (node in enabled) {
            when (node.id) {
                ScraperCatalog.CoverIds.LIBRETRO_CDN -> if (system != null) {
                    val encSys = Uri.encode(system)
                    indexMatches.forEach { push("https://thumbnails.libretro.com/$encSys/Named_Boxarts/${Uri.encode(it)}", node.id) }
                    if (indexMatches.isEmpty()) {
                        directNames.take(3).forEach { push("https://thumbnails.libretro.com/$encSys/Named_Boxarts/${Uri.encode(it)}.png", node.id) }
                    }
                }
                ScraperCatalog.CoverIds.LIBRETRO_JSDELIVR -> if (system != null) {
                    val encodedRepo = Uri.encode(system.replace(' ', '_'))
                    indexMatches.take(3).forEach {
                        push("https://cdn.jsdelivr.net/gh/libretro-thumbnails/$encodedRepo@master/Named_Boxarts/${Uri.encode(it)}", node.id)
                    }
                    if (indexMatches.isEmpty()) {
                        directNames.take(2).forEach {
                            push("https://cdn.jsdelivr.net/gh/libretro-thumbnails/$encodedRepo@master/Named_Boxarts/${Uri.encode(it)}.png", node.id)
                        }
                    }
                }
                ScraperCatalog.CoverIds.LIBRETRO_GITHUB -> if (system != null) {
                    val encodedRepo = Uri.encode(system.replace(' ', '_'))
                    indexMatches.take(3).forEach {
                        push("https://raw.githubusercontent.com/libretro-thumbnails/$encodedRepo/master/Named_Boxarts/${Uri.encode(it)}", node.id)
                    }
                }
                ScraperCatalog.CoverIds.LIBRETRO_SNAPS -> if (system != null) {
                    val encSys = Uri.encode(system)
                    indexMatches.take(2).forEach {
                        push("https://thumbnails.libretro.com/$encSys/Named_Titles/${Uri.encode(it)}", node.id)
                        push("https://thumbnails.libretro.com/$encSys/Named_Snaps/${Uri.encode(it)}", node.id)
                    }
                }
                else -> {
                    apiResults[node.id].orEmpty().forEach { push(it, node.id) }
                }
            }
        }
        // 并行验证候选图片 URL 确实可访问，避免弹窗出现 404 空卡片
        val verified = out.map { item ->
            async(Dispatchers.IO) {
                if (urlExists(item.url)) item else null
            }
        }.awaitAll().filterNotNull()
        verified
    }

    private fun urlExists(urlStr: String): Boolean {
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 4_000
                requestMethod = "HEAD"
                instanceFollowRedirects = true
            }
            code = conn.responseCode
            val ct = conn.contentType.orEmpty().lowercase()
            conn.disconnect()
            val ok = code in 200..299 && (ct.isEmpty() || ct.startsWith("image/") || ct == "application/octet-stream")
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

    /** 0 表示已有缓存/自定义可直接尝试首项；-1 表示已全部失败；null 表示正常按序试探。 */
    fun knownIndex(key: String, platform: Platform?): Int? {
        if (cachedUrl(key) != null) return 0
        if (failedKeys[key] == true) return -1
        val system = platform?.libretroSystem
        val allApiCached = nodeConfigs.filter { it.enabled && it.id in API_COVER_NODES }
            .all { client.cachedHitInMemory(it.id, key) != null }
        if (allApiCached && (system == null || systemIndexes.containsKey(system)) && prefs.getBoolean("fail_$key", false)) {
            return -1
        }
        return null
    }

    fun rememberSuccess(key: String, model: Any?) {
        val wasFailed = failedKeys.remove(key) != null
        val modelStr = model?.toString()
        val origin = modelStr?.let { candidateOrigins["$key::$it"] }
            ?: modelStr?.let { inferOrigin(key, it, model) }
        if (origin != null) {
            val info = CoverResolutionInfo(origin.first, origin.second, modelStr)
            if (_coverInfo.value[key] != info) {
                _coverInfo.update { cur -> if (cur[key] == info) cur else cur + (key to info) }
            }
        }
        val httpUrl = (model as? String)?.takeIf { it.startsWith("http") }
        val urlChanged = if (httpUrl != null) resolvedUrls.put(key, httpUrl) != httpUrl else false
        if (wasFailed || urlChanged) {
            val editor = prefs.edit().remove("fail_$key")
            if (urlChanged && httpUrl != null) {
                editor.putString("url_$key", httpUrl)
                if (origin != null) {
                    editor.putString("meta_$key", "${origin.first}|${origin.second.name}")
                }
            }
            editor.apply()
        }
    }

    fun rememberFailure(key: String, platform: Platform?) {
        val hadCachedUrl = resolvedUrls.remove(key) != null
        if (hadCachedUrl) {
            prefs.edit().remove("url_$key").remove("meta_$key").apply()
            _indexVersion.value += 1
            return
        }
        val prevFailed = failedKeys.put(key, true)
        if (_coverInfo.value.containsKey(key)) {
            _coverInfo.update { it - key }
        }
        if (prevFailed == true) return
        if (!NetworkStatus.online.value) return
        val system = platform?.libretroSystem
        val allApiCached = nodeConfigs.filter { it.enabled && it.id in API_COVER_NODES }
            .all { client.cachedHitInMemory(it.id, key) != null }
        if (allApiCached && (system == null || systemIndexes.containsKey(system))) {
            prefs.edit().putBoolean("fail_$key", true).apply()
        }
    }

    fun forget(key: String) {
        resolvedUrls.remove(key)
        failedKeys.remove(key)
        _coverInfo.update { it - key }
        prefs.edit().remove("url_$key").remove("meta_$key").remove("fail_$key").apply()
        runCatching { webCacheFile(key).delete() }
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

    /**
     * 仅从掌机本地文件、应用图标、Coil 磁盘缓存或掌机本地 Web 缓存读取封面字节（不触发外部网络，Web 端绝不直连外网）。
     * 若掌机已有命中的在线封面 URL 但尚未落盘，则由掌机自身代为拉取一次并缓存。
     */
    suspend fun resolveWebCoverBytes(game: Game): Pair<String, ByteArray>? = withContext(Dispatchers.IO) {
        val custom = game.customCover?.let(::File)?.takeIf { it.isFile && it.canRead() }
        if (custom != null) {
            return@withContext mimeForFile(custom) to custom.readBytes()
        }
        val local = game.localCover?.let(::File)?.takeIf { it.isFile && it.canRead() }
        if (local != null) {
            return@withContext mimeForFile(local) to local.readBytes()
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

    /**
     * 由 Web 管理端触发掌机联网抓取并更新该游戏封面（仅掌机联网，抓到后落盘缓存供 Web 展示）。
     */
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
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PocketDeck/0.3")
            }
            code = conn.responseCode
            if (code !in 200..299) {
                ScrapeLogStore.log("COVER", ScrapeLogStore.nodeFromUrl(urlStr), "DOWNLOAD", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "non-2xx")
                return@withContext null
            }
            var overflow = false
            val bytes = conn.inputStream.use { input ->
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
            val ct = conn.contentType.orEmpty().substringBefore(';').trim().lowercase()
            if (bytes.size < 32) {
                ScrapeLogStore.log("COVER", ScrapeLogStore.nodeFromUrl(urlStr), "DOWNLOAD", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "too small (${bytes.size}B)")
                return@withContext null
            }
            val mime = if (ct.startsWith("image/")) ct else detectImageMime(bytes)
            ScrapeLogStore.log("COVER", ScrapeLogStore.nodeFromUrl(urlStr), "DOWNLOAD", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, result = "${bytes.size}B ($mime)")
            mime to bytes
        } catch (t: Throwable) {
            ScrapeLogStore.log("COVER", ScrapeLogStore.nodeFromUrl(urlStr), "DOWNLOAD", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}")
            null
        } finally {
            conn?.disconnect()
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
        val raw = prefs.getString("meta_$key", null)
        if (!raw.isNullOrBlank()) {
            val sep = raw.indexOf('|')
            if (sep > 0) {
                val prov = raw.substring(0, sep)
                val mode = runCatching { CoverMatchMode.valueOf(raw.substring(sep + 1)) }.getOrNull()
                if (mode != null) return prov to mode
            }
        }
        return inferOrigin(key, url, url)
    }

    private fun inferOrigin(key: String, url: String, model: Any): Pair<String, CoverMatchMode> {
        if (model is File) {
            return if (url.contains("/covers/")) "custom" to CoverMatchMode.CustomFile
            else "local" to CoverMatchMode.LocalFile
        }
        val prov = when {
            "thumbnails.libretro.com" in url && ("Named_Titles" in url || "Named_Snaps" in url) -> ScraperCatalog.CoverIds.LIBRETRO_SNAPS
            "thumbnails.libretro.com" in url -> ScraperCatalog.CoverIds.LIBRETRO_CDN
            "cdn.jsdelivr.net" in url -> ScraperCatalog.CoverIds.LIBRETRO_JSDELIVR
            "raw.githubusercontent.com" in url -> ScraperCatalog.CoverIds.LIBRETRO_GITHUB
            "hasheous.org" in url -> ScraperCatalog.CoverIds.HASHEOUS
            "arcadeitalia.net" in url -> ScraperCatalog.CoverIds.ARCADEITALIA
            "screenscraper" in url -> ScraperCatalog.CoverIds.SCREENSCRAPER
            "thegamesdb" in url -> ScraperCatalog.CoverIds.THEGAMESDB
            "igdb.com" in url -> ScraperCatalog.CoverIds.IGDB
            else -> "custom"
        }
        val mode = when (prov) {
            ScraperCatalog.CoverIds.HASHEOUS, ScraperCatalog.CoverIds.SCREENSCRAPER -> CoverMatchMode.ApiHash
            ScraperCatalog.CoverIds.ARCADEITALIA, ScraperCatalog.CoverIds.THEGAMESDB, ScraperCatalog.CoverIds.IGDB -> CoverMatchMode.ApiSearch
            else -> CoverMatchMode.IndexExact
        }
        return prov to mode
    }

    private fun cachedUrl(key: String): String? {
        val mem = resolvedUrls[key]
        if (mem != null) return mem.ifEmpty { null }
        val fromPrefs = prefs.getString("url_$key", null)
        resolvedUrls[key] = fromPrefs ?: NO_URL_SENTINEL
        return fromPrefs
    }

    private fun buildDirectNames(game: Game, officialName: String?): List<String> {
        val directNames = LinkedHashSet<String>()
        if (!officialName.isNullOrBlank()) {
            directNames += sanitize(officialName)
        }
        directNames += sanitize(game.baseName)
        val stripped = stripTags(game.baseName)
        if (stripped.isNotEmpty()) {
            for (region in REGIONS) directNames += sanitize("$stripped $region")
            directNames += sanitize(stripped)
        }
        val cleanTitle = game.title.trim()
        if (cleanTitle.isNotEmpty() && !cleanTitle.equals(stripped, ignoreCase = true)) {
            for (region in REGIONS) directNames += sanitize("$cleanTitle $region")
        }
        return directNames.toList()
    }

    private fun buildQueryVariants(game: Game, officialName: String?): List<String> {
        val list = ArrayList<String>(8)
        if (!officialName.isNullOrBlank()) {
            list += officialName
            list += stripTags(officialName)
        }
        list += game.baseName
        val stripped = stripTags(game.baseName)
        if (stripped.isNotEmpty()) {
            list += stripped
            list += swapRomanNumerals(stripped)
        }
        if (game.title.isNotBlank()) {
            list += game.title
            list += swapRomanNumerals(game.title)
        }
        return list
    }

    private fun fetchOnlineBoxartList(system: String, cacheFile: File): List<String> {
        val url = "https://thumbnails.libretro.com/${Uri.encode(system)}/Named_Boxarts/"
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
            }
            code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                ScrapeLogStore.log("COVER", "libretro_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "non-200")
                return emptyList()
            }
            val names = LinkedHashSet<String>(4096)
            conn.inputStream.bufferedReader().useLines { lines ->
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
            conn.disconnect()
            if (names.isNotEmpty()) {
                val tmp = File(indexDir, "${cacheFile.name}.tmp")
                tmp.writeText(names.joinToString("\n"))
                tmp.renameTo(cacheFile)
            }
            ScrapeLogStore.log("COVER", "libretro_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, result = "${names.size} entries")
            names.toList()
        } catch (t: Throwable) {
            ScrapeLogStore.log("COVER", "libretro_index", "GET $system", url = url, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}")
            emptyList()
        }
    }

    private fun buildIndex(names: List<String>): BoxartIndex {
        val byExact = HashMap<String, String>(names.size)
        val grouped = HashMap<String, MutableList<String>>(names.size)
        for (fileName in names) {
            val noExt = if (fileName.endsWith(".png", ignoreCase = true)) fileName.dropLast(4) else fileName
            byExact[noExt.lowercase()] = fileName
            val compact = compactKey(noExt)
            if (compact.isNotEmpty()) {
                grouped.getOrPut(compact) { ArrayList(2) }.add(fileName)
            }
        }
        val sortedGrouped = grouped.mapValues { (_, list) ->
            list.sortedBy { candidateScore(it) }
        }
        return BoxartIndex(byExact, sortedGrouped)
    }

    private companion object {
        const val NO_URL_SENTINEL = ""
        val API_COVER_NODES = setOf(
            ScraperCatalog.CoverIds.HASHEOUS,
            ScraperCatalog.CoverIds.ARCADEITALIA,
            ScraperCatalog.CoverIds.SCREENSCRAPER,
            ScraperCatalog.CoverIds.THEGAMESDB,
            ScraperCatalog.CoverIds.IGDB,
        )
        val REGIONS = listOf("(USA)", "(World)", "(USA, Europe)", "(Europe)", "(Japan)")
        val ILLEGAL = Regex("""[&*/:`<>?\\|"]""")
        val TAGS = Regex("""\s*[(\[][^)\]]*[)\]]""")
        val NON_ALNUM = Regex("""[^a-z0-9一-鿿]""")
        val HREF_PNG_REGEX = Regex("""href="([^"]+\.png)"""", RegexOption.IGNORE_CASE)
        val SAFE_FILE_REGEX = Regex("""[^a-zA-Z0-9._-]""")
        val WHITESPACE_REGEX = Regex("""\s+""")

        fun sanitize(name: String) = name.replace(ILLEGAL, "_")
        fun sanitizeFileName(name: String) = name.replace(SAFE_FILE_REGEX, "_")

        fun stripTags(s: String): String =
            s.replace('_', ' ').replace(TAGS, "").replace(WHITESPACE_REGEX, " ").trim()

        fun compactKey(s: String): String {
            var clean = stripTags(s)
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
            return clean.lowercase().replace(NON_ALNUM, "")
        }

        fun swapRomanNumerals(s: String): String = when {
            s.endsWith(" 4") -> s.dropLast(2) + " IV"
            s.endsWith(" 3") -> s.dropLast(2) + " III"
            s.endsWith(" 2") -> s.dropLast(2) + " II"
            s.endsWith(" IV") -> s.dropLast(3) + " 4"
            s.endsWith(" III") -> s.dropLast(4) + " 3"
            s.endsWith(" II") -> s.dropLast(3) + " 2"
            else -> s
        }

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
