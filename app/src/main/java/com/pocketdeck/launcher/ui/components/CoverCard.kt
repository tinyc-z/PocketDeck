package com.pocketdeck.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.zIndex
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
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
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
import kotlin.math.abs

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
    val indexVer = covers?.indexVersion?.collectAsState()?.value ?: 0
    val candidates = remember(game.id, game.customCover, game.localCover, game.title, platform?.libretroSystem, officialName, indexVer) {
        covers?.candidates(game, platform, officialName)
            ?: listOfNotNull(game.customCover, game.localCover)
    }
    val knownIdx = remember(game.id, game.customCover, platform?.libretroSystem, indexVer) {
        if (game.customCover != null) 0 else covers?.knownIndex(game.id, platform)
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

    val appIconBitmap: ImageBitmap? = remember(apkPackage) {
        if (apkPackage == null) null
        else runCatching {
            context.packageManager.getApplicationIcon(apkPackage).toBitmap(192, 192).asImageBitmap()
        }.getOrNull()
    }

    val scale = if (useFocusAnim) {
        animateFloatAsState(
            targetValue = if (isFocused) focusedScale else unfocusedScale,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
            label = "coverScale",
        ).value
    } else {
        1f
    }
    val focusProgress = if (useFocusAnim) {
        animateFloatAsState(
            targetValue = if (isFocused) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.80f, stiffness = 480f),
            label = "coverFocus",
        ).value
    } else if (isFocused) {
        1f
    } else {
        0f
    }
    val dimAlpha = if (useFocusAnim && unfocusedAlpha < 1f && !isFocused) {
        (1f - unfocusedAlpha) * (1f - focusProgress)
    } else {
        0f
    }
    // 仅聚焦卡片绘制柔和阴影，避免大批量非聚焦卡片触发 GPU 阴影重绘与离屏渲染
    val elevation = if (isFocused) (if (prominentFocus) 10.dp else 8.dp) else 0.dp
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
    var loadedSuccess by remember(candidates, candidateIdx) { mutableStateOf(false) }

    val gestureModifier = if (onClick != null || onLongPress != null) {
        Modifier.pointerInput(onClick, onLongPress) {
            detectTapGestures(
                onTap = {
                    if (onClick != null) {
                        KeySound.playTouch(context)
                        onClick()
                    }
                },
                onLongPress = onLongPress?.let { cb ->
                    {
                        triggerLongPressHaptic(context, view)
                        cb()
                    }
                },
            )
        }
    } else {
        Modifier
    }

    val focusAnimModifier = if (useFocusAnim) {
        Modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    } else {
        Modifier
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
            .then(gestureModifier),
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

        // 顶层：尝试加载候选封面，404 自动试下一个；按分档像素尺寸解码以复用缓存
        val isOnline by NetworkStatus.online.collectAsState()
        val currentModel = candidates.getOrNull(candidateIdx)
        if (currentModel != null) {
            val request = remember(currentModel, isOnline, decodeBucketPx) {
                ImageRequest.Builder(context)
                    .data(currentModel)
                    .size(decodeBucketPx, decodeBucketPx)
                    .networkCachePolicy(if (isOnline) CachePolicy.ENABLED else CachePolicy.DISABLED)
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
                            if (knownIndex != candidateIdx) {
                                onCandidateResolved?.invoke(candidateIdx)
                            }
                        }
                        is AsyncImagePainter.State.Error -> {
                            loadedSuccess = false
                            if (candidateIdx + 1 < candidates.size) {
                                candidateIdx += 1
                            } else {
                                candidateIdx = -1
                                if (knownIndex != -1) {
                                    onCandidateResolved?.invoke(-1)
                                }
                            }
                        }
                        else -> Unit
                    }
                },
            )
        }

        if (dimAlpha > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(DeckColors.Background.copy(alpha = dimAlpha * 0.45f)),
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

        // 焦点描边画在最上层：使用浅色主题描边确保深/浅色封面都柔和清晰
        if (focusProgress > 0.01f) {
            if (prominentFocus) {
                val innerShape = if (shapeOverride == CircleShape) CircleShape else RoundedCornerShape(6.dp)
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(3.dp, DeckColors.CardBorder.copy(alpha = focusProgress), shape)
                        .padding(3.dp)
                        .border(2.dp, Color.White.copy(alpha = focusProgress), innerShape),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.5.dp, DeckColors.CardBorder.copy(alpha = focusProgress), shape),
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
    val compact = cardHeight < 120.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.top, palette.bottom),
                    start = Offset(0f, 0f),
                    end = Offset(400f, 600f),
                ),
            ),
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
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.32f),
                    ),
                ),
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

private data class CoverPalette(val top: Color, val bottom: Color)

private val DEFAULT_PALETTES = listOf(
    CoverPalette(Color(0xFFB71C1C), Color(0xFF6D131C)), // PocketDeck Ruby
    CoverPalette(Color(0xFF3949AB), Color(0xFF1E2758)), // Classic Indigo
    CoverPalette(Color(0xFF00796B), Color(0xFF00433B)), // Retro Teal
    CoverPalette(Color(0xFF5E35B1), Color(0xFF311B66)), // GameCube Violet
    CoverPalette(Color(0xFFD84315), Color(0xFF7A1E08)), // Famicom Vermilion
    CoverPalette(Color(0xFF37474F), Color(0xFF1A2329)), // Slate Charcoal
)
