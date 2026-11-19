package com.pocketdeck.launcher.ui.library

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.widget.Toast
import com.pocketdeck.launcher.lan.TransferPhase
import com.pocketdeck.launcher.ui.components.FavoriteHeartIcon
import com.pocketdeck.launcher.ui.components.soundClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.ArchiveManager
import com.pocketdeck.launcher.data.Category
import com.pocketdeck.launcher.data.CoverResolutionInfo
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.FileTask
import com.pocketdeck.launcher.data.FileTaskKind
import com.pocketdeck.launcher.data.FileTaskSource
import com.pocketdeck.launcher.data.MetadataResolutionInfo
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.RomScanner
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.lan.LanServerManager
import com.pocketdeck.launcher.lan.QuickShareService
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.apps.drawBackArrow
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.GameCategoryPickerDialog
import com.pocketdeck.launcher.ui.components.GameCoverCard
import com.pocketdeck.launcher.ui.components.GamePropertiesDialog
import com.pocketdeck.launcher.ui.edit.DeleteFileConfirmDialog
import com.pocketdeck.launcher.ui.edit.EditGameScreen
import android.content.ClipData
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.pocketdeck.launcher.ui.lan.LanServiceControlDialog
import com.pocketdeck.launcher.ui.lan.ShareMethodDialog
import com.pocketdeck.launcher.ui.lan.SharePeerPickerDialog
import com.pocketdeck.launcher.ui.settings.ActionItem
import com.pocketdeck.launcher.ui.settings.ActionMenuDialog
import com.pocketdeck.launcher.ui.settings.ActionMenuState
import com.pocketdeck.launcher.ui.settings.NameDialog
import com.pocketdeck.launcher.ui.settings.NameDialogSpec
import com.pocketdeck.launcher.ui.settings.NameDialogState
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckFontFamily
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class LibraryMode { Games, Files }
enum class GamesFilter { All, Visible, Hidden, Uncategorized }
private enum class LibraryZone { Header, Search, Filter, FilesToolbar, List }

private data class ShareFileTarget(
    val files: List<File>,
    val gameTitle: String,
    val platformId: String,
    val platformName: String,
) {
    val primaryFile: File get() = files.first()
    val totalSize: Long get() = files.sumOf { runCatching { it.length() }.getOrDefault(0L) }
}

private data class StorageLocation(
    val dir: File,
    val label: String,
    val isRomRoot: Boolean,
    val badge: String?,
)

private suspend fun searchFilesInScope(
    roots: List<File>,
    query: String,
    recursive: Boolean,
    maxDepth: Int = 16,
    maxResults: Int = 500,
): List<File> = withContext(Dispatchers.IO) {
    val q = query.trim()
    if (q.isEmpty() || roots.isEmpty()) return@withContext emptyList()
    val results = ArrayList<File>()
    val seenResultPaths = HashSet<String>()
    val seenDirs = HashSet<String>()

    fun recordIfMatch(file: File) {
        if (results.size >= maxResults) return
        if (file.name.contains(q, ignoreCase = true) && seenResultPaths.add(file.path)) {
            results.add(file)
        }
    }

    if (!recursive) {
        for (root in roots) {
            ensureActive()
            if (!root.isDirectory || !root.canRead()) continue
            val children = root.listFiles { f -> !f.name.startsWith(".") }.orEmpty()
            for (child in children) {
                recordIfMatch(child)
                if (results.size >= maxResults) break
            }
        }
    } else {
        val queue = ArrayDeque<Pair<File, Int>>()
        for (root in roots) {
            if (!root.isDirectory || !root.canRead()) continue
            val canon = runCatching { root.canonicalPath }.getOrElse { root.path }
            if (seenDirs.add(canon)) {
                queue.addLast(root to 0)
            }
        }
        while (queue.isNotEmpty() && results.size < maxResults) {
            ensureActive()
            val (dir, depth) = queue.removeFirst()
            val children = dir.listFiles { f -> !f.name.startsWith(".") }.orEmpty()
            for (child in children) {
                recordIfMatch(child)
                if (results.size >= maxResults) break
                if (child.isDirectory && depth < maxDepth && child.canRead()) {
                    val canon = runCatching { child.canonicalPath }.getOrElse { child.path }
                    if (seenDirs.add(canon)) {
                        queue.addLast(child to (depth + 1))
                    }
                }
            }
        }
    }

    results.sortWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }, { it.path.lowercase() }))
    results
}

private fun discoverStorageLocations(context: Context, romRoots: List<File>): List<StorageLocation> {
    val result = ArrayList<StorageLocation>()
    val seenCanon = HashSet<String>()
    val sdDir = Environment.getExternalStorageDirectory()
    val sdPath = sdDir.path

    fun addIfValid(dir: File?, label: String, isRomRoot: Boolean, badge: String?) {
        if (dir == null || !dir.isDirectory || !dir.canRead()) return
        val canon = runCatching { dir.canonicalPath }.getOrDefault(dir.path)
        if (!seenCanon.add(canon)) return
        result += StorageLocation(dir = dir, label = label, isRomRoot = isRomRoot, badge = badge)
    }

    // 1) 已配置的 ROM 目录
    romRoots.forEach { r ->
        addIfValid(r, r.path.replace(sdPath, "~"), isRomRoot = true, badge = context.getString(R.string.lib_storage_rom_tag))
    }

    // 2) 快传收件箱 & 下载目录 (Download)
    val inboxDir = QuickShareService.defaultInboxDir()
    addIfValid(inboxDir, context.getString(R.string.lib_storage_inbox), isRomRoot = false, badge = "QuickShare")
    val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    addIfValid(dlDir, context.getString(R.string.lib_storage_download), isRomRoot = false, badge = "Download")

    // 3) 内部存储根目录
    addIfValid(sdDir, context.getString(R.string.lib_storage_internal), isRomRoot = false, badge = null)

    // 4) 外接 TF / SD 卡与 USB OTG U 盘
    val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
    if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        sm.storageVolumes.forEach { vol ->
            if (!vol.isPrimary && vol.state == Environment.MEDIA_MOUNTED) {
                val volDir = vol.directory
                val desc = runCatching { vol.getDescription(context) }.getOrNull().orEmpty()
                    .ifBlank { volDir?.name ?: context.getString(R.string.lib_storage_external) }
                addIfValid(volDir, desc, isRomRoot = false, badge = context.getString(R.string.lib_storage_external))
            }
        }
    }
    // 兼容部分机型 USB/TF 挂载扫描
    runCatching {
        context.getExternalFilesDirs(null).forEach { appDir ->
            var cur = appDir
            repeat(4) { cur = cur?.parentFile }
            if (cur != null && cur.path != sdPath) {
                addIfValid(cur, cur.name, isRomRoot = false, badge = context.getString(R.string.lib_storage_external))
            }
        }
    }
    runCatching {
        File("/storage").listFiles()?.forEach { vol ->
            val n = vol.name
            if (vol.isDirectory && n != "emulated" && n != "self" && n != "enc_emulated") {
                addIfValid(vol, n, isRomRoot = false, badge = context.getString(R.string.lib_storage_external))
            }
        }
    }
    return result
}

private sealed interface MoveTarget {
    val label: String

    data class Single(val file: File, val isCopy: Boolean = false) : MoveTarget {
        override val label: String get() = file.name
    }

    data class Batch(val files: List<File>, val isCopy: Boolean = false) : MoveTarget {
        override val label: String get() = "${files.size} items"
    }

    data class ExtractArchive(val archive: File) : MoveTarget {
        override val label: String get() = archive.name
    }
}

/**
 * 「游戏库」内置应用图标：融合 文件夹 + 手柄 + 搜索管理角标 的第一方风格 App Logo。
 */
@Composable
fun GameLibraryAppIcon(
    size: Dp = 54.dp,
    modifier: Modifier = Modifier,
) {
    val corner = size * 0.22f
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner)),
    ) {
        val w = this.size.width
        val h = this.size.height

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF3B6EF6), Color(0xFF1D4ED8), Color(0xFF0E7490)),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.14f),
            radius = w * 0.48f,
            center = Offset(w * 0.25f, h * 0.12f),
        )

        val fLeft = w * 0.15f
        val fRight = w * 0.85f
        val fTop = h * 0.23f
        val fBottom = h * 0.78f
        val tabW = w * 0.28f
        val tabH = h * 0.09f
        val r = w * 0.08f

        val folderPath = Path().apply {
            moveTo(fLeft, fTop + r)
            quadraticTo(fLeft, fTop, fLeft + r, fTop)
            lineTo(fLeft + tabW, fTop)
            lineTo(fLeft + tabW + tabH * 0.8f, fTop + tabH)
            lineTo(fRight - r, fTop + tabH)
            quadraticTo(fRight, fTop + tabH, fRight, fTop + tabH + r)
            lineTo(fRight, fBottom - r)
            quadraticTo(fRight, fBottom, fRight - r, fBottom)
            lineTo(fLeft + r, fBottom)
            quadraticTo(fLeft, fBottom, fLeft, fBottom - r)
            close()
        }
        drawPath(folderPath, color = Color.White.copy(alpha = 0.94f))

        val padW = w * 0.38f
        val padH = h * 0.22f
        val padLeft = (w - padW) * 0.47f
        val padTop = h * 0.44f
        drawRoundRect(
            color = Color(0xFF1E3A8A),
            topLeft = Offset(padLeft, padTop),
            size = Size(padW, padH),
            cornerRadius = CornerRadius(padH * 0.5f, padH * 0.5f),
        )
        val dpadCx = padLeft + padW * 0.30f
        val dpadCy = padTop + padH * 0.5f
        val dpadR = padH * 0.28f
        val dpadStroke = (w * 0.036f).coerceAtLeast(1.5f)
        drawLine(
            Color.White,
            Offset(dpadCx - dpadR, dpadCy),
            Offset(dpadCx + dpadR, dpadCy),
            strokeWidth = dpadStroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            Color.White,
            Offset(dpadCx, dpadCy - dpadR),
            Offset(dpadCx, dpadCy + dpadR),
            strokeWidth = dpadStroke,
            cap = StrokeCap.Round,
        )
        val dotR = (w * 0.025f).coerceAtLeast(1.2f)
        drawCircle(Color(0xFF38BDF8), dotR, Offset(padLeft + padW * 0.65f, padTop + padH * 0.62f))
        drawCircle(Color(0xFFFCA5A5), dotR, Offset(padLeft + padW * 0.77f, padTop + padH * 0.39f))

        val badgeCx = w * 0.75f
        val badgeCy = h * 0.74f
        val badgeR = w * 0.175f
        drawCircle(Color(0xFF0EA5E9), badgeR, Offset(badgeCx, badgeCy))
        drawCircle(
            Color.White,
            badgeR,
            Offset(badgeCx, badgeCy),
            style = Stroke(width = (w * 0.03f).coerceAtLeast(1.5f)),
        )
        val glassR = badgeR * 0.42f
        val glassCx = badgeCx - badgeR * 0.12f
        val glassCy = badgeCy - badgeR * 0.12f
        val glassStroke = (w * 0.034f).coerceAtLeast(1.5f)
        drawCircle(
            Color.White,
            glassR,
            Offset(glassCx, glassCy),
            style = Stroke(width = glassStroke),
        )
        drawLine(
            Color.White,
            Offset(glassCx + glassR * 0.72f, glassCy + glassR * 0.72f),
            Offset(badgeCx + badgeR * 0.54f, badgeCy + badgeR * 0.54f),
            strokeWidth = glassStroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 游戏库管理器：
 * - 游戏管理视图：关键词搜索、全部/已显示/已隐藏/未归类、平台过滤、快速恢复隐藏、属性、所属分类、重命名、移动、删除
 * - 目录文件视图：多选批量操作、文件夹改名/移动/删除、常见压缩包（ZIP/7Z/RAR/TAR/GZ/BZ2/XZ）预览与解压/压缩、图片与文本/配置查看编辑
 * - 局域网服务与快传入口；全程支持纯键盘与手柄操作（无触屏亦可完整使用）
 */
@Composable
fun GameLibraryScreen(
    fullLibrary: Library?,
    filesRevision: Long = 0L,
    fileTasks: List<FileTask> = emptyList(),
    hiddenIds: Set<String>,
    favorites: Set<String>,
    categories: List<Category>,
    metadataMap: Map<String, GameMetadata>,
    officialNames: Map<String, String>,
    metaResolutionMap: Map<String, MetadataResolutionInfo>,
    coverResolutionMap: Map<String, CoverResolutionInfo>,
    platformLaunchers: Map<String, String>,
    gameLaunchers: Map<String, String>,
    covers: CoverSource,
    lanManager: LanServerManager,
    initialHiddenOnly: Boolean = false,
    initialFilesDir: String? = null,
    initialFocusFilePath: String? = null,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    platformFor: (Game) -> Platform,
    effectiveLauncherFor: (Game) -> String?,
    computeCrc32: suspend (String) -> String?,
    onRefreshMetadata: suspend (Game) -> Boolean,
    onLaunchGame: (Game) -> Unit,
    onHideGame: (String) -> Unit,
    onUnhideGame: (String) -> Unit,
    onSaveGameEdits: (String, String?, String?) -> Unit,
    onSetGameLauncher: (String, String?) -> Unit,
    onSetGameCategories: (String, Set<String>) -> Unit,
    onCreateCategoryForGame: ((String, String) -> Unit)? = null,
    onLoadRoots: suspend () -> List<File>,
    onRenameFile: suspend (File, String) -> Result<File>,
    onMoveFile: suspend (File, File) -> Result<File>,
    onMoveEntries: suspend (List<File>, File) -> Result<Int> = { list, dir ->
        var okCount = 0
        var firstErr: Throwable? = null
        for (f in list) {
            val res = onMoveFile(f, dir)
            if (res.isFailure) {
                firstErr = res.exceptionOrNull()
                break
            }
            okCount++
        }
        if (firstErr != null) Result.failure(firstErr) else Result.success(okCount)
    },
    onCopyFile: suspend (File, File) -> Result<File> = { _, _ -> Result.failure(UnsupportedOperationException()) },
    onCopyEntries: suspend (List<File>, File) -> Result<Int> = { list, dir ->
        var okCount = 0
        var firstErr: Throwable? = null
        for (f in list) {
            val res = onCopyFile(f, dir)
            if (res.isFailure) {
                firstErr = res.exceptionOrNull()
                break
            }
            okCount++
        }
        if (firstErr != null) Result.failure(firstErr) else Result.success(okCount)
    },
    onDeleteFile: suspend (File) -> Result<Unit>,
    onDeleteEntries: suspend (List<File>) -> Result<Int> = { list ->
        var okCount = 0
        var firstErr: Throwable? = null
        for (f in list) {
            val res = onDeleteFile(f)
            if (res.isFailure) {
                firstErr = res.exceptionOrNull()
                break
            }
            okCount++
        }
        if (firstErr != null) Result.failure(firstErr) else Result.success(okCount)
    },
    onExtractArchive: suspend (File, File, (Int, String, Long) -> Unit, () -> Boolean) -> Result<Int> = { file, destDir, onProgress, isCancelled ->
        ArchiveManager.extract(file, destDir, onProgress = onProgress, isCancelled = isCancelled)
    },
    onCompressZip: suspend (List<File>, File, (String) -> Unit, () -> Boolean) -> Result<File> = { sources, destZip, onProgress, isCancelled ->
        ArchiveManager.compressToZip(sources, destZip, onProgress = onProgress, isCancelled = isCancelled)
    },
    onCancelFileTask: (String) -> Unit = {},
    onApplyFontId: (String) -> Unit = {},
    onOpenQuickShare: () -> Unit = {},
    onRescan: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val searchFieldFr = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val filterRowState = rememberLazyListState()

    var mode by remember(initialFilesDir) {
        mutableStateOf(if (!initialFilesDir.isNullOrBlank()) LibraryMode.Files else LibraryMode.Games)
    }
    var focusZone by remember { mutableStateOf(LibraryZone.List) }
    var headerIdx by remember(initialFilesDir) {
        mutableIntStateOf(if (!initialFilesDir.isNullOrBlank()) 3 else 2)
    }
    var filterChipIdx by remember { mutableIntStateOf(0) }
    var filesToolbarIdx by remember { mutableIntStateOf(0) }
    var searchFieldFocused by remember { mutableStateOf(false) }

    var gamesFilter by remember(initialHiddenOnly) {
        mutableStateOf(if (initialHiddenOnly) GamesFilter.Hidden else GamesFilter.All)
    }
    var selectedPlatformId by remember { mutableStateOf<String?>(null) }
    var gameSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    LaunchedEffect(gameSearchOpen) {
        if (gameSearchOpen) {
            delay(60)
            runCatching { searchFieldFr.requestFocus() }
        }
    }
    var fileSearchOpen by remember { mutableStateOf(false) }
    var fileSearchQuery by remember { mutableStateOf("") }
    var fileSearchRecursive by remember { mutableStateOf(false) }
    var fileSearchFieldFocused by remember { mutableStateOf(false) }
    val fileSearchFieldFr = remember { FocusRequester() }
    var fileSearchChipIdx by remember { mutableIntStateOf(0) } // 0=Input, 1=Current dir, 2=Recursive
    var fileSearching by remember { mutableStateOf(false) }
    var selectedIdx by remember { mutableIntStateOf(0) }

    // 文件浏览与多选状态
    var romRoots by remember { mutableStateOf<List<File>>(emptyList()) }
    var storageLocations by remember { mutableStateOf<List<StorageLocation>>(emptyList()) }
    var currentDir by remember { mutableStateOf<File?>(null) }
    var fileEntries by remember { mutableStateOf<List<File>>(emptyList()) }
    var dirRefreshTick by remember { mutableIntStateOf(0) }
    var lastLocalRefreshTick by remember { mutableIntStateOf(0) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingFocusPath by remember { mutableStateOf<String?>(null) }
    var lastLoadedDirPath by remember { mutableStateOf<String?>(null) }
    val dirParentHistory = remember { androidx.compose.runtime.mutableStateMapOf<String, File?>() }

    // 子弹窗状态
    var actionMenu by remember { mutableStateOf<ActionMenuState?>(null) }
    var propertiesGame by remember { mutableStateOf<Game?>(null) }
    var editingGame by remember { mutableStateOf<Game?>(null) }
    var categoryTargetGame by remember { mutableStateOf<Game?>(null) }
    var nameSpec by remember { mutableStateOf<NameDialogSpec?>(null) }
    val nameState = remember(nameSpec) { nameSpec?.let { NameDialogState(it) } }
    var moveTarget by remember { mutableStateOf<MoveTarget?>(null) }
    var deleteConfirm by remember { mutableStateOf<Pair<File, Game?>?>(null) }
    var batchDeleteFiles by remember { mutableStateOf<List<File>?>(null) }
    var showLanDialog by remember { mutableStateOf(false) }
    var shareMethodTarget by remember { mutableStateOf<ShareFileTarget?>(null) }
    var shareTarget by remember { mutableStateOf<ShareFileTarget?>(null) }
    var archivePreviewFile by remember { mutableStateOf<File?>(null) }
    var imageViewerFile by remember { mutableStateOf<File?>(null) }
    var textEditorFile by remember { mutableStateOf<File?>(null) }
    var audioPlayerFile by remember { mutableStateOf<File?>(null) }
    var fontViewerFile by remember { mutableStateOf<File?>(null) }
    var opProgress by remember { mutableStateOf<Pair<String, String>?>(null) }
    var opJob by remember { mutableStateOf<Job?>(null) }
    val opCancelled = remember { AtomicBoolean(false) }

    val lanState by lanManager.state.collectAsState()

    val allGames = remember(fullLibrary) {
        fullLibrary?.games?.values?.flatten().orEmpty().sortedBy { it.title.lowercase() }
    }
    val allPlatforms = remember(fullLibrary) {
        fullLibrary?.platforms.orEmpty()
    }
    val categorizedPlatforms = remember(categories) { categories.flatMap { it.sources }.toSet() }
    val categorizedGames = remember(categories) { categories.flatMap { it.games }.toSet() }
    val hiddenCount = remember(allGames, hiddenIds) { allGames.count { it.id in hiddenIds } }

    val filteredGames = remember(
        allGames,
        hiddenIds,
        gamesFilter,
        selectedPlatformId,
        searchQuery,
        categorizedPlatforms,
        categorizedGames,
    ) {
        val q = searchQuery.trim().lowercase()
        allGames.filter { g ->
            val matchFilter = when (gamesFilter) {
                GamesFilter.All -> true
                GamesFilter.Visible -> g.id !in hiddenIds
                GamesFilter.Hidden -> g.id in hiddenIds
                GamesFilter.Uncategorized -> g.platformId !in categorizedPlatforms && g.id !in categorizedGames
            }
            if (!matchFilter) return@filter false
            if (selectedPlatformId != null && g.platformId != selectedPlatformId) return@filter false
            if (q.isNotEmpty()) {
                val plat = platformFor(g)
                val hay = "${g.title} ${g.fileName} ${g.id} ${plat.shortName} ${plat.fullName}".lowercase()
                if (q !in hay) return@filter false
            }
            true
        }
    }

    LaunchedEffect(initialFilesDir, initialFocusFilePath) {
        val roots = onLoadRoots()
        romRoots = roots
        storageLocations = discoverStorageLocations(context, roots)
        val jumpTarget = initialFilesDir?.let(::File)?.takeIf { it.isDirectory }
        if (jumpTarget != null) {
            currentDir = jumpTarget
            pendingFocusPath = initialFocusFilePath
        } else if (roots.size == 1) {
            currentDir = roots.first()
        }
        focusRequester.requestFocus()
    }

    LaunchedEffect(mode, currentDir, dirRefreshTick, filesRevision, fileSearchQuery, fileSearchRecursive) {
        if (mode != LibraryMode.Files) {
            fileSearching = false
            return@LaunchedEffect
        }
        val dir = currentDir
        if (dir != null && !dir.exists()) {
            var ancestor = dir.parentFile
            while (ancestor != null && !ancestor.exists()) {
                ancestor = ancestor.parentFile
            }
            val topDirs = storageLocations.map { it.dir } + romRoots
            val fallback = ancestor?.takeIf { a ->
                topDirs.any { top -> a.path == top.path || a.path.startsWith("${top.path}${File.separator}") }
            }
            currentDir = fallback
            return@LaunchedEffect
        }
        val dirChanged = lastLoadedDirPath != dir?.path
        val localRefresh = lastLocalRefreshTick != dirRefreshTick
        val hadPendingFocus = pendingFocusPath != null
        val q = fileSearchQuery.trim()
        if (q.isNotEmpty()) {
            if (fileSearchRecursive) delay(120)
            fileSearching = true
            try {
                if (dir == null) {
                    storageLocations = discoverStorageLocations(context, romRoots)
                }
                fileEntries = if (dir == null && !fileSearchRecursive) {
                    storageLocations.filter { loc ->
                        loc.label.contains(q, ignoreCase = true) ||
                            loc.dir.name.contains(q, ignoreCase = true) ||
                            loc.dir.path.contains(q, ignoreCase = true)
                    }.map { it.dir }
                } else {
                    val roots = if (dir != null) listOf(dir) else storageLocations.map { it.dir }
                    searchFilesInScope(roots = roots, query = q, recursive = fileSearchRecursive)
                }
            } finally {
                fileSearching = false
            }
        } else if (dir == null) {
            fileSearching = false
            storageLocations = discoverStorageLocations(context, romRoots)
            fileEntries = storageLocations.map { it.dir }
        } else {
            fileSearching = false
            fileEntries = withContext(Dispatchers.IO) {
                dir.listFiles { f -> !f.name.startsWith(".") }
                    .orEmpty()
                    .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            }
        }
        val upNow = dir != null && q.isEmpty()
        val targetPath = pendingFocusPath
        if (targetPath != null) {
            val matchIdx = fileEntries.indexOfFirst { it.path == targetPath }
            selectedIdx = if (matchIdx >= 0) (if (upNow) 1 else 0) + matchIdx else 0
            pendingFocusPath = null
        } else if (!dirChanged) {
            val totalNow = (if (upNow) 1 else 0) + fileEntries.size
            selectedIdx = selectedIdx.coerceIn(0, (totalNow - 1).coerceAtLeast(0))
        } else {
            selectedIdx = 0
        }
        lastLoadedDirPath = dir?.path
        lastLocalRefreshTick = dirRefreshTick
        if (dirChanged || localRefresh || hadPendingFocus) {
            multiSelectMode = false
            selectedPaths = emptySet()
            if (fileEntries.isNotEmpty()) {
                runCatching { listState.scrollToItem((selectedIdx - 2).coerceAtLeast(0)) }
            }
        } else if (multiSelectMode) {
            val existingPaths = fileEntries.mapTo(HashSet()) { it.path }
            selectedPaths = selectedPaths.intersect(existingPaths)
            if (existingPaths.isEmpty()) multiSelectMode = false
        }
    }

    val overlayActive = actionMenu != null ||
        propertiesGame != null ||
        editingGame != null ||
        categoryTargetGame != null ||
        nameSpec != null ||
        moveTarget != null ||
        deleteConfirm != null ||
        batchDeleteFiles != null ||
        showLanDialog ||
        shareMethodTarget != null ||
        shareTarget != null ||
        archivePreviewFile != null ||
        imageViewerFile != null ||
        textEditorFile != null ||
        audioPlayerFile != null ||
        fontViewerFile != null ||
        opProgress != null

    LaunchedEffect(overlayActive) {
        if (!overlayActive) focusRequester.requestFocus()
    }
    LaunchedEffect(fileSearchOpen) {
        if (fileSearchOpen) {
            delay(40)
            runCatching { fileSearchFieldFr.requestFocus() }
        }
    }

    fun enterDir(target: File) {
        dirParentHistory[target.path] = currentDir
        fileSearchQuery = ""
        fileSearchOpen = false
        currentDir = target
    }

    fun canStepUp(): Boolean = currentDir != null

    fun stepUpDir(): Boolean {
        val dir = currentDir ?: return false
        pendingFocusPath = dir.path
        val parentFromHistory = if (dirParentHistory.containsKey(dir.path)) {
            dirParentHistory.remove(dir.path)
        } else {
            val isTopLocation = storageLocations.any { it.dir.path == dir.path } ||
                romRoots.any { it.path == dir.path }
            if (isTopLocation) null else dir.parentFile
        }
        fileSearchQuery = ""
        fileSearchOpen = false
        currentDir = parentFromHistory
        return true
    }

    val hasUp = canStepUp() && fileSearchQuery.isBlank()
    val activeItemCount = if (mode == LibraryMode.Games) {
        filteredGames.size
    } else {
        (if (hasUp) 1 else 0) + fileEntries.size
    }

    LaunchedEffect(selectedIdx, activeItemCount) {
        if (activeItemCount > 0 && selectedIdx >= activeItemCount) {
            selectedIdx = activeItemCount - 1
        }
        if (selectedIdx in 0 until activeItemCount) {
            val vis = listState.layoutInfo.visibleItemsInfo
            if (vis.none { it.index == selectedIdx }) {
                listState.animateScrollToItem(selectedIdx)
            }
        }
    }

    LaunchedEffect(filterChipIdx, focusZone) {
        if (focusZone == LibraryZone.Filter) {
            runCatching { filterRowState.animateScrollToItem((filterChipIdx - 2).coerceAtLeast(0)) }
        }
    }

    // 多语言文案预取
    val playLabel = stringResource(R.string.menu_play)
    val propsLabel = stringResource(R.string.menu_properties)
    val categoriesLabel = stringResource(R.string.lib_action_categories)
    val hideLabel = stringResource(R.string.lib_action_hide)
    val unhideLabel = stringResource(R.string.lib_action_unhide)
    val editLabel = stringResource(R.string.menu_edit)
    val renameFileLabel = stringResource(R.string.lib_action_rename_file)
    val moveFileLabel = stringResource(R.string.lib_action_move_file)
    val copyFileLabel = stringResource(R.string.lib_action_copy_file)
    val deleteFileLabel = stringResource(R.string.edit_delete_file)
    val renameTitle = stringResource(R.string.lib_rename_file_title)
    val renameDone = stringResource(R.string.lib_rename_done)
    val renameFailFmt = stringResource(R.string.lib_rename_failed)
    val moveDone = stringResource(R.string.lib_move_done)
    val moveFailFmt = stringResource(R.string.lib_move_failed)
    val copyDone = stringResource(R.string.lib_copy_done)
    val copyFailFmt = stringResource(R.string.lib_copy_failed)
    val copyBatchDoneFmt = stringResource(R.string.lib_copy_batch_done)
    val storageLocationsLabel = stringResource(R.string.lib_storage_locations)
    val batchCopyFmt = stringResource(R.string.lib_batch_copy)
    val deleteDone = stringResource(R.string.delete_file_done)
    val deleteFailFmt = stringResource(R.string.delete_file_failed)
    val newFolderTitle = stringResource(R.string.lib_new_folder_title)
    val newFolderDone = stringResource(R.string.lib_new_folder_done)
    val newFolderFailFmt = stringResource(R.string.lib_new_folder_failed)
    val shareSendLabel = stringResource(R.string.lan_share_send_action)

    val folderOpenLabel = stringResource(R.string.lib_folder_open)
    val folderRenameLabel = stringResource(R.string.lib_folder_rename)
    val folderMoveLabel = stringResource(R.string.lib_folder_move)
    val folderDeleteLabel = stringResource(R.string.lib_folder_delete)
    val viewImageLabel = stringResource(R.string.lib_file_view_image)
    val viewTextLabel = stringResource(R.string.lib_file_view_text)
    val playAudioLabel = stringResource(R.string.lib_file_play_audio)
    val viewFontLabel = stringResource(R.string.lib_file_view_font)
    val shareBatchLabel = stringResource(R.string.lan_share_batch_action)
    val shareNoFilesMsg = stringResource(R.string.lan_share_no_files)
    val shareChooserTitle = stringResource(R.string.lan_share_method_android)
    val shareSentLanMsg = stringResource(R.string.lan_share_started_hint)
    val archiveInspectLabel = stringResource(R.string.lib_archive_inspect)
    val archiveExtractHereLabel = stringResource(R.string.lib_archive_extract_here)
    val compressZipLabel = stringResource(R.string.lib_archive_compress_zip)
    val extractingTitle = stringResource(R.string.lib_archive_extracting)
    val compressingTitle = stringResource(R.string.lib_archive_compressing)
    val extractDoneFmt = stringResource(R.string.lib_archive_extract_done)
    val compressDoneFmt = stringResource(R.string.lib_archive_compress_done)
    val archiveFailFmt = stringResource(R.string.lib_archive_failed)
    val addMultiSelectLabel = stringResource(R.string.lib_add_multi_select)
    val selectAllLabel = stringResource(R.string.lib_select_all)
    val exitMultiLabel = stringResource(R.string.lib_exit_select)
    val batchExtractLabel = stringResource(R.string.lib_batch_extract)
    val batchDoneFmt = stringResource(R.string.lib_batch_done)
    val batchMoveFmt = stringResource(R.string.lib_batch_move)
    val batchDeleteFmt = stringResource(R.string.lib_batch_delete)
    val batchActionsFmt = stringResource(R.string.lib_batch_actions)

    fun launchSystemShare(files: List<File>) {
        val valid = files.filter { it.isFile && it.canRead() }
        if (valid.isEmpty()) {
            Toast.makeText(context, shareNoFilesMsg, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val authority = "${context.packageName}.fileprovider"
            val uris = ArrayList(valid.map { FileProvider.getUriForFile(context, authority, it) })
            fun mimeOf(f: File): String =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())
                    ?: "application/octet-stream"
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeOf(valid.first())
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    putExtra(Intent.EXTRA_SUBJECT, valid.first().name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                val distinctMimes = valid.map(::mimeOf).toSet()
                val batchMime = if (distinctMimes.size == 1) distinctMimes.first() else "*/*"
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = batchMime
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    putExtra(Intent.EXTRA_SUBJECT, valid.joinToString(", ") { it.name })
                    clipData = ClipData.newUri(context.contentResolver, "share", uris.first()).apply {
                        for (i in 1 until uris.size) addItem(ClipData.Item(uris[i]))
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            val chooser = Intent.createChooser(intent, shareChooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, it.message ?: "Share failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun runExtract(archive: File, destDir: File) {
        opCancelled.set(false)
        opProgress = extractingTitle to archive.name
        opJob = scope.launch {
            try {
                val res = onExtractArchive(
                    archive,
                    destDir,
                    { _, entryName, _ -> if (!opCancelled.get()) opProgress = extractingTitle to entryName },
                    { opCancelled.get() },
                )
                if (!opCancelled.get()) {
                    if (res.isSuccess) {
                        Toast.makeText(context, extractDoneFmt.format(res.getOrDefault(0)), Toast.LENGTH_SHORT).show()
                        dirRefreshTick++
                        onRescan()
                    } else {
                        Toast.makeText(context, archiveFailFmt.format(res.exceptionOrNull()?.message.orEmpty()), Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                opProgress = null
                opJob = null
            }
        }
    }

    fun runCompressZip(sources: List<File>, destDir: File, defaultZipName: String) {
        nameSpec = NameDialogSpec(compressZipLabel, defaultZipName) { entered ->
            val clean = entered.trim()
            if (clean.isEmpty() || clean == "." || clean == ".." || '/' in clean || '\\' in clean) return@NameDialogSpec
            val finalName = if (clean.lowercase().endsWith(".zip")) clean else "$clean.zip"
            val destZip = File(destDir, finalName)
            opCancelled.set(false)
            opProgress = compressingTitle to finalName
            opJob = scope.launch {
                try {
                    val res = onCompressZip(
                        sources,
                        destZip,
                        { name -> if (!opCancelled.get()) opProgress = compressingTitle to name },
                        { opCancelled.get() },
                    )
                    if (!opCancelled.get()) {
                        if (res.isSuccess) {
                            Toast.makeText(context, compressDoneFmt.format(finalName), Toast.LENGTH_SHORT).show()
                            multiSelectMode = false
                            selectedPaths = emptySet()
                            dirRefreshTick++
                        } else {
                            Toast.makeText(context, archiveFailFmt.format(res.exceptionOrNull()?.message.orEmpty()), Toast.LENGTH_SHORT).show()
                        }
                    }
                } finally {
                    opProgress = null
                    opJob = null
                }
            }
        }
    }

    fun openNewFolderDialog() {
        val dirNow = currentDir ?: return
        nameSpec = NameDialogSpec(newFolderTitle, "") { folderName ->
            val clean = folderName.trim()
            if (clean.isEmpty() || clean == "." || clean == ".." || '/' in clean || '\\' in clean) {
                Toast.makeText(context, newFolderFailFmt.format(clean), Toast.LENGTH_SHORT).show()
                return@NameDialogSpec
            }
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    val target = File(dirNow, clean)
                    !target.exists() && runCatching { target.mkdir() }.getOrDefault(false)
                }
                Toast.makeText(
                    context,
                    if (ok) newFolderDone else newFolderFailFmt.format(clean),
                    Toast.LENGTH_SHORT,
                ).show()
                if (ok) dirRefreshTick++
            }
        }
    }

    fun openGameMenu(game: Game) {
        val isHidden = game.id in hiddenIds
        val isRom = !game.id.startsWith(GameLauncher.APK_PREFIX)
        val file = File(game.id)
        val plat = platformFor(game)
        val items = buildList {
            add(ActionItem("▶  $playLabel") { onLaunchGame(game) })
            add(ActionItem("ⓘ  $propsLabel") { propertiesGame = game })
            if (isRom && file.isFile) {
                add(ActionItem("📤  $shareSendLabel") {
                    shareMethodTarget = ShareFileTarget(listOf(file), game.title, plat.id, plat.shortName)
                })
            }
            add(ActionItem("🏷  $categoriesLabel") { categoryTargetGame = game })
            add(
                ActionItem(if (isHidden) "👁  $unhideLabel" else "🙈  $hideLabel") {
                    if (isHidden) onUnhideGame(game.id) else onHideGame(game.id)
                },
            )
            add(ActionItem("✎  $editLabel") { editingGame = game })
            if (isRom && file.isFile) {
                add(
                    ActionItem("📝  $renameFileLabel") {
                        nameSpec = NameDialogSpec(renameTitle, file.name) { newName ->
                            scope.launch {
                                val res = onRenameFile(file, newName)
                                Toast.makeText(
                                    context,
                                    if (res.isSuccess) renameDone else renameFailFmt.format(res.exceptionOrNull()?.message.orEmpty()),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                dirRefreshTick++
                            }
                        }
                    },
                )
                add(ActionItem("📦  $moveFileLabel") { moveTarget = MoveTarget.Single(file, isCopy = false) })
                add(ActionItem("📋  $copyFileLabel") { moveTarget = MoveTarget.Single(file, isCopy = true) })
                add(ActionItem("🗑  $deleteFileLabel") { deleteConfirm = file to game })
            }
        }
        actionMenu = ActionMenuState(game.title, items)
    }

    fun openFolderMenu(folder: File) {
        val isRoot = romRoots.any { runCatching { it.canonicalPath == folder.canonicalPath }.getOrDefault(false) } ||
            storageLocations.any { !it.isRomRoot && runCatching { it.dir.canonicalPath == folder.canonicalPath }.getOrDefault(false) }
        val items = buildList {
            add(ActionItem("📂  $folderOpenLabel") { enterDir(folder) })
            add(ActionItem("📋  $copyFileLabel") { moveTarget = MoveTarget.Single(folder, isCopy = true) })
            if (!isRoot) {
                add(
                    ActionItem("📝  $folderRenameLabel") {
                        nameSpec = NameDialogSpec(folderRenameLabel, folder.name) { newName ->
                            scope.launch {
                                val res = onRenameFile(folder, newName)
                                Toast.makeText(
                                    context,
                                    if (res.isSuccess) renameDone else renameFailFmt.format(res.exceptionOrNull()?.message.orEmpty()),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                dirRefreshTick++
                            }
                        }
                    },
                )
                add(ActionItem("📦  $folderMoveLabel") { moveTarget = MoveTarget.Single(folder, isCopy = false) })
                folder.parentFile?.let { parent ->
                    add(ActionItem("🗜  $compressZipLabel") {
                        runCompressZip(listOf(folder), parent, "${folder.name}.zip")
                    })
                }
                if (currentDir != null) {
                    add(ActionItem("☑  $addMultiSelectLabel") {
                        multiSelectMode = true
                        selectedPaths = selectedPaths + folder.path
                    })
                }
                add(ActionItem("🗑  $folderDeleteLabel") { batchDeleteFiles = listOf(folder) })
            }
        }
        actionMenu = ActionMenuState(folder.name, items)
    }

    fun openFileMenu(file: File) {
        val matchedGame = fullLibrary?.byId?.get(file.path)?.takeIf { RomScanner.isPotentialRomFile(file, it.platformId) }
        val isArchive = ArchiveManager.isArchive(file)
        val isImage = ArchiveManager.isImageFile(file)
        val isAudio = ArchiveManager.isAudioFile(file)
        val isFont = ArchiveManager.isFontFile(file)
        val isText = !isArchive && !isImage && !isAudio && !isFont &&
            (ArchiveManager.isKnownTextFile(file) || ArchiveManager.looksLikeTextFile(file))

        val items = buildList {
            if (matchedGame != null) {
                add(ActionItem("▶  $playLabel") { onLaunchGame(matchedGame) })
                add(ActionItem("ⓘ  $propsLabel") { propertiesGame = matchedGame })
            }
            if (isArchive) {
                add(ActionItem("🗜  $archiveInspectLabel") { archivePreviewFile = file })
                file.parentFile?.let { parent ->
                    add(ActionItem("📂  $archiveExtractHereLabel") { runExtract(file, parent) })
                }
            }
            if (isImage) {
                add(ActionItem("🖼  $viewImageLabel") { imageViewerFile = file })
            }
            if (isAudio) {
                add(ActionItem("🎵  $playAudioLabel") { audioPlayerFile = file })
            }
            if (isFont) {
                add(ActionItem("🔤  $viewFontLabel") { fontViewerFile = file })
            }
            if (isText) {
                add(ActionItem("📄  $viewTextLabel") { textEditorFile = file })
            }
            if (file.isFile) {
                add(ActionItem("📤  $shareSendLabel") {
                    val plat = matchedGame?.let(platformFor)
                    shareMethodTarget = ShareFileTarget(
                        files = listOf(file),
                        gameTitle = matchedGame?.title ?: file.nameWithoutExtension,
                        platformId = plat?.id.orEmpty(),
                        platformName = plat?.shortName.orEmpty(),
                    )
                })
            }
            add(
                ActionItem("📝  $renameFileLabel") {
                    nameSpec = NameDialogSpec(renameTitle, file.name) { newName ->
                        scope.launch {
                            val res = onRenameFile(file, newName)
                            Toast.makeText(
                                context,
                                if (res.isSuccess) renameDone else renameFailFmt.format(res.exceptionOrNull()?.message.orEmpty()),
                                Toast.LENGTH_SHORT,
                            ).show()
                            dirRefreshTick++
                        }
                    }
                },
            )
            add(ActionItem("📦  $moveFileLabel") { moveTarget = MoveTarget.Single(file, isCopy = false) })
            add(ActionItem("📋  $copyFileLabel") { moveTarget = MoveTarget.Single(file, isCopy = true) })
            file.parentFile?.let { parent ->
                add(ActionItem("🗜  $compressZipLabel") {
                    runCompressZip(listOf(file), parent, "${file.nameWithoutExtension}.zip")
                })
            }
            if (currentDir != null) {
                add(ActionItem("☑  $addMultiSelectLabel") {
                    multiSelectMode = true
                    selectedPaths = selectedPaths + file.path
                })
            }
            add(ActionItem("🗑  $deleteFileLabel") { deleteConfirm = file to matchedGame })
        }
        actionMenu = ActionMenuState(file.name, items)
    }

    fun openBatchMenu() {
        val picked = fileEntries.filter { it.path in selectedPaths }
        if (picked.isEmpty()) return
        val shareable = ArchiveManager.collectShareableFiles(picked)
        val archives = picked.filter { ArchiveManager.isArchive(it) }
        val dirNow = currentDir ?: picked.first().parentFile
        val items = buildList {
            if (shareable.isNotEmpty()) {
                add(ActionItem("📤  ${shareBatchLabel.format(shareable.size)}") {
                    shareMethodTarget = ShareFileTarget(
                        files = shareable,
                        gameTitle = "${shareable.size} files",
                        platformId = "",
                        platformName = "",
                    )
                })
            }
            add(ActionItem("📦  ${batchMoveFmt.format(picked.size)}") { moveTarget = MoveTarget.Batch(picked, isCopy = false) })
            add(ActionItem("📋  ${batchCopyFmt.format(picked.size)}") { moveTarget = MoveTarget.Batch(picked, isCopy = true) })
            if (dirNow != null) {
                add(ActionItem("🗜  $compressZipLabel") {
                    runCompressZip(picked, dirNow, "archive_${picked.size}.zip")
                })
            }
            if (archives.isNotEmpty() && dirNow != null) {
                add(ActionItem("📂  $batchExtractLabel (${archives.size})") {
                    opCancelled.set(false)
                    opProgress = extractingTitle to archives.first().name
                    opJob = scope.launch {
                        var totalFiles = 0
                        var firstErr: String? = null
                        try {
                            for (arc in archives) {
                                if (opCancelled.get()) break
                                val res = onExtractArchive(
                                    arc,
                                    dirNow,
                                    { _, name, _ -> if (!opCancelled.get()) opProgress = extractingTitle to "${arc.name} · $name" },
                                    { opCancelled.get() },
                                )
                                if (res.isSuccess) {
                                    totalFiles += res.getOrDefault(0)
                                } else if (firstErr == null) {
                                    firstErr = res.exceptionOrNull()?.message
                                }
                            }
                            if (!opCancelled.get()) {
                                if (firstErr != null && totalFiles == 0) {
                                    Toast.makeText(context, archiveFailFmt.format(firstErr), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, extractDoneFmt.format(totalFiles), Toast.LENGTH_SHORT).show()
                                }
                                multiSelectMode = false
                                selectedPaths = emptySet()
                                dirRefreshTick++
                                onRescan()
                            }
                        } finally {
                            opProgress = null
                            opJob = null
                        }
                    }
                })
            }
            add(ActionItem("🗑  ${batchDeleteFmt.format(picked.size)}") { batchDeleteFiles = picked })
            add(ActionItem("☑  $selectAllLabel") { selectedPaths = fileEntries.map { it.path }.toSet() })
            add(ActionItem("✕  $exitMultiLabel") {
                multiSelectMode = false
                selectedPaths = emptySet()
            })
        }
        actionMenu = ActionMenuState(batchActionsFmt.format(picked.size), items)
    }

    // Files Toolbar 动作列表（确保全键盘/手柄可达）
    val filesToolbarActions: List<Pair<String, () -> Unit>> = if (!multiSelectMode) {
        buildList {
            add("🔍" to {
                if (fileSearchOpen) {
                    fileSearchOpen = false
                    fileSearchQuery = ""
                    selectedIdx = 0
                } else {
                    fileSearchOpen = true
                    fileSearchChipIdx = 0
                }
            })
            if (currentDir != null) {
                add("💾 $storageLocationsLabel" to {
                    pendingFocusPath = currentDir?.path
                    fileSearchOpen = false
                    fileSearchQuery = ""
                    currentDir = null
                    filesToolbarIdx = 0
                })
                add(stringResource(R.string.lib_new_folder) to ::openNewFolderDialog)
                if (fileEntries.isNotEmpty()) {
                    add("☑ ${stringResource(R.string.lib_multi_select)}" to {
                        multiSelectMode = true
                        selectedPaths = emptySet()
                        filesToolbarIdx = 0
                    })
                }
            }
        }
    } else if (currentDir == null) {
        emptyList()
    } else {
        buildList {
            val allChecked = fileEntries.isNotEmpty() && selectedPaths.size == fileEntries.size
            add((if (allChecked) stringResource(R.string.lib_select_clear) else selectAllLabel) to {
                selectedPaths = if (allChecked) emptySet() else fileEntries.map { it.path }.toSet()
            })
            if (selectedPaths.isNotEmpty()) {
                add(stringResource(R.string.lib_batch_actions, selectedPaths.size) to ::openBatchMenu)
            }
            add(exitMultiLabel to {
                multiSelectMode = false
                selectedPaths = emptySet()
                filesToolbarIdx = 0
            })
        }
    }

    val totalFilterChips = 6 + allPlatforms.size

    fun triggerHeader(idx: Int) {
        when (idx) {
            0 -> onDismiss()
            1 -> showLanDialog = true
            2 -> {
                mode = LibraryMode.Games
                selectedIdx = 0
                focusZone = LibraryZone.List
            }
            3 -> {
                mode = LibraryMode.Files
                selectedIdx = 0
                focusZone = LibraryZone.List
            }
        }
    }

    fun triggerFilterChip(idx: Int) {
        when (idx) {
            0 -> {
                if (gameSearchOpen) {
                    gameSearchOpen = false
                    searchQuery = ""
                } else {
                    gameSearchOpen = true
                }
                selectedIdx = 0
            }
            1 -> { gamesFilter = GamesFilter.All; selectedIdx = 0 }
            2 -> { gamesFilter = GamesFilter.Visible; selectedIdx = 0 }
            3 -> { gamesFilter = GamesFilter.Hidden; selectedIdx = 0 }
            4 -> { gamesFilter = GamesFilter.Uncategorized; selectedIdx = 0 }
            5 -> { selectedPlatformId = null; selectedIdx = 0 }
            else -> allPlatforms.getOrNull(idx - 6)?.let { p ->
                selectedPlatformId = if (selectedPlatformId == p.id) null else p.id
                selectedIdx = 0
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(DeckColors.Background)
            .focusRequester(focusRequester)
            .focusProperties { canFocus = !overlayActive || actionMenu != null }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                nameSpec?.let { spec ->
                    return@onPreviewKeyEvent when {
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                            nameSpec = null; true
                        }
                        GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                            spec.onConfirm(nameState?.value?.text.orEmpty()); nameSpec = null; true
                        }
                        else -> false
                    }
                }

                actionMenu?.let { m ->
                    when {
                        event.key == Key.DirectionUp -> m.move(-1)
                        event.key == Key.DirectionDown -> m.move(1)
                        GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                            val item = m.items.getOrNull(m.index)
                            actionMenu = null
                            item?.action?.invoke()
                        }
                        GamepadKeys.isBack(event.key) ||
                            GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) ||
                            event.key == Key.DirectionLeft -> actionMenu = null
                    }
                    return@onPreviewKeyEvent true
                }

                if (propertiesGame != null || editingGame != null || categoryTargetGame != null ||
                    moveTarget != null || deleteConfirm != null || batchDeleteFiles != null ||
                    showLanDialog || shareMethodTarget != null || shareTarget != null ||
                    archivePreviewFile != null || imageViewerFile != null || textEditorFile != null ||
                    audioPlayerFile != null || fontViewerFile != null || opProgress != null
                ) {
                    return@onPreviewKeyEvent false
                }

                if (searchFieldFocused || fileSearchFieldFocused) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Escape, Key.Back -> {
                            focusRequester.requestFocus()
                            focusZone = LibraryZone.Search
                            true
                        }
                        Key.DirectionDown -> {
                            focusRequester.requestFocus()
                            focusZone = if (searchFieldFocused) LibraryZone.Filter else LibraryZone.List
                            true
                        }
                        else -> false
                    }
                }

                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        if (mode == LibraryMode.Games && searchQuery.isNotBlank()) {
                            searchQuery = ""
                            selectedIdx = 0
                            true
                        } else if (mode == LibraryMode.Games && gameSearchOpen) {
                            gameSearchOpen = false
                            selectedIdx = 0
                            true
                        } else if (mode == LibraryMode.Files && multiSelectMode) {
                            multiSelectMode = false
                            selectedPaths = emptySet()
                            true
                        } else if (mode == LibraryMode.Files && fileSearchQuery.isNotBlank()) {
                            fileSearchQuery = ""
                            selectedIdx = 0
                            true
                        } else if (mode == LibraryMode.Files && fileSearchOpen) {
                            fileSearchOpen = false
                            selectedIdx = 0
                            true
                        } else if (mode == LibraryMode.Files && stepUpDir()) {
                            true
                        } else {
                            onDismiss()
                            true
                        }
                    }
                    GamepadKeys.isPrevTab(event.key, swapAbxy, keyBindings) -> {
                        if (mode == LibraryMode.Games) {
                            val all = GamesFilter.entries
                            gamesFilter = all[(all.indexOf(gamesFilter) - 1 + all.size) % all.size]
                            selectedIdx = 0
                        } else {
                            mode = LibraryMode.Games
                            focusZone = LibraryZone.List
                        }
                        true
                    }
                    GamepadKeys.isNextTab(event.key, swapAbxy, keyBindings) -> {
                        if (mode == LibraryMode.Games) {
                            val all = GamesFilter.entries
                            gamesFilter = all[(all.indexOf(gamesFilter) + 1) % all.size]
                            selectedIdx = 0
                        } else {
                            mode = LibraryMode.Files
                            focusZone = LibraryZone.List
                        }
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        when (focusZone) {
                            LibraryZone.Header -> headerIdx = (headerIdx - 1).coerceAtLeast(0)
                            LibraryZone.Filter -> filterChipIdx = (filterChipIdx - 1).coerceAtLeast(0)
                            LibraryZone.FilesToolbar -> filesToolbarIdx = (filesToolbarIdx - 1).coerceAtLeast(0)
                            LibraryZone.Search -> {
                                if (mode == LibraryMode.Files) {
                                    fileSearchChipIdx = (fileSearchChipIdx - 1).coerceAtLeast(0)
                                }
                            }
                            LibraryZone.List -> Unit
                        }
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        when (focusZone) {
                            LibraryZone.Header -> headerIdx = (headerIdx + 1).coerceAtMost(3)
                            LibraryZone.Filter -> filterChipIdx = (filterChipIdx + 1).coerceAtMost(totalFilterChips - 1)
                            LibraryZone.FilesToolbar -> {
                                if (filesToolbarActions.isNotEmpty()) {
                                    filesToolbarIdx = (filesToolbarIdx + 1).coerceAtMost(filesToolbarActions.lastIndex)
                                }
                            }
                            LibraryZone.Search -> {
                                if (mode == LibraryMode.Files) {
                                    fileSearchChipIdx = (fileSearchChipIdx + 1).coerceAtMost(2)
                                }
                            }
                            LibraryZone.List -> Unit
                        }
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        when (focusZone) {
                            LibraryZone.Header -> Unit
                            LibraryZone.Search -> {
                                focusZone = if (mode == LibraryMode.Files && filesToolbarActions.isNotEmpty()) {
                                    filesToolbarIdx = filesToolbarIdx.coerceIn(0, filesToolbarActions.lastIndex)
                                    LibraryZone.FilesToolbar
                                } else {
                                    LibraryZone.Header
                                }
                            }
                            LibraryZone.Filter -> focusZone = if (gameSearchOpen || searchQuery.isNotBlank()) {
                                LibraryZone.Search
                            } else {
                                LibraryZone.Header
                            }
                            LibraryZone.FilesToolbar -> focusZone = LibraryZone.Header
                            LibraryZone.List -> {
                                if (selectedIdx > 0) {
                                    selectedIdx--
                                } else {
                                    focusZone = when {
                                        mode == LibraryMode.Games -> LibraryZone.Filter
                                        fileSearchOpen -> LibraryZone.Search
                                        filesToolbarActions.isNotEmpty() -> {
                                            filesToolbarIdx = filesToolbarIdx.coerceIn(0, filesToolbarActions.lastIndex)
                                            LibraryZone.FilesToolbar
                                        }
                                        else -> LibraryZone.Header
                                    }
                                }
                            }
                        }
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        when (focusZone) {
                            LibraryZone.Header -> {
                                focusZone = when {
                                    mode == LibraryMode.Games -> if (gameSearchOpen || searchQuery.isNotBlank()) {
                                        LibraryZone.Search
                                    } else {
                                        LibraryZone.Filter
                                    }
                                    filesToolbarActions.isNotEmpty() -> {
                                        filesToolbarIdx = filesToolbarIdx.coerceIn(0, filesToolbarActions.lastIndex)
                                        LibraryZone.FilesToolbar
                                    }
                                    fileSearchOpen -> LibraryZone.Search
                                    else -> LibraryZone.List
                                }
                            }
                            LibraryZone.FilesToolbar -> {
                                if (fileSearchOpen) {
                                    focusZone = LibraryZone.Search
                                } else if (activeItemCount > 0) {
                                    focusZone = LibraryZone.List
                                    selectedIdx = selectedIdx.coerceIn(0, activeItemCount - 1)
                                }
                            }
                            LibraryZone.Search -> {
                                if (mode == LibraryMode.Games) {
                                    focusZone = LibraryZone.Filter
                                } else if (activeItemCount > 0) {
                                    focusZone = LibraryZone.List
                                    selectedIdx = selectedIdx.coerceIn(0, activeItemCount - 1)
                                }
                            }
                            LibraryZone.Filter -> {
                                if (activeItemCount > 0) {
                                    focusZone = LibraryZone.List
                                    selectedIdx = selectedIdx.coerceIn(0, activeItemCount - 1)
                                }
                            }
                            LibraryZone.List -> {
                                if (selectedIdx < activeItemCount - 1) selectedIdx++
                            }
                        }
                        true
                    }
                    GamepadKeys.isMenu(event.key, swapAbxy, keyBindings) -> {
                        if (mode == LibraryMode.Games) {
                            filteredGames.getOrNull(selectedIdx)?.let { propertiesGame = it }
                        } else if (focusZone == LibraryZone.List) {
                            if (multiSelectMode && selectedPaths.isNotEmpty()) {
                                openBatchMenu()
                            } else {
                                val fIdx = if (hasUp) selectedIdx - 1 else selectedIdx
                                if (fIdx >= 0) {
                                    fileEntries.getOrNull(fIdx)?.let { f ->
                                        if (f.isDirectory) openFolderMenu(f) else openFileMenu(f)
                                    }
                                }
                            }
                        }
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        when (focusZone) {
                            LibraryZone.Header -> triggerHeader(headerIdx)
                            LibraryZone.Search -> {
                                if (mode == LibraryMode.Games) {
                                    searchFieldFr.requestFocus()
                                } else {
                                    when (fileSearchChipIdx) {
                                        0 -> fileSearchFieldFr.requestFocus()
                                        1 -> {
                                            fileSearchRecursive = false
                                            selectedIdx = 0
                                        }
                                        else -> {
                                            fileSearchRecursive = true
                                            selectedIdx = 0
                                        }
                                    }
                                }
                            }
                            LibraryZone.Filter -> triggerFilterChip(filterChipIdx)
                            LibraryZone.FilesToolbar -> filesToolbarActions.getOrNull(filesToolbarIdx)?.second?.invoke()
                            LibraryZone.List -> {
                                if (mode == LibraryMode.Games) {
                                    filteredGames.getOrNull(selectedIdx)?.let(::openGameMenu)
                                } else {
                                    if (hasUp && selectedIdx == 0) {
                                        stepUpDir()
                                    } else {
                                        val fIdx = if (hasUp) selectedIdx - 1 else selectedIdx
                                        fileEntries.getOrNull(fIdx)?.let { f ->
                                            if (multiSelectMode) {
                                                if (currentDir != null) {
                                                    selectedPaths = if (f.path in selectedPaths) {
                                                        selectedPaths - f.path
                                                    } else {
                                                        selectedPaths + f.path
                                                    }
                                                }
                                            } else if (f.isDirectory) {
                                                enterDir(f)
                                            } else {
                                                openFileMenu(f)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶部标题栏
            val backTint = DeckColors.TextPrimary
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val backHl = focusZone == LibraryZone.Header && headerIdx == 0
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (backHl) DeckColors.Ruby.copy(alpha = 0.14f) else Color.Transparent)
                        .border(if (backHl) 1.5.dp else 0.dp, if (backHl) DeckColors.Ruby else Color.Transparent, RoundedCornerShape(8.dp))
                        .soundClickable(onClick = onDismiss)
                        .drawBehind { drawBackArrow(backTint) },
                )
                Spacer(Modifier.width(10.dp))
                GameLibraryAppIcon(size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.game_library_title),
                        fontFamily = DeckFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = DeckColors.TextPrimary,
                    )
                    Text(
                        stringResource(R.string.game_library_stats, allGames.size, hiddenCount),
                        style = DeckType.metaLabel,
                        color = DeckColors.TextSecondary,
                    )
                }

                // 局域网服务与任务进度入口（有任意任务时以半透明进度条填充，取最慢任务）
                val taskProgress = lanState.slowestActiveTaskProgress
                val hasActiveTasks = taskProgress != null
                val lanActive = lanState.anyRunning || hasActiveTasks
                val lanAccent = when {
                    hasActiveTasks -> Color(0xFF0EA5E9)
                    lanState.shareRunning -> Color(0xFF10B981)
                    else -> Color(0xFFF59E0B)
                }
                val lanHl = focusZone == LibraryZone.Header && headerIdx == 1
                val animTaskProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = (taskProgress ?: 0f).coerceIn(0f, 1f),
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                    label = "lib_lan_task_progress",
                )
                Row(
                    Modifier
                        .clip(DeckShapes.tabPill)
                        .background(
                            when {
                                hasActiveTasks -> lanAccent.copy(alpha = 0.12f)
                                lanActive -> lanAccent.copy(alpha = 0.16f)
                                lanHl -> DeckColors.Ruby.copy(alpha = 0.12f)
                                else -> DeckColors.Surface
                            },
                        )
                        .drawBehind {
                            if (hasActiveTasks && animTaskProgress > 0f) {
                                drawRect(
                                    color = lanAccent.copy(alpha = 0.28f),
                                    size = androidx.compose.ui.geometry.Size(size.width * animTaskProgress, size.height),
                                )
                            }
                        }
                        .border(
                            width = if (lanActive || lanHl) 1.5.dp else 1.dp,
                            color = when {
                                lanHl -> DeckColors.Ruby
                                lanActive -> lanAccent
                                else -> DeckColors.DialogBorder
                            },
                            shape = DeckShapes.tabPill,
                        )
                        .soundClickable { showLanDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (lanActive) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(lanAccent))
                    }
                    Text(
                        text = when {
                            hasActiveTasks -> stringResource(R.string.lan_status_task_pct, (taskProgress.coerceIn(0f, 1f) * 100f).toInt())
                            lanActive -> when {
                                lanState.shareRunning && lanState.webRunning -> stringResource(R.string.lan_status_both)
                                lanState.shareRunning -> stringResource(R.string.lan_status_share)
                                lanState.webRunning -> stringResource(R.string.lan_status_web)
                                else -> stringResource(R.string.lan_status_tasks)
                            }
                            else -> "📡 ${stringResource(R.string.lan_service_btn)}"
                        },
                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                        color = if (lanActive) lanAccent else if (lanHl) DeckColors.Ruby else DeckColors.TextPrimary,
                    )
                }
                Spacer(Modifier.width(10.dp))

                // 模式切换：游戏管理 | 目录文件
                Row(
                    Modifier
                        .clip(DeckShapes.tabPill)
                        .background(DeckColors.Surface)
                        .border(1.dp, DeckColors.DialogBorder, DeckShapes.tabPill)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ModePill(
                        label = stringResource(R.string.lib_mode_games),
                        selected = mode == LibraryMode.Games,
                        focused = focusZone == LibraryZone.Header && headerIdx == 2,
                        onClick = { mode = LibraryMode.Games; selectedIdx = 0; focusZone = LibraryZone.List },
                    )
                    ModePill(
                        label = stringResource(R.string.lib_mode_files),
                        selected = mode == LibraryMode.Files,
                        focused = focusZone == LibraryZone.Header && headerIdx == 3,
                        onClick = { mode = LibraryMode.Files; selectedIdx = 0; focusZone = LibraryZone.List },
                    )
                }
            }

            HorizontalDivider(color = DeckColors.DialogBorder, thickness = 1.dp)

            if (mode == LibraryMode.Games) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (gameSearchOpen || searchQuery.isNotBlank()) {
                        val searchHl = focusZone == LibraryZone.Search || searchFieldFocused
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.Surface)
                                .border(
                                    width = if (searchHl) 1.5.dp else 1.dp,
                                    color = if (searchHl) DeckColors.Ruby else DeckColors.DialogBorder,
                                    shape = DeckShapes.tabPill,
                                )
                                .clickable { searchFieldFr.requestFocus() }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🔍", fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        stringResource(R.string.lib_search_hint),
                                        style = DeckType.settingsLabel,
                                        color = DeckColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it; selectedIdx = 0 },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        focusRequester.requestFocus()
                                        focusZone = LibraryZone.List
                                    }),
                                    textStyle = DeckType.settingsValue.copy(color = DeckColors.TextPrimary),
                                    cursorBrush = SolidColor(DeckColors.Ruby),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFieldFr)
                                        .onFocusChanged { searchFieldFocused = it.isFocused },
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                Text(
                                    "✕",
                                    style = DeckType.settingsLabel,
                                    color = DeckColors.TextSecondary,
                                    modifier = Modifier
                                        .clickable { searchQuery = ""; selectedIdx = 0 }
                                        .padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }

                    // 搜索开关 + 状态筛选 + 平台筛选
                    LazyRow(
                        state = filterRowState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item {
                            FilterChipPill(
                                label = "🔍",
                                selected = gameSearchOpen || searchQuery.isNotBlank(),
                                focused = focusZone == LibraryZone.Filter && filterChipIdx == 0,
                                onClick = {
                                    if (gameSearchOpen) {
                                        gameSearchOpen = false
                                        searchQuery = ""
                                    } else {
                                        gameSearchOpen = true
                                    }
                                    selectedIdx = 0
                                },
                            )
                        }
                        item {
                            FilterChipPill(
                                label = stringResource(R.string.lib_filter_all),
                                selected = gamesFilter == GamesFilter.All,
                                focused = focusZone == LibraryZone.Filter && filterChipIdx == 1,
                                onClick = { gamesFilter = GamesFilter.All; selectedIdx = 0 },
                            )
                        }
                        item {
                            FilterChipPill(
                                label = stringResource(R.string.lib_filter_visible),
                                selected = gamesFilter == GamesFilter.Visible,
                                focused = focusZone == LibraryZone.Filter && filterChipIdx == 2,
                                onClick = { gamesFilter = GamesFilter.Visible; selectedIdx = 0 },
                            )
                        }
                        item {
                            FilterChipPill(
                                label = stringResource(R.string.lib_filter_hidden, hiddenCount),
                                selected = gamesFilter == GamesFilter.Hidden,
                                focused = focusZone == LibraryZone.Filter && filterChipIdx == 3,
                                onClick = { gamesFilter = GamesFilter.Hidden; selectedIdx = 0 },
                            )
                        }
                        item {
                            FilterChipPill(
                                label = stringResource(R.string.lib_filter_uncategorized),
                                selected = gamesFilter == GamesFilter.Uncategorized,
                                focused = focusZone == LibraryZone.Filter && filterChipIdx == 4,
                                onClick = { gamesFilter = GamesFilter.Uncategorized; selectedIdx = 0 },
                            )
                        }
                        item {
                            Box(Modifier.width(1.dp).height(18.dp).background(DeckColors.DialogBorder))
                        }
                        item {
                            FilterChipPill(
                                label = stringResource(R.string.lib_platform_all),
                                selected = selectedPlatformId == null,
                                focused = focusZone == LibraryZone.Filter && filterChipIdx == 5,
                                onClick = { selectedPlatformId = null; selectedIdx = 0 },
                            )
                        }
                        itemsIndexed(allPlatforms, key = { _, p -> p.id }) { pIdx, plat ->
                            FilterChipPill(
                                label = plat.shortName,
                                selected = selectedPlatformId == plat.id,
                                focused = focusZone == LibraryZone.Filter && filterChipIdx == 6 + pIdx,
                                onClick = {
                                    selectedPlatformId = if (selectedPlatformId == plat.id) null else plat.id
                                    selectedIdx = 0
                                },
                            )
                        }
                    }
                }

                if (filteredGames.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.lib_empty_games),
                            style = DeckType.emptyMessage,
                            color = DeckColors.TextSecondary,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(filteredGames, key = { _, g -> g.id }) { idx, game ->
                            val plat = platformFor(game)
                            val isHidden = game.id in hiddenIds
                            val isFav = game.id in favorites
                            val isUncat = game.platformId !in categorizedPlatforms && game.id !in categorizedGames
                            val hl = focusZone == LibraryZone.List && idx == selectedIdx

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (hl) DeckColors.Ruby.copy(alpha = 0.08f) else DeckColors.Surface)
                                    .border(
                                        width = if (hl) 1.5.dp else 1.dp,
                                        color = if (hl) DeckColors.Ruby else DeckColors.DialogBorder,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .soundClickable {
                                        focusZone = LibraryZone.List
                                        selectedIdx = idx
                                        openGameMenu(game)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val h = 44.dp
                                val w = (h * plat.coverAspect).coerceIn(32.dp, 62.dp)
                                GameCoverCard(
                                    game = game,
                                    platform = plat,
                                    covers = covers,
                                    officialName = officialNames[game.id],
                                    width = w,
                                    height = h,
                                    isFocused = false,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = game.title,
                                            style = DeckType.settingsValue.copy(fontWeight = FontWeight.Medium),
                                            color = if (isHidden) DeckColors.TextSecondary else DeckColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (isFav) FavoriteHeartIcon(filled = true, color = DeckColors.Ruby, size = 14.dp)
                                        if (isHidden) TagBadge(stringResource(R.string.lib_badge_hidden), accent = true)
                                        if (isUncat) TagBadge(stringResource(R.string.lib_badge_uncategorized), accent = false)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    val sizeTag = formatCompactSize(game.fileSize)
                                    Text(
                                        text = listOfNotNull(plat.shortName, sizeTag, game.fileName).joinToString(" · "),
                                        style = DeckType.metaLabel,
                                        color = DeckColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (isHidden) {
                                    Text(
                                        stringResource(R.string.lib_action_unhide),
                                        style = DeckType.settingsLabel,
                                        color = DeckColors.Ruby,
                                        modifier = Modifier
                                            .clip(DeckShapes.tabPill)
                                            .background(DeckColors.Ruby.copy(alpha = 0.12f))
                                            .soundClickable { onUnhideGame(game.id) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    "···",
                                    style = DeckType.settingsValue,
                                    color = DeckColors.TextSecondary,
                                    modifier = Modifier
                                        .clip(DeckShapes.tabPill)
                                        .background(DeckColors.Background)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                // 目录文件管理视图
                val sdPath = remember { Environment.getExternalStorageDirectory().path }
                val dirNow = currentDir
                val pathDisplay = if (multiSelectMode) {
                    stringResource(R.string.lib_selected_count, selectedPaths.size)
                } else {
                    dirNow?.path?.replace(sdPath, "~") ?: stringResource(R.string.lib_storage_locations)
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        pathDisplay,
                        style = DeckType.settingsLabel.copy(
                            fontWeight = if (multiSelectMode) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (multiSelectMode) DeckColors.Ruby else DeckColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    filesToolbarActions.forEachIndexed { tIdx, (label, onClick) ->
                        val tHl = focusZone == LibraryZone.FilesToolbar && filesToolbarIdx == tIdx
                        Text(
                            label,
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = DeckColors.Ruby,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.Ruby.copy(alpha = if (tHl) 0.22f else 0.10f))
                                .border(
                                    width = if (tHl) 1.5.dp else 0.dp,
                                    color = if (tHl) DeckColors.Ruby else Color.Transparent,
                                    shape = DeckShapes.tabPill,
                                )
                                .soundClickable(onClick = onClick)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                if (fileSearchOpen || fileSearchQuery.isNotBlank()) {
                    val fileSearchBoxHl = (focusZone == LibraryZone.Search && fileSearchChipIdx == 0) || fileSearchFieldFocused
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.Surface)
                                .border(
                                    width = if (fileSearchBoxHl) 1.5.dp else 1.dp,
                                    color = if (fileSearchBoxHl) DeckColors.Ruby else DeckColors.DialogBorder,
                                    shape = DeckShapes.tabPill,
                                )
                                .clickable {
                                    focusZone = LibraryZone.Search
                                    fileSearchChipIdx = 0
                                    fileSearchFieldFr.requestFocus()
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🔍", fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (fileSearchQuery.isEmpty()) {
                                    Text(
                                        stringResource(
                                            if (fileSearchRecursive) R.string.lib_files_search_recursive_hint
                                            else R.string.lib_files_search_hint,
                                        ),
                                        style = DeckType.settingsLabel,
                                        color = DeckColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                BasicTextField(
                                    value = fileSearchQuery,
                                    onValueChange = { fileSearchQuery = it; selectedIdx = 0 },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        focusRequester.requestFocus()
                                        focusZone = LibraryZone.List
                                    }),
                                    textStyle = DeckType.settingsValue.copy(color = DeckColors.TextPrimary),
                                    cursorBrush = SolidColor(DeckColors.Ruby),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(fileSearchFieldFr)
                                        .onFocusChanged { fileSearchFieldFocused = it.isFocused },
                                )
                            }
                            if (fileSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 1.8.dp,
                                    color = DeckColors.Ruby,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                "✕",
                                style = DeckType.settingsLabel,
                                color = DeckColors.TextSecondary,
                                modifier = Modifier
                                    .clickable {
                                        if (fileSearchQuery.isNotEmpty()) {
                                            fileSearchQuery = ""
                                        } else {
                                            fileSearchOpen = false
                                        }
                                        selectedIdx = 0
                                    }
                                    .padding(horizontal = 4.dp),
                            )
                        }

                        FilterChipPill(
                            label = stringResource(R.string.lib_files_search_current),
                            selected = !fileSearchRecursive,
                            focused = focusZone == LibraryZone.Search && fileSearchChipIdx == 1,
                            onClick = {
                                fileSearchRecursive = false
                                selectedIdx = 0
                            },
                        )
                        FilterChipPill(
                            label = stringResource(R.string.lib_files_search_multi),
                            selected = fileSearchRecursive,
                            focused = focusZone == LibraryZone.Search && fileSearchChipIdx == 2,
                            onClick = {
                                fileSearchRecursive = true
                                selectedIdx = 0
                            },
                        )
                    }
                }

                if (fileTasks.isNotEmpty()) {
                    FileTasksBanner(
                        tasks = fileTasks,
                        onCancel = onCancelFileTask,
                    )
                }

                if (!hasUp && fileEntries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                when {
                                    fileSearching -> R.string.scrape_loading
                                    fileSearchQuery.isNotBlank() -> R.string.lib_files_search_empty
                                    else -> R.string.lib_files_empty
                                },
                            ),
                            style = DeckType.emptyMessage,
                            color = DeckColors.TextSecondary,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (hasUp) {
                            item(key = "__up__") {
                                val hl = focusZone == LibraryZone.List && selectedIdx == 0
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (hl) DeckColors.Ruby.copy(alpha = 0.08f) else DeckColors.Surface)
                                        .border(
                                            width = if (hl) 1.5.dp else 1.dp,
                                            color = if (hl) DeckColors.Ruby else DeckColors.DialogBorder,
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .soundClickable { stepUpDir() }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("↩", style = DeckType.settingsValue, color = DeckColors.Ruby)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.lib_files_up),
                                        style = DeckType.settingsValue,
                                        color = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                                    )
                                }
                            }
                        }

                        itemsIndexed(fileEntries, key = { _, f -> f.path }) { fIdx, file ->
                            val rowIdx = if (hasUp) fIdx + 1 else fIdx
                            val hl = focusZone == LibraryZone.List && rowIdx == selectedIdx
                            val checked = file.path in selectedPaths
                            val matchedGame = fullLibrary?.byId?.get(file.path)?.takeIf { RomScanner.isPotentialRomFile(file, it.platformId) }
                            val storageLoc = if (dirNow == null) storageLocations.firstOrNull { it.dir.path == file.path } else null
                            val isArchive = ArchiveManager.isArchive(file)
                            val isImage = ArchiveManager.isImageFile(file)
                            val isAudio = ArchiveManager.isAudioFile(file)
                            val isFont = ArchiveManager.isFontFile(file)
                            val isText = !file.isDirectory && !isArchive && !isImage && !isAudio && !isFont && ArchiveManager.isKnownTextFile(file)
                            val inSearchMode = fileSearchQuery.isNotBlank()
                            val label = storageLoc?.label ?: if (dirNow == null && !inSearchMode) file.path.replace(sdPath, "~") else file.name
                            val icon = when {
                                storageLoc != null && !storageLoc.isRomRoot -> "💾"
                                file.isDirectory -> "📁"
                                isArchive -> "🗜"
                                isImage -> "🖼"
                                isAudio -> "🎵"
                                isFont -> "🔤"
                                isText -> "📝"
                                else -> "📄"
                            }

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            hl -> DeckColors.Ruby.copy(alpha = 0.10f)
                                            checked -> DeckColors.Ruby.copy(alpha = 0.06f)
                                            else -> DeckColors.Surface
                                        },
                                    )
                                    .border(
                                        width = if (hl || checked) 1.5.dp else 1.dp,
                                        color = if (hl || checked) DeckColors.Ruby else DeckColors.DialogBorder,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .soundClickable {
                                        focusZone = LibraryZone.List
                                        selectedIdx = rowIdx
                                        if (multiSelectMode && dirNow != null) {
                                            selectedPaths = if (checked) selectedPaths - file.path else selectedPaths + file.path
                                        } else if (file.isDirectory) {
                                            enterDir(file)
                                        } else {
                                            openFileMenu(file)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (multiSelectMode && dirNow != null) {
                                    Text(
                                        if (checked) "☑" else "☐",
                                        style = DeckType.menuButton,
                                        color = if (checked) DeckColors.Ruby else DeckColors.TextSecondary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(icon, fontSize = 16.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            label,
                                            style = DeckType.settingsValue,
                                            color = if (hl || checked) DeckColors.Ruby else DeckColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (storageLoc?.badge != null) {
                                            TagBadge(storageLoc.badge, accent = storageLoc.isRomRoot)
                                        }
                                        if (matchedGame != null) {
                                            TagBadge(stringResource(R.string.lib_files_rom_tag), accent = true)
                                            if (matchedGame.id in hiddenIds) {
                                                TagBadge(stringResource(R.string.lib_badge_hidden), accent = false)
                                            }
                                        }
                                    }
                                    val relParent = if (inSearchMode) {
                                        val parent = file.parentFile
                                        when {
                                            parent == null -> null
                                            dirNow == null -> parent.path.replace(sdPath, "~")
                                            parent.path == dirNow.path -> null
                                            parent.path.startsWith("${dirNow.path}${File.separator}") ->
                                                parent.path.removePrefix("${dirNow.path}${File.separator}")
                                            else -> parent.path.replace(sdPath, "~")
                                        }
                                    } else null

                                    if (storageLoc != null && !inSearchMode) {
                                        val freeStr = remember(file.path, filesRevision, dirRefreshTick) {
                                            formatCompactSize(file.usableSpace)
                                        }
                                        val sub = listOfNotNull(
                                            file.path.replace(sdPath, "~"),
                                            freeStr?.let { stringResource(R.string.lib_storage_free_fmt, it) },
                                        ).joinToString(" · ")
                                        Text(sub, style = DeckType.metaLabel, color = DeckColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    } else if (!file.isDirectory) {
                                        val sizeStr = remember(file.path, filesRevision, dirRefreshTick) {
                                            formatCompactSize(file.length())
                                        }
                                        val sub = listOfNotNull(relParent, sizeStr).joinToString(" · ")
                                        if (sub.isNotEmpty()) {
                                            Text(sub, style = DeckType.metaLabel, color = DeckColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    } else if (!relParent.isNullOrBlank()) {
                                        Text(relParent, style = DeckType.metaLabel, color = DeckColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (file.isDirectory && !multiSelectMode) {
                                    if (dirNow != null) {
                                        Text(
                                            "···",
                                            style = DeckType.settingsValue,
                                            color = DeckColors.TextSecondary,
                                            modifier = Modifier
                                                .clip(DeckShapes.tabPill)
                                                .background(DeckColors.Background)
                                                .soundClickable {
                                                    selectedIdx = rowIdx
                                                    openFolderMenu(file)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 3.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("›", style = DeckType.settingsSection, color = DeckColors.TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 操作菜单
        actionMenu?.let { m ->
            ActionMenuDialog(
                state = m,
                onPick = { item -> actionMenu = null; item.action() },
                onDismiss = { actionMenu = null },
            )
        }

        // 游戏属性弹窗
        propertiesGame?.let { game ->
            val gameCats = categories.filter { game.id in it.games || game.platformId in it.sources }.map { it.name }
            GamePropertiesDialog(
                game = game,
                platform = platformFor(game),
                metadata = metadataMap[game.id],
                officialName = officialNames[game.id],
                metaInfo = metaResolutionMap[game.id],
                coverInfo = coverResolutionMap[game.id] ?: covers.resolveCoverInfo(game),
                isFavorite = game.id in favorites,
                categoryNames = gameCats,
                effectiveLauncherId = effectiveLauncherFor(game),
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                computeCrc32 = computeCrc32,
                onRefreshMetadata = { onRefreshMetadata(game) },
                onLocateFile = {
                    val f = File(game.id)
                    val dir = f.parentFile
                    if (dir != null && dir.isDirectory) {
                        propertiesGame = null
                        fileSearchQuery = ""
                        fileSearchOpen = false
                        mode = LibraryMode.Files
                        headerIdx = 3
                        focusZone = LibraryZone.List
                        currentDir = dir
                        pendingFocusPath = f.absolutePath
                        dirRefreshTick++
                    }
                },
                onDismiss = { propertiesGame = null },
            )
        }

        // 编辑游戏页面
        editingGame?.let { game ->
            EditGameScreen(
                game = game,
                platform = platformFor(game),
                covers = covers,
                officialName = officialNames[game.id],
                customLauncherId = gameLaunchers[game.id],
                onSetGameLauncher = { id -> onSetGameLauncher(game.id, id) },
                categories = categories,
                onSetGameCategories = { ids -> onSetGameCategories(game.id, ids) },
                onCreateCategory = onCreateCategoryForGame?.let { create -> { name -> create(name, game.id) } },
                onDismiss = { editingGame = null },
                onSave = { name, coverPath ->
                    onSaveGameEdits(game.id, name, coverPath)
                    editingGame = null
                },
                onHide = {
                    onHideGame(game.id)
                    editingGame = null
                },
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onDeleteFile = {
                    val f = File(game.id)
                    scope.launch {
                        val res = onDeleteFile(f)
                        Toast.makeText(
                            context,
                            if (res.isSuccess) deleteDone else deleteFailFmt.format(res.exceptionOrNull()?.message.orEmpty()),
                            Toast.LENGTH_SHORT,
                        ).show()
                        editingGame = null
                        dirRefreshTick++
                    }
                },
                onLocateFile = {
                    val f = File(game.id)
                    val dir = f.parentFile
                    if (dir != null && dir.isDirectory) {
                        editingGame = null
                        fileSearchQuery = ""
                        fileSearchOpen = false
                        mode = LibraryMode.Files
                        headerIdx = 3
                        focusZone = LibraryZone.List
                        currentDir = dir
                        pendingFocusPath = f.absolutePath
                        dirRefreshTick++
                    }
                },
            )
        }

        // 单游戏所属分类弹窗
        categoryTargetGame?.let { game ->
            GameCategoryPickerDialog(
                game = game,
                categories = categories,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onCreateCategory = onCreateCategoryForGame?.let { create -> { name -> create(name, game.id) } },
                onConfirm = { pickedIds ->
                    onSetGameCategories(game.id, pickedIds)
                    categoryTargetGame = null
                },
                onDismiss = { categoryTargetGame = null },
            )
        }

        // 重命名 / 新建文件夹 / 压缩包命名弹窗
        nameSpec?.let { spec ->
            val s = nameState ?: return@let
            NameDialog(
                spec = spec,
                state = s,
                onConfirm = { spec.onConfirm(s.value.text); nameSpec = null },
                onDismiss = { nameSpec = null },
            )
        }

        // 移动 / 复制到目标目录 / 解压到指定目录弹窗
        moveTarget?.let { mt ->
            val initialDir = when (mt) {
                is MoveTarget.Single -> if (mt.isCopy) romRoots.firstOrNull() ?: mt.file.parentFile else mt.file.parentFile
                is MoveTarget.Batch -> if (mt.isCopy) romRoots.firstOrNull() ?: mt.files.firstOrNull()?.parentFile else mt.files.firstOrNull()?.parentFile
                is MoveTarget.ExtractArchive -> mt.archive.parentFile
            }
            val isCopyOp = when (mt) {
                is MoveTarget.Single -> mt.isCopy
                is MoveTarget.Batch -> mt.isCopy
                is MoveTarget.ExtractArchive -> false
            }
            val pickerRoots = remember(storageLocations, romRoots) {
                storageLocations.map { it.dir }.ifEmpty { romRoots }
            }
            MoveFolderDialog(
                fileName = mt.label,
                roots = pickerRoots,
                storageLocations = storageLocations,
                initialDir = initialDir,
                isCopy = isCopyOp,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onPickDir = { targetDir ->
                    moveTarget = null
                    when (mt) {
                        is MoveTarget.Single -> scope.launch {
                            val res = if (mt.isCopy) onCopyFile(mt.file, targetDir) else onMoveFile(mt.file, targetDir)
                            val okMsg = if (mt.isCopy) copyDone else moveDone
                            val failFmt = if (mt.isCopy) copyFailFmt else moveFailFmt
                            Toast.makeText(
                                context,
                                if (res.isSuccess) okMsg else failFmt.format(res.exceptionOrNull()?.message.orEmpty()),
                                Toast.LENGTH_SHORT,
                            ).show()
                            dirRefreshTick++
                            onRescan()
                        }
                        is MoveTarget.Batch -> scope.launch {
                            val res = if (mt.isCopy) onCopyEntries(mt.files, targetDir) else onMoveEntries(mt.files, targetDir)
                            val okMsg = if (mt.isCopy) copyBatchDoneFmt.format(res.getOrDefault(mt.files.size)) else batchDoneFmt.format(res.getOrDefault(mt.files.size))
                            val failFmt = if (mt.isCopy) copyFailFmt else moveFailFmt
                            Toast.makeText(
                                context,
                                if (res.isSuccess) okMsg else failFmt.format(res.exceptionOrNull()?.message.orEmpty()),
                                Toast.LENGTH_SHORT,
                            ).show()
                            multiSelectMode = false
                            selectedPaths = emptySet()
                            dirRefreshTick++
                            onRescan()
                        }
                        is MoveTarget.ExtractArchive -> runExtract(mt.archive, targetDir)
                    }
                },
                onDismiss = { moveTarget = null },
            )
        }

        // 删除单个文件二次警告确认框
        deleteConfirm?.let { (file, matchedGame) ->
            DeleteFileConfirmDialog(
                fileName = file.name,
                filePath = file.path,
                onUseHide = if (matchedGame != null) {
                    {
                        deleteConfirm = null
                        onHideGame(matchedGame.id)
                    }
                } else null,
                onConfirmDelete = {
                    deleteConfirm = null
                    scope.launch {
                        val res = onDeleteFile(file)
                        Toast.makeText(
                            context,
                            if (res.isSuccess) deleteDone else deleteFailFmt.format(res.exceptionOrNull()?.message.orEmpty()),
                            Toast.LENGTH_SHORT,
                        ).show()
                        dirRefreshTick++
                    }
                },
                onDismiss = { deleteConfirm = null },
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
            )
        }

        // 文件夹 / 批量删除二次警告确认框
        batchDeleteFiles?.let { files ->
            BatchDeleteConfirmDialog(
                count = files.size,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onConfirm = {
                    batchDeleteFiles = null
                    scope.launch {
                        val res = onDeleteEntries(files)
                        Toast.makeText(
                            context,
                            if (res.isSuccess) batchDoneFmt.format(res.getOrDefault(files.size))
                            else deleteFailFmt.format(res.exceptionOrNull()?.message.orEmpty()),
                            Toast.LENGTH_SHORT,
                        ).show()
                        multiSelectMode = false
                        selectedPaths = emptySet()
                        dirRefreshTick++
                    }
                },
                onDismiss = { batchDeleteFiles = null },
            )
        }

        // 压缩包内容预览与解压选择
        archivePreviewFile?.let { arc ->
            ArchivePreviewDialog(
                file = arc,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onExtractHere = {
                    archivePreviewFile = null
                    arc.parentFile?.let { runExtract(arc, it) }
                },
                onExtractSubfolder = {
                    archivePreviewFile = null
                    arc.parentFile?.let { parent ->
                        runExtract(arc, File(parent, ArchiveManager.suggestedFolderName(arc)))
                    }
                },
                onExtractPickDir = {
                    archivePreviewFile = null
                    moveTarget = MoveTarget.ExtractArchive(arc)
                },
                onDismiss = { archivePreviewFile = null },
            )
        }

        // 图片查看器
        imageViewerFile?.let { img ->
            ImageViewerDialog(
                file = img,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onDismiss = { imageViewerFile = null },
            )
        }

        // 文本 / 配置文件查看与编辑器
        textEditorFile?.let { txt ->
            TextFileViewerEditorDialog(
                file = txt,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onSaved = { dirRefreshTick++ },
                onDismiss = { textEditorFile = null },
            )
        }

        audioPlayerFile?.let { audio ->
            AudioPlayerDialog(
                file = audio,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onDismiss = { audioPlayerFile = null },
            )
        }

        fontViewerFile?.let { font ->
            FontViewerDialog(
                file = font,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onApplyFontId = onApplyFontId,
                onDismiss = { fontViewerFile = null },
            )
        }

        // 解压 / 压缩进度弹窗
        opProgress?.let { (title, detail) ->
            FileOpProgressDialog(
                title = title,
                detail = detail,
                onCancel = {
                    opCancelled.set(true)
                    opJob?.cancel()
                    opJob = null
                    opProgress = null
                },
            )
        }

        // 局域网服务与快传控制 / 接收页面
        if (showLanDialog) {
            LanServiceControlDialog(
                state = lanState,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onRefreshInterfaces = { lanManager.refreshInterfaces() },
                onSelectInterface = lanManager::selectInterface,
                onRenameDevice = lanManager::setDeviceName,
                onRandomizeDeviceName = lanManager::randomizeDeviceName,
                onToggleQuickShare = { on ->
                    if (on) lanManager.startQuickShare() else lanManager.stopQuickShare()
                },
                onToggleLocalSendCompat = lanManager::setLocalSendCompat,
                onScanPeers = lanManager::scanPeers,
                onToggleWebServer = { on ->
                    if (on) lanManager.startWebServer() else lanManager.stopWebServer()
                },
                onRefreshWebPassword = lanManager::refreshWebPassword,
                onKickWebClient = lanManager::kickWebClient,
                onClearWebHistory = lanManager::clearWebTransferHistory,
                onDeleteWebHistoryItem = lanManager::deleteWebTransferHistoryItem,
                onOpenFolder = { dir, focusFile ->
                    showLanDialog = false
                    fileSearchQuery = ""
                    fileSearchOpen = false
                    mode = LibraryMode.Files
                    headerIdx = 3
                    currentDir = dir
                    pendingFocusPath = focusFile?.absolutePath
                    dirRefreshTick++
                },
                onOpenQuickShareScreen = {
                    showLanDialog = false
                    onOpenQuickShare()
                },
                onAcceptIncoming = { sid, dir -> lanManager.acceptIncoming(sid, dir) },
                onRejectIncoming = { sid -> lanManager.rejectIncoming(sid) },
                onCancelTransfer = lanManager::cancelTransfer,
                onCancelWebTransfer = lanManager::cancelWebTransfer,
                onCancelFileTask = lanManager::cancelFileTask,
                onSubmitPin = lanManager::submitQuickSharePin,
                onCancelPin = lanManager::cancelQuickSharePin,
                onDismissTransferBanner = lanManager::dismissTransferBanner,
                onStopAll = lanManager::stopAll,
                onDismiss = { showLanDialog = false },
            )
        }

        // 发送方式选择（局域网快传 vs 安卓系统/快速分享）
        shareMethodTarget?.let { target ->
            val summary = if (target.files.size > 1) {
                "${target.files.size} 个文件 · ${formatCompactSize(target.totalSize).orEmpty()}"
            } else {
                "${target.gameTitle} · ${target.primaryFile.name}"
            }
            ShareMethodDialog(
                fileSummary = summary,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onPickLanShare = {
                    shareMethodTarget = null
                    shareTarget = target
                },
                onPickSystemShare = {
                    shareMethodTarget = null
                    multiSelectMode = false
                    selectedPaths = emptySet()
                    launchSystemShare(target.files)
                },
                onDismiss = { shareMethodTarget = null },
            )
        }

        // 局域网发送目标设备选择
        shareTarget?.let { target ->
            SharePeerPickerDialog(
                fileName = target.primaryFile.name,
                fileSize = target.totalSize,
                gameTitle = target.gameTitle,
                fileCount = target.files.size,
                state = lanState,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onEnableQuickShare = { lanManager.startQuickShare() },
                onScanPeers = lanManager::scanPeers,
                onPickPeer = { peer ->
                    multiSelectMode = false
                    selectedPaths = emptySet()
                    lanManager.sendFiles(
                        peer = peer,
                        files = target.files,
                        title = target.gameTitle,
                        platformId = target.platformId,
                        platformName = target.platformName,
                    )
                },
                onCancelTransfer = lanManager::cancelTransfer,
                onSubmitPin = lanManager::submitQuickSharePin,
                onCancelPin = lanManager::cancelQuickSharePin,
                onDismissTransferBanner = lanManager::dismissTransferBanner,
                onDismiss = { shareTarget = null },
            )
        }
    }
}

@Composable
private fun ModePill(
    label: String,
    selected: Boolean,
    focused: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = DeckType.settingsLabel.copy(fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal),
        color = if (selected) Color.White else if (focused) DeckColors.Ruby else DeckColors.TextSecondary,
        modifier = Modifier
            .clip(DeckShapes.tabPill)
            .background(if (selected) DeckColors.Ruby else Color.Transparent)
            .border(
                width = if (focused) 1.5.dp else 0.dp,
                color = if (focused) (if (selected) Color.White else DeckColors.Ruby) else Color.Transparent,
                shape = DeckShapes.tabPill,
            )
            .soundClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    focused: Boolean = false,
    accent: Boolean = true,
    onClick: () -> Unit,
) {
    val activeBg = if (accent) DeckColors.Ruby.copy(alpha = 0.14f) else DeckColors.PillSelected
    val activeFg = if (accent) DeckColors.Ruby else DeckColors.PillText
    Text(
        text = label,
        style = DeckType.settingsLabel.copy(fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal),
        color = if (selected || focused) activeFg else DeckColors.TextSecondary,
        modifier = Modifier
            .clip(DeckShapes.tabPill)
            .background(if (selected) activeBg else if (focused) DeckColors.Ruby.copy(alpha = 0.08f) else DeckColors.Surface)
            .border(
                width = if (focused) 1.6.dp else 1.dp,
                color = when {
                    focused -> DeckColors.Ruby
                    selected -> activeFg.copy(alpha = 0.55f)
                    else -> DeckColors.DialogBorder
                },
                shape = DeckShapes.tabPill,
            )
            .soundClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun TagBadge(text: String, accent: Boolean) {
    Text(
        text = text,
        style = DeckType.metaLabel,
        color = if (accent) DeckColors.Ruby else DeckColors.TextSecondary,
        modifier = Modifier
            .clip(DeckShapes.tabPill)
            .background(if (accent) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Background)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun MoveFolderDialog(
    fileName: String,
    roots: List<File>,
    storageLocations: List<StorageLocation> = emptyList(),
    initialDir: File?,
    isCopy: Boolean = false,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onPickDir: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    var curDir by remember { mutableStateOf(initialDir ?: roots.firstOrNull()) }
    var dirs by remember { mutableStateOf<List<File>>(emptyList()) }
    var focusIdx by remember { mutableIntStateOf(0) }
    var pendingSelectPath by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val sdPath = remember { Environment.getExternalStorageDirectory().path }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(curDir) {
        val d = curDir
        dirs = if (d == null) {
            roots
        } else {
            withContext(Dispatchers.IO) {
                d.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                    .orEmpty()
                    .sortedBy { it.name.lowercase() }
            }
        }
        val hdr = if (d != null) 2 else 0
        val p = pendingSelectPath
        if (p != null) {
            val match = dirs.indexOfFirst { it.path == p }
            focusIdx = if (match >= 0) hdr + match else 0
            pendingSelectPath = null
        } else {
            focusIdx = 0
        }
    }

    val headerItems = if (curDir != null) 2 else 0 // 0=move/copy here, 1=up
    val totalFocus = (headerItems + dirs.size).coerceAtLeast(1)

    fun stepUpPicker() {
        val d = curDir ?: return
        pendingSelectPath = d.path
        curDir = if (roots.any { it.path == d.path }) null else d.parentFile
    }

    LaunchedEffect(focusIdx) {
        if (focusIdx in 0 until (headerItems + dirs.size)) {
            listState.animateScrollToItem((focusIdx - 2).coerceAtLeast(0))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(onClick = onDismiss)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        focusIdx = (focusIdx - 1).coerceAtLeast(0)
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        focusIdx = (focusIdx + 1).coerceAtMost(totalFocus - 1)
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        when {
                            curDir != null && focusIdx == 0 -> curDir?.let(onPickDir)
                            curDir != null && focusIdx == 1 -> stepUpPicker()
                            focusIdx < headerItems + dirs.size -> {
                                dirs.getOrNull(focusIdx - headerItems)?.let { curDir = it }
                            }
                        }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier
                .width(460.dp)
                .heightIn(max = 540.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Column(Modifier.padding(end = 36.dp)) {
                Text(
                    stringResource(if (isCopy) R.string.lib_copy_title_fmt else R.string.lib_move_title, fileName),
                    style = DeckType.menuTitle,
                    color = DeckColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    curDir?.path?.replace(sdPath, "~") ?: stringResource(R.string.lib_storage_locations),
                    style = DeckType.metaLabel,
                    color = DeckColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (curDir != null) {
                    item(key = "__move_here__") {
                        val hl = focusIdx == 0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(DeckShapes.settingsDropdown)
                                .background(DeckColors.Ruby.copy(alpha = if (hl) 0.20f else 0.12f))
                                .border(if (hl) 1.5.dp else 0.dp, if (hl) DeckColors.Ruby else Color.Transparent, DeckShapes.settingsDropdown)
                                .soundClickable { curDir?.let(onPickDir) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "✓  ${stringResource(if (isCopy) R.string.lib_copy_here else R.string.lib_move_here)}",
                                style = DeckType.settingsValue,
                                color = DeckColors.Ruby,
                            )
                        }
                    }
                    item(key = "__up__") {
                        val hl = focusIdx == 1
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(DeckShapes.settingsDropdown)
                                .background(if (hl) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
                                .border(if (hl) 1.5.dp else 0.dp, if (hl) DeckColors.Ruby else Color.Transparent, DeckShapes.settingsDropdown)
                                .soundClickable { stepUpPicker() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("↩  ${stringResource(R.string.lib_files_up)}", style = DeckType.settingsValue, color = DeckColors.TextPrimary)
                        }
                    }
                }
                itemsIndexed(dirs, key = { _, d -> d.path }) { idx, dir ->
                    val storageLoc = if (curDir == null) storageLocations.firstOrNull { it.dir.path == dir.path } else null
                    val label = storageLoc?.label ?: if (curDir == null) dir.path.replace(sdPath, "~") else dir.name
                    val hl = focusIdx == headerItems + idx
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.settingsDropdown)
                            .background(if (hl) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
                            .border(if (hl) 1.5.dp else 0.dp, if (hl) DeckColors.Ruby else Color.Transparent, DeckShapes.settingsDropdown)
                            .soundClickable { curDir = dir }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${if (storageLoc != null && !storageLoc.isRomRoot) "💾" else "📁"}  $label",
                            style = DeckType.settingsValue,
                            color = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text("›", style = DeckType.settingsSection, color = DeckColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileTasksBanner(
    tasks: List<FileTask>,
    onCancel: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tasks.forEach { task ->
            val icon = when (task.kind) {
                FileTaskKind.Compress -> "🗜"
                FileTaskKind.Extract -> "📦"
                FileTaskKind.Copy -> "📋"
                FileTaskKind.Move -> "📦"
                FileTaskKind.Delete -> "🗑"
                FileTaskKind.Upload -> "📤"
            }
            val kindLabel = stringResource(
                when (task.kind) {
                    FileTaskKind.Compress -> R.string.lib_task_compress
                    FileTaskKind.Extract -> R.string.lib_task_extract
                    FileTaskKind.Copy -> R.string.lib_task_copy
                    FileTaskKind.Move -> R.string.lib_task_move
                    FileTaskKind.Delete -> R.string.lib_task_delete
                    FileTaskKind.Upload -> R.string.lib_task_upload
                },
            )
            val srcLabel = stringResource(
                if (task.source == FileTaskSource.Web) R.string.lib_task_src_web else R.string.lib_task_src_local,
            )
            val hasTotal = task.totalBytes > 0L
            val pct = if (hasTotal) ((task.bytesDone.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f)) else null
            val progressText = when {
                hasTotal -> {
                    val doneStr = formatCompactSize(task.bytesDone) ?: "0 KB"
                    val totalStr = formatCompactSize(task.totalBytes) ?: ""
                    "${((pct ?: 0f) * 100).toInt()}% · $doneStr / $totalStr"
                }
                task.bytesDone > 0L -> stringResource(
                    R.string.lib_task_processed_fmt,
                    formatCompactSize(task.bytesDone) ?: "0 KB",
                )
                else -> ""
            }
            val subParts = listOfNotNull(
                progressText.takeIf { it.isNotEmpty() },
                task.detail.takeIf { it.isNotBlank() && it != task.title },
            ).joinToString(" · ")

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeckColors.Surface)
                    .border(1.2.dp, DeckColors.Ruby.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = DeckColors.Ruby,
                        strokeWidth = 2.dp,
                    )
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "$icon $kindLabel · ${task.title}",
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            TagBadge(srcLabel, accent = task.source == FileTaskSource.Web)
                        }
                        if (subParts.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                subParts,
                                style = DeckType.metaLabel,
                                color = DeckColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (task.cancellable) {
                        Text(
                            stringResource(if (task.cancelling) R.string.lib_task_cancelling else R.string.lib_task_cancel),
                            style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = if (task.cancelling) DeckColors.TextSecondary else DeckColors.Ruby,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.Ruby.copy(alpha = 0.12f))
                                .soundClickable(enabled = !task.cancelling) { onCancel(task.id) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (pct != null) {
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = DeckColors.Ruby,
                        trackColor = DeckColors.DialogBorder,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = DeckColors.Ruby,
                        trackColor = DeckColors.DialogBorder,
                    )
                }
            }
        }
    }
}

private fun formatCompactSize(bytes: Long): String? {
    if (bytes <= 0L) return null
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
