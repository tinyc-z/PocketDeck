package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            // 对称圆润心形路径
            moveTo(w * 0.50f, h * 0.88f)
            cubicTo(
                w * 0.48f, h * 0.86f,
                w * 0.08f, h * 0.60f,
                w * 0.08f, h * 0.34f,
            )
            cubicTo(
                w * 0.08f, h * 0.16f,
                w * 0.22f, h * 0.08f,
                w * 0.36f, h * 0.08f,
            )
            cubicTo(
                w * 0.44f, h * 0.08f,
                w * 0.49f, h * 0.13f,
                w * 0.50f, h * 0.19f,
            )
            cubicTo(
                w * 0.51f, h * 0.13f,
                w * 0.56f, h * 0.08f,
                w * 0.64f, h * 0.08f,
            )
            cubicTo(
                w * 0.78f, h * 0.08f,
                w * 0.92f, h * 0.16f,
                w * 0.92f, h * 0.34f,
            )
            cubicTo(
                w * 0.92f, h * 0.60f,
                w * 0.52f, h * 0.86f,
                w * 0.50f, h * 0.88f,
            )
            close()
        }
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
