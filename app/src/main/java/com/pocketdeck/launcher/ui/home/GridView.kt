package com.pocketdeck.launcher.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.ui.components.GameCoverCard
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import kotlin.math.abs

const val GRID_COLUMNS = 5

/**
 * Grid 视图：顶部固定显示当前选中游戏名与平台信息 + 5 列封面网格，焦点项描边高亮 + 自动滚入视口。
 */
@Composable
fun GridView(
    games: List<Game>,
    platformOf: (Game) -> Platform?,
    focusIndex: Int,
    covers: CoverSource?,
    officialNames: Map<String, String>,
    favorites: Set<String> = emptySet(),
    onFocusChange: (Int) -> Unit,
    onLaunch: (Game) -> Unit,
    onLongPress: ((Game) -> Unit)? = null,
    fastScrolling: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (games.isEmpty()) {
        EmptyPlatformMessage(modifier)
        return
    }

    val gridState = rememberLazyGridState()
    val reduceMotion = LocalReduceMotion.current
    var lastScrollTriggerMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(focusIndex, games.size, reduceMotion) {
        if (focusIndex !in games.indices) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        val rapidRepeat = fastScrolling || (now - lastScrollTriggerMs < 170L)
        lastScrollTriggerMs = now

        val info = gridState.layoutInfo
        val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == focusIndex }
        if (itemInfo != null) {
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val itemCenter = itemInfo.offset.y + itemInfo.size.height / 2f
            val delta = itemCenter - viewportCenter
            if (abs(delta) > 6f) {
                if (reduceMotion) {
                    gridState.scrollBy(delta)
                } else {
                    gridState.animateScrollBy(
                        value = delta,
                        animationSpec = if (rapidRepeat) {
                            tween(durationMillis = 70, easing = LinearEasing)
                        } else {
                            tween(durationMillis = 180, easing = FastOutSlowInEasing)
                        },
                    )
                }
            }
        } else {
            val halfVisible = (info.visibleItemsInfo.size / 2).coerceAtLeast(GRID_COLUMNS)
            val target = (focusIndex - halfVisible).coerceAtLeast(0)
            if (rapidRepeat || reduceMotion) {
                gridState.scrollToItem(target)
            } else {
                gridState.animateScrollToItem(target)
            }
        }
    }

    val focusedGame = games.getOrNull(focusIndex)
    val latestFocusIndex by rememberUpdatedState(focusIndex)
    var placePillAtTop by remember { mutableStateOf(false) }
    LaunchedEffect(gridState, focusIndex, fastScrolling) {
        if (fastScrolling) return@LaunchedEffect
        delay(120L)
        snapshotFlow {
            val info = gridState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == latestFocusIndex }
            val viewportH = info.viewportEndOffset - info.viewportStartOffset
            Triple(item, viewportH, gridState.isScrollInProgress to gridState.canScrollForward)
        }.collect { (item, viewportH, flags) ->
            val (scrolling, canScrollDown) = flags
            if (scrolling || item == null || viewportH <= 0) return@collect
            val itemCenterY = item.offset.y + item.size.height / 2f
            val itemBottomY = item.offset.y + item.size.height
            placePillAtTop = if (!placePillAtTop) {
                !canScrollDown && (itemBottomY > viewportH * 0.78f || itemCenterY > viewportH * 0.70f)
            } else {
                !canScrollDown && itemCenterY >= viewportH * 0.56f
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val hPadding = 18.dp
            val spacing = 14.dp
            val cellWidth = (maxWidth - hPadding * 2 - spacing * (GRID_COLUMNS - 1)) / GRID_COLUMNS

            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = hPadding, end = hPadding, top = 14.dp, bottom = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                itemsIndexed(
                    items = games,
                    key = { _, g -> g.id },
                    contentType = { _, _ -> "grid_cell" },
                ) { i, game ->
                    val plat = platformOf(game)
                    val aspect = plat?.coverAspect ?: 1f
                    val cellHeight = (cellWidth / aspect).coerceIn(66.dp, 134.dp)
                    val focused = i == focusIndex
                    val itemOnClick = remember(game.id, i, focused, onLaunch, onFocusChange) {
                        { if (focused) onLaunch(game) else onFocusChange(i) }
                    }
                    val itemOnLongPress = remember(game.id, onLongPress) {
                        onLongPress?.let { cb -> { cb(game) } }
                    }

                    GameCoverCard(
                        game = game,
                        platform = plat,
                        covers = covers,
                        officialName = officialNames[game.id],
                        width = cellWidth,
                        height = cellHeight,
                        isFocused = focused,
                        isFavorite = game.id in favorites,
                        fastScrolling = fastScrolling,
                        prominentFocus = true,
                        focusedScale = 1.09f,
                        unfocusedScale = 0.94f,
                        unfocusedAlpha = 0.72f,
                        onClick = itemOnClick,
                        onLongPress = itemOnLongPress,
                    )
                }
            }
        }

        FocusedGameFloatingPill(
            game = focusedGame,
            platform = focusedGame?.let(platformOf),
            isFavorite = focusedGame?.id in favorites,
            index = focusIndex,
            total = games.size,
            placeAtTop = placePillAtTop,
            fastScrolling = fastScrolling,
        )
    }
}
