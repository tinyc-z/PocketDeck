package com.pocketdeck.launcher.ui.home

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.AppLocale
import com.pocketdeck.launcher.data.FileTask
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.lan.LanServerManager
import com.pocketdeck.launcher.lan.LanServerState
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.apps.AppsScreen
import com.pocketdeck.launcher.ui.components.GameMenu
import com.pocketdeck.launcher.ui.components.GamePropertiesDialog
import com.pocketdeck.launcher.ui.components.ViewModeMenu
import com.pocketdeck.launcher.ui.edit.EditGameScreen
import com.pocketdeck.launcher.ui.lan.LanServiceControlDialog
import com.pocketdeck.launcher.ui.lan.QuickShareScreen
import com.pocketdeck.launcher.ui.library.GameLibraryScreen
import com.pocketdeck.launcher.ui.settings.LauncherRow
import com.pocketdeck.launcher.ui.settings.ManageCallbacks
import com.pocketdeck.launcher.ui.settings.SettingsScreen
import com.pocketdeck.launcher.ui.settings.SettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun HomeOverlays(
    state: HomeUiState,
    fullLibrary: Library?,
    filesRevision: Long,
    fileTasks: List<FileTask>,
    lanManager: LanServerManager,
    lanState: LanServerState,
    vm: HomeViewModel,
    context: Context,
    scope: CoroutineScope,
    showLanControlFromStatusBar: Boolean,
    onShowLanControlFromStatusBarChange: (Boolean) -> Unit,
    showQuickShareScreen: Boolean,
    onShowQuickShareScreenChange: (Boolean) -> Unit,
    onCapturingKeyBindChange: (Boolean) -> Unit,
    launchGame: (Game) -> Unit,
) {
    val scrapeProgress by vm.scrapeProgress.collectAsState()
    // 视图切换弹出菜单
    if (state.showViewMenu) {
        ViewModeMenu(
            currentMode = state.viewMode,
            highlightedIndex = state.viewMenuIndex,
            onSelect = { vm.setViewMode(it) },
            onDismiss = { vm.closeViewMenu() },
        )
    }

    // 游戏长按 / X 键菜单
    state.menuGame?.let { game ->
        GameMenu(
            game = game,
            isFavorite = game.id in state.favorites,
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            onDismiss = { vm.hideMenu() },
            onFavorite = { vm.toggleFavorite(game.id) },
            onEdit = { vm.openEdit(game) },
            onProperties = { vm.openProperties(game) },
            onLaunch = {
                vm.hideMenu()
                launchGame(game)
            },
            onLaunchDisc = if (game.discFiles.size > 1) {
                { discPath ->
                    vm.hideMenu()
                    val discFile = File(discPath)
                    val discGame = game.copy(id = discPath, fileName = discFile.name, baseName = discFile.nameWithoutExtension)
                    val platform = state.platformFor(game)
                    val preferredEmulator = state.effectiveLauncherFor(game)
                    val result = GameLauncher.launch(context, discGame, platform, preferredEmulator)
                    when (result) {
                        is GameLauncher.Result.Ok -> vm.addRecent(game.id)
                        is GameLauncher.Result.Error ->
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            } else null,
            onGenerateM3u = if (game.discFiles.size > 1 && !game.fileName.endsWith(".m3u", ignoreCase = true)) {
                {
                    vm.hideMenu()
                    scope.launch {
                        val res = vm.generateM3uForGame(game)
                        val msg = res.fold(
                            onSuccess = { f -> context.getString(R.string.m3u_generated_ok, f.name) },
                            onFailure = { e -> context.getString(R.string.m3u_generated_fail, e.message.orEmpty()) },
                        )
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            } else null,
            onManualScrape = { vm.openManualScrape(game) },
        )
    }

    // 手动搜索刮削弹窗
    state.manualScrapeGame?.let { game ->
        com.pocketdeck.launcher.ui.components.ManualScrapeDialog(
            game = game,
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            onSearch = { kw -> vm.manualSearchScrape(kw, game) },
            onApply = { cand ->
                vm.applyManualScrape(game, cand)
                Toast.makeText(context, context.getString(R.string.manual_scrape_applied), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { vm.closeManualScrape() },
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
            onManualScrape = { vm.openManualScrape(game) },
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
            onOpenQuickShare = { onShowQuickShareScreenChange(true) },
            hasTopOverlay = state.showLibraryManager || showQuickShareScreen,
            onDismiss = { vm.closeApps() },
        )
    }

    // 设置页面
    if (state.showSettings) {
        val launcherRows = remember(state.platformsById) {
            state.platformsById.values
                .sortedBy { it.shortName }
                .map { p -> LauncherRow(p.id, p.fullName, GameLauncher.availableLaunchers(context, p.id)) }
                .filter { it.options.isNotEmpty() }
        }
        SettingsScreen(
            state = SettingsState(
                romRoot = state.romRoot,
                clock12h = state.is12h,
                swapAbxy = state.swapAbxy,
                nowTab = state.tabs.any { it.type == TabType.Now },
                favsTab = state.tabs.any { it.type == TabType.Favs },
                bottomBar = state.bottomBar,
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
                launcherRows = launcherRows,
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
                libraryPlatforms = state.platformsById.values.sortedWith(
                    compareBy({ PlatformCatalog.sortKey(it.id) }, { it.shortName }),
                ),
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
                lanShareRunning = lanState.shareRunning,
                lanWebRunning = lanState.webRunning,
                scrapeProgress = scrapeProgress,
            ),
            onDismiss = {
                onCapturingKeyBindChange(false)
                vm.toggleSettings()
            },
            onToggleClock12h = { vm.toggleClock12h() },
            onToggleSwapAbxy = { vm.toggleSwapAbxy() },
            onToggleKeySound = { vm.toggleKeySound() },
            onToggleReduceMotion = { vm.toggleReduceMotion() },
            onToggleNowTab = { vm.toggleNowTab() },
            onToggleFavsTab = { vm.toggleFavsTab() },
            onToggleBottomBar = { vm.toggleBottomBar() },
            onToggleShowMetadata = { vm.toggleShowMetadata() },
            onCycleBackground = { vm.cycleBackground() },
            onSetBackgroundStyle = { vm.setBackgroundStyle(it) },
            onSetCustomBackground = vm::setCustomBackground,
            onUpdateKeyBindings = vm::updateKeyBindings,
            onResetKeyBindings = vm::resetKeyBindings,
            onCapturingKeyBindChange = onCapturingKeyBindChange,
            onCycleViewMode = { vm.cycleViewMode() },
            onSetViewMode = { vm.setViewMode(it) },
            onCycleTheme = { vm.cycleTheme() },
            onRescan = { vm.refresh() },
            onToggleShowClock = { vm.toggleShowClock() },
            onToggleShowBattery = { vm.toggleShowBattery() },
            onToggleShowWifi = { vm.toggleShowWifi() },
            onToggleShowBluetooth = { vm.toggleShowBluetooth() },
            onToggleShowMobileSignal = { vm.toggleShowMobileSignal() },
            onToggleShowHeadset = { vm.toggleShowHeadset() },
            onToggleShowAirplane = { vm.toggleShowAirplane() },
            onToggleShowDnd = { vm.toggleShowDnd() },
            onSetPlatformLauncher = { pid, eid -> vm.setPlatformLauncher(pid, eid) },
            onCycleGalleryAlign = { vm.cycleGalleryAlign() },
            manage = ManageCallbacks(
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
                refreshAllMetadata = vm::refreshAllMetadata,
                toggleMetaField = vm::toggleMetaField,
                unhideGame = vm::unhideGame,
                toggleShowAndroidGames = vm::toggleShowAndroidGames,
                setThemeAccent = vm::setThemeAccent,
                setFontId = vm::setFontId,
                setPlatformAspect = vm::setPlatformAspect,
                saveCustomLauncherPreset = vm::saveCustomLauncherPreset,
                deleteCustomLauncherPreset = vm::deleteCustomLauncherPreset,
                sampleGameFor = { pid -> fullLibrary?.games?.get(pid)?.firstOrNull() },
                openLibraryHidden = { vm.openLibraryManager(hiddenOnly = true) },
                openLanService = { onShowLanControlFromStatusBarChange(true) },
            ),
            isCovered = state.showLibraryManager || showLanControlFromStatusBar || showQuickShareScreen,
            onLanguageChange = { code ->
                AppLocale.set(context, code)
                (context as? Activity)?.recreate()
            },
        )
    }

    // 游戏库管理器（可从 Apps 中「游戏库」打开，或从「设置 → 游戏库 → 管理已隐藏」直接跳入）
    if (state.showLibraryManager) {
        val metaResolutionMap by vm.metaResolutionMap.collectAsState()
        val coverResolutionMap by vm.coverResolutionMap.collectAsState()
        GameLibraryScreen(
            fullLibrary = fullLibrary,
            filesRevision = filesRevision,
            fileTasks = fileTasks,
            scrapeProgress = scrapeProgress,
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
            platformFor = state::platformFor,
            effectiveLauncherFor = state::effectiveLauncherFor,
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
            onOpenQuickShare = { onShowQuickShareScreenChange(true) },
            onRescan = vm::refresh,
            onDismiss = vm::closeLibraryManager,
        )
    }

    // 点击状态栏共享标志弹出的局域网管理与接收界面（唯一可确认接收/取消传输/关闭服务的入口，其余页面不弹窗打扰）
    if (showLanControlFromStatusBar) {
        LanServiceControlDialog(
            state = lanState,
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            onRefreshInterfaces = { lanManager.refreshInterfaces() },
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
            onOpenFolder = { dir, focusFile ->
                onShowLanControlFromStatusBarChange(false)
                vm.openLibraryManager(
                    initialDirPath = dir.absolutePath,
                    initialFocusFilePath = focusFile?.absolutePath,
                )
            },
            onOpenQuickShareScreen = {
                onShowLanControlFromStatusBarChange(false)
                onShowQuickShareScreenChange(true)
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

    // 独立游戏快传界面（含收发控制 + 接收历史记录管理）
    if (showQuickShareScreen) {
        QuickShareScreen(
            state = lanState,
            swapAbxy = state.swapAbxy,
            keyBindings = state.keyBindings,
            onRefreshInterfaces = { lanManager.refreshInterfaces() },
            onSelectInterface = lanManager::selectInterface,
            onRenameDevice = lanManager::setDeviceName,
            onRandomizeDeviceName = lanManager::randomizeDeviceName,
            onToggleQuickShare = { on ->
                if (on) lanManager.startQuickShare() else lanManager.stopQuickShare()
            },
            onToggleLocalSendCompat = lanManager::setLocalSendCompat,
            onScanPeers = lanManager::scanPeers,
            onAcceptIncoming = { sid, dir -> lanManager.acceptIncoming(sid, dir) },
            onRejectIncoming = { sid -> lanManager.rejectIncoming(sid) },
            onCancelTransfer = lanManager::cancelTransfer,
            onSubmitPin = lanManager::submitQuickSharePin,
            onCancelPin = lanManager::cancelQuickSharePin,
            onDismissTransferBanner = lanManager::dismissTransferBanner,
            onDeleteHistory = lanManager::deleteShareHistoryItem,
            onClearHistory = { lanManager.clearShareHistory(false) },
            onApplyFontId = vm::setFontId,
            onRescanLibrary = vm::refresh,
            onOpenFolder = { dir, focusFile ->
                onShowQuickShareScreenChange(false)
                vm.openLibraryManager(
                    initialDirPath = dir.absolutePath,
                    initialFocusFilePath = focusFile?.absolutePath,
                )
            },
            onDismiss = { onShowQuickShareScreenChange(false) },
        )
    }
}
