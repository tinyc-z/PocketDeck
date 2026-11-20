package com.pocketdeck.launcher.ui.lan

import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckProgressBar
import com.pocketdeck.launcher.ui.components.DeckSwitch
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import com.pocketdeck.launcher.ui.theme.brandPillBackground
import com.pocketdeck.launcher.ui.theme.cardSurface
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.data.FileTask
import com.pocketdeck.launcher.data.FileTaskKind
import com.pocketdeck.launcher.data.FileTaskSource
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.lan.IncomingShareRequest
import com.pocketdeck.launcher.lan.LanPeer
import com.pocketdeck.launcher.lan.LanServerState
import com.pocketdeck.launcher.lan.LocalSendPinChallenge
import com.pocketdeck.launcher.lan.ShareProtocol
import com.pocketdeck.launcher.lan.ShareHistoryItem
import com.pocketdeck.launcher.lan.ShareTransferState
import com.pocketdeck.launcher.lan.TransferDirection
import com.pocketdeck.launcher.lan.TransferPhase
import com.pocketdeck.launcher.lan.WebTransferDirection
import com.pocketdeck.launcher.lan.WebTransferHistoryItem
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.DpadNavController
import com.pocketdeck.launcher.ui.components.dpadNavItem
import com.pocketdeck.launcher.ui.components.gamepadClickable
import com.pocketdeck.launcher.ui.components.scrimTapDismiss
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.settings.NameDialog
import com.pocketdeck.launcher.ui.settings.NameDialogSpec
import com.pocketdeck.launcher.ui.settings.NameDialogState
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.blockOverlayTouchThrough
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
internal fun LanShareServiceCard(
    state: LanServerState,
    nav: DpadNavController,
    onToggleQuickShare: (Boolean) -> Unit,
    onRandomizeDeviceName: () -> Unit,
    onOpenRenameDialog: () -> Unit,
    onScanPeers: () -> Unit,
    onToggleLocalSendCompat: (Boolean) -> Unit,
    onOpenShareHistory: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DeckBrand.RadiusM))
            .background(DeckColors.Surface)
            .border(0.7.dp, DeckColors.Line, RoundedCornerShape(DeckBrand.RadiusM))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 本机快传昵称（归属游戏快传）
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DeckColors.DialogBg)
                .border(0.6.dp, DeckColors.Line, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.lan_device_name),
                    style = DeckType.metaLabel,
                    color = DeckColors.TextSecondary,
                )
                Text(
                    state.deviceName.ifBlank { "..." },
                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                    color = DeckColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SmallPillButton(
                label = stringResource(R.string.lan_device_random),
                nav = nav,
                navId = "rand_name",
                onClick = onRandomizeDeviceName,
            )
            Spacer(Modifier.width(6.dp))
            SmallPillButton(
                label = stringResource(R.string.lan_device_rename),
                accent = true,
                nav = nav,
                navId = "rename",
                onClick = onOpenRenameDialog,
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
                    label = stringResource(R.string.lan_share_scan),
                    accent = true,
                    nav = nav,
                    navId = "scan_peers",
                    onClick = onScanPeers,
                )
            }
        } else {
            Text(
                stringResource(R.string.lan_share_desc),
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .dpadNavItem(nav, "compat_localsend", shape = RoundedCornerShape(8.dp)) {
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
            DeckSwitch(checked = state.localSendCompat)
        }

        if (state.shareRunning || state.shareHistory.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                SmallPillButton(
                    label = "${stringResource(R.string.lan_share_history_btn, state.shareHistory.size)} →",
                    accent = true,
                    nav = nav,
                    navId = "share_history",
                    onClick = onOpenShareHistory,
                )
            }
        }
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
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier.width(460.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Text(
                stringResource(R.string.lan_share_method_title),
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
                modifier = Modifier.padding(end = 36.dp),
            )
            Text(
                fileSummary,
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(color = DeckColors.Line)

            val options = listOf(
                Triple(DeckIcons.Radio, stringResource(R.string.lan_share_method_lan), stringResource(R.string.lan_share_method_lan_desc)),
                Triple(DeckIcons.Send, stringResource(R.string.lan_share_method_android), stringResource(R.string.lan_share_method_android_desc)),
            )
            options.forEachIndexed { idx, (icon, title, desc) ->
                val hl = focusIdx == idx
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hl) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Surface)
                        .border(
                            width = if (hl) 0.9.dp else 0.6.dp,
                            color = if (hl) DeckColors.Ruby else DeckColors.Line,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable {
                            focusIdx = idx
                            if (idx == 0) onPickLanShare() else onPickSystemShare()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeckIcon(icon = icon, tint = if (hl) DeckColors.Ruby else DeckColors.TextPrimary, size = 20.dp)
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
    onCancelTransfer: () -> Unit = {},
    onSubmitPin: (String) -> Unit = {},
    onCancelPin: () -> Unit = {},
    onDismissTransferBanner: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var focusIdx by remember(state.peers.size) { mutableIntStateOf(0) }
    var transferStartedHere by remember { mutableStateOf(false) }
    val peers = state.peers

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        if (state.shareRunning) onScanPeers()
    }
    LaunchedEffect(state.pinChallenge != null) {
        if (state.pinChallenge == null) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss { if (state.pinChallenge == null) onDismiss() }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (state.pinChallenge != null) return@onPreviewKeyEvent false
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
                            peers.getOrNull(focusIdx)?.let { peer ->
                                transferStartedHere = true
                                onPickPeer(peer)
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
                .heightIn(max = 520.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Column(Modifier.padding(end = 36.dp)) {
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
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DeckColors.Line)
            Spacer(Modifier.height(12.dp))

            val tx = state.transfer
            val showInlineTx = tx != null && (
                transferStartedHere ||
                    tx.phase == TransferPhase.WaitingAccept ||
                    tx.phase == TransferPhase.PinRequired ||
                    tx.phase == TransferPhase.Transferring
                )
            if (tx != null && showInlineTx) {
                val txActive = tx.phase == TransferPhase.WaitingAccept ||
                    tx.phase == TransferPhase.PinRequired ||
                    tx.phase == TransferPhase.Transferring
                val headerText = when (tx.phase) {
                    TransferPhase.WaitingAccept -> stringResource(R.string.lan_tx_waiting, tx.peerName)
                    TransferPhase.PinRequired -> "${stringResource(R.string.lan_tx_pin_required, tx.peerName)}"
                    TransferPhase.Transferring -> stringResource(
                        if (tx.direction == TransferDirection.Sending) R.string.lan_tx_sending else R.string.lan_tx_receiving,
                        tx.peerName,
                    )
                    TransferPhase.Completed -> "${stringResource(R.string.lan_tx_done)}"
                    TransferPhase.Rejected -> stringResource(R.string.lan_tx_rejected, tx.peerName)
                    TransferPhase.Cancelled -> stringResource(R.string.lan_tx_cancelled)
                    TransferPhase.Failed -> "${stringResource(R.string.lan_tx_failed)}"
                }
                val itemDisplay = if (tx.totalFiles > 1 && txActive) {
                    "(${tx.currentFileIndex}/${tx.totalFiles}) ${tx.fileName}"
                } else {
                    tx.fileName
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeckColors.Surface)
                        .border(0.75.dp, DeckColors.Ruby, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            headerText,
                            style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                            color = DeckColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        SmallPillButton(
                            label = stringResource(if (txActive) R.string.menu_cancel else R.string.lan_tx_clear),
                            danger = txActive,
                            accent = !txActive,
                            swapAbxy = swapAbxy,
                            keyBindings = keyBindings,
                            onClick = {
                                if (txActive) {
                                    onCancelTransfer()
                                } else {
                                    transferStartedHere = false
                                    onDismissTransferBanner()
                                }
                            },
                        )
                    }
                    Text(
                        itemDisplay,
                        style = DeckType.settingsLabel,
                        color = DeckColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ShareTransferProgressSection(tx = tx, formatSize = ::formatSize)
                    if (!tx.errorMessage.isNullOrBlank() && tx.phase == TransferPhase.Failed) {
                        Text(tx.errorMessage, style = DeckType.metaLabel, color = Color(0xFFDC2626))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

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
                    color = Color.White,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .brandBackground(DeckShapes.menuButton)
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
                        label = stringResource(R.string.lan_share_scan),
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
                                        width = if (hl) 0.9.dp else 0.6.dp,
                                        color = if (hl) DeckColors.Ruby else DeckColors.Line,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        focusIdx = idx
                                        transferStartedHere = true
                                        onPickPeer(peer)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DeckIcon(
                                    icon = if (peer.protocol == ShareProtocol.LocalSend) DeckIcons.Radio else DeckIcons.Gamepad,
                                    tint = if (hl) DeckColors.Ruby else DeckColors.TextPrimary,
                                    size = 19.dp,
                                )
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
        }

        state.pinChallenge?.let { challenge ->
            LocalSendPinDialog(
                challenge = challenge,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onSubmit = onSubmitPin,
                onCancel = onCancelPin,
            )
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
            .blockOverlayTouchThrough()
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
                        btnIdx = 1
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        btnIdx = 0
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
                "${stringResource(R.string.lan_incoming_title)}",
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
                    .border(0.6.dp, DeckColors.Line, RoundedCornerShape(10.dp))
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
                    DeckIcon(DeckIcons.Refresh, size = 16.dp, tint = DeckColors.Ruby)
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
                            width = if (rejHl) 0.9.dp else 0.dp,
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
                    color = Color.White,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .brandBackground(DeckShapes.menuButton)
                        .border(
                            width = if (accHl) 1.dp else 0.dp,
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
internal fun LocalSendPinDialog(
    challenge: LocalSendPinChallenge,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val inputFr = remember { FocusRequester() }
    var pinText by remember(challenge.sessionId, challenge.wrongPin) { mutableStateOf("") }
    LaunchedEffect(challenge.sessionId, challenge.wrongPin) {
        runCatching { inputFr.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss(onCancel)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onCancel()
                        true
                    }
                    event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                        if (pinText.isNotBlank()) onSubmit(pinText)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 10.dp)
                .width(420.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${stringResource(R.string.lan_pin_title)}",
                style = DeckType.menuTitle,
                color = DeckColors.TextPrimary,
            )
            Text(
                if (challenge.wrongPin) {
                    "${stringResource(R.string.lan_pin_wrong)}"
                } else {
                    stringResource(R.string.lan_pin_desc, challenge.peerName)
                },
                style = DeckType.settingsLabel.copy(
                    fontWeight = if (challenge.wrongPin) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (challenge.wrongPin) Color(0xFFDC2626) else DeckColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DeckColors.Surface)
                    .border(0.75.dp, DeckColors.Ruby, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (pinText.isEmpty()) {
                    Text(
                        stringResource(R.string.lan_pin_hint),
                        style = DeckType.settingsValue,
                        color = DeckColors.TextSecondary.copy(alpha = 0.65f),
                    )
                }
                BasicTextField(
                    value = pinText,
                    onValueChange = { pinText = it },
                    singleLine = true,
                    textStyle = DeckType.settingsValue.copy(color = DeckColors.TextPrimary),
                    cursorBrush = SolidColor(DeckColors.Ruby),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (pinText.isNotBlank()) onSubmit(pinText) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFr),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = DeckColors.TextSecondary,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .gamepadClickable(swapAbxy, keyBindings, onClick = onCancel)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(8.dp))
                val canSubmit = pinText.isNotBlank()
                Text(
                    stringResource(R.string.action_ok),
                    style = DeckType.menuButton,
                    color = if (canSubmit) Color.White else DeckColors.TextSecondary,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .brandPillBackground(selected = canSubmit, shape = DeckShapes.menuButton, unselectedColor = DeckColors.Surface)
                        .gamepadClickable(swapAbxy, keyBindings) {
                            if (pinText.isNotBlank()) onSubmit(pinText)
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
fun ShareTransferHistoryDialog(
    history: List<ShareHistoryItem>,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onClearAll: () -> Unit,
    onDeleteItem: (String) -> Unit,
    onOpenFolder: ((File, File?) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val closeFr = remember { FocusRequester() }
    val sdPath = remember { Environment.getExternalStorageDirectory().path }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.90f).dp
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        runCatching { closeFr.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss(onDismiss)
            .onPreviewKeyEvent { event ->
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
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            closeFocusRequester = closeFr,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.lan_share_history_title, history.size),
                    style = DeckType.menuTitle,
                    color = DeckColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (history.isNotEmpty()) {
                    SmallPillButton(
                        label = stringResource(R.string.quick_share_history_clear),
                        danger = true,
                        swapAbxy = swapAbxy,
                        keyBindings = keyBindings,
                        onClick = onClearAll,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DeckColors.Line)
            Spacer(Modifier.height(8.dp))

            if (history.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.lan_share_history_empty),
                        style = DeckType.settingsValue,
                        color = DeckColors.TextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(history, key = { it.id }) { item ->
                        val targetFile = remember(item.filePath) { File(item.filePath) }
                        val openDir = remember(item.filePath) {
                            if (targetFile.isDirectory) targetFile else targetFile.parentFile
                        }?.takeIf { it.isDirectory }
                        val countPart = if (item.fileCount > 1) " · ${item.fileCount} files" else ""
                        val timePart = if (item.receivedAtMs > 0L) " · ${timeFmt.format(Date(item.receivedAtMs))}" else ""
                        val senderPart = if (item.senderName.isNotBlank()) "${item.senderName} (${item.senderIp})" else item.senderIp

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DeckColors.Surface)
                                .border(0.6.dp, DeckColors.Line, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (item.platformName.isNotBlank()) {
                                        Text(
                                            item.platformName,
                                            style = DeckType.metaLabel.copy(fontWeight = FontWeight.Bold),
                                            color = DeckColors.Ruby,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(DeckColors.AccentSoft)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text(
                                        item.fileName,
                                        style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                        color = DeckColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Text(
                                    item.filePath.replace(sdPath, "~"),
                                    style = DeckType.metaLabel.copy(fontFamily = FontFamily.Monospace),
                                    color = DeckColors.TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "$senderPart · ${formatSize(item.totalBytes)}$countPart$timePart",
                                    style = DeckType.metaLabel,
                                    color = DeckColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (openDir != null && onOpenFolder != null) {
                                    SmallPillButton(
                                        label = stringResource(R.string.quick_share_history_jump_folder),
                                        accent = true,
                                        swapAbxy = swapAbxy,
                                        keyBindings = keyBindings,
                                        onClick = { onOpenFolder(openDir, targetFile.takeIf { it.exists() }) },
                                    )
                                }
                                SmallPillButton(
                                    label = stringResource(R.string.quick_share_history_delete),
                                    danger = true,
                                    swapAbxy = swapAbxy,
                                    keyBindings = keyBindings,
                                    onClick = { onDeleteItem(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
