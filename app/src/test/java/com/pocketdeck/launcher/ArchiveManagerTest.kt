package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.ArchiveManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
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
}
