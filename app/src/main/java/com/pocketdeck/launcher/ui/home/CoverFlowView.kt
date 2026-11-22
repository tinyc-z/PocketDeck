package com.pocketdeck.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import com.pocketdeck.launcher.ui.theme.cardSurface
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * CoverFlow（3D 立体扇面）视图：
 * - 焦点封面居中正面放大，左右两侧封面按距离平滑 3D 透视微倾（rotationY + 缩放 + 层次 zIndex）
 * - 常规模式：夹紧长按弹簧滞后（<=1.8 格）+ 覆盖视觉窗口，零露白；使用 ModulateAlpha 避免离屏 RenderTarget
 * - 减少动画模式：沿用低开销快滚策略（snapTo 瞬切 + 快滚期仅内存缓存 + 关闭 3D 透视与透明度），极致流畅
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
                // 长按连发时将动画起点夹紧在目标 1.5 格内，既保留顺滑滑行感，又确保主力 5 张视觉卡片 100% 落在 ±3 窗口内不露白
                val diff = target - animatedFocus.value
                if (abs(diff) > 1.5f) {
                    animatedFocus.snapTo(target - sign(diff) * 1.5f)
                }
                animatedFocus.animateTo(
                    target,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = if (latestFastScrolling) 900f else 520f,
                    ),
                    initialVelocity = animatedFocus.velocity,
                )
            }
        }
    }

    // 基准间距比例按列表稳定取值，避免混合平台切换焦点时全舞台卡片间距抖动
    val stageAspect = remember(games.firstOrNull()?.id, games.size) {
        (platformOf(games.first())?.coverAspect ?: 0.82f).coerceIn(0.65f, 1.35f)
    }

    val dragModifier = remember(density, scope) {
        Modifier.pointerInput(Unit) {
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
        }
    }

    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    val safeFocus = focusIndex.coerceIn(0, games.lastIndex)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 6.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 3D 扇面主舞台：使用普通 Box + onSizeChanged 替代 BoxWithConstraints，避免 SubcomposeLayout 开销
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { if (it != stageSize) stageSize = it }
                .then(dragModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (stageSize.width > 0 && stageSize.height > 0) {
                val maxW = with(density) { stageSize.width.toDp() }
                val maxH = with(density) { stageSize.height.toDp() }
                CoverFlowStage(
                    games = games,
                    platformOf = platformOf,
                    animatedFocus = animatedFocus,
                    covers = covers,
                    officialNames = officialNames,
                    favorites = favorites,
                    fastScrolling = fastScrolling || isDragging,
                    reduceMotion = reduceMotion,
                    maxWidth = maxW,
                    maxHeight = maxH,
                    stageAspect = stageAspect,
                    onFocusChange = onFocusChange,
                    onLaunch = onLaunch,
                    onLongPress = onLongPress,
                )
            }
        }

        // 下方居中标题与元数据胶囊：快滚期冻结元数据胶囊重组
        CoverFlowFooter(
            games = games,
            platformOf = platformOf,
            safeFocus = safeFocus,
            metadataMap = metadataMap,
            fastScrolling = fastScrolling || isDragging,
            showMetadata = showMetadata,
            metaDisplay = metaDisplay,
        )
    }
}

@Composable
private fun CoverFlowStage(
    games: List<Game>,
    platformOf: (Game) -> Platform?,
    animatedFocus: Animatable<Float, AnimationVector1D>,
    covers: CoverSource?,
    officialNames: Map<String, String>,
    favorites: Set<String>,
    fastScrolling: Boolean,
    reduceMotion: Boolean,
    maxWidth: Dp,
    maxHeight: Dp,
    stageAspect: Float,
    onFocusChange: (Int) -> Unit,
    onLaunch: (Game) -> Unit,
    onLongPress: ((Game) -> Unit)?,
) {
    val density = LocalDensity.current

    val cardH = (maxHeight * 0.86f).coerceIn(145.dp, 260.dp)
    val baseCenterW = (cardH * stageAspect).coerceAtMost(maxWidth * 0.42f)
    val baseCenterWPx = with(density) { baseCenterW.toPx() }
    val cameraDistPx = 14f * density.density

    // 始终以实际视觉中心 animatedFocus.value.roundToInt() 为窗口中心：
    // 窗口仍严格维持 ±3（最多 7 张，零额外渲染开销），彻底避免快滚滞后时将屏幕后侧正在显示的卡片提前裁掉导致露白
    val windowCenter by remember(games.size) {
        derivedStateOf {
            animatedFocus.value.roundToInt().coerceIn(0, games.lastIndex)
        }
    }
    val visibleRange = remember(windowCenter, games.size) {
        val start = (windowCenter - 3).coerceAtLeast(0)
        val end = (windowCenter + 3).coerceAtMost(games.lastIndex)
        start..end
    }
    // 开启减少动画效果时，沿用原有低消耗策略（快滚期不读盘解码）；
    // 常规模式允许可见窗口内快滚读盘，避免长按出现空白/占位卡，且保持常量避免滑动中重复重建 ImageRequest
    val allowDiskInFastScroll = !reduceMotion

    for (idx in visibleRange) {
        val game = games[idx]
        key(game.id) {
            val plat = platformOf(game)
            val aspect = (plat?.coverAspect ?: 0.82f).coerceIn(0.65f, 1.35f)
            val cardW = (cardH * aspect).coerceAtMost(maxWidth * 0.42f)
            val distFromCenter = abs(idx - windowCenter)
            val isCenter = distFromCenter == 0
            val staticZ = 100f - distFromCenter * 10f

            // zIndex 放在外层 Box，避免把每次按键都会变化的 staticZ 传入 CoverFlowCardSlot 导致所有卡片强制重组
            Box(modifier = Modifier.zIndex(staticZ)) {
                CoverFlowCardSlot(
                    idx = idx,
                    game = game,
                    platform = plat,
                    covers = covers,
                    officialName = officialNames[game.id],
                    cardW = cardW,
                    cardH = cardH,
                    isCenter = isCenter,
                    isFavorite = game.id in favorites,
                    fastScrolling = fastScrolling,
                    allowDiskDuringFastScroll = allowDiskInFastScroll,
                    reduceMotion = reduceMotion,
                    animatedFocus = animatedFocus,
                    baseCenterWPx = baseCenterWPx,
                    cameraDistPx = cameraDistPx,
                    onFocusChange = onFocusChange,
                    onLaunch = onLaunch,
                    onLongPress = onLongPress,
                )
            }
        }
    }
}

@Composable
private fun CoverFlowCardSlot(
    idx: Int,
    game: Game,
    platform: Platform?,
    covers: CoverSource?,
    officialName: String?,
    cardW: Dp,
    cardH: Dp,
    isCenter: Boolean,
    isFavorite: Boolean,
    fastScrolling: Boolean,
    allowDiskDuringFastScroll: Boolean,
    reduceMotion: Boolean,
    animatedFocus: Animatable<Float, AnimationVector1D>,
    baseCenterWPx: Float,
    cameraDistPx: Float,
    onFocusChange: (Int) -> Unit,
    onLaunch: (Game) -> Unit,
    onLongPress: ((Game) -> Unit)?,
) {
    val latestGame by rememberUpdatedState(game)
    val latestIsCenter by rememberUpdatedState(isCenter)
    val cardOnClick = remember(game.id, idx, onLaunch, onFocusChange) {
        {
            if (latestIsCenter) onLaunch(latestGame) else onFocusChange(idx)
        }
    }
    val cardOnLongPress = remember(game.id, onLongPress) {
        onLongPress?.let { cb -> { cb(latestGame) } }
    }

    // 单层 RenderNode 硬件属性更新：拉大横向步长降低单页密度（一屏主力展示 5 张卡），减少 3D 透视裁剪面积；
    // 不设 alpha < 1f 避免离屏 RenderTarget；减少动画模式下跳过 3D 透视计算
    val transformModifier = remember(idx, baseCenterWPx, cameraDistPx, reduceMotion) {
        Modifier.graphicsLayer {
            val d = idx - animatedFocus.value
            val absD = abs(d)
            val nearFactor = absD.coerceAtMost(1f)
            val farFactor = (absD - 1f).coerceAtLeast(0f)
            val s = (1f - 0.16f * nearFactor - 0.12f * farFactor).coerceAtLeast(0.56f)
            translationX = sign(d) * (baseCenterWPx * 0.82f * nearFactor + baseCenterWPx * 0.68f * farFactor)
            scaleX = s
            scaleY = s
            if (!reduceMotion) {
                rotationY = -sign(d) * (26f * nearFactor + 4f * farFactor.coerceAtMost(1.5f))
                cameraDistance = cameraDistPx
            }
        }
    }

    // prominentFocus 恒为 false：避免焦点切换时反复创建/销毁 CoverCard 内部动画状态与 LayoutNode
    GameCoverCard(
        game = game,
        platform = platform,
        covers = covers,
        officialName = officialName,
        width = cardW,
        height = cardH,
        isFocused = isCenter,
        isFavorite = isFavorite,
        fastScrolling = fastScrolling,
        allowDiskDuringFastScroll = allowDiskDuringFastScroll,
        offscreenDefaultTitle = !reduceMotion,
        prominentFocus = false,
        drawShadow = false,
        onClick = cardOnClick,
        onLongPress = cardOnLongPress,
        modifier = transformModifier,
    )
}

@Composable
private fun CoverFlowFooter(
    games: List<Game>,
    platformOf: (Game) -> Platform?,
    safeFocus: Int,
    metadataMap: Map<String, GameMetadata>,
    fastScrolling: Boolean,
    showMetadata: Boolean,
    metaDisplay: MetadataDisplayConfig,
) {
    val focusedGame = games[safeFocus]
    val focusedPlatform = platformOf(focusedGame)
    val activeMeta = metadataMap[focusedGame.id]

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
            modifier = Modifier.widthIn(max = 560.dp),
        )

        val subtitleText = remember(focusedPlatform?.fullName, showMetadata, metaDisplay, activeMeta, safeFocus, games.size) {
            buildList {
                focusedPlatform?.fullName?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (showMetadata) {
                    if (metaDisplay.releaseDate) {
                        activeMeta?.releaseDate?.takeIf { it.isNotBlank() }?.take(4)?.let { add(it) }
                    }
                    if (metaDisplay.publisher) {
                        activeMeta?.publisher?.takeIf { it.isNotBlank() }?.let { add(it) }
                    } else if (metaDisplay.developer) {
                        activeMeta?.developer?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
                add("${safeFocus + 1}/${games.size}")
            }.joinToString(" · ")
        }
        if (subtitleText.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitleText,
                style = DeckType.metaLabel.copy(fontSize = 12.sp),
                color = DeckColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        if (showMetadata) {
            val pills = remember(focusedGame.id, focusedGame.fileSize, activeMeta, metaDisplay) {
                buildList {
                    if (metaDisplay.genre) activeMeta?.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (metaDisplay.developer && metaDisplay.publisher) {
                        activeMeta?.developer?.takeIf { it.isNotBlank() && it != activeMeta.publisher }?.let { add(it) }
                    }
                    if (metaDisplay.releaseDate) activeMeta?.releaseDate?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (metaDisplay.fileSize && focusedGame.fileSize > 0L) add(formatFileSize(focusedGame.fileSize))
                }.take(3)
            }
            // 保留固定高度槽位防止 3D 舞台上下跳动；快滚期保持实时显示，仅跳过胶囊硬件阴影重算
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .height(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (pills.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        pills.forEach { text ->
                            Box(
                                modifier = Modifier
                                    .cardSurface(
                                        shape = CircleShape,
                                        elevation = if (fastScrolling) 0.dp else 3.dp,
                                    )
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
