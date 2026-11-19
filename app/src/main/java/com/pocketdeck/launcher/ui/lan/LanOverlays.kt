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
    onClearWebHistory: () -> Unit = {},
    onDeleteWebHistoryItem: (String) -> Unit = {},
    onOpenFolder: ((File, File?) -> Unit)? = null,
    onOpenQuickShareScreen: (() -> Unit)? = null,
    onAcceptIncoming: (String, File) -> Unit = { _, _ -> },
    onRejectIncoming: (String) -> Unit = {},
    onCancelTransfer: () -> Unit = {},
    onCancelWebTransfer: (String) -> Unit = {},
    onCancelFileTask: (String) -> Unit = {},
    onSubmitPin: (String) -> Unit = {},
    onCancelPin: () -> Unit = {},
    onDismissTransferBanner: () -> Unit = {},
    onStopAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val nav = remember { DpadNavController() }
    var editingName by remember { mutableStateOf(false) }
    var showWebHistory by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = editingName || showWebHistory) {
        if (editingName) editingName = false else showWebHistory = false
    }
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
    var wasWebTransferring by remember { mutableStateOf(false) }
    var wasFileTasksActive by remember { mutableStateOf(false) }

    LaunchedEffect(state.webRunning, state.webTransferring, state.fileTasks.isNotEmpty()) {
        val turnedOn = !wasWebRunning && state.webRunning
        val hasTasksNow = state.fileTasks.isNotEmpty()
        val startedTransfer = ((!wasWebTransferring && state.webTransferring) || (!wasFileTasksActive && hasTasksNow)) &&
            state.incomingRequest == null && state.transfer == null
        wasWebRunning = state.webRunning
        wasWebTransferring = state.webTransferring
        wasFileTasksActive = hasTasksNow
        if (turnedOn || startedTransfer) {
            delay(80)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val view = LocalView.current
    LaunchedEffect(editingName, showWebHistory, state.pinChallenge != null, state.incomingRequest?.sessionId) {
        if (!editingName && !showWebHistory && state.pinChallenge == null) {
            delay(30)
            runCatching { nav.rootFr.requestFocus() }
            nav.ensureDefault(
                preferredId = if (state.incomingRequest != null) "inc_accept" else "rand_name",
                inTouchMode = view.isInTouchMode,
            )
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
            .focusRequester(nav.rootFr)
            .focusProperties {
                canFocus = !editingName && !showWebHistory && state.pinChallenge == null
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (editingName || showWebHistory || state.pinChallenge != null) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    event.key == Key.DirectionDown || event.key == Key.DirectionRight -> {
                        nav.move(1)
                        true
                    }
                    event.key == Key.DirectionUp || event.key == Key.DirectionLeft -> {
                        nav.move(-1)
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        nav.triggerCurrent()
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 20.dp)
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
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
                DeckIcon(DeckIcons.Radio, tint = DeckColors.Ruby, size = 20.dp)
                Text(
                    stringResource(R.string.lan_service_title),
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
                            .dpadNavItem(nav, "stop_all", shape = DeckShapes.tabPill, onClick = onStopAll)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DeckColors.Line)
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
                            .background(DeckColors.AccentSoft)
                            .brandBorder(active = true, shape = RoundedCornerShape(12.dp), activeWidth = 1.4.dp)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${stringResource(R.string.lan_incoming_title)}",
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
                                    .background(if (picked) DeckColors.AccentSoft else DeckColors.DialogBg)
                                    .brandBorder(active = picked, shape = RoundedCornerShape(8.dp), activeWidth = 1.3.dp, inactiveWidth = 0.6.dp)
                                    .dpadNavItem(nav, "inc_dir_$dIdx", shape = RoundedCornerShape(8.dp)) {
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
                                if (picked) DeckIcon(DeckIcons.Check, size = 15.dp, tint = DeckColors.Ruby)
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
                                    .border(0.6.dp, DeckColors.Line, DeckShapes.menuButton)
                                    .dpadNavItem(nav, "inc_reject") {
                                        onRejectIncoming(req.sessionId)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                            Text(
                                stringResource(R.string.lan_incoming_accept),
                                style = DeckType.menuButton,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(DeckShapes.menuButton)
                                    .brandBackground(DeckShapes.menuButton)
                                    .dpadNavItem(nav, "inc_accept") {
                                        onAcceptIncoming(req.sessionId, chosenDir)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                // 0-B) 当前传输进度 / 取消或关闭状态卡片（用户可随时退出去做别的事，点状态栏回来管理与取消）
                state.transfer?.let { tx ->
                    val txActive = tx.phase == TransferPhase.WaitingAccept ||
                        tx.phase == TransferPhase.PinRequired ||
                        tx.phase == TransferPhase.Transferring
                    val progress = if (tx.totalBytes > 0L) {
                        (tx.bytesTransferred.toFloat() / tx.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val heading = when (tx.phase) {
                        TransferPhase.WaitingAccept -> stringResource(R.string.lan_tx_waiting, tx.peerName)
                        TransferPhase.PinRequired -> "${stringResource(R.string.lan_tx_pin_required, tx.peerName)}"
                        TransferPhase.Transferring -> if (tx.direction == TransferDirection.Sending) {
                            stringResource(R.string.lan_tx_sending, tx.peerName)
                        } else {
                            stringResource(R.string.lan_tx_receiving, tx.peerName)
                        }
                        TransferPhase.Completed -> "${stringResource(R.string.lan_tx_done)}"
                        TransferPhase.Rejected -> stringResource(R.string.lan_tx_rejected, tx.peerName)
                        TransferPhase.Cancelled -> stringResource(R.string.lan_tx_cancelled)
                        TransferPhase.Failed -> "${stringResource(R.string.lan_tx_failed)}"
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DeckColors.Surface)
                            .border(
                                width = 0.9.dp,
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
                                    .dpadNavItem(nav, "tx_action", shape = DeckShapes.tabPill) {
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
                        ShareTransferProgressSection(tx = tx, formatSize = ::formatSize)
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
                        .border(0.6.dp, DeckColors.Line, RoundedCornerShape(12.dp))
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
                            label = stringResource(R.string.lan_device_random),
                            nav = nav,
                            navId = "rand_name",
                            onClick = onRandomizeDeviceName,
                        )
                        Spacer(Modifier.width(8.dp))
                        SmallPillButton(
                            label = stringResource(R.string.lan_device_rename),
                            accent = true,
                            nav = nav,
                            navId = "rename",
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
                        .border(0.6.dp, DeckColors.Line, RoundedCornerShape(12.dp))
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
                            nav = nav,
                            navId = "refresh_nic",
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
                        state.interfaces.forEachIndexed { nicIdx, nic ->
                            val selected = nic.interfaceName == state.selectedInterface?.interfaceName
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) DeckColors.AccentSoft else DeckColors.DialogBg)
                                    .brandBorder(active = selected, shape = RoundedCornerShape(10.dp), activeWidth = 1.4.dp, inactiveWidth = 0.6.dp)
                                    .dpadNavItem(nav, "nic_$nicIdx", shape = RoundedCornerShape(10.dp)) {
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
                                    DeckIcon(DeckIcons.Check, size = 16.dp, tint = DeckColors.Ruby)
                                }
                            }
                        }
                    }
                }

                // 3) 独立服务 A：游戏快传
                Column(
                    Modifier
                        .fillMaxWidth()
                        .cardSurface(RoundedCornerShape(DeckBrand.RadiusM), elevation = 2.dp)
                        .border(0.8.dp, DeckColors.Line, RoundedCornerShape(DeckBrand.RadiusM))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .dpadNavItem(nav, "toggle_share", shape = RoundedCornerShape(10.dp)) {
                                onToggleQuickShare(!state.shareRunning)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DeckIcon(
                                    icon = DeckIcons.Send,
                                    tint = if (state.shareRunning) DeckColors.Ruby else DeckColors.TextPrimary,
                                    size = 17.dp,
                                )
                                Text(
                                    stringResource(R.string.lan_share_title),
                                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (state.shareRunning) DeckColors.Ruby else DeckColors.TextPrimary,
                                )
                            }
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
                        DeckSwitch(checked = state.shareRunning)
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

                    if (onOpenQuickShareScreen != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            SmallPillButton(
                                label = stringResource(R.string.quick_share_open_standalone, state.shareHistory.size),
                                accent = true,
                                nav = nav,
                                navId = "open_quickshare",
                                onClick = onOpenQuickShareScreen,
                            )
                        }
                    }
                }

                // 4) 独立服务 B：Web 游戏管理（动态密码登录）
                Column(
                    Modifier
                        .fillMaxWidth()
                        .cardSurface(RoundedCornerShape(DeckBrand.RadiusM), elevation = 2.dp)
                        .border(0.8.dp, DeckColors.Line, RoundedCornerShape(DeckBrand.RadiusM))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .dpadNavItem(nav, "toggle_web", shape = RoundedCornerShape(10.dp)) {
                                onToggleWebServer(!state.webRunning)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DeckIcon(
                                    icon = DeckIcons.Globe,
                                    tint = if (state.webRunning) DeckColors.Ruby else DeckColors.TextPrimary,
                                    size = 17.dp,
                                )
                                Text(
                                    stringResource(R.string.lan_web_title),
                                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (state.webRunning) DeckColors.Ruby else DeckColors.TextPrimary,
                                )
                            }
                            Text(
                                stringResource(R.string.lan_web_desc),
                                style = DeckType.settingsLabel,
                                color = DeckColors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        DeckSwitch(checked = state.webRunning)
                    }

                    if (state.webRunning) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DeckColors.DialogBg)
                                .border(0.6.dp, DeckColors.Line, RoundedCornerShape(10.dp))
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        state.webPassword.orEmpty(),
                                        style = DeckType.menuTitle.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 3.sp,
                                        ),
                                        color = DeckColors.Ruby,
                                    )
                                    val iconTint = DeckColors.Ruby
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(iconTint.copy(alpha = 0.12f))
                                            .border(0.6.dp, iconTint.copy(alpha = 0.45f), CircleShape)
                                            .dpadNavItem(nav, "refresh_pwd", shape = CircleShape, onClick = onRefreshWebPassword),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Canvas(Modifier.size(14.dp)) {
                                            val strokePx = 1.7.dp.toPx()
                                            val pad = strokePx * 0.8f
                                            val arcSize = size.width - pad * 2f
                                            drawArc(
                                                color = iconTint,
                                                startAngle = -50f,
                                                sweepAngle = 285f,
                                                useCenter = false,
                                                topLeft = Offset(pad, pad),
                                                size = Size(arcSize, arcSize),
                                                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                                            )
                                            val tipX = size.width * 0.86f
                                            val tipY = size.height * 0.36f
                                            val head = size.width * 0.24f
                                            val arrow = Path().apply {
                                                moveTo(tipX, tipY)
                                                lineTo(tipX - head * 1.15f, tipY - head * 0.15f)
                                                lineTo(tipX + head * 0.15f, tipY - head * 1.15f)
                                                close()
                                            }
                                            drawPath(arrow, color = iconTint)
                                        }
                                    }
                                }
                            }
                        }

                        // 当前已连接的客户端 IP、实时传输活动与上下行速率
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DeckColors.DialogBg)
                                .border(0.6.dp, DeckColors.Line, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.lan_web_clients_title, state.webClients.size),
                                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                    color = DeckColors.TextSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (state.webClients.isNotEmpty() || state.webTransferring) {
                                    val upSpeed = state.webUploadSpeedBps
                                    val downSpeed = state.webDownloadSpeedBps
                                    Text(
                                        "↑ ${formatSpeed(upSpeed)}",
                                        style = DeckType.metaLabel.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (upSpeed > 0L) FontWeight.Bold else FontWeight.Normal,
                                        ),
                                        color = if (upSpeed > 0L) Color(0xFF10B981) else DeckColors.TextSecondary,
                                    )
                                    Text(
                                        "↓ ${formatSpeed(downSpeed)}",
                                        style = DeckType.metaLabel.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (downSpeed > 0L) FontWeight.Bold else FontWeight.Normal,
                                        ),
                                        color = if (downSpeed > 0L) Color(0xFF0EA5E9) else DeckColors.TextSecondary,
                                    )
                                }
                            }
                            val visibleFileTasks = state.fileTasks.filterNot { t ->
                                t.kind == FileTaskKind.Upload &&
                                    state.webClients.any { c -> c.activeTransfers.any { tx -> tx.direction == WebTransferDirection.Upload && tx.fileName == t.title } }
                            }
                            if (state.webClients.isEmpty()) {
                                Text(
                                    stringResource(R.string.lan_web_clients_empty),
                                    style = DeckType.metaLabel,
                                    color = DeckColors.TextSecondary,
                                )
                            } else {
                                val nowMs = System.currentTimeMillis()
                                val uploadingLabel = stringResource(R.string.lan_web_uploading)
                                val downloadingLabel = stringResource(R.string.lan_web_downloading)
                                val idleLabel = stringResource(R.string.lan_web_client_idle)
                                state.webClients.forEach { client ->
                                    val clientTasks = visibleFileTasks.filter {
                                        it.source == FileTaskSource.Web && it.clientIp == client.ip
                                    }
                                    val hasTx = client.activeTransfers.isNotEmpty() || clientTasks.isNotEmpty()
                                    val mins = ((nowMs - client.lastActiveMs) / 60_000L).toInt()
                                    val activeStr = if (hasTx || mins <= 0) {
                                        stringResource(R.string.lan_web_client_just_now)
                                    } else {
                                        stringResource(R.string.lan_web_client_min_ago, mins)
                                    }
                                    val meta = listOfNotNull(
                                        client.userAgent.takeIf { it.isNotBlank() },
                                        activeStr,
                                        idleLabel.takeIf { !hasTx },
                                    ).joinToString(" · ")
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(if (hasTx) Color(0xFF0EA5E9) else Color(0xFF10B981)),
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
                                            if (client.activeTransfers.isNotEmpty()) {
                                                Column(
                                                    horizontalAlignment = Alignment.End,
                                                    modifier = Modifier.padding(end = 6.dp),
                                                ) {
                                                    if (client.uploadSpeedBps > 0L) {
                                                        Text(
                                                            "↑ ${formatSpeed(client.uploadSpeedBps)}",
                                                            style = DeckType.metaLabel.copy(
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.SemiBold,
                                                            ),
                                                            color = Color(0xFF10B981),
                                                        )
                                                    }
                                                    if (client.downloadSpeedBps > 0L) {
                                                        Text(
                                                            "↓ ${formatSpeed(client.downloadSpeedBps)}",
                                                            style = DeckType.metaLabel.copy(
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.SemiBold,
                                                            ),
                                                            color = Color(0xFF0EA5E9),
                                                        )
                                                    }
                                                }
                                            }
                                            if (client.sessionCount > 0 || hasTx) {
                                                Spacer(Modifier.width(4.dp))
                                                SmallPillButton(
                                                    label = stringResource(R.string.lan_web_client_kick),
                                                    danger = true,
                                                    swapAbxy = swapAbxy,
                                                    keyBindings = keyBindings,
                                                    onClick = { onKickWebClient(client.ip) },
                                                )
                                            }
                                        }
                                        client.activeTransfers.forEach { tx ->
                                            val isUp = tx.direction == WebTransferDirection.Upload
                                            val txColor = if (isUp) Color(0xFF10B981) else Color(0xFF0EA5E9)
                                            val dirPrefix = if (isUp) "⬆ $uploadingLabel" else "⬇ $downloadingLabel"
                                            val sizePart = if (tx.totalBytes > 0L) {
                                                "${formatSize(tx.bytesTransferred)}/${formatSize(tx.totalBytes)}"
                                            } else {
                                                formatSize(tx.bytesTransferred)
                                            }
                                            Column(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 15.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(txColor.copy(alpha = 0.08f))
                                                    .border(0.6.dp, txColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(
                                                        "$dirPrefix · ${tx.fileName}",
                                                        style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
                                                        color = DeckColors.TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    Text(
                                                        "$sizePart · ${formatSpeed(tx.speedBytesPerSec)}",
                                                        style = DeckType.metaLabel.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.SemiBold,
                                                        ),
                                                        color = txColor,
                                                        maxLines = 1,
                                                    )
                                                    TaskCancelCrossButton(
                                                        tint = txColor,
                                                        swapAbxy = swapAbxy,
                                                        keyBindings = keyBindings,
                                                        onClick = { onCancelWebTransfer(tx.id) },
                                                    )
                                                }
                                                Text(
                                                    tx.filePath.replace(sdPath, "~"),
                                                    style = DeckType.metaLabel.copy(fontFamily = FontFamily.Monospace),
                                                    color = DeckColors.TextSecondary,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (tx.totalBytes > 0L) {
                                                    LinearProgressIndicator(
                                                        progress = {
                                                            (tx.bytesTransferred.toFloat() / tx.totalBytes.toFloat()).coerceIn(0f, 1f)
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(4.dp)
                                                            .clip(DeckShapes.tabPill),
                                                        color = txColor,
                                                        trackColor = DeckColors.DialogBg,
                                                        gapSize = 0.dp,
                                                        drawStopIndicator = {},
                                                    )
                                                }
                                            }
                                        }
                                        clientTasks.forEach { task ->
                                            FileTaskItemRow(
                                                task = task,
                                                showSourceBadge = false,
                                                swapAbxy = swapAbxy,
                                                keyBindings = keyBindings,
                                                onCancel = onCancelFileTask,
                                                modifier = Modifier.padding(start = 15.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (state.webRunning || state.webTransferHistory.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            SmallPillButton(
                                label = "${stringResource(R.string.lan_web_history_btn, state.webTransferHistory.size)} →",
                                accent = true,
                                nav = nav,
                                navId = "web_history",
                                onClick = { showWebHistory = true },
                            )
                        }
                    }
                }

                val connectedIps = state.webClients.mapTo(HashSet()) { it.ip }
                val orphanFileTasks = state.fileTasks.filter { t ->
                    val dupUpload = t.kind == FileTaskKind.Upload &&
                        state.webClients.any { c -> c.activeTransfers.any { tx -> tx.direction == WebTransferDirection.Upload && tx.fileName == t.title } }
                    val attachedToClient = t.source == FileTaskSource.Web && t.clientIp in connectedIps
                    !dupUpload && !attachedToClient
                }
                if (orphanFileTasks.isNotEmpty()) {
                    LanActiveFileTasksCard(
                        tasks = orphanFileTasks,
                        swapAbxy = swapAbxy,
                        keyBindings = keyBindings,
                        onCancel = onCancelFileTask,
                    )
                }
            }
        }
    }

    if (showWebHistory) {
        WebTransferHistoryDialog(
            history = state.webTransferHistory,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onClearAll = onClearWebHistory,
            onDeleteItem = onDeleteWebHistoryItem,
            onOpenFolder = onOpenFolder,
            onDismiss = { showWebHistory = false },
        )
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
fun ShareTransferDialog(
    tx: ShareTransferState,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val active = tx.phase == TransferPhase.WaitingAccept ||
        tx.phase == TransferPhase.PinRequired ||
        tx.phase == TransferPhase.Transferring

    LaunchedEffect(tx.sessionId, tx.phase) { focusRequester.requestFocus() }

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
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier.width(440.dp),
            contentPadding = PaddingValues(22.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            val heading = when (tx.phase) {
                TransferPhase.WaitingAccept -> stringResource(R.string.lan_tx_waiting, tx.peerName)
                TransferPhase.PinRequired -> "${stringResource(R.string.lan_tx_pin_required, tx.peerName)}"
                TransferPhase.Transferring -> if (tx.direction == TransferDirection.Sending) {
                    stringResource(R.string.lan_tx_sending, tx.peerName)
                } else {
                    stringResource(R.string.lan_tx_receiving, tx.peerName)
                }
                TransferPhase.Completed -> "${stringResource(R.string.lan_tx_done)}"
                TransferPhase.Rejected -> stringResource(R.string.lan_tx_rejected, tx.peerName)
                TransferPhase.Cancelled -> stringResource(R.string.lan_tx_cancelled)
                TransferPhase.Failed -> "${stringResource(R.string.lan_tx_failed)}"
            }
            Text(
                heading,
                style = DeckType.menuTitle,
                color = if (tx.phase == TransferPhase.Failed) Color(0xFFDC2626) else DeckColors.TextPrimary,
                modifier = Modifier.padding(end = 34.dp),
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
                DeckProgressBar(progress = progress, height = 7.dp)
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

            if (active) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        stringResource(R.string.menu_cancel),
                        style = DeckType.menuButton,
                        color = Color(0xFFDC2626),
                        modifier = Modifier
                            .clip(DeckShapes.menuButton)
                            .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallPillButton(
    label: String,
    accent: Boolean = false,
    danger: Boolean = false,
    nav: DpadNavController? = null,
    navId: String? = null,
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
        accent -> DeckColors.AccentSoft
        else -> DeckColors.SurfaceAlt
    }
    val borderColor = Color.Transparent
    Text(
        label,
        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
        color = fg,
        modifier = Modifier
            .clip(DeckShapes.tabPill)
            .background(bg)
            .border(
                width = 0.6.dp,
                color = borderColor,
                shape = DeckShapes.tabPill,
            )
            .then(
                if (nav != null && navId != null) {
                    Modifier.dpadNavItem(nav, navId, shape = DeckShapes.tabPill, onClick = onClick)
                } else {
                    Modifier.gamepadClickable(
                        swapAbxy = swapAbxy,
                        keyBindings = keyBindings,
                        shape = DeckShapes.tabPill,
                        onClick = onClick,
                    )
                },
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun WebTransferHistoryDialog(
    history: List<WebTransferHistoryItem>,
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
                    "${stringResource(R.string.lan_web_history_title, history.size)}",
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
                        stringResource(R.string.lan_web_history_empty),
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
                        val isUp = item.direction == WebTransferDirection.Upload
                        val dirColor = if (isUp) Color(0xFF10B981) else Color(0xFF0EA5E9)
                        val dirLabel = if (isUp) {
                            "⬆ ${stringResource(R.string.lan_web_dir_upload)}"
                        } else {
                            "⬇ ${stringResource(R.string.lan_web_dir_download)}"
                        }
                        val targetFile = remember(item.filePath) { File(item.filePath) }
                        val openDir = remember(item.filePath) {
                            if (targetFile.isDirectory) targetFile else targetFile.parentFile
                        }?.takeIf { it.isDirectory }
                        val uaPart = item.userAgent.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                        val speedPart = item.avgSpeedBytesPerSec.takeIf { it > 0L }?.let { " · ${formatSpeed(it)}" }.orEmpty()
                        val timePart = if (item.completedAtMs > 0L) " · ${timeFmt.format(Date(item.completedAtMs))}" else ""

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
                                    Text(
                                        dirLabel,
                                        style = DeckType.metaLabel.copy(fontWeight = FontWeight.Bold),
                                        color = dirColor,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(dirColor.copy(alpha = 0.14f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                    if (!item.success) {
                                        Text(
                                            stringResource(R.string.lan_web_tx_failed),
                                            style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color(0xFFD97706),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
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
                                    "${item.clientIp}$uaPart · ${formatSize(item.fileSize)}$speedPart$timePart",
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
                                        label = "${stringResource(R.string.quick_share_history_jump_folder)}",
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

@Composable
private fun LanActiveFileTasksCard(
    tasks: List<FileTask>,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onCancel: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DeckColors.DialogBg)
            .border(0.6.dp, DeckColors.Ruby.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.lan_file_tasks_title, tasks.size),
            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
            color = DeckColors.TextSecondary,
        )
        tasks.forEach { task ->
            FileTaskItemRow(
                task = task,
                showSourceBadge = true,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun FileTaskItemRow(
    task: FileTask,
    showSourceBadge: Boolean,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (task.kind) {
        FileTaskKind.Compress -> "ZIP"
        FileTaskKind.Extract -> "EXT"
        FileTaskKind.Copy -> "CP"
        FileTaskKind.Move -> "MV"
        FileTaskKind.Delete -> "DEL"
        FileTaskKind.Upload -> "UP"
    }
    val kindLabel = stringResource(
        when (task.kind) {
            FileTaskKind.Compress -> R.string.lib_task_compress
            FileTaskKind.Extract -> R.string.lib_task_extract
            FileTaskKind.Copy -> R.string.lib_task_copy
            FileTaskKind.Move -> R.string.lib_task_move
            FileTaskKind.Delete -> R.string.lib_task_delete
            FileTaskKind.Upload -> R.string.lib_task_upload
        },
    )
    val srcLabel = stringResource(
        if (task.source == FileTaskSource.Web) R.string.lib_task_src_web else R.string.lib_task_src_local,
    )
    val hasTotal = task.totalBytes > 0L
    val pct = if (hasTotal) (task.bytesDone.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f) else null
    val progressText = when {
        hasTotal -> "${((pct ?: 0f) * 100).toInt()}% · ${formatSize(task.bytesDone)} / ${formatSize(task.totalBytes)}"
        task.bytesDone > 0L -> stringResource(R.string.lib_task_processed_fmt, formatSize(task.bytesDone))
        else -> ""
    }
    val subParts = listOfNotNull(
        progressText.takeIf { it.isNotEmpty() },
        task.detail.takeIf { it.isNotBlank() && it != task.title },
    ).joinToString(" · ")

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DeckColors.Ruby.copy(alpha = 0.07f))
            .border(0.6.dp, DeckColors.Ruby.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = DeckColors.Ruby,
                strokeWidth = 2.dp,
            )
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "$icon $kindLabel · ${task.title}",
                        style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
                        color = DeckColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (showSourceBadge) {
                        Text(
                            srcLabel,
                            style = DeckType.metaLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = if (task.source == FileTaskSource.Web) DeckColors.Ruby else DeckColors.TextSecondary,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.Ruby.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                if (subParts.isNotEmpty()) {
                    Text(
                        subParts,
                        style = DeckType.metaLabel,
                        color = DeckColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (task.cancellable) {
                TaskCancelCrossButton(
                    tint = Color(0xFFDC2626),
                    enabled = !task.cancelling,
                    swapAbxy = swapAbxy,
                    keyBindings = keyBindings,
                    onClick = { onCancel(task.id) },
                )
            }
        }
        if (pct != null) {
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(DeckShapes.tabPill),
                color = DeckColors.Ruby,
                trackColor = DeckColors.DialogBg,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(DeckShapes.tabPill),
                color = DeckColors.Ruby,
                trackColor = DeckColors.DialogBg,
            )
        }
    }
}

@Composable
private fun TaskCancelCrossButton(
    tint: Color,
    enabled: Boolean = true,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onClick: () -> Unit,
) {
    val activeTint = if (enabled) tint else DeckColors.TextSecondary.copy(alpha = 0.5f)
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(activeTint.copy(alpha = 0.12f))
            .border(0.6.dp, activeTint.copy(alpha = 0.45f), CircleShape)
            .gamepadClickable(swapAbxy, keyBindings, shape = CircleShape) {
                if (enabled) onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(9.dp)) {
            val stroke = 1.6.dp.toPx()
            drawLine(
                color = activeTint,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = activeTint,
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun ShareTransferProgressSection(
    tx: ShareTransferState,
    formatSize: (Long) -> String,
) {
    val progress = if (tx.totalBytes > 0L) {
        (tx.bytesTransferred.toFloat() / tx.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    if (tx.phase == TransferPhase.WaitingAccept || tx.phase == TransferPhase.PinRequired) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(DeckShapes.tabPill),
            color = DeckColors.Ruby,
            trackColor = DeckColors.DialogBg,
        )
    } else if (tx.phase == TransferPhase.Transferring || tx.phase == TransferPhase.Completed) {
        DeckProgressBar(progress = progress, height = 7.dp)
        val speedText = if (tx.speedBytesPerSec > 0L) " · ${formatSize(tx.speedBytesPerSec)}/s" else ""
        val prefix = if (tx.totalFiles > 1) "${stringResource(R.string.lan_total_progress)} · " else ""
        Text(
            "$prefix${formatSize(tx.bytesTransferred)} / ${formatSize(tx.totalBytes)} (${(progress * 100).toInt()}%)$speedText",
            style = DeckType.metaLabel,
            color = DeckColors.TextSecondary,
        )
        if (tx.totalFiles > 1 && tx.phase == TransferPhase.Transferring && tx.activeFiles.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                tx.activeFiles.forEach { af ->
                    val filePct = if (af.totalBytes > 0L) {
                        (af.bytesTransferred.toFloat() / af.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val fileSpeed = if (af.speedBytesPerSec > 0L) " · ${formatSize(af.speedBytesPerSec)}/s" else ""
                    val fileMeta = if (af.totalBytes > 0L) {
                        "${formatSize(af.bytesTransferred)} / ${formatSize(af.totalBytes)} (${(filePct * 100).toInt()}%)$fileSpeed"
                    } else {
                        "${formatSize(af.bytesTransferred)}$fileSpeed"
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DeckColors.DialogBg.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                af.fileName,
                                style = DeckType.metaLabel,
                                color = DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                fileMeta,
                                style = DeckType.metaLabel,
                                color = DeckColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                        if (af.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { filePct },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.5.dp)
                                    .clip(DeckShapes.tabPill),
                                color = DeckColors.Ruby.copy(alpha = 0.85f),
                                trackColor = DeckColors.Surface,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0 B/s"
    if (bytesPerSec < 1024L) return "$bytesPerSec B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB/s", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB/s", mb)
    return String.format(Locale.US, "%.2f GB/s", mb / 1024.0)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

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
