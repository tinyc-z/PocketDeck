package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.LocalPrefsStore
import com.pocketdeck.launcher.data.ScrapeDatabase
import com.pocketdeck.launcher.lan.WebManagerServer
import com.pocketdeck.launcher.ui.components.DpadNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * UT-22 · 第 3 批持久化容错、手柄导航排序与 Web 安全响应头回归测试。
 */
class Batch3PolishAndReliabilityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun localPrefsStoreDecodeJsonIsResilientPerKey() {
        // 单键类型损坏（b 中混入字符串、s 中混入数字、ss 中混入非字符串、m 中混入数字）不应连累其它正常键
        val raw = """
            {
              "v": 1,
              "b": {"goodBool": true, "badBool": "not_a_bool"},
              "s": {"goodStr": "hello", "badStr": 12345},
              "ss": {"goodSet": ["a", 99, "b"], "badSet": "nope"},
              "m": {"goodMap": {"k1": "v1", "k2": 42}, "badMap": [1, 2]}
            }
        """.trimIndent()

        val decoded = LocalPrefsStore.decodeJson(raw)
        assertEquals(true, decoded["goodBool"])
        assertFalse(decoded.containsKey("badBool"))
        assertEquals("hello", decoded["goodStr"])
        assertFalse(decoded.containsKey("badStr"))
        assertEquals(setOf("a", "b"), decoded["goodSet"])
        assertFalse(decoded.containsKey("badSet"))
        assertEquals(mapOf("k1" to "v1"), decoded["goodMap"])
        assertFalse(decoded.containsKey("badMap"))
    }

    @Test
    fun localPrefsStoreBackupCorruptFileCreatesTimestampedBackup() {
        val dir = tmp.newFolder("prefs")
        val target = File(dir, "user_prefs.json").apply { writeText("{corrupted json!!!") }
        val store = LocalPrefsStore(targetFile = target)

        store.backupCorruptFile(target)

        val backups = dir.listFiles { f -> f.name.startsWith("user_prefs.json.corrupt.") }.orEmpty()
        assertEquals(1, backups.size)
        assertEquals("{corrupted json!!!", backups.first().readText())
    }

    @Test
    fun dpadNavControllerSortsItemsByExplicitOrderRegardlessOfRegistrationOrder() {
        val nav = DpadNavController()
        val triggered = mutableListOf<String>()

        nav.actions["third"] = { triggered += "third" }
        nav.orders["third"] = 30
        nav.actions["first"] = { triggered += "first" }
        nav.orders["first"] = 10
        nav.actions["second"] = { triggered += "second" }
        nav.orders["second"] = 20

        nav.ensureDefault()
        assertEquals("first", nav.focusedId)
        nav.move(1)
        assertEquals("second", nav.focusedId)
        nav.move(1)
        assertEquals("third", nav.focusedId)
        assertTrue(nav.triggerCurrent())
        assertEquals(listOf("third"), triggered)

        // 边界钳位：尾部再下移仍停留在最后一项
        nav.move(1)
        assertEquals("third", nav.focusedId)
        nav.move(-2)
        assertEquals("first", nav.focusedId)
    }

    @Test
    fun webManagerHtmlSecurityHeadersProvideCspAndClickjackingDefense() {
        val headers = WebManagerServer.HTML_SECURITY_HEADERS
        val csp = headers["Content-Security-Policy"].orEmpty()
        assertTrue(csp.contains("default-src 'self'"))
        assertTrue(csp.contains("object-src 'none'"))
        assertTrue(csp.contains("frame-ancestors 'none'"))
        assertEquals("nosniff", headers["X-Content-Type-Options"])
        assertEquals("DENY", headers["X-Frame-Options"])
    }

    @Test
    fun romIdentityRowPreservesRomSize() {
        val row = ScrapeDatabase.RomIdentityRow(crc32 = "DEADBEEF", sha1 = null, serial = "NTR-ABCD", romSize = 16_777_216L)
        assertFalse(row.isEmpty)
        assertEquals(16_777_216L, row.romSize)

        val emptyRow = ScrapeDatabase.RomIdentityRow(romSize = 0L)
        assertTrue(emptyRow.isEmpty)
    }
}
