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
                // 0-A) 接收确认卡片（仅在管理/接收页面显示与确认，不在全局乱弹窗）
                state.incomingRequest?.let { req ->
                    LanIncomingRequestCard(
                        req = req,
                        incomingDirIdx = incomingDirIdx,
                        onSelectDir = { incomingDirIdx = it },
                        sdPath = sdPath,
                        nav = nav,
                        onRejectIncoming = onRejectIncoming,
                        onAcceptIncoming = onAcceptIncoming,
                    )
                }

                // 0-B) 当前传输进度 / 取消或关闭状态卡片
                state.transfer?.let { tx ->
                    LanActiveTransferCard(
                        tx = tx,
                        nav = nav,
                        onCancelTransfer = onCancelTransfer,
                        onDismissTransferBanner = onDismissTransferBanner,
                    )
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
                // 3) 独立服务 A：游戏快传
                LanShareServiceCard(
                    state = state,
                    nav = nav,
                    onToggleQuickShare = onToggleQuickShare,
                    onScanPeers = onScanPeers,
                    onToggleLocalSendCompat = onToggleLocalSendCompat,
                    onOpenQuickShareScreen = onOpenQuickShareScreen,
                )

                // 4) 独立服务 B：Web 游戏管理（动态密码登录）
                LanWebServiceCard(
                    state = state,
                    nav = nav,
                    sdPath = sdPath,
                    swapAbxy = swapAbxy,
                    keyBindings = keyBindings,
                    onToggleWebServer = onToggleWebServer,
                    onRefreshWebPassword = onRefreshWebPassword,
                    onKickWebClient = onKickWebClient,
                    onCancelWebTransfer = onCancelWebTransfer,
                    onCancelFileTask = onCancelFileTask,
                    onOpenWebHistory = { showWebHistory = true },
                )

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

