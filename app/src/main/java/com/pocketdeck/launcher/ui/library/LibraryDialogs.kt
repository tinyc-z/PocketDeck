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
import com.pocketdeck.launcher.lan.LanServerState
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


internal data class ShareFileTarget(
    val files: List<File>,
    val gameTitle: String,
    val platformId: String,
    val platformName: String,
) {
    val primaryFile: File get() = files.first()
    val totalSize: Long get() = files.sumOf { runCatching { it.length() }.getOrDefault(0L) }
}


internal sealed interface MoveTarget {
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

/** 「游戏库」品牌图标：手柄 + 扇形叠放的游戏封面，与 App 主图标、游戏快传共用同一底板与风格。 */
@Composable

internal fun MoveFolderDialog(
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
                                .brandBorder(active = hl, shape = DeckShapes.settingsDropdown, activeWidth = 1.3.dp, inactiveWidth = 0.dp, inactiveColor = Color.Transparent)
                                .soundClickable { curDir?.let(onPickDir) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeckIcon(DeckIcons.Check, tint = DeckColors.Ruby, size = 15.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(if (isCopy) R.string.lib_copy_here else R.string.lib_move_here),
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
                                .border(if (hl) 0.9.dp else 0.dp, if (hl) DeckColors.Ruby else Color.Transparent, DeckShapes.settingsDropdown)
                                .soundClickable { stepUpPicker() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeckIcon(PanelIcons.CornerLeftUp, tint = DeckColors.TextSecondary, size = 15.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.lib_files_up), style = DeckType.settingsValue, color = DeckColors.TextPrimary)
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
                            .background(if (hl) DeckColors.AccentSoft else DeckColors.Surface)
                            .brandBorder(active = hl, shape = DeckShapes.settingsDropdown, activeWidth = 1.3.dp, inactiveWidth = 0.dp, inactiveColor = Color.Transparent)
                            .soundClickable { curDir = dir }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
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
internal fun LibraryOverlayDialogs(
    actionMenu: ActionMenuState?,
    onDismissActionMenu: () -> Unit,
    propertiesGame: Game?,
    onDismissPropertiesGame: () -> Unit,
    editingGame: Game?,
    onDismissEditingGame: () -> Unit,
    categoryTargetGame: Game?,
    onDismissCategoryTargetGame: () -> Unit,
    nameSpec: NameDialogSpec?,
    nameState: NameDialogState?,
    onDismissNameDialog: () -> Unit,
    moveTarget: MoveTarget?,
    onDismissMoveTarget: () -> Unit,
    deleteConfirm: Pair<File, Game?>?,
    onDismissDeleteConfirm: () -> Unit,
    batchDeleteFiles: List<File>?,
    onDismissBatchDelete: () -> Unit,
    archivePreviewFile: File?,
    onDismissArchivePreview: () -> Unit,
    onPickExtractDir: (File) -> Unit,
    imageViewerFile: File?,
    onDismissImageViewer: () -> Unit,
    textEditorFile: File?,
    onDismissTextEditor: () -> Unit,
    audioPlayerFile: File?,
    onDismissAudioPlayer: () -> Unit,
    fontViewerFile: File?,
    onDismissFontViewer: () -> Unit,
    opProgress: Pair<String, String>?,
    onCancelOpProgress: () -> Unit,
    showLanDialog: Boolean,
    onDismissLanDialog: () -> Unit,
    shareMethodTarget: ShareFileTarget?,
    onDismissShareMethod: () -> Unit,
    onPickLanShareMethod: (ShareFileTarget) -> Unit,
    onPickSystemShareMethod: (ShareFileTarget) -> Unit,
    shareTarget: ShareFileTarget?,
    onDismissShareTarget: () -> Unit,
    onClearMultiSelect: () -> Unit,
    onBumpDirRefresh: () -> Unit,
    onLocateInFiles: (File, File?) -> Unit,
    categories: List<Category>,
    metadataMap: Map<String, GameMetadata>,
    officialNames: Map<String, String>,
    metaResolutionMap: Map<String, MetadataResolutionInfo>,
    coverResolutionMap: Map<String, CoverResolutionInfo>,
    favorites: Set<String>,
    gameLaunchers: Map<String, String>,
    covers: CoverSource,
    lanManager: LanServerManager,
    lanState: LanServerState,
    romRoots: List<File>,
    storageLocations: List<StorageLocation>,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    platformFor: (Game) -> Platform,
    effectiveLauncherFor: (Game) -> String?,
    computeCrc32: suspend (String) -> String?,
    onRefreshMetadata: suspend (Game) -> Boolean,
    onHideGame: (String) -> Unit,
    onSaveGameEdits: (String, String?, String?) -> Unit,
    onSetGameLauncher: (String, String?) -> Unit,
    onSetGameCategories: (String, Set<String>) -> Unit,
    onCreateCategoryForGame: ((String, String) -> Unit)?,
    onMoveFile: suspend (File, File) -> Result<File>,
    onMoveEntries: suspend (List<File>, File) -> Result<Int>,
    onCopyFile: suspend (File, File) -> Result<File>,
    onCopyEntries: suspend (List<File>, File) -> Result<Int>,
    onDeleteFile: suspend (File) -> Result<Unit>,
    onDeleteEntries: suspend (List<File>) -> Result<Int>,
    onRunExtract: (File, File) -> Unit,
    onApplyFontId: (String) -> Unit,
    onOpenQuickShare: () -> Unit,
    onRescan: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    deleteDone: String,
    deleteFailFmt: String,
    moveDone: String,
    moveFailFmt: String,
    copyDone: String,
    copyFailFmt: String,
    copyBatchDoneFmt: String,
    batchDoneFmt: String,
) {
    // 操作菜单
    actionMenu?.let { m ->
        ActionMenuDialog(
            state = m,
            onPick = { item -> onDismissActionMenu(); item.action() },
            onDismiss = onDismissActionMenu,
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
                    onDismissPropertiesGame()
                    onLocateInFiles(dir, f)
                }
            },
            onDismiss = onDismissPropertiesGame,
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
            onDismiss = onDismissEditingGame,
            onSave = { name, coverPath ->
                onSaveGameEdits(game.id, name, coverPath)
                onDismissEditingGame()
            },
            onHide = {
                onHideGame(game.id)
                onDismissEditingGame()
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
                    onDismissEditingGame()
                    onBumpDirRefresh()
                }
            },
            onLocateFile = {
                val f = File(game.id)
                val dir = f.parentFile
                if (dir != null && dir.isDirectory) {
                    onDismissEditingGame()
                    onLocateInFiles(dir, f)
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
                onDismissCategoryTargetGame()
            },
            onDismiss = { onDismissCategoryTargetGame() },
        )
    }

    // 重命名 / 新建文件夹 / 压缩包命名弹窗
    nameSpec?.let { spec ->
        val s = nameState ?: return@let
        NameDialog(
            spec = spec,
            state = s,
            onConfirm = { spec.onConfirm(s.value.text); onDismissNameDialog() },
            onDismiss = { onDismissNameDialog() },
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
                onDismissMoveTarget()
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
                        onBumpDirRefresh()
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
                        onClearMultiSelect()
                        onBumpDirRefresh()
                        onRescan()
                    }
                    is MoveTarget.ExtractArchive -> onRunExtract(mt.archive, targetDir)
                }
            },
            onDismiss = { onDismissMoveTarget() },
        )
    }

    // 删除单个文件二次警告确认框
    deleteConfirm?.let { (file, matchedGame) ->
        DeleteFileConfirmDialog(
            fileName = file.name,
            filePath = file.path,
            onUseHide = if (matchedGame != null) {
                {
                    onDismissDeleteConfirm()
                    onHideGame(matchedGame.id)
                }
            } else null,
            onConfirmDelete = {
                onDismissDeleteConfirm()
                scope.launch {
                    val res = onDeleteFile(file)
                    Toast.makeText(
                        context,
                        if (res.isSuccess) deleteDone else deleteFailFmt.format(res.exceptionOrNull()?.message.orEmpty()),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onBumpDirRefresh()
                }
            },
            onDismiss = { onDismissDeleteConfirm() },
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
                onDismissBatchDelete()
                scope.launch {
                    val res = onDeleteEntries(files)
                    Toast.makeText(
                        context,
                        if (res.isSuccess) batchDoneFmt.format(res.getOrDefault(files.size))
                        else deleteFailFmt.format(res.exceptionOrNull()?.message.orEmpty()),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onClearMultiSelect()
                    onBumpDirRefresh()
                }
            },
            onDismiss = { onDismissBatchDelete() },
        )
    }

    // 压缩包内容预览与解压选择
    archivePreviewFile?.let { arc ->
        ArchivePreviewDialog(
            file = arc,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onExtractHere = {
                onDismissArchivePreview()
                arc.parentFile?.let { onRunExtract(arc, it) }
            },
            onExtractSubfolder = {
                onDismissArchivePreview()
                arc.parentFile?.let { parent ->
                    onRunExtract(arc, File(parent, ArchiveManager.suggestedFolderName(arc)))
                }
            },
            onExtractPickDir = {
                onDismissArchivePreview()
                onPickExtractDir(arc)
            },
            onDismiss = onDismissArchivePreview,
        )
    }

    // 图片查看器
    imageViewerFile?.let { img ->
        ImageViewerDialog(
            file = img,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onDismiss = onDismissImageViewer,
        )
    }

    // 文本 / 配置文件查看与编辑器
    textEditorFile?.let { txt ->
        TextFileViewerEditorDialog(
            file = txt,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onSaved = { onBumpDirRefresh() },
            onDismiss = onDismissTextEditor,
        )
    }

    audioPlayerFile?.let { audio ->
        AudioPlayerDialog(
            file = audio,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onDismiss = onDismissAudioPlayer,
        )
    }

    fontViewerFile?.let { font ->
        FontViewerDialog(
            file = font,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onApplyFontId = onApplyFontId,
            onDismiss = onDismissFontViewer,
        )
    }

    // 解压 / 压缩进度弹窗
    opProgress?.let { (title, detail) ->
        FileOpProgressDialog(
            title = title,
            detail = detail,
            onCancel = onCancelOpProgress,
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
                onDismissLanDialog()
                onLocateInFiles(dir, focusFile)
            },
            onOpenQuickShareScreen = {
                onDismissLanDialog()
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
            onDismiss = onDismissLanDialog,
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
                onPickLanShareMethod(target)
            },
            onPickSystemShare = {
                onPickSystemShareMethod(target)
            },
            onDismiss = onDismissShareMethod,
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
                onClearMultiSelect()
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
            onDismiss = onDismissShareTarget,
        )
    }
}
