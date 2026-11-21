package com.pocketdeck.launcher.input

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import com.pocketdeck.launcher.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

/**
 * 掌机/手柄按键音效播放器。
 *
 * 内置「清脆触点」与「机械青轴」（分类切换专属）两组高采样率（48 kHz）WAV 音效，
 * 配合 [SoundPool] 通过游戏/媒体音频通道播放，延迟极低、不受系统触摸音开关屏蔽，并带防连发节流。
 */
object KeySound {
    private const val MIN_INTERVAL_MS = 32L

    private enum class Tone {
        /** 默认交互与方向导航：清脆触点。 */
        Click,

        /** 分类/标签切换：机械青轴。 */
        TabSwitch,
    }

    @Volatile
    private var pool: SoundPool? = null

    private val soundExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "KeySound").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
        }
    }

    private val soundIds = ConcurrentHashMap<Tone, Int>()
    private val loadedIds = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var pendingTone: Tone? = null

    @Volatile
    private var lastPlayMs: Long = 0L

    @Volatile
    private var configuredEnabled: Boolean = true

    fun configure(enabled: Boolean) {
        configuredEnabled = enabled
    }

    /** 触屏点击按钮/交互项时播放默认清脆触点音效。 */
    fun playTouch(context: Context) {
        if (!configuredEnabled) return
        playTone(context, enabled = true, tone = Tone.Click)
    }

    /** 切换分类/标签页时播放机械青轴音效。 */
    fun playTabSwitch(context: Context, enabled: Boolean = configuredEnabled) {
        playTone(context, enabled = enabled, tone = Tone.TabSwitch)
    }

    private fun rawResFor(tone: Tone): Int = when (tone) {
        Tone.Click -> R.raw.key_click
        Tone.TabSwitch -> R.raw.key_mech
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
                    val pending = pendingTone
                    if (pending != null && soundIds[pending] == id) {
                        pendingTone = null
                        poolRef.play(id, 0.9f, 0.9f, 1, 0, 1f)
                    }
                }
            }
            for (tone in Tone.entries) {
                soundIds[tone] = sp.load(appCtx, rawResFor(tone), 1)
            }
            pool = sp
        }
    }

    fun play(
        context: Context,
        enabled: Boolean = configuredEnabled,
    ) {
        playTone(context, enabled = enabled, tone = Tone.Click)
    }

    private fun playTone(
        context: Context,
        enabled: Boolean,
        tone: Tone,
    ) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayMs < MIN_INTERVAL_MS) return
        lastPlayMs = now

        val appCtx = context.applicationContext
        // 异步到专属音频线程触发 SoundPool.play，彻底避免 AudioFlinger JNI IPC 阻塞主线程（实测每键 4ms+）
        soundExecutor.execute {
            runCatching {
                if (pool == null) warmup(appCtx)
                val sp = pool
                val sid = soundIds[tone] ?: 0
                if (sp != null && sid != 0 && sid in loadedIds) {
                    sp.play(sid, 0.9f, 0.9f, 1, 0, 1f)
                } else if (sp != null && sid != 0) {
                    pendingTone = tone
                } else {
                    val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    am?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.8f)
                }
            }
        }
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
