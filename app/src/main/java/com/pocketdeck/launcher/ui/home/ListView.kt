package com.pocketdeck.launcher.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * List 视图：左侧游戏名列表（PS 风格选中效果：弹性缩放 + 胶囊背景淡入 + 文字高亮），
 * 右侧大尺寸封面预览 + 分栏游戏信息。
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
    val listState = rememberLazyListState()
    LaunchedEffect(focusIndex, games.size) {
        if (focusIndex !in games.indices) return@LaunchedEffect
        val info = listState.layoutInfo
        val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == focusIndex }
        if (itemInfo != null) {
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val itemCenter = itemInfo.offset + itemInfo.size / 2f
            listState.animateScrollBy(itemCenter - viewportCenter)
        } else {
            val halfVisible = (info.visibleItemsInfo.size / 2).coerceAtLeast(1)
            listState.animateScrollToItem((focusIndex - halfVisible).coerceAtLeast(0))
        }
    }

    val focusedGame = games.getOrNull(focusIndex)
    val focusedPlatform = focusedGame?.let(platformOf)

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
            itemsIndexed(games, key = { _, g -> g.id }) { i, game ->
                val focused = i == focusIndex

                // PS 风格动画：选中项弹性缩放 + 背景胶囊淡入
                val scale by animateFloatAsState(
                    targetValue = if (focused) 1.05f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "listScale",
                )
                val bgAlpha by animateFloatAsState(
                    targetValue = if (focused) 0.20f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "listBgAlpha",
                )
                val textAlpha by animateFloatAsState(
                    targetValue = if (focused) 1f else 0.60f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "listTextAlpha",
                )
                val indent by animateFloatAsState(
                    targetValue = if (focused) 6f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "listIndent",
                )

                val isFav = game.id in favorites
                Row(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = indent * density
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeckColors.Ruby.copy(alpha = bgAlpha))
                        .pointerInput(focused, i, game.id) {
                            detectTapGestures(
                                onTap = {
                                    KeySound.playTouch(context)
                                    if (focused) onLaunch(game) else onFocusChange(i)
                                },
                                onLongPress = onLongPress?.let { cb ->
                                    {
                                        triggerLongPressHaptic(context, view)
                                        cb(game)
                                    }
                                },
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = game.title,
                        style = if (focused) DeckType.listItemSelected else DeckType.listItem,
                        color = if (focused) DeckColors.Ruby else DeckColors.TextSecondary.copy(alpha = textAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isFav) {
                        Spacer(Modifier.width(8.dp))
                        FavoriteHeartIcon(
                            filled = true,
                            color = if (focused) DeckColors.Ruby else DeckColors.Ruby.copy(alpha = 0.85f),
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

            BoxWithConstraints(
                Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .padding(top = 2.dp, bottom = 4.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                val aspect = focusedPlatform?.coverAspect ?: 1f
                val infoReserve = when {
                    fields.isEmpty() -> 8.dp
                    fields.size <= 2 -> 42.dp
                    fields.size <= 4 -> 76.dp
                    else -> 110.dp
                }
                val maxCoverW = (maxWidth - 4.dp).coerceAtLeast(100.dp)
                val maxCoverH = (maxHeight - infoReserve - 10.dp).coerceAtLeast(140.dp)
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
