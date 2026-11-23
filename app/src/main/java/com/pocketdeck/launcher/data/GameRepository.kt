package com.pocketdeck.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.launch.EmulatorRegistry
import com.pocketdeck.launcher.launch.GameLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class FileTaskKind { Compress, Extract, Copy, Move, Delete, Upload }

enum class FileTaskSource { Local, Web }

/** 正在执行的文件系统耗时任务状态，供手机端与 Web 端目录界面实时展示。 */
data class FileTask(
    val id: String,
    val kind: FileTaskKind,
    val source: FileTaskSource,
    val title: String,
    val detail: String = "",
    val clientIp: String = "",
    val bytesDone: Long = 0L,
    val totalBytes: Long = 0L,
    val startedAtMs: Long = System.currentTimeMillis(),
    val cancelling: Boolean = false,
    val cancellable: Boolean = false,
    val cancelFlag: AtomicBoolean = AtomicBoolean(false),
    val onCancel: () -> Unit = {},
)

/** 最近完成或失败的文件任务事件，用于异步任务结束后向前端推送准确结果提示。 */
data class FileTaskEvent(
    val seq: Long,
    val taskId: String,
    val kind: FileTaskKind,
    val source: FileTaskSource,
    val title: String,
    val success: Boolean,
    val cancelled: Boolean = false,
    val count: Int = 0,
    val createdName: String? = null,
    val error: String? = null,
)

/** 扫描结果 + 原生 Android 游戏 + 用户数据（改名、自定义封面、隐藏）合并后的游戏库。 */
class GameRepository(
    private val context: Context,
    private val userStore: UserStore,
    private val scope: CoroutineScope,
    private val scrapeDb: ScrapeDatabase? = null,
) {
    private val scanned = MutableStateFlow<Library?>(null)
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _filesRevision = MutableStateFlow(0L)
    /** 磁盘文件发生变更时递增，供文件目录浏览页实时同步刷新。 */
    val filesRevision: StateFlow<Long> = _filesRevision.asStateFlow()

    private val _fileTasks = MutableStateFlow<List<FileTask>>(emptyList())
    val fileTasks: StateFlow<List<FileTask>> = _fileTasks.asStateFlow()

    private val _lastFileTaskEvent = MutableStateFlow<FileTaskEvent?>(null)
    val lastFileTaskEvent: StateFlow<FileTaskEvent?> = _lastFileTaskEvent.asStateFlow()
    private val taskEventSeq = AtomicLong(0L)

    fun notifyFilesChanged() {
        _filesRevision.update { it + 1L }
    }

    /** 仅删除位于应用私有 `custom_covers/` 沙箱内的旧自定义封面文件，防止路径穿越或误删用户文件。 */
    fun deleteOwnedCustomCover(path: String) {
        val coversDir = File(context.applicationContext.filesDir, "custom_covers")
        val target = File(path)
        if (com.pocketdeck.launcher.lan.WebFileSecurity.isInsideDir(target, coversDir)) {
            runCatching { target.delete() }
        }
    }

    fun launchFileTask(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(Dispatchers.IO, block = block)
    }

    fun beginFileTask(
        kind: FileTaskKind,
        source: FileTaskSource,
        title: String,
        detail: String = "",
        clientIp: String = "",
        totalBytes: Long = 0L,
        cancellable: Boolean = false,
        onCancel: () -> Unit = {},
    ): FileTask {
        val task = FileTask(
            id = UUID.randomUUID().toString(),
            kind = kind,
            source = source,
            title = title,
            detail = detail,
            clientIp = clientIp,
            totalBytes = totalBytes,
            cancellable = cancellable,
            onCancel = onCancel,
        )
        _fileTasks.update { it + task }
        return task
    }

    fun updateFileTask(
        id: String,
        detail: String? = null,
        bytesDone: Long? = null,
        totalBytes: Long? = null,
    ) {
        _fileTasks.update { list ->
            list.map { t ->
                if (t.id == id) {
                    t.copy(
                        detail = detail ?: t.detail,
                        bytesDone = bytesDone ?: t.bytesDone,
                        totalBytes = totalBytes ?: t.totalBytes,
                    )
                } else {
                    t
                }
            }
        }
    }

    fun endFileTask(
        id: String,
        success: Boolean? = null,
        cancelled: Boolean = false,
        count: Int = 0,
        createdName: String? = null,
        error: String? = null,
    ) {
        var ended: FileTask? = null
        _fileTasks.update { list ->
            ended = list.firstOrNull { it.id == id }
            list.filterNot { it.id == id }
        }
        val task = ended
        if (task != null && success != null) {
            _lastFileTaskEvent.value = FileTaskEvent(
                seq = taskEventSeq.incrementAndGet(),
                taskId = task.id,
                kind = task.kind,
                source = task.source,
                title = task.title,
                success = success,
                cancelled = cancelled,
                count = count,
                createdName = createdName,
                error = error,
            )
        }
    }

    fun cancelFileTask(id: String): Boolean {
        var found: FileTask? = null
        _fileTasks.update { list ->
            list.map { t ->
                if (t.id == id && t.cancellable) {
                    found = t
                    t.cancelFlag.set(true)
                    t.copy(cancelling = true)
                } else {
                    t
                }
            }
        }
        val target = found
        if (target != null) {
            runCatching { target.onCancel() }
        }
        return target != null
    }

    val user: StateFlow<UserData?> = userStore.data.stateIn(scope, SharingStarted.Eagerly, null)

    /** 仅关注影响游戏库构建的字段，避免切换主题/时钟/按键音等无关设置时重算全部游戏。 */
    private data class LibraryUserProjection(
        val renames: Map<String, String>,
        val covers: Map<String, String>,
        val platformAspects: Map<String, Float>,
        val showAndroidGames: Boolean,
        val androidGamePackages: Set<String>,
        val excludedAndroidPackages: Set<String>,
        val user: UserData,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is LibraryUserProjection) return false
            return renames == other.renames &&
                covers == other.covers &&
                platformAspects == other.platformAspects &&
                showAndroidGames == other.showAndroidGames &&
                androidGamePackages == other.androidGamePackages &&
                excludedAndroidPackages == other.excludedAndroidPackages
        }
        override fun hashCode(): Int {
            var r = renames.hashCode()
            r = 31 * r + covers.hashCode()
            r = 31 * r + platformAspects.hashCode()
            r = 31 * r + showAndroidGames.hashCode()
            r = 31 * r + androidGamePackages.hashCode()
            r = 31 * r + excludedAndroidPackages.hashCode()
            return r
        }
    }

    private val installedAppsFlow = MutableStateFlow<List<CachedAndroidApp>?>(null)

    /** 包含已隐藏游戏的完整游戏库（供游戏库管理器查看、搜索、恢复隐藏或管理文件）。 */
    val fullLibrary: StateFlow<Library?> = combine(
        scanned,
        installedAppsFlow,
        userStore.data.map { u ->
            LibraryUserProjection(
                renames = u.renames,
                covers = u.covers,
                platformAspects = u.platformAspects,
                showAndroidGames = u.showAndroidGames,
                androidGamePackages = u.androidGamePackages,
                excludedAndroidPackages = u.excludedAndroidPackages,
                user = u,
            )
        }.distinctUntilChanged(),
    ) { lib, apps, proj ->
        lib?.withAndroidGames(proj.user, apps)?.applyUser(proj.user, includeHidden = true)
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    /** 过滤已隐藏游戏的主页游戏库（直接从 fullLibrary 派生，保留 localMetadata 与 dirSnapshots）。 */
    val library: StateFlow<Library?> = combine(
        fullLibrary,
        userStore.data.map { it.hidden }.distinctUntilChanged(),
    ) { full, hidden ->
        if (full == null) null
        else if (hidden.isEmpty()) full
        else {
            val filteredGames = HashMap<String, List<Game>>(full.games.size * 2)
            val filteredPlatforms = ArrayList<Platform>(full.platforms.size)
            for (p in full.platforms) {
                val list = full.games[p.id].orEmpty().filter { it.id !in hidden }
                if (list.isNotEmpty()) {
                    filteredGames[p.id] = list
                    filteredPlatforms += p
                }
            }
            full.copy(platforms = filteredPlatforms, games = filteredGames)
        }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    private val indexCache = LibraryIndexCache(context)
    private val refreshGen = java.util.concurrent.atomic.AtomicLong(0L)
    @Volatile
    private var refreshJob: Job? = null

    /** 文件改名/移动/删除后的内存缓存联动回调（在 AppContainer 挂载，同步通知封面/元数据/抓取缓存）。 */
    @Volatile var onGameMigrated: ((oldId: String, newId: String) -> Unit)? = null
    @Volatile var onGameRemoved: ((gameId: String) -> Unit)? = null
    @Volatile var onPathPrefixMigrated: ((oldDir: String, newDir: String) -> Unit)? = null
    @Volatile var onPathPrefixRemoved: ((dirPath: String) -> Unit)? = null

    private data class RefreshScanOutcome(
        val library: Library,
        val apps: List<CachedAndroidApp>,
        val libChanged: Boolean,
        val appsChanged: Boolean,
    )

    /** 推断用户首页第一个可见分类涉及的平台 ID，用于无缓存冷启动时优先扫描首屏平台。 */
    private fun firstVisiblePlatformIds(user: UserData): Set<String> {
        val orderedCats = if (user.tabOrder.isNotEmpty()) {
            val orderMap = user.tabOrder.withIndex().associate { it.value to it.index }
            user.categories.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
        } else {
            user.categories
        }
        val firstVisible = orderedCats.firstOrNull { it.id !in user.hiddenTabs && it.sources.isNotEmpty() }
        return firstVisible?.sources?.toSet() ?: setOf("nes", "snes", "gb", "gba")
    }

    fun refresh() {
        val gen = refreshGen.incrementAndGet()
        refreshJob?.cancel()
        refreshJob = scope.launch {
            _scanning.value = true
            try {
                // 并行启动二进制索引预读与 UserStore 首读，缩短冷启等待
                val warmCacheDeferred = if (scanned.value == null) {
                    async(Dispatchers.IO) { indexCache.loadUncheckedSnapshot() }
                } else {
                    null
                }
                val userData = userStore.data.first()
                val roots = effectiveRoots(userData)
                val rootsKey = roots.joinToString(";") { it.path }

                // 阶段 1：冷启动命中本地二进制索引缓存 → < 30ms 秒开首屏（同步还原 Android 游戏列表，零 PackageManager IPC）
                val hadWarmCache = if (scanned.value == null) {
                    val cachedSnap = warmCacheDeferred?.await()?.takeIf { it.library.root == rootsKey }
                    if (cachedSnap != null && refreshGen.get() == gen) {
                        if (cachedSnap.installedApps != null) {
                            installedAppsFlow.value = cachedSnap.installedApps
                        }
                        scanned.value = cachedSnap.library
                        true
                    } else {
                        false
                    }
                } else {
                    true
                }

                // 阶段 2：后台并行核对真实磁盘与已安装应用（仅查 1 次 PackageManager，所有深比较均在 IO 线程完成）
                val priorityPlats = firstVisiblePlatformIds(userData)
                val prevScanned = scanned.value
                val prevApps = installedAppsFlow.value
                val outcome = withContext(Dispatchers.IO) {
                    val appsDeferred = async { queryInstalledLaunchableApps() }
                    val lib = RomScanner.scanAllProgressive(
                        roots = roots,
                        priorityPlatformIds = priorityPlats,
                        previousLibrary = prevScanned,
                        onProgress = if (!hadWarmCache) { partial ->
                            if (refreshGen.get() == gen) {
                                scanned.value = partial
                            }
                        } else null,
                    )
                    val apps = appsDeferred.await()
                    val libChanged = prevScanned == null || lib != prevScanned
                    val appsChanged = prevApps == null || apps != prevApps
                    // 仅当本轮扫描仍为最新代际时才写盘，防止过期慢扫描覆写新目录/删除结果
                    if (refreshGen.get() == gen && (!hadWarmCache || libChanged || appsChanged)) {
                        indexCache.save(rootsKey, lib, apps)
                    }
                    RefreshScanOutcome(lib, apps, libChanged, appsChanged)
                }
                if (refreshGen.get() == gen) {
                    if (outcome.appsChanged) installedAppsFlow.value = outcome.apps
                    if (outcome.libChanged) scanned.value = outcome.library
                }
            } finally {
                if (refreshGen.get() == gen) {
                    _scanning.value = false
                }
            }
        }
    }

    private fun queryInstalledLaunchableApps(): List<CachedAndroidApp> = runCatching {
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
            val label = info.loadLabel(pm).toString().trim().ifEmpty { pkg }
            @Suppress("DEPRECATION")
            val isFlagGame = (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val isCatGame = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                ai.category == ApplicationInfo.CATEGORY_GAME
            val isAuto = (isFlagGame || isCatGame) && pkg !in emuAndLauncherPkgs && !pkg.startsWith("com.ayaneo.")
            val apkFile = ai.sourceDir?.let(::File)
            val apkSize = runCatching { apkFile?.length() ?: 0L }.getOrDefault(0L)
            val apkMod = runCatching { apkFile?.lastModified() ?: 0L }.getOrDefault(0L)
            CachedAndroidApp(pkg, label, isAuto, apkSize, apkMod)
        }.sortedBy { it.packageName }
    }.getOrDefault(emptyList())

    private fun Library.withAndroidGames(u: UserData, apps: List<CachedAndroidApp>?): Library {
        if (!u.showAndroidGames || apps.isNullOrEmpty()) return this
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
                var anyRenamed = false
                val out = ArrayList<Game>(list.size)
                for (g in list) {
                    if (!includeHidden && g.id in u.hidden) continue
                    val name = u.renames[g.id]
                    val cover = u.covers[g.id]
                    if (name != null) anyRenamed = true
                    out += if (name == null && cover == null) g else g.copy(title = name ?: g.title, customCover = cover)
                }
                if (anyRenamed) out.sortBy { it.title.lowercase() }
                out
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
    suspend fun addRecent(id: String) = userStore.recordGameLaunch(id)

    suspend fun addPlayDuration(id: String, deltaMs: Long) = userStore.addPlayDuration(id, deltaMs)
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
            val caseOnlyRename = file.name.equals(clean, ignoreCase = true)
            if (!caseOnlyRename) {
                require(!target.exists()) { "Target already exists" }
            }
            val wasDir = file.isDirectory
            if (caseOnlyRename) {
                val tmp = File(parent, ".rename_${System.currentTimeMillis()}_${clean}")
                check(file.renameTo(tmp) && tmp.renameTo(target)) { "Failed to rename" }
            } else {
                check(file.renameTo(target)) { "Failed to rename" }
            }
            if (wasDir) {
                userStore.migratePathPrefix(file.path, target.path)
                scrapeDb?.migratePathPrefix(file.path, target.path)
                onPathPrefixMigrated?.invoke(file.path, target.path)
            } else {
                userStore.migrateGameId(file.path, target.path)
                scrapeDb?.migrateGameId(file.path, target.path)
                onGameMigrated?.invoke(file.path, target.path)
            }
            target
        }.also {
            if (it.isSuccess) {
                notifyFilesChanged()
                refresh()
            }
        }
    }

    /** 移动文件或文件夹到目标目录（防自我嵌套，支持同盘 rename 与跨存储递归 copy+delete），同步迁移数据并刷新。 */
    suspend fun moveFile(
        file: File,
        targetDir: File,
        source: FileTaskSource = FileTaskSource.Local,
        clientIp: String = "",
    ): Result<File> = withContext(Dispatchers.IO) {
        val total = measureEntrySize(file)
        val task = beginFileTask(
            kind = FileTaskKind.Move,
            source = source,
            title = file.name,
            detail = targetDir.name,
            clientIp = clientIp,
            totalBytes = total,
            cancellable = true,
        )
        var bytesDone = 0L
        var lastReportMs = 0L
        runCatching {
            moveEntryInternal(file, targetDir, task.cancelFlag) { name, delta ->
                bytesDone += delta
                val now = System.currentTimeMillis()
                if (now - lastReportMs >= 150L) {
                    lastReportMs = now
                    updateFileTask(task.id, detail = name, bytesDone = bytesDone)
                }
            }
        }.also { res ->
            if (res.isSuccess) {
                notifyFilesChanged()
                refresh()
            }
            endFileTask(
                id = task.id,
                success = res.isSuccess,
                cancelled = task.cancelFlag.get(),
                count = if (res.isSuccess) 1 else 0,
                error = res.exceptionOrNull()?.message,
            )
        }
    }

    /** 批量移动多个文件/文件夹到目标目录。 */
    suspend fun moveEntries(
        entries: List<File>,
        targetDir: File,
        source: FileTaskSource = FileTaskSource.Local,
        clientIp: String = "",
    ): Result<Int> = withContext(Dispatchers.IO) {
        val total = entries.sumOf(::measureEntrySize)
        val title = if (entries.size == 1) entries.first().name else "${entries.firstOrNull()?.name ?: ""} (${entries.size})"
        val task = beginFileTask(
            kind = FileTaskKind.Move,
            source = source,
            title = title,
            detail = targetDir.name,
            clientIp = clientIp,
            totalBytes = total,
            cancellable = true,
        )
        var bytesDone = 0L
        var lastReportMs = 0L
        runCatching {
            var moved = 0
            for (entry in entries) {
                if (task.cancelFlag.get()) error("Cancelled")
                updateFileTask(task.id, detail = entry.name, bytesDone = bytesDone)
                moveEntryInternal(entry, targetDir, task.cancelFlag) { name, delta ->
                    bytesDone += delta
                    val now = System.currentTimeMillis()
                    if (now - lastReportMs >= 150L) {
                        lastReportMs = now
                        updateFileTask(task.id, detail = name, bytesDone = bytesDone)
                    }
                }
                moved++
            }
            moved
        }.also { res ->
            if (res.isSuccess || bytesDone > 0L) notifyFilesChanged()
            if (res.isSuccess) refresh()
            endFileTask(
                id = task.id,
                success = res.isSuccess,
                cancelled = task.cancelFlag.get(),
                count = res.getOrDefault(0),
                error = res.exceptionOrNull()?.message,
            )
        }
    }

    private suspend fun moveEntryInternal(
        entry: File,
        targetDir: File,
        cancelFlag: AtomicBoolean = AtomicBoolean(false),
        onChunk: (entryName: String, deltaBytes: Long) -> Unit = { _, _ -> },
    ): File {
        if (cancelFlag.get()) error("Cancelled")
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
                try {
                    copyDirRecursive(entry, target, cancelFlag, onChunk)
                } catch (t: Throwable) {
                    runCatching { target.deleteRecursively() }
                    throw t
                }
                check(entry.deleteRecursively()) { "Failed to remove source folder after copy" }
            } else {
                try {
                    copyFileChunked(entry, target, cancelFlag) { delta -> onChunk(entry.name, delta) }
                    check(target.isFile && target.length() == entry.length()) { "Copy verification failed" }
                } catch (t: Throwable) {
                    runCatching { target.delete() }
                    throw t
                }
                check(entry.delete()) { "Failed to remove source after copy" }
            }
        }
        if (wasDir) {
            userStore.migratePathPrefix(entry.path, target.path)
            scrapeDb?.migratePathPrefix(entry.path, target.path)
            onPathPrefixMigrated?.invoke(entry.path, target.path)
        } else {
            userStore.migrateGameId(entry.path, target.path)
            scrapeDb?.migrateGameId(entry.path, target.path)
            onGameMigrated?.invoke(entry.path, target.path)
        }
        return target
    }

    private fun copyFileChunked(
        src: File,
        dst: File,
        cancelFlag: AtomicBoolean,
        onDelta: (Long) -> Unit,
    ) {
        val buf = ByteArray(64 * 1024)
        try {
            src.inputStream().use { input ->
                dst.outputStream().use { output ->
                    while (true) {
                        if (cancelFlag.get()) error("Cancelled")
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        onDelta(n.toLong())
                    }
                }
            }
        } catch (t: Throwable) {
            runCatching { dst.delete() }
            throw t
        }
    }

    private fun isSymlink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    private fun copyDirRecursive(
        src: File,
        dst: File,
        cancelFlag: AtomicBoolean,
        onChunk: (entryName: String, deltaBytes: Long) -> Unit,
        visitedDirs: MutableSet<String> = HashSet(),
        depth: Int = 0,
    ) {
        if (cancelFlag.get()) error("Cancelled")
        if (depth > 32 || isSymlink(src)) return
        val srcCanon = runCatching { src.canonicalPath }.getOrNull() ?: return
        if (!visitedDirs.add(srcCanon)) return
        if (!dst.exists()) check(dst.mkdirs()) { "Cannot create ${dst.path}" }
        src.listFiles().orEmpty().forEach { child ->
            if (cancelFlag.get()) error("Cancelled")
            if (isSymlink(child)) return@forEach
            val childDst = File(dst, child.name)
            if (child.isDirectory) {
                copyDirRecursive(child, childDst, cancelFlag, onChunk, visitedDirs, depth + 1)
            } else {
                copyFileChunked(child, childDst, cancelFlag) { delta -> onChunk(child.name, delta) }
            }
        }
    }

    private fun measureEntrySize(
        entry: File,
        visitedDirs: MutableSet<String> = HashSet(),
        depth: Int = 0,
    ): Long {
        if (depth > 32 || isSymlink(entry)) return 0L
        if (entry.isFile) return entry.length()
        if (!entry.isDirectory) return 0L
        val canon = runCatching { entry.canonicalPath }.getOrNull() ?: return 0L
        if (!visitedDirs.add(canon)) return 0L
        var sum = 0L
        entry.listFiles()?.forEach { sum += measureEntrySize(it, visitedDirs, depth + 1) }
        return sum
    }

    /** 复制文件或文件夹到目标目录（支持跨存储/U盘/TF卡与同名自动重命名），若落入 ROM 目录则刷新游戏库。 */
    suspend fun copyFile(
        file: File,
        targetDir: File,
        source: FileTaskSource = FileTaskSource.Local,
        clientIp: String = "",
    ): Result<File> = withContext(Dispatchers.IO) {
        val total = measureEntrySize(file)
        val task = beginFileTask(
            kind = FileTaskKind.Copy,
            source = source,
            title = file.name,
            detail = targetDir.name,
            clientIp = clientIp,
            totalBytes = total,
            cancellable = true,
        )
        var bytesDone = 0L
        var lastReportMs = 0L
        runCatching {
            copyEntryInternal(file, targetDir, task.cancelFlag) { name, delta ->
                bytesDone += delta
                val now = System.currentTimeMillis()
                if (now - lastReportMs >= 150L) {
                    lastReportMs = now
                    updateFileTask(task.id, detail = name, bytesDone = bytesDone)
                }
            }
        }.also { res ->
            if (res.isSuccess) {
                notifyFilesChanged()
                refreshIfInRomRoots(targetDir)
            }
            endFileTask(
                id = task.id,
                success = res.isSuccess,
                cancelled = task.cancelFlag.get(),
                count = if (res.isSuccess) 1 else 0,
                error = res.exceptionOrNull()?.message,
            )
        }
    }

    /** 批量复制多个文件/文件夹到目标目录。 */
    suspend fun copyEntries(
        entries: List<File>,
        targetDir: File,
        source: FileTaskSource = FileTaskSource.Local,
        clientIp: String = "",
    ): Result<Int> = withContext(Dispatchers.IO) {
        val total = entries.sumOf(::measureEntrySize)
        val title = if (entries.size == 1) entries.first().name else "${entries.firstOrNull()?.name ?: ""} (${entries.size})"
        val task = beginFileTask(
            kind = FileTaskKind.Copy,
            source = source,
            title = title,
            detail = targetDir.name,
            clientIp = clientIp,
            totalBytes = total,
            cancellable = true,
        )
        var bytesDone = 0L
        var lastReportMs = 0L
        runCatching {
            var copied = 0
            for (entry in entries) {
                if (task.cancelFlag.get()) error("Cancelled")
                updateFileTask(task.id, detail = entry.name, bytesDone = bytesDone)
                copyEntryInternal(entry, targetDir, task.cancelFlag) { name, delta ->
                    bytesDone += delta
                    val now = System.currentTimeMillis()
                    if (now - lastReportMs >= 150L) {
                        lastReportMs = now
                        updateFileTask(task.id, detail = name, bytesDone = bytesDone)
                    }
                }
                copied++
            }
            copied
        }.also { res ->
            if (res.isSuccess || bytesDone > 0L) notifyFilesChanged()
            if (res.isSuccess) refreshIfInRomRoots(targetDir)
            endFileTask(
                id = task.id,
                success = res.isSuccess,
                cancelled = task.cancelFlag.get(),
                count = res.getOrDefault(0),
                error = res.exceptionOrNull()?.message,
            )
        }
    }

    private fun copyEntryInternal(
        entry: File,
        targetDir: File,
        cancelFlag: AtomicBoolean = AtomicBoolean(false),
        onChunk: (entryName: String, deltaBytes: Long) -> Unit = { _, _ -> },
    ): File {
        if (cancelFlag.get()) error("Cancelled")
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
            try {
                copyDirRecursive(entry, target, cancelFlag, onChunk)
            } catch (t: Throwable) {
                if (cancelFlag.get()) runCatching { target.deleteRecursively() }
                throw t
            }
        } else {
            copyFileChunked(entry, target, cancelFlag) { delta -> onChunk(entry.name, delta) }
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
    suspend fun deleteFile(
        file: File,
        source: FileTaskSource = FileTaskSource.Local,
        clientIp: String = "",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val task = beginFileTask(FileTaskKind.Delete, source, file.name, clientIp = clientIp)
        runCatching {
            deleteEntryInternal(file)
        }.also { res ->
            if (res.isSuccess) {
                notifyFilesChanged()
                refresh()
            }
            endFileTask(
                id = task.id,
                success = res.isSuccess,
                count = if (res.isSuccess) 1 else 0,
                error = res.exceptionOrNull()?.message,
            )
        }
    }

    /** 批量永久删除多个文件/文件夹。 */
    suspend fun deleteEntries(
        entries: List<File>,
        source: FileTaskSource = FileTaskSource.Local,
        clientIp: String = "",
    ): Result<Int> = withContext(Dispatchers.IO) {
        val title = if (entries.size == 1) entries.first().name else "${entries.firstOrNull()?.name ?: ""} (${entries.size})"
        val task = beginFileTask(FileTaskKind.Delete, source, title, clientIp = clientIp)
        runCatching {
            var count = 0
            for (entry in entries) {
                updateFileTask(task.id, detail = entry.name)
                deleteEntryInternal(entry)
                count++
            }
            count
        }.also { res ->
            if (res.isSuccess) {
                notifyFilesChanged()
                refresh()
            }
            endFileTask(
                id = task.id,
                success = res.isSuccess,
                count = res.getOrDefault(0),
                error = res.exceptionOrNull()?.message,
            )
        }
    }

    /** 统一带任务状态追踪的压缩包解压。 */
    suspend fun extractArchive(
        file: File,
        destDir: File,
        source: FileTaskSource = FileTaskSource.Local,
        clientIp: String = "",
        onProgress: (count: Int, entryName: String, bytesDone: Long) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
        overwriteExisting: Boolean = true,
        skipHiddenEntries: Boolean = false,
    ): Result<Int> {
        val task = beginFileTask(
            kind = FileTaskKind.Extract,
            source = source,
            title = file.name,
            detail = destDir.name,
            clientIp = clientIp,
            cancellable = true,
        )
        return try {
            val res = ArchiveManager.extract(
                file = file,
                destDir = destDir,
                onProgress = onProgress,
                isCancelled = { isCancelled() || task.cancelFlag.get() },
                onBytesProgress = { entryName, bytesDone, totalBytes ->
                    updateFileTask(task.id, detail = entryName, bytesDone = bytesDone, totalBytes = totalBytes)
                },
                overwriteExisting = overwriteExisting,
                skipHiddenEntries = skipHiddenEntries,
            )
            if (res.isSuccess) {
                notifyFilesChanged()
                refresh()
            }
            endFileTask(
                id = task.id,
                success = res.isSuccess,
                cancelled = isCancelled() || task.cancelFlag.get(),
                count = res.getOrDefault(0),
                createdName = destDir.name,
                error = res.exceptionOrNull()?.message,
            )
            res
        } catch (t: Throwable) {
            endFileTask(
                id = task.id,
                success = false,
                cancelled = isCancelled() || task.cancelFlag.get(),
                error = t.message,
            )
            Result.failure(t)
        }
    }

    /** 统一带任务状态追踪的 ZIP 打包。 */
    suspend fun compressToZip(
        sources: List<File>,
        destZip: File,
        source: FileTaskSource = FileTaskSource.Local,
        clientIp: String = "",
        onProgress: (entryName: String) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Result<File> {
        val task = beginFileTask(
            kind = FileTaskKind.Compress,
            source = source,
            title = destZip.name,
            clientIp = clientIp,
            totalBytes = sources.sumOf(::measureEntrySize),
            cancellable = true,
        )
        return try {
            val res = ArchiveManager.compressToZip(
                sources = sources,
                destZip = destZip,
                onProgress = onProgress,
                isCancelled = { isCancelled() || task.cancelFlag.get() },
                onBytesProgress = { entryName, bytesDone, totalBytes ->
                    updateFileTask(task.id, detail = entryName, bytesDone = bytesDone, totalBytes = totalBytes)
                },
            )
            if (res.isSuccess) {
                notifyFilesChanged()
                refreshIfInRomRoots(destZip.parentFile ?: destZip)
            }
            endFileTask(
                id = task.id,
                success = res.isSuccess,
                cancelled = isCancelled() || task.cancelFlag.get(),
                createdName = destZip.name,
                error = res.exceptionOrNull()?.message,
            )
            res
        } catch (t: Throwable) {
            endFileTask(
                id = task.id,
                success = false,
                cancelled = isCancelled() || task.cancelFlag.get(),
                error = t.message,
            )
            Result.failure(t)
        }
    }

    private suspend fun deleteEntryInternal(entry: File) {
        require(entry.exists()) { "File does not exist" }
        val roots = configuredRoots().mapNotNull { runCatching { it.canonicalPath }.getOrNull() }.toSet()
        val canon = runCatching { entry.canonicalPath }.getOrDefault(entry.path)
        require(roots.none { r -> r == canon || r.startsWith("$canon${File.separator}") }) {
            "Cannot delete ROM root directory or its parent"
        }

        val wasDir = entry.isDirectory
        if (wasDir) {
            check(entry.deleteRecursively()) { "Failed to delete folder" }
            userStore.removePathPrefix(entry.path)
            scrapeDb?.deletePathPrefix(entry.path)
            onPathPrefixRemoved?.invoke(entry.path)
        } else {
            check(entry.delete()) { "Failed to delete file" }
            userStore.removeGameData(entry.path)
            scrapeDb?.deleteGameData(entry.path)
            onGameRemoved?.invoke(entry.path)
        }
    }
}
