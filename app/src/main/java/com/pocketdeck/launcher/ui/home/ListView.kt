package com.pocketdeck.launcher.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pocketdeck.launcher.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.MetadataDisplayConfig
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import androidx.compose.ui.res.stringResource
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.input.triggerLongPressHaptic
import com.pocketdeck.launcher.ui.components.FavoriteHeartIcon
import com.pocketdeck.launcher.ui.components.GameCoverCard
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlin.math.abs

/**
 * List 视图：左侧游戏名列表（PS 风格选中效果：绘制层弹性缩放 + 胶囊背景淡入 + 文字高亮），
 * 右侧大尺寸封面预览 + 分栏游戏信息（快滚期冻结右侧大封面与元数据以避免闪烁/冗余解码）。
 */
@Composable
fun ListView(
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

    val context = LocalContext.current
    val view = LocalView.current
    val densityPx = LocalDensity.current.density
    val rubyColor = DeckColors.Ruby
    val palette = LocalDeckColors.current
    val rowSurface = palette.surface
    val brandColors = remember(palette.brandStart, palette.brandMid, palette.brandEnd) {
        listOf(palette.brandStart, palette.brandMid, palette.brandEnd)
    }
    val listState = rememberLazyListState()
    val reduceMotion = LocalReduceMotion.current
    var lastScrollTriggerMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(focusIndex, games.size, reduceMotion) {
        if (focusIndex !in games.indices) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        val rapidRepeat = fastScrolling || (now - lastScrollTriggerMs < 170L)
        lastScrollTriggerMs = now

        val info = listState.layoutInfo
        val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == focusIndex }
        if (itemInfo != null) {
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val itemCenter = itemInfo.offset + itemInfo.size / 2f
            val delta = itemCenter - viewportCenter
            if (abs(delta) > 4f) {
                if (reduceMotion) {
                    listState.scrollBy(delta)
                } else {
                    listState.animateScrollBy(
                        value = delta,
                        animationSpec = if (rapidRepeat) {
                            tween(durationMillis = 65, easing = LinearEasing)
                        } else {
                            tween(durationMillis = 160, easing = FastOutSlowInEasing)
                        },
                    )
                }
            }
        } else {
            val halfVisible = (info.visibleItemsInfo.size / 2).coerceAtLeast(1)
            val target = (focusIndex - halfVisible).coerceAtLeast(0)
            if (rapidRepeat || reduceMotion) {
                listState.scrollToItem(target)
            } else {
                listState.animateScrollToItem(target)
            }
        }
    }

    val focusedGame = games.getOrNull(focusIndex)
    val focusedPlatform = focusedGame?.let(platformOf)

    val latestOnFocusChange by rememberUpdatedState(onFocusChange)
    val latestOnLaunch by rememberUpdatedState(onLaunch)
    val latestOnLongPress by rememberUpdatedState(onLongPress)

    Row(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(
                items = games,
                key = { _, g -> g.id },
                contentType = { _, _ -> "list_row" },
            ) { i, game ->
                val focused = i == focusIndex
                val currentFocused by rememberUpdatedState(focused)
                val currentIndex by rememberUpdatedState(i)
                val currentGame by rememberUpdatedState(game)

                // 单一动画驱动缩放 / 位移 / 胶囊背景（仅在 graphicsLayer / drawBehind 读取，避免逐帧重组合）
                val focusProgressState = animateFloatAsState(
                    targetValue = if (focused) 1f else 0f,
                    animationSpec = if (fastScrolling || reduceMotion) {
                        snap()
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 520f,
                        )
                    },
                    label = "listFocus",
                )

                val isFav = game.id in favorites
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp)
                        .graphicsLayer {
                            val p = focusProgressState.value
                            val s = 1f + 0.05f * p
                            scaleX = s
                            scaleY = s
                            translationX = 6f * p * densityPx
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .drawWithCache {
                            // 品牌焦点行：卡面底色 + 渐变细描边，随焦点进度淡入（渐变/描边按尺寸缓存，绘制期零分配）
                            val radius = CornerRadius(DeckBrand.RadiusM.toPx())
                            val strokeW = 1.5.dp.toPx()
                            val inset = strokeW / 2f
                            val ringBrush = Brush.linearGradient(
                                brandColors,
                                start = Offset.Zero,
                                end = Offset(size.width, size.height),
                            )
                            val ringStroke = Stroke(width = strokeW)
                            val ringTopLeft = Offset(inset, inset)
                            val ringSize = Size(size.width - strokeW, size.height - strokeW)
                            onDrawBehind {
                                val p = focusProgressState.value
                                if (p > 0.01f) {
                                    drawRoundRect(color = rowSurface, cornerRadius = radius, alpha = p)
                                    drawRoundRect(
                                        brush = ringBrush,
                                        topLeft = ringTopLeft,
                                        size = ringSize,
                                        cornerRadius = radius,
                                        style = ringStroke,
                                        alpha = p,
                                    )
                                }
                            }
                        }
                        .pointerInput(game.id) {
                            detectTapGestures(
                                onTap = {
                                    KeySound.playTouch(context)
                                    if (currentFocused) {
                                        latestOnLaunch(currentGame)
                                    } else {
                                        latestOnFocusChange(currentIndex)
                                    }
                                },
                                onLongPress = if (latestOnLongPress != null) {
                                    {
                                        latestOnLongPress?.let { cb ->
                                            triggerLongPressHaptic(context, view)
                                            cb(currentGame)
                                        }
                                    }
                                } else null,
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = game.title,
                        style = if (focused) DeckType.listItemSelected else DeckType.listItem,
                        color = if (focused) DeckColors.TextPrimary else DeckColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isFav) {
                        Spacer(Modifier.width(8.dp))
                        FavoriteHeartIcon(
                            filled = true,
                            color = rubyColor,
                            size = 15.dp,
                        )
                    }
                }
            }
        }

        if (focusedGame != null) {
            val fields = if (showMetadata) {
                metadataMap[focusedGame.id].toDisplayFields(
                    visibility = metaDisplay,
                    fileSizeBytes = focusedGame.fileSize,
                    fallbackPlatform = focusedPlatform?.fullName,
                    labelReleaseDate = stringResource(R.string.meta_release_date),
                    labelGenre = stringResource(R.string.meta_genre),
                    labelDeveloper = stringResource(R.string.meta_developer),
                    labelPublisher = stringResource(R.string.meta_publisher),
                    labelFileSize = stringResource(R.string.meta_file_size),
                    labelPlatform = stringResource(R.string.meta_platform),
                )
            } else {
                emptyList()
            }

            val density = LocalDensity.current
            var previewAreaSize by remember { mutableStateOf(IntSize.Zero) }

            Box(
                Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .padding(top = 2.dp, bottom = 4.dp)
                    .onSizeChanged { if (it != previewAreaSize) previewAreaSize = it },
                contentAlignment = Alignment.TopCenter,
            ) {
                if (previewAreaSize.width > 0 && previewAreaSize.height > 0) {
                val maxWidth = with(density) { previewAreaSize.width.toDp() }
                val maxHeight = with(density) { previewAreaSize.height.toDp() }
                val aspect = focusedPlatform?.coverAspect ?: 1f
                val infoReserve = when {
                    fields.isEmpty() -> 8.dp
                    fields.size <= 2 -> 42.dp
                    fields.size <= 4 -> 76.dp
                    else -> 110.dp
                }
                val maxCoverW = (maxWidth - 4.dp).coerceAtLeast(100.dp)
                val maxCoverH = (maxHeight - infoReserve - 10.dp).coerceAtLeast(64.dp)
                val targetH = (maxCoverW / aspect).coerceAtMost(maxCoverH)
                val targetW = (targetH * aspect).coerceAtMost(maxCoverW)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    GameCoverCard(
                        game = focusedGame,
                        platform = focusedPlatform,
                        covers = covers,
                        officialName = officialNames[focusedGame.id],
                        width = targetW,
                        height = targetH,
                        isFocused = false,
                        isFavorite = focusedGame.id in favorites,
                        fastScrolling = fastScrolling,
                        allowDiskDuringFastScroll = false,
                        onClick = { onLaunch(focusedGame) },
                        onLongPress = onLongPress?.let { cb -> { cb(focusedGame) } },
                    )

                    if (fields.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .width((targetW + 44.dp).coerceAtMost(maxCoverW))
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            fields.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    pair.forEach { (label, value) ->
                                        MetaField(
                                            label = label,
                                            value = value,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (pair.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
