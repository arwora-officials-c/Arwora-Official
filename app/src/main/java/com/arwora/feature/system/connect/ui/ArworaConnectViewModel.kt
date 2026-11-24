package com.arwora.feature.system.connect.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arwora.feature.system.connect.data.ArworaConnectEngine
import com.arwora.feature.system.connect.data.ArworaConnectManager
import com.arwora.feature.system.connect.data.ConnectNetworkUtils
import com.arwora.feature.system.connect.model.FileTransferRecord
import com.arwora.feature.system.connect.model.LocalIdentityState
import com.arwora.feature.system.connect.model.PairingQrPayload
import com.arwora.feature.system.connect.model.PeerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArworaConnectViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: ArworaConnectEngine = ArworaConnectManager.getEngine(application)

    val localIdentity: StateFlow<LocalIdentityState> = engine.localIdentity
    val peers: StateFlow<List<PeerUiState>> = engine.peers
    val transfers: StateFlow<List<FileTransferRecord>> = engine.transfers
    val statusMessage: StateFlow<String?> = engine.statusMessage
    val isServiceRunning: StateFlow<Boolean> = ArworaConnectManager.isServiceRunning
    val autoReconnectEnabled: StateFlow<Boolean> = ArworaConnectManager.autoReconnectEnabled

    private val _isPairingBusy = MutableStateFlow(false)
    val isPairingBusy: StateFlow<Boolean> = _isPairingBusy.asStateFlow()

    private val _isScanningLan = MutableStateFlow(false)
    val isScanningLan: StateFlow<Boolean> = _isScanningLan.asStateFlow()

    private val _discoveredLanHosts = MutableStateFlow<List<String>>(emptyList())
    val discoveredLanHosts: StateFlow<List<String>> = _discoveredLanHosts.asStateFlow()

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    init {
        engine.refreshLocalNetworkInfo()
    }

    fun startServerAndMonitor() {
        viewModelScope.launch {
            engine.startServer()
            ArworaConnectManager.startHubService(getApplication())
        }
    }

    fun stopServerAndMonitor() {
        viewModelScope.launch {
            ArworaConnectManager.stopHubService(getApplication())
            engine.stopServer()
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        ArworaConnectManager.setAutoReconnectEnabled(getApplication(), enabled)
    }

    fun buildQrPayload(): PairingQrPayload = engine.buildQrPayload()

    fun regeneratePairingCode() {
        val code = engine.regeneratePairingCode()
        _actionFeedback.value = "New pairing code: $code"
    }

    fun renameLocalDevice(newName: String) {
        viewModelScope.launch {
            val updated = engine.updateDeviceName(newName)
            _actionFeedback.value = "Device renamed to \"$updated\""
        }
    }

    fun togglePeerSelection(deviceId: String) {
        engine.togglePeerSelection(deviceId)
    }

    fun disconnectPeer(deviceId: String) {
        engine.disconnectPeer(deviceId, userInitiated = true)
        _actionFeedback.value = "Disconnected peer"
    }

    fun reconnectPeer(deviceId: String) {
        viewModelScope.launch {
            val ok = engine.reconnectPeer(deviceId)
            _actionFeedback.value = if (ok) "Reconnected to peer" else "Peer currently unreachable"
        }
    }

    fun forgetPeer(deviceId: String) {
        engine.forgetTrustedPeer(deviceId)
        _actionFeedback.value = "Removed trusted peer certificate"
    }

    fun pairWithQrRaw(rawQr: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val payload = PairingQrPayload.parse(rawQr)
        if (payload == null) {
            val msg = "Invalid Arwora Connect QR payload"
            _actionFeedback.value = msg
            onResult(false, msg)
            return
        }
        viewModelScope.launch {
            _isPairingBusy.value = true
            val result = engine.pairWithQrPayload(payload)
            _isPairingBusy.value = false
            result.onSuccess { peer ->
                val msg = "Paired & trusted ${peer.deviceName}"
                _actionFeedback.value = msg
                onResult(true, msg)
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "QR pairing failed"
                _actionFeedback.value = msg
                onResult(false, msg)
            }
        }
    }

    fun pairWithCode(hostIp: String, port: Int, code: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isPairingBusy.value = true
            val result = engine.pairWithCode(hostIp, port, code)
            _isPairingBusy.value = false
            result.onSuccess { peer ->
                val msg = "Paired & trusted ${peer.deviceName}"
                _actionFeedback.value = msg
                onResult(true, msg)
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Pairing failed"
                _actionFeedback.value = msg
                onResult(false, msg)
            }
        }
    }

    fun scanLanForPeers() {
        viewModelScope.launch {
            _isScanningLan.value = true
            engine.refreshLocalNetworkInfo()
            val localIp = engine.localIdentity.value.localIp
            val found = ConnectNetworkUtils.discoverReachableSubnetPeers(
                localIp = localIp,
                port = engine.localIdentity.value.listeningPort
            )
            _discoveredLanHosts.value = found
            _isScanningLan.value = false
            _actionFeedback.value = if (found.isEmpty()) {
                "No open Arwora Connect listeners found on $localIp subnet"
            } else {
                "Found ${found.size} peer(s) on local Wi-Fi"
            }
        }
    }

    fun sendFiles(uris: List<Uri>, targetPeerIds: List<String>? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val sent = engine.broadcastFilesToSelectedPeers(uris, targetPeerIds)
            _actionFeedback.value = if (sent > 0) {
                "Completed $sent file transfer(s)"
            } else {
                "Ensure at least one trusted peer is connected"
            }
        }
    }

    fun probeReconnectNow() {
        viewModelScope.launch {
            val count = engine.probeAndAutoReconnectDisconnectedPeers()
            _actionFeedback.value = if (count > 0) {
                "Reconnected $count trusted peer(s)"
            } else {
                "No disconnected trusted peers reachable"
            }
        }
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }
}
