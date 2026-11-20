package com.pocketdeck.launcher.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.PlayStats
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DetailAction(
    val label: String,
    val primary: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * F2 游戏详情预览卡（Sunset Arcade）：
 * 封面 + 简介 + 发行商/年份/平台/文件大小 + 游玩次数/累计时长/上次游玩 + 多盘信息 + 快捷操作栏。
 */
@Composable
fun GameDetailDialog(
    game: Game,
    platform: Platform,
    covers: CoverSource,
    metadata: GameMetadata?,
    officialName: String?,
    playStats: PlayStats?,
    isFavorite: Boolean,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onLaunch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onManualScrape: () -> Unit,
    onProperties: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var actionIdx by remember(game.id) { mutableIntStateOf(0) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    val scrollState = rememberScrollState()

    val actions = listOf(
        DetailAction(stringResource(R.string.menu_play), primary = true, onClick = onLaunch),
        DetailAction(
            if (isFavorite) stringResource(R.string.menu_unfavourite) else stringResource(R.string.menu_favourite),
            onClick = onToggleFavorite,
        ),
        DetailAction(stringResource(R.string.menu_edit), onClick = onEdit),
        DetailAction(stringResource(R.string.menu_manual_scrape), onClick = onManualScrape),
        DetailAction(stringResource(R.string.menu_properties), onClick = onProperties),
    )

    LaunchedEffect(game.id) {
        runCatching { focusRequester.requestFocus() }
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
                    event.key == Key.DirectionRight -> {
                        actionIdx = (actionIdx + 1) % actions.size
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        actionIdx = (actionIdx - 1 + actions.size) % actions.size
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        actions.getOrNull(actionIdx)?.onClick?.invoke()
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
                .padding(horizontal = 28.dp, vertical = 18.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                val coverH = 172.dp
                val coverW = (coverH * platform.coverAspect).coerceIn(104.dp, 168.dp)
                GameCoverCard(
                    game = game,
                    platform = platform,
                    covers = covers,
                    officialName = officialName,
                    width = coverW,
                    height = coverH,
                    isFocused = false,
                )

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = game.title,
                        style = DeckType.menuTitle.copy(fontWeight = FontWeight.Bold),
                        color = DeckColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 28.dp),
                    )
                    val metaChips = listOfNotNull(
                        platform.fullName,
                        metadata?.releaseDate,
                        metadata?.publisher ?: metadata?.developer,
                        metadata?.genre,
                        formatDetailFileSize(game.fileSize),
                        if (game.discFiles.size > 1) stringResource(R.string.detail_multi_disc_fmt, game.discFiles.size) else null,
                    )
                    if (metaChips.isNotEmpty()) {
                        Text(
                            text = metaChips.joinToString(" · "),
                            style = DeckType.settingsLabel,
                            color = DeckColors.TextSecondary,
                        )
                    }

                    // 游玩统计栏（次数 / 累计时长 / 上次游玩）
                    val count = playStats?.playCount ?: 0
                    val totalStr = formatPlayDuration(playStats?.totalPlayMs ?: 0L)
                    val lastStr = playStats?.lastPlayedAt?.takeIf { it > 0L }?.let {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
                    } ?: stringResource(R.string.detail_never_played)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeckColors.Surface)
                            .border(0.6.dp, DeckColors.Line, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatCell(stringResource(R.string.detail_play_count), "$count")
                        StatCell(stringResource(R.string.detail_play_time), totalStr)
                        StatCell(stringResource(R.string.detail_last_played), lastStr)
                    }

                    val desc = metadata?.description?.takeIf { it.isNotBlank() }
                    if (desc != null) {
                        Text(
                            text = desc,
                            style = DeckType.metaLabel,
                            color = DeckColors.TextSecondary,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = DeckColors.Line)
            Spacer(Modifier.height(12.dp))

            // 底部快捷操作栏
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEachIndexed { idx, act ->
                    val hl = idx == actionIdx
                    val bgMod = when {
                        act.primary && hl -> Modifier.brandBackground(DeckShapes.tabPill)
                        hl -> Modifier.background(DeckColors.AccentSoft)
                        act.primary -> Modifier.background(DeckColors.Ruby.copy(alpha = 0.14f))
                        else -> Modifier.background(DeckColors.Surface)
                    }
                    val fgColor = when {
                        act.primary && hl -> Color.White
                        hl || act.primary -> DeckColors.Ruby
                        else -> DeckColors.TextPrimary
                    }
                    Box(
                        Modifier
                            .weight(if (act.primary) 1.3f else 1f)
                            .clip(DeckShapes.tabPill)
                            .then(bgMod)
                            .brandBorder(active = hl, shape = DeckShapes.tabPill, activeWidth = 1.4.dp, inactiveWidth = 0.6.dp)
                            .soundClickable {
                                actionIdx = idx
                                act.onClick()
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = act.label,
                            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
                            color = fgColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = DeckType.metaLabel, color = DeckColors.TextSecondary)
        Text(
            value,
            style = DeckType.settingsLabel.copy(fontWeight = FontWeight.SemiBold),
            color = DeckColors.TextPrimary,
            maxLines = 1,
        )
    }
}

private fun formatPlayDuration(ms: Long): String {
    if (ms < 60_000L) return "< 1m"
    val totalMins = ms / 60_000L
    val hours = totalMins / 60L
    val mins = totalMins % 60L
    return if (hours > 0L) "${hours}h ${mins}m" else "${mins}m"
}

private fun formatDetailFileSize(bytes: Long): String? {
    if (bytes <= 0L) return null
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}
