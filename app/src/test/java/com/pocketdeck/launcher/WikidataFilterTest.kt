package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.ScraperClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikidataFilterTest {

    @Test
    fun `accepts mainstream and indie game descriptions including Super Tilt Bro`() {
        assertTrue(ScraperClient.isWikidataGameDescription("WTFPL, optionally-networked versus-platformer for the NES"))
        assertTrue(ScraperClient.isWikidataGameDescription("1985 platform video game developed by Nintendo"))
        assertTrue(ScraperClient.isWikidataGameDescription("single-player action game for GBA"))
        assertTrue(ScraperClient.isWikidataGameDescription("2004 Japanese visual novel"))
        assertTrue(ScraperClient.isWikidataGameDescription("NES homebrew roguelike"))
        assertTrue(ScraperClient.isWikidataGameDescription("1991年发行的电子游戏"))
    }

    @Test
    fun `rejects non-game entities and series entries`() {
        assertFalse(ScraperClient.isWikidataGameDescription("video game series published by Nintendo"))
        assertFalse(ScraperClient.isWikidataGameDescription("1992 American animated musical fantasy film"))
        assertFalse(ScraperClient.isWikidataGameDescription("soundtrack album by Koji Kondo"))
        assertFalse(ScraperClient.isWikidataGameDescription("fictional character in the Mario franchise"))
        assertFalse(ScraperClient.isWikidataGameDescription("Japanese video game developer"))
        assertFalse(ScraperClient.isWikidataGameDescription("日本电子游戏系列"))
        assertFalse(ScraperClient.isWikidataGameDescription(""))
    }

    @Test
    fun `fallback candidate requires high similarity and non-excluded description`() {
        assertTrue(ScraperClient.isWikidataFallbackCandidate("", 0.95))
        assertTrue(ScraperClient.isWikidataFallbackCandidate("2020 NES software release", 0.92))
        assertFalse(ScraperClient.isWikidataFallbackCandidate("", 0.85))
        assertFalse(ScraperClient.isWikidataFallbackCandidate("1992 American animated film", 1.0))
        assertFalse(ScraperClient.isWikidataFallbackCandidate("video game series", 1.0))
    }
}
