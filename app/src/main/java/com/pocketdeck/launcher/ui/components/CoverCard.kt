package com.pocketdeck.launcher.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.model.Game
import com.pocketdeck.launcher.data.model.Platform
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.input.triggerLongPressHaptic
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckFontFamily
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import com.pocketdeck.launcher.ui.theme.brandFocusRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** 全局网络连通性 CompositionLocal，由顶层 HomeScreen 统一订阅一次，避免每张封面卡片各自订阅 StateFlow。 */
val LocalIsOnline = staticCompositionLocalOf { true }

internal object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(64)

    fun get(pkg: String): ImageBitmap? = cache.get(pkg)

    fun loadSync(context: Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        return runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(192, 192).asImageBitmap()
        }.getOrNull()?.also { cache.put(pkg, it) }
    }

    fun trimMemory() {
        cache.evictAll()
    }
}

/** 系统内存紧张或退后台时回收 UI 位图缓存（AppIconCache + DefaultCoverArtCache）。 */
internal fun trimCoverCardUiCaches() {
    AppIconCache.trimMemory()
    DefaultCoverArtCache.trimMemory()
}

/**
 * 便捷封装：自动监听在线封面索引更新、匹配官方名并缓存命中的封面 URL。
 */
@Composable
fun GameCoverCard(
    game: Game,
    platform: Platform?,
    covers: CoverSource?,
    officialName: String?,
    width: Dp,
    height: Dp,
    isFocused: Boolean,
    isFavorite: Boolean = false,
    fastScrolling: Boolean = false,
    allowDiskDuringFastScroll: Boolean = false,
    offscreenDefaultTitle: Boolean = false,
    modifier: Modifier = Modifier,
    shapeOverride: Shape? = null,
    centeredTitle: Boolean = false,
    prominentFocus: Boolean = false,
    focusedScale: Float = 1f,
    unfocusedScale: Float = 1f,
    unfocusedAlpha: Float = 1f,
    showPlatformBadge: Boolean = true,
    drawShadow: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val candidatesUnlockedHolder = remember(game.id) { BooleanArray(1) }
    if (!fastScrolling) {
        candidatesUnlockedHolder[0] = true
    }
    val candidatesUnlocked = candidatesUnlockedHolder[0]

    val isOnline = LocalIsOnline.current
    val candidates: List<Any>
    val knownIdx: Int?
    var offlineLocalFallback = false
    if (!candidatesUnlocked) {
        candidates = remember(game.id, game.customCover, game.localCover) {
            covers?.fastMemoryCandidates(game)
                ?: listOfNotNull(game.customCover, game.localCover)
        }
        knownIdx = if (candidates.isNotEmpty()) 0 else null
    } else {
        val indexVer = covers?.indexVersion?.collectAsState()?.value ?: 0
        val baseCandidates = remember(game.id, game.customCover, game.localCover, game.title, platform?.libretroSystem, officialName, indexVer) {
            covers?.candidates(game, platform, officialName)
                ?: listOfNotNull(game.customCover, game.localCover)
        }
        val localPath = game.localCover
        if (!isOnline && game.customCover == null && localPath != null &&
            baseCandidates.size == 1 && baseCandidates[0] is String &&
            (baseCandidates[0] as String).startsWith("http", ignoreCase = true)
        ) {
            offlineLocalFallback = true
            candidates = remember(baseCandidates, localPath, covers) {
                val localModel: Any = covers?.preferredLocalCoverFile(localPath) ?: File(localPath)
                listOf(baseCandidates[0], localModel)
            }
        } else {
            candidates = baseCandidates
        }
        knownIdx = remember(game.id, game.customCover, game.localCover, platform?.libretroSystem, officialName, indexVer) {
            if (game.customCover != null || (game.localCover != null && covers?.isManualCover(game.id) != true)) 0
            else covers?.knownIndex(game.id, platform)
        }
    }
    val apkPackage = remember(game.id) {
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) game.id.removePrefix(GameLauncher.APK_PREFIX) else null
    }
    val stableOnCandidateResolved: (Int) -> Unit = remember(game.id, covers, candidates, platform, offlineLocalFallback) {
        { idx: Int ->
            if (idx >= 0) {
                // 离线临时回退到本地图展示时，不覆盖已缓存的在线封面来源信息
                if (!(offlineLocalFallback && idx > 0)) {
                    covers?.rememberSuccess(game.id, candidates.getOrNull(idx))
                }
            } else {
                covers?.rememberFailure(game.id, platform)
            }
        }
    }
    val stableOnFileCandidateError: (File) -> Unit = remember(game.id, covers) {
        { failedFile: File ->
            if (covers?.isWebCacheFile(game.id, failedFile) == true) {
                covers.invalidateWebCacheFile(game.id, failedFile)
            }
        }
    }
    CoverCard(
        candidates = candidates,
        knownIndex = knownIdx,
        onCandidateResolved = stableOnCandidateResolved,
        onFileCandidateError = stableOnFileCandidateError,
        width = width,
        height = height,
        isFocused = isFocused,
        isFavorite = isFavorite,
        title = game.title,
        platformLabel = platform?.shortName,
        apkPackage = apkPackage,
        fastScrolling = fastScrolling,
        allowDiskDuringFastScroll = allowDiskDuringFastScroll,
        offscreenDefaultTitle = offscreenDefaultTitle,
        modifier = modifier,
        shapeOverride = shapeOverride,
        centeredTitle = centeredTitle,
        prominentFocus = prominentFocus,
        focusedScale = focusedScale,
        unfocusedScale = unfocusedScale,
        unfocusedAlpha = unfocusedAlpha,
        showPlatformBadge = showPlatformBadge,
        drawShadow = drawShadow,
        onClick = onClick,
        onLongPress = onLongPress,
    )
}

/**
 * 封面卡片：支持多候选封面自动回退 + 缓存命中下标 + 缺失封面时显示精致默认封面。
 *
 * 使用 pointerInput 响应点击和长按，避免抢占主页手柄按键焦点。
 */
@Composable
fun CoverCard(
    candidates: List<Any>,
    knownIndex: Int?,
    onCandidateResolved: ((Int) -> Unit)?,
    width: Dp,
    height: Dp,
    isFocused: Boolean,
    title: String,
    onFileCandidateError: ((File) -> Unit)? = null,
    isFavorite: Boolean = false,
    platformLabel: String? = null,
    apkPackage: String? = null,
    fastScrolling: Boolean = false,
    allowDiskDuringFastScroll: Boolean = false,
    offscreenDefaultTitle: Boolean = false,
    modifier: Modifier = Modifier,
    shapeOverride: Shape? = null,
    centeredTitle: Boolean = false,
    prominentFocus: Boolean = false,
    focusedScale: Float = 1f,
    unfocusedScale: Float = 1f,
    unfocusedAlpha: Float = 1f,
    showPlatformBadge: Boolean = true,
    drawShadow: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val shape = shapeOverride ?: DeckShapes.focusCard
    val context = LocalContext.current
    val view = LocalView.current
    val useFocusAnim = prominentFocus || focusedScale != 1f || unfocusedScale != 1f || unfocusedAlpha != 1f

    val cachedIcon = remember(apkPackage) { apkPackage?.let(AppIconCache::get) }
    val appIconBitmap by produceState(
        initialValue = cachedIcon,
        key1 = apkPackage,
        key2 = fastScrolling,
        key3 = allowDiskDuringFastScroll,
    ) {
        if (apkPackage == null) {
            value = null
        } else if (value == null && (!fastScrolling || allowDiskDuringFastScroll)) {
            value = withContext(Dispatchers.IO) { AppIconCache.loadSync(context, apkPackage) }
        }
    }

    val reduceMotion = LocalReduceMotion.current
    val scaleState = if (useFocusAnim) {
        animateFloatAsState(
            targetValue = if (isFocused) focusedScale else unfocusedScale,
            animationSpec = if (fastScrolling || reduceMotion) snap() else spring(dampingRatio = 0.65f, stiffness = 420f),
            label = "coverScale",
        )
    } else {
        null
    }
    val focusProgressState = if (useFocusAnim) {
        animateFloatAsState(
            targetValue = if (isFocused) 1f else 0f,
            animationSpec = if (fastScrolling || reduceMotion) snap() else spring(dampingRatio = 0.80f, stiffness = 480f),
            label = "coverFocus",
        )
    } else {
        null
    }
    // 焦点卡由外层 brandFocusRing 绘制渐变环 + 柔和彩色阴影；非焦点卡使用轻量缓存投影，减少动态效果时关闭阴影
    val cardShadowColor = DeckColors.CardShadow
    val drawSoftShadow = drawShadow && !isFocused && !fastScrolling && !reduceMotion && height >= 64.dp

    val density = LocalDensity.current
    val decodeBucketPx = remember(width, height, density.density) {
        val maxPx = with(density) { maxOf(width, height).toPx() }
        when {
            maxPx <= 220f -> 256
            maxPx <= 380f -> 384
            else -> 512
        }
    }

    var candidateIdx by remember(candidates, knownIndex) {
        mutableIntStateOf(
            when {
                candidates.isEmpty() -> -1
                knownIndex == -1 -> -1
                knownIndex != null && knownIndex in candidates.indices -> knownIndex
                else -> 0
            },
        )
    }
    val currentModel = candidates.getOrNull(candidateIdx)
    var loadedSuccess by remember(currentModel) { mutableStateOf(false) }
    val loadUnlockedHolder = remember(currentModel) { BooleanArray(1) }
    if (!fastScrolling) {
        loadUnlockedHolder[0] = true
    }
    val loadUnlocked = loadUnlockedHolder[0]

    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    val currentOnLongPress by androidx.compose.runtime.rememberUpdatedState(onLongPress)
    val hasClick = onClick != null
    val hasLongPress = onLongPress != null
    val gestureModifier = if (hasClick || hasLongPress) {
        Modifier.pointerInput(hasClick, hasLongPress) {
            detectTapGestures(
                onTap = {
                    currentOnClick?.let { cb ->
                        KeySound.playTouch(context)
                        cb()
                    }
                },
                onLongPress = if (hasLongPress) {
                    {
                        currentOnLongPress?.let { cb ->
                            triggerLongPressHaptic(context, view)
                            cb()
                        }
                    }
                } else null,
            )
        }
    } else {
        Modifier
    }

    val focusAnimModifier = if (useFocusAnim) {
        Modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                val s = scaleState?.value ?: 1f
                scaleX = s
                scaleY = s
            }
    } else {
        Modifier
    }

    val dimColor = DeckColors.Background
    val isCircle = shapeOverride == CircleShape
    val ringRadius = if (isCircle) maxOf(width, height) / 2 else FOCUS_CARD_RADIUS
    // 焦点卡外侧悬浮渐变收藏徽章（方形大卡）；其余情况徽章收在卡内
    val outerFavBadge = isFavorite && isFocused && !isCircle && width >= 120.dp && height >= 120.dp

    Box(
        modifier
            .then(focusAnimModifier)
            .size(width, height)
            .then(
                if (drawSoftShadow) {
                    Modifier.drawWithCache {
                        val cr = if (isCircle) size.minDimension / 2f else FOCUS_CARD_RADIUS.toPx()
                        val corner = CornerRadius(cr, cr)
                        val dy = 2.5.dp.toPx()
                        val col = cardShadowColor.copy(alpha = 0.22f)
                        onDrawBehind {
                            if (isCircle) {
                                drawCircle(col, radius = size.minDimension / 2f, center = Offset(size.width / 2f, size.height / 2f + dy))
                            } else {
                                drawRoundRect(col, topLeft = Offset(0f, dy), size = size, cornerRadius = corner)
                            }
                        }
                    }
                } else {
                    Modifier
                },
            )
            .brandFocusRing(isFocused, cornerRadius = ringRadius, glow = !fastScrolling),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(DeckColors.Surface)
                .then(gestureModifier)
                .drawWithContent {
                    drawContent()
                    val p = focusProgressState?.value ?: if (isFocused) 1f else 0f
                    if (useFocusAnim && unfocusedAlpha < 1f && p < 0.99f) {
                        val dimAlpha = (1f - unfocusedAlpha) * (1f - p)
                        if (dimAlpha > 0.01f) {
                            drawRect(dimColor.copy(alpha = dimAlpha * 0.58f))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // 底层：加载中或无封面时显示品牌渐变默认封面（原生安卓游戏则居中显示应用图标）
            if (!loadedSuccess) {
                DefaultCoverArt(
                    title = title,
                    platformLabel = platformLabel,
                    cardHeight = height,
                    centeredTitle = centeredTitle,
                    appIcon = appIconBitmap,
                    offscreenTitle = offscreenDefaultTitle,
                )
            }

            // 顶层：快滚期禁止联网与候选回退试探；已加载成功（loadedSuccess）后锁存策略，杜绝快滚起停触发双重 Coil 重载
            val isOnline = LocalIsOnline.current
            if (currentModel != null) {
                val latchedPolicy = remember(currentModel) { BooleanArray(2) }
                val effectiveAllowDisk: Boolean
                val effectiveAllowNetwork: Boolean
                if (loadedSuccess) {
                    effectiveAllowDisk = latchedPolicy[0]
                    effectiveAllowNetwork = latchedPolicy[1]
                } else {
                    effectiveAllowDisk = loadUnlocked || allowDiskDuringFastScroll
                    effectiveAllowNetwork = !fastScrolling && loadUnlocked && isOnline
                    latchedPolicy[0] = effectiveAllowDisk
                    latchedPolicy[1] = effectiveAllowNetwork
                }
                val request = remember(currentModel, decodeBucketPx, effectiveAllowDisk, effectiveAllowNetwork) {
                    ImageRequest.Builder(context)
                        .data(currentModel)
                        .size(decodeBucketPx, decodeBucketPx)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(if (effectiveAllowDisk) CachePolicy.ENABLED else CachePolicy.DISABLED)
                        .networkCachePolicy(if (effectiveAllowNetwork) CachePolicy.ENABLED else CachePolicy.DISABLED)
                        .build()
                }
                // LocalAsyncImageModelEqualityDelegate 已在 HomeScreen 根部统一提供 AllProperties，免去每张卡片单独包一层 Provider
                AsyncImage(
                    model = request,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        when (state) {
                            is AsyncImagePainter.State.Success -> {
                                loadedSuccess = true
                                if (loadUnlocked && !fastScrolling && knownIndex != candidateIdx) {
                                    onCandidateResolved?.invoke(candidateIdx)
                                }
                            }
                            is AsyncImagePainter.State.Error -> {
                                loadedSuccess = false
                                if (loadUnlocked && !fastScrolling) {
                                    val failedModel = currentModel
                                    val handledCorruptFile = failedModel is File && onFileCandidateError != null
                                    if (failedModel is File) {
                                        onFileCandidateError?.invoke(failedModel)
                                    }
                                    if (candidateIdx + 1 < candidates.size) {
                                        candidateIdx += 1
                                    } else {
                                        candidateIdx = -1
                                        val isOfflineHttpMiss = !isOnline &&
                                            failedModel is String &&
                                            (failedModel.startsWith("http://", ignoreCase = true) ||
                                                failedModel.startsWith("https://", ignoreCase = true))
                                        // 本地坏缓存文件交由 onFileCandidateError 摘除并触发重算候选，绝不顺带写入 -1 负缓存阻断在线回退
                                        if (knownIndex != -1 && !isOfflineHttpMiss && !handledCorruptFile) {
                                            onCandidateResolved?.invoke(-1)
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }
                    },
                )
            }

            // 左上角平台缩写角标（有封面图与无封面图统一显示，圆形气泡与极小卡片不显示）
            if (showPlatformBadge && !isCircle && !centeredTitle && !platformLabel.isNullOrBlank() && height >= 76.dp) {
                PlatformBadge(
                    label = platformLabel,
                    compact = height < 120.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(if (height < 120.dp) 5.dp else 7.dp),
                )
            }

            // 卡内收藏徽章：方形卡置右上角（避免遮挡底部标题）；气泡置正下方
            if (isFavorite && !outerFavBadge && width >= 54.dp && height >= 54.dp) {
                val compactBadge = width < 120.dp || height < 120.dp
                val badgeSize = if (compactBadge) 18.dp else 22.dp
                val badgeModifier = if (isCircle) {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (height * 0.07f).coerceAtLeast(5.dp))
                } else {
                    val pad = if (compactBadge) 5.dp else 7.dp
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = pad, end = pad)
                }
                BrandHeartBadge(size = badgeSize, shadow = false, modifier = badgeModifier)
            }
        }

        // 焦点大卡：右上角外悬浮渐变心形徽章（与设计稿一致）
        if (outerFavBadge) {
            BrandHeartBadge(
                size = 28.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = (-9).dp),
            )
        }
    }
}

private val FOCUS_CARD_RADIUS = 9.dp
private val SMALL_CARD_RADIUS = 7.dp

/** 统一平台角标：深梅色半透明圆角小牌 + 白色粗体缩写。 */
@Composable
fun PlatformBadge(
    label: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(if (compact) 4.dp else 5.dp))
            .background(PLATFORM_BADGE_BG)
            .padding(
                horizontal = if (compact) 4.dp else 5.5.dp,
                vertical = if (compact) 1.dp else 1.5.dp,
            ),
    ) {
        Text(
            text = label,
            fontFamily = DeckFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 7.5.sp else 9.5.sp,
            lineHeight = if (compact) 9.sp else 11.sp,
            letterSpacing = 0.3.sp,
            color = Color.White,
            maxLines = 1,
        )
    }
}

private val PLATFORM_BADGE_BG = Color(0x9E24172F)

/** 默认封面：主题品牌渐变底 + 白色手柄图标 + 游戏名（与网页控制台一致）。 */
@Composable
fun DefaultCoverArt(
    title: String,
    platformLabel: String?,
    cardHeight: Dp,
    centeredTitle: Boolean = false,
    appIcon: ImageBitmap? = null,
    offscreenTitle: Boolean = false,
) {
    val p = LocalDeckColors.current
    val tiny = cardHeight < 64.dp
    val compact = cardHeight < 120.dp

    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val dstW = w.roundToInt().coerceAtLeast(1)
                val dstH = h.roundToInt().coerceAtLeast(1)
                val maxSide = 280f
                val scale = if (w > maxSide || h > maxSide) maxSide / maxOf(w, h) else 1f
                val bw = (((w * scale).roundToInt().coerceAtLeast(32) + 7) and 7.inv())
                val bh = (((h * scale).roundToInt().coerceAtLeast(32) + 7) and 7.inv())
                val bmp = DefaultCoverArtCache.getOrCreate(
                    brandStart = p.brandStart,
                    brandMid = p.brandMid,
                    brandEnd = p.brandEnd,
                    widthPx = bw,
                    heightPx = bh,
                    tiny = tiny,
                    compact = compact,
                    centeredTitle = centeredTitle,
                    drawIcon = appIcon == null,
                    strokeDensity = density * scale,
                )
                val dstSize = IntSize(dstW, dstH)
                onDrawBehind {
                    drawImage(image = bmp, dstSize = dstSize)
                }
            },
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = title,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size((cardHeight * 0.48f).coerceIn(36.dp, 96.dp))
                    .clip(RoundedCornerShape(14.dp)),
            )
        }

        val titleLayerModifier = if (offscreenTitle) {
            Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        } else {
            Modifier
        }

        if (tiny) {
            // 极小列表缩略图仅展示居中图标，避免与外侧标题重叠
        } else if (centeredTitle) {
            // 气泡或极小卡片：居中简洁显示标题
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(cardHeight * 0.18f))
                Text(
                    text = title,
                    fontFamily = DeckFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 10.sp else 12.sp,
                    lineHeight = if (compact) 12.sp else 15.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = titleLayerModifier,
                )
            }
        } else {
            // 盒装封面风格：左上角平台角标由外层 CoverCard 统一绘制，此处仅保留底部游戏标题
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(if (compact) 6.dp else 10.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = title,
                    fontFamily = DeckFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 9.sp else 13.sp,
                    lineHeight = if (compact) 11.sp else 17.sp,
                    color = Color.White,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(titleLayerModifier),
                )
            }
        }
    }
}

private val DEFAULT_COVER_SCRIM_BRUSH = Brush.verticalGradient(
    0f to Color.Transparent,
    0.55f to Color.Transparent,
    1f to Color(0xFF24172F).copy(alpha = 0.28f),
)

internal data class DefaultCoverArtKey(
    val startArgb: Int,
    val midArgb: Int,
    val endArgb: Int,
    val widthPx: Int,
    val heightPx: Int,
    val flags: Int,
    val strokeBucket: Int = 10,
)

/**
 * 离屏预渲染默认封面底图（品牌渐变 + 右上光晕 + 线性手柄图标 + 底部暗角）。
 * 按 4MB 字节预算管理 LRU（自动区分描边密度桶），并接入 `PocketDeckApp.onTrimMemory` 回收。
 */
internal object DefaultCoverArtCache {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val lru = object : LruCache<DefaultCoverArtKey, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: DefaultCoverArtKey, value: ImageBitmap): Int =
            (value.width * value.height * 4).coerceAtLeast(1)
    }

    fun trimMemory() {
        synchronized(this) {
            lru.evictAll()
        }
    }

    internal fun cachedSizeBytes(): Int = synchronized(this) { lru.size() }

    internal fun buildKey(
        brandStart: Color,
        brandMid: Color,
        brandEnd: Color,
        widthPx: Int,
        heightPx: Int,
        tiny: Boolean,
        compact: Boolean,
        centeredTitle: Boolean,
        drawIcon: Boolean,
        strokeDensity: Float,
    ): DefaultCoverArtKey {
        val flags = (if (tiny) 1 else 0) or
            (if (compact) 2 else 0) or
            (if (centeredTitle) 4 else 0) or
            (if (drawIcon) 8 else 0)
        return DefaultCoverArtKey(
            startArgb = brandStart.toArgb(),
            midArgb = brandMid.toArgb(),
            endArgb = brandEnd.toArgb(),
            widthPx = widthPx,
            heightPx = heightPx,
            flags = flags,
            strokeBucket = (strokeDensity.coerceAtLeast(1f) * 10f).roundToInt(),
        )
    }

    fun getOrCreate(
        brandStart: Color,
        brandMid: Color,
        brandEnd: Color,
        widthPx: Int,
        heightPx: Int,
        tiny: Boolean,
        compact: Boolean,
        centeredTitle: Boolean,
        drawIcon: Boolean,
        strokeDensity: Float,
    ): ImageBitmap {
        val key = buildKey(
            brandStart = brandStart,
            brandMid = brandMid,
            brandEnd = brandEnd,
            widthPx = widthPx,
            heightPx = heightPx,
            tiny = tiny,
            compact = compact,
            centeredTitle = centeredTitle,
            drawIcon = drawIcon,
            strokeDensity = strokeDensity,
        )
        synchronized(this) {
            lru.get(key)?.let { return it }
        }
        val bitmap = ImageBitmap(widthPx, heightPx)
        val canvas = ComposeCanvas(bitmap)
        val drawScope = CanvasDrawScope()
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        val bg = Brush.linearGradient(
            listOf(brandStart, brandMid, brandEnd),
            start = Offset.Zero,
            end = Offset(w, h),
        )
        val iconScale = when {
            tiny -> 0.56f
            compact -> 0.40f
            else -> 0.34f
        }
        val gw = w * iconScale
        val gh = gw * 0.62f
        val gx = (w - gw) / 2f
        val gy = when {
            tiny -> (h - gh) / 2f
            centeredTitle -> h * 0.20f
            else -> h * 0.30f
        }
        val strokeW = (if (compact) 1.6f else 2.4f) * strokeDensity.coerceAtLeast(1f)
        val stroke = Stroke(width = strokeW)
        val bodyCorner = CornerRadius(gh * 0.48f, gh * 0.48f)
        val dpadCx = gx + gw * 0.28f
        val dpadCy = gy + gh * 0.50f
        val dpadArm = gh * 0.20f
        val btnR = gh * 0.085f
        val iconColor = Color.White.copy(alpha = 0.92f)
        drawScope.draw(
            density = Density(strokeDensity.coerceAtLeast(1f)),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(w, h),
        ) {
            drawRect(bg)
            drawCircle(Color.White.copy(alpha = 0.10f), radius = w * 0.55f, center = Offset(w * 0.92f, h * 0.08f))
            if (drawIcon) {
                drawRoundRect(iconColor, Offset(gx, gy), Size(gw, gh), bodyCorner, style = stroke)
                drawLine(iconColor, Offset(dpadCx - dpadArm, dpadCy), Offset(dpadCx + dpadArm, dpadCy), strokeW, cap = StrokeCap.Round)
                drawLine(iconColor, Offset(dpadCx, dpadCy - dpadArm), Offset(dpadCx, dpadCy + dpadArm), strokeW, cap = StrokeCap.Round)
                drawCircle(iconColor, btnR, Offset(gx + gw * 0.68f, gy + gh * 0.60f))
                drawCircle(iconColor, btnR, Offset(gx + gw * 0.79f, gy + gh * 0.42f))
            }
            drawRect(DEFAULT_COVER_SCRIM_BRUSH)
        }
        synchronized(this) {
            lru.put(key, bitmap)
        }
        return bitmap
    }
}
