package com.pocketdeck.launcher.ui.components.background

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import com.pocketdeck.launcher.ui.theme.DeckPalette
import kotlin.math.sqrt

/** 地平线位置（画布高度比例）。卡片区位于其上方，地面网格不会干扰封面与文字。 */
private const val HORIZON = 0.60f

/** 网格每前进一格的时间（秒），为 [BG_TIME_WRAP_SEC] 的约数。 */
private const val GRID_STEP_SEC = 2.5f

private const val H_LINES = 16
private const val V_LINES_HALF = 7

/**
 * Retro Grid：合成波场景。天空渐变 + 条纹落日（品牌渐变，条纹为镂空）+ 透视网格地面，
 * 横线朝观察者滚动，纵线汇聚于消失点。浅色模式为暖白天空 + 低透明度粉紫网格。
 */
internal fun Modifier.retroGrid(p: DeckPalette, time: () -> Float): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val unit = w / BG_REF_WIDTH_DP
    val dark = p.isDark
    val bg = p.background
    val violet = Color(0xFF7C4DCC)
    val horizonY = h * HORIZON
    val floorH = (h - horizonY).coerceAtLeast(1f)

    // ── 天空 ──
    val skyBrush = if (dark) {
        Brush.verticalGradient(
            0f to lerp(bg, violet, 0.10f),
            0.70f to lerp(bg, lerp(violet, p.brandEnd, 0.40f), 0.30f),
            1f to lerp(bg, p.brandEnd, 0.42f),
            startY = 0f,
            endY = horizonY,
        )
    } else {
        Brush.verticalGradient(
            0f to bg,
            0.65f to lerp(bg, p.brandStart, 0.08f),
            1f to lerp(bg, p.brandMid, 0.20f),
            startY = 0f,
            endY = horizonY,
        )
    }
    // ── 地面 ──
    val floorTop = if (dark) lerp(bg, violet, 0.12f) else lerp(bg, p.brandMid, 0.07f)
    val floorBrush = Brush.verticalGradient(
        0f to floorTop,
        1f to if (dark) lerp(bg, violet, 0.04f) else bg,
        startY = horizonY,
        endY = h,
    )

    // ── 落日（镂空条纹，仅地平线以上）──
    val sunR = w * 0.17f
    val sunC = Offset(w * 0.66f, horizonY + sunR * 0.08f)
    val sunPath = run {
        val disc = Path().apply { addOval(Rect(sunC, sunR)) }
        val cut = Path()
        for (k in 0 until 4) {
            val top = horizonY - sunR * (0.70f - 0.16f * k)
            val hh = sunR * (0.035f + 0.025f * k)
            cut.addRect(Rect(sunC.x - sunR - 2f, top, sunC.x + sunR + 2f, top + hh))
        }
        // 地平线以下的部分也挖掉
        cut.addRect(Rect(0f, horizonY, w, h))
        Path().apply { op(disc, cut, PathOperation.Difference) }
    }
    val sunTop = if (dark) lerp(p.brandStart, Color(0xFFFFE08A), 0.35f) else lerp(p.brandStart, Color.White, 0.30f)
    val sunBottom = if (dark) p.brandEnd else lerp(p.brandEnd, Color.White, 0.25f)
    val sunAlpha = if (dark) 0.88f else 0.50f
    val sunBrush = Brush.verticalGradient(
        0f to sunTop,
        1f to sunBottom,
        startY = sunC.y - sunR,
        endY = horizonY,
    )
    val glowColor = p.brandMid.copy(alpha = if (dark) 0.22f else 0.16f)
    val sunGlow = Brush.radialGradient(
        0f to glowColor,
        0.5f to glowColor.copy(alpha = glowColor.alpha * 0.4f),
        1f to glowColor.copy(alpha = 0f),
        center = sunC,
        radius = sunR * 2.4f,
    )
    // 地平线辉光带
    val horizonGlowColor = if (dark) p.brandEnd.copy(alpha = 0.30f) else p.brandMid.copy(alpha = 0.14f)
    val horizonGlow = Brush.verticalGradient(
        0f to horizonGlowColor.copy(alpha = 0f),
        0.5f to horizonGlowColor,
        1f to horizonGlowColor.copy(alpha = 0f),
        startY = horizonY - 18f * unit,
        endY = horizonY + 18f * unit,
    )

    // ── 网格线 ──
    val lineColor = if (dark) lerp(p.brandEnd, Color.White, 0.25f) else lerp(p.brandMid, violet, 0.30f)
    val lineAlpha = if (dark) 0.55f else 0.26f
    val stroke = (1.3f * unit).coerceAtLeast(1f)
    val vBrush = Brush.verticalGradient(
        0f to lineColor.copy(alpha = 0f),
        0.25f to lineColor.copy(alpha = lineAlpha * 0.5f),
        1f to lineColor.copy(alpha = lineAlpha),
        startY = horizonY,
        endY = h,
    )
    val vpX = w * 0.5f
    val vSpacing = w * 0.13f
    // 底部可读性遮罩（底栏按钮提示在这里）
    val bottomVeil = Brush.verticalGradient(
        0f to bg.copy(alpha = 0f),
        1f to bg.copy(alpha = if (dark) 0.55f else 0.60f),
        startY = h * 0.84f,
        endY = h,
    )
    val skySize = Size(w, horizonY)

    onDrawBehind {
        if (w <= 1f || h <= 1f) return@onDrawBehind
        drawRect(skyBrush, size = skySize)
        drawRect(floorBrush, topLeft = Offset(0f, horizonY), size = Size(w, floorH))
        drawRect(sunGlow, size = skySize)
        drawPath(sunPath, brush = sunBrush, alpha = sunAlpha)
        drawRect(horizonGlow, topLeft = Offset(0f, horizonY - 18f * unit), size = Size(w, 36f * unit))

        // 纵线：汇聚到消失点
        for (j in -V_LINES_HALF..V_LINES_HALF) {
            drawLine(
                brush = vBrush,
                start = Offset(vpX, horizonY),
                end = Offset(vpX + j * vSpacing * 2.2f, h),
                strokeWidth = stroke,
            )
        }
        // 横线：透视投影 y = horizon + floorH / z，z 随时间减小 → 朝观察者滚动
        val phase = (time() / GRID_STEP_SEC) % 1f
        for (i in 0 until H_LINES) {
            val z = i + 1f - phase
            if (z <= 0.0001f) continue
            val y = horizonY + floorH / z
            if (y > h + stroke) continue
            val depth = ((y - horizonY) / floorH).coerceIn(0f, 1f)
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = stroke,
                cap = StrokeCap.Butt,
                alpha = lineAlpha * (0.15f + 0.85f * sqrt(depth)),
            )
        }
        drawLine(
            color = lineColor,
            start = Offset(0f, horizonY),
            end = Offset(w, horizonY),
            strokeWidth = stroke,
            alpha = lineAlpha * 0.9f,
        )
        drawRect(bottomVeil, topLeft = Offset(0f, h * 0.84f), size = Size(w, h * 0.16f))
    }
}
