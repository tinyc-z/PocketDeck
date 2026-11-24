package com.pocketdeck.launcher.ui.components

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalDensity
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
import com.pocketdeck.launcher.data.ScrapeMissingSummary
import com.pocketdeck.launcher.data.ScrapeProgress
import com.pocketdeck.launcher.data.ScraperCatalog
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.components.icons.HomeIcons
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.blockOverlayTouchThrough
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
                        .background(if (progress.cancelling) DeckColors.SurfaceAlt else DeckColors.Ruby)
                        .then(if (!progress.cancelling) Modifier.soundClickable(onClick = onCancel) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(if (progress.cancelling) R.string.scrape_task_cancelling else R.string.scrape_task_stop),
                        style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
                        color = if (progress.cancelling) DeckColors.TextSecondary else Color.White,
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
    missingSummary: ScrapeMissingSummary? = null,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onCancel: () -> Unit,
    onRestart: () -> Unit,
    onViewLogs: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val isIdle = !progress.running && progress.total == 0
    val isRunning = progress.running
    val isCancelling = progress.running && progress.cancelling
    val isFinished = !progress.running && progress.total > 0
    val canStartInIdle = isIdle && (missingSummary?.pendingTotal ?: 0) > 0
    val canContinueAfterFinish = isFinished && (missingSummary == null || missingSummary.pendingTotal > 0)
    val showDualButtons = isRunning || (isFinished && onViewLogs != null)

    // 运行中/正在终止默认焦点放在右侧「转入后台」，避免误触终止；已结束有双按钮时默认聚焦右侧主按钮；单按钮态聚焦 0
    val defaultFocus = when {
        isRunning -> 1
        showDualButtons -> 1
        else -> 0
    }
    var btnFocus by remember(isRunning, isCancelling, isIdle, showDualButtons) {
        mutableIntStateOf(defaultFocus)
    }
    val fraction = if (progress.total > 0) {
        (progress.done.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(Unit) {
        focusRequester.requestFocusSafely()
    }

    val dangerTint = DeckColors.StatusDanger
    val okTint = DeckColors.StatusSuccess
    val warnTint = DeckColors.StatusWarning

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .then(if (!isRunning) Modifier.scrimTapDismiss(onDismiss) else Modifier.blockOverlayTouchThrough())
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isInitialPress = event.nativeKeyEvent.repeatCount == 0
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        if (isInitialPress) onDismiss()
                        true
                    }
                    event.key == Key.DirectionLeft || event.key == Key.DirectionUp -> {
                        if (showDualButtons && !isCancelling) btnFocus = 0
                        true
                    }
                    event.key == Key.DirectionRight || event.key == Key.DirectionDown -> {
                        if (showDualButtons) btnFocus = 1
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        if (isInitialPress) {
                            when {
                                isCancelling -> onDismiss()
                                isRunning -> if (btnFocus == 0) onCancel() else onDismiss()
                                canStartInIdle -> onRestart()
                                isIdle -> onDismiss()
                                showDualButtons -> {
                                    if (btnFocus == 0) onViewLogs?.invoke()
                                    else if (canContinueAfterFinish) onRestart() else onDismiss()
                                }
                                canContinueAfterFinish -> onRestart()
                                else -> onDismiss()
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
            showCloseButton = !isRunning,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = DeckDialogSize.WidthM)
                .fillMaxWidth(),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = if (isRunning) 0.dp else 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isRunning) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.scraping_missing),
                        style = DeckType.menuTitle.copy(fontWeight = FontWeight.Bold),
                        color = DeckColors.TextPrimary,
                    )
                    val (pillText, pillBg, pillFg) = when {
                        isCancelling -> Triple(
                            stringResource(R.string.scrape_task_cancelling),
                            warnTint.copy(alpha = 0.14f),
                            warnTint,
                        )
                        isRunning -> Triple(
                            stringResource(R.string.scrape_task_pill_running_fmt, progress.percent),
                            DeckColors.Ruby.copy(alpha = 0.13f),
                            DeckColors.Ruby,
                        )
                        isIdle && missingSummary == null -> Triple(
                            "…",
                            DeckColors.SurfaceAlt,
                            DeckColors.TextSecondary,
                        )
                        isIdle && (missingSummary?.pendingTotal ?: 0) > 0 -> Triple(
                            stringResource(R.string.scrape_task_pill_pending_fmt, missingSummary!!.pendingTotal),
                            DeckColors.Ruby.copy(alpha = 0.13f),
                            DeckColors.Ruby,
                        )
                        isIdle -> Triple(
                            stringResource(R.string.scrape_task_pill_complete),
                            okTint.copy(alpha = 0.14f),
                            okTint,
                        )
                        progress.cancelled -> Triple(
                            stringResource(R.string.scrape_task_pill_cancelled_fmt, progress.percent),
                            DeckColors.SurfaceAlt,
                            DeckColors.TextSecondary,
                        )
                        else -> Triple(
                            stringResource(R.string.scrape_task_pill_done),
                            okTint.copy(alpha = 0.14f),
                            okTint,
                        )
                    }
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(pillBg)
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (isRunning && !isCancelling) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(pillFg),
                            )
                        }
                        Text(
                            text = pillText,
                            style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = pillFg,
                        )
                    }
                }

                val statusText = when {
                    isCancelling -> stringResource(R.string.scrape_task_cancelling_desc)
                    isRunning -> null
                    isIdle && missingSummary == null -> stringResource(R.string.scrape_task_scanning)
                    isIdle && missingSummary != null && missingSummary.totalGames == 0 ->
                        stringResource(R.string.scrape_task_empty_library)
                    isIdle && missingSummary != null && missingSummary.pendingTotal == 0 ->
                        stringResource(R.string.scrape_task_all_complete_fmt, missingSummary.totalGames)
                    isIdle && missingSummary != null ->
                        stringResource(R.string.scrape_task_ready_fmt, missingSummary.pendingTotal, missingSummary.totalGames)
                    else -> null
                }
                if (statusText != null) {
                    Text(
                        text = statusText,
                        style = DeckType.metaLabel,
                        color = DeckColors.TextSecondary,
                    )
                }

                if (!isIdle) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(DeckShapes.tabPill),
                        color = when {
                            isCancelling -> DeckColors.TextSecondary
                            isRunning -> DeckColors.Ruby
                            !progress.cancelled -> okTint
                            else -> DeckColors.TextSecondary
                        },
                        trackColor = DeckColors.SurfaceAlt,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }

                if (isRunning && !isCancelling && progress.currentTitle.isNotBlank()) {
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeckColors.Surface)
                        .border(0.6.dp, DeckColors.Line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (isIdle) {
                        val pendingText = missingSummary?.pendingTotal?.toString() ?: "…"
                        val coverText = missingSummary?.missingCoverCount?.toString() ?: "…"
                        val metaText = missingSummary?.missingMetaCount?.toString() ?: "…"
                        ScrapeStatCell(
                            label = stringResource(R.string.scrape_task_stat_pending),
                            value = pendingText,
                            valueColor = when {
                                missingSummary == null -> DeckColors.TextPrimary
                                missingSummary.pendingTotal > 0 -> DeckColors.Ruby
                                else -> okTint
                            },
                        )
                        ScrapeStatCell(stringResource(R.string.scrape_task_stat_missing_cover), coverText)
                        ScrapeStatCell(stringResource(R.string.scrape_task_stat_missing_meta), metaText)
                    } else {
                        ScrapeStatCell(stringResource(R.string.scrape_task_stat_processed), "${progress.done} / ${progress.total}")
                        ScrapeStatCell(
                            label = stringResource(R.string.scrape_task_stat_updated),
                            value = "${progress.updatedCount}",
                            valueColor = if (progress.updatedCount > 0) okTint else DeckColors.TextPrimary,
                        )
                        ScrapeStatCell(stringResource(R.string.scrape_task_stat_missed), "${progress.failedCount}")
                    }
                }

                if (isFinished && missingSummary != null && missingSummary.pendingTotal > 0) {
                    Text(
                        text = stringResource(
                            R.string.scrape_task_remaining_fmt,
                            missingSummary.pendingTotal,
                            missingSummary.missingCoverCount,
                            missingSummary.missingMetaCount,
                        ),
                        style = DeckType.metaLabel,
                        color = DeckColors.TextSecondary,
                    )
                }

                val shape = RoundedCornerShape(11.dp)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isRunning) {
                        // 左：终止任务（柔红警示态；点击后立即锁定为「正在终止…」不可连点）
                        val stopFocused = btnFocus == 0 && !isCancelling
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(shape)
                                .then(
                                    when {
                                        isCancelling -> Modifier
                                            .background(DeckColors.SurfaceAlt)
                                            .border(0.7.dp, DeckColors.Line, shape)
                                        stopFocused -> Modifier
                                            .background(dangerTint)
                                        else -> Modifier
                                            .background(dangerTint.copy(alpha = 0.11f))
                                            .border(0.8.dp, dangerTint.copy(alpha = 0.32f), shape)
                                    },
                                )
                                .then(
                                    if (!isCancelling) Modifier.soundClickable {
                                        btnFocus = 1
                                        onCancel()
                                    } else Modifier,
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (isCancelling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 1.8.dp,
                                        color = DeckColors.TextSecondary,
                                    )
                                }
                                Text(
                                    text = if (isCancelling) {
                                        stringResource(R.string.scrape_task_cancelling)
                                    } else {
                                        stringResource(R.string.scrape_task_stop_action)
                                    },
                                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                    color = when {
                                        isCancelling -> DeckColors.TextSecondary
                                        stopFocused -> Color.White
                                        else -> dangerTint
                                    },
                                )
                            }
                        }

                        // 右：转入后台（正向主按钮）
                        val bgFocused = btnFocus == 1 || isCancelling
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(shape)
                                .then(
                                    if (bgFocused) Modifier.brandBackground(shape)
                                    else Modifier
                                        .background(DeckColors.Surface)
                                        .border(0.7.dp, DeckColors.Line, shape),
                                )
                                .soundClickable {
                                    btnFocus = 1
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.scrape_task_background_short),
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = if (bgFocused) Color.White else DeckColors.TextPrimary,
                            )
                        }
                    } else if (isIdle) {
                        // 待开始态：单主按钮（有缺失显示「开始刮削（N 款）」，0 缺失显示「完成」）
                        val label = when {
                            canStartInIdle -> stringResource(R.string.scrape_task_start_fmt, missingSummary!!.pendingTotal)
                            else -> stringResource(R.string.scrape_task_finish)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .brandBackground(shape)
                                .soundClickable {
                                    if (canStartInIdle) onRestart() else onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                        }
                    } else {
                        // 已终止 / 已完成态：左「查看日志」（次按钮）+ 右「继续刮削 / 完成」（主按钮）
                        if (onViewLogs != null) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .then(
                                        if (btnFocus == 0) Modifier.brandBackground(shape)
                                        else Modifier
                                            .background(DeckColors.Surface)
                                            .border(0.7.dp, DeckColors.Line, shape),
                                    )
                                    .soundClickable {
                                        btnFocus = 0
                                        onViewLogs()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.scrape_task_view_logs),
                                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Medium),
                                    color = if (btnFocus == 0) Color.White else DeckColors.TextPrimary,
                                )
                            }
                        }
                        val rightFocused = !showDualButtons || btnFocus == 1
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(shape)
                                .then(
                                    if (rightFocused) Modifier.brandBackground(shape)
                                    else Modifier
                                        .background(DeckColors.Surface)
                                        .border(0.7.dp, DeckColors.Line, shape),
                                )
                                .soundClickable {
                                    btnFocus = 1
                                    if (canContinueAfterFinish) onRestart() else onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (canContinueAfterFinish) {
                                    stringResource(R.string.scrape_task_continue)
                                } else {
                                    stringResource(R.string.scrape_task_finish)
                                },
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = if (rightFocused) Color.White else DeckColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrapeStatCell(
    label: String,
    value: String,
    valueColor: Color = DeckColors.TextPrimary,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = DeckType.metaLabel,
            color = DeckColors.TextSecondary,
        )
        Text(
            text = value,
            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
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
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val isOnline by com.pocketdeck.launcher.data.NetworkStatus.online.collectAsState()
    var keyword by remember(game.id) { mutableStateOf(game.title) }
    var fieldFocused by remember { mutableStateOf(false) }
    val suggestions = remember(keyword) { onSuggest(keyword) }
    // 0 = 输入框（高亮但不强弹键盘），1 = 提示词胶囊行，2 = 搜索结果列表
    var focusZone by remember(game.id) { mutableIntStateOf(2) }
    var sugIdx by remember(game.id) { mutableIntStateOf(0) }
    val sugListState = rememberLazyListState()
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

    LaunchedEffect(suggestions.size) {
        if (suggestions.isEmpty()) {
            sugIdx = 0
            if (focusZone == 1) focusZone = if (results.isNotEmpty()) 2 else 0
        } else if (sugIdx >= suggestions.size) {
            sugIdx = suggestions.lastIndex
        }
    }

    LaunchedEffect(sugIdx, focusZone, suggestions.size) {
        if (focusZone == 1 && sugIdx in suggestions.indices) {
            sugListState.animateScrollToItem(sugIdx)
        }
    }

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

    fun dismissKeyboard(targetZone: Int? = null) {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        focusRequester.requestFocusSafely()
        if (targetZone != null) {
            focusZone = targetZone
        }
    }

    fun runSearch(overrideKeyword: String? = null) {
        val q = (overrideKeyword ?: keyword).trim()
        if (overrideKeyword != null) keyword = overrideKeyword
        dismissKeyboard(targetZone = 2)
        if (q.isEmpty() || !isOnline) return
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

    val searchFieldFr = remember { FocusRequester() }
    val keyboardActive = imeVisible || fieldFocused
    androidx.activity.compose.BackHandler(enabled = previewPair != null || keyboardActive) {
        when {
            previewPair != null -> previewPair = null
            keyboardActive -> dismissKeyboard(targetZone = if (suggestions.isNotEmpty()) 1 else 2)
        }
    }

    LaunchedEffect(game.id) {
        focusRequester.requestFocusSafely()
        runSearch()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss {
                if (keyboardActive) {
                    dismissKeyboard(targetZone = if (suggestions.isNotEmpty()) 1 else 2)
                } else {
                    onDismiss()
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isInitialPress = event.nativeKeyEvent.repeatCount == 0
                if (fieldFocused) {
                    return@onPreviewKeyEvent when {
                        GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                            if (isInitialPress) dismissKeyboard(targetZone = if (suggestions.isNotEmpty()) 1 else 2)
                            true
                        }
                        event.key == Key.DirectionDown -> {
                            dismissKeyboard(targetZone = if (suggestions.isNotEmpty()) 1 else 2)
                            true
                        }
                        else -> false // 放行键盘输入与 Enter 提交搜索，避免误应用旧结果
                    }
                }
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        if (isInitialPress) {
                            when {
                                previewPair != null -> previewPair = null
                                imeVisible -> dismissKeyboard(targetZone = if (suggestions.isNotEmpty()) 1 else 2)
                                else -> onDismiss()
                            }
                        }
                        true
                    }
                    previewPair != null && GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        if (isInitialPress) {
                            val idx = previewPair?.first ?: selectedIdx
                            previewPair = null
                            applyAt(idx)
                        }
                        true
                    }
                    previewPair != null -> true
                    focusZone == 0 -> when {
                        GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                            if (isInitialPress) {
                                searchFieldFr.requestFocusSafely()
                                keyboardController?.show()
                            }
                            true
                        }
                        event.key == Key.DirectionDown -> {
                            focusZone = when {
                                suggestions.isNotEmpty() -> 1
                                results.isNotEmpty() -> 2
                                else -> 0
                            }
                            true
                        }
                        else -> false
                    }
                    focusZone == 1 -> when {
                        event.key == Key.DirectionLeft && suggestions.isNotEmpty() -> {
                            sugIdx = (sugIdx - 1).coerceAtLeast(0)
                            true
                        }
                        event.key == Key.DirectionRight && suggestions.isNotEmpty() -> {
                            sugIdx = (sugIdx + 1).coerceAtMost(suggestions.lastIndex)
                            true
                        }
                        event.key == Key.DirectionUp -> {
                            focusZone = 0
                            true
                        }
                        event.key == Key.DirectionDown -> {
                            if (results.isNotEmpty()) focusZone = 2
                            true
                        }
                        GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) && suggestions.isNotEmpty() -> {
                            if (isInitialPress) {
                                suggestions.getOrNull(sugIdx)?.let { runSearch(it) }
                            }
                            true
                        }
                        else -> false
                    }
                    event.key == Key.DirectionDown && results.isNotEmpty() -> {
                        selectedIdx = (selectedIdx + 1).coerceAtMost(results.lastIndex)
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        if (results.isEmpty() || selectedIdx <= 0) {
                            focusZone = if (suggestions.isNotEmpty()) 1 else 0
                        } else {
                            selectedIdx = (selectedIdx - 1).coerceAtLeast(0)
                        }
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) && results.isNotEmpty() -> {
                        if (isInitialPress) applyAt(selectedIdx)
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

            val inputHighlighted = fieldFocused || focusZone == 0
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeckColors.Surface)
                        .brandBorder(
                            active = inputHighlighted,
                            shape = RoundedCornerShape(10.dp),
                            activeWidth = 1.4.dp,
                            inactiveWidth = 0.8.dp,
                        )
                        .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                            .weight(1f)
                            .focusRequester(searchFieldFr)
                            .onFocusChanged {
                                fieldFocused = it.isFocused
                                if (it.isFocused) focusZone = 0
                            },
                    )
                    if (keyword.isNotEmpty()) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(DeckColors.SurfaceAlt)
                                .soundClickable {
                                    keyword = ""
                                    searchFieldFr.requestFocusSafely()
                                    keyboardController?.show()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            DeckIcon(DeckIcons.Close, size = 12.dp, tint = DeckColors.TextSecondary)
                        }
                    }
                    if (keyboardActive) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(DeckColors.AccentSoft)
                                .soundClickable {
                                    dismissKeyboard(targetZone = if (suggestions.isNotEmpty()) 1 else 2)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            DeckIcon(HomeIcons.ChevronDown, size = 14.dp, tint = DeckColors.Ruby)
                        }
                    }
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

            // 提示词常驻（不再随收起键盘消失），支持触屏点击与手柄 ←/→ 选择后按 A 搜索
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    state = sugListState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(suggestions) { idx, sug ->
                        val sugHl = focusZone == 1 && idx == sugIdx
                        Text(
                            text = sug,
                            style = DeckType.metaLabel.copy(fontWeight = if (sugHl) FontWeight.SemiBold else FontWeight.Medium),
                            color = if (sugHl) Color.White else DeckColors.Ruby,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .then(
                                    if (sugHl) Modifier.brandBackground(DeckShapes.tabPill)
                                    else Modifier
                                        .background(DeckColors.AccentSoft)
                                        .border(0.7.dp, DeckColors.Ruby.copy(alpha = 0.35f), DeckShapes.tabPill),
                                )
                                .soundClickable {
                                    sugIdx = idx
                                    runSearch(sug)
                                }
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
                !isOnline && results.isEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.network_offline_action_toast),
                            style = DeckType.metaLabel,
                            color = DeckColors.StatusDanger,
                        )
                    }
                }
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
                            val hl = focusZone == 2 && idx == selectedIdx
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

        // 点击缩略图弹出与游戏详情统一的全屏高清大图预览浮层
        previewPair?.let { (pIdx, pUrl) ->
            val pCand = results.getOrNull(pIdx)
            FullscreenCoverLightbox(
                model = pUrl,
                title = pCand?.title,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                actionLabel = stringResource(R.string.manual_scrape_apply_btn),
                onAction = {
                    previewPair = null
                    applyAt(pIdx)
                },
                onDismiss = { previewPair = null },
            )
        }
    }
}
