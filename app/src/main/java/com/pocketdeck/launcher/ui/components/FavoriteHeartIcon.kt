package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
