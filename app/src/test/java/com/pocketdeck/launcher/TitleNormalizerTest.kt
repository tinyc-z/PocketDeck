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

    @Test
    fun matchesTrailingVersionAndEditionSuffixes() {
        val goldSim = TitleNormalizer.similarity(
            "Pokemon Gold (Taiwan) (En) (Unl)",
            "Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible)",
        )
        assertTrue("Expected high similarity for Pokemon Gold vs Gold Version, got $goldSim", goldSim >= 0.90)

        val silverSim = TitleNormalizer.similarity(
            "Pokemon Gold (Taiwan) (En) (Unl)",
            "Pokemon - Silver Version (USA, Europe)",
        )
        assertTrue("Expected low similarity for Pokemon Gold vs Silver Version, got $silverSim", silverSim < 0.70)
    }

    @Test
    fun suggestsKeywordsForPartialChineseInput() {
        val suggestions = com.pocketdeck.launcher.data.CjkAliases.suggestKeywords("超级马里", limit = 8)
        assertTrue("Expected 超级马里奥合集 in $suggestions", suggestions.contains("超级马里奥合集"))
        assertTrue("Expected 超级马里奥世界 in $suggestions", suggestions.contains("超级马里奥世界"))
    }

    @Test
    fun expandsMultiPlatformAliasesAndSequelsWithoutShortWordFalsePositives() {
        val mg = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("合金装备")
        assertTrue("Expected both Metal Gear & Metal Gear Solid in $mg", mg.contains("Metal Gear") && mg.contains("Metal Gear Solid"))

        val kirby = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("星之卡比-梦之泉的物语")
        assertTrue("Expected Kirby's Adventure in $kirby", kirby.contains("Kirby's Adventure"))

        val dd2 = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("双截龙2-复仇")
        assertTrue("Expected Double Dragon II - The Revenge in $dd2", dd2.contains("Double Dragon II - The Revenge"))
        assertTrue("Must not pollute Double Dragon 1 into $dd2", !dd2.contains("Double Dragon"))

        val dq3 = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("勇者斗恶龙3-罪恶渊源")
        assertTrue("Expected Dragon Quest III in $dq3", dq3.contains("Dragon Quest III"))

        val sfcSequel = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("勇者斗恶龙6-幻之大地")
        assertTrue("Expected Dragon Quest 6 extracted before subtitle in $sfcSequel", sfcSequel.contains("Dragon Quest 6"))

        val twinbee3 = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("兵蜂3（战机）")
        assertTrue("Expected TwinBee 3 in $twinbee3", twinbee3.any { it.startsWith("TwinBee 3") })

        val gunNac = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("加纳战机(战机天花板)")
        assertTrue("Expected Gun-Nac in $gunNac", gunNac.contains("Gun-Nac"))

        val bucky = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("外星战将")
        assertTrue("Expected Bucky O'Hare in $bucky", bucky.contains("Bucky O'Hare"))

        // 短词与系列前缀边界防误配
        val falseGodOfWar = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("飞天战神")
        assertTrue("飞天战神 must NOT match God of War, got $falseGodOfWar", falseGodOfWar.isEmpty())

        val falseRtk = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("傲视三国志")
        assertTrue("傲视三国志 must NOT match Romance of the Three Kingdoms, got $falseRtk", falseRtk.isEmpty())

        val falseFatalFrame = com.pocketdeck.launcher.data.CjkAliases.expandEnglishAliases("零式战机")
        assertTrue("零式战机 must NOT match Fatal Frame, got $falseFatalFrame", falseFatalFrame.isEmpty())
    }
}
