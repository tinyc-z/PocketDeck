package com.pocketdeck.launcher.ui.library

import android.content.Context
import android.os.Environment
import android.widget.Toast
import com.pocketdeck.launcher.data.formatCompactSize
import com.pocketdeck.launcher.lan.TransferPhase
import com.pocketdeck.launcher.ui.components.BrandTileIcon
import com.pocketdeck.launcher.ui.components.FavoriteHeartIcon
import com.pocketdeck.launcher.ui.components.soundClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.pocketdeck.launcher.ui.components.CircularProgressIndicator
import com.pocketdeck.launcher.ui.components.HorizontalDivider
import com.pocketdeck.launcher.ui.components.LinearProgressIndicator
import com.pocketdeck.launcher.ui.components.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.ui.components.DeckIcon
import com.pocketdeck.launcher.ui.components.DeckIcons
import com.pocketdeck.launcher.ui.components.InlineExpandableSearchPill
import com.pocketdeck.launcher.ui.components.icons.PanelIcons
import com.pocketdeck.launcher.data.ArchiveManager
import com.pocketdeck.launcher.data.Category
import com.pocketdeck.launcher.data.CoverResolutionInfo
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.FileTask
import com.pocketdeck.launcher.data.FileTaskKind
import com.pocketdeck.launcher.data.FileTaskSource
import com.pocketdeck.launcher.data.MetadataResolutionInfo
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.RomScanner
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Library
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.lan.LanServerManager
import com.pocketdeck.launcher.lan.LanServerState
import com.pocketdeck.launcher.lan.QuickShareService
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.components.DialogPanelCard
import com.pocketdeck.launcher.ui.components.GameCategoryPickerDialog
import com.pocketdeck.launcher.ui.components.GameCoverCard
import com.pocketdeck.launcher.ui.components.GamePropertiesDialog
import com.pocketdeck.launcher.ui.edit.DeleteFileConfirmDialog
import com.pocketdeck.launcher.ui.edit.EditGameScreen
import android.content.ClipData
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.pocketdeck.launcher.ui.lan.LanServiceControlDialog
import com.pocketdeck.launcher.ui.lan.ShareMethodDialog
import com.pocketdeck.launcher.ui.lan.SharePeerPickerDialog
import com.pocketdeck.launcher.ui.settings.ActionItem
import com.pocketdeck.launcher.ui.settings.ActionMenuDialog
import com.pocketdeck.launcher.ui.settings.ActionMenuState
import com.pocketdeck.launcher.ui.settings.NameDialog
import com.pocketdeck.launcher.ui.settings.NameDialogSpec
import com.pocketdeck.launcher.ui.settings.NameDialogState
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.blockOverlayTouchThrough
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.cardSurface
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import com.pocketdeck.launcher.ui.theme.brandPillBackground
import com.pocketdeck.launcher.ui.theme.DeckFontFamily
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean


@Composable
internal fun LibraryHeaderBar(
    allGamesCount: Int,
    hiddenCount: Int,
    lanState: LanServerState,
    mode: LibraryMode,
    focusZone: LibraryZone,
    headerIdx: Int,
    onOpenLanDialog: () -> Unit,
    onSelectMode: (LibraryMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val compactHeader = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 500
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = if (compactHeader) 12.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val backHl = focusZone == LibraryZone.Header && headerIdx == 0
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (backHl) DeckColors.AccentSoft else DeckColors.Surface)
                .brandBorder(active = backHl, shape = CircleShape, activeWidth = 1.3.dp, inactiveWidth = 0.6.dp)
                .soundClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            DeckIcon(
                icon = DeckIcons.ChevronLeft,
                tint = if (backHl) DeckColors.Ruby else DeckColors.TextPrimary,
                size = 18.dp,
            )
        }
        Spacer(Modifier.width(if (compactHeader) 8.dp else 10.dp))
        if (!compactHeader) {
            GameLibraryAppIcon(size = 28.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.game_library_title),
                fontFamily = DeckFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compactHeader) 15.sp else 18.sp,
                color = DeckColors.TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.game_library_stats, allGamesCount, hiddenCount),
                style = DeckType.metaLabel,
                color = DeckColors.TextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }

        // 局域网服务与任务进度入口（有任意任务时以半透明进度条填充，取最慢任务）
        val taskProgress = lanState.slowestActiveTaskProgress
        val hasActiveTasks = taskProgress != null
        val lanActive = lanState.anyRunning || hasActiveTasks
        val lanAccent = when {
            hasActiveTasks -> DeckColors.StatusInfo
            lanState.shareRunning -> DeckColors.StatusSuccess
            else -> DeckColors.StatusWarning
        }
        val lanHl = focusZone == LibraryZone.Header && headerIdx == 1
        val targetTaskProgress = (taskProgress ?: 0f).coerceIn(0f, 1f)
        val reduceMotion = com.pocketdeck.launcher.ui.theme.LocalReduceMotion.current
        val animTaskProgress = if (reduceMotion) {
            targetTaskProgress
        } else {
            androidx.compose.animation.core.animateFloatAsState(
                targetValue = targetTaskProgress,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                label = "lib_lan_task_progress",
            ).value
        }
        Row(
            Modifier
                .clip(DeckShapes.tabPill)
                .background(
                    when {
                        hasActiveTasks -> lanAccent.copy(alpha = 0.12f)
                        lanActive -> lanAccent.copy(alpha = 0.16f)
                        lanHl -> DeckColors.Ruby.copy(alpha = 0.12f)
                        else -> DeckColors.Surface
                    },
                )
                .drawBehind {
                    if (hasActiveTasks && animTaskProgress > 0f) {
                        drawRect(
                            color = lanAccent.copy(alpha = 0.28f),
                            size = androidx.compose.ui.geometry.Size(size.width * animTaskProgress, size.height),
                        )
                    }
                }
                .border(
                    width = if (lanActive || lanHl) 0.9.dp else 0.6.dp,
                    color = when {
                        lanHl -> DeckColors.Ruby
                        lanActive -> lanAccent
                        else -> DeckColors.Line
                    },
                    shape = DeckShapes.tabPill,
                )
                .soundClickable { onOpenLanDialog() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (lanActive) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(lanAccent))
            }
            Text(
                text = when {
                    hasActiveTasks -> stringResource(R.string.lan_status_task_pct, (taskProgress.coerceIn(0f, 1f) * 100f).toInt())
                    lanActive -> when {
                        lanState.shareRunning && lanState.webRunning -> stringResource(R.string.lan_status_both)
                        lanState.shareRunning -> stringResource(R.string.lan_status_share)
                        lanState.webRunning -> stringResource(R.string.lan_status_web)
                        else -> stringResource(R.string.lan_status_tasks)
                    }
                    else -> "${stringResource(R.string.lan_service_btn)}"
                },
                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                color = if (lanActive) lanAccent else if (lanHl) DeckColors.Ruby else DeckColors.TextPrimary,
            )
        }
        Spacer(Modifier.width(10.dp))

        // 模式切换：游戏管理 | 目录文件
        Row(
            Modifier
                .clip(DeckShapes.tabPill)
                .background(DeckColors.Surface)
                .border(0.6.dp, DeckColors.Line, DeckShapes.tabPill)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ModePill(
                label = stringResource(R.string.lib_mode_games),
                selected = mode == LibraryMode.Games,
                focused = focusZone == LibraryZone.Header && headerIdx == 2,
                onClick = { onSelectMode(LibraryMode.Games) },
            )
            ModePill(
                label = stringResource(R.string.lib_mode_files),
                selected = mode == LibraryMode.Files,
                focused = focusZone == LibraryZone.Header && headerIdx == 3,
                onClick = { onSelectMode(LibraryMode.Files) },
            )
        }
    }
}

@Composable
internal fun ModePill(
    label: String,
    selected: Boolean,
    focused: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = DeckType.settingsLabel.copy(fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium),
        color = if (selected) Color.White else if (focused) DeckColors.Ruby else DeckColors.TextSecondary,
        modifier = Modifier
            .clip(DeckShapes.tabPill)
            .brandPillBackground(selected = selected, shape = DeckShapes.tabPill)
            .then(
                if (focused && selected) {
                    Modifier.border(1.2.dp, Color.White.copy(alpha = 0.9f), DeckShapes.tabPill)
                } else {
                    Modifier.brandBorder(active = focused, shape = DeckShapes.tabPill, activeWidth = 1.2.dp, inactiveWidth = 0.dp, inactiveColor = Color.Transparent)
                },
            )
            .soundClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

internal data class FilesToolbarAction(
    val label: String = "",
    val icon: ImageVector? = null,
    val isSearch: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
internal fun FilterChipPill(
    label: String,
    selected: Boolean,
    focused: Boolean = false,
    accent: Boolean = true,
    onClick: () -> Unit,
) {
    val activeBg = if (accent) DeckColors.AccentSoft else DeckColors.PillSelected
    val activeFg = if (accent) DeckColors.Ruby else DeckColors.PillText
    Text(
        text = label,
        style = DeckType.settingsLabel.copy(fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium),
        color = if (selected) Color.White else if (focused) activeFg else DeckColors.TextSecondary,
        modifier = Modifier
            .clip(DeckShapes.tabPill)
            .brandPillBackground(
                selected = selected,
                shape = DeckShapes.tabPill,
                unselectedColor = if (focused) activeBg else DeckColors.Surface,
            )
            .then(
                if (selected) {
                    if (focused) Modifier.border(1.4.dp, Color.White.copy(alpha = 0.9f), DeckShapes.tabPill) else Modifier
                } else {
                    Modifier.brandBorder(active = focused, shape = DeckShapes.tabPill, activeWidth = 1.4.dp, inactiveWidth = 0.6.dp)
                },
            )
            .soundClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
internal fun TagBadge(text: String, accent: Boolean) {
    Text(
        text = text,
        style = DeckType.metaLabel,
        color = if (accent) DeckColors.Ruby else DeckColors.TextSecondary,
        modifier = Modifier
            .clip(DeckShapes.tabPill)
            .background(if (accent) DeckColors.Ruby.copy(alpha = 0.12f) else DeckColors.Background)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable

internal fun FileTasksBanner(
    tasks: List<FileTask>,
    onCancel: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tasks.forEach { task ->
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
            val pct = if (hasTotal) ((task.bytesDone.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f)) else null
            val progressText = when {
                hasTotal -> {
                    val doneStr = formatCompactSize(task.bytesDone) ?: "0 KB"
                    val totalStr = formatCompactSize(task.totalBytes) ?: ""
                    "${((pct ?: 0f) * 100).toInt()}% · $doneStr / $totalStr"
                }
                task.bytesDone > 0L -> stringResource(
                    R.string.lib_task_processed_fmt,
                    formatCompactSize(task.bytesDone) ?: "0 KB",
                )
                else -> ""
            }
            val subParts = listOfNotNull(
                progressText.takeIf { it.isNotEmpty() },
                task.detail.takeIf { it.isNotBlank() && it != task.title },
            ).joinToString(" · ")

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeckColors.Surface)
                    .border(0.75.dp, DeckColors.Ruby.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
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
                                style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            TagBadge(srcLabel, accent = task.source == FileTaskSource.Web)
                        }
                        if (subParts.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
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
                        Text(
                            stringResource(if (task.cancelling) R.string.lib_task_cancelling else R.string.lib_task_cancel),
                            style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = if (task.cancelling) DeckColors.TextSecondary else DeckColors.Ruby,
                            modifier = Modifier
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.Ruby.copy(alpha = 0.12f))
                                .soundClickable(enabled = !task.cancelling) { onCancel(task.id) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (pct != null) {
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = DeckColors.Ruby,
                        trackColor = DeckColors.Line,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = DeckColors.Ruby,
                        trackColor = DeckColors.Line,
                    )
                }
            }
        }
    }
}
