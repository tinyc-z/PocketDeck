package com.pocketdeck.launcher.ui.components

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.pocketdeck.launcher.safeCoroutineHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.LocalDeckColors
import com.pocketdeck.launcher.ui.theme.LocalReduceMotion
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.NavBarStyle
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 顶栏：L1 徽标 | 可滚动标签行 | R1 徽标 | 时间 · 飞行模式 耳机 蓝牙 Wi-Fi 电池
 */
/**
 * 桌面状态栏与设置页头部共用的「后台刮削进度」胶囊：旋转图标 + 实时百分比，点击进入任务管理弹窗。
 */
@Composable
fun ScrapeStatusPill(
    percent: Int,
    onClick: () -> Unit,
    paused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val spinAngleState = if (!reduceMotion && !paused) {
        val inf = rememberInfiniteTransition(label = "scrape_spin")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "scrape_spin_angle",
        )
    } else null
    val accent = DeckColors.Ruby
    Row(
        modifier
            .clip(DeckShapes.tabPill)
            .background(DeckColors.AccentSoft)
            .soundClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DeckIcon(
            icon = DeckIcons.Refresh,
            size = 12.dp,
            tint = accent,
            // 延迟在 graphicsLayer 绘制阶段读取旋转角，实现 0 重组流畅旋转
            modifier = Modifier.graphicsLayer { rotationZ = spinAngleState?.value ?: 0f },
        )
        Text(
            text = "$percent%",
            style = DeckType.metaLabel.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
            color = accent,
            maxLines = 1,
        )
    }
}

/**
 * 悬浮磨砂岛胶囊表面（自动适配浅色 / 深色模式，零离屏 FBO 开销）。
 * 多层柔和投影 + 半透明毛玻璃渐变填充 + 顶部高光描边，用于顶栏与底栏悬浮岛。
 */
internal fun Modifier.glassDockCapsule(
    isDark: Boolean,
    surfaceColor: Color,
    backgroundColor: Color,
    reduceMotion: Boolean = false,
): Modifier = this
    .drawWithCache {
        val drawShadow = !reduceMotion && size.width > 0f && size.height > 0f
        val r = size.height / 2f
        val yOff = 3.dp.toPx()
        val maxAlpha = if (isDark) 0.24f else 0.085f
        val layers = if (drawShadow) {
            (3 downTo 1).map { step ->
                val spread = step * 2.4.dp.toPx()
                Triple(
                    Color.Black.copy(alpha = maxAlpha * (4 - step) / 3f),
                    Offset(-spread * 0.35f, yOff - spread * 0.15f),
                    Size(size.width + spread * 0.7f, size.height + spread * 0.55f) to
                        CornerRadius(r + spread * 0.35f, r + spread * 0.35f),
                )
            }
        } else {
            emptyList()
        }
        onDrawBehind {
            for ((color, topLeft, szCorner) in layers) {
                drawRoundRect(
                    color = color,
                    topLeft = topLeft,
                    size = szCorner.first,
                    cornerRadius = szCorner.second,
                )
            }
        }
    }
    .clip(CircleShape)
    .drawWithCache {
        val fillBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    surfaceColor.copy(alpha = 0.88f),
                    backgroundColor.copy(alpha = 0.80f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.82f),
                    Color.White.copy(alpha = 0.68f),
                ),
            )
        }
        val borderBrush = if (isDark) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.20f),
                    Color.White.copy(alpha = 0.07f),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.95f),
                    Color.White.copy(alpha = 0.55f),
                ),
            )
        }
        val strokeW = 1.dp.toPx()
        val halfStroke = strokeW / 2f
        val r = (size.height - strokeW).coerceAtLeast(0f) / 2f
        val borderCr = CornerRadius(r, r)
        val borderStroke = Stroke(width = strokeW)
        val borderTl = Offset(halfStroke, halfStroke)
        val borderSz = Size((size.width - strokeW).coerceAtLeast(0f), (size.height - strokeW).coerceAtLeast(0f))
        onDrawWithContent {
            drawRect(brush = fillBrush)
            drawContent()
            if (borderSz.width > 0f && borderSz.height > 0f) {
                drawRoundRect(
                    brush = borderBrush,
                    topLeft = borderTl,
                    size = borderSz,
                    cornerRadius = borderCr,
                    style = borderStroke,
                )
            }
        }
    }

@Composable
fun TopBar(
    tabs: List<String>,
    selectedIndex: Int,
    navBarStyle: NavBarStyle = NavBarStyle.GlassDock,
    is12h: Boolean = false,
    showClock: Boolean = true,
    showBattery: Boolean = true,
    showWifi: Boolean = true,
    showBluetooth: Boolean = true,
    showMobileSignal: Boolean = true,
    showHeadset: Boolean = true,
    showAirplane: Boolean = true,
    showDnd: Boolean = true,
    lanSharingActive: Boolean = false,
    lanWebActive: Boolean = false,
    lanPendingIncoming: Boolean = false,
    lanTransferWaiting: Boolean = false,
    lanTaskProgress: Float? = null,
    scrapeRunning: Boolean = false,
    scrapePercent: Int = 0,
    onScrapeBadgeClick: () -> Unit = {},
    onLanBadgeClick: () -> Unit = {},
    onWifiClick: () -> Unit = onLanBadgeClick,
    onTabClick: (Int) -> Unit = {},
    onPrevTab: () -> Unit = {},
    onNextTab: () -> Unit = {},
    rapidTabSwitchFlow: StateFlow<Boolean>? = null,
    animationsPaused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isGlassDock = navBarStyle == NavBarStyle.GlassDock

    Row(
        modifier
            .fillMaxWidth()
            .height(if (isGlassDock) 54.dp else 44.dp)
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (isGlassDock) 8.dp else 0.dp,
                bottom = if (isGlassDock) 2.dp else 0.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarTabsSection(
            tabs = tabs,
            selectedIndex = selectedIndex,
            navBarStyle = navBarStyle,
            onTabClick = onTabClick,
            rapidTabSwitchFlow = rapidTabSwitchFlow,
            modifier = Modifier.weight(1f),
        )

        TopBarStatusCluster(
            navBarStyle = navBarStyle,
            is12h = is12h,
            showClock = showClock,
            showBattery = showBattery,
            showWifi = showWifi,
            showBluetooth = showBluetooth,
            showMobileSignal = showMobileSignal,
            showHeadset = showHeadset,
            showAirplane = showAirplane,
            showDnd = showDnd,
            lanSharingActive = lanSharingActive,
            lanWebActive = lanWebActive,
            lanPendingIncoming = lanPendingIncoming,
            lanTransferWaiting = lanTransferWaiting,
            lanTaskProgress = lanTaskProgress,
            scrapeRunning = scrapeRunning,
            scrapePercent = scrapePercent,
            animationsPaused = animationsPaused,
            onScrapeBadgeClick = onScrapeBadgeClick,
            onLanBadgeClick = onLanBadgeClick,
            onWifiClick = onWifiClick,
        )
    }
}

/** 独立的顶部标签栏重组边界：时钟、电量、Wi-Fi RSSI、刮削进度等状态栏刷新绝不会触发标签行重组。 */
@Composable
private fun TopBarTabsSection(
    tabs: List<String>,
    selectedIndex: Int,
    navBarStyle: NavBarStyle,
    onTabClick: (Int) -> Unit,
    rapidTabSwitchFlow: StateFlow<Boolean>?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceMotion = LocalReduceMotion.current
    val rapidTabSwitch = rapidTabSwitchFlow?.collectAsState()?.value ?: false
    val noTabAnim = reduceMotion || rapidTabSwitch
    val scrollState = rememberScrollState()
    val tabBounds = remember(tabs, navBarStyle) { mutableStateMapOf<Int, Pair<Float, Float>>() }
    var pillHeightPx by remember(navBarStyle) { mutableFloatStateOf(0f) }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val pillX = remember { Animatable(0f) }
    val pillW = remember { Animatable(0f) }
    var pillInitialized by remember(navBarStyle) { mutableStateOf(false) }

    val safeSelected = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val targetBounds = tabBounds[safeSelected]

    LaunchedEffect(safeSelected, targetBounds, viewportWidthPx, noTabAnim) {
        val (tx, tw) = targetBounds ?: return@LaunchedEffect
        val scrollTarget = if (viewportWidthPx > 0) {
            (tx + tw / 2f - viewportWidthPx / 2f).roundToInt().coerceIn(0, scrollState.maxValue)
        } else {
            null
        }
        if (!pillInitialized || pillW.value <= 0f || noTabAnim) {
            pillX.snapTo(tx)
            pillW.snapTo(tw)
            pillInitialized = true
            if (scrollTarget != null) scrollState.scrollTo(scrollTarget)
        } else {
            val pillSpec = spring<Float>(dampingRatio = 0.80f, stiffness = 560f)
            coroutineScope {
                launch { pillX.animateTo(tx, pillSpec) }
                launch { pillW.animateTo(tw, pillSpec) }
                if (scrollTarget != null) {
                    launch {
                        scrollState.animateScrollTo(
                            scrollTarget,
                            tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        )
                    }
                }
            }
        }
    }

    val isGlassDock = navBarStyle == NavBarStyle.GlassDock
    val palette = LocalDeckColors.current
    val brandStart = palette.brandStart
    val brandMid = palette.brandMid
    val brandEnd = palette.brandEnd
    val tabGlow = palette.brandEnd
    val tabSelectedStyle = DeckType.tabSelected
    val tabInactiveStyle = DeckType.tabInactive.copy(fontWeight = FontWeight.SemiBold)
    val fadeLeft by remember { derivedStateOf { !reduceMotion && scrollState.value > 2 } }
    val fadeRight by remember { derivedStateOf { !reduceMotion && scrollState.value < scrollState.maxValue - 2 } }

    // 可滚动标签行 + 动态滑动缩放的渐变选中胶囊（减少动态效果时跳过离屏渐隐 FBO 与 3 层辉光）
    val tabsViewport: @Composable (Modifier) -> Unit = { vpMod ->
        Box(
            modifier = vpMod
                .fillMaxHeight()
                .onSizeChanged { viewportWidthPx = it.width }
                .graphicsLayer(
                    compositingStrategy = if (fadeLeft || fadeRight) CompositingStrategy.Offscreen else CompositingStrategy.Auto,
                )
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    if (w > 0f && (fadeLeft || fadeRight)) {
                        val fadePx = if (isGlassDock) 28.dp.toPx() else 20f
                        val fadeFrac = (fadePx / w).coerceIn(0.01f, 0.45f)
                        val leftColor = if (fadeLeft) Color.Transparent else Color.Black
                        val rightColor = if (fadeRight) Color.Transparent else Color.Black
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to leftColor,
                                fadeFrac to Color.Black,
                                1f - fadeFrac to Color.Black,
                                1f to rightColor,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
                    .drawBehind {
                        val w = if (noTabAnim && targetBounds != null) targetBounds.second else pillW.value
                        val h = pillHeightPx
                        if (w > 0f && h > 0f) {
                            val x = if (noTabAnim && targetBounds != null) targetBounds.first else pillX.value
                            val y = (size.height - h) / 2f
                            val r = h / 2f
                            if (!reduceMotion) {
                                val yOff = 2.dp.toPx()
                                // 柔和彩色投影辉光（无离屏开销）
                                for (step in 3 downTo 1) {
                                    val spread = step * 2.2.dp.toPx()
                                    drawRoundRect(
                                        color = tabGlow.copy(alpha = 0.08f * (4 - step) / 3f),
                                        topLeft = Offset(x - spread * 0.45f, y + yOff - spread * 0.25f),
                                        size = Size(w + spread * 0.9f, h + spread * 0.8f),
                                        cornerRadius = CornerRadius(r + spread * 0.4f, r + spread * 0.4f),
                                    )
                                }
                            }
                            drawRoundRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(brandStart, brandMid, brandEnd),
                                    start = Offset(x, y),
                                    end = Offset(x + w, y + h),
                                ),
                                topLeft = Offset(x, y),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(r, r),
                            )
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                val inactiveTabColor = if (isGlassDock) {
                    palette.textPrimary.copy(alpha = if (palette.isDark) 0.76f else 0.78f)
                } else {
                    DeckColors.TextLabel
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isGlassDock) 2.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { i, tab ->
                        val selected = i == safeSelected
                        val targetTextColor = if (selected) Color.White else inactiveTabColor
                        val textColorState = if (noTabAnim) {
                            null
                        } else {
                            animateColorAsState(
                                targetValue = targetTextColor,
                                animationSpec = tween(durationMillis = 180),
                                label = "tabTextColor",
                            )
                        }
                        Box(
                            Modifier
                                .onPlaced { coords ->
                                    val left = coords.positionInParent().x
                                    val width = coords.size.width.toFloat()
                                    val prev = tabBounds[i]
                                    if (prev == null || kotlin.math.abs(prev.first - left) > 0.5f || kotlin.math.abs(prev.second - width) > 0.5f) {
                                        tabBounds[i] = left to width
                                    }
                                    val h = coords.size.height.toFloat()
                                    if (h > 0f && kotlin.math.abs(pillHeightPx - h) > 0.5f) {
                                        pillHeightPx = h
                                    }
                                }
                                .clip(CircleShape)
                                .clickable {
                                    KeySound.playTabSwitch(context)
                                    onTabClick(i)
                                }
                                .padding(
                                    horizontal = if (isGlassDock) 13.dp else 12.dp,
                                    vertical = if (isGlassDock) 5.dp else 4.dp,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 延迟至绘制期通过 ColorProducer 读取颜色动画值，切 Tab 期间实现 0 重组文字变色
                            BasicText(
                                text = tab,
                                style = if (selected) tabSelectedStyle else tabInactiveStyle,
                                color = ColorProducer { textColorState?.value ?: targetTextColor },
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
        }
    }

    if (isGlassDock) {
        Box(
            modifier = modifier.fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .glassDockCapsule(
                        isDark = palette.isDark,
                        surfaceColor = palette.surface,
                        backgroundColor = palette.background,
                        reduceMotion = reduceMotion,
                    )
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                tabsViewport(Modifier)
            }
        }
    } else {
        tabsViewport(modifier)
    }
}

/**
 * 独立的状态栏集群重组边界：
 * - 全部系统广播与回调（Wi-Fi RSSI / NetworkCallback / AudioDeviceCallback / ContentObserver）
 *   一律切到 `statusScope (Dispatchers.IO)` 执行 Binder IPC，主线程仅接收轻量结果；
 * - 状态变化仅重组状态集群自身，不波及左侧标签行与整条 TopBar。
 */
@Composable
private fun TopBarStatusCluster(
    navBarStyle: NavBarStyle,
    is12h: Boolean,
    showClock: Boolean,
    showBattery: Boolean,
    showWifi: Boolean,
    showBluetooth: Boolean,
    showMobileSignal: Boolean,
    showHeadset: Boolean,
    showAirplane: Boolean,
    showDnd: Boolean,
    lanSharingActive: Boolean,
    lanWebActive: Boolean,
    lanPendingIncoming: Boolean,
    lanTransferWaiting: Boolean,
    lanTaskProgress: Float?,
    scrapeRunning: Boolean,
    scrapePercent: Int,
    animationsPaused: Boolean,
    onScrapeBadgeClick: () -> Unit,
    onLanBadgeClick: () -> Unit,
    onWifiClick: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reduceMotion = LocalReduceMotion.current
    val isGlassDock = navBarStyle == NavBarStyle.GlassDock
    val palette = LocalDeckColors.current

    var timeText by remember { mutableStateOf("") }
    var batteryPct by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var wifiLevel by remember { mutableIntStateOf(-1) } // -1=off/disconnected, 0-4=signal level
    var btEnabled by remember { mutableStateOf(false) }
    var headsetConnected by remember { mutableStateOf(false) }
    var airplaneMode by remember { mutableStateOf(false) }
    var dndEnabled by remember { mutableStateOf(false) }
    var mobileLevel by remember { mutableIntStateOf(-1) } // -1=no SIM/no service, 0-4=signal level

    DisposableEffect(is12h, lifecycleOwner) {
        val mainHandler = Handler(Looper.getMainLooper())
        val fmt = DateTimeFormatter.ofPattern(if (is12h) "h:mm" else "H:mm", Locale.getDefault())
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val disposed = AtomicBoolean(false)
        val statusScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + safeCoroutineHandler("TopBarStatus"))

        timeText = LocalTime.now().format(fmt)

        fun syncAllStatusAsync(excludingLostNetwork: Network? = null) {
            statusScope.launch {
                if (disposed.get()) return@launch
                val wifi = readWifiLevel(context, excludingLostNetwork)
                val bt = readBluetoothEnabled(context)
                val headset = readWiredHeadsetConnected(audioManager)
                val airplane = readAirplaneMode(context)
                val dnd = readDndEnabled(context)
                val mobile = readMobileSignalLevel(context)
                val battery = runCatching {
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                }.getOrNull()
                val nowStr = LocalTime.now().format(fmt)
                mainHandler.post {
                    if (disposed.get()) return@post
                    timeText = nowStr
                    wifiLevel = wifi
                    btEnabled = bt
                    headsetConnected = headset
                    airplaneMode = airplane
                    dndEnabled = dnd
                    mobileLevel = mobile
                    if (battery != null && battery in 0..100) batteryPct = battery
                }
            }
        }

        fun refreshWifiAsync(excludingNetwork: Network? = null) {
            statusScope.launch {
                if (disposed.get()) return@launch
                val wifi = readWifiLevel(context, excludingNetwork)
                mainHandler.post { if (!disposed.get()) wifiLevel = wifi }
            }
        }

        fun refreshHeadsetAsync() {
            statusScope.launch {
                if (disposed.get()) return@launch
                val connected = readWiredHeadsetConnected(audioManager)
                mainHandler.post { if (!disposed.get()) headsetConnected = connected }
            }
        }

        fun refreshDndAsync() {
            statusScope.launch {
                if (disposed.get()) return@launch
                val dnd = readDndEnabled(context)
                mainHandler.post { if (!disposed.get()) dndEnabled = dnd }
            }
        }

        fun refreshSettingsAsync() {
            statusScope.launch {
                if (disposed.get()) return@launch
                val air = readAirplaneMode(context)
                val dnd = readDndEnabled(context)
                val wifi = readWifiLevel(context)
                mainHandler.post {
                    if (disposed.get()) return@post
                    airplaneMode = air
                    dndEnabled = dnd
                    wifiLevel = wifi
                }
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                timeText = LocalTime.now().format(fmt)
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        intent.extras?.let { b ->
                            val level = b.getInt(BatteryManager.EXTRA_LEVEL, -1)
                            val scale = b.getInt(BatteryManager.EXTRA_SCALE, 100)
                            if (level >= 0 && scale > 0) batteryPct = level * 100 / scale
                            val status = b.getInt(BatteryManager.EXTRA_STATUS, -1)
                            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == BatteryManager.BATTERY_STATUS_FULL
                        }
                    }
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                        if (state == WifiManager.WIFI_STATE_ENABLED) {
                            refreshWifiAsync()
                        } else {
                            wifiLevel = -1
                        }
                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION,
                    WifiManager.RSSI_CHANGED_ACTION -> {
                        refreshWifiAsync()
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        btEnabled = state == BluetoothAdapter.STATE_ON
                    }
                    AudioManager.ACTION_HEADSET_PLUG -> {
                        val plugged = intent.getIntExtra("state", 0) == 1
                        if (plugged) {
                            headsetConnected = true
                        } else {
                            refreshHeadsetAsync()
                        }
                    }
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                        if (intent.hasExtra("state")) {
                            airplaneMode = intent.getBooleanExtra("state", false)
                            refreshWifiAsync()
                        } else {
                            refreshSettingsAsync()
                        }
                    }
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                        refreshDndAsync()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        val wifiCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refreshWifiAsync()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                refreshWifiAsync()
            }

            override fun onLost(network: Network) {
                refreshWifiAsync(excludingNetwork = network)
            }
        }
        val wifiCallbackRegistered = AtomicBoolean(false)

        val audioCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                refreshHeadsetAsync()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                refreshHeadsetAsync()
            }
        }

        val settingsObserver = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                refreshSettingsAsync()
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncAllStatusAsync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        // 首帧完成绘制后再异步批量读取系统状态并挂载回调，避免冷启首帧触发主线程 Binder IPC 风暴
        syncAllStatusAsync()
        mainHandler.post {
            if (disposed.get()) return@post
            runCatching {
                val req = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                connectivityManager?.registerNetworkCallback(req, wifiCallback)
                if (connectivityManager != null) wifiCallbackRegistered.set(true)
            }
            runCatching { audioManager?.registerAudioDeviceCallback(audioCallback, mainHandler) }
            runCatching {
                context.contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
                    false,
                    settingsObserver,
                )
                context.contentResolver.registerContentObserver(
                    Settings.Global.getUriFor("zen_mode"),
                    false,
                    settingsObserver,
                )
            }
        }

        onDispose {
            disposed.set(true)
            statusScope.cancel()
            runCatching { context.unregisterReceiver(receiver) }
            if (wifiCallbackRegistered.get()) {
                runCatching { connectivityManager?.unregisterNetworkCallback(wifiCallback) }
            }
            runCatching { audioManager?.unregisterAudioDeviceCallback(audioCallback) }
            runCatching { context.contentResolver.unregisterContentObserver(settingsObserver) }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    val hasLanBadge = lanSharingActive || lanWebActive || lanPendingIncoming || lanTransferWaiting || lanTaskProgress != null
    val hasCoreStatusItems = showClock || showWifi || showBattery ||
        (showDnd && dndEnabled) || (showAirplane && airplaneMode) ||
        (showHeadset && headsetConnected) || (showBluetooth && btEnabled) ||
        (showMobileSignal && mobileLevel >= 0)
    val hasAnyStatusItem = scrapeRunning || hasLanBadge || hasCoreStatusItems

    // 状态栏：支持刮削进度 / 快传 / Web 与系统状态图标动态伸缩
    val statusTint = DeckColors.TextPrimary
    val statusBg = if (isGlassDock) {
        if (palette.isDark) palette.surface else Color.White
    } else {
        DeckColors.Background
    }
    val statusInactive = DeckColors.TextSecondary.copy(alpha = 0.28f)
    val statusSlash = DeckColors.TextSecondary.copy(alpha = 0.72f)
    val statusGap = if (isGlassDock) 6.dp else 5.dp
    val hasRightOfScrape = hasLanBadge || hasCoreStatusItems
    val hasRightOfLan = hasCoreStatusItems

    val statusContent: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 后台刮削任务旋转图标 + 进度百分比胶囊（平滑展开宽度并推开胶囊，避免右端裁切）
            StatusSlotVisibility(
                visible = scrapeRunning,
                reduceMotion = reduceMotion,
                trailingGap = if (hasRightOfScrape) statusGap else 0.dp,
            ) {
                ScrapeStatusPill(
                    percent = scrapePercent,
                    onClick = onScrapeBadgeClick,
                    paused = animationsPaused,
                )
            }

            // 局域网快传 / Web 管理与任务进度提醒（平滑横向扩缩 + 轻弹缩放，自然撑开状态岛）
            val hasTask = lanTaskProgress != null
            var lastShowShare by remember { mutableStateOf(false) }
            var lastShowWeb by remember { mutableStateOf(false) }
            val curShowTextOnly = lanPendingIncoming || lanTransferWaiting
            val curShowShare = lanSharingActive || (hasTask && !lanWebActive)
            val curShowWeb = lanWebActive
            if (hasLanBadge && !curShowTextOnly) {
                lastShowShare = curShowShare
                lastShowWeb = curShowWeb
            }
            StatusSlotVisibility(
                visible = hasLanBadge,
                reduceMotion = reduceMotion,
                trailingGap = if (hasRightOfLan) statusGap else 0.dp,
            ) {
                val showTextOnly = curShowTextOnly
                val isDarkTheme = DeckColors.isDark
                val targetProgress = (lanTaskProgress ?: 0f).coerceIn(0f, 1f)
                val animProgressState = if (reduceMotion) {
                    null
                } else {
                    animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = tween(durationMillis = 200),
                        label = "lan_task_progress",
                    )
                }
                val isColored = lanPendingIncoming || hasTask || lanTransferWaiting
                val accent = when {
                    lanPendingIncoming -> DeckColors.Ruby
                    hasTask || lanTransferWaiting -> DeckColors.StatusInfo
                    else -> statusTint.copy(alpha = 0.85f)
                }
                val bgTint = when {
                    !isColored -> statusTint.copy(alpha = if (isDarkTheme) 0.11f else 0.07f)
                    !showTextOnly && hasTask -> accent.copy(alpha = if (isDarkTheme) 0.18f else 0.12f)
                    else -> accent.copy(alpha = if (isDarkTheme) 0.22f else 0.14f)
                }

                Row(
                    Modifier
                        .clip(DeckShapes.tabPill)
                        .background(bgTint)
                        .drawBehind {
                            val progress = animProgressState?.value ?: targetProgress
                            if (!showTextOnly && hasTask && progress > 0f) {
                                drawRect(
                                    color = accent.copy(alpha = if (isDarkTheme) 0.36f else 0.25f),
                                    size = Size(size.width * progress, size.height),
                                )
                            }
                        }
                        .soundClickable(onClick = onLanBadgeClick)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showTextOnly) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(accent),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(
                                if (lanPendingIncoming) R.string.lan_status_pending else R.string.lan_status_waiting,
                            ),
                            style = DeckType.metaLabel.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                            color = accent,
                            maxLines = 1,
                        )
                    } else {
                        val showShareLogo = if (hasLanBadge) curShowShare else lastShowShare
                        val showWebLogo = if (hasLanBadge) curShowWeb else lastShowWeb
                        StatusSlotVisibility(
                            visible = showShareLogo,
                            reduceMotion = reduceMotion,
                            trailingGap = if (showWebLogo) 5.dp else 0.dp,
                        ) {
                            Box(
                                Modifier
                                    .size(13.dp)
                                    .drawBehind { drawQuickShareLogo(accent) },
                            )
                        }
                        StatusSlotVisibility(
                            visible = showWebLogo,
                            reduceMotion = reduceMotion,
                            trailingGap = 0.dp,
                        ) {
                            Box(
                                Modifier
                                    .size(13.dp)
                                    .drawBehind { drawWebGlobeLogo(accent) },
                            )
                        }
                    }
                }
            }

            // 其余状态栏核心项（时钟 / 勿扰 / 飞行 / 耳机 / 蓝牙 / 信号 / Wi-Fi / 电池）
            if (!isGlassDock && showClock) {
                Text(timeText, style = DeckType.statusInfo.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = statusTint)
                val hasRightOfClassicClock = (showDnd && dndEnabled) || (showAirplane && airplaneMode) ||
                    (showHeadset && headsetConnected) || (showBluetooth && btEnabled) ||
                    (showMobileSignal && mobileLevel >= 0) || showWifi || showBattery
                if (hasRightOfClassicClock) Spacer(Modifier.width(statusGap))
            }

            val hasTrailingCore = showWifi || showBattery || (isGlassDock && showClock)
            val hasAfterDnd = (showAirplane && airplaneMode) || (showHeadset && headsetConnected) ||
                (showBluetooth && btEnabled) || (showMobileSignal && mobileLevel >= 0) || hasTrailingCore
            val hasAfterAirplane = (showHeadset && headsetConnected) ||
                (showBluetooth && btEnabled) || (showMobileSignal && mobileLevel >= 0) || hasTrailingCore
            val hasAfterHeadset = (showBluetooth && btEnabled) || (showMobileSignal && mobileLevel >= 0) || hasTrailingCore
            val hasAfterBluetooth = (showMobileSignal && mobileLevel >= 0) || hasTrailingCore

            StatusSlotVisibility(
                visible = showDnd && dndEnabled,
                reduceMotion = reduceMotion,
                trailingGap = if (hasAfterDnd) statusGap else 0.dp,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .drawBehind { drawDndIcon(statusTint, statusBg) }
                )
            }

            StatusSlotVisibility(
                visible = showAirplane && airplaneMode,
                reduceMotion = reduceMotion,
                trailingGap = if (hasAfterAirplane) statusGap else 0.dp,
            ) {
                Box(
                    Modifier
                        .size(15.dp)
                        .drawBehind { drawAirplaneIcon(statusTint) }
                )
            }

            StatusSlotVisibility(
                visible = showHeadset && headsetConnected,
                reduceMotion = reduceMotion,
                trailingGap = if (hasAfterHeadset) statusGap else 0.dp,
            ) {
                Box(
                    Modifier
                        .size(15.dp)
                        .drawBehind { drawHeadphoneIcon(statusTint) }
                )
            }

            StatusSlotVisibility(
                visible = showBluetooth && btEnabled,
                reduceMotion = reduceMotion,
                trailingGap = if (hasAfterBluetooth) statusGap else 0.dp,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .drawBehind { drawBluetoothIcon(statusTint) }
                )
            }

            StatusSlotVisibility(
                visible = showMobileSignal && mobileLevel >= 0,
                reduceMotion = reduceMotion,
                trailingGap = if (hasTrailingCore) statusGap else 0.dp,
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .drawBehind {
                            drawMobileSignalIcon(
                                level = mobileLevel,
                                activeColor = statusTint,
                                inactiveColor = statusInactive,
                            )
                        }
                )
            }

            // Wi-Fi 图标（点击可快捷呼出局域网服务与快传控制面板）
            if (showWifi) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .soundClickable(onClick = onWifiClick)
                        .padding(horizontal = 3.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .drawBehind {
                                drawWifiIcon(
                                    level = wifiLevel,
                                    activeColor = statusTint,
                                    inactiveColor = statusInactive,
                                    slashColor = statusSlash,
                                )
                            },
                    )
                }
                if (showBattery || (isGlassDock && showClock)) Spacer(Modifier.width(statusGap))
            }

            // 电池图标
            if (showBattery) {
                BatteryIcon(
                    percent = batteryPct,
                    charging = isCharging,
                    modifier = Modifier.size(width = 28.dp, height = 14.dp),
                )
                if (isGlassDock && showClock) Spacer(Modifier.width(statusGap))
            }

            // 悬浮磨砂岛下时钟稳固锚定在最右端，左侧插入快传/刮削等图标时胶囊自然向左平滑撑开
            if (isGlassDock && showClock) {
                Text(timeText, style = DeckType.statusInfo.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = statusTint)
            }
        }
    }

    if (isGlassDock) {
        if (hasAnyStatusItem) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .glassDockCapsule(
                        isDark = palette.isDark,
                        surfaceColor = palette.surface,
                        backgroundColor = palette.background,
                        reduceMotion = reduceMotion,
                    )
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                statusContent()
            }
        }
    } else {
        Spacer(Modifier.width(24.dp))
        statusContent()
    }
}

/**
 * 状态栏动态项平滑插入/收起容器：
 * 以子项自身横向扩缩（expandHorizontally + 轻弹缩放 + 淡入）实时驱动外层磨砂胶囊自然向左撑开，
 * 避免对父容器使用 animateContentSize 导致的右侧时钟裁切与突变感。
 */
@Composable
private fun StatusSlotVisibility(
    visible: Boolean,
    reduceMotion: Boolean,
    trailingGap: Dp,
    content: @Composable () -> Unit,
) {
    if (reduceMotion) {
        if (visible) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                content()
                if (trailingGap > 0.dp) Spacer(Modifier.width(trailingGap))
            }
        }
        return
    }
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(
            expandFrom = Alignment.Start,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        ) + fadeIn(
            animationSpec = tween(durationMillis = 180),
        ) + scaleIn(
            initialScale = 0.80f,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        ),
        exit = shrinkHorizontally(
            shrinkTowards = Alignment.Start,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 460f),
        ) + fadeOut(
            animationSpec = tween(durationMillis = 130),
        ) + scaleOut(
            targetScale = 0.80f,
            animationSpec = tween(durationMillis = 130),
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            content()
            if (trailingGap > 0.dp) Spacer(Modifier.width(trailingGap))
        }
    }
}

/**
 * 电池图标：Android 扁平风格。
 * 横向矩形 + 右端小正极凸起 + 内部纯色填充条。
 */
@Composable
private fun BatteryIcon(
    percent: Int,
    charging: Boolean,
    modifier: Modifier = Modifier,
) {
    val fillColor = when {
        charging -> DeckColors.StatusSuccess
        percent <= 20 -> DeckColors.StatusDanger
        else -> DeckColors.TextPrimary
    }
    val outlineColor = DeckColors.TextPrimary

    Box(modifier.drawBehind {
        val w = size.width
        val h = size.height
        val stroke = 1.6f.dp.toPx()
        val tipW = 2.dp.toPx()
        val tipH = h * 0.4f
        val bodyW = w - tipW - 1.dp.toPx()
        val r = 2.dp.toPx()   // 安卓扁平：小圆角
        val inset = stroke / 2

        // 电池主体描边
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(inset, inset),
            size = Size(bodyW - stroke, h - stroke),
            cornerRadius = CornerRadius(r),
            style = Stroke(stroke),
        )

        // 正极凸起（实心小矩形）
        drawRect(
            color = outlineColor,
            topLeft = Offset(bodyW, (h - tipH) / 2),
            size = Size(tipW, tipH),
        )

        // 内部填充条
        val pad = stroke + 1.dp.toPx()
        val fillW = (bodyW - pad * 2) * (percent / 100f).coerceIn(0f, 1f)
        if (fillW > 1f) {
            drawRect(
                color = fillColor,
                topLeft = Offset(pad, pad),
                size = Size(fillW, h - pad * 2),
            )
        }

        // 充电闪电
        if (charging) {
            val cx = bodyW / 2
            val cy = h / 2
            val s = h * 0.32f
            val bolt = Path().apply {
                moveTo(cx + s * 0.1f, cy - s)
                lineTo(cx - s * 0.5f, cy + s * 0.1f)
                lineTo(cx + s * 0.05f, cy + s * 0.1f)
                lineTo(cx - s * 0.1f, cy + s)
                lineTo(cx + s * 0.5f, cy - s * 0.1f)
                lineTo(cx - s * 0.05f, cy - s * 0.1f)
                close()
            }
            drawPath(bolt, Color.White)
        }
    })
}

/** 蓝牙图标：Material 扁平风格，粗笔画 */
private fun DrawScope.drawBluetoothIcon(color: Color) {
    val cx = size.width / 2
    val cy = size.height / 2
    val hw = size.width * 0.24f
    val hh = size.height * 0.44f
    val notch = size.height * 0.22f
    val sw = 2f.dp.toPx()

    val path = Path().apply {
        moveTo(cx - hw, cy + notch)
        lineTo(cx + hw, cy - notch)
        lineTo(cx, cy - hh)
        lineTo(cx, cy + hh)
        lineTo(cx + hw, cy + notch)
        lineTo(cx - hw, cy - notch)
    }
    drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round))
    // 中间竖线
    drawLine(color, Offset(cx, cy - hh), Offset(cx, cy + hh), sw, cap = StrokeCap.Round)
}

/** 勿扰模式图标：实心圆 + 中间横向减号 */
private fun DrawScope.drawDndIcon(color: Color, cutoutColor: Color) {
    val cx = size.width / 2
    val cy = size.height / 2
    val r = size.minDimension * 0.44f
    drawCircle(color, r, Offset(cx, cy))
    val barHalf = r * 0.56f
    drawLine(
        color = cutoutColor,
        start = Offset(cx - barHalf, cy),
        end = Offset(cx + barHalf, cy),
        strokeWidth = 2f.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

/** 飞行模式图标：扁平飞机轮廓 */
private fun DrawScope.drawAirplaneIcon(color: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val fw = w * 0.085f
    val plane = Path().apply {
        moveTo(cx, h * 0.08f)
        lineTo(cx + fw, h * 0.22f)
        lineTo(cx + fw, h * 0.38f)
        lineTo(w * 0.94f, h * 0.58f)
        lineTo(w * 0.94f, h * 0.68f)
        lineTo(cx + fw, h * 0.56f)
        lineTo(cx + fw * 0.85f, h * 0.77f)
        lineTo(cx + w * 0.22f, h * 0.89f)
        lineTo(cx + w * 0.22f, h * 0.95f)
        lineTo(cx, h * 0.89f)
        lineTo(cx - w * 0.22f, h * 0.95f)
        lineTo(cx - w * 0.22f, h * 0.89f)
        lineTo(cx - fw * 0.85f, h * 0.77f)
        lineTo(cx - fw, h * 0.56f)
        lineTo(w * 0.06f, h * 0.68f)
        lineTo(w * 0.06f, h * 0.58f)
        lineTo(cx - fw, h * 0.38f)
        lineTo(cx - fw, h * 0.22f)
        close()
    }
    drawPath(plane, color)
}

/** 有线/USB 耳机图标：头梁弧线 + 左右实心耳罩 */
private fun DrawScope.drawHeadphoneIcon(color: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val r = w * 0.35f
    val arcCy = h * 0.54f
    val sw = 1.8f.dp.toPx()

    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - r, arcCy - r),
        size = Size(r * 2, r * 2),
        style = Stroke(sw, cap = StrokeCap.Round),
    )

    val cupW = w * 0.21f
    val cupH = h * 0.35f
    val cupTop = h * 0.52f
    val cr = CornerRadius(2.dp.toPx())
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - r - cupW * 0.25f, cupTop),
        size = Size(cupW, cupH),
        cornerRadius = cr,
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(cx + r - cupW * 0.75f, cupTop),
        size = Size(cupW, cupH),
        cornerRadius = cr,
    )
}

/**
 * Wi-Fi 图标：Android 扁平风格——填充扇形。
 * 信号等级 0-4：底部圆点始终显示，上方三层扇形按等级填充；-1 为关闭/未连接（暗淡 + 斜线）。
 */
private fun DrawScope.drawWifiIcon(
    level: Int,
    activeColor: Color,
    inactiveColor: Color,
    slashColor: Color,
) {
    val cx = size.width / 2
    val cy = size.height * 0.85f
    val dotR = 1.8f.dp.toPx()
    val sw = 2f.dp.toPx()

    // 底部圆点
    drawCircle(if (level >= 0) activeColor else inactiveColor, dotR, Offset(cx, cy))

    // 三层弧线（从内到外）
    for (i in 1..3) {
        val r = 3.dp.toPx() + i * size.width * 0.19f
        val color = if (level >= i) activeColor else inactiveColor
        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(sw, cap = StrokeCap.Round),
        )
    }

    if (level < 0) {
        drawLine(
            color = slashColor,
            start = Offset(size.width * 0.16f, size.height * 0.18f),
            end = Offset(size.width * 0.84f, size.height * 0.86f),
            strokeWidth = 1.8f.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun readWifiLevel(context: Context, excludingNetwork: Network? = null): Int {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: return -1
    val enabled = runCatching { wm.isWifiEnabled }.getOrDefault(false)
    if (!enabled) return -1

    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (cm != null) {
        @Suppress("DEPRECATION")
        val networks = cm.allNetworks
            .asSequence()
            .filter { it != excludingNetwork }
            .mapNotNull { runCatching { cm.getNetworkCapabilities(it) }.getOrNull() }
            .toList()
        val wifiCap = networks.firstOrNull {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } ?: networks.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
            ?: return -1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ss = wifiCap.signalStrength
            if (ss != Int.MIN_VALUE && ss > -127) {
                @Suppress("DEPRECATION")
                return WifiManager.calculateSignalLevel(ss, 5)
            }
        }
    }

    @Suppress("DEPRECATION")
    val info = runCatching { wm.connectionInfo }.getOrNull() ?: return -1
    @Suppress("DEPRECATION")
    if (info.networkId == -1 || info.rssi <= -127) return -1
    @Suppress("DEPRECATION")
    return WifiManager.calculateSignalLevel(info.rssi, 5)
}

private fun readBluetoothEnabled(context: Context): Boolean = runCatching {
    val btm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    btm?.adapter?.isEnabled == true
}.getOrDefault(false)

private fun readWiredHeadsetConnected(audioManager: AudioManager?): Boolean {
    if (audioManager == null) return false
    return runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { dev ->
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                dev.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }.getOrDefault(false)
}

private fun readAirplaneMode(context: Context): Boolean = runCatching {
    Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
}.getOrDefault(false)

private fun readDndEnabled(context: Context): Boolean = runCatching {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (nm != null) {
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    } else {
        Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 0
    }
}.getOrDefault(false)

/**
 * 移动信号等级：-1 = 无 SIM / 无服务，0-4 = 信号等级。
 * 通过 ConnectivityManager 判断是否有 CELLULAR transport。
 */
private fun readMobileSignalLevel(context: Context): Int = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return@runCatching -1
    val network = cm.activeNetwork ?: return@runCatching -1
    val caps = cm.getNetworkCapabilities(network) ?: return@runCatching -1
    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return@runCatching -1
    // 有移动网络连接 → 简单返回一个固定等级 3（无需 READ_PHONE_STATE 权限），
    // 显示有移动信号即可；精确的信号等级需要 TelephonyManager + 权限。
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val ss = caps.signalStrength
        if (ss != Int.MIN_VALUE && ss > -127) {
            // 大致映射 dBm → 0-4 等级
            return@runCatching when {
                ss >= -80 -> 4
                ss >= -90 -> 3
                ss >= -100 -> 2
                ss >= -110 -> 1
                else -> 0
            }
        }
    }
    3 // fallback: 有移动连接但无法读信号强度
}.getOrDefault(-1)

/** 移动信号图标：4 个递增的竖条（Android 风格）。 */
private fun DrawScope.drawMobileSignalIcon(
    level: Int,
    activeColor: Color,
    inactiveColor: Color,
) {
    val w = size.width
    val h = size.height
    val barCount = 4
    val gap = w * 0.06f
    val barW = (w - gap * (barCount - 1)) / barCount
    val maxH = h * 0.85f
    val minH = h * 0.2f
    val bottom = h * 0.92f

    for (i in 0 until barCount) {
        val barH = minH + (maxH - minH) * (i.toFloat() / (barCount - 1))
        val x = i * (barW + gap)
        val color = if (level >= i + 1) activeColor else inactiveColor
        drawRoundRect(
            color = color,
            topLeft = Offset(x, bottom - barH),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

/** 顶部 L1 / R1 肩键徽标：仿手柄肩键半圆轮廓，左右互为镜像。 */
@Composable
fun BumperBadge(
    text: String,
    isLeft: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape = if (isLeft) {
        RoundedCornerShape(topStart = 12.dp, bottomStart = 8.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 8.dp)
    }
    Box(
        modifier
            .size(width = 24.dp, height = 18.dp)
            .clip(shape)
            .background(DeckColors.ButtonBadge)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = DeckType.statusInfo.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
            ),
            color = DeckColors.ButtonBadgeText,
            maxLines = 1,
        )
    }
}

/**
 * 按键字母徽标：墨色圆形 + 反色粗体字母（支持多字符按键名如 L1 / START 自动变胶囊）。
 * [primary] 为 true 时使用品牌渐变底 + 白字（主操作 A 键）。
 */
@Composable
fun ButtonBadge(text: String, modifier: Modifier = Modifier, primary: Boolean = false, size: Dp = 22.dp) {
    val bgModifier = if (primary) Modifier.background(DeckBrand.brandBrush) else Modifier.background(DeckColors.ButtonBadge)
    Box(
        modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .clip(CircleShape)
            .then(bgModifier)
            .padding(horizontal = if (text.length > 1) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = DeckType.statusInfo.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (text.length > 1) 9.sp else (size.value * 0.46f).sp,
                lineHeight = (size.value * 0.6f).sp,
            ),
            color = if (primary) Color.White else DeckColors.ButtonBadgeText,
            maxLines = 1,
        )
    }
}

/** 扁平单色「快传」纤细纸飞机 Logo（13dp 小尺寸依然锐利灵动）。 */
private fun DrawScope.drawQuickShareLogo(color: Color) {
    val w = size.width
    val h = size.height
    val sw = (w * 0.115f).coerceAtLeast(1.25f)
    val plane = Path().apply {
        moveTo(w * 0.88f, h * 0.14f)
        lineTo(w * 0.12f, h * 0.46f)
        lineTo(w * 0.46f, h * 0.56f)
        lineTo(w * 0.58f, h * 0.88f)
        close()
    }
    drawPath(plane, color = color, style = Stroke(width = sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawLine(
        color = color,
        start = Offset(w * 0.86f, h * 0.16f),
        end = Offset(w * 0.46f, h * 0.56f),
        strokeWidth = sw * 0.92f,
        cap = StrokeCap.Round,
    )
}

/** 扁平单色「Web 服务」极简浏览器窗口 Logo（避免地球网格缩小后密集糊边）。 */
private fun DrawScope.drawWebGlobeLogo(color: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = minOf(w, h) * 0.42f
    val sw = (w * 0.11f).coerceAtLeast(1.2f)
    // 外圆地球轮廓
    drawCircle(
        color = color,
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = sw),
    )
    // 赤道横线
    drawLine(
        color = color,
        start = Offset(cx - r, cy),
        end = Offset(cx + r, cy),
        strokeWidth = sw * 0.9f,
        cap = StrokeCap.Round,
    )
    // 经线椭圆（简洁单椭圆，小尺寸依然通透不拥挤）
    drawOval(
        color = color,
        topLeft = Offset(cx - r * 0.48f, cy - r),
        size = Size(r * 0.96f, r * 2f),
        style = Stroke(width = sw * 0.9f),
    )
}
