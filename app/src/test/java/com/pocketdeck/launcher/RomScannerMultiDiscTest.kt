package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.RomScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RomScannerMultiDiscTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun rejectsPlaylistM3uAndAcceptsDiscM3u() {
        val psxDir = tmp.newFolder("psx")
        val disc1 = File(psxDir, "Final Fantasy VII (Disc 1).chd").apply { writeText("d1") }
        val disc2 = File(psxDir, "Final Fantasy VII (Disc 2).chd").apply { writeText("d2") }

        val validM3u = File(psxDir, "Final Fantasy VII.m3u").apply {
            writeText("${disc1.name}\n${disc2.name}\n")
        }
        val musicM3u = File(psxDir, "playlist.m3u").apply {
            writeText("#EXTM3U\n#EXTINF:123,Song\nhttp://example.com/a.mp3\n")
        }

        assertTrue(RomScanner.isPotentialRomFile(validM3u, "psx"))
        assertFalse(RomScanner.isPotentialRomFile(musicM3u, "psx"))
        assertFalse(RomScanner.isPotentialRomFile(validM3u, "gba"))
    }

    @Test
    fun scanAllIgnoresMediaDirsAndNonRomFiles() {
        val root = tmp.newFolder("roms")
        val gba = File(root, "gba").apply { mkdirs() }
        File(gba, "Metroid Fusion (USA).gba").writeText("rom")
        File(gba, "Metroid Fusion (USA).sav").writeText("save")
        File(gba, "readme.txt").writeText("info")
        val imagesDir = File(root, "images").apply { mkdirs() }
        File(imagesDir, "cover.png").writeText("png")

        val lib = RomScanner.scanAll(listOf(root))
        assertEquals(1, lib.platforms.size)
        assertEquals("gba", lib.platforms.first().id)
        val gbaGames = lib.games["gba"].orEmpty()
        assertEquals(1, gbaGames.size)
        assertEquals("Metroid Fusion", gbaGames.first().title)
    }

    @Test
    fun scanAllHidesCueReferencedBinFiles() {
        val root = tmp.newFolder("roms_psx")
        val psx = File(root, "psx").apply { mkdirs() }
        File(psx, "Castlevania.bin").writeText("track")
        File(psx, "Castlevania.cue").writeText("FILE \"Castlevania.bin\" BINARY\n  TRACK 01 MODE2/2352\n")

        val lib = RomScanner.scanAll(listOf(root))
        val psxGames = lib.games["psx"].orEmpty()
        assertEquals(1, psxGames.size)
        assertEquals("Castlevania.cue", psxGames.first().fileName)
    }
}
