package com.pocketdeck.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.pocketdeck.launcher.data.ThemeAccent

data class DeckPalette(
    val isDark: Boolean,
    val ruby: Color,
    val rubyLight: Color,
    val rubyFaded: Color,
    val accentGlow: Color,
    val accentTertiary: Color,
    val background: Color,
    val surface: Color,
    val cardBorder: Color,
    val cardShadow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textLabel: Color,
    val textMeta: Color,
    val pillSelected: Color,
    val pillText: Color,
    val pillTextInactive: Color,
    val bottomBarBg: Color,
    val buttonBadge: Color,
    val buttonBadgeText: Color,
    val favSelected: Color,
    val dialogBg: Color,
    val dialogBorder: Color,
    // ── Sunset Arcade 设计令牌 ──
    /** 品牌渐变三色（选中 Tab、A 键、开关、主按钮、焦点环）。 */
    val brandStart: Color,
    val brandMid: Color,
    val brandEnd: Color,
    /** 次级表面：输入框、Chip、分组内嵌块。 */
    val surfaceAlt: Color,
    /** 发丝分割线 / 中性描边。 */
    val line: Color,
    /** 柔和强调底色（选中行、Tag 背景）。 */
    val accentSoft: Color,
    /** 背景流光的三个色块（随主题变化）。 */
    val flowA: Color,
    val flowB: Color,
    val flowC: Color,
)

fun deckPalette(accent: ThemeAccent, isDark: Boolean): DeckPalette {
    val primary = Color(if (isDark) accent.darkPrimary else accent.lightPrimary)
    val gStart = Color(accent.gradStart)
    val gMid = Color(accent.gradMid)
    val gEnd = Color(accent.gradEnd)
    val lightAccent = lerp(primary, Color.White, if (isDark) 0.34f else 0.38f)
    val borderAccent = lerp(primary, Color.White, if (isDark) 0.26f else 0.42f)
    // 流光色块：浅色模式取渐变色与白色混合的粉彩，深色模式取低明度饱和色
    val violet = Color(0xFF7C4DCC)

    return if (isDark) {
        DeckPalette(
            isDark = true,
            ruby = primary,
            rubyLight = lightAccent,
            rubyFaded = primary.copy(alpha = 0.20f),
            accentGlow = gStart,
            accentTertiary = gEnd,
            background = Color(0xFF17111F),
            surface = Color(0xFF231A2E),
            cardBorder = borderAccent,
            cardShadow = Color(0x99000000),
            textPrimary = Color(0xFFF7F0FA),
            textSecondary = Color(0xFFB7A9C2),
            textLabel = Color(0xFF7E7090),
            textMeta = Color(0xFFD3C7DC),
            pillSelected = primary.copy(alpha = 0.22f),
            pillText = primary,
            pillTextInactive = Color(0xFF8E80A0),
            bottomBarBg = Color(0x00000000),
            buttonBadge = Color(0xFFF7F0FA),
            buttonBadgeText = Color(0xFF17111F),
            favSelected = gMid,
            dialogBg = Color(0xFF211929),
            dialogBorder = Color(0xFF3A2D48),
            brandStart = gStart,
            brandMid = gMid,
            brandEnd = gEnd,
            surfaceAlt = Color(0xFF2B2138),
            line = Color(0xFF3A2D48),
            accentSoft = primary.copy(alpha = 0.16f),
            flowA = gStart.copy(alpha = 0.42f),
            flowB = gEnd.copy(alpha = 0.40f),
            flowC = violet.copy(alpha = 0.50f),
        )
    } else {
        DeckPalette(
            isDark = false,
            ruby = primary,
            rubyLight = lightAccent,
            rubyFaded = primary.copy(alpha = 0.14f),
            accentGlow = gStart,
            accentTertiary = gEnd,
            background = Color(0xFFFFF8F4),
            surface = Color(0xFFFFFFFF),
            cardBorder = borderAccent,
            cardShadow = Color(0x33782846),
            textPrimary = Color(0xFF24172F),
            textSecondary = Color(0xFF6E6176),
            textLabel = Color(0xFFA89CAE),
            textMeta = Color(0xFF5A4D63),
            pillSelected = primary.copy(alpha = 0.12f),
            pillText = primary,
            pillTextInactive = Color(0xFF9A8EA2),
            bottomBarBg = Color(0x00000000),
            buttonBadge = Color(0xFF24172F),
            buttonBadgeText = Color.White,
            favSelected = gMid,
            dialogBg = Color(0xFFFFFCFA),
            dialogBorder = Color(0xFFF1E3DE),
            brandStart = gStart,
            brandMid = gMid,
            brandEnd = gEnd,
            surfaceAlt = Color(0xFFFBF1EE),
            line = Color(0xFFF1E3DE),
            accentSoft = primary.copy(alpha = 0.10f),
            flowA = lerp(gStart, Color.White, 0.35f).copy(alpha = 0.60f),
            flowB = lerp(gEnd, Color.White, 0.45f).copy(alpha = 0.55f),
            flowC = lerp(violet, Color.White, 0.55f).copy(alpha = 0.45f),
        )
    }
}

val LightDeckPalette = deckPalette(ThemeAccent.Sunset, isDark = false)
val DarkDeckPalette = deckPalette(ThemeAccent.Sunset, isDark = true)

val LocalDeckColors = staticCompositionLocalOf { LightDeckPalette }

/** PocketDeck 动态主题色板（支持亮/暗模式与 8 种预设主题色实时联动）。 */
object DeckColors {
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.isDark

    val Ruby: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.ruby

    val RubyLight: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.rubyLight

    val RubyFaded: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.rubyFaded

    val AccentGlow: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.accentGlow

    val AccentTertiary: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.accentTertiary

    val Background: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.background

    val Surface: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.surface

    val CardBorder: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.cardBorder

    val CardShadow: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.cardShadow

    val TextPrimary: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.textPrimary

    val TextSecondary: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.textSecondary

    val TextLabel: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.textLabel

    val TextMeta: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.textMeta

    val PillSelected: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.pillSelected

    val PillText: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.pillText

    val PillTextInactive: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.pillTextInactive

    val BottomBarBg: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.bottomBarBg

    val ButtonBadge: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.buttonBadge

    val ButtonBadgeText: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.buttonBadgeText

    val FavSelected: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.favSelected

    val DialogBg: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.dialogBg

    val DialogBorder: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.dialogBorder

    val BrandStart: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.brandStart

    val BrandMid: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.brandMid

    val BrandEnd: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.brandEnd

    val SurfaceAlt: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.surfaceAlt

    val Line: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.line

    val AccentSoft: Color
        @Composable @ReadOnlyComposable get() = LocalDeckColors.current.accentSoft
}
