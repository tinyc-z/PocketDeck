package com.pocketdeck.launcher.lan

import android.content.Context
import android.content.res.Configuration
import com.pocketdeck.launcher.data.CoverSource
import com.pocketdeck.launcher.data.GameRepository
import com.pocketdeck.launcher.data.MetadataRepository
import com.pocketdeck.launcher.data.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val shareRunning: Boolean = false,
    val shareIp: String? = null,
    val sharePort: Int = -1,
    val localSendCompat: Boolean = true,
    val peers: List<LanPeer> = emptyList(),
    val incomingRequest: IncomingShareRequest? = null,
    val transfer: ShareTransferState? = null,
    val shareHistory: List<ShareHistoryItem> = emptyList(),
    val lastError: String? = null,
) {
    val anyRunning: Boolean get() = webRunning || shareRunning
    val selectedInterface: LanInterface?
        get() = interfaces.firstOrNull { it.interfaceName == selectedInterfaceName } ?: interfaces.firstOrNull()
}

/**
 * 统一协调 Web 管理端与局域网游戏快传（两者独立启停，默认全关闭，挂载在 AppContainer 全局存活）。
 */
class LanServerManager(
    private val context: Context,
    private val userStore: UserStore,
    private val repo: GameRepository,
    covers: CoverSource,
    metadataRepo: MetadataRepository,
    private val scope: CoroutineScope,
) {
    val webServer = WebManagerServer(context, repo, covers, metadataRepo)
    val quickShare = QuickShareService(context, repo)

    private val opMutex = Mutex()
    private val _state = MutableStateFlow(LanServerState())
    val state: StateFlow<LanServerState> = _state.asStateFlow()

    init {
        refreshInterfaces()
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
                quickShare.updateIdentity(id, name)
                quickShare.setLocalSendCompat(u.localSendCompat)
                val sysDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                webServer.updateTheme(u.theme, u.themeAccent, sysDark)
                _state.value = _state.value.copy(
                    deviceId = id,
                    deviceName = name,
                    localSendCompat = u.localSendCompat,
                )
            }
        }
        scope.launch {
            quickShare.peers.collectLatest { peers ->
                _state.value = _state.value.copy(peers = peers)
            }
        }
        scope.launch {
            quickShare.incomingRequest.collectLatest { req ->
                _state.value = _state.value.copy(incomingRequest = req)
            }
        }
        scope.launch {
            quickShare.transfer.collectLatest { tx ->
                _state.value = _state.value.copy(transfer = tx)
            }
        }
        scope.launch {
            quickShare.history.collectLatest { hist ->
                _state.value = _state.value.copy(shareHistory = hist)
            }
        }
        scope.launch {
            webServer.connectedClients.collectLatest { clients ->
                _state.value = _state.value.copy(webClients = clients)
            }
        }
    }

    fun refreshInterfaces(): List<LanInterface> {
        val list = LanInterfaces.listAvailable(context)
        val curSel = _state.value.selectedInterfaceName
        val nextSel = if (curSel != null && list.any { it.interfaceName == curSel }) {
            curSel
        } else {
            list.firstOrNull()?.interfaceName
        }
        _state.value = _state.value.copy(interfaces = list, selectedInterfaceName = nextSel)
        return list
    }

    fun selectInterface(interfaceName: String) {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock {
                val list = refreshInterfaces()
                val chosen = list.firstOrNull { it.interfaceName == interfaceName } ?: return@withLock
                val wasWeb = webServer.isRunning
                val wasShare = quickShare.isRunning
                val prevIface = _state.value.selectedInterfaceName
                _state.value = _state.value.copy(selectedInterfaceName = chosen.interfaceName, lastError = null)
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
        val lan = explicitIface ?: refreshInterfaces().let { list ->
            list.firstOrNull { it.interfaceName == _state.value.selectedInterfaceName } ?: list.firstOrNull()
        }
        if (lan == null) {
            _state.value = _state.value.copy(lastError = "NO_LAN_INTERFACE")
            return false
        }
        return runCatching {
            val port = webServer.start(lan)
            _state.value = _state.value.copy(
                selectedInterfaceName = lan.interfaceName,
                webRunning = true,
                webUrl = webServer.accessUrl,
                webPassword = webServer.dynamicPassword,
                webPort = port,
                lastError = null,
            )
            true
        }.getOrElse {
            _state.value = _state.value.copy(lastError = it.message ?: "WEB_START_FAILED")
            false
        }
    }

    fun stopWebServer() {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock { stopWebLocked() }
        }
    }

    private fun stopWebLocked() {
        webServer.stop()
        _state.value = _state.value.copy(
            webRunning = false,
            webUrl = null,
            webPassword = null,
            webPort = -1,
        )
    }

    fun refreshWebPassword() {
        if (!webServer.isRunning) return
        val pwd = webServer.refreshPassword()
        _state.value = _state.value.copy(webPassword = pwd)
    }

    fun startQuickShare(explicitIface: LanInterface? = null) {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock { startShareLocked(explicitIface) }
        }
    }

    private fun startShareLocked(explicitIface: LanInterface? = null): Boolean {
        val lan = explicitIface ?: refreshInterfaces().let { list ->
            list.firstOrNull { it.interfaceName == _state.value.selectedInterfaceName } ?: list.firstOrNull()
        }
        if (lan == null) {
            _state.value = _state.value.copy(lastError = "NO_LAN_INTERFACE")
            return false
        }
        val id = _state.value.deviceId.ifBlank { UUID.randomUUID().toString() }
        val name = _state.value.deviceName.ifBlank { DeviceNames.randomCuteName() }
        val lsCompat = _state.value.localSendCompat
        return runCatching {
            val port = quickShare.start(lan, id, name, lsCompat)
            _state.value = _state.value.copy(
                selectedInterfaceName = lan.interfaceName,
                shareRunning = true,
                shareIp = lan.ipAddress,
                sharePort = port,
                lastError = null,
            )
            true
        }.getOrElse {
            _state.value = _state.value.copy(lastError = it.message ?: "SHARE_START_FAILED")
            false
        }
    }

    fun stopQuickShare() {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock { stopShareLocked() }
        }
    }

    private fun stopShareLocked() {
        quickShare.stop()
        _state.value = _state.value.copy(
            shareRunning = false,
            shareIp = null,
            sharePort = -1,
            peers = emptyList(),
            incomingRequest = null,
        )
    }

    fun stopAll() {
        scope.launch(Dispatchers.IO) {
            opMutex.withLock {
                stopWebLocked()
                stopShareLocked()
            }
        }
    }

    fun scanPeers() = quickShare.scanPeers()

    fun sendGame(peer: LanPeer, file: File, gameTitle: String, platformId: String, platformName: String) {
        quickShare.sendGame(peer, file, gameTitle, platformId, platformName)
    }

    fun sendFiles(peer: LanPeer, files: List<File>, title: String, platformId: String, platformName: String) {
        quickShare.sendFiles(peer, files, title, platformId, platformName)
    }

    fun acceptIncoming(sessionId: String, targetDir: File) = quickShare.acceptIncoming(sessionId, targetDir)
    fun rejectIncoming(sessionId: String) = quickShare.rejectIncoming(sessionId)
    fun cancelTransfer() = quickShare.cancelTransfer()
    fun dismissTransferBanner() = quickShare.dismissTransferBanner()
    fun deleteShareHistoryItem(id: String, deleteFiles: Boolean = false) = quickShare.deleteHistoryItem(id, deleteFiles)
    fun clearShareHistory(deleteFiles: Boolean = false) = quickShare.clearHistory(deleteFiles)
    fun kickWebClient(ip: String) = webServer.kickClient(ip)

    fun setLocalSendCompat(enabled: Boolean) {
        quickShare.setLocalSendCompat(enabled)
        _state.value = _state.value.copy(localSendCompat = enabled)
        scope.launch {
            userStore.update { it.copy(localSendCompat = enabled) }
        }
    }
}
