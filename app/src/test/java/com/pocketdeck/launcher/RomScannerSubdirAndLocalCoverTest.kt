package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.RomScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class RomScannerSubdirAndLocalCoverTest {

    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun scansUpToThreeSubdirLevelsAndSkipsIgnoredAndMarkerDirs() {
        val root = tmp.newFolder("roms_subdirs")
        val nes = File(root, "nes").apply { mkdirs() }

        // Depth 0..3 should be scanned
        File(nes, "Contra (USA).nes").writeText("d0")
        val d1 = File(nes, "国产外星").apply { mkdirs() }
        File(d1, "仙剑问情.nes").writeText("d1")
        val d2 = File(d1, "RPG").apply { mkdirs() }
        File(d2, "东周列国志.nes").writeText("d2")
        val d3 = File(d2, "Series").apply { mkdirs() }
        File(d3, "天龙八部.nes").writeText("d3")

        // Depth 4 must be ignored
        val d4 = File(d3, "TooDeep").apply { mkdirs() }
        File(d4, "DeepGame.nes").writeText("d4")

        // Ignored media/hidden/marker dirs must be skipped
        File(nes, "Imgs").apply { mkdirs() }.let { File(it, "Fake.nes").writeText("ignored_imgs") }
        File(nes, "media/covers").apply { mkdirs() }.let { File(it, "Fake2.nes").writeText("ignored_media") }
        File(nes, ".hidden").apply { mkdirs() }.let { File(it, "Hidden.nes").writeText("ignored_hidden") }
        File(nes, "__MACOSX").apply { mkdirs() }.let { File(it, "Junk.nes").writeText("ignored_mac") }
        File(nes, "nomedia_dir").apply { mkdirs() }.let {
            File(it, ".nomedia").writeText("")
            File(it, "IgnoredNoMedia.nes").writeText("ignored_nm")
        }
        File(nes, "ignore_dir").apply { mkdirs() }.let {
            File(it, ".pocketdeckignore").writeText("")
            File(it, "IgnoredPocket.nes").writeText("ignored_pd")
        }

        val lib = RomScanner.scanAll(listOf(root))
        val nesGames = lib.games["nes"].orEmpty()
        assertEquals(listOf("Contra", "东周列国志", "仙剑问情", "天龙八部").sorted(), nesGames.map { it.title }.sorted())
    }

    @Test
    fun nonRecursivePlatformsAndRootMiscDoNotRecurse() {
        val root = tmp.newFolder("roms_nonrec")
        val dos = File(root, "dos").apply { mkdirs() }
        File(dos, "Doom.dosz").writeText("dos_entry")
        val dosSub = File(dos, "DoomData/assets").apply { mkdirs() }
        File(dosSub, "DOOM.EXE").writeText("inner_exe")

        // Loose root non-platform folder (like test/) must not recurse into misc
        val testDir = File(root, "test/bundle").apply { mkdirs() }
        File(testDir, "sample.nes").writeText("not_a_platform_rom")

        val lib = RomScanner.scanAll(listOf(root))
        val dosGames = lib.games["dos"].orEmpty()
        assertEquals(1, dosGames.size)
        assertEquals("Doom.dosz", dosGames.single().fileName)
        assertTrue(lib.games["nes"].isNullOrEmpty())
        assertTrue(lib.games["misc"].isNullOrEmpty())
    }

    @Test
    fun symlinkLoopInSubdirsDoesNotHangOrDuplicate() {
        val root = tmp.newFolder("roms_symlink")
        val gba = File(root, "gba").apply { mkdirs() }
        val sub = File(gba, "Sub").apply { mkdirs() }
        File(sub, "Golden Sun (USA).gba").writeText("rom")
        val loopLink = File(sub, "LoopBack").toPath()
        val created = runCatching {
            Files.createSymbolicLink(loopLink, gba.toPath())
            true
        }.getOrDefault(false)

        if (created) {
            val lib = RomScanner.scanAll(listOf(root))
            assertEquals(1, lib.games["gba"]?.size)
        }
    }

    @Test
    fun isolatesMultiDiscAndCuePerSubdirSupportsCrossDirM3uAndPbpSubdirTitle() {
        val root = tmp.newFolder("roms_disc_pbp")
        val psx = File(root, "psx").apply { mkdirs() }

        val gameA = File(psx, "Castlevania").apply { mkdirs() }
        File(gameA, "Disc 1.bin").writeText("a1")
        File(gameA, "Disc 1.cue").writeText("FILE \"Disc 1.bin\" BINARY\n")

        val gameB = File(psx, "Xenogears").apply { mkdirs() }
        File(gameB, "Disc 1.bin").writeText("b1")
        File(gameB, "Disc 1.cue").writeText("FILE \"Disc 1.bin\" BINARY\n")

        // Cross-subdir .m3u in psx root pointing to ./FF7/Disc 1.chd & Disc 2.chd
        val ff7Dir = File(psx, "FF7").apply { mkdirs() }
        File(ff7Dir, "Disc 1.chd").writeText("c1")
        File(ff7Dir, "Disc 2.chd").writeText("c2")
        File(psx, "Final Fantasy VII.m3u").writeText("./FF7/Disc 1.chd\n./FF7/Disc 2.chd\n")

        // Two PSP EBOOT.PBP files without SFO headers in separate subdirs must use folder names
        val psp = File(root, "psp").apply { mkdirs() }
        File(psp, "Syphon Filter 3").apply { mkdirs() }.let { File(it, "EBOOT.PBP").writeText("not_pbp_header") }
        File(psp, "Crisis Core").apply { mkdirs() }.let { File(it, "EBOOT.PBP").writeText("not_pbp_header_2") }

        val lib = RomScanner.scanAll(listOf(root))
        val psxGames = lib.games["psx"].orEmpty()
        assertEquals(3, psxGames.size)
        val ff7 = psxGames.first { it.fileName == "Final Fantasy VII.m3u" }
        assertEquals(2, ff7.discFiles.size)

        val pspGames = lib.games["psp"].orEmpty()
        assertEquals(listOf("Crisis Core", "Syphon Filter 3"), pspGames.map { it.title }.sorted())
    }

    @Test
    fun supportsImgsTrimuiMirrorExtensionSuffixAndGenericSingleGameCover() {
        val container = tmp.newFolder("sd_root")
        val romsRoot = File(container, "Roms").apply { mkdirs() }
        val nes = File(romsRoot, "nes").apply { mkdirs() }
        val sub = File(nes, "国产").apply { mkdirs() }

        val r1 = File(sub, "魂斗罗.nes").apply { writeText("r1") }
        val r2 = File(nes, "赤色要塞.nes").apply { writeText("r2") }
        val r3 = File(nes, "超级魂斗罗.nes").apply { writeText("r3") }

        // 1) OnionOS/GarlicOS `<platform>/Imgs/<sub?>/<rom>.png` (uppercase Imgs)
        val c1 = File(nes, "Imgs/国产/魂斗罗.png").apply { parentFile?.mkdirs(); writeText("c1") }
        // 2) Extension-inclusive filename `<rom>.nes.png`
        val c2 = File(nes, "Imgs/赤色要塞.nes.png").apply { parentFile?.mkdirs(); writeText("c2") }
        // 3) Trimui sibling `<grandParent>/Imgs/<platform>/<rom>.png`
        val c3 = File(container, "Imgs/nes/超级魂斗罗.png").apply { parentFile?.mkdirs(); writeText("c3") }

        // 4) Single-game subdir generic cover (`cover.png` / `folder.jpg`) vs multi-game subdir (must NOT match generic cover)
        val psx = File(romsRoot, "psx").apply { mkdirs() }
        val singleDir = File(psx, "FF7").apply { mkdirs() }
        File(singleDir, "Final Fantasy VII.chd").writeText("chd")
        val singleCover = File(singleDir, "folder.jpg").apply { writeText("jpg") }

        val multiDir = File(psx, "Collection").apply { mkdirs() }
        File(multiDir, "Game One.chd").writeText("g1")
        File(multiDir, "Game Two.chd").writeText("g2")
        File(multiDir, "cover.png").writeText("generic_should_not_apply")

        val lib = RomScanner.scanAll(listOf(romsRoot))
        val byId = lib.byId
        assertEquals(c1.path, byId[r1.path]?.localCover)
        assertEquals(c2.path, byId[r2.path]?.localCover)
        assertEquals(c3.path, byId[r3.path]?.localCover)
        assertEquals(singleCover.path, lib.games["psx"]?.first { it.title == "Final Fantasy VII" }?.localCover)
        assertNull(lib.games["psx"]?.first { it.title == "Game One" }?.localCover)
        assertNull(lib.games["psx"]?.first { it.title == "Game Two" }?.localCover)
    }

    @Test
    fun parsesPegasusMetadataPerGameMediaAndPrioritizesBoxartOverMixAndScreenshot() {
        val root = tmp.newFolder("roms_pegasus")
        val nes = File(root, "nes").apply { mkdirs() }
        val sub = File(nes, "外星科技").apply { mkdirs() }
        val rom1 = File(sub, "仙剑问情.nes").apply { writeText("rom1") }
        val rom2 = File(nes, "天龙八部.nes").apply { writeText("rom2") }
        val rom3 = File(nes, "雪人兄弟.nes").apply { writeText("rom3") }

        // Pegasus per-game media folder: media/<gameName>/boxFront.png beats screenshot.png
        val pegasusPerGameDir = File(nes, "media/天龙八部").apply { mkdirs() }
        File(pegasusPerGameDir, "screenshot.png").writeText("shot")
        val boxFront = File(pegasusPerGameDir, "boxFront.png").apply { writeText("box") }

        // ES-DE fallback miximages vs higher-priority boxart 3dboxes
        val mixImg = File(nes, "media/miximages/雪人兄弟-mix.png").apply { parentFile?.mkdirs(); writeText("mix") }
        val box3d = File(nes, "media/3dboxes/雪人兄弟-3dbox.png").apply { parentFile?.mkdirs(); writeText("3d") }

        val pegasusBox = File(nes, "media/box2dfront/仙剑问情.png").apply { parentFile?.mkdirs(); writeText("pegasus_box") }
        File(nes, "metadata.pegasus.txt").writeText(
            """
            collection: Nintendo Entertainment System
            shortname: nes

            game: 仙剑问情
            file: ./外星科技/仙剑问情.nes
            developer: 外星科技
            publisher: 外星电脑科技
            genre: RPG
            release: 2001-05-01
            description:
              第一段简介。
              .
              第二段简介。
            assets.boxFront: media/box2dfront/仙剑问情.png
            """.trimIndent(),
        )

        val lib = RomScanner.scanAll(listOf(root))
        val byId = lib.byId
        assertEquals(pegasusBox.path, byId[rom1.path]?.localCover)
        val meta1 = lib.localMetadata[rom1.path]
        assertNotNull(meta1)
        assertEquals("外星科技", meta1?.developer)
        assertEquals("外星电脑科技", meta1?.publisher)
        assertEquals("RPG", meta1?.genre)
        assertEquals("2001-05-01", meta1?.releaseDate)
        assertEquals("第一段简介。\n\n第二段简介。", meta1?.description)

        assertEquals(boxFront.path, byId[rom2.path]?.localCover)
        // 3dboxes belongs to boxart tier, strictly preferred over miximages fallback tier
        assertEquals(box3d.path, byId[rom3.path]?.localCover)
        assertTrue(mixImg.isFile)
    }

    @Test
    fun gamelistXmlRelativePathPreventsSameFileNameCollisionAcrossSubdirs() {
        val root = tmp.newFolder("roms_gl_rel")
        val psp = File(root, "psp").apply { mkdirs() }
        val subA = File(psp, "GameA").apply { mkdirs() }
        val subB = File(psp, "GameB").apply { mkdirs() }
        val ebootA = File(subA, "EBOOT.PBP").apply { writeText("a") }
        val ebootB = File(subB, "EBOOT.PBP").apply { writeText("b") }
        val covA = File(psp, "media/covers/a.png").apply { parentFile?.mkdirs(); writeText("ca") }
        val covB = File(psp, "media/covers/b.png").apply { parentFile?.mkdirs(); writeText("cb") }

        File(psp, "gamelist.xml").writeText(
            """
            <?xml version="1.0"?>
            <gameList>
              <game>
                <path>./GameA/EBOOT.PBP</path>
                <image>./media/covers/a.png</image>
                <developer>DevA</developer>
              </game>
              <game>
                <path>./GameB/EBOOT.PBP</path>
                <image>./media/covers/b.png</image>
                <developer>DevB</developer>
              </game>
            </gameList>
            """.trimIndent(),
        )

        val lib = RomScanner.scanAll(listOf(root))
        assertEquals(covA.path, lib.byId[ebootA.path]?.localCover)
        assertEquals(covB.path, lib.byId[ebootB.path]?.localCover)
        assertEquals("DevA", lib.localMetadata[ebootA.path]?.developer)
        assertEquals("DevB", lib.localMetadata[ebootB.path]?.developer)
    }

    @Test
    fun incrementalSnapshotReusesSubdirsAndDetectsSubdirAndCoverChanges() {
        val root = tmp.newFolder("roms_inc_subdir")
        val nes = File(root, "nes").apply { mkdirs() }
        val sub = File(nes, "RPG/Chinese").apply { mkdirs() }
        val rom1 = File(sub, "Hero.nes").apply { writeText("1") }

        val scan1 = RomScanner.scanAll(listOf(root))
        assertEquals(1, scan1.games["nes"]?.size)
        assertNull(scan1.byId[rom1.path]?.localCover)

        // Unchanged tree must reuse cached games INCLUDING depth-2 subdir games
        val scan2 = RomScanner.scanAll(listOf(root), previousLibrary = scan1)
        assertEquals(scan1, scan2)
        assertEquals(1, scan2.games["nes"]?.size)

        // Adding a ROM inside depth-2 subdir invalidates snapshot
        val rom2 = File(sub, "Hero2.nes").apply { writeText("2") }
        sub.setLastModified(sub.lastModified() + 5_000L)
        val scan3 = RomScanner.scanAll(listOf(root), previousLibrary = scan2)
        assertNotEquals(scan2.dirSnapshots[nes.path], scan3.dirSnapshots[nes.path])
        assertEquals(2, scan3.games["nes"]?.size)

        // Adding a cover in Imgs/ invalidates snapshot and binds cover
        val imgsDir = File(nes, "Imgs").apply { mkdirs() }
        val cov = File(imgsDir, "Hero.png").apply { writeText("img") }
        imgsDir.setLastModified(imgsDir.lastModified() + 5_000L)
        val scan4 = RomScanner.scanAll(listOf(root), previousLibrary = scan3)
        assertNotEquals(scan3.dirSnapshots[nes.path], scan4.dirSnapshots[nes.path])
        assertEquals(cov.path, scan4.byId[rom1.path]?.localCover)
        assertNotNull(scan4.byId[rom2.path])
    }

    @Test
    fun flat500RomSnapshotAndScanPerformanceSmokeTest() {
        val root = tmp.newFolder("roms_perf_500")
        val nes = File(root, "nes").apply { mkdirs() }
        repeat(500) { i ->
            File(nes, "Game_$i (USA).nes").writeText("r")
        }

        val tScan0 = System.nanoTime()
        val lib = RomScanner.scanAll(listOf(root))
        val scanMs = (System.nanoTime() - tScan0) / 1_000_000.0
        assertEquals(500, lib.games["nes"]?.size)

        val tSnap0 = System.nanoTime()
        val inc = RomScanner.scanAll(listOf(root), previousLibrary = lib)
        val incMs = (System.nanoTime() - tSnap0) / 1_000_000.0
        assertEquals(lib, inc)
        println("[BENCH] RomScanner_500FlatGames | coldScan=${"%.2f".format(scanMs)}ms | incrementalReuse=${"%.2f".format(incMs)}ms")
    }
}
