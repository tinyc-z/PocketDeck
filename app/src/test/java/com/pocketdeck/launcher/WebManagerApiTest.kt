package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.CategorySortOrder
import com.pocketdeck.launcher.lan.CategoryValidator
import org.junit.Assert.assertEquals
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
}
