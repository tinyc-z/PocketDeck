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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.MetadataDisplayConfig
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.ui.components.GameCoverCard
import com.pocketdeck.launcher.ui.components.Text
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import com.pocketdeck.launcher.ui.theme.cardSurface

const val SHELF_COLUMNS = 5

/**
 * Shelf（实体收藏架）视图：
 * - 基于 LazyColumn 按层（每层 5 盒）严格虚拟化，大库也仅组合屏幕可见 2~3 层
 * - 盒装封面底边对齐立于半透明磨砂陈列层板上，带柔和接触阴影与层板立面光泽
 * - 焦点盒通过 graphicsLayer 零重排微微抽出放大，并在该层板下方浮出聚焦游戏信息胶囊
 */
@Composable
fun ShelfView(
    games: List<Game>,
    platformOf: (Game) -> Platform?,
    focusIndex: Int,
    covers: CoverSource?,
    metadataMap: Map<String, GameMetadata>,
    officialNames: Map<String, String>,
    favorites: Set<String> = emptySet(),
    onFocusChange: (Int) -> Unit,
    onLaunch: (Game) -> Unit,
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
    val focusedRow = safeFocus / SHELF_COLUMNS
    val rowCount = remember(games.size) { (games.size + SHELF_COLUMNS - 1) / SHELF_COLUMNS }
    val listState = rememberLazyListState()
    val reduceMotion = LocalReduceMotion.current
    val palette = LocalDeckColors.current
    val density = LocalDensity.current

    // 切分类时立刻对齐
    LaunchedEffect(games.firstOrNull()?.id, games.size) {
        listState.scrollToItem(focusedRow)
    }

    // 焦点切层时平滑滚动到可见区
    LaunchedEffect(focusedRow, fastScrolling, reduceMotion) {
        val visible = listState.layoutInfo.visibleItemsInfo
        val alreadyComfortablyVisible = visible.any {
            it.index == focusedRow && it.offset >= 0 &&
                it.offset + it.size <= listState.layoutInfo.viewportEndOffset
        }
        if (!alreadyComfortablyVisible) {
            if (fastScrolling || reduceMotion) {
                listState.scrollToItem(focusedRow)
            } else {
                listState.animateScrollToItem(focusedRow)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        val horizontalGap = 14.dp
        val shelfInnerPad = 14.dp
        val availW = (maxWidth - shelfInnerPad * 2 - horizontalGap * (SHELF_COLUMNS - 1)).coerceAtLeast(200.dp)
        val slotW = availW / SHELF_COLUMNS
        val boxH = (slotW * 1.08f).coerceIn(82.dp, 134.dp)
        val isTouchScrolling by remember(listState) { derivedStateOf { listState.isScrollInProgress } }
        val effectiveFastScroll = fastScrolling || isTouchScrolling

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = rowCount,
                key = { r -> games[r * SHELF_COLUMNS].id },
            ) { rowIdx ->
                val startIdx = rowIdx * SHELF_COLUMNS
                val endIdx = (startIdx + SHELF_COLUMNS).coerceAtMost(games.size)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 该层 5 个盒装游戏（底边齐平立于层板上）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = shelfInnerPad),
                        horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        for (col in 0 until SHELF_COLUMNS) {
                            val idx = startIdx + col
                            if (idx < endIdx) {
                                val game = games[idx]
                                val plat = platformOf(game)
                                val isSelected = idx == safeFocus
                                val aspect = (plat?.coverAspect ?: 0.88f).coerceIn(0.68f, 1.25f)
                                val cardW = (boxH * aspect).coerceAtMost(slotW)
                                val cardH = (cardW / aspect).coerceAtMost(boxH)

                                val pullProgress = animateFloatAsState(
                                    targetValue = if (isSelected) 1f else 0f,
                                    animationSpec = if (reduceMotion || fastScrolling) snap() else spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = 620f,
                                    ),
                                    label = "shelfPull",
                                )

                                Box(
                                    modifier = Modifier
                                        .width(slotW)
                                        .height(boxH)
                                        .zIndex(if (isSelected) 10f else 1f),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    Box(
                                        modifier = Modifier.graphicsLayer {
                                            // 严格在绘制阶段读取动画值，零重组、零重排
                                            val p = pullProgress.value
                                            val s = 1f + 0.09f * p
                                            scaleX = s
                                            scaleY = s
                                            translationY = (-5f * p) * density.density
                                        },
                                    ) {
                                        GameCoverCard(
                                            game = game,
                                            platform = plat,
                                            covers = covers,
                                            officialName = officialNames[game.id],
                                            width = cardW,
                                            height = cardH,
                                            isFocused = isSelected,
                                            isFavorite = game.id in favorites,
                                            fastScrolling = effectiveFastScroll,
                                            shapeOverride = RoundedCornerShape(9.dp),
                                            prominentFocus = isSelected && !effectiveFastScroll,
                                            onClick = {
                                                if (isSelected) onLaunch(game) else onFocusChange(idx)
                                            },
                                            onLongPress = onLongPress?.let { cb -> { cb(game) } },
                                        )
                                    }
                                }
                            } else {
                                Spacer(Modifier.width(slotW))
                            }
                        }
                    }

                    // 磨砂玻璃陈列层板 + 柔和底部投影（快滚期跳过层板投影，使用 drawWithCache 避免每帧重建渐变）
                    val shelfShape = RoundedCornerShape(6.dp)
                    val drawShelfShadow = !effectiveFastScroll && !reduceMotion
                    val shelfShadowColor = Color.Black.copy(alpha = if (palette.isDark) 0.34f else 0.11f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(11.dp)
                            .then(
                                if (drawShelfShadow) {
                                    Modifier.drawWithCache {
                                        val brush = Brush.verticalGradient(
                                            listOf(shelfShadowColor, Color.Transparent),
                                            startY = size.height * 0.55f,
                                            endY = size.height + 10.dp.toPx(),
                                        )
                                        val tl = Offset(8.dp.toPx(), size.height * 0.55f)
                                        val sz = Size(size.width - 16.dp.toPx(), 12.dp.toPx())
                                        val cr = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                        onDrawBehind {
                                            drawRoundRect(brush = brush, topLeft = tl, size = sz, cornerRadius = cr)
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .clip(shelfShape)
                            .background(
                                Brush.verticalGradient(
                                    if (palette.isDark) {
                                        listOf(
                                            Color.White.copy(alpha = 0.22f),
                                            Color.White.copy(alpha = 0.09f),
                                        )
                                    } else {
                                        listOf(
                                            Color.White.copy(alpha = 0.92f),
                                            Color.White.copy(alpha = 0.62f),
                                        )
                                    },
                                ),
                                shelfShape,
                            )
                            .border(
                                0.75.dp,
                                if (palette.isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.85f),
                                shelfShape,
                            ),
                    )
                }
            }
        }

        // 悬浮聚焦信息胶囊（独立于 LazyColumn 行外，保证每层架子高度恒定，上下切层零重排跳动）
        val focusedGame = games[safeFocus]
        val focusedPlat = platformOf(focusedGame)
        val focusedMeta = metadataMap[focusedGame.id]
        val pillText = remember(focusedGame.id, focusedGame.title, focusedPlat?.shortName, focusedMeta, showMetadata, metaDisplay, safeFocus, games.size) {
            buildList {
                add(focusedGame.title)
                focusedPlat?.shortName?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (showMetadata && metaDisplay.releaseDate) {
                    focusedMeta?.releaseDate?.takeIf { it.isNotBlank() }?.take(4)?.let { add(it) }
                }
                add("${safeFocus + 1}/${games.size}")
            }.joinToString(" · ")
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .cardSurface(shape = CircleShape, elevation = 6.dp)
                .border(0.8.dp, DeckColors.Line, CircleShape)
                .padding(horizontal = 16.dp, vertical = 5.dp),
        ) {
            Text(
                text = pillText,
                style = DeckType.metaLabel.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = DeckColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 500.dp),
            )
        }
    }
}
