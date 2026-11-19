package com.pocketdeck.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.launch.EmulatorRegistry
import com.pocketdeck.launcher.launch.GameLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 扫描结果 + 原生 Android 游戏 + 用户数据（改名、自定义封面、隐藏）合并后的游戏库。 */
class GameRepository(
    private val context: Context,
    private val userStore: UserStore,
    private val scope: CoroutineScope,
) {
    private val scanned = MutableStateFlow<Library?>(null)
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val user: StateFlow<UserData?> = userStore.data.stateIn(scope, SharingStarted.Eagerly, null)

    val library: StateFlow<Library?> = combine(scanned, userStore.data) { lib, u ->
        lib?.withAndroidGames(u)?.applyUser(u, includeHidden = false)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** 包含已隐藏游戏的完整游戏库（供游戏库管理器查看、搜索、恢复隐藏或管理文件）。 */
    val fullLibrary: StateFlow<Library?> = combine(scanned, userStore.data) { lib, u ->
        lib?.withAndroidGames(u)?.applyUser(u, includeHidden = true)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    @Volatile
    private var cachedInstalledApps: List<AndroidAppEntry> = emptyList()

    private data class AndroidAppEntry(
        val packageName: String,
        val label: String,
        val isAutoGame: Boolean,
        val apkSize: Long,
        val lastModifiedMs: Long,
    )

    fun refresh() {
        scope.launch {
            _scanning.value = true
            val roots = effectiveRoots(userStore.data.first())
            val result = withContext(Dispatchers.IO) {
                cachedInstalledApps = queryInstalledLaunchableApps()
                RomScanner.scanAll(roots)
            }
            scanned.value = result
            _scanning.value = false
        }
    }

    private fun queryInstalledLaunchableApps(): List<AndroidAppEntry> = runCatching {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val emuAndLauncherPkgs = buildSet {
            add("com.retroarch")
            add("com.retroarch.aarch64")
            add("com.retroarch.ra32")
            add("com.radikal.gamelauncher")
            EmulatorRegistry.ALL.forEach { addAll(it.packages) }
        }
        val resolved = pm.queryIntentActivities(intent, 0)
        val seen = HashSet<String>()
        resolved.mapNotNull { info ->
            val ai = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
            @Suppress("DEPRECATION")
            val isFlagGame = (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val isCatGame = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                ai.category == ApplicationInfo.CATEGORY_GAME
            val isAuto = (isFlagGame || isCatGame) && pkg !in emuAndLauncherPkgs && !pkg.startsWith("com.ayaneo.")
            val apkFile = File(ai.sourceDir)
            val apkSize = runCatching { apkFile.length() }.getOrDefault(0L)
            val apkMod = runCatching { apkFile.lastModified() }.getOrDefault(0L)
            AndroidAppEntry(pkg, label, isAuto, apkSize, apkMod)
        }
    }.getOrDefault(emptyList())

    private fun Library.withAndroidGames(u: UserData): Library {
        if (!u.showAndroidGames) return this
        val apps = cachedInstalledApps.ifEmpty {
            queryInstalledLaunchableApps().also { cachedInstalledApps = it }
        }
        val androidGames = apps.asSequence()
            .filter { app ->
                (app.isAutoGame || app.packageName in u.androidGamePackages) &&
                    app.packageName !in u.excludedAndroidPackages
            }
            .map { app ->
                Game(
                    id = "${GameLauncher.APK_PREFIX}${app.packageName}",
                    platformId = PlatformCatalog.ANDROID_ID,
                    baseName = app.label,
                    fileName = app.packageName,
                    title = app.label,
                    fileSize = app.apkSize,
                    lastModifiedMs = app.lastModifiedMs,
                )
            }
            .sortedBy { it.title.lowercase() }
            .toList()

        if (androidGames.isEmpty()) return this

        val androidPlat = PlatformCatalog.forFolder(PlatformCatalog.ANDROID_ID)
        val newGames = games.toMutableMap().apply {
            val existing = this[PlatformCatalog.ANDROID_ID].orEmpty()
            this[PlatformCatalog.ANDROID_ID] = (existing + androidGames).distinctBy { it.id }
                .sortedBy { it.title.lowercase() }
        }
        val newPlatforms = (platforms + androidPlat)
            .distinctBy { it.id }
            .sortedWith(compareBy({ PlatformCatalog.sortKey(it.id) }, { it.id }))
        return copy(platforms = newPlatforms, games = newGames)
    }

    /** 用户配置了目录就用启用的那些，否则自动检测一个。 */
    private fun effectiveRoots(u: UserData): List<File> =
        if (u.romDirs.isNotEmpty()) u.romDirs.filter { it.enabled }.map { File(it.path) }
        else listOf(resolveRoot(u.romRoot))

    suspend fun configuredRoots(): List<File> = withContext(Dispatchers.IO) {
        effectiveRoots(userStore.data.first()).filter { it.exists() }
    }

    /** 自动检测到的根目录（用于首次添加目录时把它保留下来）。 */
    fun autoRoot(u: UserData): String = resolveRoot(u.romRoot).path

    private fun resolveRoot(custom: String?): File {
        if (custom != null) return File(custom)
        val sd = Environment.getExternalStorageDirectory()
        return RomScanner.defaultRoots(sd).firstOrNull { it.isDirectory } ?: RomScanner.defaultRoots(sd).first()
    }

    private fun Library.applyUser(u: UserData, includeHidden: Boolean): Library {
        val mapped = if (u.renames.isEmpty() && u.covers.isEmpty() && (includeHidden || u.hidden.isEmpty())) {
            games
        } else {
            games.mapValues { (_, list) ->
                list.asSequence()
                    .filter { includeHidden || it.id !in u.hidden }
                    .map { g ->
                        val name = u.renames[g.id]
                        val cover = u.covers[g.id]
                        if (name == null && cover == null) g else g.copy(title = name ?: g.title, customCover = cover)
                    }
                    .sortedBy { it.title.lowercase() }
                    .toList()
            }.filterValues { it.isNotEmpty() }
        }
        val adjustedPlatforms = platforms
            .filter { it.id in mapped }
            .map { plat ->
                val aspect = u.platformAspects[plat.id]
                if (aspect != null) plat.copy(coverAspect = aspect) else plat
            }
        return copy(platforms = adjustedPlatforms, games = mapped)
    }

    suspend fun toggleFavorite(id: String) = userStore.toggleFavorite(id)
    suspend fun addRecent(id: String) = userStore.addRecent(id)
    suspend fun saveGameEdits(id: String, name: String?, cover: String?) = userStore.saveGameEdits(id, name, cover)
    suspend fun hide(id: String) = userStore.hide(id)
    suspend fun unhide(id: String) = userStore.unhide(id)
    suspend fun updatePrefs(transform: (UserData) -> UserData) = userStore.update(transform)

    /** 磁盘重命名文件或文件夹（支持改扩展名），同时迁移关联游戏数据并重新扫描。 */
    suspend fun renameFile(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = newName.trim()
            require(clean.isNotEmpty() && clean != "." && clean != ".." && '/' !in clean && '\\' !in clean && '\u0000' !in clean) {
                "Invalid file name"
            }
            require(file.exists()) { "Source does not exist" }
            val parent = file.parentFile ?: error("No parent directory")
            val target = File(parent, clean)
            if (target.path == file.path) return@runCatching file
            require(!target.exists()) { "Target already exists" }
            val wasDir = file.isDirectory
            check(file.renameTo(target)) { "Failed to rename" }
            if (wasDir) {
                userStore.migratePathPrefix(file.path, target.path)
            } else {
                userStore.migrateGameId(file.path, target.path)
            }
            target
        }.also { if (it.isSuccess) refresh() }
    }

    /** 移动文件或文件夹到目标目录（防自我嵌套，支持同盘 rename 与跨存储递归 copy+delete），同步迁移数据并刷新。 */
    suspend fun moveFile(file: File, targetDir: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            moveEntryInternal(file, targetDir)
        }.also { if (it.isSuccess) refresh() }
    }

    /** 批量移动多个文件/文件夹到目标目录。 */
    suspend fun moveEntries(entries: List<File>, targetDir: File): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var moved = 0
            for (entry in entries) {
                moveEntryInternal(entry, targetDir)
                moved++
            }
            moved
        }.also { if (it.isSuccess) refresh() }
    }

    private suspend fun moveEntryInternal(entry: File, targetDir: File): File {
        require(entry.exists()) { "Source does not exist" }
        if (!targetDir.exists()) check(targetDir.mkdirs()) { "Cannot create target directory" }
        require(targetDir.isDirectory) { "Target is not a directory" }
        val srcCanon = entry.canonicalPath
        val dstDirCanon = targetDir.canonicalPath
        if (entry.isDirectory) {
            require(dstDirCanon != srcCanon && !dstDirCanon.startsWith("$srcCanon${File.separator}")) {
                "Cannot move folder into itself"
            }
        }
        val target = File(targetDir, entry.name)
        if (target.canonicalPath == srcCanon) return entry
        require(!target.exists()) { "Target already exists: ${entry.name}" }

        val wasDir = entry.isDirectory
        if (!entry.renameTo(target)) {
            if (wasDir) {
                copyDirRecursive(entry, target)
                check(entry.deleteRecursively()) { "Failed to remove source folder after copy" }
            } else {
                entry.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                check(target.isFile && target.length() == entry.length()) { "Copy verification failed" }
                check(entry.delete()) { "Failed to remove source after copy" }
            }
        }
        if (wasDir) {
            userStore.migratePathPrefix(entry.path, target.path)
        } else {
            userStore.migrateGameId(entry.path, target.path)
        }
        return target
    }

    private fun copyDirRecursive(src: File, dst: File) {
        if (!dst.exists()) check(dst.mkdirs()) { "Cannot create ${dst.path}" }
        src.listFiles().orEmpty().forEach { child ->
            val childDst = File(dst, child.name)
            if (child.isDirectory) {
                copyDirRecursive(child, childDst)
            } else {
                child.inputStream().use { input ->
                    childDst.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    /** 复制文件或文件夹到目标目录（支持跨存储/U盘/TF卡与同名自动重命名），若落入 ROM 目录则刷新游戏库。 */
    suspend fun copyFile(file: File, targetDir: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            copyEntryInternal(file, targetDir)
        }.also { if (it.isSuccess) refreshIfInRomRoots(targetDir) }
    }

    /** 批量复制多个文件/文件夹到目标目录。 */
    suspend fun copyEntries(entries: List<File>, targetDir: File): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var copied = 0
            for (entry in entries) {
                copyEntryInternal(entry, targetDir)
                copied++
            }
            copied
        }.also { if (it.isSuccess) refreshIfInRomRoots(targetDir) }
    }

    private fun copyEntryInternal(entry: File, targetDir: File): File {
        require(entry.exists()) { "Source does not exist" }
        if (!targetDir.exists()) check(targetDir.mkdirs()) { "Cannot create target directory" }
        require(targetDir.isDirectory) { "Target is not a directory" }
        val srcCanon = entry.canonicalPath
        val dstDirCanon = targetDir.canonicalPath
        if (entry.isDirectory) {
            require(dstDirCanon != srcCanon && !dstDirCanon.startsWith("$srcCanon${File.separator}")) {
                "Cannot copy folder into itself"
            }
        }
        val target = uniqueCopyTarget(targetDir, entry.name)
        if (entry.isDirectory) {
            copyDirRecursive(entry, target)
        } else {
            entry.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            check(target.isFile && target.length() == entry.length()) { "Copy verification failed" }
        }
        return target
    }

    private fun uniqueCopyTarget(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem ($n)$ext")
            n++
        }
        return candidate
    }

    private suspend fun refreshIfInRomRoots(targetDir: File) {
        val dstCanon = runCatching { targetDir.canonicalPath }.getOrDefault(targetDir.path)
        val inRom = configuredRoots().any { root ->
            val rc = runCatching { root.canonicalPath }.getOrDefault(root.path)
            dstCanon == rc || dstCanon.startsWith("$rc${File.separator}")
        }
        if (inRom) refresh()
    }

    /** 永久删除磁盘上的文件或文件夹（递归删除，禁止删除已配置的根目录），清理关联数据并刷新。 */
    suspend fun deleteFile(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            deleteEntryInternal(file)
        }.also { if (it.isSuccess) refresh() }
    }

    /** 批量永久删除多个文件/文件夹。 */
    suspend fun deleteEntries(entries: List<File>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var count = 0
            for (entry in entries) {
                deleteEntryInternal(entry)
                count++
            }
            count
        }.also { if (it.isSuccess) refresh() }
    }

    private suspend fun deleteEntryInternal(entry: File) {
        require(entry.exists()) { "File does not exist" }
        val roots = configuredRoots().mapNotNull { runCatching { it.canonicalPath }.getOrNull() }.toSet()
        val canon = runCatching { entry.canonicalPath }.getOrDefault(entry.path)
        require(canon !in roots) { "Cannot delete ROM root directory" }

        val wasDir = entry.isDirectory
        if (wasDir) {
            check(entry.deleteRecursively()) { "Failed to delete folder" }
            userStore.removePathPrefix(entry.path)
        } else {
            check(entry.delete()) { "Failed to delete file" }
            userStore.removeGameData(entry.path)
        }
    }
}
