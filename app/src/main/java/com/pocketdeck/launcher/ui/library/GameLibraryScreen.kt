package com.pocketdeck.launcher.ui.library

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.widget.Toast
import com.pocketdeck.launcher.lan.TransferPhase
import com.pocketdeck.launcher.ui.components.BrandTileIcon
import com.pocketdeck.launcher.ui.components.FavoriteHeartIcon
import com.pocketdeck.launcher.ui.components.soundClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.InlineExpandableSearchPill
import com.pocketdeck.launcher.ui.components.icons.PanelIcons
import com.pocketdeck.launcher.ui.components.requestFocusSafely
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
import com.pocketdeck.launcher.ui.theme.blockOverlayTouchThrough
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.cardSurface
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import com.pocketdeck.launcher.ui.theme.brandPillBackground
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
internal enum class LibraryZone { Header, Filter, FilesToolbar, List }


@Composable
fun GameLibraryAppIcon(
    size: Dp = 54.dp,
    modifier: Modifier = Modifier,
) = BrandTileIcon(R.drawable.ic_tile_game_library, size, modifier)

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
    scrapeProgress: com.pocketdeck.launcher.data.ScrapeProgress = com.pocketdeck.launcher.data.ScrapeProgress(),
    onCancelScrape: () -> Unit = {},
    onDismissScrapeProgress: () -> Unit = {},
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
            runCatching { filterRowState.animateScrollToItem(0) }
            delay(80)
            runCatching { searchFieldFr.requestFocus() }
        }
    }
    var fileSearchOpen by remember { mutableStateOf(false) }
    var fileSearchQuery by remember { mutableStateOf("") }
    var fileSearchRecursive by remember { mutableStateOf(false) }
    var fileSearchFieldFocused by remember { mutableStateOf(false) }
    val fileSearchFieldFr = remember { FocusRequester() }
    var fileSearching by remember { mutableStateOf(false) }
    var selectedIdx by remember { mutableIntStateOf(0) }

    // 文件浏览与多选状态
    var romRoots by remember { mutableStateOf<List<File>>(emptyList()) }
    var storageLocations by remember { mutableStateOf<List<StorageLocation>>(emptyList()) }
    var currentDir by remember { mutableStateOf<File?>(null) }
    var fileEntries by remember { mutableStateOf<List<File>>(emptyList()) }
    // 后台预计算的目录条目元信息（isDirectory / 文件大小 / 卷剩余空间），消除文件列表滚动时主线程逐行 stat()/statvfs()
    var fileMetaMap by remember { mutableStateOf<Map<String, FileRowMeta>>(emptyMap()) }
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
        storageLocations = withContext(Dispatchers.IO) { discoverStorageLocations(context, roots) }
        val jumpTarget = initialFilesDir?.let(::File)?.takeIf { withContext(Dispatchers.IO) { it.isDirectory } }
        if (jumpTarget != null) {
            currentDir = jumpTarget
            pendingFocusPath = initialFocusFilePath
        } else if (roots.size == 1) {
            currentDir = roots.first()
        }
        focusRequester.requestFocusSafely()
    }

    LaunchedEffect(mode, currentDir, dirRefreshTick, filesRevision, fileSearchQuery, fileSearchRecursive) {
        if (mode != LibraryMode.Files) {
            fileSearching = false
            return@LaunchedEffect
        }
        val dir = currentDir
        if (dir != null && !withContext(Dispatchers.IO) { dir.exists() }) {
            var ancestor = dir.parentFile
            while (ancestor != null) {
                val cur = ancestor
                if (withContext(Dispatchers.IO) { cur.exists() }) break
                ancestor = cur.parentFile
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
                    storageLocations = withContext(Dispatchers.IO) { discoverStorageLocations(context, romRoots) }
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
            storageLocations = withContext(Dispatchers.IO) { discoverStorageLocations(context, romRoots) }
            fileEntries = storageLocations.map { it.dir }
        } else {
            fileSearching = false
            fileEntries = withContext(Dispatchers.IO) {
                dir.listFiles { f -> !f.name.startsWith(".") }
                    .orEmpty()
                    .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            }
        }
        val storagePathSet = storageLocations.mapTo(HashSet()) { it.dir.path }
        val entriesSnapshot = fileEntries
        fileMetaMap = withContext(Dispatchers.IO) {
            val map = HashMap<String, FileRowMeta>(entriesSnapshot.size)
            for (f in entriesSnapshot) {
                val isDir = f.isDirectory
                val isVol = dir == null && f.path in storagePathSet
                map[f.path] = FileRowMeta(
                    isDir = isDir,
                    sizeText = if (!isDir) formatCompactSize(f.length()) else null,
                    freeSpaceText = if (isVol) formatCompactSize(f.usableSpace) else null,
                )
            }
            map
        }
        val targetPath = pendingFocusPath
        if (targetPath != null) {
            val matchIdx = fileEntries.indexOfFirst { it.path == targetPath }
            selectedIdx = if (matchIdx >= 0) matchIdx else 0
            pendingFocusPath = null
        } else if (!dirChanged) {
            selectedIdx = selectedIdx.coerceIn(0, (fileEntries.size - 1).coerceAtLeast(0))
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
        if (!overlayActive) focusRequester.requestFocusSafely()
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
        fileEntries.size
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
            Toast.makeText(context, it.message ?: context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
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
            add(ActionItem(playLabel) { onLaunchGame(game) })
            add(ActionItem(propsLabel) { propertiesGame = game })
            if (isRom && file.isFile) {
                add(ActionItem("$shareSendLabel") {
                    shareMethodTarget = ShareFileTarget(listOf(file), game.title, plat.id, plat.shortName)
                })
            }
            add(ActionItem("$categoriesLabel") { categoryTargetGame = game })
            add(
                ActionItem(if (isHidden) "$unhideLabel" else "$hideLabel") {
                    if (isHidden) onUnhideGame(game.id) else onHideGame(game.id)
                },
            )
            add(ActionItem(editLabel) { editingGame = game })
            if (isRom && file.isFile) {
                add(
                    ActionItem("$renameFileLabel") {
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
                add(ActionItem("$moveFileLabel") { moveTarget = MoveTarget.Single(file, isCopy = false) })
                add(ActionItem("$copyFileLabel") { moveTarget = MoveTarget.Single(file, isCopy = true) })
                add(ActionItem("$deleteFileLabel") { deleteConfirm = file to game })
            }
        }
        actionMenu = ActionMenuState(game.title, items)
    }

    fun openFolderMenu(folder: File) {
        val isRoot = romRoots.any { runCatching { it.canonicalPath == folder.canonicalPath }.getOrDefault(false) } ||
            storageLocations.any { !it.isRomRoot && runCatching { it.dir.canonicalPath == folder.canonicalPath }.getOrDefault(false) }
        val items = buildList {
            add(ActionItem("$folderOpenLabel") { enterDir(folder) })
            add(ActionItem("$copyFileLabel") { moveTarget = MoveTarget.Single(folder, isCopy = true) })
            if (!isRoot) {
                add(
                    ActionItem("$folderRenameLabel") {
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
                add(ActionItem("$folderMoveLabel") { moveTarget = MoveTarget.Single(folder, isCopy = false) })
                folder.parentFile?.let { parent ->
                    add(ActionItem("$compressZipLabel") {
                        runCompressZip(listOf(folder), parent, "${folder.name}.zip")
                    })
                }
                if (currentDir != null) {
                    add(ActionItem(addMultiSelectLabel) {
                        multiSelectMode = true
                        selectedPaths = selectedPaths + folder.path
                    })
                }
                add(ActionItem("$folderDeleteLabel") { batchDeleteFiles = listOf(folder) })
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
                add(ActionItem(playLabel) { onLaunchGame(matchedGame) })
                add(ActionItem(propsLabel) { propertiesGame = matchedGame })
            }
            if (isArchive) {
                add(ActionItem("$archiveInspectLabel") { archivePreviewFile = file })
                file.parentFile?.let { parent ->
                    add(ActionItem("$archiveExtractHereLabel") { runExtract(file, parent) })
                }
            }
            if (isImage) {
                add(ActionItem("$viewImageLabel") { imageViewerFile = file })
            }
            if (isAudio) {
                add(ActionItem("$playAudioLabel") { audioPlayerFile = file })
            }
            if (isFont) {
                add(ActionItem("$viewFontLabel") { fontViewerFile = file })
            }
            if (isText) {
                add(ActionItem("$viewTextLabel") { textEditorFile = file })
            }
            if (file.isFile) {
                add(ActionItem("$shareSendLabel") {
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
                ActionItem("$renameFileLabel") {
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
            add(ActionItem("$moveFileLabel") { moveTarget = MoveTarget.Single(file, isCopy = false) })
            add(ActionItem("$copyFileLabel") { moveTarget = MoveTarget.Single(file, isCopy = true) })
            file.parentFile?.let { parent ->
                add(ActionItem("$compressZipLabel") {
                    runCompressZip(listOf(file), parent, "${file.nameWithoutExtension}.zip")
                })
            }
            if (currentDir != null) {
                add(ActionItem(addMultiSelectLabel) {
                    multiSelectMode = true
                    selectedPaths = selectedPaths + file.path
                })
            }
            add(ActionItem("$deleteFileLabel") { deleteConfirm = file to matchedGame })
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
                add(ActionItem("${shareBatchLabel.format(shareable.size)}") {
                    shareMethodTarget = ShareFileTarget(
                        files = shareable,
                        gameTitle = "${shareable.size} files",
                        platformId = "",
                        platformName = "",
                    )
                })
            }
            add(ActionItem("${batchMoveFmt.format(picked.size)}") { moveTarget = MoveTarget.Batch(picked, isCopy = false) })
            add(ActionItem("${batchCopyFmt.format(picked.size)}") { moveTarget = MoveTarget.Batch(picked, isCopy = true) })
            if (dirNow != null) {
                add(ActionItem("$compressZipLabel") {
                    runCompressZip(picked, dirNow, "archive_${picked.size}.zip")
                })
            }
            if (archives.isNotEmpty() && dirNow != null) {
                add(ActionItem("$batchExtractLabel (${archives.size})") {
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
            add(ActionItem("${batchDeleteFmt.format(picked.size)}") { batchDeleteFiles = picked })
            add(ActionItem(selectAllLabel) { selectedPaths = fileEntries.map { it.path }.toSet() })
            add(ActionItem(exitMultiLabel) {
                multiSelectMode = false
                selectedPaths = emptySet()
            })
        }
        actionMenu = ActionMenuState(batchActionsFmt.format(picked.size), items)
    }

    // Files Toolbar 动作列表（确保全键盘/手柄可达）
    val filesToolbarActions: List<FilesToolbarAction> = if (!multiSelectMode) {
        buildList {
            add(
                FilesToolbarAction(
                    isSearch = true,
                    onClick = {
                        if (!fileSearchOpen) {
                            fileSearchOpen = true
                        } else {
                            runCatching { fileSearchFieldFr.requestFocus() }
                        }
                    },
                ),
            )
            if (currentDir != null) {
                add(FilesToolbarAction(label = stringResource(R.string.lib_new_folder), onClick = ::openNewFolderDialog))
                if (fileEntries.isNotEmpty()) {
                    add(
                        FilesToolbarAction(
                            label = stringResource(R.string.lib_multi_select),
                            icon = PanelIcons.CheckSquare,
                        ) {
                            multiSelectMode = true
                            selectedPaths = emptySet()
                            filesToolbarIdx = 0
                        },
                    )
                }
            }
        }
    } else if (currentDir == null) {
        emptyList()
    } else {
        buildList {
            val allChecked = fileEntries.isNotEmpty() && selectedPaths.size == fileEntries.size
            add(
                FilesToolbarAction(label = if (allChecked) stringResource(R.string.lib_select_clear) else selectAllLabel) {
                    selectedPaths = if (allChecked) emptySet() else fileEntries.map { it.path }.toSet()
                },
            )
            if (selectedPaths.isNotEmpty()) {
                add(FilesToolbarAction(label = stringResource(R.string.lib_batch_actions, selectedPaths.size), onClick = ::openBatchMenu))
            }
            add(
                FilesToolbarAction(label = exitMultiLabel) {
                    multiSelectMode = false
                    selectedPaths = emptySet()
                    filesToolbarIdx = 0
                },
            )
        }
    }

    val totalFilterChips = 6 + allPlatforms.size

    fun selectLibraryMode(targetMode: LibraryMode) {
        if (targetMode == LibraryMode.Files && mode == LibraryMode.Files && currentDir != null) {
            // 已在文件目录页时再次点击「文件目录」Tab：直接回到顶层「存储位置」列表
            pendingFocusPath = currentDir?.path
            fileSearchOpen = false
            fileSearchQuery = ""
            multiSelectMode = false
            selectedPaths = emptySet()
            currentDir = null
            filesToolbarIdx = 0
            focusZone = LibraryZone.List
            return
        }
        mode = targetMode
        selectedIdx = 0
        focusZone = LibraryZone.List
    }

    fun triggerHeader(idx: Int) {
        when (idx) {
            0 -> onDismiss()
            1 -> showLanDialog = true
            2 -> selectLibraryMode(LibraryMode.Games)
            3 -> selectLibraryMode(LibraryMode.Files)
        }
    }

    fun triggerFilterChip(idx: Int) {
        when (idx) {
            0 -> {
                if (!gameSearchOpen) {
                    gameSearchOpen = true
                } else {
                    runCatching { searchFieldFr.requestFocus() }
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
            .blockOverlayTouchThrough()
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
                            focusRequester.requestFocusSafely()
                            focusZone = if (searchFieldFocused) LibraryZone.Filter else LibraryZone.FilesToolbar
                            true
                        }
                        Key.DirectionDown -> {
                            focusRequester.requestFocusSafely()
                            focusZone = LibraryZone.List
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
                            LibraryZone.List -> Unit
                        }
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        when (focusZone) {
                            LibraryZone.Header -> Unit
                            LibraryZone.Filter -> focusZone = LibraryZone.Header
                            LibraryZone.FilesToolbar -> focusZone = LibraryZone.Header
                            LibraryZone.List -> {
                                if (selectedIdx > 0) {
                                    selectedIdx--
                                } else {
                                    focusZone = when {
                                        mode == LibraryMode.Games -> LibraryZone.Filter
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
                                    mode == LibraryMode.Games -> LibraryZone.Filter
                                    filesToolbarActions.isNotEmpty() -> {
                                        filesToolbarIdx = filesToolbarIdx.coerceIn(0, filesToolbarActions.lastIndex)
                                        LibraryZone.FilesToolbar
                                    }
                                    else -> LibraryZone.List
                                }
                            }
                            LibraryZone.FilesToolbar -> {
                                if (activeItemCount > 0) {
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
                                fileEntries.getOrNull(selectedIdx)?.let { f ->
                                    if (f.isDirectory) openFolderMenu(f) else openFileMenu(f)
                                }
                            }
                        }
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        when (focusZone) {
                            LibraryZone.Header -> triggerHeader(headerIdx)
                            LibraryZone.Filter -> triggerFilterChip(filterChipIdx)
                            LibraryZone.FilesToolbar -> filesToolbarActions.getOrNull(filesToolbarIdx)?.onClick?.invoke()
                            LibraryZone.List -> {
                                if (mode == LibraryMode.Games) {
                                    filteredGames.getOrNull(selectedIdx)?.let(::openGameMenu)
                                } else {
                                    fileEntries.getOrNull(selectedIdx)?.let { f ->
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
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶部标题栏
            // 顶部标题栏
            LibraryHeaderBar(
                allGamesCount = allGames.size,
                hiddenCount = hiddenCount,
                lanState = lanState,
                mode = mode,
                focusZone = focusZone,
                headerIdx = headerIdx,
                onOpenLanDialog = { showLanDialog = true },
                onSelectMode = ::selectLibraryMode,
                onDismiss = onDismiss,
            )

            HorizontalDivider(color = DeckColors.Line, thickness = 1.dp)

            if (scrapeProgress.total > 0 && (scrapeProgress.running || scrapeProgress.finishedAtMs > 0L)) {
                com.pocketdeck.launcher.ui.components.ScrapeProgressBanner(
                    progress = scrapeProgress,
                    onCancel = onCancelScrape,
                    onDismiss = onDismissScrapeProgress,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            if (mode == LibraryMode.Games) {
                LibraryGamesTab(
                    filterRowState = filterRowState,
                    listState = listState,
                    gameSearchOpen = gameSearchOpen,
                    searchQuery = searchQuery,
                    searchFieldFr = searchFieldFr,
                    searchFieldFocused = searchFieldFocused,
                    focusZone = focusZone,
                    filterChipIdx = filterChipIdx,
                    gamesFilter = gamesFilter,
                    hiddenCount = hiddenCount,
                    selectedPlatformId = selectedPlatformId,
                    allPlatforms = allPlatforms,
                    filteredGames = filteredGames,
                    selectedIdx = selectedIdx,
                    hiddenIds = hiddenIds,
                    favorites = favorites,
                    categorizedPlatforms = categorizedPlatforms,
                    categorizedGames = categorizedGames,
                    covers = covers,
                    officialNames = officialNames,
                    platformFor = platformFor,
                    onSearchQueryChange = { searchQuery = it; selectedIdx = 0 },
                    onOpenGameSearch = {
                        gameSearchOpen = true
                        selectedIdx = 0
                    },
                    onCloseGameSearch = {
                        searchQuery = ""
                        gameSearchOpen = false
                        selectedIdx = 0
                        runCatching { focusRequester.requestFocus() }
                    },
                    onSearchFieldFocusChange = { searchFieldFocused = it },
                    onSearchAction = {
                        focusRequester.requestFocusSafely()
                        focusZone = LibraryZone.List
                    },
                    onSelectFilter = { gamesFilter = it; selectedIdx = 0 },
                    onSelectPlatform = { selectedPlatformId = it; selectedIdx = 0 },
                    onClickGame = { idx, game ->
                        focusZone = LibraryZone.List
                        selectedIdx = idx
                        openGameMenu(game)
                    },
                    onUnhideGame = onUnhideGame,
                )
            } else {
                LibraryFilesTab(
                    currentDir = currentDir,
                    multiSelectMode = multiSelectMode,
                    selectedPaths = selectedPaths,
                    filesToolbarActions = filesToolbarActions,
                    focusZone = focusZone,
                    filesToolbarIdx = filesToolbarIdx,
                    fileSearchOpen = fileSearchOpen,
                    fileSearchQuery = fileSearchQuery,
                    fileSearchRecursive = fileSearchRecursive,
                    fileSearching = fileSearching,
                    fileSearchFieldFr = fileSearchFieldFr,
                    fileSearchFieldFocused = fileSearchFieldFocused,
                    fileTasks = fileTasks,
                    hasUp = hasUp,
                    fileEntries = fileEntries,
                    fileMetaMap = fileMetaMap,
                    storageLocations = storageLocations,
                    fullLibrary = fullLibrary,
                    hiddenIds = hiddenIds,
                    listState = listState,
                    selectedIdx = selectedIdx,
                    onFileSearchQueryChange = { fileSearchQuery = it; selectedIdx = 0 },
                    onOpenFileSearch = {
                        fileSearchOpen = true
                        selectedIdx = 0
                    },
                    onCloseFileSearch = {
                        fileSearchQuery = ""
                        fileSearchOpen = false
                        selectedIdx = 0
                        runCatching { focusRequester.requestFocus() }
                    },
                    onFileSearchFieldFocusChange = { fileSearchFieldFocused = it },
                    onFileSearchAction = {
                        focusRequester.requestFocusSafely()
                        focusZone = LibraryZone.List
                    },
                    onToggleFileSearchRecursive = {
                        fileSearchRecursive = !fileSearchRecursive
                        selectedIdx = 0
                    },
                    onCancelFileTask = onCancelFileTask,
                    onStepUpDir = { stepUpDir() },
                    onClickEntry = { rowIdx, file, isDir, checked ->
                        focusZone = LibraryZone.List
                        selectedIdx = rowIdx
                        if (multiSelectMode && currentDir != null) {
                            selectedPaths = if (checked) selectedPaths - file.path else selectedPaths + file.path
                        } else if (isDir) {
                            enterDir(file)
                        } else {
                            openFileMenu(file)
                        }
                    },
                    onClickFolderMenu = { rowIdx, file ->
                        selectedIdx = rowIdx
                        openFolderMenu(file)
                    },
                )
            }
        }

        LibraryOverlayDialogs(
            actionMenu = actionMenu,
            onDismissActionMenu = { actionMenu = null },
            propertiesGame = propertiesGame,
            onDismissPropertiesGame = { propertiesGame = null },
            editingGame = editingGame,
            onDismissEditingGame = { editingGame = null },
            categoryTargetGame = categoryTargetGame,
            onDismissCategoryTargetGame = { categoryTargetGame = null },
            nameSpec = nameSpec,
            nameState = nameState,
            onDismissNameDialog = { nameSpec = null },
            moveTarget = moveTarget,
            onDismissMoveTarget = { moveTarget = null },
            deleteConfirm = deleteConfirm,
            onDismissDeleteConfirm = { deleteConfirm = null },
            batchDeleteFiles = batchDeleteFiles,
            onDismissBatchDelete = { batchDeleteFiles = null },
            archivePreviewFile = archivePreviewFile,
            onDismissArchivePreview = { archivePreviewFile = null },
            onPickExtractDir = { moveTarget = MoveTarget.ExtractArchive(it) },
            imageViewerFile = imageViewerFile,
            onDismissImageViewer = { imageViewerFile = null },
            textEditorFile = textEditorFile,
            onDismissTextEditor = { textEditorFile = null },
            audioPlayerFile = audioPlayerFile,
            onDismissAudioPlayer = { audioPlayerFile = null },
            fontViewerFile = fontViewerFile,
            onDismissFontViewer = { fontViewerFile = null },
            opProgress = opProgress,
            onCancelOpProgress = {
                opCancelled.set(true)
                opJob?.cancel()
                opJob = null
                opProgress = null
            },
            showLanDialog = showLanDialog,
            onDismissLanDialog = { showLanDialog = false },
            shareMethodTarget = shareMethodTarget,
            onDismissShareMethod = { shareMethodTarget = null },
            onPickLanShareMethod = { target ->
                shareMethodTarget = null
                shareTarget = target
            },
            onPickSystemShareMethod = { target ->
                shareMethodTarget = null
                multiSelectMode = false
                selectedPaths = emptySet()
                launchSystemShare(target.files)
            },
            shareTarget = shareTarget,
            onDismissShareTarget = { shareTarget = null },
            onClearMultiSelect = {
                multiSelectMode = false
                selectedPaths = emptySet()
            },
            onBumpDirRefresh = { dirRefreshTick++ },
            onLocateInFiles = { dir, focusFile ->
                fileSearchQuery = ""
                fileSearchOpen = false
                mode = LibraryMode.Files
                headerIdx = 3
                focusZone = LibraryZone.List
                currentDir = dir
                pendingFocusPath = focusFile?.absolutePath
                dirRefreshTick++
            },
            categories = categories,
            metadataMap = metadataMap,
            officialNames = officialNames,
            metaResolutionMap = metaResolutionMap,
            coverResolutionMap = coverResolutionMap,
            favorites = favorites,
            gameLaunchers = gameLaunchers,
            covers = covers,
            lanManager = lanManager,
            lanState = lanState,
            romRoots = romRoots,
            storageLocations = storageLocations,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            platformFor = platformFor,
            effectiveLauncherFor = effectiveLauncherFor,
            computeCrc32 = computeCrc32,
            onRefreshMetadata = onRefreshMetadata,
            onHideGame = onHideGame,
            onSaveGameEdits = onSaveGameEdits,
            onSetGameLauncher = onSetGameLauncher,
            onSetGameCategories = onSetGameCategories,
            onCreateCategoryForGame = onCreateCategoryForGame,
            onMoveFile = onMoveFile,
            onMoveEntries = onMoveEntries,
            onCopyFile = onCopyFile,
            onCopyEntries = onCopyEntries,
            onDeleteFile = onDeleteFile,
            onDeleteEntries = onDeleteEntries,
            onRunExtract = ::runExtract,
            onApplyFontId = onApplyFontId,
            onOpenQuickShare = onOpenQuickShare,
            onRescan = onRescan,
            scope = scope,
            context = context,
            deleteDone = deleteDone,
            deleteFailFmt = deleteFailFmt,
            moveDone = moveDone,
            moveFailFmt = moveFailFmt,
            copyDone = copyDone,
            copyFailFmt = copyFailFmt,
            copyBatchDoneFmt = copyBatchDoneFmt,
            batchDoneFmt = batchDoneFmt,
        )
    }
}
