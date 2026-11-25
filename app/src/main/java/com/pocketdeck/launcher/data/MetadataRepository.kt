package com.pocketdeck.launcher.data

import android.content.Context
import android.net.Uri
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.launch.GameLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
    val currentPlatform: String = "",
    val updatedCount: Int = 0,
    val failedCount: Int = 0,
    val cancelling: Boolean = false,
    val cancelled: Boolean = false,
    val finishedAtMs: Long = 0L,
) {
    val percent: Int
        get() = if (total <= 0) 0 else ((done * 100L) / total).toInt().coerceIn(0, 100)
}

/** 刮削前的缺失项统计预览（只读、零网络）。 */
data class ScrapeMissingSummary(
    val totalGames: Int = 0,
    val pendingTotal: Int = 0,
    val missingCoverCount: Int = 0,
    val missingMetaCount: Int = 0,
)

data class ManualScrapeCandidate(
    val title: String,
    val providerId: String,
    val coverUrl: String? = null,
    val metadata: GameMetadata = GameMetadata(),
    val fallbackCoverUrls: List<String> = emptyList(),
    val matchedByHash: Boolean = false,
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
    private val cacheDir = File(appCtx.cacheDir, "metadata")
    private val systemTables = ConcurrentHashMap<String, SystemTable>()
    private val systemTableLastUsedMs = ConcurrentHashMap<String, Long>()
    private val systemLocks = ConcurrentHashMap<String, Mutex>()
    private val remoteMetaRefreshInFlight = ConcurrentHashMap<String, Boolean>()
    private val gameCache = ConcurrentHashMap<String, GameMetadata>()
    private val metaMissStates = ConcurrentHashMap<String, MetaMissState>()
    private val localSeedMap = ConcurrentHashMap<String, GameMetadata>()
    private val manualMetaIds = ConcurrentHashMap.newKeySet<String>()
    /** 英文/国际标准检索名（独立于本地化展示名，用于跨源查英文 API 与 Libretro Boxarts）。 */
    private val canonicalNames = ConcurrentHashMap<String, String>()
    private val focusApiResolved = ConcurrentHashMap<String, Boolean>()
    private val inFlightFocus = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var ensureLoadedJob: Job? = null

    private data class MetaMissState(
        val expiresAtMs: Long,
        val focusChecked: Boolean,
    )

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

    private val _newlyResolvedNames = MutableSharedFlow<Set<String>>(extraBufferCapacity = 32)
    /** 增量通知本批新解析出官方名的 gameId 集合，避免下游对全量 officialNames Map 做 O(N) diff。 */
    val newlyResolvedNames: SharedFlow<Set<String>> = _newlyResolvedNames.asSharedFlow()

    private val _resolutionInfo = MutableStateFlow<Map<String, MetadataResolutionInfo>>(emptyMap())
    val resolutionInfo: StateFlow<Map<String, MetadataResolutionInfo>> = _resolutionInfo.asStateFlow()

    private val _scrapeProgress = MutableStateFlow(ScrapeProgress())
    val scrapeProgress: StateFlow<ScrapeProgress> = _scrapeProgress.asStateFlow()

    @Volatile
    private var missingScrapeJob: Job? = null
    private val scrapeStartMutex = Mutex()
    private val scrapeRunSeq = AtomicLong(0L)

    // 批量合并增量更新，避免高频 resolveFocusGame 引发 O(N²) Map 拷贝
    private val batchLock = Any()
    private val pendingMeta = HashMap<String, GameMetadata>()
    private val pendingNames = HashMap<String, String>()
    private val pendingInfo = HashMap<String, MetadataResolutionInfo>()
    private var batchFlushJob: Job? = null
    private val initialCacheReady = CompletableDeferred<Unit>()

    init {
        // 后台异步恢复持久化的自动/手动刮削元数据，避免冷启阻塞主线程且已刮削游戏无需再解压 25MB 离线库
        scope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    val metaPrefs = appCtx.getSharedPreferences("cover_matcher_meta", Context.MODE_PRIVATE)
                    if (metaPrefs != null && metaPrefs.getInt("meta_matcher_ver", 0) < CoverSource.MATCHER_VERSION) {
                        client.database.clearResolvedMetadataMisses()
                        metaPrefs.edit().putInt("meta_matcher_ver", CoverSource.MATCHER_VERSION).apply()
                    }
                }
                val now = System.currentTimeMillis()
                val savedResolved = client.database.loadAllResolvedMetadata(now)
                val savedManual = client.database.loadAllManualMetadata()
                if (savedResolved.isNotEmpty() || savedManual.isNotEmpty()) {
                    val totalSize = savedResolved.size + savedManual.size
                    val metaBatch = HashMap<String, GameMetadata>(totalSize)
                    val nameBatch = HashMap<String, String>(totalSize)
                    val infoBatch = HashMap<String, MetadataResolutionInfo>(totalSize)
                    for (row in savedResolved) {
                        if (!row.meta.isEmpty) {
                            gameCache[row.gameId] = row.meta
                            metaMissStates.remove(row.gameId)
                            metaBatch[row.gameId] = row.meta
                        } else if (row.status == ScrapeDatabase.STATUS_MISS) {
                            metaMissStates[row.gameId] = MetaMissState(
                                expiresAtMs = row.expiresAtMs,
                                focusChecked = row.focusChecked,
                            )
                        } else if (!row.officialName.isNullOrBlank()) {
                            // 兼容旧版落库的“仅有官方名、无元数据”记录（如 PS1/PS2/MAME 等 M=0 平台），避免冷启重复解压离线表
                            metaMissStates[row.gameId] = MetaMissState(
                                expiresAtMs = now + ScrapeDatabase.TTL_MISS_MS,
                                focusChecked = false,
                            )
                        }
                        if (!row.officialName.isNullOrBlank()) {
                            nameBatch[row.gameId] = row.officialName
                        }
                        val canon = row.canonicalName?.takeIf { it.isNotBlank() } ?: row.officialName
                        if (!canon.isNullOrBlank()) {
                            canonicalNames[row.gameId] = canon
                        }
                        if (!row.meta.isEmpty || !row.officialName.isNullOrBlank()) {
                            infoBatch[row.gameId] = MetadataResolutionInfo(
                                providers = listOf(row.providerId),
                                matchModes = listOf(MetaMatchMode.TitleMatch),
                            )
                        }
                    }
                    for (row in savedManual) {
                        manualMetaIds.add(row.gameId)
                        metaMissStates.remove(row.gameId)
                        if (!row.meta.isEmpty) {
                            gameCache[row.gameId] = row.meta
                            metaBatch[row.gameId] = row.meta
                        }
                        if (!row.officialName.isNullOrBlank()) {
                            nameBatch[row.gameId] = row.officialName
                        }
                        val canon = row.canonicalName?.takeIf { it.isNotBlank() } ?: row.officialName
                        if (!canon.isNullOrBlank()) {
                            canonicalNames[row.gameId] = canon
                        }
                        infoBatch[row.gameId] = MetadataResolutionInfo(
                            providers = listOf(row.providerId),
                            matchModes = listOf(MetaMatchMode.ApiNameSearch),
                        )
                    }
                    if (metaBatch.isNotEmpty() || nameBatch.isNotEmpty() || infoBatch.isNotEmpty()) {
                        // 启动恢复已落库的历史官方名不属于“新解析结果”，不发射 newlyResolvedNames 避免误清封面负缓存
                        enqueueBatchUpdate(metaBatch, nameBatch, infoBatch, immediate = true, emitNewlyResolved = false)
                    }
                }
            } finally {
                initialCacheReady.complete(Unit)
            }
        }
        scope.launch {
            var prev = NetworkStatus.online.value
            NetworkStatus.online.collect { now ->
                if (now && !prev) {
                    focusApiResolved.clear()
                    client.hostCircuitBreaker.resetAll()
                }
                prev = now
            }
        }
    }

    /** 返回最适合用于封面/英文 API 跨源检索的标准名（优先英文 canonicalName，其次已解析 officialName）。 */
    fun bestSearchNameFor(gameId: String): String? =
        canonicalNames[gameId]?.takeIf { it.isNotBlank() }
            ?: _officialNames.value[gameId]?.takeIf { it.isNotBlank() }

    suspend fun computeCrc32(path: String): String? = withContext(Dispatchers.IO) {
        client.computeRomCrc32(File(path))
    }

    private fun isOfflineMissFresh(gameId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val state = metaMissStates[gameId] ?: return false
        if (state.expiresAtMs in 1L..nowMs) {
            metaMissStates.remove(gameId, state)
            return false
        }
        return true
    }

    private fun isFullMissFresh(gameId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val state = metaMissStates[gameId] ?: return false
        if (state.expiresAtMs in 1L..nowMs) {
            metaMissStates.remove(gameId, state)
            return false
        }
        return state.focusChecked
    }

    private fun recordMetaMiss(gameId: String, focusChecked: Boolean, nowMs: Long): MetaMissState {
        val expiresAt = nowMs + ScrapeDatabase.TTL_MISS_MS
        return metaMissStates.compute(gameId) { _, prev ->
            val keepFocus = prev != null && prev.focusChecked && (prev.expiresAtMs == 0L || prev.expiresAtMs > nowMs)
            MetaMissState(
                expiresAtMs = maxOf(expiresAt, prev?.expiresAtMs ?: 0L),
                focusChecked = focusChecked || keepFocus,
            )
        }!!
    }

    /** 注入从本地 ES-DE / Pegasus `gamelist.xml` 解析的元数据（零网络优先命中）。 */
    fun seedLocalMetadata(localMeta: Map<String, GameMetadata>) {
        if (localMeta.isEmpty()) return
        val metaUpdates = HashMap<String, GameMetadata>()
        val infoUpdates = HashMap<String, MetadataResolutionInfo>()
        for ((gid, meta) in localMeta) {
            if (meta.isEmpty) continue
            localSeedMap[gid] = meta
            metaMissStates.remove(gid)
            if (gameCache[gid]?.isEmpty != false) {
                gameCache[gid] = meta
                metaUpdates[gid] = meta
                infoUpdates[gid] = MetadataResolutionInfo(
                    providers = listOf("local"),
                    matchModes = listOf(MetaMatchMode.LocalFile),
                )
            }
        }
        if (metaUpdates.isNotEmpty()) {
            _metadata.update { it + metaUpdates }
            _resolutionInfo.update { it + infoUpdates }
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

    private class OverlayTable(
        val byCrc: Map<String, ResolvedRow>,
        val bySerial: Map<String, ResolvedRow>,
        val byExact: Map<String, ResolvedRow>,
        val byTitle: Map<String, ResolvedRow>,
        val byCompact: Map<String, ResolvedRow>,
    ) {
        @Volatile
        private var _byToken: Map<String, List<ResolvedRow>>? = null

        fun ensureTokenIndexBuilt(): Map<String, List<ResolvedRow>> {
            _byToken?.let { return it }
            synchronized(this) {
                _byToken?.let { return it }
                val map = HashMap<String, MutableList<ResolvedRow>>(byTitle.size + 16)
                for ((normKey, row) in byTitle) {
                    for (tok in TitleNormalizer.tokenize(normKey)) {
                        map.getOrPut(tok) { ArrayList(4) }.add(row)
                    }
                }
                _byToken = map
                return map
            }
        }
    }

    /**
     * 零拷贝分层平台元数据表：直接共享 [OfflineGameDb.PlatformTable] 底座，
     * 远端 `.dat`/缓存仅作为轻量化 [OverlayTable] 叠加，消除 2~4 倍重复内存拷贝与重复建表。
     */
    private class SystemTable(
        val offline: OfflineGameDb.PlatformTable?,
        val overlay: OverlayTable?,
    ) {
        private fun mergeRows(primary: ResolvedRow?, fallback: ResolvedRow?): ResolvedRow? {
            if (primary == null) return fallback
            if (fallback == null) return primary
            return ResolvedRow(
                officialName = primary.officialName.ifBlank { fallback.officialName },
                meta = OfflineGameDb.mergeMeta(primary.meta, fallback.meta),
            )
        }

        private fun offlineRowFor(name: String): ResolvedRow {
            val base = offline ?: return ResolvedRow(name, GameMetadata())
            val direct = base.metaByRawName[name]
                ?: base.rowByExact[TitleNormalizer.exactKey(name)]?.meta
                ?: base.rowByNorm[TitleNormalizer.normalizedTitle(name)]?.meta
                ?: GameMetadata()
            return ResolvedRow(name, direct)
        }

        private fun enrichRow(row: ResolvedRow): ResolvedRow {
            if (!row.meta.isEmpty || row.officialName.isBlank()) return row
            val exactKey = TitleNormalizer.exactKey(row.officialName)
            val normKey = TitleNormalizer.normalizedTitle(row.officialName)
            val extraOverlay = overlay?.let { it.byExact[exactKey] ?: it.byTitle[normKey] }
            val extraOffline = offline?.let { it.rowByExact[exactKey] ?: it.rowByNorm[normKey] }
                ?.let { ResolvedRow(it.name, it.meta) }
            val extra = mergeRows(extraOverlay, extraOffline)
            return if (extra != null && !extra.meta.isEmpty) {
                ResolvedRow(row.officialName, OfflineGameDb.mergeMeta(row.meta, extra.meta))
            } else {
                row
            }
        }

        fun find(
            baseName: String,
            title: String,
            crc: String?,
            serial: String?,
        ): TableHit? {
            if (!crc.isNullOrBlank()) {
                val up = crc.uppercase()
                val ovHit = overlay?.byCrc?.get(up)
                val offHit = offline?.hashToName?.get(up)?.let(::offlineRowFor)
                val combined = mergeRows(ovHit, offHit)
                if (combined != null) {
                    return TableHit(enrichRow(combined), MetaMatchMode.Crc32Hash)
                }
            }
            if (!serial.isNullOrBlank()) {
                val up = serial.uppercase()
                val ovHit = overlay?.let { it.bySerial[up] ?: it.bySerial[up.replace("-", "")] }
                if (ovHit != null) {
                    return TableHit(enrichRow(ovHit), MetaMatchMode.SerialId)
                }
            }
            val exactKey = TitleNormalizer.exactKey(baseName)
            if (exactKey.isNotEmpty()) {
                val ovExact = overlay?.byExact?.get(exactKey)
                val offExact = offline?.rowByExact?.get(exactKey)?.let { ResolvedRow(it.name, it.meta) }
                mergeRows(ovExact, offExact)?.let {
                    return TableHit(it, MetaMatchMode.ExactFilename)
                }
            }

            val variants = TitleNormalizer.buildQueryVariants(baseName, title)
            for (q in variants) {
                val norm = TitleNormalizer.normalizedTitle(q)
                if (norm.isNotEmpty()) {
                    val ovNorm = overlay?.byTitle?.get(norm)
                    val offNorm = offline?.rowByNorm?.get(norm)?.let { ResolvedRow(it.name, it.meta) }
                    mergeRows(ovNorm, offNorm)?.let { return TableHit(it, MetaMatchMode.TitleMatch) }

                    val comp = TitleNormalizer.compactFromNorm(norm)
                    if (comp.isNotEmpty()) {
                        val ovComp = overlay?.byCompact?.get(comp)
                        val offComp = offline?.rowByCompact?.get(comp)?.let { ResolvedRow(it.name, it.meta) }
                        mergeRows(ovComp, offComp)?.let { return TableHit(it, MetaMatchMode.TitleMatch) }
                    }
                }
            }

            // 倒排词模糊匹配（按需懒建倒排索引，防止细微标点或版本差异漏掉）
            val ovTokens = overlay?.ensureTokenIndexBuilt()
            val offTokens = offline?.ensureTokenIndexBuilt()
            if (ovTokens.isNullOrEmpty() && offTokens.isNullOrEmpty()) return null

            var bestRow: ResolvedRow? = null
            var bestScore = 0.80
            val evaluated = HashSet<String>(64)
            for (q in TitleNormalizer.selectFuzzyQueries(variants)) {
                val norm = TitleNormalizer.normalizedTitle(q)
                val tokens = TitleNormalizer.tokenize(norm)
                if (tokens.isEmpty()) continue
                val minOverlap = if (tokens.size <= 2) 1 else 2

                if (!ovTokens.isNullOrEmpty()) {
                    val counts = HashMap<ResolvedRow, Int>(32)
                    for (tok in tokens) {
                        val bucket = ovTokens[tok] ?: continue
                        if (bucket.size > 400) continue
                        for (row in bucket) {
                            counts[row] = (counts[row] ?: 0) + 1
                        }
                    }
                    for ((row, cnt) in counts) {
                        if (cnt < minOverlap || !evaluated.add("ov:${row.officialName}")) continue
                        val sim = TitleNormalizer.similarity(q, row.officialName)
                        if (sim > bestScore) {
                            bestScore = sim
                            bestRow = row
                        }
                    }
                }

                if (!offTokens.isNullOrEmpty()) {
                    val counts = HashMap<OfflineGameDb.MetaRow, Int>(32)
                    for (tok in tokens) {
                        val bucket = offTokens[tok] ?: continue
                        if (bucket.size > 400) continue
                        for (row in bucket) {
                            counts[row] = (counts[row] ?: 0) + 1
                        }
                    }
                    for ((row, cnt) in counts) {
                        if (cnt < minOverlap || !evaluated.add("off:${row.name}")) continue
                        val sim = TitleNormalizer.similarity(q, row.name)
                        if (sim > bestScore) {
                            bestScore = sim
                            bestRow = ResolvedRow(row.name, row.meta)
                        }
                    }
                }
            }
            return bestRow?.let { TableHit(it, MetaMatchMode.TitleMatch) }
        }
    }

    fun configure(providers: List<SourceItemConfig>, cred: ScraperCredentials) {
        val normalized = ScraperCatalog.normalizeMetadataConfigs(providers)
        val prevProviders = providerConfigs
        val prevCred = credentials
        val providersChanged = normalized != prevProviders
        val metaCredChanged = cred.ssUser != prevCred.ssUser ||
            cred.ssPass != prevCred.ssPass ||
            cred.tgdbKey != prevCred.tgdbKey ||
            cred.igdbClientId != prevCred.igdbClientId ||
            cred.igdbClientSecret != prevCred.igdbClientSecret
        providerConfigs = normalized
        credentials = cred
        if (!configuredOnce) {
            configuredOnce = true
            return
        }
        if (providersChanged || metaCredChanged) {
            ensureLoadedJob?.cancel()
            ensureLoadedJob = null
            metaMissStates.clear()
            focusApiResolved.clear()
            clearPendingBatch()

            val prevEnabled = prevProviders.filter { it.enabled }.map { it.id }.toSet()
            val curEnabled = normalized.filter { it.enabled }.map { it.id }.toSet()
            val disabledProviders = prevEnabled - curEnabled

            // 仅剔除被禁用元数据节点产出的自动解析条目，完整保留手动刮削（manualMetaIds）与本地 gamelist.xml（localSeedMap）
            val purgedFromDb = HashSet<String>()
            if (disabledProviders.isNotEmpty()) {
                val curInfo = _resolutionInfo.value
                val toRemove = HashSet<String>()
                val restoredMeta = HashMap<String, GameMetadata>()
                val restoredInfo = HashMap<String, MetadataResolutionInfo>()
                for ((gid, info) in curInfo) {
                    if (gid in manualMetaIds) continue
                    if (info.providers.isNotEmpty() && info.providers.all { it in disabledProviders }) {
                        canonicalNames.remove(gid)
                        purgedFromDb.add(gid)
                        val localMeta = localSeedMap[gid]
                        if (localMeta != null && !localMeta.isEmpty) {
                            gameCache[gid] = localMeta
                            restoredMeta[gid] = localMeta
                            restoredInfo[gid] = MetadataResolutionInfo(
                                providers = listOf("local"),
                                matchModes = listOf(MetaMatchMode.LocalFile),
                            )
                        } else {
                            gameCache.remove(gid)
                            toRemove.add(gid)
                        }
                    }
                }
                val removedOrRestoredIds = toRemove + restoredMeta.keys
                if (removedOrRestoredIds.isNotEmpty()) {
                    _officialNames.update { cur -> cur.filterKeys { it !in removedOrRestoredIds } }
                }
                if (toRemove.isNotEmpty()) {
                    _metadata.update { cur -> cur.filterKeys { it !in toRemove } }
                    _resolutionInfo.update { cur -> cur.filterKeys { it !in toRemove } }
                }
                if (restoredMeta.isNotEmpty()) {
                    _metadata.update { it + restoredMeta }
                    _resolutionInfo.update { it + restoredInfo }
                }
            }

            scope.launch(Dispatchers.IO) {
                client.database.clearResolvedMetadataMisses()
                if (purgedFromDb.isNotEmpty()) {
                    client.database.deleteResolvedMetadataBatch(purgedFromDb)
                }
            }
        }
    }

    /** 清空元数据缓存：内存瞬时生效，磁盘与数据库清理由 IO 线程执行，绝不阻塞主线程。 */
    /**
     * 系统内存吃紧时释放**非当前活跃平台**的元数据索引表，保留 [keepSystems] 正在使用的平台。
     */
    fun trimMemory(keepSystems: Set<String> = OfflineGameDb.activeSystems) {
        val toRemove = systemTables.keys.filter { it !in keepSystems }
        for (sys in toRemove) {
            systemTables.remove(sys)
            systemTableLastUsedMs.remove(sys)
        }
    }

    private fun putSystemTable(system: String, table: SystemTable) {
        systemTables[system] = table
        systemTableLastUsedMs[system] = System.currentTimeMillis()
        val keep = OfflineGameDb.activeSystems
        while (systemTables.size > MAX_CACHED_SYSTEM_TABLES) {
            val victim = systemTables.keys
                .filter { it != system && it !in keep }
                .minByOrNull { systemTableLastUsedMs[it] ?: 0L }
                ?: break
            systemTables.remove(victim)
            systemTableLastUsedMs.remove(victim)
        }
    }

    fun clearCache() {
        scope.launch { clearCacheNow() }
    }

    /** 彻底清理内存与磁盘上的元数据缓存，等待磁盘删除完成后再返回。 */
    suspend fun clearCacheNow() {
        ensureLoadedJob?.cancel()
        ensureLoadedJob = null
        systemTables.clear()
        remoteMetaRefreshInFlight.clear()
        gameCache.clear()
        metaMissStates.clear()
        manualMetaIds.clear()
        canonicalNames.clear()
        focusApiResolved.clear()
        inFlightFocus.clear()
        clearPendingBatch()
        _metadata.value = emptyMap()
        _officialNames.value = emptyMap()
        _resolutionInfo.value = emptyMap()

        withContext(Dispatchers.IO) {
            client.clearApiCache()
            runCatching { cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        }
    }

    /**
     * 快速统计当前游戏库中缺少封面或元数据的游戏数量（只读，不触发任何网络请求），
     * 供刮削任务弹窗在开始前向用户展示明细。
     */
    suspend fun inspectMissing(
        games: List<Game>,
        covers: CoverSource,
    ): ScrapeMissingSummary {
        initialCacheReady.await()
        covers.awaitInitialReady()
        val metaSnap = _metadata.value
        return withContext(Dispatchers.IO) {
            val romGames = games.filterNot { it.id.startsWith(GameLauncher.APK_PREFIX) }
            var pendingTotal = 0
            var missingCoverCount = 0
            var missingMetaCount = 0
            for (g in romGames) {
                val missingMeta = (metaSnap[g.id] ?: gameCache[g.id])?.isEmpty != false
                val missingCover = !covers.hasDownloadedCover(g)
                if (missingCover) missingCoverCount++
                if (missingMeta) missingMetaCount++
                if (missingMeta || missingCover) pendingTotal++
            }
            ScrapeMissingSummary(
                totalGames = romGames.size,
                pendingTotal = pendingTotal,
                missingCoverCount = missingCoverCount,
                missingMetaCount = missingMetaCount,
            )
        }
    }

    /**
     * 两阶段并发增量刮削所有缺少封面或元数据的游戏（支持随时 [cancelScrapeMissing] 终止）：
     * 1. 按平台分组并并行预载平台离线元数据表、Boxart 索引及全量街机批次；
     * 2. 阶段 A（零网络快通）：复用 Coil 磁盘缓存直接落盘离线封面；
     * 3. 阶段 B（受控 4 并发 Worker 池）：对缺失项并发刮削元数据与封面（复用服务商限流器），
     *    并按时间窗/批次节流刷新进度与 StateFlow，彻底消除 O(N²) 整表拷贝抖动。
     *
     * @return 本次进入队列的缺失游戏数量；若已有任务运行中，返回当前任务总数。
     */
    suspend fun scrapeMissing(
        games: List<Game>,
        platformResolver: (Game) -> Platform?,
        covers: CoverSource,
    ): Int = scrapeStartMutex.withLock {
        if (missingScrapeJob?.isActive == true) {
            return@withLock _scrapeProgress.value.total
        }
        client.hostCircuitBreaker.resetAll()
        initialCacheReady.await()
        covers.awaitInitialReady()

        val metaSnap = _metadata.value
        val pending = withContext(Dispatchers.IO) {
            val romGames = games.filterNot { it.id.startsWith(GameLauncher.APK_PREFIX) }
            romGames.filter { g ->
                val missingMeta = (metaSnap[g.id] ?: gameCache[g.id])?.isEmpty != false
                val missingCover = !covers.hasDownloadedCover(g)
                missingMeta || missingCover
            }
        }
        if (pending.isEmpty()) {
            _scrapeProgress.value = ScrapeProgress()
            return@withLock 0
        }

        val total = pending.size
        val enabled = providerConfigs.filter { it.enabled }
        val cred = credentials
        val useLibretroDb = enabled.any { it.id == ScraperCatalog.MetaIds.LIBRETRO_DB }
        val useArcadeItalia = enabled.any { it.id == ScraperCatalog.MetaIds.ARCADEITALIA }
        val runId = scrapeRunSeq.incrementAndGet()
        val flushBatchSize = if (total > 200) SCRAPE_FLUSH_LARGE_BATCH_SIZE else SCRAPE_FLUSH_BATCH_SIZE
        val flushIntervalMs = if (total > 200) SCRAPE_FLUSH_LARGE_INTERVAL_MS else SCRAPE_FLUSH_INTERVAL_MS

        _scrapeProgress.value = ScrapeProgress(
            running = true,
            done = 0,
            total = total,
            currentTitle = pending.firstOrNull()?.title.orEmpty(),
            currentPlatform = platformResolver(pending.first())?.shortName.orEmpty(),
        )

        missingScrapeJob = scope.launch(Dispatchers.IO) {
            val doneCount = AtomicInteger(0)
            val updatedCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)
            val lastProgressEmitMs = AtomicLong(0L)
            var wasCancelled = false

            val accLock = Any()
            val updates = HashMap<String, GameMetadata>()
            val nameUpdates = HashMap<String, String>()
            val infoUpdates = HashMap<String, MetadataResolutionInfo>()
            val persistBatch = ArrayList<ScrapeDatabase.ManualMetaRow>(flushBatchSize)
            var lastFlushMs = System.currentTimeMillis()
            var accSinceFlush = 0

            fun flushPartial(force: Boolean = false) {
                val now = System.currentTimeMillis()
                val mCopy: HashMap<String, GameMetadata>
                val nCopy: HashMap<String, String>
                val iCopy: HashMap<String, MetadataResolutionInfo>
                val dbCopy: ArrayList<ScrapeDatabase.ManualMetaRow>
                synchronized(accLock) {
                    if (!force && accSinceFlush < flushBatchSize && (now - lastFlushMs) < flushIntervalMs) {
                        return
                    }
                    if (updates.isEmpty() && nameUpdates.isEmpty() && infoUpdates.isEmpty() && persistBatch.isEmpty()) {
                        accSinceFlush = 0
                        lastFlushMs = now
                        return
                    }
                    mCopy = HashMap(updates)
                    nCopy = HashMap(nameUpdates)
                    iCopy = HashMap(infoUpdates)
                    dbCopy = ArrayList(persistBatch)
                    updates.clear()
                    nameUpdates.clear()
                    infoUpdates.clear()
                    persistBatch.clear()
                    accSinceFlush = 0
                    lastFlushMs = now
                }
                if (dbCopy.isNotEmpty()) {
                    client.database.putResolvedMetadataBatch(dbCopy)
                }
                if (mCopy.isNotEmpty() || nCopy.isNotEmpty() || iCopy.isNotEmpty()) {
                    enqueueBatchUpdate(mCopy, nCopy, iCopy, immediate = true)
                }
            }

            fun publishProgress(title: String, platName: String, force: Boolean = false) {
                if (scrapeRunSeq.get() != runId) return
                val now = System.currentTimeMillis()
                val prev = lastProgressEmitMs.get()
                if (!force && (now - prev) < SCRAPE_PROGRESS_THROTTLE_MS) return
                if (force || lastProgressEmitMs.compareAndSet(prev, now)) {
                    if (scrapeRunSeq.get() != runId) return
                    _scrapeProgress.update { cur ->
                        ScrapeProgress(
                            running = true,
                            done = doneCount.get(),
                            total = total,
                            currentTitle = title,
                            currentPlatform = platName,
                            updatedCount = updatedCount.get(),
                            failedCount = failCount.get(),
                            cancelling = cur.cancelling,
                        )
                    }
                }
            }

            try {
                val grouped = pending.groupBy { platformResolver(it) }
                for ((platform, platGames) in grouped) {
                    currentCoroutineContext().ensureActive()
                    val online = NetworkStatus.isOnline(appCtx)
                    val system = platform?.libretroSystem
                    val platName = platform?.shortName.orEmpty()

                    // 并行预载该平台的离线元数据表与 Boxart 索引（等待就绪，消除竞态）
                    coroutineScope {
                        val tableJob = if (useLibretroDb && !system.isNullOrBlank()) {
                            async { getOrFetchLibretroTable(system) }
                        } else null
                        val indexJob = if (online && platform != null) {
                            async { covers.ensureIndexLoadedAwait(platform) }
                        } else null
                        val arcadeJob = if (useArcadeItalia && online) {
                            async { client.prefetchArcadeItaliaBatch(platGames, platform) }
                        } else null
                        tableJob?.await()
                        indexJob?.await()
                        arcadeJob?.await()
                    }

                    // 阶段 A：对无需刮削元数据的游戏先尝试零网络转存 Coil 磁盘缓存封面
                    val needWorkerGames = ArrayList<Game>(platGames.size)
                    for (g in platGames) {
                        currentCoroutineContext().ensureActive()
                        val needMeta = g.id !in manualMetaIds && (_metadata.value[g.id] ?: gameCache[g.id])?.isEmpty != false
                        val promotedCover = !needMeta && covers.saveCoilCachedCoverIfAvailable(g)
                        if (!needMeta && promotedCover) {
                            updatedCount.incrementAndGet()
                            doneCount.incrementAndGet()
                            publishProgress(g.title, platName)
                        } else {
                            needWorkerGames += g
                        }
                    }

                    if (needWorkerGames.isEmpty()) {
                        flushPartial(force = true)
                        continue
                    }

                    // 阶段 B：固定 4 并发 Worker 池并发处理当前平台待刮削项（保持平台表热缓存不抖动）
                    val workerCount = minOf(SCRAPE_CONCURRENCY, needWorkerGames.size)
                    val channel = Channel<Game>(capacity = Channel.UNLIMITED)
                    for (g in needWorkerGames) {
                        channel.trySend(g)
                    }
                    channel.close()

                    coroutineScope {
                        repeat(workerCount) {
                            launch(Dispatchers.IO) {
                                for (g in channel) {
                                    currentCoroutineContext().ensureActive()
                                    publishProgress(g.title, platName)

                                    val needMeta = g.id !in manualMetaIds &&
                                        (_metadata.value[g.id] ?: gameCache[g.id])?.isEmpty != false
                                    val needCover = !covers.hasDownloadedCover(g)
                                    var gotSomething = false
                                    var searchName = bestSearchNameFor(g.id)

                                    if (needMeta && enabled.isNotEmpty()) {
                                        val isOnline = NetworkStatus.isOnline(appCtx)
                                        val res = resolveSingleGame(
                                            g = g,
                                            platform = platform,
                                            enabled = enabled,
                                            cred = cred,
                                            queryApi = isOnline,
                                        )
                                        val now = System.currentTimeMillis()
                                        val effSearch = res.canonicalName?.takeIf { it.isNotBlank() } ?: res.officialName
                                        if (!effSearch.isNullOrBlank()) {
                                            canonicalNames[g.id] = effSearch
                                            searchName = effSearch
                                        }
                                        var rowToPersist: ScrapeDatabase.ManualMetaRow? = null
                                        if (!res.meta.isEmpty) {
                                            gameCache[g.id] = res.meta
                                            metaMissStates.remove(g.id)
                                            rowToPersist = ScrapeDatabase.ManualMetaRow(
                                                gameId = g.id,
                                                providerId = res.info?.providers?.firstOrNull() ?: ScrapeDatabase.RESOLVED_META_PROVIDER,
                                                officialName = res.officialName,
                                                canonicalName = effSearch,
                                                meta = res.meta,
                                                status = ScrapeDatabase.STATUS_HIT,
                                                expiresAtMs = 0L,
                                                focusChecked = true,
                                            )
                                        } else {
                                            gameCache.putIfAbsent(g.id, res.meta)
                                            val libretroMissingOffline = useLibretroDb && !system.isNullOrBlank() &&
                                                systemTables[system] == null && !isOnline
                                            if (!libretroMissingOffline && gameCache[g.id]?.isEmpty != false) {
                                                val missState = recordMetaMiss(
                                                    gameId = g.id,
                                                    focusChecked = isOnline && !res.hadTransientApiError,
                                                    nowMs = now,
                                                )
                                                val persistedOff = res.officialName?.takeIf { it.isNotBlank() } ?: _officialNames.value[g.id]
                                                val persistedCanon = effSearch ?: persistedOff ?: canonicalNames[g.id]
                                                rowToPersist = ScrapeDatabase.ManualMetaRow(
                                                    gameId = g.id,
                                                    providerId = res.info?.providers?.firstOrNull() ?: ScrapeDatabase.RESOLVED_META_PROVIDER,
                                                    officialName = persistedOff,
                                                    canonicalName = persistedCanon,
                                                    meta = res.meta,
                                                    status = ScrapeDatabase.STATUS_MISS,
                                                    expiresAtMs = missState.expiresAtMs,
                                                    focusChecked = missState.focusChecked,
                                                )
                                            }
                                        }
                                        synchronized(accLock) {
                                            if (!res.meta.isEmpty) {
                                                updates[g.id] = res.meta
                                                gotSomething = true
                                            }
                                            if (!res.officialName.isNullOrBlank()) {
                                                nameUpdates[g.id] = res.officialName
                                            }
                                            res.info?.let { infoUpdates[g.id] = it }
                                            if (rowToPersist != null) {
                                                persistBatch += rowToPersist
                                            }
                                            accSinceFlush++
                                        }
                                    }

                                    if (needCover) {
                                        val ok = covers.fetchWebCoverNow(g, platform, searchName)
                                        if (ok) gotSomething = true
                                    }

                                    if (gotSomething) updatedCount.incrementAndGet() else failCount.incrementAndGet()
                                    doneCount.incrementAndGet()
                                    flushPartial(force = false)
                                    publishProgress(g.title, platName)
                                }
                            }
                        }
                    }
                    flushPartial(force = true)
                }
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            } finally {
                flushPartial(force = true)
                if (scrapeRunSeq.get() == runId) {
                    _scrapeProgress.value = ScrapeProgress(
                        running = false,
                        done = doneCount.get(),
                        total = total,
                        currentTitle = "",
                        currentPlatform = "",
                        updatedCount = updatedCount.get(),
                        failedCount = failCount.get(),
                        cancelled = wasCancelled,
                        finishedAtMs = System.currentTimeMillis(),
                    )
                }
            }
        }
        total
    }

    /** 立即终止正在运行的增量刮削任务（已刮削的数据全部保留）；同步置 cancelling 锁定 UI 按钮防连点。 */
    fun cancelScrapeMissing() {
        val job = missingScrapeJob
        _scrapeProgress.update { cur ->
            if (cur.running && !cur.cancelling) cur.copy(cancelling = true) else cur
        }
        job?.cancel()
        missingScrapeJob = null
    }

    /** ROM 重命名或移动后，同步迁移内存元数据缓存。 */
    fun migrateGameId(oldId: String, newId: String) {
        if (oldId == newId) return
        localSeedMap.remove(oldId)?.let { localSeedMap[newId] = it }
        gameCache.remove(oldId)?.let { gameCache[newId] = it }
        metaMissStates.remove(oldId)?.let { metaMissStates[newId] = it }
        if (manualMetaIds.remove(oldId)) manualMetaIds.add(newId)
        canonicalNames.remove(oldId)?.let { canonicalNames[newId] = it }
        focusApiResolved.remove(oldId)?.let { focusApiResolved[newId] = it }
        _metadata.update { cur ->
            val v = cur[oldId] ?: return@update cur
            (cur - oldId) + (newId to v)
        }
        _officialNames.update { cur ->
            val v = cur[oldId] ?: return@update cur
            (cur - oldId) + (newId to v)
        }
        _resolutionInfo.update { cur ->
            val v = cur[oldId] ?: return@update cur
            (cur - oldId) + (newId to v)
        }
    }

    /** ROM 永久删除后，同步清理内存元数据缓存。 */
    fun forgetGame(gameId: String) {
        localSeedMap.remove(gameId)
        gameCache.remove(gameId)
        metaMissStates.remove(gameId)
        manualMetaIds.remove(gameId)
        canonicalNames.remove(gameId)
        focusApiResolved.remove(gameId)
        _metadata.update { if (gameId in it) it - gameId else it }
        _officialNames.update { if (gameId in it) it - gameId else it }
        _resolutionInfo.update { if (gameId in it) it - gameId else it }
    }

    /** 目录重命名或移动后，批量迁移该前缀下全部内存元数据缓存。 */
    fun migratePathPrefix(oldDir: String, newDir: String) {
        val oldPrefix = if (oldDir.endsWith("/")) oldDir else "$oldDir/"
        val newPrefix = if (newDir.endsWith("/")) newDir else "$newDir/"
        if (oldPrefix == newPrefix) return
        val matchedIds = (gameCache.keys + localSeedMap.keys + canonicalNames.keys + manualMetaIds + metaMissStates.keys)
            .filter { it.startsWith(oldPrefix) }
            .distinct()
        for (oldId in matchedIds) {
            migrateGameId(oldId, newPrefix + oldId.removePrefix(oldPrefix))
        }
    }

    /** 目录永久删除后，批量清理该前缀下全部内存元数据缓存。 */
    fun forgetPathPrefix(dirPath: String) {
        val prefix = if (dirPath.endsWith("/")) dirPath else "$dirPath/"
        val matchedIds = (gameCache.keys + localSeedMap.keys + canonicalNames.keys + manualMetaIds + metaMissStates.keys)
            .filter { it.startsWith(prefix) }
            .distinct()
        for (gid in matchedIds) {
            forgetGame(gid)
        }
    }

    /** 收起已完成/已终止的任务进度横幅。 */
    fun dismissScrapeProgress() {
        if (!_scrapeProgress.value.running) {
            _scrapeProgress.value = ScrapeProgress()
        }
    }

    /**
     * 预加载指定平台下可见游戏的元数据：
     * - 仅对尚未缓存且未处于负缓存冷却期的游戏按需加载离线库，已完整刮削或无元数据平台冷启动零离线表 IO；
     * - 按焦点外扩顺序优先加载当前首屏附近平台，并将并发离线表解析上限控制在 [MAX_CONCURRENT_PRELOAD_SYSTEMS]。
     */
    fun ensureLoaded(
        games: List<Game>,
        focusIdx: Int = 0,
        platformResolver: (Game) -> Platform?,
    ) {
        ensureLoadedJob?.cancel()
        if (games.isEmpty()) {
            ensureLoadedJob = null
            return
        }
        val enabled = providerConfigs.filter { it.enabled }
        if (enabled.isEmpty()) {
            ensureLoadedJob = null
            return
        }
        val useLibretroDb = enabled.any { it.id == ScraperCatalog.MetaIds.LIBRETRO_DB }
        val useArcadeItalia = enabled.any { it.id == ScraperCatalog.MetaIds.ARCADEITALIA }

        ensureLoadedJob = scope.launch(Dispatchers.IO) {
            initialCacheReady.await()
            val now = System.currentTimeMillis()
            // 按焦点向两侧收集待处理游戏并按平台分组，保证首屏平台在受控并发下最先解析
            val grouped = LinkedHashMap<Platform?, MutableList<Game>>()
            OfflineGameDb.forEachFocusOutward(games.size, focusIdx) { idx ->
                val g = games[idx]
                if (g.id.startsWith(GameLauncher.APK_PREFIX)) return@forEachFocusOutward
                if (g.id in manualMetaIds || gameCache.containsKey(g.id) || isOfflineMissFresh(g.id, now)) {
                    return@forEachFocusOutward
                }
                val plat = platformResolver(g)
                grouped.getOrPut(plat) { ArrayList() }.add(g)
            }
            if (grouped.isEmpty()) return@launch
            val groupedList = grouped.entries.toList()
            val nextIdx = AtomicInteger(0)
            val workerCount = minOf(MAX_CONCURRENT_PRELOAD_SYSTEMS, groupedList.size)
            coroutineScope {
                repeat(workerCount) {
                    launch(Dispatchers.IO) {
                        while (true) {
                            val i = nextIdx.getAndIncrement()
                            if (i >= groupedList.size) break
                            currentCoroutineContext().ensureActive()
                            val (platform, platGames) = groupedList[i]
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
            }
        }
    }

    /** 确保单个焦点游戏从全部已启用的服务商查询并合并元数据（已持久化命中或全源负缓存的游戏冷启动跳过离线表解压）。 */
    fun resolveFocusGame(game: Game?, platform: Platform?) {
        val g = game ?: return
        if (g.id.startsWith(GameLauncher.APK_PREFIX)) return
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
                initialCacheReady.await()
                val now = System.currentTimeMillis()
                val existing = gameCache[g.id]
                if (g.id in manualMetaIds || (existing != null && !existing.isEmpty)) {
                    focusApiResolved[g.id] = true
                    return@launch
                }
                if (isFullMissFresh(g.id, now)) {
                    focusApiResolved[g.id] = true
                    return@launch
                }
                val alreadyCheckedOffline = isOfflineMissFresh(g.id, now)
                if (!online && alreadyCheckedOffline) {
                    return@launch
                }
                val system = platform?.libretroSystem
                val skipLibretroLoad = alreadyCheckedOffline && (
                    !canonicalNames[g.id].isNullOrBlank() ||
                        withContext(Dispatchers.IO) { client.hasFastFingerprint(File(g.id), platform?.id) }
                    )
                if (useLibretroDb && !system.isNullOrBlank() && !skipLibretroLoad) {
                    getOrFetchLibretroTable(system)
                }
                resolveGames(listOf(g), platform, queryApi = online, skippedLibretroLoad = skipLibretroLoad)
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
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) return@withContext false
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

        if (!res.meta.isEmpty) {
            gameCache[game.id] = res.meta
            metaMissStates.remove(game.id)
        } else {
            gameCache.putIfAbsent(game.id, res.meta)
        }
        focusApiResolved[game.id] = true
        (res.canonicalName?.takeIf { it.isNotBlank() } ?: res.officialName)?.let { canonicalNames[game.id] = it }
        if (game.id !in manualMetaIds) {
            val now = System.currentTimeMillis()
            val status = if (!res.meta.isEmpty) ScrapeDatabase.STATUS_HIT else ScrapeDatabase.STATUS_MISS
            val expiresAt = if (!res.meta.isEmpty) 0L else now + ScrapeDatabase.TTL_MISS_MS
            val focusChecked = !res.meta.isEmpty || !res.hadTransientApiError
            if (res.meta.isEmpty) {
                recordMetaMiss(game.id, focusChecked = focusChecked, nowMs = now)
            }
            client.database.putResolvedMetadataBatch(
                listOf(
                    ScrapeDatabase.ManualMetaRow(
                        gameId = game.id,
                        providerId = res.info?.providers?.firstOrNull() ?: ScrapeDatabase.RESOLVED_META_PROVIDER,
                        officialName = res.officialName,
                        canonicalName = res.canonicalName,
                        meta = res.meta,
                        status = status,
                        expiresAtMs = expiresAt,
                        focusChecked = focusChecked,
                    ),
                ),
            )
        }
        enqueueBatchUpdate(
            metaUpdates = if (!res.meta.isEmpty) mapOf(game.id to res.meta) else emptyMap(),
            nameUpdates = res.officialName?.takeIf { it.isNotBlank() }?.let { mapOf(game.id to it) }.orEmpty(),
            infoUpdates = res.info?.let { mapOf(game.id to it) }.orEmpty(),
            immediate = true,
        )
        true
    }

    /** 按需补查单个游戏的元数据（优先复用本地索引与 SQLite 缓存，供 Web 详情页打开时静默触发）。 */
    suspend fun ensureGameMetadata(game: Game, platform: Platform?): Boolean = withContext(Dispatchers.IO) {
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) return@withContext false
        val existingMeta = gameCache[game.id]
        val existingName = _officialNames.value[game.id]
        if (focusApiResolved[game.id] == true && ((existingMeta != null && !existingMeta.isEmpty) || !existingName.isNullOrBlank())) {
            return@withContext false
        }
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
            queryApi = NetworkStatus.isOnline(appCtx),
            forceApiRefresh = false,
        )
        focusApiResolved[game.id] = true
        val hasResult = !res.meta.isEmpty || !res.officialName.isNullOrBlank() || res.info?.crc32 != null
        if (!hasResult) return@withContext false
        if (!res.meta.isEmpty) {
            gameCache[game.id] = res.meta
            metaMissStates.remove(game.id)
        }
        (res.canonicalName?.takeIf { it.isNotBlank() } ?: res.officialName)?.let { canonicalNames[game.id] = it }
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
        val canonicalName: String?,
        val info: MetadataResolutionInfo?,
        val hadTransientApiError: Boolean = false,
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
        var canonicalName: String? = null
        var releaseDate: String? = null
        var genre: String? = null
        var developer: String? = null
        var publisher: String? = null
        var identity: RomIdentityExtractor.Identity? = null
        var description: String? = localSeedMap[g.id]?.description
        var hadTransientApiError = false
        val usedProviders = LinkedHashSet<String>()
        val usedModes = LinkedHashSet<MetaMatchMode>()

        fun getIdentity(): RomIdentityExtractor.Identity {
            identity?.let { return it }
            // 静默浏览（queryApi=false）仅读头部 Serial/压缩包目录 CRC，避免切大分类时流式读 GB 级全文件 CRC32
            val ext = client.extractIdentity(File(g.id), platform?.id, skipFullFileCrc = !queryApi)
            identity = ext
            return ext
        }

        fun merge(
            providerId: String,
            mode: MetaMatchMode,
            name: String?,
            meta: GameMetadata,
            enCanonical: String? = null,
        ) {
            var contributed = false
            if (officialName == null && !name.isNullOrBlank()) {
                officialName = name; contributed = true
            }
            val candCanon = enCanonical?.takeIf { it.isNotBlank() }
                ?: name?.takeIf { it.isNotBlank() && !CjkAliases.containsCjk(it) }
            if (canonicalName == null && candCanon != null) {
                canonicalName = candCanon
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
                            // 对快速指纹格式（缓存命中 / zip / 7z / nds / 3ds / chd / rvz / wbfs）优先精确指纹，避免模糊标题误配
                            if (identity == null && client.hasFastFingerprint(File(g.id), platform.id)) {
                                getIdentity()
                            }
                            var hit = table.find(g.baseName, g.title, crc = identity?.crc32, serial = identity?.serial)
                            if (hit == null && identity == null && queryApi) {
                                val id = getIdentity()
                                if (id.crc32 != null || id.serial != null) {
                                    hit = table.find(g.baseName, g.title, id.crc32, id.serial)
                                }
                            }
                            if (hit != null) {
                                merge(provider.id, hit.mode, hit.row.officialName, hit.row.meta, enCanonical = hit.row.officialName)
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
                            merge(
                                provider.id,
                                mode,
                                match.hit.officialName,
                                match.hit.meta,
                                enCanonical = match.hit.canonicalName,
                            )
                        }
                    }
                }
                else -> {
                    val desc = ScraperCatalog.metaDescriptor(provider.id)
                    if (!ScraperCatalog.hasCredentials(desc?.credentialKind, cred)) continue
                    val searchHint = canonicalName
                        ?: officialName?.takeIf { !CjkAliases.containsCjk(it) }
                        ?: canonicalNames[g.id]
                        ?: _officialNames.value[g.id]?.takeIf { !CjkAliases.containsCjk(it) }
                    val hadFreshCacheBefore = queryApi && !forceApiRefresh && client.cachedHit(provider.id, g.id) != null
                    var hit = if (queryApi) {
                        client.queryProvider(
                            providerId = provider.id,
                            game = g,
                            platform = platform,
                            cred = cred,
                            forceRefresh = forceApiRefresh,
                            officialNameHint = searchHint,
                        )
                    } else {
                        client.cachedHit(provider.id, g.id)
                    }
                    if (queryApi && (hit == null || !client.hasConfirmedCacheInMemory(provider.id, g.id))) {
                        hadTransientApiError = true
                    }
                    // I3: 仅对支持名称检索、尚未负缓存且未提供英文提示的中文 ROM 尝试第二英文别名补充，避免重复发包或击穿负缓存
                    if (queryApi && !hadFreshCacheBefore && (hit == null || hit.isEmpty) && searchHint == null &&
                        provider.id in NAME_SEARCH_META_PROVIDERS &&
                        (CjkAliases.containsCjk(g.title) || CjkAliases.containsCjk(g.baseName))
                    ) {
                        val aliases = CjkAliases.expandEnglishAliases(g.title).ifEmpty {
                            CjkAliases.expandEnglishAliases(g.baseName)
                        }
                        val firstNorm = aliases.firstOrNull()?.let(TitleNormalizer::normalizedTitle)
                        val secondAlias = aliases.drop(1).firstOrNull { cand ->
                            val n = TitleNormalizer.normalizedTitle(cand)
                            n.isNotEmpty() && n != firstNorm
                        }
                        if (!secondAlias.isNullOrBlank()) {
                            hit = client.queryProvider(
                                providerId = provider.id,
                                game = g,
                                platform = platform,
                                cred = cred,
                                forceRefresh = true,
                                officialNameHint = secondAlias,
                            )
                            if (hit == null || !client.hasConfirmedCacheInMemory(provider.id, g.id)) {
                                hadTransientApiError = true
                            }
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
                        merge(provider.id, apiMode, hit.officialName, hit.meta, enCanonical = hit.canonicalName)
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
        return SingleResolution(resolved, officialName, canonicalName, info, hadTransientApiError)
    }

    private suspend fun resolveGames(
        games: List<Game>,
        platform: Platform?,
        queryApi: Boolean,
        skippedLibretroLoad: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val enabled = providerConfigs.filter { it.enabled }
        if (enabled.isEmpty()) return@withContext
        val cred = credentials
        val useLibretroDb = enabled.any { it.id == ScraperCatalog.MetaIds.LIBRETRO_DB }
        val system = platform?.libretroSystem

        val updates = HashMap<String, GameMetadata>()
        val nameUpdates = HashMap<String, String>()
        val infoUpdates = HashMap<String, MetadataResolutionInfo>()
        val persistRows = ArrayList<ScrapeDatabase.ManualMetaRow>()

        val nowStart = System.currentTimeMillis()
        val romOnly = games.filterNot { it.id.startsWith(GameLauncher.APK_PREFIX) }
        val pending = if (queryApi) {
            romOnly.filter { it.id !in manualMetaIds }
        } else {
            romOnly.filter { it.id !in manualMetaIds && !gameCache.containsKey(it.id) && !isOfflineMissFresh(it.id, nowStart) }
        }
        // 静默浏览加载（queryApi=false）绝不写 _scrapeProgress，避免切 Tab 覆写运行中的全量刮削进度或误关常亮
        val trackProgress = queryApi && pending.size > 1 && missingScrapeJob?.isActive != true
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
                currentCoroutineContext().ensureActive()
                if (!queryApi && (g.id in manualMetaIds || gameCache.containsKey(g.id))) {
                    doneCount++
                    continue
                }
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
                val now = System.currentTimeMillis()
                val off = res.officialName
                if (!off.isNullOrBlank()) {
                    nameUpdates[g.id] = off
                }
                val canon = res.canonicalName?.takeIf { it.isNotBlank() } ?: off
                if (!canon.isNullOrBlank()) {
                    canonicalNames[g.id] = canon
                }
                res.info?.let { infoUpdates[g.id] = it }

                if (!res.meta.isEmpty) {
                    gameCache[g.id] = res.meta
                    metaMissStates.remove(g.id)
                    updates[g.id] = res.meta
                    persistRows += ScrapeDatabase.ManualMetaRow(
                        gameId = g.id,
                        providerId = res.info?.providers?.firstOrNull() ?: ScrapeDatabase.RESOLVED_META_PROVIDER,
                        officialName = off,
                        canonicalName = canon,
                        meta = res.meta,
                        status = ScrapeDatabase.STATUS_HIT,
                        expiresAtMs = 0L,
                        focusChecked = true,
                    )
                } else {
                    failCount++
                    gameCache.putIfAbsent(g.id, res.meta)
                    // 仅当未命中非空元数据时写入负缓存，避免并发 queryApi=false 降级覆盖有效结果或 focusChecked=true
                    val existingNonEmpty = gameCache[g.id]?.isEmpty == false
                    val libretroMissingOffline = useLibretroDb && !system.isNullOrBlank() &&
                        systemTables[system] == null && !skippedLibretroLoad && !NetworkStatus.isOnline(appCtx)
                    if (!existingNonEmpty && !libretroMissingOffline) {
                        val missState = recordMetaMiss(
                            gameId = g.id,
                            focusChecked = queryApi && !res.hadTransientApiError,
                            nowMs = now,
                        )
                        val persistedOff = off?.takeIf { it.isNotBlank() } ?: _officialNames.value[g.id]
                        val persistedCanon = canon ?: persistedOff ?: canonicalNames[g.id]
                        persistRows += ScrapeDatabase.ManualMetaRow(
                            gameId = g.id,
                            providerId = res.info?.providers?.firstOrNull() ?: ScrapeDatabase.RESOLVED_META_PROVIDER,
                            officialName = persistedOff,
                            canonicalName = persistedCanon,
                            meta = res.meta,
                            status = ScrapeDatabase.STATUS_MISS,
                            expiresAtMs = missState.expiresAtMs,
                            focusChecked = missState.focusChecked,
                        )
                    } else if (!existingNonEmpty && !off.isNullOrBlank()) {
                        persistRows += ScrapeDatabase.ManualMetaRow(
                            gameId = g.id,
                            providerId = res.info?.providers?.firstOrNull() ?: ScrapeDatabase.RESOLVED_META_PROVIDER,
                            officialName = off,
                            canonicalName = canon,
                            meta = res.meta,
                            status = ScrapeDatabase.STATUS_HIT,
                            expiresAtMs = 0L,
                            focusChecked = false,
                        )
                    }
                }
                doneCount++
                // 浏览大分类时每 48 款增量刷出一次内存与 SQLite 批次，避免等 3k+ 款全部跑完才出现元数据
                if (pending.size > RESOLVE_INCREMENTAL_BATCH && doneCount % RESOLVE_INCREMENTAL_BATCH == 0) {
                    if (persistRows.isNotEmpty()) {
                        client.database.putResolvedMetadataBatch(ArrayList(persistRows))
                        persistRows.clear()
                    }
                    if (updates.isNotEmpty() || nameUpdates.isNotEmpty() || infoUpdates.isNotEmpty()) {
                        enqueueBatchUpdate(HashMap(updates), HashMap(nameUpdates), HashMap(infoUpdates), immediate = true)
                        updates.clear()
                        nameUpdates.clear()
                        infoUpdates.clear()
                    }
                }
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

        if (persistRows.isNotEmpty()) {
            client.database.putResolvedMetadataBatch(persistRows)
        }
        if (updates.isNotEmpty() || nameUpdates.isNotEmpty() || infoUpdates.isNotEmpty()) {
            enqueueBatchUpdate(updates, nameUpdates, infoUpdates, immediate = games.size > 4)
        }
    }

    /** 手动关键词搜索刮削：本地封面秒出 + Libretro 索引 + 在线节点并发查询，谁先回来就立刻增量回调更新列表。 */
    suspend fun manualSearch(
        keyword: String,
        game: Game,
        platform: Platform?,
        covers: CoverSource,
        onActiveSourcesChanged: (List<String>) -> Unit = {},
        onPartialResults: (List<ManualScrapeCandidate>) -> Unit = {},
        localCoverPaths: List<String> = emptyList(),
        localKeywordCoversProvider: (suspend () -> List<String>)? = null,
    ): List<ManualScrapeCandidate> = withContext(Dispatchers.IO) {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isEmpty()) return@withContext emptyList()
        val synthetic = game.copy(
            id = "${game.id}#manual#${cleanKeyword.lowercase()}",
            baseName = cleanKeyword,
            title = cleanKeyword,
        )
        val lock = Any()
        val out = ArrayList<ManualScrapeCandidate>()
        val seen = HashMap<String, Int>()
        val isRom = !game.id.startsWith(GameLauncher.APK_PREFIX)

        fun dedupKey(c: ManualScrapeCandidate): String {
            val u = c.coverUrl?.trim().orEmpty()
            if (c.providerId == CoverSource.LOCAL_COVER_PROVIDER) return "local|${u.lowercase()}"
            if (u.isEmpty()) return "meta|${c.title.lowercase()}|${c.providerId}"
            val boxIdx = u.indexOf("/Named_")
            return if (boxIdx >= 0) {
                "libretro|" + Uri.decode(u.substring(boxIdx)).lowercase()
            } else {
                "url|" + u.lowercase()
            }
        }

        fun mergeMeta(a: GameMetadata, b: GameMetadata): GameMetadata = GameMetadata(
            releaseDate = a.releaseDate ?: b.releaseDate,
            genre = a.genre ?: b.genre,
            developer = a.developer ?: b.developer,
            publisher = a.publisher ?: b.publisher,
            description = a.description ?: b.description,
        )

        fun addCandidatesLocked(items: List<ManualScrapeCandidate>): Boolean {
            var added = false
            for (c in items) {
                val key = dedupKey(c)
                val existingIdx = seen[key]
                if (existingIdx == null) {
                    if (c.matchedByHash) {
                        val insertAt = out.indexOfFirst { !it.matchedByHash }.let { if (it < 0) out.size else it }
                        out.add(insertAt, c)
                        seen.clear()
                        out.forEachIndexed { idx, item -> seen[dedupKey(item)] = idx }
                    } else {
                        seen[key] = out.size
                        out += c
                    }
                    added = true
                } else {
                    val prev = out[existingIdx]
                    val merged = prev.copy(
                        metadata = mergeMeta(prev.metadata, c.metadata),
                        matchedByHash = prev.matchedByHash || c.matchedByHash,
                        fallbackCoverUrls = if (prev.fallbackCoverUrls.isNotEmpty()) prev.fallbackCoverUrls else c.fallbackCoverUrls,
                    )
                    if (merged != prev) {
                        out[existingIdx] = merged
                        if (merged.matchedByHash && !prev.matchedByHash) {
                            out.removeAt(existingIdx)
                            val insertAt = out.indexOfFirst { !it.matchedByHash }.let { if (it < 0) out.size else it }
                            out.add(insertAt, merged)
                            seen.clear()
                            out.forEachIndexed { idx, item -> seen[dedupKey(item)] = idx }
                        }
                        added = true
                    }
                }
            }
            return added
        }

        fun snapshot(): List<ManualScrapeCandidate> = synchronized(lock) { out.take(20).toList() }

        // 0) 本机预扫描封面 + 关键词匹配的本地封面（离线亦可用）
        val keywordLocalPaths = runCatching { localKeywordCoversProvider?.invoke().orEmpty() }.getOrDefault(emptyList())
        val localItems = (localCoverPaths + keywordLocalPaths)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .mapNotNull { path ->
                val f = File(path).takeIf { it.isFile } ?: return@mapNotNull null
                val parent = f.parentFile?.name
                val label = if (!parent.isNullOrBlank()) "$parent/${f.name}" else f.name
                ManualScrapeCandidate(
                    title = label,
                    providerId = CoverSource.LOCAL_COVER_PROVIDER,
                    coverUrl = f.absolutePath,
                    metadata = GameMetadata(),
                )
            }
            .toList()
        if (synchronized(lock) { addCandidatesLocked(localItems) }) {
            onPartialResults(snapshot())
        }

        if (!NetworkStatus.isOnline(appCtx)) {
            onActiveSourcesChanged(emptyList())
            return@withContext snapshot()
        }

        // 1) Libretro Boxarts 索引候选（本地内存毫秒级返回，有结果立即推送给界面）
        onActiveSourcesChanged(listOf(ScraperCatalog.CoverIds.LIBRETRO_BOXARTS))
        val boxarts = runCatching { covers.searchIndexCandidates(cleanKeyword, platform) }.getOrDefault(emptyList())
        val system = platform?.libretroSystem
        val table = system?.let { systemTables[it] }
        val libretroItems = boxarts.map { match ->
            val meta = table?.find(match.title, match.title, null, null)?.row?.meta ?: GameMetadata()
            ManualScrapeCandidate(
                title = match.title,
                providerId = ScraperCatalog.CoverIds.LIBRETRO_BOXARTS,
                coverUrl = match.url,
                metadata = meta,
                fallbackCoverUrls = match.fallbackUrls,
            )
        }
        val gotLibretro = synchronized(lock) { addCandidatesLocked(libretroItems) }
        if (gotLibretro) {
            onPartialResults(snapshot())
        }

        // 2) 在线 API 节点 + ROM Hash 查询全并发执行，谁先返回就立刻增量刷新列表（Hash 命中自动置顶并标明）
        val cred = credentials
        val apiProviders = buildList {
            if (isRom) add(ScraperCatalog.MetaIds.HASHEOUS)
            add(ScraperCatalog.MetaIds.GAMETDB)
            add(ScraperCatalog.MetaIds.THEGAMESDB)
            add(ScraperCatalog.MetaIds.SCREENSCRAPER)
            add(ScraperCatalog.MetaIds.IGDB)
            add(ScraperCatalog.MetaIds.WIKIDATA)
            add(ScraperCatalog.CoverIds.STEAMGRIDDB)
        }.filter { pid ->
            if (pid == ScraperCatalog.MetaIds.GAMETDB && !client.gameTdb.supportsPlatform(platform?.id)) {
                return@filter false
            }
            val desc = ScraperCatalog.metaDescriptor(pid) ?: ScraperCatalog.coverDescriptor(pid)
            ScraperCatalog.hasCredentials(desc?.credentialKind, cred)
        }

        val active = LinkedHashSet<String>(apiProviders)
        if (isRom && table != null) active.add(ScraperCatalog.MetaIds.LIBRETRO_DB)
        if (active.isNotEmpty()) {
            onActiveSourcesChanged(synchronized(lock) { active.toList() })
            coroutineScope {
                // 2a) 离线 Libretro DB ROM Hash / Serial 反查官方名 → Libretro Boxarts 封面
                if (isRom && table != null) {
                    launch(Dispatchers.IO) {
                        try {
                            val romId = runCatching { client.extractIdentity(File(game.id), platform.id) }.getOrNull()
                            if (romId != null && (romId.crc32 != null || romId.serial != null)) {
                                val hashHit = table.find("", "", crc = romId.crc32, serial = romId.serial)
                                    ?.takeIf { it.mode == MetaMatchMode.Crc32Hash || it.mode == MetaMatchMode.SerialId }
                                val official = hashHit?.row?.officialName?.takeIf { it.isNotBlank() }
                                if (official != null) {
                                    val hashBoxarts = runCatching {
                                        covers.searchIndexCandidates(official, platform)
                                    }.getOrDefault(emptyList())
                                    val hashBatch = if (hashBoxarts.isNotEmpty()) {
                                        hashBoxarts.take(3).map { match ->
                                            ManualScrapeCandidate(
                                                title = official,
                                                providerId = ScraperCatalog.MetaIds.LIBRETRO_DB,
                                                coverUrl = match.url,
                                                metadata = hashHit.row.meta,
                                                fallbackCoverUrls = match.fallbackUrls,
                                                matchedByHash = true,
                                            )
                                        }
                                    } else {
                                        listOf(
                                            ManualScrapeCandidate(
                                                title = official,
                                                providerId = ScraperCatalog.MetaIds.LIBRETRO_DB,
                                                coverUrl = null,
                                                metadata = hashHit.row.meta,
                                                matchedByHash = true,
                                            ),
                                        )
                                    }
                                    val changed = synchronized(lock) { addCandidatesLocked(hashBatch) }
                                    if (changed) onPartialResults(snapshot())
                                }
                            }
                        } finally {
                            val remaining = synchronized(lock) {
                                active.remove(ScraperCatalog.MetaIds.LIBRETRO_DB)
                                active.toList()
                            }
                            onActiveSourcesChanged(remaining)
                        }
                    }
                }

                // 2b) 各在线 API 节点并发查询（Hasheous / ScreenScraper 传入真实 ROM 以支持 Hash 命中）
                for (pid in apiProviders) {
                    launch(Dispatchers.IO) {
                        try {
                            val useRealRom = isRom && (
                                pid == ScraperCatalog.MetaIds.HASHEOUS ||
                                    pid == ScraperCatalog.MetaIds.SCREENSCRAPER
                                )
                            val hit = runCatching {
                                client.queryProvider(
                                    providerId = pid,
                                    game = if (useRealRom) game else synthetic,
                                    platform = platform,
                                    cred = cred,
                                    forceRefresh = true,
                                    officialNameHint = cleanKeyword,
                                )
                            }.getOrNull()
                            if (hit != null && (!hit.isEmpty || hit.coverUrls.isNotEmpty())) {
                                val isHashHit = pid == ScraperCatalog.MetaIds.HASHEOUS ||
                                    (pid == ScraperCatalog.MetaIds.SCREENSCRAPER && useRealRom)
                                val title = hit.officialName?.takeIf { it.isNotBlank() } ?: cleanKeyword
                                val batch = if (hit.coverUrls.isNotEmpty()) {
                                    hit.coverUrls.take(3).map { url ->
                                        ManualScrapeCandidate(title, pid, url, hit.meta, matchedByHash = isHashHit)
                                    }
                                } else {
                                    // 若 Hash/API 命中了官方名但未自带图片（如 Hasheous 仅返回游戏名），顺手用该官方名补配 Libretro 封面
                                    val fallbackBoxarts = if (hit.officialName?.isNotBlank() == true) {
                                        runCatching { covers.searchIndexCandidates(title, platform) }.getOrDefault(emptyList())
                                    } else {
                                        emptyList()
                                    }
                                    if (fallbackBoxarts.isNotEmpty()) {
                                        fallbackBoxarts.take(2).map { match ->
                                            ManualScrapeCandidate(
                                                title = title,
                                                providerId = pid,
                                                coverUrl = match.url,
                                                metadata = hit.meta,
                                                fallbackCoverUrls = match.fallbackUrls,
                                                matchedByHash = isHashHit,
                                            )
                                        }
                                    } else {
                                        listOf(ManualScrapeCandidate(title, pid, null, hit.meta, matchedByHash = isHashHit))
                                    }
                                }
                                val changed = synchronized(lock) { addCandidatesLocked(batch) }
                                if (changed) {
                                    onPartialResults(snapshot())
                                }
                            }
                        } finally {
                            val remaining = synchronized(lock) {
                                active.remove(pid)
                                active.toList()
                            }
                            onActiveSourcesChanged(remaining)
                        }
                    }
                }
            }
        } else {
            onActiveSourcesChanged(emptyList())
        }
        snapshot()
    }

    /** 专供 Web 端「在线选图」并发查询 ROM Hash 候选封面（Libretro DB CRC32/Serial + Hasheous + ScreenScraper），仅返回外链 URL。 */
    suspend fun searchByHashForWeb(
        game: Game,
        platform: Platform?,
        covers: CoverSource,
    ): List<ManualScrapeCandidate> = withContext(Dispatchers.IO) {
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) return@withContext emptyList()
        val romId = runCatching { client.extractIdentity(File(game.id), platform?.id) }.getOrNull()
        if (romId == null || (romId.crc32 == null && romId.sha1 == null && romId.serial == null)) {
            return@withContext emptyList()
        }
        val out = ArrayList<ManualScrapeCandidate>()
        val seenUrls = HashSet<String>()
        val lock = Any()
        fun addItem(item: ManualScrapeCandidate) {
            val u = item.coverUrl?.takeIf { it.isNotBlank() } ?: return
            synchronized(lock) {
                if (seenUrls.add(u)) out += item
            }
        }

        val system = platform?.libretroSystem
        val table = system?.let { systemTables[it] }
        val online = NetworkStatus.isOnline(appCtx)
        val cred = credentials

        coroutineScope {
            // 1) 离线 Libretro DB CRC32 / Serial → 官方名 → Libretro Boxarts
            if (table != null && (romId.crc32 != null || romId.serial != null)) {
                launch(Dispatchers.IO) {
                    val hashHit = table.find("", "", crc = romId.crc32, serial = romId.serial)
                        ?.takeIf { it.mode == MetaMatchMode.Crc32Hash || it.mode == MetaMatchMode.SerialId }
                    val official = hashHit?.row?.officialName?.takeIf { it.isNotBlank() } ?: return@launch
                    val boxarts = runCatching { covers.searchIndexCandidates(official, platform) }.getOrDefault(emptyList())
                    boxarts.take(3).forEach { match ->
                        addItem(
                            ManualScrapeCandidate(
                                title = official,
                                providerId = ScraperCatalog.MetaIds.LIBRETRO_DB,
                                coverUrl = match.url,
                                metadata = hashHit.row.meta,
                                fallbackCoverUrls = match.fallbackUrls,
                                matchedByHash = true,
                            ),
                        )
                    }
                }
            }

            // 2) 在线 Hash 源（Hasheous + 已配置凭证时的 ScreenScraper）
            if (online) {
                val hashProviders = buildList {
                    add(ScraperCatalog.MetaIds.HASHEOUS)
                    val ssDesc = ScraperCatalog.metaDescriptor(ScraperCatalog.MetaIds.SCREENSCRAPER)
                    if (ScraperCatalog.hasCredentials(ssDesc?.credentialKind, cred)) {
                        add(ScraperCatalog.MetaIds.SCREENSCRAPER)
                    }
                }
                for (pid in hashProviders) {
                    launch(Dispatchers.IO) {
                        val hit = runCatching {
                            client.queryProvider(
                                providerId = pid,
                                game = game,
                                platform = platform,
                                cred = cred,
                                forceRefresh = false,
                                officialNameHint = _officialNames.value[game.id],
                            )
                        }.getOrNull() ?: return@launch
                        val title = hit.officialName?.takeIf { it.isNotBlank() } ?: game.title
                        if (hit.coverUrls.isNotEmpty()) {
                            hit.coverUrls.take(3).forEach { url ->
                                addItem(ManualScrapeCandidate(title, pid, url, hit.meta, matchedByHash = true))
                            }
                        } else if (!hit.officialName.isNullOrBlank()) {
                            val boxarts = runCatching { covers.searchIndexCandidates(title, platform) }.getOrDefault(emptyList())
                            boxarts.take(2).forEach { match ->
                                addItem(
                                    ManualScrapeCandidate(
                                        title = title,
                                        providerId = pid,
                                        coverUrl = match.url,
                                        metadata = hit.meta,
                                        fallbackCoverUrls = match.fallbackUrls,
                                        matchedByHash = true,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        synchronized(lock) { out.take(6).toList() }
    }

    /** 一键应用手动搜索刮削结果（封面 + 元数据 + 官方名），并永久持久化元数据到 SQLite 防重启丢失。 */
    fun applyManualScrape(
        gameId: String,
        candidate: ManualScrapeCandidate,
        covers: CoverSource,
    ) {
        manualMetaIds.add(gameId)
        metaMissStates.remove(gameId)
        if (!candidate.metadata.isEmpty) {
            gameCache[gameId] = candidate.metadata
            _metadata.update { it + (gameId to candidate.metadata) }
        }
        val title = candidate.title.takeIf { it.isNotBlank() }
        if (title != null) {
            _officialNames.update { it + (gameId to title) }
            if (!CjkAliases.containsCjk(title)) {
                canonicalNames[gameId] = title
            }
            _newlyResolvedNames.tryEmit(setOf(gameId))
        }
        _resolutionInfo.update {
            it + (
                gameId to MetadataResolutionInfo(
                    providers = listOf(candidate.providerId),
                    matchModes = listOf(MetaMatchMode.ApiNameSearch),
                )
            )
        }
        scope.launch(Dispatchers.IO) {
            client.database.putManualMetadata(
                gameId = gameId,
                providerId = candidate.providerId,
                officialName = title,
                canonicalName = canonicalNames[gameId] ?: title,
                meta = candidate.metadata,
            )
        }
        if (candidate.providerId != CoverSource.LOCAL_COVER_PROVIDER) {
            covers.applyManualCover(gameId, candidate.coverUrl, candidate.providerId)
        }
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
        emitNewlyResolved: Boolean = true,
    ) {
        synchronized(batchLock) {
            pendingMeta.putAll(metaUpdates)
            pendingNames.putAll(nameUpdates)
            pendingInfo.putAll(infoUpdates)
            if (immediate) {
                batchFlushJob?.cancel()
                batchFlushJob = null
                flushBatchLocked(emitNewlyResolved = emitNewlyResolved)
            } else if (batchFlushJob?.isActive != true) {
                batchFlushJob = scope.launch(Dispatchers.Default) {
                    delay(140L)
                    synchronized(batchLock) {
                        batchFlushJob = null
                        flushBatchLocked(emitNewlyResolved = true)
                    }
                }
            }
        }
    }

    private fun flushBatchLocked(emitNewlyResolved: Boolean = true) {
        if (pendingMeta.isNotEmpty()) {
            val delta = HashMap(pendingMeta)
            pendingMeta.clear()
            _metadata.update { it + delta }
        }
        if (pendingNames.isNotEmpty()) {
            val delta = HashMap(pendingNames)
            pendingNames.clear()
            _officialNames.update { it + delta }
            if (emitNewlyResolved) {
                _newlyResolvedNames.tryEmit(delta.keys)
            }
        }
        if (pendingInfo.isNotEmpty()) {
            val delta = HashMap(pendingInfo)
            pendingInfo.clear()
            _resolutionInfo.update { it + delta }
        }
    }

    /**
     * 加载指定平台的 Libretro 元数据与哈希表：
     * 1. 立即读取 APK 内置只读底座（`assets/offline_db/meta/`）并与本地缓存文件合并，零等待立刻可用；
     * 2. 若尚无远端缓存文件且联网，则异步在后台拉取 `libretro-database` 5 份 `.dat` 并合并覆盖到本地缓存，
     *    不阻塞当前界面与封面加载。
     */
    private suspend fun getOrFetchLibretroTable(system: String): SystemTable? {
        systemTables[system]?.let { return it }
        val lock = systemLocks.getOrPut(system) { Mutex() }
        return lock.withLock {
            systemTables[system]?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val t0 = System.currentTimeMillis()
                val cacheFile = File(cacheDir, "v3_${sanitizeFileName(system)}.tsv")
                val offlineTable = OfflineGameDb.loadPlatformTable(appCtx, system)
                val tRead = System.currentTimeMillis()
                val bundled = offlineTable.takeUnless { it.isEmpty }
                val cached = if (cacheFile.isFile && cacheFile.length() > 0) {
                    readCachedTsv(cacheFile)
                } else {
                    null
                }
                val initial = if (bundled != null || cached != null) {
                    SystemTable(offline = bundled, overlay = cached)
                } else {
                    null
                }
                runCatching {
                    android.util.Log.i(
                        "PocketDeckPerf",
                        "MetadataRepo.getOrFetch($system): h=${offlineTable.hashToName.size} m=${offlineTable.metaByRawName.size} read=${tRead - t0}ms build=${System.currentTimeMillis() - tRead}ms",
                    )
                }
                if (initial != null) {
                    putSystemTable(system, initial)
                    if (cached == null && NetworkStatus.isOnline(appCtx)) {
                        launchAsyncRemoteMetaMerge(system, cacheFile, bundled)
                    }
                    return@withContext initial
                }
                val remote = if (NetworkStatus.isOnline(appCtx)) {
                    fetchFromLibretroWithFailover(system, cacheFile)
                } else {
                    null
                }
                val remoteTable = remote?.let { SystemTable(offline = null, overlay = it) }
                if (remoteTable != null) {
                    putSystemTable(system, remoteTable)
                }
                remoteTable
            }
        }
    }

    private fun launchAsyncRemoteMetaMerge(
        system: String,
        cacheFile: File,
        bundled: OfflineGameDb.PlatformTable?,
    ) {
        if (remoteMetaRefreshInFlight.putIfAbsent(system, true) != null) return
        scope.launch(Dispatchers.IO) {
            try {
                // 内置离线底座已立即可用：延迟 3 秒再做低优先级远端 .dat 合并，避免切 Tab 瞬间与首屏封面抢 CPU 和触发 GC
                delay(3_000L)
                val remote = fetchFromLibretroWithFailover(system, cacheFile) ?: return@launch
                putSystemTable(system, SystemTable(offline = bundled, overlay = remote))
                // 远端首次拉回新的 .dat 覆盖层时，清理负缓存以便下次访问能命中新增元数据
                metaMissStates.clear()
                client.database.clearResolvedMetadataMisses()
            } finally {
                remoteMetaRefreshInFlight.remove(system)
            }
        }
    }

    private suspend fun fetchFromLibretroWithFailover(
        system: String,
        cacheFile: File,
    ): OverlayTable? = coroutineScope {
        val encodedSys = Uri.encode(system)
        val mirrors = listOf(
            "https://cdn.jsdelivr.net/gh/libretro/libretro-database@master/metadat",
            "https://fastly.jsdelivr.net/gh/libretro/libretro-database@master/metadat",
            "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat",
        )
        val kinds = listOf("developer", "publisher", "genre", "releaseyear", "releasemonth")

        // 优先走 jsDelivr；单个 kind 若遇到网络/5xx 故障自动回退 Fastly / GitHub Raw（404 不重复请求）
        val parsed = kinds.map { kind ->
            async(Dispatchers.IO) {
                var res = DatDownloadResult(emptyList(), transientError = true)
                for (mirror in mirrors) {
                    res = downloadDat("$mirror/$kind/$encodedSys.dat")
                    if (!res.transientError) break
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
                cacheDir.mkdirs()
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

        buildOverlayTable(rows)
    }

    private fun readCachedTsv(file: File): OverlayTable? = runCatching {
        val rows = ArrayList<CachedRow>(2048)
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                val t1 = line.indexOf('\t'); if (t1 < 0) continue
                val t2 = line.indexOf('\t', t1 + 1); if (t2 < 0) continue
                val t3 = line.indexOf('\t', t2 + 1); if (t3 < 0) continue
                val t4 = line.indexOf('\t', t3 + 1); if (t4 < 0) continue
                val t5 = line.indexOf('\t', t4 + 1); if (t5 < 0) continue
                val t6 = line.indexOf('\t', t5 + 1); if (t6 < 0) continue
                val t7 = line.indexOf('\t', t6 + 1)
                val meta = GameMetadata(
                    releaseDate = if (t4 > t3 + 1) line.substring(t3 + 1, t4) else null,
                    genre = if (t5 > t4 + 1) line.substring(t4 + 1, t5) else null,
                    developer = if (t6 > t5 + 1) line.substring(t5 + 1, t6) else null,
                    publisher = (if (t7 < 0) line.substring(t6 + 1) else line.substring(t6 + 1, t7))
                        .takeIf { it.isNotEmpty() },
                )
                if (!meta.isEmpty) {
                    rows += CachedRow(
                        crc = line.substring(0, t1),
                        serial = line.substring(t1 + 1, t2),
                        comment = line.substring(t2 + 1, t3),
                        meta = meta,
                    )
                }
            }
        }
        if (rows.isEmpty()) null else buildOverlayTable(rows)
    }.getOrNull()

    private fun buildOverlayTable(rows: List<CachedRow>): OverlayTable {
        val byCrc = HashMap<String, ResolvedRow>(rows.size)
        val bySerial = HashMap<String, ResolvedRow>(rows.size / 4 + 16)
        val byExact = HashMap<String, ResolvedRow>(rows.size)
        val byTitle = HashMap<String, ResolvedRow>(rows.size)
        val byCompact = HashMap<String, ResolvedRow>(rows.size)

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
                    val comp = TitleNormalizer.compactFromNorm(titleKey)
                    if (comp.isNotEmpty()) {
                        val prevC = byCompact[comp]
                        if (prevC == null || fieldCount(r.meta) > fieldCount(prevC.meta)) {
                            byCompact[comp] = row
                        }
                    }
                }
            }
        }
        return OverlayTable(byCrc, bySerial, byExact, byTitle, byCompact)
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
        if (client.hostCircuitBreaker.shouldSkipUrl(urlStr)) {
            return DatDownloadResult(emptyList(), transientError = true)
        }
        val t0 = System.currentTimeMillis()
        var code: Int? = null
        return try {
            val req = Request.Builder()
                .url(urlStr)
                .header("User-Agent", USER_AGENT)
                .build()
            client.http.newCall(req).execute().use { resp ->
                code = resp.code
                client.hostCircuitBreaker.recordSuccessForUrl(urlStr)
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
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val tripped = if (t is IOException) client.hostCircuitBreaker.recordTransportFailureForUrl(urlStr) else false
            val suffix = if (tripped) " [circuit open ${client.hostCircuitBreaker.cooldownSeconds()}s]" else ""
            ScrapeLogStore.log("META", ScrapeLogStore.nodeFromUrl(urlStr), "GET DAT", url = urlStr, status = code, elapsedMs = System.currentTimeMillis() - t0, error = "${t.javaClass.simpleName}: ${t.message}$suffix")
            DatDownloadResult(emptyList(), transientError = true)
        }
    }

    internal companion object {
        const val USER_AGENT = "PocketDeck/0.4 (Android Handheld Launcher)"
        const val MAX_CACHED_SYSTEM_TABLES = 6
        const val MAX_CONCURRENT_PRELOAD_SYSTEMS = 2
        const val SCRAPE_CONCURRENCY = 4
        const val SCRAPE_FLUSH_BATCH_SIZE = 16
        const val SCRAPE_FLUSH_INTERVAL_MS = 450L
        const val SCRAPE_FLUSH_LARGE_BATCH_SIZE = 64
        const val SCRAPE_FLUSH_LARGE_INTERVAL_MS = 1_000L
        const val RESOLVE_INCREMENTAL_BATCH = 48
        const val SCRAPE_PROGRESS_THROTTLE_MS = 200L
        val NAME_SEARCH_META_PROVIDERS = setOf(
            ScraperCatalog.MetaIds.THEGAMESDB,
            ScraperCatalog.MetaIds.IGDB,
            ScraperCatalog.MetaIds.WIKIDATA,
        )
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

        fun sanitizeFileName(name: String): String = sanitizeFileKey(name)
    }
}
