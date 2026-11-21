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
internal fun LanIncomingRequestCard(
    req: IncomingShareRequest,
    incomingDirIdx: Int,
    onSelectDir: (Int) -> Unit,
    sdPath: String,
    nav: DpadNavController,
    onRejectIncoming: (String) -> Unit,
    onAcceptIncoming: (String, File) -> Unit,
) {
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
                        onSelectDir(dIdx)
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


@Composable
internal fun LanActiveTransferCard(
    tx: ShareTransferState,
    nav: DpadNavController,
    onCancelTransfer: () -> Unit,
    onDismissTransferBanner: () -> Unit,
) {
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

@Composable
internal fun SmallPillButton(
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
internal fun LanActiveFileTasksCard(
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
internal fun FileTaskItemRow(
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
internal fun TaskCancelCrossButton(
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

internal fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0 B/s"
    if (bytesPerSec < 1024L) return "$bytesPerSec B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB/s", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB/s", mb)
    return String.format(Locale.US, "%.2f GB/s", mb / 1024.0)
}

internal fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

