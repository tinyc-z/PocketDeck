package com.pocketdeck.launcher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.components.icons.PanelIcons
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.brandFocusRing
import com.pocketdeck.launcher.ui.theme.cardSurface

/**
 * 全 App 弹窗四级标准宽度常量（遵循 docs/DESIGN_GUIDELINES.md 第 2 节）。
 */
object DeckDialogSize {
    val WidthS = 340.dp   // 快捷菜单 / 轻浮层 HUD
    val WidthM = 440.dp   // 确认框 / 单表单 / 短选择 / 进度
    val WidthL = 520.dp   // 复杂属性 / 长列表 / 刮削 / 文件预览
    val WidthXL = 640.dp  // 双栏图文展示（全 App 上限）
}

/**
 * 弹窗面板容器：统一圆角底板 + 右上角等距（top == end）关闭（✕）图标按钮。
 */
@Composable
fun DialogPanelCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    closeInset: Dp = 14.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    showCloseButton: Boolean = true,
    closeFocused: Boolean = false,
    closeFocusRequester: FocusRequester? = null,
    topEndExtra: (@Composable () -> Unit)? = null,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetShape = RoundedCornerShape(DeckBrand.RadiusXL)
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val topMarginPx = with(density) { 8.dp.toPx() }
    val keyboardClearancePx = with(density) { 8.dp.toPx() }
    var naturalTopPx by remember { mutableFloatStateOf(0f) }
    var naturalHeightPx by remember { mutableFloatStateOf(0f) }
    var parentHeightPx by remember { mutableFloatStateOf(0f) }

    // 软键盘弹起时平滑上移弹窗，尽量让弹窗底边露在键盘上方，最多上移至顶部留白 8dp；键盘收起后平滑复位
    val targetTranslationY = if (imeBottomPx > 0 && parentHeightPx > 0f && naturalHeightPx > 0f) {
        val naturalBottom = naturalTopPx + naturalHeightPx
        val keyboardTop = parentHeightPx - imeBottomPx
        val neededShift = (naturalBottom - (keyboardTop - keyboardClearancePx)).coerceAtLeast(0f)
        val maxShift = (naturalTopPx - topMarginPx).coerceAtLeast(0f)
        -neededShift.coerceAtMost(maxShift)
    } else {
        0f
    }
    val animatedTranslationY by animateFloatAsState(
        targetValue = targetTranslationY,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "dialog_ime_shift",
    )

    Box(
        modifier
            .onGloballyPositioned { coords ->
                naturalHeightPx = coords.size.height.toFloat()
                parentHeightPx = coords.findRootCoordinates().size.height.toFloat()
                naturalTopPx = coords.positionInRoot().y
            }
            .graphicsLayer { translationY = animatedTranslationY }
            .cardSurface(sheetShape, elevation = 6.dp)
            .clip(sheetShape)
            .background(DeckColors.DialogBg)
            .clickable(enabled = false, onClick = {}),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
        if (topEndExtra != null || showCloseButton) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = closeInset, end = closeInset),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (topEndExtra != null) {
                    topEndExtra()
                }
                if (showCloseButton) {
                    DialogCloseButton(
                        onClick = onDismiss,
                        focused = closeFocused,
                        focusRequester = closeFocusRequester,
                        swapAbxy = swapAbxy,
                        keyBindings = keyBindings,
                    )
                }
            }
        }
    }
}

/**
 * 弹窗右上角统一使用的圆形关闭（✕）矢量图标按钮，支持触屏音效与手柄焦点高亮。
 */
@Composable
fun DialogCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    darkSurface: Boolean = false,
    focused: Boolean = false,
    focusRequester: FocusRequester? = null,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
) {
    val bg = when {
        darkSurface -> Color.White.copy(alpha = 0.16f)
        focused -> DeckColors.AccentSoft
        else -> DeckColors.SurfaceAlt
    }
    val iconColor = when {
        darkSurface -> Color.White
        focused -> DeckColors.Ruby
        else -> DeckColors.TextSecondary
    }
    Box(
        modifier
            .brandFocusRing(focused, cornerRadius = size / 2, width = 2.dp, gap = 2.dp, glow = false)
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .gamepadClickable(
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                focusRequester = focusRequester,
                shape = CircleShape,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        DeckIcon(PanelIcons.Close, tint = iconColor, size = size * 0.5f)
    }
}
