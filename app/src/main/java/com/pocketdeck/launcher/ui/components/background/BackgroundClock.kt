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

/**
 * 背景动画的循环周期上限（秒）。所有效果的运动周期都是它的约数，
 * 因此时间回绕时画面不会跳变；同时避免 Float 长时间累加后精度下降。
 */
internal const val BG_TIME_WRAP_SEC = 1200f

/** 慢速效果（流光、极光、封面光）以 30Hz 推进时间即可，节省弱 GPU 的填充率。 */
internal const val BG_STEP_SLOW = 1f / 30f

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
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastNanos = 0L
            var accum = 0f
            // 帧回调只创建一次，避免每帧分配 lambda
            val onFrame: (Long) -> Unit = { now ->
                if (lastNanos != 0L) {
                    // 单帧最多推进 50ms：从暂停/后台恢复时不会突然跳一大段
                    accum += ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    if (accum >= stepSec) {
                        var t = time.floatValue + accum
                        if (t >= BG_TIME_WRAP_SEC) t -= BG_TIME_WRAP_SEC
                        time.floatValue = t
                        accum = 0f
                    }
                }
                lastNanos = now
            }
            while (true) {
                withFrameNanos(onFrame)
            }
        }
    }
    return time
}
