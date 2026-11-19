package com.pocketdeck.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GalleryAlign
import com.pocketdeck.launcher.data.MetadataDisplayConfig
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.ui.components.FavoriteHeartIcon
import com.pocketdeck.launcher.ui.components.GameCoverCard
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Gallery 视图：焦点卡放大 + 两侧小卡 + 弹簧滚动 + 手势滑动 + 小图长按菜单 + 4 列游戏元数据。
 *
 * 性能关键路径：
 * 1. 用 Animatable<Float> 驱动焦点位置，只有 offset / 缩放在动画期间重算。
 * 2. 只组合焦点附近约 12 张卡片（手动虚拟化）。
 */
@Composable
fun GalleryView(
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
    modifier: Modifier = Modifier,
    focusCardHeight: Dp = 208.dp,
    smallCardHeight: Dp = 95.dp,
    cardSpacing: Dp = 22.dp,
    leftMargin: Dp = 54.dp,
    showMetadata: Boolean = true,
    metaDisplay: MetadataDisplayConfig = MetadataDisplayConfig(),
    align: GalleryAlign = GalleryAlign.Top,
) {
    if (games.isEmpty()) {
        EmptyPlatformMessage(modifier)
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val animatedFocus = remember { Animatable(focusIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    val latestGames by rememberUpdatedState(games)
    val latestFocusIndex by rememberUpdatedState(focusIndex)
    val latestOnFocusChange by rememberUpdatedState(onFocusChange)

    // 切平台、排序或列表变化时立即对齐焦点
    LaunchedEffect(games.firstOrNull()?.id, games.lastOrNull()?.id, games.size) {
        animatedFocus.snapTo(focusIndex.toFloat())
    }

    // 滚动动画：中等刚度 + 保留速度，长按左右键也能连续丝滑滑行
    LaunchedEffect(focusIndex, isDragging) {
        if (!isDragging) {
            animatedFocus.animateTo(
                focusIndex.toFloat(),
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 560f,
                ),
                initialVelocity = animatedFocus.velocity,
            )
        }
    }

    val dragAlpha by animateFloatAsState(
        targetValue = if (isDragging || fastScrolling) 0.45f else 1f,
        animationSpec = tween(120),
        label = "titleDragAlpha",
    )
    val listKey = remember(games) { games.firstOrNull()?.id }
    val titleSlidePx = with(density) { 28.dp.roundToPx() }

    val focusedGame = games.getOrNull(focusIndex)
    var settledMetaGame by remember(listKey, games.size) { mutableStateOf(focusedGame) }
    if ((!fastScrolling && !isDragging) || settledMetaGame == null) {
        settledMetaGame = focusedGame
    }
    val focusCardHPx = with(density) { focusCardHeight.toPx() }
    val smallCardHPx = with(density) { smallCardHeight.toPx() }
    val spacingPx = with(density) { cardSpacing.toPx() }
    val startXPx = with(density) { leftMargin.toPx() }

    val cardAspects = remember(games, platformOf) {
        FloatArray(games.size) { idx -> platformOf(games[idx])?.coverAspect ?: 1f }
    }
    val prefixSmallW = remember(cardAspects, smallCardHPx) {
        FloatArray(cardAspects.size + 1).apply {
            for (k in cardAspects.indices) {
                this[k + 1] = this[k] + smallCardHPx * cardAspects[k]
            }
        }
    }
    val avgSmallW = if (cardAspects.isEmpty()) smallCardHPx else prefixSmallW[cardAspects.size] / cardAspects.size
    val stepPx = (avgSmallW + spacingPx).coerceAtLeast(60f)

    Column(modifier) {
        // 标题：焦点游戏名（常态带左右滑动进入动画；快滚连按期间直接刷新 Text 避免重叠重组合开销）
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = leftMargin, end = 16.dp)
                .height(32.dp)
                .graphicsLayer { alpha = if (isDragging) dragAlpha else 1f },
        ) {
            if (fastScrolling) {
                Text(
                    focusedGame?.title.orEmpty(),
                    style = DeckType.gameTitle,
                    color = DeckColors.Ruby,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                AnimatedContent(
                    targetState = Triple(listKey, focusIndex, focusedGame?.title.orEmpty()),
                    transitionSpec = {
                        if (targetState.first != initialState.first) {
                            fadeIn(tween(150)).togetherWith(fadeOut(tween(100)))
                        } else {
                            val dir = if (targetState.second >= initialState.second) 1 else -1
                            (fadeIn(tween(170)) + slideInHorizontally(tween(190)) { dir * titleSlidePx })
                                .togetherWith(
                                    fadeOut(tween(100)) + slideOutHorizontally(tween(130)) { -dir * (titleSlidePx / 2) },
                                )
                                .using(SizeTransform(clip = false))
                        }
                    },
                    label = "galleryTitle",
                ) { (_, _, title) ->
                    Text(
                        title,
                        style = DeckType.gameTitle,
                        color = DeckColors.Ruby,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 卡片行（仅通过 graphicsLayer 驱动位移与缩放，避免动画期逐帧重组合和重测量）
        Box(
            Modifier
                .fillMaxWidth()
                .height(focusCardHeight + 16.dp)
                .pointerInput(stepPx) {
                    val velocityTracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            velocityTracker.resetTracking()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val maxIdx = (latestGames.size - 1).coerceAtLeast(0).toFloat()
                            val next = (animatedFocus.value - dragAmount / stepPx).coerceIn(0f, maxIdx)
                            scope.launch { animatedFocus.snapTo(next) }
                        },
                        onDragEnd = {
                            val vx = velocityTracker.calculateVelocity().x
                            val maxIdx = (latestGames.size - 1).coerceAtLeast(0)
                            // 带轻微惯性预测落点
                            val projected = animatedFocus.value - (vx / stepPx) * 0.14f
                            val target = projected.roundToInt().coerceIn(0, maxIdx)
                            isDragging = false
                            if (target != latestFocusIndex) {
                                latestOnFocusChange(target)
                            } else {
                                scope.launch {
                                    animatedFocus.animateTo(
                                        target.toFloat(),
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = 560f,
                                        ),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            val target = animatedFocus.value.roundToInt()
                                .coerceIn(0, (latestGames.size - 1).coerceAtLeast(0))
                            latestOnFocusChange(target)
                        },
                    )
                },
        ) {
            val windowCenter by remember { derivedStateOf { animatedFocus.value.roundToInt() } }
            val windowStart = (windowCenter - 6).coerceAtLeast(0)
            val windowEnd = (windowCenter + 6).coerceAtMost(games.size - 1)
            val minScale = smallCardHeight / focusCardHeight
            val alignY = when (align) {
                GalleryAlign.Top -> 0f
                GalleryAlign.Center -> 0.5f
                GalleryAlign.Bottom -> 1f
            }

            for (i in windowStart..windowEnd) {
                val game = games[i]
                key(game.id) {
                    val plat = platformOf(game)
                    val aspect = plat?.coverAspect ?: 1f
                    val cardFullW = focusCardHeight * aspect
                    val isCardFocused = i == focusIndex

                    GameCoverCard(
                        game = game,
                        platform = plat,
                        covers = covers,
                        officialName = officialNames[game.id],
                        width = cardFullW,
                        height = focusCardHeight,
                        isFocused = isCardFocused,
                        isFavorite = game.id in favorites,
                        fastScrolling = fastScrolling || isDragging,
                        onClick = {
                            if (isCardFocused) {
                                onLaunch(game)
                            } else {
                                onFocusChange(i)
                            }
                        },
                        onLongPress = onLongPress?.let { cb -> { cb(game) } },
                        modifier = Modifier
                            .zIndex(if (isCardFocused) 10f else (5f - abs(i - focusIndex)).coerceAtLeast(0f))
                            .graphicsLayer {
                                val cur = animatedFocus.value
                                val dist = i - cur
                                val absDist = abs(dist)
                                val s = if (absDist < 1f) 1f - (1f - minScale) * absDist else minScale
                                val x = computeCardX(
                                    index = i,
                                    cur = cur,
                                    aspects = cardAspects,
                                    prefixSmallW = prefixSmallW,
                                    focusCardHPx = focusCardHPx,
                                    spacingPx = spacingPx,
                                    startXPx = startXPx,
                                )
                                transformOrigin = TransformOrigin(0f, alignY)
                                translationX = x
                                scaleX = s
                                scaleY = s
                                alpha = if (absDist > 5.5f) 0f else 1f
                            },
                    )
                }
            }
        }

        // 元数据行：Release date / Genre / Developer / Publisher / File size（快滚/拖动期冻结并在停稳后同步，避免高速滚动抖动）
        if (showMetadata) {
            Spacer(Modifier.height(10.dp))
            val meta = settledMetaGame?.let { metadataMap[it.id] }
            MetadataRow(
                metadata = meta,
                metaDisplay = metaDisplay,
                fileSizeBytes = settledMetaGame?.fileSize ?: 0L,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = leftMargin, end = 24.dp)
                    .graphicsLayer { alpha = dragAlpha },
            )
        }
    }
}

private fun computeCardX(
    index: Int,
    cur: Float,
    aspects: FloatArray,
    prefixSmallW: FloatArray,
    focusCardHPx: Float,
    spacingPx: Float,
    startXPx: Float,
): Float {
    val maxIdx = aspects.lastIndex
    if (maxIdx < 0) return startXPx

    fun xAtFocus(f: Int): Float = when {
        index == f -> startXPx
        index > f -> {
            val focusW = focusCardHPx * aspects[f]
            startXPx + focusW + (prefixSmallW[index] - prefixSmallW[f + 1]) + (index - f) * spacingPx
        }
        else -> {
            startXPx - (prefixSmallW[f] - prefixSmallW[index]) - (f - index) * spacingPx
        }
    }

    val clamped = cur.coerceIn(0f, maxIdx.toFloat())
    val f0 = floor(clamped).toInt().coerceIn(0, maxIdx)
    val f1 = (f0 + 1).coerceAtMost(maxIdx)
    val t = clamped - f0
    val x0 = xAtFocus(f0)
    return if (f0 == f1 || t <= 0f) x0 else x0 + (xAtFocus(f1) - x0) * t
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> {
        val kb = bytes / 1024.0
        if (kb >= 100.0) String.format(Locale.US, "%.0f KB", kb) else String.format(Locale.US, "%.1f KB", kb)
    }
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

internal fun GameMetadata?.toDisplayFields(
    visibility: MetadataDisplayConfig = MetadataDisplayConfig(),
    fileSizeBytes: Long = 0L,
    fallbackPlatform: String? = null,
    labelReleaseDate: String = "Release date",
    labelGenre: String = "Genre",
    labelDeveloper: String = "Developer",
    labelPublisher: String = "Publisher",
    labelFileSize: String = "File size",
    labelPlatform: String = "Platform",
): List<Pair<String, String>> {
    val fields = buildList {
        if (visibility.releaseDate) this@toDisplayFields?.releaseDate?.let { add(labelReleaseDate to it) }
        if (visibility.genre) this@toDisplayFields?.genre?.let { add(labelGenre to it) }
        if (visibility.developer) this@toDisplayFields?.developer?.let { add(labelDeveloper to it) }
        if (visibility.publisher) this@toDisplayFields?.publisher?.let { add(labelPublisher to it) }
        if (visibility.fileSize && fileSizeBytes > 0L) add(labelFileSize to formatFileSize(fileSizeBytes))
    }
    return if (fields.isEmpty() && !fallbackPlatform.isNullOrBlank()) {
        listOf(labelPlatform to fallbackPlatform)
    } else {
        fields
    }
}

@Composable
internal fun MetaField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            style = DeckType.metaLabel,
            color = DeckColors.TextLabel,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = DeckType.metaValue,
            color = DeckColors.TextMeta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetadataRow(
    metadata: GameMetadata?,
    metaDisplay: MetadataDisplayConfig,
    fileSizeBytes: Long,
    modifier: Modifier = Modifier,
) {
    val fields = metadata.toDisplayFields(
        visibility = metaDisplay,
        fileSizeBytes = fileSizeBytes,
        labelReleaseDate = stringResource(R.string.meta_release_date),
        labelGenre = stringResource(R.string.meta_genre),
        labelDeveloper = stringResource(R.string.meta_developer),
        labelPublisher = stringResource(R.string.meta_publisher),
        labelFileSize = stringResource(R.string.meta_file_size),
        labelPlatform = stringResource(R.string.meta_platform),
    )
    if (fields.isEmpty()) {
        Spacer(modifier.height(36.dp))
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        for ((label, value) in fields) {
            MetaField(
                label = label,
                value = value,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
internal fun EmptyPlatformMessage(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.no_games_platform),
) {
    Box(modifier.padding(horizontal = 54.dp, vertical = 32.dp)) {
        Text(
            text = text,
            style = DeckType.emptyMessage,
            color = DeckColors.TextSecondary,
        )
    }
}

/**
 * Grid 与 Bubbles 视图共用的悬浮胶囊指示器：
 * 悬浮在视口边缘（默认底部居中，当焦点接近底部时自动切至顶部居中），不占用任何网格/气泡行高。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoxScope.FocusedGameFloatingPill(
    game: Game?,
    platform: Platform?,
    isFavorite: Boolean,
    index: Int,
    total: Int,
    placeAtTop: Boolean,
    fastScrolling: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (game == null) return
    val align = if (placeAtTop) Alignment.TopCenter else Alignment.BottomCenter
    val pad = if (placeAtTop) PaddingValues(top = 4.dp, start = 16.dp, end = 16.dp)
    else PaddingValues(bottom = 6.dp, start = 16.dp, end = 16.dp)

    Row(
        modifier
            .align(align)
            .zIndex(20f)
            .padding(pad)
            .widthIn(max = 460.dp)
            .shadow(10.dp, CircleShape, ambientColor = DeckColors.CardShadow, spotColor = DeckColors.CardShadow)
            .clip(CircleShape)
            .background(DeckColors.DialogBg.copy(alpha = 0.92f))
            .border(1.2.dp, DeckColors.Ruby.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFavorite) {
            FavoriteHeartIcon(
                filled = true,
                color = DeckColors.Ruby,
                size = 13.dp,
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = game.title,
            style = DeckType.settingsValue.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
            color = DeckColors.Ruby,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (fastScrolling) {
                Modifier.weight(1f, fill = false)
            } else {
                Modifier
                    .weight(1f, fill = false)
                    .basicMarquee(iterations = Int.MAX_VALUE)
            },
        )
        platform?.shortName?.takeIf { it.isNotBlank() }?.let { shortName ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = shortName,
                style = DeckType.metaLabel.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                color = DeckColors.TextSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DeckColors.Surface)
                    .border(0.8.dp, DeckColors.DialogBorder, CircleShape)
                    .padding(horizontal = 7.dp, vertical = 1.5.dp),
            )
        }
        if (total > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${index + 1}/$total",
                style = DeckType.metaLabel.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                color = DeckColors.TextSecondary,
            )
        }
    }
}
