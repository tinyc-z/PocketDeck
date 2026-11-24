package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.ArchiveManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun fileTypeHelpersAndSuggestedFolderNameWork() {
        assertTrue(ArchiveManager.isArchive(tmp.newFile("game.zip")))
        assertTrue(ArchiveManager.isArchive(tmp.newFile("game.7z")))
        assertFalse(ArchiveManager.isArchive(tmp.newFile("game.gba")))
        assertTrue(ArchiveManager.isImageFile(tmp.newFile("cover.png")))
        assertTrue(ArchiveManager.isAudioFile(tmp.newFile("bgm.mp3")))
        assertTrue(ArchiveManager.isKnownTextFile(tmp.newFile("retroarch.cfg")))
        assertEquals("bundle", ArchiveManager.suggestedFolderName(File("bundle.zip")))
        assertEquals("pack", ArchiveManager.suggestedFolderName(File("pack.tar.gz")))
    }

    @Test
    fun compressInspectAndExtractRoundTrip() = runBlocking {
        val srcDir = tmp.newFolder("src")
        val sub = File(srcDir, "sub").apply { mkdirs() }
        File(srcDir, "a.txt").writeText("hello pocketdeck")
        File(sub, "b.cfg").writeText("key=val")

        val zipFile = File(tmp.root, "out.zip")
        val compRes = ArchiveManager.compressToZip(listOf(srcDir), zipFile)
        assertTrue("compress failed: ${compRes.exceptionOrNull()}", compRes.isSuccess)
        assertTrue(zipFile.isFile && zipFile.length() > 0L)

        val entries = ArchiveManager.inspect(zipFile).getOrThrow()
        assertTrue(entries.any { it.name.endsWith("a.txt") })
        assertTrue(entries.any { it.name.endsWith("b.cfg") })

        val outDir = tmp.newFolder("extracted")
        val extRes = ArchiveManager.extract(zipFile, outDir)
        assertTrue("extract failed: ${extRes.exceptionOrNull()}", extRes.isSuccess)
        assertEquals("hello pocketdeck", File(outDir, "src/a.txt").readText())
        assertEquals("key=val", File(outDir, "src/sub/b.cfg").readText())
    }

    @Test
    fun extractBlocksZipSlipPathTraversal() = runBlocking {
        val evilZip = File(tmp.root, "evil.zip")
        ZipOutputStream(evilZip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("../../escaped.txt"))
            zos.write("malicious".toByteArray())
            zos.closeEntry()
        }
        val outDir = tmp.newFolder("safe_out")
        val res = ArchiveManager.extract(evilZip, outDir)
        assertTrue("Zip Slip must fail", res.isFailure)
        assertFalse(File(tmp.root, "escaped.txt").exists())
    }

    @Test
    fun rarInspectAndExtractReportsStreamingProgress() = runBlocking {
        val payload = ByteArray(512 * 1024) { idx -> (idx * 31).toByte() }
        val rarFile = File(tmp.root, "game.rar").apply {
            writeBytes(buildStoredRar4(listOf("rom.bin" to payload)))
        }

        val entries = ArchiveManager.inspect(rarFile).getOrThrow()
        assertEquals(1, entries.size)
        assertEquals("rom.bin", entries[0].name)
        assertEquals(payload.size.toLong(), entries[0].size)

        val snapshots = mutableListOf<Pair<Long, Long>>()
        val outDir = tmp.newFolder("rar_out")
        val extRes = ArchiveManager.extract(
            file = rarFile,
            destDir = outDir,
            onBytesProgress = { _, bytesDone, totalBytes ->
                snapshots += bytesDone to totalBytes
            },
            progressThrottleMs = 0L,
        )
        assertTrue("extract failed: ${extRes.exceptionOrNull()}", extRes.isSuccess)
        assertEquals(1, extRes.getOrThrow())
        assertTrue("Expected multiple progress updates, got ${snapshots.size}", snapshots.size >= 2)
        assertTrue(
            "Every snapshot must carry exact totalBytes=${payload.size}, got $snapshots",
            snapshots.all { (_, total) -> total == payload.size.toLong() },
        )
        assertTrue(
            "Expected intermediate progress between 0 and ${payload.size}, got $snapshots",
            snapshots.any { (done, total) -> done in 1L until total },
        )
        assertEquals(payload.size.toLong() to payload.size.toLong(), snapshots.last())

        val extracted = File(outDir, "rom.bin")
        assertTrue(extracted.isFile)
        assertTrue(extracted.readBytes().contentEquals(payload))
    }

    @Test
    fun decodeAndEncodeTextSupportsGbkBig5ShiftJisUtf16AndTruncatedUtf8() {
        // 1. GBK 中文文本识别与回写保护（含新增不可编码字符时无损升级为 UTF-8）
        val gbkCharset = Charset.forName("GBK")
        val gbkText = "PSX虹吸战士3\r\n1）全部枪无限子弹。\r\n2）不减血[不是无敌]。"
        val gbkDec = ArchiveManager.decodeTextBytes(gbkText.toByteArray(gbkCharset))
        assertEquals("GBK", gbkDec.label)
        assertFalse(gbkDec.hasBom)
        assertEquals(gbkText, gbkDec.text)
        assertFalse('\uFFFD' in gbkDec.text)

        val editedGbk = "PSX虹吸战士3\r\n3）全任务开启。"
        val reEncodedGbk = ArchiveManager.encodeTextPreserveCharset(editedGbk, gbkDec.charset, gbkDec.hasBom)
        assertEquals(editedGbk, String(reEncodedGbk, gbkCharset))

        val upgradedBytes = ArchiveManager.encodeTextPreserveCharset("$editedGbk 🎮", gbkDec.charset, gbkDec.hasBom)
        val upgradedDec = ArchiveManager.decodeTextBytes(upgradedBytes)
        assertEquals("UTF-8", upgradedDec.label)
        assertEquals("$editedGbk 🎮", upgradedDec.text)

        // 2. UTF-8 BOM 与截断多字节尾巴保护
        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "中文UTF8".toByteArray(StandardCharsets.UTF_8)
        val bomDec = ArchiveManager.decodeTextBytes(bomBytes)
        assertEquals("UTF-8-BOM", bomDec.label)
        assertTrue(bomDec.hasBom)
        assertEquals("中文UTF8", bomDec.text)
        assertTrue(ArchiveManager.encodeTextPreserveCharset("中文UTF8", bomDec.charset, bomDec.hasBom).contentEquals(bomBytes))

        val truncRaw = "测试截断字符".toByteArray(StandardCharsets.UTF_8)
        val truncDec = ArchiveManager.decodeTextBytes(truncRaw, length = 5, truncated = true)
        assertEquals("UTF-8", truncDec.label)
        assertEquals("测", truncDec.text)
        assertFalse('\uFFFD' in truncDec.text)

        // 3. UTF-16LE BOM 识别及 looksLikeTextFile 放行
        val utf16Bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "掌机说明".toByteArray(StandardCharsets.UTF_16LE)
        val utf16Dec = ArchiveManager.decodeTextBytes(utf16Bytes)
        assertEquals("UTF-16LE", utf16Dec.label)
        assertTrue(utf16Dec.hasBom)
        assertEquals("掌机说明", utf16Dec.text)
        val utf16UnknownExt = File(tmp.root, "readme.dat").apply { writeBytes(utf16Bytes) }
        assertTrue(ArchiveManager.looksLikeTextFile(utf16UnknownExt))

        // 4. Shift_JIS 日文文本识别
        val sjisText = "このゲームはテストです。ボタンを押してください。"
        val sjisDec = ArchiveManager.decodeTextBytes(sjisText.toByteArray(Charset.forName("Shift_JIS")))
        assertEquals("Shift_JIS", sjisDec.label)
        assertEquals(sjisText, sjisDec.text)

        // 5. Big5 繁体中文文本识别
        val big5Text = "繁體中文遊戲修改內容說明與武器裝備指南"
        val big5Dec = ArchiveManager.decodeTextBytes(big5Text.toByteArray(Charset.forName("Big5")))
        assertEquals("Big5", big5Dec.label)
        assertEquals(big5Text, big5Dec.text)
    }

    private fun buildStoredRar4(entries: List<Pair<String, ByteArray>>): ByteArray {
        fun crc32Of(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

        fun withHeaderCrc(body: ByteArray): ByteArray {
            val crc16 = (crc32Of(body) and 0xFFFFL).toInt().toShort()
            return ByteBuffer.allocate(2 + body.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(crc16)
                .put(body)
                .array()
        }

        val out = ByteArrayOutputStream()
        // RAR4 signature
        out.write(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00))
        // Main header (type 0x73)
        val mainBody = ByteBuffer.allocate(11)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(0x73.toByte())
            .putShort(0)
            .putShort(13)
            .putShort(0)
            .putInt(0)
            .array()
        out.write(withHeaderCrc(mainBody))

        for ((name, data) in entries) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val headerSize = (7 + 25 + nameBytes.size).toShort()
            val fileCrc = crc32Of(data).toInt()
            val fhBody = ByteBuffer.allocate(30 + nameBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(0x74.toByte())
                .putShort(0x8000.toShort())
                .putShort(headerSize)
                .putInt(data.size)
                .putInt(data.size)
                .put(3.toByte())
                .putInt(fileCrc)
                .putInt(0)
                .put(29.toByte())
                .put(0x30.toByte())
                .putShort(nameBytes.size.toShort())
                .putInt(0x20)
                .put(nameBytes)
                .array()
            out.write(withHeaderCrc(fhBody))
            out.write(data)
        }

        // End of archive header (type 0x7b)
        val endBody = ByteBuffer.allocate(5)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(0x7B.toByte())
            .putShort(0x4000.toShort())
            .putShort(7)
            .array()
        out.write(withHeaderCrc(endBody))
        return out.toByteArray()
    }
}
