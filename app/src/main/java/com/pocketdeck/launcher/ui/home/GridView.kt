package com.pocketdeck.launcher.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.ui.components.GameCoverCard
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
    modifier: Modifier = Modifier,
) {
    if (games.isEmpty()) {
        EmptyPlatformMessage(modifier)
        return
    }

    val gridState = rememberLazyGridState()
    var lastScrollTriggerMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(focusIndex, games.size) {
        if (focusIndex !in games.indices) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        val rapidRepeat = now - lastScrollTriggerMs < 170L
        lastScrollTriggerMs = now

        val info = gridState.layoutInfo
        val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == focusIndex }
        if (itemInfo != null) {
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val itemCenter = itemInfo.offset.y + itemInfo.size.height / 2f
            val delta = itemCenter - viewportCenter
            if (abs(delta) > 6f) {
                gridState.animateScrollBy(
                    value = delta,
                    animationSpec = if (rapidRepeat) {
                        tween(durationMillis = 70, easing = LinearEasing)
                    } else {
                        tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    },
                )
            }
        } else {
            val halfVisible = (info.visibleItemsInfo.size / 2).coerceAtLeast(GRID_COLUMNS)
            val target = (focusIndex - halfVisible).coerceAtLeast(0)
            if (rapidRepeat) {
                gridState.scrollToItem(target)
            } else {
                gridState.animateScrollToItem(target)
            }
        }
    }

    val focusedGame = games.getOrNull(focusIndex)
    val placePillAtTop by remember(focusIndex, games.size) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == focusIndex }
            val viewportH = info.viewportEndOffset - info.viewportStartOffset
            if (item != null && viewportH > 0) {
                val itemCenterY = item.offset.y + item.size.height / 2f
                itemCenterY > viewportH * 0.60f
            } else {
                false
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
                contentPadding = PaddingValues(start = hPadding, end = hPadding, top = 8.dp, bottom = 44.dp),
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

                    GameCoverCard(
                        game = game,
                        platform = plat,
                        covers = covers,
                        officialName = officialNames[game.id],
                        width = cellWidth,
                        height = cellHeight,
                        isFocused = focused,
                        isFavorite = game.id in favorites,
                        prominentFocus = true,
                        focusedScale = 1.07f,
                        unfocusedScale = 0.98f,
                        unfocusedAlpha = 1f,
                        onClick = {
                            if (focused) onLaunch(game) else onFocusChange(i)
                        },
                        onLongPress = onLongPress?.let { cb -> { cb(game) } },
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
        )
    }
}
