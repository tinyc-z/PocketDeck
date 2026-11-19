package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * PocketDeck 统一纤细线性扁平图标库（Lucide 24×24 几何规范，1.7dp 细线 + 圆润端点）。
 * 零外部重型依赖，天然支持明暗模式与主题色着色。
 */
object DeckIcons {

    private inline fun strokeIcon(
        name: String,
        strokeWidth: Float = 1.75f,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

    private inline fun ImageVector.Builder.strokePath(
        strokeWidth: Float = 1.75f,
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

    /** 圆（两段弧闭合）。 */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    /** 圆角矩形。 */
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

    /** 主页（房子） */
    val Home: ImageVector by lazy {
        strokeIcon("Home") {
            strokePath {
                moveTo(3.5f, 10.5f); lineTo(12f, 3.5f); lineTo(20.5f, 10.5f)
                verticalLineTo(19.5f); curveTo(20.5f, 20.05f, 20.05f, 20.5f, 19.5f, 20.5f)
                horizontalLineTo(15f); verticalLineTo(14.5f); horizontalLineTo(9f); verticalLineTo(20.5f)
                horizontalLineTo(4.5f); curveTo(3.95f, 20.5f, 3.5f, 20.05f, 3.5f, 19.5f); close()
            }
        }
    }

    /** 游戏库（书架） */
    val Library: ImageVector by lazy {
        strokeIcon("Library") {
            strokePath {
                roundRect(3f, 4f, 7f, 16f, 1.5f)
                roundRect(12f, 4f, 4f, 16f, 1f)
                moveTo(18f, 5f); lineTo(21f, 5.8f); lineTo(17.4f, 19.8f); lineTo(14.9f, 19.1f)
            }
        }
    }

    /** 封面与信息（图片） */
    val Image: ImageVector by lazy {
        strokeIcon("Image") {
            strokePath {
                roundRect(3f, 4f, 18f, 16f, 2.5f)
                circle(9f, 10f, 2f)
                moveTo(21f, 16f); lineTo(16f, 11f); lineTo(7f, 20f)
            }
        }
    }

    /** 信息 */
    val Info: ImageVector by lazy {
        strokeIcon("Info") {
            strokePath {
                circle(12f, 12f, 9f)
                moveTo(12f, 11f); verticalLineTo(16.5f)
            }
            strokePath(strokeWidth = 2.2f) { moveTo(12f, 7.8f); lineTo(12.01f, 7.8f) }
        }
    }

    /** 左箭头（返回） */
    val ChevronLeft: ImageVector by lazy {
        strokeIcon("ChevronLeft") {
            strokePath(strokeWidth = 2.2f) {
                moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f)
            }
        }
    }

    /** 加号（新建 / 添加） */
    val Plus: ImageVector by lazy {
        strokeIcon("Plus") {
            strokePath(strokeWidth = 2f) {
                moveTo(12f, 5f); verticalLineTo(19f)
                moveTo(5f, 12f); horizontalLineTo(19f)
            }
        }
    }

    /** 分享 */
    val Share: ImageVector by lazy {
        strokeIcon("Share") {
            strokePath {
                circle(18f, 5f, 2.8f)
                circle(6f, 12f, 2.8f)
                circle(18f, 19f, 2.8f)
                moveTo(8.5f, 13.4f); lineTo(15.5f, 17.6f)
                moveTo(15.5f, 6.4f); lineTo(8.5f, 10.6f)
            }
        }
    }

    /** 音量（界面音效） */
    val Volume: ImageVector by lazy {
        strokeIcon("Volume") {
            strokePath {
                moveTo(11f, 5f); lineTo(6.5f, 9f); horizontalLineTo(3.5f); verticalLineTo(15f)
                horizontalLineTo(6.5f); lineTo(11f, 19f); close()
                moveTo(15.5f, 8.8f); curveTo(17.2f, 10.6f, 17.2f, 13.4f, 15.5f, 15.2f)
                moveTo(18.4f, 6f); curveTo(21.6f, 9.4f, 21.6f, 14.6f, 18.4f, 18f)
            }
        }
    }

    /** 时钟 */
    val Clock: ImageVector by lazy {
        strokeIcon("Clock") {
            strokePath {
                circle(12f, 12f, 9f)
                moveTo(12f, 7f); verticalLineTo(12f); lineTo(15.2f, 14f)
            }
        }
    }

    /** 无线网络 */
    val Wifi: ImageVector by lazy {
        strokeIcon("Wifi") {
            strokePath {
                moveTo(2.5f, 9f); curveTo(7.8f, 4.3f, 16.2f, 4.3f, 21.5f, 9f)
                moveTo(5.5f, 12.5f); curveTo(9.1f, 9.4f, 14.9f, 9.4f, 18.5f, 12.5f)
                moveTo(8.7f, 16f); curveTo(10.6f, 14.4f, 13.4f, 14.4f, 15.3f, 16f)
            }
            strokePath(strokeWidth = 2.4f) { moveTo(12f, 19.5f); lineTo(12.01f, 19.5f) }
        }
    }

    /** 可见 */
    val Eye: ImageVector by lazy {
        strokeIcon("Eye") {
            strokePath {
                moveTo(2.5f, 12f); curveTo(4.6f, 7.8f, 8f, 5.5f, 12f, 5.5f)
                curveTo(16f, 5.5f, 19.4f, 7.8f, 21.5f, 12f)
                curveTo(19.4f, 16.2f, 16f, 18.5f, 12f, 18.5f)
                curveTo(8f, 18.5f, 4.6f, 16.2f, 2.5f, 12f); close()
                circle(12f, 12f, 3f)
            }
        }
    }

    /** 隐藏 */
    val EyeOff: ImageVector by lazy {
        strokeIcon("EyeOff") {
            strokePath {
                moveTo(9.9f, 5.8f); curveTo(10.6f, 5.6f, 11.3f, 5.5f, 12f, 5.5f)
                curveTo(16f, 5.5f, 19.4f, 7.8f, 21.5f, 12f)
                curveTo(20.9f, 13.2f, 20.2f, 14.2f, 19.4f, 15.1f)
                moveTo(14.1f, 14.1f); curveTo(12.9f, 15.3f, 11f, 15.3f, 9.9f, 14.1f)
                curveTo(8.7f, 12.9f, 8.7f, 11f, 9.9f, 9.9f)
                moveTo(17.1f, 17.1f); curveTo(15.6f, 18f, 13.8f, 18.5f, 12f, 18.5f)
                curveTo(8f, 18.5f, 4.6f, 16.2f, 2.5f, 12f)
                curveTo(3.4f, 10.2f, 4.6f, 8.6f, 6.1f, 7.4f)
                moveTo(3.5f, 3.5f); lineTo(20.5f, 20.5f)
            }
        }
    }

    /** 收藏（心形） */
    val Heart: ImageVector by lazy {
        strokeIcon("Heart") {
            strokePath {
                moveTo(12f, 20f)
                curveTo(5f, 15.5f, 2.5f, 12f, 2.5f, 8.6f)
                curveTo(2.5f, 5.7f, 4.7f, 3.5f, 7.4f, 3.5f)
                curveTo(9.3f, 3.5f, 10.9f, 4.6f, 12f, 6.3f)
                curveTo(13.1f, 4.6f, 14.7f, 3.5f, 16.6f, 3.5f)
                curveTo(19.3f, 3.5f, 21.5f, 5.7f, 21.5f, 8.6f)
                curveTo(21.5f, 12f, 19f, 15.5f, 12f, 20f)
                close()
            }
        }
    }

    /** 下载 */
    val Download: ImageVector by lazy {
        strokeIcon("Download") {
            strokePath {
                moveTo(12f, 4f); verticalLineTo(15f)
                moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f)
                moveTo(4f, 16.5f); verticalLineTo(19f); curveTo(4f, 19.55f, 4.45f, 20f, 5f, 20f)
                horizontalLineTo(19f); curveTo(19.55f, 20f, 20f, 19.55f, 20f, 19f); verticalLineTo(16.5f)
            }
        }
    }

    /** 上传 */
    val Upload: ImageVector by lazy {
        strokeIcon("Upload") {
            strokePath {
                moveTo(12f, 15f); verticalLineTo(4f)
                moveTo(7.5f, 8.5f); lineTo(12f, 4f); lineTo(16.5f, 8.5f)
                moveTo(4f, 16.5f); verticalLineTo(19f); curveTo(4f, 19.55f, 4.45f, 20f, 5f, 20f)
                horizontalLineTo(19f); curveTo(19.55f, 20f, 20f, 19.55f, 20f, 19f); verticalLineTo(16.5f)
            }
        }
    }

    /** 设备（掌机 / 系统） */
    val Device: ImageVector by lazy {
        strokeIcon("Device") {
            strokePath {
                roundRect(6f, 2.5f, 12f, 19f, 2.5f)
                moveTo(10f, 18.5f); horizontalLineTo(14f)
            }
        }
    }

    /** 操控（摇杆 / 准星） */
    val Joystick: ImageVector by lazy {
        strokeIcon("Joystick") {
            strokePath {
                circle(12f, 12f, 8f)
                circle(12f, 12f, 3f)
                moveTo(12f, 3f); verticalLineTo(6f)
                moveTo(12f, 18f); verticalLineTo(21f)
                moveTo(3f, 12f); horizontalLineTo(6f)
                moveTo(18f, 12f); horizontalLineTo(21f)
            }
        }
    }

    /** 外观（调色板 / 主题视觉） */
    val Palette: ImageVector by lazy {
        strokeIcon("Palette") {
            strokePath {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveTo(6.48f, 22f, 12f, 22f)
                curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f)
                curveTo(14f, 19.5f, 13.8f, 19.05f, 13.5f, 18.7f)
                curveTo(13.2f, 18.35f, 13f, 17.9f, 13f, 17.4f)
                curveTo(13f, 16.3f, 13.9f, 15.4f, 15f, 15.4f)
                horizontalLineTo(17f)
                curveTo(19.76f, 15.4f, 22f, 13.16f, 22f, 10.4f)
                curveTo(22f, 5.76f, 17.52f, 2f, 12f, 2f)
                close()
            }
            strokePath(strokeWidth = 2.1f) {
                moveTo(7.5f, 10.5f); lineTo(7.51f, 10.5f)
                moveTo(10.5f, 7.2f); lineTo(10.51f, 7.2f)
                moveTo(14.8f, 7.5f); lineTo(14.81f, 7.5f)
                moveTo(17.5f, 10.8f); lineTo(17.51f, 10.8f)
            }
        }
    }

    /** 主页与状态栏（仪表布局） */
    val LayoutGrid: ImageVector by lazy {
        strokeIcon("LayoutGrid") {
            strokePath {
                // 左上
                moveTo(4f, 5f); curveTo(4f, 4.45f, 4.45f, 4f, 5f, 4f)
                horizontalLineTo(9.5f); curveTo(10.05f, 4f, 10.5f, 4.45f, 10.5f, 5f)
                verticalLineTo(9.5f); curveTo(10.5f, 10.05f, 10.05f, 10.5f, 9.5f, 10.5f)
                horizontalLineTo(5f); curveTo(4.45f, 10.5f, 4f, 10.05f, 4f, 9.5f); close()
                // 右上
                moveTo(13.5f, 5f); curveTo(13.5f, 4.45f, 13.95f, 4f, 14.5f, 4f)
                horizontalLineTo(19f); curveTo(19.55f, 4f, 20f, 4.45f, 20f, 5f)
                verticalLineTo(9.5f); curveTo(20f, 10.05f, 19.55f, 10.5f, 19f, 10.5f)
                horizontalLineTo(14.5f); curveTo(13.95f, 10.5f, 13.5f, 10.05f, 13.5f, 9.5f); close()
                // 左下
                moveTo(4f, 14.5f); curveTo(4f, 13.95f, 4.45f, 13.5f, 5f, 13.5f)
                horizontalLineTo(9.5f); curveTo(10.05f, 13.5f, 10.5f, 13.95f, 10.5f, 14.5f)
                verticalLineTo(19f); curveTo(10.5f, 19.55f, 10.05f, 20f, 9.5f, 20f)
                horizontalLineTo(5f); curveTo(4.45f, 20f, 4f, 19.55f, 4f, 19f); close()
                // 右下
                moveTo(13.5f, 14.5f); curveTo(13.5f, 13.95f, 13.95f, 13.5f, 14.5f, 13.5f)
                horizontalLineTo(19f); curveTo(19.55f, 13.5f, 20f, 13.95f, 20f, 14.5f)
                verticalLineTo(19f); curveTo(20f, 19.55f, 19.55f, 20f, 19f, 20f)
                horizontalLineTo(14.5f); curveTo(13.95f, 20f, 13.5f, 19.55f, 13.5f, 19f); close()
            }
        }
    }

    /** 游戏库与刮削（文件夹） */
    val Folder: ImageVector by lazy {
        strokeIcon("Folder") {
            strokePath {
                moveTo(3f, 6.5f)
                curveTo(3f, 5.4f, 3.9f, 4.5f, 5f, 4.5f)
                horizontalLineTo(9.2f)
                lineTo(11.2f, 7f)
                horizontalLineTo(19f)
                curveTo(20.1f, 7f, 21f, 7.9f, 21f, 9f)
                verticalLineTo(17.5f)
                curveTo(21f, 18.6f, 20.1f, 19.5f, 19f, 19.5f)
                horizontalLineTo(5f)
                curveTo(3.9f, 19.5f, 3f, 18.6f, 3f, 17.5f)
                close()
            }
        }
    }

    /** 启动器（芯片 / 引擎核心） */
    val Cpu: ImageVector by lazy {
        strokeIcon("Cpu") {
            strokePath {
                moveTo(7f, 6f); curveTo(6.45f, 6f, 6f, 6.45f, 6f, 7f)
                verticalLineTo(17f); curveTo(6f, 17.55f, 6.45f, 18f, 7f, 18f)
                horizontalLineTo(17f); curveTo(17.55f, 18f, 18f, 17.55f, 18f, 17f)
                verticalLineTo(7f); curveTo(18f, 6.45f, 17.55f, 6f, 17f, 6f); close()
                moveTo(10f, 10f); horizontalLineTo(14f); verticalLineTo(14f); horizontalLineTo(10f); close()
                // 引脚
                moveTo(9.5f, 3f); verticalLineTo(6f)
                moveTo(14.5f, 3f); verticalLineTo(6f)
                moveTo(9.5f, 18f); verticalLineTo(21f)
                moveTo(14.5f, 18f); verticalLineTo(21f)
                moveTo(3f, 9.5f); horizontalLineTo(6f)
                moveTo(3f, 14.5f); horizontalLineTo(6f)
                moveTo(18f, 9.5f); horizontalLineTo(21f)
                moveTo(18f, 14.5f); horizontalLineTo(21f)
            }
        }
    }

    /** 操控与音效（手柄） */
    val Gamepad: ImageVector by lazy {
        strokeIcon("Gamepad") {
            strokePath {
                moveTo(6.5f, 7.5f)
                horizontalLineTo(17.5f)
                curveTo(19.98f, 7.5f, 21.5f, 9.5f, 21.5f, 12.2f)
                curveTo(21.5f, 15.1f, 20.1f, 17.2f, 18.2f, 17.2f)
                curveTo(16.9f, 17.2f, 16.1f, 16.2f, 15.2f, 15.2f)
                horizontalLineTo(8.8f)
                curveTo(7.9f, 16.2f, 7.1f, 17.2f, 5.8f, 17.2f)
                curveTo(3.9f, 17.2f, 2.5f, 15.1f, 2.5f, 12.2f)
                curveTo(2.5f, 9.5f, 4.02f, 7.5f, 6.5f, 7.5f)
                close()
                // 十字键
                moveTo(6.5f, 11.8f); horizontalLineTo(9.9f)
                moveTo(8.2f, 10.1f); verticalLineTo(13.5f)
            }
            strokePath(strokeWidth = 2.1f) {
                moveTo(15.2f, 12.6f); lineTo(15.21f, 12.6f)
                moveTo(17.4f, 10.9f); lineTo(17.41f, 10.9f)
            }
        }
    }

    /** 系统与关于（调节滑块 / 通用信息） */
    val Sliders: ImageVector by lazy {
        strokeIcon("Sliders") {
            strokePath {
                moveTo(4f, 7f); horizontalLineTo(8f)
                moveTo(12f, 7f); horizontalLineTo(20f)
                moveTo(4f, 12f); horizontalLineTo(14f)
                moveTo(18f, 12f); horizontalLineTo(20f)
                moveTo(4f, 17f); horizontalLineTo(9f)
                moveTo(13f, 17f); horizontalLineTo(20f)
                // 圆点滑块
                moveTo(10f, 5f); curveTo(11.1f, 5f, 12f, 5.9f, 12f, 7f)
                curveTo(12f, 8.1f, 11.1f, 9f, 10f, 9f)
                curveTo(8.9f, 9f, 8f, 8.1f, 8f, 7f)
                curveTo(8f, 5.9f, 8.9f, 5f, 10f, 5f)
                moveTo(16f, 10f); curveTo(17.1f, 10f, 18f, 10.9f, 18f, 12f)
                curveTo(18f, 13.1f, 17.1f, 14f, 16f, 14f)
                curveTo(14.9f, 14f, 14f, 13.1f, 14f, 12f)
                curveTo(14f, 10.9f, 14.9f, 10f, 16f, 10f)
                moveTo(11f, 15f); curveTo(12.1f, 15f, 13f, 15.9f, 13f, 17f)
                curveTo(13f, 18.1f, 12.1f, 19f, 11f, 19f)
                curveTo(9.9f, 19f, 9f, 18.1f, 9f, 17f)
                curveTo(9f, 15.9f, 9.9f, 15f, 11f, 15f)
            }
        }
    }

    /** 搜索 */
    val Search: ImageVector by lazy {
        strokeIcon("Search") {
            strokePath {
                moveTo(11f, 4f)
                curveTo(14.87f, 4f, 18f, 7.13f, 18f, 11f)
                reflectiveCurveTo(14.87f, 18f, 11f, 18f)
                reflectiveCurveTo(4f, 14.87f, 4f, 11f)
                reflectiveCurveTo(7.13f, 4f, 11f, 4f)
                moveTo(20.5f, 20.5f); lineTo(16.2f, 16.2f)
            }
        }
    }

    /** 拖拽排序手柄 */
    val GripHorizontal: ImageVector by lazy {
        strokeIcon("GripHorizontal") {
            strokePath {
                moveTo(5f, 9f); horizontalLineTo(19f)
                moveTo(5f, 15f); horizontalLineTo(19f)
            }
        }
    }

    /** 勾选 */
    val Check: ImageVector by lazy {
        strokeIcon("Check", strokeWidth = 2.0f) {
            strokePath(strokeWidth = 2.0f) {
                moveTo(5f, 12.5f); lineTo(9.5f, 17f); lineTo(19f, 7.5f)
            }
        }
    }

    /** 关闭 */
    val Close: ImageVector by lazy {
        strokeIcon("Close", strokeWidth = 1.9f) {
            strokePath(strokeWidth = 1.9f) {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }
    }

    /** 右箭头 */
    val ChevronRight: ImageVector by lazy {
        strokeIcon("ChevronRight") {
            strokePath {
                moveTo(9.5f, 6.5f); lineTo(15f, 12f); lineTo(9.5f, 17.5f)
            }
        }
    }

    /** 存储设备 / 硬盘 */
    val HardDrive: ImageVector by lazy {
        strokeIcon("HardDrive") {
            strokePath {
                moveTo(4f, 14f); horizontalLineTo(20f)
                moveTo(5.5f, 6f); horizontalLineTo(18.5f)
                lineTo(20.5f, 14f); verticalLineTo(17.5f)
                curveTo(20.5f, 18.3f, 19.8f, 19f, 19f, 19f)
                horizontalLineTo(5f)
                curveTo(4.2f, 19f, 3.5f, 18.3f, 3.5f, 17.5f)
                verticalLineTo(14f); close()
                moveTo(7.2f, 16.5f); lineTo(7.21f, 16.5f)
                moveTo(10.2f, 16.5f); lineTo(10.21f, 16.5f)
            }
        }
    }

    /** 普通文件 */
    val FileText: ImageVector by lazy {
        strokeIcon("FileText") {
            strokePath {
                moveTo(6.5f, 3.5f); horizontalLineTo(14f); lineTo(18.5f, 8f); verticalLineTo(19.5f)
                curveTo(18.5f, 20.05f, 18.05f, 20.5f, 17.5f, 20.5f)
                horizontalLineTo(6.5f); curveTo(5.95f, 20.5f, 5.5f, 20.05f, 5.5f, 19.5f)
                verticalLineTo(4.5f); curveTo(5.5f, 3.95f, 5.95f, 3.5f, 6.5f, 3.5f); close()
                moveTo(14f, 3.5f); verticalLineTo(8f); horizontalLineTo(18.5f)
                moveTo(8.5f, 12.5f); horizontalLineTo(15.5f)
                moveTo(8.5f, 16f); horizontalLineTo(13.5f)
            }
        }
    }

    /** 压缩包 */
    val Archive: ImageVector by lazy {
        strokeIcon("Archive") {
            strokePath {
                moveTo(4f, 5f); horizontalLineTo(20f); verticalLineTo(9f); horizontalLineTo(4f); close()
                moveTo(5.5f, 9f); verticalLineTo(18.5f)
                curveTo(5.5f, 19.3f, 6.2f, 20f, 7f, 20f)
                horizontalLineTo(17f)
                curveTo(17.8f, 20f, 18.5f, 19.3f, 18.5f, 18.5f)
                verticalLineTo(9f)
                moveTo(10f, 13f); horizontalLineTo(14f)
            }
        }
    }

    /** 音频 */
    val Music: ImageVector by lazy {
        strokeIcon("Music") {
            strokePath {
                moveTo(9f, 17.5f); verticalLineTo(6.5f); lineTo(19f, 4.5f); verticalLineTo(15.5f)
                moveTo(7f, 15.5f)
                curveTo(8.1f, 15.5f, 9f, 16.4f, 9f, 17.5f)
                reflectiveCurveTo(8.1f, 19.5f, 7f, 19.5f)
                reflectiveCurveTo(5f, 18.6f, 5f, 17.5f)
                reflectiveCurveTo(5.9f, 15.5f, 7f, 15.5f)
                moveTo(17f, 13.5f)
                curveTo(18.1f, 13.5f, 19f, 14.4f, 19f, 15.5f)
                reflectiveCurveTo(18.1f, 17.5f, 17f, 17.5f)
                reflectiveCurveTo(15f, 16.6f, 15f, 15.5f)
                reflectiveCurveTo(15.9f, 13.5f, 17f, 13.5f)
            }
        }
    }

    /** 局域网 / 无线电波 */
    val Radio: ImageVector by lazy {
        strokeIcon("Radio") {
            strokePath {
                moveTo(12f, 12f); lineTo(12.01f, 12f)
                moveTo(8.5f, 15.5f); curveTo(6.6f, 13.6f, 6.6f, 10.4f, 8.5f, 8.5f)
                moveTo(15.5f, 8.5f); curveTo(17.4f, 10.4f, 17.4f, 13.6f, 15.5f, 15.5f)
                moveTo(5.5f, 18.5f); curveTo(2f, 15f, 2f, 9f, 5.5f, 5.5f)
                moveTo(18.5f, 5.5f); curveTo(22f, 9f, 22f, 15f, 18.5f, 18.5f)
            }
        }
    }

    /** 随机 / 洗牌 */
    val Shuffle: ImageVector by lazy {
        strokeIcon("Shuffle") {
            strokePath {
                moveTo(16f, 4f); lineTo(20f, 4f); lineTo(20f, 8f)
                moveTo(4f, 19f); lineTo(20f, 4f)
                moveTo(20f, 16f); lineTo(20f, 20f); lineTo(16f, 20f)
                moveTo(14.5f, 14.5f); lineTo(20f, 20f)
                moveTo(4f, 5f); lineTo(9.5f, 10.5f)
            }
        }
    }

    /** 编辑 / 铅笔 */
    val Pencil: ImageVector by lazy {
        strokeIcon("Pencil") {
            strokePath {
                moveTo(16.5f, 3.5f); lineTo(20.5f, 7.5f); lineTo(8.5f, 19.5f); lineTo(4f, 20.5f); lineTo(5f, 16f); close()
            }
        }
    }

    /** 发送 / 快传 */
    val Send: ImageVector by lazy {
        strokeIcon("Send") {
            strokePath {
                moveTo(20.5f, 3.5f); lineTo(10.5f, 13.5f)
                moveTo(20.5f, 3.5f); lineTo(14f, 20.5f); lineTo(10.5f, 13.5f); lineTo(3.5f, 10f); close()
            }
        }
    }

    /** 刷新 */
    val Refresh: ImageVector by lazy {
        strokeIcon("Refresh") {
            strokePath {
                moveTo(20f, 11f)
                curveTo(19.5f, 7f, 16.1f, 4f, 12f, 4f)
                curveTo(8.6f, 4f, 5.7f, 6.1f, 4.5f, 9.1f)
                moveTo(4f, 4.5f); verticalLineTo(9.2f); horizontalLineTo(8.7f)
                moveTo(4f, 13f)
                curveTo(4.5f, 17f, 7.9f, 20f, 12f, 20f)
                curveTo(15.4f, 20f, 18.3f, 17.9f, 19.5f, 14.9f)
                moveTo(20f, 19.5f); verticalLineTo(14.8f); horizontalLineTo(15.3f)
            }
        }
    }

    /** 循环播放 */
    val Repeat: ImageVector by lazy {
        strokeIcon("Repeat") {
            strokePath {
                moveTo(17f, 3f); lineTo(20.5f, 6.5f); lineTo(17f, 10f)
                moveTo(4f, 11f); verticalLineTo(9.5f); curveTo(4f, 7.8f, 5.3f, 6.5f, 7f, 6.5f); horizontalLineTo(20.5f)
                moveTo(7f, 21f); lineTo(3.5f, 17.5f); lineTo(7f, 14f)
                moveTo(20f, 13f); verticalLineTo(14.5f); curveTo(20f, 16.2f, 18.7f, 17.5f, 17f, 17.5f); horizontalLineTo(3.5f)
            }
        }
    }

    /** 清理缓存 / 删除（Lucide trash-2） */
    val Trash: ImageVector by lazy {
        strokeIcon("Trash") {
            strokePath {
                moveTo(3f, 6f); horizontalLineTo(21f)
                moveTo(19f, 6f); verticalLineTo(20f)
                curveTo(19f, 21f, 18f, 22f, 17f, 22f)
                horizontalLineTo(7f)
                curveTo(6f, 22f, 5f, 21f, 5f, 20f)
                verticalLineTo(6f)
                moveTo(8f, 6f); verticalLineTo(4f)
                curveTo(8f, 3f, 9f, 2f, 10f, 2f)
                horizontalLineTo(14f)
                curveTo(15f, 2f, 16f, 3f, 16f, 4f)
                verticalLineTo(6f)
                moveTo(10f, 11f); verticalLineTo(17f)
                moveTo(14f, 11f); verticalLineTo(17f)
            }
        }
    }

    /** 浏览器 / 网页管理 */
    val Globe: ImageVector by lazy {
        strokeIcon("Globe") {
            strokePath {
                moveTo(12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                reflectiveCurveTo(16.97f, 21f, 12f, 21f)
                reflectiveCurveTo(3f, 16.97f, 3f, 12f)
                reflectiveCurveTo(7.03f, 3f, 12f, 3f)
                moveTo(3.5f, 12f); horizontalLineTo(20.5f)
                moveTo(12f, 3f); curveTo(14.6f, 5.5f, 15.8f, 8.7f, 15.8f, 12f)
                curveTo(15.8f, 15.3f, 14.6f, 18.5f, 12f, 21f)
                moveTo(12f, 3f); curveTo(9.4f, 5.5f, 8.2f, 8.7f, 8.2f, 12f)
                curveTo(8.2f, 15.3f, 9.4f, 18.5f, 12f, 21f)
            }
        }
    }
}

/** 轻量纤细矢量图标绘制组件（使用 [rememberVectorPainter] 着色渲染）。 */
@Composable
fun DeckIcon(
    icon: ImageVector,
    tint: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val painter = rememberVectorPainter(icon)
    Canvas(modifier = modifier.size(size)) {
        with(painter) {
            draw(
                size = this@Canvas.size,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
            )
        }
    }
}
