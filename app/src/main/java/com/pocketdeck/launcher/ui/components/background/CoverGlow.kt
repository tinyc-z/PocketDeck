package com.pocketdeck.launcher.ui.components.background

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.ui.theme.DeckPalette
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ==================== 布局 ====================

private val CoverBlobs = arrayOf(
    BlobSpec(bx = 0.25f, by = 0.35f, ax = 0.05f, ay = 0.04f, fx = 1, fy = 1, px = 0.0f, py = 1.6f, radius = 0.60f, breathe = 0.08f),
    BlobSpec(bx = 0.75f, by = 0.70f, ax = 0.04f, ay = 0.05f, fx = 1, fy = 2, px = 2.4f, py = 0.3f, radius = 0.60f, breathe = 0.08f),
    BlobSpec(bx = 0.70f, by = 0.15f, ax = 0.06f, ay = 0.03f, fx = 2, fy = 1, px = 4.1f, py = 2.2f, radius = 0.46f, breathe = 0.10f),
)

/** 设置页缩略图用的示例封面色（灰蓝 / 朱红 / 琥珀）。 */
internal val SampleCoverColors = intArrayOf(0xFF8E8EA8.toInt(), 0xFFDC4628.toInt(), 0xFFF0BE3C.toInt())

/** 将封面原色调整为适合当前主题的光斑色（含透明度）。 */
internal fun styleCoverColors(raw: IntArray, dark: Boolean): Array<Color> {
    val alphas = if (dark) floatArrayOf(0.62f, 0.52f, 0.38f) else floatArrayOf(0.55f, 0.46f, 0.36f)
    return Array(3) { i ->
        var c = Color(raw[i % raw.size]).copy(alpha = 1f)
        c = if (dark) {
            if (c.luminance() < 0.06f) lerp(c, Color.White, 0.22f) else c
        } else {
            lerp(c, Color.White, 0.30f)
        }
        c.copy(alpha = alphas[i])
    }
}

/**
 * 封面氛围光的绘制层：[from] → [to] 两组颜色按 [progress] 交叉淡化，光斑缓慢呼吸。
 */
internal fun Modifier.coverGlow(
    p: DeckPalette,
    from: Array<Color>,
    to: Array<Color>,
    progress: () -> Float,
    time: () -> Float,
): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val dark = p.isDark
    val bg = p.background
    val radii = FloatArray(CoverBlobs.size) { blobRadiusPx(CoverBlobs[it], w, h) }
    val fromBrushes = Array(CoverBlobs.size) { glowBrush(from[it], radii[it]) }
    val toBrushes = Array(CoverBlobs.size) { glowBrush(to[it], radii[it]) }
    val veil = Brush.verticalGradient(
        0f to bg.copy(alpha = if (dark) 0.36f else 0.24f),
        0.26f to bg.copy(alpha = 0f),
        0.64f to bg.copy(alpha = 0f),
        1f to bg.copy(alpha = if (dark) 0.60f else 0.42f),
    )
    onDrawBehind {
        if (w <= 1f || h <= 1f) return@onDrawBehind
        val t = time()
        val pr = progress().coerceIn(0f, 1f)
        if (pr < 1f) {
            for (i in CoverBlobs.indices) drawBlob(CoverBlobs[i], fromBrushes[i], radii[i], t, 1f - pr)
        }
        for (i in CoverBlobs.indices) drawBlob(CoverBlobs[i], toBrushes[i], radii[i], t, pr)
        drawRect(veil)
    }
}

/**
 * Cover Glow：从焦点封面提取 2~3 个主色，渲染为 3 个柔和光斑；切换游戏时颜色交叉淡化。
 * 没有封面时回退到 Sunset Flow 的主题色。参数与旧 CoverBlurEffect 一致。
 */
@Composable
internal fun CoverGlowBackground(
    modifier: Modifier,
    coverCandidates: List<Any>,
    knownCoverIndex: Int?,
    apkPackage: String?,
    time: () -> Float,
) {
    val context = LocalContext.current
    val p = LocalDeckColors.current
    val isOnline by NetworkStatus.online.collectAsState()
    val online by rememberUpdatedState(isOnline)

    // 当前封面的原始主色（null = 无封面，回退主题色）
    var raw by remember { mutableStateOf<IntArray?>(null) }
    LaunchedEffect(coverCandidates, knownCoverIndex, apkPackage) {
        val cached = CoverColorCache.peek(coverCandidates, knownCoverIndex, apkPackage)
        if (cached != null) {
            raw = cached
            return@LaunchedEffect
        }
        // 快速滚动时去抖，避免为一闪而过的游戏解码封面
        delay(140)
        raw = withContext(Dispatchers.IO) {
            resolveCoverColors(context, coverCandidates, knownCoverIndex, apkPackage, online)
        }
    }

    val display = remember(raw, p) {
        raw?.let { styleCoverColors(it, p.isDark) } ?: arrayOf(p.flowA, p.flowB, p.flowC)
    }
    var from by remember { mutableStateOf(display) }
    var to by remember { mutableStateOf(display) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(display) {
        if (display === to) return@LaunchedEffect
        // 以当前混合色为起点，打断中的过渡也不会跳色
        val pr = progress.value
        val f = from
        val t0 = to
        from = Array(3) { lerp(f[it], t0[it], pr) }
        to = display
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    Spacer(
        modifier
            .fillMaxSize()
            .coverGlow(p, from, to, progress = { progress.value }, time = time),
    )
}

// ==================== 主色提取 ====================

private object CoverColorCache {
    private val cache = LruCache<String, IntArray>(96)

    fun get(key: String): IntArray? = cache.get(key)
    fun put(key: String, value: IntArray) {
        cache.put(key, value)
    }

    /** 仅查缓存（主线程安全、无 IO），命中则可跳过去抖立即切换颜色。 */
    fun peek(candidates: List<Any>, knownIdx: Int?, apk: String?): IntArray? {
        val start = startIndex(candidates, knownIdx)
        if (start >= 0) {
            return get(coverKey(candidates[start]))
        }
        return apk?.let { get(appKey(it)) }
    }
}

private fun coverKey(model: Any) = "c:$model"
private fun appKey(pkg: String) = "a:$pkg"

private fun startIndex(candidates: List<Any>, knownIdx: Int?): Int = when {
    candidates.isEmpty() -> -1
    knownIdx == -1 -> -1
    knownIdx != null && knownIdx in candidates.indices -> knownIdx
    else -> 0
}

private suspend fun resolveCoverColors(
    context: Context,
    candidates: List<Any>,
    knownIdx: Int?,
    apk: String?,
    online: Boolean,
): IntArray? {
    val start = startIndex(candidates, knownIdx)
    if (start >= 0) {
        for (i in start until candidates.size) {
            val model = candidates[i]
            val key = coverKey(model)
            CoverColorCache.get(key)?.let { return it }
            val bmp = loadCoverBitmap(context, model, online) ?: continue
            val colors = extractDominantColors(bmp) ?: continue
            CoverColorCache.put(key, colors)
            return colors
        }
    }
    if (apk != null) {
        val key = appKey(apk)
        CoverColorCache.get(key)?.let { return it }
        val colors = runCatching {
            val bmp = context.packageManager.getApplicationIcon(apk).toBitmap(32, 32, Bitmap.Config.ARGB_8888)
            extractDominantColors(bmp)
        }.getOrNull()
        if (colors != null) {
            CoverColorCache.put(key, colors)
            return colors
        }
    }
    return null
}

private suspend fun loadCoverBitmap(context: Context, model: Any, online: Boolean): Bitmap? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(48, 48)
            .allowHardware(false)
            .networkCachePolicy(if (online) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        (result as? SuccessResult)?.image?.toBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}

/**
 * 廉价主色提取：缩放到 16x16，按 RGB 各 3bit 分桶求平均，
 * 以「像素数 × 饱和度权重」打分，选出彼此差异足够大的 3 个颜色。
 */
internal fun extractDominantColors(bmp: Bitmap): IntArray? = runCatching {
    if (bmp.isRecycled) return@runCatching null
    val src = if (bmp.config == Bitmap.Config.HARDWARE) {
        bmp.copy(Bitmap.Config.ARGB_8888, false) ?: return@runCatching null
    } else {
        bmp
    }
    val small = Bitmap.createScaledBitmap(src, 16, 16, true)
    val px = IntArray(256)
    small.getPixels(px, 0, 16, 0, 0, 16, 16)
    if (small !== bmp && small !== src) small.recycle()
    if (src !== bmp) src.recycle()

    val count = IntArray(512)
    val rs = IntArray(512)
    val gs = IntArray(512)
    val bs = IntArray(512)
    for (c in px) {
        if ((c ushr 24) < 128) continue
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val idx = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
        count[idx]++
        rs[idx] += r
        gs[idx] += g
        bs[idx] += b
    }
    val hsv = FloatArray(3)
    val buckets = ArrayList<Pair<Int, Float>>(64) // (argb, score)
    for (i in 0 until 512) {
        val n = count[i]
        if (n == 0) continue
        val r = rs[i] / n
        val g = gs[i] / n
        val b = bs[i] / n
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        val s = hsv[1]
        val v = hsv[2]
        var weight = 0.35f + s
        if (v < 0.12f) weight *= 0.25f              // 近黑
        if (v > 0.93f && s < 0.10f) weight *= 0.35f // 近白
        buckets += (0xFF000000.toInt() or (r shl 16) or (g shl 8) or b) to n * weight
    }
    if (buckets.isEmpty()) return@runCatching null
    buckets.sortByDescending { it.second }

    val chosen = ArrayList<Int>(3)
    for ((c, _) in buckets) {
        if (chosen.all { colorDist2(it, c) > 56 * 56 }) chosen += c
        if (chosen.size == 3) break
    }
    // 单色封面：用同色的明暗变体补齐
    val first = chosen[0]
    while (chosen.size < 3) {
        chosen += if (chosen.size == 1) shade(first, 1.25f) else shade(first, 0.72f)
    }
    chosen.toIntArray()
}.getOrNull()

private fun colorDist2(a: Int, b: Int): Int {
    val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
    val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
    val db = (a and 0xFF) - (b and 0xFF)
    return dr * dr + dg * dg + db * db
}

private fun shade(c: Int, f: Float): Int {
    val r = (((c shr 16) and 0xFF) * f).toInt().coerceIn(0, 255)
    val g = (((c shr 8) and 0xFF) * f).toInt().coerceIn(0, 255)
    val b = ((c and 0xFF) * f).toInt().coerceIn(0, 255)
    return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
}
