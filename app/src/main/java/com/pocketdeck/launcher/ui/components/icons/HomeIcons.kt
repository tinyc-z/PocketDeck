package com.pocketdeck.launcher.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 主页专用线性图标（与 DeckIcons 同一 24×24 Lucide 规范，用 DeckIcon(icon, tint) 渲染）。
 */
object HomeIcons {

    private inline fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private inline fun ImageVector.Builder.strokePath(
        strokeWidth: Float = 2f,
        block: PathBuilder.() -> Unit,
    ) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }

    private inline fun ImageVector.Builder.fillPath(block: PathBuilder.() -> Unit) {
        path(fill = SolidColor(Color.Black), pathBuilder = block)
    }

    private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        horizontalLineTo(x + w - r)
        arcToRelative(r, r, 0f, false, true, r, r)
        verticalLineTo(y + h - r)
        arcToRelative(r, r, 0f, false, true, -r, r)
        horizontalLineTo(x + r)
        arcToRelative(r, r, 0f, false, true, -r, -r)
        verticalLineTo(y + r)
        arcToRelative(r, r, 0f, false, true, r, -r)
        close()
    }

    /** 开始游戏（实心三角） */
    val Play: ImageVector by lazy {
        icon("Play") {
            fillPath {
                moveTo(7.5f, 4.8f)
                curveTo(7.5f, 4.0f, 8.4f, 3.5f, 9.1f, 3.9f)
                lineTo(19.2f, 10.6f)
                curveTo(19.9f, 11.0f, 19.9f, 12.0f, 19.2f, 12.4f)
                lineTo(9.1f, 19.1f)
                curveTo(8.4f, 19.5f, 7.5f, 19.0f, 7.5f, 18.2f)
                close()
            }
        }
    }

    /** 编辑 */
    val Pencil: ImageVector by lazy {
        icon("Pencil") {
            strokePath {
                moveTo(16.5f, 3.5f)
                lineTo(20.5f, 7.5f)
                lineTo(8f, 20f)
                lineTo(3.5f, 20.5f)
                lineTo(4f, 16f)
                close()
                moveTo(14f, 6f)
                lineTo(18f, 10f)
            }
        }
    }

    /** 换封面 / 图库（与设计图 COVER_ICON_SVG 1:1 对齐：圆角画框 + 太阳 + 山脉） */
    val CoverGallery: ImageVector by lazy {
        icon("CoverGallery") {
            strokePath(strokeWidth = 2.0f) {
                // 圆角矩形 x=3..21, y=3..21, rx=3
                moveTo(6f, 3f)
                lineTo(18f, 3f)
                arcTo(3f, 3f, 0f, false, true, 21f, 6f)
                lineTo(21f, 18f)
                arcTo(3f, 3f, 0f, false, true, 18f, 21f)
                lineTo(6f, 21f)
                arcTo(3f, 3f, 0f, false, true, 3f, 18f)
                lineTo(3f, 6f)
                arcTo(3f, 3f, 0f, false, true, 6f, 3f)
                close()
                // 山脉 M21 15 L16 10 L5 21
                moveTo(21f, 15f)
                lineTo(16f, 10f)
                lineTo(5f, 21f)
            }
            strokePath(strokeWidth = 2.0f) {
                // 圆点 cx=8.5, cy=8.5, r=1.5
                moveTo(8.5f, 7f)
                arcTo(1.5f, 1.5f, 0f, true, true, 8.49f, 7f)
                close()
            }
        }
    }

    /** 属性 / 信息 */
    val Info: ImageVector by lazy {
        icon("Info") {
            strokePath {
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 11.99f, 3f)
                close()
                moveTo(12f, 11f)
                lineTo(12f, 16.5f)
            }
            strokePath(strokeWidth = 2.6f) {
                moveTo(12f, 7.6f)
                lineTo(12.01f, 7.6f)
            }
        }
    }

    /** 视图键（手柄 View / Select 键：两枚交叠窗口） */
    val ViewButton: ImageVector by lazy {
        icon("ViewButton") {
            strokePath(strokeWidth = 2.4f) {
                moveTo(9f, 4.5f)
                lineTo(19.5f, 4.5f)
                lineTo(19.5f, 15f)
                moveTo(4.5f, 9f)
                lineTo(15f, 9f)
                lineTo(15f, 19.5f)
                lineTo(4.5f, 19.5f)
                close()
            }
        }
    }

    /** 菜单 / 选项（三横线） */
    val Hamburger: ImageVector by lazy {
        icon("Hamburger") {
            strokePath(strokeWidth = 2.4f) {
                moveTo(5f, 7.5f)
                lineTo(19f, 7.5f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
                moveTo(5f, 16.5f)
                lineTo(19f, 16.5f)
            }
        }
    }

    /** 搜索 */
    val Search: ImageVector by lazy {
        icon("Search") {
            strokePath(strokeWidth = 2.0f) {
                moveTo(10.5f, 3.8f)
                arcTo(6.7f, 6.7f, 0f, true, true, 10.49f, 3.8f)
                close()
                moveTo(15.4f, 15.4f)
                lineTo(20.5f, 20.5f)
            }
        }
    }

    /** 关闭 */
    val Close: ImageVector by lazy {
        icon("Close") {
            strokePath(strokeWidth = 2.2f) {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }
    }

    /** 收起键盘 */
    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            strokePath(strokeWidth = 2.2f) {
                moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f)
            }
        }
    }

    /** 选中勾 */
    val Check: ImageVector by lazy {
        icon("Check") {
            strokePath(strokeWidth = 2.4f) {
                moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
            }
        }
    }

    /** 应用抽屉（四宫格圆角描边方块） */
    val Apps: ImageVector by lazy {
        icon("Apps") {
            strokePath(strokeWidth = 2.0f) {
                roundRect(3.5f, 3.5f, 6.6f, 6.6f, 1.6f)
                roundRect(13.9f, 3.5f, 6.6f, 6.6f, 1.6f)
                roundRect(3.5f, 13.9f, 6.6f, 6.6f, 1.6f)
                roundRect(13.9f, 13.9f, 6.6f, 6.6f, 1.6f)
            }
        }
    }

    /** 设置（八齿圆润齿轮 + 圆心） */
    val Settings: ImageVector by lazy {
        icon("Settings") {
            strokePath(strokeWidth = 2.0f) {
                // 中心圆孔
                moveTo(12f, 9.2f)
                arcTo(2.8f, 2.8f, 0f, true, true, 11.99f, 9.2f)
                close()
                // 对称八齿外廓（StrokeJoin.Round 自然圆润齿顶与齿谷）
                val cx = 12f
                val cy = 12f
                val rIn = 6.95f
                val rOut = 9.65f
                val teeth = 8
                val step = 2.0 * PI / teeth
                val deg = PI / 180.0
                for (i in 0 until teeth) {
                    val c = -PI / 2.0 + i * step
                    val a1 = c - 13.0 * deg
                    val a2 = c - 8.0 * deg
                    val a3 = c + 8.0 * deg
                    val a4 = c + 13.0 * deg
                    val x1 = (cx + rIn * cos(a1)).toFloat()
                    val y1 = (cy + rIn * sin(a1)).toFloat()
                    val x2 = (cx + rOut * cos(a2)).toFloat()
                    val y2 = (cy + rOut * sin(a2)).toFloat()
                    val x3 = (cx + rOut * cos(a3)).toFloat()
                    val y3 = (cy + rOut * sin(a3)).toFloat()
                    val x4 = (cx + rIn * cos(a4)).toFloat()
                    val y4 = (cy + rIn * sin(a4)).toFloat()
                    if (i == 0) moveTo(x1, y1) else lineTo(x1, y1)
                    lineTo(x2, y2)
                    lineTo(x3, y3)
                    lineTo(x4, y4)
                }
                close()
            }
        }
    }

    /** 视图切换（圆角画廊框 + 左右双竖线） */
    val ViewCarousel: ImageVector by lazy {
        icon("ViewCarousel") {
            strokePath(strokeWidth = 2.0f) {
                roundRect(6.5f, 5.2f, 11.0f, 13.6f, 2.8f)
                moveTo(2.8f, 7.0f)
                lineTo(2.8f, 17.0f)
                moveTo(21.2f, 7.0f)
                lineTo(21.2f, 17.0f)
            }
        }
    }
}
