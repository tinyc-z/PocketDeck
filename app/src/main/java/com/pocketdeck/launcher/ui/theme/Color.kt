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
)

fun deckPalette(accent: ThemeAccent, isDark: Boolean): DeckPalette {
    val primary = Color(if (isDark) accent.darkPrimary else accent.lightPrimary)
    val glow = Color(if (isDark) accent.darkGlow else accent.lightGlow)
    val tertiary = Color(if (isDark) accent.darkTertiary else accent.lightTertiary)
    val lightAccent = lerp(primary, Color.White, if (isDark) 0.34f else 0.38f)
    val borderAccent = lerp(primary, Color.White, if (isDark) 0.26f else 0.42f)

    return if (isDark) {
        DeckPalette(
            isDark = true,
            ruby = primary,
            rubyLight = lightAccent,
            rubyFaded = primary.copy(alpha = 0.22f),
            accentGlow = glow,
            accentTertiary = tertiary,
            background = Color(0xFF121215),
            surface = Color(0xFF1E1E24),
            cardBorder = borderAccent,
            cardShadow = Color(0x99000000),
            textPrimary = Color(0xFFF2F2F6),
            textSecondary = Color(0xFFA0A0AA),
            textLabel = Color(0xFF7E7E8A),
            textMeta = Color(0xFFC4C4CE),
            pillSelected = primary.copy(alpha = 0.22f),
            pillText = primary,
            pillTextInactive = Color(0xFF888894),
            bottomBarBg = Color(0x00000000),
            buttonBadge = Color(0xFFE6E6EC),
            buttonBadgeText = Color(0xFF141418),
            favSelected = primary,
            dialogBg = Color(0xFF1A1A20),
            dialogBorder = Color(0xFF2E2E37),
        )
    } else {
        DeckPalette(
            isDark = false,
            ruby = primary,
            rubyLight = lightAccent,
            rubyFaded = primary.copy(alpha = 0.18f),
            accentGlow = glow,
            accentTertiary = tertiary,
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF4F4F4),
            cardBorder = borderAccent,
            cardShadow = Color(0x40000000),
            textPrimary = Color(0xFF1A1A1A),
            textSecondary = Color(0xFF757575),
            textLabel = Color(0xFFB0B0B0),
            textMeta = Color(0xFF606060),
            pillSelected = primary.copy(alpha = 0.13f),
            pillText = primary,
            pillTextInactive = Color(0xFF8C8C94),
            bottomBarBg = Color(0x00000000),
            buttonBadge = Color(0xFF1A1A1A),
            buttonBadgeText = Color.White,
            favSelected = primary,
            dialogBg = Color(0xFFFAFAFA),
            dialogBorder = Color(0xFFE0E0E0),
        )
    }
}

val LightDeckPalette = deckPalette(ThemeAccent.Ruby, isDark = false)
val DarkDeckPalette = deckPalette(ThemeAccent.Ruby, isDark = true)

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
}
