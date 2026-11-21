package com.pocketdeck.launcher.ui.components

import com.pocketdeck.launcher.data.formatCompactSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.PlayStats
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.components.icons.HomeIcons
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandBorder
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DetailAction(
    val label: String,
    val primary: Boolean = false,
    val active: Boolean = false,
    val isFavoriteToggle: Boolean = false,
    val shortcutBadge: String? = null,
    val onClick: () -> Unit,
)

private enum class DetailFocusZone {
    Actions,
    CoverEdit,
    Properties,
}

/**
 * 桌面游戏详情与操作中心（Sunset Arcade）：
 * 封面 + 所属分类/模拟器徽章 + 发行商/年份/平台/文件大小 + 游玩统计 + 简介 / 多盘内联选盘 + 快捷操作栏。
 * 支持横屏左右分栏与窄屏（<480dp）上下分块自适应。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameDetailDialog(
    game: Game,
    platform: Platform,
    covers: CoverSource,
    metadata: GameMetadata?,
    officialName: String?,
    playStats: PlayStats?,
    isFavorite: Boolean,
    categoryNames: List<String> = emptyList(),
    launcherLabel: String? = null,
    swapAbxy: Boolean = false,
    keyBindings: Map<KeyAction, Int> = emptyMap(),
    onLaunch: () -> Unit,
    onLaunchDisc: ((String) -> Unit)? = null,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onManualScrape: (() -> Unit)? = null,
    onProperties: () -> Unit,
    onDismiss: () -> Unit,
    isTopmost: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    var actionIdx by remember(game.id) { mutableIntStateOf(0) }
    var focusZone by remember(game.id) { mutableStateOf(DetailFocusZone.Actions) }
    val propertiesFocused = focusZone == DetailFocusZone.Properties
    val coverEditFocused = focusZone == DetailFocusZone.CoverEdit && onManualScrape != null
    var pickingDisc by remember(game.id) { mutableStateOf(false) }
    var discIdx by remember(game.id, pickingDisc) { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val hasMultiDisc = game.discFiles.size > 1 && onLaunchDisc != null
    val discLabelFmt = stringResource(R.string.disc_label_fmt)
    val actions = buildList {
        add(
            DetailAction(
                label = stringResource(R.string.menu_play),
                primary = true,
                onClick = onLaunch,
            ),
        )
        if (hasMultiDisc) {
            add(
                DetailAction(
                    if (pickingDisc) stringResource(R.string.detail_action_back_info)
                    else stringResource(R.string.detail_action_discs, game.discFiles.size),
                    onClick = { pickingDisc = !pickingDisc },
                ),
            )
        }
        add(
            DetailAction(
                label = if (isFavorite) stringResource(R.string.menu_unfavourite) else stringResource(R.string.menu_favourite),
                active = isFavorite,
                isFavoriteToggle = true,
                onClick = onToggleFavorite,
            ),
        )
        add(
            DetailAction(
                label = stringResource(R.string.menu_edit),
                onClick = onEdit,
            ),
        )
    }
    if (actionIdx >= actions.size) actionIdx = 0

    LaunchedEffect(game.id, isTopmost) {
        if (isTopmost) focusRequester.requestFocusSafely()
    }

    BoxWithConstraints(
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
                        if (pickingDisc) {
                            pickingDisc = false
                        } else {
                            onDismiss()
                        }
                        true
                    }
                    pickingDisc && event.key == Key.DirectionDown -> {
                        discIdx = (discIdx + 1) % game.discFiles.size
                        true
                    }
                    pickingDisc && event.key == Key.DirectionUp -> {
                        discIdx = (discIdx - 1 + game.discFiles.size) % game.discFiles.size
                        true
                    }
                    pickingDisc && GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        game.discFiles.getOrNull(discIdx)?.let { onLaunchDisc?.invoke(it) }
                        true
                    }
                    !pickingDisc && event.key == Key.DirectionDown -> {
                        if (focusZone != DetailFocusZone.Actions) {
                            focusZone = DetailFocusZone.Actions
                        } else {
                            scope.launch { scrollState.animateScrollTo((scrollState.value + 120).coerceAtMost(scrollState.maxValue)) }
                        }
                        true
                    }
                    !pickingDisc && event.key == Key.DirectionUp -> {
                        if (scrollState.value > 0 && focusZone == DetailFocusZone.Actions) {
                            scope.launch { scrollState.animateScrollTo((scrollState.value - 120).coerceAtLeast(0)) }
                        } else if (focusZone == DetailFocusZone.Actions) {
                            focusZone = if (actionIdx == 0 && onManualScrape != null) {
                                DetailFocusZone.CoverEdit
                            } else {
                                DetailFocusZone.Properties
                            }
                        }
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        pickingDisc = false
                        when (focusZone) {
                            DetailFocusZone.CoverEdit -> focusZone = DetailFocusZone.Properties
                            DetailFocusZone.Properties -> Unit
                            DetailFocusZone.Actions -> actionIdx = (actionIdx + 1) % actions.size
                        }
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        pickingDisc = false
                        when (focusZone) {
                            DetailFocusZone.Properties -> if (onManualScrape != null) {
                                focusZone = DetailFocusZone.CoverEdit
                            }
                            DetailFocusZone.CoverEdit -> Unit
                            DetailFocusZone.Actions -> actionIdx = (actionIdx - 1 + actions.size) % actions.size
                        }
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        when (focusZone) {
                            DetailFocusZone.Properties -> onProperties()
                            DetailFocusZone.CoverEdit -> onManualScrape?.invoke()
                            DetailFocusZone.Actions -> actions.getOrNull(actionIdx)?.onClick?.invoke()
                        }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val isNarrow = maxWidth < 460.dp
        val isShort = maxHeight < 340.dp
        val dialogMaxHeight = maxHeight * if (isShort) 0.92f else 0.88f
        DialogPanelCard(
            onDismiss = onDismiss,
            modifier = Modifier
                .padding(
                    horizontal = if (isNarrow) 16.dp else 28.dp,
                    vertical = when {
                        isShort -> 8.dp
                        isNarrow -> 12.dp
                        else -> 18.dp
                    },
                )
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .heightIn(max = dialogMaxHeight),
            contentPadding = PaddingValues(
                horizontal = if (isNarrow) 14.dp else 20.dp,
                vertical = when {
                    isShort -> 10.dp
                    isNarrow -> 14.dp
                    else -> 18.dp
                },
            ),
            topEndExtra = {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .then(
                            if (propertiesFocused) Modifier.brandBackground(CircleShape)
                            else Modifier.background(DeckColors.SurfaceAlt),
                        )
                        .brandBorder(
                            active = propertiesFocused,
                            shape = CircleShape,
                            activeWidth = 1.4.dp,
                            inactiveWidth = 0.6.dp,
                        )
                        .soundClickable(onClick = onProperties),
                    contentAlignment = Alignment.Center,
                ) {
                    DeckIcon(
                        icon = HomeIcons.Info,
                        size = 15.dp,
                        tint = if (propertiesFocused) Color.White else DeckColors.TextSecondary,
                    )
                }
            },
            swapAbxy = swapAbxy,
            keyBindings = keyBindings,
        ) {
            val metaChips = listOfNotNull(
                platform.fullName,
                metadata?.releaseDate,
                metadata?.publisher ?: metadata?.developer,
                metadata?.genre,
                formatCompactSize(game.fileSize),
                if (game.discFiles.size > 1) stringResource(R.string.detail_multi_disc_fmt, game.discFiles.size) else null,
            )
            val categoryLabel = if (categoryNames.isNotEmpty()) {
                categoryNames.joinToString(" · ")
            } else {
                stringResource(R.string.lib_badge_uncategorized)
            }

            val count = playStats?.playCount ?: 0
            val totalStr = formatPlayDuration(playStats?.totalPlayMs ?: 0L)
            val lastStr = playStats?.lastPlayedAt?.takeIf { it > 0L }?.let {
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it))
            } ?: stringResource(R.string.detail_never_played)
            val desc = metadata?.description?.takeIf { it.isNotBlank() }

            @Composable
            fun TagsRow() {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .clip(DeckShapes.tabPill)
                            .background(DeckColors.AccentSoft)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = categoryLabel,
                            style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                            color = DeckColors.Ruby,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!launcherLabel.isNullOrBlank()) {
                        Box(
                            Modifier
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.Surface)
                                .border(0.6.dp, DeckColors.Line, DeckShapes.tabPill)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = launcherLabel,
                                style = DeckType.metaLabel.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                                color = DeckColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            @Composable
            fun StatsAndBody() {
                if (pickingDisc && hasMultiDisc) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        game.discFiles.forEachIndexed { idx, path ->
                            val focused = idx == discIdx
                            val shape = RoundedCornerShape(10.dp)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .then(if (focused) Modifier.brandBackground(shape) else Modifier.background(DeckColors.Surface))
                                    .border(0.6.dp, if (focused) Color.Transparent else DeckColors.Line, shape)
                                    .soundClickable {
                                        discIdx = idx
                                        onLaunchDisc.invoke(path)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "$discLabelFmt ${idx + 1} · ${File(path).name}",
                                    style = DeckType.settingsLabel.copy(fontWeight = FontWeight.Medium),
                                    color = if (focused) Color.White else DeckColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeckColors.Surface)
                            .border(0.6.dp, DeckColors.Line, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatCell(stringResource(R.string.detail_play_count), "$count")
                        StatCell(stringResource(R.string.detail_play_time), totalStr)
                        StatCell(stringResource(R.string.detail_last_played), lastStr)
                    }
                    if (desc != null) {
                        Text(
                            text = desc,
                            style = DeckType.metaLabel,
                            color = DeckColors.TextSecondary,
                            maxLines = if (isNarrow) 4 else 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            @Composable
            fun CoverWithScrapeBadge(
                coverW: androidx.compose.ui.unit.Dp,
                coverH: androidx.compose.ui.unit.Dp,
                compact: Boolean,
            ) {
                Box(Modifier.size(width = coverW, height = coverH)) {
                    GameCoverCard(
                        game = game,
                        platform = platform,
                        covers = covers,
                        officialName = officialName,
                        width = coverW,
                        height = coverH,
                        isFocused = coverEditFocused,
                    )
                    if (onManualScrape != null) {
                        val visualSize = if (compact) 22.dp else 26.dp
                        val iconSize = if (compact) 11.dp else 13.dp
                        // 外层 36dp 透明触摸热区，内部清晰白底红铅笔圆钮（获焦时亮品牌渐变）
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .soundClickable(onClick = onManualScrape)
                                .padding(end = 6.dp, bottom = 6.dp),
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            Box(
                                Modifier
                                    .size(if (coverEditFocused) visualSize + 3.dp else visualSize)
                                    .clip(CircleShape)
                                    .then(
                                        if (coverEditFocused) Modifier.brandBackground(CircleShape)
                                        else Modifier.background(DeckColors.DialogBg.copy(alpha = 0.94f)),
                                    )
                                    .border(
                                        width = if (coverEditFocused) 1.5.dp else 0.8.dp,
                                        color = if (coverEditFocused) Color.White else DeckColors.Line,
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                DeckIcon(
                                    icon = HomeIcons.Pencil,
                                    size = if (coverEditFocused) iconSize + 1.dp else iconSize,
                                    tint = if (coverEditFocused) Color.White else DeckColors.Ruby,
                                )
                            }
                        }
                    }
                }
            }

            if (isNarrow) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 62.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val coverH = if (isShort) 72.dp else 88.dp
                        val coverW = (coverH * platform.coverAspect).coerceIn(54.dp, if (isShort) 80.dp else 96.dp)
                        CoverWithScrapeBadge(coverW = coverW, coverH = coverH, compact = true)
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = game.title,
                                style = DeckType.menuTitle.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                                color = DeckColors.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (metaChips.isNotEmpty()) {
                                Text(
                                    text = metaChips.joinToString(" · "),
                                    style = DeckType.metaLabel,
                                    color = DeckColors.TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TagsRow()
                        }
                    }
                    StatsAndBody()
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    val coverH = 168.dp
                    val coverW = (coverH * platform.coverAspect).coerceIn(104.dp, 164.dp)
                    CoverWithScrapeBadge(coverW = coverW, coverH = coverH, compact = false)

                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(
                            text = game.title,
                            style = DeckType.menuTitle.copy(fontWeight = FontWeight.Bold),
                            color = DeckColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 64.dp),
                        )
                        if (metaChips.isNotEmpty()) {
                            Text(
                                text = metaChips.joinToString(" · "),
                                style = DeckType.settingsLabel,
                                color = DeckColors.TextSecondary,
                            )
                        }
                        TagsRow()
                        StatsAndBody()
                    }
                }
            }

            Spacer(Modifier.height(when { isShort -> 6.dp; isNarrow -> 10.dp; else -> 14.dp }))
            HorizontalDivider(color = DeckColors.Line)
            Spacer(Modifier.height(when { isShort -> 6.dp; isNarrow -> 10.dp; else -> 12.dp }))

            @Composable
            fun ActionPill(act: DetailAction, idx: Int, modifier: Modifier) {
                val hl = !pickingDisc && focusZone == DetailFocusZone.Actions && idx == actionIdx
                val bgMod = when {
                    act.primary && hl -> Modifier.brandBackground(DeckShapes.tabPill)
                    act.active && hl -> Modifier.background(DeckColors.Ruby.copy(alpha = 0.22f))
                    hl -> Modifier.background(DeckColors.AccentSoft)
                    act.primary -> Modifier.background(DeckColors.Ruby.copy(alpha = 0.14f))
                    act.active -> Modifier.background(DeckColors.AccentSoft)
                    else -> Modifier.background(DeckColors.Surface)
                }
                val fgColor = when {
                    act.primary && hl -> Color.White
                    hl || act.primary || act.active -> DeckColors.Ruby
                    else -> DeckColors.TextPrimary
                }
                val borderMod = if (act.active && !hl) {
                    Modifier.border(1.dp, DeckColors.Ruby.copy(alpha = 0.55f), DeckShapes.tabPill)
                } else {
                    Modifier.brandBorder(active = hl, shape = DeckShapes.tabPill, activeWidth = 1.4.dp, inactiveWidth = 0.6.dp)
                }
                Box(
                    modifier
                        .clip(DeckShapes.tabPill)
                        .then(bgMod)
                        .then(borderMod)
                        .soundClickable {
                            actionIdx = idx
                            act.onClick()
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (!act.shortcutBadge.isNullOrBlank()) {
                            val badgeBg = if (act.primary && hl) {
                                Color.White.copy(alpha = 0.24f)
                            } else {
                                fgColor.copy(alpha = 0.14f)
                            }
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(badgeBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = act.shortcutBadge,
                                    style = DeckType.metaLabel.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp,
                                        lineHeight = 10.sp,
                                    ),
                                    color = fgColor,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (act.isFavoriteToggle) {
                            FavoriteHeartIcon(
                                filled = act.active,
                                color = fgColor,
                                size = 13.dp,
                                strokeWidth = 1.6.dp,
                            )
                        }
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

            if (isNarrow && actions.size > 3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionPill(actions[0], 0, Modifier.fillMaxWidth())
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (i in 1 until actions.size) {
                            ActionPill(actions[i], i, Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEachIndexed { idx, act ->
                        ActionPill(act, idx, Modifier.weight(if (act.primary) 1.35f else 1f))
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
            overflow = TextOverflow.Ellipsis,
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
