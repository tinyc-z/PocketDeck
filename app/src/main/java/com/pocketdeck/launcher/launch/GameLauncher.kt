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

    fun isRetroArchPackage(pkg: String): Boolean = pkg in RETROARCH_PACKAGES

    /** 平台推荐核心顺序（第一个为推荐核心）。 */
    fun platformCoreCandidates(platform: Platform): List<String> = buildList {
        platform.core?.let { add(it) }
        CORE_CANDIDATES[platform.id]?.forEach { c -> if (c !in this) add(c) }
    }

    /** 所有平台候选表里出现过的核心（用于过滤掉明显属于其他平台的已检测核心）。 */
    fun allKnownCores(): Set<String> = CORE_CANDIDATES.values.flatten().map { it.lowercase() }.toSet()

    /** 从 RetroArch 配置目录 / 播放列表中检测到用过的核心（无法读取 RetroArch 私有目录，仅作参考）。 */
    fun detectedRetroArchCores(context: Context): Set<String> {
        val raPkg = RETROARCH_PACKAGES.firstOrNull { isInstalled(context, it) } ?: return emptySet()
        return detectConfiguredCores(raPkg)
    }

    /** 自动模式下 RetroArch 会使用的核心。 */
    fun autoRetroArchCore(context: Context, platform: Platform): String? {
        val raPkg = RETROARCH_PACKAGES.firstOrNull { isInstalled(context, it) } ?: return null
        return resolveInstalledCore(raPkg, platform)
    }

    fun installedRetroArchCores(context: Context, platform: Platform?): List<String> {
        val raPkg = RETROARCH_PACKAGES.firstOrNull { isInstalled(context, it) } ?: return emptyList()
        val configured = detectConfiguredCores(raPkg) + RetroArchCoreCatalog.scanSafAndExternalCores(context)
        val candidates = buildList {
            platform?.core?.let { add(it) }
            platform?.id?.let { CORE_CANDIDATES[it]?.forEach { c -> if (c !in this) add(c) } }
            configured.sorted().forEach { c -> if (c !in this) add(c) }
        }
        return candidates
    }

    /** 3D/现代机种优先使用已安装的独立模拟器，未安装再回退 RetroArch；2D 复古机种优先 RetroArch。 */
    private val STANDALONE_PREFERRED_PLATFORMS = setOf(
        "psp", "psx", "ps2", "ps3", "psvita",
        "nds", "dsi", "3ds", "gamecube", "wii", "wiiu", "switch",
        "dreamcast", "naomi", "atomiswave", "saturn", "n64",
        "dos", "j2me", "openbor", "winlator",
    )

    @Volatile
    private var cachedCoresPkg: String? = null
    @Volatile
    private var cachedCoresSet: Set<String> = emptySet()
    @Volatile
    private var cachedCoresAtMs: Long = 0L
    private const val CORE_CACHE_TTL_MS = 30_000L

    /**
     * 获取某平台可用的内置启动器（已安装的），按平台推荐优先级排序，返回 (id, displayName) 列表。
     */
    fun availableLaunchers(context: Context, platformId: String): List<Pair<String, String>> {
        if (platformId == PlatformCatalog.ANDROID_ID) return emptyList()
        val standalones = EmulatorRegistry.availableForPlatform(context, platformId)
            .map { (emu, _) -> emu.id to emu.displayName }

        val raPkg = RETROARCH_PACKAGES.firstOrNull { isInstalled(context, it) }
        val raEntry = if (raPkg != null) {
            val platform = PlatformCatalog.forFolder(platformId)
            val core = resolveInstalledCore(raPkg, platform)
            if (core != null) RETROARCH_ID to "RetroArch · ${RetroArchCoreCatalog.displayName(core)}" else null
        } else null

        val result = mutableListOf<Pair<String, String>>()
        if (platformId in STANDALONE_PREFERRED_PLATFORMS) {
            result.addAll(standalones)
            if (raEntry != null) result.add(raEntry)
        } else {
            if (raEntry != null) result.add(raEntry)
            result.addAll(standalones)
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
            return if (core != null) "RetroArch · ${RetroArchCoreCatalog.displayName(core)}" else "RetroArch"
        }
        CustomLauncherCodec.decodeRaCore(launcherId)?.let { core ->
            return "RetroArch · ${RetroArchCoreCatalog.displayName(core)}"
        }
        CustomLauncherCodec.decodeEmuVariant(launcherId)?.let { (emuId, pkg) ->
            val emu = EmulatorRegistry.byId(emuId) ?: return emuId
            val label = appLabel(context, pkg)
            return if (label == null || label.equals(emu.displayName, ignoreCase = true)) emu.displayName
            else "${emu.displayName} · $label"
        }
        CustomLauncherCodec.decodeCustomApp(launcherId)?.let { spec ->
            return appLabel(context, spec.packageName) ?: spec.packageName
        }
        EmulatorRegistry.byId(launcherId)?.let { return it.displayName }
        return launcherId
    }

    fun appLabel(context: Context, pkg: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

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
            if (spec.mode != CustomLaunchMode.AppOnly && !File(game.id).exists()) {
                return Result.Error("ROM file not found: ${game.fileName}")
            }
            return launchWithCustomApp(context, game, spec)
        }

        if (!File(game.id).exists()) {
            return Result.Error("ROM file not found: ${game.fileName}")
        }

        // 2. 用户指定了 RetroArch 特定核心
        CustomLauncherCodec.decodeRaCore(preferredEmulatorId)?.let { core ->
            return launchWithRetroArch(context, game, resolvedPlatform, coreOverride = core)
        }

        // 3. 用户指定了内置独立模拟器（可带具体版本包名）
        CustomLauncherCodec.decodeEmuVariant(preferredEmulatorId)?.let { (emuId, pkg) ->
            val emu = EmulatorRegistry.byId(emuId) ?: return Result.Error("Unknown emulator: $emuId")
            val usePkg = pkg.takeIf { isInstalled(context, it) }
                ?: EmulatorRegistry.installedPackage(context, emu)
                ?: return Result.Error("${emu.displayName} is not installed")
            return launchStandalone(context, game, emu, usePkg)
        }
        if (preferredEmulatorId != null && preferredEmulatorId != RETROARCH_ID) {
            return launchWithStandalone(context, game, preferredEmulatorId)
        }

        // 4. 用户显式指定 RetroArch
        if (preferredEmulatorId == RETROARCH_ID) {
            return launchWithRetroArch(context, game, resolvedPlatform)
        }

        // 5. 自动模式（preferredEmulatorId == null）：按平台智能优先级（3D/现代机种优先独立模拟器，2D 复古优先 RetroArch）
        val preferStandalone = resolvedPlatform.id in STANDALONE_PREFERRED_PLATFORMS
        val standalone = EmulatorRegistry.availableForPlatform(context, resolvedPlatform.id).firstOrNull()
        var firstError: Result.Error? = null
        if (preferStandalone && standalone != null) {
            val res = launchStandalone(context, game, standalone.first, standalone.second)
            if (res is Result.Ok) return res
            if (res is Result.Error && firstError == null) firstError = res
        }

        val raInstalled = isRetroArchInstalled(context)
        if (raInstalled) {
            val raResult = launchWithRetroArch(context, game, resolvedPlatform)
            if (raResult is Result.Ok) return raResult
            if (raResult is Result.Error && firstError == null) firstError = raResult
        }

        if (!preferStandalone && standalone != null) {
            val res = launchStandalone(context, game, standalone.first, standalone.second)
            if (res is Result.Ok) return res
            if (res is Result.Error && firstError == null) firstError = res
        }

        return firstError ?: Result.Error("No emulator found for ${resolvedPlatform.fullName}")
    }

    private fun launchWithCustomApp(
        context: Context,
        game: Game,
        spec: CustomLauncherCodec.CustomAppSpec,
    ): Result {
        if (!isInstalled(context, spec.packageName)) {
            return Result.Error("App not installed: ${spec.packageName}")
        }
        val intent = runCatching {
            when (spec.mode) {
                CustomLaunchMode.ViewContentUri, CustomLaunchMode.ViewFileUri -> Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(CustomLauncherCodec.uriForFile(context, game.id, useContentUri = true), "application/octet-stream")
                    if (spec.activityClass.isNotBlank()) setClassName(spec.packageName, spec.activityClass)
                    else setPackage(spec.packageName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
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
        }.getOrElse { err ->
            return Result.Error("Cannot prepare launch for ${spec.packageName}: ${err.message}")
        }
        return startSafely(context, intent, spec.packageName)
    }

    private val SAFE_CORE_NAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")
    internal val PLAYLIST_CORE_REGEX = Regex("""/([a-z0-9_-]+)_libretro_android\.so""", RegexOption.IGNORE_CASE)

    /** 归一化并校验 RetroArch 核心名（剥离重复 `_libretro_android.so` 后缀，拦截路径穿越/非法字符）。 */
    internal fun normalizeRetroArchCore(raw: String): String? {
        var name = raw.trim()
        if (name.isEmpty()) return null
        while (name.endsWith("_libretro_android.so", ignoreCase = true)) {
            name = name.dropLast("_libretro_android.so".length)
        }
        while (name.endsWith("_libretro.so", ignoreCase = true)) {
            name = name.dropLast("_libretro.so".length)
        }
        if (name.isEmpty() || name.contains("..") || name.contains('/') || name.contains('\\')) return null
        if (!SAFE_CORE_NAME_REGEX.matches(name)) return null
        return name
    }

    /** 从 RetroArch `.lpl` 播放列表行中提取核心 ID（支持含连字符如 `mesen-s` 的核心）。 */
    internal fun extractCoreFromPlaylistLine(line: String): String? =
        PLAYLIST_CORE_REGEX.find(line)?.groupValues?.getOrNull(1)?.lowercase()

    private fun launchWithRetroArch(
        context: Context,
        game: Game,
        platform: Platform,
        coreOverride: String? = null,
    ): Result {
        val pkg = RETROARCH_PACKAGES.firstOrNull { isInstalled(context, it) }
            ?: return Result.Error("RetroArch is not installed")
        val rawCore = coreOverride ?: resolveInstalledCore(pkg, platform)
            ?: return Result.Error("No RetroArch core mapped for ${platform.shortName}")
        val core = normalizeRetroArchCore(rawCore)
            ?: return Result.Error("Invalid RetroArch core: $rawCore")

        val sd = Environment.getExternalStorageDirectory().path
        val external = "$sd/Android/data/$pkg/files"
        val cfgCandidates = listOf(
            "$sd/Android/data/$pkg/files/retroarch.cfg",
            "$sd/RetroArch/retroarch.cfg",
        )
        val cfgFile = cfgCandidates.firstOrNull { File(it).exists() } ?: cfgCandidates.first()
        val dataDir = runCatching {
            context.packageManager.getApplicationInfo(pkg, 0).dataDir
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "/data/data/$pkg"
        val coreFileName = "${core}_libretro_android.so"
        val customCorePath = RetroArchCoreCatalog.readLibretroDirectory(pkg)
            ?.let { File(it, coreFileName) }
            ?.takeIf { it.isFile }
            ?.absolutePath
        val libretroPath = customCorePath ?: "$dataDir/cores/$coreFileName"

        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM", game.id)
            putExtra("LIBRETRO", libretroPath)
            putExtra("CONFIGFILE", cfgFile)
            putExtra("IME", Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
            putExtra("DATADIR", dataDir)
            putExtra("SDCARD", sd)
            putExtra("EXTERNAL", external)
            putExtra("QUITFOCUS", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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
        val primaryRes = runCatching { emu.intentBuilder(context, pkg, game) }.fold(
            onSuccess = { startSafely(context, it, emu.displayName) },
            onFailure = { Result.Error("Cannot start ${emu.displayName}: ${it.message}") },
        )
        if (primaryRes is Result.Error && emu.fallbackIntentBuilder != null) {
            val fbRes = runCatching { emu.fallbackIntentBuilder.invoke(context, pkg, game) }.fold(
                onSuccess = { startSafely(context, it, emu.displayName) },
                onFailure = { Result.Error("Cannot start ${emu.displayName}: ${it.message}") },
            )
            if (fbRes is Result.Ok) return fbRes
        }
        return primaryRes
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
        val now = System.currentTimeMillis()
        if (cachedCoresPkg == pkg && (now - cachedCoresAtMs) < CORE_CACHE_TTL_MS) {
            return cachedCoresSet
        }
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
        for (dir in playlistDirs) {
            dir.walkTopDown().maxDepth(2).forEach { f ->
                if (f.isFile && f.extension.equals("lpl", ignoreCase = true) && f.length() < 2_000_000) {
                    runCatching {
                        f.forEachLine { line ->
                            extractCoreFromPlaylistLine(line)?.let { found += it }
                        }
                    }
                }
            }
        }
        cachedCoresPkg = pkg
        cachedCoresSet = found
        cachedCoresAtMs = now
        return found
    }

    private fun isInstalled(context: Context, pkg: String): Boolean =
        EmulatorRegistry.isPackageInstalled(context, pkg)
}
