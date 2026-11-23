package com.pocketdeck.launcher.data

import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量级按域名传输层熔断器：
 * - 连续发生 [failureThreshold] 次传输层故障（如 SSLHandshakeException / SocketTimeoutException / ConnectException）后
 *   对目标域名进入 [cooldownMs] 冷却期，避免大库刮削时对被拦截或不可达主机反复阻塞数十分钟并刷屏报错。
 * - 仅统计网络/TLS 传输故障；任何合法 HTTP 响应（含 404/429/5xx）均视为主机可达并立即重置连续失败计数。
 * - 冷却到期后采用单探针半开（Half-Open）机制：仅放行 1 次试探请求，成功即全量恢复，失败则立刻续期冷却，杜绝多 Worker 惊群。
 */
class HostCircuitBreaker(
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val probeGraceMs: Long = DEFAULT_PROBE_GRACE_MS,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    private class HostState(
        var consecutiveFailures: Int = 0,
        var openUntilMs: Long = 0L,
    )

    private val states = ConcurrentHashMap<String, HostState>()

    /** 只读检查目标域名当前是否处于熔断冷却期（不消耗半开试探名额，适用于选路/构造候选 URL）。 */
    fun isCurrentlyOpen(host: String): Boolean {
        val h = normalizeHost(host) ?: return false
        val st = states[h] ?: return false
        return synchronized(st) {
            st.openUntilMs > clockMs()
        }
    }

    /** 只读检查 URL 对应域名当前是否处于熔断冷却期。 */
    fun isUrlCurrentlyOpen(urlStr: String): Boolean {
        val host = extractHost(urlStr) ?: return false
        return isCurrentlyOpen(host)
    }

    /**
     * 请求发出前的熔断闸门：
     * - 冷却期内返回 `true`（应立即跳过该请求）；
     * - 冷却刚到期时仅放行首个试探请求（返回 `false`），并在 [probeGraceMs] 窗口内继续挡住并发惊群。
     */
    fun shouldSkip(host: String): Boolean {
        val h = normalizeHost(host) ?: return false
        val st = states[h] ?: return false
        return synchronized(st) {
            if (st.openUntilMs <= 0L) return@synchronized false
            val now = clockMs()
            if (now < st.openUntilMs) return@synchronized true
            // 冷却结束：进入半开状态，放行本次单探针并在探针耗时窗口内防并发惊群
            st.consecutiveFailures = (failureThreshold - 1).coerceAtLeast(0)
            st.openUntilMs = if (probeGraceMs > 0L) now + probeGraceMs else 0L
            false
        }
    }

    /** URL 版本的请求前熔断闸门。 */
    fun shouldSkipUrl(urlStr: String): Boolean {
        val host = extractHost(urlStr) ?: return false
        return shouldSkip(host)
    }

    /** 主机成功完成 TCP+TLS 并收到任意 HTTP 响应时调用，清零连续失败并立即解除熔断。 */
    fun recordSuccess(host: String) {
        val h = normalizeHost(host) ?: return
        val st = states[h] ?: return
        synchronized(st) {
            st.consecutiveFailures = 0
            st.openUntilMs = 0L
        }
    }

    fun recordSuccessForUrl(urlStr: String) {
        val host = extractHost(urlStr) ?: return
        recordSuccess(host)
    }

    /**
     * 记录一次传输层故障（SSL 握手失败 / 连接被重置 / 读写超时 / 无法连接等）。
     * @return 当且仅当本次故障恰好触发熔断开启或半开试探失败重开时返回 `true`（供调用方打一条汇总日志，避免刷屏）。
     */
    fun recordTransportFailure(host: String): Boolean {
        val h = normalizeHost(host) ?: return false
        val st = states.getOrPut(h) { HostState() }
        return synchronized(st) {
            st.consecutiveFailures += 1
            if (st.consecutiveFailures >= failureThreshold) {
                val now = clockMs()
                val alreadyLongOpen = st.openUntilMs > now + probeGraceMs
                st.openUntilMs = now + cooldownMs
                !alreadyLongOpen
            } else {
                false
            }
        }
    }

    fun recordTransportFailureForUrl(urlStr: String): Boolean {
        val host = extractHost(urlStr) ?: return false
        return recordTransportFailure(host)
    }

    fun cooldownSeconds(): Long = (cooldownMs / 1000L).coerceAtLeast(1L)

    /** 网络切换、重连或用户手动启动新一轮刮削时重置全部域名熔断状态。 */
    fun resetAll() {
        states.clear()
    }

    companion object {
        const val DEFAULT_FAILURE_THRESHOLD = 3
        const val DEFAULT_COOLDOWN_MS = 120_000L
        const val DEFAULT_PROBE_GRACE_MS = 20_000L

        fun extractHost(urlStr: String): String? = runCatching {
            normalizeHost(URL(urlStr).host)
        }.getOrNull()

        private fun normalizeHost(host: String?): String? =
            host?.trim()?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() }
    }
}
