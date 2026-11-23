package com.pocketdeck.launcher

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UT-20 · 手柄按键映射、ABXY 互换、冲突检测与键盘兜底测试。
 */
class GamepadKeysTest {

    @Test
    fun defaultKeyCodesRespectSwapAbxy() {
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, GamepadKeys.defaultKeyCode(KeyAction.Play, swapAbxy = false))
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, GamepadKeys.defaultKeyCode(KeyAction.Back, swapAbxy = false))
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, GamepadKeys.defaultKeyCode(KeyAction.Favorite, swapAbxy = false))
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, GamepadKeys.defaultKeyCode(KeyAction.Settings, swapAbxy = false))

        assertEquals(KeyEvent.KEYCODE_BUTTON_B, GamepadKeys.defaultKeyCode(KeyAction.Play, swapAbxy = true))
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, GamepadKeys.defaultKeyCode(KeyAction.Back, swapAbxy = true))
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, GamepadKeys.defaultKeyCode(KeyAction.Favorite, swapAbxy = true))
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, GamepadKeys.defaultKeyCode(KeyAction.Settings, swapAbxy = true))

        // 肩键与功能键不受 swapAbxy 影响
        assertEquals(KeyEvent.KEYCODE_BUTTON_L1, GamepadKeys.defaultKeyCode(KeyAction.PrevTab, swapAbxy = true))
        assertEquals(KeyEvent.KEYCODE_BUTTON_R1, GamepadKeys.defaultKeyCode(KeyAction.NextTab, swapAbxy = true))
        assertEquals(KeyEvent.KEYCODE_BUTTON_START, GamepadKeys.defaultKeyCode(KeyAction.Menu, swapAbxy = true))
        assertEquals(KeyEvent.KEYCODE_BUTTON_SELECT, GamepadKeys.defaultKeyCode(KeyAction.Apps, swapAbxy = true))
    }

    @Test
    fun withBindingRemovesEntryWhenMatchingDefaultAndPreservesUnbound() {
        val custom = GamepadKeys.withBinding(emptyMap(), KeyAction.Menu, KeyEvent.KEYCODE_BUTTON_L2, swapAbxy = false)
        assertEquals(KeyEvent.KEYCODE_BUTTON_L2, custom[KeyAction.Menu])

        val restored = GamepadKeys.withBinding(custom, KeyAction.Menu, KeyEvent.KEYCODE_BUTTON_START, swapAbxy = false)
        assertFalse(restored.containsKey(KeyAction.Menu))

        val unbound = GamepadKeys.withBinding(emptyMap(), KeyAction.Favorite, GamepadKeys.UNBOUND, swapAbxy = false)
        assertEquals(GamepadKeys.UNBOUND, GamepadKeys.resolvedKeyCode(KeyAction.Favorite, unbound, swapAbxy = false))
        assertNull(GamepadKeys.badgeLabel(unbound, KeyAction.Favorite, swapAbxy = false))
    }

    @Test
    fun findConflictsAndCanSwapConflictsWorkConsistently() {
        // 将 Apps 改绑为 B 键，应检测到与 Back 冲突
        val conflicts = GamepadKeys.findConflicts(
            target = KeyAction.Apps,
            keyCode = KeyEvent.KEYCODE_BUTTON_B,
            bindings = emptyMap(),
            swapAbxy = false,
        )
        assertEquals(listOf(KeyAction.Back), conflicts)
        assertTrue(
            GamepadKeys.canSwapConflicts(
                target = KeyAction.Apps,
                newKeyCode = KeyEvent.KEYCODE_BUTTON_B,
                conflicts = conflicts,
                bindings = emptyMap(),
                swapAbxy = false,
            ),
        )

        // Search 默认未绑定（UNBOUND），与已绑定动作冲突时不能互换
        val searchConflicts = GamepadKeys.findConflicts(
            target = KeyAction.Search,
            keyCode = KeyEvent.KEYCODE_BUTTON_Y,
            bindings = emptyMap(),
            swapAbxy = false,
        )
        assertEquals(listOf(KeyAction.Settings), searchConflicts)
        assertFalse(
            GamepadKeys.canSwapConflicts(
                target = KeyAction.Search,
                newKeyCode = KeyEvent.KEYCODE_BUTTON_Y,
                conflicts = searchConflicts,
                bindings = emptyMap(),
                swapAbxy = false,
            ),
        )

        // UNBOUND 或空冲突列表不报错
        assertTrue(GamepadKeys.findConflicts(KeyAction.Menu, GamepadKeys.UNBOUND, emptyMap(), false).isEmpty())
        assertFalse(GamepadKeys.canSwapConflicts(KeyAction.Menu, KeyEvent.KEYCODE_BUTTON_X, emptyList(), emptyMap(), false))
    }

    @Test
    fun confirmGamepadOnlyExcludesEnterWhileSystemBackAlwaysMatches() {
        assertTrue(GamepadKeys.isConfirm(Key.Enter, swapAbxy = false))
        assertTrue(GamepadKeys.isConfirm(Key.DirectionCenter, swapAbxy = false))
        assertTrue(GamepadKeys.isConfirm(Key.ButtonA, swapAbxy = false))

        assertFalse(GamepadKeys.isConfirmGamepadOnly(Key.Enter, swapAbxy = false))
        assertFalse(GamepadKeys.isConfirmGamepadOnly(Key.DirectionCenter, swapAbxy = false))
        assertTrue(GamepadKeys.isConfirmGamepadOnly(Key.ButtonA, swapAbxy = false))
        assertTrue(GamepadKeys.isConfirmGamepadOnly(Key.ButtonB, swapAbxy = true))
        assertFalse(GamepadKeys.isConfirmGamepadOnly(Key.ButtonA, swapAbxy = true))

        // 即使 Back 被显式清除，系统 Back / Escape 仍保持可退出兜底
        val unboundBack = mapOf(KeyAction.Back to GamepadKeys.UNBOUND)
        assertTrue(GamepadKeys.isCancelOrApps(Key.Back, swapAbxy = false, bindings = unboundBack))
        assertTrue(GamepadKeys.isCancelOrApps(Key.Escape, swapAbxy = false, bindings = unboundBack))
        assertFalse(GamepadKeys.isCancelOrApps(Key.ButtonB, swapAbxy = false, bindings = unboundBack))
    }

    @Test
    fun keyboardFallbackIsSuppressedWhenExplicitlyBoundToAnotherAction() {
        // 默认 F3 是 Menu 的键盘兜底
        assertTrue(GamepadKeys.isMenu(Key.F3, swapAbxy = false))
        // 一旦用户把 F3 显式绑定给 Search，F3 不再同时触发 Menu
        val custom = mapOf(KeyAction.Search to KeyEvent.KEYCODE_F3)
        assertTrue(GamepadKeys.isSearch(Key.F3, swapAbxy = false, bindings = custom))
        assertFalse(GamepadKeys.isMenu(Key.F3, swapAbxy = false, bindings = custom))
    }
}
