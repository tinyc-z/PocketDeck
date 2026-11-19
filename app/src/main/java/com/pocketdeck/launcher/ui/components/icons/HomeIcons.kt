package com.pocketdeck.launcher.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

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

    /** 搜索 */
    val Search: ImageVector by lazy {
        icon("Search") {
            strokePath(strokeWidth = 2.2f) {
                moveTo(10.5f, 3.5f)
                arcTo(7f, 7f, 0f, true, true, 10.49f, 3.5f)
                close()
                moveTo(15.6f, 15.6f)
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
}
