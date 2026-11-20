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
import androidx.compose.foundation.lazy.LazyListState
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


internal data class StorageLocation(
    val dir: File,
    val label: String,
    val isRomRoot: Boolean,
    val badge: String?,
)

internal suspend fun searchFilesInScope(
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

internal fun discoverStorageLocations(context: Context, romRoots: List<File>): List<StorageLocation> {
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

internal data class FileRowMeta(
    val isDir: Boolean,
    val sizeText: String?,
    val freeSpaceText: String?,
)


@Composable
internal fun LibraryFilesTab(
    currentDir: File?,
    multiSelectMode: Boolean,
    selectedPaths: Set<String>,
    filesToolbarActions: List<FilesToolbarAction>,
    focusZone: LibraryZone,
    filesToolbarIdx: Int,
    fileSearchOpen: Boolean,
    fileSearchQuery: String,
    fileSearchRecursive: Boolean,
    fileSearching: Boolean,
    fileSearchFieldFr: FocusRequester,
    fileSearchFieldFocused: Boolean,
    fileTasks: List<FileTask>,
    hasUp: Boolean,
    fileEntries: List<File>,
    fileMetaMap: Map<String, FileRowMeta>,
    storageLocations: List<StorageLocation>,
    fullLibrary: Library?,
    hiddenIds: Set<String>,
    listState: LazyListState,
    selectedIdx: Int,
    onFileSearchQueryChange: (String) -> Unit,
    onOpenFileSearch: () -> Unit,
    onCloseFileSearch: () -> Unit,
    onFileSearchFieldFocusChange: (Boolean) -> Unit,
    onFileSearchAction: () -> Unit,
    onToggleFileSearchRecursive: () -> Unit,
    onCancelFileTask: (String) -> Unit,
    onStepUpDir: () -> Unit,
    onClickEntry: (Int, File, Boolean, Boolean) -> Unit,
    onClickFolderMenu: (Int, File) -> Unit,
) {
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
        Row(
            Modifier
                .widthIn(max = 438.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filesToolbarActions.forEachIndexed { tIdx, action ->
                val tHl = focusZone == LibraryZone.FilesToolbar && filesToolbarIdx == tIdx
                if (action.isSearch) {
                    val fileSearchExpanded = fileSearchOpen || fileSearchQuery.isNotBlank()
                    InlineExpandableSearchPill(
                        expanded = fileSearchExpanded,
                        query = fileSearchQuery,
                        onQueryChange = onFileSearchQueryChange,
                        placeholder = stringResource(
                            if (fileSearchRecursive) R.string.lib_files_search_recursive_hint
                            else R.string.lib_files_search_hint,
                        ),
                        onOpen = {
                            onOpenFileSearch()
                        },
                        onClose = {
                            onCloseFileSearch()
                        },
                        focusRequester = fileSearchFieldFr,
                        expandedWidth = 186.dp,
                        focused = tHl,
                        fieldFocused = fileSearchFieldFocused,
                        onFieldFocusChange = onFileSearchFieldFocusChange,
                        onSearchAction = {
                            onFileSearchAction()
                        },
                        collapsedBackground = DeckColors.Ruby.copy(alpha = if (tHl) 0.22f else 0.10f),
                        collapsedIconTint = DeckColors.Ruby,
                        collapsedBorderColor = Color.Transparent,
                        collapsedBorderWidth = 0.dp,
                        trailingContent = {
                            if (fileSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.6.dp,
                                    color = DeckColors.Ruby,
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = stringResource(R.string.lib_files_search_multi_short),
                                style = DeckType.metaLabel.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (fileSearchRecursive) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = if (fileSearchRecursive) Color.White else DeckColors.TextSecondary,
                                modifier = Modifier
                                    .clip(DeckShapes.tabPill)
                                    .brandPillBackground(
                                        selected = fileSearchRecursive,
                                        shape = DeckShapes.tabPill,
                                        unselectedColor = DeckColors.Background,
                                    )
                                    .soundClickable {
                                        onToggleFileSearchRecursive()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        },
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .clip(DeckShapes.tabPill)
                            .background(DeckColors.Ruby.copy(alpha = if (tHl) 0.22f else 0.10f))
                            .brandBorder(active = tHl, shape = DeckShapes.tabPill, activeWidth = 1.3.dp, inactiveWidth = 0.dp, inactiveColor = Color.Transparent)
                            .soundClickable(onClick = action.onClick)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (action.icon != null) {
                            DeckIcon(action.icon, tint = DeckColors.Ruby, size = 13.dp)
                        }
                        Text(
                            action.label,
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = DeckColors.Ruby,
                        )
                    }
                }
            }
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
                            .background(if (hl) DeckColors.AccentSoft else DeckColors.Surface)
                            .brandBorder(active = hl, shape = RoundedCornerShape(10.dp), activeWidth = 1.4.dp, inactiveWidth = 0.6.dp)
                            .soundClickable { onStepUpDir() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeckIcon(PanelIcons.CornerLeftUp, tint = DeckColors.Ruby, size = 16.dp)
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
                val rowMeta = fileMetaMap[file.path]
                val isDir = rowMeta?.isDir ?: false
                val matchedGame = if (!isDir) fullLibrary?.byId?.get(file.path) else null
                val storageLoc = if (dirNow == null) storageLocations.firstOrNull { it.dir.path == file.path } else null
                val isArchive = !isDir && ArchiveManager.isArchive(file)
                val isImage = !isDir && !isArchive && ArchiveManager.isImageFile(file)
                val isAudio = !isDir && !isArchive && !isImage && ArchiveManager.isAudioFile(file)
                val isFont = !isDir && !isArchive && !isImage && !isAudio && ArchiveManager.isFontFile(file)
                val isText = !isDir && !isArchive && !isImage && !isAudio && !isFont && ArchiveManager.isKnownTextFile(file)
                val inSearchMode = fileSearchQuery.isNotBlank()
                val label = storageLoc?.label ?: if (dirNow == null && !inSearchMode) file.path.replace(sdPath, "~") else file.name
                val iconSpec = when {
                    storageLoc != null && !storageLoc.isRomRoot -> DeckIcons.HardDrive to DeckColors.Ruby
                    isDir -> DeckIcons.Folder to DeckColors.Ruby
                    isArchive -> DeckIcons.Archive to DeckColors.TextSecondary
                    isImage -> DeckIcons.Image to DeckColors.TextSecondary
                    isAudio -> DeckIcons.Music to DeckColors.TextSecondary
                    isFont -> DeckIcons.FileText to DeckColors.TextSecondary
                    isText -> DeckIcons.FileText to DeckColors.TextSecondary
                    matchedGame != null -> DeckIcons.Gamepad to DeckColors.Ruby
                    else -> DeckIcons.FileText to DeckColors.TextSecondary
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                hl -> DeckColors.AccentSoft
                                checked -> DeckColors.Ruby.copy(alpha = 0.06f)
                                else -> DeckColors.Surface
                            },
                        )
                        .brandBorder(active = hl || checked, shape = RoundedCornerShape(10.dp), activeWidth = 1.4.dp, inactiveWidth = 0.6.dp)
                        .soundClickable {
                            onClickEntry(rowIdx, file, isDir, checked)
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (multiSelectMode && dirNow != null) {
                        DeckIcon(
                            if (checked) PanelIcons.CheckSquare else PanelIcons.Square,
                            tint = if (checked) DeckColors.Ruby else DeckColors.TextSecondary,
                            size = 17.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    DeckIcon(iconSpec.first, tint = iconSpec.second, size = 18.dp)
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
                            val freeStr = rowMeta?.freeSpaceText
                            val sub = listOfNotNull(
                                file.path.replace(sdPath, "~"),
                                freeStr?.let { stringResource(R.string.lib_storage_free_fmt, it) },
                            ).joinToString(" · ")
                            Text(sub, style = DeckType.metaLabel, color = DeckColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else if (!isDir) {
                            val sizeStr = rowMeta?.sizeText
                            val sub = listOfNotNull(relParent, sizeStr).joinToString(" · ")
                            if (sub.isNotEmpty()) {
                                Text(sub, style = DeckType.metaLabel, color = DeckColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else if (!relParent.isNullOrBlank()) {
                            Text(relParent, style = DeckType.metaLabel, color = DeckColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (isDir && !multiSelectMode) {
                        if (dirNow != null) {
                            Box(
                                modifier = Modifier
                                    .clip(DeckShapes.tabPill)
                                    .background(DeckColors.Background)
                                    .soundClickable {
                                        onClickFolderMenu(rowIdx, file)
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                DeckIcon(PanelIcons.MoreHorizontal, tint = DeckColors.TextSecondary, size = 15.dp)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        DeckIcon(DeckIcons.ChevronRight, tint = DeckColors.TextSecondary, size = 15.dp)
                    }
                }
            }
        }
    }
}
