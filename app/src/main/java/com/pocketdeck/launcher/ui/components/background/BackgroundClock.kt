package com.pocketdeck.launcher.ui.components.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * 背景动画的循环周期上限（秒）。所有效果的运动周期都是它的约数，
 * 因此时间回绕时画面不会跳变；同时避免 Float 长时间累加后精度下降。
 */
internal const val BG_TIME_WRAP_SEC = 1200f

/** 慢速效果（流光、极光、封面光）以 20Hz 推进时间已足够丝滑，降低弱 GPU / 掌机连续唤醒负载。 */
internal const val BG_STEP_SLOW = 1f / 20f

/** 选中卡片微幅 3D 呼吸与气泡悬浮以 30Hz 推进，兼顾细腻流畅与 60~120Hz 掌机待机功耗。 */
internal const val BG_STEP_IDLE = 1f / 30f

internal class IdleBreathingClock(
    val seconds: FloatState,
    val envelope: FloatState,
)

/**
 * 节流帧时钟累加器（纯逻辑可单测）：
 * - 对 [stepSec] 引入 2ms 容差（`stepSec - 0.002f`），解决 120Hz 屏幕下 6 帧 49.98ms < 50ms 被判定未到步长而腰斩至 10Hz/15Hz 的拍频问题；
 * - 单帧最大步进钳位在 50ms，避免后台恢复时动画大跳变。
 */
internal class ThrottledClockStepper(
    private val stepSec: Float,
) {
    private val thresholdSec: Float = computeThrottledStepThreshold(stepSec)
    private var lastNanos: Long = 0L
    private var accum: Float = 0f

    fun onFrame(nowNanos: Long, onAdvance: (dt: Float) -> Unit) {
        if (lastNanos != 0L) {
            accum += ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            if (accum >= thresholdSec) {
                val dt = accum
                accum = 0f
                onAdvance(dt)
            }
        }
        lastNanos = nowNanos
    }

    companion object {
        internal const val TOLERANCE_SEC = 0.002f

        internal fun computeThrottledStepThreshold(stepSec: Float): Float =
            if (stepSec > 0f) (stepSec - TOLERANCE_SEC).coerceAtLeast(0f) else 0f

        internal fun computeThrottledSleepMs(stepSec: Float): Long =
            if (stepSec > 0f) ((stepSec - 0.008f) * 1000f).toLong().coerceAtLeast(8L) else 0L
    }
}

private suspend fun runThrottledFrameClock(
    lifecycle: Lifecycle,
    stepSec: Float,
    onAdvance: (dt: Float) -> Unit,
) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        val stepper = ThrottledClockStepper(stepSec)
        // 帧回调只创建一次，避免每帧分配 lambda
        val onFrame: (Long) -> Unit = { now -> stepper.onFrame(now, onAdvance) }
        val sleepMs = ThrottledClockStepper.computeThrottledSleepMs(stepSec)
        while (true) {
            withFrameNanos(onFrame)
            if (sleepMs > 0L) delay(sleepMs)
        }
    }
}

/**
 * 背景共享时间源（秒）。
 *
 * - 只在绘制阶段读取（`time.floatValue` 放在 onDraw 里），因此推进时间只触发重绘、不触发重组。
 * - [paused] 为 true 时冻结时间（不再请求帧）。
 * - 宿主生命周期低于 STARTED（息屏、切到后台、被游戏覆盖）时自动停止请求帧。
 * - [stepSec] > 0 时按该步长节流（例如 30Hz），0 表示每帧推进。
 */
@Composable
internal fun rememberBackgroundClock(paused: Boolean, stepSec: Float = 0f): FloatState {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(paused, stepSec, lifecycle) {
        if (paused) return@LaunchedEffect
        runThrottledFrameClock(lifecycle, stepSec) { dt ->
            var t = time.floatValue + dt
            if (t >= BG_TIME_WRAP_SEC) t -= BG_TIME_WRAP_SEC
            time.floatValue = t
        }
    }
    return time
}

/**
 * 选中卡片/气泡空闲 3D 呼吸时间源：
 * - 复用 [runThrottledFrameClock] 实现 30Hz 节流、`Lifecycle.State.STARTED` 自动暂停与零每帧 lambda；
 * - [resetKey] 或暂停态变化时立即归零并在停稳 [delayMs] 后通过 [rampSec] 平滑渐入，纯 `graphicsLayer` 读取零重组。
 */
@Composable
internal fun rememberIdleBreathingClock(
    paused: Boolean,
    resetKey: Any?,
    delayMs: Long = 120L,
    rampSec: Float = 0.65f,
    stepSec: Float = BG_STEP_IDLE,
): IdleBreathingClock {
    val seconds = remember { mutableFloatStateOf(0f) }
    val envelope = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(paused, resetKey, delayMs, rampSec, stepSec, lifecycle) {
        seconds.floatValue = 0f
        envelope.floatValue = 0f
        if (paused) return@LaunchedEffect
        if (delayMs > 0L) delay(delayMs)
        runThrottledFrameClock(lifecycle, stepSec) { dt ->
            var t = seconds.floatValue + dt
            if (t >= BG_TIME_WRAP_SEC) t -= BG_TIME_WRAP_SEC
            seconds.floatValue = t
            val curEnv = envelope.floatValue
            if (curEnv < 1f) {
                envelope.floatValue = (curEnv + dt / rampSec).coerceAtMost(1f)
            }
        }
    }
    return remember(seconds, envelope) { IdleBreathingClock(seconds, envelope) }
}
