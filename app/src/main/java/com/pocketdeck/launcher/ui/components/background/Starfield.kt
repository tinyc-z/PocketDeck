package com.pocketdeck.launcher.ui.components.background

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.pocketdeck.launcher.ui.theme.DeckPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TWO_PI = (PI * 2).toFloat()
private const val STAR_COUNT = 78
private const val STAR_WRAP = 1.06f
private const val STAR_PAD = 0.03f
private const val METEOR_CYCLE_SEC = 6.0f
private const val METEOR_DURATION_SEC = 1.15f
private const val METEOR_PHASE_OFFSET_SEC = 1.8f
private const val GLOW_BASE_RADIUS = 16f

private val NebulaViolet = Color(0xFF7C4DCC)
private val StarCoreDark = Color(0xFFFFFCFF)

/**
 * 与预览脚本一致的确定性伪随机函数（双精度），保证星空构图稳定复现。
 */
private fun starHash(n: Double): Float {
    val x = sin(n * 127.1 + 311.7) * 43758.5453123
    return (x - floor(x)).toFloat()
}

private class StarSpec(
    val x0: Float,
    val y0: Float,
    val layer: Int,        // 0=远, 1=中, 2=近
    val xSpeed: Float,     // 单位：wrap 空间 / 秒（对齐 BG_TIME_WRAP_SEC 整数周）
    val ySpeed: Float,
    val size: Float,
    val phase: Float,
    val twinkleOmega: Float,
    val colorPick: Int,
    val isCross: Boolean,
)

private val Stars: Array<StarSpec> = Array(STAR_COUNT) { i ->
    val layer = when {
        i < 42 -> 0
        i < 66 -> 1
        else -> 2
    }
    // 水平/垂直运动周期均取 BG_TIME_WRAP_SEC (1200s) 的整数倍，确保 1200s 回绕无缝衔接
    val xCycles = when (layer) {
        0 -> 5
        1 -> 12
        else -> 25
    }
    val yCycles = when (layer) {
        0 -> 1
        1 -> 3
        else -> 5
    }
    val rawTwinkleSpeed = 0.7f + starHash(i * 2.9 + 9.0) * 1.6f
    val twinkleCycles = (rawTwinkleSpeed * BG_TIME_WRAP_SEC / TWO_PI).roundToInt().coerceAtLeast(1)
    val size = when (layer) {
        0 -> 0.7f + starHash(i + 11.0) * 0.7f
        1 -> 1.2f + starHash(i + 17.0) * 0.9f
        else -> 1.8f + starHash(i + 23.0) * 1.1f
    }
    StarSpec(
        x0 = starHash(i * 3.1 + 1.0),
        y0 = starHash(i * 5.7 + 2.0),
        layer = layer,
        xSpeed = xCycles * STAR_WRAP / BG_TIME_WRAP_SEC,
        ySpeed = yCycles * STAR_WRAP / BG_TIME_WRAP_SEC,
        size = size,
        phase = starHash(i * 7.3 + 5.0) * TWO_PI,
        twinkleOmega = twinkleCycles * TWO_PI / BG_TIME_WRAP_SEC,
        colorPick = i % 3,
        isCross = layer == 2 && i % 3 == 0,
    )
}

/**
 * Starfield（星海漫游）：
 * 3 层视差星场缓慢向右上漂移，搭配双团柔和品牌星云、闪烁十字星芒与偶发斜向流星。
 * 所有径向/线性渐变 Brush 均在 [drawWithCache] 中预构建，逐帧零对象分配。
 */
internal fun Modifier.starfield(p: DeckPalette, time: () -> Float): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val dark = p.isDark
    val bg = p.background
    val unit = (w / 800f).coerceAtLeast(0.28f)

    // 1. 双团星云底晕（原点居中缓存，绘制时 translate 平移）
    val neb1R = (w * 0.42f).coerceAtLeast(1f)
    val neb1Color = if (dark) lerp(p.brandEnd, NebulaViolet, 0.45f) else lerp(p.brandStart, Color.White, 0.25f)
    val neb1Brush = Brush.radialGradient(
        0.00f to neb1Color.copy(alpha = if (dark) 0.24f else 0.28f),
        0.55f to p.brandMid.copy(alpha = if (dark) 0.08f else 0.10f),
        1.00f to p.brandMid.copy(alpha = 0f),
        center = Offset.Zero,
        radius = neb1R,
    )

    val neb2R = (w * 0.38f).coerceAtLeast(1f)
    val neb2Color = if (dark) p.brandStart else p.brandEnd
    val neb2Brush = Brush.radialGradient(
        0.00f to neb2Color.copy(alpha = if (dark) 0.18f else 0.22f),
        0.50f to p.brandMid.copy(alpha = if (dark) 0.06f else 0.08f),
        1.00f to p.brandMid.copy(alpha = 0f),
        center = Offset.Zero,
        radius = neb2R,
    )

    // 2. 预计算各层各色调星点颜色与通用径向柔光 Brush（3 色 × 2 外光层 = 6 个 Brush）
    val palette3 = arrayOf(p.brandStart, p.brandMid, p.brandEnd)
    val farColors = Array(3) { c ->
        if (dark) lerp(palette3[c], Color.White, 0.72f) else lerp(palette3[c], p.ruby, 0.35f)
    }
    val nearColors = Array(3) { c ->
        if (dark) lerp(palette3[c], Color.White, 0.48f) else lerp(palette3[c], p.ruby, 0.35f)
    }
    val glowBrushes = Array(3) { c ->
        val col = nearColors[c]
        Brush.radialGradient(
            0.0f to col.copy(alpha = 1f),
            1.0f to col.copy(alpha = 0f),
            center = Offset.Zero,
            radius = GLOW_BASE_RADIUS,
        )
    }
    val crossStrokeWidth = (1.1f * unit).coerceAtLeast(0.9f)

    // 3. 流星渐变 Brush（沿本地 X 轴从 -meteorLen 到 0，绘制时 translate + rotate）
    val meteorDx = w * 0.24f
    val meteorDy = h * 0.22f
    val meteorTailLen = hypot(meteorDx * 0.28f, meteorDy * 0.28f).coerceAtLeast(1f)
    val meteorAngleDeg = (atan2(meteorDy.toDouble(), meteorDx.toDouble()) * 180.0 / PI).toFloat()
    val meteorCol = if (dark) lerp(p.brandStart, Color.White, 0.60f) else p.brandMid
    val meteorHeadCol = if (dark) Color.White else p.ruby
    val meteorBrush = Brush.linearGradient(
        0.00f to meteorCol.copy(alpha = 0f),
        0.75f to meteorCol.copy(alpha = 0.55f),
        1.00f to meteorHeadCol.copy(alpha = 0.92f),
        start = Offset(-meteorTailLen, 0f),
        end = Offset.Zero,
    )
    val meteorStrokeWidth = (2.0f * unit).coerceAtLeast(1.2f)

    // 4. 顶底安全渐隐 Veil
    val veilScale = if (dark) 1f else 1.05f
    val veil = Brush.verticalGradient(
        0.00f to bg.copy(alpha = (0.42f * veilScale).coerceAtMost(0.80f)),
        0.22f to bg.copy(alpha = 0f),
        0.78f to bg.copy(alpha = 0f),
        1.00f to bg.copy(alpha = (0.48f * veilScale).coerceAtMost(0.85f)),
    )

    // 星云呼吸角速度（对齐 1200s 整数周）
    val neb1OmegaX = 27f * TWO_PI / BG_TIME_WRAP_SEC // ~0.141 rad/s
    val neb1OmegaY = 21f * TWO_PI / BG_TIME_WRAP_SEC // ~0.110 rad/s
    val neb2OmegaX = 23f * TWO_PI / BG_TIME_WRAP_SEC // ~0.120 rad/s
    val neb2OmegaY = 31f * TWO_PI / BG_TIME_WRAP_SEC // ~0.162 rad/s

    onDrawBehind {
        if (w <= 1f || h <= 1f) return@onDrawBehind
        val t = time()

        // 星云 1 & 2
        val neb1X = w * (0.26f + sin(t * neb1OmegaX) * 0.05f)
        val neb1Y = h * (0.34f + cos(t * neb1OmegaY) * 0.04f)
        translate(neb1X, neb1Y) {
            drawCircle(brush = neb1Brush, radius = neb1R, center = Offset.Zero)
        }
        val neb2X = w * (0.76f + cos(t * neb2OmegaX) * 0.05f)
        val neb2Y = h * (0.66f + sin(t * neb2OmegaY) * 0.05f)
        translate(neb2X, neb2Y) {
            drawCircle(brush = neb2Brush, radius = neb2R, center = Offset.Zero)
        }

        // 三层视差星点
        val darkScale = if (dark) 1f else 0.72f
        for (i in Stars.indices) {
            val s = Stars[i]
            val rawX = (s.x0 + t * s.xSpeed) % STAR_WRAP
            val sx = (rawX - STAR_PAD) * w
            var rawY = (s.y0 - t * s.ySpeed) % STAR_WRAP
            if (rawY < 0f) rawY += STAR_WRAP
            val sy = (rawY - STAR_PAD) * h

            val twinkle = 0.55f + 0.45f * sin(t * s.twinkleOmega + s.phase)
            val r = (s.size * unit).coerceAtLeast(0.85f)
            val baseCol = if (s.layer == 0) farColors[s.colorPick] else nearColors[s.colorPick]
            val layerAlpha = when (s.layer) {
                0 -> 0.42f
                1 -> 0.68f
                else -> 0.88f
            }
            val alpha = (layerAlpha * twinkle * darkScale).coerceIn(0f, 1f)

            // 外柔光（中/近景星点）
            if (s.layer >= 1) {
                val glowR = r * (if (s.isCross) 4.8f else 3.0f)
                val glowScale = glowR / GLOW_BASE_RADIUS
                translate(sx, sy) {
                    scale(glowScale, glowScale, pivot = Offset.Zero) {
                        drawCircle(
                            brush = glowBrushes[s.colorPick],
                            radius = GLOW_BASE_RADIUS,
                            center = Offset.Zero,
                            alpha = (alpha * 0.48f).coerceIn(0f, 1f),
                        )
                    }
                }
            }

            // 十字星芒（近景精选亮星）
            if (s.isCross) {
                val arm = r * (3.2f + 1.4f * twinkle)
                val crossAlpha = (alpha * 0.85f).coerceIn(0f, 1f)
                drawLine(
                    color = baseCol,
                    start = Offset(sx - arm, sy),
                    end = Offset(sx + arm, sy),
                    strokeWidth = crossStrokeWidth,
                    cap = StrokeCap.Round,
                    alpha = crossAlpha,
                )
                drawLine(
                    color = baseCol,
                    start = Offset(sx, sy - arm),
                    end = Offset(sx, sy + arm),
                    strokeWidth = crossStrokeWidth,
                    cap = StrokeCap.Round,
                    alpha = crossAlpha,
                )
            }

            // 星核
            drawCircle(
                color = if (dark) StarCoreDark else baseCol,
                radius = r,
                center = Offset(sx, sy),
                alpha = alpha,
            )
        }

        // 偶发斜向流星（每 6 秒划过一次）
        val shifted = t + METEOR_PHASE_OFFSET_SEC
        val mLocal = shifted % METEOR_CYCLE_SEC
        if (mLocal < METEOR_DURATION_SEC) {
            val progress = mLocal / METEOR_DURATION_SEC
            val ease = 1f - (1f - progress).pow(2)
            val fade = sin(progress * PI.toFloat()).coerceIn(0f, 1f)
            val idx = floor((shifted / METEOR_CYCLE_SEC).toDouble())
            val startX = w * (0.28f + starHash(idx * 13.7) * 0.55f)
            val startY = h * (0.08f + starHash(idx * 19.3) * 0.25f)
            val headX = startX + meteorDx * ease
            val headY = startY + meteorDy * ease

            translate(headX, headY) {
                rotate(degrees = meteorAngleDeg, pivot = Offset.Zero) {
                    drawLine(
                        brush = meteorBrush,
                        start = Offset(-meteorTailLen, 0f),
                        end = Offset.Zero,
                        strokeWidth = meteorStrokeWidth,
                        cap = StrokeCap.Round,
                        alpha = fade,
                    )
                }
            }
        }

        // 顶底安全渐隐遮罩
        drawRect(brush = veil)
    }
}
