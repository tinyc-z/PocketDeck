package com.pocketdeck.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.mutableIntStateOf
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
 * 轻量刮削进度条（用于游戏库顶栏与设置 → 封面与信息）。
 */
@Composable
fun ScrapeProgressBanner(
    progress: ScrapeProgress,
    modifier: Modifier = Modifier,
) {
    if (!progress.running || progress.total <= 0) return
    val fraction = (progress.done.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DeckColors.AccentSoft)
            .border(0.7.dp, DeckColors.Ruby.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.scrape_progress_fmt, progress.done, progress.total),
                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                color = DeckColors.Ruby,
            )
            if (progress.currentTitle.isNotBlank()) {
                Text(
                    text = progress.currentTitle,
                    style = DeckType.metaLabel,
                    color = DeckColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (progress.failedCount > 0) {
                Text(
                    text = stringResource(R.string.scrape_progress_failed_fmt, progress.failedCount),
                    style = DeckType.metaLabel,
                    color = DeckColors.TextSecondary,
                )
            }
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(DeckShapes.tabPill),
            color = DeckColors.Ruby,
            trackColor = DeckColors.DialogBg,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/**
 * 手动搜索刮削弹窗：输入关键词 → 展示候选封面与元数据 → 一键应用。
 */
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
    onApply: (ManualScrapeCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var keyword by remember(game.id) { mutableStateOf(game.title) }
    var searching by remember { mutableStateOf(false) }
    var activeSourceIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchedOnce by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ManualScrapeCandidate>>(emptyList()) }
    var selectedIdx by remember { mutableIntStateOf(0) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()
    val resolvedUrls = remember(results) { mutableMapOf<Int, String>() }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp

    fun applyAt(idx: Int) {
        val cand = results.getOrNull(idx) ?: return
        val workingUrl = resolvedUrls[idx] ?: cand.coverUrl
        onApply(if (workingUrl != cand.coverUrl) cand.copy(coverUrl = workingUrl) else cand)
    }

    LaunchedEffect(selectedIdx, results.size) {
        if (results.isNotEmpty() && selectedIdx in results.indices) {
            listState.animateScrollToItem(selectedIdx)
        }
    }

    fun dismissKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        runCatching { focusRequester.requestFocus() }
    }

    fun runSearch() {
        val q = keyword.trim()
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
                        onDismiss()
                        true
                    }
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
                        modifier = Modifier.fillMaxWidth(),
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
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.manual_scrape_empty),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                        )
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
                            val activeUrl = allUrls.getOrNull(urlIdx)
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
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 48.dp, height = 64.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DeckColors.DialogBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!activeUrl.isNullOrBlank()) {
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
                                    } else {
                                        DeckIcon(DeckIcons.Image, tint = DeckColors.TextSecondary, size = 18.dp)
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
                                    Text(
                                        text = metaSummary,
                                        style = DeckType.metaLabel,
                                        color = DeckColors.TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
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
    }
}
