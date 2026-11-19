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
import androidx.compose.ui.graphics.asImageBitmap
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
    style: BackgroundStyle = BackgroundStyle.Waves,
    coverCandidates: List<Any> = emptyList(),
    knownCoverIndex: Int? = null,
    apkPackage: String? = null,
    customBackground: String? = null,
    paused: Boolean = false,
) {
    when (style) {
        BackgroundStyle.None -> { /* 纯色背景，由外层 Box 提供 */ }
        BackgroundStyle.Waves -> WavesEffect(modifier, animated = true, paused = paused)
        BackgroundStyle.Static -> WavesEffect(modifier, animated = false, paused = true)
        BackgroundStyle.Gradient -> GradientEffect(modifier, paused = paused)
        BackgroundStyle.Particles -> ParticlesEffect(modifier, paused = paused)
        BackgroundStyle.CoverBlur -> CoverBlurEffect(
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
    val ruby = DeckColors.Ruby
    val rubyLight = DeckColors.RubyLight
    val waveLayers = remember(animated, isDark, ruby, rubyLight) {
        if (animated) {
            listOf(
                WaveLayer(ruby.copy(alpha = if (isDark) 0.24f else 0.20f), 0.038f, 1.15f, 0.42f, 0.0f, 0f),
                WaveLayer(rubyLight.copy(alpha = if (isDark) 0.20f else 0.22f), 0.044f, 0.90f, 0.65f, 2.1f, 0.04f),
                WaveLayer(ruby.copy(alpha = if (isDark) 0.16f else 0.15f), 0.038f, 1.45f, 0.85f, 4.2f, 0.08f),
            )
        } else {
            // 静态模式使用略微更饱满的层叠曲线，与纯白/纯黑一眼区分
            listOf(
                WaveLayer(ruby.copy(alpha = if (isDark) 0.28f else 0.24f), 0.045f, 1.10f, 0.42f, 0.9f, -0.02f),
                WaveLayer(rubyLight.copy(alpha = if (isDark) 0.22f else 0.25f), 0.052f, 0.85f, 0.65f, 2.8f, 0.04f),
                WaveLayer(ruby.copy(alpha = if (isDark) 0.18f else 0.18f), 0.042f, 1.40f, 0.85f, 4.6f, 0.10f),
            )
        }
    }
    val wavePath = remember { Path() }

    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val midY = h * 0.42f
                val fadeBottom = h * 0.94f
                val layerBrushes = waveLayers.map { layer ->
                    Brush.verticalGradient(
                        0f to layer.color,
                        0.65f to layer.color.copy(alpha = layer.color.alpha * 0.72f),
                        1f to Color.Transparent,
                        startY = midY,
                        endY = fadeBottom,
                    )
                }
                val bottomFadeBrush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to bgColor.copy(alpha = 0.85f),
                    startY = h * 0.80f,
                    endY = h,
                )
                onDrawBehind {
                    val t = if (animated) elapsedSec else 1.4f
                    waveLayers.forEachIndexed { idx, layer ->
                        buildWavePath(wavePath, w, h, midY, t, layer)
                        drawPath(wavePath, layerBrushes[idx])
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
    val primaryColor = DeckColors.Ruby
    val glowColor = DeckColors.AccentGlow
    val tertiaryColor = DeckColors.AccentTertiary
    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val c1 = primaryColor.copy(alpha = if (isDark) 0.40f else 0.32f)
                val c2 = glowColor.copy(alpha = if (isDark) 0.33f else 0.27f)
                val c3 = tertiaryColor.copy(alpha = if (isDark) 0.27f else 0.22f)
                val orb1Colors = listOf(
                    primaryColor.copy(alpha = if (isDark) 0.38f else 0.28f),
                    Color.Transparent,
                )
                val orb2Colors = listOf(
                    glowColor.copy(alpha = if (isDark) 0.32f else 0.26f),
                    Color.Transparent,
                )
                val bottomFadeBrush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to bgColor.copy(alpha = if (isDark) 0.45f else 0.30f),
                    startY = h * 0.6f,
                    endY = h,
                )
                onDrawBehind {
                    val angle = elapsedSec * 0.35f
                    val cx = w * 0.5f
                    val cy = h * 0.5f
                    val dx = cos(angle.toDouble()).toFloat() * w * 0.55f
                    val dy = sin(angle.toDouble()).toFloat() * h * 0.55f

                    drawRect(
                        Brush.linearGradient(
                            0f to c1,
                            0.48f to c2,
                            1f to c3,
                            start = Offset(cx - dx, cy - dy),
                            end = Offset(cx + dx, cy + dy),
                        ),
                    )

                    val orb1Center = Offset(
                        w * (0.32f + 0.22f * cos((elapsedSec * 0.42f).toDouble()).toFloat()),
                        h * (0.38f + 0.20f * sin((elapsedSec * 0.36f).toDouble()).toFloat()),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = orb1Colors,
                            center = orb1Center,
                            radius = w * 0.55f,
                        ),
                        radius = w * 0.55f,
                        center = orb1Center,
                    )

                    val orb2Center = Offset(
                        w * (0.68f + 0.22f * sin((elapsedSec * 0.31f + 1.5f).toDouble()).toFloat()),
                        h * (0.62f + 0.20f * cos((elapsedSec * 0.45f + 0.8f).toDouble()).toFloat()),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = orb2Colors,
                            center = orb2Center,
                            radius = w * 0.5f,
                        ),
                        radius = w * 0.5f,
                        center = orb2Center,
                    )

                    drawRect(bottomFadeBrush)
                }
            },
    )
}

// ==================== 粒子效果 ====================

private data class Particle(
    val x: Float,       // 0~1 normalized
    val y: Float,       // 0~1 normalized
    val radius: Float,  // dp-like scale at 480w
    val speed: Float,   // 移动速度乘数
    val alpha: Float,
    val tone: Int,      // 0: Ruby, 1: Coral, 2: Amber
    val phase: Float,
)

@Composable
private fun ParticlesEffect(modifier: Modifier, paused: Boolean) {
    val particles = remember {
        val rng = Random(42)
        val count = 26
        List(count) { i ->
            val slotX = (i + 0.12f + rng.nextFloat() * 0.76f) / count
            Particle(
                x = slotX.coerceIn(0.04f, 0.96f),
                y = rng.nextFloat(),
                radius = 12f + rng.nextFloat() * 34f,
                speed = 0.45f + rng.nextFloat() * 0.85f,
                alpha = 0.16f + rng.nextFloat() * 0.22f,
                tone = i % 3,
                phase = rng.nextFloat() * 6.28f,
            )
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
    val rubyColor = DeckColors.Ruby
    val coralColor = DeckColors.RubyLight
    val amberColor = DeckColors.AccentGlow
    val bgColor = DeckColors.Background

    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val washBrush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to rubyColor.copy(alpha = if (isDark) 0.12f else 0.08f),
                )
                val bottomFadeBrush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to bgColor.copy(alpha = 0.28f),
                    startY = h * 0.72f,
                    endY = h,
                )
                val paletteStops = particles.map { p ->
                    val baseColor = when (p.tone) {
                        0 -> rubyColor
                        1 -> coralColor
                        else -> amberColor
                    }
                    listOf(
                        baseColor.copy(alpha = p.alpha),
                        baseColor.copy(alpha = p.alpha * 0.35f),
                        Color.Transparent,
                    )
                }
                onDrawBehind {
                    drawRect(washBrush)
                    particles.forEachIndexed { idx, p ->
                        val py = ((p.y - elapsedSec * p.speed * 0.06f) % 1f + 1f) % 1f
                        val px = p.x + sin((elapsedSec * p.speed * 0.9f + p.phase).toDouble()).toFloat() * 0.032f
                        val r = p.radius * (w / 480f)
                        val center = Offset(px * w, py * h)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = paletteStops[idx],
                                center = center,
                                radius = r * 1.45f,
                            ),
                            radius = r * 1.45f,
                            center = center,
                        )
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
        Modifier.blur(26.dp)
    } else {
        Modifier
    }

    val isOnline by NetworkStatus.online.collectAsState()
    Box(modifier.fillMaxSize()) {
        Crossfade(
            targetState = currentModel,
            animationSpec = tween(280),
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
                    alpha = if (isDark) 0.72f else 0.65f,
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
                        alpha = if (isDark) 0.68f else 0.58f,
                    )
                }
            }
        }

        // 柔和可读性遮罩，确保封面色调透出且文字清晰
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val tint = bgColor.copy(alpha = if (isDark) 0.36f else 0.32f)
                    val grad = Brush.verticalGradient(
                        0f to bgColor.copy(alpha = 0.25f),
                        0.45f to Color.Transparent,
                        1f to bgColor.copy(alpha = if (isDark) 0.55f else 0.45f),
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
    val color: Color,
    val amplitude: Float,
    val frequency: Float,
    val speedRad: Float,
    val phaseOffset: Float,
    val yOffset: Float,
)

private fun buildWavePath(path: Path, w: Float, h: Float, midY: Float, elapsedSec: Float, layer: WaveLayer) {
    val amp = h * layer.amplitude
    val freq = layer.frequency
    val baseY = midY + h * layer.yOffset
    val twoPi = PI.toFloat() * 2f
    val p = elapsedSec * layer.speedRad + layer.phaseOffset
    val pHarmonic = elapsedSec * layer.speedRad * 0.5f

    fun waveY(frac: Float): Float {
        val angle = p + frac * freq * twoPi
        val wave = 0.82f * sin(angle.toDouble()).toFloat() +
            0.18f * sin((angle * 2f - pHarmonic).toDouble()).toFloat()
        return baseY + amp * wave
    }

    path.rewind()
    val steps = 24
    path.moveTo(0f, h)
    var prevX = 0f
    var prevY = waveY(0f)
    path.lineTo(0f, prevY)
    for (i in 1..steps) {
        val frac = i.toFloat() / steps
        val x = w * frac
        val y = waveY(frac)
        val midX = (prevX + x) * 0.5f
        val midPtY = (prevY + y) * 0.5f
        path.quadraticTo(prevX, prevY, midX, midPtY)
        prevX = x
        prevY = y
    }
    path.lineTo(w, prevY)
    path.lineTo(w, h)
    path.close()
}
