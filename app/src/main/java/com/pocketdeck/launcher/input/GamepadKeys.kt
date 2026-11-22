package com.pocketdeck.launcher.input

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode

/** 可自定义绑定的快捷操作。 */
enum class KeyAction {
    PrevTab,
    NextTab,
    PrevLetter,
    NextLetter,
    QuickHud,
    Apps,
    Settings,
    Play,
    Back,
    Menu,
    ViewMode,
    Search,
}

/** 统一手柄按键映射（支持 Swap ABXY 与自定义按键绑定）。 */
object GamepadKeys {

    /** 显式清除绑定（区别于 null = 跟随默认）。 */
    const val UNBOUND = -1

    /** 默认按键码（跟随 swapAbxy）。 */
    fun defaultKeyCode(action: KeyAction, swapAbxy: Boolean): Int = when (action) {
        KeyAction.PrevTab -> KeyEvent.KEYCODE_BUTTON_L1
        KeyAction.NextTab -> KeyEvent.KEYCODE_BUTTON_R1
        KeyAction.PrevLetter -> KeyEvent.KEYCODE_BUTTON_L2
        KeyAction.NextLetter -> KeyEvent.KEYCODE_BUTTON_R2
        KeyAction.QuickHud -> KeyEvent.KEYCODE_BUTTON_THUMBL
        KeyAction.Apps -> KeyEvent.KEYCODE_BUTTON_SELECT
        KeyAction.Settings -> if (swapAbxy) KeyEvent.KEYCODE_BUTTON_X else KeyEvent.KEYCODE_BUTTON_Y
        KeyAction.Play -> if (swapAbxy) KeyEvent.KEYCODE_BUTTON_B else KeyEvent.KEYCODE_BUTTON_A
        KeyAction.Back -> if (swapAbxy) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B
        KeyAction.Menu -> KeyEvent.KEYCODE_BUTTON_START
        KeyAction.ViewMode -> KeyEvent.KEYCODE_BUTTON_THUMBR
        KeyAction.Search -> if (swapAbxy) KeyEvent.KEYCODE_BUTTON_Y else KeyEvent.KEYCODE_BUTTON_X
    }

    /** 解析某操作当前生效的 Android keyCode（可能返回 [UNBOUND]）。 */
    fun resolvedKeyCode(
        action: KeyAction,
        bindings: Map<KeyAction, Int>,
        swapAbxy: Boolean,
    ): Int = bindings[action] ?: defaultKeyCode(action, swapAbxy)

    /** 某按键码是否被任意操作显式自定义绑定。 */
    private fun isExplicitlyBound(keyCode: Int, bindings: Map<KeyAction, Int>): Boolean =
        keyCode > 0 && bindings.values.any { it == keyCode }

    /** 判断按键事件是否匹配指定操作（含键盘/遥控器兜底按键）。 */
    fun matches(
        key: Key,
        action: KeyAction,
        bindings: Map<KeyAction, Int> = emptyMap(),
        swapAbxy: Boolean = false,
    ): Boolean {
        val code = key.nativeKeyCode
        val bound = resolvedKeyCode(action, bindings, swapAbxy)
        if (bound != UNBOUND && code == bound) return true

        // 系统返回键始终允许作为 Back 兜底，避免用户无法退出界面
        if (action == KeyAction.Back && (key == Key.Back || key == Key.Escape)) return true

        // 兜底按键仅在未被其他操作显式占用时生效
        if (isExplicitlyBound(code, bindings)) return false
        return when (action) {
            KeyAction.Play -> key == Key.Enter || key == Key.NumPadEnter || key == Key.DirectionCenter
            KeyAction.Menu -> key == Key.Menu || key == Key.F3
            KeyAction.PrevTab -> key == Key.PageUp
            KeyAction.NextTab -> key == Key.PageDown
            KeyAction.PrevLetter -> key == Key.LeftBracket
            KeyAction.NextLetter -> key == Key.RightBracket
            KeyAction.QuickHud -> key == Key.F8
            KeyAction.Apps -> key == Key.F1
            KeyAction.Settings -> key == Key.F2
            KeyAction.ViewMode -> key == Key.F4
            KeyAction.Search -> key == Key.F5 || key == Key.Search || key == Key.Slash
            KeyAction.Back -> false
        }
    }

    fun isConfirm(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.Play, bindings, swapAbxy)

    fun isCancelOrApps(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.Back, bindings, swapAbxy)

    fun isApps(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.Apps, bindings, swapAbxy)

    fun isMenu(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.Menu, bindings, swapAbxy)

    fun isSettings(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.Settings, bindings, swapAbxy)

    fun isPrevTab(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.PrevTab, bindings, swapAbxy)

    fun isNextTab(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.NextTab, bindings, swapAbxy)

    fun isPrevLetter(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.PrevLetter, bindings, swapAbxy)

    fun isNextLetter(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.NextLetter, bindings, swapAbxy)

    fun isQuickHud(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.QuickHud, bindings, swapAbxy)

    fun isViewMode(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.ViewMode, bindings, swapAbxy)

    fun isSearch(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean = matches(key, KeyAction.Search, bindings, swapAbxy)

    fun isBack(key: Key): Boolean = key == Key.Back || key == Key.Escape

    /** 仅匹配手柄/自定义绑定确认键（不含 Enter/DirectionCenter，用于输入框确认弹窗）。 */
    fun isConfirmGamepadOnly(
        key: Key,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int> = emptyMap(),
    ): Boolean {
        val bound = resolvedKeyCode(KeyAction.Play, bindings, swapAbxy)
        return bound != UNBOUND && key.nativeKeyCode == bound
    }

    /** 系统或导航保留按键（禁止绑定，避免破坏 D-pad 导航与系统控制）。 */
    fun isReservedKey(key: Key): Boolean = when (key) {
        Key.DirectionUp,
        Key.DirectionDown,
        Key.DirectionLeft,
        Key.DirectionRight,
        Key.DirectionCenter,
        Key.Back,
        Key.Escape,
        Key.MoveHome,
        Key.SystemHome,
        Key.VolumeUp,
        Key.VolumeDown,
        Key.VolumeMute,
        Key.Power,
        Key.Enter,
        Key.NumPadEnter,
        -> true
        else -> key.nativeKeyCode <= 0
    }

    /**
     * 两个操作是否在相同交互上下文中运行（会彼此冲突）。
     * 仅 [KeyAction.Apps]（仅主桌面生效）与 [KeyAction.Back]（仅子页面/弹窗生效）天然互斥，允许共用同一按键。
     */
    private fun actionsConflict(a: KeyAction, b: KeyAction): Boolean {
        if (a == b) return false
        return true
    }

    /** 检查将 [target] 绑定到 [keyCode] 时，与哪些现有操作发生按键冲突。 */
    fun findConflicts(
        target: KeyAction,
        keyCode: Int,
        bindings: Map<KeyAction, Int>,
        swapAbxy: Boolean,
        ignoring: Set<KeyAction> = emptySet(),
    ): List<KeyAction> {
        if (keyCode <= 0) return emptyList()
        return KeyAction.entries.filter { other ->
            other != target &&
                other !in ignoring &&
                actionsConflict(target, other) &&
                resolvedKeyCode(other, bindings, swapAbxy) == keyCode
        }
    }

    /**
     * 计算冲突时是否可以安全互换：将 [target] 绑定为 [newKeyCode]，同时将 [conflicts] 改为 [target] 原先的按键。
     */
    fun canSwapConflicts(
        target: KeyAction,
        newKeyCode: Int,
        conflicts: List<KeyAction>,
        bindings: Map<KeyAction, Int>,
        swapAbxy: Boolean,
    ): Boolean {
        if (conflicts.isEmpty()) return false
        val oldKey = resolvedKeyCode(target, bindings, swapAbxy)
        if (oldKey <= 0 || oldKey == newKeyCode) return false
        val group = setOf(target) + conflicts
        return conflicts.all { c ->
            findConflicts(c, oldKey, bindings, swapAbxy, ignoring = group).isEmpty()
        }
    }

    /** 构造更新后的绑定映射（若与默认一致则清理键，保持跟随 Swap ABXY）。 */
    fun withBinding(
        bindings: Map<KeyAction, Int>,
        action: KeyAction,
        keyCode: Int,
        swapAbxy: Boolean,
    ): Map<KeyAction, Int> {
        val next = bindings.toMutableMap()
        if (keyCode != UNBOUND && keyCode == defaultKeyCode(action, swapAbxy)) {
            next.remove(action)
        } else {
            next[action] = keyCode
        }
        return next
    }

    /** 返回用于在桌面/设置角标上显示的简短按键名称（未绑定返回 null）。 */
    fun badgeLabel(
        bindings: Map<KeyAction, Int>,
        action: KeyAction,
        swapAbxy: Boolean,
    ): String? {
        val code = resolvedKeyCode(action, bindings, swapAbxy)
        if (code == UNBOUND) return null
        return keyLabel(code)
    }

    /** Android keyCode → 紧凑可读的按键名。 */
    fun keyLabel(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> "A"
        KeyEvent.KEYCODE_BUTTON_B -> "B"
        KeyEvent.KEYCODE_BUTTON_C -> "C"
        KeyEvent.KEYCODE_BUTTON_X -> "X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Y"
        KeyEvent.KEYCODE_BUTTON_Z -> "Z"
        KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
        KeyEvent.KEYCODE_BUTTON_START -> "START"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "SELECT"
        KeyEvent.KEYCODE_BUTTON_MODE -> "MODE"
        KeyEvent.KEYCODE_MENU -> "MENU"
        KeyEvent.KEYCODE_SEARCH -> "SEARCH"
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "PLAY"
        KeyEvent.KEYCODE_DPAD_CENTER -> "OK"
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "ENTER"
        KeyEvent.KEYCODE_SPACE -> "SPACE"
        KeyEvent.KEYCODE_TAB -> "TAB"
        KeyEvent.KEYCODE_PAGE_UP -> "PGUP"
        KeyEvent.KEYCODE_PAGE_DOWN -> "PGDN"
        in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16 ->
            "BTN${keyCode - KeyEvent.KEYCODE_BUTTON_1 + 1}"
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ->
            "F${keyCode - KeyEvent.KEYCODE_F1 + 1}"
        else -> KeyEvent.keyCodeToString(keyCode)
            .removePrefix("KEYCODE_")
            .removePrefix("BUTTON_")
            .ifBlank { "#$keyCode" }
    }
}
