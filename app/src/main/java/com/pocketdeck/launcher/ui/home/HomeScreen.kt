package com.pocketdeck.launcher.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketdeck.launcher.PocketDeckApp
import com.pocketdeck.launcher.data.AppLocale
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.AppTheme
import com.pocketdeck.launcher.data.BackgroundStyle
import com.pocketdeck.launcher.data.ViewMode
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.settings.LauncherRow
import com.pocketdeck.launcher.ui.settings.ManageCallbacks
import com.pocketdeck.launcher.ui.apps.AppsScreen
import com.pocketdeck.launcher.ui.components.BottomBar
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.ui.components.LocalIsOnline
import com.pocketdeck.launcher.ui.components.GameMenu
import com.pocketdeck.launcher.ui.components.GamePropertiesDialog
import com.pocketdeck.launcher.ui.components.SearchBar
import com.pocketdeck.launcher.ui.components.TopBar
import com.pocketdeck.launcher.ui.components.ViewModeMenu
import com.pocketdeck.launcher.ui.components.WaveBackground
import com.pocketdeck.launcher.ui.edit.EditGameScreen
import com.pocketdeck.launcher.lan.TransferDirection
import com.pocketdeck.launcher.lan.TransferPhase
import com.pocketdeck.launcher.ui.lan.LanServiceControlDialog
import com.pocketdeck.launcher.ui.lan.QuickShareScreen
import com.pocketdeck.launcher.ui.library.GameLibraryScreen
import com.pocketdeck.launcher.ui.settings.SettingsScreen
import com.pocketdeck.launcher.ui.settings.SettingsState
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.InterFamily
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.LocalDeckFontFamily
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import com.pocketdeck.launcher.ui.theme.SystemFonts
import com.pocketdeck.launcher.ui.theme.deckPalette
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主屏幕：波浪/气泡背景 + 顶栏 + Gallery/List/Grid/Bubbles + 底栏 + 搜索/菜单/Apps/Edit/Settings。
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    vm: HomeViewModel = homeViewModel(),
) {
    val state by vm.state.collectAsState()
    val fullLibrary by vm.fullLibrary.collectAsState()
    val filesRevision by vm.filesRevision.collectAsState()
    val fileTasks by vm.fileTasks.collectAsState()
    val context = LocalContext.current
    val lanManager = remember { (context.applicationContext as PocketDeckApp).container.lanManager }
    val lanState by lanManager.state.collectAsState()
    var showLanControlFromStatusBar by remember { mutableStateOf(false) }
    var showQuickShareScreen by remember { mutableStateOf(false) }
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

    var hasStorageAccess by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager(),
        )
    }

    LaunchedEffect(
        state.menuGame,
        state.propertiesGame,
        state.editingGame,
        state.showLibraryManager,
        state.showApps,
        state.showSettings,
        showLanControlFromStatusBar,
        showQuickShareScreen,
    ) {
        if (state.menuGame == null && state.propertiesGame == null && state.editingGame == null &&
            !state.showLibraryManager && !state.showApps && !state.showSettings &&
            !showLanControlFromStatusBar && !showQuickShareScreen
        ) {
            focusRequester.requestFocus()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
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
        focusRequester.requestFocus()
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
                focusRequester.requestFocus()
            }
            prevImeVisible = imeVisible
        }
    }

    val hasChildFocusOverlay = state.menuGame != null ||
        state.propertiesGame != null ||
        state.editingGame != null ||
        state.showLibraryManager ||
        state.showApps ||
        state.showSettings ||
        showLanControlFromStatusBar ||
        showQuickShareScreen

    val overlayOpen = hasChildFocusOverlay ||
        state.showViewMenu ||
        state.searching

    BackHandler(enabled = overlayOpen) {
        when {
            showQuickShareScreen -> showQuickShareScreen = false
            showLanControlFromStatusBar -> showLanControlFromStatusBar = false
            state.showLibraryManager -> vm.closeLibraryManager()
            state.propertiesGame != null -> vm.closeProperties()
            state.editingGame != null -> vm.closeEdit()
            state.menuGame != null -> vm.hideMenu()
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
            focusRequester.requestFocus()
        }
    }

    // 快滚/导航动画状态用 StateFlow + 协程 Job 管理，绝不在 HomeScreen 顶层读取，避免每次方向键触发 1200 行顶层全树重组
    val fastScrollingFlow = remember { MutableStateFlow(false) }
    val navAnimatingFlow = remember { MutableStateFlow(false) }
    val rapidTabSwitchFlow = remember { MutableStateFlow(false) }
    val scrollCtrl = remember {
        object {
            var lastNavKeyMs: Long = 0L
            var fastScrollJob: Job? = null
            var navAnimJob: Job? = null
            var rapidTabJob: Job? = null
        }
    }

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
                val isGameScrollKey = when (state.viewMode) {
                    ViewMode.Gallery -> event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                    ViewMode.List -> event.key == Key.DirectionUp || event.key == Key.DirectionDown
                    ViewMode.Grid, ViewMode.Bubbles ->
                        event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                            event.key == Key.DirectionUp || event.key == Key.DirectionDown
                }
                if (event.type == KeyEventType.KeyUp) {
                    val isTabKey = GamepadKeys.isNextTab(event.key, state.swapAbxy, state.keyBindings) ||
                        GamepadKeys.isPrevTab(event.key, state.swapAbxy, state.keyBindings) ||
                        (state.viewMode == ViewMode.List && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight))
                    if (isTabKey && rapidTabSwitchFlow.value) {
                        scrollCtrl.rapidTabJob?.cancel()
                        scrollCtrl.rapidTabJob = scope.launch {
                            delay(60L)
                            rapidTabSwitchFlow.value = false
                        }
                    }
                    if (isGameScrollKey && fastScrollingFlow.value) {
                        scrollCtrl.fastScrollJob?.cancel()
                        scrollCtrl.fastScrollJob = scope.launch {
                            delay(110L)
                            fastScrollingFlow.value = false
                        }
                    }
                    return@onPreviewKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                val isHomeTabSwitchKey = !hasChildFocusOverlay &&
                    !state.showViewMenu &&
                    !(state.searching && imeVisible) &&
                    (
                        GamepadKeys.isNextTab(event.key, state.swapAbxy, state.keyBindings) ||
                            GamepadKeys.isPrevTab(event.key, state.swapAbxy, state.keyBindings) ||
                            (state.viewMode == ViewMode.List && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight))
                        )
                if (isHomeTabSwitchKey) {
                    // 仅按住连发（repeatCount > 0）时临时降级为无动画瞬切；所有短按与点按保持弹性动画（除非开启减少动态效果）
                    if (event.nativeKeyEvent.repeatCount > 0) {
                        rapidTabSwitchFlow.value = true
                        scrollCtrl.rapidTabJob?.cancel()
                        scrollCtrl.rapidTabJob = scope.launch {
                            delay(220L)
                            rapidTabSwitchFlow.value = false
                        }
                    } else if (rapidTabSwitchFlow.value) {
                        scrollCtrl.rapidTabJob?.cancel()
                        rapidTabSwitchFlow.value = false
                    }
                }

                if (state.keySoundEnabled &&
                    !capturingKeyBind &&
                    !imeVisible &&
                    KeySound.isNavOrGamepadKey(event, state.swapAbxy, state.keyBindings)
                ) {
                    val isTabSwitchKey = isHomeTabSwitchKey
                    if (isTabSwitchKey) {
                        KeySound.playTabSwitch(context, true)
                    } else {
                        KeySound.play(context, true)
                    }
                }

                // 弹窗/全屏子页面自己处理按键
                if (state.menuGame != null || state.propertiesGame != null || state.editingGame != null ||
                    state.showLibraryManager || state.showApps || state.showSettings ||
                    showLanControlFromStatusBar || showQuickShareScreen
                ) {
                    return@onPreviewKeyEvent false
                }

                // 视图菜单打开时截获方向与确认键
                if (state.showViewMenu) {
                    return@onPreviewKeyEvent when {
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) -> {
                            vm.closeViewMenu()
                            true
                        }
                        event.key == Key.DirectionUp -> {
                            vm.moveViewMenu(-1)
                            true
                        }
                        event.key == Key.DirectionDown -> {
                            vm.moveViewMenu(1)
                            true
                        }
                        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                            vm.setViewMode(ViewMode.entries[state.viewMenuIndex])
                            true
                        }
                        else -> true
                    }
                }

                // 搜索模式：键盘开启时支持 B/Back 收起键盘保留结果；键盘收起后方向键/A 直接操作搜索结果
                if (state.searching) {
                    if (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings)) {
                        if ((imeVisible || searchFieldFocused) && state.searchQuery.isNotBlank()) {
                            dismissSearchKeyboard()
                        } else {
                            dismissSearchKeyboard()
                            vm.toggleSearch()
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (imeVisible) {
                        if (event.key == Key.DirectionDown) {
                            dismissSearchKeyboard()
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }
                    if (searchFieldFocused) {
                        // 键盘已隐藏但输入框仍残留焦点：交给主页并继续执行方向键/确认键
                        dismissSearchKeyboard()
                    }
                }

                if (isGameScrollKey) {
                    navAnimatingFlow.value = true
                    scrollCtrl.navAnimJob?.cancel()
                    scrollCtrl.navAnimJob = scope.launch {
                        delay(220L)
                        navAnimatingFlow.value = false
                    }
                    val now = SystemClock.uptimeMillis()
                    val rapid = event.nativeKeyEvent.repeatCount > 0 || (now - scrollCtrl.lastNavKeyMs < 170L)
                    scrollCtrl.lastNavKeyMs = now
                    if (rapid) {
                        fastScrollingFlow.value = true
                        scrollCtrl.fastScrollJob?.cancel()
                        scrollCtrl.fastScrollJob = scope.launch {
                            delay(200L)
                            fastScrollingFlow.value = false
                        }
                    }
                }

                when {
                    GamepadKeys.isNextTab(event.key, state.swapAbxy, state.keyBindings) -> {
                        vm.nextTab()
                        true
                    }
                    GamepadKeys.isPrevTab(event.key, state.swapAbxy, state.keyBindings) -> {
                        vm.prevTab()
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        if (state.viewMode == ViewMode.List) vm.nextTab() else vm.moveFocus(1)
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        if (state.viewMode == ViewMode.List) vm.prevTab() else vm.moveFocus(-1)
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        when (state.viewMode) {
                            ViewMode.List -> vm.moveFocus(1)
                            ViewMode.Grid -> vm.moveFocus(GRID_COLUMNS)
                            ViewMode.Bubbles -> vm.setFocus(bubblesMoveVertical(vm.focusIndex.value, state.games.size, 1))
                            ViewMode.Gallery -> Unit
                        }
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        when (state.viewMode) {
                            ViewMode.List -> vm.moveFocus(-1)
                            ViewMode.Grid -> vm.moveFocus(-GRID_COLUMNS)
                            ViewMode.Bubbles -> vm.setFocus(bubblesMoveVertical(vm.focusIndex.value, state.games.size, -1))
                            ViewMode.Gallery -> Unit
                        }
                        true
                    }
                    GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                        if (!hasStorageAccess && state.games.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            openAllFilesAccessSettings(context)
                        } else {
                            launchCurrentGame()
                        }
                        true
                    }
                    GamepadKeys.isApps(event.key, state.swapAbxy, state.keyBindings) -> {
                        vm.openApps()
                        true
                    }
                    GamepadKeys.isMenu(event.key, state.swapAbxy, state.keyBindings) -> {
                        showCurrentGameMenu()
                        true
                    }
                    GamepadKeys.isSettings(event.key, state.swapAbxy, state.keyBindings) -> {
                        vm.toggleSettings()
                        true
                    }
                    GamepadKeys.isViewMode(event.key, state.swapAbxy, state.keyBindings) -> {
                        vm.openViewMenu()
                        true
                    }
                    GamepadKeys.isSearch(event.key, state.swapAbxy, state.keyBindings) -> {
                        vm.toggleSearch()
                        true
                    }
                    event.key == Key.F6 && (lanState.anyRunning || lanState.incomingRequest != null || lanState.transfer != null) -> {
                        showLanControlFromStatusBar = true
                        true
                    }
                    else -> false
                }
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
                onOpenQuickShare = { showQuickShareScreen = true },
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
                        compareBy({ com.pocketdeck.launcher.data.PlatformCatalog.sortKey(it.id) }, { it.shortName }),
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
                ),
                onDismiss = {
                    capturingKeyBind = false
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
                onCapturingKeyBindChange = { capturingKeyBind = it },
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
                ),
                isCovered = state.showLibraryManager,
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
                onOpenQuickShare = { showQuickShareScreen = true },
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
                    showLanControlFromStatusBar = false
                    vm.openLibraryManager(
                        initialDirPath = dir.absolutePath,
                        initialFocusFilePath = focusFile?.absolutePath,
                    )
                },
                onOpenQuickShareScreen = {
                    showLanControlFromStatusBar = false
                    showQuickShareScreen = true
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
                onDismiss = { showLanControlFromStatusBar = false },
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
                    showQuickShareScreen = false
                    vm.openLibraryManager(
                        initialDirPath = dir.absolutePath,
                        initialFocusFilePath = focusFile?.absolutePath,
                    )
                },
                onDismiss = { showQuickShareScreen = false },
            )
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
