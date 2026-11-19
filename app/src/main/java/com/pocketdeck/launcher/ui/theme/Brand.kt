package com.pocketdeck.launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sunset Arcade 品牌视觉辅助：
 * - [brandBrush]：当前主题的三色渐变（选中 Tab、A 键、开关、主按钮）
 * - [Modifier.brandFocusRing]：手柄焦点环（白色内隙 + 渐变外环 + 彩色辉光）
 * - [Modifier.cardSurface]：白卡 + 柔和粉调阴影（深色模式自动切换）
 */
object DeckBrand {
    /** 统一圆角刻度。 */
    val RadiusS = 10.dp
    val RadiusM = 14.dp
    val RadiusL = 18.dp
    val RadiusXL = 22.dp

    val brandBrush: Brush
        @Composable @ReadOnlyComposable get() {
            val p = LocalDeckColors.current
            return Brush.linearGradient(listOf(p.brandStart, p.brandMid, p.brandEnd))
        }

    fun brush(p: DeckPalette, start: Offset = Offset.Zero, end: Offset = Offset.Infinite): Brush =
        Brush.linearGradient(listOf(p.brandStart, p.brandMid, p.brandEnd), start = start, end = end)
}

/** 品牌渐变填充。 */
@Composable
fun Modifier.brandBackground(shape: Shape = RoundedCornerShape(50)): Modifier =
    this.background(DeckBrand.brandBrush, shape)

/** 选中时品牌渐变填充，未选中时使用 [unselectedColor]。 */
@Composable
fun Modifier.brandPillBackground(
    selected: Boolean,
    shape: Shape = RoundedCornerShape(50),
    unselectedColor: Color = Color.Transparent,
): Modifier = if (selected) {
    this.background(DeckBrand.brandBrush, shape)
} else {
    this.background(unselectedColor, shape)
}

/** 激活/焦点态使用当前主题品牌渐变描边，非激活态使用 [inactiveColor]。 */
@Composable
fun Modifier.brandBorder(
    active: Boolean,
    shape: Shape = RoundedCornerShape(DeckBrand.RadiusM),
    activeWidth: Dp = 1.4.dp,
    inactiveWidth: Dp = 0.75.dp,
    inactiveColor: Color = DeckColors.Line,
): Modifier = if (active) {
    this.border(activeWidth, DeckBrand.brandBrush, shape)
} else if (inactiveColor == Color.Transparent || inactiveWidth <= 0.dp) {
    this
} else {
    this.border(inactiveWidth, inactiveColor, shape)
}

/**
 * 卡片表面：浅色模式为白卡 + 粉调软阴影，深色模式为深紫卡。
 * [elevation] 为 0 时只绘制底色。
 */
@Composable
fun Modifier.cardSurface(
    shape: Shape = RoundedCornerShape(DeckBrand.RadiusL),
    elevation: Dp = 3.dp,
): Modifier {
    val p = LocalDeckColors.current
    val base = if (elevation > 0.dp) {
        this.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = p.cardShadow,
            spotColor = p.cardShadow,
            clip = false,
        )
    } else this
    return base.background(p.surface, shape)
}

/**
 * 手柄焦点环。[focused] 为 false 时不绘制任何东西（零开销）。
 * 环位于组件外侧 [gap] 处，宽度 [width]，并带一圈低透明度辉光。
 */
@Composable
fun Modifier.brandFocusRing(
    focused: Boolean,
    cornerRadius: Dp = DeckBrand.RadiusL,
    width: Dp = 2.5.dp,
    gap: Dp = 3.dp,
    glow: Boolean = true,
): Modifier {
    if (!focused) return this
    val p = LocalDeckColors.current
    return this.drawBehind {
        val g = gap.toPx()
        val w = width.toPx()
        val r = cornerRadius.toPx() + g + w / 2f
        val tl = Offset(-g - w / 2f, -g - w / 2f)
        val sz = Size(size.width + 2 * g + w, size.height + 2 * g + w)
        val brush = Brush.linearGradient(
            listOf(p.brandStart, p.brandMid, p.brandEnd),
            start = tl,
            end = Offset(tl.x + sz.width, tl.y + sz.height),
        )
        if (glow) {
            for (i in 1..3) {
                val spread = w + i * 3.dp.toPx()
                drawRoundRect(
                    color = p.brandMid.copy(alpha = 0.10f / i),
                    topLeft = Offset(tl.x - (spread - w) / 2f, tl.y - (spread - w) / 2f),
                    size = Size(sz.width + spread - w, sz.height + spread - w),
                    cornerRadius = CornerRadius(r + (spread - w) / 2f),
                    style = Stroke(width = spread),
                )
            }
        }
        drawRoundRect(
            brush = brush,
            topLeft = tl,
            size = sz,
            cornerRadius = CornerRadius(r),
            style = Stroke(width = w),
        )
    }
}

/** 轻量描边版焦点（列表行等密集场景）：柔和底色 + 渐变细描边。 */
@Composable
fun Modifier.brandFocusRow(focused: Boolean, shape: Shape = RoundedCornerShape(DeckBrand.RadiusM)): Modifier {
    if (!focused) return this
    val p = LocalDeckColors.current
    return this
        .background(p.surface, shape)
        .border(1.5.dp, Brush.linearGradient(listOf(p.brandStart, p.brandMid, p.brandEnd)), shape)
}

/** 便捷：在非 Composable 绘制作用域里取渐变色列表。 */
fun DeckPalette.brandColors(): List<Color> = listOf(brandStart, brandMid, brandEnd)
