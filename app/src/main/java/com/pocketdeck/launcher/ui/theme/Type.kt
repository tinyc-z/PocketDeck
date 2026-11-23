package com.pocketdeck.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R

/** Inter 可变字体（按各字重显式注册 wght/opsz 变体，确保 Medium/SemiBold/Bold/ExtraBold 真正生效）。 */
val InterFamily = FontFamily(
    Font(
        R.font.inter,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.Setting("opsz", 14f),
        ),
    ),
    Font(
        R.font.inter,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.Setting("opsz", 14f),
        ),
    ),
    Font(
        R.font.inter,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.Setting("opsz", 16f),
        ),
    ),
    Font(
        R.font.inter,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.Setting("opsz", 20f),
        ),
    ),
    Font(
        R.font.inter,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(800),
            FontVariation.Setting("opsz", 24f),
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

@Immutable
private class DeckTypography(family: FontFamily) {
    val tabSelected = fontStyle(family, FontWeight.Bold, 16)
    val tabInactive = fontStyle(family, FontWeight.Normal, 16)
    val statusInfo = fontStyle(family, FontWeight.Normal, 14)
    val gameTitle = fontStyle(family, FontWeight.SemiBold, 22, 26)
    val metaLabel = fontStyle(family, FontWeight.Normal, 10, 12)
    val metaValue = fontStyle(family, FontWeight.Normal, 14, 18)
    val bottomBar = fontStyle(family, FontWeight.Medium, 14)
    val menuTitle = fontStyle(family, FontWeight.SemiBold, 20, 24)
    val menuButton = fontStyle(family, FontWeight.Medium, 16)
    val listItem = fontStyle(family, FontWeight.Normal, 16, 20)
    val listItemSelected = fontStyle(family, FontWeight.SemiBold, 16, 20)
    val searchHint = fontStyle(family, FontWeight.Normal, 16)
    val emptyMessage = fontStyle(family, FontWeight.Normal, 16)
    val settingsSection = fontStyle(family, FontWeight.SemiBold, 20, 24)
    val settingsLabel = fontStyle(family, FontWeight.Normal, 12, 16)
    val settingsValue = fontStyle(family, FontWeight.Normal, 16, 20)
    val settingsNav = fontStyle(family, FontWeight.Medium, 16, 20)
}

object DeckType {
    private val cache = ConcurrentHashMap<FontFamily, DeckTypography>(4)

    @Composable
    @ReadOnlyComposable
    private fun current(): DeckTypography {
        val family = DeckFontFamily
        return cache[family] ?: cache.computeIfAbsent(family) { DeckTypography(it) }
    }

    /** 顶栏选中标签 */
    val tabSelected: TextStyle
        @Composable @ReadOnlyComposable get() = current().tabSelected

    /** 顶栏未选中标签 */
    val tabInactive: TextStyle
        @Composable @ReadOnlyComposable get() = current().tabInactive

    /** 时钟 & 电量 */
    val statusInfo: TextStyle
        @Composable @ReadOnlyComposable get() = current().statusInfo

    /** 焦点游戏标题 */
    val gameTitle: TextStyle
        @Composable @ReadOnlyComposable get() = current().gameTitle

    /** 元数据标签（Release date 等） */
    val metaLabel: TextStyle
        @Composable @ReadOnlyComposable get() = current().metaLabel

    /** 元数据值 */
    val metaValue: TextStyle
        @Composable @ReadOnlyComposable get() = current().metaValue

    /** 底栏按钮 */
    val bottomBar: TextStyle
        @Composable @ReadOnlyComposable get() = current().bottomBar

    /** 游戏菜单标题 */
    val menuTitle: TextStyle
        @Composable @ReadOnlyComposable get() = current().menuTitle

    /** 游戏菜单按钮 */
    val menuButton: TextStyle
        @Composable @ReadOnlyComposable get() = current().menuButton

    /** List 视图游戏名 */
    val listItem: TextStyle
        @Composable @ReadOnlyComposable get() = current().listItem
    val listItemSelected: TextStyle
        @Composable @ReadOnlyComposable get() = current().listItemSelected

    /** 搜索框 & 空态 */
    val searchHint: TextStyle
        @Composable @ReadOnlyComposable get() = current().searchHint
    val emptyMessage: TextStyle
        @Composable @ReadOnlyComposable get() = current().emptyMessage

    /** Settings */
    val settingsSection: TextStyle
        @Composable @ReadOnlyComposable get() = current().settingsSection
    val settingsLabel: TextStyle
        @Composable @ReadOnlyComposable get() = current().settingsLabel
    val settingsValue: TextStyle
        @Composable @ReadOnlyComposable get() = current().settingsValue
    val settingsNav: TextStyle
        @Composable @ReadOnlyComposable get() = current().settingsNav
}
