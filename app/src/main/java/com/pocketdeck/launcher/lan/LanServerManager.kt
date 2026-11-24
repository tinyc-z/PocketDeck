package com.pocketdeck.launcher.lan

import android.content.Context
import android.content.res.Configuration
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.FileTask
import com.pocketdeck.launcher.data.GameRepository
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

data class LanServerState(
    val deviceId: String = "",
    val deviceName: String = "",
    val interfaces: List<LanInterface> = emptyList(),
    val selectedInterfaceName: String? = null,
    val webRunning: Boolean = false,
    val webUrl: String? = null,
    val webPassword: String? = null,
    val webPort: Int = -1,
    val webClients: List<WebClientInfo> = emptyList(),
    val webActiveTransfers: List<WebActiveTransfer> = emptyList(),
    val webTransferHistory: List<WebTransferHistoryItem> = emptyList(),
    val fileTasks: List<FileTask> = emptyList(),
    val shareRunning: Boolean = false,
    val shareIp: String? = null,
    val sharePort: Int = -1,
    val localSendCompat: Boolean = true,
    val peers: List<LanPeer> = emptyList(),
    val incomingRequest: IncomingShareRequest? = null,
    val pinChallenge: LocalSendPinChallenge? = null,
    val transfer: ShareTransferState? = null,
    val shareHistory: List<ShareHistoryItem> = emptyList(),
    val lastError: String? = null,
    val lastErrorSeq: Long = 0L,
) {
    val anyRunning: Boolean get() = webRunning || shareRunning
    val webTransferring: Boolean get() = webActiveTransfers.isNotEmpty()
    val webUploadSpeedBps: Long
        get() = webClients.sumOf { it.uploadSpeedBps }
    val webDownloadSpeedBps: Long
        get() = webClients.sumOf { it.downloadSpeedBps }
    val selectedInterface: LanInterface?
        get() = interfaces.firstOrNull { it.interfaceName == selectedInterfaceName } ?: interfaces.firstOrNull()

    /**
     * 汇总所有进行中的任务（快传收发、Web 上传/下载、文件移动/复制/压缩/解压/删除）：
     * - 无活跃任务返回 null
     * - 存在多个任务时取进度最慢的（最小百分比 0f..1f）返回
     */
    val slowestActiveTaskProgress: Float?
        get() {
            val progresses = ArrayList<Float>(4)
            val tx = transfer
            if (tx != null && tx.phase == TransferPhase.Transferring) {
                val p = if (tx.totalBytes > 0L) {
                    (tx.bytesTransferred.toFloat() / tx.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f
                progresses += p
            }
            for (wt in webActiveTransfers) {
                val p = if (wt.totalBytes > 0L) {
                    (wt.bytesTransferred.toFloat() / wt.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f
                progresses += p
            }
            for (ft in fileTasks) {
                val p = if (ft.totalBytes > 0L) {
                    (ft.bytesDone.toFloat() / ft.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f
                progresses += p
            }
            return progresses.minOrNull()
        }
}

/**
 * 统一协调 Web 管理端与局域网游戏快传（两者独立启停，默认全关闭，挂载在 AppContainer 全局存活）。
 */
class LanServerManager(
    private val context: Context,
    private val userStore: UserStore,
    private val repo: GameRepository,
    private val covers: CoverSource,
    private val metadataRepo: MetadataRepository,
    private val scope: CoroutineScope,
) {
    private val webServerLazy = lazy { WebManagerServer(context, repo, covers, metadataRepo) }
    private val quickShareLazy = lazy { QuickShareService(context, repo) }
    val webServer: WebManagerServer get() = webServerLazy.value
    val quickShare: QuickShareService get() = quickShareLazy.value

    private val opMutex = Mutex()
    private val _state = MutableStateFlow(LanServerState())
    val state: StateFlow<LanServerState> = _state.asStateFlow()

    @Volatile
    private var serversWired = false
    private val wireLock = Any()

    init {
        // 冷启动仅挂接轻量文件任务流（供顶栏显示压缩/解压/复制等本地文件任务进度）；
        // WebManagerServer、QuickShareService 与网卡枚举延后至用户首次打开局域网面板或启停服务时初始化。
        scope.launch {
            repo.fileTasks.collectLatest { tasks ->
                _state.update { it.copy(fileTasks = tasks) }
            }
        }
    }

    private fun ensureServersWired() {
        if (serversWired) return
        synchronized(wireLock) {
            if (serversWired) return
            serversWired = true
            val ws = webServer
            val qs = quickShare
            scope.launch {
                userStore.data.collectLatest { u ->
                    var id = u.deviceId
                    var name = u.deviceName
                    if (id.isBlank() || name.isBlank()) {
                        val newId = id.ifBlank { UUID.randomUUID().toString() }
                        val newName = name.ifBlank { DeviceNames.randomCuteName() }
                        userStore.update { cur ->
                            cur.copy(
                                deviceId = cur.deviceId.ifBlank { newId },
                                deviceName = cur.deviceName.ifBlank { newName },
                            )
                        }
                        id = newId
                        name = newName
                    }
                    qs.updateIdentity(id, name)
                    qs.setLocalSendCompat(u.localSendCompat)
                    val sysDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    ws.updateTheme(u.theme, u.themeAccent, sysDark)
                    _state.update {
                        it.copy(
                            deviceId = id,
                            deviceName = name,
                            localSendCompat = u.localSendCompat,
                        )
                    }
                }
            }
            scope.launch {
                qs.peers.collectLatest { peers ->
                    _state.update { it.copy(peers = peers) }
                }
            }
            scope.launch {
                qs.incomingRequest.collectLatest { req ->
                    _state.update { it.copy(incomingRequest = req) }
                }
            }
            scope.launch {
                qs.transfer.collectLatest { tx ->
                    _state.update { it.copy(transfer = tx) }
                }
            }
            scope.launch {
                qs.pinChallenge.collectLatest { pin ->
                    _state.update { it.copy(pinChallenge = pin) }
                }
            }
            scope.launch {
                qs.history.collectLatest { hist ->
                    _state.update { it.copy(shareHistory = hist) }
                }
            }
            scope.launch {
                ws.connectedClients.collectLatest { clients ->
                    _state.update { it.copy(webClients = clients) }
                }
            }
            scope.launch {
                ws.activeTransfers.collectLatest { active ->
                    _state.update { it.copy(webActiveTransfers = active) }
                    while (isActive && active.isNotEmpty()) {
                        delay(1000L)
                        ws.tickActiveTransfers()
                    }
                }
            }
            scope.launch {
                ws.transferHistory.collectLatest { hist ->
                    _state.update { it.copy(webTransferHistory = hist) }
                }
            }
        }
    }

    fun cancelFileTask(id: String): Boolean = repo.cancelFileTask(id)

    fun cancelWebTransfer(id: String): Boolean =
        if (webServerLazy.isInitialized()) webServer.cancelActiveTransfer(id) else false

    fun refreshInterfaces(): List<LanInterface> {
        ensureServersWired()
        val list = LanInterfaces.listAvailable(context)
        val curSel = _state.value.selectedInterfaceName
        val nextSel = if (curSel != null && list.any { it.interfaceName == curSel }) {
            curSel
        } else {
            list.firstOrNull()?.interfaceName
        }
        _state.update { it.copy(interfaces = list, selectedInterfaceName = nextSel) }
        return list
    }

    fun refreshInterfacesAsync() {
        scope.launch(Dispatchers.IO) { refreshInterfaces() }
    }

    fun selectInterface(interfaceName: String) {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock {
                val prevIface = _state.value.selectedInterfaceName
                val list = refreshInterfaces()
                val chosen = list.firstOrNull { it.interfaceName == interfaceName } ?: return@withLock
                val wasWeb = webServerLazy.isInitialized() && webServer.isRunning
                val wasShare = quickShareLazy.isInitialized() && quickShare.isRunning
                _state.update { it.copy(selectedInterfaceName = chosen.interfaceName, lastError = null) }
                if (prevIface != chosen.interfaceName) {
                    if (wasWeb) startWebLocked(chosen)
                    if (wasShare) startShareLocked(chosen)
                }
            }
        }
    }

    fun setDeviceName(newName: String) {
        val clean = newName.trim().take(24)
        if (clean.isEmpty()) return
        scope.launch {
            userStore.update { it.copy(deviceName = clean) }
        }
    }

    fun randomizeDeviceName() {
        val current = _state.value.deviceName
        var next = DeviceNames.randomCuteName()
        if (next == current) next = DeviceNames.randomCuteName()
        setDeviceName(next)
    }

    fun startWebServer(explicitIface: LanInterface? = null) {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock { startWebLocked(explicitIface) }
        }
    }

    private fun startWebLocked(explicitIface: LanInterface? = null): Boolean {
        ensureServersWired()
        val lan = explicitIface ?: refreshInterfaces().let { list ->
            list.firstOrNull { it.interfaceName == _state.value.selectedInterfaceName } ?: list.firstOrNull()
        }
        if (lan == null) {
            _state.update { it.copy(lastError = "NO_LAN_INTERFACE", lastErrorSeq = it.lastErrorSeq + 1) }
            return false
        }
        return runCatching {
            val port = webServer.start(lan)
            _state.update { it.copy(
                selectedInterfaceName = lan.interfaceName,
                webRunning = true,
                webUrl = webServer.accessUrl,
                webPassword = webServer.dynamicPassword,
                webPort = port,
                lastError = null,
            ) }
            syncForegroundService()
            true
        }.getOrElse { err ->
            _state.update {
                it.copy(lastError = err.message ?: "WEB_START_FAILED", lastErrorSeq = it.lastErrorSeq + 1)
            }
            false
        }
    }

    fun stopWebServer() {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock { stopWebLocked() }
        }
    }

    private fun stopWebLocked() {
        if (webServerLazy.isInitialized()) {
            webServer.stop()
        }
        _state.update { it.copy(
            webRunning = false,
            webUrl = null,
            webPassword = null,
            webPort = -1,
        ) }
        syncForegroundService()
    }

    fun refreshWebPassword() {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock {
                if (!webServerLazy.isInitialized() || !webServer.isRunning) return@withLock
                val pwd = webServer.refreshPassword()
                _state.update { it.copy(webPassword = pwd) }
            }
        }
    }

    fun startQuickShare(explicitIface: LanInterface? = null) {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock { startShareLocked(explicitIface) }
        }
    }

    private fun startShareLocked(explicitIface: LanInterface? = null): Boolean {
        ensureServersWired()
        val lan = explicitIface ?: refreshInterfaces().let { list ->
            list.firstOrNull { it.interfaceName == _state.value.selectedInterfaceName } ?: list.firstOrNull()
        }
        if (lan == null) {
            _state.update { it.copy(lastError = "NO_LAN_INTERFACE", lastErrorSeq = it.lastErrorSeq + 1) }
            return false
        }
        val id = _state.value.deviceId.ifBlank { UUID.randomUUID().toString() }
        val name = _state.value.deviceName.ifBlank { DeviceNames.randomCuteName() }
        val lsCompat = _state.value.localSendCompat
        return runCatching {
            val port = quickShare.start(lan, id, name, lsCompat)
            _state.update { it.copy(
                selectedInterfaceName = lan.interfaceName,
                shareRunning = true,
                shareIp = lan.ipAddress,
                sharePort = port,
                lastError = null,
            ) }
            syncForegroundService()
            true
        }.getOrElse { err ->
            _state.update {
                it.copy(lastError = err.message ?: "SHARE_START_FAILED", lastErrorSeq = it.lastErrorSeq + 1)
            }
            false
        }
    }

    fun stopQuickShare() {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock { stopShareLocked() }
        }
    }

    private fun stopShareLocked() {
        if (quickShareLazy.isInitialized()) {
            quickShare.stop()
        }
        _state.update { it.copy(
            shareRunning = false,
            shareIp = null,
            sharePort = -1,
            peers = emptyList(),
            incomingRequest = null,
            pinChallenge = null,
        ) }
        syncForegroundService()
    }

    private fun syncForegroundService() {
        LanForegroundService.sync(context, _state.value)
    }

    fun stopAll() {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock {
                stopWebLocked()
                stopShareLocked()
            }
        }
    }

    fun scanPeers() {
        ensureServersWired()
        quickShare.scanPeers()
    }

    fun sendGame(peer: LanPeer, file: File, gameTitle: String, platformId: String, platformName: String) {
        ensureServersWired()
        quickShare.sendGame(peer, file, gameTitle, platformId, platformName)
    }

    fun sendFiles(peer: LanPeer, files: List<File>, title: String, platformId: String, platformName: String) {
        ensureServersWired()
        quickShare.sendFiles(peer, files, title, platformId, platformName)
    }

    fun acceptIncoming(sessionId: String, targetDir: File) {
        ensureServersWired()
        quickShare.acceptIncoming(sessionId, targetDir)
    }
    fun rejectIncoming(sessionId: String) {
        if (quickShareLazy.isInitialized()) quickShare.rejectIncoming(sessionId)
    }
    fun cancelTransfer() {
        if (quickShareLazy.isInitialized()) quickShare.cancelTransfer()
    }
    fun submitQuickSharePin(pin: String) {
        if (quickShareLazy.isInitialized()) quickShare.submitSendPin(pin)
    }
    fun cancelQuickSharePin() {
        if (quickShareLazy.isInitialized()) quickShare.cancelSendPin()
    }
    fun dismissTransferBanner() {
        if (quickShareLazy.isInitialized()) quickShare.dismissTransferBanner()
    }
    fun deleteShareHistoryItem(id: String, deleteFiles: Boolean = false) {
        if (quickShareLazy.isInitialized()) quickShare.deleteHistoryItem(id, deleteFiles)
    }
    fun clearShareHistory(deleteFiles: Boolean = false) {
        if (quickShareLazy.isInitialized()) quickShare.clearHistory(deleteFiles)
    }
    fun deleteWebTransferHistoryItem(id: String) {
        if (webServerLazy.isInitialized()) webServer.deleteTransferHistoryItem(id)
    }
    fun clearWebTransferHistory() {
        if (webServerLazy.isInitialized()) webServer.clearTransferHistory()
    }
    fun kickWebClient(ip: String) {
        if (!webServerLazy.isInitialized()) return
        scope.launch(Dispatchers.IO) {
            webServer.kickClient(ip)
        }
    }

    fun setLocalSendCompat(enabled: Boolean) {
        if (quickShareLazy.isInitialized()) {
            quickShare.setLocalSendCompat(enabled)
        }
        _state.update { it.copy(localSendCompat = enabled) }
        scope.launch {
            userStore.update { it.copy(localSendCompat = enabled) }
        }
    }
}
