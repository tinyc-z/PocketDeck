package com.pocketdeck.launcher.data

import android.content.Context
import android.net.Uri
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
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
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class MetaMatchMode {
    LocalFile,
    Crc32Hash,
    SerialId,
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
    val serial: String? = null,
)

data class ScrapeProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val currentTitle: String = "",
    val failedCount: Int = 0,
)

data class ManualScrapeCandidate(
    val title: String,
    val providerId: String,
    val coverUrl: String? = null,
    val metadata: GameMetadata = GameMetadata(),
    val fallbackCoverUrls: List<String> = emptyList(),
)

/**
 * 多服务商游戏元数据仓库（Release date / Genre / Developer / Publisher / Official Name）。
 *
 * 改进要点：
 * - 单一 `libretro_db` 节点（jsDelivr → GitHub 故障转移，内存与 TSV 缓存减半，且不再丢弃无 CRC 光盘条目）
 * - 支持 `gametdb`（任天堂全系 + PS3 Serial/标题/中文名离线索引）
 * - 统一使用 [TitleNormalizer] + [CjkAliases] + 倒排词模糊打分
 * - 统一通过 [RomIdentityExtractor] 读取无头 CRC32 / 7z CRC / CHD SHA1 / 光盘卡带 Serial
 * - 批量合并 StateFlow 更新，杜绝海量游戏时的 O(N²) 整表拷贝与主线程重绘抖动
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
    private val localSeedMap = ConcurrentHashMap<String, GameMetadata>()
    private val focusApiResolved = ConcurrentHashMap<String, Boolean>()
    private val inFlightFocus = ConcurrentHashMap.newKeySet<String>()

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

    private val _scrapeProgress = MutableStateFlow(ScrapeProgress())
    val scrapeProgress: StateFlow<ScrapeProgress> = _scrapeProgress.asStateFlow()

    // 批量合并增量更新，避免高频 resolveFocusGame 引发 O(N²) Map 拷贝
    private val batchLock = Any()
    private val pendingMeta = HashMap<String, GameMetadata>()
    private val pendingNames = HashMap<String, String>()
    private val pendingInfo = HashMap<String, MetadataResolutionInfo>()
    private var batchFlushJob: Job? = null

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

    /** 注入从本地 ES-DE / Pegasus `gamelist.xml` 解析的元数据（零网络优先命中）。 */
    fun seedLocalMetadata(localMeta: Map<String, GameMetadata>) {
        if (localMeta.isEmpty()) return
        val metaUpdates = HashMap<String, GameMetadata>()
        val infoUpdates = HashMap<String, MetadataResolutionInfo>()
        for ((gid, meta) in localMeta) {
            if (meta.isEmpty) continue
            localSeedMap[gid] = meta
            if (!gameCache.containsKey(gid)) {
                gameCache[gid] = meta
                metaUpdates[gid] = meta
                infoUpdates[gid] = MetadataResolutionInfo(
                    providers = listOf("local"),
                    matchModes = listOf(MetaMatchMode.LocalFile),
                )
            }
        }
        if (metaUpdates.isNotEmpty()) {
            _metadata.value = _metadata.value + metaUpdates
            _resolutionInfo.value = _resolutionInfo.value + infoUpdates
        }
    }

    private class MutableEntry(
        var comment: String = "",
        var serial: String? = null,
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
    private data class CachedRow(
        val crc: String,
        val serial: String,
        val comment: String,
        val meta: GameMetadata,
    )

    private class SystemTable(
        val byCrc: Map<String, ResolvedRow>,
        val bySerial: Map<String, ResolvedRow>,
        val byExact: Map<String, ResolvedRow>,
        val byTitle: Map<String, ResolvedRow>,
        val byCompact: Map<String, ResolvedRow>,
        val byToken: Map<String, List<ResolvedRow>>,
    ) {
        fun find(
            baseName: String,
            title: String,
            crc: String?,
            serial: String?,
        ): TableHit? {
            if (!crc.isNullOrBlank()) {
                byCrc[crc.uppercase()]?.let { return TableHit(it, MetaMatchMode.Crc32Hash) }
            }
            if (!serial.isNullOrBlank()) {
                val up = serial.uppercase()
                (bySerial[up] ?: bySerial[up.replace("-", "")])?.let {
                    return TableHit(it, MetaMatchMode.SerialId)
                }
            }
            byExact[TitleNormalizer.exactKey(baseName)]?.let {
                return TableHit(it, MetaMatchMode.ExactFilename)
            }

            val variants = TitleNormalizer.buildQueryVariants(baseName, title)
            for (q in variants) {
                val norm = TitleNormalizer.normalizedTitle(q)
                if (norm.isNotEmpty()) {
                    byTitle[norm]?.let { return TableHit(it, MetaMatchMode.TitleMatch) }
                }
                val comp = TitleNormalizer.compactKey(q)
                if (comp.isNotEmpty()) {
                    byCompact[comp]?.let { return TableHit(it, MetaMatchMode.TitleMatch) }
                }
            }

            // 倒排词模糊匹配（防止细微标点或版本差异漏掉）
            var bestRow: ResolvedRow? = null
            var bestScore = 0.80
            val evaluated = HashSet<String>(64)
            for (q in variants.take(4)) {
                val norm = TitleNormalizer.normalizedTitle(q)
                val tokens = TitleNormalizer.tokenize(norm)
                if (tokens.isEmpty()) continue
                val counts = HashMap<ResolvedRow, Int>(32)
                for (tok in tokens) {
                    val bucket = byToken[tok] ?: continue
                    if (bucket.size > 400) continue
                    for (row in bucket) {
                        counts[row] = (counts[row] ?: 0) + 1
                    }
                }
                val minOverlap = if (tokens.size <= 2) 1 else 2
                for ((row, cnt) in counts) {
                    if (cnt < minOverlap || !evaluated.add(row.officialName)) continue
                    val sim = TitleNormalizer.similarity(q, row.officialName)
                    if (sim > bestScore) {
                        bestScore = sim
                        bestRow = row
                    }
                }
            }
            return bestRow?.let { TableHit(it, MetaMatchMode.TitleMatch) }
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
            clearPendingBatch()
            _metadata.value = emptyMap()
            _officialNames.value = emptyMap()
            _resolutionInfo.value = emptyMap()
        }
    }

    /** 清空元数据缓存：内存瞬时生效，磁盘与数据库清理由 IO 线程执行，绝不阻塞主线程。 */
    fun clearCache() {
        systemTables.clear()
        gameCache.clear()
        focusApiResolved.clear()
        inFlightFocus.clear()
        clearPendingBatch()
        _metadata.value = emptyMap()
        _officialNames.value = emptyMap()
        _resolutionInfo.value = emptyMap()

        scope.launch(Dispatchers.IO) {
            client.clearApiCache()
            runCatching { cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        }
    }

    fun refreshAll(games: List<Game>, platformResolver: (Game) -> Platform?) {
        if (!NetworkStatus.isOnline(appCtx)) {
            ensureLoaded(games, platformResolver)
            return
        }
        gameCache.clear()
        focusApiResolved.clear()
        clearPendingBatch()
        _metadata.value = emptyMap()
        _officialNames.value = emptyMap()
        _resolutionInfo.value = emptyMap()
        scope.launch(Dispatchers.IO) {
            client.clearApiCache()
            ensureLoaded(games, platformResolver)
        }
    }

    /** 预加载指定平台下可见游戏的元数据（先使用 DAT / GameTDB 与已缓存 API 结果，再按需补齐）。 */
    fun ensureLoaded(games: List<Game>, platformResolver: (Game) -> Platform?) {
        if (games.isEmpty()) return
        val enabled = providerConfigs.filter { it.enabled }
        if (enabled.isEmpty()) return
        val useLibretroDb = enabled.any { it.id == ScraperCatalog.MetaIds.LIBRETRO_DB }
        val useArcadeItalia = enabled.any { it.id == ScraperCatalog.MetaIds.ARCADEITALIA }

        scope.launch {
            val grouped = games.groupBy { platformResolver(it) }
            for ((platform, platGames) in grouped) {
                val system = platform?.libretroSystem
                if (useLibretroDb && !system.isNullOrBlank()) {
                    getOrFetchLibretroTable(system)
                }
                if (useArcadeItalia && NetworkStatus.isOnline(appCtx)) {
                    // 街机平台批量预取（每批 20 款仅 1 次 HTTP RTT）
                    client.prefetchArcadeItaliaBatch(platGames.take(60), platform)
                }
                resolveGames(platGames, platform, queryApi = false)
            }
        }
    }

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
        val useLibretroDb = enabled.any { it.id == ScraperCatalog.MetaIds.LIBRETRO_DB }
        scope.launch {
            try {
                val system = platform?.libretroSystem
                if (useLibretroDb && !system.isNullOrBlank()) {
                    getOrFetchLibretroTable(system)
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
        if (enabled.any { it.id == ScraperCatalog.MetaIds.LIBRETRO_DB } && !system.isNullOrBlank()) {
            getOrFetchLibretroTable(system)
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
        enqueueBatchUpdate(
            metaUpdates = if (!res.meta.isEmpty) mapOf(game.id to res.meta) else emptyMap(),
            nameUpdates = res.officialName?.takeIf { it.isNotBlank() }?.let { mapOf(game.id to it) }.orEmpty(),
            infoUpdates = res.info?.let { mapOf(game.id to it) }.orEmpty(),
            immediate = true,
        )
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
        var identity: RomIdentityExtractor.Identity? = null
        var description: String? = localSeedMap[g.id]?.description
        val usedProviders = LinkedHashSet<String>()
        val usedModes = LinkedHashSet<MetaMatchMode>()

        fun getIdentity(): RomIdentityExtractor.Identity {
            identity?.let { return it }
            val ext = client.extractIdentity(File(g.id), platform?.id)
            identity = ext
            return ext
        }

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
            if (description == null && !meta.description.isNullOrBlank()) {
                description = meta.description; contributed = true
            }
            if (contributed) {
                usedProviders += providerId
                usedModes += mode
            }
        }

        localSeedMap[g.id]?.let { local ->
            if (!local.isEmpty) {
                merge("local", MetaMatchMode.LocalFile, null, local)
            }
        }

        fun isComplete(): Boolean =
            officialName != null && releaseDate != null && genre != null && developer != null && publisher != null

        for (provider in enabled) {
            if (isComplete()) break
            when (provider.id) {
                ScraperCatalog.MetaIds.LIBRETRO_DB -> {
                    if (!system.isNullOrBlank()) {
                        val table = systemTables[system]
                        if (table != null) {
                            var hit = table.find(g.baseName, g.title, crc = identity?.crc32, serial = identity?.serial)
                            val allowHeavyId = queryApi || g.id.endsWith(".zip", true) || g.id.endsWith(".7z", true)
                            if (hit == null && identity == null && allowHeavyId) {
                                val id = getIdentity()
                                if (id.crc32 != null || id.serial != null) {
                                    hit = table.find(g.baseName, g.title, id.crc32, id.serial)
                                }
                            }
                            if (hit != null) {
                                merge(provider.id, hit.mode, hit.row.officialName, hit.row.meta)
                            }
                        }
                    }
                }
                ScraperCatalog.MetaIds.GAMETDB -> {
                    if (client.gameTdb.supportsPlatform(platform?.id)) {
                        val match = if (queryApi) {
                            client.gameTdb.lookup(g, platform, allowDownload = true)
                        } else {
                            client.gameTdb.lookup(g, platform, allowDownload = false)
                        }
                        if (match != null && !match.hit.isEmpty) {
                            val mode = if (match.bySerial) MetaMatchMode.SerialId else MetaMatchMode.TitleMatch
                            merge(provider.id, mode, match.hit.officialName, match.hit.meta)
                        }
                    }
                }
                else -> {
                    val desc = ScraperCatalog.metaDescriptor(provider.id)
                    if (!ScraperCatalog.hasCredentials(desc?.credentialKind, cred)) continue
                    var hit = if (queryApi) {
                        client.queryProvider(
                            providerId = provider.id,
                            game = g,
                            platform = platform,
                            cred = cred,
                            forceRefresh = forceApiRefresh,
                            officialNameHint = officialName,
                        )
                    } else {
                        client.cachedHit(provider.id, g.id)
                    }
                    // I3: 中文/汉化 ROM 首次未命中时，自动以 CjkAliases 标准化英文名二次重试
                    if (queryApi && (hit == null || hit.isEmpty) &&
                        (CjkAliases.containsCjk(g.title) || CjkAliases.containsCjk(g.baseName))
                    ) {
                        val enAlias = CjkAliases.expandEnglishAliases(g.title).firstOrNull()
                            ?: CjkAliases.expandEnglishAliases(g.baseName).firstOrNull()
                        if (!enAlias.isNullOrBlank() && !enAlias.equals(officialName, ignoreCase = true)) {
                            hit = client.queryProvider(
                                providerId = provider.id,
                                game = g,
                                platform = platform,
                                cred = cred,
                                forceRefresh = true,
                                officialNameHint = enAlias,
                            )
                        }
                    }
                    if (hit != null && !hit.isEmpty) {
                        val apiMode = when (provider.id) {
                            ScraperCatalog.MetaIds.HASHEOUS -> MetaMatchMode.ApiHash
                            ScraperCatalog.MetaIds.ARCADEITALIA -> MetaMatchMode.ApiRomName
                            ScraperCatalog.MetaIds.SCREENSCRAPER -> {
                                val id = getIdentity()
                                when {
                                    id.serial != null -> MetaMatchMode.SerialId
                                    id.crc32 != null || id.sha1 != null -> MetaMatchMode.ApiHash
                                    else -> MetaMatchMode.ApiNameSearch
                                }
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
            description = description,
        )
        val info = if (usedProviders.isNotEmpty() || identity?.crc32 != null || identity?.serial != null) {
            MetadataResolutionInfo(
                providers = usedProviders.toList(),
                matchModes = usedModes.toList(),
                crc32 = identity?.crc32,
                serial = identity?.serial,
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

        val pending = if (queryApi) games else games.filter { !gameCache.containsKey(it.id) }
        val trackProgress = pending.size > 1
        var doneCount = 0
        var failCount = 0
        if (trackProgress) {
            _scrapeProgress.value = ScrapeProgress(
                running = true,
                done = 0,
                total = pending.size,
                currentTitle = pending.firstOrNull()?.title.orEmpty(),
                failedCount = 0,
            )
        }

        try {
            for (g in pending) {
                if (trackProgress) {
                    _scrapeProgress.value = ScrapeProgress(
                        running = true,
                        done = doneCount,
                        total = pending.size,
                        currentTitle = g.title,
                        failedCount = failCount,
                    )
                }
                val res = resolveSingleGame(g, platform, enabled, cred, queryApi = queryApi)
                gameCache[g.id] = res.meta
                if (!res.meta.isEmpty) {
                    updates[g.id] = res.meta
                } else {
                    failCount++
                }
                val off = res.officialName
                if (!off.isNullOrBlank()) {
                    nameUpdates[g.id] = off
                }
                res.info?.let { infoUpdates[g.id] = it }
                doneCount++
            }
        } finally {
            if (trackProgress) {
                _scrapeProgress.value = ScrapeProgress(
                    running = false,
                    done = doneCount,
                    total = pending.size,
                    currentTitle = "",
                    failedCount = failCount,
                )
            }
        }

        if (updates.isNotEmpty() || nameUpdates.isNotEmpty() || infoUpdates.isNotEmpty()) {
            enqueueBatchUpdate(updates, nameUpdates, infoUpdates, immediate = games.size > 4)
        }
    }

    /** 手动关键词搜索刮削：综合 Libretro Boxart 索引与已启用的在线刮削节点返回候选列表。 */
    suspend fun manualSearch(
        keyword: String,
        game: Game,
        platform: Platform?,
        covers: CoverSource,
        onSourceStart: (String) -> Unit = {},
    ): List<ManualScrapeCandidate> = withContext(Dispatchers.IO) {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isEmpty()) return@withContext emptyList()
        val synthetic = game.copy(
            id = "${game.id}#manual#${cleanKeyword.lowercase()}",
            baseName = cleanKeyword,
            title = cleanKeyword,
        )
        val out = ArrayList<ManualScrapeCandidate>()
        val seen = HashSet<String>()

        fun addCandidate(c: ManualScrapeCandidate) {
            val key = "${c.title.lowercase()}|${c.coverUrl.orEmpty()}"
            if (seen.add(key)) out += c
        }

        // 1) Libretro Boxarts 索引候选（秒级出图）
        onSourceStart(ScraperCatalog.CoverIds.LIBRETRO_BOXARTS)
        val boxarts = runCatching { covers.searchIndexCandidates(cleanKeyword, platform) }.getOrDefault(emptyList())
        val system = platform?.libretroSystem
        val table = system?.let { systemTables[it] }
        for (match in boxarts) {
            val meta = table?.find(match.title, match.title, null, null)?.row?.meta
                ?: GameMetadata()
            addCandidate(
                ManualScrapeCandidate(
                    title = match.title,
                    providerId = ScraperCatalog.CoverIds.LIBRETRO_BOXARTS,
                    coverUrl = match.url,
                    metadata = meta,
                    fallbackCoverUrls = match.fallbackUrls,
                ),
            )
        }

        // 2) 在线 API 节点候选
        val cred = credentials
        val apiProviders = listOf(
            ScraperCatalog.MetaIds.GAMETDB,
            ScraperCatalog.MetaIds.THEGAMESDB,
            ScraperCatalog.MetaIds.SCREENSCRAPER,
            ScraperCatalog.MetaIds.IGDB,
            ScraperCatalog.MetaIds.WIKIDATA,
            ScraperCatalog.CoverIds.STEAMGRIDDB,
        )
        for (pid in apiProviders) {
            if (pid == ScraperCatalog.MetaIds.GAMETDB && !client.gameTdb.supportsPlatform(platform?.id)) continue
            val desc = ScraperCatalog.metaDescriptor(pid) ?: ScraperCatalog.coverDescriptor(pid)
            if (!ScraperCatalog.hasCredentials(desc?.credentialKind, cred)) continue
            onSourceStart(pid)
            val hit = runCatching {
                client.queryProvider(
                    providerId = pid,
                    game = synthetic,
                    platform = platform,
                    cred = cred,
                    forceRefresh = true,
                    officialNameHint = cleanKeyword,
                )
            }.getOrNull() ?: continue
            if (!hit.isEmpty || hit.coverUrls.isNotEmpty()) {
                val title = hit.officialName?.takeIf { it.isNotBlank() } ?: cleanKeyword
                if (hit.coverUrls.isNotEmpty()) {
                    for (url in hit.coverUrls.take(3)) {
                        addCandidate(ManualScrapeCandidate(title, pid, url, hit.meta))
                    }
                } else {
                    addCandidate(ManualScrapeCandidate(title, pid, null, hit.meta))
                }
            }
        }
        out.take(15)
    }

    /** 一键应用手动搜索刮削结果（封面 + 元数据 + 官方名）。 */
    fun applyManualScrape(
        gameId: String,
        candidate: ManualScrapeCandidate,
        covers: CoverSource,
    ) {
        if (!candidate.metadata.isEmpty) {
            gameCache[gameId] = candidate.metadata
            _metadata.value = _metadata.value + (gameId to candidate.metadata)
        }
        if (candidate.title.isNotBlank()) {
            _officialNames.value = _officialNames.value + (gameId to candidate.title)
        }
        _resolutionInfo.value = _resolutionInfo.value + (
            gameId to MetadataResolutionInfo(
                providers = listOf(candidate.providerId),
                matchModes = listOf(MetaMatchMode.ApiNameSearch),
            )
        )
        covers.applyManualCover(gameId, candidate.coverUrl, candidate.providerId)
    }

    private fun clearPendingBatch() {
        synchronized(batchLock) {
            pendingMeta.clear()
            pendingNames.clear()
            pendingInfo.clear()
            batchFlushJob?.cancel()
            batchFlushJob = null
        }
    }

    private fun enqueueBatchUpdate(
        metaUpdates: Map<String, GameMetadata>,
        nameUpdates: Map<String, String>,
        infoUpdates: Map<String, MetadataResolutionInfo>,
        immediate: Boolean,
    ) {
        synchronized(batchLock) {
            pendingMeta.putAll(metaUpdates)
            pendingNames.putAll(nameUpdates)
            pendingInfo.putAll(infoUpdates)
            if (immediate) {
                batchFlushJob?.cancel()
                batchFlushJob = null
                flushBatchLocked()
            } else if (batchFlushJob?.isActive != true) {
                batchFlushJob = scope.launch {
                    delay(140L)
                    synchronized(batchLock) {
                        batchFlushJob = null
                        flushBatchLocked()
                    }
                }
            }
        }
    }

    private fun flushBatchLocked() {
        if (pendingMeta.isNotEmpty()) {
            val delta = HashMap(pendingMeta)
            pendingMeta.clear()
            _metadata.value = _metadata.value + delta
        }
        if (pendingNames.isNotEmpty()) {
            val delta = HashMap(pendingNames)
            pendingNames.clear()
            _officialNames.value = _officialNames.value + delta
        }
        if (pendingInfo.isNotEmpty()) {
            val delta = HashMap(pendingInfo)
            pendingInfo.clear()
            _resolutionInfo.value = _resolutionInfo.value + delta
        }
    }

    private suspend fun getOrFetchLibretroTable(system: String): SystemTable? {
        systemTables[system]?.let { return it }
        val lock = systemLocks.getOrPut(system) { Mutex() }
        return lock.withLock {
            systemTables[system]?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val cacheFile = File(cacheDir, "v3_${sanitizeFileName(system)}.tsv")
                val table = if (cacheFile.isFile && cacheFile.length() > 0) {
                    readCachedTsv(cacheFile)
                } else if (NetworkStatus.isOnline(appCtx)) {
                    fetchFromLibretroWithFailover(system, cacheFile)
                } else {
                    null
                }
                if (table != null) {
                    systemTables[system] = table
                }
                table
            }
        }
    }

    private suspend fun fetchFromLibretroWithFailover(
        system: String,
        cacheFile: File,
    ): SystemTable? = coroutineScope {
        val encodedSys = Uri.encode(system)
        val mirrors = listOf(
            "https://cdn.jsdelivr.net/gh/libretro/libretro-database@master/metadat",
            "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat",
        )
        val kinds = listOf("developer", "publisher", "genre", "releaseyear", "releasemonth")

        // 优先走 jsDelivr；单个 kind 若遇到网络/5xx 故障自动回退 GitHub Raw（404 不重复请求）
        val parsed = kinds.map { kind ->
            async(Dispatchers.IO) {
                var res = downloadDat("${mirrors[0]}/$kind/$encodedSys.dat")
                if (res.transientError) {
                    res = downloadDat("${mirrors[1]}/$kind/$encodedSys.dat")
                }
                kind to res.entries
            }
        }.awaitAll()

        if (parsed.all { it.second.isEmpty() }) return@coroutineScope null

        val merged = LinkedHashMap<String, MutableEntry>(4096)
        val byTitleTemp = HashMap<String, MutableEntry>(2048)

        for ((kind, entries) in parsed) {
            for (e in entries) {
                // 修复：即使无 CRC（如 PS1/PS2/PSP/GC/Wii 光盘条目）也以 serial 或 title 为主键保留，不再丢弃！
                val key = when {
                    e.crc.isNotEmpty() -> "c:${e.crc}"
                    e.serial.isNotEmpty() -> "s:${e.serial}"
                    e.comment.isNotBlank() -> "n:${TitleNormalizer.exactKey(e.comment)}"
                    else -> continue
                }
                val target = merged.getOrPut(key) {
                    MutableEntry(comment = e.comment, serial = e.serial.takeIf { it.isNotEmpty() })
                }
                if (target.comment.isBlank() && e.comment.isNotBlank()) target.comment = e.comment
                if (target.serial == null && e.serial.isNotEmpty()) target.serial = e.serial
                when (kind) {
                    "developer" -> target.developer = e.value
                    "publisher" -> target.publisher = e.value
                    "genre" -> target.genre = e.value
                    "releaseyear" -> target.year = e.value
                    "releasemonth" -> target.month = e.value
                }
                val titleKey = TitleNormalizer.normalizedTitle(e.comment)
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
            val agg = byTitleTemp[TitleNormalizer.normalizedTitle(entry.comment)] ?: continue
            if (entry.developer == null) entry.developer = agg.developer
            if (entry.publisher == null) entry.publisher = agg.publisher
            if (entry.genre == null) entry.genre = agg.genre
            if (entry.year == null) entry.year = agg.year
            if (entry.month == null) entry.month = agg.month
        }

        val rows = ArrayList<CachedRow>(merged.size)
        for ((key, entry) in merged) {
            val m = entry.toMetadata()
            if (m.isEmpty) continue
            val crc = if (key.startsWith("c:")) key.removePrefix("c:") else ""
            rows += CachedRow(crc = crc, serial = entry.serial.orEmpty(), comment = entry.comment, meta = m)
        }
        if (rows.isEmpty()) return@coroutineScope null

        // 修复：只要有任一 DAT 维度成功拉取就写本地缓存，避免小众平台每次启动都重复联网拉取
        val succeededKinds = parsed.count { it.second.isNotEmpty() }
        if (succeededKinds >= 1) {
            runCatching {
                val tmp = File(cacheDir, "${cacheFile.name}.tmp")
                tmp.bufferedWriter().use { out ->
                    for (r in rows) {
                        out.append(r.crc).append('\t')
                            .append(r.serial.replace('\t', ' ')).append('\t')
                            .append(r.comment.replace('\t', ' ')).append('\t')
                            .append((r.meta.releaseDate ?: "").replace('\t', ' ')).append('\t')
                            .append((r.meta.genre ?: "").replace('\t', ' ')).append('\t')
                            .append((r.meta.developer ?: "").replace('\t', ' ')).append('\t')
                            .append((r.meta.publisher ?: "").replace('\t', ' ')).append('\n')
                    }
                }
                tmp.renameTo(cacheFile)
            }
        }

        buildTable(rows)
    }

    private fun readCachedTsv(file: File): SystemTable? = runCatching {
        val rows = ArrayList<CachedRow>(2048)
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                val parts = line.split('\t')
                if (parts.size < 7) continue
                val meta = GameMetadata(
                    releaseDate = parts[3].takeIf { it.isNotEmpty() },
                    genre = parts[4].takeIf { it.isNotEmpty() },
                    developer = parts[5].takeIf { it.isNotEmpty() },
                    publisher = parts[6].takeIf { it.isNotEmpty() },
                )
                if (!meta.isEmpty) {
                    rows += CachedRow(
                        crc = parts[0],
                        serial = parts[1],
                        comment = parts[2],
                        meta = meta,
                    )
                }
            }
        }
        if (rows.isEmpty()) null else buildTable(rows)
    }.getOrNull()

    private fun buildTable(rows: List<CachedRow>): SystemTable {
        val byCrc = HashMap<String, ResolvedRow>(rows.size)
        val bySerial = HashMap<String, ResolvedRow>(rows.size / 4 + 16)
        val byExact = HashMap<String, ResolvedRow>(rows.size)
        val byTitle = HashMap<String, ResolvedRow>(rows.size)
        val byCompact = HashMap<String, ResolvedRow>(rows.size)
        val byToken = HashMap<String, MutableList<ResolvedRow>>(rows.size)

        for (r in rows) {
            if (r.meta.isEmpty) continue
            val row = ResolvedRow(r.comment, r.meta)
            if (r.crc.isNotEmpty()) byCrc.putIfAbsent(r.crc.uppercase(), row)
            if (r.serial.isNotEmpty()) {
                val up = r.serial.uppercase()
                bySerial.putIfAbsent(up, row)
                bySerial.putIfAbsent(up.replace("-", ""), row)
            }
            if (r.comment.isNotBlank()) {
                byExact.putIfAbsent(TitleNormalizer.exactKey(r.comment), row)
                val titleKey = TitleNormalizer.normalizedTitle(r.comment)
                if (titleKey.isNotEmpty()) {
                    val prev = byTitle[titleKey]
                    if (prev == null || fieldCount(r.meta) > fieldCount(prev.meta)) {
                        byTitle[titleKey] = row
                    }
                    val comp = TitleNormalizer.compactKey(r.comment)
                    if (comp.isNotEmpty()) {
                        val prevC = byCompact[comp]
                        if (prevC == null || fieldCount(r.meta) > fieldCount(prevC.meta)) {
                            byCompact[comp] = row
                        }
                    }
                    for (tok in TitleNormalizer.tokenize(titleKey)) {
                        byToken.getOrPut(tok) { ArrayList(4) }.add(row)
                    }
                }
            }
        }
        return SystemTable(byCrc, bySerial, byExact, byTitle, byCompact, byToken)
    }

    private data class DatEntry(
        val comment: String,
        val value: String,
        val crc: String,
        val serial: String,
    )

    private data class DatDownloadResult(
        val entries: List<DatEntry>,
        val transientError: Boolean,
    )

    private fun downloadDat(urlStr: String): DatDownloadResult {
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val req = Request.Builder()
                .url(urlStr)
                .header("User-Agent", USER_AGENT)
                .build()
            client.http.newCall(req).execute().use { resp ->
                code = resp.code
                if (code == 404) {
                    ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET DAT", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, result = "404 not found")
                    return DatDownloadResult(emptyList(), transientError = false)
                }
                if (!resp.isSuccessful) {
                    ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET DAT", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "HTTP $code")
                    return DatDownloadResult(emptyList(), transientError = true)
                }
                val body = resp.body ?: return DatDownloadResult(emptyList(), transientError = true)
                val list = ArrayList<DatEntry>(2048)
                body.charStream().buffered().use { reader ->
                    var inGame = false
                    var comment = ""
                    var nameFallback = ""
                    var value = ""
                    var crc = ""
                    var serial = ""
                    reader.forEachLine { raw ->
                        val line = raw.trim()
                        when {
                            line == "game (" -> {
                                inGame = true
                                comment = ""
                                nameFallback = ""
                                value = ""
                                crc = ""
                                serial = ""
                            }
                            inGame && line == ")" -> {
                                val title = comment.ifBlank { nameFallback }
                                if (value.isNotEmpty() && (crc.isNotEmpty() || serial.isNotEmpty() || title.isNotBlank())) {
                                    list += DatEntry(title, value, crc.uppercase(), serial.uppercase())
                                }
                                inGame = false
                            }
                            inGame && line.startsWith("comment ") -> {
                                comment = extractQuoted(line)
                            }
                            inGame && line.startsWith("name ") -> {
                                nameFallback = extractQuoted(line)
                            }
                            inGame && (
                                line.startsWith("developer ") || line.startsWith("publisher ") ||
                                    line.startsWith("genre ") || line.startsWith("releaseyear ") ||
                                    line.startsWith("releasemonth ")
                                ) -> {
                                value = extractQuoted(line)
                            }
                            inGame && line.startsWith("rom (") -> {
                                CRC_REGEX.find(line)?.let { crc = it.groupValues[1] }
                                SERIAL_REGEX.find(line)?.let { serial = it.groupValues[1] }
                            }
                        }
                    }
                }
                ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET DAT", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, result = "${list.size} entries")
                DatDownloadResult(list, transientError = false)
            }
        } catch (t: Throwable) {
            ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET DAT", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}")
            DatDownloadResult(emptyList(), transientError = true)
        }
    }

    private companion object {
        const val USER_AGENT = "PocketDeck/0.4 (Android Handheld Launcher)"
        val CRC_REGEX = Regex("""\bcrc\s+([0-9A-Fa-f]{8})\b""")
        val SERIAL_REGEX = Regex("""\bserial\s+"([^"]+)"""")
        val MONTHS = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )

        fun extractQuoted(line: String): String {
            val start = line.indexOf('"')
            val end = line.lastIndexOf('"')
            return if (start >= 0 && end > start) line.substring(start + 1, end) else ""
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
