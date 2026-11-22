package com.pocketdeck.launcher.data

import android.content.Context
import android.net.Uri
import com.pocketdeck.launcher.BuildConfig
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ScrapeHit(
    val officialName: String? = null,
    val meta: GameMetadata = GameMetadata(),
    val coverUrls: List<String> = emptyList(),
    val matchedBySerial: Boolean = false,
    /** 英文/国际标准名（用于中文系统下继续匹配英文封面库与英文 API，不污染跨源查询）。 */
    val canonicalName: String? = null,
) {
    val effectiveSearchName: String?
        get() = canonicalName?.takeIf { it.isNotBlank() } ?: officialName?.takeIf { it.isNotBlank() }

    val isEmpty: Boolean
        get() = officialName == null && canonicalName == null && meta.isEmpty && coverUrls.isEmpty()
}

/**
 * 共享的多源刮削客户端：
 * - 共享连接池化 [OkHttpClient]（HTTP/2 复用，供 CoverSource / MetadataRepository / GameTdbClient 共用）
 * - [ScrapeDatabase] SQLite WAL 持久化 + 命中/负缓存/瞬时错误 TTL 分级管理
 * - [RomIdentityExtractor] 无头 CRC32 / 7z 内层 CRC / CHD SHA1 / 光盘与卡带 Serial ID
 * - `inFlight` `Deferred` 请求去重（MetadataRepository 与 CoverSource 绝不重复发包或写竞态）
 * - 分服务商限流器（ScreenScraper 1 req/s、IGDB 4 req/s、其余 3~4 req/s）
 * - 完整支持 GameTDB / Hasheous / ArcadeItalia / ScreenScraper / TheGamesDB / IGDB / SteamGridDB / Wikidata
 */
class ScraperClient(context: Context) {

    private val appCtx = context.applicationContext.also { ScrapeLogStore.init(it) }
    val database = ScrapeDatabase(appCtx)
    val identityExtractor = RomIdentityExtractor(database)

    /** 供 Scraper 与 Coil 共用的基础 OkHttpClient（共享 ConnectionPool、Dispatcher 与 HTTP/2 连接复用，懒初始化不拖慢冷启）。 */
    private val sharedBaseHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .connectionPool(ConnectionPool(10, 2, TimeUnit.MINUTES))
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 32
                    maxRequestsPerHost = 6
                },
            )
            .build()
    }

    val http: OkHttpClient by lazy {
        sharedBaseHttp.newBuilder()
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(HttpLogInterceptor("scraper"))
            }
            .build()
    }

    /** 基于 [sharedBaseHttp] 派生 Coil 专用客户端：共享底层连接池与调度器，仅在 Debug 挂日志拦截器并限制单图 8MB。 */
    fun newCoilHttpClient(): OkHttpClient =
        sharedBaseHttp.newBuilder()
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(HttpLogInterceptor("coil"))
            }
            .addNetworkInterceptor { chain ->
                val resp = chain.proceed(chain.request())
                val len = resp.body?.contentLength() ?: -1L
                if (len > 8L * 1024 * 1024) {
                    resp.close()
                    throw IOException("Image exceeds 8MB limit ($len bytes)")
                }
                resp
            }
            .build()

    val gameTdb = GameTdbClient(appCtx, { http }, identityExtractor)

    private val memHits = BoundedLruCache<String, CacheEntry>(MAX_MEM_HITS)
    private val inFlight = ConcurrentHashMap<String, Deferred<ScrapeHit?>>()
    private val hasheousHashCache = BoundedLruCache<String, QueryOutcome>(MAX_HASHEOUS_HASH_CACHE)
    private val hasheousHashLocks = Array(32) { Mutex() }
    private val rateLocks = ConcurrentHashMap<String, Mutex>()
    private val lastRequestMs = ConcurrentHashMap<String, Long>()

    private val igdbTokenMutex = Mutex()
    @Volatile
    private var igdbToken: String? = null
    @Volatile
    private var igdbTokenExpireMs: Long = 0L
    @Volatile
    private var igdbTokenCredKey: String = ""

    private data class CacheEntry(
        val hit: ScrapeHit?,
        val expiresAtMs: Long,
    )

    private data class HttpResult(
        val body: String?,
        val statusCode: Int?,
        val transientError: Boolean,
    )

    private data class QueryOutcome(
        val hit: ScrapeHit?,
        val transientError: Boolean = false,
    )

    /** 仅查询内存命中的刮削缓存（O(1)，无任何磁盘 I/O，可安全在主线程调用）。 */
    fun cachedHitInMemory(providerId: String, gameId: String): ScrapeHit? {
        val entry = memHits["$providerId::$gameId"] ?: return null
        if (entry.expiresAtMs > 0L && entry.expiresAtMs < System.currentTimeMillis()) return null
        return entry.hit
    }

    /** 检查是否已有有效缓存记录（包括未过期负缓存，避免重复触发网络请求）。 */
    fun hasFreshCacheInMemory(providerId: String, gameId: String): Boolean {
        val entry = memHits["$providerId::$gameId"] ?: return false
        return entry.expiresAtMs == 0L || entry.expiresAtMs >= System.currentTimeMillis()
    }

    fun cachedHit(providerId: String, gameId: String): ScrapeHit? {
        val key = "$providerId::$gameId"
        val now = System.currentTimeMillis()
        memHits[key]?.let { entry ->
            if (entry.expiresAtMs == 0L || entry.expiresAtMs >= now) {
                return entry.hit
            }
        }
        val row = database.getScrapeHit(providerId, gameId, now) ?: return null
        val hit = if (row.status == ScrapeDatabase.STATUS_HIT) row.hit else ScrapeHit()
        memHits[key] = CacheEntry(hit = hit, expiresAtMs = row.expiresAtMs)
        return hit
    }

    fun clearApiCache() {
        memHits.clear()
        hasheousHashCache.clear()
        identityExtractor.clearMemoryCache()
        database.clearScrapeCache()
        gameTdb.clearCache()
        // 清理旧版本遗留的 scraper_api 零散 JSON 缓存目录
        runCatching { File(appCtx.cacheDir, "scraper_api").deleteRecursively() }
    }

    /** 系统内存吃紧时收缩内存 LRU 缓存并释放非活跃 GameTDB 平台表，持久化 SQLite 缓存不受影响。 */
    fun trimMemory(
        keepSystems: Set<String> = OfflineGameDb.activeSystems,
        aggressive: Boolean = false,
    ) {
        memHits.trimToSize(if (aggressive) 0 else 256)
        hasheousHashCache.clear()
        identityExtractor.trimMemory(if (aggressive) 0 else 256)
        gameTdb.trimMemoryByLibretroSystems(keepSystems, aggressive = aggressive)
    }

    internal fun memHitsSize(): Int = memHits.size
    internal fun hasheousHashCacheSize(): Int = hasheousHashCache.size

    fun hasFastFingerprint(file: File, platformId: String? = null): Boolean =
        identityExtractor.hasFastFingerprint(file, platformId)

    fun extractIdentity(file: File, platformId: String? = null): RomIdentityExtractor.Identity =
        identityExtractor.extract(file, platformId)

    fun computeRomCrc32(file: File, platformId: String? = null): String? =
        identityExtractor.extract(file, platformId).crc32

    suspend fun queryProvider(
        providerId: String,
        game: Game,
        platform: Platform?,
        cred: ScraperCredentials,
        forceRefresh: Boolean = false,
        officialNameHint: String? = null,
    ): ScrapeHit? {
        val key = "$providerId::${game.id}"
        if (!forceRefresh) {
            val now = System.currentTimeMillis()
            memHits[key]?.let { entry ->
                if (entry.expiresAtMs == 0L || entry.expiresAtMs >= now) return entry.hit
            }
            val dbRow = withContext(Dispatchers.IO) { database.getScrapeHit(providerId, game.id, now) }
            if (dbRow != null) {
                val h = if (dbRow.status == ScrapeDatabase.STATUS_HIT) dbRow.hit else ScrapeHit()
                memHits[key] = CacheEntry(h, dbRow.expiresAtMs)
                return h
            }
        }

        // 请求去重：相同 provider + gameId 若已有在途请求，直接复用其结果
        val created = CompletableDeferred<ScrapeHit?>()
        val existing = inFlight.putIfAbsent(key, created)
        if (existing != null) {
            return existing.await()
        }

        try {
            val outcome = withContext(Dispatchers.IO) {
                executeProviderQuery(providerId, game, platform, cred, officialNameHint)
            }
            val hit = outcome.hit
            val now = System.currentTimeMillis()

            // 强制刷新时若远端未命中，保留本地已有有效缓存，避免覆盖掉有效元数据
            if (forceRefresh && (hit == null || hit.isEmpty)) {
                val prev = cachedHit(providerId, game.id)
                if (prev != null && !prev.isEmpty) {
                    created.complete(prev)
                    return prev
                }
            }

            if (outcome.transientError) {
                val exp = now + ScrapeDatabase.TTL_ERROR_MS
                val empty = ScrapeHit()
                memHits[key] = CacheEntry(empty, exp)
                withContext(Dispatchers.IO) {
                    database.putScrapeHit(providerId, game.id, empty, ScrapeDatabase.STATUS_ERROR, exp)
                }
                created.complete(null)
                return null
            }

            if (hit != null) {
                val isHit = !hit.isEmpty
                val status = if (isHit) ScrapeDatabase.STATUS_HIT else ScrapeDatabase.STATUS_MISS
                val exp = now + if (isHit) ScrapeDatabase.TTL_HIT_MS else ScrapeDatabase.TTL_MISS_MS
                memHits[key] = CacheEntry(hit, exp)
                withContext(Dispatchers.IO) {
                    database.putScrapeHit(providerId, game.id, hit, status, exp)
                }
                created.complete(hit)
                return hit
            }

            created.complete(null)
            return null
        } catch (e: CancellationException) {
            created.complete(null)
            throw e
        } catch (_: Throwable) {
            created.complete(null)
            return null
        } finally {
            inFlight.remove(key, created)
        }
    }

    /** 批量查询 ArcadeItalia（单次 HTTP 请求最多合并 20 个街机 ROM 名，大幅降低 RTT）。 */
    suspend fun prefetchArcadeItaliaBatch(games: List<Game>, platform: Platform?) {
        val pid = platform?.id?.lowercase().orEmpty()
        if (pid !in ARCADE_PLATFORMS) return
        if (!NetworkStatus.isOnline(appCtx)) return
        val pending = games.filter { !hasFreshCacheInMemory(ScraperCatalog.CoverIds.ARCADEITALIA, it.id) }
        if (pending.isEmpty()) return

        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val toFetch = pending.filter {
                database.getScrapeHit(ScraperCatalog.CoverIds.ARCADEITALIA, it.id, now) == null
            }
            for (chunk in toFetch.chunked(20)) {
                throttle(ScraperCatalog.CoverIds.ARCADEITALIA, 260L)
                val byRomLower = chunk.groupBy { it.baseName.lowercase() }
                val uniqueNames = byRomLower.values.map { it.first().baseName }
                val namesParam = uniqueNames.joinToString(";") { Uri.encode(it) }
                val res = httpGet("https://adb.arcadeitalia.net/service_scraper.php?ajax=query_mame&game_name=$namesParam")
                if (res.transientError || res.body == null) continue
                val matched = HashMap<String, ScrapeHit>()
                runCatching {
                    val arr = JSONObject(res.body).optJSONArray("result") ?: return@runCatching
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val rom = item.optString("game_name").lowercase()
                        if (rom.isNotEmpty()) {
                            parseArcadeItaliaItem(item)?.let { matched[rom] = it }
                        }
                    }
                }
                val ts = System.currentTimeMillis()
                val batchRows = ArrayList<ScrapeDatabase.BatchScrapeEntry>(chunk.size)
                for ((romKey, sameNameGames) in byRomLower) {
                    val hit = matched[romKey] ?: ScrapeHit()
                    val isHit = !hit.isEmpty
                    val status = if (isHit) ScrapeDatabase.STATUS_HIT else ScrapeDatabase.STATUS_MISS
                    val exp = ts + if (isHit) ScrapeDatabase.TTL_HIT_MS else ScrapeDatabase.TTL_MISS_MS
                    for (game in sameNameGames) {
                        memHits["${ScraperCatalog.CoverIds.ARCADEITALIA}::${game.id}"] = CacheEntry(hit, exp)
                        batchRows += ScrapeDatabase.BatchScrapeEntry(
                            providerId = ScraperCatalog.CoverIds.ARCADEITALIA,
                            gameId = game.id,
                            hit = hit,
                            status = status,
                            expiresAtMs = exp,
                        )
                    }
                }
                database.putScrapeHitsBatch(batchRows)
            }
        }
    }

    private suspend fun executeProviderQuery(
        providerId: String,
        game: Game,
        platform: Platform?,
        cred: ScraperCredentials,
        officialNameHint: String?,
    ): QueryOutcome = when (providerId) {
        ScraperCatalog.CoverIds.GAMETDB -> queryGameTdb(game, platform)
        ScraperCatalog.CoverIds.HASHEOUS -> queryHasheous(game, platform)
        ScraperCatalog.CoverIds.ARCADEITALIA -> queryArcadeItalia(game, platform)
        ScraperCatalog.MetaIds.WIKIDATA -> queryWikidata(game, officialNameHint)
        ScraperCatalog.CoverIds.SCREENSCRAPER -> queryScreenScraper(game, platform, cred)
        ScraperCatalog.CoverIds.THEGAMESDB -> queryTheGamesDb(game, platform, cred, officialNameHint)
        ScraperCatalog.CoverIds.IGDB -> queryIgdb(game, platform, cred, officialNameHint)
        ScraperCatalog.CoverIds.STEAMGRIDDB -> querySteamGridDb(game, cred, officialNameHint)
        else -> QueryOutcome(null)
    }

    private suspend fun throttle(providerId: String, minIntervalMs: Long) {
        val lock = rateLocks.getOrPut(providerId) { Mutex() }
        lock.withLock {
            val now = System.currentTimeMillis()
            val prev = lastRequestMs[providerId] ?: 0L
            val wait = prev + minIntervalMs - now
            if (wait > 0L) delay(wait)
            lastRequestMs[providerId] = System.currentTimeMillis()
        }
    }

    // ───────────────────────── GameTDB ─────────────────────────

    private suspend fun queryGameTdb(game: Game, platform: Platform?): QueryOutcome {
        if (!gameTdb.supportsPlatform(platform?.id)) return QueryOutcome(ScrapeHit())
        val match = gameTdb.lookup(game, platform, allowDownload = true)
            ?: return QueryOutcome(ScrapeHit())
        return QueryOutcome(match.hit.copy(matchedBySerial = match.bySerial))
    }

    // ───────────────────────── Hasheous ─────────────────────────

    private suspend fun queryHasheous(game: Game, platform: Platform?): QueryOutcome {
        val identity = identityExtractor.extract(File(game.id), platform?.id)
        val (hashKey, lookupUrl) = when {
            identity.crc32 != null -> "crc:${identity.crc32}" to "https://hasheous.org/api/v1/Lookup/ByHash/crc/${identity.crc32}?returnAllMetadata=true"
            identity.sha1 != null -> "sha1:${identity.sha1}" to "https://hasheous.org/api/v1/Lookup/ByHash/sha1/${identity.sha1}?returnAllMetadata=true"
            else -> return QueryOutcome(ScrapeHit())
        }
        hasheousHashCache[hashKey]?.let { return it }
        val lock = hasheousHashLocks[(hashKey.hashCode() and Int.MAX_VALUE) % hasheousHashLocks.size]
        return lock.withLock {
            hasheousHashCache[hashKey]?.let { return@withLock it }
            throttle(ScraperCatalog.CoverIds.HASHEOUS, 250L)
            val res = httpGet(lookupUrl)
            if (res.statusCode == 404) {
                return@withLock QueryOutcome(ScrapeHit()).also { hasheousHashCache[hashKey] = it }
            }
            if (res.transientError || res.body == null) {
                return@withLock QueryOutcome(null, transientError = true)
            }

            val hit = runCatching {
                val o = JSONObject(res.body)
                val name = o.optString("name").takeIf { it.isNotBlank() }
                val sigGame = o.optJSONObject("signature")?.optJSONObject("game")
                val year = sigGame?.optString("year")?.takeIf { it.isNotBlank() }
                val publisher = o.optJSONObject("publisher")?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: sigGame?.optString("publisher")?.takeIf { it.isNotBlank() }

                // 严格优先 BoxArt，其次 Logo，避免把透明横幅当主封面
                val boxArts = LinkedHashSet<String>()
                val otherImgs = LinkedHashSet<String>()
                val attrs = o.optJSONArray("attributes")
                if (attrs != null) {
                    for (i in 0 until attrs.length()) {
                        val a = attrs.optJSONObject(i) ?: continue
                        val type = a.optString("attributeType")
                        val attrName = a.optString("attributeName")
                        val link = a.optString("link")
                        if (!link.startsWith("/")) continue
                        val full = "https://hasheous.org$link"
                        when {
                            attrName.equals("BoxArt", ignoreCase = true) ||
                                attrName.contains("Box", ignoreCase = true) -> boxArts += full
                            type == "ImageId" || attrName.equals("Logo", ignoreCase = true) -> otherImgs += full
                        }
                    }
                }
                val urls = (boxArts + otherImgs).toList()
                ScrapeHit(
                    officialName = name,
                    meta = GameMetadata(releaseDate = year, publisher = publisher),
                    coverUrls = urls,
                )
            }.getOrNull() ?: ScrapeHit()
            QueryOutcome(hit).also { hasheousHashCache[hashKey] = it }
        }
    }

    // ───────────────────────── ArcadeItalia ─────────────────────────

    private suspend fun queryArcadeItalia(game: Game, platform: Platform?): QueryOutcome {
        val pid = platform?.id?.lowercase().orEmpty()
        if (pid !in ARCADE_PLATFORMS) return QueryOutcome(ScrapeHit())
        throttle(ScraperCatalog.CoverIds.ARCADEITALIA, 220L)
        val romName = Uri.encode(game.baseName)
        val res = httpGet("https://adb.arcadeitalia.net/service_scraper.php?ajax=query_mame&game_name=$romName")
        if (res.transientError || res.body == null) return QueryOutcome(null, transientError = true)
        val hit = runCatching {
            val arr = JSONObject(res.body).optJSONArray("result") ?: return@runCatching ScrapeHit()
            if (arr.length() == 0) return@runCatching ScrapeHit()
            parseArcadeItaliaItem(arr.getJSONObject(0)) ?: ScrapeHit()
        }.getOrDefault(ScrapeHit())
        return QueryOutcome(hit)
    }

    private fun parseArcadeItaliaItem(item: JSONObject): ScrapeHit? {
        val title = item.optString("title").takeIf { it.isNotBlank() }
        val year = item.optString("year").takeIf { it.isNotBlank() }
        val genre = item.optString("genre").takeIf { it.isNotBlank() }
        val maker = item.optString("manufacturer").takeIf { it.isNotBlank() }
        val urls = listOf(
            item.optString("url_image_flyer"),
            item.optString("url_image_title"),
            item.optString("url_image_cabinet"),
            item.optString("url_image_ingame"),
        ).filter { it.startsWith("http") }
        val hit = ScrapeHit(
            officialName = title,
            meta = GameMetadata(
                releaseDate = year,
                genre = genre,
                developer = maker,
                publisher = maker,
            ),
            coverUrls = urls,
        )
        return if (hit.isEmpty) null else hit
    }

    // ───────────────────────── Wikidata (默认关闭，支持多语言) ─────────────────────────

    private suspend fun queryWikidata(game: Game, officialNameHint: String?): QueryOutcome {
        val query = TitleNormalizer.bestApiSearchQuery(game.baseName, game.title, officialNameHint)
        if (query.length < 2) return QueryOutcome(ScrapeHit())
        throttle(ScraperCatalog.MetaIds.WIKIDATA, 300L)
        val lang = if (CjkAliases.containsCjk(query)) "zh" else "en"
        val searchUrl = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${Uri.encode(query)}&language=$lang&uselang=en&format=json&limit=5"
        val searchRes = httpGet(searchUrl)
        if (searchRes.transientError || searchRes.body == null) return QueryOutcome(null, transientError = true)

        val hit = runCatching {
            val results = JSONObject(searchRes.body).optJSONArray("search") ?: return@runCatching ScrapeHit()
            var qid: String? = null
            var label: String? = null
            var bestScore = 0.68
            var fallbackQid: String? = null
            var fallbackLabel: String? = null
            var bestFallbackScore = 0.899
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val desc = r.optString("description")
                val lbl = r.optString("label").takeIf { it.isNotBlank() } ?: continue
                val sim = TitleNormalizer.similarity(query, lbl)
                if (isWikidataGameDescription(desc)) {
                    if (sim > bestScore) {
                        bestScore = sim
                        qid = r.optString("id")
                        label = lbl
                    }
                } else if (isWikidataFallbackCandidate(desc, sim) && sim > bestFallbackScore) {
                    bestFallbackScore = sim
                    fallbackQid = r.optString("id")
                    fallbackLabel = lbl
                }
            }
            val usedFallback = qid == null && fallbackQid != null
            val chosenQid = qid ?: fallbackQid ?: return@runCatching ScrapeHit()
            val chosenLabel = label ?: fallbackLabel

            val entRes = httpGet("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$chosenQid&props=claims&format=json")
            if (entRes.body == null) return@runCatching ScrapeHit()
            val claims = JSONObject(entRes.body).optJSONObject("entities")
                ?.optJSONObject(chosenQid)?.optJSONObject("claims") ?: return@runCatching ScrapeHit()
            // 兜底候选必须带有 P400（游戏平台）属性才确认是游戏词条，防止误配非游戏百科条目
            if (usedFallback && firstClaimEntityId(claims, "P400") == null) {
                return@runCatching ScrapeHit()
            }

            val devId = firstClaimEntityId(claims, "P178")
            val pubId = firstClaimEntityId(claims, "P123")
            val genreId = firstClaimEntityId(claims, "P136")
            val release = firstClaimTime(claims, "P577")
            val coverUrls = buildList {
                firstClaimString(claims, "P18")?.let { add(wikimediaCommonsUrl(it)) }
                firstClaimString(claims, "P154")?.let { add(wikimediaCommonsUrl(it)) }
            }.distinct()

            val idsToResolve = listOfNotNull(devId, pubId, genreId).distinct()
            val labels = if (idsToResolve.isEmpty()) emptyMap() else resolveWikidataLabels(idsToResolve)

            ScrapeHit(
                coverUrls = coverUrls,
                officialName = chosenLabel,
                meta = GameMetadata(
                    releaseDate = release,
                    genre = genreId?.let { labels[it] },
                    developer = devId?.let { labels[it] },
                    publisher = pubId?.let { labels[it] },
                ),
            )
        }.getOrDefault(ScrapeHit())
        return QueryOutcome(hit)
    }

    private fun resolveWikidataLabels(ids: List<String>): Map<String, String> {
        val res = httpGet(
            "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=${ids.joinToString("|")}&props=labels&languages=en|zh&format=json",
        )
        val body = res.body ?: return emptyMap()
        return runCatching {
            val entities = JSONObject(body).optJSONObject("entities") ?: return@runCatching emptyMap()
            val preferZh = Locale.getDefault().language.equals("zh", ignoreCase = true)
            val map = HashMap<String, String>(ids.size)
            for (id in ids) {
                val labels = entities.optJSONObject(id)?.optJSONObject("labels") ?: continue
                val v = if (preferZh) {
                    labels.optJSONObject("zh")?.optString("value")?.takeIf { it.isNotBlank() }
                        ?: labels.optJSONObject("en")?.optString("value")
                } else {
                    labels.optJSONObject("en")?.optString("value")?.takeIf { it.isNotBlank() }
                        ?: labels.optJSONObject("zh")?.optString("value")
                }
                if (!v.isNullOrBlank()) map[id] = v
            }
            map
        }.getOrDefault(emptyMap())
    }

    // ───────────────────────── ScreenScraper.fr ─────────────────────────

    private suspend fun queryScreenScraper(
        game: Game,
        platform: Platform?,
        cred: ScraperCredentials,
    ): QueryOutcome {
        if (cred.ssUser.isBlank() || cred.ssPass.isBlank()) return QueryOutcome(null)
        // ScreenScraper 免费账号严格限制单线程并发，串行限流 1.1s 避免 429/430 封禁
        throttle(ScraperCatalog.CoverIds.SCREENSCRAPER, 1100L)
        val file = File(game.id)
        val identity = identityExtractor.extract(file, platform?.id)
        val sysId = SCREENSCRAPER_SYSTEM_IDS[platform?.id?.lowercase()]
        val sb = StringBuilder("https://api.screenscraper.fr/api2/jeuInfos.php")
            .append("?ssid=").append(Uri.encode(cred.ssUser))
            .append("&sspassword=").append(Uri.encode(cred.ssPass))
            .append("&softname=PocketDeck")
            .append("&output=json")
            .append("&romtype=rom")
            .append("&romnom=").append(Uri.encode(game.fileName))
        val isArchive = file.extension.lowercase() in setOf("zip", "7z")
        val effectiveRomSize = if (isArchive) identity.romSize else game.fileSize.takeIf { it > 0L }
        if (effectiveRomSize != null && effectiveRomSize > 0L) sb.append("&romtaille=").append(effectiveRomSize)
        if (identity.crc32 != null) sb.append("&crc=").append(identity.crc32)
        if (identity.sha1 != null) sb.append("&sha1=").append(identity.sha1)
        if (identity.serial != null) sb.append("&serialnum=").append(Uri.encode(identity.serial))
        if (sysId != null) sb.append("&systemeid=").append(sysId)

        val res = httpGet(sb.toString())
        if (res.statusCode == 404) return QueryOutcome(ScrapeHit())
        if (res.transientError || res.body == null) return QueryOutcome(null, transientError = true)

        val hit = runCatching {
            val jeu = JSONObject(res.body).optJSONObject("response")?.optJSONObject("jeu")
                ?: return@runCatching ScrapeHit()
            val preferZh = Locale.getDefault().language.equals("zh", ignoreCase = true)
            // 修复：noms 数组字段是 "region"（非 "langue"），genres 内部才是 "langue"
            val nomsArr = jeu.optJSONArray("noms")
            val name = extractSsByRegion(nomsArr, preferZh)
            val canonicalEn = extractSsByRegion(nomsArr, preferZh = false)
            val date = extractSsByRegion(jeu.optJSONArray("dates"), preferZh)
            val genre = jeu.optJSONArray("genres")?.optJSONObject(0)?.optJSONArray("noms")
                ?.let { extractSsByLang(it, preferZh) }
            val dev = jeu.optJSONObject("developpeur")?.optString("text")?.takeIf { it.isNotBlank() }
            val pub = jeu.optJSONObject("editeur")?.optString("text")?.takeIf { it.isNotBlank() }

            val urls = LinkedHashSet<String>()
            val medias = jeu.optJSONArray("medias")
            if (medias != null) {
                val preferredTypes = listOf("box-2D", "box-2D-front", "box-3D", "ss", "sstitle")
                val preferredRegions = if (preferZh) listOf("cn", "zh", "us", "wor", "jp", "eu") else listOf("us", "wor", "eu", "jp")
                for (type in preferredTypes) {
                    for (reg in preferredRegions) {
                        for (i in 0 until medias.length()) {
                            val m = medias.optJSONObject(i) ?: continue
                            if (m.optString("type") == type && m.optString("region").equals(reg, ignoreCase = true)) {
                                val u = m.optString("url")
                                if (u.startsWith("http")) urls += u
                            }
                        }
                    }
                    // 兜底任意区域
                    for (i in 0 until medias.length()) {
                        val m = medias.optJSONObject(i) ?: continue
                        if (m.optString("type") == type) {
                            val u = m.optString("url")
                            if (u.startsWith("http")) urls += u
                        }
                    }
                }
            }
            ScrapeHit(
                officialName = name,
                canonicalName = canonicalEn,
                meta = GameMetadata(releaseDate = date, genre = genre, developer = dev, publisher = pub),
                coverUrls = urls.take(4),
                matchedBySerial = identity.serial != null,
            )
        }.getOrDefault(ScrapeHit())
        return QueryOutcome(hit)
    }

    // ───────────────────────── TheGamesDB.net ─────────────────────────

    private suspend fun queryTheGamesDb(
        game: Game,
        platform: Platform?,
        cred: ScraperCredentials,
        officialNameHint: String?,
    ): QueryOutcome {
        if (cred.tgdbKey.isBlank()) return QueryOutcome(null)
        val q = TitleNormalizer.bestApiSearchQuery(game.baseName, game.title, officialNameHint)
        if (q.length < 2) return QueryOutcome(ScrapeHit())
        throttle(ScraperCatalog.CoverIds.THEGAMESDB, 260L)

        val sb = StringBuilder("https://api.thegamesdb.net/v1/Games/ByGameName")
            .append("?apikey=").append(Uri.encode(cred.tgdbKey))
            .append("&name=").append(Uri.encode(q))
            .append("&fields=publishers,genres,developers")
            .append("&include=boxart,developers,publishers,genres")
        TGDB_PLATFORM_IDS[platform?.id?.lowercase()]?.let { pid ->
            sb.append("&filter[platform]=").append(pid)
        }

        val res = httpGet(sb.toString())
        if (res.statusCode == 404) return QueryOutcome(ScrapeHit())
        if (res.transientError || res.body == null) return QueryOutcome(null, transientError = true)

        val hit = runCatching {
            val root = JSONObject(res.body)
            val data = root.optJSONObject("data") ?: return@runCatching ScrapeHit()
            val gamesArr = data.optJSONArray("games") ?: return@runCatching ScrapeHit()
            if (gamesArr.length() == 0) return@runCatching ScrapeHit()

            // 严格按标题相似度挑选最佳匹配，无达标候选则放弃（绝不盲兜底 [0] 误配无关游戏并锁 30 天）
            val g = selectBestTitleCandidate(
                query = q,
                candidates = gamesArr,
                titleField = "game_title",
                limit = 10,
                minScore = 0.58,
            ) ?: return@runCatching ScrapeHit()
            val gid = g.optInt("id").toString()
            val title = g.optString("game_title").takeIf { it.isNotBlank() }
            val rel = g.optString("release_date").takeIf { it.isNotBlank() }?.take(4)

            val include = root.optJSONObject("include")
            val devMap = parseTgdbLookup(include?.optJSONObject("developers")?.optJSONObject("data"))
            val pubMap = parseTgdbLookup(include?.optJSONObject("publishers")?.optJSONObject("data"))
            val genreMap = parseTgdbLookup(include?.optJSONObject("genres")?.optJSONObject("data"))

            val dev = firstLookupName(g.optJSONArray("developers"), devMap)
            val pub = firstLookupName(g.optJSONArray("publishers"), pubMap)
            val genre = firstLookupName(g.optJSONArray("genres"), genreMap)

            val boxart = include?.optJSONObject("boxart")
            val baseImg = boxart?.optJSONObject("base_url")?.optString("medium")
                ?.takeIf { it.isNotBlank() }
                ?: boxart?.optJSONObject("base_url")?.optString("thumb")
                ?: ""
            val frontUrls = LinkedHashSet<String>()
            val fallbackUrls = LinkedHashSet<String>()
            val imgArr = boxart?.optJSONObject("data")?.optJSONArray(gid)
            if (imgArr != null && baseImg.isNotBlank()) {
                for (i in 0 until imgArr.length()) {
                    val img = imgArr.optJSONObject(i) ?: continue
                    if (img.optString("type") == "boxart") {
                        val fn = img.optString("filename")
                        if (fn.isBlank()) continue
                        val side = img.optString("side").lowercase()
                        if (side == "front" || side.isEmpty()) {
                            frontUrls += baseImg + fn
                        } else if (side != "back") {
                            fallbackUrls += baseImg + fn
                        }
                    }
                }
            }
            ScrapeHit(
                officialName = title,
                meta = GameMetadata(releaseDate = rel, genre = genre, developer = dev, publisher = pub),
                coverUrls = (frontUrls + fallbackUrls).toList(),
            )
        }.getOrDefault(ScrapeHit())
        return QueryOutcome(hit)
    }

    private fun parseTgdbLookup(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val out = HashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val name = obj.optJSONObject(k)?.optString("name")?.takeIf { it.isNotBlank() }
            if (name != null) out[k] = name
        }
        return out
    }

    private fun firstLookupName(ids: JSONArray?, lookup: Map<String, String>): String? {
        if (ids == null || lookup.isEmpty()) return null
        for (i in 0 until ids.length()) {
            val idStr = ids.opt(i)?.toString() ?: continue
            lookup[idStr]?.let { return it }
        }
        return null
    }

    // ───────────────────────── IGDB.com ─────────────────────────

    private suspend fun queryIgdb(
        game: Game,
        platform: Platform?,
        cred: ScraperCredentials,
        officialNameHint: String?,
    ): QueryOutcome {
        if (cred.igdbClientId.isBlank() || cred.igdbClientSecret.isBlank()) return QueryOutcome(null)
        val q = TitleNormalizer.bestApiSearchQuery(game.baseName, game.title, officialNameHint)
            .replace("\"", "")
        if (q.length < 2) return QueryOutcome(ScrapeHit())

        val token = ensureIgdbToken(cred) ?: return QueryOutcome(null, transientError = true)
        // IGDB 限制 4 req/s，间隔 260ms 稳定不触发 429
        throttle(ScraperCatalog.CoverIds.IGDB, 260L)

        val igdbPlatformId = IGDB_PLATFORM_IDS[platform?.id?.lowercase()]
        val whereClause = if (igdbPlatformId != null) " where platforms = ($igdbPlatformId);" else ""
        val query = "search \"$q\"; fields name,first_release_date,genres.name,involved_companies.company.name,involved_companies.developer,involved_companies.publisher,cover.image_id;$whereClause limit 5;"
        val res = httpPost(
            urlStr = "https://api.igdb.com/v4/games",
            body = query,
            headers = mapOf(
                "Client-ID" to cred.igdbClientId,
                "Authorization" to "Bearer $token",
                "Accept" to "application/json",
            ),
        )
        if (res.statusCode == 401) {
            igdbToken = null
            igdbTokenExpireMs = 0L
            return QueryOutcome(null, transientError = true)
        }
        if (res.transientError || res.body == null) return QueryOutcome(null, transientError = true)

        val hit = runCatching {
            val arr = JSONArray(res.body)
            if (arr.length() == 0) return@runCatching ScrapeHit()

            val g = selectBestTitleCandidate(
                query = q,
                candidates = arr,
                titleField = "name",
                minScore = 0.58,
            ) ?: return@runCatching ScrapeHit()
            val name = g.optString("name").takeIf { it.isNotBlank() }
            val ts = g.optLong("first_release_date", 0L)
            val year = if (ts > 0) {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.timeInMillis = ts * 1000L
                cal.get(Calendar.YEAR).toString()
            } else null

            val genre = g.optJSONArray("genres")?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }
            var dev: String? = null
            var pub: String? = null
            val companies = g.optJSONArray("involved_companies")
            if (companies != null) {
                for (i in 0 until companies.length()) {
                    val c = companies.optJSONObject(i) ?: continue
                    val cname = c.optJSONObject("company")?.optString("name")?.takeIf { it.isNotBlank() } ?: continue
                    if (dev == null && c.optBoolean("developer")) dev = cname
                    if (pub == null && c.optBoolean("publisher")) pub = cname
                }
            }
            val imageId = g.optJSONObject("cover")?.optString("image_id")?.takeIf { it.isNotBlank() }
            val urls = if (imageId != null) {
                listOf("https://images.igdb.com/igdb/image/upload/t_cover_big/$imageId.jpg")
            } else emptyList()

            ScrapeHit(
                officialName = name,
                meta = GameMetadata(releaseDate = year, genre = genre, developer = dev, publisher = pub),
                coverUrls = urls,
            )
        }.getOrDefault(ScrapeHit())
        return QueryOutcome(hit)
    }

    private suspend fun ensureIgdbToken(cred: ScraperCredentials): String? = igdbTokenMutex.withLock {
        val credKey = "${cred.igdbClientId}:${cred.igdbClientSecret}"
        val now = System.currentTimeMillis()
        if (igdbToken != null && igdbTokenCredKey == credKey && now < igdbTokenExpireMs) {
            return@withLock igdbToken
        }
        val url = "https://id.twitch.tv/oauth2/token?client_id=${Uri.encode(cred.igdbClientId)}&client_secret=${Uri.encode(cred.igdbClientSecret)}&grant_type=client_credentials"
        val res = httpPost(url, "", emptyMap())
        val body = res.body ?: return@withLock null
        runCatching {
            val o = JSONObject(body)
            val token = o.optString("access_token").takeIf { it.isNotBlank() } ?: return@runCatching null
            val expires = o.optLong("expires_in", 3600L)
            igdbToken = token
            igdbTokenCredKey = credKey
            igdbTokenExpireMs = now + (expires - 60).coerceAtLeast(60) * 1000L
            token
        }.getOrNull()
    }

    // ───────────────────────── SteamGridDB ─────────────────────────

    private suspend fun querySteamGridDb(
        game: Game,
        cred: ScraperCredentials,
        officialNameHint: String?,
    ): QueryOutcome {
        if (cred.sgdbKey.isBlank()) return QueryOutcome(null)
        val q = TitleNormalizer.bestApiSearchQuery(game.baseName, game.title, officialNameHint)
        if (q.length < 2) return QueryOutcome(ScrapeHit())
        throttle(ScraperCatalog.CoverIds.STEAMGRIDDB, 250L)

        val headers = mapOf(
            "Authorization" to "Bearer ${cred.sgdbKey}",
            "Accept" to "application/json",
        )
        val searchUrl = "https://www.steamgriddb.com/api/v2/search/autocomplete/${Uri.encode(q)}"
        val searchRes = httpGet(searchUrl, headers)
        if (searchRes.statusCode == 404) return QueryOutcome(ScrapeHit())
        if (searchRes.transientError || searchRes.body == null) return QueryOutcome(null, transientError = true)

        val gameIdAndName = runCatching {
            val data = JSONObject(searchRes.body).optJSONArray("data") ?: return@runCatching null
            var bestId: Int? = null
            var bestName: String? = null
            var bestScore = 0.56
            for (i in 0 until minOf(data.length(), 8)) {
                val item = data.optJSONObject(i) ?: continue
                val name = item.optString("name")
                val sim = TitleNormalizer.similarity(q, name)
                if (sim > bestScore) {
                    bestScore = sim
                    bestId = item.optInt("id")
                    bestName = name
                }
            }
            if (bestId != null) bestId to bestName else null
        }.getOrNull() ?: return QueryOutcome(ScrapeHit())

        val gridUrl = "https://www.steamgriddb.com/api/v2/grids/game/${gameIdAndName.first}?types=static&nsfw=false&humor=false&limit=6"
        val gridRes = httpGet(gridUrl, headers)
        if (gridRes.transientError || gridRes.body == null) return QueryOutcome(null, transientError = true)

        val urls = runCatching {
            val data = JSONObject(gridRes.body).optJSONArray("data") ?: return@runCatching emptyList<String>()
            val out = LinkedHashSet<String>(4)
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val u = item.optString("thumb").takeIf { it.startsWith("http") }
                    ?: item.optString("url").takeIf { it.startsWith("http") }
                    ?: continue
                out += u
                if (out.size >= 4) break
            }
            out.toList()
        }.getOrDefault(emptyList())

        return QueryOutcome(ScrapeHit(officialName = gameIdAndName.second, coverUrls = urls))
    }

    // ───────────────────────── OkHttp Transport ─────────────────────────

    private fun httpGet(urlStr: String, headers: Map<String, String> = emptyMap()): HttpResult {
        if (!NetworkStatus.isOnline(appCtx)) {
            ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET", url = urlStr, error = "offline")
            return HttpResult(null, null, transientError = true)
        }
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val rb = Request.Builder()
                .url(urlStr)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
            headers.forEach { (k, v) -> rb.header(k, v) }
            http.newCall(rb.build()).execute().use { resp ->
                code = resp.code
                val elapsed = System.currentTimeMillis() - t0
                val bodyText = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val isTransient = resp.code == 429 || resp.code == 430 || resp.code >= 500 || resp.code == 408
                    ScrapeLogStore.log(
                        "META",
                        ScrapeLogStore.nodeFromUrl(urlStr),
                        "GET",
                        url = urlStr,
                        status = code,
                        elapsedMs = elapsed,
                        error = bodyText.take(140).ifBlank { "HTTP $code" },
                    )
                    return HttpResult(null, code, transientError = isTransient)
                }
                ScrapeLogStore.log(
                    "META",
                    ScrapeLogStore.nodeFromUrl(urlStr),
                    "GET",
                    url = urlStr,
                    status = code,
                    elapsedMs = elapsed,
                    result = "${bodyText.length}B ${bodyText.take(120)}",
                )
                HttpResult(bodyText, code, transientError = false)
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log(
                "META",
                ScrapeLogStore.nodeFromUrl(urlStr),
                "GET",
                url = urlStr,
                status = code,
                elapsedMs = System.currentTimeMillis() - t0,
                error = "${t.javaClass.simpleName}: ${t.message}",
            )
            HttpResult(null, code, transientError = true)
        }
    }

    private fun httpPost(urlStr: String, body: String, headers: Map<String, String>): HttpResult {
        if (!NetworkStatus.isOnline(appCtx)) {
            ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "POST", url = urlStr, error = "offline")
            return HttpResult(null, null, transientError = true)
        }
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val reqBody = body.toRequestBody(TEXT_PLAIN)
            val rb = Request.Builder()
                .url(urlStr)
                .post(reqBody)
                .header("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> rb.header(k, v) }
            http.newCall(rb.build()).execute().use { resp ->
                code = resp.code
                val elapsed = System.currentTimeMillis() - t0
                val bodyText = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val isTransient = resp.code == 429 || resp.code >= 500 || resp.code == 408
                    ScrapeLogStore.log(
                        "META",
                        ScrapeLogStore.nodeFromUrl(urlStr),
                        "POST",
                        url = urlStr,
                        status = code,
                        elapsedMs = elapsed,
                        error = bodyText.take(140).ifBlank { "HTTP $code" },
                    )
                    return HttpResult(null, code, transientError = isTransient)
                }
                ScrapeLogStore.log(
                    "META",
                    ScrapeLogStore.nodeFromUrl(urlStr),
                    "POST",
                    url = urlStr,
                    status = code,
                    elapsedMs = elapsed,
                    result = "${bodyText.length}B ${bodyText.take(120)}",
                )
                HttpResult(bodyText, code, transientError = false)
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log(
                "META",
                ScrapeLogStore.nodeFromUrl(urlStr),
                "POST",
                url = urlStr,
                status = code,
                elapsedMs = System.currentTimeMillis() - t0,
                error = "${t.javaClass.simpleName}: ${t.message}",
            )
            HttpResult(null, code, transientError = true)
        }
    }

    internal companion object {
        const val USER_AGENT = "PocketDeck/0.4 (Android Handheld Launcher)"
        const val MAX_MEM_HITS = 2048
        const val MAX_HASHEOUS_HASH_CACHE = 1024
        val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()

        /** 严格按标题相似度挑选最佳搜索结果，无超过阈值的候选时返回 null，绝不盲取第 0 条误配。 */
        internal fun selectBestTitleCandidate(
            query: String,
            candidates: JSONArray,
            titleField: String,
            limit: Int = candidates.length(),
            minScore: Double = 0.58,
        ): JSONObject? {
            var best: JSONObject? = null
            var bestScore = minScore
            val count = minOf(candidates.length(), limit)
            for (i in 0 until count) {
                val cand = candidates.optJSONObject(i) ?: continue
                val sim = TitleNormalizer.similarity(query, cand.optString(titleField))
                if (sim > bestScore) {
                    bestScore = sim
                    best = cand
                }
            }
            return best
        }

        val ARCADE_PLATFORMS = setOf(
            "arcade", "fbneo", "mame", "cps1", "cps2", "cps3", "neogeo",
            "pgm", "naomi", "atomiswave",
        )

        val SCREENSCRAPER_SYSTEM_IDS = mapOf(
            // Nintendo
            "nes" to 3, "fds" to 106, "snes" to 4, "sfc" to 4, "n64" to 14, "n64dd" to 122,
            "gb" to 9, "gbc" to 10, "gba" to 12, "nds" to 15, "dsi" to 15, "3ds" to 17,
            "virtualboy" to 11, "pokemini" to 211, "gamecube" to 13, "wii" to 16,
            "wiiu" to 18, "switch" to 225, "gameandwatch" to 52,
            // Sega
            "mastersystem" to 2, "megadrive" to 1, "genesis" to 1, "gamegear" to 21,
            "sg1000" to 109, "segacd" to 20, "sega32x" to 19, "saturn" to 22,
            "dreamcast" to 23, "naomi" to 56, "atomiswave" to 53,
            // Sony
            "psx" to 57, "psp" to 61, "psvita" to 62, "ps2" to 58, "ps3" to 59,
            // NEC / SNK / Bandai
            "pcengine" to 31, "pcenginecd" to 114, "supergrafx" to 105, "pcfx" to 72,
            "ngp" to 25, "ngpc" to 82, "neogeo" to 142, "neogeocd" to 70,
            "wswan" to 45, "wswanc" to 46,
            // Atari / Classic
            "atari2600" to 26, "atari5200" to 40, "atari7800" to 41, "lynx" to 28,
            "jaguar" to 27, "jaguarcd" to 171, "colecovision" to 48, "intellivision" to 115,
            "vectrex" to 102, "odyssey2" to 104, "channelf" to 80, "supervision" to 207,
            // Home Computers & Optical
            "msx" to 113, "msx2" to 116, "c64" to 66, "amiga" to 64, "amigacd32" to 130,
            "atarist" to 42, "zxspectrum" to 76, "amstradcpc" to 65, "x68000" to 79,
            "pc98" to 208, "fmtowns" to 253, "3do" to 29, "cdi" to 133,
            // Arcade & PC
            "arcade" to 75, "fbneo" to 75, "mame" to 75, "cps1" to 6, "cps2" to 7, "cps3" to 8, "pgm" to 75,
            "dos" to 135, "scummvm" to 123, "pico8" to 234, "tic80" to 222, "winlator" to 138,
        )

        val TGDB_PLATFORM_IDS = mapOf(
            "nes" to 7, "fds" to 4936, "snes" to 6, "sfc" to 6, "n64" to 3,
            "gb" to 4, "gbc" to 41, "gba" to 5, "nds" to 8, "3ds" to 4912,
            "virtualboy" to 4918, "gamecube" to 2, "wii" to 9, "wiiu" to 38, "switch" to 4971,
            "mastersystem" to 35, "megadrive" to 36, "genesis" to 18, "gamegear" to 20,
            "segacd" to 21, "sega32x" to 33, "saturn" to 17, "dreamcast" to 16,
            "psx" to 10, "psp" to 13, "psvita" to 39, "ps2" to 11, "ps3" to 12,
            "pcengine" to 34, "pcenginecd" to 4955, "neogeo" to 24, "neogeocd" to 4956,
            "ngp" to 4922, "ngpc" to 4923, "wswan" to 4925, "wswanc" to 4926,
            "atari2600" to 22, "atari5200" to 26, "atari7800" to 27, "lynx" to 4924, "jaguar" to 28,
            "3do" to 25, "arcade" to 23, "fbneo" to 23, "mame" to 23,
            "dos" to 1, "winlator" to 1, "android" to 4916,
        )

        val IGDB_PLATFORM_IDS = mapOf(
            "nes" to 18, "fds" to 51, "snes" to 19, "sfc" to 58, "n64" to 4,
            "gb" to 33, "gbc" to 22, "gba" to 24, "nds" to 20, "dsi" to 159, "3ds" to 37,
            "virtualboy" to 87, "gamecube" to 21, "wii" to 5, "wiiu" to 41, "switch" to 130,
            "mastersystem" to 64, "megadrive" to 29, "genesis" to 29, "gamegear" to 35,
            "sg1000" to 84, "segacd" to 78, "sega32x" to 30, "saturn" to 32, "dreamcast" to 23,
            "psx" to 7, "psp" to 38, "psvita" to 46, "ps2" to 8, "ps3" to 9,
            "pcengine" to 86, "pcenginecd" to 150, "supergrafx" to 128, "pcfx" to 274,
            "neogeo" to 80, "neogeocd" to 136, "ngp" to 119, "ngpc" to 120,
            "wswan" to 57, "wswanc" to 123,
            "atari2600" to 59, "atari5200" to 66, "atari7800" to 60, "lynx" to 61, "jaguar" to 62,
            "msx" to 27, "msx2" to 53, "c64" to 15, "amiga" to 16, "atarist" to 63,
            "zxspectrum" to 26, "amstradcpc" to 25, "x68000" to 121, "pc98" to 149,
            "3do" to 50, "cdi" to 117,
            "arcade" to 52, "fbneo" to 52, "mame" to 52, "cps1" to 52, "cps2" to 52, "cps3" to 52, "pgm" to 52,
            "dos" to 13, "winlator" to 6, "android" to 34,
        )

        private val WIKIDATA_GAME_DESC_KEYWORDS = listOf(
            "video game", "arcade game", "computer game", "console game", "handheld game",
            "indie game", "mobile game", "browser game", "homebrew", "rom hack",
            "platformer", "metroidvania", "roguelike", "shoot 'em up", "beat 'em up",
            "role-playing game", "fighting game", "puzzle game", "racing game",
            "action game", "adventure game", "strategy game", "simulation game",
            "visual novel", "first-person shooter", "third-person shooter",
            "电子游戏", "街机游戏", "电脑游戏", "主机游戏", "掌机游戏", "独立游戏",
        )

        private val WIKIDATA_NON_GAME_DESC_KEYWORDS = listOf(
            "video game series", "game series", "media franchise", "franchise",
            "film", "movie", "television", "tv series", "anime", "manga",
            "soundtrack", "album", "song", "musical",
            "light novel", "graphic novel", "book", "comic", "fictional character", "character",
            "video game developer", "video game publisher", "game developer", "game publisher",
            "software company", "company", "person", "actor", "musician",
            "city", "town", "village", "country", "planet", "species",
            "电影", "动画", "漫画", "小说", "专辑", "歌曲", "原声", "角色", "公司", "游戏系列",
        )

        internal fun isWikidataGameDescription(desc: String): Boolean {
            val d = desc.lowercase()
            if (WIKIDATA_NON_GAME_DESC_KEYWORDS.any { it in d }) return false
            return WIKIDATA_GAME_DESC_KEYWORDS.any { it in d }
        }

        internal fun isWikidataFallbackCandidate(desc: String, similarity: Double): Boolean {
            if (similarity < 0.90) return false
            val d = desc.lowercase()
            return WIKIDATA_NON_GAME_DESC_KEYWORDS.none { it in d }
        }

        internal fun firstClaimString(claims: JSONObject, prop: String): String? =
            claims.optJSONArray(prop)?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optString("value")
                ?.takeIf { it.isNotBlank() }

        internal fun wikimediaCommonsUrl(fileName: String): String {
            val normalized = fileName.trim().replace(' ', '_')
            val encoded = URLEncoder.encode(normalized, "UTF-8").replace("+", "%20")
            return "https://commons.wikimedia.org/wiki/Special:FilePath/$encoded?width=600"
        }

        fun firstClaimEntityId(claims: JSONObject, prop: String): String? =
            claims.optJSONArray(prop)?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")
                ?.takeIf { it.isNotBlank() }

        fun firstClaimTime(claims: JSONObject, prop: String): String? {
            val t = claims.optJSONArray(prop)?.optJSONObject(0)?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("time")
                ?: return null
            val m = Regex("""[+-]?(\d{4})""").find(t) ?: return null
            return m.groupValues[1]
        }

        fun extractSsByRegion(arr: JSONArray?, preferZh: Boolean): String? {
            if (arr == null) return null
            val order = if (preferZh) listOf("cn", "zh", "tw", "hk", "us", "wor", "ss", "eu", "jp")
            else listOf("us", "wor", "ss", "eu", "jp", "fr")
            for (reg in order) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("region").equals(reg, ignoreCase = true)) {
                        val t = o.optString("text").takeIf { it.isNotBlank() }
                        if (t != null) return t
                    }
                }
            }
            return arr.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
        }

        fun extractSsByLang(arr: JSONArray?, preferZh: Boolean): String? {
            if (arr == null) return null
            val order = if (preferZh) listOf("zh", "cn", "en", "ja", "fr") else listOf("en", "us", "ja", "fr")
            for (lang in order) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("langue").equals(lang, ignoreCase = true) ||
                        o.optString("region").equals(lang, ignoreCase = true)
                    ) {
                        val t = o.optString("text").takeIf { it.isNotBlank() }
                        if (t != null) return t
                    }
                }
            }
            return arr.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
        }
    }
}
