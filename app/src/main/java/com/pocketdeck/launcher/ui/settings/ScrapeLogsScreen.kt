package com.pocketdeck.launcher.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.pocketdeck.launcher.ui.components.HorizontalDivider
import com.pocketdeck.launcher.ui.components.Text
import com.pocketdeck.launcher.ui.theme.blockOverlayTouchThrough
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.icons.PanelIcons
import com.pocketdeck.launcher.ui.components.InlineExpandableSearchPill
import com.pocketdeck.launcher.data.ScrapeLogStore
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.components.DeckDialogSize
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.requestFocusSafely
import com.pocketdeck.launcher.ui.components.scrimTapDismiss
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandPillBackground
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun formatCompactLogPath(fullPath: String): String {
    val parts = fullPath.split('/').filter { it.isNotEmpty() }
    return if (parts.size > 3) "…/" + parts.takeLast(3).joinToString("/") else fullPath
}

private enum class ScrapeLogFilter {
    All,
    Cover,
    Meta,
    Error,
}

private data class DiskShortcut(val label: String, val dir: File)

/**
 * 封面与元数据请求实时日志独立界面：
 * - 通过 [ScrapeLogStore.lines] 事件流实时刷新（无需轮询读盘）
 * - 支持按类型（全部 / 封面 / 元数据 / 仅报错）筛选与折叠式关键词搜索
 * - 支持选择磁盘目录导出完整 `.log` 文件
 */
@Composable
fun ScrapeLogsScreen(
    onBack: () -> Unit,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenFr = remember { FocusRequester() }
    val searchFr = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { ScrapeLogStore.init(context) }
        screenFr.requestFocusSafely()
    }

    val liveLines by ScrapeLogStore.lines.collectAsState()
    var paused by remember { mutableStateOf(false) }
    var frozenLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by remember { mutableStateOf(ScrapeLogFilter.All) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val clearedMsg = stringResource(R.string.scrape_logs_cleared)
    val pathCopiedMsg = stringResource(R.string.scrape_logs_path_copied)
    val exportOkFmt = stringResource(R.string.scrape_logs_export_ok)
    val exportFailFmt = stringResource(R.string.scrape_logs_export_fail)

    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            delay(60)
            searchFr.requestFocusSafely()
        } else if (!showExportDialog) {
            screenFr.requestFocusSafely()
        }
    }
    LaunchedEffect(showExportDialog) {
        if (!showExportDialog && !searchOpen) {
            screenFr.requestFocusSafely()
        }
    }

    val sourceLines = if (paused) frozenLines else liveLines
    val filteredLines = remember(sourceLines, filter, query) {
        val q = query.trim().lowercase()
        sourceLines.filter { line ->
            val passFilter = when (filter) {
                ScrapeLogFilter.All -> true
                ScrapeLogFilter.Cover -> "[COVER]" in line
                ScrapeLogFilter.Meta -> "[META]" in line
                ScrapeLogFilter.Error -> isScrapeLogErrorLine(line)
            }
            passFilter && (q.isEmpty() || q in line.lowercase())
        }
    }

    fun handleBackAction() {
        when {
            showExportDialog -> showExportDialog = false
            query.isNotEmpty() -> query = ""
            searchOpen -> searchOpen = false
            else -> onBack()
        }
    }

    BackHandler(enabled = true) { handleBackAction() }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.Background)
            .blockOverlayTouchThrough()
            .focusRequester(screenFr)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (showExportDialog) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) ||
                        (!searchOpen && GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings)) -> {
                        handleBackAction()
                        true
                    }
                    !searchOpen && event.key == Key.DirectionDown -> {
                        val next = (listState.firstVisibleItemIndex + 3).coerceAtMost((filteredLines.size - 1).coerceAtLeast(0))
                        scope.launch { listState.animateScrollToItem(next) }
                        true
                    }
                    !searchOpen && event.key == Key.DirectionUp -> {
                        val next = (listState.firstVisibleItemIndex - 3).coerceAtLeast(0)
                        scope.launch { listState.animateScrollToItem(next) }
                        true
                    }
                    !searchOpen && event.key == Key.DirectionLeft -> {
                        val entries = ScrapeLogFilter.entries
                        filter = entries[(entries.indexOf(filter) - 1 + entries.size) % entries.size]
                        true
                    }
                    !searchOpen && event.key == Key.DirectionRight -> {
                        val entries = ScrapeLogFilter.entries
                        filter = entries[(entries.indexOf(filter) + 1) % entries.size]
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DeckColors.Surface)
                        .border(0.6.dp, DeckColors.Line, CircleShape)
                        .soundClickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    DeckIcon(DeckIcons.ChevronLeft, size = 18.dp, tint = DeckColors.TextPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.scrape_logs_title),
                        style = DeckType.menuTitle,
                        color = DeckColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val fullLogPath = ScrapeLogStore.filePath()
                    val shortLogPath = remember(fullLogPath) { formatCompactLogPath(fullLogPath) }
                    Text(
                        shortLogPath,
                        style = DeckType.metaLabel.copy(fontFamily = FontFamily.Monospace),
                        color = DeckColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("scrape_log_path", fullLogPath))
                            Toast.makeText(context, pathCopiedMsg, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (paused) R.string.scrape_logs_paused else R.string.scrape_logs_live),
                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                    color = if (paused) DeckColors.StatusWarning else DeckColors.StatusSuccess,
                    modifier = Modifier
                        .clip(DeckShapes.tabPill)
                        .background(
                            if (paused) DeckColors.StatusWarning.copy(alpha = 0.14f)
                            else DeckColors.StatusSuccess.copy(alpha = 0.14f),
                        )
                        .soundClickable {
                            if (!paused) {
                                frozenLines = liveLines
                                paused = true
                            } else {
                                paused = false
                            }
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.scrape_logs_export),
                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                    color = DeckColors.TextPrimary,
                    modifier = Modifier
                        .clip(DeckShapes.tabPill)
                        .background(DeckColors.Surface)
                        .border(0.6.dp, DeckColors.Line, DeckShapes.tabPill)
                        .soundClickable { showExportDialog = true }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.scrape_logs_clear),
                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                    color = DeckColors.Ruby,
                    modifier = Modifier
                        .clip(DeckShapes.tabPill)
                        .background(DeckColors.Ruby.copy(alpha = 0.10f))
                        .border(0.6.dp, DeckColors.Ruby.copy(alpha = 0.25f), DeckShapes.tabPill)
                        .soundClickable {
                            ScrapeLogStore.clear()
                            frozenLines = emptyList()
                            Toast.makeText(context, clearedMsg, Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }

            HorizontalDivider(color = DeckColors.Line, thickness = 1.dp)

            // 筛选栏（内联可展开搜索胶囊 + 分类胶囊 + 条数）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val searchExpanded = searchOpen || query.isNotEmpty()
                    InlineExpandableSearchPill(
                        expanded = searchExpanded,
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.scrape_logs_search_hint),
                        onOpen = { searchOpen = true },
                        onClose = {
                            query = ""
                            searchOpen = false
                            screenFr.requestFocusSafely()
                        },
                        focusRequester = searchFr,
                        expandedWidth = 188.dp,
                        onSearchAction = { screenFr.requestFocusSafely() },
                    )
                    val chips = listOf(
                        ScrapeLogFilter.All to stringResource(R.string.scrape_logs_filter_all),
                        ScrapeLogFilter.Cover to stringResource(R.string.scrape_logs_filter_cover),
                        ScrapeLogFilter.Meta to stringResource(R.string.scrape_logs_filter_meta),
                        ScrapeLogFilter.Error to stringResource(R.string.scrape_logs_filter_err),
                    )
                    chips.forEach { (kind, label) ->
                        val selected = filter == kind
                        Text(
                            label,
                            style = DeckType.settingsLabel.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                            color = if (selected) Color.White else DeckColors.TextSecondary,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .brandPillBackground(selected = selected, shape = DeckShapes.tabPill, unselectedColor = DeckColors.Surface)
                                .then(if (selected) Modifier else Modifier.border(0.6.dp, DeckColors.Line, DeckShapes.tabPill))
                                .soundClickable { filter = kind }
                                .padding(horizontal = 13.dp, vertical = 6.dp),
                        )
                    }
                }
                Text(
                    "${filteredLines.size}",
                    style = DeckType.metaLabel.copy(fontFamily = FontFamily.Monospace),
                    color = DeckColors.TextSecondary,
                )
            }

            // 深色终端风实时日志列表
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0D1117))
                    .border(0.6.dp, Color(0xFF30363D), RoundedCornerShape(12.dp)),
            ) {
                if (filteredLines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.scrape_logs_empty),
                            style = DeckType.settingsLabel.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF8B949E),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(filteredLines) { idx, line ->
                            val styled = remember(line) { formatTerminalLogLine(line) }
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${idx + 1}",
                                    style = DeckType.metaLabel.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = Color(0xFF484F58),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier
                                        .width(34.dp)
                                        .padding(end = 10.dp),
                                )
                                Text(
                                    text = styled,
                                    style = DeckType.metaLabel.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showExportDialog) {
            ExportLogDirDialog(
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onDismiss = { showExportDialog = false },
                onExportTo = { dir ->
                    showExportDialog = false
                    scope.launch {
                        val res = withContext(Dispatchers.IO) { ScrapeLogStore.exportToDirectory(dir) }
                        if (res.isSuccess) {
                            val saved = res.getOrThrow().absolutePath
                            Toast.makeText(context, exportOkFmt.format(saved), Toast.LENGTH_LONG).show()
                        } else {
                            val err = res.exceptionOrNull()?.message.orEmpty()
                            Toast.makeText(context, exportFailFmt.format(err), Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }
    }
}

private fun discoverDiskShortcuts(
    context: Context,
    internalLabel: String,
    downloadLabel: String,
    docsLabel: String,
    sdLabel: String,
): List<DiskShortcut> {
    val result = mutableListOf<DiskShortcut>()
    val seen = HashSet<String>()
    fun add(label: String, f: File?) {
        if (f == null || !f.isDirectory) return
        val key = runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)
        if (seen.add(key)) result += DiskShortcut(label, f)
    }
    val primary = Environment.getExternalStorageDirectory()
    add(downloadLabel, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
    add(docsLabel, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
    add(internalLabel, primary)

    // 发现外置 SD 卡根目录
    var sdIdx = 1
    context.getExternalFilesDirs(null).orEmpty().forEach { appDir ->
        val p = appDir?.absolutePath ?: return@forEach
        val marker = "/Android/data/"
        val idx = p.indexOf(marker)
        if (idx > 0) {
            val volRoot = File(p.substring(0, idx))
            val primaryCanon = runCatching { primary.canonicalPath }.getOrDefault(primary.absolutePath)
            val volCanon = runCatching { volRoot.canonicalPath }.getOrDefault(volRoot.absolutePath)
            if (volCanon != primaryCanon && volRoot.isDirectory && volRoot.canRead()) {
                val lbl = if (sdIdx == 1) sdLabel else "$sdLabel $sdIdx"
                add(lbl, volRoot)
                sdIdx++
            }
        }
    }
    return result
}

@Composable
private fun ExportLogDirDialog(
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onDismiss: () -> Unit,
    onExportTo: (File) -> Unit,
) {
    val context = LocalContext.current
    val internalLabel = stringResource(R.string.scrape_logs_disk_internal)
    val downloadLabel = stringResource(R.string.scrape_logs_disk_download)
    val docsLabel = stringResource(R.string.scrape_logs_disk_documents)
    val sdLabel = stringResource(R.string.scrape_logs_disk_sd)

    val shortcuts = remember(internalLabel, downloadLabel, docsLabel, sdLabel) {
        discoverDiskShortcuts(context, internalLabel, downloadLabel, docsLabel, sdLabel)
    }
    val defaultDir = remember(shortcuts) {
        shortcuts.firstOrNull()?.dir ?: Environment.getExternalStorageDirectory()
    }
    var currentDir by remember { mutableStateOf(defaultDir) }
    var subDirs by remember { mutableStateOf<List<File>>(emptyList()) }
    val dialogFr = remember { FocusRequester() }

    LaunchedEffect(Unit) { dialogFr.requestFocusSafely() }
    LaunchedEffect(currentDir) {
        subDirs = withContext(Dispatchers.IO) {
            currentDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                .orEmpty()
                .sortedBy { it.name.lowercase() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss(onDismiss)
            .focusRequester(dialogFr)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (GamepadKeys.isBack(ev.key) || GamepadKeys.isCancelOrApps(ev.key, swapAbxy, keyBindings)) {
                    if (ev.nativeKeyEvent.repeatCount == 0) onDismiss()
                    true
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = DeckDialogSize.WidthL)
                .fillMaxWidth()
                .heightIn(max = 540.dp),
            contentPadding = PaddingValues(20.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Text(
                stringResource(R.string.scrape_logs_export_title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                modifier = Modifier.padding(end = 36.dp),
            )
            Spacer(Modifier.height(10.dp))

            // 磁盘快捷入口（下载 / 文档 / 内部存储 / SD 卡）
            if (shortcuts.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    shortcuts.forEach { sc ->
                        val active = currentDir.absolutePath == sc.dir.absolutePath
                        Text(
                            sc.label,
                            style = DeckType.settingsLabel.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                            color = if (active) Color.White else DeckColors.TextSecondary,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .brandPillBackground(selected = active, shape = DeckShapes.tabPill, unselectedColor = DeckColors.Surface)
                                .then(if (active) Modifier else Modifier.border(0.6.dp, DeckColors.Line, DeckShapes.tabPill))
                                .soundClickable { currentDir = sc.dir }
                                .padding(horizontal = 11.dp, vertical = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 当前路径展示
            Text(
                currentDir.absolutePath,
                style = DeckType.metaLabel.copy(fontFamily = FontFamily.Monospace),
                color = DeckColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DeckColors.Line)

            // 子目录列表
            LazyColumn(
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 120.dp, max = 260.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val parent = currentDir.parentFile
                if (parent != null && parent.canRead()) {
                    item(key = "..") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(DeckShapes.settingsDropdown)
                                .background(DeckColors.Surface)
                                .border(0.6.dp, DeckColors.Line, DeckShapes.settingsDropdown)
                                .soundClickable { currentDir = parent }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeckIcon(PanelIcons.CornerLeftUp, tint = DeckColors.Ruby, size = 15.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("..", style = DeckType.settingsValue, color = DeckColors.Ruby)
                        }
                    }
                }
                if (subDirs.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.scrape_logs_dir_empty),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
                        )
                    }
                } else {
                    items(subDirs, key = { it.absolutePath }) { sub ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(DeckShapes.settingsDropdown)
                                .background(DeckColors.Surface)
                                .border(0.6.dp, DeckColors.Line, DeckShapes.settingsDropdown)
                                .soundClickable { currentDir = sub }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeckIcon(DeckIcons.Folder, tint = DeckColors.Ruby, size = 16.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                sub.name,
                                style = DeckType.settingsValue,
                                color = DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(DeckShapes.menuButton)
                        .brandBackground(DeckShapes.tabPill)
                        .soundClickable { onExportTo(currentDir) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.scrape_logs_export_here),
                        style = DeckType.menuButton,
                        color = Color.White,
                    )
                }
                Box(
                    Modifier
                        .clip(DeckShapes.menuButton)
                        .background(DeckColors.Surface)
                        .border(0.6.dp, DeckColors.Line, DeckShapes.menuButton)
                        .soundClickable(onClick = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.menu_cancel),
                        style = DeckType.menuButton,
                        color = DeckColors.TextPrimary,
                    )
                }
            }
        }
    }
}

private val LOG_HEAD_TOKEN_REGEX = Regex("""\[[^\]]+\]""")
private val LOG_HTTP_OK_REGEX = Regex("""^HTTP 2\d\d.*""")
private val LOG_HTTP_404_REGEX = Regex("""^HTTP 404(?:\b.*)?$""")
private val LOG_HTTP_ERR_REGEX = Regex("""^HTTP [45]\d\d.*""")
private val LOG_MS_REGEX = Regex("""^\d+ms$""")

/** 区分真正的刮削故障与正常的 404 未命中（如汉化改版 ROM 的哈希库未命中 / 无对应封面文件）。 */
internal fun isScrapeLogErrorLine(line: String): Boolean {
    if ("error=" in line) return true
    if ("HTTP 5" in line) return true
    if ("HTTP 4" in line && "HTTP 404" !in line) return true
    return false
}

private fun formatTerminalLogLine(raw: String): AnnotatedString = buildAnnotatedString {
    val parts = raw.split(" | ")
    val head = parts.firstOrNull().orEmpty()
    val hasExplicitError = parts.any { it.startsWith("error=") }
    var cursor = 0
    var bracketIdx = 0
    for (m in LOG_HEAD_TOKEN_REGEX.findAll(head)) {
        if (m.range.first > cursor) {
            withStyle(SpanStyle(color = Color(0xFFC9D1D9))) {
                append(head.substring(cursor, m.range.first))
            }
        }
        val token = m.value
        val style = when {
            bracketIdx == 0 -> SpanStyle(color = Color(0xFF6E7681))
            token == "[META]" -> SpanStyle(color = Color(0xFF58A6FF), fontWeight = FontWeight.Bold)
            token == "[COVER]" -> SpanStyle(color = Color(0xFFD2A8FF), fontWeight = FontWeight.Bold)
            else -> SpanStyle(color = Color(0xFF79C0FF))
        }
        withStyle(style) { append(token) }
        cursor = m.range.last + 1
        bracketIdx++
    }
    if (cursor < head.length) {
        withStyle(SpanStyle(color = Color(0xFFC9D1D9))) {
            append(head.substring(cursor))
        }
    }
    for (i in 1 until parts.size) {
        withStyle(SpanStyle(color = Color(0xFF30363D))) { append(" | ") }
        val seg = parts[i]
        val style = when {
            LOG_HTTP_OK_REGEX.matches(seg) -> SpanStyle(color = Color(0xFF3FB950), fontWeight = FontWeight.Bold)
            LOG_HTTP_404_REGEX.matches(seg) && !hasExplicitError -> SpanStyle(color = Color(0xFFD29922), fontWeight = FontWeight.SemiBold)
            LOG_HTTP_ERR_REGEX.matches(seg) || seg.startsWith("error=") -> SpanStyle(color = Color(0xFFF85149), fontWeight = FontWeight.Bold)
            LOG_MS_REGEX.matches(seg) -> SpanStyle(color = Color(0xFFD29922))
            seg.startsWith("url=") -> SpanStyle(color = Color(0xFFA5D6FF))
            else -> SpanStyle(color = Color(0xFF8B949E))
        }
        withStyle(style) { append(seg) }
    }
}
