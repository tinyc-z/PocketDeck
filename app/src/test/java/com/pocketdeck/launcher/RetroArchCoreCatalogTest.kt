package com.pocketdeck.launcher

import com.pocketdeck.launcher.launch.RetroArchCoreCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroArchCoreCatalogTest {

    @Test
    fun extractCoreIdFromSoParsesValidAndRejectsInvalid() {
        assertEquals("mgba", RetroArchCoreCatalog.extractCoreIdFromSo("mgba_libretro_android.so"))
        assertEquals("snes9x2002", RetroArchCoreCatalog.extractCoreIdFromSo("snes9x2002_libretro_android.so"))
        assertEquals("fceumm", RetroArchCoreCatalog.extractCoreIdFromSo("fceumm_libretro_android.so"))
        assertNull(RetroArchCoreCatalog.extractCoreIdFromSo("_libretro_android.so"))
        assertNull(RetroArchCoreCatalog.extractCoreIdFromSo("mgba.so"))
        assertNull(RetroArchCoreCatalog.extractCoreIdFromSo("readme.txt"))
    }

    @Test
    fun displayNameReturnsHumanReadableLabel() {
        assertTrue(RetroArchCoreCatalog.displayName("mgba").contains("mGBA", ignoreCase = true))
        assertTrue(RetroArchCoreCatalog.displayName("gambatte").contains("Gambatte", ignoreCase = true))
        assertTrue(RetroArchCoreCatalog.displayName("fceumm").contains("FCEUmm", ignoreCase = true))
        assertEquals("Custom Core X", RetroArchCoreCatalog.displayName("custom_core_x"))
    }
}
