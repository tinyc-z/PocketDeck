package com.pocketdeck.launcher.lan

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import com.pocketdeck.launcher.data.GameRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.Socket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class ShareProtocol { PocketDeck, LocalSend }

data class LanPeer(
    val deviceId: String,
    val deviceName: String,
    val model: String,
    val ip: String,
    val port: Int,
    val lastSeenMs: Long,
    val protocol: ShareProtocol = ShareProtocol.PocketDeck,
    val useHttps: Boolean = false,
    val fingerprint: String = "",
)

data class IncomingShareRequest(
    val sessionId: String,
    val senderId: String,
    val senderName: String,
    val senderIp: String,
    val fileName: String,
    val fileSize: Long,
    val platformId: String,
    val platformName: String,
    val gameTitle: String,
    val defaultTargetDir: File,
    val availableDirs: List<File>,
    val fileCount: Int = 1,
    val fileNames: List<String> = listOf(fileName),
    val protocol: ShareProtocol = ShareProtocol.PocketDeck,
)

enum class TransferDirection { Sending, Receiving }

enum class TransferPhase {
    WaitingAccept,
    PinRequired,
    Transferring,
    Completed,
    Rejected,
    Cancelled,
    Failed,
}

data class LocalSendPinChallenge(
    val sessionId: String,
    val peerName: String,
    val wrongPin: Boolean = false,
)

data class ShareActiveFileProgress(
    val id: String,
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long = 0L,
)

data class ShareTransferState(
    val sessionId: String,
    val direction: TransferDirection,
    val phase: TransferPhase,
    val peerName: String,
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long = 0L,
    val savedPath: String? = null,
    val errorMessage: String? = null,
    val currentFileIndex: Int = 1,
    val totalFiles: Int = 1,
    val activeFiles: List<ShareActiveFileProgress> = emptyList(),
)

data class ShareHistoryItem(
    val id: String,
    val fileName: String,
    val filePath: String,
    val filePaths: List<String> = emptyList(),
    val fileCount: Int = 1,
    val totalBytes: Long,
    val senderName: String,
    val senderIp: String,
    val platformName: String = "",
    val receivedAtMs: Long,
)

/**
 * 局域网游戏快传服务：
 * - 独立启停，默认关闭
 * - 严格绑定用户选择的物理网卡（多网卡可选，自动绕过 TUN/VPN）
 * - 支持 PocketDeck 原生协议（53318/53319）与 LocalSend v2 协议兼容（53317，默认开启可手动关闭）
 * - 先发请求，接收端确认后才开始流式传输
 */
class QuickShareService(
    private val context: Context,
    private val repo: GameRepository,
) {
    companion object {
        const val DISCOVERY_PORT = 53318
        const val TRANSFER_PREFERRED_PORT = 53319
        const val LOCALSEND_PORT = 53317
        private const val MULTICAST_ADDR = "224.0.0.167"
        private const val PEER_STALE_MS = 16_000L
        private const val ANNOUNCE_INTERVAL_MS = 4_000L
        private const val MAX_HISTORY_ITEMS = 200
        private const val MAX_FILES_PER_SESSION = 500
        private const val MAX_CONTROL_RESPONSE_BYTES = 4 * 1024 * 1024
        private val CONTROL_CHARS = Regex("""[\u0000-\u001F\u007F]""")
        private val QUICKSHARE_CORS_HEADERS = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, X-File-Index",
            "Access-Control-Allow-Private-Network" to "true",
        )

        fun defaultInboxDir(): File {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: File(Environment.getExternalStorageDirectory(), "Download")
            return File(downloads, "PocketDeck")
        }
    }

    private enum class SessionStatus { PENDING, ACCEPTED, REJECTED, CANCELLED }

    private class ReceiveSession(
        val req: IncomingShareRequest,
        @Volatile var status: SessionStatus = SessionStatus.PENDING,
        @Volatile var targetDir: File = req.defaultTargetDir,
        @Volatile var cancelled: Boolean = false,
        val completedBytes: AtomicLong = AtomicLong(0L),
        val receivedCount: AtomicInteger = AtomicInteger(0),
        val activeFiles: ConcurrentHashMap<String, ShareActiveFileProgress> = ConcurrentHashMap(),
        val activeCancelActions: ConcurrentHashMap<String, () -> Unit> = ConcurrentHashMap(),
        val progressLock: Any = Any(),
        val sessionStartMs: AtomicLong = AtomicLong(0L),
        @Volatile var lastEmittedBytes: Long = 0L,
        @Volatile var lastEmitMs: Long = 0L,
        val completedOnce: AtomicBoolean = AtomicBoolean(false),
        val savedFiles: MutableList<File> = java.util.Collections.synchronizedList(mutableListOf()),
        val localSendFileNames: Map<String, String> = emptyMap(),
        val localSendFileTokens: Map<String, String> = emptyMap(),
    )

    private var scope: CoroutineScope? = null
    private var httpServer: HttpMiniServer? = null
    private var discoverySocket: MulticastSocket? = null

    private var localSendHttpServer: HttpMiniServer? = null
    private var localSendDiscoverySocket: MulticastSocket? = null
    private var localSendJobs: MutableList<Job> = java.util.Collections.synchronizedList(mutableListOf())
    private val localSendLock = Any()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var sendJob: Job? = null

    @Volatile
    private var activeSendSocket: Socket? = null

    @Volatile
    private var activeSendRemoteSessionId: String? = null

    @Volatile
    private var activeSendLocalSessionId: String? = null

    @Volatile
    private var activeSendCancelledByRemote: Boolean = false

    @Volatile
    private var activeLan: LanInterface? = null

    @Volatile
    private var localDeviceId: String = ""

    @Volatile
    private var localDeviceName: String = ""

    @Volatile
    private var localSendCompatEnabled: Boolean = true

    @Volatile
    private var activeReceiveSession: ReceiveSession? = null

    @Volatile
    private var activeSendCancelled = false

    @Volatile
    private var pendingPinDeferred: CompletableDeferred<String?>? = null

    private val pocketDeckPeers = ConcurrentHashMap<String, LanPeer>()
    private val localSendPeers = ConcurrentHashMap<String, LanPeer>()

    private val _peers = MutableStateFlow<List<LanPeer>>(emptyList())
    val peers: StateFlow<List<LanPeer>> = _peers.asStateFlow()

    private val _incomingRequest = MutableStateFlow<IncomingShareRequest?>(null)
    val incomingRequest: StateFlow<IncomingShareRequest?> = _incomingRequest.asStateFlow()

    private val _transfer = MutableStateFlow<ShareTransferState?>(null)
    val transfer: StateFlow<ShareTransferState?> = _transfer.asStateFlow()

    private val _pinChallenge = MutableStateFlow<LocalSendPinChallenge?>(null)
    val pinChallenge: StateFlow<LocalSendPinChallenge?> = _pinChallenge.asStateFlow()

    private val historyFile = File(context.filesDir, "share_history.json")
    private val historyLock = Any()
    private val _history = MutableStateFlow<List<ShareHistoryItem>>(loadHistoryFromDisk())
    val history: StateFlow<List<ShareHistoryItem>> = _history.asStateFlow()

    val isRunning: Boolean get() = httpServer != null
    val boundPort: Int get() = httpServer?.boundPort ?: -1
    val localSendBoundPort: Int get() = localSendHttpServer?.boundPort ?: -1

    fun updateIdentity(deviceId: String, deviceName: String) {
        localDeviceId = deviceId
        localDeviceName = deviceName
        if (isRunning) {
            scope?.launch(Dispatchers.IO) {
                sendDiscoveryPacket("announce")
                if (localSendCompatEnabled) sendLocalSendAnnounce(announce = true)
            }
        }
    }

    fun setLocalSendCompat(enabled: Boolean) {
        if (localSendCompatEnabled == enabled) return
        localSendCompatEnabled = enabled
        val s = scope
        val lan = activeLan
        if (isRunning && s != null && lan != null) {
            if (enabled) {
                startLocalSend(s, lan)
            } else {
                stopLocalSend()
            }
        }
    }

    fun start(
        lan: LanInterface,
        deviceId: String,
        deviceName: String,
        localSendCompat: Boolean = localSendCompatEnabled,
    ): Int {
        stop()
        localDeviceId = deviceId
        localDeviceName = deviceName
        localSendCompatEnabled = localSendCompat
        activeLan = lan

        val server = HttpMiniServer(lan.address, TRANSFER_PREFERRED_PORT) { req, res ->
            handleHttp(req, res)
        }
        val port = server.start()
        httpServer = server

        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s

        acquireMulticastLock()
        startDiscovery(s, lan)
        if (localSendCompat) {
            startLocalSend(s, lan)
        }
        return port
    }

    fun stop() {
        val lan = activeLan
        if (lan != null) {
            runCatching { sendDiscoveryPacketSync(lan, "goodbye") }
        }
        clearPendingPin(null)
        sendJob?.cancel()
        sendJob = null
        runCatching { activeSendSocket?.close() }
        activeSendSocket = null
        activeReceiveSession?.cancelled = true
        activeReceiveSession = null
        _incomingRequest.value = null
        stopLocalSend()
        scope?.cancel()
        scope = null
        runCatching { discoverySocket?.close() }
        discoverySocket = null
        releaseMulticastLock()
        httpServer?.stop()
        httpServer = null
        activeLan = null
        pocketDeckPeers.clear()
        localSendPeers.clear()
        _peers.value = emptyList()
    }

    fun scanPeers() {
        if (!isRunning) return
        scope?.launch(Dispatchers.IO) {
            sendDiscoveryPacket("probe")
            delay(120)
            sendDiscoveryPacket("announce")
            if (localSendCompatEnabled) {
                sendLocalSendAnnounce(announce = true)
            }
        }
    }

    fun acceptIncoming(sessionId: String, targetDir: File) {
        val sess = activeReceiveSession ?: return
        if (sess.req.sessionId != sessionId) return
        sess.targetDir = targetDir
        sess.status = SessionStatus.ACCEPTED
        _incomingRequest.value = null
        _transfer.value = ShareTransferState(
            sessionId = sessionId,
            direction = TransferDirection.Receiving,
            phase = TransferPhase.Transferring,
            peerName = sess.req.senderName,
            fileName = sess.req.fileNames.firstOrNull() ?: sess.req.fileName,
            bytesTransferred = 0L,
            totalBytes = sess.req.fileSize,
            currentFileIndex = 1,
            totalFiles = sess.req.fileCount,
        )
    }

    fun rejectIncoming(sessionId: String) {
        val sess = activeReceiveSession ?: return
        if (sess.req.sessionId != sessionId) return
        sess.status = SessionStatus.REJECTED
        _incomingRequest.value = null
        activeReceiveSession = null
    }

    fun cancelTransfer() {
        val cur = _transfer.value
        val sess = activeReceiveSession
        if (sess != null && cur?.sessionId == sess.req.sessionId) {
            sess.status = SessionStatus.CANCELLED
            sess.cancelled = true
            activeReceiveSession = null
            _incomingRequest.value = null
            sess.activeCancelActions.values.forEach { act -> runCatching { act() } }
            if (sess.req.protocol == ShareProtocol.PocketDeck) {
                val lan = activeLan
                val senderPeer = _peers.value.firstOrNull {
                    it.protocol == ShareProtocol.PocketDeck && (it.deviceId == sess.req.senderId || it.ip == sess.req.senderIp)
                }
                if (lan != null && senderPeer != null) {
                    scope?.launch(Dispatchers.IO) {
                        runCatching {
                            httpJson(
                                lan,
                                senderPeer.ip,
                                senderPeer.port,
                                "POST",
                                "/api/share/cancel?sessionId=${URLEncoder.encode(sess.req.sessionId, "UTF-8")}",
                                "{}",
                            )
                        }
                    }
                }
            }
        }
        activeSendCancelled = true
        clearPendingPin(null)
        runCatching { activeSendSocket?.close() }
        activeSendSocket = null
        sendJob?.cancel()
        sendJob = null
        if (cur != null && (
                cur.phase == TransferPhase.WaitingAccept ||
                    cur.phase == TransferPhase.PinRequired ||
                    cur.phase == TransferPhase.Transferring
                )
        ) {
            _transfer.value = cur.copy(phase = TransferPhase.Cancelled)
        }
    }

    fun submitSendPin(pin: String) {
        val trimmed = pin.trim()
        if (trimmed.isEmpty()) return
        _pinChallenge.value = null
        pendingPinDeferred?.complete(trimmed)
        pendingPinDeferred = null
    }

    fun cancelSendPin() {
        cancelTransfer()
    }

    private fun clearPendingPin(result: String? = null) {
        _pinChallenge.value = null
        pendingPinDeferred?.complete(result)
        pendingPinDeferred = null
    }

    fun dismissTransferBanner() {
        val cur = _transfer.value ?: return
        if (cur.phase != TransferPhase.WaitingAccept &&
            cur.phase != TransferPhase.PinRequired &&
            cur.phase != TransferPhase.Transferring
        ) {
            _transfer.value = null
        }
    }

    fun sendGame(
        peer: LanPeer,
        file: File,
        gameTitle: String,
        platformId: String,
        platformName: String,
    ) = sendFiles(peer, listOf(file), gameTitle, platformId, platformName)

    fun sendFiles(
        peer: LanPeer,
        files: List<File>,
        gameTitle: String,
        platformId: String,
        platformName: String,
    ) {
        if (peer.protocol == ShareProtocol.LocalSend) {
            sendFilesViaLocalSend(peer, files)
            return
        }
        val lan = activeLan ?: return
        val validFiles = files.filter { it.isFile && it.canRead() }
        if (validFiles.isEmpty()) return
        val s = scope ?: return

        sendJob?.cancel()
        clearPendingPin(null)
        activeSendCancelled = false
        activeSendCancelledByRemote = false
        activeSendLocalSessionId = null
        val sessionId = UUID.randomUUID().toString()
        val totalBytes = validFiles.sumOf { it.length() }
        val fileCount = validFiles.size
        val displayTitle = if (fileCount == 1) gameTitle else "${validFiles.first().name} ($fileCount files)"
        val primaryName = if (fileCount == 1) validFiles.first().name else "${validFiles.first().name} +${fileCount - 1}"

        _transfer.value = ShareTransferState(
            sessionId = sessionId,
            direction = TransferDirection.Sending,
            phase = TransferPhase.WaitingAccept,
            peerName = peer.deviceName,
            fileName = primaryName,
            bytesTransferred = 0L,
            totalBytes = totalBytes,
            currentFileIndex = 1,
            totalFiles = fileCount,
        )

        sendJob = s.launch {
            try {
                // 1) 发送准备请求（支持单文件与多文件列表）
                val namesArr = JSONArray().apply { validFiles.forEach { put(it.name) } }
                val prepBody = JSONObject().apply {
                    put("sessionId", sessionId)
                    put("senderId", localDeviceId)
                    put("senderName", localDeviceName)
                    put("fileName", validFiles.first().name)
                    put("fileSize", totalBytes)
                    put("fileCount", fileCount)
                    put("fileNames", namesArr)
                    put("platformId", platformId)
                    put("platformName", platformName)
                    put("gameTitle", displayTitle)
                }.toString()

                val prepRes = httpJson(lan, peer.ip, peer.port, "POST", "/api/share/prepare", prepBody)
                if (prepRes.first == 409) {
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Failed,
                        errorMessage = "Target device is busy",
                    )
                    return@launch
                }
                if (prepRes.first !in 200..299) {
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Failed,
                        errorMessage = "Prepare failed (${prepRes.first})",
                    )
                    return@launch
                }
                activeSendLocalSessionId = sessionId

                // 2) 轮询等待对方点击接收
                val deadline = System.currentTimeMillis() + 90_000L
                var accepted = false
                while (isActive && !activeSendCancelled && System.currentTimeMillis() < deadline) {
                    delay(350)
                    val st = httpJson(
                        lan,
                        peer.ip,
                        peer.port,
                        "GET",
                        "/api/share/status?sessionId=${URLEncoder.encode(sessionId, "UTF-8")}",
                        null,
                    )
                    if (st.first == 200) {
                        val status = runCatching { JSONObject(st.second).optString("status") }.getOrDefault("")
                        when (status) {
                            "ACCEPTED" -> {
                                accepted = true
                                break
                            }
                            "REJECTED" -> {
                                _transfer.value = _transfer.value?.copy(phase = TransferPhase.Rejected)
                                return@launch
                            }
                            "CANCELLED" -> {
                                _transfer.value = _transfer.value?.copy(phase = TransferPhase.Cancelled)
                                return@launch
                            }
                        }
                    }
                }

                if (activeSendCancelled) {
                    if (!activeSendCancelledByRemote) {
                        runCatching {
                            httpJson(
                                lan,
                                peer.ip,
                                peer.port,
                                "POST",
                                "/api/share/cancel?sessionId=${URLEncoder.encode(sessionId, "UTF-8")}",
                                "{}",
                            )
                        }
                    }
                    _transfer.value = _transfer.value?.copy(phase = TransferPhase.Cancelled)
                    return@launch
                }

                if (!accepted) {
                    runCatching {
                        httpJson(
                            lan,
                            peer.ip,
                            peer.port,
                            "POST",
                            "/api/share/cancel?sessionId=${URLEncoder.encode(sessionId, "UTF-8")}",
                            "{}",
                        )
                    }
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Failed,
                        errorMessage = "Confirmation timed out",
                    )
                    return@launch
                }

                // 3) 对方已同意，逐个流式传输文件
                var batchSentBase = 0L
                var uploadCode = 200
                for ((idx, curFile) in validFiles.withIndex()) {
                    if (activeSendCancelled) break
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Transferring,
                        fileName = curFile.name,
                        bytesTransferred = batchSentBase,
                        totalBytes = totalBytes,
                        currentFileIndex = idx + 1,
                        totalFiles = fileCount,
                    )
                    uploadCode = streamUploadFile(
                        lan = lan,
                        peer = peer,
                        sessionId = sessionId,
                        file = curFile,
                        fileIndex = idx + 1,
                        fileCount = fileCount,
                        isLast = idx == validFiles.lastIndex,
                    ) { sent, speed ->
                        _transfer.value = _transfer.value?.copy(
                            phase = TransferPhase.Transferring,
                            fileName = curFile.name,
                            bytesTransferred = batchSentBase + sent,
                            totalBytes = totalBytes,
                            speedBytesPerSec = speed,
                            currentFileIndex = idx + 1,
                            totalFiles = fileCount,
                            activeFiles = if (fileCount > 1) {
                                listOf(
                                    ShareActiveFileProgress(
                                        id = curFile.path,
                                        fileName = curFile.name,
                                        bytesTransferred = sent,
                                        totalBytes = curFile.length(),
                                        speedBytesPerSec = speed,
                                    ),
                                )
                            } else {
                                emptyList()
                            },
                        )
                    }
                    if (uploadCode !in 200..299 || activeSendCancelled) break
                    batchSentBase += curFile.length()
                }

                if (activeSendCancelled || uploadCode == 499) {
                    if (!activeSendCancelledByRemote && uploadCode != 499) {
                        runCatching {
                            httpJson(
                                lan,
                                peer.ip,
                                peer.port,
                                "POST",
                                "/api/share/cancel?sessionId=${URLEncoder.encode(sessionId, "UTF-8")}",
                                "{}",
                            )
                        }
                    }
                    _transfer.value = _transfer.value?.copy(phase = TransferPhase.Cancelled)
                } else if (uploadCode in 200..299) {
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Completed,
                        fileName = primaryName,
                        bytesTransferred = totalBytes,
                        totalBytes = totalBytes,
                        currentFileIndex = fileCount,
                        totalFiles = fileCount,
                    )
                } else {
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Failed,
                        errorMessage = "Upload failed ($uploadCode)",
                    )
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val cur = _transfer.value
                if (cur?.sessionId == sessionId) {
                    if (activeSendCancelled) {
                        _transfer.value = cur.copy(phase = TransferPhase.Cancelled)
                    } else {
                        _transfer.value = cur.copy(
                            phase = TransferPhase.Failed,
                            errorMessage = t.message ?: "Transfer error",
                        )
                    }
                }
            } finally {
                if (activeSendLocalSessionId == sessionId) {
                    activeSendSocket = null
                    activeSendLocalSessionId = null
                }
            }
        }
    }

    // ───────────────────────── PocketDeck Native HTTP ─────────────────────────

    private fun applyCors(req: HttpRequest, res: HttpResponseWriter): Boolean {
        res.defaultHeaders = QUICKSHARE_CORS_HEADERS
        if (req.method == "OPTIONS") {
            res.sendBytes(204, ByteArray(0), "text/plain; charset=utf-8")
            return true
        }
        return false
    }

    private suspend fun handleHttp(req: HttpRequest, res: HttpResponseWriter) {
        if (applyCors(req, res)) return
        when {
            req.method == "GET" && req.path == "/api/share/ping" -> {
                res.sendJson(
                    200,
                    JSONObject().apply {
                        put("deviceId", localDeviceId)
                        put("deviceName", localDeviceName)
                        put("model", Build.MODEL.orEmpty())
                        put("port", boundPort)
                    }.toString(),
                )
            }
            req.method == "POST" && req.path == "/api/share/prepare" -> handlePrepare(req, res)
            req.method == "GET" && req.path == "/api/share/status" -> handleStatus(req, res)
            req.method == "POST" && req.path == "/api/share/cancel" -> handleCancel(req, res)
            req.method == "POST" && req.path == "/api/share/upload" -> handleUpload(req, res)
            else -> res.sendJson(404, """{"ok":false}""")
        }
    }

    private suspend fun handlePrepare(req: HttpRequest, res: HttpResponseWriter) {
        if (activeReceiveSession != null && activeReceiveSession?.status in setOf(SessionStatus.PENDING, SessionStatus.ACCEPTED)) {
            res.sendJson(409, """{"ok":false,"error":"busy"}""")
            return
        }
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val sessionId = body?.optString("sessionId", "").orEmpty().replace(CONTROL_CHARS, "").trim()
        val senderId = body?.optString("senderId", "").orEmpty().replace(CONTROL_CHARS, "").take(128)
        val senderName = body?.optString("senderName", "").orEmpty().replace(CONTROL_CHARS, "").trim().take(64).ifBlank { req.remoteIp }
        val fileName = sanitizeFileName(body?.optString("fileName", "").orEmpty())
        val fileSize = body?.optLong("fileSize", 0L) ?: 0L
        val platformId = body?.optString("platformId", "").orEmpty().replace(CONTROL_CHARS, "").take(40)
        val platformName = body?.optString("platformName", "").orEmpty().replace(CONTROL_CHARS, "").take(40)
        val gameTitle = body?.optString("gameTitle", "").orEmpty().replace(CONTROL_CHARS, "").take(200).ifBlank { fileName.orEmpty() }
        val namesJson = body?.optJSONArray("fileNames")
        val parsedNames = buildList {
            if (namesJson != null) {
                val limit = minOf(namesJson.length(), MAX_FILES_PER_SESSION)
                for (i in 0 until limit) {
                    sanitizeFileName(namesJson.optString(i, ""))?.let(::add)
                }
            }
            if (isEmpty() && fileName != null) add(fileName)
        }
        val fileCount = (body?.optInt("fileCount", parsedNames.size) ?: parsedNames.size).coerceIn(1, MAX_FILES_PER_SESSION)

        if (sessionId.length !in 1..128 || fileName == null || fileSize < 0L) {
            res.sendJson(400, """{"ok":false,"error":"invalid_request"}""")
            return
        }

        val roots = repo.configuredRoots()
        val candidateDirs = buildCandidateDirs(roots, platformId)
        val defaultDir = candidateDirs.first()

        val incoming = IncomingShareRequest(
            sessionId = sessionId,
            senderId = senderId,
            senderName = senderName,
            senderIp = req.remoteIp,
            fileName = fileName,
            fileSize = fileSize,
            platformId = platformId,
            platformName = platformName,
            gameTitle = gameTitle,
            defaultTargetDir = defaultDir,
            availableDirs = candidateDirs,
            fileCount = fileCount,
            fileNames = parsedNames,
            protocol = ShareProtocol.PocketDeck,
        )
        activeReceiveSession = ReceiveSession(incoming)
        _incomingRequest.value = incoming
        res.sendJson(202, """{"ok":true,"status":"PENDING"}""")
    }

    private fun handleStatus(req: HttpRequest, res: HttpResponseWriter) {
        val sid = req.query["sessionId"].orEmpty()
        val sess = activeReceiveSession
        if (sess == null || sess.req.sessionId != sid) {
            res.sendJson(200, """{"status":"CANCELLED"}""")
            return
        }
        val statusStr = sess.status.name
        if (sess.status == SessionStatus.REJECTED || sess.status == SessionStatus.CANCELLED) {
            activeReceiveSession = null
        }
        res.sendJson(200, """{"status":"$statusStr"}""")
    }

    private fun handleCancel(req: HttpRequest, res: HttpResponseWriter) {
        val sid = req.query["sessionId"].orEmpty()
        val sess = activeReceiveSession
        if (sess != null && sess.req.sessionId == sid) {
            sess.status = SessionStatus.CANCELLED
            sess.cancelled = true
            activeReceiveSession = null
            _incomingRequest.value = null
            sess.activeCancelActions.values.forEach { act -> runCatching { act() } }
            val cur = _transfer.value
            if (cur?.sessionId == sid) {
                _transfer.value = cur.copy(phase = TransferPhase.Cancelled)
            }
        } else {
            val outSid = activeSendLocalSessionId
            if (outSid != null && sid.isNotBlank() && sid == outSid) {
                activeSendCancelledByRemote = true
                activeSendCancelled = true
                runCatching { activeSendSocket?.close() }
                val cur = _transfer.value
                if (cur?.direction == TransferDirection.Sending) {
                    _transfer.value = cur.copy(phase = TransferPhase.Cancelled)
                }
            }
        }
        res.sendJson(200, """{"ok":true}""")
    }

    private suspend fun handleUpload(req: HttpRequest, res: HttpResponseWriter) {
        val sid = req.query["sessionId"].orEmpty()
        val sess = activeReceiveSession
        if (sess == null || sess.req.sessionId != sid || sess.status != SessionStatus.ACCEPTED) {
            res.sendJson(403, """{"ok":false,"error":"not_accepted"}""")
            return
        }

        val rawName = req.query["name"].orEmpty()
        val itemName = sanitizeFileName(rawName) ?: sess.req.fileName
        val itemIdx = req.query["index"]?.toIntOrNull()?.coerceAtLeast(1) ?: (sess.receivedCount.get() + 1)
        val totalFiles = sess.req.fileCount.coerceAtLeast(1)
        val isLast = req.query["isLast"]?.let { it == "1" || it.equals("true", ignoreCase = true) }
            ?: (itemIdx >= totalFiles)

        streamReceiveSingleFile(
            req = req,
            res = res,
            sess = sess,
            sid = sid,
            itemName = itemName,
            itemIdx = itemIdx,
            totalFiles = totalFiles,
            isLast = isLast,
            emptyResponseJson = """{"ok":true}""",
        )
    }

    private fun streamReceiveSingleFile(
        req: HttpRequest,
        res: HttpResponseWriter,
        sess: ReceiveSession,
        sid: String,
        itemName: String,
        itemIdx: Int,
        totalFiles: Int,
        isLast: Boolean,
        emptyResponseJson: String,
    ) {
        val targetDir = sess.targetDir
        if (!targetDir.exists()) runCatching { targetDir.mkdirs() }
        val finalTarget = uniqueFile(targetDir, itemName)
        val partFile = File(targetDir, ".share_${System.currentTimeMillis()}_${finalTarget.name}.part")
        val totalBatchBytes = sess.req.fileSize.coerceAtLeast(req.contentLength)
        val fileTotalBytes = req.contentLength.coerceAtLeast(0L)
        val uploadKey = UUID.randomUUID().toString()
        val nowStart = System.currentTimeMillis()
        sess.sessionStartMs.compareAndSet(0L, nowStart)
        sess.activeCancelActions[uploadKey] = req.cancelConnection
        sess.activeFiles[uploadKey] = ShareActiveFileProgress(
            id = uploadKey,
            fileName = finalTarget.name,
            bytesTransferred = 0L,
            totalBytes = fileTotalBytes,
        )
        var fileWritten = 0L

        val ok = runCatching {
            partFile.outputStream().use { out ->
                val written = req.copyBodyTo(out) { copied ->
                    if (sess.cancelled) throw IllegalStateException("Cancelled")
                    val now = System.currentTimeMillis()
                    val fileElapsedSec = ((now - nowStart).coerceAtLeast(1L)) / 1000.0
                    val fileSpeed = (copied / fileElapsedSec).toLong()
                    sess.activeFiles[uploadKey] = ShareActiveFileProgress(
                        id = uploadKey,
                        fileName = finalTarget.name,
                        bytesTransferred = copied,
                        totalBytes = fileTotalBytes,
                        speedBytesPerSec = fileSpeed,
                    )
                    synchronized(sess.progressLock) {
                        val hitFileEnd = req.contentLength > 0L && copied >= req.contentLength
                        if (now - sess.lastEmitMs >= 120L || hitFileEnd) {
                            sess.lastEmitMs = now
                            val totalNow = sess.completedBytes.get() + sess.activeFiles.values.sumOf { it.bytesTransferred }
                            val monotonicBytes = maxOf(totalNow, sess.lastEmittedBytes)
                            sess.lastEmittedBytes = monotonicBytes
                            val elapsedSec = ((now - sess.sessionStartMs.get()).coerceAtLeast(1L)) / 1000.0
                            val speed = (monotonicBytes / elapsedSec).toLong()
                            val activeSnapshot = sess.activeFiles.values.sortedBy { it.fileName }
                            val displayName = if (activeSnapshot.size > 1) {
                                "${finalTarget.name} (+${activeSnapshot.size - 1})"
                            } else {
                                finalTarget.name
                            }
                            val curIdx = (sess.receivedCount.get() + 1).coerceIn(1, totalFiles)
                            _transfer.value = ShareTransferState(
                                sessionId = sid,
                                direction = TransferDirection.Receiving,
                                phase = TransferPhase.Transferring,
                                peerName = sess.req.senderName,
                                fileName = displayName,
                                bytesTransferred = monotonicBytes,
                                totalBytes = maxOf(totalBatchBytes, monotonicBytes),
                                speedBytesPerSec = speed,
                                currentFileIndex = curIdx,
                                totalFiles = totalFiles,
                                activeFiles = activeSnapshot,
                            )
                        }
                    }
                }
                check(!sess.cancelled && (req.isChunked || written == req.contentLength)) { "Incomplete transfer" }
                fileWritten = written
            }
            check(partFile.renameTo(finalTarget)) { "Cannot finalize received file" }
            true
        }.getOrElse {
            runCatching { partFile.delete() }
            false
        }

        if (!ok) {
            synchronized(sess.progressLock) {
                sess.activeCancelActions.remove(uploadKey)
                sess.activeFiles.remove(uploadKey)
            }
            if (sess.completedOnce.compareAndSet(false, true)) {
                activeReceiveSession = null
                _transfer.value = ShareTransferState(
                    sessionId = sid,
                    direction = TransferDirection.Receiving,
                    phase = if (sess.cancelled) TransferPhase.Cancelled else TransferPhase.Failed,
                    peerName = sess.req.senderName,
                    fileName = finalTarget.name,
                    bytesTransferred = sess.lastEmittedBytes,
                    totalBytes = totalBatchBytes,
                    currentFileIndex = itemIdx,
                    totalFiles = totalFiles,
                    errorMessage = if (sess.cancelled) null else "Receive failed",
                )
            }
            res.sendJson(if (sess.cancelled) 499 else 500, """{"ok":false}""")
            return
        }

        val totalDoneBytes: Long
        val doneCount: Int
        synchronized(sess.progressLock) {
            sess.activeCancelActions.remove(uploadKey)
            sess.activeFiles.remove(uploadKey)
            sess.savedFiles.add(finalTarget)
            totalDoneBytes = sess.completedBytes.addAndGet(fileWritten)
            doneCount = sess.receivedCount.incrementAndGet()
            if (totalDoneBytes > sess.lastEmittedBytes) {
                sess.lastEmittedBytes = totalDoneBytes
            }
        }
        repo.notifyFilesChanged()
        if ((isLast || doneCount >= totalFiles) && sess.completedOnce.compareAndSet(false, true)) {
            activeReceiveSession = null
            repo.refresh()
            val savedPaths = sess.savedFiles.map { it.absolutePath }
            val displayName = if (totalFiles > 1) "${sess.req.fileName} ($totalFiles files)" else finalTarget.name
            val primaryPath = if (totalFiles > 1) targetDir.absolutePath else finalTarget.absolutePath
            val finalTotal = totalDoneBytes.coerceAtLeast(totalBatchBytes)
            recordHistoryItem(
                ShareHistoryItem(
                    id = sid,
                    fileName = displayName,
                    filePath = primaryPath,
                    filePaths = savedPaths,
                    fileCount = savedPaths.size.coerceAtLeast(1),
                    totalBytes = finalTotal,
                    senderName = sess.req.senderName,
                    senderIp = sess.req.senderIp,
                    platformName = sess.req.platformName,
                    receivedAtMs = System.currentTimeMillis(),
                ),
            )
            _transfer.value = ShareTransferState(
                sessionId = sid,
                direction = TransferDirection.Receiving,
                phase = TransferPhase.Completed,
                peerName = sess.req.senderName,
                fileName = displayName,
                bytesTransferred = finalTotal,
                totalBytes = finalTotal,
                savedPath = primaryPath,
                currentFileIndex = totalFiles,
                totalFiles = totalFiles,
            )
        }
        res.sendJson(200, emptyResponseJson)
    }

    // ───────────────────────── LocalSend v2 Protocol ─────────────────────────

    private fun startLocalSend(s: CoroutineScope, lan: LanInterface) {
        synchronized(localSendLock) {
            stopLocalSendLocked()
            val cert = runCatching { LocalSendCert.getOrCreate(context) }.getOrNull()
            val server = runCatching {
                HttpMiniServer(
                    bindAddress = lan.address,
                    preferredPort = LOCALSEND_PORT,
                    portScanCount = 10,
                    sslContext = cert?.serverSslContext,
                ) { req, res ->
                    handleLocalSendHttp(req, res)
                }.also { it.start() }
            }.getOrNull()
            localSendHttpServer = server

            val mcast = runCatching {
                MulticastSocket(LOCALSEND_PORT).apply {
                    reuseAddress = true
                    soTimeout = 2000
                    runCatching { networkInterface = lan.networkInterface }
                    runCatching {
                        val group = InetSocketAddress(InetAddress.getByName(MULTICAST_ADDR), LOCALSEND_PORT)
                        joinGroup(group, lan.networkInterface)
                    }
                }
            }.getOrNull()
            localSendDiscoverySocket = mcast

            if (mcast != null) {
                localSendJobs += s.launch {
                    val buf = ByteArray(4096)
                    while (isActive && !mcast.isClosed && localSendCompatEnabled) {
                        val pkt = DatagramPacket(buf, buf.size)
                        val ok = runCatching { mcast.receive(pkt); true }.getOrDefault(false)
                        if (!ok) continue
                        val senderIp = pkt.address?.hostAddress.orEmpty()
                        val msg = runCatching {
                            String(pkt.data, pkt.offset, pkt.length, StandardCharsets.UTF_8)
                        }.getOrNull() ?: continue
                        handleLocalSendDiscoveryPacket(msg, senderIp, lan)
                    }
                }
            }

            localSendJobs += s.launch {
                while (isActive && localSendCompatEnabled) {
                    sendLocalSendAnnounce(announce = true)
                    delay(ANNOUNCE_INTERVAL_MS)
                }
            }
        }
    }

    private fun stopLocalSend() {
        synchronized(localSendLock) {
            stopLocalSendLocked()
        }
        localSendPeers.clear()
        publishPeers()
    }

    private fun stopLocalSendLocked() {
        localSendJobs.forEach { it.cancel() }
        localSendJobs.clear()
        runCatching { localSendDiscoverySocket?.close() }
        localSendDiscoverySocket = null
        localSendHttpServer?.stop()
        localSendHttpServer = null
    }

    private fun localSendInfoJson(): JSONObject {
        val cert = runCatching { LocalSendCert.getOrCreate(context) }.getOrNull()
        val port = localSendBoundPort.takeIf { it > 0 } ?: LOCALSEND_PORT
        return JSONObject().apply {
            put("alias", localDeviceName.ifBlank { Build.MODEL.orEmpty() })
            put("version", "2.0")
            put("deviceModel", Build.MODEL.orEmpty())
            put("deviceType", "mobile")
            put("fingerprint", cert?.fingerprint ?: localDeviceId)
            put("port", port)
            put("protocol", if (cert != null) "https" else "http")
            put("download", false)
        }
    }

    private fun sendLocalSendAnnounce(announce: Boolean) {
        val lan = activeLan ?: return
        if (!localSendCompatEnabled || localSendBoundPort <= 0) return
        val payload = localSendInfoJson().apply {
            put("announce", announce)
        }.toString().toByteArray(StandardCharsets.UTF_8)

        runCatching {
            DatagramSocket(InetSocketAddress(lan.address, 0)).use { sock ->
                sock.broadcast = true
                runCatching {
                    sock.send(DatagramPacket(payload, payload.size, InetAddress.getByName(MULTICAST_ADDR), LOCALSEND_PORT))
                }
                lan.broadcastAddress?.let { bcast ->
                    runCatching {
                        sock.send(DatagramPacket(payload, payload.size, bcast, LOCALSEND_PORT))
                    }
                }
            }
        }
    }

    private fun handleLocalSendDiscoveryPacket(raw: String, packetIp: String, lan: LanInterface) {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val fp = json.optString("fingerprint", "").trim()
        val myFp = runCatching { LocalSendCert.getOrCreate(context).fingerprint }.getOrNull().orEmpty()
        if (fp.isBlank() || fp == myFp || fp == localDeviceId) return
        if (packetIp == lan.ipAddress && json.optInt("port", LOCALSEND_PORT) == localSendBoundPort) return

        val alias = json.optString("alias", "").ifBlank { packetIp }
        val model = json.optString("deviceModel", "").ifBlank { json.optString("deviceType", "LocalSend") }
        val port = json.optInt("port", LOCALSEND_PORT).takeIf { it > 0 } ?: LOCALSEND_PORT
        val proto = json.optString("protocol", "https").lowercase()
        val useHttps = proto != "http"
        val announce = json.optBoolean("announce", false)

        localSendPeers[fp] = LanPeer(
            deviceId = fp,
            deviceName = alias,
            model = model,
            ip = packetIp,
            port = port,
            lastSeenMs = System.currentTimeMillis(),
            protocol = ShareProtocol.LocalSend,
            useHttps = useHttps,
            fingerprint = fp,
        )
        publishPeers()

        if (announce) {
            scope?.launch(Dispatchers.IO) {
                val infoStr = localSendInfoJson().toString()
                val ok = runCatching {
                    val cert = LocalSendCert.getOrCreate(context)
                    httpJsonAny(
                        lan = lan,
                        ip = packetIp,
                        port = port,
                        useHttps = useHttps,
                        sslContext = cert.clientSslContext,
                        method = "POST",
                        path = "/api/localsend/v2/register",
                        jsonBody = infoStr,
                    ).first in 200..299
                }.getOrDefault(false)
                if (!ok) {
                    sendLocalSendAnnounce(announce = false)
                }
            }
        }
    }

    private suspend fun handleLocalSendHttp(req: HttpRequest, res: HttpResponseWriter) {
        if (applyCors(req, res)) return
        if (!localSendCompatEnabled) {
            res.sendJson(404, """{"message":"disabled"}""")
            return
        }
        when {
            req.method == "GET" && req.path == "/api/localsend/v2/info" -> {
                res.sendJson(200, localSendInfoJson().toString())
            }
            req.method == "POST" && req.path == "/api/localsend/v2/register" -> {
                val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
                if (body != null) {
                    val fp = body.optString("fingerprint", "").trim()
                    val myFp = runCatching { LocalSendCert.getOrCreate(context).fingerprint }.getOrNull().orEmpty()
                    if (fp.isNotBlank() && fp != myFp) {
                        val alias = body.optString("alias", "").ifBlank { req.remoteIp }
                        val model = body.optString("deviceModel", "").ifBlank { body.optString("deviceType", "LocalSend") }
                        val port = body.optInt("port", LOCALSEND_PORT).takeIf { it > 0 } ?: LOCALSEND_PORT
                        val useHttps = body.optString("protocol", "https").lowercase() != "http"
                        localSendPeers[fp] = LanPeer(
                            deviceId = fp,
                            deviceName = alias,
                            model = model,
                            ip = req.remoteIp,
                            port = port,
                            lastSeenMs = System.currentTimeMillis(),
                            protocol = ShareProtocol.LocalSend,
                            useHttps = useHttps,
                            fingerprint = fp,
                        )
                        publishPeers()
                    }
                }
                res.sendJson(200, localSendInfoJson().toString())
            }
            req.method == "POST" && req.path == "/api/localsend/v2/prepare-upload" -> {
                handleLocalSendPrepareUpload(req, res)
            }
            req.method == "POST" && req.path == "/api/localsend/v2/upload" -> {
                handleLocalSendUpload(req, res)
            }
            req.method == "POST" && req.path == "/api/localsend/v2/cancel" -> {
                val sid = req.query["sessionId"].orEmpty()
                val sess = activeReceiveSession
                if (sess != null && (sid.isBlank() || sess.req.sessionId == sid)) {
                    sess.status = SessionStatus.CANCELLED
                    sess.cancelled = true
                    sess.activeCancelActions.values.forEach { runCatching { it() } }
                    sess.activeCancelActions.clear()
                    activeReceiveSession = null
                    if (_incomingRequest.value?.sessionId == sess.req.sessionId) {
                        _incomingRequest.value = null
                    }
                    val cur = _transfer.value
                    if (cur?.sessionId == sess.req.sessionId) {
                        _transfer.value = cur.copy(phase = TransferPhase.Cancelled)
                    }
                } else {
                    val outSid = activeSendRemoteSessionId
                    if (outSid != null && (sid.isBlank() || sid == outSid)) {
                        activeSendCancelledByRemote = true
                        activeSendCancelled = true
                        runCatching { activeSendSocket?.close() }
                        val cur = _transfer.value
                        if (cur?.direction == TransferDirection.Sending) {
                            _transfer.value = cur.copy(phase = TransferPhase.Cancelled)
                        }
                    }
                }
                res.sendJson(200, "{}")
            }
            else -> res.sendJson(404, """{"message":"not_found"}""")
        }
    }

    private suspend fun handleLocalSendPrepareUpload(req: HttpRequest, res: HttpResponseWriter) {
        if (activeReceiveSession != null && activeReceiveSession?.status in setOf(SessionStatus.PENDING, SessionStatus.ACCEPTED)) {
            res.sendJson(409, """{"message":"Blocked by another session"}""")
            return
        }
        val body = runCatching { JSONObject(req.readBodyUtf8()) }.getOrNull()
        val infoObj = body?.optJSONObject("info")
        val filesObj = body?.optJSONObject("files")
        if (body == null || filesObj == null || filesObj.length() == 0) {
            res.sendJson(400, """{"message":"Invalid prepare-upload payload"}""")
            return
        }

        val senderId = infoObj?.optString("fingerprint", "").orEmpty().replace(CONTROL_CHARS, "").take(128).ifBlank { req.remoteIp }
        val senderName = infoObj?.optString("alias", "").orEmpty().replace(CONTROL_CHARS, "").trim().take(64).ifBlank { req.remoteIp }
        if (filesObj.length() > MAX_FILES_PER_SESSION) {
            res.sendJson(400, """{"message":"Too many files in one session"}""")
            return
        }

        val idToName = LinkedHashMap<String, String>()
        val idToToken = LinkedHashMap<String, String>()
        var totalSize = 0L
        val keys = filesObj.keys()
        while (keys.hasNext()) {
            val fid = keys.next()
            val fObj = filesObj.optJSONObject(fid) ?: continue
            val rawName = fObj.optString("fileName", "")
            val cleanName = sanitizeLocalSendFileName(rawName)
            if (cleanName == null) {
                res.sendJson(400, """{"message":"Invalid file name"}""")
                return
            }
            val fSize = fObj.optLong("size", 0L).coerceAtLeast(0L)
            totalSize += fSize
            idToName[fid] = cleanName
            idToToken[fid] = UUID.randomUUID().toString().replace("-", "")
        }

        if (idToName.isEmpty()) {
            res.sendJson(400, """{"message":"No valid files"}""")
            return
        }

        val sessionId = UUID.randomUUID().toString()
        val fileNamesList = idToName.values.toList()
        val roots = repo.configuredRoots()
        val candidateDirs = buildCandidateDirs(roots, "")
        val defaultDir = candidateDirs.first()

        val incoming = IncomingShareRequest(
            sessionId = sessionId,
            senderId = senderId,
            senderName = senderName,
            senderIp = req.remoteIp,
            fileName = fileNamesList.first(),
            fileSize = totalSize,
            platformId = "",
            platformName = "LocalSend",
            gameTitle = fileNamesList.first(),
            defaultTargetDir = defaultDir,
            availableDirs = candidateDirs,
            fileCount = fileNamesList.size,
            fileNames = fileNamesList,
            protocol = ShareProtocol.LocalSend,
        )
        val sess = ReceiveSession(
            req = incoming,
            localSendFileNames = idToName,
            localSendFileTokens = idToToken,
        )
        activeReceiveSession = sess
        _incomingRequest.value = incoming

        // LocalSend prepare-upload 长轮询等待用户点击接收或拒绝；同步探测对端是否已断开连接（取消发送）
        val deadline = System.currentTimeMillis() + 90_000L
        var checkTick = 0
        while (sess.status == SessionStatus.PENDING && !sess.cancelled && System.currentTimeMillis() < deadline) {
            if (checkTick % 2 == 1 && req.isPeerClosed()) {
                sess.status = SessionStatus.CANCELLED
                sess.cancelled = true
                if (activeReceiveSession === sess) activeReceiveSession = null
                if (_incomingRequest.value?.sessionId == sessionId) _incomingRequest.value = null
                val cur = _transfer.value
                if (cur?.sessionId == sessionId) {
                    _transfer.value = cur.copy(phase = TransferPhase.Cancelled)
                }
                return
            }
            checkTick++
            delay(120)
        }

        when (sess.status) {
            SessionStatus.ACCEPTED -> {
                val filesTokensJson = JSONObject()
                idToToken.forEach { (fid, tok) -> filesTokensJson.put(fid, tok) }
                val sendRes = runCatching {
                    res.sendJson(
                        200,
                        JSONObject().apply {
                            put("sessionId", sessionId)
                            put("files", filesTokensJson)
                        }.toString(),
                    )
                }
                if (sendRes.isFailure) {
                    sess.cancelled = true
                    if (activeReceiveSession === sess) activeReceiveSession = null
                    val cur = _transfer.value
                    if (cur?.sessionId == sessionId) {
                        _transfer.value = cur.copy(phase = TransferPhase.Cancelled)
                    }
                    return
                }
                // 看门狗：若对端在同意后 20 秒内始终未开始上传任何数据，自动结束等待态避免无限卡住
                scope?.launch {
                    delay(20_000L)
                    if (activeReceiveSession === sess &&
                        !sess.cancelled &&
                        sess.receivedCount.get() == 0 &&
                        sess.activeFiles.isEmpty() &&
                        sess.completedBytes.get() == 0L
                    ) {
                        sess.cancelled = true
                        activeReceiveSession = null
                        val cur = _transfer.value
                        if (cur?.sessionId == sessionId && cur.phase == TransferPhase.Transferring) {
                            _transfer.value = cur.copy(phase = TransferPhase.Cancelled)
                        }
                    }
                }
            }
            SessionStatus.REJECTED, SessionStatus.CANCELLED -> {
                if (activeReceiveSession === sess) activeReceiveSession = null
                if (_incomingRequest.value?.sessionId == sessionId) _incomingRequest.value = null
                runCatching { res.sendJson(403, """{"message":"Rejected by user"}""") }
            }
            SessionStatus.PENDING -> {
                sess.cancelled = true
                if (activeReceiveSession === sess) activeReceiveSession = null
                if (_incomingRequest.value?.sessionId == sessionId) _incomingRequest.value = null
                runCatching { res.sendJson(408, """{"message":"Timed out waiting for acceptance"}""") }
            }
        }
    }

    private suspend fun handleLocalSendUpload(req: HttpRequest, res: HttpResponseWriter) {
        val sid = req.query["sessionId"].orEmpty()
        val fid = req.query["fileId"].orEmpty()
        val tok = req.query["token"].orEmpty()
        val sess = activeReceiveSession
        if (sess == null || sess.req.sessionId != sid || sess.status != SessionStatus.ACCEPTED) {
            res.sendJson(403, """{"message":"Session not accepted"}""")
            return
        }
        val expectedToken = sess.localSendFileTokens[fid]
        val expectedName = sess.localSendFileNames[fid]
        if (expectedToken == null || expectedName == null || expectedToken != tok) {
            res.sendJson(403, """{"message":"Invalid file token"}""")
            return
        }
        val totalFiles = sess.req.fileCount.coerceAtLeast(1)
        val nextIdx = (sess.receivedCount.get() + 1).coerceAtMost(totalFiles)
        streamReceiveSingleFile(
            req = req,
            res = res,
            sess = sess,
            sid = sid,
            itemName = expectedName,
            itemIdx = nextIdx,
            totalFiles = totalFiles,
            isLast = false,
            emptyResponseJson = "{}",
        )
    }

    private fun sendFilesViaLocalSend(peer: LanPeer, files: List<File>) {
        val lan = activeLan ?: return
        val validFiles = files.filter { it.isFile && it.canRead() }
        if (validFiles.isEmpty()) return
        val s = scope ?: return

        sendJob?.cancel()
        clearPendingPin(null)
        activeSendCancelled = false
        activeSendCancelledByRemote = false
        activeSendLocalSessionId = null
        val totalBytes = validFiles.sumOf { it.length() }
        val fileCount = validFiles.size
        val primaryName = if (fileCount == 1) validFiles.first().name else "${validFiles.first().name} +${fileCount - 1}"
        val tempSessionId = UUID.randomUUID().toString()

        _transfer.value = ShareTransferState(
            sessionId = tempSessionId,
            direction = TransferDirection.Sending,
            phase = TransferPhase.WaitingAccept,
            peerName = peer.deviceName,
            fileName = primaryName,
            bytesTransferred = 0L,
            totalBytes = totalBytes,
            currentFileIndex = 1,
            totalFiles = fileCount,
        )

        sendJob = s.launch {
            var remoteSessionId = ""
            try {
                val cert = LocalSendCert.getOrCreate(context)
                val filesJson = JSONObject()
                val idToFile = LinkedHashMap<String, File>()
                validFiles.forEachIndexed { idx, file ->
                    val fid = "f_$idx"
                    idToFile[fid] = file
                    filesJson.put(
                        fid,
                        JSONObject().apply {
                            put("id", fid)
                            put("fileName", file.name)
                            put("size", file.length())
                            put("fileType", guessMime(file))
                        },
                    )
                }

                val prepBody = JSONObject().apply {
                    put("info", localSendInfoJson())
                    put("files", filesJson)
                }.toString()

                var pin: String? = null
                var prepRes: Pair<Int, String>
                var pinAttempt = 0
                while (true) {
                    if (activeSendCancelled) {
                        _transfer.value = _transfer.value?.copy(phase = TransferPhase.Cancelled)
                        return@launch
                    }
                    val prepPath = if (pin != null) {
                        "/api/localsend/v2/prepare-upload?pin=${URLEncoder.encode(pin, "UTF-8")}"
                    } else {
                        "/api/localsend/v2/prepare-upload"
                    }
                    prepRes = httpJsonAny(
                        lan = lan,
                        ip = peer.ip,
                        port = peer.port,
                        useHttps = peer.useHttps,
                        sslContext = cert.clientSslContext,
                        method = "POST",
                        path = prepPath,
                        jsonBody = prepBody,
                        readTimeoutMs = 95_000,
                    )
                    if (activeSendCancelled) {
                        _transfer.value = _transfer.value?.copy(phase = TransferPhase.Cancelled)
                        return@launch
                    }
                    if (prepRes.first != 401) break

                    val wrong = pinAttempt > 0
                    pinAttempt++
                    val deferred = CompletableDeferred<String?>()
                    pendingPinDeferred = deferred
                    _transfer.value = _transfer.value?.copy(phase = TransferPhase.PinRequired)
                    _pinChallenge.value = LocalSendPinChallenge(
                        sessionId = tempSessionId,
                        peerName = peer.deviceName,
                        wrongPin = wrong,
                    )
                    val entered = try {
                        deferred.await()
                    } finally {
                        if (pendingPinDeferred === deferred) pendingPinDeferred = null
                        if (_pinChallenge.value?.sessionId == tempSessionId) _pinChallenge.value = null
                    }
                    if (entered.isNullOrBlank() || activeSendCancelled) {
                        activeSendCancelled = true
                        _transfer.value = _transfer.value?.copy(phase = TransferPhase.Cancelled)
                        return@launch
                    }
                    pin = entered
                    _transfer.value = _transfer.value?.copy(phase = TransferPhase.WaitingAccept)
                }

                if (prepRes.first == 403) {
                    _transfer.value = _transfer.value?.copy(phase = TransferPhase.Rejected)
                    return@launch
                }
                if (prepRes.first == 429) {
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Failed,
                        errorMessage = "Too many PIN attempts (429)",
                    )
                    return@launch
                }
                if (prepRes.first == 409) {
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Failed,
                        errorMessage = "Target device is busy",
                    )
                    return@launch
                }
                if (prepRes.first !in 200..299) {
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Failed,
                        errorMessage = "LocalSend prepare failed (${prepRes.first})",
                    )
                    return@launch
                }

                val prepObj = JSONObject(prepRes.second)
                remoteSessionId = prepObj.optString("sessionId", "")
                activeSendRemoteSessionId = remoteSessionId.takeIf { it.isNotBlank() }
                val tokensObj = prepObj.optJSONObject("files") ?: JSONObject()

                var batchSentBase = 0L
                var uploadCode = 200
                var idx = 0
                for ((fid, curFile) in idToFile) {
                    if (activeSendCancelled) break
                    val token = tokensObj.optString(fid, "")
                    if (token.isBlank()) continue
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Transferring,
                        fileName = curFile.name,
                        bytesTransferred = batchSentBase,
                        totalBytes = totalBytes,
                        currentFileIndex = idx + 1,
                        totalFiles = fileCount,
                    )
                    val encSid = URLEncoder.encode(remoteSessionId, "UTF-8")
                    val encFid = URLEncoder.encode(fid, "UTF-8")
                    val encTok = URLEncoder.encode(token, "UTF-8")
                    uploadCode = streamUploadRaw(
                        lan = lan,
                        ip = peer.ip,
                        port = peer.port,
                        useHttps = peer.useHttps,
                        sslContext = cert.clientSslContext,
                        path = "/api/localsend/v2/upload?sessionId=$encSid&fileId=$encFid&token=$encTok",
                        file = curFile,
                    ) { sent, speed ->
                        _transfer.value = _transfer.value?.copy(
                            phase = TransferPhase.Transferring,
                            fileName = curFile.name,
                            bytesTransferred = batchSentBase + sent,
                            totalBytes = totalBytes,
                            speedBytesPerSec = speed,
                            currentFileIndex = idx + 1,
                            totalFiles = fileCount,
                            activeFiles = if (fileCount > 1) {
                                listOf(
                                    ShareActiveFileProgress(
                                        id = curFile.path,
                                        fileName = curFile.name,
                                        bytesTransferred = sent,
                                        totalBytes = curFile.length(),
                                        speedBytesPerSec = speed,
                                    ),
                                )
                            } else {
                                emptyList()
                            },
                        )
                    }
                    if (uploadCode !in 200..299 || activeSendCancelled) break
                    batchSentBase += curFile.length()
                    idx++
                }

                if (activeSendCancelled) {
                    if (remoteSessionId.isNotBlank() && !activeSendCancelledByRemote) {
                        runCatching {
                            httpJsonAny(
                                lan, peer.ip, peer.port, peer.useHttps, cert.clientSslContext,
                                "POST", "/api/localsend/v2/cancel?sessionId=${URLEncoder.encode(remoteSessionId, "UTF-8")}", null,
                            )
                        }
                    }
                    _transfer.value = _transfer.value?.copy(phase = TransferPhase.Cancelled)
                } else if (uploadCode in 200..299) {
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Completed,
                        fileName = primaryName,
                        bytesTransferred = totalBytes,
                        totalBytes = totalBytes,
                        currentFileIndex = fileCount,
                        totalFiles = fileCount,
                    )
                } else {
                    _transfer.value = _transfer.value?.copy(
                        phase = TransferPhase.Failed,
                        errorMessage = "Upload failed ($uploadCode)",
                    )
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val cur = _transfer.value
                if (cur?.sessionId == tempSessionId) {
                    if (activeSendCancelled) {
                        _transfer.value = cur.copy(phase = TransferPhase.Cancelled)
                    } else {
                        _transfer.value = cur.copy(
                            phase = TransferPhase.Failed,
                            errorMessage = t.message ?: "LocalSend error",
                        )
                    }
                }
            } finally {
                if (_transfer.value?.sessionId == tempSessionId || activeSendRemoteSessionId == remoteSessionId) {
                    clearPendingPin(null)
                    activeSendSocket = null
                    activeSendRemoteSessionId = null
                }
            }
        }
    }

    // ───────────────────────── History & Discovery ─────────────────────────

    fun deleteHistoryItem(id: String, deleteFiles: Boolean = false) {
        val removed = synchronized(historyLock) {
            val cur = _history.value
            val target = cur.firstOrNull { it.id == id }
            val next = cur.filterNot { it.id == id }
            if (next.size != cur.size) {
                _history.value = next
                saveHistoryToDisk(next)
            }
            target
        }
        if (deleteFiles && removed != null) {
            val paths = if (removed.filePaths.isNotEmpty()) removed.filePaths else listOf(removed.filePath)
            paths.forEach { p ->
                val f = File(p)
                if (f.isFile) runCatching { f.delete() }
            }
            repo.notifyFilesChanged()
            repo.refresh()
        }
    }

    fun clearHistory(deleteFiles: Boolean = false) {
        val old = synchronized(historyLock) {
            val prev = _history.value
            _history.value = emptyList()
            saveHistoryToDisk(emptyList())
            prev
        }
        if (deleteFiles && old.isNotEmpty()) {
            old.forEach { item ->
                val paths = if (item.filePaths.isNotEmpty()) item.filePaths else listOf(item.filePath)
                paths.forEach { p ->
                    val f = File(p)
                    if (f.isFile) runCatching { f.delete() }
                }
            }
            repo.notifyFilesChanged()
            repo.refresh()
        }
    }

    private fun recordHistoryItem(item: ShareHistoryItem) {
        synchronized(historyLock) {
            val next = (listOf(item) + _history.value.filterNot { it.id == item.id }).take(MAX_HISTORY_ITEMS)
            _history.value = next
            saveHistoryToDisk(next)
        }
    }

    private fun loadHistoryFromDisk(): List<ShareHistoryItem> = runCatching {
        if (!historyFile.isFile) return emptyList()
        val arr = JSONArray(historyFile.readText(StandardCharsets.UTF_8))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", "").ifBlank { continue }
                val pathsArr = o.optJSONArray("filePaths")
                val paths = buildList {
                    if (pathsArr != null) {
                        for (j in 0 until pathsArr.length()) {
                            pathsArr.optString(j, "").takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }
                add(
                    ShareHistoryItem(
                        id = id,
                        fileName = o.optString("fileName", ""),
                        filePath = o.optString("filePath", ""),
                        filePaths = paths,
                        fileCount = o.optInt("fileCount", 1).coerceAtLeast(1),
                        totalBytes = o.optLong("totalBytes", 0L),
                        senderName = o.optString("senderName", ""),
                        senderIp = o.optString("senderIp", ""),
                        platformName = o.optString("platformName", ""),
                        receivedAtMs = o.optLong("receivedAtMs", 0L),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun saveHistoryToDisk(list: List<ShareHistoryItem>) {
        runCatching {
            val arr = JSONArray()
            for (item in list) {
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("fileName", item.fileName)
                        put("filePath", item.filePath)
                        put("filePaths", JSONArray(item.filePaths))
                        put("fileCount", item.fileCount)
                        put("totalBytes", item.totalBytes)
                        put("senderName", item.senderName)
                        put("senderIp", item.senderIp)
                        put("platformName", item.platformName)
                        put("receivedAtMs", item.receivedAtMs)
                    },
                )
            }
            historyFile.writeText(arr.toString(), StandardCharsets.UTF_8)
        }
    }

    private fun startDiscovery(s: CoroutineScope, lan: LanInterface) {
        val mcast = runCatching {
            MulticastSocket(DISCOVERY_PORT).apply {
                reuseAddress = true
                soTimeout = 2000
                runCatching { networkInterface = lan.networkInterface }
                runCatching {
                    val group = InetSocketAddress(InetAddress.getByName(MULTICAST_ADDR), DISCOVERY_PORT)
                    joinGroup(group, lan.networkInterface)
                }
            }
        }.getOrNull()
        discoverySocket = mcast

        // 接收 UDP 发现包
        if (mcast != null) {
            s.launch {
                val buf = ByteArray(4096)
                while (isActive && !mcast.isClosed) {
                    val pkt = DatagramPacket(buf, buf.size)
                    val ok = runCatching { mcast.receive(pkt); true }.getOrDefault(false)
                    if (!ok) continue
                    val senderIp = pkt.address?.hostAddress.orEmpty()
                    val msg = runCatching { String(pkt.data, pkt.offset, pkt.length, StandardCharsets.UTF_8) }.getOrNull()
                        ?: continue
                    handleDiscoveryMessage(msg, senderIp)
                }
            }
        }

        // 定时广播自己 + 清理离线设备
        s.launch {
            while (isActive) {
                sendDiscoveryPacket("announce")
                pruneStalePeers()
                delay(ANNOUNCE_INTERVAL_MS)
            }
        }
    }

    private fun handleDiscoveryMessage(raw: String, packetIp: String) {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val type = json.optString("type", "")
        val id = json.optString("deviceId", "")
        if (id.isBlank() || id == localDeviceId) return

        when (type) {
            "goodbye" -> {
                if (pocketDeckPeers.remove(id) != null) publishPeers()
            }
            "probe", "announce" -> {
                val name = json.optString("deviceName", "").replace(CONTROL_CHARS, "").trim().take(64).ifBlank { packetIp }
                val model = json.optString("model", "").replace(CONTROL_CHARS, "").trim().take(64)
                val ip = packetIp
                val port = json.optInt("port", TRANSFER_PREFERRED_PORT)
                if (ip.isNotBlank() && port in 1..65535) {
                    pocketDeckPeers[id] = LanPeer(
                        deviceId = id,
                        deviceName = name,
                        model = model,
                        ip = ip,
                        port = port,
                        lastSeenMs = System.currentTimeMillis(),
                        protocol = ShareProtocol.PocketDeck,
                    )
                    publishPeers()
                }
                if (type == "probe") {
                    scope?.launch(Dispatchers.IO) { sendDiscoveryPacket("announce") }
                }
            }
        }
    }

    private fun pruneStalePeers() {
        val now = System.currentTimeMillis()
        var changed = false
        for (map in listOf(pocketDeckPeers, localSendPeers)) {
            val it = map.entries.iterator()
            while (it.hasNext()) {
                if (now - it.next().value.lastSeenMs > PEER_STALE_MS) {
                    it.remove()
                    changed = true
                }
            }
        }
        if (changed) publishPeers()
    }

    private fun publishPeers() {
        val pdIps = pocketDeckPeers.values.map { it.ip }.toSet()
        val combined = pocketDeckPeers.values + localSendPeers.values.filter { it.ip !in pdIps }
        _peers.value = combined.sortedBy { it.deviceName.lowercase() }
    }

    private fun sendDiscoveryPacket(type: String) {
        val lan = activeLan ?: return
        sendDiscoveryPacketSync(lan, type)
    }

    private fun sendDiscoveryPacketSync(lan: LanInterface, type: String) {
        val port = boundPort
        if (port <= 0 && type != "goodbye") return
        val payload = JSONObject().apply {
            put("type", type)
            put("deviceId", localDeviceId)
            put("deviceName", localDeviceName)
            put("model", Build.MODEL.orEmpty())
            put("ip", lan.ipAddress)
            put("port", port)
        }.toString().toByteArray(StandardCharsets.UTF_8)

        runCatching {
            DatagramSocket(InetSocketAddress(lan.address, 0)).use { sock ->
                sock.broadcast = true
                runCatching {
                    sock.send(DatagramPacket(payload, payload.size, InetAddress.getByName(MULTICAST_ADDR), DISCOVERY_PORT))
                }
                lan.broadcastAddress?.let { bcast ->
                    runCatching {
                        sock.send(DatagramPacket(payload, payload.size, bcast, DISCOVERY_PORT))
                    }
                }
            }
        }
    }

    private fun httpJson(
        lan: LanInterface,
        ip: String,
        port: Int,
        method: String,
        path: String,
        jsonBody: String?,
    ): Pair<Int, String> = httpJsonAny(lan, ip, port, false, null, method, path, jsonBody)

    private fun httpJsonAny(
        lan: LanInterface,
        ip: String,
        port: Int,
        useHttps: Boolean,
        sslContext: javax.net.ssl.SSLContext?,
        method: String,
        path: String,
        jsonBody: String?,
        readTimeoutMs: Int = 10_000,
        trackAsActiveSend: Boolean = false,
    ): Pair<Int, String> {
        val sock = if (useHttps && sslContext != null) {
            LanInterfaces.openBoundTlsSocket(lan, sslContext, ip, port, connectTimeoutMs = 4000, readTimeoutMs = readTimeoutMs)
        } else {
            LanInterfaces.openBoundSocket(lan, ip, port, connectTimeoutMs = 4000, readTimeoutMs = readTimeoutMs)
        }
        if (trackAsActiveSend) activeSendSocket = sock
        return sock.use { s ->
            val bodyBytes = jsonBody?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
            val out = BufferedOutputStream(s.getOutputStream())
            val reqHead = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: $ip:$port\r\n")
                append("Connection: close\r\n")
                if (jsonBody != null) {
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                }
                append("\r\n")
            }
            out.write(reqHead.toByteArray(StandardCharsets.US_ASCII))
            if (bodyBytes.isNotEmpty()) out.write(bodyBytes)
            out.flush()
            readHttpResponse(s)
        }
    }

    private fun streamUploadFile(
        lan: LanInterface,
        peer: LanPeer,
        sessionId: String,
        file: File,
        fileIndex: Int = 1,
        fileCount: Int = 1,
        isLast: Boolean = true,
        onProgress: (sentBytes: Long, speedBps: Long) -> Unit,
    ): Int {
        val encodedSid = URLEncoder.encode(sessionId, "UTF-8")
        val encodedName = URLEncoder.encode(file.name, "UTF-8")
        val lastFlag = if (isLast) 1 else 0
        return streamUploadRaw(
            lan = lan,
            ip = peer.ip,
            port = peer.port,
            useHttps = false,
            sslContext = null,
            path = "/api/share/upload?sessionId=$encodedSid&name=$encodedName&index=$fileIndex&count=$fileCount&isLast=$lastFlag",
            file = file,
            onProgress = onProgress,
        )
    }

    private fun streamUploadRaw(
        lan: LanInterface,
        ip: String,
        port: Int,
        useHttps: Boolean,
        sslContext: javax.net.ssl.SSLContext?,
        path: String,
        file: File,
        onProgress: (sentBytes: Long, speedBps: Long) -> Unit,
    ): Int {
        val total = file.length()
        val sock = if (useHttps && sslContext != null) {
            LanInterfaces.openBoundTlsSocket(lan, sslContext, ip, port, connectTimeoutMs = 5000, readTimeoutMs = 120_000)
        } else {
            LanInterfaces.openBoundSocket(lan, ip, port, connectTimeoutMs = 5000, readTimeoutMs = 120_000)
        }
        activeSendSocket = sock
        return sock.use { s ->
            val out = BufferedOutputStream(s.getOutputStream(), 64 * 1024)
            val reqHead = buildString {
                append("POST $path HTTP/1.1\r\n")
                append("Host: $ip:$port\r\n")
                append("Content-Type: application/octet-stream\r\n")
                append("Content-Length: $total\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(reqHead.toByteArray(StandardCharsets.US_ASCII))
            val startMs = System.currentTimeMillis()
            var lastEmitMs = startMs
            var sent = 0L
            FileInputStream(file).use { fis ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (activeSendCancelled) break
                    val n = fis.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    sent += n
                    val now = System.currentTimeMillis()
                    if (now - lastEmitMs >= 120L || sent >= total) {
                        lastEmitMs = now
                        val sec = ((now - startMs).coerceAtLeast(1L)) / 1000.0
                        onProgress(sent, (sent / sec).toLong())
                    }
                }
            }
            out.flush()
            if (activeSendCancelled) return 499
            readHttpResponse(s).first
        }
    }

    private fun readHttpResponse(sock: Socket): Pair<Int, String> {
        val input = BufferedInputStream(sock.getInputStream())
        val statusLine = readLine(input) ?: return 500 to ""
        val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 500
        var contentLen = -1
        var chunked = false
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
                val hName = line.substring(0, idx).trim()
                val hVal = line.substring(idx + 1).trim()
                if (hName.equals("content-length", ignoreCase = true)) {
                    contentLen = hVal.toIntOrNull() ?: -1
                } else if (hName.equals("transfer-encoding", ignoreCase = true) && hVal.contains("chunked", ignoreCase = true)) {
                    chunked = true
                }
            }
        }
        val body = if (chunked) {
            val baos = ByteArrayOutputStream()
            while (baos.size() < MAX_CONTROL_RESPONSE_BYTES) {
                val sizeLine = readLine(input)?.substringBefore(';')?.trim() ?: break
                val chunkSize = sizeLine.toIntOrNull(16) ?: break
                if (chunkSize <= 0) break
                var rem = chunkSize
                val tmp = ByteArray(minOf(chunkSize, 4096))
                while (rem > 0) {
                    val n = input.read(tmp, 0, minOf(rem, tmp.size))
                    if (n <= 0) break
                    val allowed = minOf(n, MAX_CONTROL_RESPONSE_BYTES - baos.size())
                    if (allowed > 0) baos.write(tmp, 0, allowed)
                    rem -= n
                }
                readLine(input) // trailing CRLF
            }
            String(baos.toByteArray(), StandardCharsets.UTF_8)
        } else if (contentLen > 0) {
            val capped = minOf(contentLen, MAX_CONTROL_RESPONSE_BYTES)
            val buf = ByteArray(capped)
            var read = 0
            while (read < capped) {
                val n = input.read(buf, read, capped - read)
                if (n <= 0) break
                read += n
            }
            String(buf, 0, read, StandardCharsets.UTF_8)
        } else if (contentLen == 0) {
            ""
        } else {
            val baos = ByteArrayOutputStream()
            val tmp = ByteArray(4096)
            while (baos.size() < MAX_CONTROL_RESPONSE_BYTES) {
                val maxRead = minOf(tmp.size, MAX_CONTROL_RESPONSE_BYTES - baos.size())
                val n = input.read(tmp, 0, maxRead)
                if (n <= 0) break
                baos.write(tmp, 0, n)
            }
            String(baos.toByteArray(), StandardCharsets.UTF_8)
        }
        return code to body
    }

    private fun readLine(input: BufferedInputStream): String? {
        val baos = ByteArrayOutputStream(128)
        var b = input.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) baos.write(b)
            b = input.read()
        }
        return baos.toString(StandardCharsets.UTF_8.name())
    }

    private fun buildCandidateDirs(roots: List<File>, platformId: String): List<File> {
        val dirs = LinkedHashSet<File>()
        // 默认固定保存至独立快传收件子目录（避免污染游戏 ROM 扫描根目录），同时保留平台/根目录作可选
        dirs += defaultInboxDir()
        val pLower = platformId.lowercase()
        if (pLower.isNotBlank() && pLower != "android") {
            for (r in roots) {
                val exact = File(r, platformId)
                if (exact.isDirectory) dirs += exact
                r.listFiles { f -> f.isDirectory }?.firstOrNull { it.name.equals(platformId, ignoreCase = true) }?.let {
                    dirs += it
                }
            }
        }
        roots.forEach { if (it.isDirectory) dirs += it }
        return dirs.toList()
    }

    private fun sanitizeFileName(raw: String): String? {
        val t = raw.replace(CONTROL_CHARS, "").trim()
        if (t.isEmpty() || t.length > 255 || t == "." || t == "..") return null
        if ('/' in t || '\\' in t || '\u0000' in t) return null
        return t
    }

    private fun sanitizeLocalSendFileName(raw: String): String? {
        // LocalSend sometimes sends relative path segments; extract only final leaf safely
        val leaf = raw.replace('\\', '/').substringAfterLast('/').trim()
        return sanitizeFileName(leaf)
    }

    private fun guessMime(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "zip" -> "application/zip"
        "txt", "cfg", "ini", "json" -> "text/plain"
        else -> "application/octet-stream"
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

    private fun acquireMulticastLock() {
        runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("pocketdeck_quick_share")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
    }
}
