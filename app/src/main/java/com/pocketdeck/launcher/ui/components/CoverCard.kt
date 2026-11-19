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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.brandFocusRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    allowDiskDuringFastScroll: Boolean = false,
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
        allowDiskDuringFastScroll = allowDiskDuringFastScroll,
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
    allowDiskDuringFastScroll: Boolean = false,
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
    // 无边框选中态仅通过柔和浮起阴影与微幅暗退突显焦点；快滚期间跳过离屏阴影重绘
    // 焦点卡：品牌色柔光浮起阴影（快滚期跳过）；其余小卡：粉调软阴影
    val elevation = when {
        isFocused && !fastScrolling -> if (prominentFocus) 12.dp else 10.dp
        isFocused -> 0.dp
        else -> 2.dp
    }
    val ambientShadowColor = if (isFocused) DeckColors.BrandEnd else DeckColors.CardShadow
    val spotShadowColor = if (isFocused) DeckColors.BrandMid else DeckColors.CardShadow

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
    val allowDisk = loadUnlocked || allowDiskDuringFastScroll

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
    val ringRadius = if (isCircle) maxOf(width, height) / 2 else if (isFocused) FOCUS_CARD_RADIUS else SMALL_CARD_RADIUS
    // 焦点卡外侧悬浮渐变收藏徽章（方形大卡）；其余情况徽章收在卡内
    val outerFavBadge = isFavorite && isFocused && !isCircle && width >= 120.dp && height >= 120.dp

    Box(
        modifier
            .then(focusAnimModifier)
            .size(width, height)
            .brandFocusRing(isFocused, cornerRadius = ringRadius),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .then(
                    if (elevation > 0.dp) {
                        Modifier.shadow(elevation, shape, ambientColor = ambientShadowColor, spotColor = spotShadowColor)
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
                )
            }

            // 顶层：快滚期只命中内存缓存（List 预览图支持盘缓存秒显，仅延后联网）；停下解锁后加载完整磁盘/网络封面
            val isOnline by NetworkStatus.online.collectAsState()
            if (currentModel != null) {
                val request = remember(currentModel, isOnline, decodeBucketPx, loadUnlocked, allowDisk) {
                    ImageRequest.Builder(context)
                        .data(currentModel)
                        .size(decodeBucketPx, decodeBucketPx)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(if (allowDisk) CachePolicy.ENABLED else CachePolicy.DISABLED)
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

            // 左上角平台缩写角标（有封面图与无封面图统一显示，圆形气泡与极小卡片不显示）
            if (!isCircle && !centeredTitle && !platformLabel.isNullOrBlank() && height >= 76.dp) {
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
) {
    val p = LocalDeckColors.current
    val compact = cardHeight < 120.dp

    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val bg = Brush.linearGradient(
                    listOf(p.brandStart, p.brandMid, p.brandEnd),
                    start = Offset.Zero,
                    end = Offset(w, h),
                )
                // 手柄图标几何（白色线性），仅在尺寸变化时重算
                val iconScale = if (compact) 0.40f else 0.34f
                val gw = w * iconScale
                val gh = gw * 0.62f
                val gx = (w - gw) / 2f
                val gy = if (centeredTitle) h * 0.20f else h * 0.30f
                val strokeW = (if (compact) 1.6f else 2.4f).dp.toPx()
                val stroke = Stroke(width = strokeW)
                val bodyCorner = CornerRadius(gh * 0.48f, gh * 0.48f)
                val dpadCx = gx + gw * 0.28f
                val dpadCy = gy + gh * 0.50f
                val dpadArm = gh * 0.20f
                val btnR = gh * 0.085f
                val iconColor = Color.White.copy(alpha = 0.92f)
                val drawIcon = appIcon == null
                onDrawBehind {
                    drawRect(bg)
                    // 右上角柔和光晕
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

        if (centeredTitle) {
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
                    modifier = Modifier.fillMaxWidth(),
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
