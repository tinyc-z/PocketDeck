package com.pocketdeck.launcher.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(64)

    fun get(pkg: String): ImageBitmap? = cache.get(pkg)

    fun loadSync(context: Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        return runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(192, 192).asImageBitmap()
        }.getOrNull()?.also { cache.put(pkg, it) }
    }
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
    modifier: Modifier = Modifier,
    shapeOverride: Shape? = null,
    centeredTitle: Boolean = false,
    prominentFocus: Boolean = false,
    focusedScale: Float = 1f,
    unfocusedScale: Float = 1f,
    unfocusedAlpha: Float = 1f,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    var candidatesUnlocked by remember(game.id) { mutableStateOf(!fastScrolling) }
    if (!fastScrolling && !candidatesUnlocked) {
        candidatesUnlocked = true
    }

    val candidates: List<Any>
    val knownIdx: Int?
    if (!candidatesUnlocked) {
        candidates = remember(game.id, game.customCover, game.localCover) {
            covers?.fastMemoryCandidates(game)
                ?: listOfNotNull(game.customCover, game.localCover)
        }
        knownIdx = if (candidates.isNotEmpty()) 0 else null
    } else {
        val indexVer = covers?.indexVersion?.collectAsState()?.value ?: 0
        candidates = remember(game.id, game.customCover, game.localCover, game.title, platform?.libretroSystem, officialName, indexVer) {
            covers?.candidates(game, platform, officialName)
                ?: listOfNotNull(game.customCover, game.localCover)
        }
        knownIdx = remember(game.id, game.customCover, platform?.libretroSystem, indexVer) {
            if (game.customCover != null) 0 else covers?.knownIndex(game.id, platform)
        }
    }
    val apkPackage = remember(game.id) {
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) game.id.removePrefix(GameLauncher.APK_PREFIX) else null
    }
    CoverCard(
        candidates = candidates,
        knownIndex = knownIdx,
        onCandidateResolved = { idx ->
            if (idx >= 0) {
                covers?.rememberSuccess(game.id, candidates.getOrNull(idx))
            } else {
                covers?.rememberFailure(game.id, platform)
            }
        },
        width = width,
        height = height,
        isFocused = isFocused,
        isFavorite = isFavorite,
        title = game.title,
        platformLabel = platform?.shortName,
        apkPackage = apkPackage,
        fastScrolling = fastScrolling,
        modifier = modifier,
        shapeOverride = shapeOverride,
        centeredTitle = centeredTitle,
        prominentFocus = prominentFocus,
        focusedScale = focusedScale,
        unfocusedScale = unfocusedScale,
        unfocusedAlpha = unfocusedAlpha,
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
    isFavorite: Boolean = false,
    platformLabel: String? = null,
    apkPackage: String? = null,
    fastScrolling: Boolean = false,
    modifier: Modifier = Modifier,
    shapeOverride: Shape? = null,
    centeredTitle: Boolean = false,
    prominentFocus: Boolean = false,
    focusedScale: Float = 1f,
    unfocusedScale: Float = 1f,
    unfocusedAlpha: Float = 1f,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val shape = shapeOverride ?: if (isFocused) DeckShapes.focusCard else DeckShapes.smallCard
    val context = LocalContext.current
    val view = LocalView.current
    val useFocusAnim = prominentFocus || focusedScale != 1f || unfocusedScale != 1f || unfocusedAlpha != 1f

    val cachedIcon = remember(apkPackage) { apkPackage?.let(AppIconCache::get) }
    val appIconBitmap by produceState(initialValue = cachedIcon, key1 = apkPackage, key2 = fastScrolling) {
        if (apkPackage == null) {
            value = null
        } else if (value == null && !fastScrolling) {
            value = withContext(Dispatchers.IO) { AppIconCache.loadSync(context, apkPackage) }
        }
    }

    val scaleState = if (useFocusAnim) {
        animateFloatAsState(
            targetValue = if (isFocused) focusedScale else unfocusedScale,
            animationSpec = if (fastScrolling) snap() else spring(dampingRatio = 0.65f, stiffness = 420f),
            label = "coverScale",
        )
    } else {
        null
    }
    val focusProgressState = if (useFocusAnim) {
        animateFloatAsState(
            targetValue = if (isFocused) 1f else 0f,
            animationSpec = if (fastScrolling) snap() else spring(dampingRatio = 0.80f, stiffness = 480f),
            label = "coverFocus",
        )
    } else {
        null
    }
    // 仅在非快滚时为聚焦卡片绘制柔和阴影，避免高速连按滑动时触发离屏阴影重绘
    val elevation = if (isFocused && !fastScrolling) (if (prominentFocus) 10.dp else 8.dp) else 0.dp
    val shadowColor = if (isFocused) DeckColors.Ruby else Color.Black

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
    var loadUnlocked by remember(currentModel) { mutableStateOf(!fastScrolling) }
    if (!fastScrolling && !loadedSuccess && !loadUnlocked) {
        loadUnlocked = true
    }

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

    val cardBorderColor = DeckColors.CardBorder
    val dimColor = DeckColors.Background
    val cornerRadiusDp = when {
        shapeOverride == CircleShape -> null
        isFocused -> 9.dp
        else -> 7.dp
    }

    Box(
        modifier
            .then(focusAnimModifier)
            .size(width, height)
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(elevation, shape, ambientColor = shadowColor, spotColor = shadowColor)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(DeckColors.Surface)
            .then(gestureModifier)
            .drawWithContent {
                drawContent()
                val p = focusProgressState?.value ?: if (isFocused) 1f else 0f
                if (useFocusAnim && unfocusedAlpha < 1f && p < 0.99f) {
                    val dimAlpha = (1f - unfocusedAlpha) * (1f - p)
                    if (dimAlpha > 0.01f) {
                        drawRect(dimColor.copy(alpha = dimAlpha * 0.45f))
                    }
                }
                if (p > 0.01f) {
                    if (prominentFocus) {
                        val outerStroke = 3.dp.toPx()
                        val innerPad = 3.dp.toPx()
                        val innerStroke = 2.dp.toPx()
                        if (cornerRadiusDp == null) {
                            drawCircle(
                                color = cardBorderColor.copy(alpha = p),
                                radius = ((size.minDimension - outerStroke) / 2f).coerceAtLeast(0f),
                                style = Stroke(width = outerStroke),
                            )
                            val innerR = size.minDimension / 2f - innerPad - innerStroke / 2f
                            drawCircle(
                                color = Color.White.copy(alpha = p),
                                radius = innerR.coerceAtLeast(0f),
                                style = Stroke(width = innerStroke),
                            )
                        } else {
                            val outerCorner = (cornerRadiusDp.toPx() - outerStroke / 2f).coerceAtLeast(0f)
                            val h1 = outerStroke / 2f
                            drawRoundRect(
                                color = cardBorderColor.copy(alpha = p),
                                topLeft = Offset(h1, h1),
                                size = Size((size.width - outerStroke).coerceAtLeast(0f), (size.height - outerStroke).coerceAtLeast(0f)),
                                cornerRadius = CornerRadius(outerCorner, outerCorner),
                                style = Stroke(width = outerStroke),
                            )
                            val innerCorner = (6.dp.toPx() - innerStroke / 2f).coerceAtLeast(0f)
                            val h2 = innerPad + innerStroke / 2f
                            drawRoundRect(
                                color = Color.White.copy(alpha = p),
                                topLeft = Offset(h2, h2),
                                size = Size(
                                    (size.width - innerPad * 2f - innerStroke).coerceAtLeast(0f),
                                    (size.height - innerPad * 2f - innerStroke).coerceAtLeast(0f),
                                ),
                                cornerRadius = CornerRadius(innerCorner, innerCorner),
                                style = Stroke(width = innerStroke),
                            )
                        }
                    } else {
                        val strokeW = 2.5.dp.toPx()
                        val h = strokeW / 2f
                        if (cornerRadiusDp == null) {
                            drawCircle(
                                color = cardBorderColor.copy(alpha = p),
                                radius = ((size.minDimension - strokeW) / 2f).coerceAtLeast(0f),
                                style = Stroke(width = strokeW),
                            )
                        } else {
                            val cr = (cornerRadiusDp.toPx() - h).coerceAtLeast(0f)
                            drawRoundRect(
                                color = cardBorderColor.copy(alpha = p),
                                topLeft = Offset(h, h),
                                size = Size((size.width - strokeW).coerceAtLeast(0f), (size.height - strokeW).coerceAtLeast(0f)),
                                cornerRadius = CornerRadius(cr, cr),
                                style = Stroke(width = strokeW),
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 底层：加载中或无封面时显示精致默认封面（原生安卓游戏则居中显示应用图标）
        if (!loadedSuccess) {
            DefaultCoverArt(
                title = title,
                platformLabel = platformLabel,
                cardHeight = height,
                centeredTitle = centeredTitle,
                appIcon = appIconBitmap,
            )
        }

        // 顶层：快滚期只命中内存缓存（零磁盘解码/零联网）；停下解锁后按分档像素尺寸加载磁盘/网络封面
        val isOnline by NetworkStatus.online.collectAsState()
        if (currentModel != null) {
            val request = remember(currentModel, isOnline, decodeBucketPx, loadUnlocked) {
                ImageRequest.Builder(context)
                    .data(currentModel)
                    .size(decodeBucketPx, decodeBucketPx)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(if (loadUnlocked) CachePolicy.ENABLED else CachePolicy.DISABLED)
                    .networkCachePolicy(if (loadUnlocked && isOnline) CachePolicy.ENABLED else CachePolicy.DISABLED)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            loadedSuccess = true
                            if (loadUnlocked && knownIndex != candidateIdx) {
                                onCandidateResolved?.invoke(candidateIdx)
                            }
                        }
                        is AsyncImagePainter.State.Error -> {
                            loadedSuccess = false
                            if (loadUnlocked) {
                                if (candidateIdx + 1 < candidates.size) {
                                    candidateIdx += 1
                                } else {
                                    candidateIdx = -1
                                    if (knownIndex != -1) {
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

        // 收藏心形角标（固定显示在封面右下角；圆形气泡内缩在圆弧安全区内）
        if (isFavorite && width >= 54.dp && height >= 54.dp) {
            val isCircle = shapeOverride == CircleShape
            val compactBadge = width < 120.dp || height < 120.dp
            val badgeSize = if (compactBadge) 19.dp else 24.dp
            val heartSize = if (compactBadge) 11.dp else 14.dp
            val endPad = if (isCircle) (width * 0.135f) else if (compactBadge) 5.dp else 8.dp
            val bottomPad = if (isCircle) (height * 0.135f) else if (compactBadge) 5.dp else 8.dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = endPad, bottom = bottomPad)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(DeckColors.Background.copy(alpha = 0.88f))
                    .border(0.8.dp, DeckColors.DialogBorder.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                FavoriteHeartIcon(
                    filled = true,
                    color = DeckColors.Ruby,
                    size = heartSize,
                )
            }
        }
    }
}

/** 精致的默认封面：和谐渐变底色 + 几何装饰纹样 + 矢量手柄图标 + 平台角标 + 游戏名。 */
@Composable
fun DefaultCoverArt(
    title: String,
    platformLabel: String?,
    cardHeight: Dp,
    centeredTitle: Boolean = false,
    appIcon: ImageBitmap? = null,
) {
    val palette = remember(title) {
        val idx = abs(title.hashCode()) % DEFAULT_PALETTES.size
        DEFAULT_PALETTES[idx]
    }
    val bgBrush = remember(palette) {
        Brush.linearGradient(
            colors = listOf(palette.top, palette.bottom),
            start = Offset(0f, 0f),
            end = Offset(400f, 600f),
        )
    }
    val compact = cardHeight < 120.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(bgBrush),
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
        } else {
            // 几何装饰背景与复古手柄矢量图
            Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 右上角柔和同心圆光晕
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = w * 0.55f,
                center = Offset(w * 0.88f, h * 0.14f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = w * 0.34f,
                center = Offset(w * 0.88f, h * 0.14f),
            )

            // 居中偏上绘制极简手柄图标（非居中标题模式下）
            val iconScale = if (compact) 0.34f else 0.30f
            val gw = w * iconScale
            val gh = gw * 0.58f
            val gx = (w - gw) / 2f
            val gy = if (centeredTitle) h * 0.22f else h * 0.33f
            val strokeW = (if (compact) 1.5f else 2.2f).dp.toPx()
            val iconColor = Color.White.copy(alpha = if (centeredTitle) 0.24f else 0.34f)

            // 手柄机身
            drawRoundRect(
                color = iconColor,
                topLeft = Offset(gx, gy),
                size = Size(gw, gh),
                cornerRadius = CornerRadius(gh * 0.48f, gh * 0.48f),
                style = Stroke(width = strokeW),
            )
            // 左侧十字键
            val dpadCx = gx + gw * 0.28f
            val dpadCy = gy + gh * 0.50f
            val dpadArm = gh * 0.22f
            drawLine(iconColor, Offset(dpadCx - dpadArm, dpadCy), Offset(dpadCx + dpadArm, dpadCy), strokeW)
            drawLine(iconColor, Offset(dpadCx, dpadCy - dpadArm), Offset(dpadCx, dpadCy + dpadArm), strokeW)
            // 右侧 AB 键两点
            val btnR = gh * 0.09f
            drawCircle(iconColor, btnR, Offset(gx + gw * 0.68f, gy + gh * 0.58f))
            drawCircle(iconColor, btnR, Offset(gx + gw * 0.78f, gy + gh * 0.42f))
        }
        }

        // 底部柔和暗色渐变遮罩，衬托文字清晰可读
        Box(
            Modifier
                .fillMaxSize()
                .background(DEFAULT_COVER_SCRIM_BRUSH),
        )

        if (centeredTitle) {
            // 气泡或极小卡片：居中简洁显示标题
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    fontFamily = DeckFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 10.sp else 12.sp,
                    lineHeight = if (compact) 12.sp else 15.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            // 盒装封面风格：左上平台小胶囊 + 底部游戏标题
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(if (compact) 6.dp else 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                if (!platformLabel.isNullOrBlank() && cardHeight >= 76.dp) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.20f))
                            .padding(
                                horizontal = if (compact) 6.dp else 8.dp,
                                vertical = if (compact) 1.5.dp else 2.5.dp,
                            ),
                    ) {
                        Text(
                            text = platformLabel,
                            fontFamily = DeckFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 7.sp else 9.sp,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                } else {
                    Spacer(Modifier.height(1.dp))
                }

                Text(
                    text = title,
                    fontFamily = DeckFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 9.sp else 13.sp,
                    lineHeight = if (compact) 11.sp else 17.sp,
                    color = Color.White,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val DEFAULT_COVER_SCRIM_BRUSH = Brush.verticalGradient(
    0f to Color.Transparent,
    0.55f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.32f),
)

private data class CoverPalette(val top: Color, val bottom: Color)

private val DEFAULT_PALETTES = listOf(
    CoverPalette(Color(0xFFB71C1C), Color(0xFF6D131C)), // PocketDeck Ruby
    CoverPalette(Color(0xFF3949AB), Color(0xFF1E2758)), // Classic Indigo
    CoverPalette(Color(0xFF00796B), Color(0xFF00433B)), // Retro Teal
    CoverPalette(Color(0xFF5E35B1), Color(0xFF311B66)), // GameCube Violet
    CoverPalette(Color(0xFFD84315), Color(0xFF7A1E08)), // Famicom Vermilion
    CoverPalette(Color(0xFF37474F), Color(0xFF1A2329)), // Slate Charcoal
)
