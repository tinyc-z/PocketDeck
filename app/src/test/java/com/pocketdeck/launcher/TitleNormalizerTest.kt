package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.TitleNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleNormalizerTest {

    @Test
    fun stripsBracketsAndLeadingIndex() {
        assertEquals(
            "Super Mario World",
            TitleNormalizer.stripRomTags("001 - Super Mario World (USA) [!].sfc".removeSuffix(".sfc")),
        )
        assertEquals(
            "Chrono Trigger",
            TitleNormalizer.stripRomTags("No.042_Chrono_Trigger_(Japan)_[T+Chi]"),
        )
    }

    @Test
    fun movesTrailingArticleWithoutBreakingThemeWords() {
        assertEquals(
            "The Legend of Zelda: A Link to the Past",
            TitleNormalizer.stripRomTags("Legend of Zelda, The: A Link to the Past (USA)"),
        )
        assertEquals(
            "Theme Hospital",
            TitleNormalizer.stripRomTags("Theme Hospital (Europe)"),
        )
    }

    @Test
    fun stripsCjkPackTagsAndVersions() {
        assertEquals(
            "火焰纹章 圣魔之光石",
            TitleNormalizer.stripRomTags("火焰纹章 圣魔之光石 [简体中文] 完美汉化版 v1.2"),
        )
        assertEquals(
            "宝可梦 绿宝石",
            TitleNormalizer.stripRomTags("宝可梦 绿宝石 (简中) 无敌版"),
        )
    }

    @Test
    fun foldsDiacriticsAndNormalizesRomanNumerals() {
        assertEquals("pokemon emerald", TitleNormalizer.normalizedTitle("Pokémon Emerald (USA)"))
        assertEquals("okami", TitleNormalizer.normalizedTitle("Ōkami (USA)"))
        assertEquals("final fantasy 7", TitleNormalizer.normalizedTitle("Final Fantasy VII (USA)"))
        assertEquals("finalfantasy7", TitleNormalizer.compactKey("Final Fantasy VII (Disc 1)"))
    }

    @Test
    fun preservesJapaneseKanaAndKoreanHangul() {
        val jp = TitleNormalizer.normalizedTitle("クロノ・トリガー (Japan)")
        assertTrue("Expected kana preserved, got: $jp", jp.contains("クロノ"))
        val kr = TitleNormalizer.normalizedTitle("포켓몬스터 (Korea)")
        assertTrue("Expected hangul preserved, got: $kr", kr.contains("포켓몬스터"))
    }

    @Test
    fun buildQueryVariantsIncludesRomanAndArabicForms() {
        val variants = TitleNormalizer.buildQueryVariants(
            baseName = "Final Fantasy VII (USA)",
            title = "Final Fantasy VII",
        )
        assertTrue(variants.any { it.equals("Final Fantasy 7", ignoreCase = true) })
        assertTrue(variants.any { it.equals("Final Fantasy VII", ignoreCase = true) })
    }

    @Test
    fun stripsHackAndChsTagsAndExpandsCjkAliases() {
        assertEquals(
            "时空之轮",
            TitleNormalizer.stripRomTags("时空之轮 [CHS] Hack v1.3"),
        )
        val aliases = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("时空之轮 [CHS]")
        assertTrue("Expected Chrono Trigger in $aliases", aliases.contains("Chrono Trigger"))
        val ff7 = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("最终幻想7 汉化版")
        assertTrue("Expected Final Fantasy VII in $ff7", ff7.contains("Final Fantasy VII"))
    }

    @Test
    fun playStatsCodecRoundTrips() {
        val input = mapOf(
            "/roms/gba/metroid.gba" to com.pocketdeck.launcher.data.PlayStats(
                playCount = 5,
                totalPlayMs = 3_600_000L,
                lastPlayedAt = 1_700_000_000_000L,
            ),
        )
        val encoded = com.pocketdeck.launcher.data.UserStore.PlayStatsCodec.encodePlayStats(input)
        val decoded = com.pocketdeck.launcher.data.UserStore.PlayStatsCodec.decodePlayStats(encoded)
        assertEquals(input, decoded)
    }

    @Test
    fun updateCheckerComparesSemverCorrectly() {
        assertTrue(com.pocketdeck.launcher.data.UpdateChecker.isNewerVersion("v0.6.0", "0.5.1"))
        assertTrue(com.pocketdeck.launcher.data.UpdateChecker.isNewerVersion("1.0.0", "0.9.9"))
        assertTrue(!com.pocketdeck.launcher.data.UpdateChecker.isNewerVersion("0.5.1", "0.5.1"))
        assertTrue(!com.pocketdeck.launcher.data.UpdateChecker.isNewerVersion("0.5.0", "0.5.1"))
    }
}
