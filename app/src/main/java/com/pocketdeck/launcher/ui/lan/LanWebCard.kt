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


@Composable
internal fun LanWebServiceCard(
    state: LanServerState,
    nav: DpadNavController,
    sdPath: String,
    nicExpanded: Boolean,
    onToggleNicExpanded: () -> Unit,
    onRefreshInterfaces: () -> Unit,
    onSelectInterface: (String) -> Unit,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onToggleWebServer: (Boolean) -> Unit,
    onRefreshWebPassword: () -> Unit,
    onKickWebClient: (String) -> Unit,
    onCancelWebTransfer: (String) -> Unit,
    onCancelFileTask: (String) -> Unit,
    onOpenWebHistory: () -> Unit,
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
        if (!state.webRunning) {
            Text(
                stringResource(R.string.lan_web_desc),
                style = DeckType.settingsLabel,
                color = DeckColors.TextSecondary,
            )
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.lan_web_url_label),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                        )
                        val nicName = state.selectedInterface?.interfaceName ?: "NIC"
                        Text(
                            text = "$nicName ${if (nicExpanded) "▴" else "▾"}",
                            style = DeckType.metaLabel.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                            color = if (nicExpanded) DeckColors.Ruby else DeckColors.TextPrimary,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .background(if (nicExpanded) DeckColors.AccentSoft else DeckColors.Surface)
                                .border(0.6.dp, if (nicExpanded) DeckColors.Ruby else DeckColors.Line, DeckShapes.tabPill)
                                .dpadNavItem(nav, "toggle_nic", shape = DeckShapes.tabPill, onClick = onToggleNicExpanded)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
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

            if (nicExpanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeckColors.DialogBg)
                        .border(0.6.dp, DeckColors.Line, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.lan_nic_title),
                            style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
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
                            style = DeckType.metaLabel,
                            color = Color(0xFFDC2626),
                        )
                    } else {
                        state.interfaces.forEachIndexed { nicIdx, nic ->
                            val selected = nic.interfaceName == state.selectedInterface?.interfaceName
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) DeckColors.AccentSoft else DeckColors.Surface)
                                    .brandBorder(active = selected, shape = RoundedCornerShape(8.dp), activeWidth = 1.2.dp, inactiveWidth = 0.5.dp)
                                    .dpadNavItem(nav, "nic_$nicIdx", shape = RoundedCornerShape(8.dp)) {
                                        onSelectInterface(nic.interfaceName)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    nic.labelWithIp,
                                    style = DeckType.settingsLabel.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = if (selected) DeckColors.Ruby else DeckColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selected) DeckIcon(DeckIcons.Check, size = 15.dp, tint = DeckColors.Ruby)
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
                                if (client.activeTransfers.isNotEmpty() || client.uploadSpeedBps > 0L || client.downloadSpeedBps > 0L) {
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
                    onClick = { onOpenWebHistory() },
                )
            }
        }
    }
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

