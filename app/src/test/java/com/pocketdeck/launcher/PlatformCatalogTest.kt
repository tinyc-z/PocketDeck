package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.PlatformCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformCatalogTest {

    @Test
    fun aliasesResolveToCanonicalPlatforms() {
        assertEquals("nes", PlatformCatalog.forFolder("fc").id)
        assertEquals("nes", PlatformCatalog.forFolder("FAMICOM").id)
        assertEquals("psx", PlatformCatalog.forFolder("ps1").id)
        assertEquals("megadrive", PlatformCatalog.forFolder("md").id)
        assertEquals("gamecube", PlatformCatalog.forFolder("gc").id)
        assertEquals("dreamcast", PlatformCatalog.forFolder("dc").id)
        assertEquals("switch", PlatformCatalog.forFolder("ns").id)
        assertEquals(PlatformCatalog.ANDROID_ID, PlatformCatalog.forFolder("apk").id)
    }

    @Test
    fun isKnownDistinguishesKnownAndCustomFolders() {
        assertTrue(PlatformCatalog.isKnown("gba"))
        assertTrue(PlatformCatalog.isKnown("PS1"))
        assertTrue(PlatformCatalog.isKnown("md"))
        assertFalse(PlatformCatalog.isKnown("test"))
        assertFalse(PlatformCatalog.isKnown("my_custom_roms"))
    }

    @Test
    fun unknownFolderFallsBackCleanly() {
        val custom = PlatformCatalog.forFolder("MyHomebrew")
        assertEquals("myhomebrew", custom.id)
        assertEquals("MYHOMEBR", custom.shortName)
        assertEquals("MyHomebrew", custom.fullName)
    }

    @Test
    fun sortKeyOrdersKnownBeforeUnknown() {
        assertTrue(PlatformCatalog.sortKey("nes") < PlatformCatalog.sortKey("gba"))
        assertTrue(PlatformCatalog.sortKey("gba") < PlatformCatalog.sortKey("unknown_folder"))
    }
}
