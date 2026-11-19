package com.pocketdeck.launcher.launch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pocketdeck.launcher.data.model.Game
import java.io.File

/**
 * 自定义外部 App 启动协议。
 */
enum class CustomLaunchMode {
    /** ACTION_VIEW + FileProvider content:// URI（推荐：兼容 Android 11+ 现代模拟器）。 */
    ViewContentUri,
    /** ACTION_VIEW + file:// URI（兼容旧版模拟器）。 */
    ViewFileUri,
    /** ACTION_MAIN + putExtra(extraKey, romPath)（如 DuckStation bootPath / RetroArch ROM）。 */
    MainWithExtra,
    /** 仅直接打开应用（适合 Winlator / 独立启动器 / 云游戏）。 */
    AppOnly,
}

/**
 * 自定义启动器编码解析器（统一编码为字符串存入 platformLaunchers / gameLaunchers）。
 *
 * 格式：
 * - "retroarch" → RetroArch 自动核心
 * - "ra_core:<coreName>" → RetroArch 指定核心
 * - "app:<pkg>|<mode>|<extraKey>" → 任意已安装 Android App + 启动协议
 * - 其它 → [EmulatorRegistry] 中的内置模拟器 ID
 */
object CustomLauncherCodec {
    const val PREFIX_RA_CORE = "ra_core:"
    const val PREFIX_CUSTOM_APP = "app:"

    data class CustomAppSpec(
        val packageName: String,
        val mode: CustomLaunchMode,
        val extraKey: String = "",
        val activityClass: String = "",
    )

    fun encodeRaCore(core: String): String = "$PREFIX_RA_CORE$core"
    fun decodeRaCore(id: String?): String? =
        if (id != null && id.startsWith(PREFIX_RA_CORE)) id.removePrefix(PREFIX_RA_CORE).takeIf { it.isNotBlank() } else null

    fun encodeCustomApp(
        pkg: String,
        mode: CustomLaunchMode,
        extraKey: String = "",
        activityClass: String = "",
    ): String {
        val cleanKey = extraKey.replace("|", "")
        val cleanAct = activityClass.replace("|", "")
        return if (cleanAct.isBlank()) "$PREFIX_CUSTOM_APP$pkg|${mode.name}|$cleanKey"
        else "$PREFIX_CUSTOM_APP$pkg|${mode.name}|$cleanKey|$cleanAct"
    }

    fun decodeCustomApp(id: String?): CustomAppSpec? {
        if (id == null || !id.startsWith(PREFIX_CUSTOM_APP)) return null
        val parts = id.removePrefix(PREFIX_CUSTOM_APP).split('|')
        val pkg = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val mode = parts.getOrNull(1)?.let { runCatching { CustomLaunchMode.valueOf(it) }.getOrNull() }
            ?: CustomLaunchMode.ViewContentUri
        val extra = parts.getOrNull(2).orEmpty()
        val activity = parts.getOrNull(3).orEmpty()
        return CustomAppSpec(pkg, mode, extra, activity)
    }

    fun uriForFile(context: Context, path: String, useContentUri: Boolean): Uri {
        val file = File(path)
        if (!useContentUri) return Uri.fromFile(file)
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse { Uri.fromFile(file) }
    }
}

/**
 * 外部独立模拟器注册表。
 */
data class EmulatorDef(
    val displayName: String,
    val id: String,
    val packages: List<String>,
    val intentBuilder: (context: Context, pkg: String, game: Game) -> Intent,
)

private fun viewIntent(
    context: Context,
    pkg: String,
    game: Game,
    preferContentUri: Boolean = true,
): Intent {
    val uri = CustomLauncherCodec.uriForFile(context, game.id, preferContentUri)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/octet-stream")
        setPackage(pkg)
        if (preferContentUri) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/**
 * 所有已知独立模拟器。
 *
 * 注意：RetroArch 由 [GameLauncher] 通过 libretro 核心方式单独处理。
 */
object EmulatorRegistry {

    val PPSSPP = EmulatorDef(
        displayName = "PPSSPP",
        id = "ppsspp",
        packages = listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val DRASTIC = EmulatorDef(
        displayName = "DraStic",
        id = "drastic",
        packages = listOf("com.dsemu.drastic"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val MELONDS = EmulatorDef(
        displayName = "melonDS",
        id = "melonds_standalone",
        packages = listOf("me.magnum.melonds"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val DUCKSTATION = EmulatorDef(
        displayName = "DuckStation",
        id = "duckstation",
        packages = listOf("com.github.stenzek.duckstation"),
        intentBuilder = { _, pkg, game ->
            Intent(Intent.ACTION_MAIN).apply {
                setClassName(pkg, "com.github.stenzek.duckstation.EmulationActivity")
                putExtra("bootPath", game.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        },
    )

    val AETHERSX2 = EmulatorDef(
        displayName = "AetherSX2 / NetherSX2",
        id = "aethersx2",
        packages = listOf("xyz.aethersx2.android", "com.nethersx2.emulator", "com.tahlreth.aethersx2"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val DOLPHIN = EmulatorDef(
        displayName = "Dolphin",
        id = "dolphin",
        packages = listOf(
            "org.dolphinemu.dolphinemu",
            "org.dolphinemu.handheld",
            "org.dolphinemu.mmjr",
            "org.dolphinemu.mmjr2",
            "org.mmjr.dolphinemu",
        ),
        intentBuilder = { _, pkg, game ->
            Intent(Intent.ACTION_MAIN).apply {
                setClassName(pkg, "org.dolphinemu.dolphinemu.activities.EmulationActivity")
                putExtra("AutoStartFile", game.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        },
    )

    val CITRA = EmulatorDef(
        displayName = "Citra / Azahar",
        id = "citra",
        packages = listOf(
            "org.azahar_emu.azahar",
            "io.github.lime3ds.android",
            "org.citra.citra_emu",
            "org.citra.emu",
            "org.citra.citra_emu.canary",
        ),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val YUZU_FORKS = EmulatorDef(
        displayName = "Yuzu / Sudachi / Citron / Eden",
        id = "yuzu_switch",
        packages = listOf(
            "dev.eden.eden_emulator",
            "org.citron.citron_emu",
            "org.sudachi.sudachi_emu",
            "org.yuzu.yuzu_emu",
            "dev.suyu.suyu_emu",
            "org.ryujinx.android",
        ),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val CEMU = EmulatorDef(
        displayName = "Cemu",
        id = "cemu",
        packages = listOf("info.cemu.cemu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val VITA3K = EmulatorDef(
        displayName = "Vita3K",
        id = "vita3k",
        packages = listOf("org.vita3k.emulator"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val FLYCAST = EmulatorDef(
        displayName = "Flycast",
        id = "flycast_standalone",
        packages = listOf("com.flycast.emulator"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val YABA = EmulatorDef(
        displayName = "Yaba Sanshiro",
        id = "yabasanshiro_standalone",
        packages = listOf("org.uoyabause.urern.pro", "org.uoyabause.urern"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val MGBA = EmulatorDef(
        displayName = "mGBA",
        id = "mgba_standalone",
        packages = listOf("io.mgba"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val PIZZABOY_GBA = EmulatorDef(
        displayName = "Pizza Boy A",
        id = "pizzaboy_gba",
        packages = listOf("it.dbtecno.pizzaboygbapro", "it.dbtecno.pizzaboygba"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val PIZZABOY_GBC = EmulatorDef(
        displayName = "Pizza Boy C",
        id = "pizzaboy_gbc",
        packages = listOf("it.dbtecno.pizzaboypro", "it.dbtecno.pizzaboy"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val MYBOY = EmulatorDef(
        displayName = "My Boy!",
        id = "myboy",
        packages = listOf("com.fastemulator.gba", "com.fastemulator.gbafree"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val MYOLDBOY = EmulatorDef(
        displayName = "My OldBoy!",
        id = "myoldboy",
        packages = listOf("com.fastemulator.gbc", "com.fastemulator.gbcfree"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val MUPEN64 = EmulatorDef(
        displayName = "Mupen64Plus FZ",
        id = "mupen64plus",
        packages = listOf("org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val REDREAM = EmulatorDef(
        displayName = "Redream",
        id = "redream",
        packages = listOf("io.recompiled.redream"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val LEMUROID = EmulatorDef(
        displayName = "Lemuroid",
        id = "lemuroid",
        packages = listOf("com.swordfish.lemuroid"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val J2ME_LOADER = EmulatorDef(
        displayName = "J2ME Loader",
        id = "j2me_loader",
        packages = listOf("ru.playsoftware.j2meloader"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val EASYRPG = EmulatorDef(
        displayName = "EasyRPG Player",
        id = "easyrpg_standalone",
        packages = listOf("org.easyrpg.player"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val SCUMMVM = EmulatorDef(
        displayName = "ScummVM",
        id = "scummvm_standalone",
        packages = listOf("org.scummvm.scummvm"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val OPENBOR = EmulatorDef(
        displayName = "OpenBOR",
        id = "openbor_standalone",
        packages = listOf("org.openbor.engine"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val WINLATOR = EmulatorDef(
        displayName = "Winlator / MiceWine / GameHub",
        id = "winlator_standalone",
        packages = listOf("com.winlator.cmod", "com.winlator.frost", "com.winlator", "com.micewine.emu", "com.xiaoji.egggame"),
        intentBuilder = { ctx, pkg, _ ->
            ctx.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: Intent(Intent.ACTION_MAIN).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )

    val EPSXE = EmulatorDef(
        displayName = "ePSXe",
        id = "epsxe",
        packages = listOf("com.epsxe.ePSXe"),
        intentBuilder = { _, pkg, game ->
            Intent(Intent.ACTION_MAIN).apply {
                setClassName(pkg, "com.epsxe.ePSXe.ePSXe")
                putExtra("com.epsxe.ePSXe.isoName", game.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        },
    )

    val FPSE = EmulatorDef(
        displayName = "FPse",
        id = "fpse",
        packages = listOf("com.emulator.fpse64", "com.emulator.fpse"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val APS3E = EmulatorDef(
        displayName = "RPCS3 / aPS3e",
        id = "aps3e",
        packages = listOf("aenu.aps3e", "net.rpcs3.rpcs3"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val SNES9X_EX = EmulatorDef(
        displayName = "Snes9x EX+",
        id = "snes9x_ex",
        packages = listOf("com.explusalpha.Snes9xPlus"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val NES_EMU = EmulatorDef(
        displayName = "NES.emu",
        id = "nes_emu",
        packages = listOf("com.explusalpha.NesEmu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val GBA_EMU = EmulatorDef(
        displayName = "GBA.emu",
        id = "gba_emu",
        packages = listOf("com.explusalpha.GbaEmu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val GBC_EMU = EmulatorDef(
        displayName = "GBC.emu",
        id = "gbc_emu",
        packages = listOf("com.explusalpha.GbcEmu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val MD_EMU = EmulatorDef(
        displayName = "MD.emu",
        id = "md_emu",
        packages = listOf("com.explusalpha.MdEmu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val PCE_EMU = EmulatorDef(
        displayName = "PCE.emu",
        id = "pce_emu",
        packages = listOf("com.explusalpha.PceEmu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val MSX_EMU = EmulatorDef(
        displayName = "MSX.emu",
        id = "msx_emu",
        packages = listOf("com.explusalpha.MsxEmu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val NGP_EMU = EmulatorDef(
        displayName = "NGP.emu",
        id = "ngp_emu",
        packages = listOf("com.explusalpha.NgpEmu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val A2600_EMU = EmulatorDef(
        displayName = "2600.emu",
        id = "a2600_emu",
        packages = listOf("com.explusalpha.A2600Emu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val C64_EMU = EmulatorDef(
        displayName = "C64.emu",
        id = "c64_emu",
        packages = listOf("com.explusalpha.C64Emu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val SATURN_EMU = EmulatorDef(
        displayName = "Saturn.emu",
        id = "saturn_emu",
        packages = listOf("com.explusalpha.SaturnEmu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val LYNX_EMU = EmulatorDef(
        displayName = "Lynx.emu",
        id = "lynx_emu",
        packages = listOf("com.explusalpha.LynxEmu"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game) },
    )

    val JOHN_NESS = EmulatorDef(
        displayName = "John NESS",
        id = "john_ness",
        packages = listOf("com.johnemulators.johnness"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val JOHN_GBAC = EmulatorDef(
        displayName = "John GBAC",
        id = "john_gbac",
        packages = listOf("com.johnemulators.johngbac"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    val MAGIC_DOSBOX = EmulatorDef(
        displayName = "Magic DosBox",
        id = "magic_dosbox",
        packages = listOf("bruenor.magicbox", "bruenor.magicbox.free"),
        intentBuilder = { ctx, pkg, game -> viewIntent(ctx, pkg, game, preferContentUri = true) },
    )

    /** 每个平台 ID → 可用的独立模拟器（不含 RetroArch）。 */
    val PLATFORM_EMULATORS: Map<String, List<EmulatorDef>> = buildMap {
        put("psp", listOf(PPSSPP, LEMUROID))
        listOf("nds", "dsi").forEach { put(it, listOf(DRASTIC, MELONDS, LEMUROID)) }
        put("psx", listOf(DUCKSTATION, EPSXE, FPSE, LEMUROID))
        put("ps2", listOf(AETHERSX2))
        put("ps3", listOf(APS3E))
        put("psvita", listOf(VITA3K))
        listOf("gamecube", "wii").forEach { put(it, listOf(DOLPHIN)) }
        put("wiiu", listOf(CEMU))
        put("3ds", listOf(CITRA, LEMUROID))
        put("switch", listOf(YUZU_FORKS))
        listOf("dreamcast", "naomi", "atomiswave").forEach { put(it, listOf(FLYCAST, REDREAM)) }
        put("saturn", listOf(YABA, SATURN_EMU))
        put("n64", listOf(MUPEN64, LEMUROID))
        listOf("gb", "gbc").forEach { put(it, listOf(PIZZABOY_GBC, GBC_EMU, MYOLDBOY, JOHN_GBAC, MGBA, LEMUROID)) }
        put("gba", listOf(PIZZABOY_GBA, GBA_EMU, MYBOY, JOHN_GBAC, MGBA, LEMUROID))
        listOf("nes", "fds").forEach { put(it, listOf(NES_EMU, JOHN_NESS, LEMUROID)) }
        listOf("snes", "sfc").forEach { put(it, listOf(SNES9X_EX, JOHN_NESS, LEMUROID)) }
        listOf("mastersystem", "megadrive", "genesis", "gamegear", "segacd", "sega32x").forEach {
            put(it, listOf(MD_EMU, LEMUROID))
        }
        listOf("pcengine", "pcenginecd", "supergrafx").forEach { put(it, listOf(PCE_EMU, LEMUROID)) }
        put("msx", listOf(MSX_EMU))
        put("msx2", listOf(MSX_EMU))
        listOf("ngp", "ngpc").forEach { put(it, listOf(NGP_EMU, LEMUROID)) }
        put("atari2600", listOf(A2600_EMU, LEMUROID))
        put("c64", listOf(C64_EMU))
        put("lynx", listOf(LYNX_EMU, LEMUROID))
        listOf("arcade", "fbneo", "mame", "neogeo", "cps1", "cps2", "cps3", "pgm").forEach {
            put(it, listOf(LEMUROID))
        }
        put("dos", listOf(MAGIC_DOSBOX))
        put("j2me", listOf(J2ME_LOADER))
        put("easyrpg", listOf(EASYRPG))
        put("scummvm", listOf(SCUMMVM))
        put("openbor", listOf(OPENBOR))
        put("winlator", listOf(WINLATOR))
    }

    /** 所有已知的独立模拟器（去重，共 40+ 款）。 */
    val ALL: List<EmulatorDef> = listOf(
        PPSSPP, DRASTIC, MELONDS, DUCKSTATION, EPSXE, FPSE, AETHERSX2, APS3E, DOLPHIN, CITRA,
        YUZU_FORKS, CEMU, VITA3K, FLYCAST, REDREAM, YABA, SATURN_EMU, MUPEN64,
        SNES9X_EX, NES_EMU, JOHN_NESS, GBA_EMU, GBC_EMU, PIZZABOY_GBA, PIZZABOY_GBC,
        MYBOY, MYOLDBOY, JOHN_GBAC, MGBA, MD_EMU, PCE_EMU, MSX_EMU, NGP_EMU,
        A2600_EMU, C64_EMU, LYNX_EMU, LEMUROID, MAGIC_DOSBOX, J2ME_LOADER,
        EASYRPG, SCUMMVM, OPENBOR, WINLATOR,
    )

    fun byId(id: String): EmulatorDef? = ALL.firstOrNull { it.id == id }

    /** 返回某平台在注册表中支持的全部独立模拟器列表（含未安装的）。 */
    fun allForPlatform(platformId: String): List<EmulatorDef> =
        PLATFORM_EMULATORS[platformId].orEmpty()

    /** 检查某个模拟器是否已安装，返回可用的包名（兼容 Android 11+ 包可见性）。 */
    fun installedPackage(context: Context, emu: EmulatorDef): String? {
        val pm = context.packageManager
        return emu.packages.firstOrNull { pkg ->
            pm.getLaunchIntentForPackage(pkg) != null ||
                runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
        }
    }

    /** 获取某平台所有已安装的独立模拟器列表。 */
    fun availableForPlatform(context: Context, platformId: String): List<Pair<EmulatorDef, String>> {
        val emus = PLATFORM_EMULATORS[platformId] ?: return emptyList()
        return emus.mapNotNull { emu ->
            installedPackage(context, emu)?.let { pkg -> emu to pkg }
        }
    }
}
