package com.pocketdeck.launcher.ui.library

import com.pocketdeck.launcher.data.formatSize
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import com.pocketdeck.launcher.ui.components.CircularProgressIndicator
import com.pocketdeck.launcher.ui.components.HorizontalDivider
import com.pocketdeck.launcher.ui.components.Slider
import com.pocketdeck.launcher.ui.components.SliderDefaults
import com.pocketdeck.launcher.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.data.ArchiveEntryInfo
import com.pocketdeck.launcher.data.ArchiveManager
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.components.DialogCloseButton
import com.pocketdeck.launcher.ui.components.DeckDialogSize
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.gamepadClickable
import com.pocketdeck.launcher.ui.components.requestFocusSafely
import com.pocketdeck.launcher.ui.components.scrimTapDismiss
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.blockOverlayTouchThrough
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import com.pocketdeck.launcher.ui.theme.brandPillBackground
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.SystemFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * 图片查看器（支持安全降采样加载、手势缩放与纯键盘/手柄缩放关闭）。
 */
@Composable
fun ImageViewerDialog(
    file: File,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var scale by remember(file.path) { mutableFloatStateOf(1f) }
    var offsetX by remember(file.path) { mutableFloatStateOf(0f) }
    var offsetY by remember(file.path) { mutableFloatStateOf(0f) }

    val loaded by produceState<Triple<ImageBitmap?, Int, Int>?>(initialValue = null, file.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                val origW = opts.outWidth
                val origH = opts.outHeight
                if (origW <= 0 || origH <= 0) return@runCatching Triple(null, 0, 0)
                var sample = 1
                while ((origW / sample) > 2048 || (origH / sample) > 2048) sample *= 2
                val decOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, decOpts)
                Triple(bmp?.asImageBitmap(), origW, origH)
            }.getOrNull()
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocusSafely() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .blockOverlayTouchThrough()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    event.key == Key.Equals || event.key == Key.NumPadAdd || event.key == Key.Plus -> {
                        scale = (scale * 1.25f).coerceAtMost(5f)
                        true
                    }
                    event.key == Key.Minus || event.key == Key.NumPadSubtract -> {
                        scale = (scale / 1.25f).coerceAtLeast(1f)
                        if (scale <= 1.01f) { offsetX = 0f; offsetY = 0f }
                        true
                    }
                    event.key == Key.Zero || event.key == Key.NumPad0 -> {
                        scale = 1f; offsetX = 0f; offsetY = 0f
                        true
                    }
                    scale > 1.05f && event.key == Key.DirectionLeft -> { offsetX += 60f; true }
                    scale > 1.05f && event.key == Key.DirectionRight -> { offsetX -= 60f; true }
                    scale > 1.05f && event.key == Key.DirectionUp -> { offsetY += 60f; true }
                    scale > 1.05f && event.key == Key.DirectionDown -> { offsetY -= 60f; true }
                    else -> false
                }
            },
    ) {
        val bmp = loaded?.first
        if (loaded == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = DeckColors.Ruby,
            )
        } else if (bmp == null) {
            Text(
                stringResource(R.string.edit_image_failed),
                style = DeckType.menuTitle,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Image(
                bitmap = bmp,
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp)
                    .pointerInput(file.path) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 5f)
                            scale = next
                            if (next > 1.02f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
        }

        // 顶部信息栏 + 缩放/关闭按钮
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val dims = loaded?.let { if (it.second > 0) "${it.second}×${it.third} · " else "" }.orEmpty()
                Text(
                    "$dims${formatSize(file.length())} · ${stringResource(R.string.lib_image_zoom_hint)}",
                    style = DeckType.metaLabel,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            DialogCloseButton(
                onClick = onDismiss,
                darkSurface = true,
            )
        }
    }
}

/**
 * 文本 / 配置文件查看与编辑器（支持 .cfg / .ini / .json / .xml / .cue / .m3u / .cht / .txt 等）。
 */
@Composable
fun TextFileViewerEditorDialog(
    file: File,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onSaved: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val rootFr = remember { FocusRequester() }
    val editorFr = remember { FocusRequester() }

    var loading by remember(file.path) { mutableStateOf(true) }
    var truncated by remember(file.path) { mutableStateOf(false) }
    var editing by remember(file.path) { mutableStateOf(false) }
    var content by remember(file.path) { mutableStateOf("") }
    var originalContent by remember(file.path) { mutableStateOf("") }

    LaunchedEffect(file.path) {
        loading = true
        val res = withContext(Dispatchers.IO) {
            runCatching {
                val maxPreview = 512 * 1024
                val len = file.length()
                val isTrunc = len > maxPreview
                val toRead = minOf(len, maxPreview.toLong()).toInt().coerceAtLeast(0)
                val bytes = ByteArray(toRead)
                var read = 0
                FileInputStream(file).use { fis ->
                    while (read < toRead) {
                        val n = fis.read(bytes, read, toRead - read)
                        if (n <= 0) break
                        read += n
                    }
                }
                String(bytes, 0, read, StandardCharsets.UTF_8) to isTrunc
            }.getOrElse { ("Error: ${it.message}") to true }
        }
        content = res.first
        originalContent = res.first
        truncated = res.second
        loading = false
    }

    LaunchedEffect(editing) {
        if (editing) editorFr.requestFocusSafely()
        else rootFr.requestFocusSafely()
    }

    val saveOkMsg = stringResource(R.string.lib_text_saved)
    val saveFailFmt = stringResource(R.string.lib_text_save_failed)

    fun saveNow() {
        if (truncated) return
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    file.writeText(content, StandardCharsets.UTF_8)
                }
            }
            if (res.isSuccess) {
                originalContent = content
                editing = false
                Toast.makeText(context, saveOkMsg, Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(context, saveFailFmt.format(res.exceptionOrNull()?.message.orEmpty()), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .blockOverlayTouchThrough()
            .focusRequester(rootFr)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        if (editing) editing = false else onDismiss()
                        true
                    }
                    !editing && event.key == Key.DirectionDown -> {
                        scope.launch { scrollState.animateScrollTo((scrollState.value + 160).coerceAtMost(scrollState.maxValue)) }
                        true
                    }
                    !editing && event.key == Key.DirectionUp -> {
                        scope.launch { scrollState.animateScrollTo((scrollState.value - 160).coerceAtLeast(0)) }
                        true
                    }
                    !editing && !truncated && GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        editing = true
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = DeckDialogSize.WidthL)
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 600.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        style = DeckType.menuTitle,
                        color = DeckColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${formatSize(file.length())} · ${content.count { it == '\n' } + 1} lines" +
                            if (content != originalContent) " *" else "",
                        style = DeckType.metaLabel,
                        color = DeckColors.TextSecondary,
                    )
                }
                if (!truncated && !loading) {
                    if (editing) {
                        Text(
                            stringResource(R.string.lib_text_save),
                            style = DeckType.menuButton,
                            color = Color.White,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .brandBackground(DeckShapes.tabPill)
                                .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill) { saveNow() }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                        )
                    } else {
                        Text(
                            stringResource(R.string.lib_text_edit),
                            style = DeckType.menuButton,
                            color = DeckColors.Ruby,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.Ruby.copy(alpha = 0.14f))
                                .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill) { editing = true }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }

            if (truncated) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lib_text_truncated),
                    style = DeckType.settingsLabel,
                    color = DeckColors.Ruby,
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DeckColors.Line)
            Spacer(Modifier.height(10.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeckColors.Surface)
                    .border(0.6.dp, DeckColors.Line, RoundedCornerShape(10.dp))
                    .verticalScroll(scrollState)
                    .padding(12.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = DeckColors.Ruby,
                    )
                } else if (editing) {
                    BasicTextField(
                        value = content,
                        onValueChange = { content = it },
                        textStyle = DeckType.settingsLabel.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = DeckColors.TextPrimary,
                        ),
                        cursorBrush = SolidColor(DeckColors.Ruby),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(editorFr),
                    )
                } else {
                    Text(
                        text = content,
                        style = DeckType.settingsLabel.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        ),
                        color = DeckColors.TextPrimary,
                    )
                }
            }
        }
    }
}

/**
 * 压缩包内容预览与解压选择弹窗（支持直接解压 / 解压到同名目录 / 指定目录）。
 */
@Composable
fun ArchivePreviewDialog(
    file: File,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onExtractHere: () -> Unit,
    onExtractSubfolder: () -> Unit,
    onExtractPickDir: () -> Unit,
    onDismiss: () -> Unit,
) {
    val subName = remember(file.path) { ArchiveManager.suggestedFolderName(file) }
    val inspectState by produceState<Result<List<ArchiveEntryInfo>>?>(initialValue = null, file.path) {
        value = ArchiveManager.inspect(file)
    }
    val actions = listOf(
        Triple(stringResource(R.string.lib_archive_extract_here), true, onExtractHere),
        Triple(stringResource(R.string.lib_archive_extract_sub, subName), false, onExtractSubfolder),
        Triple(stringResource(R.string.lib_archive_extract_to), false, onExtractPickDir),
        Triple(stringResource(R.string.menu_cancel), false, onDismiss),
    )
    var focusIdx by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocusSafely() }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss(onDismiss)
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
                        focusIdx = (focusIdx - 1 + actions.size) % actions.size
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        focusIdx = (focusIdx + 1) % actions.size
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        actions[focusIdx].third()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = DeckDialogSize.WidthL)
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeckIcon(DeckIcons.Archive, tint = DeckColors.Ruby, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    file.name,
                    style = DeckType.menuTitle,
                    color = DeckColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val entries = inspectState?.getOrNull()
            val err = inspectState?.exceptionOrNull()
            Spacer(Modifier.height(4.dp))
            when {
                inspectState == null -> Text(
                    stringResource(R.string.scrape_loading),
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                )
                err != null -> Text(
                    err.message ?: "Error",
                    style = DeckType.settingsLabel,
                    color = DeckColors.StatusDanger,
                )
                entries != null -> {
                    val fileCount = entries.count { !it.isDir }
                    val totalSize = entries.sumOf { if (it.isDir) 0L else it.size }
                    Text(
                        stringResource(R.string.lib_archive_entries_fmt, fileCount, formatSize(totalSize)),
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DeckColors.Line)

            if (entries != null) {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(entries.take(300)) { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(DeckColors.Surface)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeckIcon(
                                icon = if (e.isDir) DeckIcons.Folder else DeckIcons.FileText,
                                tint = if (e.isDir) DeckColors.Ruby else DeckColors.TextSecondary,
                                size = 15.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                e.name,
                                style = DeckType.metaLabel,
                                color = DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (!e.isDir && e.size > 0L) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    formatSize(e.size),
                                    style = DeckType.metaLabel,
                                    color = DeckColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            actions.forEachIndexed { idx, (label, primary, onClick) ->
                if (idx > 0) Spacer(Modifier.height(6.dp))
                val hl = idx == focusIdx
                Text(
                    label,
                    style = DeckType.menuButton,
                    color = when {
                        primary && hl -> DeckColors.Background
                        hl -> DeckColors.Ruby
                        else -> DeckColors.TextPrimary
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeckShapes.menuButton)
                        .brandPillBackground(
                            selected = primary && hl,
                            shape = DeckShapes.menuButton,
                            unselectedColor = if (hl) DeckColors.AccentSoft else DeckColors.Surface,
                        )
                        .then(
                            if (primary && hl) Modifier
                            else Modifier.brandBorder(active = hl, shape = DeckShapes.menuButton, activeWidth = 1.4.dp, inactiveWidth = 0.6.dp),
                        )
                        .soundClickable {
                            focusIdx = idx
                            onClick()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * 批量删除二次确认框（默认高亮取消，支持手柄/键盘操作）。
 */
@Composable
fun BatchDeleteConfirmDialog(
    count: Int,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var selectedIdx by remember { mutableIntStateOf(1) } // 0 = Delete, 1 = Cancel
    LaunchedEffect(Unit) { focusRequester.requestFocusSafely() }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss(onDismiss)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    event.key == Key.DirectionUp || event.key == Key.DirectionLeft -> {
                        selectedIdx = 0
                        true
                    }
                    event.key == Key.DirectionDown || event.key == Key.DirectionRight -> {
                        selectedIdx = 1
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        if (selectedIdx == 0) onConfirm() else onDismiss()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = DeckDialogSize.WidthM)
                .fillMaxWidth()
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(24.dp),
        ) {
            Text(
                stringResource(R.string.lib_batch_delete_title),
                style = DeckType.menuTitle,
                color = DeckColors.Ruby,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.lib_batch_delete_desc, count),
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            val delHl = selectedIdx == 0
            Text(
                stringResource(R.string.lib_batch_delete_yes, count),
                style = DeckType.menuButton,
                color = DeckColors.StatusDanger,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DeckShapes.menuButton)
                    .background(if (delHl) DeckColors.StatusDanger.copy(alpha = 0.16f) else DeckColors.Surface)
                    .border(0.9.dp, if (delHl) DeckColors.StatusDanger else DeckColors.Line, DeckShapes.menuButton)
                    .soundClickable(onClick = onConfirm)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
            Spacer(Modifier.height(8.dp))
            val cancelHl = selectedIdx == 1
            Text(
                stringResource(R.string.menu_cancel),
                style = DeckType.menuButton,
                color = if (cancelHl) DeckColors.Ruby else DeckColors.TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DeckShapes.menuButton)
                    .background(if (cancelHl) DeckColors.AccentSoft else DeckColors.Surface)
                    .brandBorder(active = cancelHl, shape = DeckShapes.menuButton, activeWidth = 1.4.dp, inactiveWidth = 0.6.dp)
                    .soundClickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}

/**
 * 解压 / 压缩 / 批量文件操作进度弹窗。
 */
@Composable
fun FileOpProgressDialog(
    title: String,
    detail: String,
    onCancel: () -> Unit,
) {
    val progressFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { progressFr.requestFocusSafely() }
    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .clickable(enabled = true, onClick = {})
            .focusRequester(progressFr)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (GamepadKeys.isBack(ev.key) || ev.key == Key.ButtonB) {
                    onCancel()
                    true
                } else true
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = DeckDialogSize.WidthM)
                .fillMaxWidth()
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = DeckColors.Ruby,
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(title, style = DeckType.menuTitle, color = DeckColors.TextPrimary)
            }
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = DeckColors.Ruby,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(DeckColors.Ruby.copy(alpha = 0.12f))
                        .soundClickable(onClick = onCancel)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * 常见音频播放器弹窗（支持播放/暂停、循环、进度条拖动及纯键盘/手柄方向键快进退）。
 */
@Composable
fun AudioPlayerDialog(
    file: File,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val playBtnFr = remember { FocusRequester() }
    var prepared by remember(file.path) { mutableStateOf(false) }
    var playing by remember(file.path) { mutableStateOf(false) }
    var looping by remember(file.path) { mutableStateOf(false) }
    var durationMs by remember(file.path) { mutableIntStateOf(0) }
    var positionMs by remember(file.path) { mutableIntStateOf(0) }
    var errorMsg by remember(file.path) { mutableStateOf<String?>(null) }
    var playerRef by remember(file.path) { mutableStateOf<MediaPlayer?>(null) }

    val openFailMsg = stringResource(R.string.lib_audio_error)

    DisposableEffect(file.path) {
        val mp = MediaPlayer()
        var released = false
        playerRef = mp
        runCatching {
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener {
                if (released) return@setOnPreparedListener
                prepared = true
                durationMs = it.duration.coerceAtLeast(0)
                runCatching {
                    it.start()
                    playing = true
                }
            }
            mp.setOnCompletionListener {
                if (released) return@setOnCompletionListener
                if (!it.isLooping) {
                    playing = false
                    positionMs = durationMs
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                if (!released) {
                    errorMsg = openFailMsg
                    playing = false
                }
                true
            }
            mp.prepareAsync()
        }.onFailure {
            errorMsg = openFailMsg
        }
        onDispose {
            released = true
            playerRef = null
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
    }

    LaunchedEffect(prepared, playing) {
        while (prepared) {
            val mp = playerRef ?: break
            runCatching {
                positionMs = mp.currentPosition.coerceAtLeast(0)
                playing = mp.isPlaying
            }
            delay(250)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocusSafely() }

    fun togglePlay() {
        val mp = playerRef ?: return
        if (!prepared || errorMsg != null) return
        runCatching {
            if (mp.isPlaying) {
                mp.pause()
                playing = false
            } else {
                if (durationMs > 0 && positionMs >= durationMs - 200) {
                    mp.seekTo(0)
                    positionMs = 0
                }
                mp.start()
                playing = true
            }
        }
    }

    fun seekBy(deltaMs: Int) {
        val mp = playerRef ?: return
        if (!prepared || durationMs <= 0) return
        val target = (positionMs + deltaMs).coerceIn(0, durationMs)
        runCatching { mp.seekTo(target) }
        positionMs = target
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss(onDismiss)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    event.key == Key.Spacebar || event.key == Key.MediaPlayPause ||
                        GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        togglePlay()
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        seekBy(-5000)
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        seekBy(5000)
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = DeckDialogSize.WidthM)
                .fillMaxWidth(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeckColors.Ruby.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    DeckIcon(DeckIcons.Music, tint = DeckColors.Ruby, size = 22.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        style = DeckType.menuTitle,
                        color = DeckColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${file.extension.uppercase()} · ${formatSize(file.length())}",
                        style = DeckType.metaLabel,
                        color = DeckColors.TextSecondary,
                    )
                }
            }

            HorizontalDivider(color = DeckColors.Line)

            if (errorMsg != null) {
                Text(
                    errorMsg!!,
                    style = DeckType.settingsValue,
                    color = DeckColors.StatusDanger,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else if (!prepared) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = DeckColors.Ruby, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.lib_audio_loading), style = DeckType.settingsLabel, color = DeckColors.TextSecondary)
                }
            } else {
                val safeDur = durationMs.coerceAtLeast(1)
                Slider(
                    value = (positionMs.toFloat() / safeDur).coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        val tgt = (frac * durationMs).toInt().coerceIn(0, durationMs)
                        positionMs = tgt
                        runCatching { playerRef?.seekTo(tgt) }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = DeckColors.Ruby,
                        activeTrackColor = DeckColors.Ruby,
                        inactiveTrackColor = DeckColors.Surface,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatAudioTime(positionMs), style = DeckType.metaLabel, color = DeckColors.TextSecondary)
                    Text(formatAudioTime(durationMs), style = DeckType.metaLabel, color = DeckColors.TextSecondary)
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "⏪ -5s",
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextPrimary,
                        modifier = Modifier
                            .clip(DeckShapes.menuButton)
                            .background(DeckColors.Surface)
                            .gamepadClickable(swapAbxy, keyBindings) { seekBy(-5000) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Text(
                        if (playing) "⏸ ${stringResource(R.string.lib_audio_pause)}" else "▶ ${stringResource(R.string.lib_audio_play)}",
                        style = DeckType.menuButton,
                        color = DeckColors.Background,
                        modifier = Modifier
                            .clip(DeckShapes.menuButton)
                            .brandBackground(DeckShapes.tabPill)
                            .gamepadClickable(swapAbxy, keyBindings, focusRequester = playBtnFr) { togglePlay() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        "+5s ⏩",
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextPrimary,
                        modifier = Modifier
                            .clip(DeckShapes.menuButton)
                            .background(DeckColors.Surface)
                            .gamepadClickable(swapAbxy, keyBindings) { seekBy(5000) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(DeckShapes.menuButton)
                            .background(if (looping) DeckColors.Ruby.copy(alpha = 0.14f) else DeckColors.Surface)
                            .gamepadClickable(swapAbxy, keyBindings) {
                                looping = !looping
                                runCatching { playerRef?.isLooping = looping }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeckIcon(
                            icon = DeckIcons.Repeat,
                            tint = if (looping) DeckColors.Ruby else DeckColors.TextSecondary,
                            size = 14.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (looping) stringResource(R.string.lib_audio_loop_on) else stringResource(R.string.lib_audio_loop_off),
                            style = DeckType.settingsLabel,
                            color = if (looping) DeckColors.Ruby else DeckColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 字体文件预览弹窗（校验合法性防崩溃，展示多字号中英数排版样本，并支持一键应用为界面字体）。
 */
@Composable
fun FontViewerDialog(
    file: File,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onApplyFontId: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var applying by remember { mutableStateOf(false) }

    val loadedState by produceState<Pair<Boolean, FontFamily?>?>(initialValue = null, file.path) {
        val fam = SystemFonts.previewFontFamily(file)
        value = (fam != null) to fam
    }

    val appliedMsg = stringResource(R.string.font_import_success)
    val invalidMsg = stringResource(R.string.lib_font_invalid)

    LaunchedEffect(Unit) { focusRequester.requestFocusSafely() }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss(onDismiss)
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
                        scope.launch { scrollState.animateScrollTo((scrollState.value - 120).coerceAtLeast(0)) }
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        scope.launch { scrollState.animateScrollTo((scrollState.value + 120).coerceAtMost(scrollState.maxValue)) }
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = DeckDialogSize.WidthL)
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Column(Modifier.padding(end = 36.dp)) {
                Text(
                    file.name,
                    style = DeckType.menuTitle,
                    color = DeckColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${file.extension.uppercase()} · ${formatSize(file.length())}",
                    style = DeckType.metaLabel,
                    color = DeckColors.TextSecondary,
                )
            }

            HorizontalDivider(color = DeckColors.Line)

            val state = loadedState
            if (state == null) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = DeckColors.Ruby)
                }
            } else if (!state.first || state.second == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                    Text(invalidMsg, style = DeckType.settingsValue, color = DeckColors.StatusDanger)
                }
            } else {
                val previewFamily = state.second!!
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeckColors.Surface)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "PocketDeck · 掌机游戏启动器",
                        fontFamily = previewFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckColors.TextPrimary,
                    )
                    Text(
                        "The quick brown fox jumps over the lazy dog.",
                        fontFamily = previewFamily,
                        fontSize = 18.sp,
                        color = DeckColors.TextPrimary,
                    )
                    Text(
                        "ABCDEFGHIJKLM NOPQRSTUVWXYZ",
                        fontFamily = previewFamily,
                        fontSize = 15.sp,
                        color = DeckColors.TextSecondary,
                    )
                    Text(
                        "abcdefghijklm nopqrstuvwxyz",
                        fontFamily = previewFamily,
                        fontSize = 15.sp,
                        color = DeckColors.TextSecondary,
                    )
                    Text(
                        "0123456789  !@#\$%^&*()_+-=[]{}|;':\",./<>?",
                        fontFamily = previewFamily,
                        fontSize = 14.sp,
                        color = DeckColors.TextSecondary,
                    )
                    Text(
                        "天地玄黄，宇宙洪荒。日月盈昃，辰宿列张。手柄与键盘畅玩复古游戏！",
                        fontFamily = previewFamily,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = DeckColors.TextPrimary,
                    )
                    Text(
                        "12sp Small Caption · 小号说明文字预览 123456",
                        fontFamily = previewFamily,
                        fontSize = 12.sp,
                        color = DeckColors.TextSecondary,
                    )
                }

                if (onApplyFontId != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            if (applying) "..." else stringResource(R.string.lib_font_apply_ui),
                            style = DeckType.menuButton,
                            color = DeckColors.Background,
                            modifier = Modifier
                                .clip(DeckShapes.menuButton)
                                .brandBackground(DeckShapes.tabPill)
                                .gamepadClickable(swapAbxy, keyBindings) {
                                    if (applying) return@gamepadClickable
                                    applying = true
                                    scope.launch {
                                        val res = SystemFonts.importFromFile(context, file)
                                        applying = false
                                        res.onSuccess { id ->
                                            onApplyFontId(id)
                                            Toast.makeText(context, appliedMsg, Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }.onFailure {
                                            Toast.makeText(context, invalidMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatAudioTime(ms: Int): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
}
