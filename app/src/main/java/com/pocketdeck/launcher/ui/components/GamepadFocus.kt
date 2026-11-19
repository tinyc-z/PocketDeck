package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes

/** 弹窗半透明遮罩的纯点击关闭（不参与 D-pad/键盘焦点链，防止误触发关闭）。 */
fun Modifier.scrimTapDismiss(onDismiss: () -> Unit): Modifier =
    pointerInput(Unit) {
        detectTapGestures { onDismiss() }
    }

/** 带触屏点击音效的 clickable（如果用户开启了按键音）。 */
fun Modifier.soundClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val context = LocalContext.current
    clickable(enabled = enabled) {
        KeySound.playTouch(context)
        onClick()
    }
}

/** 带触屏点击音效且自定义 interactionSource / indication 的 clickable。 */
fun Modifier.soundClickableNoIndication(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val context = LocalContext.current
    clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
    ) {
        KeySound.playTouch(context)
        onClick()
    }
}

/**
 * 统一的键盘/手柄可聚焦点击修饰符：
 * - 支持 D-pad / 方向键 / Tab 聚焦与自动滚动到可视区
 * - 聚焦时绘制醒目高亮边框
 * - 按 Enter / 确认键 (A) 或点击均可触发 [onClick]
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.gamepadClickable(
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    focusRequester: FocusRequester? = null,
    shape: Shape = DeckShapes.menuButton,
    showFocusBorder: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) bringIntoView.bringIntoView()
    }
    return this
        .bringIntoViewRequester(bringIntoView)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .onPreviewKeyEvent { event ->
            if (GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings)) {
                if (event.type == KeyEventType.KeyDown) {
                    onClick()
                }
                // KeyDown 与 KeyUp 一并消费，避免 Compose clickable 二次触发或向上冒泡
                true
            } else {
                false
            }
        }
        .then(
            if (showFocusBorder && focused) {
                Modifier.border(1.8.dp, DeckColors.Ruby, shape)
            } else {
                Modifier
            },
        )
        .clickable {
            KeySound.playTouch(context)
            onClick()
        }
}
