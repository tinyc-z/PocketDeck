package com.pocketdeck.launcher.ui.lan

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.lan.IncomingShareRequest
import com.pocketdeck.launcher.lan.LanPeer
import com.pocketdeck.launcher.lan.LanServerState
import com.pocketdeck.launcher.lan.ShareProtocol
import com.pocketdeck.launcher.lan.ShareTransferState
import com.pocketdeck.launcher.lan.TransferDirection
import com.pocketdeck.launcher.lan.TransferPhase
import com.pocketdeck.launcher.ui.components.gamepadClickable
import com.pocketdeck.launcher.ui.components.scrimTapDismiss
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.settings.NameDialog
import com.pocketdeck.launcher.ui.settings.NameDialogSpec
import com.pocketdeck.launcher.ui.settings.NameDialogState
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * 局域网服务与游戏快传控制面板：
 * - 可在游戏管理器顶部或首页点击状态栏标志弹出
 * - 独立控制「游戏快传」与「Web 网页管理」两个服务
 * - 支持多网卡切换（自动屏蔽 TUN/VPN）、本机可爱昵称修改、刷新动态密码
 */
@Composable
fun LanServiceControlDialog(
    state: LanServerState,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onRefreshInterfaces: () -> Unit,
    onSelectInterface: (String) -> Unit,
    onRenameDevice: (String) -> Unit,
    onRandomizeDeviceName: () -> Unit,
    onToggleQuickShare: (Boolean) -> Unit,
    onToggleLocalSendCompat: (Boolean) -> Unit = {},
    onScanPeers: () -> Unit,
    onToggleWebServer: (Boolean) -> Unit,
    onRefreshWebPassword: () -> Unit,
    onKickWebClient: (String) -> Unit = {},
    onOpenQuickShareScreen: (() -> Unit)? = null,
    onAcceptIncoming: (String, File) -> Unit = { _, _ -> },
    onRejectIncoming: (String) -> Unit = {},
    onCancelTransfer: () -> Unit = {},
    onDismissTransferBanner: () -> Unit = {},
    onStopAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val closeFr = remember { FocusRequester() }
    val shareRowFr = remember { FocusRequester() }
    val acceptBtnFr = remember { FocusRequester() }
    var editingName by remember { mutableStateOf(false) }
    var incomingDirIdx by remember(state.incomingRequest?.sessionId) { mutableIntStateOf(0) }
    val sdPath = remember { Environment.getExternalStorageDirectory().path }
    val renameTitle = stringResource(R.string.lan_device_name)
    val nameSpec = remember(editingName, state.deviceName) {
        if (editingName) NameDialogSpec(renameTitle, state.deviceName) { onRenameDevice(it) } else null
    }
    val nameState = remember(nameSpec) { nameSpec?.let { NameDialogState(it) } }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.94f).dp
    val scrollState = rememberScrollState()
    var wasWebRunning by remember { mutableStateOf(state.webRunning) }

    LaunchedEffect(state.webRunning) {
        val turnedOn = !wasWebRunning && state.webRunning
        wasWebRunning = state.webRunning
        if (turnedOn) {
            delay(80)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    LaunchedEffect(editingName, state.incomingRequest?.sessionId) {
        if (!editingName) {
            val targetFr = if (state.incomingRequest != null) acceptBtnFr else shareRowFr
            runCatching { targetFr.requestFocus() }.onFailure { runCatching { closeFr.requestFocus() } }
        }
    }
    LaunchedEffect(Unit) {
        onRefreshInterfaces()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss(onDismiss)
            .onPreviewKeyEvent { event ->
                if (editingName) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings)) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(580.dp)
                .heightIn(max = maxHeight)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "📡  ${stringResource(R.string.lan_service_title)}",
                    style = DeckType.menuTitle,
                    color = DeckColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (state.anyRunning) {
                    Text(
                        stringResource(R.string.lan_stop_all),
                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFDC2626),
                        modifier = Modifier
                            .clip(DeckShapes.tabPill)
                            .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                            .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill, onClick = onStopAll)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Text(
                    stringResource(R.string.prop_close),
                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                    color = DeckColors.Ruby,
                    modifier = Modifier
                        .clip(DeckShapes.tabPill)
                        .background(DeckColors.Ruby.copy(alpha = 0.12f))
                        .gamepadClickable(swapAbxy, keyBindings, focusRequester = closeFr, shape = DeckShapes.tabPill, onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DeckColors.DialogBorder)
            Spacer(Modifier.height(8.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 0-A) 接收确认卡片（仅在管理/接收页面显示与确认，不在全局乱弹窗）
                state.incomingRequest?.let { req ->
                    val chosenDir = req.availableDirs.getOrElse(incomingDirIdx) { req.defaultTargetDir }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DeckColors.Ruby.copy(alpha = 0.08f))
                            .border(1.5.dp, DeckColors.Ruby, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "📥  ${stringResource(R.string.lan_incoming_title)}",
                            style = DeckType.settingsValue.copy(fontWeight = FontWeight.Bold),
                            color = DeckColors.Ruby,
                        )
                        Text(
                            stringResource(R.string.lan_incoming_from, req.senderName, req.senderIp),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                        )
                        val fileSummary = if (req.fileCount > 1) {
                            "${stringResource(R.string.lan_multi_file_summary, req.fileCount)} · ${formatSize(req.fileSize)}"
                        } else {
                            "${req.gameTitle} · ${req.fileName} (${formatSize(req.fileSize)})"
                        }
                        Text(
                            fileSummary,
                            style = DeckType.settingsValue.copy(fontWeight = FontWeight.Medium),
                            color = DeckColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (req.fileCount > 1 && req.fileNames.isNotEmpty()) {
                            Text(
                                req.fileNames.take(4).joinToString(" · ") +
                                    if (req.fileNames.size > 4) " ..." else "",
                                style = DeckType.metaLabel,
                                color = DeckColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            stringResource(R.string.lan_incoming_save_to),
                            style = DeckType.metaLabel,
                            color = DeckColors.TextSecondary,
                        )
                        req.availableDirs.forEachIndexed { dIdx, dir ->
                            val picked = dIdx == incomingDirIdx
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (picked) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.DialogBg)
                                    .border(
                                        width = if (picked) 1.5.dp else 1.dp,
                                        color = if (picked) DeckColors.Ruby else DeckColors.DialogBorder,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .gamepadClickable(swapAbxy, keyBindings, shape = RoundedCornerShape(8.dp)) {
                                        incomingDirIdx = dIdx
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    dir.path.replace(sdPath, "~"),
                                    style = DeckType.metaLabel,
                                    color = if (picked) DeckColors.Ruby else DeckColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (picked) Text("✓", style = DeckType.metaLabel, color = DeckColors.Ruby)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        ) {
                            Text(
                                stringResource(R.string.lan_incoming_reject),
                                style = DeckType.menuButton,
                                color = DeckColors.TextSecondary,
                                modifier = Modifier
                                    .clip(DeckShapes.menuButton)
                                    .background(DeckColors.DialogBg)
                                    .border(1.dp, DeckColors.DialogBorder, DeckShapes.menuButton)
                                    .gamepadClickable(swapAbxy, keyBindings) {
                                        onRejectIncoming(req.sessionId)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                            Text(
                                stringResource(R.string.lan_incoming_accept),
                                style = DeckType.menuButton,
                                color = DeckColors.Background,
                                modifier = Modifier
                                    .clip(DeckShapes.menuButton)
                                    .background(DeckColors.Ruby)
                                    .gamepadClickable(swapAbxy, keyBindings, focusRequester = acceptBtnFr) {
                                        onAcceptIncoming(req.sessionId, chosenDir)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                // 0-B) 当前传输进度 / 取消或关闭状态卡片（用户可随时退出去做别的事，点状态栏回来管理与取消）
                state.transfer?.let { tx ->
                    val txActive = tx.phase == TransferPhase.WaitingAccept || tx.phase == TransferPhase.Transferring
                    val progress = if (tx.totalBytes > 0L) {
                        (tx.bytesTransferred.toFloat() / tx.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val heading = when (tx.phase) {
                        TransferPhase.WaitingAccept -> stringResource(R.string.lan_tx_waiting, tx.peerName)
                        TransferPhase.Transferring -> if (tx.direction == TransferDirection.Sending) {
                            stringResource(R.string.lan_tx_sending, tx.peerName)
                        } else {
                            stringResource(R.string.lan_tx_receiving, tx.peerName)
                        }
                        TransferPhase.Completed -> "✅  ${stringResource(R.string.lan_tx_done)}"
                        TransferPhase.Rejected -> stringResource(R.string.lan_tx_rejected, tx.peerName)
                        TransferPhase.Cancelled -> stringResource(R.string.lan_tx_cancelled)
                        TransferPhase.Failed -> "⚠  ${stringResource(R.string.lan_tx_failed)}"
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DeckColors.Surface)
                            .border(
                                width = 1.5.dp,
                                color = if (tx.phase == TransferPhase.Failed) Color(0xFFDC2626) else DeckColors.Ruby.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                heading,
                                style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                color = if (tx.phase == TransferPhase.Failed) Color(0xFFDC2626) else DeckColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (txActive) {
                                    if (tx.direction == TransferDirection.Receiving) {
                                        stringResource(R.string.lan_cancel_receive)
                                    } else {
                                        stringResource(R.string.lan_cancel_send)
                                    }
                                } else {
                                    stringResource(R.string.lan_tx_clear)
                                },
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = if (txActive) Color(0xFFDC2626) else DeckColors.Ruby,
                                modifier = Modifier
                                    .clip(DeckShapes.tabPill)
                                    .background(if (txActive) Color(0xFFEF4444).copy(alpha = 0.14f) else DeckColors.Ruby.copy(alpha = 0.12f))
                                    .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill) {
                                        if (txActive) onCancelTransfer() else onDismissTransferBanner()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        val itemDisplay = if (tx.totalFiles > 1 && txActive) {
                            "(${tx.currentFileIndex}/${tx.totalFiles}) ${tx.fileName}"
                        } else {
                            tx.fileName
                        }
                        Text(
                            itemDisplay,
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (tx.phase == TransferPhase.WaitingAccept) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(DeckShapes.tabPill),
                                color = DeckColors.Ruby,
                                trackColor = DeckColors.DialogBg,
                            )
                        } else if (tx.phase == TransferPhase.Transferring || tx.phase == TransferPhase.Completed) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(DeckShapes.tabPill),
                                color = DeckColors.Ruby,
                                trackColor = DeckColors.DialogBg,
                            )
                            val speedText = if (tx.speedBytesPerSec > 0L) " · ${formatSize(tx.speedBytesPerSec)}/s" else ""
                            Text(
                                "${formatSize(tx.bytesTransferred)} / ${formatSize(tx.totalBytes)} (${(progress * 100).toInt()}%)$speedText",
                                style = DeckType.metaLabel,
                                color = DeckColors.TextSecondary,
                            )
                        }
                        if (!tx.errorMessage.isNullOrBlank() && tx.phase == TransferPhase.Failed) {
                            Text(tx.errorMessage, style = DeckType.metaLabel, color = Color(0xFFDC2626))
                        }
                    }
                }

                // 1) 本机可爱昵称
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeckColors.Surface)
                        .border(1.dp, DeckColors.DialogBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.lan_device_name),
                                style = DeckType.settingsLabel,
                                color = DeckColors.TextSecondary,
                            )
                            Text(
                                state.deviceName.ifBlank { "..." },
                                style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                color = DeckColors.TextPrimary,
                            )
                        }
                        SmallPillButton(
                            label = "🎲 ${stringResource(R.string.lan_device_random)}",
                            swapAbxy = swapAbxy,
                            keyBindings = keyBindings,
                            onClick = onRandomizeDeviceName,
                        )
                        Spacer(Modifier.width(8.dp))
                        SmallPillButton(
                            label = "✎ ${stringResource(R.string.lan_device_rename)}",
                            accent = true,
                            swapAbxy = swapAbxy,
                            keyBindings = keyBindings,
                            onClick = { editingName = true },
                        )
                    }
                }

                // 2) 多网卡选择（已过滤 TUN/VPN）
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeckColors.Surface)
                        .border(1.dp, DeckColors.DialogBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.lan_nic_title),
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = DeckColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        SmallPillButton(
                            label = "↻ ${stringResource(R.string.lan_nic_refresh)}",
                            swapAbxy = swapAbxy,
                            keyBindings = keyBindings,
                            onClick = onRefreshInterfaces,
                        )
                    }

                    if (state.interfaces.isEmpty()) {
                        Text(
                            stringResource(R.string.lan_nic_empty),
                            style = DeckType.settingsLabel,
                            color = Color(0xFFDC2626),
                        )
                    } else {
                        state.interfaces.forEach { nic ->
                            val selected = nic.interfaceName == state.selectedInterface?.interfaceName
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) DeckColors.Ruby.copy(alpha = 0.10f) else DeckColors.DialogBg)
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) DeckColors.Ruby else DeckColors.DialogBorder,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .gamepadClickable(swapAbxy, keyBindings, shape = RoundedCornerShape(10.dp)) {
                                        onSelectInterface(nic.interfaceName)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) DeckColors.Ruby else DeckColors.TextSecondary.copy(alpha = 0.3f)),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    nic.labelWithIp,
                                    style = DeckType.settingsValue.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = if (selected) DeckColors.Ruby else DeckColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selected) {
                                    Text("✓", style = DeckType.settingsValue, color = DeckColors.Ruby)
                                }
                            }
                        }
                    }
                }

                // 3) 独立服务 A：游戏快传
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (state.shareRunning) DeckColors.Ruby.copy(alpha = 0.06f) else DeckColors.Surface)
                        .border(
                            width = if (state.shareRunning) 1.5.dp else 1.dp,
                            color = if (state.shareRunning) DeckColors.Ruby.copy(alpha = 0.7f) else DeckColors.DialogBorder,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .gamepadClickable(
                                swapAbxy = swapAbxy,
                                keyBindings = keyBindings,
                                focusRequester = shareRowFr,
                                shape = RoundedCornerShape(10.dp),
                            ) { onToggleQuickShare(!state.shareRunning) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "📤  ${stringResource(R.string.lan_share_title)}",
                                style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                color = if (state.shareRunning) DeckColors.Ruby else DeckColors.TextPrimary,
                            )
                            Text(
                                if (state.shareRunning) {
                                    stringResource(
                                        R.string.lan_share_online_fmt,
                                        state.shareIp.orEmpty(),
                                        state.sharePort,
                                        state.peers.size,
                                    )
                                } else {
                                    stringResource(R.string.lan_share_desc)
                                },
                                style = DeckType.settingsLabel,
                                color = DeckColors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = state.shareRunning,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeckColors.Background,
                                checkedTrackColor = DeckColors.Ruby,
                                uncheckedThumbColor = DeckColors.TextSecondary,
                                uncheckedTrackColor = DeckColors.DialogBg,
                            ),
                        )
                    }

                    if (state.shareRunning) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                if (state.peers.isEmpty()) stringResource(R.string.lan_share_peers_empty)
                                else state.peers.joinToString(" · ") { p ->
                                    val tag = if (p.protocol == ShareProtocol.LocalSend) "[LocalSend] " else ""
                                    "$tag${p.deviceName} (${p.ip})"
                                },
                                style = DeckType.settingsLabel,
                                color = DeckColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            SmallPillButton(
                                label = "↻ ${stringResource(R.string.lan_share_scan)}",
                                accent = true,
                                swapAbxy = swapAbxy,
                                keyBindings = keyBindings,
                                onClick = onScanPeers,
                            )
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .gamepadClickable(swapAbxy, keyBindings, shape = RoundedCornerShape(8.dp)) {
                                onToggleLocalSendCompat(!state.localSendCompat)
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.lan_localsend_compat_title),
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = DeckColors.TextPrimary,
                            )
                            Text(
                                stringResource(R.string.lan_localsend_compat_desc),
                                style = DeckType.metaLabel,
                                color = DeckColors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = state.localSendCompat,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeckColors.Background,
                                checkedTrackColor = DeckColors.Ruby,
                                uncheckedThumbColor = DeckColors.TextSecondary,
                                uncheckedTrackColor = DeckColors.DialogBg,
                            ),
                        )
                    }

                    if (onOpenQuickShareScreen != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            SmallPillButton(
                                label = "${stringResource(R.string.quick_share_open_standalone, state.shareHistory.size)} →",
                                accent = true,
                                swapAbxy = swapAbxy,
                                keyBindings = keyBindings,
                                onClick = onOpenQuickShareScreen,
                            )
                        }
                    }
                }

                // 4) 独立服务 B：Web 游戏管理（动态密码登录）
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (state.webRunning) DeckColors.Ruby.copy(alpha = 0.06f) else DeckColors.Surface)
                        .border(
                            width = if (state.webRunning) 1.5.dp else 1.dp,
                            color = if (state.webRunning) DeckColors.Ruby.copy(alpha = 0.7f) else DeckColors.DialogBorder,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .gamepadClickable(swapAbxy, keyBindings, shape = RoundedCornerShape(10.dp)) {
                                onToggleWebServer(!state.webRunning)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "🌐  ${stringResource(R.string.lan_web_title)}",
                                style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                color = if (state.webRunning) DeckColors.Ruby else DeckColors.TextPrimary,
                            )
                            Text(
                                stringResource(R.string.lan_web_desc),
                                style = DeckType.settingsLabel,
                                color = DeckColors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = state.webRunning,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeckColors.Background,
                                checkedTrackColor = DeckColors.Ruby,
                                uncheckedThumbColor = DeckColors.TextSecondary,
                                uncheckedTrackColor = DeckColors.DialogBg,
                            ),
                        )
                    }

                    if (state.webRunning) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DeckColors.DialogBg)
                                .border(1.dp, DeckColors.DialogBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.lan_web_url_label),
                                    style = DeckType.settingsLabel,
                                    color = DeckColors.TextSecondary,
                                )
                                Text(
                                    state.webUrl.orEmpty(),
                                    style = DeckType.settingsValue.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = DeckColors.TextPrimary,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(R.string.lan_web_pwd_label),
                                    style = DeckType.settingsLabel,
                                    color = DeckColors.TextSecondary,
                                )
                                Text(
                                    state.webPassword.orEmpty(),
                                    style = DeckType.menuTitle.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 3.sp,
                                    ),
                                    color = DeckColors.Ruby,
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            SmallPillButton(
                                label = "🔄 ${stringResource(R.string.lan_web_pwd_refresh)}",
                                accent = true,
                                swapAbxy = swapAbxy,
                                keyBindings = keyBindings,
                                onClick = onRefreshWebPassword,
                            )
                        }

                        // 当前已连接的客户端 IP 列表
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DeckColors.DialogBg)
                                .border(1.dp, DeckColors.DialogBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.lan_web_clients_title, state.webClients.size),
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = DeckColors.TextSecondary,
                            )
                            if (state.webClients.isEmpty()) {
                                Text(
                                    stringResource(R.string.lan_web_clients_empty),
                                    style = DeckType.metaLabel,
                                    color = DeckColors.TextSecondary,
                                )
                            } else {
                                val nowMs = System.currentTimeMillis()
                                state.webClients.forEach { client ->
                                    val mins = ((nowMs - client.lastActiveMs) / 60_000L).toInt()
                                    val activeStr = if (mins <= 0) {
                                        stringResource(R.string.lan_web_client_just_now)
                                    } else {
                                        stringResource(R.string.lan_web_client_min_ago, mins)
                                    }
                                    val meta = listOfNotNull(
                                        client.userAgent.takeIf { it.isNotBlank() },
                                        activeStr,
                                    ).joinToString(" · ")
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981)),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                client.ip,
                                                style = DeckType.settingsLabel.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                                color = DeckColors.TextPrimary,
                                            )
                                            if (meta.isNotBlank()) {
                                                Text(
                                                    meta,
                                                    style = DeckType.metaLabel,
                                                    color = DeckColors.TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        SmallPillButton(
                                            label = stringResource(R.string.lan_web_client_kick),
                                            danger = true,
                                            swapAbxy = swapAbxy,
                                            keyBindings = keyBindings,
                                            onClick = { onKickWebClient(client.ip) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (nameSpec != null && nameState != null) {
        NameDialog(
            spec = nameSpec,
            state = nameState,
            onConfirm = {
                nameSpec.onConfirm(nameState.value.text)
                editingName = false
            },
            onDismiss = { editingName = false },
        )
    }
}

/**
 * 发送方式选择弹窗：局域网 PocketDeck 快传 vs 安卓系统 / 快速分享（无需同款客户端）。
 */
@Composable
fun ShareMethodDialog(
    fileSummary: String,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onPickLanShare: () -> Unit,
    onPickSystemShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var focusIdx by remember { mutableIntStateOf(0) } // 0 = LAN, 1 = System Quick Share
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
                        focusIdx = 0
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        focusIdx = 1
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        if (focusIdx == 0) onPickLanShare() else onPickSystemShare()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.lan_share_method_title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
            )
            Text(
                fileSummary,
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(color = DeckColors.DialogBorder)

            val options = listOf(
                Triple("📡", stringResource(R.string.lan_share_method_lan), stringResource(R.string.lan_share_method_lan_desc)),
                Triple("📲", stringResource(R.string.lan_share_method_android), stringResource(R.string.lan_share_method_android_desc)),
            )
            options.forEachIndexed { idx, (icon, title, desc) ->
                val hl = focusIdx == idx
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hl) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
                        .border(
                            width = if (hl) 1.5.dp else 1.dp,
                            color = if (hl) DeckColors.Ruby else DeckColors.DialogBorder,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable {
                            focusIdx = idx
                            if (idx == 0) onPickLanShare() else onPickSystemShare()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(icon, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                            color = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                        )
                        Text(
                            desc,
                            style = DeckType.metaLabel,
                            color = DeckColors.TextSecondary,
                        )
                    }
                    Text("→", style = DeckType.settingsValue, color = DeckColors.Ruby)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = DeckColors.TextSecondary,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/**
 * 局域网发送目标设备选择弹窗（未开快传时可直接一键开启，支持单文件与多文件批量发送）。
 */
@Composable
fun SharePeerPickerDialog(
    fileName: String,
    fileSize: Long,
    gameTitle: String,
    fileCount: Int = 1,
    state: LanServerState,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onEnableQuickShare: () -> Unit,
    onScanPeers: () -> Unit,
    onPickPeer: (LanPeer) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var focusIdx by remember(state.peers.size) { mutableIntStateOf(0) }
    val peers = state.peers

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        if (state.shareRunning) onScanPeers()
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
                    event.key == Key.DirectionUp && peers.isNotEmpty() -> {
                        focusIdx = (focusIdx - 1).coerceAtLeast(0)
                        true
                    }
                    event.key == Key.DirectionDown && peers.isNotEmpty() -> {
                        focusIdx = (focusIdx + 1).coerceAtMost(peers.lastIndex)
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        if (!state.shareRunning) {
                            onEnableQuickShare()
                        } else {
                            peers.getOrNull(focusIdx)?.let(onPickPeer)
                        }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .heightIn(max = 520.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.lan_share_pick_peer_title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (fileCount > 1) {
                    "${stringResource(R.string.lan_multi_file_summary, fileCount)} · ${formatSize(fileSize)}"
                } else {
                    "$gameTitle · $fileName (${formatSize(fileSize)})"
                },
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DeckColors.DialogBorder)
            Spacer(Modifier.height(12.dp))

            if (!state.shareRunning) {
                Text(
                    stringResource(R.string.lan_share_enable_first),
                    style = DeckType.settingsValue,
                    color = DeckColors.TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                state.selectedInterface?.let { nic ->
                    Text(
                        nic.labelWithIp,
                        style = DeckType.settingsLabel,
                        color = DeckColors.Ruby,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    stringResource(R.string.lan_share_enable_btn),
                    style = DeckType.menuButton,
                    color = DeckColors.Background,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(DeckColors.Ruby)
                        .clickable(onClick = onEnableQuickShare)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${state.deviceName} (${state.shareIp.orEmpty()})",
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                    )
                    SmallPillButton(
                        label = "↻ ${stringResource(R.string.lan_share_scan)}",
                        accent = true,
                        onClick = onScanPeers,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (peers.isEmpty()) {
                    Text(
                        stringResource(R.string.lan_share_peers_empty),
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        peers.forEachIndexed { idx, peer ->
                            val hl = idx == focusIdx
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (hl) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
                                    .border(
                                        width = if (hl) 1.5.dp else 1.dp,
                                        color = if (hl) DeckColors.Ruby else DeckColors.DialogBorder,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        focusIdx = idx
                                        onPickPeer(peer)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (peer.protocol == ShareProtocol.LocalSend) "📡" else "🎮", fontSize = 18.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        peer.deviceName,
                                        style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                                    )
                                    val protoBadge = if (peer.protocol == ShareProtocol.LocalSend) "LocalSend" else "PocketDeck"
                                    Text(
                                        listOfNotNull(protoBadge, peer.model.takeIf { it.isNotBlank() }, peer.ip).joinToString(" · "),
                                        style = DeckType.settingsLabel,
                                        color = DeckColors.TextSecondary,
                                    )
                                }
                                Text("→", style = DeckType.settingsValue, color = DeckColors.Ruby)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = DeckColors.TextSecondary,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * 接收方全局确认弹窗（不管当前停留在桌面、设置或游戏库都能弹出接收确认）。
 */
@Composable
fun IncomingShareDialog(
    req: IncomingShareRequest,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onAccept: (File) -> Unit,
    onReject: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var dirIdx by remember(req.sessionId) { mutableIntStateOf(0) }
    var btnIdx by remember(req.sessionId) { mutableIntStateOf(0) } // 0 = Accept, 1 = Reject
    val dirs = req.availableDirs
    val chosenDir = dirs.getOrElse(dirIdx) { req.defaultTargetDir }
    val sdPath = remember { Environment.getExternalStorageDirectory().path }

    LaunchedEffect(req.sessionId) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onReject()
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        btnIdx = 0
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        btnIdx = 1
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        if (btnIdx == 0) onAccept(chosenDir) else onReject()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .padding(22.dp),
        ) {
            Text(
                "📥  ${stringResource(R.string.lan_incoming_title)}",
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.lan_incoming_from, req.senderName, req.senderIp),
                style = DeckType.settingsValue,
                color = DeckColors.Ruby,
            )
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeckColors.Surface)
                    .border(1.dp, DeckColors.DialogBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    req.gameTitle,
                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                    color = DeckColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        req.platformName.takeIf { it.isNotBlank() },
                        formatSize(req.fileSize),
                        req.fileName,
                    ).joinToString(" · "),
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeckColors.Surface)
                    .clickable(enabled = dirs.size > 1) { dirIdx = (dirIdx + 1) % dirs.size }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.lan_incoming_save_to),
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                    )
                    Text(
                        chosenDir.path.replace(sdPath, "~"),
                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                        color = DeckColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (dirs.size > 1) {
                    Text("↻", style = DeckType.settingsValue, color = DeckColors.Ruby)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val rejHl = btnIdx == 1
                Text(
                    stringResource(R.string.lan_incoming_reject),
                    style = DeckType.menuButton,
                    color = if (rejHl) DeckColors.Ruby else DeckColors.TextSecondary,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(if (rejHl) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
                        .border(
                            width = if (rejHl) 1.5.dp else 0.dp,
                            color = if (rejHl) DeckColors.Ruby else Color.Transparent,
                            shape = DeckShapes.menuButton,
                        )
                        .clickable(onClick = onReject)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
                Spacer(Modifier.width(10.dp))
                val accHl = btnIdx == 0
                Text(
                    stringResource(R.string.lan_incoming_accept),
                    style = DeckType.menuButton,
                    color = DeckColors.Background,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(DeckColors.Ruby)
                        .border(
                            width = if (accHl) 2.dp else 0.dp,
                            color = if (accHl) DeckColors.TextPrimary else Color.Transparent,
                            shape = DeckShapes.menuButton,
                        )
                        .clickable { onAccept(chosenDir) }
                        .padding(horizontal = 22.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/**
 * 传输进度与结果浮层（等待确认 / 发送或接收进度 / 结果提示）。
 */
@Composable
fun ShareTransferDialog(
    tx: ShareTransferState,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val active = tx.phase == TransferPhase.WaitingAccept || tx.phase == TransferPhase.Transferring

    LaunchedEffect(tx.sessionId, tx.phase) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        if (active) onCancel() else onDismiss()
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        if (!active) onDismiss()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(440.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .padding(22.dp),
        ) {
            val heading = when (tx.phase) {
                TransferPhase.WaitingAccept -> stringResource(R.string.lan_tx_waiting, tx.peerName)
                TransferPhase.Transferring -> if (tx.direction == TransferDirection.Sending) {
                    stringResource(R.string.lan_tx_sending, tx.peerName)
                } else {
                    stringResource(R.string.lan_tx_receiving, tx.peerName)
                }
                TransferPhase.Completed -> "✅  ${stringResource(R.string.lan_tx_done)}"
                TransferPhase.Rejected -> stringResource(R.string.lan_tx_rejected, tx.peerName)
                TransferPhase.Cancelled -> stringResource(R.string.lan_tx_cancelled)
                TransferPhase.Failed -> "⚠  ${stringResource(R.string.lan_tx_failed)}"
            }
            Text(
                heading,
                style = DeckType.menuTitle,
                color = if (tx.phase == TransferPhase.Failed) Color(0xFFDC2626) else DeckColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                tx.fileName,
                style = DeckType.settingsValue,
                color = DeckColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (tx.phase == TransferPhase.Transferring || tx.phase == TransferPhase.Completed) {
                Spacer(Modifier.height(14.dp))
                val progress = if (tx.totalBytes > 0L) {
                    (tx.bytesTransferred.toFloat() / tx.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(DeckShapes.tabPill),
                    color = DeckColors.Ruby,
                    trackColor = DeckColors.Surface,
                )
                Spacer(Modifier.height(6.dp))
                val speedText = if (tx.speedBytesPerSec > 0L) " · ${formatSize(tx.speedBytesPerSec)}/s" else ""
                Text(
                    "${formatSize(tx.bytesTransferred)} / ${formatSize(tx.totalBytes)} (${(progress * 100).toInt()}%)$speedText",
                    style = DeckType.settingsLabel,
                    color = DeckColors.TextSecondary,
                )
            } else if (tx.phase == TransferPhase.WaitingAccept) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(DeckShapes.tabPill),
                    color = DeckColors.Ruby,
                    trackColor = DeckColors.Surface,
                )
            }

            if (!tx.errorMessage.isNullOrBlank() && tx.phase == TransferPhase.Failed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    tx.errorMessage,
                    style = DeckType.settingsLabel,
                    color = Color(0xFFDC2626),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    if (active) stringResource(R.string.menu_cancel) else stringResource(R.string.lan_tx_close),
                    style = DeckType.menuButton,
                    color = if (active) Color(0xFFDC2626) else DeckColors.Background,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(if (active) Color(0xFFEF4444).copy(alpha = 0.14f) else DeckColors.Ruby)
                        .clickable { if (active) onCancel() else onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun SmallPillButton(
    label: String,
    accent: Boolean = false,
    danger: Boolean = false,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onClick: () -> Unit,
) {
    val fg = when {
        danger -> Color(0xFFDC2626)
        accent -> DeckColors.Ruby
        else -> DeckColors.TextPrimary
    }
    val bg = when {
        danger -> Color(0xFFEF4444).copy(alpha = 0.12f)
        accent -> DeckColors.Ruby.copy(alpha = 0.12f)
        else -> DeckColors.DialogBg
    }
    val borderColor = when {
        danger -> Color(0xFFEF4444).copy(alpha = 0.45f)
        accent -> DeckColors.Ruby.copy(alpha = 0.45f)
        else -> DeckColors.DialogBorder
    }
    Text(
        label,
        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
        color = fg,
        modifier = Modifier
            .clip(DeckShapes.tabPill)
            .background(bg)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = DeckShapes.tabPill,
            )
            .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
