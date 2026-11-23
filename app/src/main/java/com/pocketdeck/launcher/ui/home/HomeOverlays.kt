package com.pocketdeck.launcher.ui.home

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.Layout
import kotlinx.coroutines.delay
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.AppLocale
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.lan.LanServerManager
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.apps.AppsScreen
import com.pocketdeck.launcher.ui.components.GamePropertiesDialog
import com.pocketdeck.launcher.ui.components.ViewModeMenu
import com.pocketdeck.launcher.ui.edit.EditGameScreen
import com.pocketdeck.launcher.ui.lan.LanServiceControlDialog
import com.pocketdeck.launcher.ui.library.GameLibraryScreen
import com.pocketdeck.launcher.ui.settings.ManageCallbacks
import com.pocketdeck.launcher.ui.settings.SettingsScreen
import com.pocketdeck.launcher.ui.settings.SettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun HomeOverlays(
    state: HomeUiState,
    lanManager: LanServerManager,
    vm: HomeViewModel,
    context: Context,
    scope: CoroutineScope,
    showLanControlFromStatusBar: Boolean,
    onShowLanControlFromStatusBarChange: (Boolean) -> Unit,
    onCapturingKeyBindChange: (Boolean) -> Unit,
    launchGame: (Game) -> Unit,
) {
    // 视图切换弹出菜单
    if (state.showViewMenu) {
        ViewModeMenu(
            currentMode = state.viewMode,
            highlightedIndex = state.viewMenuIndex,
            onSelect = { vm.setViewMode(it) },
            onDismiss = { vm.closeViewMenu() },
        )
    }

    // F2 游戏详情与操作中心（直接替代桌面右键菜单）
    state.detailGame?.let { game ->
        val platform = state.platformFor(game)
        val gameCats = state.categories.filter { game.id in it.games || game.platformId in it.sources }.map { it.name }
        val launcherLabel = remember(platform.id, state.effectiveLauncherFor(game)) {
            GameLauncher.describeLauncher(context, platform.id, state.effectiveLauncherFor(game))
        }
        com.pocketdeck.launcher.ui.components.GameDetailDialog(
            game = game,
            platform = platform,
            covers = vm.covers,
            metadata = state.metadataMap[game.id],
            officialName = state.officialNames[game.id],
            playStats = state.playStats[game.id],
            isFavorite = game.id in state.favorites,
            categoryNames = gameCats,
            launcherLabel = launcherLabel,
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            onLaunch = {
                vm.closeDetail()
                launchGame(game)
            },
            onLaunchDisc = if (game.discFiles.size > 1) {
                { discPath ->
                    vm.closeDetail()
                    val discFile = File(discPath)
                    val discGame = game.copy(id = discPath, fileName = discFile.name, baseName = discFile.nameWithoutExtension)
                    val preferredEmulator = state.effectiveLauncherFor(game)
                    val result = GameLauncher.launch(context, discGame, platform, preferredEmulator)
                    when (result) {
                        is GameLauncher.Result.Ok -> vm.addRecent(game.id)
                        is GameLauncher.Result.Error ->
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            } else null,
            onToggleFavorite = { vm.toggleFavorite(game.id) },
            onEdit = { vm.openEdit(game) },
            onManualScrape = if (!game.id.startsWith(GameLauncher.APK_PREFIX)) {
                { vm.openManualScrape(game) }
            } else null,
            onProperties = { vm.openProperties(game) },
            onDismiss = { vm.closeDetail() },
            isTopmost = !state.showQuickHud &&
                state.propertiesGame == null &&
                state.editingGame == null &&
                state.manualScrapeGame == null &&
                !state.showApps &&
                !state.showSettings &&
                !state.showLibraryManager &&
                !showLanControlFromStatusBar &&
                !state.showScrapeTaskDialog,
        )
    }

    // F10 掌机快捷 HUD 浮层（实验性功能）
    if (state.showQuickHud) {
        val hudLanState by lanManager.state.collectAsState()
        com.pocketdeck.launcher.ui.components.QuickHudOverlay(
            lanState = hudLanState,
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            onToggleQuickShare = { on ->
                if (on) lanManager.startQuickShare() else lanManager.stopQuickShare()
            },
            onToggleWebServer = { on ->
                if (on) lanManager.startWebServer() else lanManager.stopWebServer()
            },
            onDismiss = { vm.closeQuickHud() },
        )
    }

    // 游戏属性详情弹窗
    state.propertiesGame?.let { game ->
        val metaResolutionMap by vm.metaResolutionMap.collectAsState()
        val coverResolutionMap by vm.coverResolutionMap.collectAsState()
        val gameCats = state.categories.filter { game.id in it.games || game.platformId in it.sources }.map { it.name }
        GamePropertiesDialog(
            game = game,
            platform = state.platformFor(game),
            metadata = state.metadataMap[game.id],
            officialName = state.officialNames[game.id],
            metaInfo = metaResolutionMap[game.id],
            coverInfo = coverResolutionMap[game.id] ?: vm.covers.resolveCoverInfo(game),
            isFavorite = game.id in state.favorites,
            categoryNames = gameCats,
            effectiveLauncherId = state.effectiveLauncherFor(game),
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            computeCrc32 = vm::computeCrc32,
            onRefreshMetadata = { vm.refreshGameMetadata(game) },
            onLocateFile = {
                val f = File(game.id)
                val dir = f.parentFile
                if (dir != null && dir.isDirectory) {
                    vm.closeProperties()
                    vm.openLibraryManager(
                        initialDirPath = dir.absolutePath,
                        initialFocusFilePath = f.absolutePath,
                    )
                }
            },
            onDismiss = { vm.closeProperties() },
        )
    }

    // Edit Game 页面
    state.editingGame?.let { game ->
        EditGameScreen(
            game = game,
            platform = state.platformFor(game),
            covers = vm.covers,
            officialName = state.officialNames[game.id],
            customLauncherId = state.gameLaunchers[game.id],
            onSetGameLauncher = { id -> vm.setGameLauncher(game.id, id) },
            categories = state.categories,
            onSetGameCategories = { ids -> vm.setGameCategories(game.id, ids) },
            onCreateCategory = { name -> vm.createCategoryForGame(name, game.id) },
            onDismiss = { vm.closeEdit() },
            onSave = { name, coverPath -> vm.saveGameEdits(game.id, name, coverPath) },
            onHide = { vm.hideGame(game.id) },
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            onDeleteFile = {
                scope.launch {
                    val res = vm.deleteFile(File(game.id))
                    Toast.makeText(
                        context,
                        if (res.isSuccess) context.getString(R.string.delete_file_done)
                        else context.getString(R.string.delete_file_failed, res.exceptionOrNull()?.message.orEmpty()),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onLocateFile = {
                val f = File(game.id)
                val dir = f.parentFile
                if (dir != null && dir.isDirectory) {
                    vm.closeEdit()
                    vm.openLibraryManager(
                        initialDirPath = dir.absolutePath,
                        initialFocusFilePath = f.absolutePath,
                    )
                }
            },
            onManualScrape = if (!game.id.startsWith(GameLauncher.APK_PREFIX)) {
                { vm.openManualScrape(game) }
            } else null,
            isTopmost = state.manualScrapeGame == null &&
                !state.showApps &&
                !state.showSettings &&
                !state.showLibraryManager &&
                !showLanControlFromStatusBar &&
                !state.showScrapeTaskDialog,
        )
    }

    // 手动搜索刮削弹窗（放在 EditGameScreen 之后，避免从编辑页打开时被遮挡）
    state.manualScrapeGame?.let { game ->
        com.pocketdeck.launcher.ui.components.ManualScrapeDialog(
            game = game,
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            onSearch = { kw, onActiveSources, onPartial ->
                vm.manualSearchScrape(kw, game, onActiveSources, onPartial)
            },
            onSuggest = { kw -> vm.suggestManualScrapeKeywords(kw, game) },
            onApply = { cand ->
                vm.applyManualScrape(game, cand)
                Toast.makeText(context, context.getString(R.string.manual_scrape_applied), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { vm.closeManualScrape() },
        )
    }

    // Apps 抽屉
    if (state.showApps) {
        AppsScreen(
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            androidGamePackages = state.androidGamePackages,
            excludedAndroidPackages = state.excludedAndroidPackages,
            autoGamePackages = state.autoAndroidPackages,
            onToggleInGameLibrary = { pkg, add -> vm.toggleAppInGameLibrary(pkg, add) },
            onOpenGameLibrary = { vm.openLibraryManager(hiddenOnly = false) },
            onOpenQuickShare = { onShowLanControlFromStatusBarChange(true) },
            hasTopOverlay = state.showSettings ||
                state.showLibraryManager ||
                showLanControlFromStatusBar ||
                state.showScrapeTaskDialog,
            onDismiss = { vm.closeApps() },
        )
    }

    // 设置页面：首帧稳定后空闲预热 + 关闭后零尺寸保活，消除首次/再次进入设置页的冷组合卡顿
    var settingsWarmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1200L)
        settingsWarmed = true
    }
    if (state.showSettings && !settingsWarmed) {
        settingsWarmed = true
    }
    if (settingsWarmed) {
        val libraryPlatforms = remember(state.platformsById) {
            state.platformsById.values.sortedWith(
                compareBy({ PlatformCatalog.sortKey(it.id) }, { it.shortName }),
            )
        }
        val frozenStateHolder = remember { mutableStateOf<SettingsState?>(null) }
        val activeSettingsState = if (state.showSettings || frozenStateHolder.value == null) {
            val settingsLanState = if (state.showSettings) lanManager.state.collectAsState().value else lanManager.state.value
            val settingsScrapeProgress = if (state.showSettings) vm.scrapeProgress.collectAsState().value else vm.scrapeProgress.value
            SettingsState(
                romRoot = state.romRoot,
                clock12h = state.is12h,
                swapAbxy = state.swapAbxy,
                nowTab = state.tabs.any { it.type == TabType.Now },
                favsTab = state.tabs.any { it.type == TabType.Favs },
                bottomBar = state.bottomBar,
                navBarStyle = state.navBarStyle,
                showMetadata = state.showMetadata,
                backgroundStyle = state.backgroundStyle,
                customBackground = state.customBackground,
                viewMode = state.viewMode,
                showClock = state.showClock,
                showBattery = state.showBattery,
                showWifi = state.showWifi,
                showBluetooth = state.showBluetooth,
                showMobileSignal = state.showMobileSignal,
                showHeadset = state.showHeadset,
                showAirplane = state.showAirplane,
                showDnd = state.showDnd,
                platformLaunchers = state.platformLaunchers,
                galleryAlign = state.galleryAlign,
                language = AppLocale.get(context),
                romDirs = state.romDirs,
                autoRoot = state.autoRoot,
                manageTabs = state.manageTabs,
                theme = state.theme,
                coverSources = state.coverSources,
                metadataSources = state.metadataSources,
                scraperCredentials = state.scraperCredentials,
                metaDisplay = state.metaDisplay,
                categories = state.categories,
                libraryPlatforms = libraryPlatforms,
                hiddenIds = state.hiddenIds,
                showAndroidGames = state.showAndroidGames,
                keyBindings = state.keyBindings,
                keySoundEnabled = state.keySoundEnabled,
                reduceMotion = state.reduceMotion,
                themeAccent = state.themeAccent,
                fontId = state.fontId,
                platformAspects = state.platformAspects,
                customLaunchers = state.customLaunchers,
                systemTabs = state.systemTabs,
                uncategorizedPlatforms = state.uncategorizedPlatforms,
                uncategorizedCount = state.uncategorizedCount,
                lanShareRunning = settingsLanState.shareRunning,
                lanWebRunning = settingsLanState.webRunning,
                scrapeProgress = settingsScrapeProgress,
                quickHudEnabled = state.quickHudEnabled,
            ).also { frozenStateHolder.value = it }
        } else {
            frozenStateHolder.value!!
        }
        val showSettingsNow = state.showSettings
        // 缓存全部回调对象，防止冷启保活隐藏态 SettingsScreen 随主页 state 每次变化触发整树重组
        val stableOnDismiss = remember(vm, onCapturingKeyBindChange) {
            {
                onCapturingKeyBindChange(false)
                vm.toggleSettings()
            }
        }
        val stableToggleClock12h = remember(vm) { { vm.toggleClock12h() } }
        val stableToggleSwapAbxy = remember(vm) { { vm.toggleSwapAbxy() } }
        val stableToggleKeySound = remember(vm) { { vm.toggleKeySound() } }
        val stableToggleReduceMotion = remember(vm) { { vm.toggleReduceMotion() } }
        val stableToggleNowTab = remember(vm) { { vm.toggleNowTab() } }
        val stableToggleFavsTab = remember(vm) { { vm.toggleFavsTab() } }
        val stableToggleBottomBar = remember(vm) { { vm.toggleBottomBar() } }
        val stableCycleNavBarStyle = remember(vm) { { vm.cycleNavBarStyle() } }
        val stableToggleShowMetadata = remember(vm) { { vm.toggleShowMetadata() } }
        val stableCycleBackground = remember(vm) { { vm.cycleBackground() } }
        val stableSetBackgroundStyle = remember(vm) { { style: com.pocketdeck.launcher.data.BackgroundStyle -> vm.setBackgroundStyle(style) } }
        val stableCycleViewMode = remember(vm) { { vm.cycleViewMode() } }
        val stableSetViewMode = remember(vm) { { mode: com.pocketdeck.launcher.data.ViewMode -> vm.setViewMode(mode) } }
        val stableCycleTheme = remember(vm) { { vm.cycleTheme() } }
        val stableRescan = remember(vm) { { vm.refresh() } }
        val stableToggleShowClock = remember(vm) { { vm.toggleShowClock() } }
        val stableToggleShowBattery = remember(vm) { { vm.toggleShowBattery() } }
        val stableToggleShowWifi = remember(vm) { { vm.toggleShowWifi() } }
        val stableToggleShowBluetooth = remember(vm) { { vm.toggleShowBluetooth() } }
        val stableToggleShowMobileSignal = remember(vm) { { vm.toggleShowMobileSignal() } }
        val stableToggleShowHeadset = remember(vm) { { vm.toggleShowHeadset() } }
        val stableToggleShowAirplane = remember(vm) { { vm.toggleShowAirplane() } }
        val stableToggleShowDnd = remember(vm) { { vm.toggleShowDnd() } }
        val stableSetPlatformLauncher = remember(vm) { { pid: String, eid: String? -> vm.setPlatformLauncher(pid, eid) } }
        val stableCycleGalleryAlign = remember(vm) { { vm.cycleGalleryAlign() } }
        val stableManageCallbacks = remember(vm, onShowLanControlFromStatusBarChange) {
            ManageCallbacks(
                addDir = vm::addRomDir,
                toggleDir = vm::toggleRomDir,
                removeDir = vm::removeRomDir,
                moveTab = vm::moveTab,
                moveTabTo = vm::moveTabTo,
                toggleTabHidden = vm::toggleTabHidden,
                updateSystemTab = vm::updateSystemTab,
                createCategory = vm::createCategory,
                quickCreateCategoryForPlatform = vm::quickCreateCategoryForPlatform,
                addPlatformToCategory = vm::addPlatformToCategory,
                setCategorySources = vm::setCategorySources,
                renameCategory = vm::renameCategory,
                setCategorySortOrder = vm::setCategorySortOrder,
                deleteCategory = vm::deleteCategory,
                mergeTab = vm::mergeTab,
                resetTabOrder = vm::resetTabOrder,
                toggleCoverSource = vm::toggleCoverSource,
                moveCoverSource = vm::moveCoverSource,
                toggleMetadataSource = vm::toggleMetadataSource,
                moveMetadataSource = vm::moveMetadataSource,
                saveCredentials = vm::updateScraperCredentials,
                clearCoverCache = vm::clearCoverCache,
                scrapeMissing = vm::scrapeMissingAll,
                cancelScrape = vm::cancelScrapeMissing,
                openScrapeTask = vm::openScrapeTaskDialog,
                dismissScrapeProgress = vm::dismissScrapeProgress,
                toggleMetaField = vm::toggleMetaField,
                unhideGame = vm::unhideGame,
                toggleShowAndroidGames = vm::toggleShowAndroidGames,
                setThemeAccent = vm::setThemeAccent,
                setFontId = vm::setFontId,
                setPlatformAspect = vm::setPlatformAspect,
                saveCustomLauncherPreset = vm::saveCustomLauncherPreset,
                deleteCustomLauncherPreset = vm::deleteCustomLauncherPreset,
                sampleGameFor = { pid -> vm.fullLibrary.value?.games?.get(pid)?.firstOrNull() },
                openLibraryHidden = { vm.openLibraryManager(hiddenOnly = true) },
                openLanService = { onShowLanControlFromStatusBarChange(true) },
                toggleQuickHudEnabled = vm::toggleQuickHudEnabled,
            )
        }
        val stableLanguageChange: (String) -> Unit = remember(context) {
            { code: String ->
                AppLocale.set(context, code)
                (context as? Activity)?.recreate()
            }
        }
        Layout(
            content = {
                SettingsScreen(
                    state = activeSettingsState,
                    onDismiss = stableOnDismiss,
                    onToggleClock12h = stableToggleClock12h,
                    onToggleSwapAbxy = stableToggleSwapAbxy,
                    onToggleKeySound = stableToggleKeySound,
                    onToggleReduceMotion = stableToggleReduceMotion,
                    onToggleNowTab = stableToggleNowTab,
                    onToggleFavsTab = stableToggleFavsTab,
                    onToggleBottomBar = stableToggleBottomBar,
                    onCycleNavBarStyle = stableCycleNavBarStyle,
                    onToggleShowMetadata = stableToggleShowMetadata,
                    onCycleBackground = stableCycleBackground,
                    onSetBackgroundStyle = stableSetBackgroundStyle,
                    onSetCustomBackground = vm::setCustomBackground,
                    onUpdateKeyBindings = vm::updateKeyBindings,
                    onResetKeyBindings = vm::resetKeyBindings,
                    onCapturingKeyBindChange = onCapturingKeyBindChange,
                    onCycleViewMode = stableCycleViewMode,
                    onSetViewMode = stableSetViewMode,
                    onCycleTheme = stableCycleTheme,
                    onRescan = stableRescan,
                    onToggleShowClock = stableToggleShowClock,
                    onToggleShowBattery = stableToggleShowBattery,
                    onToggleShowWifi = stableToggleShowWifi,
                    onToggleShowBluetooth = stableToggleShowBluetooth,
                    onToggleShowMobileSignal = stableToggleShowMobileSignal,
                    onToggleShowHeadset = stableToggleShowHeadset,
                    onToggleShowAirplane = stableToggleShowAirplane,
                    onToggleShowDnd = stableToggleShowDnd,
                    onSetPlatformLauncher = stableSetPlatformLauncher,
                    onCycleGalleryAlign = stableCycleGalleryAlign,
                    manage = stableManageCallbacks,
                    isVisible = showSettingsNow,
                    isCovered = state.showLibraryManager || showLanControlFromStatusBar || state.showScrapeTaskDialog,
                    onLanguageChange = stableLanguageChange,
                )
            },
        ) { measurables, constraints ->
            if (!showSettingsNow) {
                layout(0, 0) {}
            } else {
                val p = measurables.firstOrNull()?.measure(constraints)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    p?.placeRelative(0, 0)
                }
            }
        }
    }

    // 游戏库管理器（可从 Apps 中「游戏库」打开，或从「设置 → 游戏库 → 管理已隐藏」直接跳入）
    if (state.showLibraryManager) {
        val fullLibrary by vm.fullLibrary.collectAsState()
        val filesRevision by vm.filesRevision.collectAsState()
        val fileTasks by vm.fileTasks.collectAsState()
        val libScrapeProgress by vm.scrapeProgress.collectAsState()
        val metaResolutionMap by vm.metaResolutionMap.collectAsState()
        val coverResolutionMap by vm.coverResolutionMap.collectAsState()
        val stablePlatformFor = remember(state.platformsById) {
            { g: Game -> state.platformsById[g.platformId] ?: com.pocketdeck.launcher.data.PlatformCatalog.forFolder(g.platformId) }
        }
        val stableEffectiveLauncherFor = remember(state.gameLaunchers, state.platformLaunchers) {
            { g: Game -> state.gameLaunchers[g.id] ?: state.platformLaunchers[g.platformId] }
        }
        GameLibraryScreen(
            fullLibrary = fullLibrary,
            filesRevision = filesRevision,
            fileTasks = fileTasks,
            scrapeProgress = libScrapeProgress,
            onCancelScrape = vm::cancelScrapeMissing,
            onDismissScrapeProgress = vm::dismissScrapeProgress,
            hiddenIds = state.hiddenIds,
            favorites = state.favorites,
            categories = state.categories,
            metadataMap = state.metadataMap,
            officialNames = state.officialNames,
            metaResolutionMap = metaResolutionMap,
            coverResolutionMap = coverResolutionMap,
            platformLaunchers = state.platformLaunchers,
            gameLaunchers = state.gameLaunchers,
            covers = vm.covers,
            lanManager = lanManager,
            initialHiddenOnly = state.libraryManagerHiddenOnly,
            initialFilesDir = state.libraryManagerInitialDir,
            initialFocusFilePath = state.libraryManagerInitialFocusFile,
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            platformFor = stablePlatformFor,
            effectiveLauncherFor = stableEffectiveLauncherFor,
            computeCrc32 = vm::computeCrc32,
            onRefreshMetadata = vm::refreshGameMetadata,
            onLaunchGame = launchGame,
            onHideGame = vm::hideGame,
            onUnhideGame = vm::unhideGame,
            onSaveGameEdits = vm::saveGameEdits,
            onSetGameLauncher = vm::setGameLauncher,
            onSetGameCategories = vm::setGameCategories,
            onCreateCategoryForGame = vm::createCategoryForGame,
            onLoadRoots = vm::configuredRomRoots,
            onRenameFile = vm::renameFile,
            onMoveFile = vm::moveFile,
            onMoveEntries = vm::moveEntries,
            onCopyFile = vm::copyFile,
            onCopyEntries = vm::copyEntries,
            onDeleteFile = vm::deleteFile,
            onDeleteEntries = vm::deleteEntries,
            onExtractArchive = vm::extractArchive,
            onCompressZip = vm::compressToZip,
            onCancelFileTask = vm::cancelFileTask,
            onApplyFontId = vm::setFontId,
            onOpenQuickShare = { onShowLanControlFromStatusBarChange(true) },
            onRescan = vm::refresh,
            onDismiss = vm::closeLibraryManager,
        )
    }

    // 局域网与快传管理弹窗（状态栏 / Apps 抽屉 / 游戏中心统一入口）
    if (showLanControlFromStatusBar) {
        val dialogLanState by lanManager.state.collectAsState()
        LanServiceControlDialog(
            state = dialogLanState,
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            onRefreshInterfaces = lanManager::refreshInterfacesAsync,
            onSelectInterface = lanManager::selectInterface,
            onRenameDevice = lanManager::setDeviceName,
            onRandomizeDeviceName = lanManager::randomizeDeviceName,
            onToggleQuickShare = { on ->
                if (on) lanManager.startQuickShare() else lanManager.stopQuickShare()
            },
            onToggleLocalSendCompat = lanManager::setLocalSendCompat,
            onScanPeers = lanManager::scanPeers,
            onToggleWebServer = { on ->
                if (on) lanManager.startWebServer() else lanManager.stopWebServer()
            },
            onRefreshWebPassword = lanManager::refreshWebPassword,
            onKickWebClient = lanManager::kickWebClient,
            onClearWebHistory = lanManager::clearWebTransferHistory,
            onDeleteWebHistoryItem = lanManager::deleteWebTransferHistoryItem,
            onClearShareHistory = { lanManager.clearShareHistory(false) },
            onDeleteShareHistoryItem = { id -> lanManager.deleteShareHistoryItem(id, false) },
            onOpenFolder = { dir, focusFile ->
                onShowLanControlFromStatusBarChange(false)
                vm.openLibraryManager(
                    initialDirPath = dir.absolutePath,
                    initialFocusFilePath = focusFile?.absolutePath,
                )
            },
            onAcceptIncoming = { sid, dir -> lanManager.acceptIncoming(sid, dir) },
            onRejectIncoming = { sid -> lanManager.rejectIncoming(sid) },
            onCancelTransfer = lanManager::cancelTransfer,
            onCancelWebTransfer = lanManager::cancelWebTransfer,
            onCancelFileTask = lanManager::cancelFileTask,
            onSubmitPin = lanManager::submitQuickSharePin,
            onCancelPin = lanManager::cancelQuickSharePin,
            onDismissTransferBanner = lanManager::dismissTransferBanner,
            onStopAll = lanManager::stopAll,
            onDismiss = { onShowLanControlFromStatusBarChange(false) },
        )
    }

    // 后台刮削任务管理弹窗（置于最顶层，从桌面状态栏或设置页头部点击旋转进度徽章均可直达）
    if (state.showScrapeTaskDialog) {
        val dialogScrapeProgress by vm.scrapeProgress.collectAsState()
        com.pocketdeck.launcher.ui.components.ScrapeTaskDialog(
            progress = dialogScrapeProgress,
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            onCancel = {
                vm.cancelScrapeMissing()
                Toast.makeText(context, context.getString(R.string.scraping_missing_stopped_toast), Toast.LENGTH_SHORT).show()
            },
            onRestart = {
                scope.launch {
                    val queued = vm.scrapeMissingAll()
                    val msg = if (queued > 0) {
                        context.getString(R.string.scraping_missing_started_toast, queued)
                    } else {
                        context.getString(R.string.scraping_missing_none_toast)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { vm.closeScrapeTaskDialog() },
        )
    }
}
