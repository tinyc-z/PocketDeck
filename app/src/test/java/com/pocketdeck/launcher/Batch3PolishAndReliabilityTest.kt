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

        // 确认 HttpResponseWriter 合并默认头与 HTML_SECURITY_HEADERS 时不输出重复安全头
        val out = java.io.ByteArrayOutputStream()
        com.pocketdeck.launcher.lan.HttpResponseWriter(out).sendBytes(
            status = 200,
            contentType = "text/html; charset=utf-8",
            bytes = "<html></html>".toByteArray(),
            extraHeaders = headers,
        )
        val rawHeaders = out.toString(Charsets.UTF_8.name()).substringBefore("\r\n\r\n")
        val lines = rawHeaders.lines().map { it.substringBefore(':').lowercase() }
        assertEquals(1, lines.count { it == "x-content-type-options" })
        assertEquals(1, lines.count { it == "x-frame-options" })
        assertEquals(1, lines.count { it == "referrer-policy" })
        assertEquals(1, lines.count { it == "content-security-policy" })
    }

    @Test
    fun romIdentityRowPreservesRomSize() {
        val row = ScrapeDatabase.RomIdentityRow(crc32 = "DEADBEEF", sha1 = null, serial = "NTR-ABCD", romSize = 16_777_216L)
        assertFalse(row.isEmpty)
        assertEquals(16_777_216L, row.romSize)

        val emptyRow = ScrapeDatabase.RomIdentityRow(romSize = 0L)
        assertTrue(emptyRow.isEmpty)
    }

    @Test
    fun localPrefsStoreSkipsDiskWriteOnNoOpEdit() {
        val dir = tmp.newFolder("prefs_noop")
        val target = File(dir, "user_prefs.json").apply {
            writeText("""{"v":1,"s":{"k":"v1"}}""")
        }
        val pinnedMtime = target.lastModified() - 5_000L
        target.setLastModified(pinnedMtime)

        val store = LocalPrefsStore(targetFile = target)
        LocalPrefsStore::class.java.getDeclaredField("cachedMap").apply { isAccessible = true }
            .set(store, mapOf("k" to "v1"))
        val key = com.pocketdeck.launcher.data.stringPrefsKey("k")
        kotlinx.coroutines.runBlocking {
            // 写入相同值（no-op）：若未跳过落盘则在纯 JVM stub 下 AtomicFile 会抛 NPE 或改写 mtime
            store.edit { it[key] = "v1" }
        }
        assertEquals("No-op edit must skip disk rewrite", pinnedMtime, target.lastModified())
    }

    @Test
    fun systemFontsDisplayNameUsesFamilyCacheWithoutDiskStat() {
        val missingPath = File(tmp.root, "nonexistent_cached_font.ttf").absolutePath
        val cacheField = com.pocketdeck.launcher.ui.theme.SystemFonts::class.java
            .getDeclaredField("familyCache")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(com.pocketdeck.launcher.ui.theme.SystemFonts) as MutableMap<String, androidx.compose.ui.text.font.FontFamily>
        val id = "file:$missingPath"
        cache[id] = androidx.compose.ui.text.font.FontFamily.SansSerif
        try {
            val label = com.pocketdeck.launcher.ui.theme.SystemFonts.displayName(
                fontId = id,
                defaultLabel = "Default",
                missingFmt = "%s (MISSING)",
            )
            assertEquals("nonexistent cached font", label)
            assertFalse("Cache-hit displayName must not fall back to disk stat missing suffix", label.contains("MISSING"))
        } finally {
            cache.remove(id)
        }
    }
}
