package com.pocketdeck.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R

/** Inter 可变字体（opsz + wght），和 Beacon 保持一致。 */
val InterFamily = FontFamily(
    Font(
        R.font.inter,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.Setting("opsz", 14f),
        ),
    ),
)

val LocalDeckFontFamily = staticCompositionLocalOf { InterFamily }

val DeckFontFamily: FontFamily
    @Composable @ReadOnlyComposable get() = LocalDeckFontFamily.current

private fun fontStyle(family: FontFamily, weight: FontWeight, size: Int, lineHeight: Int = (size * 1.3).toInt()) =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
    )

object DeckType {
    /** 顶栏选中标签 */
    val tabSelected: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Bold, 16)

    /** 顶栏未选中标签 */
    val tabInactive: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Normal, 16)

    /** 时钟 & 电量 */
    val statusInfo: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Normal, 14)

    /** 焦点游戏标题 */
    val gameTitle: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.SemiBold, 22, 26)

    /** 元数据标签（Release date 等） */
    val metaLabel: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Normal, 10, 12)

    /** 元数据值 */
    val metaValue: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Normal, 14, 18)

    /** 底栏按钮 */
    val bottomBar: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Medium, 14)

    /** 游戏菜单标题 */
    val menuTitle: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.SemiBold, 20, 24)

    /** 游戏菜单按钮 */
    val menuButton: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Medium, 16)

    /** List 视图游戏名 */
    val listItem: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Normal, 16, 20)
    val listItemSelected: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.SemiBold, 16, 20)

    /** 搜索框 & 空态 */
    val searchHint: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Normal, 16)
    val emptyMessage: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Normal, 16)

    /** Settings */
    val settingsSection: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.SemiBold, 20, 24)
    val settingsLabel: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Normal, 12, 16)
    val settingsValue: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Normal, 16, 20)
    val settingsNav: TextStyle
        @Composable @ReadOnlyComposable get() = fontStyle(DeckFontFamily, FontWeight.Medium, 16, 20)
}
