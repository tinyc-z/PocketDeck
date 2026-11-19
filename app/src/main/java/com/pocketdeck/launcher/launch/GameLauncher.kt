package com.pocketdeck.launcher.launch

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import java.io.File

/**
 * 统一游戏启动入口。
 *
 * 支持：
 * 1. 原生 Android APK 游戏（id 以 "apk://" 开头）
 * 2. 自定义任意 Android App 启动器（"app:<pkg>|<mode>|<extra>"）
 * 3. RetroArch 自动或指定核心（"retroarch" / "ra_core:<core>"）
 * 4. 内置独立模拟器（PPSSPP / DuckStation / Dolphin / Citra / Azahar / Yuzu forks / NetherSX2 等）
 */
object GameLauncher {

    const val RETROARCH_ID = "retroarch"
    const val APK_PREFIX = "apk://"

    private val RETROARCH_PACKAGES = listOf(
        "com.retroarch.aarch64",
        "com.retroarch",
        "com.retroarch.ra32",
    )

    /** 平台可能的备用核心顺序。 */
    private val CORE_CANDIDATES: Map<String, List<String>> = mapOf(
        "snes" to listOf("snes9x", "snes9x2002", "snes9x2010", "snes9x2005", "snes9x2005_plus", "bsnes"),
        "sfc" to listOf("snes9x", "snes9x2002", "snes9x2010", "snes9x2005", "bsnes"),
        "nes" to listOf("fceumm", "nestopia", "quicknes", "mesen"),
        "fds" to listOf("fceumm", "nestopia", "mesen"),
        "gb" to listOf("gambatte", "sameboy", "mgba", "gearboy"),
        "gbc" to listOf("gambatte", "sameboy", "mgba", "gearboy"),
        "gba" to listOf("mgba", "vba_next", "vbam", "gpsp"),
        "megadrive" to listOf("genesis_plus_gx", "picodrive"),
        "genesis" to listOf("genesis_plus_gx", "picodrive"),
        "mastersystem" to listOf("genesis_plus_gx", "picodrive"),
        "gamegear" to listOf("genesis_plus_gx", "picodrive"),
        "segacd" to listOf("genesis_plus_gx", "picodrive"),
        "psx" to listOf("swanstation", "pcsx_rearmed", "mednafen_psx_hw", "mednafen_psx"),
        "n64" to listOf("mupen64plus_next_gles3", "mupen64plus_next_gles2", "parallel_n64"),
        "nds" to listOf("melonds", "melondsds", "desmume"),
        "3ds" to listOf("citra", "citra2018"),
        "dreamcast" to listOf("flycast"),
        "saturn" to listOf("yabasanshiro", "yabause", "mednafen_saturn"),
        "arcade" to listOf("fbneo", "mame2003_plus", "mame"),
        "fbneo" to listOf("fbneo", "mame2003_plus"),
        "mame" to listOf("mame2003_plus", "mame", "fbneo"),
        "neogeo" to listOf("fbneo", "mame2003_plus"),
        "cps1" to listOf("fbneo", "mame2003_plus"),
        "cps2" to listOf("fbneo", "mame2003_plus"),
        "cps3" to listOf("fbneo", "mame2003_plus"),
        "sega32x" to listOf("picodrive", "genesis_plus_gx"),
        "pcengine" to listOf("mednafen_pce_fast", "mednafen_pce"),
        "pcenginecd" to listOf("mednafen_pce_fast", "mednafen_pce"),
        "supergrafx" to listOf("mednafen_supergrafx", "mednafen_pce"),
        "ngp" to listOf("mednafen_ngp"),
        "ngpc" to listOf("mednafen_ngp"),
        "wswan" to listOf("mednafen_wswan"),
        "wswanc" to listOf("mednafen_wswan"),
        "atari2600" to listOf("stella", "stella2014"),
        "lynx" to listOf("handy", "mednafen_lynx"),
        "msx" to listOf("bluemsx", "fmsx"),
        "msx2" to listOf("bluemsx", "fmsx"),
        "c64" to listOf("vice_x64", "vice_x64sc"),
        "amiga" to listOf("puae", "uae4arm"),
        "3do" to listOf("opera"),
        "dos" to listOf("dosbox_pure", "dosbox_svn"),
        "scummvm" to listOf("scummvm"),
        "easyrpg" to listOf("easyrpg"),
        "pico8" to listOf("retro8", "fake08"),
        "tic80" to listOf("tic80"),
    )

    sealed interface Result {
        data object Ok : Result
        data class Error(val message: String) : Result
    }

    fun isRetroArchInstalled(context: Context): Boolean =
        RETROARCH_PACKAGES.any { isInstalled(context, it) }

    fun installedRetroArchCores(context: Context, platform: Platform?): List<String> {
        val raPkg = RETROARCH_PACKAGES.firstOrNull { isInstalled(context, it) } ?: return emptyList()
        val configured = detectConfiguredCores(raPkg)
        val candidates = buildList {
            platform?.core?.let { add(it) }
            platform?.id?.let { CORE_CANDIDATES[it]?.forEach { c -> if (c !in this) add(c) } }
            configured.sorted().forEach { c -> if (c !in this) add(c) }
        }
        return candidates
    }

    /**
     * 获取某平台可用的内置启动器（已安装的），返回 (id, displayName) 列表。
     */
    fun availableLaunchers(context: Context, platformId: String): List<Pair<String, String>> {
        if (platformId == PlatformCatalog.ANDROID_ID) return emptyList()
        val result = mutableListOf<Pair<String, String>>()

        val raPkg = RETROARCH_PACKAGES.firstOrNull { isInstalled(context, it) }
        if (raPkg != null) {
            val platform = PlatformCatalog.forFolder(platformId)
            val core = resolveInstalledCore(raPkg, platform)
            if (core != null) {
                result.add(RETROARCH_ID to "RetroArch")
            }
        }

        EmulatorRegistry.availableForPlatform(context, platformId).forEach { (emu, _) ->
            result.add(emu.id to emu.displayName)
        }

        return result
    }

    /** 返回该平台支持的独立模拟器及是否已安装。 */
    fun supportedStandaloneEmulators(context: Context, platformId: String): List<Pair<EmulatorDef, Boolean>> {
        if (platformId == PlatformCatalog.ANDROID_ID) return emptyList()
        return EmulatorRegistry.allForPlatform(platformId).map { emu ->
            emu to (EmulatorRegistry.installedPackage(context, emu) != null)
        }
    }

    /** 解析启动器 ID 为可读显示名（用于设置界面与属性弹窗）。 */
    fun describeLauncher(context: Context, platformId: String, launcherId: String?): String? {
        if (platformId == PlatformCatalog.ANDROID_ID) return "Android App"
        if (launcherId == null) {
            val first = availableLaunchers(context, platformId).firstOrNull()
            return first?.second
        }
        if (launcherId == RETROARCH_ID) {
            val raPkg = RETROARCH_PACKAGES.firstOrNull { isInstalled(context, it) }
            val core = raPkg?.let { resolveInstalledCore(it, PlatformCatalog.forFolder(platformId)) }
            return if (core != null) "RetroArch ($core)" else "RetroArch"
        }
        CustomLauncherCodec.decodeRaCore(launcherId)?.let { core ->
            return "RetroArch ($core)"
        }
        CustomLauncherCodec.decodeCustomApp(launcherId)?.let { spec ->
            val appLabel = runCatching {
                val pm = context.packageManager
                val ai = pm.getApplicationInfo(spec.packageName, 0)
                pm.getApplicationLabel(ai).toString()
            }.getOrDefault(spec.packageName)
            return appLabel
        }
        EmulatorRegistry.byId(launcherId)?.let { return it.displayName }
        return launcherId
    }

    /**
     * 启动游戏。
     */
    fun launch(
        context: Context,
        game: Game,
        platform: Platform?,
        preferredEmulatorId: String? = null,
    ): Result {
        if (game.id.startsWith(APK_PREFIX)) {
            val pkg = game.id.removePrefix(APK_PREFIX)
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: return Result.Error("App not installed: $pkg")
            return startSafely(context, intent, game.title)
        }

        val resolvedPlatform = platform ?: PlatformCatalog.forFolder(game.platformId)

        // 1. 用户指定了任意自定义 App 启动器
        CustomLauncherCodec.decodeCustomApp(preferredEmulatorId)?.let { spec ->
            return launchWithCustomApp(context, game, spec)
        }

        // 2. 用户指定了 RetroArch 特定核心
        CustomLauncherCodec.decodeRaCore(preferredEmulatorId)?.let { core ->
            return launchWithRetroArch(context, game, resolvedPlatform, coreOverride = core)
        }

        // 3. 用户指定了内置独立模拟器
        if (preferredEmulatorId != null && preferredEmulatorId != RETROARCH_ID) {
            val standaloneResult = launchWithStandalone(context, game, preferredEmulatorId)
            if (standaloneResult is Result.Ok) return standaloneResult
            return standaloneResult
        }

        // 4. 用户指定 RetroArch 或未指定（自动）
        if (preferredEmulatorId == null || preferredEmulatorId == RETROARCH_ID) {
            val raResult = launchWithRetroArch(context, game, resolvedPlatform)
            if (raResult is Result.Ok) return raResult
            if (preferredEmulatorId == RETROARCH_ID) return raResult
        }

        // 5. 自动回退：尝试该平台的第一个已安装独立模拟器
        val fallback = EmulatorRegistry.availableForPlatform(context, resolvedPlatform.id).firstOrNull()
        if (fallback != null) {
            return launchStandalone(context, game, fallback.first, fallback.second)
        }

        return Result.Error("No emulator found for ${resolvedPlatform.fullName}")
    }

    private fun launchWithCustomApp(
        context: Context,
        game: Game,
        spec: CustomLauncherCodec.CustomAppSpec,
    ): Result {
        if (!isInstalled(context, spec.packageName)) {
            return Result.Error("App not installed: ${spec.packageName}")
        }
        val intent = when (spec.mode) {
            CustomLaunchMode.ViewContentUri -> Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(CustomLauncherCodec.uriForFile(context, game.id, useContentUri = true), "application/octet-stream")
                if (spec.activityClass.isNotBlank()) setClassName(spec.packageName, spec.activityClass)
                else setPackage(spec.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            CustomLaunchMode.ViewFileUri -> Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(CustomLauncherCodec.uriForFile(context, game.id, useContentUri = false), "application/octet-stream")
                if (spec.activityClass.isNotBlank()) setClassName(spec.packageName, spec.activityClass)
                else setPackage(spec.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            CustomLaunchMode.MainWithExtra -> {
                val base = if (spec.activityClass.isNotBlank()) {
                    Intent(Intent.ACTION_MAIN).setClassName(spec.packageName, spec.activityClass)
                } else {
                    context.packageManager.getLaunchIntentForPackage(spec.packageName)
                        ?: Intent(Intent.ACTION_MAIN).setPackage(spec.packageName)
                }
                val key = spec.extraKey.ifBlank { "ROM" }
                base.apply {
                    putExtra(key, game.id)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            CustomLaunchMode.AppOnly -> {
                if (spec.activityClass.isNotBlank()) {
                    Intent(Intent.ACTION_MAIN).setClassName(spec.packageName, spec.activityClass)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } else {
                    context.packageManager.getLaunchIntentForPackage(spec.packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    } ?: Intent(Intent.ACTION_MAIN).setPackage(spec.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
        val res = startSafely(context, intent, spec.packageName)
        if (res is Result.Error && spec.mode == CustomLaunchMode.ViewFileUri) {
            val contentIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(CustomLauncherCodec.uriForFile(context, game.id, useContentUri = true), "application/octet-stream")
                if (spec.activityClass.isNotBlank()) setClassName(spec.packageName, spec.activityClass)
                else setPackage(spec.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val fallback = startSafely(context, contentIntent, spec.packageName)
            if (fallback is Result.Ok) return fallback
        }
        return res
    }

    private fun launchWithRetroArch(
        context: Context,
        game: Game,
        platform: Platform,
        coreOverride: String? = null,
    ): Result {
        val pkg = RETROARCH_PACKAGES.firstOrNull { isInstalled(context, it) }
            ?: return Result.Error("RetroArch is not installed")
        val core = coreOverride ?: resolveInstalledCore(pkg, platform)
            ?: return Result.Error("No RetroArch core mapped for ${platform.shortName}")

        val sd = Environment.getExternalStorageDirectory().path
        val external = context.getExternalFilesDir(null)?.path ?: "$sd/Android/data/$pkg/files"
        val cfgCandidates = listOf(
            "$sd/Android/data/$pkg/files/retroarch.cfg",
            "$sd/RetroArch/retroarch.cfg",
        )
        val cfgFile = cfgCandidates.firstOrNull { File(it).exists() } ?: cfgCandidates.first()

        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM", game.id)
            putExtra("LIBRETRO", "/data/data/$pkg/cores/${core}_libretro_android.so")
            putExtra("CONFIGFILE", cfgFile)
            putExtra("IME", Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
            putExtra("DATADIR", "/data/data/$pkg")
            putExtra("SDCARD", sd)
            putExtra("EXTERNAL", external)
            putExtra("QUITFOCUS", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(pkg)
        }
        return startSafely(context, intent, "RetroArch")
    }

    private fun launchWithStandalone(
        context: Context,
        game: Game,
        emulatorId: String,
    ): Result {
        val emu = EmulatorRegistry.byId(emulatorId)
            ?: return Result.Error("Unknown emulator: $emulatorId")
        val pkg = EmulatorRegistry.installedPackage(context, emu)
            ?: return Result.Error("${emu.displayName} is not installed")
        return launchStandalone(context, game, emu, pkg)
    }

    private fun launchStandalone(context: Context, game: Game, emu: EmulatorDef, pkg: String): Result {
        val intent = emu.intentBuilder(context, pkg, game)
        return startSafely(context, intent, emu.displayName)
    }

    private fun startSafely(context: Context, intent: Intent, targetName: String): Result = try {
        context.startActivity(intent)
        Result.Ok
    } catch (e: ActivityNotFoundException) {
        Result.Error("Cannot start $targetName: ${e.message}")
    } catch (e: SecurityException) {
        Result.Error("Cannot start $targetName: ${e.message}")
    } catch (e: Exception) {
        Result.Error("Cannot start $targetName: ${e.message}")
    }

    private fun resolveInstalledCore(pkg: String, platform: Platform): String? {
        val candidates = buildList {
            platform.core?.let { add(it) }
            CORE_CANDIDATES[platform.id]?.forEach { if (it !in this) add(it) }
        }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        val knownCores = detectConfiguredCores(pkg)
        if (knownCores.isNotEmpty()) {
            candidates.firstOrNull { c -> c.lowercase() in knownCores }?.let { return it }
        }
        return candidates.first()
    }

    private fun detectConfiguredCores(pkg: String): Set<String> {
        val sd = Environment.getExternalStorageDirectory()
        val found = HashSet<String>()
        val configDirs = listOf(
            File(sd, "RetroArch/config"),
            File(sd, "Android/data/$pkg/files/config"),
        )
        for (dir in configDirs) {
            dir.listFiles()?.forEach { sub ->
                if (sub.isDirectory) {
                    found += sub.name.lowercase().replace(" ", "").replace("-", "_")
                }
            }
        }
        val playlistDirs = listOf(
            File(sd, "RetroArch/playlists"),
            File(sd, "Android/data/$pkg/files/playlists"),
        )
        val coreRegex = Regex("""/([a-z0-9_]+)_libretro_android\.so""")
        for (dir in playlistDirs) {
            dir.walkTopDown().maxDepth(2).forEach { f ->
                if (f.isFile && f.extension.equals("lpl", ignoreCase = true) && f.length() < 2_000_000) {
                    runCatching {
                        f.forEachLine { line ->
                            coreRegex.find(line)?.let { m -> found += m.groupValues[1].lowercase() }
                        }
                    }
                }
            }
        }
        return found
    }

    private fun isInstalled(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        return pm.getLaunchIntentForPackage(pkg) != null ||
            runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }
}
