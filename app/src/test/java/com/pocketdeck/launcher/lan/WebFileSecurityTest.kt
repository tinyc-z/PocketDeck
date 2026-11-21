package com.pocketdeck.launcher.lan

import com.pocketdeck.launcher.data.ArchiveManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Web 管理端文件安全回归测试（高危 1-4 修复验证）：
 * 1) auto-create-platforms 越权扩大 romDirs / 敏感目录拦截；
 * 2) writeText 非文本/二进制/隐藏文件拦截；
 * 3) extract here 模式不覆写已有文件且过滤隐藏点文件 + 拦截 Zip Slip；
 * 4) Web 单段文件名过滤控制字符、超长与 `.` 前缀隐藏名。
 */
class WebFileSecurityTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    @Test
    fun sanitizeWebFileName_stripsControlCharsRejectsHiddenAndOverlong() {
        assertNull(WebFileSecurity.sanitizeWebFileName(".nomedia"))
        assertNull(WebFileSecurity.sanitizeWebFileName(".hidden"))
        assertNull(WebFileSecurity.sanitizeWebFileName(".."))
        assertNull(WebFileSecurity.sanitizeWebFileName("sub/rom.gba"))
        assertNull(WebFileSecurity.sanitizeWebFileName("sub\\rom.gba"))
        assertNull(WebFileSecurity.sanitizeWebFileName("x".repeat(256)))
        assertNull(WebFileSecurity.sanitizeWebFileName("\u0007\u0008"))
        assertEquals("Mario.gba", WebFileSecurity.sanitizeWebFileName("  Ma\r\nrio\u0007.gba  "))
    }

    @Test
    fun isEditableWebTextFile_allowsTextAndBlocksRomSaveBinaryAndHidden() {
        val root = tmpDir.root
        val cfg = File(root, "retroarch.cfg").apply { writeText("video_vsync = true\n") }
        val m3u = File(root, "disc.m3u").apply { writeText("Disc 1.chd\n") }
        val readme = File(root, "README").apply { writeText("hello\n") }
        val zeroGba = File(root, "empty.gba").apply { writeBytes(ByteArray(0)) }
        val binarySav = File(root, "zelda.sav").apply { writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03)) }
        val zipFile = File(root, "game.zip").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }
        val hidden = File(root, ".nomedia").apply { writeText("") }

        assertTrue(WebFileSecurity.isEditableWebTextFile(cfg, "video_vsync = false\n"))
        assertTrue(WebFileSecurity.isEditableWebTextFile(m3u, "Disc 1.chd\n"))
        assertTrue(WebFileSecurity.isEditableWebTextFile(readme, "updated"))
        assertFalse(WebFileSecurity.isEditableWebTextFile(cfg, "bad\u0000content"))
        assertFalse(WebFileSecurity.isEditableWebTextFile(zeroGba, "corrupt"))
        assertFalse(WebFileSecurity.isEditableWebTextFile(binarySav, "corrupt"))
        assertFalse(WebFileSecurity.isEditableWebTextFile(zipFile, "corrupt"))
        assertFalse(WebFileSecurity.isEditableWebTextFile(hidden, "corrupt"))
    }

    @Test
    fun resolveAutoCreateTarget_blocksVolumeRootEscalationAndProtectedDirs() {
        val volumeRoot = tmpDir.newFolder("sdcard")
        val romRoot = File(volumeRoot, "ROMs").apply { mkdirs() }
        val downloadDir = File(volumeRoot, "Download").apply { mkdirs() }
        val otherDir = File(volumeRoot, "DCIM").apply { mkdirs() }

        // 攻击 1：dir 传共享存储根目录、parentName 留空企图将整张卡加入 romDirs
        val emptyParent = WebFileSecurity.resolveAutoCreateTarget(
            dirPath = volumeRoot.absolutePath,
            parentName = "",
            romRoots = listOf(romRoot),
            volumeRoots = listOf(volumeRoot),
        )
        assertTrue(emptyParent is WebFileSecurity.AutoCreateTargetResult.Error)
        assertEquals(400, (emptyParent as WebFileSecurity.AutoCreateTargetResult.Error).httpStatus)

        // 攻击 2：dir 传存储根目录 + parentName 传 Download / DCIM / Android 敏感系统目录
        val protectedRes = WebFileSecurity.resolveAutoCreateTarget(
            dirPath = volumeRoot.absolutePath,
            parentName = "Download",
            romRoots = listOf(romRoot),
            volumeRoots = listOf(volumeRoot),
        )
        assertTrue(protectedRes is WebFileSecurity.AutoCreateTargetResult.Error)
        assertEquals("protected_dir", (protectedRes as WebFileSecurity.AutoCreateTargetResult.Error).code)

        // 攻击 3：dir 传 romDirs 以外的子目录（如 Download / DCIM）
        val outsideSubdir = WebFileSecurity.resolveAutoCreateTarget(
            dirPath = otherDir.absolutePath,
            parentName = "my_sub",
            romRoots = listOf(romRoot),
            volumeRoots = listOf(volumeRoot),
        )
        assertTrue(outsideSubdir is WebFileSecurity.AutoCreateTargetResult.Error)
        assertEquals(403, (outsideSubdir as WebFileSecurity.AutoCreateTargetResult.Error).httpStatus)

        // 攻击 4：dir=volumeRoot 下的符号链接逃逸
        val outsideTarget = tmpDir.newFolder("outside")
        val symLink = File(volumeRoot, "link_roms")
        Files.createSymbolicLink(symLink.toPath(), outsideTarget.toPath())
        val symRes = WebFileSecurity.resolveAutoCreateTarget(
            dirPath = volumeRoot.absolutePath,
            parentName = "link_roms",
            romRoots = listOf(romRoot),
            volumeRoots = listOf(volumeRoot),
        )
        assertTrue(symRes is WebFileSecurity.AutoCreateTargetResult.Error)
        assertEquals(403, (symRes as WebFileSecurity.AutoCreateTargetResult.Error).httpStatus)

        // 正常场景 A：在存储卷根目录下显式创建非敏感父目录（如 "MyRoms"）
        val validNew = WebFileSecurity.resolveAutoCreateTarget(
            dirPath = volumeRoot.absolutePath,
            parentName = "MyRoms",
            romRoots = listOf(romRoot),
            volumeRoots = listOf(volumeRoot),
        )
        assertTrue(validNew is WebFileSecurity.AutoCreateTargetResult.Ok)
        assertEquals(File(volumeRoot, "MyRoms").canonicalPath, (validNew as WebFileSecurity.AutoCreateTargetResult.Ok).targetRoot.canonicalPath)

        // 正常场景 B：在已授权的 romRoot 内部调用且 parentName 为空
        val validExisting = WebFileSecurity.resolveAutoCreateTarget(
            dirPath = romRoot.absolutePath,
            parentName = "",
            romRoots = listOf(romRoot),
            volumeRoots = listOf(volumeRoot),
        )
        assertTrue(validExisting is WebFileSecurity.AutoCreateTargetResult.Ok)
        assertEquals(romRoot.canonicalPath, (validExisting as WebFileSecurity.AutoCreateTargetResult.Ok).targetRoot.canonicalPath)
        assertFalse(downloadDir.absolutePath == (validExisting).targetRoot.absolutePath)
    }

    @Test
    fun extract_nonOverwriteAndSkipHidden_preservesExistingAndSkipsDotfiles() = runBlocking {
        val work = tmpDir.newFolder("extract_work")
        val existingRom = File(work, "mario.gba").apply { writeText("ORIGINAL_ROM") }
        val zipFile = File(work, "pack.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("mario.gba"))
            zos.write("OVERWRITE_PAYLOAD".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry(".nomedia"))
            zos.write("hidden".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("sub/.DS_Store"))
            zos.write("ds".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("sub/clean.gba"))
            zos.write("NEW_ROM".toByteArray())
            zos.closeEntry()
        }

        val res = ArchiveManager.extract(
            file = zipFile,
            destDir = work,
            overwriteExisting = false,
            skipHiddenEntries = true,
        )
        assertTrue(res.isSuccess)
        assertEquals(2, res.getOrThrow())
        assertEquals("ORIGINAL_ROM", existingRom.readText())
        val suffixed = File(work, "mario_1.gba")
        assertTrue(suffixed.isFile)
        assertEquals("OVERWRITE_PAYLOAD", suffixed.readText())
        assertFalse(File(work, ".nomedia").exists())
        assertFalse(File(work, "sub/.DS_Store").exists())
        assertEquals("NEW_ROM", File(work, "sub/clean.gba").readText())

        // 同时确认 Zip Slip 仍被拒绝
        val evilZip = File(work, "evil.zip")
        ZipOutputStream(evilZip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("../escape.txt"))
            zos.write("pwn".toByteArray())
            zos.closeEntry()
        }
        val evilRes = ArchiveManager.extract(
            file = evilZip,
            destDir = work,
            overwriteExisting = false,
            skipHiddenEntries = true,
        )
        assertTrue(evilRes.isFailure)
        assertNotNull(evilRes.exceptionOrNull())
        assertFalse(File(work.parentFile, "escape.txt").exists())
    }
}
