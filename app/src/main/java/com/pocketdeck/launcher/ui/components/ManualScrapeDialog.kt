package com.pocketdeck.launcher.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.CjkAliases
import com.pocketdeck.launcher.data.ManualScrapeCandidate
import com.pocketdeck.launcher.data.ScrapeProgress
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 刮削任务管理进度条（用于游戏库顶栏与设置 → 封面与信息）：
 * 显示百分比、已完成/总数、当前平台与游戏名、更新/未命中统计，并支持随时终止或收起结果。
 */
@Composable
fun ScrapeProgressBanner(
    progress: ScrapeProgress,
    onCancel: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val showResult = !progress.running && progress.total > 0 && progress.finishedAtMs > 0L
    if (!progress.running && !showResult) return
    if (progress.total <= 0) return
    val fraction = (progress.done.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
    val accent = if (progress.running) DeckColors.Ruby else DeckColors.TextPrimary
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (progress.running) DeckColors.AccentSoft else DeckColors.Surface)
            .border(
                0.7.dp,
                if (progress.running) DeckColors.Ruby.copy(alpha = 0.35f) else DeckColors.Line,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    progress.running -> stringResource(
                        R.string.scrape_progress_pct_fmt,
                        progress.percent,
                        progress.done,
                        progress.total,
                    )
                    progress.cancelled -> stringResource(
                        R.string.scrape_task_cancelled_fmt,
                        progress.percent,
                        progress.updatedCount,
                    )
                    else -> stringResource(
                        R.string.scrape_task_done_fmt,
                        progress.updatedCount,
                        progress.total,
                    )
                },
                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
            )
            val detailText = buildString {
                if (progress.running) {
                    if (progress.currentPlatform.isNotBlank()) {
                        append('[').append(progress.currentPlatform).append("] ")
                    }
                    append(progress.currentTitle)
                } else if (progress.failedCount > 0) {
                    append(stringResource(R.string.scrape_progress_failed_fmt, progress.failedCount))
                }
            }
            if (detailText.isNotBlank()) {
                Text(
                    text = detailText,
                    style = DeckType.metaLabel,
                    color = DeckColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (progress.running && progress.updatedCount > 0) {
                Text(
                    text = stringResource(R.string.scrape_progress_updated_fmt, progress.updatedCount),
                    style = DeckType.metaLabel,
                    color = DeckColors.TextSecondary,
                )
            }
            if (progress.running && onCancel != null) {
                Box(
                    Modifier
                        .clip(DeckShapes.tabPill)
                        .background(DeckColors.Ruby)
                        .soundClickable(onClick = onCancel)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.scrape_task_stop),
                        style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            } else if (!progress.running && onDismiss != null) {
                Box(
                    Modifier
                        .clip(DeckShapes.tabPill)
                        .background(DeckColors.SurfaceAlt)
                        .border(0.6.dp, DeckColors.Line, DeckShapes.tabPill)
                        .soundClickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.scrape_task_close),
                        style = DeckType.metaLabel.copy(fontWeight = FontWeight.Medium),
                        color = DeckColors.TextSecondary,
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(DeckShapes.tabPill),
            color = if (progress.running) DeckColors.Ruby else DeckColors.TextSecondary,
            trackColor = DeckColors.DialogBg,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/**
 * 后台刮削任务管理弹窗：由状态栏旋转图标或设置页进入，展示实时百分比、当前游戏与统计，支持随时终止。
 */
@Composable
fun ScrapeTaskDialog(
    progress: ScrapeProgress,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onCancel: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var btnFocus by remember(progress.running) { mutableIntStateOf(0) }
    val fraction = if (progress.total > 0) {
        (progress.done.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
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
                        onDismiss(); true
                    }
                    event.key == Key.DirectionLeft || event.key == Key.DirectionUp -> {
                        btnFocus = 0; true
                    }
                    event.key == Key.DirectionRight || event.key == Key.DirectionDown -> {
                        btnFocus = 1; true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        if (progress.running) {
                            if (btnFocus == 0) onCancel() else onDismiss()
                        } else {
                            if (btnFocus == 0) onDismiss() else onRestart()
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.scrape_task_title),
                        style = DeckType.menuTitle.copy(fontWeight = FontWeight.Bold),
                        color = DeckColors.TextPrimary,
                    )
                    Text(
                        text = "${progress.percent}%",
                        style = DeckType.menuTitle.copy(fontWeight = FontWeight.Bold),
                        color = if (progress.running) DeckColors.Ruby else DeckColors.TextPrimary,
                    )
                }

                Text(
                    text = when {
                        progress.running -> stringResource(R.string.scrape_task_status_running)
                        progress.cancelled -> stringResource(R.string.scrape_task_status_cancelled)
                        else -> stringResource(R.string.scrape_task_status_done)
                    },
                    style = DeckType.metaLabel,
                    color = DeckColors.TextSecondary,
                )

                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(DeckShapes.tabPill),
                    color = if (progress.running) DeckColors.Ruby else DeckColors.TextSecondary,
                    trackColor = DeckColors.SurfaceAlt,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )

                if (progress.running && progress.currentTitle.isNotBlank()) {
                    val curLine = if (progress.currentPlatform.isNotBlank()) {
                        "[${progress.currentPlatform}] ${progress.currentTitle}"
                    } else {
                        progress.currentTitle
                    }
                    Text(
                        text = curLine,
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeckColors.Surface)
                        .border(0.6.dp, DeckColors.Line, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ScrapeStatCell(stringResource(R.string.scrape_task_stat_progress), "${progress.done} / ${progress.total}")
                    ScrapeStatCell(stringResource(R.string.scrape_task_stat_updated), "${progress.updatedCount}")
                    ScrapeStatCell(stringResource(R.string.scrape_task_stat_missed), "${progress.failedCount}")
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val primaryLabel = if (progress.running) {
                        stringResource(R.string.scrape_task_stop_action)
                    } else {
                        stringResource(R.string.scrape_task_close)
                    }
                    val secondaryLabel = if (progress.running) {
                        stringResource(R.string.scrape_task_background)
                    } else {
                        stringResource(R.string.scraping_missing)
                    }
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .then(
                                if (btnFocus == 0) Modifier.brandBackground(shape)
                                else Modifier.background(DeckColors.AccentSoft),
                            )
                            .soundClickable {
                                btnFocus = 0
                                if (progress.running) onCancel() else onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = primaryLabel,
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = if (btnFocus == 0) Color.White else DeckColors.Ruby,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .then(
                                if (btnFocus == 1) Modifier.brandBackground(shape)
                                else Modifier.background(DeckColors.Surface),
                            )
                            .border(0.7.dp, if (btnFocus == 1) Color.Transparent else DeckColors.Line, shape)
                            .soundClickable {
                                btnFocus = 1
                                if (progress.running) onDismiss() else onRestart()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = secondaryLabel,
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Medium),
                            color = if (btnFocus == 1) Color.White else DeckColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrapeStatCell(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = DeckType.metaLabel,
            color = DeckColors.TextSecondary,
        )
        Text(
            text = value,
            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
            color = DeckColors.TextPrimary,
        )
    }
}

/**
 * 手动搜索刮削弹窗：输入关键词 → 展示候选封面与元数据 → 一键应用。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualScrapeDialog(
    game: Game,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onSearch: suspend (
        keyword: String,
        onActiveSourcesChanged: (List<String>) -> Unit,
        onPartialResults: (List<ManualScrapeCandidate>) -> Unit,
    ) -> List<ManualScrapeCandidate>,
    onSuggest: (String) -> List<String> = { CjkAliases.suggestKeywords(it, limit = 8) },
    onApply: (ManualScrapeCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var keyword by remember(game.id) { mutableStateOf(game.title) }
    var fieldFocused by remember { mutableStateOf(false) }
    val suggestions = remember(keyword) { onSuggest(keyword) }
    var searching by remember { mutableStateOf(false) }
    var activeSourceIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchedOnce by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ManualScrapeCandidate>>(emptyList()) }
    var selectedIdx by remember { mutableIntStateOf(0) }
    var previewPair by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()
    val resolvedUrls = remember(results) { mutableStateMapOf<Int, String>() }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp

    fun applyAt(idx: Int) {
        val cand = results.getOrNull(idx) ?: return
        val workingUrl = resolvedUrls[idx] ?: cand.coverUrl
        onApply(if (workingUrl != cand.coverUrl) cand.copy(coverUrl = workingUrl) else cand)
    }

    LaunchedEffect(selectedIdx, results.size) {
        if (results.isEmpty() || selectedIdx !in results.indices) return@LaunchedEffect
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        val itemInfo = visible.firstOrNull { it.index == selectedIdx }
        val viewportH = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
        val fullyVisible = itemInfo != null &&
            itemInfo.offset >= info.viewportStartOffset &&
            (itemInfo.offset + itemInfo.size) <= info.viewportEndOffset
        if (!fullyVisible) {
            val firstVis = visible.firstOrNull()?.index ?: 0
            if (selectedIdx <= firstVis || (itemInfo != null && itemInfo.offset < info.viewportStartOffset)) {
                listState.animateScrollToItem(selectedIdx, scrollOffset = 0)
            } else {
                // 向下滚出底边：按当前可视区高度与实际行高，把选中行底边精确对齐到可视区底部
                val estItemH = itemInfo?.size ?: visible.firstOrNull()?.size ?: 180
                val bottomAlignedOffset = -(viewportH - estItemH).coerceAtLeast(0)
                listState.animateScrollToItem(selectedIdx, scrollOffset = bottomAlignedOffset)
            }
        }
    }

    fun dismissKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        runCatching { focusRequester.requestFocus() }
    }

    fun runSearch(overrideKeyword: String? = null) {
        val q = (overrideKeyword ?: keyword).trim()
        if (overrideKeyword != null) keyword = overrideKeyword
        dismissKeyboard()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = scope.launch {
            searching = true
            activeSourceIds = emptyList()
            results = emptyList()
            selectedIdx = 0
            try {
                val finalList = runCatching {
                    onSearch(
                        q,
                        { sources -> activeSourceIds = sources },
                        { partial ->
                            results = partial
                            if (selectedIdx >= partial.size) {
                                selectedIdx = (partial.size - 1).coerceAtLeast(0)
                            }
                        },
                    )
                }.getOrDefault(results)
                results = finalList
                if (selectedIdx >= finalList.size) {
                    selectedIdx = (finalList.size - 1).coerceAtLeast(0)
                }
            } finally {
                searchedOnce = true
                activeSourceIds = emptyList()
                searching = false
            }
        }
    }

    LaunchedEffect(game.id) {
        runCatching { focusRequester.requestFocus() }
        runSearch()
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
                        if (previewPair != null) {
                            previewPair = null
                        } else {
                            onDismiss()
                        }
                        true
                    }
                    previewPair != null && GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        val idx = previewPair?.first ?: selectedIdx
                        previewPair = null
                        applyAt(idx)
                        true
                    }
                    previewPair != null -> true
                    event.key == Key.DirectionDown && results.isNotEmpty() -> {
                        selectedIdx = (selectedIdx + 1).coerceAtMost(results.lastIndex)
                        true
                    }
                    event.key == Key.DirectionUp && results.isNotEmpty() -> {
                        selectedIdx = (selectedIdx - 1).coerceAtLeast(0)
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) && results.isNotEmpty() -> {
                        applyAt(selectedIdx)
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
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Text(
                text = stringResource(R.string.manual_scrape_title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                modifier = Modifier.padding(end = 36.dp),
            )
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeckColors.Surface)
                        .border(0.8.dp, DeckColors.Line, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    BasicTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        singleLine = true,
                        textStyle = DeckType.settingsValue.copy(color = DeckColors.TextPrimary),
                        cursorBrush = SolidColor(DeckColors.Ruby),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { fieldFocused = it.isFocused },
                    )
                }
                Text(
                    text = stringResource(R.string.manual_scrape_search_btn),
                    style = DeckType.menuButton,
                    color = Color.White,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .brandBackground(DeckShapes.menuButton)
                        .soundClickable { runSearch() }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }

            // 输入时实时显示紧凑单行补全建议，点一下立刻填入并搜索
            if (fieldFocused && suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(suggestions) { sug ->
                        Text(
                            text = sug,
                            style = DeckType.metaLabel.copy(fontWeight = FontWeight.Medium),
                            color = DeckColors.Ruby,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.AccentSoft)
                                .border(0.7.dp, DeckColors.Ruby.copy(alpha = 0.35f), DeckShapes.tabPill)
                                .soundClickable { runSearch(sug) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val activeSourceLabels = activeSourceIds.map { pid ->
                (ScraperCatalog.coverDescriptor(pid) ?: ScraperCatalog.metaDescriptor(pid))
                    ?.nameRes
                    ?.let { stringResource(it) }
                    ?: pid
            }
            val activeSourceSummary = activeSourceLabels.joinToString(" · ")

            when {
                searching && results.isEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                color = DeckColors.Ruby,
                                strokeWidth = 1.8.dp,
                                modifier = Modifier.size(14.dp),
                            )
                            if (activeSourceSummary.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.manual_scrape_searching_source, activeSourceSummary),
                                    style = DeckType.metaLabel,
                                    color = DeckColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                results.isEmpty() && searchedOnce -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.manual_scrape_empty),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                        )
                        if (suggestions.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.manual_scrape_suggestions_hint),
                                style = DeckType.metaLabel,
                                color = DeckColors.TextSecondary,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                for (sug in suggestions) {
                                    Text(
                                        text = sug,
                                        style = DeckType.metaLabel.copy(fontWeight = FontWeight.Medium),
                                        color = DeckColors.Ruby,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clip(DeckShapes.tabPill)
                                            .background(DeckColors.AccentSoft)
                                            .border(0.7.dp, DeckColors.Ruby.copy(alpha = 0.35f), DeckShapes.tabPill)
                                            .soundClickable { runSearch(sug) }
                                            .padding(horizontal = 11.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(results) { idx, cand ->
                            val hl = idx == selectedIdx
                            val allUrls = remember(cand) {
                                (listOfNotNull(cand.coverUrl) + cand.fallbackCoverUrls).distinct()
                            }
                            var urlIdx by remember(cand) { mutableIntStateOf(0) }
                            var retryTick by remember(cand) { mutableIntStateOf(0) }
                            val activeUrl = allUrls.getOrNull(urlIdx)
                            val canRetry = allUrls.isNotEmpty() && activeUrl == null
                            val canPreview = !activeUrl.isNullOrBlank() && resolvedUrls[idx] != null
                            // 固定每行缩略图尺寸与内边距，避免焦点切换时上一行缩小/下一行放大引发整列高度抖动
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (hl) DeckColors.AccentSoft else DeckColors.Surface)
                                    .brandBorder(active = hl, shape = RoundedCornerShape(10.dp), activeWidth = 1.4.dp, inactiveWidth = 0.6.dp)
                                    .soundClickable {
                                        selectedIdx = idx
                                        applyAt(idx)
                                    }
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 56.dp, height = 74.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(DeckColors.DialogBg)
                                        .then(
                                            when {
                                                canRetry -> Modifier
                                                    .border(0.8.dp, DeckColors.Ruby.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                                                    .soundClickable {
                                                        urlIdx = 0
                                                        retryTick += 1
                                                    }
                                                canPreview -> Modifier.soundClickable {
                                                    selectedIdx = idx
                                                    previewPair = idx to (resolvedUrls[idx] ?: activeUrl)
                                                }
                                                else -> Modifier
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!activeUrl.isNullOrBlank()) {
                                        key(retryTick, activeUrl) {
                                            AsyncImage(
                                                model = activeUrl,
                                                contentDescription = cand.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                                onState = { st ->
                                                    when (st) {
                                                        is AsyncImagePainter.State.Success -> {
                                                            resolvedUrls[idx] = activeUrl
                                                        }
                                                        is AsyncImagePainter.State.Error -> {
                                                            if (urlIdx + 1 < allUrls.size) {
                                                                urlIdx += 1
                                                            } else {
                                                                urlIdx = -1
                                                            }
                                                        }
                                                        else -> Unit
                                                    }
                                                },
                                            )
                                        }
                                    } else if (canRetry) {
                                        DeckIcon(DeckIcons.Refresh, tint = DeckColors.Ruby, size = 18.dp)
                                    } else {
                                        Text(
                                            text = "—",
                                            style = DeckType.metaLabel,
                                            color = DeckColors.TextSecondary,
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = cand.title,
                                        style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val metaSummary = listOfNotNull(
                                        cand.providerId,
                                        cand.metadata.releaseDate,
                                        cand.metadata.developer ?: cand.metadata.publisher,
                                        cand.metadata.genre,
                                    ).joinToString(" · ")
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        if (cand.matchedByHash) {
                                            Text(
                                                text = "Hash",
                                                style = DeckType.metaLabel.copy(fontWeight = FontWeight.Bold),
                                                color = DeckColors.Ruby,
                                                maxLines = 1,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(DeckColors.Ruby.copy(alpha = 0.14f))
                                                    .border(0.6.dp, DeckColors.Ruby.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                            )
                                        }
                                        Text(
                                            text = metaSummary,
                                            style = DeckType.metaLabel,
                                            color = DeckColors.TextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.manual_scrape_apply_btn),
                                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (hl) Color.White else DeckColors.Ruby,
                                    modifier = Modifier
                                        .clip(DeckShapes.tabPill)
                                        .then(
                                            if (hl) Modifier.brandBackground(DeckShapes.tabPill)
                                            else Modifier.background(DeckColors.Ruby.copy(alpha = 0.12f)),
                                        )
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                    if (searching && activeSourceSummary.isNotBlank()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                color = DeckColors.Ruby,
                                strokeWidth = 1.6.dp,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = stringResource(R.string.manual_scrape_searching_source, activeSourceSummary),
                                style = DeckType.metaLabel,
                                color = DeckColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // 点击缩略图弹出高清大图预览浮层
        previewPair?.let { (pIdx, pUrl) ->
            val pCand = results.getOrNull(pIdx)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .scrimTapDismiss { previewPair = null },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DeckColors.DialogBg)
                        .border(0.8.dp, DeckColors.Line, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = (LocalConfiguration.current.screenHeightDp * 0.60f).dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeckColors.Surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = pUrl,
                            contentDescription = pCand?.title.orEmpty(),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    val previewTitle = pCand?.title?.takeIf { it.isNotBlank() }
                    if (previewTitle != null) {
                        Text(
                            text = previewTitle,
                            style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                            color = DeckColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.menu_cancel),
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Medium),
                            color = DeckColors.TextSecondary,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.Surface)
                                .border(0.7.dp, DeckColors.Line, DeckShapes.tabPill)
                                .soundClickable { previewPair = null }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                        )
                        Text(
                            text = stringResource(R.string.manual_scrape_apply_btn),
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .brandBackground(DeckShapes.tabPill)
                                .soundClickable {
                                    previewPair = null
                                    applyAt(pIdx)
                                }
                                .padding(horizontal = 18.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}
