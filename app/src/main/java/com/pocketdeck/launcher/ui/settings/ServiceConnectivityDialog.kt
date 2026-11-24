package com.pocketdeck.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.ServiceConnectivityTester
import com.pocketdeck.launcher.data.ServiceProbeResult
import com.pocketdeck.launcher.data.ServiceProbeStatus
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.components.CircularProgressIndicator
import com.pocketdeck.launcher.ui.components.DeckDialogSize
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.Text
import com.pocketdeck.launcher.ui.components.requestFocusSafely
import com.pocketdeck.launcher.ui.components.scrimTapDismiss
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandBackground
import kotlinx.coroutines.launch

/**
 * 服务连通性测试弹窗：并发探测各刮削源与更新服务的可达性与延迟，支持手柄滚动与重测。
 */
@Composable
internal fun ServiceConnectivityDialog(
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val isOnline by NetworkStatus.online.collectAsState()
    var results by remember { mutableStateOf(ServiceConnectivityTester.initialItems()) }
    var testing by remember { mutableStateOf(false) }
    var runTick by remember { mutableIntStateOf(0) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    LaunchedEffect(Unit) {
        focusRequester.requestFocusSafely()
    }

    LaunchedEffect(runTick) {
        testing = true
        results = ServiceConnectivityTester.initialItems()
        try {
            ServiceConnectivityTester.probeAll(context) { updated ->
                results = results.map { if (it.id == updated.id) updated else it }
            }
        } finally {
            testing = false
        }
    }

    val doneCount = results.count { it.status != ServiceProbeStatus.Pending }
    val reachableCount = results.count {
        it.status == ServiceProbeStatus.Reachable || it.status == ServiceProbeStatus.Slow
    }
    val unreachableCount = results.count {
        it.status == ServiceProbeStatus.Unreachable ||
            it.status == ServiceProbeStatus.Timeout ||
            it.status == ServiceProbeStatus.Offline
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
                val isInitialPress = event.nativeKeyEvent.repeatCount == 0
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        if (isInitialPress) onDismiss()
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        scope.launch {
                            val next = (listState.firstVisibleItemIndex + 2).coerceAtMost(results.lastIndex)
                            listState.animateScrollToItem(next)
                        }
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        scope.launch {
                            val prev = (listState.firstVisibleItemIndex - 2).coerceAtLeast(0)
                            listState.animateScrollToItem(prev)
                        }
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        if (isInitialPress && !testing) {
                            runTick++
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
                .widthIn(max = DeckDialogSize.WidthL)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.conn_test_title),
                    style = DeckType.menuTitle.copy(fontWeight = FontWeight.Bold),
                    color = DeckColors.TextPrimary,
                    modifier = Modifier.padding(end = 36.dp),
                )

                val summaryText = when {
                    !isOnline -> stringResource(R.string.conn_test_offline_banner)
                    testing -> stringResource(R.string.conn_test_progress_fmt, doneCount, results.size)
                    else -> stringResource(R.string.conn_test_summary_fmt, reachableCount, unreachableCount)
                }
                val summaryColor = if (!isOnline) DeckColors.StatusDanger else DeckColors.TextSecondary
                Text(
                    text = summaryText,
                    style = DeckType.metaLabel,
                    color = summaryColor,
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeckColors.Surface)
                        .border(0.6.dp, DeckColors.Line, RoundedCornerShape(12.dp)),
                ) {
                    items(results, key = { it.id }) { item ->
                        ServiceProbeRow(item)
                    }
                }

                Spacer(Modifier.height(2.dp))
                val btnShape = RoundedCornerShape(11.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(btnShape)
                        .then(
                            if (testing) {
                                Modifier
                                    .background(DeckColors.SurfaceAlt)
                                    .border(0.7.dp, DeckColors.Line, btnShape)
                            } else {
                                Modifier.brandBackground(btnShape)
                            },
                        )
                        .then(if (!testing) Modifier.soundClickable { runTick++ } else Modifier)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.8.dp,
                                color = DeckColors.TextSecondary,
                            )
                        }
                        Text(
                            text = stringResource(if (testing) R.string.conn_test_running else R.string.conn_test_retest),
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = if (testing) DeckColors.TextSecondary else Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceProbeRow(item: ServiceProbeResult) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(item.nameRes),
                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Medium),
                color = DeckColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.host,
                style = DeckType.metaLabel,
                color = DeckColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val (pillText, pillColor) = when (item.status) {
            ServiceProbeStatus.Pending -> stringResource(R.string.conn_status_testing) to DeckColors.TextSecondary
            ServiceProbeStatus.Reachable -> stringResource(R.string.conn_status_ok_fmt, item.latencyMs) to DeckColors.StatusSuccess
            ServiceProbeStatus.Slow -> stringResource(R.string.conn_status_slow_fmt, item.latencyMs) to DeckColors.StatusWarning
            ServiceProbeStatus.Timeout -> stringResource(R.string.conn_status_timeout) to DeckColors.StatusDanger
            ServiceProbeStatus.Unreachable -> stringResource(R.string.conn_status_unreachable) to DeckColors.StatusDanger
            ServiceProbeStatus.Offline -> stringResource(R.string.conn_status_offline) to DeckColors.StatusDanger
        }

        Row(
            Modifier
                .clip(CircleShape)
                .background(pillColor.copy(alpha = 0.13f))
                .padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (item.status == ServiceProbeStatus.Pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = pillColor,
                )
            }
            Text(
                text = pillText,
                style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
                color = pillColor,
            )
        }
    }
}
