package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.pocketdeck.launcher.data.BackgroundStyle
import com.pocketdeck.launcher.ui.components.background.BG_STEP_SLOW
import com.pocketdeck.launcher.ui.components.background.CoverGlowBackground
import com.pocketdeck.launcher.ui.components.background.SampleCoverColors
import com.pocketdeck.launcher.ui.components.background.aurora
import com.pocketdeck.launcher.ui.components.background.coverGlow
import com.pocketdeck.launcher.ui.components.background.pixelDrift
import com.pocketdeck.launcher.ui.components.background.rememberBackgroundClock
import com.pocketdeck.launcher.ui.components.background.retroGrid
import com.pocketdeck.launcher.ui.components.background.styleCoverColors
import com.pocketdeck.launcher.ui.components.background.sunsetFlow
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import java.io.File

/**
 * 主页背景效果（Sunset Arcade）。所有颜色均取自当前主题色板，自动适配浅/深色。
 *
 * - SunsetFlow：品牌色柔光斑缓慢漂移（默认）
 * - PixelDrift：像素游戏元素上浮
 * - RetroGrid：合成波落日 + 透视网格
 * - CoverGlow：取焦点封面主色的氛围光
 * - Aurora：品牌色极光丝带
 * - Image：用户自选静态图片
 * - None：纯色背景（由外层 Box 提供）
 *
 * 动画效果共用一个时间源，只在绘制阶段读取（不触发重组）；[paused] 时冻结，宿主不可见时停止请求帧。
 * 本组件不绘制底色，底色由外层容器提供。
 */
@Composable
fun WaveBackground(
    modifier: Modifier = Modifier,
    style: BackgroundStyle = BackgroundStyle.SunsetFlow,
    coverCandidates: List<Any> = emptyList(),
    knownCoverIndex: Int? = null,
    apkPackage: String? = null,
    customBackground: String? = null,
    paused: Boolean = false,
) {
    val animated = when (style) {
        BackgroundStyle.Image, BackgroundStyle.None -> false
        else -> true
    }
    // 网格横线靠近观察者时移动较快，需要逐帧推进；其余慢速效果 30Hz 足够平滑
    val step = if (style == BackgroundStyle.RetroGrid) 0f else BG_STEP_SLOW
    val clock = rememberBackgroundClock(paused = paused || !animated, stepSec = step)
    val time = remember(clock) { { clock.floatValue } }
    val p = LocalDeckColors.current

    when (style) {
        BackgroundStyle.None -> Unit
        BackgroundStyle.SunsetFlow -> Spacer(modifier.fillMaxSize().sunsetFlow(p, time))
        BackgroundStyle.PixelDrift -> Spacer(modifier.fillMaxSize().pixelDrift(p, time))
        BackgroundStyle.RetroGrid -> Spacer(modifier.fillMaxSize().retroGrid(p, time))
        BackgroundStyle.Aurora -> Spacer(modifier.fillMaxSize().aurora(p, time))
        BackgroundStyle.CoverGlow -> CoverGlowBackground(
            modifier = modifier,
            coverCandidates = coverCandidates,
            knownCoverIndex = knownCoverIndex,
            apkPackage = apkPackage,
            time = time,
        )
        BackgroundStyle.Image -> CustomImageEffect(modifier, customBackground)
    }
}

/**
 * 背景样式缩略图（设置页背景选择器用，约 120x90dp）。静态绘制，包含底色。
 * 尺寸按画布宽度等比换算，与全屏效果构图一致。
 */
@Composable
fun BackgroundPreview(style: BackgroundStyle, modifier: Modifier = Modifier) {
    val p = LocalDeckColors.current
    val base = modifier.background(p.background)
    when (style) {
        BackgroundStyle.SunsetFlow -> Spacer(base.sunsetFlow(p) { PREVIEW_T })
        BackgroundStyle.PixelDrift -> Spacer(base.pixelDrift(p) { PREVIEW_T })
        BackgroundStyle.RetroGrid -> Spacer(base.retroGrid(p) { 0.6f })
        BackgroundStyle.Aurora -> Spacer(base.aurora(p) { PREVIEW_T })
        BackgroundStyle.CoverGlow -> {
            val colors = remember(p.isDark) { styleCoverColors(SampleCoverColors, p.isDark) }
            Spacer(base.coverGlow(p, colors, colors, progress = { 1f }, time = { PREVIEW_T }))
        }
        BackgroundStyle.Image -> Spacer(base.pictureGlyph(p.textSecondary.copy(alpha = 0.75f), p.brandMid))
        BackgroundStyle.None -> Spacer(base)
    }
}

/** 缩略图使用的固定时刻（选一个光斑分布均衡的瞬间）。 */
private const val PREVIEW_T = 6f

/** 「图片」字形：圆角框 + 山峦 + 太阳。 */
private fun Modifier.pictureGlyph(ink: Color, accent: Color): Modifier = drawWithCache {
    val s = minOf(size.width, size.height) * 0.42f
    val left = (size.width - s * 1.25f) / 2f
    val top = (size.height - s) / 2f
    val fw = s * 1.25f
    val strokeW = (s * 0.075f).coerceAtLeast(1f)
    val stroke = Stroke(width = strokeW, join = StrokeJoin.Round)
    val mountains = Path().apply {
        moveTo(left + fw * 0.10f, top + s * 0.86f)
        lineTo(left + fw * 0.38f, top + s * 0.48f)
        lineTo(left + fw * 0.56f, top + s * 0.70f)
        lineTo(left + fw * 0.70f, top + s * 0.56f)
        lineTo(left + fw * 0.90f, top + s * 0.86f)
        close()
    }
    onDrawBehind {
        drawRoundRect(
            color = ink,
            topLeft = Offset(left, top),
            size = Size(fw, s),
            cornerRadius = CornerRadius(s * 0.16f),
            style = stroke,
        )
        drawPath(mountains, color = ink)
        drawCircle(color = accent, radius = s * 0.10f, center = Offset(left + fw * 0.72f, top + s * 0.28f))
    }
}

// ==================== 自定义静态图片背景 ====================

@Composable
private fun CustomImageEffect(modifier: Modifier, imagePath: String?) {
    val context = LocalContext.current
    val p = LocalDeckColors.current
    val bgColor = p.background
    val isDark = p.isDark
    val file = remember(imagePath) {
        imagePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }
    val request = remember(file?.path, file?.lastModified()) {
        file?.let { f ->
            val dm = context.resources.displayMetrics
            val maxW = dm.widthPixels.coerceIn(640, 1920)
            val maxH = dm.heightPixels.coerceIn(480, 1920)
            coil3.request.ImageRequest.Builder(context)
                .data(f)
                .size(maxW, maxH)
                .precision(coil3.size.Precision.INEXACT)
                .build()
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 轻微半透明遮罩保障顶栏/底栏与文字可读性
            Box(
                Modifier
                    .fillMaxSize()
                    .background(bgColor.copy(alpha = if (isDark) 0.32f else 0.22f)),
            )
        }
    }
}
