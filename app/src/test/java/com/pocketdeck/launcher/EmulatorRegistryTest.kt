package com.pocketdeck.launcher

import com.pocketdeck.launcher.launch.CustomLaunchMode
import com.pocketdeck.launcher.launch.CustomLauncherCodec
import com.pocketdeck.launcher.launch.EmulatorRegistry
import com.pocketdeck.launcher.launch.GameLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorRegistryTest {

    @Test
    fun allEmulatorIdsAreUniqueAndLookupWorks() {
        val ids = EmulatorRegistry.ALL.map { it.id }
        assertEquals("Duplicate emulator IDs found", ids.size, ids.toSet().size)
        assertNotNull(EmulatorRegistry.byId("ppsspp"))
        assertNotNull(EmulatorRegistry.byId("duckstation"))
        assertNotNull(EmulatorRegistry.byId("dolphin"))
        assertNull(EmulatorRegistry.byId("nonexistent_emu"))
    }

    @Test
    fun findByPackageRecognizesKnownEmulators() {
        val ppsspp = EmulatorRegistry.findByPackage("org.ppsspp.ppsspp")
        assertNotNull(ppsspp)
        assertEquals("ppsspp", ppsspp?.id)

        val duck = EmulatorRegistry.findByPackage("com.github.stenzek.duckstation")
        assertNotNull(duck)
        assertEquals("duckstation", duck?.id)

        assertEquals("skyemu", EmulatorRegistry.findByPackage("com.sky.SkyEmu")?.id)
        assertEquals("skyemu", EmulatorRegistry.findByPackage("com.skylersaleh.skyemu")?.id)
        assertEquals("lemuroid", EmulatorRegistry.findByPackage("com.swordfish.lemuroid")?.id)
        assertEquals("nes_emu", EmulatorRegistry.findByPackage("com.explusalpha.NesEmu")?.id)
        assertEquals("snes9x_ex", EmulatorRegistry.findByPackage("com.explusalpha.Snes9xPlus")?.id)
        assertEquals("azahar", EmulatorRegistry.findByPackage("org.azahar_emu.azahar")?.id)
        assertEquals("flycast_standalone", EmulatorRegistry.findByPackage("com.flycast.emulator")?.id)

        assertNull(EmulatorRegistry.findByPackage("com.example.unknown"))
    }

    @Test
    fun platformMappingCoversCoreConsoles() {
        assertTrue(EmulatorRegistry.allForPlatform("psp").any { it.id == "ppsspp" })
        assertTrue(EmulatorRegistry.allForPlatform("psx").any { it.id == "duckstation" })
        assertTrue(EmulatorRegistry.allForPlatform("gba").any { it.id == "skyemu" })
        assertTrue(EmulatorRegistry.allForPlatform("gb").any { it.id == "skyemu" })
        assertTrue(EmulatorRegistry.allForPlatform("nes").any { it.id == "nes_emu" })
        assertTrue(EmulatorRegistry.allForPlatform("snes").any { it.id == "snes9x_ex" })
        assertTrue(EmulatorRegistry.allForPlatform("nds").any { it.id == "melonds_standalone" })
        assertTrue(EmulatorRegistry.allForPlatform("3ds").any { it.id == "azahar" })
        assertTrue(EmulatorRegistry.allForPlatform("dreamcast").any { it.id == "flycast_standalone" })
    }
}

class CustomLauncherCodecTest {

    @Test
    fun roundTripsRetroArchCore() {
        val encoded = CustomLauncherCodec.encodeRaCore("mgba_libretro_android.so")
        assertEquals("ra_core:mgba_libretro_android.so", encoded)
        assertEquals("mgba_libretro_android.so", CustomLauncherCodec.decodeRaCore(encoded))
        assertNull(CustomLauncherCodec.decodeRaCore("ppsspp"))
        assertNull(CustomLauncherCodec.emulatorIdOf(encoded))
    }

    @Test
    fun roundTripsEmulatorVariant() {
        val encoded = CustomLauncherCodec.encodeEmuVariant("ppsspp", "org.ppsspp.ppssppgold")
        assertEquals("emu:ppsspp|org.ppsspp.ppssppgold", encoded)
        assertEquals("ppsspp" to "org.ppsspp.ppssppgold", CustomLauncherCodec.decodeEmuVariant(encoded))
        assertEquals("ppsspp", CustomLauncherCodec.emulatorIdOf(encoded))
        assertEquals("dolphin", CustomLauncherCodec.emulatorIdOf("dolphin"))
        assertNull(CustomLauncherCodec.emulatorIdOf(GameLauncher.RETROARCH_ID))
    }

    @Test
    fun roundTripsCustomAppSpecAndSanitizesPipe() {
        val encoded = CustomLauncherCodec.encodeCustomApp(
            pkg = "com.example.emu",
            mode = CustomLaunchMode.MainWithExtra,
            extraKey = "ROM|PATH",
            activityClass = "com.example.emu.MainActivity",
        )
        val decoded = CustomLauncherCodec.decodeCustomApp(encoded)
        assertNotNull(decoded)
        assertEquals("com.example.emu", decoded?.packageName)
        assertEquals(CustomLaunchMode.MainWithExtra, decoded?.mode)
        assertEquals("ROMPATH", decoded?.extraKey)
        assertEquals("com.example.emu.MainActivity", decoded?.activityClass)
    }

    @Test
    fun invalidCustomAppSpecReturnsNull() {
        assertNull(CustomLauncherCodec.decodeCustomApp(null))
        assertNull(CustomLauncherCodec.decodeCustomApp("app:"))
        assertNull(CustomLauncherCodec.decodeCustomApp("ppsspp"))
    }
}
