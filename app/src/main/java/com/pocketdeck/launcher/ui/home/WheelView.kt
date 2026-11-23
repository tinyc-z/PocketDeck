package com.pocketdeck.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
import com.pocketdeck.launcher.ui.components.soundClickable
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import com.pocketdeck.launcher.ui.theme.cardSurface
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Arc Wheel（弧形转盘）视图：
 * - 左侧 52%：柔和圆弧进度轨 + 沿 C 形弧线排布的胶囊游戏卡（手动虚拟化仅组合焦点 ±3 张，全 graphicsLayer 零重排）
 * - 右侧 48%：大尺寸高清封面展示 + 元数据胶囊 + 序号
 * - 支持 D-Pad 上下拨动转盘（左右切标签）与触摸上下拖拽滑动
 */
@Composable
fun WheelView(
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
    val palette = LocalDeckColors.current

    val animatedFocus = remember { Animatable(focusIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    val latestGames by rememberUpdatedState(games)
    val latestFocusIndex by rememberUpdatedState(focusIndex)
    val latestOnFocusChange by rememberUpdatedState(onFocusChange)
    val latestFastScrolling by rememberUpdatedState(fastScrolling)

    LaunchedEffect(games.firstOrNull()?.id, games.lastOrNull()?.id, games.size) {
        animatedFocus.snapTo(focusIndex.toFloat())
    }

    LaunchedEffect(focusIndex, isDragging, reduceMotion) {
        if (!isDragging) {
            if (reduceMotion) {
                animatedFocus.snapTo(focusIndex.toFloat())
            } else {
                val target = focusIndex.toFloat()
                // 连发快滚时将动画起点夹紧在目标 1.5 格内，避免滞后脱节与多余空窗
                val diff = target - animatedFocus.value
                if (abs(diff) > 1.5f) {
                    animatedFocus.snapTo(target - kotlin.math.sign(diff) * 1.5f)
                }
                animatedFocus.animateTo(
                    target,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = if (latestFastScrolling) 900f else 560f,
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
    var wheelAreaSize by remember { mutableStateOf(IntSize.Zero) }
    var coverAreaSize by remember { mutableStateOf(IntSize.Zero) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧弧形转盘（普通 Box + onSizeChanged + drawWithCache 零每帧分配）
        Box(
            modifier = Modifier
                .weight(0.54f)
                .fillMaxHeight()
                .clipToBounds()
                .onSizeChanged { if (it != wheelAreaSize) wheelAreaSize = it }
                .drawWithCache {
                    val trackW = size.width * 0.44f
                    val arcRect = Size(trackW, size.height * 0.82f)
                    val topLeft = Offset(-trackW * 0.64f, size.height * 0.09f)
                    val trackColor = if (palette.isDark) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        Color.White.copy(alpha = 0.62f)
                    }
                    val trackStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    val thumbStroke = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    val thumbBrush = Brush.verticalGradient(
                        listOf(palette.brandStart, palette.brandMid, palette.brandEnd),
                    )
                    val totalLastIndex = games.lastIndex
                    val singleItem = games.size <= 1
                    onDrawBehind {
                        drawArc(
                            color = trackColor,
                            startAngle = -56f,
                            sweepAngle = 112f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcRect,
                            style = trackStroke,
                        )
                        val ratio = if (singleItem) 0.5f else (animatedFocus.value / totalLastIndex.toFloat()).coerceIn(0f, 1f)
                        val thumbSweep = 24f
                        val thumbStart = -56f + (112f - thumbSweep) * ratio
                        drawArc(
                            brush = thumbBrush,
                            startAngle = thumbStart,
                            sweepAngle = thumbSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcRect,
                            style = thumbStroke,
                        )
                    }
                }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            tracker.resetTracking()
                        },
                        onDragEnd = {
                            isDragging = false
                            val v = tracker.calculateVelocity().y
                            val stepPx = with(density) { 58.dp.toPx() }
                            val projected = animatedFocus.value - (v * 0.13f / stepPx)
                            val target = projected.roundToInt().coerceIn(0, latestGames.lastIndex)
                            if (target != latestFocusIndex) {
                                latestOnFocusChange(target)
                            } else {
                                scope.launch {
                                    animatedFocus.animateTo(
                                        target.toFloat(),
                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 560f),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch { animatedFocus.animateTo(latestFocusIndex.toFloat()) }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val stepPx = with(density) { 58.dp.toPx() }
                            val next = (animatedFocus.value - dragAmount / stepPx)
                                .coerceIn(-0.35f, latestGames.lastIndex + 0.35f)
                            scope.launch { animatedFocus.snapTo(next) }
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            if (wheelAreaSize.width > 0 && wheelAreaSize.height > 0) {
            val maxWidth = with(density) { wheelAreaSize.width.toDp() }
            val maxHeight = with(density) { wheelAreaSize.height.toDp() }
            val stepYPx = with(density) { (maxHeight * 0.20f).coerceIn(48.dp, 62.dp).toPx() }
            val arcDepthPx = with(density) { 44.dp.toPx() }
            val baseLeftPx = with(density) { 26.dp.toPx() }
            val pillMaxW = (maxWidth - 78.dp).coerceIn(168.dp, 282.dp)
            val focusBorderBrush = remember(palette.brandStart, palette.brandMid, palette.brandEnd) {
                Brush.linearGradient(listOf(palette.brandStart, palette.brandMid, palette.brandEnd))
            }

            val effectiveFastScroll = fastScrolling || isDragging
            val visibleRange by remember(games.size) {
                derivedStateOf {
                    val center = animatedFocus.value.roundToInt().coerceIn(0, games.lastIndex)
                    val start = (center - 2).coerceAtLeast(0)
                    val end = (center + 2).coerceAtMost(games.lastIndex)
                    start..end
                }
            }

            for (idx in visibleRange) {
                val game = games[idx]
                key(game.id) {
                    val plat = platformOf(game)
                    val isSelected = idx == safeFocus
                    val staticZ = 50f - abs(idx - safeFocus) * 10f

                    val pillShape = RoundedCornerShape(24.dp)
                    Row(
                        modifier = Modifier
                            .zIndex(staticZ)
                            .width(pillMaxW)
                            .graphicsLayer {
                                // 严格在绘制阶段读取 animatedFocus.value，动画期零重组、零重排
                                val d = idx - animatedFocus.value
                                val absD = abs(d)
                                val normD = (absD / 2.25f).coerceIn(0f, 1.15f)
                                val bulge = (1f - normD * normD).coerceAtLeast(0f)
                                val s = (1f - absD * 0.075f).coerceAtLeast(0.80f)
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                translationX = baseLeftPx + bulge * arcDepthPx
                                translationY = d * stepYPx
                                scaleX = s
                                scaleY = s
                                alpha = (1f - absD * 0.24f).coerceIn(0.18f, 1f)
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .cardSurface(
                                            shape = pillShape,
                                            elevation = if (effectiveFastScroll) 0.dp else 4.dp,
                                        )
                                        .border(2.dp, focusBorderBrush, pillShape)
                                } else {
                                    Modifier
                                        .clip(pillShape)
                                        .background(
                                            palette.surface.copy(alpha = if (palette.isDark) 0.56f else 0.68f),
                                            pillShape,
                                        )
                                        .border(0.75.dp, DeckColors.Line, pillShape)
                                },
                            )
                            .soundClickable {
                                if (isSelected) onLaunch(game) else onFocusChange(idx)
                            }
                            .padding(start = 8.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GameCoverCard(
                            game = game,
                            platform = plat,
                            covers = covers,
                            officialName = officialNames[game.id],
                            width = 36.dp,
                            height = 36.dp,
                            isFocused = false,
                            isFavorite = false,
                            fastScrolling = effectiveFastScroll,
                            shapeOverride = RoundedCornerShape(10.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = game.title,
                            style = if (isSelected) {
                                DeckType.listItemSelected.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            } else {
                                DeckType.listItem.copy(fontSize = 13.sp)
                            },
                            color = if (isSelected) DeckColors.TextPrimary else DeckColors.TextSecondary,
                            maxLines = if (isSelected) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected && plat != null) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DeckColors.AccentSoft)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = plat.shortName,
                                    style = DeckType.metaLabel.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = DeckColors.Ruby,
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        // 右侧大封面陈列区（普通 Box + onSizeChanged 替代 BoxWithConstraints；快滚不读 512px 磁盘）
        Box(
            modifier = Modifier
                .weight(0.46f)
                .fillMaxHeight()
                .padding(start = 10.dp, end = 6.dp)
                .onSizeChanged { if (it != coverAreaSize) coverAreaSize = it },
            contentAlignment = Alignment.Center,
        ) {
            if (coverAreaSize.width > 0 && coverAreaSize.height > 0) {
            val maxWidth = with(density) { coverAreaSize.width.toDp() }
            val maxHeight = with(density) { coverAreaSize.height.toDp() }
            val coverH = (maxHeight * 0.76f).coerceIn(140.dp, 250.dp)
            val aspect = (focusedPlatform?.coverAspect ?: 0.85f).coerceIn(0.65f, 1.32f)
            val coverW = (coverH * aspect).coerceAtMost(maxWidth * 0.92f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GameCoverCard(
                    game = focusedGame,
                    platform = focusedPlatform,
                    covers = covers,
                    officialName = officialNames[focusedGame.id],
                    width = coverW,
                    height = coverH,
                    isFocused = true,
                    isFavorite = focusedGame.id in favorites,
                    fastScrolling = fastScrolling || isDragging,
                    allowDiskDuringFastScroll = false,
                    prominentFocus = false,
                    onClick = { onLaunch(focusedGame) },
                    onLongPress = onLongPress?.let { cb -> { cb(focusedGame) } },
                )

                Spacer(Modifier.height(10.dp))

                val pills = buildList {
                    if (showMetadata) {
                        val pubYear = listOfNotNull(
                            if (metaDisplay.publisher) focusedMeta?.publisher?.takeIf { it.isNotBlank() }
                            else if (metaDisplay.developer) focusedMeta?.developer?.takeIf { it.isNotBlank() }
                            else null,
                            if (metaDisplay.releaseDate) focusedMeta?.releaseDate?.takeIf { it.isNotBlank() }?.take(4) else null,
                        ).joinToString(" · ")
                        if (pubYear.isNotBlank()) add(pubYear)
                        if (metaDisplay.genre) focusedMeta?.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
                        if (metaDisplay.fileSize && focusedGame.fileSize > 0L) add(formatFileSize(focusedGame.fileSize))
                    }
                    if (isEmpty()) {
                        focusedPlatform?.fullName?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                    add("${safeFocus + 1}/${games.size}")
                }.take(3)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pills.forEach { text ->
                        Box(
                            modifier = Modifier
                                .cardSurface(shape = CircleShape, elevation = 3.dp)
                                .border(0.75.dp, DeckColors.Line, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = text,
                                style = DeckType.metaLabel.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                color = DeckColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp),
                            )
                        }
                    }
                }
            }
            }
        }
    }
}
