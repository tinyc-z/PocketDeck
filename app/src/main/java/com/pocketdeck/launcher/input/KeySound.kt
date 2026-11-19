package com.pocketdeck.launcher.input

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent
import android.view.SoundEffectConstants
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import com.pocketdeck.launcher.R
import com.pocketdeck.launcher.data.KeySoundStyle
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

/**
 * 掌机/手柄按键提示音播放器。
 *
 * 内置多套清脆高采样率（48 kHz）WAV 音效配合 [SoundPool] 通过游戏/媒体音频通道播放，
 * 延迟极低、不受系统触摸音开关屏蔽，并带防连发节流。
 */
object KeySound {
    private const val MIN_INTERVAL_MS = 32L

    @Volatile
    private var pool: SoundPool? = null

    private val soundIds = ConcurrentHashMap<KeySoundStyle, Int>()
    private val loadedIds = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var pendingStyle: KeySoundStyle? = null

    @Volatile
    private var lastPlayMs: Long = 0L

    @Volatile
    private var configuredEnabled: Boolean = true

    @Volatile
    private var configuredStyle: KeySoundStyle = KeySoundStyle.CrispTick

    fun configure(enabled: Boolean, style: KeySoundStyle) {
        configuredEnabled = enabled
        configuredStyle = style
    }

    /** 触屏点击按钮/交互项时播放提示音（受全局按键音开关与当前风格控制）。 */
    fun playTouch(context: Context) {
        if (!configuredEnabled) return
        play(context, enabled = true, style = configuredStyle)
    }

    private fun rawResFor(style: KeySoundStyle): Int = when (style) {
        KeySoundStyle.CrispTick -> R.raw.key_click
        KeySoundStyle.CrystalDrop -> R.raw.key_crystal
        KeySoundStyle.WoodBlock -> R.raw.key_wood
        KeySoundStyle.Mechanical -> R.raw.key_mech
        KeySoundStyle.RetroBit -> R.raw.key_retro
    }

    fun warmup(context: Context) {
        if (pool != null) return
        synchronized(this) {
            if (pool != null) return
            val appCtx = context.applicationContext
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val sp = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build()
            sp.setOnLoadCompleteListener { poolRef, id, status ->
                if (status == 0) {
                    loadedIds.add(id)
                    val pending = pendingStyle
                    if (pending != null && soundIds[pending] == id) {
                        pendingStyle = null
                        poolRef.play(id, 0.9f, 0.9f, 1, 0, 1f)
                    }
                }
            }
            for (style in KeySoundStyle.entries) {
                soundIds[style] = sp.load(appCtx, rawResFor(style), 1)
            }
            pool = sp
        }
    }

    fun play(
        context: Context,
        enabled: Boolean = true,
        style: KeySoundStyle = KeySoundStyle.CrispTick,
    ) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayMs < MIN_INTERVAL_MS) return
        lastPlayMs = now

        warmup(context)
        val sp = pool
        val sid = soundIds[style] ?: 0
        if (sp != null && sid != 0 && sid in loadedIds) {
            sp.play(sid, 0.9f, 0.9f, 1, 0, 1f)
        } else if (sp != null && sid != 0) {
            pendingStyle = style
        } else {
            runCatching {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.8f)
            }
        }
    }

    /** 在设置页切换音效时立即试听一次（跳过 32ms 节流）。 */
    fun preview(context: Context, style: KeySoundStyle) {
        lastPlayMs = 0L
        play(context, enabled = true, style = style)
    }

    /** 判断一次按键事件是否属于应该触发按键音的导航/手柄操作按键。 */
    fun isNavOrGamepadKey(
        event: ComposeKeyEvent,
        swapAbxy: Boolean,
        bindings: Map<KeyAction, Int>,
    ): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val key = event.key
        val nativeCode = key.nativeKeyCode
        return when {
            key == Key.DirectionUp ||
                key == Key.DirectionDown ||
                key == Key.DirectionLeft ||
                key == Key.DirectionRight ||
                key == Key.DirectionCenter ||
                key == Key.Enter ||
                key == Key.NumPadEnter ||
                key == Key.Back ||
                key == Key.Escape ||
                KeyEvent.isGamepadButton(nativeCode) -> true
            KeyAction.entries.any { GamepadKeys.matches(key, it, bindings, swapAbxy) } -> true
            else -> false
        }
    }
}
