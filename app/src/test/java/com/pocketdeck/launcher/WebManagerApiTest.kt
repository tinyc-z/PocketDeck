package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.CategorySortOrder
import com.pocketdeck.launcher.data.ScrapeProgress
import com.pocketdeck.launcher.lan.CategoryValidator
import com.pocketdeck.launcher.lan.WebManagerServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebManagerApiTest {

    @Test
    fun categoryValidatorAcceptsValidPayload() {
        val json = """
            {
              "categories": [
                {
                  "id": "cat:rpg",
                  "name": "RPG Classics",
                  "sortOrder": "TitleAsc",
                  "sources": ["gba", "psx"],
                  "games": ["/storage/emulated/0/ROMs/gba/Mother3.gba"]
                }
              ],
              "tabOrder": ["now", "favs", "cat:rpg", "other"],
              "hiddenTabs": ["other"],
              "nowTab": true,
              "favsTab": true
            }
        """.trimIndent()

        val result = CategoryValidator.validate(json, emptyMap())
        assertTrue("Expected Ok, got $result", result is CategoryValidator.Result.Ok)
        val cfg = (result as CategoryValidator.Result.Ok).config
        assertEquals(1, cfg.categories.size)
        assertEquals("cat:rpg", cfg.categories.first().id)
        assertEquals("RPG Classics", cfg.categories.first().name)
        assertEquals(CategorySortOrder.TitleAsc, cfg.categories.first().sortOrder)
        assertEquals(listOf("gba", "psx"), cfg.categories.first().sources)
        assertTrue("other" in cfg.hiddenTabs)
    }

    @Test
    fun categoryValidatorRejectsPathTraversalAndReservedId() {
        val badIdJson = """
            {
              "categories": [
                { "id": "now", "name": "Conflict" }
              ]
            }
        """.trimIndent()
        val r1 = CategoryValidator.validate(badIdJson, emptyMap())
        assertTrue(r1 is CategoryValidator.Result.Error)
        assertEquals("reserved_category_id", (r1 as CategoryValidator.Result.Error).code)

        val badGameJson = """
            {
              "categories": [
                { "id": "cat:bad", "name": "Bad", "games": ["/roms/../../etc/passwd"] }
              ]
            }
        """.trimIndent()
        val r2 = CategoryValidator.validate(badGameJson, emptyMap())
        assertTrue(r2 is CategoryValidator.Result.Error)
        assertEquals("invalid_game_id", (r2 as CategoryValidator.Result.Error).code)
    }

    @Test
    fun webBatchValidatorParsesValidAndRejectsInvalidPayloads() {
        val okJson = """
            {
              "action": "setCategories",
              "ids": ["/roms/gba/a.gba", "/roms/gba/b.gba", "/roms/gba/a.gba"],
              "categoryIds": ["cat:rpg"]
            }
        """.trimIndent()
        val parsed = com.pocketdeck.launcher.lan.WebBatchValidator.parse(okJson)
        assertEquals("setCategories", parsed?.action)
        assertEquals(listOf("/roms/gba/a.gba", "/roms/gba/b.gba"), parsed?.ids)
        assertEquals(listOf("cat:rpg"), parsed?.categoryIds)

        assertEquals(null, com.pocketdeck.launcher.lan.WebBatchValidator.parse("""{"action":"deleteAll","ids":["a"]}"""))
        assertEquals(null, com.pocketdeck.launcher.lan.WebBatchValidator.parse("""{"action":"hide","ids":[]}"""))
        assertTrue(com.pocketdeck.launcher.lan.WebBatchValidator.isSafePlatformId("gba"))
        assertTrue(!com.pocketdeck.launcher.lan.WebBatchValidator.isSafePlatformId("../etc"))
    }

    @Test
    fun buildScrapeProgressJsonSerializesRunningCancelledAndIdleStates() {
        val idle = WebManagerServer.buildScrapeProgressJson(ScrapeProgress())
        assertFalse(idle.getBoolean("running"))
        assertEquals(0, idle.getInt("done"))
        assertEquals(0, idle.getInt("total"))
        assertEquals(0, idle.getInt("percent"))
        assertEquals(0L, idle.getLong("finishedAtMs"))

        val running = WebManagerServer.buildScrapeProgressJson(
            ScrapeProgress(
                running = true,
                done = 12,
                total = 48,
                currentTitle = "Zelda",
                currentPlatform = "GBA",
                updatedCount = 10,
                failedCount = 2,
                cancelled = false,
                finishedAtMs = 0L,
            ),
        )
        assertTrue(running.getBoolean("running"))
        assertEquals(12, running.getInt("done"))
        assertEquals(48, running.getInt("total"))
        assertEquals(25, running.getInt("percent"))
        assertEquals("Zelda", running.getString("currentTitle"))
        assertEquals("GBA", running.getString("currentPlatform"))
        assertEquals(10, running.getInt("updatedCount"))
        assertEquals(2, running.getInt("failedCount"))
        assertFalse(running.getBoolean("cancelled"))

        val cancelled = WebManagerServer.buildScrapeProgressJson(
            ScrapeProgress(
                running = false,
                done = 20,
                total = 48,
                updatedCount = 15,
                failedCount = 5,
                cancelled = true,
                finishedAtMs = 1700000000000L,
            ),
        )
        assertFalse(cancelled.getBoolean("running"))
        assertTrue(cancelled.getBoolean("cancelled"))
        assertEquals(41, cancelled.getInt("percent"))
        assertEquals(1700000000000L, cancelled.getLong("finishedAtMs"))
    }
}
