package com.pocketdeck.launcher.data

import com.pocketdeck.launcher.data.model.Platform

/**
 * 文件夹名 → 平台信息映射，列表顺序即为默认标签顺序。
 */
object PlatformCatalog {

    const val ANDROID_ID = "android"

    private const val PORTRAIT = 0.72f   // NES / MD 等竖版盒
    private const val DVD = 0.70f        // PS2 / GC / Wii / 3DS DVD 盒
    private const val LANDSCAPE = 1.37f  // 美版 SNES / N64 横版盒
    private const val SQUARE = 1f

    private fun p(
        id: String, short: String, full: String, aspect: Float,
        system: String?, core: String?,
    ) = Platform(id, short, full, aspect, system, core)

    val known: List<Platform> = listOf(
        // Nintendo
        p("nes", "NES", "Nintendo Entertainment System", PORTRAIT, "Nintendo - Nintendo Entertainment System", "fceumm"),
        p("fds", "FDS", "Famicom Disk System", SQUARE, "Nintendo - Family Computer Disk System", "fceumm"),
        p("snes", "SNES", "Super Nintendo", LANDSCAPE, "Nintendo - Super Nintendo Entertainment System", "snes9x"),
        p("sfc", "SFC", "Super Famicom", LANDSCAPE, "Nintendo - Super Nintendo Entertainment System", "snes9x"),
        p("n64", "N64", "Nintendo 64", LANDSCAPE, "Nintendo - Nintendo 64", "mupen64plus_next_gles3"),
        p("n64dd", "64DD", "Nintendo 64DD", SQUARE, "Nintendo - Nintendo 64DD", "parallel_n64"),
        p("gb", "GB", "Game Boy", SQUARE, "Nintendo - Game Boy", "gambatte"),
        p("gbc", "GBC", "Game Boy Color", SQUARE, "Nintendo - Game Boy Color", "gambatte"),
        p("gba", "GBA", "Game Boy Advance", SQUARE, "Nintendo - Game Boy Advance", "mgba"),
        p("nds", "NDS", "Nintendo DS", 0.89f, "Nintendo - Nintendo DS", "melonds"),
        p("dsi", "DSi", "Nintendo DSi", 0.89f, "Nintendo - Nintendo DSi", "melondsds"),
        p("3ds", "3DS", "Nintendo 3DS", SQUARE, "Nintendo - Nintendo 3DS", "citra"),
        p("virtualboy", "VB", "Virtual Boy", PORTRAIT, "Nintendo - Virtual Boy", "mednafen_vb"),
        p("pokemini", "MINI", "Pokémon Mini", SQUARE, "Nintendo - Pokemon Mini", "pokemini"),
        p("gamecube", "GC", "GameCube", DVD, "Nintendo - GameCube", "dolphin"),
        p("wii", "WII", "Wii", DVD, "Nintendo - Wii", "dolphin"),
        p("wiiu", "WII U", "Wii U", DVD, "Nintendo - Wii U", null),
        p("switch", "SWITCH", "Nintendo Switch", 0.62f, "Nintendo - Nintendo Switch", null),

        // Sega
        p("mastersystem", "SMS", "Master System", PORTRAIT, "Sega - Master System - Mark III", "genesis_plus_gx"),
        p("megadrive", "MD", "Mega Drive", PORTRAIT, "Sega - Mega Drive - Genesis", "genesis_plus_gx"),
        p("genesis", "GEN", "Genesis", PORTRAIT, "Sega - Mega Drive - Genesis", "genesis_plus_gx"),
        p("gamegear", "GG", "Game Gear", 0.8f, "Sega - Game Gear", "genesis_plus_gx"),
        p("sg1000", "SG", "SG-1000", PORTRAIT, "Sega - SG-1000", "genesis_plus_gx"),
        p("segacd", "SCD", "Sega CD", SQUARE, "Sega - Mega-CD - Sega CD", "genesis_plus_gx"),
        p("sega32x", "32X", "Sega 32X", PORTRAIT, "Sega - 32X", "picodrive"),
        p("saturn", "SAT", "Saturn", SQUARE, "Sega - Saturn", "yabasanshiro"),
        p("dreamcast", "DC", "Dreamcast", SQUARE, "Sega - Dreamcast", "flycast"),
        p("naomi", "NAOMI", "Sega NAOMI", PORTRAIT, "Sega - Naomi", "flycast"),
        p("atomiswave", "AW", "Sammy Atomiswave", PORTRAIT, "Atomiswave", "flycast"),

        // Sony
        p("psx", "PS", "PlayStation", SQUARE, "Sony - PlayStation", "swanstation"),
        p("psp", "PSP", "PlayStation Portable", 0.58f, "Sony - PlayStation Portable", "ppsspp"),
        p("psvita", "VITA", "PlayStation Vita", 0.78f, "Sony - PlayStation Vita", null),
        p("ps2", "PS2", "PlayStation 2", DVD, "Sony - PlayStation 2", "pcsx2"),
        p("ps3", "PS3", "PlayStation 3", 0.86f, "Sony - PlayStation 3", null),

        // NEC / SNK / Bandai
        p("pcengine", "PCE", "PC Engine", SQUARE, "NEC - PC Engine - TurboGrafx 16", "mednafen_pce_fast"),
        p("pcenginecd", "PCECD", "PC Engine CD", SQUARE, "NEC - PC Engine CD - TurboGrafx-CD", "mednafen_pce_fast"),
        p("supergrafx", "SGX", "SuperGrafx", SQUARE, "NEC - PC Engine SuperGrafx", "mednafen_supergrafx"),
        p("pcfx", "PC-FX", "NEC PC-FX", SQUARE, "NEC - PC-FX", "mednafen_pcfx"),
        p("ngp", "NGP", "Neo Geo Pocket", SQUARE, "SNK - Neo Geo Pocket", "mednafen_ngp"),
        p("ngpc", "NGPC", "Neo Geo Pocket Color", SQUARE, "SNK - Neo Geo Pocket Color", "mednafen_ngp"),
        p("neogeo", "NEOGEO", "Neo Geo", PORTRAIT, "SNK - Neo Geo", "fbneo"),
        p("neogeocd", "NEOCD", "Neo Geo CD", SQUARE, "SNK - Neo Geo CD", "neocd"),
        p("wswan", "WS", "WonderSwan", SQUARE, "Bandai - WonderSwan", "mednafen_wswan"),
        p("wswanc", "WSC", "WonderSwan Color", SQUARE, "Bandai - WonderSwan Color", "mednafen_wswan"),

        // Atari / Coleco / Mattel / GCE / Magnavox
        p("atari2600", "2600", "Atari 2600", PORTRAIT, "Atari - 2600", "stella2014"),
        p("atari5200", "5200", "Atari 5200", PORTRAIT, "Atari - 5200", "a5200"),
        p("atari7800", "7800", "Atari 7800", PORTRAIT, "Atari - 7800", "prosystem"),
        p("lynx", "LYNX", "Atari Lynx", PORTRAIT, "Atari - Lynx", "handy"),
        p("jaguar", "JAG", "Atari Jaguar", PORTRAIT, "Atari - Jaguar", "virtualjaguar"),
        p("jaguarcd", "JAGCD", "Atari Jaguar CD", SQUARE, "Atari - Jaguar", "virtualjaguar"),
        p("colecovision", "COLECO", "ColecoVision", PORTRAIT, "Coleco - ColecoVision", "gearcoleco"),
        p("intellivision", "INTV", "Intellivision", PORTRAIT, "Mattel - Intellivision", "freeintv"),
        p("vectrex", "VECTREX", "Vectrex", PORTRAIT, "GCE - Vectrex", "vecx"),
        p("odyssey2", "O2", "Magnavox Odyssey 2", PORTRAIT, "Magnavox - Odyssey2", "o2em"),
        p("channelf", "CHF", "Fairchild Channel F", PORTRAIT, "Fairchild - Channel F", "freechaf"),
        p("supervision", "SV", "Watara Supervision", PORTRAIT, "Watara - Supervision", "potator"),
        p("megaduck", "DUCK", "Mega Duck", SQUARE, "Welback - Mega Duck", "sameduck"),

        // Home Computers & Optical
        p("msx", "MSX", "MSX / MSX2", PORTRAIT, "Microsoft - MSX", "bluemsx"),
        p("msx2", "MSX2", "MSX2", PORTRAIT, "Microsoft - MSX2", "bluemsx"),
        p("c64", "C64", "Commodore 64", PORTRAIT, "Commodore - 64", "vice_x64"),
        p("amiga", "AMIGA", "Amiga", PORTRAIT, "Commodore - Amiga", "puae"),
        p("amigacd32", "CD32", "Amiga CD32", SQUARE, "Commodore - Amiga", "puae"),
        p("atarist", "ST", "Atari ST", PORTRAIT, "Atari - ST", "hatari"),
        p("zxspectrum", "ZX", "ZX Spectrum", PORTRAIT, "Sinclair - ZX Spectrum", "fuse"),
        p("amstradcpc", "CPC", "Amstrad CPC", PORTRAIT, "Amstrad - CPC", "cap32"),
        p("x68000", "X68K", "Sharp X68000", PORTRAIT, "Sharp - X68000", "px68k"),
        p("pc98", "PC-98", "NEC PC-98", PORTRAIT, "NEC - PC-98", "np2kai"),
        p("fmtowns", "TOWNS", "FM Towns", SQUARE, "Fujitsu - FM Towns", null),
        p("3do", "3DO", "3DO Interactive Multiplayer", PORTRAIT, "The 3DO Company - 3DO", "opera"),
        p("cdi", "CD-i", "Philips CD-i", SQUARE, "Philips - CD-i", "same_cdi"),

        // Arcade
        p("arcade", "ARCADE", "Arcade", PORTRAIT, "FBNeo - Arcade Games", "fbneo"),
        p("fbneo", "FBNEO", "FinalBurn Neo", PORTRAIT, "FBNeo - Arcade Games", "fbneo"),
        p("mame", "MAME", "MAME", PORTRAIT, "MAME", "mame2003_plus"),
        p("cps1", "CPS1", "CPS-1", PORTRAIT, "FBNeo - Arcade Games", "fbneo"),
        p("cps2", "CPS2", "CPS-2", PORTRAIT, "FBNeo - Arcade Games", "fbneo"),
        p("cps3", "CPS3", "CPS-3", PORTRAIT, "FBNeo - Arcade Games", "fbneo"),
        p("pgm", "PGM", "PolyGame Master", PORTRAIT, "FBNeo - Arcade Games", "fbneo"),

        // Fantasy Consoles / Engines / Handheld / PC / Native
        p("gameandwatch", "G&W", "Game & Watch", SQUARE, "Handheld Electronic Game", "gw"),
        p("pico8", "PICO-8", "PICO-8", SQUARE, "PICO-8", "retro8"),
        p("tic80", "TIC-80", "TIC-80", SQUARE, "TIC-80", "tic80"),
        p("wasm4", "WASM-4", "WASM-4", SQUARE, "WASM-4", "wasm4"),
        p("arduboy", "ARDUBOY", "Arduboy", SQUARE, "Arduboy Inc - Arduboy", "arduous"),
        p("chip8", "CHIP-8", "CHIP-8", SQUARE, null, "jaxe"),
        p("dos", "DOS", "MS-DOS", PORTRAIT, "DOS", "dosbox_pure"),
        p("scummvm", "SCUMM", "ScummVM", PORTRAIT, "ScummVM", "scummvm"),
        p("easyrpg", "RPG", "EasyRPG (RPG Maker 2000/2003)", PORTRAIT, "RPG Maker", "easyrpg"),
        p("openbor", "OPENBOR", "OpenBOR", PORTRAIT, null, null),
        p("j2me", "J2ME", "Java ME (J2ME)", PORTRAIT, null, null),
        p("flash", "FLASH", "Adobe Flash", SQUARE, null, "ruffle"),
        p("winlator", "PC", "Windows / PC (Winlator)", DVD, "Microsoft - Windows", null),
        p("ports", "PORTS", "Ports", PORTRAIT, null, null),
        p(ANDROID_ID, "ANDROID", "Android Native Games", SQUARE, null, null),
    )

    /** 常见文件夹别名映射到标准平台 ID（兼顾复古前端与中文玩家习惯命名）。 */
    private val ALIASES: Map<String, String> = mapOf(
        "fc" to "nes",
        "famicom" to "nes",
        "supernintendo" to "snes",
        "superfamicom" to "sfc",
        "smc" to "snes",
        "ps1" to "psx",
        "ps" to "psx",
        "psone" to "psx",
        "gc" to "gamecube",
        "gcn" to "gamecube",
        "ngc" to "gamecube",
        "md" to "megadrive",
        "sms" to "mastersystem",
        "gg" to "gamegear",
        "scd" to "segacd",
        "megacd" to "segacd",
        "32x" to "sega32x",
        "dc" to "dreamcast",
        "pce" to "pcengine",
        "tg16" to "pcengine",
        "pcecd" to "pcenginecd",
        "tgcd" to "pcenginecd",
        "sgx" to "supergrafx",
        "ns" to "switch",
        "nx" to "switch",
        "n3ds" to "3ds",
        "citra" to "3ds",
        "vita" to "psvita",
        "psv" to "psvita",
        "vb" to "virtualboy",
        "neocd" to "neogeocd",
        "neocdz" to "neogeocd",
        "ws" to "wswan",
        "wsc" to "wswanc",
        "cpc" to "amstradcpc",
        "cd32" to "amigacd32",
        "pc" to "winlator",
        "windows" to "winlator",
        "portmaster" to "ports",
        "apk" to ANDROID_ID,
    )

    private val byId = known.associateBy { it.id }
    private val order = known.withIndex().associate { it.value.id to it.index }

    fun forFolder(folder: String): Platform {
        val lower = folder.lowercase()
        val canonical = ALIASES[lower] ?: lower
        return byId[canonical] ?: Platform(
            id = lower,
            shortName = folder.uppercase().take(8),
            fullName = folder,
        )
    }

    private val knownIds by lazy { known.map { it.id }.toSet() }

    /** 文件夹名是否对应一个已知平台（含别名）。 */
    fun isKnown(folder: String): Boolean = forFolder(folder).id in knownIds

    fun sortKey(id: String): Int = order[id] ?: Int.MAX_VALUE
}
