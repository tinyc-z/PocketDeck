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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.ui.components.GameCoverCard
import kotlin.math.abs

/**
 * Bubbles 视图：顶部固定显示当前选中游戏名 + 圆形封面蜂窝错行排列（3 / 4 / 3 / 4 交替）。
 *
 * - 固定行高 + O(1) 闭式行索引计算，避免焦点切换时全列表重算与 relayout 卡顿
 * - 选中游戏以弹性缩放 + 红白双环 prominentFocus 突出显示
 * - 连续长按上下键时使用平滑线性短动画，不反复打断重启动画
 */
@Composable
fun BubblesView(
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

    val totalRows = honeycombTotalRows(games.size)
    val focusedRowIdx = honeycombRowOf(focusIndex.coerceIn(0, games.lastIndex))

    val listState = rememberLazyListState()
    var lastScrollTriggerMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(focusedRowIdx, totalRows) {
        if (focusedRowIdx !in 0 until totalRows) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        val rapidRepeat = fastScrolling || (now - lastScrollTriggerMs < 170L)
        lastScrollTriggerMs = now

        val info = listState.layoutInfo
        val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == focusedRowIdx }
        if (itemInfo != null) {
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val itemCenter = itemInfo.offset + itemInfo.size / 2f
            val delta = itemCenter - viewportCenter
            if (abs(delta) > 6f) {
                listState.animateScrollBy(
                    value = delta,
                    animationSpec = if (rapidRepeat) {
                        tween(durationMillis = 70, easing = LinearEasing)
                    } else {
                        tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    },
                )
            }
        } else {
            val halfVisible = (info.visibleItemsInfo.size / 2).coerceAtLeast(1)
            val target = (focusedRowIdx - halfVisible).coerceAtLeast(0)
            if (rapidRepeat) {
                listState.scrollToItem(target)
            } else {
                listState.animateScrollToItem(target)
            }
        }
    }

    val focusedGame = games.getOrNull(focusIndex)
    val latestFocusedRowIdx by rememberUpdatedState(focusedRowIdx)
    val placePillAtTop by remember {
        derivedStateOf {
            val targetRow = latestFocusedRowIdx
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == targetRow }
            val viewportH = info.viewportEndOffset - info.viewportStartOffset
            if (item != null && viewportH > 0) {
                val itemCenterY = item.offset + item.size / 2f
                itemCenterY > viewportH * 0.60f
            } else {
                false
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val hGap = 14.dp
            val bubbleSize = ((maxWidth - 36.dp - hGap * 3) / 4).coerceIn(88.dp, 120.dp)
            val gridWidth = bubbleSize * 4 + hGap * 3
            // 固定行槽高度（已包含 1.18x 选中放大冗余），杜绝动画改高引起的全列表 relayout 卡顿
            val focusExtra = bubbleSize * 0.18f
            val rowSlotHeight = bubbleSize + focusExtra + 6.dp

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 46.dp),
            ) {
                items(
                    count = totalRows,
                    key = { rowIndex -> games[honeycombRowStart(rowIndex)].id },
                    contentType = { "bubble_row" },
                ) { rowIndex ->
                    val rowFocused = rowIndex == focusedRowIdx
                    val rowStart = honeycombRowStart(rowIndex)
                    val rowCount = honeycombRowCount(rowIndex, games.size)

                    @Composable
                    fun renderBubbles() {
                        for (offset in 0 until rowCount) {
                            val gameIdx = rowStart + offset
                            val game = games[gameIdx]
                            val plat = platformOf(game)
                            val focused = gameIdx == focusIndex

                            GameCoverCard(
                                game = game,
                                platform = plat,
                                covers = covers,
                                officialName = officialNames[game.id],
                                width = bubbleSize,
                                height = bubbleSize,
                                isFocused = focused,
                                isFavorite = game.id in favorites,
                                fastScrolling = fastScrolling,
                                prominentFocus = true,
                                focusedScale = 1.16f,
                                unfocusedScale = 0.89f,
                                unfocusedAlpha = 0.82f,
                                shapeOverride = CircleShape,
                                centeredTitle = true,
                                onClick = {
                                    if (focused) onLaunch(game) else onFocusChange(gameIdx)
                                },
                                onLongPress = onLongPress?.let { cb -> { cb(game) } },
                            )
                        }
                    }

                    if (totalRows == 1) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(rowSlotHeight)
                                .zIndex(if (rowFocused) 1f else 0f),
                            horizontalArrangement = Arrangement.spacedBy(hGap, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            renderBubbles()
                        }
                    } else {
                        val startIndent = if (rowIndex % 2 == 0) (bubbleSize + hGap) / 2 else 0.dp
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(rowSlotHeight)
                                .zIndex(if (rowFocused) 1f else 0f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                Modifier
                                    .width(gridWidth)
                                    .padding(start = startIndent),
                                horizontalArrangement = Arrangement.spacedBy(hGap, Alignment.Start),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                renderBubbles()
                            }
                        }
                    }
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

private fun honeycombRowOf(index: Int): Int {
    val pair = index / 7
    return pair * 2 + if (index % 7 < 3) 0 else 1
}

private fun honeycombRowStart(row: Int): Int =
    (row / 2) * 7 + if (row % 2 == 0) 0 else 3

private fun honeycombRowCount(row: Int, total: Int): Int =
    minOf(if (row % 2 == 0) 3 else 4, (total - honeycombRowStart(row)).coerceAtLeast(0))

private fun honeycombTotalRows(total: Int): Int =
    if (total <= 0) 0 else (total / 7) * 2 + when {
        total % 7 == 0 -> 0
        total % 7 <= 3 -> 1
        else -> 2
    }

/** 计算蜂窝视图中按上下方向键的新焦点下标（O(1) 闭式计算，零分配）。 */
fun bubblesMoveVertical(currentIndex: Int, total: Int, rowDelta: Int): Int {
    if (total <= 1) return 0
    val totalRows = honeycombTotalRows(total)
    val clamped = currentIndex.coerceIn(0, total - 1)
    val curRowIdx = honeycombRowOf(clamped)
    val targetRowIdx = (curRowIdx + rowDelta).coerceIn(0, totalRows - 1)
    if (targetRowIdx == curRowIdx) return clamped

    fun slotX(rowIdx: Int, col: Int): Float =
        (if (totalRows > 1 && rowIdx % 2 == 0) 0.5f else 0f) + col

    val curStart = honeycombRowStart(curRowIdx)
    val targetStart = honeycombRowStart(targetRowIdx)
    val targetSize = honeycombRowCount(targetRowIdx, total).coerceAtLeast(1)
    val curX = slotX(curRowIdx, clamped - curStart)
    var bestCol = 0
    var bestDist = Float.MAX_VALUE
    for (col in 0 until targetSize) {
        val d = abs(slotX(targetRowIdx, col) - curX)
        if (d < bestDist) {
            bestDist = d
            bestCol = col
        }
    }
    return (targetStart + bestCol).coerceIn(0, total - 1)
}
