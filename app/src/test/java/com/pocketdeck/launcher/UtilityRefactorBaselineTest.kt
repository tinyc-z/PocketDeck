package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.OfflineGameDb
import com.pocketdeck.launcher.data.detectImageFormat
import com.pocketdeck.launcher.data.formatCacheBytes
import com.pocketdeck.launcher.data.formatCompactSize
import com.pocketdeck.launcher.data.formatFileSize
import com.pocketdeck.launcher.data.formatHttpLogBytes
import com.pocketdeck.launcher.data.formatSize
import com.pocketdeck.launcher.data.formatSpeed
import com.pocketdeck.launcher.data.mimeTypeForFile
import com.pocketdeck.launcher.data.sanitizeFileKey
import com.pocketdeck.launcher.data.sanitizeUploadFileName
import com.pocketdeck.launcher.data.uniqueFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 重构回归测试：验证抽干合并后的字节格式化、文件名安全校验、唯一命名及图片格式识别保持行为一致。
 */
class UtilityRefactorBaselineTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    @Test
    fun formatCompactSize_matchesBaseline() {
        assertNull(formatCompactSize(-1L))
        assertNull(formatCompactSize(0L))
        assertEquals("0 KB", formatCompactSize(1L))
        assertEquals("1 KB", formatCompactSize(512L))
        assertEquals("1 KB", formatCompactSize(1024L))
        assertEquals("2 KB", formatCompactSize(1536L))
        assertEquals("1.0 MB", formatCompactSize(1024L * 1024L))
        assertEquals("1.5 MB", formatCompactSize(1536L * 1024L))
        assertEquals("1.00 GB", formatCompactSize(1024L * 1024L * 1024L))
        assertEquals("2.50 GB", formatCompactSize(2560L * 1024L * 1024L))
    }

    @Test
    fun formatSize_matchesBaseline() {
        assertEquals("0 B", formatSize(-5L))
        assertEquals("0 B", formatSize(0L))
        assertEquals("1 KB", formatSize(1024L))
        assertEquals("500 KB", formatSize(500L * 1024L))
        assertEquals("1.5 MB", formatSize(1536L * 1024L))
        assertEquals("1.00 GB", formatSize(1024L * 1024L * 1024L))
    }

    @Test
    fun formatSpeed_matchesBaseline() {
        assertEquals("0 B/s", formatSpeed(-1L))
        assertEquals("0 B/s", formatSpeed(0L))
        assertEquals("512 B/s", formatSpeed(512L))
        assertEquals("1023 B/s", formatSpeed(1023L))
        assertEquals("1 KB/s", formatSpeed(1024L))
        assertEquals("2 KB/s", formatSpeed(1536L))
        assertEquals("1.5 MB/s", formatSpeed(1536L * 1024L))
        assertEquals("1.00 GB/s", formatSpeed(1024L * 1024L * 1024L))
    }

    @Test
    fun formatCacheBytes_matchesBaseline() {
        assertEquals("0 B", formatCacheBytes(-1L))
        assertEquals("0 B", formatCacheBytes(0L))
        assertEquals("0.5 KB", formatCacheBytes(512L))
        assertEquals("1.0 KB", formatCacheBytes(1024L))
        assertEquals("1.5 KB", formatCacheBytes(1536L))
        assertEquals("1.5 MB", formatCacheBytes(1536L * 1024L))
        assertEquals("2.00 GB", formatCacheBytes(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun formatFileSize_matchesBaseline() {
        assertEquals("0 B", formatFileSize(-1L))
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("1023 B", formatFileSize(1023L))
        assertEquals("1.0 KB", formatFileSize(1024L))
        assertEquals("1.5 KB", formatFileSize(1536L))
        assertEquals("99.5 KB", formatFileSize((99.5 * 1024).toLong()))
        assertEquals("100 KB", formatFileSize(100L * 1024L))
        assertEquals("1.5 MB", formatFileSize(1536L * 1024L))
        assertEquals("2.50 GB", formatFileSize(2560L * 1024L * 1024L))
    }

    @Test
    fun formatHttpLogBytes_matchesBaseline() {
        assertEquals("?B", formatHttpLogBytes(-1L))
        assertEquals("0B", formatHttpLogBytes(0L))
        assertEquals("512B", formatHttpLogBytes(512L))
        assertEquals("1KB", formatHttpLogBytes(1024L))
        assertEquals("1.5MB", formatHttpLogBytes(1536L * 1024L))
    }

    @Test
    fun sanitizeFileKey_and_sanitizeSystemName_matchBaseline() {
        assertEquals("Nintendo_-_Game_Boy_Advance", OfflineGameDb.sanitizeSystemName("Nintendo - Game Boy Advance"))
        assertEquals("Sony___PlayStation__PS1___USA_", sanitizeFileKey("Sony / PlayStation (PS1) [USA]"))
        assertEquals("_____01.bin-rev2", sanitizeFileKey("中文平台_01.bin-rev2"))
    }

    @Test
    fun sanitizeUploadFileName_rejectsTraversalAndControlChars() {
        assertNull(sanitizeUploadFileName(""))
        assertNull(sanitizeUploadFileName("   "))
        assertNull(sanitizeUploadFileName("."))
        assertNull(sanitizeUploadFileName(".."))
        assertNull(sanitizeUploadFileName("a/b.zip"))
        assertNull(sanitizeUploadFileName("a\\b.zip"))
        assertNull(sanitizeUploadFileName("a\u0000b.zip"))
        assertEquals("game.zip", sanitizeUploadFileName("  game.zip  "))
        assertEquals("ab.zip", sanitizeUploadFileName("a\u0007b.zip", stripControlChars = true, maxLength = 255))
        assertNull(sanitizeUploadFileName("x".repeat(256), stripControlChars = true, maxLength = 255))
    }

    @Test
    fun uniqueFile_appendsIncrementingSuffixOnCollision() {
        val dir = tmpDir.root
        val f0 = uniqueFile(dir, "rom.gba")
        assertEquals("rom.gba", f0.name)
        f0.writeText("1")
        val f1 = uniqueFile(dir, "rom.gba")
        assertEquals("rom_1.gba", f1.name)
        f1.writeText("2")
        val f2 = uniqueFile(dir, "rom.gba")
        assertEquals("rom_2.gba", f2.name)
    }

    @Test
    fun detectImageFormat_and_mimeTypeForFile_work() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val webp = "RIFF1234WEBP".toByteArray()
        val gif = "GIF89a".toByteArray()
        assertEquals("png" to "image/png", detectImageFormat(png))
        assertEquals("jpg" to "image/jpeg", detectImageFormat(jpg))
        assertEquals("webp" to "image/webp", detectImageFormat(webp))
        assertEquals("gif" to "image/gif", detectImageFormat(gif))
        assertNull(detectImageFormat("plain text".toByteArray()))

        assertEquals("image/png", mimeTypeForFile(File("cover.PNG")))
        assertEquals("application/zip", mimeTypeForFile(File("rom.zip")))
        assertEquals("image/jpeg", mimeTypeForFile(File("rom.bin"), defaultMime = "image/jpeg"))
        assertEquals("application/octet-stream", mimeTypeForFile(File("rom.bin")))
    }
}
