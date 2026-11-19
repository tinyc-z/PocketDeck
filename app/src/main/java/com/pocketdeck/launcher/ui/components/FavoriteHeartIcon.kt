package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun buildModernHeartPath(path: Path, w: Float, h: Float) {
    path.rewind()
    path.moveTo(w * 0.50f, h * 0.90f)
    path.cubicTo(w * 0.47f, h * 0.88f, w * 0.06f, h * 0.62f, w * 0.06f, h * 0.35f)
    path.cubicTo(w * 0.06f, h * 0.17f, w * 0.20f, h * 0.08f, w * 0.35f, h * 0.10f)
    path.cubicTo(w * 0.43f, h * 0.11f, w * 0.48f, h * 0.17f, w * 0.50f, h * 0.23f)
    path.cubicTo(w * 0.52f, h * 0.17f, w * 0.57f, h * 0.11f, w * 0.65f, h * 0.10f)
    path.cubicTo(w * 0.80f, h * 0.08f, w * 0.94f, h * 0.17f, w * 0.94f, h * 0.35f)
    path.cubicTo(w * 0.94f, h * 0.62f, w * 0.53f, h * 0.88f, w * 0.50f, h * 0.90f)
    path.close()
}

/**
 * 品牌收藏徽章：主题渐变圆 + 白色实心心形 + 彩色柔光阴影（封面右上角 / 列表行尾）。
 * 渐变与心形路径在 drawWithCache 中按尺寸缓存，绘制期零分配。
 */
@Composable
fun BrandHeartBadge(
    size: Dp,
    modifier: Modifier = Modifier,
    shadow: Boolean = true,
) {
    val p = LocalDeckColors.current
    val heartPath = remember { Path() }
    Box(
        modifier
            .size(size)
            .then(
                if (shadow) {
                    Modifier.shadow(
                        elevation = size * 0.22f,
                        shape = CircleShape,
                        ambientColor = p.brandEnd,
                        spotColor = p.brandEnd,
                        clip = false,
                    )
                } else {
                    Modifier
                },
            )
            .drawWithCache {
                val brush = Brush.linearGradient(
                    listOf(p.brandStart, p.brandMid, p.brandEnd),
                    start = Offset.Zero,
                    end = Offset(this.size.width, this.size.height),
                )
                val hw = this.size.minDimension * 0.54f
                val hh = hw * 0.92f
                buildModernHeartPath(heartPath, hw, hh)
                val left = (this.size.width - hw) / 2f
                val top = (this.size.height - hh) / 2f + hh * 0.02f
                onDrawBehind {
                    drawCircle(brush)
                    translate(left = left, top = top) {
                        drawPath(path = heartPath, color = Color.White, style = Fill)
                    }
                }
            },
    )
}

/**
 * 统一的收藏心形图标（支持实心已收藏与空心未收藏两种状态）。
 */
@Composable
fun FavoriteHeartIcon(
    filled: Boolean,
    color: Color,
    size: Dp = 16.dp,
    strokeWidth: Dp = 1.7.dp,
    modifier: Modifier = Modifier,
) {
    val path = remember { Path() }
    Canvas(modifier.size(size)) {
        buildModernHeartPath(path, this.size.width, this.size.height)
        if (filled) {
            drawPath(path = path, color = color, style = Fill)
        } else {
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}
