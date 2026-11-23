package com.pocketdeck.launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.PlayStats
import com.pocketdeck.launcher.data.formatCompactSize
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.ui.components.icons.HomeIcons
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandBackground
import com.pocketdeck.launcher.ui.theme.brandFocusRing
import com.pocketdeck.launcher.ui.theme.cardSurface
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
    val isEdit: Boolean = false,
    val onClick: () -> Unit,
)

private enum class DetailFocusZone {
    Actions,
    CoverEdit,
    Properties,
}

/**
 * 桌面游戏详情与操作中心（全高海报分栏 · 经典均衡风）：
 * - 左侧全高无边距海报封面 + 右缘无缝羽化渐变 + 右下磨砂「图库图标 + 换封面」胶囊
 * - 右侧标题行（仅保留右上角关闭钮）+ 元数据行 + 标签行（分类/平台胶囊 + 模拟器胶囊 +「ⓘ 属性」胶囊）
 *   + 单框 3 列游玩统计卡 + 简介文本 + 水平分割线 + 底部 3 胶囊操作栏（▶ 开始游戏 / ♥ 收藏 / 编辑）
 * - 支持横屏左右全高分栏与极窄屏（<460dp）上下海报自适应，完整支持手柄方向键聚焦。
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
                    label = if (pickingDisc) stringResource(R.string.detail_action_back_info)
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
                isEdit = true,
                onClick = onEdit,
            ),
        )
    }
    if (actionIdx >= actions.size) actionIdx = 0

    BackHandler(enabled = isTopmost && pickingDisc) {
        pickingDisc = false
    }

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
                val isInitialPress = event.nativeKeyEvent.repeatCount == 0
                when {
                    GamepadKeys.isBack(event.key) || GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) -> {
                        if (isInitialPress) {
                            if (pickingDisc) {
                                pickingDisc = false
                            } else {
                                onDismiss()
                            }
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
                        if (isInitialPress) {
                            game.discFiles.getOrNull(discIdx)?.let { onLaunchDisc?.invoke(it) }
                        }
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
                        if (isInitialPress) {
                            when (focusZone) {
                                DetailFocusZone.Properties -> onProperties()
                                DetailFocusZone.CoverEdit -> onManualScrape?.invoke()
                                DetailFocusZone.Actions -> actions.getOrNull(actionIdx)?.onClick?.invoke()
                            }
                        }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val isNarrow = maxWidth < 460.dp
        val isCompact = maxWidth < 720.dp
        val isShort = maxHeight < 340.dp
        val outerHPad = if (isNarrow) 16.dp else 24.dp
        val outerVPad = when {
            isShort -> 10.dp
            isNarrow -> 12.dp
            else -> 16.dp
        }
        val dialogBg = DeckColors.DialogBg
        val dialogLine = DeckColors.Line
        val isDark = DeckColors.isDark
        val dialogShape = RoundedCornerShape(DeckBrand.RadiusXL)

        val metaChips = listOfNotNull(
            platform.fullName,
            metadata?.releaseDate,
            metadata?.publisher ?: metadata?.developer,
            metadata?.genre,
            formatCompactSize(game.fileSize),
            if (game.discFiles.size > 1) stringResource(R.string.detail_multi_disc_fmt, game.discFiles.size) else null,
        )
        val primaryTagLabel = if (categoryNames.isNotEmpty()) {
            categoryNames.joinToString(" · ")
        } else {
            platform.shortName
        }

        val count = playStats?.playCount ?: 0
        val totalStr = formatPlayDuration(playStats?.totalPlayMs ?: 0L)
        val lastStr = playStats?.lastPlayedAt?.takeIf { it > 0L }?.let {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it))
        } ?: stringResource(R.string.detail_never_played)
        val desc = metadata?.description?.takeIf { it.isNotBlank() }

        @Composable
        fun CoverChangePill(modifier: Modifier) {
            if (onManualScrape == null) return
            val pillBg = if (isDark) DeckColors.Background.copy(alpha = 0.78f) else DeckColors.Surface.copy(alpha = 0.88f)
            val pillBorder = when {
                coverEditFocused -> Color.White.copy(alpha = 0.62f)
                isDark -> Color.White.copy(alpha = 0.22f)
                else -> DeckColors.TextPrimary.copy(alpha = 0.16f)
            }
            val pillFg = when {
                coverEditFocused -> Color.White
                isDark -> Color.White.copy(alpha = 0.94f)
                else -> DeckColors.TextPrimary
            }
            Box(
                modifier
                    .graphicsLayer {
                        val s = if (coverEditFocused) 1.03f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .brandFocusRing(
                        focused = coverEditFocused,
                        cornerRadius = 14.dp,
                        width = 1.8.dp,
                        gap = 2.dp,
                        glow = true,
                    )
                    .height(28.dp)
                    .clip(CircleShape)
                    .then(
                        if (coverEditFocused) Modifier.brandBackground(CircleShape)
                        else Modifier.background(pillBg),
                    )
                    .border(1.dp, pillBorder, CircleShape)
                    .soundClickable {
                        focusZone = DetailFocusZone.CoverEdit
                        onManualScrape()
                    }
                    .padding(start = 8.dp, end = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    DeckIcon(
                        icon = HomeIcons.CoverGallery,
                        size = 13.dp,
                        tint = pillFg,
                    )
                    Text(
                        text = stringResource(R.string.detail_change_cover),
                        style = DeckType.metaLabel.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.1.sp,
                        ),
                        color = pillFg,
                        maxLines = 1,
                    )
                }
            }
        }

        @Composable
        fun TagsRow() {
            val dashedBorderColor = DeckColors.Line
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // 平台 / 分类主题强调胶囊
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(DeckColors.AccentSoft)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = primaryTagLabel,
                        style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                        color = DeckColors.Ruby,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 模拟器 / 核心胶囊
                if (!launcherLabel.isNullOrBlank()) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(DeckColors.Surface)
                            .border(0.8.dp, DeckColors.Line, CircleShape)
                            .padding(horizontal = 9.dp, vertical = 3.dp),
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
                // 标签行末尾「ⓘ 属性」胶囊（释放右上角标题空间）
                val propFg = if (propertiesFocused) DeckColors.Ruby else DeckColors.TextSecondary
                Box(
                    Modifier
                        .brandFocusRing(
                            focused = propertiesFocused,
                            cornerRadius = 12.dp,
                            width = 1.6.dp,
                            gap = 1.5.dp,
                            glow = false,
                        )
                        .clip(CircleShape)
                        .background(if (propertiesFocused) DeckColors.AccentSoft else Color.Transparent)
                        .then(
                            if (propertiesFocused) {
                                Modifier
                            } else {
                                Modifier.drawBehind {
                                    val strokePx = 1.dp.toPx()
                                    val r = size.height / 2f
                                    drawRoundRect(
                                        color = dashedBorderColor,
                                        cornerRadius = CornerRadius(r, r),
                                        style = Stroke(
                                            width = strokePx,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f),
                                        ),
                                    )
                                }
                            },
                        )
                        .soundClickable {
                            focusZone = DetailFocusZone.Properties
                            onProperties()
                        }
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DeckIcon(
                            icon = HomeIcons.Info,
                            size = 12.dp,
                            tint = propFg,
                        )
                        Text(
                            text = stringResource(R.string.menu_properties),
                            style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                            color = propFg,
                            maxLines = 1,
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
                                .border(0.8.dp, if (focused) Color.Transparent else DeckColors.Line, shape)
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
                // 单框 3 列统计卡（游玩次数 / 累计时长 / 上次游玩）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeckColors.Surface)
                        .border(0.8.dp, DeckColors.Line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCell(stringResource(R.string.detail_play_count), "$count", Modifier.weight(1f))
                    StatCell(stringResource(R.string.detail_play_time), totalStr, Modifier.weight(1f))
                    StatCell(stringResource(R.string.detail_last_played), lastStr, Modifier.weight(1f))
                }
                if (desc != null) {
                    Text(
                        text = desc,
                        style = DeckType.metaLabel.copy(
                            fontSize = 11.5.sp,
                            lineHeight = 17.5.sp,
                        ),
                        color = DeckColors.TextSecondary,
                        maxLines = if (isCompact) 3 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        @Composable
        fun ActionPill(act: DetailAction, idx: Int, modifier: Modifier) {
            val hl = !pickingDisc && focusZone == DetailFocusZone.Actions && idx == actionIdx
            val btnHeight = if (isShort) 34.dp else 38.dp
            val bgMod = when {
                act.primary -> Modifier.brandBackground(CircleShape)
                act.active && hl -> Modifier.background(DeckColors.Ruby.copy(alpha = 0.22f))
                act.active -> Modifier.background(DeckColors.AccentSoft)
                hl -> Modifier.background(DeckColors.AccentSoft)
                else -> Modifier.background(DeckColors.Surface)
            }
            val fgColor = when {
                act.primary -> Color.White
                act.active || hl -> DeckColors.Ruby
                else -> DeckColors.TextPrimary
            }
            val borderMod = when {
                act.primary || hl -> Modifier
                act.active -> Modifier.border(1.dp, DeckColors.Ruby.copy(alpha = 0.55f), CircleShape)
                else -> Modifier.border(0.8.dp, DeckColors.Line, CircleShape)
            }
            Box(
                modifier
                    .brandFocusRing(
                        focused = hl,
                        cornerRadius = btnHeight / 2,
                        width = 2.dp,
                        gap = 2.dp,
                        glow = act.primary,
                    )
                    .height(btnHeight)
                    .clip(CircleShape)
                    .then(bgMod)
                    .then(borderMod)
                    .soundClickable {
                        focusZone = DetailFocusZone.Actions
                        actionIdx = idx
                        act.onClick()
                    }
                    .padding(horizontal = if (isCompact) 6.dp else 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 5.dp),
                ) {
                    if (act.primary) {
                        DeckIcon(
                            icon = HomeIcons.Play,
                            size = if (isCompact) 10.5.dp else 12.dp,
                            tint = Color.White,
                        )
                    } else if (act.isFavoriteToggle) {
                        FavoriteHeartIcon(
                            filled = act.active,
                            color = fgColor,
                            size = if (isCompact) 11.dp else 12.5.dp,
                            strokeWidth = 1.6.dp,
                        )
                    }
                    Text(
                        text = act.label,
                        style = DeckType.settingsLabel.copy(
                            fontSize = if (isCompact) 11.5.sp else 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = fgColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        @Composable
        fun ActionsBar() {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(if (isShort) 6.dp else 8.dp))
                HorizontalDivider(thickness = 0.8.dp, color = DeckColors.Line)
                Spacer(Modifier.height(if (isShort) 8.dp else 10.dp))
                val longFavLabel = actions.any { it.isFavoriteToggle && it.label.length > 6 }
                if ((isNarrow || isCompact) && actions.size > 3) {
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
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actions.forEachIndexed { idx, act ->
                            val w = when {
                                actions.size > 3 -> when {
                                    act.primary -> 1.28f
                                    act.isEdit -> 0.80f
                                    act.isFavoriteToggle && longFavLabel -> 1.24f
                                    else -> 1f
                                }
                                isCompact && longFavLabel -> when {
                                    act.primary -> 1.05f
                                    act.isFavoriteToggle -> 1.42f
                                    act.isEdit -> 0.75f
                                    else -> 1f
                                }
                                act.primary -> if (isCompact) 1.38f else 1.55f
                                act.isEdit -> if (isCompact) 0.78f else 0.85f
                                else -> if (isCompact) 1.16f else 1f
                            }
                            ActionPill(act, idx, Modifier.weight(w))
                        }
                    }
                }
            }
        }

        @Composable
        fun RightInfoContent(modifier: Modifier, includeTitleClose: Boolean) {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = !isNarrow)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 7.dp else 9.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = game.title,
                            style = DeckType.menuTitle.copy(
                                fontSize = if (isCompact) 19.sp else 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.3).sp,
                                lineHeight = if (isCompact) 23.sp else 26.sp,
                            ),
                            color = DeckColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (includeTitleClose) {
                            DialogCloseButton(
                                onClick = onDismiss,
                                size = 30.dp,
                                swapAbxy = swapAbxy,
                                keyBindings = keyBindings,
                            )
                        }
                    }
                    if (metaChips.isNotEmpty()) {
                        Text(
                            text = metaChips.joinToString(" · "),
                            style = DeckType.metaLabel.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            ),
                            color = DeckColors.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TagsRow()
                    StatsAndBody()
                }
                ActionsBar()
            }
        }

        if (isNarrow) {
            // 窄屏（<460dp）：顶部全宽海报 + 底部无缝羽化 + 右下磨砂「换封面」胶囊
            val dialogMaxH = (maxHeight * if (isShort) 0.90f else 0.88f)
            val bannerH: Dp = if (isShort) 112.dp else 148.dp
            Box(
                Modifier
                    .padding(horizontal = outerHPad, vertical = outerVPad)
                    .widthIn(max = DeckDialogSize.WidthM)
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxH)
                    .cardSurface(dialogShape, elevation = 12.dp)
                    .clip(dialogShape)
                    .background(dialogBg)
                    .border(1.dp, dialogLine, dialogShape)
                    .clickable(enabled = false, onClick = {}),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .height(bannerH)
                            .background(if (isDark) DeckColors.Background else DeckColors.SurfaceAlt),
                    ) {
                        GameCoverCard(
                            game = game,
                            platform = platform,
                            covers = covers,
                            officialName = officialName,
                            width = maxWidth,
                            height = maxHeight,
                            isFocused = false,
                            shapeOverride = RectangleShape,
                            showPlatformBadge = false,
                            drawShadow = false,
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.00f to Color.Black.copy(alpha = 0.24f),
                                        0.30f to Color.Transparent,
                                        0.68f to Color.Transparent,
                                        1.00f to dialogBg,
                                    ),
                                ),
                        )
                        DialogCloseButton(
                            onClick = onDismiss,
                            size = 30.dp,
                            darkSurface = true,
                            swapAbxy = swapAbxy,
                            keyBindings = keyBindings,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 12.dp, end = 12.dp),
                        )
                        CoverChangePill(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 12.dp, end = 14.dp),
                        )
                    }
                    RightInfoContent(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        includeTitleClose = false,
                    )
                }
            }
        } else {
            // 横屏标准：全高海报分栏（左侧无边距封面 + 右缘羽化 + 右下磨砂「换封面」胶囊，右侧经典均衡排版）
            val availW = (maxWidth - outerHPad * 2).coerceAtMost(DeckDialogSize.WidthXL)
            val maxTargetH = if (desc == null && !pickingDisc) 284.dp else 340.dp
            val dialogH = (maxHeight * if (isShort) 0.90f else 0.86f).coerceAtMost(maxTargetH)
            val artW = (availW * 0.40f).coerceIn(180.dp, 260.dp)

            Box(
                Modifier
                    .padding(horizontal = outerHPad, vertical = outerVPad)
                    .width(availW)
                    .height(dialogH)
                    .cardSurface(dialogShape, elevation = 12.dp)
                    .clip(dialogShape)
                    .background(dialogBg)
                    .border(1.dp, dialogLine, dialogShape)
                    .clickable(enabled = false, onClick = {}),
            ) {
                Row(Modifier.fillMaxSize()) {
                    // 左侧全高封面海报区（无重复平台角标，右缘无缝羽化进弹窗底色）
                    BoxWithConstraints(
                        Modifier
                            .width(artW)
                            .fillMaxHeight()
                            .background(if (isDark) DeckColors.Background else DeckColors.SurfaceAlt),
                    ) {
                        GameCoverCard(
                            game = game,
                            platform = platform,
                            covers = covers,
                            officialName = officialName,
                            width = maxWidth,
                            height = maxHeight,
                            isFocused = false,
                            shapeOverride = RectangleShape,
                            showPlatformBadge = false,
                            drawShadow = false,
                        )
                        // 上下轻暗角（保障底部胶囊对比度）
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.00f to Color.Black.copy(alpha = 0.22f),
                                        0.24f to Color.Transparent,
                                        0.72f to Color.Transparent,
                                        1.00f to Color.Black.copy(alpha = 0.52f),
                                    ),
                                ),
                        )
                        // 右侧边缘无缝羽化进弹窗背景（70% -> 100%）
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0.00f to Color.Transparent,
                                        0.70f to Color.Transparent,
                                        1.00f to dialogBg,
                                    ),
                                ),
                        )
                        // 右下角磨砂「换封面」胶囊（紧贴羽化带左侧，不贴边）
                        CoverChangePill(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    bottom = 14.dp,
                                    end = if (isCompact) 18.dp else 24.dp,
                                ),
                        )
                    }

                    // 右侧经典均衡信息 + 操作区
                    RightInfoContent(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(
                                start = if (isCompact) 12.dp else 14.dp,
                                end = if (isCompact) 18.dp else 22.dp,
                                top = if (isCompact) 16.dp else 20.dp,
                                bottom = if (isCompact) 14.dp else 18.dp,
                            ),
                        includeTitleClose = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = DeckType.metaLabel.copy(fontSize = 10.5.sp),
            color = DeckColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = DeckType.settingsLabel.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
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
