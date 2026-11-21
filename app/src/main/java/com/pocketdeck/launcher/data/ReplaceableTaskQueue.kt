package com.pocketdeck.launcher.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 固定并发数的「可替换等待队列」调度器：
 * - 最多 [maxConcurrency] 个常驻 Worker 并发执行；
 * - 单任务硬超时 [taskTimeoutMs]，防止慢连接或异常任务永久占满并发槽位；
 * - 调用 [replacePending] / [scheduleReplace] 时：
 *   1) 正在执行中的在途任务保持继续运行直至完成（不浪费已发出的网络请求）；
 *   2) 清空尚未开始执行的旧等待队列，替换为新一批优先级任务（自动跳过已在途与重复项）。
 */
internal class ReplaceableTaskQueue<T>(
    private val scope: CoroutineScope,
    private val maxConcurrency: Int = 2,
    private val taskTimeoutMs: Long = 20_000L,
    private val worker: suspend (T) -> Unit,
) {
    private val lock = Any()
    private val pending = ArrayDeque<Pair<String, T>>()
    private val inFlight = HashSet<String>()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var workersStarted = false
    private var debounceJob: Job? = null

    fun scheduleReplace(items: List<Pair<String, T>>, debounceMs: Long = 180L) {
        if (debounceMs <= 0L) {
            replacePending(items)
            return
        }
        synchronized(lock) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(debounceMs)
                replacePending(items)
            }
        }
    }

    fun replacePending(items: List<Pair<String, T>>) {
        synchronized(lock) {
            pending.clear()
            val seen = HashSet<String>(items.size)
            for (item in items) {
                val key = item.first
                if (key in inFlight || !seen.add(key)) continue
                pending.addLast(item)
            }
            if (!workersStarted && pending.isNotEmpty()) {
                workersStarted = true
                repeat(maxConcurrency.coerceAtLeast(1)) {
                    scope.launch { runWorker() }
                }
            }
        }
        if (items.isNotEmpty()) {
            signal.trySend(Unit)
        }
    }

    private suspend fun runWorker() {
        while (scope.isActive) {
            val next = pollNext()
            if (next == null) {
                signal.receive()
                continue
            }
            val (key, payload) = next
            try {
                withTimeoutOrNull(taskTimeoutMs) {
                    worker(payload)
                }
            } finally {
                val hasMore = synchronized(lock) {
                    inFlight.remove(key)
                    pending.isNotEmpty()
                }
                if (hasMore) signal.trySend(Unit)
            }
        }
    }

    private fun pollNext(): Pair<String, T>? = synchronized(lock) {
        while (pending.isNotEmpty()) {
            val head = pending.removeFirst()
            if (inFlight.add(head.first)) return@synchronized head
        }
        null
    }
}
