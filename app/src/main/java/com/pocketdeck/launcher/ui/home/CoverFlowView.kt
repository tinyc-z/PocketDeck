package com.pocketdeck.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.MetadataDisplayConfig
import com.pocketdeck.launcher.data.formatFileSize
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.GameMetadata
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.ui.components.GameCoverCard
import com.pocketdeck.launcher.ui.components.Text
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import com.pocketdeck.launcher.ui.theme.cardSurface
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * CoverFlow（3D 立体扇面）视图：
 * - 焦点封面居中正面放大，左右两侧封面按距离平滑 3D 透视微倾（rotationY + 缩放 + 层次 zIndex）
 * - 手动虚拟化，仅组合焦点两侧 ±4 张卡片，60fps 零卡顿
 * - 下方居中展示粗体标题、副标题与元数据胶囊
 */
@Composable
fun CoverFlowView(
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

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current

    val animatedFocus = remember { Animatable(focusIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    val latestGames by rememberUpdatedState(games)
    val latestFocusIndex by rememberUpdatedState(focusIndex)
    val latestOnFocusChange by rememberUpdatedState(onFocusChange)

    LaunchedEffect(games.firstOrNull()?.id, games.lastOrNull()?.id, games.size) {
        animatedFocus.snapTo(focusIndex.toFloat())
    }

    LaunchedEffect(focusIndex, isDragging, reduceMotion) {
        if (!isDragging) {
            if (reduceMotion) {
                animatedFocus.snapTo(focusIndex.toFloat())
            } else {
                animatedFocus.animateTo(
                    focusIndex.toFloat(),
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 520f,
                    ),
                    initialVelocity = animatedFocus.velocity,
                )
            }
        }
    }

    val safeFocus = focusIndex.coerceIn(0, games.lastIndex)
    val focusedGame = games[safeFocus]
    val focusedPlatform = platformOf(focusedGame)
    val focusedMeta = metadataMap[focusedGame.id]

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 6.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 3D 扇面主舞台
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            tracker.resetTracking()
                        },
                        onDragEnd = {
                            isDragging = false
                            val v = tracker.calculateVelocity().x
                            val stepPx = with(density) { 120.dp.toPx() }
                            val projected = animatedFocus.value - (v * 0.14f / stepPx)
                            val target = projected.roundToInt().coerceIn(0, latestGames.lastIndex)
                            if (target != latestFocusIndex) {
                                latestOnFocusChange(target)
                            } else {
                                scope.launch {
                                    animatedFocus.animateTo(
                                        target.toFloat(),
                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 520f),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                animatedFocus.animateTo(latestFocusIndex.toFloat())
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val stepPx = with(density) { 120.dp.toPx() }
                            val next = (animatedFocus.value - dragAmount / stepPx)
                                .coerceIn(-0.35f, latestGames.lastIndex + 0.35f)
                            scope.launch { animatedFocus.snapTo(next) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val cardH = (maxHeight * 0.86f).coerceIn(145.dp, 260.dp)
            val centerAspect = (focusedPlatform?.coverAspect ?: 0.82f).coerceIn(0.65f, 1.35f)
            val baseCenterW = (cardH * centerAspect).coerceAtMost(maxWidth * 0.42f)
            val baseCenterWPx = with(density) { baseCenterW.toPx() }
            val cameraDistPx = 14f * density.density

            val visibleRange by remember(games.size) {
                derivedStateOf {
                    val cur = animatedFocus.value
                    val start = (floor(cur).toInt() - 4).coerceAtLeast(0)
                    val end = (ceil(cur).toInt() + 4).coerceAtMost(games.lastIndex)
                    start..end
                }
            }

            for (idx in visibleRange) {
                val game = games[idx]
                key(game.id) {
                    val plat = platformOf(game)
                    val aspect = (plat?.coverAspect ?: 0.82f).coerceIn(0.65f, 1.35f)
                    val cardW = (cardH * aspect).coerceAtMost(maxWidth * 0.42f)
                    val d = idx - animatedFocus.value
                    val absD = abs(d)
                    val isCenter = idx == safeFocus

                    val nearFactor = absD.coerceAtMost(1f)
                    val farFactor = (absD - 1f).coerceAtLeast(0f)
                    val scale = (1f - 0.14f * nearFactor - 0.075f * farFactor).coerceAtLeast(0.58f)
                    val rotY = if (reduceMotion) 0f else (-sign(d) * (32f * nearFactor + 4f * farFactor.coerceAtMost(2f)))
                    val transX = sign(d) * (baseCenterWPx * 0.62f * nearFactor + baseCenterWPx * 0.44f * farFactor)
                    val cardAlpha = (1f - absD * 0.15f).coerceIn(0.28f, 1f)
                    val z = 100f - absD * 10f

                    Box(
                        modifier = Modifier
                            .zIndex(z)
                            .graphicsLayer {
                                translationX = transX
                                scaleX = scale
                                scaleY = scale
                                rotationY = rotY
                                cameraDistance = cameraDistPx
                                alpha = cardAlpha
                            },
                    ) {
                        GameCoverCard(
                            game = game,
                            platform = plat,
                            covers = covers,
                            officialName = officialNames[game.id],
                            width = cardW,
                            height = cardH,
                            isFocused = isCenter,
                            isFavorite = game.id in favorites,
                            fastScrolling = fastScrolling,
                            prominentFocus = isCenter,
                            onClick = {
                                if (isCenter) onLaunch(game) else onFocusChange(idx)
                            },
                            onLongPress = onLongPress?.let { cb -> { cb(game) } },
                        )
                    }
                }
            }
        }

        // 下方居中标题与元数据胶囊
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = focusedGame.title,
                style = DeckType.gameTitle.copy(fontSize = 20.sp, lineHeight = 25.sp),
                color = DeckColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE),
            )

            val subtitleParts = buildList {
                focusedPlatform?.fullName?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (showMetadata) {
                    if (metaDisplay.releaseDate) {
                        focusedMeta?.releaseDate?.takeIf { it.isNotBlank() }?.take(4)?.let { add(it) }
                    }
                    if (metaDisplay.publisher) {
                        focusedMeta?.publisher?.takeIf { it.isNotBlank() }?.let { add(it) }
                    } else if (metaDisplay.developer) {
                        focusedMeta?.developer?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
                add("${safeFocus + 1}/${games.size}")
            }
            if (subtitleParts.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitleParts.joinToString(" · "),
                    style = DeckType.metaLabel.copy(fontSize = 12.sp),
                    color = DeckColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            if (showMetadata) {
                val pills = buildList {
                    if (metaDisplay.genre) focusedMeta?.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (metaDisplay.developer && metaDisplay.publisher) {
                        focusedMeta?.developer?.takeIf { it.isNotBlank() && it != focusedMeta.publisher }?.let { add(it) }
                    }
                    if (metaDisplay.releaseDate) focusedMeta?.releaseDate?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (metaDisplay.fileSize && focusedGame.fileSize > 0L) add(formatFileSize(focusedGame.fileSize))
                }.take(3)
                if (pills.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        pills.forEach { text ->
                            Box(
                                modifier = Modifier
                                    .cardSurface(shape = CircleShape, elevation = 3.dp)
                                    .border(0.75.dp, DeckColors.Line, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = text,
                                    style = DeckType.metaLabel.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                    color = DeckColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
