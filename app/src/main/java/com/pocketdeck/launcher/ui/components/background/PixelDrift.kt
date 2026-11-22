package com.pocketdeck.launcher.ui.components.background

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.pocketdeck.launcher.ui.theme.DeckPalette
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val TWO_PI = (PI * 2).toFloat()

/** 设计基准宽度（dp）：960px@280dpi。尺寸按画布宽度换算，缩略图也能等比呈现。 */
internal const val BG_REF_WIDTH_DP = 548f

// ── 像素精灵（X = 实心像素）──
private val SPRITE_DPAD = arrayOf(
    "...XXX...",
    "...XXX...",
    "...XXX...",
    "XXXXXXXXX",
    "XXXX.XXXX",
    "XXXXXXXXX",
    "...XXX...",
    "...XXX...",
    "...XXX...",
)
private val SPRITE_STAR = arrayOf(
    "...X...",
    "...X...",
    "..XXX..",
    "XXXXXXX",
    "..XXX..",
    "...X...",
    "...X...",
)
private val SPRITE_HEART = arrayOf(
    ".XX.XX.",
    "XXXXXXX",
    "XXXXXXX",
    ".XXXXX.",
    "..XXX..",
    "...X...",
)
private val SPRITE_INVADER = arrayOf(
    "..X.....X..",
    "...X...X...",
    "..XXXXXXX..",
    ".XX.XXX.XX.",
    "XXXXXXXXXXX",
    "X.XXXXXXX.X",
    "X.X.....X.X",
    "...XX.XX...",
)
private val SPRITE_COIN = arrayOf(
    "..XXX..",
    ".XXXXX.",
    "XXX.XXX",
    "XXX.XXX",
    "XXX.XXX",
    ".XXXXX.",
    "..XXX..",
)
private val SPRITE_PIXEL = arrayOf("X")

private val SPRITES = arrayOf(SPRITE_DPAD, SPRITE_STAR, SPRITE_HEART, SPRITE_INVADER, SPRITE_COIN, SPRITE_PIXEL)

private class SpriteStrip(
    val topLeft: Offset,
    val size: Size,
)

/**
 * 将像素图按行合并成矩形条数组（以中心为原点，单位：像素格）。
 * 相比组合 Path，旋转/缩放时直接 drawRect 全部走 GPU 硬件四边形直出，避免 Skia 每帧软件光栅化 Path 吃满 RenderThread 与 hwuiTask。
 */
private fun buildSpriteStrips(rows: Array<String>): Array<SpriteStrip> {
    val list = ArrayList<SpriteStrip>(12)
    val hRows = rows.size
    val wCols = rows.maxOf { it.length }
    val ox = wCols / 2f
    val oy = hRows / 2f
    for (y in 0 until hRows) {
        val row = rows[y]
        var x = 0
        while (x < row.length) {
            if (row[x] == 'X') {
                val start = x
                while (x < row.length && row[x] == 'X') x++
                list.add(
                    SpriteStrip(
                        topLeft = Offset(start - ox, y - oy),
                        size = Size((x - start).toFloat(), 1f),
                    ),
                )
            } else {
                x++
            }
        }
    }
    return list.toTypedArray()
}

private val SpriteStrips: Array<Array<SpriteStrip>> = Array(SPRITES.size) { buildSpriteStrips(SPRITES[it]) }

private class Sprite(
    val kind: Int,
    val x: Float,          // 0..1
    val phase: Float,      // 0..1 初始纵向位置
    val rise: Int,         // 每 BG_TIME_WRAP_SEC 秒上升的屏数（整数保证无缝回绕）
    val swayAmp: Float,    // 宽度比例
    val swayCycles: Int,   // 每 BG_TIME_WRAP_SEC 秒的摆动周期数
    val cellDp: Float,     // 像素格大小（dp）
    val rotAmp: Float,     // 度
    val tone: Int,         // 0..3
    val alpha: Float,      // 0..1 相对强度
    val seed: Float,
)

private const val SPRITE_COUNT = 18

private val SpriteLayout: Array<Sprite> = run {
    val rng = Random(20261119)
    // 种类分布：较多星星/十字/小像素，少量入侵者/爱心/金币
    val kinds = intArrayOf(0, 1, 5, 2, 3, 1, 4, 5, 0, 1, 2, 5, 4, 3, 1, 5, 0, 2)
    Array(SPRITE_COUNT) { i ->
        val kind = kinds[i % kinds.size]
        // 横向分槽 + 抖动，避免扎堆
        val slot = (i * 7) % SPRITE_COUNT
        val x = ((slot + 0.2f + rng.nextFloat() * 0.6f) / SPRITE_COUNT).coerceIn(0.03f, 0.97f)
        val big = kind == 3 || kind == 0
        Sprite(
            kind = kind,
            x = x,
            phase = rng.nextFloat(),
            rise = 15 + rng.nextInt(22), // 33~80 秒穿越一屏
            swayAmp = 0.012f + rng.nextFloat() * 0.020f,
            swayCycles = 4 * (15 + rng.nextInt(15)), // 4 的倍数：0.5x/0.75x 谐波也能无缝回绕
            cellDp = when {
                kind == 5 -> 3.5f + rng.nextFloat() * 3f
                big -> 2.4f + rng.nextFloat() * 1.4f
                else -> 2.6f + rng.nextFloat() * 1.8f
            },
            rotAmp = if (kind == 5) 0f else 4f + rng.nextFloat() * 8f,
            tone = rng.nextInt(4),
            alpha = 0.65f + rng.nextFloat() * 0.35f,
            seed = rng.nextFloat() * TWO_PI,
        )
    }
}

/**
 * Pixel Drift：D-pad、四角星、爱心、太空入侵者、金币与小像素块缓缓上浮，伴随轻微摆动、缩放与旋转，
 * 越出顶部后从底部回绕。布局由固定种子生成，每次启动一致。
 */
internal fun Modifier.pixelDrift(p: DeckPalette, time: () -> Float): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val unit = w / BG_REF_WIDTH_DP // 1dp 对应的像素
    val maxAlpha = if (p.isDark) 0.50f else 0.36f
    val violet = if (p.isDark) Color(0xFF9B7BE0) else Color(0xFF9C7FE6)
    val tones = arrayOf(p.brandStart, p.brandMid, p.brandEnd, violet)
    val colors = Array(SPRITE_COUNT) { i ->
        val s = SpriteLayout[i]
        tones[s.tone].copy(alpha = maxAlpha * s.alpha)
    }
    val washColor = lerp(p.background, p.brandMid, if (p.isDark) 0.10f else 0.07f)
    val wash = Brush.linearGradient(
        0f to washColor.copy(alpha = 0f),
        1f to washColor.copy(alpha = if (p.isDark) 0.55f else 0.85f),
        start = Offset(0f, 0f),
        end = Offset(w * 0.45f, h),
    )
    val margin = 24f * unit
    val span = h + margin * 2f
    onDrawBehind {
        if (w <= 1f || h <= 1f) return@onDrawBehind
        drawRect(wash)
        val t = time()
        val tn = t / BG_TIME_WRAP_SEC
        for (i in 0 until SPRITE_COUNT) {
            val s = SpriteLayout[i]
            var yFrac = (s.phase - tn * s.rise) % 1f
            if (yFrac < 0f) yFrac += 1f
            val y = yFrac * span - margin
            val swayA = tn * s.swayCycles * TWO_PI + s.seed
            val x = w * (s.x + s.swayAmp * sin(swayA))
            val cell = s.cellDp * unit * (1f + 0.08f * sin(swayA * 0.5f + 1.3f))
            val rot = s.rotAmp * sin(swayA * 0.75f + s.seed)
            val strips = SpriteStrips[s.kind]
            val col = colors[i]
            translate(x, y) {
                rotate(rot, pivot = Offset.Zero) {
                    scale(cell, cell, pivot = Offset.Zero) {
                        for (j in strips.indices) {
                            val strip = strips[j]
                            drawRect(color = col, topLeft = strip.topLeft, size = strip.size)
                        }
                    }
                }
            }
        }
    }
}
