package com.pocketdeck.launcher.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.pocketdeck.launcher.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketdeck.launcher.PocketDeckApp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.AppTheme
import com.pocketdeck.launcher.data.BackgroundStyle
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.ViewMode
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.lan.TransferPhase
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.components.BottomBar
import com.pocketdeck.launcher.ui.components.LocalIsOnline
import com.pocketdeck.launcher.ui.components.SearchBar
import com.pocketdeck.launcher.ui.components.TopBar
import com.pocketdeck.launcher.ui.components.WaveBackground
import com.pocketdeck.launcher.ui.components.requestFocusSafely
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.InterFamily
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.LocalDeckFontFamily
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import com.pocketdeck.launcher.ui.theme.SystemFonts
import com.pocketdeck.launcher.ui.theme.deckPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * 主屏幕：波浪/气泡背景 + 顶栏 + Gallery/List/Grid/Bubbles + 底栏 + 搜索/菜单/Apps/Edit/Settings。
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    homeResetTick: Int = 0,
    vm: HomeViewModel = homeViewModel(),
) {
    val state by vm.state.collectAsState()
    ReportDrawnWhen { state.libraryLoaded }
    val fullLibrary by vm.fullLibrary.collectAsState()
    val filesRevision by vm.filesRevision.collectAsState()
    val fileTasks by vm.fileTasks.collectAsState()
    val context = LocalContext.current
    val lanManager = remember { (context.applicationContext as PocketDeckApp).container.lanManager }
    val lanState by lanManager.state.collectAsState()
    var showLanControlFromStatusBar by remember { mutableStateOf(false) }
    LaunchedEffect(state.keySoundEnabled) {
        KeySound.configure(state.keySoundEnabled)
    }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeVisible by remember(density, imeInsets) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var prevImeVisible by remember { mutableStateOf(false) }
    var capturingKeyBind by remember { mutableStateOf(false) }
    var letterBadge by remember { mutableStateOf<String?>(null) }
    var letterBadgeTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(letterBadgeTick) {
        if (letterBadge != null) {
            delay(650L)
            letterBadge = null
        }
    }

    var hasStorageAccess by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager(),
        )
    }

    LaunchedEffect(
        state.detailGame,
        state.propertiesGame,
        state.editingGame,
        state.showLibraryManager,
        state.showApps,
        state.showSettings,
        showLanControlFromStatusBar,
    ) {
        if (state.detailGame == null && state.propertiesGame == null && state.editingGame == null &&
            !state.showLibraryManager && !state.showApps && !state.showSettings &&
            !showLanControlFromStatusBar
        ) {
            focusRequester.requestFocusSafely()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onAppResumed()
                val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                    Environment.isExternalStorageManager()
                if (granted && !hasStorageAccess) {
                    vm.refresh()
                }
                hasStorageAccess = granted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun dismissSearchKeyboard() {
        keyboardController?.hide()
        searchFieldFocused = false
        focusManager.clearFocus(force = true)
        focusRequester.requestFocusSafely()
    }

    // 软键盘被系统返回或收起按钮关闭后，自动把焦点交还主页以便方向键选游戏
    LaunchedEffect(imeVisible, state.searching) {
        if (!state.searching) {
            prevImeVisible = false
            searchFieldFocused = false
        } else {
            if (prevImeVisible && !imeVisible && searchFieldFocused) {
                searchFieldFocused = false
                focusManager.clearFocus(force = true)
                focusRequester.requestFocusSafely()
            }
            prevImeVisible = imeVisible
        }
    }

    val hasChildFocusOverlay = state.detailGame != null ||
        state.propertiesGame != null ||
        state.editingGame != null ||
        state.manualScrapeGame != null ||
        state.showScrapeTaskDialog ||
        state.showQuickHud ||
        state.showLibraryManager ||
        state.showApps ||
        state.showSettings ||
        showLanControlFromStatusBar

    val overlayOpen = hasChildFocusOverlay ||
        state.showViewMenu ||
        state.searching

    BackHandler(enabled = overlayOpen) {
        when {
            showLanControlFromStatusBar -> showLanControlFromStatusBar = false
            state.showScrapeTaskDialog -> vm.closeScrapeTaskDialog()
            state.showQuickHud -> vm.closeQuickHud()
            state.showLibraryManager -> vm.closeLibraryManager()
            state.manualScrapeGame != null -> vm.closeManualScrape()
            state.detailGame != null -> vm.closeDetail()
            state.propertiesGame != null -> vm.closeProperties()
            state.editingGame != null -> vm.closeEdit()
            state.showApps -> vm.closeApps()
            state.showSettings -> vm.toggleSettings()
            state.showViewMenu -> vm.closeViewMenu()
            state.searching -> {
                if ((imeVisible || searchFieldFocused) && state.searchQuery.isNotBlank()) {
                    dismissSearchKeyboard()
                } else {
                    dismissSearchKeyboard()
                    vm.toggleSearch()
                }
            }
        }
    }

    // 任意弹窗关闭后自动夺回主页按键焦点
    LaunchedEffect(overlayOpen) {
        if (!overlayOpen) {
            focusRequester.requestFocusSafely()
        }
    }

    // 在 PocketDeck 内任意子页面/弹窗按系统 Home 键时，一键收起回到主桌面
    LaunchedEffect(homeResetTick) {
        if (homeResetTick <= 0) return@LaunchedEffect
        showLanControlFromStatusBar = false
        dismissSearchKeyboard()
        vm.returnToDesktop()
        focusRequester.requestFocusSafely()
    }

    // 快滚/导航动画状态用 StateFlow + 协程 Job 管理，绝不在 HomeScreen 顶层读取，避免每次方向键触发顶层全树重组
    val fastScrollingFlow = remember { MutableStateFlow(false) }
    val navAnimatingFlow = remember { MutableStateFlow(false) }
    val rapidTabSwitchFlow = remember { MutableStateFlow(false) }
    val scrollCtrl = remember { HomeScrollController() }

    LaunchedEffect(state.selectedTab, state.viewMode, overlayOpen) {
        scrollCtrl.fastScrollJob?.cancel()
        fastScrollingFlow.value = false
    }

    val currentState by rememberUpdatedState(state)
    val currentImeVisible by rememberUpdatedState(imeVisible)

    val launchGame: (Game) -> Unit = remember(context, vm, keyboardController) {
        { game: Game ->
            if (currentImeVisible) keyboardController?.hide()
            val s = currentState
            val platform = s.platformFor(game)
            val preferredEmulator = s.effectiveLauncherFor(game)
            val result = GameLauncher.launch(context, game, platform, preferredEmulator)
            when (result) {
                is GameLauncher.Result.Ok -> vm.addRecent(game.id)
                is GameLauncher.Result.Error ->
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchCurrentGame() {
        val s = currentState
        s.games.getOrNull(vm.focusIndex.value)?.let(launchGame)
    }

    fun showCurrentGameMenu() {
        val s = currentState
        s.games.getOrNull(vm.focusIndex.value)?.let { vm.showMenu(it) }
    }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (state.theme) {
        AppTheme.System -> systemDark
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }
    val palette = remember(state.themeAccent, isDark) {
        deckPalette(state.themeAccent, isDark)
    }
    val cachedFont = SystemFonts.cachedOrNull(state.fontId)
    val fontFamily by produceState(
        initialValue = cachedFont ?: InterFamily,
        key1 = state.fontId,
    ) {
        value = SystemFonts.cachedOrNull(state.fontId)
            ?: withContext(Dispatchers.IO) { SystemFonts.resolve(state.fontId) }
    }

    val isOnline by NetworkStatus.online.collectAsState()
    CompositionLocalProvider(
        LocalDeckColors provides palette,
        LocalDeckFontFamily provides fontFamily,
        LocalIsOnline provides isOnline,
        LocalReduceMotion provides state.reduceMotion,
    ) {
        Box(
            modifier
                .background(DeckColors.Background)
                .focusRequester(focusRequester)
                .focusProperties { canFocus = !hasChildFocusOverlay }
                .focusable()
                .onPreviewKeyEvent { event ->
                    handleHomePreviewKeyEvent(
                        event = event,
                        state = state,
                        lanState = lanState,
                        vm = vm,
                        context = context,
                        scope = scope,
                        scrollCtrl = scrollCtrl,
                        fastScrollingFlow = fastScrollingFlow,
                        navAnimatingFlow = navAnimatingFlow,
                        rapidTabSwitchFlow = rapidTabSwitchFlow,
                        hasChildFocusOverlay = hasChildFocusOverlay,
                        showLanControlFromStatusBar = showLanControlFromStatusBar,
                        capturingKeyBind = capturingKeyBind,
                        imeVisible = imeVisible,
                        searchFieldFocused = searchFieldFocused,
                        hasStorageAccess = hasStorageAccess,
                        dismissSearchKeyboard = ::dismissSearchKeyboard,
                        openAllFilesAccess = { openAllFilesAccessSettings(context) },
                        launchCurrentGame = ::launchCurrentGame,
                        showCurrentGameMenu = ::showCurrentGameMenu,
                        onLetterJump = { dir ->
                            vm.jumpLetter(dir)?.let { ch ->
                                letterBadge = ch
                                letterBadgeTick++
                            }
                        },
                        openLanControlFromStatusBar = { showLanControlFromStatusBar = true },
                    )
                },
        ) {
            // 底层背景效果（独立重组边界：滚动暂停与 CoverGlow 焦点切换绝不触发 HomeScreen 重组）
            HomeBackgroundSlot(
                style = state.backgroundStyle,
                customBackground = state.customBackground,
                staticPaused = state.reduceMotion || hasChildFocusOverlay,
                selectedTab = state.selectedTab,
                games = state.games,
                officialNames = state.officialNames,
                platformFor = state::platformFor,
                vm = vm,
                focusIndexFlow = vm.focusIndex,
                fastScrollingFlow = fastScrollingFlow,
                navAnimatingFlow = navAnimatingFlow,
            )

            // 主内容列（不套 imePadding，避免软键盘弹起/落下时逐帧挤压主页内容和底栏导致游戏卡片抖动）
            Column(
                Modifier.fillMaxSize(),
            ) {
                val nowLabel = stringResource(R.string.tab_name_now)
                val favsLabel = stringResource(R.string.tab_name_favs)
                val otherLabel = stringResource(R.string.tab_name_other)
                val tabLabels = remember(state.tabs, nowLabel, favsLabel, otherLabel) {
                    state.tabs.map { tab ->
                        if (tab.type == TabType.Custom) tab.label
                        else if (!tab.followSystemLanguage && tab.label.isNotBlank()) tab.label
                        else when (tab.type) {
                            TabType.Now -> nowLabel
                            TabType.Favs -> favsLabel
                            TabType.Other -> otherLabel
                            TabType.Custom -> tab.label
                        }
                    }
                }
                val stableOpenLanDialog = remember { { showLanControlFromStatusBar = true } }
                val stableSelectTab = remember(vm) { { idx: Int -> vm.selectTab(idx) } }
                val stablePrevTab = remember(vm) { { vm.prevTab() } }
                val stableNextTab = remember(vm) { { vm.nextTab() } }
                val txNow = lanState.transfer
                TopBar(
                    tabs = tabLabels,
                    selectedIndex = state.selectedTab,
                    is12h = state.is12h,
                    showClock = state.showClock,
                    showBattery = state.showBattery,
                    showWifi = state.showWifi,
                    showBluetooth = state.showBluetooth,
                    showMobileSignal = state.showMobileSignal,
                    showHeadset = state.showHeadset,
                    showAirplane = state.showAirplane,
                    showDnd = state.showDnd,
                    lanSharingActive = lanState.shareRunning,
                    lanWebActive = lanState.webRunning,
                    lanPendingIncoming = lanState.incomingRequest != null,
                    lanTransferWaiting = txNow?.phase == TransferPhase.WaitingAccept || txNow?.phase == TransferPhase.PinRequired,
                    lanTaskProgress = lanState.slowestActiveTaskProgress,
                    scrapeRunning = vm.scrapeProgress.collectAsState().value.running,
                    scrapePercent = vm.scrapeProgress.collectAsState().value.percent,
                    onScrapeBadgeClick = remember(vm) { { vm.openScrapeTaskDialog() } },
                    onLanBadgeClick = stableOpenLanDialog,
                    onWifiClick = stableOpenLanDialog,
                    onTabClick = stableSelectTab,
                    onPrevTab = stablePrevTab,
                    onNextTab = stableNextTab,
                    rapidTabSwitchFlow = rapidTabSwitchFlow,
                )

                // 搜索栏置顶在 TopBar 下方，不遮挡搜索结果和键盘区域
                if (state.reduceMotion) {
                    if (state.searching) {
                        SearchBar(
                            query = state.searchQuery,
                            imeVisible = imeVisible,
                            onQueryChange = { vm.setSearchQuery(it) },
                            onFieldFocusChange = { searchFieldFocused = it },
                            onHideKeyboard = { dismissSearchKeyboard() },
                            onClose = {
                                dismissSearchKeyboard()
                                vm.toggleSearch()
                            },
                            searchAllCategories = state.searchAllCategories,
                            onToggleSearchAllCategories = { vm.toggleSearchAllCategories() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    AnimatedVisibility(
                        visible = state.searching,
                        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(180)) + fadeIn(tween(150)),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) + fadeOut(tween(120)),
                    ) {
                        SearchBar(
                            query = state.searchQuery,
                            imeVisible = imeVisible,
                            onQueryChange = { vm.setSearchQuery(it) },
                            onFieldFocusChange = { searchFieldFocused = it },
                            onHideKeyboard = { dismissSearchKeyboard() },
                            onClose = {
                                dismissSearchKeyboard()
                                vm.toggleSearch()
                            },
                            searchAllCategories = state.searchAllCategories,
                            onToggleSearchAllCategories = { vm.toggleSearchAllCategories() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }

                val topGap = when {
                    state.searching -> 6.dp
                    state.viewMode == ViewMode.Grid || state.viewMode == ViewMode.Bubbles -> 4.dp
                    else -> 6.dp
                }
                Spacer(Modifier.height(topGap))

                if (!hasStorageAccess && state.games.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clip(CircleShape)
                            .background(DeckColors.Ruby.copy(alpha = 0.12f))
                            .clickable { openAllFilesAccessSettings(context) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.storage_permission_required),
                            style = DeckType.metaLabel,
                            color = DeckColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.grant_file_access),
                            style = DeckType.metaLabel,
                            color = DeckColors.Ruby,
                        )
                    }
                }

                // 权限缺失提示或游戏视图
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when {
                        !hasStorageAccess && state.games.isEmpty() -> {
                            Column(Modifier.padding(horizontal = 54.dp, vertical = 32.dp)) {
                                Text(
                                    stringResource(R.string.storage_permission_required),
                                    style = DeckType.emptyMessage,
                                    color = DeckColors.TextSecondary,
                                )
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(com.pocketdeck.launcher.ui.theme.DeckBrand.brandBrush)
                                        .clickable {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                openAllFilesAccessSettings(context)
                                            }
                                        }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Text(stringResource(R.string.grant_file_access), style = DeckType.bottomBar, color = Color.White)
                                }
                            }
                        }

                        (!state.libraryLoaded || state.scanning) && state.games.isEmpty() -> {
                            EmptyPlatformMessage(
                                modifier = Modifier.fillMaxSize(),
                                text = stringResource(R.string.loading_games),
                            )
                        }

                        state.searching && state.searchQuery.isBlank() -> {
                            EmptyPlatformMessage(
                                modifier = Modifier.fillMaxSize(),
                                text = stringResource(R.string.search_empty),
                            )
                        }

                        state.searching && state.games.isEmpty() -> {
                            EmptyPlatformMessage(
                                modifier = Modifier.fillMaxSize(),
                                text = stringResource(R.string.no_games),
                            )
                        }

                        state.games.isEmpty() && state.tabs.getOrNull(state.selectedTab)?.type == TabType.Now -> {
                            EmptyPlatformMessage(
                                modifier = Modifier.fillMaxSize(),
                                text = stringResource(R.string.no_recent),
                            )
                        }

                        state.games.isEmpty() && state.tabs.getOrNull(state.selectedTab)?.type == TabType.Favs -> {
                            EmptyPlatformMessage(
                                modifier = Modifier.fillMaxSize(),
                                text = stringResource(R.string.no_favorites),
                            )
                        }

                        state.games.isEmpty() && state.tabs.getOrNull(state.selectedTab)?.type == TabType.Other -> {
                            EmptyPlatformMessage(
                                modifier = Modifier.fillMaxSize(),
                                text = stringResource(R.string.no_other_games),
                            )
                        }

                        else -> {
                            val stablePlatformOf = remember(state.platformsById) {
                                { g: Game -> state.platformsById[g.platformId] ?: PlatformCatalog.forFolder(g.platformId) }
                            }
                            val stableOnFocusChange = remember(vm) { { idx: Int -> vm.setFocus(idx) } }
                            val stableOnLongPress = remember(vm) { { game: Game -> vm.showMenu(game) } }
                            HomeContentSlot(
                                viewMode = state.viewMode,
                                games = state.games,
                                platformOf = stablePlatformOf,
                                focusIndexFlow = vm.focusIndex,
                                fastScrollingFlow = fastScrollingFlow,
                                covers = vm.covers,
                                metadataMap = state.metadataMap,
                                officialNames = state.officialNames,
                                favorites = state.favorites,
                                onFocusChange = stableOnFocusChange,
                                onLaunch = launchGame,
                                onLongPress = stableOnLongPress,
                                showMetadata = state.showMetadata,
                                metaDisplay = state.metaDisplay,
                                galleryAlign = state.galleryAlign,
                                idlePaused = hasChildFocusOverlay,
                            )
                        }
                    }
                }

                if (state.bottomBar) {
                    val stableOnApps = remember(vm) { { vm.openApps() } }
                    val stableOnSettings = remember(vm) { { vm.toggleSettings() } }
                    val stableOnViewSwitch = remember(vm) { { vm.openViewMenu() } }
                    val stableOnSearch = remember(vm) { { vm.toggleSearch() } }
                    val stableOnPlay = remember(launchGame) { { launchCurrentGame() } }
                    BottomBar(
                        viewMode = state.viewMode,
                        searching = state.searching,
                        onApps = stableOnApps,
                        onSettings = stableOnSettings,
                        onOptions = null,
                        onViewSwitch = stableOnViewSwitch,
                        onSearch = stableOnSearch,
                        onPlay = stableOnPlay,
                        swapAbxy = state.swapAbxy,
                        keyBindings = state.keyBindings,
                    )
                }
            }

            HomeOverlays(
                state = state,
                fullLibrary = fullLibrary,
                filesRevision = filesRevision,
                fileTasks = fileTasks,
                lanManager = lanManager,
                lanState = lanState,
                vm = vm,
                context = context,
                scope = scope,
                showLanControlFromStatusBar = showLanControlFromStatusBar,
                onShowLanControlFromStatusBarChange = { showLanControlFromStatusBar = it },
                onCapturingKeyBindChange = { capturingKeyBind = it },
                launchGame = launchGame,
            )

            letterBadge?.let { ch ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                        .background(com.pocketdeck.launcher.ui.theme.DeckBrand.brandBrush)
                        .padding(horizontal = 30.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ch,
                        style = DeckType.menuTitle.copy(
                            fontSize = androidx.compose.ui.unit.TextUnit(44f, androidx.compose.ui.unit.TextUnitType.Sp),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        ),
                        color = Color.White,
                    )
                }
            }

            if (state.scanning && state.games.isNotEmpty()) {
                Text(
                    stringResource(R.string.scanning_roms),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (state.bottomBar) 62.dp else 18.dp),
                    color = DeckColors.TextSecondary,
                    style = DeckType.statusInfo,
                )
            }
        }
    }
}

@Composable
private fun HomeBackgroundSlot(
    style: BackgroundStyle,
    customBackground: String?,
    staticPaused: Boolean,
    selectedTab: Int,
    games: List<Game>,
    officialNames: Map<String, String>,
    platformFor: (Game) -> Platform?,
    vm: HomeViewModel,
    focusIndexFlow: StateFlow<Int>,
    fastScrollingFlow: StateFlow<Boolean>,
    navAnimatingFlow: StateFlow<Boolean>,
) {
    val isFastScrolling by fastScrollingFlow.collectAsState()
    val isNavAnimating by navAnimatingFlow.collectAsState()
    var focusChangePaused by remember { mutableStateOf(false) }
    LaunchedEffect(focusIndexFlow) {
        var first = true
        focusIndexFlow.collectLatest {
            if (first) {
                first = false
            } else {
                focusChangePaused = true
                delay(220L)
                focusChangePaused = false
            }
        }
    }
    var lastTab by remember { mutableIntStateOf(selectedTab) }
    var tabChangePaused by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab) {
        if (selectedTab != lastTab) {
            lastTab = selectedTab
            tabChangePaused = true
            delay(220L)
            tabChangePaused = false
        }
    }

    val coverCandidates: List<Any>
    val knownIdx: Int?
    val apkPackage: String?
    if (style == BackgroundStyle.CoverGlow) {
        val focusIndex by focusIndexFlow.collectAsState()
        val focusedGame = games.getOrNull(focusIndex)
        var settledFocusGame by remember(selectedTab, games.firstOrNull()?.id) {
            mutableStateOf(focusedGame)
        }
        if (!isFastScrolling || settledFocusGame == null) {
            settledFocusGame = focusedGame
        }
        val coverIndexVer = vm.covers.indexVersion.collectAsState().value
        val blurOfficialName = settledFocusGame?.let { officialNames[it.id] }
        val map by vm.coverResolutionMap.collectAsState()
        val blurResolvedSource = settledFocusGame?.let { map[it.id]?.source }
        coverCandidates = remember(
            settledFocusGame?.id,
            settledFocusGame?.customCover,
            settledFocusGame?.localCover,
            blurOfficialName,
            blurResolvedSource,
            coverIndexVer,
        ) {
            val g = settledFocusGame
            if (g == null) emptyList()
            else vm.covers.candidates(g, platformFor(g), blurOfficialName)
        }
        knownIdx = remember(
            settledFocusGame?.id,
            settledFocusGame?.customCover,
            blurResolvedSource,
            coverIndexVer,
        ) {
            val g = settledFocusGame
            if (g == null) null
            else if (g.customCover != null) 0
            else vm.covers.knownIndex(g.id, platformFor(g))
        }
        apkPackage = remember(settledFocusGame?.id) {
            val g = settledFocusGame
            if (g != null && g.id.startsWith(GameLauncher.APK_PREFIX)) {
                g.id.removePrefix(GameLauncher.APK_PREFIX)
            } else {
                null
            }
        }
    } else {
        coverCandidates = emptyList()
        knownIdx = null
        apkPackage = null
    }

    WaveBackground(
        style = style,
        coverCandidates = coverCandidates,
        knownCoverIndex = knownIdx,
        apkPackage = apkPackage,
        customBackground = customBackground,
        paused = staticPaused || isFastScrolling || isNavAnimating || focusChangePaused || tabChangePaused,
    )
}

@Composable
private fun HomeContentSlot(
    viewMode: ViewMode,
    games: List<Game>,
    platformOf: (Game) -> Platform?,
    focusIndexFlow: StateFlow<Int>,
    fastScrollingFlow: StateFlow<Boolean>,
    covers: com.pocketdeck.launcher.data.CoverSource,
    metadataMap: Map<String, com.pocketdeck.launcher.data.model.GameMetadata>,
    officialNames: Map<String, String>,
    favorites: Set<String>,
    onFocusChange: (Int) -> Unit,
    onLaunch: (Game) -> Unit,
    onLongPress: (Game) -> Unit,
    showMetadata: Boolean,
    metaDisplay: com.pocketdeck.launcher.data.MetadataDisplayConfig,
    galleryAlign: com.pocketdeck.launcher.data.GalleryAlign,
    idlePaused: Boolean = false,
) {
    val focusIndex by focusIndexFlow.collectAsState()
    val isFastScrolling by fastScrollingFlow.collectAsState()
    val screenCfg = androidx.compose.ui.platform.LocalConfiguration.current
    val maxGalleryByWidth = ((screenCfg.screenWidthDp - 76f) / 1.35f).coerceAtLeast(182f)
    val galleryFocusH = (screenCfg.screenHeightDp * 0.42f).coerceAtMost(maxGalleryByWidth).coerceIn(182f, 320f).dp
    val gallerySmallH = (104f * (galleryFocusH.value / 182f)).dp
    when (viewMode) {
        ViewMode.Gallery -> GalleryView(
            games = games,
            platformOf = platformOf,
            focusIndex = focusIndex,
            covers = covers,
            metadataMap = metadataMap,
            officialNames = officialNames,
            favorites = favorites,
            onFocusChange = onFocusChange,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
            fastScrolling = isFastScrolling,
            idlePaused = idlePaused,
            focusCardHeight = galleryFocusH,
            smallCardHeight = gallerySmallH,
            showMetadata = showMetadata,
            metaDisplay = metaDisplay,
            align = galleryAlign,
            modifier = Modifier.fillMaxSize(),
        )
        ViewMode.List -> ListView(
            games = games,
            platformOf = platformOf,
            focusIndex = focusIndex,
            covers = covers,
            metadataMap = metadataMap,
            officialNames = officialNames,
            favorites = favorites,
            onFocusChange = onFocusChange,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
            fastScrolling = isFastScrolling,
            showMetadata = showMetadata,
            metaDisplay = metaDisplay,
            modifier = Modifier.fillMaxSize(),
        )
        ViewMode.Grid -> GridView(
            games = games,
            platformOf = platformOf,
            focusIndex = focusIndex,
            covers = covers,
            officialNames = officialNames,
            favorites = favorites,
            onFocusChange = onFocusChange,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
            fastScrolling = isFastScrolling,
            idlePaused = idlePaused,
            modifier = Modifier.fillMaxSize(),
        )
        ViewMode.Bubbles -> BubblesView(
            games = games,
            platformOf = platformOf,
            focusIndex = focusIndex,
            covers = covers,
            officialNames = officialNames,
            favorites = favorites,
            onFocusChange = onFocusChange,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
            fastScrolling = isFastScrolling,
            idlePaused = idlePaused,
            modifier = Modifier.fillMaxSize(),
        )
        ViewMode.CoverFlow -> CoverFlowView(
            games = games,
            platformOf = platformOf,
            focusIndex = focusIndex,
            covers = covers,
            metadataMap = metadataMap,
            officialNames = officialNames,
            favorites = favorites,
            onFocusChange = onFocusChange,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
            fastScrolling = isFastScrolling,
            showMetadata = showMetadata,
            metaDisplay = metaDisplay,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun homeViewModel(): HomeViewModel {
    val app = LocalContext.current.applicationContext as PocketDeckApp
    val factory = remember(app) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(
                    app.container.repository,
                    app.container.covers,
                    app.container.metadata,
                ) as T
        }
    }
    return viewModel(factory = factory)
}

private fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val intents = listOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        },
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        },
    )
    for (intent in intents) {
        if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return
    }
}
