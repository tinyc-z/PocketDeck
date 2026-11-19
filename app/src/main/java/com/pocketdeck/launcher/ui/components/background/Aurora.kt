package com.pocketdeck.launcher.ui.components.background

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.pocketdeck.launcher.ui.theme.DeckPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (PI * 2).toFloat()
private const val RIBBON_STEPS = 20

/** 极光丝带：每条丝带的基线位置与速度（每 [BG_TIME_WRAP_SEC] 秒的周期数，整数保证无缝回绕）。 */
private val RibbonBase = floatArrayOf(0.32f, 0.52f, 0.70f)
private val RibbonCycles = intArrayOf(40, 55, 65) // 5 的倍数：0.6x 谐波同样无缝

/**
 * Aurora：三条柔和的品牌色丝带缓慢起伏（保留原效果，改为主题品牌渐变配色）。
 * Path 复用、Brush 缓存，逐帧零分配。
 */
internal fun Modifier.aurora(p: DeckPalette, time: () -> Float): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val dark = p.isDark
    val bg = p.background
    val start = p.brandStart
    val mid = p.brandMid
    val end = p.brandEnd
    val ribbonPath = Path()
    val veil = Brush.verticalGradient(
        0.0f to bg.copy(alpha = if (dark) 0.50f else 0.60f),
        0.24f to bg.copy(alpha = 0f),
        0.82f to bg.copy(alpha = 0f),
        1.0f to bg.copy(alpha = if (dark) 0.56f else 0.66f),
    )
    val pairs = arrayOf(
        start.copy(alpha = if (dark) 0.26f else 0.22f) to mid.copy(alpha = if (dark) 0.20f else 0.17f),
        mid.copy(alpha = if (dark) 0.22f else 0.18f) to end.copy(alpha = if (dark) 0.16f else 0.12f),
        end.copy(alpha = if (dark) 0.26f else 0.20f) to start.copy(alpha = if (dark) 0.16f else 0.13f),
    )
    val bandBrushes = Array(3) { b ->
        val (c1, c2) = pairs[b]
        Brush.horizontalGradient(
            0.0f to c1.copy(alpha = 0f),
            0.20f to c1,
            0.55f to c2,
            0.85f to c1.copy(alpha = c1.alpha * 0.65f),
            1.0f to c2.copy(alpha = 0f),
        )
    }
    val hl = lerp(mid, Color.White, if (dark) 0.55f else 0.35f).copy(alpha = if (dark) 0.20f else 0.18f)
    val highlightBrush = Brush.horizontalGradient(
        0.0f to hl.copy(alpha = 0f),
        0.28f to hl,
        0.72f to hl.copy(alpha = hl.alpha * 0.7f),
        1.0f to hl.copy(alpha = 0f),
    )
    val stroke = Stroke(width = (1.4f * w / BG_REF_WIDTH_DP).coerceAtLeast(1f), cap = StrokeCap.Round)

    onDrawBehind {
        if (w <= 1f || h <= 1f) return@onDrawBehind
        val tn = time() / BG_TIME_WRAP_SEC * TWO_PI
        for (b in 0..2) {
            val baseFrac = RibbonBase[b]
            val ph = tn * RibbonCycles[b]
            val drift = tn * (RibbonCycles[b] / 3)
            val thickness = h * (0.20f - b * 0.025f)
            val tilt = h * (0.14f - b * 0.04f)
            ribbonPath.rewind()

            var prevX = 0f
            var prevY = spineY(0f, ph, drift, b, h, baseFrac, tilt) - thickness * 0.5f
            ribbonPath.moveTo(prevX, prevY)
            for (i in 1..RIBBON_STEPS) {
                val frac = i.toFloat() / RIBBON_STEPS
                val x = w * frac
                val y = spineY(frac, ph, drift, b, h, baseFrac, tilt) -
                    thickness * (0.38f + 0.24f * sin(frac * PI.toFloat() + b))
                ribbonPath.quadraticTo(prevX, prevY, (prevX + x) * 0.5f, (prevY + y) * 0.5f)
                prevX = x
                prevY = y
            }
            ribbonPath.lineTo(w, prevY)

            var prevBx = w
            var prevBy = spineY(1f, ph, drift, b, h, baseFrac, tilt) + thickness * 0.5f
            ribbonPath.lineTo(prevBx, prevBy)
            for (i in RIBBON_STEPS - 1 downTo 0) {
                val frac = i.toFloat() / RIBBON_STEPS
                val x = w * frac
                val y = spineY(frac, ph, drift, b, h, baseFrac, tilt) +
                    thickness * (0.42f + 0.20f * cos(frac * PI.toFloat() + b))
                ribbonPath.quadraticTo(prevBx, prevBy, (prevBx + x) * 0.5f, (prevBy + y) * 0.5f)
                prevBx = x
                prevBy = y
            }
            ribbonPath.close()
            drawPath(ribbonPath, brush = bandBrushes[b])
            if (b == 2) drawPath(ribbonPath, brush = highlightBrush, style = stroke)
        }
        drawRect(veil)
    }
}

private fun spineY(frac: Float, ph: Float, drift: Float, b: Int, h: Float, baseFrac: Float, tilt: Float): Float {
    val angle = ph + frac * 4.2f + b * 1.9f
    val wave = sin(angle) * h * 0.055f + cos(angle * 0.6f - drift) * h * 0.028f
    return h * baseFrac + (frac - 0.5f) * -tilt + wave
}
