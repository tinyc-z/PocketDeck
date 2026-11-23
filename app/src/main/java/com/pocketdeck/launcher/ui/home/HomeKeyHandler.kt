package com.pocketdeck.launcher.ui.home

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.pocketdeck.launcher.data.ViewMode
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.lan.LanServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class HomeScrollController {
    var lastNavKeyMs: Long = 0L
    var fastScrollJob: Job? = null
    var navAnimJob: Job? = null
    var rapidTabJob: Job? = null
}

internal fun handleHomePreviewKeyEvent(
    event: KeyEvent,
    state: HomeUiState,
    lanState: LanServerState,
    vm: HomeViewModel,
    context: Context,
    scope: CoroutineScope,
    scrollCtrl: HomeScrollController,
    fastScrollingFlow: MutableStateFlow<Boolean>,
    navAnimatingFlow: MutableStateFlow<Boolean>,
    rapidTabSwitchFlow: MutableStateFlow<Boolean>,
    hasChildFocusOverlay: Boolean,
    showLanControlFromStatusBar: Boolean,
    capturingKeyBind: Boolean,
    imeVisible: Boolean,
    searchFieldFocused: Boolean,
    hasStorageAccess: Boolean,
    dismissSearchKeyboard: () -> Unit,
    openAllFilesAccess: () -> Unit,
    launchCurrentGame: () -> Unit,
    showCurrentGameMenu: () -> Unit,
    onLetterJump: (Int) -> Unit,
    openLanControlFromStatusBar: () -> Unit,
): Boolean {
    val isGameScrollKey = when (state.viewMode) {
        ViewMode.Gallery, ViewMode.CoverFlow, ViewMode.Hero -> event.key == Key.DirectionLeft || event.key == Key.DirectionRight
        ViewMode.List, ViewMode.Wheel -> event.key == Key.DirectionUp || event.key == Key.DirectionDown
        ViewMode.Grid, ViewMode.Bubbles, ViewMode.Shelf ->
            event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                event.key == Key.DirectionUp || event.key == Key.DirectionDown
    }
    val isVerticalListMode = state.viewMode == ViewMode.List || state.viewMode == ViewMode.Wheel
    if (event.type == KeyEventType.KeyUp) {
        val isTabKey = GamepadKeys.isNextTab(event.key, state.swapAbxy, state.keyBindings) ||
            GamepadKeys.isPrevTab(event.key, state.swapAbxy, state.keyBindings) ||
            (isVerticalListMode && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight))
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
        return false
    }
    if (event.type != KeyEventType.KeyDown) return false

    val isHomeTabSwitchKey = !hasChildFocusOverlay &&
        !state.showViewMenu &&
        !(state.searching && imeVisible) &&
        (
            GamepadKeys.isNextTab(event.key, state.swapAbxy, state.keyBindings) ||
                GamepadKeys.isPrevTab(event.key, state.swapAbxy, state.keyBindings) ||
                (isVerticalListMode && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight))
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
        if (isHomeTabSwitchKey) {
            KeySound.playTabSwitch(context, true)
        } else {
            KeySound.play(context, true)
        }
    }

    // 弹窗/全屏子页面自己处理按键
    if (state.detailGame != null || state.propertiesGame != null || state.editingGame != null ||
        state.manualScrapeGame != null || state.showScrapeTaskDialog || state.showQuickHud ||
        state.showLibraryManager || state.showApps || state.showSettings ||
        showLanControlFromStatusBar
    ) {
        return false
    }

    // 视图菜单打开时截获方向与确认键
    if (state.showViewMenu) {
        val cols = if (ViewMode.entries.size > 4) 2 else 1
        return when {
            GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings) -> {
                vm.closeViewMenu()
                true
            }
            event.key == Key.DirectionUp -> {
                vm.moveViewMenu(-cols)
                true
            }
            event.key == Key.DirectionDown -> {
                vm.moveViewMenu(cols)
                true
            }
            event.key == Key.DirectionLeft -> {
                vm.moveViewMenu(-1)
                true
            }
            event.key == Key.DirectionRight -> {
                vm.moveViewMenu(1)
                true
            }
            GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
                if (event.nativeKeyEvent.repeatCount == 0) {
                    vm.setViewMode(ViewMode.entries[state.viewMenuIndex])
                }
                true
            }
            else -> true
        }
    }

    val isInitialPress = event.nativeKeyEvent.repeatCount == 0

    // 搜索模式：键盘开启时支持 B/Back 收起键盘保留结果；外接键盘输入文本键时不强行失焦，仅导航/手柄键切回结果列表
    if (state.searching) {
        if (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, state.swapAbxy, state.keyBindings)) {
            if (isInitialPress) {
                if ((imeVisible || searchFieldFocused) && state.searchQuery.isNotBlank()) {
                    dismissSearchKeyboard()
                } else {
                    dismissSearchKeyboard()
                    vm.toggleSearch()
                }
            }
            return true
        }
        if (imeVisible) {
            if (event.key == Key.DirectionDown) {
                dismissSearchKeyboard()
                return true
            }
            return false
        }
        if (searchFieldFocused) {
            val isNavOrGamepadKey = isGameScrollKey ||
                event.key == Key.DirectionUp ||
                event.key == Key.DirectionDown ||
                event.key == Key.DirectionLeft ||
                event.key == Key.DirectionRight ||
                GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) ||
                GamepadKeys.isNextTab(event.key, state.swapAbxy, state.keyBindings) ||
                GamepadKeys.isPrevTab(event.key, state.swapAbxy, state.keyBindings)
            if (!isNavOrGamepadKey) {
                return false
            }
            // 仅在按下方向键/手柄导航键时把焦点从已隐藏键盘的输入框交回游戏列表
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

    return when {
        GamepadKeys.isNextTab(event.key, state.swapAbxy, state.keyBindings) -> {
            vm.nextTab()
            true
        }
        GamepadKeys.isPrevTab(event.key, state.swapAbxy, state.keyBindings) -> {
            vm.prevTab()
            true
        }
        GamepadKeys.isPrevLetter(event.key, state.swapAbxy, state.keyBindings) -> {
            onLetterJump(-1)
            true
        }
        GamepadKeys.isNextLetter(event.key, state.swapAbxy, state.keyBindings) -> {
            onLetterJump(1)
            true
        }
        state.quickHudEnabled && GamepadKeys.isQuickHud(event.key, state.swapAbxy, state.keyBindings) -> {
            if (isInitialPress) vm.toggleQuickHud()
            true
        }
        event.key == Key.DirectionRight -> {
            if (isVerticalListMode) vm.nextTab() else vm.moveFocus(1)
            true
        }
        event.key == Key.DirectionLeft -> {
            if (isVerticalListMode) vm.prevTab() else vm.moveFocus(-1)
            true
        }
        event.key == Key.DirectionDown -> {
            when (state.viewMode) {
                ViewMode.List, ViewMode.Wheel -> vm.moveFocus(1)
                ViewMode.Grid -> vm.moveFocus(GRID_COLUMNS)
                ViewMode.Shelf -> vm.moveFocus(SHELF_COLUMNS)
                ViewMode.Bubbles -> vm.setFocus(bubblesMoveVertical(vm.focusIndex.value, state.games.size, 1))
                ViewMode.Gallery, ViewMode.CoverFlow, ViewMode.Hero -> Unit
            }
            true
        }
        event.key == Key.DirectionUp -> {
            when (state.viewMode) {
                ViewMode.List, ViewMode.Wheel -> vm.moveFocus(-1)
                ViewMode.Grid -> vm.moveFocus(-GRID_COLUMNS)
                ViewMode.Shelf -> vm.moveFocus(-SHELF_COLUMNS)
                ViewMode.Bubbles -> vm.setFocus(bubblesMoveVertical(vm.focusIndex.value, state.games.size, -1))
                ViewMode.Gallery, ViewMode.CoverFlow, ViewMode.Hero -> Unit
            }
            true
        }
        GamepadKeys.isConfirm(event.key, state.swapAbxy, state.keyBindings) -> {
            if (isInitialPress) {
                if (!hasStorageAccess && state.games.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    openAllFilesAccess()
                } else {
                    showCurrentGameMenu()
                }
            }
            true
        }
        GamepadKeys.isApps(event.key, state.swapAbxy, state.keyBindings) -> {
            if (isInitialPress) vm.openApps()
            true
        }
        GamepadKeys.isMenu(event.key, state.swapAbxy, state.keyBindings) -> {
            if (isInitialPress) launchCurrentGame()
            true
        }
        GamepadKeys.isFavorite(event.key, state.swapAbxy, state.keyBindings) -> {
            if (isInitialPress) {
                state.games.getOrNull(vm.focusIndex.value)?.let { vm.toggleFavorite(it.id) }
            }
            true
        }
        GamepadKeys.isSettings(event.key, state.swapAbxy, state.keyBindings) -> {
            if (isInitialPress) vm.toggleSettings()
            true
        }
        GamepadKeys.isViewMode(event.key, state.swapAbxy, state.keyBindings) -> {
            if (isInitialPress) vm.openViewMenu()
            true
        }
        GamepadKeys.isSearch(event.key, state.swapAbxy, state.keyBindings) -> {
            if (isInitialPress) vm.toggleSearch()
            true
        }
        event.key == Key.F6 && (lanState.anyRunning || lanState.incomingRequest != null || lanState.transfer != null) -> {
            if (isInitialPress) openLanControlFromStatusBar()
            true
        }
        else -> false
    }
}
