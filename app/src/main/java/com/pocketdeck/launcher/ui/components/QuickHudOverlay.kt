package com.pocketdeck.launcher.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.input.GamepadKeys
import com.pocketdeck.launcher.input.KeyAction
import com.pocketdeck.launcher.lan.LanServerState
import com.pocketdeck.launcher.ui.theme.DeckBrand
import com.pocketdeck.launcher.ui.theme.DeckColors
import com.pocketdeck.launcher.ui.theme.DeckShapes
import com.pocketdeck.launcher.ui.theme.DeckType
import com.pocketdeck.launcher.ui.theme.brandBorder
import com.pocketdeck.launcher.ui.theme.cardSurface

/**
 * F10 掌机快捷 HUD 浮层（实验性功能）：
 * 支持手柄上下切换行、左右调节亮度/媒体音量，一键开关局域网快传与 Web 管理，显示 Wi-Fi 与电量状态。
 */
@Composable
fun QuickHudOverlay(
    lanState: LanServerState,
    swapAbxy: Boolean,
    keyBindings: Map<KeyAction, Int>,
    onToggleQuickShare: (Boolean) -> Unit,
    onToggleWebServer: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVol = remember {
        runCatching { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrNull()?.coerceAtLeast(1) ?: 15
    }
    var volume by remember {
        mutableIntStateOf(
            runCatching { audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull() ?: 0,
        )
    }

    val activity = context as? Activity
    var brightness by remember {
        val wAttr = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (wAttr in 0f..1f) wAttr else 0.5f)
    }

    val batteryPct = remember {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }
    val isOnline = NetworkStatus.online.value

    var selectedRow by remember { mutableIntStateOf(0) } // 0=Brightness, 1=Volume, 2=QuickShare, 3=Web
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocusSafely() }

    fun adjustBrightness(delta: Float) {
        val next = (brightness + delta).coerceIn(0.05f, 1f)
        brightness = next
        activity?.window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = next
            w.attributes = lp
        }
    }

    fun adjustVolume(delta: Int) {
        val next = (volume + delta).coerceIn(0, maxVol)
        volume = next
        runCatching { audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DeckColors.CardShadow.copy(alpha = 0.45f))
            .scrimTapDismiss(onDismiss)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    GamepadKeys.isBack(event.key) ||
                        GamepadKeys.isCancelOrApps(event.key, swapAbxy, keyBindings) ||
                        GamepadKeys.isQuickHud(event.key, swapAbxy, keyBindings) -> {
                        onDismiss()
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        selectedRow = (selectedRow + 1) % 4
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        selectedRow = (selectedRow + 3) % 4
                        true
                    }
                    event.key == Key.DirectionLeft -> {
                        when (selectedRow) {
                            0 -> adjustBrightness(-0.1f)
                            1 -> adjustVolume(-1)
                            2 -> if (lanState.shareRunning) onToggleQuickShare(false)
                            3 -> if (lanState.webRunning) onToggleWebServer(false)
                        }
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        when (selectedRow) {
                            0 -> adjustBrightness(0.1f)
                            1 -> adjustVolume(1)
                            2 -> if (!lanState.shareRunning) onToggleQuickShare(true)
                            3 -> if (!lanState.webRunning) onToggleWebServer(true)
                        }
                        true
                    }
                    GamepadKeys.isConfirm(event.key, swapAbxy, keyBindings) -> {
                        when (selectedRow) {
                            2 -> onToggleQuickShare(!lanState.shareRunning)
                            3 -> onToggleWebServer(!lanState.webRunning)
                        }
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        val shape = RoundedCornerShape(DeckBrand.RadiusL)
        Column(
            Modifier
                .padding(top = 56.dp, end = 20.dp)
                .widthIn(min = 280.dp, max = 340.dp)
                .cardSurface(shape = shape, elevation = 14.dp)
                .border(0.8.dp, DeckColors.Line, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.quick_hud_title),
                    style = DeckType.settingsValue.copy(fontWeight = FontWeight.Bold),
                    color = DeckColors.TextPrimary,
                )
                val statusStr = buildString {
                    append(if (isOnline) "Wi-Fi" else stringResource(R.string.quick_hud_offline))
                    if (batteryPct >= 0) append(" · $batteryPct%")
                }
                Text(
                    text = statusStr,
                    style = DeckType.metaLabel,
                    color = DeckColors.TextSecondary,
                )
            }

            HudSliderRow(
                label = stringResource(R.string.quick_hud_brightness),
                valueText = "${(brightness * 100).toInt()}%",
                progress = brightness,
                highlighted = selectedRow == 0,
                onDec = { selectedRow = 0; adjustBrightness(-0.1f) },
                onInc = { selectedRow = 0; adjustBrightness(0.1f) },
            )
            HudSliderRow(
                label = stringResource(R.string.quick_hud_volume),
                valueText = "${(volume * 100) / maxVol}%",
                progress = volume.toFloat() / maxVol.toFloat(),
                highlighted = selectedRow == 1,
                onDec = { selectedRow = 1; adjustVolume(-1) },
                onInc = { selectedRow = 1; adjustVolume(1) },
            )
            HudToggleRow(
                label = stringResource(R.string.lan_share_title),
                checked = lanState.shareRunning,
                highlighted = selectedRow == 2,
                onClick = { selectedRow = 2; onToggleQuickShare(!lanState.shareRunning) },
            )
            HudToggleRow(
                label = stringResource(R.string.lan_web_title),
                checked = lanState.webRunning,
                highlighted = selectedRow == 3,
                onClick = { selectedRow = 3; onToggleWebServer(!lanState.webRunning) },
            )
        }
    }
}

@Composable
private fun HudSliderRow(
    label: String,
    valueText: String,
    progress: Float,
    highlighted: Boolean,
    onDec: () -> Unit,
    onInc: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) DeckColors.AccentSoft else DeckColors.Surface)
            .brandBorder(active = highlighted, shape = RoundedCornerShape(10.dp), activeWidth = 1.3.dp, inactiveWidth = 0.6.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = DeckType.settingsLabel, color = if (highlighted) DeckColors.Ruby else DeckColors.TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("-", style = DeckType.settingsValue, color = DeckColors.Ruby, modifier = Modifier.soundClickable(onClick = onDec).padding(horizontal = 4.dp))
                Text(valueText, style = DeckType.metaLabel, color = DeckColors.TextSecondary)
                Text("+", style = DeckType.settingsValue, color = DeckColors.Ruby, modifier = Modifier.soundClickable(onClick = onInc).padding(horizontal = 4.dp))
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(DeckShapes.tabPill),
            color = DeckColors.Ruby,
            trackColor = DeckColors.DialogBg,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun HudToggleRow(
    label: String,
    checked: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) DeckColors.AccentSoft else DeckColors.Surface)
            .brandBorder(active = highlighted, shape = RoundedCornerShape(10.dp), activeWidth = 1.3.dp, inactiveWidth = 0.6.dp)
            .soundClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = DeckType.settingsLabel,
            color = if (highlighted) DeckColors.Ruby else DeckColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        DeckSwitch(checked = checked)
    }
}
