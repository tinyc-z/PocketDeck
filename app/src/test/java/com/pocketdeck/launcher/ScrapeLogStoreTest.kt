package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.ScrapeLogStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapeLogStoreTest {

    @Test
    fun sanitizeRedactsSecretsInQueryStrings() {
        val raw = "https://api.example.com/v1?devpassword=secret123&apikey=KEY_999&game=mario&token=tok_abc"
        val cleaned = ScrapeLogStore.sanitize(raw)
        assertFalse(cleaned.contains("secret123"))
        assertFalse(cleaned.contains("KEY_999"))
        assertFalse(cleaned.contains("tok_abc"))
        assertTrue(cleaned.contains("devpassword=***"))
        assertTrue(cleaned.contains("apikey=***"))
        assertTrue(cleaned.contains("token=***"))
        assertTrue(cleaned.contains("game=mario"))
    }

    @Test
    fun nodeFromUrlIdentifiesKnownSources() {
        assertEquals("libretro", ScrapeLogStore.nodeFromUrl("https://thumbnails.libretro.com/Nintendo/box.png"))
        assertEquals("jsdelivr", ScrapeLogStore.nodeFromUrl("https://cdn.jsdelivr.net/gh/libretro-thumbnails/a/b.png"))
        assertEquals("hasheous", ScrapeLogStore.nodeFromUrl("https://hasheous.org/api/v1/Lookup"))
        assertEquals("gametdb", ScrapeLogStore.nodeFromUrl("https://art.gametdb.com/ds/cover.png"))
        assertEquals("steamgriddb", ScrapeLogStore.nodeFromUrl("https://www.steamgriddb.com/api/v2/search"))
    }
}
