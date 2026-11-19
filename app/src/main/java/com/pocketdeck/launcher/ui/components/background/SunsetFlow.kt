package com.pocketdeck.launcher.ui.components.background

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.pocketdeck.launcher.ui.theme.DeckPalette
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

private const val TWO_PI = (PI * 2).toFloat()

/** 流光一个完整循环（秒）。 */
internal const val FLOW_LOOP_SEC = 40f

/**
 * 一个柔光色块的运动参数。位置/幅度均为画布宽高的比例，半径为长边的比例。
 * 运动是 Lissajous 曲线：x/y 分别在一个循环内走 [fx]/[fy] 个周期，保证 [FLOW_LOOP_SEC] 无缝循环。
 */
internal class BlobSpec(
    val bx: Float,
    val by: Float,
    val ax: Float,
    val ay: Float,
    val fx: Int,
    val fy: Int,
    val px: Float,
    val py: Float,
    val radius: Float,
    val breathe: Float = 0.06f,
    val fb: Int = 1,
)

/** 柔和的径向衰减：结尾使用同色透明，避免浅色模式出现灰脏的晕边。 */
internal fun glowBrush(c: Color, radiusPx: Float): Brush = Brush.radialGradient(
    0f to c,
    0.35f to c.copy(alpha = c.alpha * 0.72f),
    0.65f to c.copy(alpha = c.alpha * 0.28f),
    1f to c.copy(alpha = 0f),
    center = Offset.Zero,
    radius = radiusPx.coerceAtLeast(1f),
)

/**
 * 在 (0,0) 处以缓存好的 [brush] 绘制一个色块，再通过 translate/scale 移动到当前位置。
 * Shader 只随画布尺寸创建一次，逐帧零分配。
 */
internal fun DrawScope.drawBlob(spec: BlobSpec, brush: Brush, radiusPx: Float, t: Float, alpha: Float = 1f) {
    if (alpha <= 0.002f) return
    val a = t / FLOW_LOOP_SEC * TWO_PI
    val w = size.width
    val h = size.height
    val cx = w * (spec.bx + spec.ax * sin(a * spec.fx + spec.px))
    val cy = h * (spec.by + spec.ay * sin(a * spec.fy + spec.py))
    val s = 1f + spec.breathe * sin(a * spec.fb + spec.px + spec.py)
    translate(cx, cy) {
        scale(s, s, pivot = Offset.Zero) {
            drawCircle(brush = brush, radius = radiusPx, center = Offset.Zero, alpha = alpha.coerceAtMost(1f))
        }
    }
}

internal fun blobRadiusPx(spec: BlobSpec, w: Float, h: Float): Float = (spec.radius * max(w, h)).coerceAtLeast(1f)

// ==================== Sunset Flow ====================

private val SunsetBlobs = arrayOf(
    // 左上暖橙
    BlobSpec(bx = 0.16f, by = 0.10f, ax = 0.10f, ay = 0.10f, fx = 1, fy = 2, px = 0.0f, py = 1.1f, radius = 0.62f),
    // 右侧粉
    BlobSpec(bx = 0.88f, by = 0.38f, ax = 0.08f, ay = 0.13f, fx = 2, fy = 1, px = 2.1f, py = 0.4f, radius = 0.64f, fb = 2),
    // 底部紫
    BlobSpec(bx = 0.45f, by = 1.08f, ax = 0.16f, ay = 0.06f, fx = 1, fy = 3, px = 4.0f, py = 2.6f, radius = 0.70f),
    // 极淡的游走高光
    BlobSpec(bx = 0.52f, by = 0.46f, ax = 0.22f, ay = 0.16f, fx = 3, fy = 2, px = 1.3f, py = 3.7f, radius = 0.34f, breathe = 0.12f),
)

internal fun sunsetFlowColors(p: DeckPalette): Array<Color> {
    val highlight = if (p.isDark) {
        lerp(p.brandMid, Color.White, 0.25f).copy(alpha = 0.10f)
    } else {
        lerp(p.brandMid, Color.White, 0.55f).copy(alpha = 0.22f)
    }
    return arrayOf(p.flowA, p.flowB, p.flowC, highlight)
}

/**
 * 默认背景：3 个大而柔的品牌色光斑沿 Lissajous 轨迹缓慢漂移（40s 循环），加一个极淡的高光。
 * 颜色来自主题 flowA/flowB/flowC，自动适配浅/深色。
 */
internal fun Modifier.sunsetFlow(p: DeckPalette, time: () -> Float): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val colors = sunsetFlowColors(p)
    val radii = FloatArray(SunsetBlobs.size) { blobRadiusPx(SunsetBlobs[it], w, h) }
    val brushes = Array(SunsetBlobs.size) { glowBrush(colors[it], radii[it]) }
    onDrawBehind {
        if (w <= 1f || h <= 1f) return@onDrawBehind
        val t = time()
        for (i in SunsetBlobs.indices) {
            drawBlob(SunsetBlobs[i], brushes[i], radii[i], t)
        }
    }
}
