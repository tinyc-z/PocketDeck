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
