package com.pocketdeck.launcher.lan

import kotlinx.coroutines.launch

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.pocketdeck.launcher.data.AppTheme
import com.pocketdeck.launcher.data.ArchiveManager
import com.pocketdeck.launcher.data.CategorySortOrder
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.FileTaskKind
import com.pocketdeck.launcher.data.FileTaskSource
import com.pocketdeck.launcher.data.GameRepository
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.NetworkStatus
import com.pocketdeck.launcher.data.PlatformCatalog
import com.pocketdeck.launcher.data.ScrapeLogStore
import com.pocketdeck.launcher.data.RomScanner
import com.pocketdeck.launcher.data.ThemeAccent
import com.pocketdeck.launcher.data.UserData
import com.pocketdeck.launcher.launch.GameLauncher
import com.pocketdeck.launcher.ui.theme.deckPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class WebTransferDirection { Upload, Download }

data class WebActiveTransfer(
    val id: String,
    val clientIp: String,
    val userAgent: String,
    val direction: WebTransferDirection,
    val fileName: String,
    val filePath: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val startedAtMs: Long,
)

data class WebTransferHistoryItem(
    val id: String,
    val clientIp: String,
    val userAgent: String,
    val direction: WebTransferDirection,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val avgSpeedBytesPerSec: Long,
    val completedAtMs: Long,
    val success: Boolean = true,
)

data class WebClientInfo(
    val ip: String,
    val userAgent: String,
    val connectedAtMs: Long,
    val lastActiveMs: Long,
    val sessionCount: Int = 1,
    val uploadSpeedBps: Long = 0L,
    val downloadSpeedBps: Long = 0L,
    val activeTransfers: List<WebActiveTransfer> = emptyList(),
)

data class WebThemeConfig(
    val isDark: Boolean = false,
    val accent: ThemeAccent = ThemeAccent.Sunset,
) {
    fun toJson(): JSONObject {
        val pal = deckPalette(accent, isDark)
        fun hex(c: androidx.compose.ui.graphics.Color): String {
            val rgb = (c.value shr 32).toInt() and 0xFFFFFF
            return String.format(Locale.US, "#%06X", rgb)
        }
        /** 带透明度的颜色以 CSS rgba() 输出（accentSoft / flow 色块均含 alpha）。 */
        fun rgba(c: androidx.compose.ui.graphics.Color): String {
            if (c.alpha >= 0.999f) return hex(c)
            val r = (c.red * 255f + 0.5f).toInt().coerceIn(0, 255)
            val g = (c.green * 255f + 0.5f).toInt().coerceIn(0, 255)
            val b = (c.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
            return String.format(Locale.US, "rgba(%d,%d,%d,%.3f)", r, g, b, c.alpha)
        }
        return JSONObject().apply {
            put("isDark", isDark)
            put("accent", accent.name)
            put("primary", hex(pal.ruby))
            put("glow", hex(pal.accentGlow))
            put("tertiary", hex(pal.accentTertiary))
            put("bg", hex(pal.background))
            put("panel", hex(pal.dialogBg))
            put("surf", hex(pal.surface))
            put("border", hex(pal.dialogBorder))
            put("text", hex(pal.textPrimary))
            put("muted", hex(pal.textSecondary))
            // Sunset Arcade 设计令牌：Web 端与掌机主题（含亮/暗）保持一致
            put("label", hex(pal.textLabel))
            put("brandStart", hex(pal.brandStart))
            put("brandMid", hex(pal.brandMid))
            put("brandEnd", hex(pal.brandEnd))
            put("surfaceAlt", hex(pal.surfaceAlt))
            put("line", hex(pal.line))
            put("accentSoft", rgba(pal.accentSoft))
            put("flowA", rgba(pal.flowA))
            put("flowB", rgba(pal.flowB))
            put("flowC", rgba(pal.flowC))
        }
    }
}

/**
 * 局域网 Web 游戏库管理服务端（默认关闭，独立生命周期，每次启动生成 4 位动态访问密码 + 防暴力猜测限流）。
 * - 封面与元数据 100% 由掌机自身提供/代抓缓存，Web 端不直连任何外网域名
 * - 支持游戏风格 UI 与 App 亮/暗主题及主题色实时同步
 * - 支持标签分类编辑同步（严格格式校验）与完整文件管理能力
 */
class WebManagerServer(
    private val context: Context,
    private val repo: GameRepository,
    private val covers: CoverSource,
    private val metadataRepo: MetadataRepository,
) {
    companion object {
        const val DEFAULT_PORT = 8686
        private const val SESSION_COOKIE = "pd_session"
        private const val SESSION_TTL_MS = 12 * 60 * 60 * 1000L
        private const val ATTEMPT_WINDOW_MS = 60_000L
        private const val MAX_ATTEMPTS_PER_WINDOW = 5
        private const val GLOBAL_WINDOW_MS = 5 * 60_000L
        private const val MAX_GLOBAL_FAILURES = 15
        private const val GLOBAL_LOCKOUT_MS = 120_000L
        private const val MAX_COVER_UPLOAD_BYTES = 8 * 1024 * 1024L
        private const val MAX_CANDIDATE_TOKENS = 200
        private const val MAX_WEB_HISTORY_ITEMS = 100
        private const val STALL_ZERO_MS = 1_500L
        private const val HTTP_TX_WINDOW_MS = 2_000L
        private const val PROGRESS_SAMPLE_MS = 200L
        private const val RANGE_MERGE_WINDOW_MS = 4_000L
    }

    private class SessionRecord(
        val token: String,
        val ip: String,
        val userAgent: String,
        val connectedAtMs: Long,
        @Volatile var lastActiveMs: Long,
        val expiresAtMs: Long,
    )

    private class ActiveTransferTracker(
        val id: String,
        val clientIp: String,
        val userAgent: String,
        val direction: WebTransferDirection,
        val fileName: String,
        val filePath: String,
        @Volatile var totalBytes: Long,
        val startedAtMs: Long,
        val cancelAction: () -> Unit = {},
    ) {
        @Volatile var bytesTransferred: Long = 0L
        @Volatile var speedBps: Long = 0L
        @Volatile var lastSampleMs: Long = startedAtMs
        @Volatile var lastSampleBytes: Long = 0L
        @Volatile var lastChunkMs: Long = startedAtMs

        fun onProgress(bytes: Long, total: Long): Boolean {
            val now = System.currentTimeMillis()
            bytesTransferred = bytes
            if (total > 0L) totalBytes = total
            lastChunkMs = now
            val elapsed = now - lastSampleMs
            if (elapsed >= PROGRESS_SAMPLE_MS) {
                val delta = (bytes - lastSampleBytes).coerceAtLeast(0L)
                val inst = (delta * 1000L) / elapsed
                speedBps = if (speedBps == 0L) inst else ((speedBps * 6L + inst * 4L) / 10L)
                lastSampleMs = now
                lastSampleBytes = bytes
                return true
            }
            return false
        }

        fun avgSpeedBps(endMs: Long, finalBytes: Long): Long {
            val elapsed = (endMs - startedAtMs).coerceAtLeast(1L)
            return if (finalBytes > 0L) (finalBytes * 1000L) / elapsed else 0L
        }

        fun toSnapshot(): WebActiveTransfer = WebActiveTransfer(
            id = id,
            clientIp = clientIp,
            userAgent = userAgent,
            direction = direction,
            fileName = fileName,
            filePath = filePath,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            speedBytesPerSec = speedBps,
            startedAtMs = startedAtMs,
        )
    }

    private class IpAuthState {
        val attemptTimestamps = ArrayDeque<Long>()
        var consecutiveFailures: Int = 0
        var lockedUntilMs: Long = 0L
    }

    private data class CoverCandidateToken(
        val url: String,
        val nodeId: String,
        val createdMs: Long,
    )

    private val random = SecureRandom()
    private var httpServer: HttpMiniServer? = null
    private val sessions = ConcurrentHashMap<String, SessionRecord>()
    private val activeTransferMap = ConcurrentHashMap<String, ActiveTransferTracker>()
    private val candidateTokens = ConcurrentHashMap<String, CoverCandidateToken>()
    private val authLock = Any()
    private val ipAuthStates = HashMap<String, IpAuthState>()
    private val globalFailures = ArrayDeque<Long>()
    private var globalLockoutUntilMs = 0L

    private val historyFile = File(context.filesDir, "web_transfer_history.json")
    private val historyLock = Any()

    @Volatile
    private var themeConfig = WebThemeConfig()

    private val _connectedClients = MutableStateFlow<List<WebClientInfo>>(emptyList())
    val connectedClients: StateFlow<List<WebClientInfo>> = _connectedClients.asStateFlow()

    private val _activeTransfers = MutableStateFlow<List<WebActiveTransfer>>(emptyList())
    val activeTransfers: StateFlow<List<WebActiveTransfer>> = _activeTransfers.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<WebTransferHistoryItem>>(loadTransferHistory())
    val transferHistory: StateFlow<List<WebTransferHistoryItem>> = _transferHistory.asStateFlow()

    // 常规 HTTP 响应（封面图 / 页面 / API）按客户端 IP 统计 2 秒滑动窗口上行速率
    private val httpTxLock = Any()
    private val httpTxSamples = HashMap<String, ArrayDeque<Pair<Long, Long>>>()
    private var httpTxDecayJob: kotlinx.coroutines.Job? = null
    private var lastHttpTxPublishMs = 0L

    @Volatile
    var dynamicPassword: String = generatePassword()
        private set

    @Volatile
    var activeInterface: LanInterface? = null
        private set

    val isRunning: Boolean get() = httpServer != null
    val boundPort: Int get() = httpServer?.boundPort ?: -1
    val accessUrl: String?
        get() {
            val lan = activeInterface ?: return null
            val port = boundPort
            if (port <= 0) return null
            return "http://${lan.ipAddress}:$port"
        }

    fun updateTheme(appTheme: AppTheme, accent: ThemeAccent, systemDark: Boolean) {
        val isDark = when (appTheme) {
            AppTheme.System -> systemDark
            AppTheme.Light -> false
            AppTheme.Dark -> true
        }
        themeConfig = WebThemeConfig(isDark = isDark, accent = accent)
    }

    fun start(lan: LanInterface, preferredPort: Int = DEFAULT_PORT): Int {
        stop()
        dynamicPassword = generatePassword()
        sessions.clear()
        candidateTokens.clear()
        publishClients()
        synchronized(authLock) {
            ipAuthStates.clear()
            globalFailures.clear()
            globalLockoutUntilMs = 0L
        }
        val server = HttpMiniServer(lan.address, preferredPort) { req, res ->
            handleRequest(req, res)
        }
        val port = server.start()
        httpServer = server
        activeInterface = lan
        return port
    }

    fun stop() {
        httpServer?.stop()
        httpServer = null
        activeInterface = null
        sessions.clear()
        activeTransferMap.clear()
        candidateTokens.clear()
        publishClients()
    }

    /** 刷新动态密码、吊销所有登录态，并强行断开进行中的传输连接。 */
    fun refreshPassword(): String {
        val next = generatePassword()
        dynamicPassword = next
        sessions.clear()
        httpServer?.closeAllSockets()
        publishClients()
        synchronized(authLock) {
            ipAuthStates.clear()
            globalFailures.clear()
            globalLockoutUntilMs = 0L
        }
        return next
    }

    /** 主动断开指定 IP 的所有 Web 登录会话，并强行关闭其活跃 Socket 以立刻中断传输。 */
    fun kickClient(ip: String) {
        if (ip.isBlank()) return
        var removed = false
        val it = sessions.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.ip == ip) {
                it.remove()
                removed = true
            }
        }
        httpServer?.closeSocketsByIp(ip)
        if (removed) publishClients()
    }

    /** 仅取消并中断指定 ID 的单条上传/下载传输，不影响该客户端的其它任务或登录状态。 */
    fun cancelActiveTransfer(id: String): Boolean {
        val tracker = activeTransferMap[id] ?: return false
        tracker.cancelAction()
        return true
    }

    private fun generatePassword(): String =
        String.format(Locale.US, "%04d", random.nextInt(10_000))

    private fun isAuthenticated(req: HttpRequest): Boolean {
        val token = req.cookies[SESSION_COOKIE] ?: return false
        val rec = sessions[token] ?: return false
        val now = System.currentTimeMillis()
        if (now > rec.expiresAtMs) {
            sessions.remove(token)
            publishClients()
            return false
        }
        val prevActive = rec.lastActiveMs
        rec.lastActiveMs = now
        if (now - prevActive > 5_000L) {
            publishClients()
        }
        return true
    }

    private fun newSessionToken(req: HttpRequest): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        val now = System.currentTimeMillis()
        sessions[token] = SessionRecord(
            token = token,
            ip = req.remoteIp.ifBlank { "unknown" },
            userAgent = summarizeUserAgent(req.headers["user-agent"].orEmpty()),
            connectedAtMs = now,
            lastActiveMs = now,
            expiresAtMs = now + SESSION_TTL_MS,
        )
        publishClients()
        return token
    }

    private fun publishClients() {
        val now = System.currentTimeMillis()
        val activeSessions = sessions.values.filter { now <= it.expiresAtMs }
        val activeTxSnapshots = activeTransferMap.values
            .map { it.toSnapshot() }
            .sortedBy { it.startedAtMs }
        _activeTransfers.value = activeTxSnapshots

        val sessionsByIp = activeSessions.groupBy { it.ip }
        val txByIp = activeTxSnapshots.groupBy { it.clientIp }
        val allIps = (sessionsByIp.keys + txByIp.keys)

        val grouped = allIps.map { ip ->
            val recs = sessionsByIp[ip].orEmpty()
            val txs = txByIp[ip].orEmpty()
            val ua = recs.maxByOrNull { it.lastActiveMs }?.userAgent
                ?.ifBlank { txs.firstOrNull()?.userAgent.orEmpty() }
                ?: txs.firstOrNull()?.userAgent.orEmpty()
            val connectedAt = recs.minOfOrNull { it.connectedAtMs }
                ?: txs.minOfOrNull { it.startedAtMs }
                ?: now
            val lastActive = recs.maxOfOrNull { it.lastActiveMs } ?: now
            val upBps = txs.filter { it.direction == WebTransferDirection.Upload }.sumOf { it.speedBytesPerSec } +
                httpTxBps(ip, now)
            val downBps = txs.filter { it.direction == WebTransferDirection.Download }.sumOf { it.speedBytesPerSec }
            WebClientInfo(
                ip = ip,
                userAgent = ua,
                connectedAtMs = connectedAt,
                lastActiveMs = lastActive,
                sessionCount = recs.size,
                uploadSpeedBps = upBps,
                downloadSpeedBps = downBps,
                activeTransfers = txs,
            )
        }.sortedWith(
            compareByDescending<WebClientInfo> { it.activeTransfers.isNotEmpty() }
                .thenByDescending { it.lastActiveMs },
        )
        _connectedClients.value = grouped
    }

    private fun recordHttpTx(ip: String, bytes: Long) {
        if (ip.isBlank() || bytes <= 0L) return
        val now = System.currentTimeMillis()
        val shouldPublish: Boolean
        synchronized(httpTxLock) {
            val q = httpTxSamples.getOrPut(ip) { ArrayDeque() }
            q.addLast(now to bytes)
            pruneHttpTxLocked(q, now)
            shouldPublish = (now - lastHttpTxPublishMs) >= 180L
            if (shouldPublish) lastHttpTxPublishMs = now
            if (httpTxDecayJob?.isActive != true) {
                httpTxDecayJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    while (true) {
                        kotlinx.coroutines.delay(500L)
                        val t = System.currentTimeMillis()
                        val empty = synchronized(httpTxLock) {
                            val it = httpTxSamples.entries.iterator()
                            while (it.hasNext()) {
                                val entry = it.next()
                                pruneHttpTxLocked(entry.value, t)
                                if (entry.value.isEmpty()) it.remove()
                            }
                            httpTxSamples.isEmpty()
                        }
                        publishClients()
                        if (empty) break
                    }
                }
            }
        }
        if (shouldPublish) publishClients()
    }

    private fun httpTxBps(ip: String, now: Long): Long = synchronized(httpTxLock) {
        val q = httpTxSamples[ip] ?: return@synchronized 0L
        pruneHttpTxLocked(q, now)
        if (q.isEmpty()) return@synchronized 0L
        var sum = 0L
        for ((_, b) in q) sum += b
        val spanMs = (now - q.first().first).coerceIn(500L, HTTP_TX_WINDOW_MS)
        (sum * 1000L) / spanMs
    }

    private fun pruneHttpTxLocked(q: ArrayDeque<Pair<Long, Long>>, now: Long) {
        val cutoff = now - HTTP_TX_WINDOW_MS
        while (q.isNotEmpty() && q.first().first < cutoff) {
            q.removeFirst()
        }
    }

    /** 浏览器暂停或网速归零超过 1.5s 时，将卡住的瞬时速率归零。 */
    fun tickActiveTransfers() {
        if (activeTransferMap.isEmpty()) return
        val now = System.currentTimeMillis()
        var dirty = false
        for (tracker in activeTransferMap.values) {
            if (now - tracker.lastChunkMs >= STALL_ZERO_MS && tracker.speedBps != 0L) {
                tracker.speedBps = 0L
                dirty = true
            }
        }
        if (dirty) publishClients()
    }

    fun clearTransferHistory() {
        synchronized(historyLock) {
            _transferHistory.value = emptyList()
            saveTransferHistory(emptyList())
        }
    }

    fun deleteTransferHistoryItem(id: String) {
        synchronized(historyLock) {
            val cur = _transferHistory.value
            val next = cur.filterNot { it.id == id }
            if (next.size != cur.size) {
                _transferHistory.value = next
                saveTransferHistory(next)
            }
        }
    }

    private fun startTransfer(
        req: HttpRequest,
        direction: WebTransferDirection,
        fileName: String,
        filePath: String,
        totalBytes: Long,
    ): ActiveTransferTracker {
        val now = System.currentTimeMillis()
        val ip = req.remoteIp.ifBlank { "unknown" }
        val ua = summarizeUserAgent(req.headers["user-agent"].orEmpty())
        val id = UUID.randomUUID().toString()
        val tracker = ActiveTransferTracker(
            id = id,
            clientIp = ip,
            userAgent = ua,
            direction = direction,
            fileName = fileName,
            filePath = filePath,
            totalBytes = totalBytes.coerceAtLeast(0L),
            startedAtMs = now,
            cancelAction = req.cancelConnection,
        )
        activeTransferMap[id] = tracker
        publishClients()
        return tracker
    }

    private fun finishTransfer(
        tracker: ActiveTransferTracker,
        transferredBytes: Long,
        success: Boolean,
        recordHistory: Boolean,
        overrideFileSize: Long? = null,
    ) {
        activeTransferMap.remove(tracker.id)
        publishClients()
        if (!recordHistory) return
        val now = System.currentTimeMillis()
        val size = overrideFileSize ?: if (transferredBytes > 0L) transferredBytes else tracker.totalBytes
        recordTransferHistoryItem(
            WebTransferHistoryItem(
                id = tracker.id,
                clientIp = tracker.clientIp,
                userAgent = tracker.userAgent,
                direction = tracker.direction,
                fileName = tracker.fileName,
                filePath = tracker.filePath,
                fileSize = size.coerceAtLeast(0L),
                avgSpeedBytesPerSec = tracker.avgSpeedBps(now, transferredBytes),
                completedAtMs = now,
                success = success,
            ),
        )
    }

    private fun recordTransferHistoryItem(item: WebTransferHistoryItem) {
        synchronized(historyLock) {
            val cur = _transferHistory.value
            val prev = cur.firstOrNull()
            // 合并多线程 Range 分片下载在短时间内产生的连续记录，避免刷屏历史列表
            val next = if (
                prev != null &&
                prev.direction == WebTransferDirection.Download &&
                item.direction == WebTransferDirection.Download &&
                prev.clientIp == item.clientIp &&
                prev.filePath == item.filePath &&
                item.completedAtMs - prev.completedAtMs <= RANGE_MERGE_WINDOW_MS
            ) {
                val merged = prev.copy(
                    fileSize = maxOf(prev.fileSize, item.fileSize),
                    avgSpeedBytesPerSec = maxOf(prev.avgSpeedBytesPerSec, item.avgSpeedBytesPerSec),
                    completedAtMs = item.completedAtMs,
                    success = item.success && prev.success,
                )
                listOf(merged) + cur.drop(1)
            } else {
                (listOf(item) + cur).take(MAX_WEB_HISTORY_ITEMS)
            }
            _transferHistory.value = next
            saveTransferHistory(next)
        }
    }

    private fun loadTransferHistory(): List<WebTransferHistoryItem> = runCatching {
        if (!historyFile.isFile) return emptyList()
        val arr = JSONArray(historyFile.readText(StandardCharsets.UTF_8))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val fileName = o.optString("fileName")
                val filePath = o.optString("filePath")
                if (id.isBlank() || fileName.isBlank() || filePath.isBlank()) continue
                val dir = if (o.optString("direction") == WebTransferDirection.Upload.name) {
                    WebTransferDirection.Upload
                } else {
                    WebTransferDirection.Download
                }
                add(
                    WebTransferHistoryItem(
                        id = id,
                        clientIp = o.optString("clientIp", "unknown"),
                        userAgent = o.optString("userAgent", ""),
                        direction = dir,
                        fileName = fileName,
                        filePath = filePath,
                        fileSize = o.optLong("fileSize", 0L),
                        avgSpeedBytesPerSec = o.optLong("avgSpeedBytesPerSec", 0L),
                        completedAtMs = o.optLong("completedAtMs", 0L),
                        success = o.optBoolean("success", true),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun saveTransferHistory(list: List<WebTransferHistoryItem>) {
        runCatching {
            val arr = JSONArray()
            for (item in list) {
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("clientIp", item.clientIp)
                        put("userAgent", item.userAgent)
                        put("direction", item.direction.name)
                        put("fileName", item.fileName)
                        put("filePath", item.filePath)
                        put("fileSize", item.fileSize)
                        put("avgSpeedBytesPerSec", item.avgSpeedBytesPerSec)
                        put("completedAtMs", item.completedAtMs)
                        put("success", item.success)
                    },
                )
            }
            historyFile.writeText(arr.toString(), StandardCharsets.UTF_8)
        }
    }

    private fun summarizeUserAgent(ua: String): String {
        if (ua.isBlank()) return ""
        val browser = when {
            "Edg/" in ua -> "Edge"
            "Chrome/" in ua -> "Chrome"
            "Firefox/" in ua -> "Firefox"
            "Safari/" in ua -> "Safari"
            "curl/" in ua -> "curl"
            else -> ua.take(24)
        }
        val os = when {
            "Macintosh" in ua || "Mac OS" in ua -> "macOS"
            "Windows" in ua -> "Windows"
            "iPhone" in ua || "iPad" in ua -> "iOS"
            "Android" in ua -> "Android"
            "Linux" in ua -> "Linux"
            else -> ""
        }
        return if (os.isNotEmpty() && browser != os) "$browser · $os" else browser
    }

    private fun isAllowedPostOrigin(req: HttpRequest): Boolean {
        val host = req.headers["host"].orEmpty().trim().lowercase()
        val origin = req.headers["origin"].orEmpty().trim()
        if (origin.isNotEmpty() && host.isNotEmpty()) {
            val auth = runCatching { URI(origin).authority?.lowercase() }.getOrNull() ?: return false
            if (auth != host) return false
        } else if (origin.isEmpty()) {
            val referer = req.headers["referer"].orEmpty().trim()
            if (referer.isNotEmpty() && host.isNotEmpty()) {
                val auth = runCatching { URI(referer).authority?.lowercase() }.getOrNull() ?: return false
                if (auth != host) return false
            }
        }
        return true
    }

    private suspend fun handleRequest(req: HttpRequest, res: HttpResponseWriter) {
        res.onBytesSent = { sent -> recordHttpTx(req.remoteIp, sent) }
        if (req.method == "POST" && !isAllowedPostOrigin(req)) {
            res.sendJson(403, """{"ok":false,"error":"cross_origin_forbidden"}""")
            return
        }

        if (req.method == "GET" && req.path == "/") {
            if (isAuthenticated(req)) {
                res.sendText(200, WebManagerUi.renderAppHtml(themeConfig), "text/html; charset=utf-8")
            } else {
                res.sendText(200, WebManagerUi.renderLoginHtml(themeConfig), "text/html; charset=utf-8")
            }
            return
        }

        if (req.method == "GET" && req.path == "/api/theme") {
            val now = System.currentTimeMillis()
            val authed = req.cookies[SESSION_COOKIE]?.let { sessions[it]?.let { r -> now <= r.expiresAtMs } } == true
            res.sendJson(
                200,
                JSONObject()
                    .put("ok", true)
                    .put("theme", themeConfig.toJson())
                    .put("online", NetworkStatus.isOnline(context))
                    .put("authed", authed)
                    .put("filesRevision", repo.filesRevision.value)
                    .put("activeTaskCount", repo.fileTasks.value.size)
                    .toString(),
            )
            return
        }

        if (req.method == "POST" && req.path == "/api/login") {
            handleLogin(req, res)
            return
        }

        if (req.method == "POST" && req.path == "/api/logout") {
            val removed = req.cookies[SESSION_COOKIE]?.let { sessions.remove(it) } != null
            if (removed) publishClients()
            res.sendJson(
                200,
                """{"ok":true}""",
                mapOf("Set-Cookie" to "$SESSION_COOKIE=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0"),
            )
            return
        }

        if (!isAuthenticated(req)) {
            res.sendJson(401, """{"ok":false,"error":"unauthorized"}""")
            return
        }

        when {
            req.method == "GET" && req.path == "/api/info" -> handleInfo(res)
            req.method == "GET" && req.path == "/api/games" -> handleGames(req, res)
            req.method == "GET" && req.path == "/api/games/item" -> handleGameItem(req, res)
            req.method == "GET" && req.path == "/api/games/hash-covers" -> handleGameHashCovers(req, res)
            req.method == "GET" && req.path == "/api/cover" -> handleCover(req, res)
            req.method == "GET" && req.path == "/api/cover-candidate" -> handleCoverCandidate(req, res)
            req.method == "POST" && req.path == "/api/games/rename" -> handleGameRename(req, res)
            req.method == "POST" && req.path == "/api/games/hide" -> handleGameHide(req, res)
            req.method == "POST" && req.path == "/api/games/favorite" -> handleGameFavorite(req, res)
            req.method == "POST" && req.path == "/api/games/categories" -> handleGameCategories(req, res)
            req.method == "POST" && req.path == "/api/games/batch" -> handleGamesBatch(req, res)
            req.method == "POST" && req.path == "/api/games/refresh-meta" -> handleGameRefreshMeta(req, res)
            req.method == "POST" && req.path == "/api/games/scrape-covers" -> handleGameScrapeCovers(req, res)
            req.method == "POST" && req.path == "/api/games/set-cover" -> handleGameSetCover(req, res)
            req.method == "POST" && req.path == "/api/games/upload-cover" -> handleGameUploadCover(req, res)
            req.method == "POST" && req.path == "/api/games/reset-cover" -> handleGameResetCover(req, res)
            req.method == "GET" && req.path == "/api/categories" -> handleGetCategories(res)
            req.method == "POST" && req.path == "/api/categories/save" -> handleSaveCategories(req, res)
            req.method == "GET" && req.path == "/api/files" -> handleListFiles(req, res)
            req.method == "GET" && req.path == "/api/files/download" -> handleDownload(req, res)
            req.method == "GET" && req.path == "/api/files/raw" -> handleRawFile(req, res)
            req.method == "GET" && req.path == "/api/files/text" -> handleReadText(req, res)
            req.method == "POST" && req.path == "/api/files/text" -> handleWriteText(req, res)
            req.method == "GET" && req.path == "/api/files/archive-entries" -> handleArchiveEntries(req, res)
            req.method == "POST" && req.path == "/api/files/extract" -> handleExtract(req, res)
            req.method == "POST" && req.path == "/api/files/compress" -> handleCompress(req, res)
            req.method == "POST" && req.path == "/api/files/upload" -> handleUpload(req, res)
            req.method == "POST" && req.path == "/api/upload" -> handlePlatformUpload(req, res)
            req.method == "POST" && req.path == "/api/files/mkdir" -> handleMkdir(req, res)
            req.method == "POST" && req.path == "/api/files/rename" -> handleFileRename(req, res)
            req.method == "POST" && req.path == "/api/files/move" -> handleFileMove(req, res)
            req.method == "POST" && req.path == "/api/files/copy" -> handleFileCopy(req, res)
            req.method == "POST" && req.path == "/api/files/delete" -> handleFileDelete(req, res)
            req.method == "POST" && req.path == "/api/files/batch-move" -> handleBatchMove(req, res)
            req.method == "POST" && req.path == "/api/files/batch-copy" -> handleBatchCopy(req, res)
            req.method == "POST" && req.path == "/api/files/batch-delete" -> handleBatchDelete(req, res)
            req.method == "GET" && req.path == "/api/files/tasks" -> handleFileTasks(res)
            req.method == "POST" && req.path == "/api/files/tasks/cancel" -> handleCancelFileTask(req, res)
            req.method == "GET" && req.path == "/api/scrape-logs" -> {
                ScrapeLogStore.init(context)
                val arr = JSONArray()
                ScrapeLogStore.readRecentLines(300).forEach { arr.put(it) }
                res.sendJson(
                    200,
                    JSONObject()
                        .put("ok", true)
                        .put("path", ScrapeLogStore.filePath())
                        .put("lines", arr)
                        .toString(),
                )
            }
            req.method == "GET" && req.path == "/api/scrape-logs/download" -> {
                ScrapeLogStore.init(context)
                val bytes = ScrapeLogStore.readRawText().toByteArray(Charsets.UTF_8)
                res.sendBytes(
                    200,
                    bytes,
                    "text/plain; charset=utf-8",
                    extraHeaders = mapOf(
                        "Content-Disposition" to "attachment; filename=\"pocketdeck-scrape.log\"",
                    ),
                )
            }
            req.method == "POST" && req.path == "/api/scrape-logs/clear" -> {
                ScrapeLogStore.init(context)
                ScrapeLogStore.clear()
                res.sendJson(200, """{"ok":true}""")
            }
            req.method == "POST" && req.path == "/api/rescan" -> {
                repo.notifyFilesChanged()
                repo.refresh()
                res.sendJson(200, """{"ok":true}""")
            }
            else -> res.sendJson(404, """{"ok":false,"error":"not_found"}""")
        }
    }

    private fun handleLogin(req: HttpRequest, res: HttpResponseWriter) {
        val ip = req.remoteIp.ifBlank { "unknown" }
        val now = System.currentTimeMillis()
        synchronized(authLock) {
            if (now < globalLockoutUntilMs) {
                val sec = ((globalLockoutUntilMs - now + 999L) / 1000L).coerceAtLeast(1L)
                res.sendJson(429, """{"ok":false,"error":"locked","retryAfterSec":$sec}""")
                return
            }
            val ipState = ipAuthStates.getOrPut(ip) { IpAuthState() }
            if (now < ipState.lockedUntilMs) {
                val sec = ((ipState.lockedUntilMs - now + 999L) / 1000L).coerceAtLeast(1L)
                res.sendJson(429, """{"ok":false,"error":"locked","retryAfterSec":$sec}""")
                return
            }
            while (ipState.attemptTimestamps.isNotEmpty() && now - ipState.attemptTimestamps.first() > ATTEMPT_WINDOW_MS) {
                ipState.attemptTimestamps.removeFirst()
            }
            if (ipState.attemptTimestamps.size >= MAX_ATTEMPTS_PER_WINDOW) {
                val oldest = ipState.attemptTimestamps.first()
                val waitMs = (ATTEMPT_WINDOW_MS - (now - oldest)).coerceAtLeast(1000L)
                ipState.lockedUntilMs = maxOf(ipState.lockedUntilMs, now + waitMs)
                val sec = (waitMs + 999L) / 1000L
                res.sendJson(429, """{"ok":false,"error":"rate_limited","retryAfterSec":$sec}""")
                return
            }
            ipState.attemptTimestamps.addLast(now)
        }

        val body = runCatching { JSONObject(req.readBodyUtf8(4096)) }.getOrNull()
        val pwd = body?.optString("password", "").orEmpty().trim()
        val expected = dynamicPassword
        val match = pwd.length == expected.length &&
            MessageDigest.isEqual(pwd.toByteArray(), expected.toByteArray())
        if (!match) {
            var lockedSec = 0L
            var remaining = 0
            synchronized(authLock) {
                val failNow = System.currentTimeMillis()
                val ipState = ipAuthStates.getOrPut(ip) { IpAuthState() }
                ipState.consecutiveFailures++
                while (globalFailures.isNotEmpty() && failNow - globalFailures.first() > GLOBAL_WINDOW_MS) {
                    globalFailures.removeFirst()
                }
                globalFailures.addLast(failNow)
                if (globalFailures.size >= MAX_GLOBAL_FAILURES) {
                    globalLockoutUntilMs = failNow + GLOBAL_LOCKOUT_MS
                    globalFailures.clear()
                    lockedSec = GLOBAL_LOCKOUT_MS / 1000L
                }
                val lockDuration = when {
                    ipState.consecutiveFailures >= 8 -> 600_000L
                    ipState.consecutiveFailures >= 5 -> 120_000L
                    ipState.consecutiveFailures >= 3 -> 30_000L
                    else -> 0L
                }
                if (lockDuration > 0L) {
                    ipState.lockedUntilMs = maxOf(ipState.lockedUntilMs, failNow + lockDuration)
                    lockedSec = maxOf(lockedSec, lockDuration / 1000L)
                } else {
                    val untilNextLock = 3 - ipState.consecutiveFailures
                    val windowLeft = (MAX_ATTEMPTS_PER_WINDOW - ipState.attemptTimestamps.size).coerceAtLeast(0)
                    remaining = minOf(untilNextLock, windowLeft).coerceAtLeast(0)
                }
            }
            if (lockedSec > 0L) {
                res.sendJson(429, """{"ok":false,"error":"locked","retryAfterSec":$lockedSec}""")
            } else {
                res.sendJson(401, """{"ok":false,"error":"invalid_password","remainingAttempts":$remaining}""")
            }
            return
        }
        synchronized(authLock) {
            ipAuthStates.remove(ip)
        }
        val token = newSessionToken(req)
        res.sendJson(
            200,
            """{"ok":true}""",
            mapOf("Set-Cookie" to "$SESSION_COOKIE=$token; Path=/; HttpOnly; SameSite=Strict"),
        )
    }

    private fun collectStorageVolumes(roots: List<File>): JSONArray {
        val arr = JSONArray()
        val seenRoots = LinkedHashSet<String>()
        val seenFsKey = LinkedHashSet<String>()

        fun addVolume(dir: File?, kind: String, label: String) {
            if (dir == null || !dir.exists() || !dir.isDirectory) return
            val canon = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (!seenRoots.add(canon)) return
            val stat = runCatching { StatFs(canon) }.getOrNull() ?: return
            val total = stat.totalBytes
            val free = stat.availableBytes
            if (total <= 0L) return
            // 同物理分区去重（例如内部存储下多个 ROM 根目录共享同一文件系统）
            val fsKey = "$kind:$total:${free / (1024L * 1024L)}"
            if (!seenFsKey.add(fsKey)) return
            arr.put(
                JSONObject().apply {
                    put("kind", kind)
                    put("label", label)
                    put("path", canon)
                    put("freeBytes", free)
                    put("totalBytes", total)
                },
            )
        }

        // 1. 内部存储
        val primaryDir = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        val primaryCanon = primaryDir?.let { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
        addVolume(primaryDir, "internal", "Internal")

        // 2. 外置 TF / SD / USB 存储卷（Android 11+ StorageManager）
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                sm.storageVolumes.forEach { vol ->
                    if (!vol.isPrimary && vol.state == Environment.MEDIA_MOUNTED) {
                        val volDir = vol.directory
                        val desc = runCatching { vol.getDescription(context) }.getOrNull().orEmpty()
                            .ifBlank { volDir?.name ?: "TF / SD" }
                        addVolume(volDir, "external", desc)
                    }
                }
            }
        }
        // 3. 兼容部分掌机 ROM 的 TF / USB 挂载路径
        runCatching {
            context.getExternalFilesDirs(null).orEmpty().forEach { appDir ->
                var cur = appDir
                repeat(4) { cur = cur?.parentFile }
                val canon = cur?.let { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
                if (cur != null && canon != null && canon != primaryCanon) {
                    addVolume(cur, "external", cur.name.ifBlank { "TF / SD" })
                }
            }
        }
        runCatching {
            File("/storage").listFiles()?.forEach { vol ->
                val n = vol.name
                if (vol.isDirectory && n != "emulated" && n != "self" && n != "enc_emulated") {
                    addVolume(vol, "external", n)
                }
            }
        }
        // 4. 兜底：若用户配置了上述未覆盖到的挂载根目录，也一并纳入
        for (r in roots) {
            val rCanon = runCatching { r.canonicalPath }.getOrDefault(r.absolutePath)
            val covered = seenRoots.any { sr -> rCanon == sr || rCanon.startsWith("$sr${File.separator}") }
            if (!covered) {
                val isPrimary = primaryCanon != null && (rCanon == primaryCanon || rCanon.startsWith("$primaryCanon${File.separator}"))
                addVolume(r, if (isPrimary) "internal" else "external", r.name.ifBlank { r.absolutePath })
            }
        }
        return arr
    }

    private suspend fun handleInfo(res: HttpResponseWriter) {
        val roots = repo.configuredRoots()
        val u = repo.user.value
        val storageVolumes = collectStorageVolumes(roots)
        val obj = JSONObject().apply {
            put("deviceName", u?.deviceName?.ifBlank { Build.MODEL } ?: Build.MODEL)
            put("model", Build.MODEL.orEmpty())
            put("interface", activeInterface?.labelWithIp.orEmpty())
            put("url", accessUrl.orEmpty())
            put("online", NetworkStatus.isOnline(context))
            put("roots", JSONArray(roots.map { it.absolutePath }))
            put("storage", storageVolumes)
            put("storageVolumes", storageVolumes)
            put("theme", themeConfig.toJson())
        }
        res.sendJson(200, obj.toString())
    }

    private fun gameToJson(g: com.pocketdeck.launcher.data.model.Game): JSONObject {
        val lib = repo.fullLibrary.value
        val u = repo.user.value
        val hidden = u?.hidden.orEmpty()
        val favs = u?.favorites.orEmpty()
        val cats = u?.categories.orEmpty()
        val metaMap = metadataRepo.metadata.value
        val officialNames = metadataRepo.officialNames.value
        val metaRes = metadataRepo.resolutionInfo.value

        val plat = lib?.platform(g.platformId)
            ?: PlatformCatalog.forFolder(g.platformId).let { p ->
                val aspect = u?.platformAspects?.get(p.id)
                if (aspect != null) p.copy(coverAspect = aspect) else p
            }
        val isRom = !g.id.startsWith(GameLauncher.APK_PREFIX)
        val fileSize = if (g.fileSize > 0L || !isRom) g.fileSize else runCatching { File(g.id).length() }.getOrDefault(0L)
        val m = metaMap[g.id]
        val mInfo = metaRes[g.id]
        val cInfo = covers.resolveCoverInfo(g)
        val catIds = cats.filter { g.id in it.games || g.platformId in it.sources }.map { it.id }

        return JSONObject().apply {
            put("id", g.id)
            put("title", g.title)
            put("officialName", officialNames[g.id].orEmpty())
            put("fileName", g.fileName)
            put("platformId", g.platformId)
            put("platformName", plat.shortName)
            put("platformFullName", plat.fullName)
            put("libretroSystem", plat.libretroSystem.orEmpty())
            put("coverAspect", plat.coverAspect.toDouble())
            put("isRom", isRom)
            put("hidden", g.id in hidden)
            put("favorite", g.id in favs)
            put("size", fileSize)
            put("lastModifiedMs", g.lastModifiedMs)
            put("hasCover", covers.hasLocalOrCachedCover(g))
            put("hasCustomCover", !g.customCover.isNullOrBlank())
            put("coverUrl", covers.publicCoverUrlForWeb(g).orEmpty())
            put("coverVersion", covers.coverVersionForWeb(g))
            put("categories", JSONArray(catIds))
            put(
                "metadata",
                JSONObject().apply {
                    put("releaseDate", m?.releaseDate.orEmpty())
                    put("genre", m?.genre.orEmpty())
                    put("developer", m?.developer.orEmpty())
                    put("publisher", m?.publisher.orEmpty())
                },
            )
            put(
                "metaInfo",
                JSONObject().apply {
                    put("providers", JSONArray(mInfo?.providers.orEmpty()))
                    put("matchModes", JSONArray(mInfo?.matchModes?.map { it.name }.orEmpty()))
                    put("crc32", mInfo?.crc32.orEmpty())
                },
            )
            put("coverProvider", cInfo?.providerId.orEmpty())
            put("coverMode", cInfo?.matchMode?.name.orEmpty())
        }
    }

    private fun buildGamesStatsJson(
        allGamesOverride: List<com.pocketdeck.launcher.data.model.Game>? = null,
    ): JSONObject {
        val lib = repo.fullLibrary.value
        val u = repo.user.value
        val hidden = u?.hidden.orEmpty()
        val favs = u?.favorites.orEmpty()
        val allGames = allGamesOverride ?: lib?.games?.values?.flatten().orEmpty()
        val platIds = HashSet<String>()
        val platNames = sortedSetOf<String>()
        var favCount = 0
        var hasHidden = false
        for (g in allGames) {
            platIds += g.platformId
            val pName = lib?.platform(g.platformId)?.shortName
                ?: PlatformCatalog.forFolder(g.platformId).shortName
            platNames += pName
            if (g.id in favs) favCount++
            if (!hasHidden && g.id in hidden) hasHidden = true
        }
        return JSONObject().apply {
            put("totalGames", allGames.size)
            put("platformCount", platIds.size)
            put("favCount", favCount)
            put("hasHidden", hasHidden)
            put("platforms", JSONArray(platNames.toList()))
        }
    }

    private suspend fun handleGameItem(req: HttpRequest, res: HttpResponseWriter) {
        val id = req.query["id"].orEmpty()
        val game = repo.fullLibrary.value?.byId?.get(id)
        if (game == null) {
            res.sendJson(404, """{"ok":false,"error":"game_not_found"}""")
            return
        }
        if (req.query["ensureMeta"] == "1" && !game.id.startsWith(GameLauncher.APK_PREFIX)) {
            val plat = repo.fullLibrary.value?.platform(game.platformId) ?: PlatformCatalog.forFolder(game.platformId)
            metadataRepo.ensureGameMetadata(game, plat)
        }
        val fresh = repo.fullLibrary.value?.byId?.get(id) ?: game
        val root = JSONObject().apply {
            put("ok", true)
            put("game", gameToJson(fresh))
            put("stats", buildGamesStatsJson())
        }
        res.sendJson(200, root.toString())
    }

    private suspend fun handleGameHashCovers(req: HttpRequest, res: HttpResponseWriter) {
        val id = req.query["id"].orEmpty()
        val game = repo.fullLibrary.value?.byId?.get(id)
        if (game == null) {
            res.sendJson(404, """{"ok":false,"error":"game_not_found"}""")
            return
        }
        val plat = repo.fullLibrary.value?.platform(game.platformId) ?: PlatformCatalog.forFolder(game.platformId)
        val hits = metadataRepo.searchByHashForWeb(game, plat, covers)
        val arr = JSONArray()
        for (h in hits) {
            val u = h.coverUrl?.takeIf { it.isNotBlank() } ?: continue
            arr.put(
                JSONObject().apply {
                    put("title", h.title)
                    put("providerId", h.providerId)
                    put("url", u)
                    put("fallbackUrl", h.fallbackCoverUrls.firstOrNull().orEmpty())
                    put("matchedByHash", true)
                },
            )
        }
        res.sendJson(200, JSONObject().put("ok", true).put("candidates", arr).toString())
    }

    private fun handleGames(req: HttpRequest, res: HttpResponseWriter) {
        val lib = repo.fullLibrary.value
        val u = repo.user.value
        val hidden = u?.hidden.orEmpty()
        val favs = u?.favorites.orEmpty()
        val cats = u?.categories.orEmpty()
        val metaMap = metadataRepo.metadata.value
        val officialNames = metadataRepo.officialNames.value

        val allGames = lib?.games?.values?.flatten().orEmpty().sortedBy { it.title.lowercase() }
        val statsJson = buildGamesStatsJson(allGames)

        val q = req.query["q"].orEmpty().trim().lowercase()
        val vis = req.query["vis"].orEmpty().trim().ifBlank { "all" }
        val platFilter = req.query["plat"].orEmpty().trim()
        val catFilter = req.query["cat"].orEmpty().trim()
        val catObj = if (catFilter.isNotEmpty()) cats.firstOrNull { it.id == catFilter } else null

        val filtered = allGames.filter { g ->
            when (vis) {
                "visible" -> if (g.id in hidden) return@filter false
                "hidden" -> if (g.id !in hidden) return@filter false
                "fav" -> if (g.id !in favs) return@filter false
                "nocover" -> if (covers.hasLocalOrCachedCover(g)) return@filter false
            }
            val pName = lib?.platform(g.platformId)?.shortName
                ?: PlatformCatalog.forFolder(g.platformId).shortName
            if (platFilter.isNotEmpty() && pName != platFilter) return@filter false
            if (catFilter.isNotEmpty()) {
                val inCat = catObj != null && (g.id in catObj.games || g.platformId in catObj.sources)
                if (!inCat) return@filter false
            }
            if (q.isNotEmpty()) {
                val m = metaMap[g.id]
                val hay = buildString {
                    append(g.title).append(' ')
                    append(officialNames[g.id].orEmpty()).append(' ')
                    append(g.fileName).append(' ')
                    append(pName).append(' ')
                    append(m?.developer.orEmpty()).append(' ')
                    append(m?.publisher.orEmpty()).append(' ')
                    append(m?.genre.orEmpty())
                }.lowercase()
                if (q !in hay) return@filter false
            }
            true
        }

        val total = filtered.size
        val paged = req.query.containsKey("limit") || req.query.containsKey("offset")
        val offset = req.query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = if (paged) {
            req.query["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 60
        } else {
            total.coerceAtLeast(1)
        }
        val slice = if (offset >= total) {
            emptyList()
        } else {
            filtered.subList(offset, (offset + limit).coerceAtMost(total))
        }
        val hasMore = offset + slice.size < total

        val arr = JSONArray()
        for (g in slice) {
            arr.put(gameToJson(g))
        }
        val root = JSONObject().apply {
            put("ok", true)
            put("games", arr)
            put("offset", offset)
            put("limit", limit)
            put("total", total)
            put("hasMore", hasMore)
            put("stats", statsJson)
        }
        res.sendJson(200, root.toString())
    }

    private suspend fun handleCover(req: HttpRequest, res: HttpResponseWriter) {
        val id = req.query["id"].orEmpty()
        val game = repo.fullLibrary.value?.byId?.get(id)
        if (game == null) {
            res.sendText(404, "No game")
            return
        }
        val latestCustom = repo.user.value?.covers?.get(id)
        val effectiveGame = if (latestCustom != game.customCover) game.copy(customCover = latestCustom) else game
        val resolved = covers.resolveWebCoverBytes(effectiveGame)
        if (resolved == null) {
            res.sendText(404, "No cover")
            return
        }
        val bytes = resolved.second
        val crc = java.util.zip.CRC32().apply { update(bytes) }.value
        val etag = "\"${bytes.size}-${crc.toString(16)}\""
        val versionParam = req.query["v"]?.trim().orEmpty()
        val hasVersion = versionParam.isNotEmpty() && versionParam != "0"
        val cacheHeaders = mapOf(
            "ETag" to etag,
            "Cache-Control" to if (hasVersion) "private, max-age=31536000, immutable" else "private, max-age=604800",
        )
        val ifNoneMatch = req.headers["if-none-match"]?.trim()
        if (ifNoneMatch != null && (ifNoneMatch == etag || ifNoneMatch == "*" || etag in ifNoneMatch)) {
            res.sendBytes(304, ByteArray(0), resolved.first, extraHeaders = cacheHeaders)
            return
        }
        res.sendBytes(
            200,
            bytes,
            resolved.first,
            extraHeaders = cacheHeaders,
        )
    }

    private suspend fun handleCoverCandidate(req: HttpRequest, res: HttpResponseWriter) {
        val token = req.query["token"].orEmpty()
        val entry = candidateTokens[token]
        if (entry == null) {
            res.sendText(404, "Expired")
            return
        }
        val downloaded = covers.downloadImageBytes(entry.url)
        if (downloaded == null) {
            res.sendText(404, "Unavailable")
            return
        }
        res.sendBytes(200, downloaded.second, downloaded.first)
    }

    private suspend fun awaitUserSync(predicate: (UserData) -> Boolean) {
        withTimeoutOrNull(500L) {
            repo.user.first { u -> u != null && predicate(u) }
        }
    }

    private suspend fun handleGameRename(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val id = body?.optString("id", "").orEmpty()
        val title = body?.optString("title", "").orEmpty().trim().take(200)
        if (id.isBlank() || repo.fullLibrary.value?.byId?.containsKey(id) != true) {
            res.sendJson(404, """{"ok":false,"error":"game_not_found"}""")
            return
        }
        val existingCover = repo.user.value?.covers?.get(id)
        val targetName = title.ifBlank { null }
        repo.saveGameEdits(id, targetName, existingCover)
        awaitUserSync { it.renames[id] == targetName }
        val fresh = withTimeoutOrNull(500L) {
            repo.fullLibrary.first { lib ->
                val g = lib?.byId?.get(id) ?: return@first false
                targetName == null || g.title == targetName
            }?.byId?.get(id)
        } ?: repo.fullLibrary.value?.byId?.get(id)
        val root = JSONObject().put("ok", true).put("stats", buildGamesStatsJson())
        if (fresh != null) root.put("game", gameToJson(fresh))
        res.sendJson(200, root.toString())
    }

    private suspend fun handleGameHide(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val id = body?.optString("id", "").orEmpty()
        val hidden = body?.optBoolean("hidden", false) ?: false
        if (id.isBlank() || repo.fullLibrary.value?.byId?.containsKey(id) != true) {
            res.sendJson(404, """{"ok":false,"error":"game_not_found"}""")
            return
        }
        if (hidden) repo.hide(id) else repo.unhide(id)
        awaitUserSync { (id in it.hidden) == hidden }
        val fresh = repo.fullLibrary.value?.byId?.get(id)
        val root = JSONObject().put("ok", true).put("stats", buildGamesStatsJson())
        if (fresh != null) root.put("game", gameToJson(fresh))
        res.sendJson(200, root.toString())
    }

    private suspend fun handleGameFavorite(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val id = body?.optString("id", "").orEmpty()
        if (id.isBlank() || repo.fullLibrary.value?.byId?.containsKey(id) != true) {
            res.sendJson(404, """{"ok":false,"error":"game_not_found"}""")
            return
        }
        val wantFav = if (body?.has("favorite") == true) body.optBoolean("favorite", true) else null
        val currentlyFav = id in repo.user.value?.favorites.orEmpty()
        val targetFav = wantFav ?: !currentlyFav
        if (wantFav == null || wantFav != currentlyFav) {
            repo.toggleFavorite(id)
            awaitUserSync { (id in it.favorites) == targetFav }
        }
        val fresh = repo.fullLibrary.value?.byId?.get(id)
        val root = JSONObject()
            .put("ok", true)
            .put("favorite", targetFav)
            .put("stats", buildGamesStatsJson())
        if (fresh != null) root.put("game", gameToJson(fresh))
        res.sendJson(200, root.toString())
    }

    private suspend fun handleGameCategories(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val id = body?.optString("id", "").orEmpty().trim()
        val catArr = body?.optJSONArray("categoryIds")
        if (id.isBlank() || catArr == null) {
            res.sendJson(400, """{"ok":false,"error":"invalid_params"}""")
            return
        }
        if (repo.fullLibrary.value?.byId?.containsKey(id) != true) {
            res.sendJson(404, """{"ok":false,"error":"game_not_found"}""")
            return
        }
        val wantedIds = HashSet<String>()
        for (i in 0 until catArr.length()) {
            val cid = (catArr.opt(i) as? String)?.trim().orEmpty()
            if (cid.isNotBlank()) wantedIds += cid
        }
        repo.updatePrefs { u ->
            u.copy(
                categories = u.categories.map { c ->
                    val nextGames = if (c.id in wantedIds) c.games + id else c.games - id
                    c.copy(games = nextGames)
                },
            )
        }
        awaitUserSync { u -> u.categories.all { c -> (id in c.games) == (c.id in wantedIds) } }
        val fresh = repo.fullLibrary.value?.byId?.get(id)
        val root = JSONObject().put("ok", true).put("stats", buildGamesStatsJson())
        if (fresh != null) root.put("game", gameToJson(fresh))
        res.sendJson(200, root.toString())
    }

    /** I7: 批量游戏操作（hide / unhide / setCategories / rescrape）。 */
    private suspend fun handleGamesBatch(req: HttpRequest, res: HttpResponseWriter) {
        val parsed = WebBatchValidator.parse(req.readBodyUtf8())
        if (parsed == null) {
            res.sendJson(400, """{"ok":false,"error":"invalid_batch_payload"}""")
            return
        }
        val lib = repo.fullLibrary.value
        val validIds = parsed.ids.filter { lib?.byId?.containsKey(it) == true }
        if (validIds.isEmpty()) {
            res.sendJson(404, """{"ok":false,"error":"no_valid_games"}""")
            return
        }
        when (parsed.action) {
            "hide" -> {
                repo.updatePrefs { u -> u.copy(hidden = u.hidden + validIds) }
                awaitUserSync { u -> validIds.all { it in u.hidden } }
            }
            "unhide" -> {
                repo.updatePrefs { u -> u.copy(hidden = u.hidden - validIds.toSet()) }
                awaitUserSync { u -> validIds.none { it in u.hidden } }
            }
            "setCategories" -> {
                val wanted = parsed.categoryIds.toSet()
                val idSet = validIds.toSet()
                repo.updatePrefs { u ->
                    u.copy(
                        categories = u.categories.map { c ->
                            if (c.id in wanted) c.copy(games = c.games + idSet) else c
                        },
                    )
                }
                awaitUserSync { u -> u.categories.filter { it.id in wanted }.all { c -> idSet.all { it in c.games } } }
            }
            "rescrape" -> {
                if (!NetworkStatus.isOnline(context)) {
                    res.sendJson(200, """{"ok":false,"error":"offline"}""")
                    return
                }
                val targets = validIds.filterNot { it.startsWith(GameLauncher.APK_PREFIX) }.take(50).mapNotNull { lib?.byId?.get(it) }
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    for (g in targets) {
                        val plat = lib?.platform(g.platformId) ?: PlatformCatalog.forFolder(g.platformId)
                        metadataRepo.refreshGame(g, plat)
                        val off = metadataRepo.officialNames.value[g.id]
                        covers.fetchWebCoverNow(g, plat, off)
                    }
                }
            }
            else -> {
                res.sendJson(400, """{"ok":false,"error":"unsupported_action"}""")
                return
            }
        }
        res.sendJson(
            200,
            JSONObject()
                .put("ok", true)
                .put("count", validIds.size)
                .put("stats", buildGamesStatsJson())
                .toString(),
        )
    }

    /** I7: 按平台自动归类上传 ROM（POST /api/upload?platform=<id>&name=<filename>）并触发增量扫描。 */
    private suspend fun handlePlatformUpload(req: HttpRequest, res: HttpResponseWriter) {
        val platformId = req.query["platform"].orEmpty().trim().lowercase()
        val rawName = req.query["name"].orEmpty().trim()
        if (!WebBatchValidator.isSafePlatformId(platformId)) {
            res.sendJson(400, """{"ok":false,"error":"invalid_platform"}""")
            return
        }
        val cleanName = sanitizeFileName(rawName)
        if (cleanName == null) {
            res.sendJson(400, """{"ok":false,"error":"invalid_file_name"}""")
            return
        }
        val roots = repo.configuredRoots()
        val primaryRoot = roots.firstOrNull { it.isDirectory } ?: roots.firstOrNull()
        if (primaryRoot == null) {
            res.sendJson(400, """{"ok":false,"error":"no_rom_root"}""")
            return
        }
        val existingPlatDir = roots.asSequence()
            .map { File(it, platformId) }
            .firstOrNull { it.isDirectory }
        val targetDir = existingPlatDir ?: File(primaryRoot, platformId).apply { mkdirs() }
        if (!targetDir.isDirectory) {
            res.sendJson(500, """{"ok":false,"error":"mkdir_failed"}""")
            return
        }
        if (req.contentLength < 0L && !req.isChunked) {
            res.sendJson(400, """{"ok":false,"error":"empty_body"}""")
            return
        }
        val finalTarget = uniqueFile(targetDir, cleanName)
        val partFile = File(targetDir, ".upload_${System.currentTimeMillis()}_${finalTarget.name}.part")
        val uploadTask = repo.beginFileTask(
            kind = FileTaskKind.Upload,
            source = FileTaskSource.Web,
            title = finalTarget.name,
            detail = platformId,
            clientIp = req.remoteIp,
            totalBytes = req.contentLength.coerceAtLeast(0L),
            cancellable = true,
            onCancel = req.cancelConnection,
        )
        val tracker = startTransfer(
            req = req,
            direction = WebTransferDirection.Upload,
            fileName = finalTarget.name,
            filePath = finalTarget.absolutePath,
            totalBytes = req.contentLength.coerceAtLeast(0L),
        )
        var writtenBytes = 0L
        var ok = false
        try {
            ok = runCatching {
                partFile.outputStream().use { out ->
                    val written = req.copyBodyTo(out) { copied ->
                        writtenBytes = copied
                        if (tracker.onProgress(copied, tracker.totalBytes)) {
                            repo.updateFileTask(uploadTask.id, bytesDone = copied, totalBytes = tracker.totalBytes)
                            publishClients()
                        }
                    }
                    writtenBytes = written
                    check(req.isChunked || written == req.contentLength) { "Incomplete upload: $written/${req.contentLength}" }
                }
                if (!partFile.renameTo(finalTarget)) {
                    partFile.copyTo(finalTarget, overwrite = true)
                    partFile.delete()
                }
                true
            }.getOrDefault(false)
        } finally {
            if (!ok) runCatching { partFile.delete() }
            finishTransfer(tracker, writtenBytes, ok, recordHistory = true)
            repo.endFileTask(uploadTask.id)
        }
        if (!ok) {
            res.sendJson(500, """{"ok":false,"error":"upload_failed"}""")
            return
        }
        repo.refresh()
        res.sendJson(
            200,
            JSONObject()
                .put("ok", true)
                .put("platform", platformId)
                .put("name", finalTarget.name)
                .put("path", finalTarget.absolutePath)
                .put("size", writtenBytes)
                .toString(),
        )
    }

    private suspend fun handleGameRefreshMeta(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val id = body?.optString("id", "").orEmpty()
        val game = repo.fullLibrary.value?.byId?.get(id)
        if (game == null) {
            res.sendJson(404, """{"ok":false,"error":"game_not_found"}""")
            return
        }
        if (game.id.startsWith(GameLauncher.APK_PREFIX)) {
            res.sendJson(200, JSONObject().put("ok", true).put("metaUpdated", false).put("coverUpdated", false).put("game", gameToJson(game)).put("stats", buildGamesStatsJson()).toString())
            return
        }
        if (!NetworkStatus.isOnline(context)) {
            res.sendJson(200, JSONObject().put("ok", false).put("error", "offline").put("game", gameToJson(game)).toString())
            return
        }
        val plat = repo.fullLibrary.value?.platform(game.platformId) ?: PlatformCatalog.forFolder(game.platformId)
        val metaUpdated = metadataRepo.refreshGame(game, plat)
        val officialName = metadataRepo.officialNames.value[game.id]
        val coverUpdated = covers.fetchWebCoverNow(game.copy(customCover = null), plat, officialName)
        if (coverUpdated && !game.customCover.isNullOrBlank()) {
            val dir = File(context.filesDir, "custom_covers")
            val prevCover = repo.user.value?.covers?.get(id)
            if (prevCover != null && prevCover.startsWith(dir.absolutePath)) {
                runCatching { File(prevCover).delete() }
            }
            val existingName = repo.user.value?.renames?.get(id)
            repo.saveGameEdits(id, existingName, null)
            withTimeoutOrNull(500L) {
                repo.fullLibrary.first { it?.byId?.get(id)?.customCover == null }
            }
        }
        val freshGame = (repo.fullLibrary.value?.byId?.get(id) ?: game).let {
            if (coverUpdated) it.copy(customCover = null) else it
        }
        res.sendJson(
            200,
            JSONObject().apply {
                put("ok", true)
                put("metaUpdated", metaUpdated)
                put("coverUpdated", coverUpdated)
                put("game", gameToJson(freshGame))
                put("stats", buildGamesStatsJson())
            }.toString(),
        )
    }

    private suspend fun handleGameScrapeCovers(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val id = body?.optString("id", "").orEmpty()
        val game = repo.fullLibrary.value?.byId?.get(id)
        if (game == null) {
            res.sendJson(404, """{"ok":false,"error":"game_not_found"}""")
            return
        }
        if (!NetworkStatus.isOnline(context)) {
            res.sendJson(200, JSONObject().put("ok", false).put("error", "offline").put("candidates", JSONArray()).toString())
            return
        }
        val plat = repo.fullLibrary.value?.platform(game.platformId) ?: PlatformCatalog.forFolder(game.platformId)
        val officialName = metadataRepo.officialNames.value[game.id]
        val list = covers.scrapeAllForPicker(game, plat, officialName).take(18)

        if (candidateTokens.size > MAX_CANDIDATE_TOKENS) {
            val cutoff = System.currentTimeMillis() - 10 * 60_000L
            candidateTokens.entries.removeIf { it.value.createdMs < cutoff }
        }
        val arr = JSONArray()
        val now = System.currentTimeMillis()
        for (item in list) {
            val token = UUID.randomUUID().toString().replace("-", "").take(16)
            candidateTokens[token] = CoverCandidateToken(item.url, item.nodeId, now)
            arr.put(JSONObject().put("token", token).put("nodeId", item.nodeId))
        }
        res.sendJson(200, JSONObject().put("ok", true).put("candidates", arr).toString())
    }

    private suspend fun handleGameSetCover(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val id = body?.optString("id", "").orEmpty()
        val token = body?.optString("token", "").orEmpty()
        val game = repo.fullLibrary.value?.byId?.get(id)
        val candidate = candidateTokens[token]
        if (game == null || candidate == null) {
            res.sendJson(400, """{"ok":false,"error":"invalid_params"}""")
            return
        }
        val downloaded = covers.downloadImageBytes(candidate.url)
        if (downloaded == null) {
            res.sendJson(500, """{"ok":false,"error":"download_failed"}""")
            return
        }
        val ext = when {
            "png" in downloaded.first -> "png"
            "webp" in downloaded.first -> "webp"
            else -> "jpg"
        }
        val dir = File(context.filesDir, "custom_covers").apply { mkdirs() }
        val target = File(dir, "web_${System.currentTimeMillis()}.$ext")
        val saved = runCatching { target.writeBytes(downloaded.second); true }.getOrDefault(false)
        if (!saved) {
            res.sendJson(500, """{"ok":false,"error":"save_failed"}""")
            return
        }
        covers.forget(id)
        covers.recordWebPickerSourceUrl(id, candidate.url)
        val existingName = repo.user.value?.renames?.get(id)
        repo.saveGameEdits(id, existingName, target.absolutePath)
        val fresh = withTimeoutOrNull(500L) {
            repo.fullLibrary.first { it?.byId?.get(id)?.customCover == target.absolutePath }?.byId?.get(id)
        } ?: (repo.fullLibrary.value?.byId?.get(id) ?: game).copy(customCover = target.absolutePath)
        val root = JSONObject().put("ok", true).put("stats", buildGamesStatsJson())
        root.put("game", gameToJson(fresh))
        res.sendJson(200, root.toString())
    }

    private suspend fun handleGameUploadCover(req: HttpRequest, res: HttpResponseWriter) {
        val id = req.query["id"].orEmpty()
        val game = repo.fullLibrary.value?.byId?.get(id)
        if (game == null) {
            res.sendJson(404, """{"ok":false,"error":"game_not_found"}""")
            return
        }
        val sourceUrl = req.query["sourceUrl"]?.trim().orEmpty()
        val hasSourceUrl = sourceUrl.startsWith("http://", ignoreCase = true) || sourceUrl.startsWith("https://", ignoreCase = true)
        if (req.contentLength > MAX_COVER_UPLOAD_BYTES) {
            res.sendJson(400, """{"ok":false,"error":"invalid_image_size"}""")
            return
        }
        val bodyBytes = if (req.isChunked || req.contentLength > 0L) req.readBodyBytes(MAX_COVER_UPLOAD_BYTES.toInt()) else ByteArray(0)
        val bytes = if (bodyBytes.isNotEmpty()) {
            bodyBytes
        } else if (hasSourceUrl) {
            // 若外链不允许浏览器 CORS 直读（如 hasheous.org），回退由掌机直接下载该 URL
            covers.downloadImageBytes(sourceUrl)?.second ?: ByteArray(0)
        } else {
            ByteArray(0)
        }
        if (bytes.isEmpty()) {
            res.sendJson(400, """{"ok":false,"error":"invalid_image_size"}""")
            return
        }
        val ext = detectImageExt(bytes)
        if (ext == null) {
            res.sendJson(400, """{"ok":false,"error":"invalid_image_format"}""")
            return
        }
        val dir = File(context.filesDir, "custom_covers").apply { mkdirs() }
        val prevCover = repo.user.value?.covers?.get(id)
        val target = File(dir, "upload_${System.currentTimeMillis()}.$ext")
        val saved = runCatching { target.writeBytes(bytes); true }.getOrDefault(false)
        if (!saved) {
            res.sendJson(500, """{"ok":false,"error":"save_failed"}""")
            return
        }
        if (prevCover != null && prevCover.startsWith(dir.absolutePath) && prevCover != target.absolutePath) {
            runCatching { File(prevCover).delete() }
        }
        covers.forget(id)
        if (hasSourceUrl) {
            covers.recordWebPickerSourceUrl(id, sourceUrl)
        }
        val existingName = repo.user.value?.renames?.get(id)
        repo.saveGameEdits(id, existingName, target.absolutePath)
        val fresh = withTimeoutOrNull(500L) {
            repo.fullLibrary.first { it?.byId?.get(id)?.customCover == target.absolutePath }?.byId?.get(id)
        } ?: (repo.fullLibrary.value?.byId?.get(id) ?: game).copy(customCover = target.absolutePath)
        val root = JSONObject().put("ok", true).put("stats", buildGamesStatsJson())
        root.put("game", gameToJson(fresh))
        res.sendJson(200, root.toString())
    }

    private suspend fun handleGameResetCover(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val id = body?.optString("id", "").orEmpty()
        if (id.isBlank()) {
            res.sendJson(400, """{"ok":false,"error":"missing_id"}""")
            return
        }
        val dir = File(context.filesDir, "custom_covers")
        val prevCover = repo.user.value?.covers?.get(id)
        if (prevCover != null && prevCover.startsWith(dir.absolutePath)) {
            runCatching { File(prevCover).delete() }
        }
        covers.forget(id)
        val existingName = repo.user.value?.renames?.get(id)
        repo.saveGameEdits(id, existingName, null)
        val fresh = withTimeoutOrNull(500L) {
            repo.fullLibrary.first { it?.byId?.get(id)?.customCover == null }?.byId?.get(id)
        } ?: repo.fullLibrary.value?.byId?.get(id)?.copy(customCover = null)
        if (fresh != null && fresh.localCover.isNullOrBlank() && !fresh.id.startsWith(GameLauncher.APK_PREFIX) && NetworkStatus.isOnline(context)) {
            val plat = repo.fullLibrary.value?.platform(fresh.platformId) ?: PlatformCatalog.forFolder(fresh.platformId)
            val off = metadataRepo.officialNames.value[fresh.id]
            covers.fetchWebCoverNow(fresh, plat, off)
        }
        val root = JSONObject().put("ok", true).put("stats", buildGamesStatsJson())
        if (fresh != null) root.put("game", gameToJson(fresh))
        res.sendJson(200, root.toString())
    }

    // ───────────────────────── 标签分类管理（带严格格式安全校验） ─────────────────────────

    private fun handleGetCategories(res: HttpResponseWriter, overrideUser: UserData? = null) {
        val u = overrideUser ?: repo.user.value
        val lib = repo.fullLibrary.value
        if (u == null) {
            res.sendJson(500, """{"ok":false,"error":"user_not_loaded"}""")
            return
        }
        val categorizedPlatforms = u.categories.flatMap { it.sources }.toSet()
        val categorizedGames = u.categories.flatMap { it.games }.toSet()

        val nowCfg = u.systemTabs["now"]
        val favsCfg = u.systemTabs["favs"]
        val otherCfg = u.systemTabs["other"]
        fun sysTabDisplayName(def: String, cfg: com.pocketdeck.launcher.data.SystemTabConfig?): String =
            if (cfg != null && !cfg.followSystemLanguage && cfg.customName.isNotBlank()) cfg.customName else def

        val tabEntries = buildList {
            add(
                JSONObject().apply {
                    put("id", "now")
                    put("type", "Now")
                    put("name", sysTabDisplayName("NOW", nowCfg))
                    put("customName", nowCfg?.customName.orEmpty())
                    put("followSystemLanguage", nowCfg?.followSystemLanguage ?: true)
                    put("hidden", !u.nowTab)
                    put("sources", JSONArray())
                    put("games", JSONArray())
                    put("sortOrder", nowCfg?.sortOrder?.name ?: "Recent")
                    put("gameCount", u.recents.count { lib?.byId?.containsKey(it) == true })
                },
            )
            add(
                JSONObject().apply {
                    put("id", "favs")
                    put("type", "Favs")
                    put("name", sysTabDisplayName("FAVS", favsCfg))
                    put("customName", favsCfg?.customName.orEmpty())
                    put("followSystemLanguage", favsCfg?.followSystemLanguage ?: true)
                    put("hidden", !u.favsTab)
                    put("sources", JSONArray())
                    put("games", JSONArray())
                    put("sortOrder", (favsCfg?.sortOrder ?: CategorySortOrder.TitleAsc).name)
                    put("gameCount", u.favorites.count { lib?.byId?.containsKey(it) == true })
                },
            )
            for (c in u.categories) {
                val count = (c.sources.flatMap { lib?.games?.get(it).orEmpty() } + c.games.mapNotNull { lib?.byId?.get(it) })
                    .distinctBy { it.id }.size
                add(
                    JSONObject().apply {
                        put("id", c.id)
                        put("type", "Custom")
                        put("name", c.name)
                        put("customName", c.name)
                        put("followSystemLanguage", false)
                        put("hidden", c.id in u.hiddenTabs)
                        put("sources", JSONArray(c.sources))
                        put("games", JSONArray(c.games.toList()))
                        put("sortOrder", c.sortOrder.name)
                        put("gameCount", count)
                    },
                )
            }
            val otherCount = lib?.byId?.values?.count {
                it.platformId !in categorizedPlatforms && it.id !in categorizedGames
            } ?: 0
            add(
                JSONObject().apply {
                    put("id", "other")
                    put("type", "Other")
                    put("name", sysTabDisplayName("OTHER", otherCfg))
                    put("customName", otherCfg?.customName.orEmpty())
                    put("followSystemLanguage", otherCfg?.followSystemLanguage ?: true)
                    put("hidden", "other" in u.hiddenTabs)
                    put("sources", JSONArray())
                    put("games", JSONArray())
                    put("sortOrder", (otherCfg?.sortOrder ?: CategorySortOrder.TitleAsc).name)
                    put("gameCount", otherCount)
                },
            )
        }

        val orderMap = u.tabOrder.withIndex().associate { it.value to it.index }
        val sortedTabs = tabEntries.withIndex()
            .sortedBy { (idx, obj) -> orderMap[obj.getString("id")] ?: (100_000 + idx) }
            .map { it.value }

        val libGamesByPlat = lib?.games.orEmpty()
        val knownIds = PlatformCatalog.known.map { it.id }.toSet()
        val extraPlatforms = libGamesByPlat.keys.filter { it !in knownIds }.map { PlatformCatalog.forFolder(it) }
        val allPlatforms = (PlatformCatalog.known + extraPlatforms).map { p ->
            val count = libGamesByPlat[p.id]?.size ?: 0
            JSONObject().apply {
                put("id", p.id)
                put("shortName", p.shortName)
                put("fullName", p.fullName)
                put("libretroSystem", p.libretroSystem.orEmpty())
                put("gameCount", count)
                put("inLibrary", count > 0)
            }
        }

        res.sendJson(
            200,
            JSONObject().apply {
                put("ok", true)
                put("tabs", JSONArray(sortedTabs))
                put("platforms", JSONArray(allPlatforms))
                put("sortOrders", JSONArray(CategorySortOrder.entries.map { it.name }))
            }.toString(),
        )
    }

    private suspend fun handleSaveCategories(req: HttpRequest, res: HttpResponseWriter) {
        if (req.contentLength > 512 * 1024L) {
            res.sendJson(413, """{"ok":false,"error":"payload_too_large"}""")
            return
        }
        val raw = req.readBodyUtf8(512 * 1024)
        val existingSys = repo.user.value?.systemTabs.orEmpty()
        when (val check = CategoryValidator.validate(raw, existingSys)) {
            is CategoryValidator.Result.Error -> {
                res.sendJson(
                    400,
                    JSONObject().apply {
                        put("ok", false)
                        put("error", check.code)
                        put("message", check.message)
                    }.toString(),
                )
            }
            is CategoryValidator.Result.Ok -> {
                val cfg = check.config
                val applyConfig: (UserData) -> UserData = { u ->
                    u.copy(
                        categories = cfg.categories,
                        tabOrder = cfg.tabOrder,
                        hiddenTabs = cfg.hiddenTabs,
                        nowTab = cfg.nowTab,
                        favsTab = cfg.favsTab,
                        systemTabs = cfg.systemTabs,
                        categoriesInitialized = true,
                    )
                }
                val immediateUser = repo.user.value?.let(applyConfig)
                repo.updatePrefs(applyConfig)
                handleGetCategories(res, immediateUser)
            }
        }
    }

    // ───────────────────────── 文件管理（含多选、复制、压缩、目录上传） ─────────────────────────

    private suspend fun handleListFiles(req: HttpRequest, res: HttpResponseWriter) {
        val roots = repo.configuredRoots()
        val dirParam = req.query["dir"].orEmpty().trim()
        if (dirParam.isEmpty()) {
            val arr = JSONArray()
            for (r in roots) {
                val stat = runCatching { StatFs(r.absolutePath) }.getOrNull()
                arr.put(
                    JSONObject().apply {
                        put("name", r.absolutePath)
                        put("path", r.absolutePath)
                        put("isDir", true)
                        put("size", 0L)
                        put("freeBytes", stat?.availableBytes ?: 0L)
                        put("totalBytes", stat?.totalBytes ?: 0L)
                        put("isRoot", true)
                    },
                )
            }
            res.sendJson(
                200,
                JSONObject().apply {
                    put("ok", true)
                    put("dir", "")
                    put("parent", "")
                    put("breadcrumbs", JSONArray())
                    put("entries", arr)
                }.toString(),
            )
            return
        }

        val target = resolveAllowedDir(dirParam, roots)
        if (target == null || !target.isDirectory) {
            res.sendJson(403, """{"ok":false,"error":"forbidden_or_missing"}""")
            return
        }

        val libById = repo.fullLibrary.value?.byId.orEmpty()
        val hiddenIds = repo.user.value?.hidden.orEmpty()
        val children = target.listFiles { f -> !f.name.startsWith(".") }
            .orEmpty()
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        val arr = JSONArray()
        for (f in children) {
            val isDir = f.isDirectory
            val isFile = !isDir && f.isFile
            val absPath = f.absolutePath
            val isRom = isFile && libById.containsKey(absPath) && RomScanner.isPotentialRomFile(f)
            val isArchive = isFile && ArchiveManager.isArchive(f)
            val isImage = isFile && ArchiveManager.isImageFile(f)
            val isAudio = isFile && ArchiveManager.isAudioFile(f)
            val isFont = isFile && ArchiveManager.isFontFile(f)
            val isText = isFile && !isRom && !isArchive && !isImage && !isAudio && !isFont &&
                (ArchiveManager.isKnownTextFile(f) || ArchiveManager.looksLikeTextFile(f))
            arr.put(
                JSONObject().apply {
                    put("name", f.name)
                    put("path", absPath)
                    put("isDir", isDir)
                    put("size", if (isFile) f.length() else 0L)
                    put("modified", f.lastModified())
                    put("isRom", isRom)
                    put("isHidden", isRom && absPath in hiddenIds)
                    put("isArchive", isArchive)
                    put("isImage", isImage)
                    put("isAudio", isAudio)
                    put("isFont", isFont)
                    put("isText", isText)
                },
            )
        }
        val matchedRoot = roots.firstOrNull { root ->
            val rc = runCatching { root.canonicalPath }.getOrNull() ?: return@firstOrNull false
            val tc = runCatching { target.canonicalPath }.getOrNull() ?: return@firstOrNull false
            tc == rc || tc.startsWith("$rc${File.separator}")
        }
        val isAtRoot = matchedRoot != null && runCatching { matchedRoot.canonicalPath == target.canonicalPath }.getOrDefault(false)
        val parentPath = if (isAtRoot) "" else target.parentFile?.absolutePath.orEmpty()

        val crumbs = JSONArray()
        if (matchedRoot != null) {
            val rootCanon = runCatching { matchedRoot.canonicalPath }.getOrDefault(matchedRoot.path)
            val curCanon = runCatching { target.canonicalPath }.getOrDefault(target.path)
            crumbs.put(JSONObject().put("name", matchedRoot.name.ifBlank { matchedRoot.path }).put("path", matchedRoot.absolutePath))
            if (curCanon.startsWith("$rootCanon${File.separator}")) {
                val rel = curCanon.removePrefix("$rootCanon${File.separator}")
                var acc = matchedRoot
                for (seg in rel.split(File.separatorChar).filter { it.isNotBlank() }) {
                    acc = File(acc, seg)
                    crumbs.put(JSONObject().put("name", seg).put("path", acc.absolutePath))
                }
            }
        }
        val stat = runCatching { StatFs(target.absolutePath) }.getOrNull()

        res.sendJson(
            200,
            JSONObject().apply {
                put("ok", true)
                put("dir", target.absolutePath)
                put("parent", parentPath)
                put("breadcrumbs", crumbs)
                put("freeBytes", stat?.availableBytes ?: 0L)
                put("totalBytes", stat?.totalBytes ?: 0L)
                put("entries", arr)
            }.toString(),
        )
    }

    private suspend fun handleDownload(req: HttpRequest, res: HttpResponseWriter) {
        val path = req.query["path"].orEmpty()
        val roots = repo.configuredRoots()
        val file = resolveAllowedFile(path, roots)
        if (file == null || !file.isFile) {
            res.sendText(404, "File not found")
            return
        }
        val fileLen = file.length()
        val tracker = startTransfer(
            req = req,
            direction = WebTransferDirection.Download,
            fileName = file.name,
            filePath = file.absolutePath,
            totalBytes = fileLen,
        )
        var sentBytes = 0L
        var completed = false
        try {
            sentBytes = res.sendFile(
                file = file,
                attachmentName = file.name,
                rangeHeader = req.headers["range"],
                onProgress = { sent, total ->
                    sentBytes = sent
                    if (tracker.onProgress(sent, total)) publishClients()
                },
            )
            completed = true
        } finally {
            val isRange = req.headers.containsKey("range")
            val record = sentBytes > 0L
            val historySize = if (isRange && fileLen > 0L) fileLen else sentBytes
            finishTransfer(
                tracker = tracker,
                transferredBytes = sentBytes,
                success = completed && (isRange || sentBytes >= fileLen),
                recordHistory = record,
                overrideFileSize = historySize,
            )
        }
    }

    private suspend fun handleRawFile(req: HttpRequest, res: HttpResponseWriter) {
        val path = req.query["path"].orEmpty()
        val roots = repo.configuredRoots()
        val file = resolveAllowedFile(path, roots)
        if (file == null || !file.isFile) {
            res.sendText(404, "File not found")
            return
        }
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/opus"
            else -> {
                res.sendJson(403, """{"ok":false,"error":"unsupported_preview_type"}""")
                return
            }
        }
        val fileLen = file.length()
        if (fileLen >= 512 * 1024L) {
            val tracker = startTransfer(
                req = req,
                direction = WebTransferDirection.Download,
                fileName = file.name,
                filePath = file.absolutePath,
                totalBytes = fileLen,
            )
            var sentBytes = 0L
            var completed = false
            try {
                sentBytes = res.sendFile(
                    file = file,
                    contentType = mime,
                    attachmentName = null,
                    rangeHeader = req.headers["range"],
                    onProgress = { sent, total ->
                        if (tracker.onProgress(sent, total)) publishClients()
                    },
                )
                completed = true
            } finally {
                finishTransfer(
                    tracker = tracker,
                    transferredBytes = sentBytes,
                    success = completed,
                    recordHistory = false,
                )
            }
        } else {
            res.sendFile(file, contentType = mime, attachmentName = null, rangeHeader = req.headers["range"])
        }
    }

    private suspend fun handleReadText(req: HttpRequest, res: HttpResponseWriter) {
        val path = req.query["path"].orEmpty()
        val roots = repo.configuredRoots()
        val file = resolveAllowedFile(path, roots)
        if (file == null || !file.isFile || !file.canRead()) {
            res.sendJson(404, """{"ok":false,"error":"not_found"}""")
            return
        }
        if (file.length() > 512 * 1024L) {
            res.sendJson(413, """{"ok":false,"error":"file_too_large"}""")
            return
        }
        val text = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
        if (text == null) {
            res.sendJson(500, """{"ok":false,"error":"read_failed"}""")
            return
        }
        res.sendJson(
            200,
            JSONObject().apply {
                put("ok", true)
                put("name", file.name)
                put("path", file.absolutePath)
                put("content", text)
            }.toString(),
        )
    }

    private suspend fun handleWriteText(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8(maxBytes = 1024 * 1024)) }.getOrNull()
        val path = body?.optString("path", "").orEmpty()
        if (body == null || !body.has("content")) {
            res.sendJson(400, """{"ok":false,"error":"invalid_params"}""")
            return
        }
        val content = body.optString("content", "")
        val roots = repo.configuredRoots()
        val file = resolveAllowedFile(path, roots)
        if (file == null || !file.isFile) {
            res.sendJson(404, """{"ok":false,"error":"not_found"}""")
            return
        }
        val ok = runCatching { file.writeText(content, StandardCharsets.UTF_8) }.isSuccess
        if (ok) {
            repo.notifyFilesChanged()
            res.sendJson(200, """{"ok":true}""")
        } else {
            res.sendJson(500, """{"ok":false,"error":"write_failed"}""")
        }
    }

    private suspend fun handleArchiveEntries(req: HttpRequest, res: HttpResponseWriter) {
        val path = req.query["path"].orEmpty()
        val roots = repo.configuredRoots()
        val file = resolveAllowedFile(path, roots)
        if (file == null || !file.isFile || !ArchiveManager.isArchive(file)) {
            res.sendJson(400, """{"ok":false,"error":"invalid_archive"}""")
            return
        }
        val result = ArchiveManager.inspect(file)
        if (result.isFailure) {
            res.sendJson(
                500,
                JSONObject().put("ok", false).put("error", result.exceptionOrNull()?.message ?: "inspect_failed").toString(),
            )
            return
        }
        val arr = JSONArray()
        for (e in result.getOrDefault(emptyList())) {
            arr.put(JSONObject().put("name", e.name).put("isDir", e.isDir).put("size", e.size))
        }
        res.sendJson(200, JSONObject().put("ok", true).put("entries", arr).toString())
    }

    private suspend fun handleExtract(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val path = body?.optString("path", "").orEmpty()
        val mode = body?.optString("mode", "folder").orEmpty()
        val roots = repo.configuredRoots()
        val file = resolveAllowedFile(path, roots)
        if (file == null || !file.isFile || !ArchiveManager.isArchive(file)) {
            res.sendJson(400, """{"ok":false,"error":"invalid_archive"}""")
            return
        }
        val parent = file.parentFile
        if (parent == null || !parent.isDirectory) {
            res.sendJson(400, """{"ok":false,"error":"invalid_parent"}""")
            return
        }
        val destDir = if (mode == "here") {
            parent
        } else {
            var candidate = File(parent, ArchiveManager.suggestedFolderName(file))
            var idx = 1
            while (candidate.exists()) {
                candidate = File(parent, "${ArchiveManager.suggestedFolderName(file)} ($idx)")
                idx++
            }
            candidate
        }
        val clientIp = req.remoteIp
        val isAsync = req.query["async"] == "1"
        if (isAsync) {
            repo.launchFileTask {
                repo.extractArchive(file, destDir, source = FileTaskSource.Web, clientIp = clientIp)
            }
            res.sendJson(
                202,
                JSONObject().apply {
                    put("ok", true)
                    put("async", true)
                    put("dest", destDir.name)
                }.toString(),
            )
            return
        }
        val result = repo.extractArchive(file, destDir, source = FileTaskSource.Web, clientIp = clientIp)
        if (result.isSuccess) {
            res.sendJson(
                200,
                JSONObject().apply {
                    put("ok", true)
                    put("count", result.getOrDefault(0))
                    put("dest", destDir.name)
                }.toString(),
            )
        } else {
            res.sendJson(
                500,
                JSONObject().apply {
                    put("ok", false)
                    put("error", result.exceptionOrNull()?.message ?: "extract_failed")
                }.toString(),
            )
        }
    }

    private suspend fun handleCompress(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val pathsArr = body?.optJSONArray("paths")
        val targetDirPath = body?.optString("targetDir", "").orEmpty()
        val rawZipName = sanitizeFileName(body?.optString("zipName", "").orEmpty())
        val roots = repo.configuredRoots()
        val targetDir = resolveAllowedDir(targetDirPath, roots)
        if (pathsArr == null || targetDir == null || rawZipName == null) {
            res.sendJson(400, """{"ok":false,"error":"invalid_params"}""")
            return
        }
        val sources = buildList {
            for (i in 0 until pathsArr.length()) {
                val f = resolveAllowedFile(pathsArr.optString(i, ""), roots)
                if (f != null && f.exists()) add(f)
            }
        }
        if (sources.isEmpty()) {
            res.sendJson(400, """{"ok":false,"error":"no_sources"}""")
            return
        }
        val zipFileName = if (rawZipName.endsWith(".zip", ignoreCase = true)) rawZipName else "$rawZipName.zip"
        val destZip = uniqueFile(targetDir, zipFileName)
        val clientIp = req.remoteIp
        val isAsync = req.query["async"] == "1"
        if (isAsync) {
            repo.launchFileTask {
                repo.compressToZip(sources, destZip, source = FileTaskSource.Web, clientIp = clientIp)
            }
            res.sendJson(
                202,
                JSONObject()
                    .put("ok", true)
                    .put("async", true)
                    .put("name", destZip.name)
                    .put("path", destZip.absolutePath)
                    .toString(),
            )
            return
        }
        val result = repo.compressToZip(sources, destZip, source = FileTaskSource.Web, clientIp = clientIp)
        if (result.isSuccess) {
            res.sendJson(200, JSONObject().put("ok", true).put("name", destZip.name).put("path", destZip.absolutePath).toString())
        } else {
            res.sendJson(500, JSONObject().put("ok", false).put("error", result.exceptionOrNull()?.message ?: "compress_failed").toString())
        }
    }

    private suspend fun handleUpload(req: HttpRequest, res: HttpResponseWriter) {
        val dirPath = req.query["dir"].orEmpty()
        val rawName = req.query["name"].orEmpty().trim()
        val rawSubpath = req.query["subpath"].orEmpty().trim()
        val roots = repo.configuredRoots()
        val baseDir = resolveAllowedDir(dirPath, roots)
        if (baseDir == null || !baseDir.isDirectory) {
            res.sendJson(403, """{"ok":false,"error":"invalid_dir"}""")
            return
        }
        val cleanName = sanitizeFileName(rawName)
        if (cleanName == null) {
            res.sendJson(400, """{"ok":false,"error":"invalid_file_name"}""")
            return
        }
        val targetDir = if (rawSubpath.isNotEmpty()) {
            val segments = rawSubpath.replace('\\', '/').split('/').filter { it.isNotEmpty() }
            if (segments.size > 16 || segments.any { sanitizeFileName(it) == null }) {
                res.sendJson(400, """{"ok":false,"error":"invalid_subpath"}""")
                return
            }
            var cur = baseDir
            for (seg in segments) {
                cur = File(cur, seg)
                if (cur.exists() && resolveAllowedFile(cur.absolutePath, roots) == null) {
                    res.sendJson(403, """{"ok":false,"error":"forbidden_subpath"}""")
                    return
                }
            }
            if (!cur.exists()) runCatching { cur.mkdirs() }
            if (resolveAllowedDir(cur.absolutePath, roots) == null) {
                res.sendJson(403, """{"ok":false,"error":"forbidden_subpath"}""")
                return
            }
            cur
        } else {
            baseDir
        }

        if (req.contentLength < 0L && !req.isChunked) {
            res.sendJson(400, """{"ok":false,"error":"empty_body"}""")
            return
        }

        val finalTarget = uniqueFile(targetDir, cleanName)
        val partFile = File(targetDir, ".upload_${System.currentTimeMillis()}_${finalTarget.name}.part")
        val uploadTask = repo.beginFileTask(
            kind = FileTaskKind.Upload,
            source = FileTaskSource.Web,
            title = finalTarget.name,
            detail = targetDir.name,
            clientIp = req.remoteIp,
            totalBytes = req.contentLength.coerceAtLeast(0L),
            cancellable = true,
            onCancel = req.cancelConnection,
        )
        val tracker = startTransfer(
            req = req,
            direction = WebTransferDirection.Upload,
            fileName = finalTarget.name,
            filePath = finalTarget.absolutePath,
            totalBytes = req.contentLength.coerceAtLeast(0L),
        )
        var writtenBytes = 0L
        var ok = false
        try {
            ok = runCatching {
                partFile.outputStream().use { out ->
                    val written = req.copyBodyTo(out) { copied ->
                        writtenBytes = copied
                        if (tracker.onProgress(copied, tracker.totalBytes)) {
                            repo.updateFileTask(uploadTask.id, bytesDone = copied, totalBytes = tracker.totalBytes)
                            publishClients()
                        }
                    }
                    writtenBytes = written
                    check(req.isChunked || written == req.contentLength) { "Incomplete upload: $written/${req.contentLength}" }
                }
                check(partFile.renameTo(finalTarget)) { "Cannot finalize uploaded file" }
                true
            }.getOrElse {
                runCatching { partFile.delete() }
                false
            }
        } finally {
            repo.endFileTask(uploadTask.id)
            val savedSize = if (ok) finalTarget.length() else writtenBytes
            finishTransfer(
                tracker = tracker,
                transferredBytes = writtenBytes,
                success = ok,
                recordHistory = true,
                overrideFileSize = savedSize,
            )
        }
        if (!ok) {
            res.sendJson(500, """{"ok":false,"error":"upload_failed"}""")
            return
        }
        repo.notifyFilesChanged()
        repo.refresh()
        res.sendJson(
            200,
            JSONObject().apply {
                put("ok", true)
                put("path", finalTarget.absolutePath)
                put("name", finalTarget.name)
                put("size", finalTarget.length())
            }.toString(),
        )
    }

    private suspend fun handleMkdir(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val dirPath = body?.optString("dir", "").orEmpty()
        val name = sanitizeFileName(body?.optString("name", "").orEmpty())
        val roots = repo.configuredRoots()
        val parent = resolveAllowedDir(dirPath, roots)
        if (parent == null || name == null) {
            res.sendJson(400, """{"ok":false,"error":"invalid_params"}""")
            return
        }
        val target = File(parent, name)
        if (target.exists()) {
            res.sendJson(409, """{"ok":false,"error":"already_exists"}""")
            return
        }
        val created = runCatching { target.mkdirs() }.getOrDefault(false)
        if (!created) {
            res.sendJson(500, """{"ok":false,"error":"mkdir_failed"}""")
            return
        }
        repo.notifyFilesChanged()
        res.sendJson(200, """{"ok":true}""")
    }

    private suspend fun handleFileRename(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val path = body?.optString("path", "").orEmpty()
        val newName = sanitizeFileName(body?.optString("newName", "").orEmpty())
        val roots = repo.configuredRoots()
        val file = resolveAllowedFile(path, roots)
        if (file == null || newName == null || !file.exists()) {
            res.sendJson(400, """{"ok":false,"error":"invalid_params"}""")
            return
        }
        if (isConfiguredRoot(file, roots)) {
            res.sendJson(403, """{"ok":false,"error":"cannot_rename_root"}""")
            return
        }
        val result = repo.renameFile(file, newName)
        if (result.isSuccess) {
            res.sendJson(200, """{"ok":true}""")
        } else {
            res.sendJson(400, JSONObject().put("ok", false).put("error", result.exceptionOrNull()?.message ?: "rename_failed").toString())
        }
    }

    private suspend fun handleFileMove(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val path = body?.optString("path", "").orEmpty()
        val targetDirPath = body?.optString("targetDir", "").orEmpty()
        val roots = repo.configuredRoots()
        val file = resolveAllowedFile(path, roots)
        val targetDir = resolveAllowedDir(targetDirPath, roots)
        if (file == null || !file.exists() || targetDir == null || !targetDir.isDirectory) {
            res.sendJson(400, """{"ok":false,"error":"invalid_params"}""")
            return
        }
        if (isConfiguredRoot(file, roots)) {
            res.sendJson(403, """{"ok":false,"error":"cannot_move_root"}""")
            return
        }
        val result = repo.moveFile(file, targetDir, source = FileTaskSource.Web, clientIp = req.remoteIp)
        if (result.isSuccess) {
            res.sendJson(200, """{"ok":true}""")
        } else {
            res.sendJson(400, JSONObject().put("ok", false).put("error", result.exceptionOrNull()?.message ?: "move_failed").toString())
        }
    }

    private suspend fun handleFileCopy(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val path = body?.optString("path", "").orEmpty()
        val targetDirPath = body?.optString("targetDir", "").orEmpty()
        val roots = repo.configuredRoots()
        val file = resolveAllowedFile(path, roots)
        val targetDir = resolveAllowedDir(targetDirPath, roots)
        if (file == null || !file.exists() || targetDir == null || !targetDir.isDirectory) {
            res.sendJson(400, """{"ok":false,"error":"invalid_params"}""")
            return
        }
        if (isConfiguredRoot(file, roots)) {
            res.sendJson(403, """{"ok":false,"error":"cannot_copy_root"}""")
            return
        }
        val result = repo.copyFile(file, targetDir, source = FileTaskSource.Web, clientIp = req.remoteIp)
        if (result.isSuccess) {
            res.sendJson(200, """{"ok":true}""")
        } else {
            res.sendJson(400, JSONObject().put("ok", false).put("error", result.exceptionOrNull()?.message ?: "copy_failed").toString())
        }
    }

    private suspend fun handleFileDelete(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val path = body?.optString("path", "").orEmpty()
        val roots = repo.configuredRoots()
        val file = resolveAllowedFile(path, roots)
        if (file == null || !file.exists()) {
            res.sendJson(404, """{"ok":false,"error":"not_found"}""")
            return
        }
        if (isConfiguredRoot(file, roots)) {
            res.sendJson(403, """{"ok":false,"error":"cannot_delete_root"}""")
            return
        }
        val result = repo.deleteFile(file, source = FileTaskSource.Web, clientIp = req.remoteIp)
        if (result.isSuccess) {
            res.sendJson(200, """{"ok":true}""")
        } else {
            res.sendJson(400, JSONObject().put("ok", false).put("error", result.exceptionOrNull()?.message ?: "delete_failed").toString())
        }
    }

    private suspend fun handleBatchMove(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val pathsArr = body?.optJSONArray("paths")
        val targetDirPath = body?.optString("targetDir", "").orEmpty()
        val roots = repo.configuredRoots()
        val targetDir = resolveAllowedDir(targetDirPath, roots)
        val entries = resolveBatchEntries(pathsArr, roots)
        if (entries == null || entries.isEmpty() || targetDir == null) {
            res.sendJson(400, """{"ok":false,"error":"invalid_params"}""")
            return
        }
        val clientIp = req.remoteIp
        if (req.query["async"] == "1") {
            repo.launchFileTask {
                repo.moveEntries(entries, targetDir, source = FileTaskSource.Web, clientIp = clientIp)
            }
            res.sendJson(202, JSONObject().put("ok", true).put("async", true).put("count", entries.size).toString())
            return
        }
        val result = repo.moveEntries(entries, targetDir, source = FileTaskSource.Web, clientIp = clientIp)
        if (result.isSuccess) {
            res.sendJson(200, JSONObject().put("ok", true).put("count", result.getOrDefault(0)).toString())
        } else {
            res.sendJson(400, JSONObject().put("ok", false).put("error", result.exceptionOrNull()?.message ?: "batch_move_failed").toString())
        }
    }

    private suspend fun handleBatchCopy(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val pathsArr = body?.optJSONArray("paths")
        val targetDirPath = body?.optString("targetDir", "").orEmpty()
        val roots = repo.configuredRoots()
        val targetDir = resolveAllowedDir(targetDirPath, roots)
        val entries = resolveBatchEntries(pathsArr, roots)
        if (entries == null || entries.isEmpty() || targetDir == null) {
            res.sendJson(400, """{"ok":false,"error":"invalid_params"}""")
            return
        }
        val clientIp = req.remoteIp
        if (req.query["async"] == "1") {
            repo.launchFileTask {
                repo.copyEntries(entries, targetDir, source = FileTaskSource.Web, clientIp = clientIp)
            }
            res.sendJson(202, JSONObject().put("ok", true).put("async", true).put("count", entries.size).toString())
            return
        }
        val result = repo.copyEntries(entries, targetDir, source = FileTaskSource.Web, clientIp = clientIp)
        if (result.isSuccess) {
            res.sendJson(200, JSONObject().put("ok", true).put("count", result.getOrDefault(0)).toString())
        } else {
            res.sendJson(400, JSONObject().put("ok", false).put("error", result.exceptionOrNull()?.message ?: "batch_copy_failed").toString())
        }
    }

    private suspend fun handleBatchDelete(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val pathsArr = body?.optJSONArray("paths")
        val roots = repo.configuredRoots()
        val entries = resolveBatchEntries(pathsArr, roots)
        if (entries == null || entries.isEmpty()) {
            res.sendJson(400, """{"ok":false,"error":"invalid_params"}""")
            return
        }
        val clientIp = req.remoteIp
        if (req.query["async"] == "1") {
            repo.launchFileTask {
                repo.deleteEntries(entries, source = FileTaskSource.Web, clientIp = clientIp)
            }
            res.sendJson(202, JSONObject().put("ok", true).put("async", true).put("count", entries.size).toString())
            return
        }
        val result = repo.deleteEntries(entries, source = FileTaskSource.Web, clientIp = clientIp)
        if (result.isSuccess) {
            res.sendJson(200, JSONObject().put("ok", true).put("count", result.getOrDefault(0)).toString())
        } else {
            res.sendJson(400, JSONObject().put("ok", false).put("error", result.exceptionOrNull()?.message ?: "batch_delete_failed").toString())
        }
    }

    private fun handleFileTasks(res: HttpResponseWriter) {
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        for (t in repo.fileTasks.value) {
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("kind", t.kind.name.lowercase())
                    put("source", t.source.name.lowercase())
                    put("title", t.title)
                    put("detail", t.detail)
                    put("bytesDone", t.bytesDone)
                    put("totalBytes", t.totalBytes)
                    put("elapsedMs", (now - t.startedAtMs).coerceAtLeast(0L))
                    put("cancelling", t.cancelling)
                    put("cancellable", t.cancellable)
                },
            )
        }
        val evt = repo.lastFileTaskEvent.value
        val evtObj = evt?.let {
            JSONObject().apply {
                put("seq", it.seq)
                put("taskId", it.taskId)
                put("kind", it.kind.name.lowercase())
                put("source", it.source.name.lowercase())
                put("title", it.title)
                put("success", it.success)
                put("cancelled", it.cancelled)
                put("count", it.count)
                put("createdName", it.createdName ?: "")
                put("error", it.error ?: "")
            }
        }
        val body = JSONObject().apply {
            put("ok", true)
            put("filesRevision", repo.filesRevision.value)
            put("tasks", arr)
            if (evtObj != null) put("lastEvent", evtObj)
        }
        res.sendJson(200, body.toString())
    }

    private suspend fun handleCancelFileTask(req: HttpRequest, res: HttpResponseWriter) {
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val id = body?.optString("id", "").orEmpty()
        val cancelled = id.isNotBlank() && repo.cancelFileTask(id)
        res.sendJson(200, JSONObject().put("ok", cancelled).toString())
    }

    private fun resolveBatchEntries(arr: JSONArray?, roots: List<File>): List<File>? {
        if (arr == null || arr.length() == 0 || arr.length() > 500) return null
        val out = ArrayList<File>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.optString(i, "")
            val f = resolveAllowedFile(p, roots) ?: return null
            if (!f.exists() || isConfiguredRoot(f, roots)) return null
            out += f
        }
        return out
    }

    private fun isConfiguredRoot(file: File, roots: List<File>): Boolean {
        val canon = runCatching { file.canonicalPath }.getOrNull() ?: return true
        return roots.any { runCatching { it.canonicalPath == canon }.getOrDefault(false) }
    }

    private fun resolveAllowedDir(rawPath: String, roots: List<File>): File? {
        val f = File(rawPath)
        val canonical = runCatching { f.canonicalPath }.getOrNull() ?: return null
        val allowed = roots.any { root ->
            val rc = runCatching { root.canonicalPath }.getOrNull() ?: return@any false
            canonical == rc || canonical.startsWith("$rc${File.separator}")
        }
        return if (allowed && f.isDirectory) f else null
    }

    private fun resolveAllowedFile(rawPath: String, roots: List<File>): File? {
        val f = File(rawPath)
        val canonical = runCatching { f.canonicalPath }.getOrNull() ?: return null
        val allowed = roots.any { root ->
            val rc = runCatching { root.canonicalPath }.getOrNull() ?: return@any false
            canonical == rc || canonical.startsWith("$rc${File.separator}")
        }
        return if (allowed) f else null
    }

    private fun sanitizeFileName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") return null
        if ('/' in trimmed || '\\' in trimmed || '\u0000' in trimmed) return null
        return trimmed
    }

    private fun detectImageExt(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
        bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() -> "webp"
        bytes.size >= 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "gif"
        else -> null
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "${stem}_$n$ext")
            n++
        }
        return candidate
    }
}
