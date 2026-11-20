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

    @Test
    fun foldsMultiDiscGamesAndGeneratesM3u() {
        val root = tmp.newFolder("roms_multidisc")
        val psx = File(root, "psx").apply { mkdirs() }
        val d1 = File(psx, "Final Fantasy VII (Disc 1).chd").apply { writeText("111") }
        val d2 = File(psx, "Final Fantasy VII (Disc 2).chd").apply { writeText("2222") }
        val d3 = File(psx, "Final Fantasy VII (Disc 3).chd").apply { writeText("33333") }

        val lib = RomScanner.scanAll(listOf(root))
        val games = lib.games["psx"].orEmpty()
        assertEquals(1, games.size)
        val ff7 = games.first()
        assertEquals("Final Fantasy VII", ff7.title)
        assertEquals(listOf(d1.path, d2.path, d3.path), ff7.discFiles)
        assertEquals(12L, ff7.fileSize)

        val m3u = RomScanner.generateM3uForGame(ff7).getOrThrow()
        assertTrue(m3u.isFile)
        val libAfterM3u = RomScanner.scanAll(listOf(root))
        val gamesAfterM3u = libAfterM3u.games["psx"].orEmpty()
        assertEquals(1, gamesAfterM3u.size)
        assertEquals(m3u.name, gamesAfterM3u.first().fileName)
        assertEquals(3, gamesAfterM3u.first().discFiles.size)
    }

    @Test
    fun parsesEsDeGamelistXmlAndLocalMedia() {
        val root = tmp.newFolder("roms_esde")
        val gba = File(root, "gba").apply { mkdirs() }
        val rom = File(gba, "Golden Sun (USA).gba").apply { writeText("romdata") }
        val imgDir = File(gba, "downloaded_images").apply { mkdirs() }
        val cover = File(imgDir, "Golden Sun (USA)-image.png").apply { writeText("png") }
        File(gba, "gamelist.xml").writeText(
            """
            <?xml version="1.0"?>
            <gameList>
              <game>
                <path>./Golden Sun (USA).gba</path>
                <name>Golden Sun</name>
                <desc>Classic GBA RPG by Camelot.</desc>
                <image>./downloaded_images/Golden Sun (USA)-image.png</image>
                <releasedate>20011111T000000</releasedate>
                <developer>Camelot</developer>
                <publisher>Nintendo</publisher>
                <genre>RPG</genre>
              </game>
            </gameList>
            """.trimIndent(),
        )

        val lib = RomScanner.scanAll(listOf(root))
        val game = lib.games["gba"]!!.single()
        assertEquals(cover.path, game.localCover)
        val meta = lib.localMetadata[rom.path]
        assertEquals("2001-11-11", meta?.releaseDate)
        assertEquals("Camelot", meta?.developer)
        assertEquals("Classic GBA RPG by Camelot.", meta?.description)

        // Incremental scan reuses unchanged platform directory snapshot
        val incremental = RomScanner.scanAll(listOf(root), previousLibrary = lib)
        assertEquals(1, incremental.games["gba"]?.size)
        assertEquals(meta, incremental.localMetadata[rom.path])
    }
}
