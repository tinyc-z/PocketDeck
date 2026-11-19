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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.input.KeySound
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 顶栏：L1 徽标 | 可滚动标签行 | R1 徽标 | 时间 · 飞行模式 耳机 蓝牙 Wi-Fi 电池
 */
@Composable
fun TopBar(
    tabs: List<String>,
    selectedIndex: Int,
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
    onLanBadgeClick: () -> Unit = {},
    onWifiClick: () -> Unit = onLanBadgeClick,
    onTabClick: (Int) -> Unit = {},
    onPrevTab: () -> Unit = {},
    onNextTab: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
        val fmt = SimpleDateFormat(if (is12h) "h:mm" else "H:mm", Locale.getDefault())
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        fun syncAllStatus(excludingLostNetwork: Network? = null) {
            timeText = fmt.format(Date())
            wifiLevel = readWifiLevel(context, excludingLostNetwork)
            btEnabled = readBluetoothEnabled(context)
            headsetConnected = readWiredHeadsetConnected(audioManager)
            airplaneMode = readAirplaneMode(context)
            dndEnabled = readDndEnabled(context)
            mobileLevel = readMobileSignalLevel(context)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                timeText = fmt.format(Date())
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
                        wifiLevel = if (state == WifiManager.WIFI_STATE_ENABLED) {
                            readWifiLevel(context)
                        } else {
                            -1
                        }
                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION,
                    WifiManager.RSSI_CHANGED_ACTION -> {
                        wifiLevel = readWifiLevel(context)
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        btEnabled = state == BluetoothAdapter.STATE_ON
                    }
                    AudioManager.ACTION_HEADSET_PLUG -> {
                        val plugged = intent.getIntExtra("state", 0) == 1
                        headsetConnected = plugged || readWiredHeadsetConnected(audioManager)
                    }
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                        airplaneMode = intent.getBooleanExtra("state", readAirplaneMode(context))
                        wifiLevel = readWifiLevel(context)
                    }
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                        dndEnabled = readDndEnabled(context)
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
                mainHandler.post { wifiLevel = readWifiLevel(context) }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                mainHandler.post { wifiLevel = readWifiLevel(context) }
            }

            override fun onLost(network: Network) {
                mainHandler.post { wifiLevel = readWifiLevel(context, excludingNetwork = network) }
            }
        }
        val wifiCallbackRegistered = runCatching {
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager?.registerNetworkCallback(req, wifiCallback)
            connectivityManager != null
        }.getOrDefault(false)

        val audioCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                headsetConnected = readWiredHeadsetConnected(audioManager)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                headsetConnected = readWiredHeadsetConnected(audioManager)
            }
        }
        audioManager?.registerAudioDeviceCallback(audioCallback, mainHandler)

        val settingsObserver = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                airplaneMode = readAirplaneMode(context)
                dndEnabled = readDndEnabled(context)
                wifiLevel = readWifiLevel(context)
            }
        }
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

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncAllStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        // 初始值
        syncAllStatus()
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        } catch (_: Exception) {}

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            if (wifiCallbackRegistered) {
                runCatching { connectivityManager?.unregisterNetworkCallback(wifiCallback) }
            }
            runCatching { audioManager?.unregisterAudioDeviceCallback(audioCallback) }
            runCatching { context.contentResolver.unregisterContentObserver(settingsObserver) }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    // 自动滚动选中的标签到可视区域
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (tabs.isNotEmpty()) {
            listState.animateScrollToItem(
                index = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
            )
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 可滚动标签行（仅在需要边缘渐变时启用离屏合成，避免无滚动时的多余离屏渲染开销）
        val fadeLeft by remember { androidx.compose.runtime.derivedStateOf { listState.canScrollBackward } }
        val fadeRight by remember { androidx.compose.runtime.derivedStateOf { listState.canScrollForward } }
        LazyRow(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer(
                    compositingStrategy = if (fadeLeft || fadeRight) CompositingStrategy.Offscreen else CompositingStrategy.Auto,
                )
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    if (w > 0f && (fadeLeft || fadeRight)) {
                        val fadeFrac = (20f / w).coerceIn(0.01f, 0.45f)
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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(tabs) { i, tab ->
                val selected = i == selectedIndex
                Box(
                    Modifier
                        .clickable {
                            KeySound.playTabSwitch(context)
                            onTabClick(i)
                        }
                        .then(
                            if (selected) Modifier
                                .clip(DeckShapes.tabPill)
                                .background(DeckColors.PillSelected)
                            else Modifier
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tab,
                        style = if (selected) DeckType.tabSelected else DeckType.tabInactive,
                        color = if (selected) DeckColors.PillText else DeckColors.PillTextInactive,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }

        Spacer(Modifier.width(24.dp))

        // 状态栏：时间 · 勿扰 飞行模式 耳机 蓝牙 移动信号 Wi-Fi 电池
        val statusTint = DeckColors.TextPrimary
        val statusBg = DeckColors.Background
        val statusInactive = DeckColors.TextSecondary.copy(alpha = 0.28f)
        val statusSlash = DeckColors.TextSecondary.copy(alpha = 0.72f)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // 局域网快传 / Web 管理与任务进度提醒（无边框扁平风格；常态中性轻量底色，有待确认/等待/活跃进度时呈现语义主题色）
            val hasTask = lanTaskProgress != null
            if (lanSharingActive || lanWebActive || lanPendingIncoming || lanTransferWaiting || hasTask) {
                val showTextOnly = lanPendingIncoming || lanTransferWaiting
                val isDarkTheme = DeckColors.isDark
                val animProgress by animateFloatAsState(
                    targetValue = (lanTaskProgress ?: 0f).coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 200),
                    label = "lan_task_progress",
                )
                val isColored = lanPendingIncoming || hasTask || lanTransferWaiting
                val accent = when {
                    lanPendingIncoming -> Color(0xFFEC4899)
                    hasTask || lanTransferWaiting -> Color(0xFF0EA5E9)
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
                            if (!showTextOnly && hasTask && animProgress > 0f) {
                                drawRect(
                                    color = accent.copy(alpha = if (isDarkTheme) 0.36f else 0.25f),
                                    size = Size(size.width * animProgress, size.height),
                                )
                            }
                        }
                        .soundClickable(onClick = onLanBadgeClick)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (showTextOnly) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(accent),
                        )
                        Text(
                            text = stringResource(
                                if (lanPendingIncoming) R.string.lan_status_pending else R.string.lan_status_waiting,
                            ),
                            style = DeckType.metaLabel.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                            color = accent,
                            maxLines = 1,
                        )
                    } else {
                        val showShareLogo = lanSharingActive || (hasTask && !lanWebActive)
                        val showWebLogo = lanWebActive
                        if (showShareLogo) {
                            Box(
                                Modifier
                                    .size(13.dp)
                                    .drawBehind { drawQuickShareLogo(accent) },
                            )
                        }
                        if (showWebLogo) {
                            Box(
                                Modifier
                                    .size(13.dp)
                                    .drawBehind { drawWebGlobeLogo(accent) },
                            )
                        }
                    }
                }
            }

            if (showClock) {
                Text(timeText, style = DeckType.statusInfo, color = statusTint)
            }

            // 勿扰模式图标
            if (showDnd && dndEnabled) {
                Box(
                    Modifier
                        .size(14.dp)
                        .drawBehind { drawDndIcon(statusTint, statusBg) }
                )
            }

            // 飞行模式图标
            if (showAirplane && airplaneMode) {
                Box(
                    Modifier
                        .size(15.dp)
                        .drawBehind { drawAirplaneIcon(statusTint) }
                )
            }

            // 有线/USB 耳机图标
            if (showHeadset && headsetConnected) {
                Box(
                    Modifier
                        .size(15.dp)
                        .drawBehind { drawHeadphoneIcon(statusTint) }
                )
            }

            // 蓝牙图标
            if (showBluetooth && btEnabled) {
                Box(
                    Modifier
                        .size(14.dp)
                        .drawBehind { drawBluetoothIcon(statusTint) }
                )
            }

            // 移动信号图标
            if (showMobileSignal && mobileLevel >= 0) {
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
            }

            // 电池图标
            if (showBattery) {
                BatteryIcon(
                    percent = batteryPct,
                    charging = isCharging,
                    modifier = Modifier.size(width = 28.dp, height = 14.dp),
                )
            }
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
        charging -> Color(0xFF4CAF50) // Material Green 500
        percent <= 20 -> Color(0xFFF44336) // Material Red 500
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

/** 底栏按钮旁的黑色圆形/胶囊字母徽标（支持多字符按键名如 L1 / START）。 */
@Composable
fun ButtonBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(DeckColors.ButtonBadge)
            .padding(horizontal = if (text.length > 1) 6.dp else 0.dp),
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
    val sw = (w * 0.115f).coerceAtLeast(1.25f)
    val padX = w * 0.10f
    val padY = h * 0.16f
    val rectW = w - padX * 2f
    val rectH = h - padY * 2f
    val corner = w * 0.16f
    // 外框圆角窗口
    drawRoundRect(
        color = color,
        topLeft = Offset(padX, padY),
        size = Size(rectW, rectH),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = sw),
    )
    // 顶部地址栏细横线
    val barY = padY + rectH * 0.34f
    drawLine(
        color = color,
        start = Offset(padX + sw * 0.4f, barY),
        end = Offset(padX + rectW - sw * 0.4f, barY),
        strokeWidth = sw * 0.88f,
    )
    // 左上角两个极简窗口微点
    val dotR = (w * 0.048f).coerceAtLeast(0.8f)
    val dotY = padY + rectH * 0.17f
    drawCircle(color, radius = dotR, center = Offset(padX + rectW * 0.22f, dotY))
    drawCircle(color, radius = dotR, center = Offset(padX + rectW * 0.38f, dotY))
}
