package com.pocketdeck.launcher.ui.lan

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.ArchiveManager
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.lan.LanServerState
import com.pocketdeck.launcher.lan.ShareHistoryItem
import com.pocketdeck.launcher.lan.ShareProtocol
import com.pocketdeck.launcher.lan.TransferDirection
import com.pocketdeck.launcher.lan.TransferPhase
import com.pocketdeck.launcher.ui.apps.drawBackArrow
import com.pocketdeck.launcher.ui.components.gamepadClickable
import com.pocketdeck.launcher.ui.components.scrimTapDismiss
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.library.ArchivePreviewDialog
import com.pocketdeck.launcher.ui.library.AudioPlayerDialog
import com.pocketdeck.launcher.ui.library.FontViewerDialog
import com.pocketdeck.launcher.ui.library.ImageViewerDialog
import com.pocketdeck.launcher.ui.library.TextFileViewerEditorDialog
import com.pocketdeck.launcher.ui.settings.NameDialog
import com.pocketdeck.launcher.ui.settings.NameDialogSpec
import com.pocketdeck.launcher.ui.settings.NameDialogState
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class QuickShareTab { Transfer, History }

/**
 * 独立的游戏快传界面：
 * - 「快传收发」：服务开关、网卡选择、设备昵称、在线设备扫描、待接收请求确认、传输进度与取消
 * - 「接收记录」：历史接收文件列表、查看详情/预览、单条删除（可选删本地文件）、一键清空记录
 */
@Composable
fun QuickShareScreen(
    state: LanServerState,
    initialTab: QuickShareTab = QuickShareTab.Transfer,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onRefreshInterfaces: () -> Unit,
    onSelectInterface: (String) -> Unit,
    onRenameDevice: (String) -> Unit,
    onRandomizeDeviceName: () -> Unit,
    onToggleQuickShare: (Boolean) -> Unit,
    onToggleLocalSendCompat: (Boolean) -> Unit = {},
    onScanPeers: () -> Unit,
    onAcceptIncoming: (String, File) -> Unit,
    onRejectIncoming: (String) -> Unit,
    onCancelTransfer: () -> Unit,
    onDismissTransferBanner: () -> Unit,
    onDeleteHistory: (String, Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onApplyFontId: (String) -> Unit = {},
    onRescanLibrary: () -> Unit = {},
    onOpenFolder: (dir: File, focusFile: File?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sdPath = remember { Environment.getExternalStorageDirectory().path }
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var editingName by remember { mutableStateOf(false) }
    var incomingDirIdx by remember(state.incomingRequest?.sessionId) { mutableIntStateOf(0) }
    var detailItem by remember { mutableStateOf<ShareHistoryItem?>(null) }
    var deleteConfirmItem by remember { mutableStateOf<ShareHistoryItem?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    var previewArchive by remember { mutableStateOf<File?>(null) }
    var previewImage by remember { mutableStateOf<File?>(null) }
    var previewAudio by remember { mutableStateOf<File?>(null) }
    var previewFont by remember { mutableStateOf<File?>(null) }
    var previewText by remember { mutableStateOf<File?>(null) }

    val renameTitle = stringResource(R.string.lan_device_name)
    val nameSpec = remember(editingName, state.deviceName) {
        if (editingName) NameDialogSpec(renameTitle, state.deviceName) { onRenameDevice(it) } else null
    }
    val nameState = remember(nameSpec) { nameSpec?.let { NameDialogState(it) } }
    val rootFr = remember { FocusRequester() }
    val toggleFr = remember { FocusRequester() }
    val clearFr = remember { FocusRequester() }

    val subOverlayOpen = editingName ||
        detailItem != null ||
        deleteConfirmItem != null ||
        confirmClearAll ||
        previewArchive != null ||
        previewImage != null ||
        previewAudio != null ||
        previewFont != null ||
        previewText != null

    LaunchedEffect(Unit) {
        onRefreshInterfaces()
    }
    LaunchedEffect(activeTab, subOverlayOpen) {
        if (!subOverlayOpen) {
            when (activeTab) {
                QuickShareTab.Transfer -> runCatching { toggleFr.requestFocus() }.onFailure { runCatching { rootFr.requestFocus() } }
                QuickShareTab.History -> runCatching { clearFr.requestFocus() }.onFailure { runCatching { rootFr.requestFocus() } }
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            previewArchive != null -> previewArchive = null
            previewImage != null -> previewImage = null
            previewAudio != null -> previewAudio = null
            previewFont != null -> previewFont = null
            previewText != null -> previewText = null
            deleteConfirmItem != null -> deleteConfirmItem = null
            confirmClearAll -> confirmClearAll = false
            detailItem != null -> detailItem = null
            editingName -> editingName = false
            else -> onDismiss()
        }
    }

    fun openFileWithViewer(file: File) {
        if (!file.isFile) {
            Toast.makeText(context, context.getString(R.string.quick_share_history_missing), Toast.LENGTH_SHORT).show()
            return
        }
        when {
            ArchiveManager.isImageFile(file) -> previewImage = file
            ArchiveManager.isAudioFile(file) -> previewAudio = file
            ArchiveManager.isFontFile(file) -> previewFont = file
            ArchiveManager.isArchive(file) -> previewArchive = file
            ArchiveManager.isKnownTextFile(file) -> previewText = file
            else -> Toast.makeText(context, file.absolutePath, Toast.LENGTH_LONG).show()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(DeckColors.Background)
            .focusRequester(rootFr)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (subOverlayOpen) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    GamepadKeys.isPrevTab(event.key, swapAbxy, keyBindings) -> {
                        activeTab = QuickShareTab.Transfer
                        true
                    }
                    GamepadKeys.isNextTab(event.key, swapAbxy, keyBindings) -> {
                        activeTab = QuickShareTab.History
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶部导航栏
            val iconTint = DeckColors.TextPrimary
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .gamepadClickable(swapAbxy, keyBindings, shape = RoundedCornerShape(8.dp), onClick = onDismiss)
                        .drawBehind { drawBackArrow(iconTint) },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.quick_share_title),
                        style = DeckType.menuTitle,
                        color = DeckColors.TextPrimary,
                        maxLines = 1,
                    )
                    Text(
                        if (state.shareRunning) {
                            "${state.shareIp.orEmpty()}:${state.sharePort} · ${state.deviceName}"
                        } else {
                            state.deviceName
                        },
                        style = DeckType.settingsLabel,
                        color = if (state.shareRunning) DeckColors.Ruby else DeckColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 分段标签切换：快传收发 | 接收记录
                Row(
                    Modifier
                        .clip(DeckShapes.tabPill)
                        .background(DeckColors.Surface)
                        .border(1.dp, DeckColors.DialogBorder, DeckShapes.tabPill)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val histLabel = if (state.shareHistory.isNotEmpty()) {
                        "${stringResource(R.string.quick_share_tab_history)} (${state.shareHistory.size})"
                    } else {
                        stringResource(R.string.quick_share_tab_history)
                    }
                    QuickShareTabPill(
                        label = stringResource(R.string.quick_share_tab_transfer),
                        selected = activeTab == QuickShareTab.Transfer,
                        swapAbxy = swapAbxy,
                        keyBindings = keyBindings,
                        onClick = { activeTab = QuickShareTab.Transfer },
                    )
                    QuickShareTabPill(
                        label = histLabel,
                        selected = activeTab == QuickShareTab.History,
                        swapAbxy = swapAbxy,
                        keyBindings = keyBindings,
                        onClick = { activeTab = QuickShareTab.History },
                    )
                }
            }

            HorizontalDivider(color = DeckColors.DialogBorder)

            if (activeTab == QuickShareTab.Transfer) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 1) 待确认接收请求
                    state.incomingRequest?.let { req ->
                        val chosenDir = req.availableDirs.getOrElse(incomingDirIdx) { req.defaultTargetDir }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DeckColors.Ruby.copy(alpha = 0.08f))
                                .border(1.5.dp, DeckColors.Ruby, RoundedCornerShape(14.dp))
                                .padding(16.dp),
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
                            val summaryText = if (req.fileCount > 1) {
                                "${stringResource(R.string.lan_multi_file_summary, req.fileCount)} · ${formatQuickSize(req.fileSize)}"
                            } else {
                                "${req.fileName} · ${formatQuickSize(req.fileSize)}"
                            }
                            Text(
                                summaryText,
                                style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                color = DeckColors.TextPrimary,
                            )
                            if (req.fileCount > 1) {
                                val previewNames = req.fileNames.take(5).joinToString(" · ") +
                                    if (req.fileNames.size > 5) " …(+${req.fileNames.size - 5})" else ""
                                Text(
                                    previewNames,
                                    style = DeckType.metaLabel,
                                    color = DeckColors.TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                stringResource(R.string.lan_incoming_save_to),
                                style = DeckType.settingsLabel,
                                color = DeckColors.TextSecondary,
                            )
                            req.availableDirs.forEachIndexed { idx, dir ->
                                val selected = idx == incomingDirIdx
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(DeckShapes.settingsDropdown)
                                        .background(if (selected) DeckColors.Ruby.copy(alpha = 0.14f) else DeckColors.DialogBg)
                                        .border(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = if (selected) DeckColors.Ruby else DeckColors.DialogBorder,
                                            shape = DeckShapes.settingsDropdown,
                                        )
                                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) {
                                            incomingDirIdx = idx
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        dir.path.replace(sdPath, "~"),
                                        style = DeckType.settingsLabel.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                                        color = if (selected) DeckColors.Ruby else DeckColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (selected) Text("✓", style = DeckType.settingsLabel, color = DeckColors.Ruby)
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.lan_incoming_reject),
                                    style = DeckType.menuButton,
                                    color = DeckColors.TextSecondary,
                                    modifier = Modifier
                                        .clip(DeckShapes.menuButton)
                                        .background(DeckColors.Surface)
                                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.menuButton) {
                                            onRejectIncoming(req.sessionId)
                                        }
                                        .padding(horizontal = 18.dp, vertical = 9.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.lan_incoming_accept),
                                    style = DeckType.menuButton,
                                    color = DeckColors.Background,
                                    modifier = Modifier
                                        .clip(DeckShapes.menuButton)
                                        .background(DeckColors.Ruby)
                                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.menuButton) {
                                            onAcceptIncoming(req.sessionId, chosenDir)
                                        }
                                        .padding(horizontal = 22.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }

                    // 2) 当前传输进度
                    state.transfer?.let { tx ->
                        val txActive = tx.phase == TransferPhase.WaitingAccept || tx.phase == TransferPhase.Transferring
                        val progress = if (tx.totalBytes > 0L) {
                            (tx.bytesTransferred.toFloat() / tx.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DeckColors.Surface)
                                .border(1.5.dp, if (txActive) DeckColors.Ruby else DeckColors.DialogBorder, RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                val heading = when (tx.phase) {
                                    TransferPhase.WaitingAccept -> "⏳ ${stringResource(R.string.lan_tx_waiting, tx.peerName)}"
                                    TransferPhase.Transferring -> if (tx.direction == TransferDirection.Sending) {
                                        "📤 ${stringResource(R.string.lan_tx_sending, tx.peerName)}"
                                    } else {
                                        "📥 ${stringResource(R.string.lan_tx_receiving, tx.peerName)}"
                                    }
                                    TransferPhase.Completed -> "✅ ${stringResource(R.string.lan_tx_done)}"
                                    TransferPhase.Rejected -> stringResource(R.string.lan_tx_rejected, tx.peerName)
                                    TransferPhase.Cancelled -> stringResource(R.string.lan_tx_cancelled)
                                    TransferPhase.Failed -> "⚠ ${stringResource(R.string.lan_tx_failed)}"
                                }
                                Text(
                                    heading,
                                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (tx.phase == TransferPhase.Failed) Color(0xFFDC2626) else DeckColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (txActive) {
                                        if (tx.direction == TransferDirection.Receiving) stringResource(R.string.lan_cancel_receive)
                                        else stringResource(R.string.lan_cancel_send)
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
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            Text(
                                if (tx.totalFiles > 1 && txActive) "(${tx.currentFileIndex}/${tx.totalFiles}) ${tx.fileName}" else tx.fileName,
                                style = DeckType.settingsLabel,
                                color = DeckColors.TextSecondary,
                            )
                            if (tx.phase == TransferPhase.WaitingAccept) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(DeckShapes.tabPill),
                                    color = DeckColors.Ruby,
                                    trackColor = DeckColors.DialogBg,
                                )
                            } else if (tx.phase == TransferPhase.Transferring || tx.phase == TransferPhase.Completed) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(DeckShapes.tabPill),
                                    color = DeckColors.Ruby,
                                    trackColor = DeckColors.DialogBg,
                                )
                                val speedText = if (tx.speedBytesPerSec > 0L) " · ${formatQuickSize(tx.speedBytesPerSec)}/s" else ""
                                Text(
                                    "${formatQuickSize(tx.bytesTransferred)} / ${formatQuickSize(tx.totalBytes)} (${(progress * 100).toInt()}%)$speedText",
                                    style = DeckType.metaLabel,
                                    color = DeckColors.TextSecondary,
                                )
                            }
                        }
                    }

                    // 3) 快传开关与在线设备发现
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (state.shareRunning) DeckColors.Ruby.copy(alpha = 0.06f) else DeckColors.Surface)
                            .border(
                                width = if (state.shareRunning) 1.5.dp else 1.dp,
                                color = if (state.shareRunning) DeckColors.Ruby.copy(alpha = 0.7f) else DeckColors.DialogBorder,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .gamepadClickable(swapAbxy, keyBindings, focusRequester = toggleFr, shape = RoundedCornerShape(10.dp)) {
                                    onToggleQuickShare(!state.shareRunning)
                                },
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
                                        stringResource(R.string.lan_share_online_fmt, state.shareIp.orEmpty(), state.sharePort, state.peers.size)
                                    } else {
                                        stringResource(R.string.lan_share_desc)
                                    },
                                    style = DeckType.settingsLabel,
                                    color = DeckColors.TextSecondary,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
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
                            HorizontalDivider(color = DeckColors.DialogBorder)
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    if (state.peers.isEmpty()) stringResource(R.string.lan_share_peers_empty)
                                    else state.peers.joinToString("\n") { p ->
                                        val tag = if (p.protocol == ShareProtocol.LocalSend) "[LocalSend] " else "[PocketDeck] "
                                        "📱 $tag${p.deviceName} (${p.model}) · ${p.ip}:${p.port}"
                                    },
                                    style = DeckType.settingsLabel,
                                    color = DeckColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "↻ ${stringResource(R.string.lan_share_scan)}",
                                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                    color = DeckColors.Ruby,
                                    modifier = Modifier
                                        .clip(DeckShapes.tabPill)
                                        .background(DeckColors.Ruby.copy(alpha = 0.14f))
                                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill, onClick = onScanPeers)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }

                        HorizontalDivider(color = DeckColors.DialogBorder)

                        // LocalSend 协议兼容开关（默认开启，可手动关闭）
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .gamepadClickable(swapAbxy, keyBindings, shape = RoundedCornerShape(10.dp)) {
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
                            Spacer(Modifier.width(12.dp))
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
                    }

                    // 4) 本机昵称与服务网卡设置
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DeckColors.Surface)
                            .border(1.dp, DeckColors.DialogBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.lan_device_name), style = DeckType.settingsLabel, color = DeckColors.TextSecondary)
                                Text(
                                    state.deviceName.ifBlank { "..." },
                                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.SemiBold),
                                    color = DeckColors.TextPrimary,
                                )
                            }
                            Text(
                                "🎲 ${stringResource(R.string.lan_device_random)}",
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = DeckColors.TextPrimary,
                                modifier = Modifier
                                    .clip(DeckShapes.tabPill)
                                    .background(DeckColors.Background)
                                    .border(1.dp, DeckColors.DialogBorder, DeckShapes.tabPill)
                                    .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill, onClick = onRandomizeDeviceName)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "✎ ${stringResource(R.string.lan_device_rename)}",
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = DeckColors.Ruby,
                                modifier = Modifier
                                    .clip(DeckShapes.tabPill)
                                    .background(DeckColors.Ruby.copy(alpha = 0.14f))
                                    .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill) { editingName = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }

                        HorizontalDivider(color = DeckColors.DialogBorder)

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.lan_nic_title),
                                style = DeckType.settingsLabel,
                                color = DeckColors.TextSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "↻ ${stringResource(R.string.lan_nic_refresh)}",
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = DeckColors.TextPrimary,
                                modifier = Modifier
                                    .clip(DeckShapes.tabPill)
                                    .background(DeckColors.Background)
                                    .border(1.dp, DeckColors.DialogBorder, DeckShapes.tabPill)
                                    .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill, onClick = onRefreshInterfaces)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }

                        if (state.interfaces.isEmpty()) {
                            Text(stringResource(R.string.lan_nic_empty), style = DeckType.settingsLabel, color = Color(0xFFDC2626))
                        } else {
                            state.interfaces.forEach { iface ->
                                val selected = state.selectedInterface?.interfaceName == iface.interfaceName
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(DeckShapes.settingsDropdown)
                                        .background(if (selected) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.DialogBg)
                                        .border(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = if (selected) DeckColors.Ruby else DeckColors.DialogBorder,
                                            shape = DeckShapes.settingsDropdown,
                                        )
                                        .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.settingsDropdown) {
                                            onSelectInterface(iface.interfaceName)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) DeckColors.Ruby else DeckColors.TextSecondary.copy(alpha = 0.4f)),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        iface.labelWithIp,
                                        style = DeckType.settingsLabel.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                                        color = if (selected) DeckColors.Ruby else DeckColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (selected) Text("✓", style = DeckType.settingsValue, color = DeckColors.Ruby)
                                }
                            }
                        }
                    }
                }
            } else {
                // 接收历史记录视图
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.quick_share_history_count, state.shareHistory.size),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.shareHistory.isNotEmpty()) {
                            Text(
                                "🗑 ${stringResource(R.string.quick_share_history_clear)}",
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFDC2626),
                                modifier = Modifier
                                    .clip(DeckShapes.tabPill)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                                    .gamepadClickable(swapAbxy, keyBindings, focusRequester = clearFr, shape = DeckShapes.tabPill) {
                                        confirmClearAll = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }

                    if (state.shareHistory.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.quick_share_history_empty),
                                style = DeckType.emptyMessage,
                                color = DeckColors.TextSecondary,
                            )
                        }
                    } else {
                        val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(state.shareHistory, key = { _, it -> it.id }) { _, item ->
                                val paths = if (item.filePaths.isNotEmpty()) item.filePaths else listOf(item.filePath)
                                val anyExists = paths.any { File(it).exists() }
                                val timeStr = remember(item.receivedAtMs) { fmt.format(Date(item.receivedAtMs)) }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DeckColors.Surface)
                                        .border(1.dp, DeckColors.DialogBorder, RoundedCornerShape(12.dp))
                                        .gamepadClickable(swapAbxy, keyBindings, shape = RoundedCornerShape(12.dp)) {
                                            detailItem = item
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (item.fileCount > 1) "📦" else "📥", fontSize = 18.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                item.fileName,
                                                style = DeckType.settingsValue.copy(fontWeight = FontWeight.Medium),
                                                color = DeckColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (!anyExists) {
                                                Text(
                                                    stringResource(R.string.quick_share_history_missing),
                                                    style = DeckType.metaLabel,
                                                    color = Color(0xFFDC2626),
                                                    modifier = Modifier
                                                        .clip(DeckShapes.tabPill)
                                                        .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            "${item.senderName} (${item.senderIp}) · ${formatQuickSize(item.totalBytes)} · $timeStr",
                                            style = DeckType.metaLabel,
                                            color = DeckColors.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            item.filePath.replace(sdPath, "~"),
                                            style = DeckType.metaLabel.copy(fontFamily = FontFamily.Monospace),
                                            color = DeckColors.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    val folderTarget = remember(item) { resolveHistoryFolderTarget(item) }
                                    if (folderTarget != null) {
                                        Text(
                                            "📂 ${stringResource(R.string.quick_share_history_jump_folder)}",
                                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                            color = DeckColors.Ruby,
                                            modifier = Modifier
                                                .clip(DeckShapes.tabPill)
                                                .background(DeckColors.Ruby.copy(alpha = 0.12f))
                                                .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill) {
                                                    onOpenFolder(folderTarget.first, folderTarget.second)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        stringResource(R.string.quick_share_history_delete),
                                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFFDC2626),
                                        modifier = Modifier
                                            .clip(DeckShapes.tabPill)
                                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                            .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill) {
                                                deleteConfirmItem = item
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 记录详情与文件预览弹窗
        detailItem?.let { item ->
            ShareHistoryDetailDialog(
                item = item,
                sdPath = sdPath,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onOpenFile = ::openFileWithViewer,
                onOpenFolder = { dir, focusFile ->
                    detailItem = null
                    onOpenFolder(dir, focusFile)
                },
                onDelete = {
                    detailItem = null
                    deleteConfirmItem = item
                },
                onDismiss = { detailItem = null },
            )
        }

        // 单条删除确认弹窗（支持仅删除记录 or 同时删除本地文件）
        deleteConfirmItem?.let { item ->
            val paths = if (item.filePaths.isNotEmpty()) item.filePaths else listOf(item.filePath)
            val hasDiskFiles = paths.any { File(it).isFile }
            DeleteHistoryItemDialog(
                item = item,
                hasDiskFiles = hasDiskFiles,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onDeleteOnly = {
                    onDeleteHistory(item.id, false)
                    deleteConfirmItem = null
                },
                onDeleteWithFiles = {
                    onDeleteHistory(item.id, true)
                    deleteConfirmItem = null
                    onRescanLibrary()
                },
                onDismiss = { deleteConfirmItem = null },
            )
        }

        // 清空全部确认弹窗
        if (confirmClearAll) {
            ClearHistoryConfirmDialog(
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onConfirm = {
                    onClearHistory()
                    confirmClearAll = false
                },
                onDismiss = { confirmClearAll = false },
            )
        }

        // 修改本机昵称弹窗
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

        // 内置文件预览弹窗
        previewImage?.let { img ->
            ImageViewerDialog(img, swapAbxy, keyBindings) { previewImage = null }
        }
        previewAudio?.let { audio ->
            AudioPlayerDialog(audio, swapAbxy, keyBindings) { previewAudio = null }
        }
        previewFont?.let { font ->
            FontViewerDialog(font, swapAbxy, keyBindings, onApplyFontId) { previewFont = null }
        }
        previewArchive?.let { arc ->
            ArchivePreviewDialog(
                file = arc,
                swapAbxy = swapAbxy,
                keyBindings = keyBindings,
                onExtractHere = {},
                onExtractSubfolder = {},
                onExtractPickDir = {},
                onDismiss = { previewArchive = null },
            )
        }
        previewText?.let { txt ->
            TextFileViewerEditorDialog(txt, swapAbxy, keyBindings, onSaved = {}, onDismiss = { previewText = null })
        }
    }
}

@Composable
private fun QuickShareTabPill(
    label: String,
    selected: Boolean,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = DeckType.settingsLabel.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
        color = if (selected) Color.White else DeckColors.TextSecondary,
        modifier = Modifier
            .clip(DeckShapes.tabPill)
            .background(if (selected) DeckColors.Ruby else Color.Transparent)
            .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun ShareHistoryDetailDialog(
    item: ShareHistoryItem,
    sdPath: String,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onOpenFile: (File) -> Unit,
    onOpenFolder: (dir: File, focusFile: File?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val closeFr = remember { FocusRequester() }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val files = remember(item) {
        val raw = if (item.filePaths.isNotEmpty()) item.filePaths else listOf(item.filePath)
        raw.map(::File)
    }

    LaunchedEffect(item.id) { runCatching { closeFr.requestFocus() } }

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
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(520.dp)
                .heightIn(max = maxH)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.quick_share_history_detail), style = DeckType.menuTitle, color = DeckColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.quick_share_history_from, item.senderName, item.senderIp),
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
            )
            Text(
                "${fmt.format(Date(item.receivedAtMs))} · ${formatQuickSize(item.totalBytes)}",
                style = DeckType.metaLabel,
                color = DeckColors.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DeckColors.DialogBorder)
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                files.forEach { file ->
                    val exists = file.isFile
                    val canPreview = exists && (
                        ArchiveManager.isImageFile(file) ||
                            ArchiveManager.isAudioFile(file) ||
                            ArchiveManager.isFontFile(file) ||
                            ArchiveManager.isArchive(file) ||
                            ArchiveManager.isKnownTextFile(file)
                        )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(DeckShapes.settingsDropdown)
                            .background(DeckColors.Surface)
                            .border(1.dp, DeckColors.DialogBorder, DeckShapes.settingsDropdown)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                file.name,
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = if (exists) DeckColors.TextPrimary else Color(0xFFDC2626),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                file.absolutePath.replace(sdPath, "~"),
                                style = DeckType.metaLabel.copy(fontFamily = FontFamily.Monospace),
                                color = DeckColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        val parentDir = file.parentFile?.takeIf { it.isDirectory }
                        if (canPreview || parentDir != null || !exists) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (canPreview) {
                                    Text(
                                        stringResource(R.string.quick_share_history_open_file),
                                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                        color = DeckColors.Ruby,
                                        modifier = Modifier
                                            .clip(DeckShapes.tabPill)
                                            .background(DeckColors.Ruby.copy(alpha = 0.12f))
                                            .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill) {
                                                onOpenFile(file)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                } else if (!exists) {
                                    Text(
                                        stringResource(R.string.quick_share_history_missing),
                                        style = DeckType.metaLabel,
                                        color = Color(0xFFDC2626),
                                    )
                                } else {
                                    Text(formatQuickSize(file.length()), style = DeckType.metaLabel, color = DeckColors.TextSecondary)
                                }
                                if (parentDir != null) {
                                    Text(
                                        "📂 ${stringResource(R.string.quick_share_history_jump_folder)}",
                                        style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                        color = DeckColors.TextPrimary,
                                        modifier = Modifier
                                            .clip(DeckShapes.tabPill)
                                            .background(DeckColors.DialogBg)
                                            .border(1.dp, DeckColors.DialogBorder, DeckShapes.tabPill)
                                            .gamepadClickable(swapAbxy, keyBindings, shape = DeckShapes.tabPill) {
                                                onOpenFolder(parentDir, if (exists) file else null)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            val dialogFolderTarget = remember(item) { resolveHistoryFolderTarget(item) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (dialogFolderTarget != null) {
                    Text(
                        "📂 ${stringResource(R.string.quick_share_history_open_folder)}",
                        style = DeckType.menuButton,
                        color = DeckColors.TextPrimary,
                        modifier = Modifier
                            .clip(DeckShapes.menuButton)
                            .background(DeckColors.Surface)
                            .border(1.dp, DeckColors.DialogBorder, DeckShapes.menuButton)
                            .gamepadClickable(swapAbxy, keyBindings) {
                                onOpenFolder(dialogFolderTarget.first, dialogFolderTarget.second)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    stringResource(R.string.quick_share_history_delete),
                    style = DeckType.menuButton,
                    color = Color(0xFFDC2626),
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                        .gamepadClickable(swapAbxy, keyBindings, onClick = onDelete)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.prop_close),
                    style = DeckType.menuButton,
                    color = DeckColors.Ruby,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(DeckColors.Ruby.copy(alpha = 0.12f))
                        .gamepadClickable(swapAbxy, keyBindings, focusRequester = closeFr, onClick = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun resolveHistoryFolderTarget(item: ShareHistoryItem): Pair<File, File?>? {
    val paths = if (item.filePaths.isNotEmpty()) item.filePaths else listOf(item.filePath)
    for (p in paths) {
        if (p.isBlank()) continue
        val f = File(p)
        if (f.isFile && f.parentFile?.isDirectory == true) return f.parentFile!! to f
    }
    val primary = item.filePath.takeIf { it.isNotBlank() }?.let(::File)
    if (primary != null) {
        if (primary.isDirectory) return primary to null
        val parent = primary.parentFile
        if (parent != null && parent.isDirectory) return parent to null
    }
    return null
}

@Composable
private fun DeleteHistoryItemDialog(
    item: ShareHistoryItem,
    hasDiskFiles: Boolean,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onDeleteOnly: () -> Unit,
    onDeleteWithFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(item.id) { runCatching { firstFr.requestFocus() } }

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
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(400.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.quick_share_history_delete), style = DeckType.menuTitle, color = DeckColors.TextPrimary)
            Text(item.fileName, style = DeckType.settingsLabel, color = DeckColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Text(
                stringResource(R.string.quick_share_history_delete_only),
                style = DeckType.menuButton,
                color = DeckColors.Ruby,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DeckShapes.menuButton)
                    .background(DeckColors.Ruby.copy(alpha = 0.12f))
                    .gamepadClickable(swapAbxy, keyBindings, focusRequester = firstFr, onClick = onDeleteOnly)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            if (hasDiskFiles) {
                Text(
                    stringResource(R.string.quick_share_history_delete_with_files),
                    style = DeckType.menuButton,
                    color = Color(0xFFDC2626),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DeckShapes.menuButton)
                        .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                        .gamepadClickable(swapAbxy, keyBindings, onClick = onDeleteWithFiles)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Text(
                stringResource(R.string.menu_cancel),
                style = DeckType.menuButton,
                color = DeckColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(DeckShapes.menuButton)
                    .gamepadClickable(swapAbxy, keyBindings, onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ClearHistoryConfirmDialog(
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFr.requestFocus() } }

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
                } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(400.dp)
                .clip(DeckShapes.menuPanel)
                .background(DeckColors.DialogBg)
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.quick_share_history_clear_title), style = DeckType.menuTitle, color = DeckColors.TextPrimary)
            Text(stringResource(R.string.quick_share_history_clear_desc), style = DeckType.settingsLabel, color = DeckColors.TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.menu_cancel),
                    style = DeckType.menuButton,
                    color = DeckColors.TextSecondary,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .gamepadClickable(swapAbxy, keyBindings, focusRequester = cancelFr, onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.quick_share_history_clear),
                    style = DeckType.menuButton,
                    color = Color.White,
                    modifier = Modifier
                        .clip(DeckShapes.menuButton)
                        .background(Color(0xFFDC2626))
                        .gamepadClickable(swapAbxy, keyBindings, onClick = onConfirm)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun formatQuickSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
