package com.pocketdeck.launcher.ui.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size as CoilSize
import coil3.transform.Transformation
import com.pocketdeck.launcher.data.BackgroundStyle
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.ui.theme.DeckColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 多种背景效果组件。
 *
 * - Waves: 三层波浪动画（原始风格）
 * - Gradient: 彩色渐变缓慢流动
 * - Particles: 漂浮粒子效果
 * - CoverBlur: 选中封面的毛玻璃放大背景
 * - Static: 静态波浪
 * - Image: 用户自选静态图片
 * - None: 纯色背景
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
    when (style) {
        BackgroundStyle.None -> { /* 纯色背景，由外层 Box 提供 */ }
        // TODO(redesign): 临时映射，由背景动画重写替换
        BackgroundStyle.SunsetFlow -> GradientEffect(modifier, paused = paused)
        BackgroundStyle.PixelDrift -> ParticlesEffect(modifier, paused = paused)
        BackgroundStyle.RetroGrid -> TopoEffect(modifier, paused = paused)
        BackgroundStyle.Aurora -> AuroraEffect(modifier, paused = paused)
        BackgroundStyle.CoverGlow -> CoverBlurEffect(
            modifier = modifier,
            coverCandidates = coverCandidates,
            knownCoverIndex = knownCoverIndex,
            apkPackage = apkPackage,
        )
        BackgroundStyle.Image -> CustomImageEffect(modifier, customBackground)
    }
}

// ==================== 波浪效果 ====================

private const val BG_FRAME_STEP_SEC = 0.032f

@Composable
private fun WavesEffect(modifier: Modifier, animated: Boolean, paused: Boolean) {
    var elapsedSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated, paused) {
        if (!animated || paused) return@LaunchedEffect
        var lastNanos = 0L
        var accum = 0f
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    accum += dt
                    if (accum >= BG_FRAME_STEP_SEC) {
                        elapsedSec += accum
                        accum = 0f
                    }
                }
                lastNanos = now
            }
        }
    }

    val isDark = DeckColors.isDark
    val bgColor = DeckColors.Background
    val primary = DeckColors.Ruby
    val rubyLight = DeckColors.RubyLight
    val glow = DeckColors.AccentGlow
    val tertiary = DeckColors.AccentTertiary
    val waveLayers = remember(animated, isDark, primary, rubyLight, glow, tertiary) {
        val alphaBoost = if (animated) 0f else 0.02f
        val rimMid = if (isDark) lerp(glow, Color.White, 0.48f).copy(alpha = 0.24f) else lerp(primary, Color.White, 0.32f).copy(alpha = 0.18f)
        val rimFront = if (isDark) lerp(primary, Color.White, 0.58f).copy(alpha = 0.38f) else lerp(primary, Color.White, 0.32f).copy(alpha = 0.28f)
        listOf(
            WaveLayer(
                fillTopColor = tertiary.copy(alpha = (if (isDark) 0.13f else 0.09f) + alphaBoost),
                fillMidColor = rubyLight.copy(alpha = (if (isDark) 0.13f else 0.09f) + alphaBoost),
                crestEdgeColor = null,
                crestPeakColor = null,
                amplitude = if (animated) 0.032f else 0.038f,
                frequency = 0.85f,
                speedRad = 0.28f,
                phaseOffset = 0.4f,
                yOffset = 0.00f,
                harmonicRatio = 0.14f,
            ),
            WaveLayer(
                fillTopColor = glow.copy(alpha = (if (isDark) 0.17f else 0.12f) + alphaBoost),
                fillMidColor = primary.copy(alpha = (if (isDark) 0.17f else 0.12f) + alphaBoost),
                crestEdgeColor = glow.copy(alpha = if (isDark) 0.14f else 0.10f),
                crestPeakColor = rimMid,
                amplitude = if (animated) 0.038f else 0.044f,
                frequency = 1.05f,
                speedRad = -0.36f,
                phaseOffset = 2.2f,
                yOffset = 0.065f,
                harmonicRatio = 0.18f,
            ),
            WaveLayer(
                fillTopColor = primary.copy(alpha = (if (isDark) 0.21f else 0.14f) + alphaBoost),
                fillMidColor = lerp(primary, glow, 0.35f).copy(alpha = (if (isDark) 0.21f else 0.14f) + alphaBoost),
                crestEdgeColor = primary.copy(alpha = if (isDark) 0.18f else 0.13f),
                crestPeakColor = rimFront,
                amplitude = if (animated) 0.030f else 0.036f,
                frequency = 0.78f,
                speedRad = 0.44f,
                phaseOffset = 4.1f,
                yOffset = 0.13f,
                harmonicRatio = 0.16f,
            ),
        )
    }
    val fillPath = remember { Path() }
    val crestPath = remember { Path() }

    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val midY = h * 0.46f
                val fadeBottom = h * 0.96f
                val ambientWashBrush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = if (isDark) 0.11f else 0.06f),
                        primary.copy(alpha = if (isDark) 0.04f else 0.02f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.82f, h * 0.18f),
                    radius = (w * 0.72f).coerceAtLeast(1f),
                )
                val layerBrushes = waveLayers.map { layer ->
                    val layerTopY = (midY + h * (layer.yOffset - layer.amplitude * 1.4f)).coerceAtLeast(0f)
                    Brush.verticalGradient(
                        0f to layer.fillTopColor,
                        0.55f to layer.fillMidColor.copy(alpha = layer.fillMidColor.alpha * 0.52f),
                        1f to Color.Transparent,
                        startY = layerTopY,
                        endY = fadeBottom.coerceAtLeast(layerTopY + 1f),
                    )
                }
                val crestBrushes = waveLayers.map { layer ->
                    val peak = layer.crestPeakColor
                    val edge = layer.crestEdgeColor
                    if (peak != null && edge != null) {
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.18f to edge,
                            0.55f to peak,
                            0.85f to edge,
                            1f to Color.Transparent,
                            startX = 0f,
                            endX = w.coerceAtLeast(1f),
                        )
                    } else {
                        null
                    }
                }
                val bottomFadeBrush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to bgColor.copy(alpha = if (isDark) 0.88f else 0.90f),
                    startY = h * 0.76f,
                    endY = h.coerceAtLeast(1f),
                )
                val rimStroke = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round)
                onDrawBehind {
                    if (w <= 1f || h <= 1f) return@onDrawBehind
                    drawRect(ambientWashBrush)
                    val t = if (animated) elapsedSec else 2.35f
                    waveLayers.forEachIndexed { idx, layer ->
                        val hasCrest = crestBrushes[idx] != null
                        buildWavePaths(fillPath, if (hasCrest) crestPath else null, w, h, midY, t, layer)
                        drawPath(fillPath, layerBrushes[idx])
                        crestBrushes[idx]?.let { cb ->
                            drawPath(crestPath, cb, style = rimStroke)
                        }
                    }
                    drawRect(bottomFadeBrush)
                }
            },
    )
}

// ==================== 渐变流动效果 ====================

@Composable
private fun GradientEffect(modifier: Modifier, paused: Boolean) {
    var elapsedSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var lastNanos = 0L
        var accum = 0f
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    accum += dt
                    if (accum >= BG_FRAME_STEP_SEC) {
                        elapsedSec += accum
                        accum = 0f
                    }
                }
                lastNanos = now
            }
        }
    }

    val isDark = DeckColors.isDark
    val bgColor = DeckColors.Background
    val primary = DeckColors.Ruby
    val glow = DeckColors.AccentGlow
    val tertiary = DeckColors.AccentTertiary
    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                fun poolStops(c: Color, peak: Float): List<Color> = listOf(
                    c.copy(alpha = peak),
                    c.copy(alpha = peak * 0.52f),
                    c.copy(alpha = peak * 0.16f),
                    Color.Transparent,
                )

                val baseTintBrush = Brush.linearGradient(
                    0f to primary.copy(alpha = if (isDark) 0.08f else 0.05f),
                    0.5f to glow.copy(alpha = if (isDark) 0.05f else 0.03f),
                    1f to tertiary.copy(alpha = if (isDark) 0.03f else 0.02f),
                    start = Offset.Zero,
                    end = Offset(w.coerceAtLeast(1f), h.coerceAtLeast(1f)),
                )
                val pool1Stops = poolStops(primary, if (isDark) 0.30f else 0.17f)
                val pool2Stops = poolStops(glow, if (isDark) 0.24f else 0.14f)
                val pool3Stops = poolStops(tertiary, if (isDark) 0.18f else 0.11f)
                val pool4Stops = poolStops(lerp(primary, tertiary, 0.45f), if (isDark) 0.19f else 0.11f)

                val topVeilBrush = Brush.verticalGradient(
                    0f to bgColor.copy(alpha = 0.24f),
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = (h * 0.14f).coerceAtLeast(1f),
                )
                val bottomVeilBrush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to bgColor.copy(alpha = if (isDark) 0.52f else 0.42f),
                    startY = h * 0.78f,
                    endY = h.coerceAtLeast(1f),
                )
                onDrawBehind {
                    if (w <= 1f || h <= 1f) return@onDrawBehind
                    drawRect(baseTintBrush)

                    val t = elapsedSec
                    val c1 = Offset(
                        w * (0.76f + 0.09f * cos((t * 0.29f).toDouble()).toFloat()),
                        h * (0.24f + 0.08f * sin((t * 0.23f).toDouble()).toFloat()),
                    )
                    val r1 = (w * (0.58f + 0.06f * sin((t * 0.45f).toDouble()).toFloat())).coerceAtLeast(1f)
                    drawCircle(
                        brush = Brush.radialGradient(colors = pool1Stops, center = c1, radius = r1),
                        radius = r1,
                        center = c1,
                    )

                    val c2 = Offset(
                        w * (0.22f + 0.10f * sin((t * 0.25f + 1.4f).toDouble()).toFloat()),
                        h * (0.74f + 0.08f * cos((t * 0.31f + 0.5f).toDouble()).toFloat()),
                    )
                    val r2 = (w * (0.56f + 0.05f * cos((t * 0.38f).toDouble()).toFloat())).coerceAtLeast(1f)
                    drawCircle(
                        brush = Brush.radialGradient(colors = pool2Stops, center = c2, radius = r2),
                        radius = r2,
                        center = c2,
                    )

                    val c3 = Offset(
                        w * (0.26f + 0.08f * cos((t * 0.21f + 2.6f).toDouble()).toFloat()),
                        h * (0.22f + 0.07f * sin((t * 0.27f + 0.9f).toDouble()).toFloat()),
                    )
                    val r3 = (w * (0.48f + 0.04f * sin((t * 0.31f + 1.2f).toDouble()).toFloat())).coerceAtLeast(1f)
                    drawCircle(
                        brush = Brush.radialGradient(colors = pool3Stops, center = c3, radius = r3),
                        radius = r3,
                        center = c3,
                    )

                    val c4 = Offset(
                        w * (0.78f + 0.09f * sin((t * 0.22f + 3.8f).toDouble()).toFloat()),
                        h * (0.78f + 0.07f * cos((t * 0.26f + 1.7f).toDouble()).toFloat()),
                    )
                    val r4 = (w * (0.50f + 0.04f * cos((t * 0.41f + 0.7f).toDouble()).toFloat())).coerceAtLeast(1f)
                    drawCircle(
                        brush = Brush.radialGradient(colors = pool4Stops, center = c4, radius = r4),
                        radius = r4,
                        center = c4,
                    )

                    drawRect(topVeilBrush)
                    drawRect(bottomVeilBrush)
                }
            },
    )
}

// ==================== 粒子效果 ====================

private data class Particle(
    val x: Float,          // 0~1 normalized
    val y: Float,          // 0~1 normalized
    val radius: Float,     // dp-like scale at 480w
    val speed: Float,      // 上浮速度
    val alpha: Float,
    val tone: Int,         // 0: Ruby, 1: Glow/Light, 2: Tertiary
    val phase: Float,
    val layer: Int,        // 0: 深层大光晕, 1: 中景磨砂圆环, 2: 前景微粒星尘
    val twinkleSpeed: Float,
)

@Composable
private fun ParticlesEffect(modifier: Modifier, paused: Boolean) {
    val particles = remember {
        val rng = Random(73)
        buildList {
            // 5 个远景深层氛围光斑
            repeat(5) { i ->
                val slotX = (i + 0.15f + rng.nextFloat() * 0.70f) / 5f
                add(
                    Particle(
                        x = slotX.coerceIn(0.08f, 0.92f),
                        y = rng.nextFloat(),
                        radius = 54f + rng.nextFloat() * 30f,
                        speed = 0.18f + rng.nextFloat() * 0.10f,
                        alpha = 0.09f + rng.nextFloat() * 0.04f,
                        tone = i % 3,
                        phase = rng.nextFloat() * 6.28f,
                        layer = 0,
                        twinkleSpeed = 0f,
                    ),
                )
            }
            // 9 个中景半透明磨砂光圈
            repeat(9) { i ->
                val slotX = (i + 0.12f + rng.nextFloat() * 0.76f) / 9f
                add(
                    Particle(
                        x = slotX.coerceIn(0.05f, 0.95f),
                        y = rng.nextFloat(),
                        radius = 12f + rng.nextFloat() * 12f,
                        speed = 0.34f + rng.nextFloat() * 0.20f,
                        alpha = 0.15f + rng.nextFloat() * 0.09f,
                        tone = i % 3,
                        phase = rng.nextFloat() * 6.28f,
                        layer = 1,
                        twinkleSpeed = 0f,
                    ),
                )
            }
            // 14 个前景轻盈微闪烁星尘
            repeat(14) { i ->
                val slotX = (i + 0.10f + rng.nextFloat() * 0.80f) / 14f
                add(
                    Particle(
                        x = slotX.coerceIn(0.03f, 0.97f),
                        y = rng.nextFloat(),
                        radius = 2.0f + rng.nextFloat() * 2.4f,
                        speed = 0.52f + rng.nextFloat() * 0.33f,
                        alpha = 0.32f + rng.nextFloat() * 0.22f,
                        tone = i % 3,
                        phase = rng.nextFloat() * 6.28f,
                        layer = 2,
                        twinkleSpeed = 1.4f + rng.nextFloat() * 1.4f,
                    ),
                )
            }
        }
    }

    var elapsedSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var lastNanos = 0L
        var accum = 0f
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    accum += dt
                    if (accum >= BG_FRAME_STEP_SEC) {
                        elapsedSec += accum
                        accum = 0f
                    }
                }
                lastNanos = now
            }
        }
    }

    val isDark = DeckColors.isDark
    val primary = DeckColors.Ruby
    val rubyLight = DeckColors.RubyLight
    val glow = DeckColors.AccentGlow
    val tertiary = DeckColors.AccentTertiary
    val bgColor = DeckColors.Background

    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val washBrush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to primary.copy(alpha = if (isDark) 0.10f else 0.06f),
                )
                val cornerGlowBrush = Brush.radialGradient(
                    colors = listOf(
                        glow.copy(alpha = if (isDark) 0.09f else 0.05f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.84f, h * 0.22f),
                    radius = (w * 0.58f).coerceAtLeast(1f),
                )
                val bottomFadeBrush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to bgColor.copy(alpha = if (isDark) 0.36f else 0.28f),
                    startY = h * 0.76f,
                    endY = h.coerceAtLeast(1f),
                )
                val rimStroke = Stroke(width = 1.0.dp.toPx())
                val lightModeScale = if (isDark) 1f else 0.80f
                val moteLift = if (isDark) 0.45f else 0f

                val particleColors = particles.map { p ->
                    val raw = when (p.tone) {
                        0 -> primary
                        1 -> if (isDark) rubyLight else glow
                        else -> tertiary
                    }
                    if (p.layer == 2 && moteLift > 0f) lerp(raw, Color.White, moteLift) else raw
                }
                val deepOrbStops = particles.mapIndexed { idx, p ->
                    if (p.layer != 0) emptyList()
                    else {
                        val c = particleColors[idx]
                        val a = p.alpha * (if (isDark) 1f else 0.70f)
                        listOf(
                            c.copy(alpha = a),
                            c.copy(alpha = a * 0.45f),
                            c.copy(alpha = a * 0.12f),
                            Color.Transparent,
                        )
                    }
                }

                onDrawBehind {
                    if (w <= 1f || h <= 1f) return@onDrawBehind
                    drawRect(washBrush)
                    drawRect(cornerGlowBrush)
                    val t = elapsedSec
                    val widthScale = w / 480f
                    particles.forEachIndexed { idx, p ->
                        val driftSpeed = if (p.layer == 0) 0.032f else if (p.layer == 1) 0.048f else 0.068f
                        val sway = if (p.layer == 0) 0.018f else 0.026f
                        val py = ((p.y - t * p.speed * driftSpeed) % 1f + 1f) % 1f
                        val px = p.x + sin((t * p.speed * 0.75f + p.phase).toDouble()).toFloat() * sway
                        val edgeFade = when {
                            py < 0.14f -> (py / 0.14f).coerceIn(0f, 1f)
                            py > 0.86f -> ((1f - py) / 0.14f).coerceIn(0f, 1f)
                            else -> 1f
                        }
                        if (edgeFade <= 0.01f) return@forEachIndexed

                        val r = (p.radius * widthScale).coerceAtLeast(1f)
                        val center = Offset(px * w, py * h)
                        val c = particleColors[idx]

                        when (p.layer) {
                            0 -> {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = deepOrbStops[idx],
                                        center = center,
                                        radius = r * 1.45f,
                                    ),
                                    radius = r * 1.45f,
                                    center = center,
                                    alpha = edgeFade,
                                )
                            }
                            1 -> {
                                val effAlpha = p.alpha * edgeFade * lightModeScale
                                drawCircle(
                                    color = c.copy(alpha = effAlpha * 0.26f),
                                    radius = r,
                                    center = center,
                                )
                                drawCircle(
                                    color = c.copy(alpha = effAlpha * 0.58f),
                                    radius = r,
                                    center = center,
                                    style = rimStroke,
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = effAlpha * (if (isDark) 0.30f else 0.42f)),
                                    radius = (r * 0.16f).coerceAtLeast(1f),
                                    center = Offset(center.x - r * 0.28f, center.y - r * 0.28f),
                                )
                            }
                            else -> {
                                val twinkle = 0.42f + 0.58f * (0.5f + 0.5f * sin((t * p.twinkleSpeed + p.phase).toDouble()).toFloat())
                                val effAlpha = p.alpha * edgeFade * twinkle * (if (isDark) 1f else 0.92f)
                                drawCircle(
                                    color = c.copy(alpha = effAlpha * 0.20f),
                                    radius = r * 2.4f,
                                    center = center,
                                )
                                drawCircle(
                                    color = c.copy(alpha = effAlpha * 0.88f),
                                    radius = r,
                                    center = center,
                                )
                            }
                        }
                    }
                    drawRect(bottomFadeBrush)
                }
            },
    )
}

// ==================== 封面毛玻璃效果 ====================

/** 兼容 API < 31 的轻量级降采样模糊变换，确保所有设备都能呈现毛玻璃效果。 */
private class SoftBlurTransformation(private val radius: Int = 14) : Transformation() {
    override val cacheKey: String = "pocketdeck_soft_blur_$radius"

    override suspend fun transform(input: Bitmap, size: CoilSize): Bitmap {
        val w = input.width.coerceAtLeast(1)
        val h = input.height.coerceAtLeast(1)
        val scale = (96f / maxOf(w, h)).coerceAtMost(1f)
        val sw = (w * scale).toInt().coerceAtLeast(16)
        val sh = (h * scale).toInt().coerceAtLeast(16)
        val small = Bitmap.createScaledBitmap(input, sw, sh, true)
            .copy(Bitmap.Config.ARGB_8888, true)
        boxBlurInPlace(small, radius.coerceIn(1, 20))
        return small
    }
}

private fun boxBlurInPlace(bmp: Bitmap, radius: Int) {
    val w = bmp.width
    val h = bmp.height
    val pixels = IntArray(w * h)
    val tmp = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    val div = radius * 2 + 1
    repeat(2) {
        // 水平模糊
        for (y in 0 until h) {
            val row = y * w
            var aSum = 0
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (i in -radius..radius) {
                val c = pixels[row + i.coerceIn(0, w - 1)]
                aSum += (c ushr 24) and 0xFF
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = ((aSum / div) shl 24) or
                    ((rSum / div) shl 16) or
                    ((gSum / div) shl 8) or
                    (bSum / div)
                val add = pixels[row + (x + radius + 1).coerceAtMost(w - 1)]
                val rem = pixels[row + (x - radius).coerceAtLeast(0)]
                aSum += ((add ushr 24) and 0xFF) - ((rem ushr 24) and 0xFF)
                rSum += ((add ushr 16) and 0xFF) - ((rem ushr 16) and 0xFF)
                gSum += ((add ushr 8) and 0xFF) - ((rem ushr 8) and 0xFF)
                bSum += (add and 0xFF) - (rem and 0xFF)
            }
        }
        // 垂直模糊
        for (x in 0 until w) {
            var aSum = 0
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (i in -radius..radius) {
                val c = tmp[i.coerceIn(0, h - 1) * w + x]
                aSum += (c ushr 24) and 0xFF
                rSum += (c ushr 16) and 0xFF
                gSum += (c ushr 8) and 0xFF
                bSum += c and 0xFF
            }
            for (y in 0 until h) {
                pixels[y * w + x] = ((aSum / div) shl 24) or
                    ((rSum / div) shl 16) or
                    ((gSum / div) shl 8) or
                    (bSum / div)
                val add = tmp[(y + radius + 1).coerceAtMost(h - 1) * w + x]
                val rem = tmp[(y - radius).coerceAtLeast(0) * w + x]
                aSum += ((add ushr 24) and 0xFF) - ((rem ushr 24) and 0xFF)
                rSum += ((add ushr 16) and 0xFF) - ((rem ushr 16) and 0xFF)
                gSum += ((add ushr 8) and 0xFF) - ((rem ushr 8) and 0xFF)
                bSum += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
}

@Composable
private fun CoverBlurEffect(
    modifier: Modifier,
    coverCandidates: List<Any>,
    knownCoverIndex: Int?,
    apkPackage: String?,
) {
    val context = LocalContext.current
    val isDark = DeckColors.isDark
    val bgColor = DeckColors.Background

    var candidateIdx by remember(coverCandidates, knownCoverIndex) {
        mutableIntStateOf(
            when {
                coverCandidates.isEmpty() -> -1
                knownCoverIndex == -1 -> -1
                knownCoverIndex != null && knownCoverIndex in coverCandidates.indices -> knownCoverIndex
                else -> 0
            },
        )
    }

    val rawModel = coverCandidates.getOrNull(candidateIdx)
    var debouncedModel by remember { mutableStateOf(rawModel) }
    LaunchedEffect(rawModel) {
        if (rawModel != debouncedModel) {
            kotlinx.coroutines.delay(140)
            debouncedModel = rawModel
        }
    }
    val currentModel = debouncedModel
    val appIconBlurred by produceState<ImageBitmap?>(null, apkPackage, currentModel) {
        value = if (currentModel != null || apkPackage == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = context.packageManager.getApplicationIcon(apkPackage)
                        .toBitmap(64, 64)
                        .copy(Bitmap.Config.ARGB_8888, true)
                    boxBlurInPlace(bmp, 8)
                    bmp.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier
            .graphicsLayer {
                scaleX = 1.12f
                scaleY = 1.12f
            }
            .blur(26.dp)
    } else {
        Modifier
    }

    val primaryColor = DeckColors.Ruby
    val isOnline by NetworkStatus.online.collectAsState()
    Box(modifier.fillMaxSize()) {
        // 无封面回退时保留极轻的主题氛围柔光，避免纯白/纯黑生硬感
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val fallbackGlow = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (isDark) 0.10f else 0.06f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.32f, size.height * 0.42f),
                        radius = (size.width * 0.72f).coerceAtLeast(1f),
                    )
                    onDrawBehind {
                        if (currentModel == null && appIconBlurred == null) {
                            drawRect(fallbackGlow)
                        }
                    }
                },
        )

        Crossfade(
            targetState = currentModel,
            animationSpec = tween(420),
            label = "cover-blur",
        ) { model ->
            if (model != null) {
                val request = remember<ImageRequest>(model, isOnline) {
                    ImageRequest.Builder(context)
                        .data(model)
                        .size(160, 160)
                        .networkCachePolicy(if (isOnline) CachePolicy.ENABLED else CachePolicy.DISABLED)
                        .allowHardware(false)
                        .transformations(SoftBlurTransformation(10))
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(blurModifier),
                    contentScale = ContentScale.Crop,
                    alpha = if (isDark) 0.72f else 0.80f,
                    onState = { st ->
                        if (st is AsyncImagePainter.State.Error && model == currentModel) {
                            if (candidateIdx + 1 < coverCandidates.size) {
                                candidateIdx += 1
                            } else {
                                candidateIdx = -1
                            }
                        }
                    },
                )
            } else {
                appIconBlurred?.let { iconBmp ->
                    Image(
                        bitmap = iconBmp,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(blurModifier),
                        contentScale = ContentScale.Crop,
                        alpha = if (isDark) 0.68f else 0.72f,
                    )
                }
            }
        }

        // 杂志级边缘渐隐可读性遮罩：保证顶栏/底栏/标题对比度清晰克制（浅色模式保留更多封面色彩透出）
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val tint = bgColor.copy(alpha = if (isDark) 0.42f else 0.22f)
                    val grad = Brush.verticalGradient(
                        0f to bgColor.copy(alpha = if (isDark) 0.45f else 0.30f),
                        0.26f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to bgColor.copy(alpha = if (isDark) 0.68f else 0.44f),
                    )
                    onDrawBehind {
                        drawRect(tint)
                        drawRect(grad)
                    }
                },
        )
    }
}

// ==================== 自定义静态图片背景 ====================

@Composable
private fun CustomImageEffect(modifier: Modifier, imagePath: String?) {
    val context = LocalContext.current
    val bgColor = DeckColors.Background
    val isDark = DeckColors.isDark
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

// ==================== 波浪工具 ====================

private data class WaveLayer(
    val fillTopColor: Color,
    val fillMidColor: Color,
    val crestEdgeColor: Color?,
    val crestPeakColor: Color?,
    val amplitude: Float,
    val frequency: Float,
    val speedRad: Float,
    val phaseOffset: Float,
    val yOffset: Float,
    val harmonicRatio: Float,
)

private fun buildWavePaths(
    fillPath: Path,
    crestPath: Path?,
    w: Float,
    h: Float,
    midY: Float,
    elapsedSec: Float,
    layer: WaveLayer,
) {
    val amp = h * layer.amplitude
    val freq = layer.frequency
    val baseY = midY + h * layer.yOffset
    val twoPi = PI.toFloat() * 2f
    val p = elapsedSec * layer.speedRad + layer.phaseOffset
    val pHarmonic = elapsedSec * layer.speedRad * 0.55f
    val hRatio = layer.harmonicRatio
    val primaryRatio = 1f - hRatio

    fun waveY(frac: Float): Float {
        val angle = p + frac * freq * twoPi
        val wave = primaryRatio * sin(angle.toDouble()).toFloat() +
            hRatio * sin((angle * 1.9f - pHarmonic).toDouble()).toFloat()
        return baseY + amp * wave
    }

    fillPath.rewind()
    crestPath?.rewind()
    val steps = 24
    var prevX = 0f
    var prevY = waveY(0f)
    fillPath.moveTo(0f, h)
    fillPath.lineTo(0f, prevY)
    crestPath?.moveTo(0f, prevY)
    for (i in 1..steps) {
        val frac = i.toFloat() / steps
        val x = w * frac
        val y = waveY(frac)
        val midX = (prevX + x) * 0.5f
        val midPtY = (prevY + y) * 0.5f
        fillPath.quadraticTo(prevX, prevY, midX, midPtY)
        crestPath?.quadraticTo(prevX, prevY, midX, midPtY)
        prevX = x
        prevY = y
    }
    fillPath.lineTo(w, prevY)
    fillPath.lineTo(w, h)
    fillPath.close()
    crestPath?.lineTo(w, prevY)
}

// ==================== 极光丝带（Aurora）====================

@Composable
private fun AuroraEffect(modifier: Modifier, paused: Boolean) {
    var elapsedSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var lastNanos = 0L
        var accum = 0f
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    accum += dt
                    if (accum >= BG_FRAME_STEP_SEC) {
                        elapsedSec += accum
                        accum = 0f
                    }
                }
                lastNanos = now
            }
        }
    }

    val isDark = DeckColors.isDark
    val bgColor = DeckColors.Background
    val primary = DeckColors.Ruby
    val glow = DeckColors.AccentGlow
    val tertiary = DeckColors.AccentTertiary
    val ribbonPath = remember { Path() }

    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val topVeil = Brush.verticalGradient(
                    0.0f to bgColor.copy(alpha = if (isDark) 0.50f else 0.72f),
                    0.24f to Color.Transparent,
                    0.82f to Color.Transparent,
                    1.0f to bgColor.copy(alpha = if (isDark) 0.56f else 0.78f),
                )
                val bandColors = listOf(
                    Triple(
                        tertiary.copy(alpha = if (isDark) 0.24f else 0.14f),
                        primary.copy(alpha = if (isDark) 0.18f else 0.11f),
                        0.32f,
                    ),
                    Triple(
                        glow.copy(alpha = if (isDark) 0.22f else 0.13f),
                        tertiary.copy(alpha = if (isDark) 0.14f else 0.08f),
                        0.52f,
                    ),
                    Triple(
                        primary.copy(alpha = if (isDark) 0.26f else 0.15f),
                        glow.copy(alpha = if (isDark) 0.15f else 0.09f),
                        0.70f,
                    ),
                )
                val bandBrushes = bandColors.map { (c1, c2, _) ->
                    Brush.horizontalGradient(
                        0.0f to Color.Transparent,
                        0.20f to c1,
                        0.55f to c2,
                        0.85f to c1.copy(alpha = c1.alpha * 0.65f),
                        1.0f to Color.Transparent,
                    )
                }
                val highlightColor = lerp(primary, Color.White, if (isDark) 0.55f else 0.35f)
                    .copy(alpha = if (isDark) 0.20f else 0.16f)
                val highlightBrush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.28f to highlightColor,
                    0.72f to highlightColor.copy(alpha = highlightColor.alpha * 0.7f),
                    1.0f to Color.Transparent,
                )
                val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)

                onDrawBehind {
                    val t = elapsedSec
                    val steps = 20
                    for (b in 0..2) {
                        val baseFrac = bandColors[b].third
                        val speed = 0.22f + b * 0.08f
                        val thickness = h * (0.20f - b * 0.025f)
                        val tilt = h * (0.14f - b * 0.04f)
                        ribbonPath.rewind()

                        fun spineY(frac: Float): Float {
                            val angle = t * speed + frac * 4.2f + b * 1.9f
                            val wave = sin(angle.toDouble()).toFloat() * h * 0.055f +
                                cos((angle * 0.6f - t * 0.14f).toDouble()).toFloat() * h * 0.028f
                            return h * baseFrac + (frac - 0.5f) * -tilt + wave
                        }

                        // 上边缘从左到右
                        var prevX = 0f
                        var prevY = spineY(0f) - thickness * 0.5f
                        ribbonPath.moveTo(prevX, prevY)
                        for (i in 1..steps) {
                            val frac = i.toFloat() / steps
                            val x = w * frac
                            val y = spineY(frac) - thickness * (0.38f + 0.24f * sin((frac * PI + b).toDouble()).toFloat())
                            ribbonPath.quadraticTo(prevX, prevY, (prevX + x) * 0.5f, (prevY + y) * 0.5f)
                            prevX = x
                            prevY = y
                        }
                        ribbonPath.lineTo(w, prevY)

                        // 下边缘从右到左闭合丝带
                        var prevBx = w
                        var prevBy = spineY(1f) + thickness * 0.5f
                        ribbonPath.lineTo(prevBx, prevBy)
                        for (i in steps - 1 downTo 0) {
                            val frac = i.toFloat() / steps
                            val x = w * frac
                            val y = spineY(frac) + thickness * (0.42f + 0.20f * cos((frac * PI + b).toDouble()).toFloat())
                            ribbonPath.quadraticTo(prevBx, prevBy, (prevBx + x) * 0.5f, (prevBy + y) * 0.5f)
                            prevBx = x
                            prevBy = y
                        }
                        ribbonPath.close()
                        drawPath(ribbonPath, brush = bandBrushes[b])
                        if (b == 2) {
                            drawPath(ribbonPath, brush = highlightBrush, style = stroke)
                        }
                    }
                    drawRect(brush = topVeil)
                }
            },
    )
}

// ==================== 等高线地形（Topographic Lines）====================

@Composable
private fun TopoEffect(modifier: Modifier, paused: Boolean) {
    var elapsedSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var lastNanos = 0L
        var accum = 0f
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    accum += dt
                    if (accum >= BG_FRAME_STEP_SEC) {
                        elapsedSec += accum
                        accum = 0f
                    }
                }
                lastNanos = now
            }
        }
    }

    val isDark = DeckColors.isDark
    val primary = DeckColors.Ruby
    val glow = DeckColors.AccentGlow
    val tertiary = DeckColors.AccentTertiary
    val linePath = remember { Path() }

    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val thinStroke = Stroke(width = 1.05.dp.toPx(), cap = StrokeCap.Round)
                val accentStroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                val ambientGlow = Brush.radialGradient(
                    0.0f to primary.copy(alpha = if (isDark) 0.14f else 0.08f),
                    0.6f to glow.copy(alpha = if (isDark) 0.05f else 0.03f),
                    1.0f to Color.Transparent,
                    center = Offset(w * 0.78f, h * 0.34f),
                    radius = maxOf(w, h) * 0.65f,
                )
                val lineCount = 9
                val lineBrushes = List(lineCount) { idx ->
                    val frac = idx.toFloat() / (lineCount - 1).coerceAtLeast(1)
                    val baseColor = when (idx % 3) {
                        0 -> primary
                        1 -> glow
                        else -> tertiary
                    }
                    val alpha = (if (isDark) 0.16f else 0.12f) + (if (idx == 4 || idx == 5) 0.08f else 0f)
                    Brush.horizontalGradient(
                        0.0f to Color.Transparent,
                        0.12f to baseColor.copy(alpha = alpha * 0.55f),
                        0.50f to baseColor.copy(alpha = alpha),
                        0.88f to baseColor.copy(alpha = alpha * 0.55f),
                        1.0f to Color.Transparent,
                    )
                }

                onDrawBehind {
                    drawRect(brush = ambientGlow)
                    val t = elapsedSec * 0.30f
                    val steps = 26
                    for (idx in 0 until lineCount) {
                        val rowFrac = 0.14f + idx * (0.74f / (lineCount - 1))
                        val baseY = h * rowFrac
                        val amp1 = h * (0.032f + (idx % 3) * 0.010f)
                        val amp2 = h * 0.018f
                        val phase = idx * 0.48f

                        linePath.rewind()
                        var prevX = 0f
                        var prevY = baseY +
                            amp1 * sin((t + phase).toDouble()).toFloat() +
                            amp2 * cos((-t * 0.65f + phase * 1.4f).toDouble()).toFloat()
                        linePath.moveTo(prevX, prevY)
                        for (s in 1..steps) {
                            val fx = s.toFloat() / steps
                            val x = w * fx
                            val y = baseY +
                                amp1 * sin((fx * 5.2f + t + phase).toDouble()).toFloat() +
                                amp2 * cos((fx * 9.0f - t * 0.65f + phase * 1.4f).toDouble()).toFloat()
                            linePath.quadraticTo(prevX, prevY, (prevX + x) * 0.5f, (prevY + y) * 0.5f)
                            prevX = x
                            prevY = y
                        }
                        linePath.lineTo(w, prevY)
                        drawPath(
                            path = linePath,
                            brush = lineBrushes[idx],
                            style = if (idx == 4) accentStroke else thinStroke,
                        )
                    }
                }
            },
    )
}

// ==================== 微光点阵（Dot Matrix）====================

@Composable
private fun DotMatrixEffect(modifier: Modifier, paused: Boolean) {
    var elapsedSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var lastNanos = 0L
        var accum = 0f
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    accum += dt
                    if (accum >= BG_FRAME_STEP_SEC) {
                        elapsedSec += accum
                        accum = 0f
                    }
                }
                lastNanos = now
            }
        }
    }

    val isDark = DeckColors.isDark
    val primary = DeckColors.Ruby
    val glow = DeckColors.AccentGlow
    val neutralDot = DeckColors.TextSecondary

    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val spacing = 26.dp.toPx()
                val baseRadius = 1.35.dp.toPx()
                val maxBoostRadius = 1.15.dp.toPx()
                val cols = (w / spacing).toInt().coerceIn(8, 42)
                val rows = (h / spacing).toInt().coerceIn(6, 30)
                val offsetX = (w - (cols - 1) * spacing) * 0.5f
                val offsetY = (h - (rows - 1) * spacing) * 0.5f
                val cornerMist = Brush.radialGradient(
                    0.0f to primary.copy(alpha = if (isDark) 0.14f else 0.08f),
                    0.65f to glow.copy(alpha = if (isDark) 0.05f else 0.03f),
                    1.0f to Color.Transparent,
                    center = Offset(w * 0.5f, h * 0.52f),
                    radius = maxOf(w, h) * 0.62f,
                )
                val baseAlpha = if (isDark) 0.13f else 0.10f
                val peakAlpha = if (isDark) 0.44f else 0.34f

                onDrawBehind {
                    drawRect(brush = cornerMist)
                    val t = elapsedSec * 0.72f
                    for (r in 0 until rows) {
                        val y = offsetY + r * spacing
                        val ny = r.toFloat() / (rows - 1).coerceAtLeast(1)
                        val vFade = (minOf(ny, 1f - ny) / 0.18f).coerceIn(0.25f, 1f)
                        for (c in 0 until cols) {
                            val x = offsetX + c * spacing
                            val nx = c.toFloat() / (cols - 1).coerceAtLeast(1)
                            val wave = (sin((nx * 5.2f + ny * 3.1f - t).toDouble()).toFloat() + 1f) * 0.5f
                            val wave2 = (cos((nx * 3.6f - ny * 4.4f + t * 0.65f).toDouble()).toFloat() + 1f) * 0.5f
                            val energy = (wave * 0.65f + wave2 * 0.35f)
                            val highlight = ((energy - 0.52f) / 0.48f).coerceIn(0f, 1f)
                            val radius = baseRadius + maxBoostRadius * highlight * highlight
                            val color = if (highlight > 0.05f) {
                                lerp(neutralDot, primary, highlight).copy(
                                    alpha = (baseAlpha + (peakAlpha - baseAlpha) * highlight) * vFade,
                                )
                            } else {
                                neutralDot.copy(alpha = baseAlpha * vFade)
                            }
                            drawCircle(color = color, radius = radius, center = Offset(x, y))
                        }
                    }
                }
            },
    )
}
