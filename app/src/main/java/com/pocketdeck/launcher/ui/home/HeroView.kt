package com.pocketdeck.launcher.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.MetadataDisplayConfig
import com.pocketdeck.launcher.data.PlayStats
import com.pocketdeck.launcher.data.formatFileSize
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.ui.components.GameCoverCard
import com.pocketdeck.launcher.ui.components.Text
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import com.pocketdeck.launcher.ui.theme.cardSurface

/**
 * Hero（沉浸海报流 + 底部胶片条）视图（PS5 / Steam Big Picture 风格）：
 * - 上方 62%：焦点游戏高清大封面 + 标题、平台胶囊、元数据、游玩统计与渐变主按钮
 * - 下方 38%：基于 LazyRow 严格虚拟化的横向缩略图胶片条，选中项通过 graphicsLayer 零重排微抬
 */
@Composable
fun HeroView(
    games: List<Game>,
    platformOf: (Game) -> Platform?,
    focusIndex: Int,
    covers: CoverSource?,
    metadataMap: Map<String, GameMetadata>,
    officialNames: Map<String, String>,
    favorites: Set<String> = emptySet(),
    playStats: Map<String, PlayStats> = emptyMap(),
    onFocusChange: (Int) -> Unit,
    onLaunch: (Game) -> Unit,
    onDirectLaunch: (Game) -> Unit = onLaunch,
    onLongPress: ((Game) -> Unit)? = null,
    fastScrolling: Boolean = false,
    showMetadata: Boolean = true,
    metaDisplay: MetadataDisplayConfig = MetadataDisplayConfig(),
    modifier: Modifier = Modifier,
) {
    if (games.isEmpty()) {
        EmptyPlatformMessage(modifier)
        return
    }

    val safeFocus = focusIndex.coerceIn(0, games.lastIndex)
    val focusedGame = games[safeFocus]
    val focusedPlatform = platformOf(focusedGame)
    val focusedMeta = metadataMap[focusedGame.id]
    val focusedStats = playStats[focusedGame.id]
    val reduceMotion = LocalReduceMotion.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val palette = LocalDeckColors.current

    // 切分类时立刻对齐
    LaunchedEffect(games.firstOrNull()?.id, games.size) {
        listState.scrollToItem(safeFocus)
    }

    // 焦点变化时平滑居中滚动，连发快滚或减少动态效果时零动画直达
    LaunchedEffect(safeFocus, fastScrolling, reduceMotion) {
        val visible = listState.layoutInfo.visibleItemsInfo
        val viewportW = listState.layoutInfo.viewportSize.width
        val targetItem = visible.firstOrNull { it.index == safeFocus }
        val offsetPx = if (viewportW > 0) {
            val itemW = targetItem?.size ?: with(density) { 86.dp.roundToPx() }
            -((viewportW - itemW) / 2).coerceAtLeast(0)
        } else 0
        if (fastScrolling || reduceMotion) {
            listState.scrollToItem(safeFocus, offsetPx)
        } else {
            listState.animateScrollToItem(safeFocus, offsetPx)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // 上半屏：沉浸焦点海报区
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.62f)
                .padding(horizontal = 36.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val heroCoverH = (maxHeight * 0.90f).coerceIn(130.dp, 224.dp)
            val heroAspect = (focusedPlatform?.coverAspect ?: 0.82f).coerceIn(0.65f, 1.35f)
            val heroCoverW = (heroCoverH * heroAspect).coerceAtMost(maxWidth * 0.36f)

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                GameCoverCard(
                    game = focusedGame,
                    platform = focusedPlatform,
                    covers = covers,
                    officialName = officialNames[focusedGame.id],
                    width = heroCoverW,
                    height = heroCoverH,
                    isFocused = true,
                    isFavorite = focusedGame.id in favorites,
                    fastScrolling = fastScrolling,
                    allowDiskDuringFastScroll = true,
                    prominentFocus = false,
                    onClick = { onLaunch(focusedGame) },
                    onLongPress = onLongPress?.let { cb -> { cb(focusedGame) } },
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 平台胶囊 + 计数
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        focusedPlatform?.shortName?.takeIf { it.isNotBlank() }?.let { shortName ->
                            Box(
                                modifier = Modifier
                                    .cardSurface(shape = CircleShape, elevation = 2.dp)
                                    .border(0.75.dp, DeckColors.Line, CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = shortName,
                                    style = DeckType.metaLabel.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = DeckColors.TextPrimary,
                                )
                            }
                        }
                        Text(
                            text = "${safeFocus + 1} / ${games.size}",
                            style = DeckType.metaLabel.copy(fontSize = 12.sp),
                            color = DeckColors.TextSecondary,
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // 大标题
                    Text(
                        text = focusedGame.title,
                        style = DeckType.gameTitle.copy(fontSize = 23.sp, lineHeight = 28.sp),
                        color = DeckColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // 副标题（年份 · 厂商 · 类型）
                    val subParts = buildList {
                        if (showMetadata) {
                            if (metaDisplay.releaseDate) focusedMeta?.releaseDate?.takeIf { it.isNotBlank() }?.take(4)?.let { add(it) }
                            if (metaDisplay.publisher) {
                                focusedMeta?.publisher?.takeIf { it.isNotBlank() }?.let { add(it) }
                            } else if (metaDisplay.developer) {
                                focusedMeta?.developer?.takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                            if (metaDisplay.genre) focusedMeta?.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
                        } else {
                            focusedPlatform?.fullName?.takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                    if (subParts.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subParts.joinToString(" · "),
                            style = DeckType.metaLabel.copy(fontSize = 13.sp),
                            color = DeckColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 统计 / 补充信息胶囊行
                    val statPills = buildList {
                        if (focusedStats != null && focusedStats.playCount > 0) {
                            add(stringResource(R.string.hero_play_count, focusedStats.playCount))
                        }
                        if (focusedStats != null && focusedStats.totalPlayMs >= 60_000L) {
                            val totalMins = focusedStats.totalPlayMs / 60_000L
                            if (totalMins >= 60L) {
                                add(stringResource(R.string.hero_play_hours, totalMins / 60f))
                            } else {
                                add(stringResource(R.string.hero_play_mins, totalMins.toInt()))
                            }
                        }
                        if (isEmpty() && showMetadata) {
                            if (metaDisplay.developer) focusedMeta?.developer?.takeIf { it.isNotBlank() }?.let { add(it) }
                            if (metaDisplay.releaseDate) focusedMeta?.releaseDate?.takeIf { it.isNotBlank() }?.let { add(it) }
                            if (metaDisplay.fileSize && focusedGame.fileSize > 0L) add(formatFileSize(focusedGame.fileSize))
                        }
                    }.take(3)

                    if (statPills.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            statPills.forEach { label ->
                                Box(
                                    modifier = Modifier
                                        .cardSurface(shape = CircleShape, elevation = 2.dp)
                                        .border(0.75.dp, DeckColors.Line, CircleShape)
                                        .padding(horizontal = 11.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = label,
                                        style = DeckType.metaLabel.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                        color = DeckColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 170.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // 品牌渐变主启动按钮
                    val ctaLabel = if ((focusedStats?.playCount ?: 0) > 0) {
                        stringResource(R.string.hero_continue_cta)
                    } else {
                        stringResource(R.string.hero_play_cta)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(DeckBrand.brandBrush, CircleShape)
                            .soundClickable { onDirectLaunch(focusedGame) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "▶  $ctaLabel",
                            style = DeckType.menuButton.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // 下半屏：横向胶片条底座（LazyRow 严格虚拟化）
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.38f)
                .background(palette.surface.copy(alpha = if (palette.isDark) 0.28f else 0.36f))
                .padding(top = 6.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            val thumbH = (maxHeight * 0.78f).coerceIn(72.dp, 114.dp)

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = games,
                    key = { _, g -> g.id },
                ) { idx, game ->
                    val isSelected = idx == safeFocus
                    val plat = platformOf(game)
                    val aspect = (plat?.coverAspect ?: 0.85f).coerceIn(0.68f, 1.30f)
                    val thumbW = (thumbH * aspect).coerceIn(58.dp, 124.dp)

                    val focusProgress = animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0f,
                        animationSpec = if (reduceMotion || fastScrolling) snap() else spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 620f,
                        ),
                        label = "heroThumbFocus",
                    )

                    Box(
                        modifier = Modifier.graphicsLayer {
                            // 仅在绘制阶段读取动画值，切换焦点期间零重组
                            val p = focusProgress.value
                            val s = 0.96f + 0.11f * p
                            scaleX = s
                            scaleY = s
                            translationY = (-4f * p) * density.density
                            alpha = 0.84f + 0.16f * p
                        },
                    ) {
                        GameCoverCard(
                            game = game,
                            platform = plat,
                            covers = covers,
                            officialName = officialNames[game.id],
                            width = thumbW,
                            height = thumbH,
                            isFocused = isSelected,
                            isFavorite = game.id in favorites,
                            fastScrolling = fastScrolling,
                            shapeOverride = RoundedCornerShape(10.dp),
                            prominentFocus = isSelected,
                            onClick = {
                                if (isSelected) onLaunch(game) else onFocusChange(idx)
                            },
                            onLongPress = onLongPress?.let { cb -> { cb(game) } },
                        )
                    }
                }
            }
        }
    }
}
