package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.ReplaceableTaskQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class WindowCoverQueueTest {

    @Test
    fun replacePendingPreservesInFlightAndDropsUnstartedOldItems() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val active = AtomicInteger(0)
            val maxObserved = AtomicInteger(0)
            val started = Collections.synchronizedList(ArrayList<String>())
            val completed = Collections.synchronizedList(ArrayList<String>())
            val gate = CompletableDeferred<Unit>()
            val twoStarted = CompletableDeferred<Unit>()
            val newBatchDone = CompletableDeferred<Unit>()

            val queue = ReplaceableTaskQueue<String>(scope = scope, maxConcurrency = 2) { id ->
                val cur = active.incrementAndGet()
                maxObserved.accumulateAndGet(cur, ::maxOf)
                started += id
                if (started.size == 2) twoStarted.complete(Unit)
                if (id == "old-1" || id == "old-2") {
                    gate.await()
                } else {
                    delay(15L)
                }
                completed += id
                active.decrementAndGet()
                if (id == "new-3") newBatchDone.complete(Unit)
            }

            // 第一批 5 个任务：最多 2 个立即在途（old-1, old-2），其余 3 个在等待队列
            queue.replacePending(
                listOf("old-1", "old-2", "old-3", "old-4", "old-5").map { it to it },
            )
            twoStarted.await()
            delay(30L)
            assertEquals(2, started.size)

            // 用户滑到新位置：替换等待队列为 new-1..new-3
            queue.replacePending(
                listOf("new-1", "new-2", "new-3").map { it to it },
            )

            // 放行在途的 old-1 / old-2
            gate.complete(Unit)
            newBatchDone.await()
            delay(50L)

            // 验证：最大并发 <= 2；old-1/old-2 完整跑完；未开始的 old-3/4/5 被丢弃；new-1/2/3 全部执行
            assertTrue("max concurrency exceeded: ${maxObserved.get()}", maxObserved.get() <= 2)
            assertTrue("old-1 should finish", "old-1" in completed)
            assertTrue("old-2 should finish", "old-2" in completed)
            assertFalse("old-3 should be dropped", "old-3" in started)
            assertFalse("old-4 should be dropped", "old-4" in started)
            assertFalse("old-5 should be dropped", "old-5" in started)
            assertTrue(listOf("new-1", "new-2", "new-3").all { it in completed })
        } finally {
            scope.cancel()
        }
    }
}
