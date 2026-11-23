package com.pocketdeck.launcher

import com.pocketdeck.launcher.launch.CustomLauncherCodec
import com.pocketdeck.launcher.launch.EmulatorRegistry
import com.pocketdeck.launcher.launch.GameLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UT-21 · 模拟器调起健壮性、RetroArch 核心归一化与 AndroidManifest `<queries>` / 备份规则守卫。
 */
class GameLauncherTest {

    private fun mainSrcDir(): File {
        val candidates = listOf(File("src/main"), File("app/src/main"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate app/src/main from ${File(".").absolutePath}")
    }

    @Test
    fun normalizeRetroArchCoreStripsDuplicateSuffixesAndRejectsTraversal() {
        assertEquals("snes9x", GameLauncher.normalizeRetroArchCore("snes9x"))
        assertEquals("mesen-s", GameLauncher.normalizeRetroArchCore("mesen-s"))
        assertEquals("mgba", GameLauncher.normalizeRetroArchCore("  mgba_libretro_android.so  "))
        assertEquals("fceumm", GameLauncher.normalizeRetroArchCore("fceumm_libretro_android.so_libretro_android.so"))
        assertEquals("bsnes_hd_beta", GameLauncher.normalizeRetroArchCore("bsnes_hd_beta_libretro.so"))

        // 拦截路径穿越与非法字符
        assertNull(GameLauncher.normalizeRetroArchCore(""))
        assertNull(GameLauncher.normalizeRetroArchCore("   "))
        assertNull(GameLauncher.normalizeRetroArchCore("../evil"))
        assertNull(GameLauncher.normalizeRetroArchCore("foo/bar"))
        assertNull(GameLauncher.normalizeRetroArchCore("foo\\bar"))
        assertNull(GameLauncher.normalizeRetroArchCore("a..b"))
        assertNull(GameLauncher.normalizeRetroArchCore("core;rm -rf"))
        assertNull(GameLauncher.normalizeRetroArchCore("_libretro_android.so"))
    }

    @Test
    fun customLauncherCodecPairsWithCoreNormalizer() {
        val raw = CustomLauncherCodec.decodeRaCore(CustomLauncherCodec.encodeRaCore("mesen-s_libretro_android.so"))
        assertEquals("mesen-s", GameLauncher.normalizeRetroArchCore(raw.orEmpty()))
        val traversal = CustomLauncherCodec.decodeRaCore("ra_core:../../data/local/tmp/evil")
        assertNull(GameLauncher.normalizeRetroArchCore(traversal.orEmpty()))
        assertNull(CustomLauncherCodec.decodeRaCore("ra_core:"))
    }

    @Test
    fun playlistCoreRegexMatchesHyphenatedAndUnderscoreCores() {
        assertEquals(
            "mesen-s",
            GameLauncher.extractCoreFromPlaylistLine("""  "core_path": "/data/user/0/com.retroarch.aarch64/cores/mesen-s_libretro_android.so","""),
        )
        assertEquals(
            "mupen64plus_next_gles3",
            GameLauncher.extractCoreFromPlaylistLine("""  "core_path": "/data/data/com.retroarch/cores/mupen64plus_next_gles3_libretro_android.so","""),
        )
        assertNull(GameLauncher.extractCoreFromPlaylistLine("""  "core_path": "DETECT","""))
    }

    @Test
    fun manifestQueriesDeclaresEveryRegisteredEmulatorAndRetroArchPackage() {
        val manifest = File(mainSrcDir(), "AndroidManifest.xml").readText()
        val declaredPackages = Regex("""<package\s+android:name="([^"]+)"\s*/>""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toSet()

        val expectedPackages = buildSet {
            add("com.retroarch")
            add("com.retroarch.aarch64")
            add("com.retroarch.ra32")
            EmulatorRegistry.ALL.forEach { emu -> addAll(emu.packages) }
        }

        val missing = expectedPackages - declaredPackages
        assertTrue(
            "AndroidManifest.xml <queries> 缺失以下模拟器包名（Android 11+ 会导致检测失败）：${missing.sorted()}",
            missing.isEmpty(),
        )
    }

    @Test
    fun filePathsAndBackupRulesEnforceSecurityBoundaries() {
        val mainDir = mainSrcDir()
        val filePathsXml = File(mainDir, "res/xml/file_paths.xml").readText()
        assertFalse(
            "file_paths.xml 禁止以 path=\"/\" 暴露应用私有目录",
            filePathsXml.contains("""path="/" """.trim()),
        )
        assertTrue(filePathsXml.contains("""path="storage/""""))

        val manifest = File(mainDir, "AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))

        val backupRules = File(mainDir, "res/xml/backup_rules.xml").readText()
        val dataExtractionRules = File(mainDir, "res/xml/data_extraction_rules.xml").readText()
        assertTrue(backupRules.contains("localsend_rsa.key"))
        assertTrue(backupRules.contains("user_prefs.json"))
        assertTrue(dataExtractionRules.contains("localsend_rsa.key"))
        assertTrue(dataExtractionRules.contains("user_prefs.json"))
    }
}
