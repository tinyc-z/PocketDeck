package com.pocketdeck.launcher.ui.lan

import android.os.Environment
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.FileTaskKind
import com.pocketdeck.launcher.data.FileTaskSource
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.lan.LanServerState
import com.pocketdeck.launcher.lan.TransferPhase
import com.pocketdeck.launcher.lan.WebTransferDirection
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.DeckSwitch
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.DpadNavController
import com.pocketdeck.launcher.ui.components.dpadNavItem
import com.pocketdeck.launcher.ui.components.scrimTapDismiss
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.settings.NameDialog
import com.pocketdeck.launcher.ui.settings.NameDialogSpec
import com.pocketdeck.launcher.ui.settings.NameDialogState
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import kotlinx.coroutines.delay
import java.io.File

/**
 * 局域网服务与游戏快传控制面板（v3 首屏零滚动重构）：
 * - 顶部高优置顶区：待确认接收 / 进行中传输自动插队至双开关之上，打开第一眼即可处理
 * - 首屏并排双卡：左「网页管理（IP · 接入设备数）」+ 右「游戏快传（主机名 · 附近设备数）」
 * - 下方按服务归属展开详情：网卡切换归属网页管理，主机名修改归属游戏快传，支持 L1/R1 切换
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
    onClearShareHistory: () -> Unit = {},
    onDeleteShareHistoryItem: (String) -> Unit = {},
    onOpenFolder: ((File, File?) -> Unit)? = null,
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
    var showShareHistory by remember { mutableStateOf(false) }
    // 0 = Web 网页管理，1 = 游戏快传（若仅快传在运行或有来件则默认展示快传）
    var activeTab by remember {
        mutableIntStateOf(
            if (state.incomingRequest != null || (state.shareRunning && !state.webRunning)) 1 else 0,
        )
    }
    var nicExpanded by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = editingName || showWebHistory || showShareHistory) {
        when {
            editingName -> editingName = false
            showWebHistory -> showWebHistory = false
            else -> showShareHistory = false
        }
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

    val view = LocalView.current
    LaunchedEffect(editingName, showWebHistory, showShareHistory, state.pinChallenge != null, state.incomingRequest?.sessionId) {
        if (!editingName && !showWebHistory && !showShareHistory && state.pinChallenge == null) {
            delay(30)
            runCatching { nav.rootFr.requestFocus() }
            nav.ensureDefault(
                preferredId = if (state.incomingRequest != null) "inc_accept" else "tile_web",
                inTouchMode = view.isInTouchMode,
            )
        }
    }
    LaunchedEffect(Unit) {
        onRefreshInterfaces()
    }

    val connectedIps = state.webClients.mapTo(HashSet()) { it.ip }
    val orphanFileTasks = state.fileTasks.filter { t ->
        val dupUpload = t.kind == FileTaskKind.Upload &&
            state.webClients.any { c -> c.activeTransfers.any { tx -> tx.direction == WebTransferDirection.Upload && tx.fileName == t.title } }
        val attachedToClient = t.source == FileTaskSource.Web && t.clientIp in connectedIps
        !dupUpload && !attachedToClient
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow)
            .scrimTapDismiss(onDismiss)
            .focusRequester(nav.rootFr)
            .focusProperties {
                canFocus = !editingName && !showWebHistory && !showShareHistory && state.pinChallenge == null
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (editingName || showWebHistory || showShareHistory || state.pinChallenge != null) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    GamepadKeys.isPrevTab(event.key, swapAbxy, keyBindings) -> {
                        activeTab = 0
                        nicExpanded = false
                        true
                    }
                    GamepadKeys.isNextTab(event.key, swapAbxy, keyBindings) -> {
                        activeTab = 1
                        nicExpanded = false
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
                .padding(horizontal = 28.dp, vertical = 18.dp)
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            // 顶部标题栏 + 状态徽章 + 全部停止
            val txNow = state.transfer
            val txActive = txNow != null && (
                txNow.phase == TransferPhase.WaitingAccept ||
                    txNow.phase == TransferPhase.PinRequired ||
                    txNow.phase == TransferPhase.Transferring
                )
            val headerBadge = when {
                state.incomingRequest != null -> stringResource(R.string.lan_badge_incoming)
                txActive && txNow != null && txNow.totalBytes > 0L -> {
                    val pct = ((txNow.bytesTransferred * 100L) / txNow.totalBytes).toInt().coerceIn(0, 100)
                    stringResource(R.string.lan_status_task_pct, pct)
                }
                else -> null
            }

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
                )
                if (headerBadge != null) {
                    Text(
                        text = headerBadge,
                        style = DeckType.metaLabel.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier
                            .clip(DeckShapes.tabPill)
                            .brandBackground(DeckShapes.tabPill)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.anyRunning) {
                    Text(
                        stringResource(R.string.lan_stop_all),
                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFDC2626),
                        modifier = Modifier
                            .clip(DeckShapes.tabPill)
                            .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                            .dpadNavItem(nav, "stop_all", shape = DeckShapes.tabPill, onClick = onStopAll)
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DeckColors.Line)
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 0) 高优置顶区：待确认接收 / 进行中传输 / 后台文件任务（进入弹窗第一眼即见）
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

                state.transfer?.let { tx ->
                    LanActiveTransferCard(
                        tx = tx,
                        nav = nav,
                        onCancelTransfer = onCancelTransfer,
                        onDismissTransferBanner = onDismissTransferBanner,
                    )
                }

                if (orphanFileTasks.isNotEmpty()) {
                    LanActiveFileTasksCard(
                        tasks = orphanFileTasks,
                        swapAbxy = swapAbxy,
                        keyBindings = keyBindings,
                        onCancel = onCancelFileTask,
                    )
                }

                // 1) 首屏并排双开关卡：左「网页管理（IP · 接入设备数）」+ 右「游戏快传（主机名 · 附近设备数）」
                val activeIp = state.selectedInterface?.ipAddress
                    ?: state.webUrl?.removePrefix("http://")?.substringBefore(':')
                    ?: ""
                val offLabel = stringResource(R.string.lan_status_off)
                val webSub = if (state.webRunning) {
                    val conn = stringResource(R.string.lan_web_connected_short, state.webClients.size)
                    if (activeIp.isNotBlank()) "$activeIp · $conn" else conn
                } else {
                    if (activeIp.isNotBlank()) "$activeIp · $offLabel" else offLabel
                }
                val devName = state.deviceName.ifBlank { "..." }
                val shareSub = if (state.shareRunning) {
                    "$devName · ${stringResource(R.string.lan_share_peers_short, state.peers.size)}"
                } else {
                    "$devName · $offLabel"
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 左卡：Web 网页管理
                    val webSelected = activeTab == 0
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(DeckBrand.RadiusM))
                            .background(if (webSelected) DeckColors.AccentSoft else DeckColors.Surface)
                            .brandBorder(
                                active = webSelected,
                                shape = RoundedCornerShape(DeckBrand.RadiusM),
                                activeWidth = 1.5.dp,
                                inactiveWidth = 0.7.dp,
                            )
                            .dpadNavItem(nav, "tile_web", shape = RoundedCornerShape(DeckBrand.RadiusM)) {
                                if (activeTab == 0) {
                                    onToggleWebServer(!state.webRunning)
                                } else {
                                    activeTab = 0
                                    nicExpanded = false
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeckIcon(
                                icon = DeckIcons.Globe,
                                tint = if (state.webRunning || webSelected) DeckColors.Ruby else DeckColors.TextPrimary,
                                size = 16.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.lan_web_title),
                                style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                color = if (state.webRunning || webSelected) DeckColors.Ruby else DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.soundClickable {
                                    activeTab = 0
                                    onToggleWebServer(!state.webRunning)
                                },
                            ) {
                                DeckSwitch(checked = state.webRunning)
                            }
                        }
                        Text(
                            text = webSub,
                            style = DeckType.metaLabel.copy(fontFamily = FontFamily.Monospace),
                            color = DeckColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 右卡：游戏快传
                    val shareSelected = activeTab == 1
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(DeckBrand.RadiusM))
                            .background(if (shareSelected) DeckColors.AccentSoft else DeckColors.Surface)
                            .brandBorder(
                                active = shareSelected,
                                shape = RoundedCornerShape(DeckBrand.RadiusM),
                                activeWidth = 1.5.dp,
                                inactiveWidth = 0.7.dp,
                            )
                            .dpadNavItem(nav, "tile_share", shape = RoundedCornerShape(DeckBrand.RadiusM)) {
                                if (activeTab == 1) {
                                    onToggleQuickShare(!state.shareRunning)
                                } else {
                                    activeTab = 1
                                    nicExpanded = false
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeckIcon(
                                icon = DeckIcons.Send,
                                tint = if (state.shareRunning || shareSelected) DeckColors.Ruby else DeckColors.TextPrimary,
                                size = 16.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.lan_share_title),
                                style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                color = if (state.shareRunning || shareSelected) DeckColors.Ruby else DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.incomingRequest != null) {
                                Box(
                                    Modifier
                                        .padding(end = 4.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(DeckColors.Ruby),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier.soundClickable {
                                    activeTab = 1
                                    onToggleQuickShare(!state.shareRunning)
                                },
                            ) {
                                DeckSwitch(checked = state.shareRunning)
                            }
                        }
                        Text(
                            text = shareSub,
                            style = DeckType.metaLabel,
                            color = DeckColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 2) 上下文详情区（按服务归属：Web 管理含网卡选择，快传含本机昵称）
                if (activeTab == 0) {
                    LanWebServiceCard(
                        state = state,
                        nav = nav,
                        sdPath = sdPath,
                        nicExpanded = nicExpanded,
                        onToggleNicExpanded = { nicExpanded = !nicExpanded },
                        onRefreshInterfaces = onRefreshInterfaces,
                        onSelectInterface = { name ->
                            onSelectInterface(name)
                            nicExpanded = false
                        },
                        swapAbxy = swapAbxy,
                        keyBindings = keyBindings,
                        onToggleWebServer = onToggleWebServer,
                        onRefreshWebPassword = onRefreshWebPassword,
                        onKickWebClient = onKickWebClient,
                        onCancelWebTransfer = onCancelWebTransfer,
                        onCancelFileTask = onCancelFileTask,
                        onOpenWebHistory = { showWebHistory = true },
                    )
                } else {
                    LanShareServiceCard(
                        state = state,
                        nav = nav,
                        onToggleQuickShare = onToggleQuickShare,
                        onRandomizeDeviceName = onRandomizeDeviceName,
                        onOpenRenameDialog = { editingName = true },
                        onScanPeers = onScanPeers,
                        onToggleLocalSendCompat = onToggleLocalSendCompat,
                        onOpenShareHistory = { showShareHistory = true },
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

    if (showShareHistory) {
        ShareTransferHistoryDialog(
            history = state.shareHistory,
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
            onClearAll = onClearShareHistory,
            onDeleteItem = onDeleteShareHistoryItem,
            onOpenFolder = onOpenFolder,
            onDismiss = { showShareHistory = false },
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
