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
 * 面板类界面（局域网 / 快传 / 游戏库 / 应用 / 编辑 / 弹窗）使用的补充线性图标，
 * 与 DeckIcons 同一 Lucide 24×24 规范，通过 `DeckIcon(PanelIcons.X, tint)` 绘制。
 */
object PanelIcons {

    private inline fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private inline fun ImageVector.Builder.stroke(
        width: Float = 1.9f,
        block: PathBuilder.() -> Unit,
    ) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }

    private inline fun ImageVector.Builder.solid(block: PathBuilder.() -> Unit) {
        path(fill = SolidColor(Color.Black), pathBuilder = block)
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
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

    val Plus: ImageVector by lazy {
        icon("Plus") { stroke(2.1f) { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) } }
    }

    val Minus: ImageVector by lazy {
        icon("Minus") { stroke(2.1f) { moveTo(5f, 12f); horizontalLineTo(19f) } }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") { stroke(2.2f) { moveTo(15f, 18f); lineTo(9f, 12f); lineTo(15f, 6f) } }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") { stroke(2.1f) { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) } }
    }

    val ChevronUp: ImageVector by lazy {
        icon("ChevronUp") { stroke(2.1f) { moveTo(18f, 15f); lineTo(12f, 9f); lineTo(6f, 15f) } }
    }

    val ChevronsUpDown: ImageVector by lazy {
        icon("ChevronsUpDown") {
            stroke { moveTo(7f, 15f); lineTo(12f, 20f); lineTo(17f, 15f); moveTo(7f, 9f); lineTo(12f, 4f); lineTo(17f, 9f) }
        }
    }

    val ArrowLeft: ImageVector by lazy {
        icon("ArrowLeft") { stroke(2f) { moveTo(19f, 12f); horizontalLineTo(5f); moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f) } }
    }

    val ArrowRight: ImageVector by lazy {
        icon("ArrowRight") { stroke(2f) { moveTo(5f, 12f); horizontalLineTo(19f); moveTo(12f, 5f); lineTo(19f, 12f); lineTo(12f, 19f) } }
    }

    /** 返回上级目录。 */
    val CornerLeftUp: ImageVector by lazy {
        icon("CornerLeftUp") {
            stroke(2f) {
                moveTo(14f, 9f); lineTo(9f, 4f); lineTo(4f, 9f)
                moveTo(20f, 20f); horizontalLineTo(13f)
                arcToRelative(4f, 4f, 0f, false, true, -4f, -4f)
                verticalLineTo(4f)
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") { stroke(2.1f) { moveTo(18f, 6f); lineTo(6f, 18f); moveTo(6f, 6f); lineTo(18f, 18f) } }
    }

    val Check: ImageVector by lazy {
        icon("Check") { stroke(2.3f) { moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f) } }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            stroke {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, false, true, 12f, 3f)
                curveTo(14.5f, 3f, 16.9f, 4f, 18.74f, 5.74f)
                lineTo(21f, 8f)
                moveTo(21f, 3f); verticalLineTo(8f); horizontalLineTo(16f)
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, false, true, 12f, 21f)
                curveTo(9.5f, 21f, 7.1f, 20f, 5.26f, 18.26f)
                lineTo(3f, 16f)
                moveTo(8f, 16f); horizontalLineTo(3f); verticalLineTo(21f)
            }
        }
    }

    /** 重置 / 撤销。 */
    val RotateCcw: ImageVector by lazy {
        icon("RotateCcw") {
            stroke {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, true, false, 12f, 3f)
                curveTo(9.5f, 3f, 7.1f, 4f, 5.26f, 5.74f)
                lineTo(3f, 8f)
                moveTo(3f, 3f); verticalLineTo(8f); horizontalLineTo(8f)
            }
        }
    }

    val Share: ImageVector by lazy {
        icon("Share") {
            stroke {
                circle(18f, 5f, 3f)
                circle(6f, 12f, 3f)
                circle(18f, 19f, 3f)
                moveTo(8.59f, 13.51f); lineTo(15.42f, 17.49f)
                moveTo(15.41f, 6.51f); lineTo(8.59f, 10.49f)
            }
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            stroke {
                moveTo(21f, 15f); verticalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(15f)
                moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
                moveTo(12f, 15f); verticalLineTo(3f)
            }
        }
    }

    val Upload: ImageVector by lazy {
        icon("Upload") {
            stroke {
                moveTo(21f, 15f); verticalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(15f)
                moveTo(17f, 8f); lineTo(12f, 3f); lineTo(7f, 8f)
                moveTo(12f, 3f); verticalLineTo(15f)
            }
        }
    }

    /** 三横线（列表 / 菜单 / 拖动排序）。 */
    val Menu: ImageVector by lazy {
        icon("Menu") { stroke(2f) { moveTo(4f, 6f); horizontalLineTo(20f); moveTo(4f, 12f); horizontalLineTo(20f); moveTo(4f, 18f); horizontalLineTo(20f) } }
    }

    val MoreHorizontal: ImageVector by lazy {
        icon("MoreHorizontal") {
            solid { circle(5f, 12f, 1.8f); circle(12f, 12f, 1.8f); circle(19f, 12f, 1.8f) }
        }
    }

    val Eye: ImageVector by lazy {
        icon("Eye") {
            stroke {
                moveTo(2f, 12f)
                curveTo(4.5f, 7f, 8f, 5f, 12f, 5f)
                curveTo(16f, 5f, 19.5f, 7f, 22f, 12f)
                curveTo(19.5f, 17f, 16f, 19f, 12f, 19f)
                curveTo(8f, 19f, 4.5f, 17f, 2f, 12f)
                close()
                circle(12f, 12f, 3f)
            }
        }
    }

    val EyeOff: ImageVector by lazy {
        icon("EyeOff") {
            stroke {
                moveTo(9.9f, 5.2f)
                curveTo(10.6f, 5.07f, 11.3f, 5f, 12f, 5f)
                curveTo(16f, 5f, 19.5f, 7f, 22f, 12f)
                curveTo(21.4f, 13.2f, 20.7f, 14.2f, 19.9f, 15.1f)
                moveTo(14.1f, 14.2f)
                arcTo(3f, 3f, 0f, false, true, 9.8f, 9.9f)
                moveTo(17.5f, 17.5f)
                curveTo(15.9f, 18.5f, 14f, 19f, 12f, 19f)
                curveTo(8f, 19f, 4.5f, 17f, 2f, 12f)
                curveTo(3f, 10f, 4.6f, 7.9f, 6.5f, 6.5f)
                moveTo(3f, 3f); lineTo(21f, 21f)
            }
        }
    }

    val Wifi: ImageVector by lazy {
        icon("Wifi") {
            stroke(2f) {
                moveTo(5f, 12.55f)
                arcTo(10f, 10f, 0f, false, true, 19f, 12.55f)
                moveTo(8.5f, 16.05f)
                arcTo(5f, 5f, 0f, false, true, 15.5f, 16.05f)
                moveTo(2f, 8.82f)
                arcTo(15f, 15f, 0f, false, true, 22f, 8.82f)
                moveTo(12f, 20f); lineTo(12.01f, 20f)
            }
        }
    }

    val Image: ImageVector by lazy {
        icon("Image") {
            stroke {
                roundRect(3f, 3f, 18f, 18f, 2.5f)
                circle(9f, 9f, 2f)
                moveTo(21f, 15f); lineTo(16f, 10f); lineTo(5f, 21f)
            }
        }
    }

    val Folder: ImageVector by lazy {
        icon("Folder") {
            stroke {
                moveTo(4f, 20f); horizontalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                verticalLineTo(8f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, -2f)
                horizontalLineTo(12f)
                lineTo(10f, 3f)
                horizontalLineTo(4f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                verticalLineTo(18f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                close()
            }
        }
    }

    val FolderPlus: ImageVector by lazy {
        icon("FolderPlus") {
            stroke {
                moveTo(4f, 20f); horizontalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                verticalLineTo(8f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, -2f)
                horizontalLineTo(12f)
                lineTo(10f, 3f)
                horizontalLineTo(4f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                verticalLineTo(18f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                close()
                moveTo(12f, 10f); verticalLineTo(16f)
                moveTo(9f, 13f); horizontalLineTo(15f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        icon("Copy") {
            stroke {
                roundRect(8f, 8f, 14f, 14f, 2f)
                moveTo(4f, 16f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(4f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineTo(14f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            }
        }
    }

    /** 移动到…（文件夹 + 箭头）。 */
    val FolderInput: ImageVector by lazy {
        icon("FolderInput") {
            stroke {
                moveTo(2f, 9f); verticalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineTo(10f)
                lineTo(12f, 6f)
                horizontalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(18f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(4f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(17f)
                moveTo(2f, 13f); horizontalLineTo(12f)
                moveTo(9f, 10f); lineTo(12f, 13f); lineTo(9f, 16f)
            }
        }
    }

    val Power: ImageVector by lazy {
        icon("Power") {
            stroke(2f) {
                moveTo(12f, 2f); verticalLineTo(12f)
                moveTo(18.36f, 6.64f)
                arcTo(9f, 9f, 0f, true, true, 5.64f, 6.64f)
            }
        }
    }

    val Play: ImageVector by lazy {
        icon("Play") { solid { moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close() } }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            solid { roundRect(6f, 4.5f, 4f, 15f, 1.2f); roundRect(14f, 4.5f, 4f, 15f, 1.2f) }
        }
    }

    val Stop: ImageVector by lazy {
        icon("Stop") { solid { roundRect(6f, 6f, 12f, 12f, 2f) } }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
            stroke {
                circle(12f, 12f, 10f)
                moveTo(12f, 16f); verticalLineTo(12f)
                moveTo(12f, 8f); lineTo(12.01f, 8f)
            }
        }
    }

    val Smartphone: ImageVector by lazy {
        icon("Smartphone") {
            stroke {
                roundRect(5f, 2f, 14f, 20f, 2.5f)
                moveTo(12f, 18f); lineTo(12.01f, 18f)
            }
        }
    }

    val Monitor: ImageVector by lazy {
        icon("Monitor") {
            stroke {
                roundRect(2f, 3f, 20f, 14f, 2f)
                moveTo(8f, 21f); horizontalLineTo(16f)
                moveTo(12f, 17f); verticalLineTo(21f)
            }
        }
    }

    val Clock: ImageVector by lazy {
        icon("Clock") {
            stroke {
                circle(12f, 12f, 10f)
                moveTo(12f, 6f); verticalLineTo(12f); lineTo(16f, 14f)
            }
        }
    }

    val Square: ImageVector by lazy {
        icon("Square") { stroke { roundRect(3f, 3f, 18f, 18f, 3f) } }
    }

    val CheckSquare: ImageVector by lazy {
        icon("CheckSquare") {
            stroke {
                roundRect(3f, 3f, 18f, 18f, 3f)
                moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f)
            }
        }
    }

    val Inbox: ImageVector by lazy {
        icon("Inbox") {
            stroke {
                moveTo(22f, 12f); horizontalLineTo(16f); lineTo(14f, 15f); horizontalLineTo(10f); lineTo(8f, 12f); horizontalLineTo(2f)
                moveTo(5.45f, 5.11f); lineTo(2f, 12f); verticalLineTo(18f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                horizontalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                verticalLineTo(12f)
                lineTo(18.55f, 5.11f)
                arcTo(2f, 2f, 0f, false, false, 16.76f, 4f)
                horizontalLineTo(7.24f)
                arcTo(2f, 2f, 0f, false, false, 5.45f, 5.11f)
                close()
            }
        }
    }

    /** 多文件 / 堆叠。 */
    val Layers: ImageVector by lazy {
        icon("Layers") {
            stroke {
                moveTo(12f, 2f); lineTo(2f, 7f); lineTo(12f, 12f); lineTo(22f, 7f); close()
                moveTo(2f, 17f); lineTo(12f, 22f); lineTo(22f, 17f)
                moveTo(2f, 12f); lineTo(12f, 17f); lineTo(22f, 12f)
            }
        }
    }

    val Tag: ImageVector by lazy {
        icon("Tag") {
            stroke {
                moveTo(12.6f, 2.6f)
                lineTo(21.4f, 11.4f)
                arcToRelative(2f, 2f, 0f, false, true, 0f, 2.8f)
                lineTo(14.2f, 21.4f)
                arcToRelative(2f, 2f, 0f, false, true, -2.8f, 0f)
                lineTo(2.6f, 12.6f)
                arcTo(2f, 2f, 0f, false, true, 2f, 11.2f)
                verticalLineTo(4f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineTo(11.2f)
                arcToRelative(2f, 2f, 0f, false, true, 1.4f, 0.6f)
                close()
            }
            solid { circle(7.5f, 7.5f, 1.4f) }
        }
    }

    val Lock: ImageVector by lazy {
        icon("Lock") {
            stroke {
                roundRect(3f, 11f, 18f, 11f, 2f)
                moveTo(7f, 11f); verticalLineTo(7f)
                arcTo(5f, 5f, 0f, false, true, 17f, 7f)
                verticalLineTo(11f)
            }
        }
    }

    val Link: ImageVector by lazy {
        icon("Link") {
            stroke {
                moveTo(10f, 13f)
                arcTo(5f, 5f, 0f, false, false, 17.54f, 13.54f)
                lineTo(20.54f, 10.54f)
                arcTo(5f, 5f, 0f, false, false, 13.47f, 3.47f)
                lineTo(11.75f, 5.18f)
                moveTo(14f, 11f)
                arcTo(5f, 5f, 0f, false, false, 6.46f, 10.46f)
                lineTo(3.46f, 13.46f)
                arcTo(5f, 5f, 0f, false, false, 10.53f, 20.53f)
                lineTo(12.24f, 18.82f)
            }
        }
    }

    val Grid: ImageVector by lazy {
        icon("Grid") {
            stroke {
                roundRect(3f, 3f, 7.5f, 7.5f, 1.5f)
                roundRect(13.5f, 3f, 7.5f, 7.5f, 1.5f)
                roundRect(3f, 13.5f, 7.5f, 7.5f, 1.5f)
                roundRect(13.5f, 13.5f, 7.5f, 7.5f, 1.5f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            stroke {
                circle(12f, 12f, 3f)
                // 简化齿轮：八角外轮廓
                moveTo(12f, 2.5f); lineTo(13.6f, 4.9f); lineTo(16.4f, 4.3f); lineTo(17f, 7.1f)
                lineTo(19.7f, 8.1f); lineTo(19f, 10.9f); lineTo(21.5f, 12f); lineTo(19f, 13.1f)
                lineTo(19.7f, 15.9f); lineTo(17f, 16.9f); lineTo(16.4f, 19.7f); lineTo(13.6f, 19.1f)
                lineTo(12f, 21.5f); lineTo(10.4f, 19.1f); lineTo(7.6f, 19.7f); lineTo(7f, 16.9f)
                lineTo(4.3f, 15.9f); lineTo(5f, 13.1f); lineTo(2.5f, 12f); lineTo(5f, 10.9f)
                lineTo(4.3f, 8.1f); lineTo(7f, 7.1f); lineTo(7.6f, 4.3f); lineTo(10.4f, 4.9f)
                close()
            }
        }
    }

    val Star: ImageVector by lazy {
        icon("Star") {
            stroke {
                moveTo(12f, 2.5f); lineTo(14.9f, 8.6f); lineTo(21.5f, 9.4f); lineTo(16.6f, 13.9f)
                lineTo(17.9f, 20.5f); lineTo(12f, 17.2f); lineTo(6.1f, 20.5f); lineTo(7.4f, 13.9f)
                lineTo(2.5f, 9.4f); lineTo(9.1f, 8.6f); close()
            }
        }
    }

    val Alert: ImageVector by lazy {
        icon("Alert") {
            stroke {
                moveTo(10.3f, 3.9f)
                lineTo(1.8f, 18f)
                arcToRelative(2f, 2f, 0f, false, false, 1.7f, 3f)
                horizontalLineTo(20.5f)
                arcToRelative(2f, 2f, 0f, false, false, 1.7f, -3f)
                lineTo(13.7f, 3.9f)
                arcToRelative(2f, 2f, 0f, false, false, -3.4f, 0f)
                close()
                moveTo(12f, 9f); verticalLineTo(13f)
                moveTo(12f, 17f); lineTo(12.01f, 17f)
            }
        }
    }

    val ZoomIn: ImageVector by lazy {
        icon("ZoomIn") {
            stroke {
                circle(11f, 11f, 8f)
                moveTo(21f, 21f); lineTo(16.65f, 16.65f)
                moveTo(11f, 8f); verticalLineTo(14f)
                moveTo(8f, 11f); horizontalLineTo(14f)
            }
        }
    }

    val ZoomOut: ImageVector by lazy {
        icon("ZoomOut") {
            stroke {
                circle(11f, 11f, 8f)
                moveTo(21f, 21f); lineTo(16.65f, 16.65f)
                moveTo(8f, 11f); horizontalLineTo(14f)
            }
        }
    }

    val Save: ImageVector by lazy {
        icon("Save") {
            stroke {
                moveTo(19f, 21f); horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineTo(16f)
                lineTo(21f, 8f)
                verticalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                close()
                moveTo(17f, 21f); verticalLineTo(13f); horizontalLineTo(7f); verticalLineTo(21f)
                moveTo(7f, 3f); verticalLineTo(8f); horizontalLineTo(15f)
            }
        }
    }
}
