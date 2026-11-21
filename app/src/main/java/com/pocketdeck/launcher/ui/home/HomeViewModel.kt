package com.pocketdeck.launcher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketdeck.launcher.data.AppTheme
import com.pocketdeck.launcher.data.BackgroundStyle
import com.pocketdeck.launcher.data.CoverResolutionInfo
import com.pocketdeck.launcher.data.CustomLauncherPreset
import com.pocketdeck.launcher.data.GalleryAlign
import com.pocketdeck.launcher.data.MetadataDisplayConfig
import com.pocketdeck.launcher.data.MetadataResolutionInfo
import com.pocketdeck.launcher.data.RomDir
import com.pocketdeck.launcher.data.Category
import com.pocketdeck.launcher.data.CategorySortOrder
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GameRepository
import com.pocketdeck.launcher.data.MetadataRepository
import java.io.File
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.ScraperCredentials
import com.pocketdeck.launcher.data.SourceItemConfig
import com.pocketdeck.launcher.data.SystemTabConfig
import com.pocketdeck.launcher.data.ThemeAccent
import com.pocketdeck.launcher.data.UserData
import com.pocketdeck.launcher.data.ViewMode
import com.pocketdeck.launcher.ui.theme.SystemFonts
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.launch.GameLauncher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class MetaFieldKey { ReleaseDate, Developer, Publisher, FileSize }

data class HomeUiState(
    val tabs: List<TabInfo> = emptyList(),
    val selectedTab: Int = 0,
    val games: List<Game> = emptyList(),
    val platformsById: Map<String, Platform> = emptyMap(),
    val platform: Platform? = null,
    val focusIndex: Int = 0,
    val viewMode: ViewMode = ViewMode.Gallery,
    val showMetadata: Boolean = true,
    val metaDisplay: MetadataDisplayConfig = MetadataDisplayConfig(),
    val bottomBar: Boolean = true,
    val is12h: Boolean = false,
    val swapAbxy: Boolean = false,
    val searching: Boolean = false,
    val searchQuery: String = "",
    val searchAllCategories: Boolean = true,
    val menuGame: Game? = null,
    val detailGame: Game? = null,
    val propertiesGame: Game? = null,
    val editingGame: Game? = null,
    val manualScrapeGame: Game? = null,
    val quickHudEnabled: Boolean = false,
    val showQuickHud: Boolean = false,
    val showApps: Boolean = false,
    val showLibraryManager: Boolean = false,
    val libraryManagerHiddenOnly: Boolean = false,
    val libraryManagerInitialDir: String? = null,
    val libraryManagerInitialFocusFile: String? = null,
    val showViewMenu: Boolean = false,
    val viewMenuIndex: Int = 0,
    val scanning: Boolean = false,
    val libraryLoaded: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val showSettings: Boolean = false,
    val romRoot: String? = null,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.SunsetFlow,
    val theme: AppTheme = AppTheme.System,
    val metadataMap: Map<String, GameMetadata> = emptyMap(),
    val officialNames: Map<String, String> = emptyMap(),
    /** 状态栏图标显示控制 */
    val showClock: Boolean = true,
    val showBattery: Boolean = true,
    val showWifi: Boolean = true,
    val showBluetooth: Boolean = true,
    val showMobileSignal: Boolean = true,
    val showHeadset: Boolean = true,
    val showAirplane: Boolean = true,
    val showDnd: Boolean = true,
    /** 每平台首选启动器 */
    val platformLaunchers: Map<String, String> = emptyMap(),
    /** 单游戏启动器覆盖 */
    val gameLaunchers: Map<String, String> = emptyMap(),
    val galleryAlign: GalleryAlign = GalleryAlign.Top,
    /** 设置页标签管理：全部标签（含隐藏），按显示顺序。 */
    val manageTabs: List<TabEntry> = emptyList(),
    /** 用户配置的 ROM 目录；为空时为自动检测。 */
    val romDirs: List<RomDirEntry> = emptyList(),
    /** 自动检测到的目录（romDirs 为空时生效）。 */
    val autoRoot: String? = null,
    val categories: List<Category> = emptyList(),
    val coverSources: List<SourceItemConfig> = ScraperCatalog.defaultCoverConfigs(),
    val metadataSources: List<SourceItemConfig> = ScraperCatalog.defaultMetadataConfigs(),
    val scraperCredentials: ScraperCredentials = ScraperCredentials(),
    val hiddenIds: Set<String> = emptySet(),
    val showAndroidGames: Boolean = true,
    val androidGamePackages: Set<String> = emptySet(),
    val excludedAndroidPackages: Set<String> = emptySet(),
    val autoAndroidPackages: Set<String> = emptySet(),
    val customBackground: String? = null,
    val keyBindings: Map<KeyAction, Int> = emptyMap(),
    val keySoundEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val themeAccent: ThemeAccent = ThemeAccent.Sunset,
    val fontId: String = SystemFonts.DEFAULT_ID,
    val platformAspects: Map<String, Float> = emptyMap(),
    val customLaunchers: List<CustomLauncherPreset> = emptyList(),
    val systemTabs: Map<String, SystemTabConfig> = emptyMap(),
    val uncategorizedPlatforms: List<UncategorizedPlatformInfo> = emptyList(),
    val uncategorizedCount: Int = 0,
    val playStats: Map<String, com.pocketdeck.launcher.data.PlayStats> = emptyMap(),
) {
    fun platformFor(game: Game): Platform =
        platformsById[game.platformId] ?: PlatformCatalog.forFolder(game.platformId).let { p ->
            val aspect = platformAspects[p.id]
            if (aspect != null) p.copy(coverAspect = aspect) else p
        }

    fun effectiveLauncherFor(game: Game): String? =
        gameLaunchers[game.id] ?: platformLaunchers[game.platformId]
}

data class UncategorizedPlatformInfo(
    val platform: Platform,
    val gameCount: Int,
)

data class TabInfo(
    val id: String,
    val label: String,
    val type: TabType,
    val followSystemLanguage: Boolean = true,
)

enum class TabType { Now, Favs, Custom, Other }

/** 标签管理用的完整条目（包括被隐藏的）。 */
data class TabEntry(
    val id: String,
    val label: String,
    val type: TabType,
    val hidden: Boolean,
    /** 自定义分类合并进来的平台简称。 */
    val includes: List<String> = emptyList(),
    val gameCount: Int = 0,
    val followSystemLanguage: Boolean = true,
)

/** 目录管理用的条目。 */
data class RomDirEntry(val path: String, val enabled: Boolean, val count: Int?)

class HomeViewModel(
    private val repo: GameRepository,
    val covers: CoverSource,
    private val metadataRepo: MetadataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 独立焦点索引流：方向键滚动游戏时只更新此流，避免整个 1200 行 HomeScreen 与顶栏/底栏跟随重组。 */
    private val _focusIndex = MutableStateFlow(0)
    val focusIndex: StateFlow<Int> = _focusIndex.asStateFlow()

    val fullLibrary: StateFlow<Library?> = repo.fullLibrary
    val filesRevision: StateFlow<Long> = repo.filesRevision
    val fileTasks = repo.fileTasks
    val metaResolutionMap: StateFlow<Map<String, MetadataResolutionInfo>> = metadataRepo.resolutionInfo
    val coverResolutionMap: StateFlow<Map<String, CoverResolutionInfo>> = covers.coverInfo
    val scrapeProgress: StateFlow<com.pocketdeck.launcher.data.ScrapeProgress> = metadataRepo.scrapeProgress

    private val _focusPerTab = HashMap<String, Int>()
    private val _focusGameIdPerTab = HashMap<String, String>()
    private var initialTabPicked = false
    private var focusResolveJob: Job? = null
    private var activeSessionGameId: String? = null
    private var activeSessionStartElapsedMs: Long = 0L

    /**
     * 活跃标签页排序列表 LRU 缓存（仅常驻最近访问的 5 个标签页），
     * 避免 30+ 分类、上万款游戏场景下反复全量排序或常驻过多分类列表占用内存。
     */
    private data class TabGamesCacheKey(
        val tabId: String,
        val tabType: TabType,
        val libIdentity: Int,
        val sortOrder: CategorySortOrder?,
        val listSignature: Int,
    )

    private val tabGamesLru = object : LinkedHashMap<TabGamesCacheKey, List<Game>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TabGamesCacheKey, List<Game>>?): Boolean =
            size > 5
    }

    init {
        viewModelScope.launch {
            var lastLib: Library? = null
            var lastUser: UserData? = null
            var lastScanning: Boolean? = null
            var lastOfficialNames: Map<String, String> = emptyMap()
            combine(
                combine(repo.library, repo.user, repo.scanning) { lib, user, scanning ->
                    Triple(lib, user, scanning)
                },
                combine(
                    metadataRepo.metadata,
                    metadataRepo.officialNames,
                ) { metaMap, officialNames ->
                    metaMap to officialNames
                },
            ) { (lib, user, scanning), (metaMap, officialNames) ->
                CombinedData(
                    lib = lib,
                    user = user,
                    scanning = scanning,
                    metaMap = metaMap,
                    officialNames = officialNames,
                )
            }.collect { data ->
                if (data.officialNames !== lastOfficialNames) {
                    val newlyResolved = data.officialNames.entries
                        .filter { (id, name) -> lastOfficialNames[id] != name }
                        .map { it.key }
                    lastOfficialNames = data.officialNames
                    if (newlyResolved.isNotEmpty()) {
                        covers.onOfficialNamesResolved(newlyResolved)
                    }
                }
                val u = data.user
                val curLib = data.lib
                if (curLib != null && curLib !== lastLib && curLib.localMetadata.isNotEmpty()) {
                    metadataRepo.seedLocalMetadata(curLib.localMetadata)
                }
                if (u != null && u !== lastUser) {
                    covers.configure(u.coverSources, u.scraperCredentials)
                    metadataRepo.configure(u.metadataSources, u.scraperCredentials)
                }
                if (data.lib != null && u != null) {
                    val onlyMetaChanged = data.lib === lastLib && u === lastUser && data.scanning == lastScanning
                    val cur = _state.value
                    val activeTab = cur.tabs.getOrNull(cur.selectedTab)
                    val activeSort = when (activeTab?.type) {
                        TabType.Now -> u.systemTabs["now"]?.sortOrder
                        TabType.Favs -> u.systemTabs["favs"]?.sortOrder ?: CategorySortOrder.TitleAsc
                        TabType.Other -> u.systemTabs["other"]?.sortOrder ?: CategorySortOrder.TitleAsc
                        TabType.Custom -> u.categories.firstOrNull { it.id == activeTab.id }?.sortOrder
                        null -> null
                    }
                    val sortsByRelease = activeSort == CategorySortOrder.ReleaseNewest ||
                        activeSort == CategorySortOrder.ReleaseOldest
                    if (onlyMetaChanged && !sortsByRelease) {
                        _state.value = cur.copy(
                            metadataMap = data.metaMap,
                            officialNames = data.officialNames,
                        )
                    } else {
                        lastLib = data.lib
                        lastUser = u
                        lastScanning = data.scanning
                        updateState(
                            data.lib,
                            u,
                            data.scanning,
                            data.metaMap,
                            data.officialNames,
                        )
                    }
                } else {
                    lastLib = null
                    lastUser = u
                    lastScanning = data.scanning
                    _state.value = _state.value.copy(
                        scanning = data.scanning,
                        libraryLoaded = data.lib != null,
                        theme = u?.theme ?: _state.value.theme,
                        themeAccent = u?.themeAccent ?: _state.value.themeAccent,
                        fontId = u?.fontId ?: _state.value.fontId,
                        platformAspects = u?.platformAspects ?: _state.value.platformAspects,
                        customLaunchers = u?.customLaunchers ?: _state.value.customLaunchers,
                        systemTabs = u?.systemTabs ?: _state.value.systemTabs,
                        backgroundStyle = u?.backgroundStyle ?: _state.value.backgroundStyle,
                        customBackground = u?.customBackground ?: _state.value.customBackground,
                        keyBindings = u?.keyBindings ?: _state.value.keyBindings,
                        keySoundEnabled = u?.keySoundEnabled ?: _state.value.keySoundEnabled,
                        reduceMotion = u?.reduceMotion ?: _state.value.reduceMotion,
                        viewMode = u?.viewMode ?: _state.value.viewMode,
                        metaDisplay = u?.metaDisplay ?: _state.value.metaDisplay,
                        metadataMap = data.metaMap,
                        officialNames = data.officialNames,
                    )
                }
            }
        }
        repo.refresh()
    }

    private data class CombinedData(
        val lib: Library?,
        val user: UserData?,
        val scanning: Boolean,
        val metaMap: Map<String, GameMetadata>,
        val officialNames: Map<String, String>,
    )

    private fun updateState(
        lib: Library,
        user: UserData,
        scanning: Boolean,
        metaMap: Map<String, GameMetadata>,
        officialNames: Map<String, String>,
        stateOverride: HomeUiState? = null,
    ) {
        val manage = buildManageTabs(lib, user)
        val tabs = manage.filter { !it.hidden }.map {
            TabInfo(it.id, it.label, it.type, it.followSystemLanguage)
        }
        val current = stateOverride ?: _state.value
        val prevTabId = current.tabs.getOrNull(current.selectedTab)?.id
        var tabIdx = if (prevTabId != null) {
            val found = tabs.indexOfFirst { it.id == prevTabId }
            if (found >= 0) found else current.selectedTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        } else {
            current.selectedTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        }
        if (!initialTabPicked && tabs.isNotEmpty() && (!scanning || lib.byId.isNotEmpty())) {
            val nonEmpty = tabs.indexOfFirst { getGamesForTab(it, lib, user, metaMap).isNotEmpty() }
            if (nonEmpty >= 0 || !scanning) {
                initialTabPicked = true
                // 首次打开如果当前默认标签（NOW / FAVS）为空，自动跳到第一个有游戏的标签
                if (getGamesForTab(tabs[tabIdx], lib, user, metaMap).isEmpty() && nonEmpty >= 0) {
                    tabIdx = nonEmpty
                }
            }
        }
        val tab = tabs.getOrNull(tabIdx)
        val rawGames = getGamesForTab(tab, lib, user, metaMap)
        val effectiveSearchAll = if (stateOverride != null) current.searchAllCategories else user.searchAllCategories
        val filteredGames = if (current.searching) {
            if (current.searchQuery.isBlank()) {
                emptyList()
            } else {
                val searchPool = if (effectiveSearchAll) {
                    lib.byId.values.filter { it.id !in user.hidden }.sortedBy { it.title.lowercase() }
                } else {
                    rawGames
                }
                applySearch(searchPool, current.searchQuery)
            }
        } else {
            rawGames
        }
        val focusIdx = if (current.searching) {
            _focusIndex.value.coerceIn(0, (filteredGames.size - 1).coerceAtLeast(0))
        } else {
            val tabId = tab?.id.orEmpty()
            val savedGameId = _focusGameIdPerTab[tabId]
            val byIdIdx = if (savedGameId != null) filteredGames.indexOfFirst { it.id == savedGameId } else -1
            if (byIdIdx >= 0) {
                byIdIdx
            } else {
                _focusPerTab.getOrDefault(tabId, _focusIndex.value)
                    .coerceIn(0, (filteredGames.size - 1).coerceAtLeast(0))
            }
        }
        _focusIndex.value = focusIdx
        val platformsById = lib.platforms.associateBy { it.id }
        val platform = tab?.let { lib.platform(it.id) }

        val categorizedPlatforms = user.categories.flatMap { it.sources }.toSet()
        val categorizedGames = user.categories.flatMap { it.games }.toSet()
        val uncategorizedGames = lib.byId.values.filter {
            it.platformId !in categorizedPlatforms && it.id !in categorizedGames
        }
        val uncategorizedByPlatform = uncategorizedGames
            .groupBy { it.platformId }
            .entries
            .map { (pid, list) ->
                val plat = platformsById[pid] ?: PlatformCatalog.forFolder(pid).let { p ->
                    val aspect = user.platformAspects[p.id]
                    if (aspect != null) p.copy(coverAspect = aspect) else p
                }
                UncategorizedPlatformInfo(plat, list.size)
            }
            .sortedWith(compareBy({ PlatformCatalog.sortKey(it.platform.id) }, { it.platform.shortName }))

        val autoAndroidPkgs = lib.games[PlatformCatalog.ANDROID_ID].orEmpty()
            .map { it.id.removePrefix(GameLauncher.APK_PREFIX) }
            .filter { it !in user.androidGamePackages }
            .toSet()

        val newState = current.copy(
            tabs = tabs,
            selectedTab = tabIdx,
            games = filteredGames,
            platformsById = platformsById,
            platform = platform,
            focusIndex = focusIdx,
            viewMode = user.viewMode,
            showMetadata = user.showMetadata,
            metaDisplay = user.metaDisplay,
            bottomBar = user.bottomBar,
            is12h = user.clock12h,
            swapAbxy = user.swapAbxy,
            scanning = scanning,
            libraryLoaded = true,
            favorites = user.favorites,
            romRoot = user.romRoot,
            backgroundStyle = user.backgroundStyle,
            theme = user.theme,
            themeAccent = user.themeAccent,
            fontId = user.fontId,
            platformAspects = user.platformAspects,
            customLaunchers = user.customLaunchers,
            systemTabs = user.systemTabs,
            uncategorizedPlatforms = uncategorizedByPlatform,
            uncategorizedCount = uncategorizedGames.size,
            metadataMap = metaMap,
            officialNames = officialNames,
            menuGame = current.menuGame?.let { lib.byId[it.id] ?: it },
            detailGame = current.detailGame?.let { lib.byId[it.id] ?: it },
            propertiesGame = current.propertiesGame?.let { lib.byId[it.id] ?: it },
            editingGame = current.editingGame?.let { lib.byId[it.id] ?: it },
            showClock = user.showClock,
            showBattery = user.showBattery,
            showWifi = user.showWifi,
            showBluetooth = user.showBluetooth,
            showMobileSignal = user.showMobileSignal,
            showHeadset = user.showHeadset,
            showAirplane = user.showAirplane,
            showDnd = user.showDnd,
            platformLaunchers = user.platformLaunchers,
            gameLaunchers = user.gameLaunchers,
            galleryAlign = user.galleryAlign,
            manageTabs = manage,
            romDirs = user.romDirs.map { RomDirEntry(it.path, it.enabled, lib.rootCounts[it.path]) },
            autoRoot = if (user.romDirs.isEmpty()) lib.root else null,
            categories = user.categories,
            coverSources = user.coverSources,
            metadataSources = user.metadataSources,
            scraperCredentials = user.scraperCredentials,
            hiddenIds = user.hidden,
            showAndroidGames = user.showAndroidGames,
            androidGamePackages = user.androidGamePackages,
            excludedAndroidPackages = user.excludedAndroidPackages,
            autoAndroidPackages = autoAndroidPkgs,
            customBackground = user.customBackground,
            keyBindings = user.keyBindings,
            keySoundEnabled = user.keySoundEnabled,
            reduceMotion = user.reduceMotion,
            playStats = user.playStats,
            quickHudEnabled = user.quickHudEnabled,
            searchAllCategories = effectiveSearchAll,
        )
        _state.value = newState
        preloadAssets(filteredGames, focusIdx, newState::platformFor)
    }

    private fun preloadAssets(
        games: List<Game>,
        focusIdx: Int,
        platformResolver: (Game) -> Platform,
    ) {
        if (games.isEmpty()) return
        // 为当前列表涉及的每个平台触发一次在线封面索引 & 元数据加载（支持 FAVS / NOW 混排）
        val seenSystems = HashSet<String>()
        for (g in games) {
            val plat = platformResolver(g)
            val sys = plat.libretroSystem
            if (sys != null && seenSystems.add(sys)) {
                covers.ensureIndexLoaded(plat)
            }
        }
        val focusGame = games.getOrNull(focusIdx)
        if (focusGame != null) {
            val plat = platformResolver(focusGame)
            metadataRepo.resolveFocusGame(focusGame, plat)
        }
        covers.ensureWindowCoversScraped(games, focusIdx, platformResolver)
        metadataRepo.ensureLoaded(games, platformResolver)
    }

    private fun buildManageTabs(lib: Library, user: UserData): List<TabEntry> {
        val categorizedPlatforms = user.categories.flatMap { it.sources }.toSet()
        val categorizedGames = user.categories.flatMap { it.games }.toSet()
        val nowCfg = user.systemTabs["now"] ?: SystemTabConfig()
        val favsCfg = user.systemTabs["favs"] ?: SystemTabConfig()
        val otherCfg = user.systemTabs["other"] ?: SystemTabConfig()

        val nowCount = user.recents.count { it in lib.byId }
        val favsCount = user.favorites.count { it in lib.byId }
        val otherCount = lib.byId.values.count {
            it.platformId !in categorizedPlatforms && it.id !in categorizedGames
        }

        val all = buildList {
            add(
                TabEntry(
                    id = "now",
                    label = nowCfg.customName.ifBlank { "NOW" },
                    type = TabType.Now,
                    hidden = !user.nowTab,
                    gameCount = nowCount,
                    followSystemLanguage = nowCfg.followSystemLanguage,
                ),
            )
            add(
                TabEntry(
                    id = "favs",
                    label = favsCfg.customName.ifBlank { "FAVS" },
                    type = TabType.Favs,
                    hidden = !user.favsTab,
                    gameCount = favsCount,
                    followSystemLanguage = favsCfg.followSystemLanguage,
                ),
            )
            user.categories.forEach { c ->
                val names = c.sources.map { sid -> lib.platform(sid)?.shortName ?: PlatformCatalog.forFolder(sid).shortName }
                val catCount = (c.sources.flatMap { lib.games[it].orEmpty() } + c.games.mapNotNull { lib.byId[it] })
                    .distinctBy { it.id }
                    .size
                add(
                    TabEntry(
                        id = c.id,
                        label = c.name,
                        type = TabType.Custom,
                        hidden = c.id in user.hiddenTabs,
                        includes = names,
                        gameCount = catCount,
                        followSystemLanguage = false,
                    ),
                )
            }
            add(
                TabEntry(
                    id = "other",
                    label = otherCfg.customName.ifBlank { "OTHER" },
                    type = TabType.Other,
                    hidden = "other" in user.hiddenTabs,
                    gameCount = otherCount,
                    followSystemLanguage = otherCfg.followSystemLanguage,
                ),
            )
        }
        val order = user.tabOrder.withIndex().associate { it.value to it.index }
        return all.withIndex()
            .sortedBy { (i, t) -> order[t.id] ?: (100_000 + i) }
            .map { it.value }
    }

    private fun getGamesForTab(
        tab: TabInfo?,
        lib: Library,
        user: UserData,
        metaMap: Map<String, GameMetadata> = emptyMap(),
    ): List<Game> {
        if (tab == null) return emptyList()
        val sortOrder: CategorySortOrder? = when (tab.type) {
            TabType.Now -> user.systemTabs["now"]?.sortOrder
            TabType.Favs -> user.systemTabs["favs"]?.sortOrder ?: CategorySortOrder.TitleAsc
            TabType.Custom -> user.categories.firstOrNull { it.id == tab.id }?.sortOrder ?: CategorySortOrder.TitleAsc
            TabType.Other -> user.systemTabs["other"]?.sortOrder ?: CategorySortOrder.TitleAsc
        }
        val needsMeta = sortOrder == CategorySortOrder.ReleaseNewest || sortOrder == CategorySortOrder.ReleaseOldest
        val needsPlayStats = tab.type == TabType.Now ||
            sortOrder == CategorySortOrder.LastPlayedDesc ||
            sortOrder == CategorySortOrder.PlayTimeDesc
        val listSig = when (tab.type) {
            TabType.Now -> user.recents.hashCode()
            TabType.Favs -> user.favorites.hashCode()
            TabType.Custom -> user.categories.firstOrNull { it.id == tab.id }.hashCode()
            TabType.Other -> user.categories.hashCode()
        } * 31 + (if (needsMeta) metaMap.size else 0) + (if (needsPlayStats) user.playStats.hashCode() else 0)
        val cacheKey = TabGamesCacheKey(
            tabId = tab.id,
            tabType = tab.type,
            libIdentity = System.identityHashCode(lib),
            sortOrder = sortOrder,
            listSignature = listSig,
        )
        tabGamesLru[cacheKey]?.let { return it }

        val computed = when (tab.type) {
            TabType.Now -> {
                val raw = user.recents.mapNotNull { lib.byId[it] }
                val effectiveOrder = sortOrder ?: CategorySortOrder.LastPlayedDesc
                sortCategoryGames(raw, effectiveOrder, metaMap, user.playStats)
            }
            TabType.Favs -> {
                val raw = user.favorites.mapNotNull { lib.byId[it] }
                sortCategoryGames(raw, sortOrder ?: CategorySortOrder.TitleAsc, metaMap, user.playStats)
            }
            TabType.Custom -> {
                val cat = user.categories.firstOrNull { it.id == tab.id } ?: return emptyList()
                val raw = (cat.sources.flatMap { lib.games[it].orEmpty() } + cat.games.mapNotNull { lib.byId[it] })
                    .distinctBy { it.id }
                sortCategoryGames(raw, cat.sortOrder, metaMap, user.playStats)
            }
            TabType.Other -> {
                val categorizedPlatforms = user.categories.flatMap { it.sources }.toSet()
                val categorizedGames = user.categories.flatMap { it.games }.toSet()
                val raw = lib.byId.values
                    .filter { it.platformId !in categorizedPlatforms && it.id !in categorizedGames }
                sortCategoryGames(raw, sortOrder ?: CategorySortOrder.TitleAsc, metaMap, user.playStats)
            }
        }
        tabGamesLru[cacheKey] = computed
        return computed
    }

    private fun sortCategoryGames(
        games: List<Game>,
        order: CategorySortOrder,
        metaMap: Map<String, GameMetadata>,
        playStats: Map<String, com.pocketdeck.launcher.data.PlayStats> = emptyMap(),
    ): List<Game> = when (order) {
        CategorySortOrder.TitleAsc -> games.sortedWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title) }
        CategorySortOrder.TitleDesc -> games.sortedWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(b.title, a.title) }
        CategorySortOrder.ReleaseNewest -> {
            val keys = HashMap<String, Int>(games.size * 2)
            for (g in games) {
                parseReleaseKey(metaMap[g.id]?.releaseDate)?.let { keys[g.id] = it }
            }
            games.sortedWith { a, b ->
                val ka = keys[a.id]
                val kb = keys[b.id]
                when {
                    ka == null && kb == null -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
                    ka == null -> 1
                    kb == null -> -1
                    ka != kb -> kb.compareTo(ka)
                    else -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
                }
            }
        }
        CategorySortOrder.ReleaseOldest -> {
            val keys = HashMap<String, Int>(games.size * 2)
            for (g in games) {
                parseReleaseKey(metaMap[g.id]?.releaseDate)?.let { keys[g.id] = it }
            }
            games.sortedWith { a, b ->
                val ka = keys[a.id]
                val kb = keys[b.id]
                when {
                    ka == null && kb == null -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
                    ka == null -> 1
                    kb == null -> -1
                    ka != kb -> ka.compareTo(kb)
                    else -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
                }
            }
        }
        CategorySortOrder.ModifiedNewest -> games.sortedWith { a, b ->
            val cmp = b.lastModifiedMs.compareTo(a.lastModifiedMs)
            if (cmp != 0) cmp else String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
        }
        CategorySortOrder.SizeDesc -> games.sortedWith { a, b ->
            val emptyA = a.fileSize <= 0L
            val emptyB = b.fileSize <= 0L
            when {
                emptyA != emptyB -> if (emptyA) 1 else -1
                a.fileSize != b.fileSize -> b.fileSize.compareTo(a.fileSize)
                else -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
            }
        }
        CategorySortOrder.SizeAsc -> games.sortedWith { a, b ->
            val emptyA = a.fileSize <= 0L
            val emptyB = b.fileSize <= 0L
            when {
                emptyA != emptyB -> if (emptyA) 1 else -1
                a.fileSize != b.fileSize -> a.fileSize.compareTo(b.fileSize)
                else -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
            }
        }
        CategorySortOrder.LastPlayedDesc -> games.sortedWith { a, b ->
            val ta = playStats[a.id]?.lastPlayedAt ?: 0L
            val tb = playStats[b.id]?.lastPlayedAt ?: 0L
            if (ta != tb) tb.compareTo(ta) else 0
        }
        CategorySortOrder.PlayTimeDesc -> games.sortedWith { a, b ->
            val da = playStats[a.id]?.totalPlayMs ?: 0L
            val db = playStats[b.id]?.totalPlayMs ?: 0L
            when {
                da != db -> db.compareTo(da)
                else -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
            }
        }
    }

    private fun parseReleaseKey(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val yMatch = RELEASE_YEAR_REGEX.find(raw) ?: return null
        val year = yMatch.groupValues[1].toIntOrNull() ?: return null
        val afterYear = raw.substring(yMatch.range.last + 1)
        var month = 0
        var day = 0
        var found = 0
        for (m in RELEASE_NUM_REGEX.findAll(afterYear)) {
            val v = m.value.toIntOrNull() ?: continue
            if (found == 0) {
                if (v in 1..12) month = v
                found++
            } else if (found == 1) {
                if (v in 1..31) day = v
                break
            }
        }
        if (month == 0) month = monthNameToNumber(raw) ?: 0
        return year * 10000 + month * 100 + day
    }

    private fun monthNameToNumber(raw: String): Int? {
        val low = raw.lowercase()
        val idx = RELEASE_MONTHS.indexOfFirst { it in low }
        return if (idx >= 0) idx + 1 else null
    }

    private fun applySearch(games: List<Game>, query: String): List<Game> {
        if (query.isBlank()) return games
        val q = query.trim().lowercase()
        return games.filter { it.title.lowercase().contains(q) }
    }

    fun selectTab(index: Int) {
        val current = _state.value
        if (current.tabs.isEmpty()) return
        val clamped = index.coerceIn(0, current.tabs.lastIndex)
        current.tabs.getOrNull(current.selectedTab)?.let {
            _focusPerTab[it.id] = _focusIndex.value
        }
        _state.value = current.copy(selectedTab = clamped)
        recomputeFromRepo()
    }

    fun nextTab() {
        val size = _state.value.tabs.size
        if (size > 0) selectTab((_state.value.selectedTab + 1) % size)
    }

    fun prevTab() {
        val size = _state.value.tabs.size
        if (size > 0) selectTab((_state.value.selectedTab - 1 + size) % size)
    }

    fun setFocus(index: Int) {
        val s = _state.value
        val newIdx = index.coerceIn(0, (s.games.size - 1).coerceAtLeast(0))
        if (newIdx == _focusIndex.value) return
        if (!s.searching) {
            s.tabs.getOrNull(s.selectedTab)?.let { tab ->
                _focusPerTab[tab.id] = newIdx
                s.games.getOrNull(newIdx)?.let { _focusGameIdPerTab[tab.id] = it.id }
            }
        }
        // 只更新独立 focusIndex 流，不拷贝 HomeUiState，避免主界面 1200 行全树重组
        _focusIndex.value = newIdx
        // 滑动焦点时通过可替换低并发队列调度当前窗口 16 款封面（内置 180ms 防抖：保活在途、清空未开始的旧等待队列）
        covers.ensureWindowCoversScraped(s.games, newIdx, s::platformFor)
        val focusGame = s.games.getOrNull(newIdx)
        focusResolveJob?.cancel()
        if (focusGame != null) {
            val plat = s.platformFor(focusGame)
            focusResolveJob = viewModelScope.launch {
                delay(220)
                metadataRepo.resolveFocusGame(focusGame, plat)
            }
        }
    }

    fun moveFocus(delta: Int) = setFocus(_focusIndex.value + delta)

    fun toggleSearch() {
        val s = _state.value
        val nextSearching = !s.searching
        val nextState = s.copy(
            searching = nextSearching,
            searchQuery = if (!nextSearching) "" else s.searchQuery,
            focusIndex = if (nextSearching) 0 else s.focusIndex,
        )
        if (nextSearching) _focusIndex.value = 0
        if (!recomputeFromRepo(nextState)) {
            _state.value = nextState
        }
    }

    fun toggleSearchAllCategories() {
        val nextVal = !_state.value.searchAllCategories
        _focusIndex.value = 0
        val nextState = _state.value.copy(
            searchAllCategories = nextVal,
            focusIndex = 0,
        )
        if (!recomputeFromRepo(nextState)) {
            _state.value = nextState
        }
        viewModelScope.launch {
            repo.updatePrefs { it.copy(searchAllCategories = nextVal) }
        }
    }

    fun setSearchQuery(q: String) {
        _focusIndex.value = 0
        val nextState = _state.value.copy(searchQuery = q, focusIndex = 0)
        if (!recomputeFromRepo(nextState)) {
            _state.value = nextState
        }
    }

    private fun recomputeFromRepo(stateOverride: HomeUiState? = null): Boolean {
        val lib = repo.library.value ?: return false
        val user = repo.user.value ?: return false
        updateState(
            lib,
            user,
            (stateOverride ?: _state.value).scanning,
            metadataRepo.metadata.value,
            metadataRepo.officialNames.value,
            stateOverride = stateOverride,
        )
        return true
    }

    fun showMenu(game: Game) { _state.value = _state.value.copy(menuGame = null, detailGame = game) }
    fun hideMenu() { _state.value = _state.value.copy(menuGame = null, detailGame = null) }

    fun openProperties(game: Game) {
        _state.value = _state.value.copy(menuGame = null, propertiesGame = game)
    }

    fun closeProperties() {
        _state.value = _state.value.copy(propertiesGame = null)
    }

    suspend fun computeCrc32(path: String): String? = metadataRepo.computeCrc32(path)

    fun openEdit(game: Game) {
        _state.value = _state.value.copy(menuGame = null, detailGame = null, editingGame = game)
    }

    fun closeEdit() {
        _state.value = _state.value.copy(editingGame = null)
    }

    fun openDetail(game: Game) {
        _state.value = _state.value.copy(menuGame = null, detailGame = game)
    }

    fun closeDetail() {
        _state.value = _state.value.copy(detailGame = null)
    }

    fun toggleQuickHud() {
        val cur = _state.value
        if (!cur.quickHudEnabled && !cur.showQuickHud) return
        _state.value = cur.copy(showQuickHud = !cur.showQuickHud)
    }

    fun closeQuickHud() {
        _state.value = _state.value.copy(showQuickHud = false)
    }

    fun toggleQuickHudEnabled() {
        viewModelScope.launch {
            repo.updatePrefs { it.copy(quickHudEnabled = !it.quickHudEnabled) }
        }
    }

    /** I6: 手柄 L2/R2 按首字母 A–Z / `#` 跳跃并返回目标首字母徽章文本。 */
    fun jumpLetter(direction: Int): String? {
        val games = _state.value.games
        if (games.isEmpty()) return null
        fun bucketOf(title: String): String {
            val ch = title.trim().firstOrNull()?.uppercaseChar() ?: return "#"
            return if (ch in 'A'..'Z') ch.toString() else "#"
        }
        val buckets = ArrayList<Pair<String, Int>>()
        var lastBucket: String? = null
        games.forEachIndexed { idx, g ->
            val b = bucketOf(g.title)
            if (b != lastBucket) {
                buckets += b to idx
                lastBucket = b
            }
        }
        if (buckets.isEmpty()) return null
        val curIdx = _focusIndex.value.coerceIn(0, games.lastIndex)
        val curBucketPos = buckets.indexOfLast { it.second <= curIdx }.coerceAtLeast(0)
        val nextPos = (curBucketPos + direction + buckets.size) % buckets.size
        val (letter, targetIdx) = buckets[nextPos]
        setFocus(targetIdx)
        return letter
    }

    fun openManualScrape(game: Game) {
        _state.value = _state.value.copy(menuGame = null, manualScrapeGame = game)
    }

    fun closeManualScrape() {
        _state.value = _state.value.copy(manualScrapeGame = null)
    }

    fun suggestManualScrapeKeywords(keyword: String, game: Game): List<String> =
        covers.suggestManualKeywords(keyword, _state.value.platformFor(game), limit = 8)

    suspend fun manualSearchScrape(
        keyword: String,
        game: Game,
        onActiveSourcesChanged: (List<String>) -> Unit = {},
        onPartialResults: (List<com.pocketdeck.launcher.data.ManualScrapeCandidate>) -> Unit = {},
    ): List<com.pocketdeck.launcher.data.ManualScrapeCandidate> =
        metadataRepo.manualSearch(
            keyword = keyword,
            game = game,
            platform = _state.value.platformFor(game),
            covers = covers,
            onActiveSourcesChanged = onActiveSourcesChanged,
            onPartialResults = onPartialResults,
        )

    fun applyManualScrape(game: Game, candidate: com.pocketdeck.launcher.data.ManualScrapeCandidate) {
        metadataRepo.applyManualScrape(game.id, candidate, covers)
        _state.value = _state.value.copy(manualScrapeGame = null)
    }

    fun saveGameEdits(id: String, name: String?, coverPath: String?) {
        covers.forget(id)
        viewModelScope.launch { repo.saveGameEdits(id, name, coverPath) }
        _state.value = _state.value.copy(editingGame = null, menuGame = null)
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repo.toggleFavorite(id) }
    }

    fun hideGame(id: String) {
        viewModelScope.launch { repo.hide(id) }
        _state.value = _state.value.copy(menuGame = null, editingGame = null, propertiesGame = null)
    }

    fun addRecent(id: String) {
        activeSessionGameId = id
        activeSessionStartElapsedMs = android.os.SystemClock.elapsedRealtime()
        viewModelScope.launch { repo.addRecent(id) }
    }

    /** 回到 PocketDeck 时结算本次游玩时长（过滤 < 5 秒误触与 > 8 小时超长挂机）。 */
    fun onAppResumed() {
        val gid = activeSessionGameId ?: return
        val start = activeSessionStartElapsedMs
        activeSessionGameId = null
        activeSessionStartElapsedMs = 0L
        if (start <= 0L) return
        val elapsed = android.os.SystemClock.elapsedRealtime() - start
        if (elapsed in 5_000L..(8L * 3600L * 1000L)) {
            viewModelScope.launch { repo.addPlayDuration(gid, elapsed) }
        }
    }

    /** 为多盘游戏生成 `.m3u` 换盘索引文件并触发重新扫描。 */
    suspend fun generateM3uForGame(game: Game): Result<File> {
        val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.pocketdeck.launcher.data.RomScanner.generateM3uForGame(game)
        }
        if (res.isSuccess) {
            refresh()
        }
        return res
    }

    fun openViewMenu() {
        val idx = ViewMode.entries.indexOf(_state.value.viewMode).coerceAtLeast(0)
        _state.value = _state.value.copy(showViewMenu = true, viewMenuIndex = idx)
    }

    fun closeViewMenu() {
        _state.value = _state.value.copy(showViewMenu = false)
    }

    fun moveViewMenu(delta: Int) {
        val size = ViewMode.entries.size
        val next = (_state.value.viewMenuIndex + delta + size) % size
        _state.value = _state.value.copy(viewMenuIndex = next)
    }

    fun setViewMode(mode: ViewMode) {
        _state.value = _state.value.copy(showViewMenu = false)
        viewModelScope.launch { repo.updatePrefs { it.copy(viewMode = mode) } }
    }

    fun openApps() { _state.value = _state.value.copy(showApps = true) }
    fun closeApps() { _state.value = _state.value.copy(showApps = false) }

    fun openLibraryManager(
        hiddenOnly: Boolean = false,
        initialDirPath: String? = null,
        initialFocusFilePath: String? = null,
    ) {
        _state.value = _state.value.copy(
            showLibraryManager = true,
            libraryManagerHiddenOnly = hiddenOnly,
            libraryManagerInitialDir = initialDirPath,
            libraryManagerInitialFocusFile = initialFocusFilePath,
        )
    }

    fun closeLibraryManager() {
        _state.value = _state.value.copy(
            showLibraryManager = false,
            libraryManagerInitialDir = null,
            libraryManagerInitialFocusFile = null,
        )
    }

    /** 按系统 Home 键时收起全部子页面、弹窗与搜索状态，直接回到 PocketDeck 主桌面。 */
    fun returnToDesktop() {
        val cur = _state.value
        val wasSearching = cur.searching
        val next = cur.copy(
            showApps = false,
            showSettings = false,
            showLibraryManager = false,
            libraryManagerInitialDir = null,
            libraryManagerInitialFocusFile = null,
            showViewMenu = false,
            showQuickHud = false,
            menuGame = null,
            detailGame = null,
            propertiesGame = null,
            editingGame = null,
            manualScrapeGame = null,
            searching = false,
            searchQuery = "",
        )
        if (wasSearching) {
            if (!recomputeFromRepo(next)) {
                _state.value = next
            }
        } else if (next != cur) {
            _state.value = next
        }
    }

    fun setGameCategories(gameId: String, categoryIds: Set<String>) {
        viewModelScope.launch {
            repo.updatePrefs { u ->
                val updated = u.categories.map { c ->
                    val nextGames = if (c.id in categoryIds) {
                        c.games + gameId
                    } else {
                        c.games - gameId
                    }
                    c.copy(games = nextGames)
                }
                u.copy(categories = updated)
            }
        }
    }

    suspend fun configuredRomRoots(): List<File> = repo.configuredRoots()

    suspend fun renameFile(file: File, newName: String): Result<File> =
        repo.renameFile(file, newName)

    suspend fun moveFile(file: File, targetDir: File): Result<File> =
        repo.moveFile(file, targetDir)

    suspend fun moveEntries(entries: List<File>, targetDir: File): Result<Int> =
        repo.moveEntries(entries, targetDir)

    suspend fun copyFile(file: File, targetDir: File): Result<File> =
        repo.copyFile(file, targetDir)

    suspend fun copyEntries(entries: List<File>, targetDir: File): Result<Int> =
        repo.copyEntries(entries, targetDir)

    suspend fun deleteFile(file: File): Result<Unit> {
        val res = repo.deleteFile(file)
        if (res.isSuccess) {
            _state.value = _state.value.copy(menuGame = null, editingGame = null, propertiesGame = null)
        }
        return res
    }

    suspend fun deleteEntries(entries: List<File>): Result<Int> {
        val res = repo.deleteEntries(entries)
        if (res.isSuccess) {
            _state.value = _state.value.copy(menuGame = null, editingGame = null, propertiesGame = null)
        }
        return res
    }

    suspend fun extractArchive(
        file: File,
        destDir: File,
        onProgress: (Int, String, Long) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): Result<Int> = repo.extractArchive(file, destDir, onProgress = onProgress, isCancelled = isCancelled)

    suspend fun compressToZip(
        sources: List<File>,
        destZip: File,
        onProgress: (String) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Result<File> = repo.compressToZip(sources, destZip, onProgress = onProgress, isCancelled = isCancelled)

    fun cancelFileTask(id: String) {
        repo.cancelFileTask(id)
    }

    private var pendingRescrapeAfterSettings = false

    fun toggleSettings() {
        val wasOpen = _state.value.showSettings
        val nextOpen = !wasOpen
        _state.value = _state.value.copy(showSettings = nextOpen)
        if (wasOpen && !nextOpen && pendingRescrapeAfterSettings) {
            pendingRescrapeAfterSettings = false
            val s = _state.value
            preloadAssets(s.games, _focusIndex.value, s::platformFor)
        }
    }

    fun toggleClock12h() {
        viewModelScope.launch { repo.updatePrefs { it.copy(clock12h = !it.clock12h) } }
    }

    fun toggleSwapAbxy() {
        viewModelScope.launch { repo.updatePrefs { it.copy(swapAbxy = !it.swapAbxy) } }
    }

    fun toggleNowTab() {
        viewModelScope.launch { repo.updatePrefs { it.copy(nowTab = !it.nowTab) } }
    }

    fun toggleFavsTab() {
        viewModelScope.launch { repo.updatePrefs { it.copy(favsTab = !it.favsTab) } }
    }

    fun toggleBottomBar() {
        viewModelScope.launch { repo.updatePrefs { it.copy(bottomBar = !it.bottomBar) } }
    }

    fun toggleShowMetadata() {
        viewModelScope.launch { repo.updatePrefs { it.copy(showMetadata = !it.showMetadata) } }
    }

    fun toggleMetaField(field: MetaFieldKey) {
        viewModelScope.launch {
            repo.updatePrefs { u ->
                val cur = u.metaDisplay
                val next = when (field) {
                    MetaFieldKey.ReleaseDate -> cur.copy(releaseDate = !cur.releaseDate)
                    MetaFieldKey.Developer -> cur.copy(developer = !cur.developer)
                    MetaFieldKey.Publisher -> cur.copy(publisher = !cur.publisher)
                    MetaFieldKey.FileSize -> cur.copy(fileSize = !cur.fileSize)
                }
                u.copy(metaDisplay = next)
            }
        }
    }

    fun cycleGalleryAlign() {
        viewModelScope.launch {
            repo.updatePrefs {
                val all = GalleryAlign.entries
                it.copy(galleryAlign = all[(all.indexOf(it.galleryAlign) + 1) % all.size])
            }
        }
    }

    fun cycleBackground() {
        viewModelScope.launch {
            repo.updatePrefs {
                val styles = BackgroundStyle.entries
                val next = styles[(styles.indexOf(it.backgroundStyle) + 1) % styles.size]
                it.copy(backgroundStyle = next)
            }
        }
    }

    fun setBackgroundStyle(style: BackgroundStyle) {
        viewModelScope.launch {
            repo.updatePrefs { it.copy(backgroundStyle = style) }
        }
    }

    fun setCustomBackground(path: String?) {
        viewModelScope.launch {
            repo.updatePrefs {
                it.copy(
                    customBackground = path,
                    backgroundStyle = if (path != null) BackgroundStyle.Image else it.backgroundStyle,
                )
            }
        }
    }

    fun updateKeyBindings(bindings: Map<KeyAction, Int>) {
        viewModelScope.launch { repo.updatePrefs { it.copy(keyBindings = bindings) } }
    }

    fun resetKeyBindings() {
        viewModelScope.launch { repo.updatePrefs { it.copy(keyBindings = emptyMap()) } }
    }

    fun toggleReduceMotion() {
        viewModelScope.launch { repo.updatePrefs { it.copy(reduceMotion = !it.reduceMotion) } }
    }

    fun toggleKeySound() {
        viewModelScope.launch { repo.updatePrefs { it.copy(keySoundEnabled = !it.keySoundEnabled) } }
    }

    suspend fun refreshGameMetadata(game: Game): Boolean =
        metadataRepo.refreshGame(game, _state.value.platformFor(game))

    fun cycleViewMode() {
        viewModelScope.launch {
            repo.updatePrefs {
                val modes = ViewMode.entries
                val next = modes[(modes.indexOf(it.viewMode) + 1) % modes.size]
                it.copy(viewMode = next)
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { repo.updatePrefs { it.copy(theme = theme) } }
    }

    fun cycleTheme() {
        viewModelScope.launch {
            repo.updatePrefs {
                val all = AppTheme.entries
                it.copy(theme = all[(all.indexOf(it.theme) + 1) % all.size])
            }
        }
    }

    fun setThemeAccent(accent: ThemeAccent) {
        viewModelScope.launch { repo.updatePrefs { it.copy(themeAccent = accent) } }
    }

    fun setFontId(fontId: String) {
        viewModelScope.launch { repo.updatePrefs { it.copy(fontId = fontId) } }
    }

    fun setPlatformAspect(platformId: String, aspect: Float?) {
        viewModelScope.launch {
            repo.updatePrefs { u ->
                val map = u.platformAspects.toMutableMap()
                if (aspect == null) map.remove(platformId) else map[platformId] = aspect.coerceIn(0.40f, 2.20f)
                u.copy(platformAspects = map)
            }
        }
    }

    fun saveCustomLauncherPreset(name: String, launcherId: String) {
        viewModelScope.launch {
            repo.updatePrefs { u ->
                val id = "preset:" + System.currentTimeMillis().toString(36)
                val cleanName = name.trim().ifEmpty { launcherId }
                u.copy(customLaunchers = u.customLaunchers + CustomLauncherPreset(id, cleanName, launcherId))
            }
        }
    }

    fun deleteCustomLauncherPreset(presetId: String) {
        viewModelScope.launch {
            repo.updatePrefs { u ->
                u.copy(customLaunchers = u.customLaunchers.filter { it.id != presetId })
            }
        }
    }

    // ───────────────────────── 刮削与元数据服务商 ─────────────────────────

    fun toggleCoverSource(id: String) {
        viewModelScope.launch {
            repo.updatePrefs { u ->
                u.copy(coverSources = u.coverSources.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
            }
        }
    }

    fun moveCoverSource(fromIdx: Int, toIdx: Int) {
        if (fromIdx == toIdx) return
        viewModelScope.launch {
            repo.updatePrefs { u ->
                val list = u.coverSources.toMutableList()
                if (fromIdx !in list.indices || toIdx !in list.indices) return@updatePrefs u
                list.add(toIdx, list.removeAt(fromIdx))
                u.copy(coverSources = list)
            }
        }
    }

    fun toggleMetadataSource(id: String) {
        viewModelScope.launch {
            repo.updatePrefs { u ->
                u.copy(metadataSources = u.metadataSources.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
            }
        }
    }

    fun moveMetadataSource(fromIdx: Int, toIdx: Int) {
        if (fromIdx == toIdx) return
        viewModelScope.launch {
            repo.updatePrefs { u ->
                val list = u.metadataSources.toMutableList()
                if (fromIdx !in list.indices || toIdx !in list.indices) return@updatePrefs u
                list.add(toIdx, list.removeAt(fromIdx))
                u.copy(metadataSources = list)
            }
        }
    }

    fun updateScraperCredentials(cred: ScraperCredentials) {
        viewModelScope.launch { repo.updatePrefs { it.copy(scraperCredentials = cred) } }
    }

    suspend fun clearCoverCache() {
        focusResolveJob?.cancel()
        covers.clearCoverCacheNow()
        metadataRepo.clearCacheNow()
        val s = _state.value
        if (s.showSettings) {
            // 仍在设置页时不立刻回写缓存，等退出设置页回到主页后再触发重刮
            pendingRescrapeAfterSettings = true
        } else {
            preloadAssets(s.games, _focusIndex.value, s::platformFor)
        }
    }

    fun refreshAllMetadata() {
        val lib = repo.library.value ?: return
        val allGames = lib.platforms.flatMap { lib.games[it.id].orEmpty() }
        val s = _state.value
        metadataRepo.refreshAll(allGames, s::platformFor)
        s.games.getOrNull(_focusIndex.value)?.let { metadataRepo.resolveFocusGame(it, s.platformFor(it)) }
    }

    fun refresh() = repo.refresh()

    // 状态栏图标开关
    fun toggleShowClock() { viewModelScope.launch { repo.updatePrefs { it.copy(showClock = !it.showClock) } } }
    fun toggleShowBattery() { viewModelScope.launch { repo.updatePrefs { it.copy(showBattery = !it.showBattery) } } }
    fun toggleShowWifi() { viewModelScope.launch { repo.updatePrefs { it.copy(showWifi = !it.showWifi) } } }
    fun toggleShowBluetooth() { viewModelScope.launch { repo.updatePrefs { it.copy(showBluetooth = !it.showBluetooth) } } }
    fun toggleShowMobileSignal() { viewModelScope.launch { repo.updatePrefs { it.copy(showMobileSignal = !it.showMobileSignal) } } }
    fun toggleShowHeadset() { viewModelScope.launch { repo.updatePrefs { it.copy(showHeadset = !it.showHeadset) } } }
    fun toggleShowAirplane() { viewModelScope.launch { repo.updatePrefs { it.copy(showAirplane = !it.showAirplane) } } }
    fun toggleShowDnd() { viewModelScope.launch { repo.updatePrefs { it.copy(showDnd = !it.showDnd) } } }

    /** 设置某平台的首选启动器。emulatorId 为 null 时清除设置（使用自动检测）。 */
    fun setPlatformLauncher(platformId: String, emulatorId: String?) {
        viewModelScope.launch {
            repo.updatePrefs {
                val map = it.platformLaunchers.toMutableMap()
                if (emulatorId == null) map.remove(platformId) else map[platformId] = emulatorId
                it.copy(platformLaunchers = map)
            }
        }
    }

    /** 设置单个游戏的启动器覆盖（null 表示跟随平台设置）。 */
    fun setGameLauncher(gameId: String, emulatorId: String?) {
        viewModelScope.launch {
            repo.updatePrefs {
                val map = it.gameLaunchers.toMutableMap()
                if (emulatorId == null) map.remove(gameId) else map[gameId] = emulatorId
                it.copy(gameLaunchers = map)
            }
        }
    }

    fun unhideGame(id: String) {
        viewModelScope.launch { repo.unhide(id) }
    }

    fun toggleShowAndroidGames() {
        viewModelScope.launch { repo.updatePrefs { it.copy(showAndroidGames = !it.showAndroidGames) } }
    }

    fun toggleAppInGameLibrary(pkg: String, add: Boolean) {
        viewModelScope.launch {
            repo.updatePrefs { u ->
                if (add) {
                    u.copy(
                        androidGamePackages = u.androidGamePackages + pkg,
                        excludedAndroidPackages = u.excludedAndroidPackages - pkg,
                    )
                } else {
                    u.copy(
                        androidGamePackages = u.androidGamePackages - pkg,
                        excludedAndroidPackages = u.excludedAndroidPackages + pkg,
                    )
                }
            }
        }
    }

    // ───────────────────────── 游戏目录管理 ─────────────────────────

    private fun updateDirs(transform: (UserData) -> UserData) {
        viewModelScope.launch {
            repo.updatePrefs(transform)
            repo.refresh()
        }
    }

    fun addRomDir(path: String) = updateDirs { u ->
        // 首次添加：保留当前自动检测到的目录，避免已有游戏消失
        val base = if (u.romDirs.isEmpty()) {
            val auto = repo.autoRoot(u)
            if (java.io.File(auto).isDirectory) listOf(RomDir(auto)) else emptyList()
        } else u.romDirs
        if (base.any { it.path == path }) u.copy(romDirs = base)
        else u.copy(romDirs = base + RomDir(path))
    }

    fun toggleRomDir(path: String) = updateDirs { u ->
        u.copy(romDirs = u.romDirs.map { if (it.path == path) it.copy(enabled = !it.enabled) else it })
    }

    fun removeRomDir(path: String) = updateDirs { u ->
        u.copy(romDirs = u.romDirs.filter { it.path != path })
    }

    // ───────────────────────── 标签 / 自定义分类 ─────────────────────────

    private fun updateTabs(transform: (UserData) -> UserData) {
        viewModelScope.launch { repo.updatePrefs(transform) }
    }

    /** 把标签 id 在完整顺序中移动 delta 位。 */
    fun moveTab(id: String, delta: Int) {
        val ids = _state.value.manageTabs.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        updateTabs { it.copy(tabOrder = ids) }
    }

    /** 拖动排序：把标签从 fromIdx 移动到 toIdx。 */
    fun moveTabTo(fromIdx: Int, toIdx: Int) {
        if (fromIdx == toIdx) return
        val ids = _state.value.manageTabs.map { it.id }.toMutableList()
        if (fromIdx !in ids.indices || toIdx !in ids.indices) return
        ids.add(toIdx, ids.removeAt(fromIdx))
        updateTabs { it.copy(tabOrder = ids) }
    }

    fun toggleTabHidden(id: String) = updateTabs { u ->
        when (id) {
            "now" -> u.copy(nowTab = !u.nowTab)
            "favs" -> u.copy(favsTab = !u.favsTab)
            else -> u.copy(hiddenTabs = if (id in u.hiddenTabs) u.hiddenTabs - id else u.hiddenTabs + id)
        }
    }

    fun updateSystemTab(id: String, customName: String, followSystemLanguage: Boolean) = updateTabs { u ->
        val map = u.systemTabs.toMutableMap()
        val existing = map[id]
        val trimmed = customName.trim()
        map[id] = SystemTabConfig(
            customName = trimmed,
            followSystemLanguage = if (trimmed.isEmpty()) true else followSystemLanguage,
            sortOrder = existing?.sortOrder,
        )
        u.copy(systemTabs = map)
    }

    private fun insertCatBeforeOther(tabOrder: List<String>, id: String): List<String> =
        if (tabOrder.isNotEmpty() && "other" in tabOrder) {
            tabOrder.flatMap { if (it == "other") listOf(id, "other") else listOf(it) }
        } else {
            tabOrder
        }

    fun createCategory(name: String, sources: List<String> = emptyList()) {
        val id = "cat:" + System.currentTimeMillis().toString(36)
        updateTabs { u ->
            val newCat = Category(id, name.trim().ifEmpty { "?" }, sources = sources.distinct())
            u.copy(categories = u.categories + newCat, tabOrder = insertCatBeforeOther(u.tabOrder, id))
        }
    }

    fun createCategoryForGame(name: String, gameId: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val id = "cat:" + System.currentTimeMillis().toString(36)
        updateTabs { u ->
            val newCat = Category(id, trimmed, games = setOf(gameId))
            u.copy(categories = u.categories + newCat, tabOrder = insertCatBeforeOther(u.tabOrder, id))
        }
    }

    fun quickCreateCategoryForPlatform(platform: Platform) {
        updateTabs { u ->
            if (u.categories.any { platform.id in it.sources }) return@updateTabs u
            val id = "cat:" + System.currentTimeMillis().toString(36)
            val newCat = Category(id, platform.shortName, sources = listOf(platform.id))
            u.copy(categories = u.categories + newCat, tabOrder = insertCatBeforeOther(u.tabOrder, id))
        }
    }

    fun addPlatformToCategory(catId: String, platformId: String) = updateTabs { u ->
        u.copy(
            categories = u.categories.map { c ->
                if (c.id == catId) c.copy(sources = (c.sources + platformId).distinct()) else c
            },
        )
    }

    fun setCategorySources(catId: String, sources: List<String>) = updateTabs { u ->
        u.copy(categories = u.categories.map { if (it.id == catId) it.copy(sources = sources.distinct()) else it })
    }

    fun renameCategory(id: String, name: String) = updateTabs { u ->
        u.copy(categories = u.categories.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it })
    }

    fun setCategorySortOrder(catId: String, sortOrder: CategorySortOrder?) = updateTabs { u ->
        if (catId in setOf("now", "favs", "other")) {
            val map = u.systemTabs.toMutableMap()
            val cur = map[catId] ?: SystemTabConfig()
            map[catId] = cur.copy(sortOrder = sortOrder)
            u.copy(systemTabs = map)
        } else if (sortOrder != null) {
            u.copy(categories = u.categories.map { if (it.id == catId) it.copy(sortOrder = sortOrder) else it })
        } else {
            u
        }
    }

    /** 删除分类（包含默认生成的 NES/SNES/GB/ANDROID）；所属平台自动归回 OTHER。 */
    fun deleteCategory(id: String) = updateTabs { u ->
        u.copy(
            categories = u.categories.filter { it.id != id },
            tabOrder = u.tabOrder.filter { it != id },
            hiddenTabs = u.hiddenTabs - id,
        )
    }

    /** 恢复默认标签顺序（不影响分类和隐藏状态）。 */
    fun resetTabOrder() = updateTabs { it.copy(tabOrder = emptyList()) }

    /** 把 srcId 分类合并进 dstId 分类。 */
    fun mergeTab(srcId: String, dstId: String) {
        if (srcId == dstId) return
        val tabs = _state.value.manageTabs
        val src = tabs.firstOrNull { it.id == srcId } ?: return
        val dst = tabs.firstOrNull { it.id == dstId } ?: return
        if (src.type != TabType.Custom || dst.type != TabType.Custom) return
        val orderIds = tabs.map { it.id }
        updateTabs { u ->
            val srcCat = u.categories.firstOrNull { it.id == srcId } ?: return@updateTabs u
            val cats = u.categories
                .filter { it.id != srcId }
                .map { c ->
                    if (c.id == dstId) {
                        c.copy(
                            sources = (c.sources + srcCat.sources).distinct(),
                            games = c.games + srcCat.games,
                        )
                    } else c
                }
            u.copy(
                categories = cats,
                tabOrder = orderIds.filter { it != srcId },
                hiddenTabs = u.hiddenTabs - srcId,
            )
        }
    }

    fun toggleGameInCategory(catId: String, gameId: String) = updateTabs { u ->
        u.copy(categories = u.categories.map { c ->
            if (c.id != catId) c
            else c.copy(games = if (gameId in c.games) c.games - gameId else c.games + gameId)
        })
    }

    companion object {
        private val RELEASE_YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")
        private val RELEASE_NUM_REGEX = Regex("""\d{1,2}""")
        private val RELEASE_MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    }
}
