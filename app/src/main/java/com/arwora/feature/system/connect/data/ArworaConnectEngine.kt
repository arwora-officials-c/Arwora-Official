package com.arwora.feature.system.connect.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.arwora.feature.system.connect.model.ConnectChatMessage
import com.arwora.feature.system.connect.model.DiscoveredNearbyPeer
import com.arwora.feature.system.connect.model.FileTransferRecord
import com.arwora.feature.system.connect.model.LocalIdentityState
import com.arwora.feature.system.connect.model.P2pMessageRetentionPolicy
import com.arwora.feature.system.connect.model.PairingQrPayload
import com.arwora.feature.system.connect.model.PeerConnectionState
import com.arwora.feature.system.connect.model.PeerUiState
import com.arwora.feature.system.connect.model.TransferDirection
import com.arwora.feature.system.connect.model.TransferStatus
import com.arwora.feature.system.connect.model.TrustedPeerRecord
import com.arwora.feature.system.connect.service.NearbyDiscoveryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-client TCP peer-to-peer file sharing engine for Arwora Connect:
 * - Supports simultaneous multi-device control sockets + parallel file streaming sockets
 * - QR Code & 6-digit Pairing Code handshake exchanging EC P-256 public keys
 * - Silent cryptographic certificate verification for automatic trusted stream acceptance
 * - Device renaming broadcast sync & automatic background peer reconnection
 */
class ArworaConnectEngine(
    context: Context,
    val securityStore: ConnectSecurityStore = ConnectSecurityStore(context),
    private val preferredPort: Int = ConnectSecurityStore.DEFAULT_PORT,
    private val overrideReceiveDir: File? = null,
    initialRetentionPolicy: P2pMessageRetentionPolicy = P2pMessageRetentionPolicy.OFF
) {
    private val appContext = context.applicationContext
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var heartbeatJob: Job? = null
    private val serverLock = Mutex()

    private class ActiveSession(
        val deviceId: String,
        val socket: Socket,
        val input: DataInputStream,
        val output: DataOutputStream,
        val writeMutex: Mutex = Mutex(),
        var readJob: Job? = null,
        @Volatile var rttMs: Long = 12L
    )

    private val activeSessions = ConcurrentHashMap<String, ActiveSession>()
    private val manuallyDisconnectedIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val selectedPeerIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val reconnectingIds = Collections.synchronizedSet(mutableSetOf<String>())

    val chatManager = ArworaConnectChatManager(
        context = appContext,
        initialRetentionPolicy = initialRetentionPolicy
    )
    val nearbyDiscovery = NearbyDiscoveryService(appContext, securityStore)

    private val _localIdentity = MutableStateFlow(buildIdentityState(isServerRunning = false, port = preferredPort))
    val localIdentity: StateFlow<LocalIdentityState> = _localIdentity.asStateFlow()

    private val _peers = MutableStateFlow<List<PeerUiState>>(emptyList())
    val peers: StateFlow<List<PeerUiState>> = _peers.asStateFlow()

    private val _transfers = MutableStateFlow<List<FileTransferRecord>>(emptyList())
    val transfers: StateFlow<List<FileTransferRecord>> = _transfers.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val nearbyPeers: StateFlow<List<DiscoveredNearbyPeer>> = nearbyDiscovery.discoveredPeers
    val isNearbyScanning: StateFlow<Boolean> = nearbyDiscovery.isScanning
    val chatMessagesByPeer: StateFlow<Map<String, List<ConnectChatMessage>>> = chatManager.messagesByPeer
    val unreadChatCounts: StateFlow<Map<String, Int>> = chatManager.unreadCountsByPeer

    var onIncomingFileCompleted: ((FileTransferRecord) -> Unit)? = null
    var onIncomingChatMessage: ((ConnectChatMessage) -> Unit)? = null
    var onChatHubFrame: ((peerDeviceId: String, frame: JSONObject) -> Unit)? = null

    init {
        selectedPeerIds.addAll(securityStore.loadTrustedPeers().map { it.deviceId })
        refreshPeerStates()
        refreshLocalNetworkInfo()
    }

    private fun buildIdentityState(isServerRunning: Boolean, port: Int): LocalIdentityState {
        return LocalIdentityState(
            deviceId = securityStore.getOrCreateDeviceId(),
            deviceName = securityStore.getDeviceName(),
            localIp = ConnectNetworkUtils.resolveLocalIpv4Address(appContext),
            listeningPort = port,
            pairingCode = securityStore.getOrGeneratePairingCode(),
            publicKeyBase64 = securityStore.getPublicKeyBase64(),
            certFingerprint = securityStore.getCertFingerprint(),
            isServerRunning = isServerRunning
        )
    }

    fun refreshLocalNetworkInfo() {
        val currentPort = serverSocket?.localPort ?: _localIdentity.value.listeningPort
        val running = serverSocket?.let { !it.isClosed } == true
        _localIdentity.value = buildIdentityState(isServerRunning = running, port = currentPort)
        refreshPeerStates()
    }

    fun buildQrPayload(): PairingQrPayload {
        refreshLocalNetworkInfo()
        val id = _localIdentity.value
        return PairingQrPayload(
            deviceId = id.deviceId,
            deviceName = id.deviceName,
            hostIp = id.localIp,
            port = id.listeningPort,
            pairingCode = id.pairingCode,
            publicKeyBase64 = id.publicKeyBase64,
            certFingerprint = id.certFingerprint
        )
    }

    fun regeneratePairingCode(): String {
        val next = securityStore.getOrGeneratePairingCode(forceRegenerate = true)
        _localIdentity.value = _localIdentity.value.copy(pairingCode = next)
        return next
    }

    suspend fun startServer(): Int = serverLock.withLock {
        val existing = serverSocket
        if (existing != null && !existing.isClosed) {
            refreshLocalNetworkInfo()
            return@withLock existing.localPort
        }

        val bound = withContext(Dispatchers.IO) {
            try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(preferredPort))
                }
            } catch (_: Exception) {
                ServerSocket(0)
            }
        }
        serverSocket = bound
        val boundPort = bound.localPort
        _localIdentity.value = buildIdentityState(isServerRunning = true, port = boundPort)

        acceptJob?.cancel()
        acceptJob = engineScope.launch {
            while (isActive && !bound.isClosed) {
                try {
                    val client = bound.accept()
                    launch { handleIncomingClientSocket(client) }
                } catch (_: Exception) {
                    if (bound.isClosed) break
                }
            }
        }

        heartbeatJob?.cancel()
        heartbeatJob = engineScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                pingActiveSessions()
            }
        }

        nearbyDiscovery.start(port = boundPort, deviceName = _localIdentity.value.deviceName)
        return@withLock boundPort
    }

    suspend fun stopServer() = serverLock.withLock {
        nearbyDiscovery.stop()
        acceptJob?.cancel()
        acceptJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null

        activeSessions.keys.toList().forEach { id ->
            closeSessionInternal(id, sendDisconnect = true, manual = false)
        }
        _localIdentity.value = _localIdentity.value.copy(isServerRunning = false)
        refreshPeerStates()
    }

    fun stopServerImmediate() {
        nearbyDiscovery.stop()
        acceptJob?.cancel()
        acceptJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeSessions.keys.toList().forEach { id ->
            closeSessionInternal(id, sendDisconnect = false, manual = false)
        }
        _localIdentity.value = _localIdentity.value.copy(isServerRunning = false)
        refreshPeerStates()
    }

    fun shutdown() {
        nearbyDiscovery.shutdown()
        chatManager.clearAll()
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeSessions.keys.toList().forEach { closeSessionInternal(it, sendDisconnect = false, manual = false) }
        engineScope.cancel()
    }

    suspend fun updateDeviceName(newName: String): String {
        val updated = securityStore.setDeviceName(newName)
        _localIdentity.value = _localIdentity.value.copy(deviceName = updated)
        val currentPort = serverSocket?.localPort ?: _localIdentity.value.listeningPort
        if (_localIdentity.value.isServerRunning) {
            nearbyDiscovery.updateLocalDeviceName(updated, currentPort)
        }

        // Sync device rename immediately to all active connected peers
        val msg = JSONObject().apply {
            put("type", TYPE_RENAME_NOTIFY)
            put("deviceId", _localIdentity.value.deviceId)
            put("deviceName", updated)
        }
        activeSessions.values.forEach { session ->
            engineScope.launch {
                runCatching {
                    session.writeMutex.withLock {
                        writeFrame(session.output, msg)
                    }
                }
            }
        }
        return updated
    }

    fun togglePeerSelection(deviceId: String) {
        if (selectedPeerIds.contains(deviceId)) {
            selectedPeerIds.remove(deviceId)
        } else {
            selectedPeerIds.add(deviceId)
        }
        refreshPeerStates()
    }

    suspend fun pairWithQrPayload(payload: PairingQrPayload): Result<TrustedPeerRecord> {
        if (payload.deviceId == securityStore.getOrCreateDeviceId()) {
            return Result.failure(IllegalArgumentException("Cannot pair a device with itself"))
        }
        return connectAndPairInternal(
            hostIp = payload.hostIp,
            port = payload.port,
            pairingCode = payload.pairingCode,
            method = "QR"
        )
    }

    suspend fun pairWithCode(hostIp: String, port: Int, pairingCode: String): Result<TrustedPeerRecord> {
        val cleanIp = hostIp.trim()
        val cleanCode = pairingCode.trim()
        if (cleanIp.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter peer IP address"))
        }
        if (cleanCode.length != 6 || cleanCode.any { !it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Pairing code must be 6 digits"))
        }
        return connectAndPairInternal(
            hostIp = cleanIp,
            port = if (port in 1..65535) port else ConnectSecurityStore.DEFAULT_PORT,
            pairingCode = cleanCode,
            method = "CODE"
        )
    }

    /**
     * Zero-config one-tap pairing for a peer discovered via NSD / mDNS without requiring a code or QR.
     */
    suspend fun pairWithNearbyPeer(peer: DiscoveredNearbyPeer): Result<TrustedPeerRecord> = withContext(Dispatchers.IO) {
        if (peer.deviceId == securityStore.getOrCreateDeviceId()) {
            return@withContext Result.failure(IllegalArgumentException("Cannot pair a device with itself"))
        }
        var socket: Socket? = null
        try {
            if (serverSocket == null || serverSocket?.isClosed == true) {
                startServer()
            }
            val startNs = System.nanoTime()
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = CONNECT_TIMEOUT_MS
            socket.connect(InetSocketAddress(peer.hostIp, peer.port), CONNECT_TIMEOUT_MS)

            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val myId = securityStore.getOrCreateDeviceId()
            val myName = securityStore.getDeviceName()
            val myPort = serverSocket?.localPort ?: _localIdentity.value.listeningPort
            val myPub = securityStore.getPublicKeyBase64()
            val myFp = securityStore.getCertFingerprint()
            val ts = System.currentTimeMillis()
            val sig = securityStore.signPayload("$myId|NEARBY|$ts")

            writeFrame(
                output,
                JSONObject().apply {
                    put("type", TYPE_NEARBY_PAIR_REQUEST)
                    put("method", "NSD_NEARBY")
                    put("deviceId", myId)
                    put("deviceName", myName)
                    put("listeningPort", myPort)
                    put("publicKey", myPub)
                    put("fingerprint", myFp)
                    put("timestamp", ts)
                    put("sig", sig)
                }
            )

            val resp = readFrame(input)
            if (resp.optString("type") != TYPE_NEARBY_PAIR_ACCEPT) {
                val reason = resp.optString("reason", "Nearby pairing declined")
                socket.close()
                _statusMessage.value = reason
                return@withContext Result.failure(IllegalStateException(reason))
            }

            val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            val peerId = resp.getString("deviceId")
            val peerName = resp.optString("deviceName", peer.deviceName).ifBlank { peer.deviceName }
            val peerPort = resp.optInt("listeningPort", peer.port)
            val peerPub = resp.getString("publicKey")
            val peerFp = resp.getString("fingerprint")
            val peerTs = resp.getLong("timestamp")
            val peerSig = resp.getString("sig")

            if (!verifyPublicKeyMatchesFingerprint(peerPub, peerFp) ||
                !securityStore.verifyPeerSignature(peerPub, "$peerId|NEARBY|$peerTs", peerSig)
            ) {
                socket.close()
                return@withContext Result.failure(SecurityException("Nearby peer certificate signature invalid"))
            }

            val now = System.currentTimeMillis()
            val record = TrustedPeerRecord(
                deviceId = peerId,
                deviceName = peerName,
                lastKnownIp = peer.hostIp,
                port = peerPort,
                publicKeyBase64 = peerPub,
                certFingerprint = peerFp,
                pairedAtMillis = now,
                lastSeenMillis = now,
                trustedAutoAccept = true
            )
            securityStore.upsertTrustedPeer(record)
            manuallyDisconnectedIds.remove(peerId)
            selectedPeerIds.add(peerId)
            nearbyDiscovery.removeByDeviceId(peerId)

            socket.soTimeout = SESSION_READ_TIMEOUT_MS
            attachActiveSession(peerId, socket, input, output, initialRttMs = rttMs)
            _statusMessage.value = "Connected to nearby peer $peerName"
            Result.success(record)
        } catch (e: Exception) {
            runCatching { socket?.close() }
            val msg = e.localizedMessage ?: "Unable to connect to ${peer.deviceName}"
            _statusMessage.value = msg
            Result.failure(e)
        }
    }

    private suspend fun connectAndPairInternal(
        hostIp: String,
        port: Int,
        pairingCode: String,
        method: String
    ): Result<TrustedPeerRecord> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            if (serverSocket == null || serverSocket?.isClosed == true) {
                startServer()
            }
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = CONNECT_TIMEOUT_MS
            socket.connect(InetSocketAddress(hostIp, port), CONNECT_TIMEOUT_MS)

            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val myId = securityStore.getOrCreateDeviceId()
            val myName = securityStore.getDeviceName()
            val myPort = serverSocket?.localPort ?: _localIdentity.value.listeningPort
            val myPub = securityStore.getPublicKeyBase64()
            val myFp = securityStore.getCertFingerprint()
            val ts = System.currentTimeMillis()
            val sig = securityStore.signPayload("$myId|$pairingCode|$ts")

            val req = JSONObject().apply {
                put("type", TYPE_PAIR_REQUEST)
                put("method", method)
                put("deviceId", myId)
                put("deviceName", myName)
                put("listeningPort", myPort)
                put("pairingCode", pairingCode)
                put("publicKey", myPub)
                put("fingerprint", myFp)
                put("timestamp", ts)
                put("sig", sig)
            }
            writeFrame(output, req)

            val resp = readFrame(input)
            val respType = resp.optString("type")
            if (respType != TYPE_PAIR_ACCEPT) {
                val reason = resp.optString("reason", "Pairing rejected by peer")
                socket.close()
                _statusMessage.value = reason
                return@withContext Result.failure(IllegalStateException(reason))
            }

            val peerId = resp.getString("deviceId")
            val peerName = resp.optString("deviceName", "Arwora Peer")
            val peerPort = resp.optInt("listeningPort", port)
            val peerPub = resp.getString("publicKey")
            val peerFp = resp.getString("fingerprint")
            val peerTs = resp.getLong("timestamp")
            val peerSig = resp.getString("sig")

            if (!verifyPublicKeyMatchesFingerprint(peerPub, peerFp) ||
                !securityStore.verifyPeerSignature(peerPub, "$peerId|$pairingCode|$peerTs", peerSig)
            ) {
                socket.close()
                return@withContext Result.failure(SecurityException("Peer certificate signature verification failed"))
            }

            val now = System.currentTimeMillis()
            val record = TrustedPeerRecord(
                deviceId = peerId,
                deviceName = peerName,
                lastKnownIp = hostIp,
                port = peerPort,
                publicKeyBase64 = peerPub,
                certFingerprint = peerFp,
                pairedAtMillis = now,
                lastSeenMillis = now,
                trustedAutoAccept = true
            )
            securityStore.upsertTrustedPeer(record)
            manuallyDisconnectedIds.remove(peerId)
            selectedPeerIds.add(peerId)

            socket.soTimeout = SESSION_READ_TIMEOUT_MS
            attachActiveSession(peerId, socket, input, output, initialRttMs = 10L)
            _statusMessage.value = "Paired & trusted $peerName"
            Result.success(record)
        } catch (e: Exception) {
            runCatching { socket?.close() }
            val msg = e.localizedMessage ?: "Unable to connect to $hostIp:$port"
            _statusMessage.value = msg
            Result.failure(e)
        }
    }

    suspend fun reconnectPeer(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        val peer = securityStore.loadTrustedPeers().firstOrNull { it.deviceId == deviceId } ?: return@withContext false
        manuallyDisconnectedIds.remove(deviceId)
        reconnectTrustedPeer(peer)
    }

    suspend fun probeAndAutoReconnectDisconnectedPeers(): Int = withContext(Dispatchers.IO) {
        val trustedPeers = securityStore.loadTrustedPeers()
        if (trustedPeers.isEmpty()) return@withContext 0

        val candidates = trustedPeers.filter { peer ->
            !activeSessions.containsKey(peer.deviceId) &&
                !manuallyDisconnectedIds.contains(peer.deviceId) &&
                peer.lastKnownIp.isNotBlank()
        }
        if (candidates.isEmpty()) return@withContext 0

        val reconnectedCount = coroutineScope {
            candidates.map { peer ->
                async {
                    if (reconnectTrustedPeer(peer)) 1 else 0
                }
            }.awaitAll().sum()
        }
        refreshPeerStates()
        reconnectedCount
    }

    private suspend fun reconnectTrustedPeer(peer: TrustedPeerRecord): Boolean = withContext(Dispatchers.IO) {
        if (activeSessions.containsKey(peer.deviceId)) return@withContext true
        if (!reconnectingIds.add(peer.deviceId)) return@withContext false
        refreshPeerStates()

        var socket: Socket? = null
        return@withContext try {
            if (serverSocket == null || serverSocket?.isClosed == true) {
                startServer()
            }
            val startNs = System.nanoTime()
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = RECONNECT_PROBE_TIMEOUT_MS
            socket.connect(InetSocketAddress(peer.lastKnownIp, peer.port), RECONNECT_PROBE_TIMEOUT_MS)

            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val myId = securityStore.getOrCreateDeviceId()
            val myName = securityStore.getDeviceName()
            val myPort = serverSocket?.localPort ?: _localIdentity.value.listeningPort
            val myPub = securityStore.getPublicKeyBase64()
            val myFp = securityStore.getCertFingerprint()
            val ts = System.currentTimeMillis()
            val sig = securityStore.signPayload("$myId|$ts")

            writeFrame(
                output,
                JSONObject().apply {
                    put("type", TYPE_TRUSTED_CONNECT)
                    put("deviceId", myId)
                    put("deviceName", myName)
                    put("listeningPort", myPort)
                    put("publicKey", myPub)
                    put("fingerprint", myFp)
                    put("timestamp", ts)
                    put("sig", sig)
                }
            )

            val resp = readFrame(input)
            if (resp.optString("type") != TYPE_TRUSTED_ACCEPT) {
                socket.close()
                return@withContext false
            }

            val rttMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            val peerName = resp.optString("deviceName", peer.deviceName).ifBlank { peer.deviceName }
            val peerPub = resp.getString("publicKey")
            val peerFp = resp.getString("fingerprint")
            val peerTs = resp.getLong("timestamp")
            val peerSig = resp.getString("sig")

            val trusted = securityStore.isTrustedPeerCertificateValid(peer.deviceId, peerPub, peerFp)
            if (trusted == null || !securityStore.verifyPeerSignature(peerPub, "${peer.deviceId}|$peerTs", peerSig)) {
                socket.close()
                return@withContext false
            }

            securityStore.upsertTrustedPeer(
                trusted.copy(
                    deviceName = peerName,
                    lastSeenMillis = System.currentTimeMillis()
                )
            )
            selectedPeerIds.add(peer.deviceId)
            socket.soTimeout = SESSION_READ_TIMEOUT_MS
            attachActiveSession(peer.deviceId, socket, input, output, initialRttMs = rttMs)
            _statusMessage.value = "Auto-reconnected to $peerName"
            true
        } catch (_: Exception) {
            runCatching { socket?.close() }
            false
        } finally {
            reconnectingIds.remove(peer.deviceId)
            refreshPeerStates()
        }
    }

    fun disconnectPeer(deviceId: String, userInitiated: Boolean = true) {
        if (userInitiated) {
            manuallyDisconnectedIds.add(deviceId)
        }
        closeSessionInternal(deviceId, sendDisconnect = true, manual = userInitiated)
        refreshPeerStates()
    }

    fun forgetTrustedPeer(deviceId: String) {
        manuallyDisconnectedIds.add(deviceId)
        selectedPeerIds.remove(deviceId)
        closeSessionInternal(deviceId, sendDisconnect = true, manual = true)
        securityStore.removeTrustedPeer(deviceId)
        chatManager.clearPeerChat(deviceId)
        refreshPeerStates()
    }

    suspend fun sendChatText(peerDeviceId: String, text: String): Result<ConnectChatMessage> =
        withContext(Dispatchers.IO) {
            val clean = text.trim()
            if (clean.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Message cannot be empty"))
            }
            val session = activeSessions[peerDeviceId]
                ?: return@withContext Result.failure(IllegalStateException("Peer is not connected"))
            val identity = _localIdentity.value
            val (message, packet) = chatManager.createOutgoingTextMessage(
                peerDeviceId = peerDeviceId,
                localDeviceId = identity.deviceId,
                localDeviceName = identity.deviceName,
                text = clean
            )
            chatManager.appendLocalMessage(message)
            try {
                session.writeMutex.withLock {
                    writeFrame(session.output, packet)
                }
                Result.success(message)
            } catch (e: Exception) {
                chatManager.removeMessage(peerDeviceId, message.id)
                Result.failure(e)
            }
        }

    suspend fun sendChatImage(
        peerDeviceId: String,
        uri: Uri,
        caption: String = ""
    ): Result<ConnectChatMessage> = withContext(Dispatchers.IO) {
        val base64 = chatManager.compressImageUriToBase64(uri)
            ?: return@withContext Result.failure(IllegalStateException("Unable to compress selected image"))
        sendChatImageBase64(peerDeviceId, base64, caption)
    }

    suspend fun sendChatImageBase64(
        peerDeviceId: String,
        imageBase64: String,
        caption: String = ""
    ): Result<ConnectChatMessage> = withContext(Dispatchers.IO) {
        if (imageBase64.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Image payload is empty"))
        }
        val session = activeSessions[peerDeviceId]
            ?: return@withContext Result.failure(IllegalStateException("Peer is not connected"))
        val identity = _localIdentity.value
        val (message, packet) = chatManager.createOutgoingImageMessage(
            peerDeviceId = peerDeviceId,
            localDeviceId = identity.deviceId,
            localDeviceName = identity.deviceName,
            imageBase64 = imageBase64,
            caption = caption
        )
        chatManager.appendLocalMessage(message)
        try {
            session.writeMutex.withLock {
                writeFrame(session.output, packet)
            }
            Result.success(message)
        } catch (e: Exception) {
            chatManager.removeMessage(peerDeviceId, message.id)
            Result.failure(e)
        }
    }

    suspend fun sendChatVoice(
        peerDeviceId: String,
        audioBase64: String,
        durationMs: Long,
        waveform: List<Float>
    ): Result<ConnectChatMessage> = withContext(Dispatchers.IO) {
        if (audioBase64.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Voice note payload is empty"))
        }
        val session = activeSessions[peerDeviceId]
            ?: return@withContext Result.failure(IllegalStateException("Peer is not connected"))
        val identity = _localIdentity.value
        val (message, packet) = chatManager.createOutgoingVoiceMessage(
            peerDeviceId = peerDeviceId,
            localDeviceId = identity.deviceId,
            localDeviceName = identity.deviceName,
            audioBase64 = audioBase64,
            durationMs = durationMs,
            waveform = waveform
        )
        if (packet.toString().toByteArray(Charsets.UTF_8).size > MAX_CONTROL_FRAME_BYTES) {
            return@withContext Result.failure(IllegalStateException("Voice note exceeds P2P frame limit"))
        }
        chatManager.appendLocalMessage(message)
        try {
            session.writeMutex.withLock {
                writeFrame(session.output, packet)
            }
            Result.success(message)
        } catch (e: Exception) {
            chatManager.removeMessage(peerDeviceId, message.id)
            Result.failure(e)
        }
    }

    suspend fun toggleChatReaction(
        peerDeviceId: String,
        messageId: String,
        emoji: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val session = activeSessions[peerDeviceId]
            ?: return@withContext Result.failure(IllegalStateException("Peer is not connected"))
        val myId = securityStore.getOrCreateDeviceId()
        val (_, packet) = chatManager.toggleReaction(
            peerDeviceId = peerDeviceId,
            messageId = messageId,
            actorDeviceId = myId,
            emoji = emoji
        ) ?: return@withContext Result.failure(IllegalArgumentException("Message not found"))

        try {
            session.writeMutex.withLock {
                writeFrame(session.output, packet)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            // Roll back local toggle if socket failed
            chatManager.toggleReaction(peerDeviceId, messageId, myId, emoji)
            Result.failure(e)
        }
    }

    suspend fun sendChatHubPacket(
        peerDeviceId: String,
        packet: JSONObject
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val session = activeSessions[peerDeviceId]
            ?: return@withContext Result.failure(IllegalStateException("Peer is not connected"))
        val sizeBytes = packet.toString().toByteArray(Charsets.UTF_8).size
        if (sizeBytes > MAX_CONTROL_FRAME_BYTES) {
            return@withContext Result.failure(IllegalStateException("Payload exceeds 2 MB P2P frame limit"))
        }
        try {
            session.writeMutex.withLock {
                writeFrame(session.output, packet)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun broadcastFilesToSelectedPeers(
        uris: List<Uri>,
        targetPeerIds: List<String>? = null
    ): Int = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext 0
        val fileMetas = uris.mapNotNull { ConnectNetworkUtils.readOutboundFileMeta(appContext, it) }
        if (fileMetas.isEmpty()) {
            _statusMessage.value = "Unable to read selected files"
            return@withContext 0
        }

        val currentPeers = _peers.value.filter { it.connectionState == PeerConnectionState.CONNECTED }
        val targets = if (targetPeerIds != null) {
            currentPeers.filter { it.peer.deviceId in targetPeerIds }.map { it.peer }
        } else {
            currentPeers.filter { it.isSelectedForSend }.map { it.peer }
        }

        if (targets.isEmpty()) {
            _statusMessage.value = "No connected devices selected"
            return@withContext 0
        }

        var successCount = 0
        for (meta in fileMetas) {
            val results = coroutineScope {
                targets.map { peer ->
                    async { sendSingleFileToPeer(peer, meta) }
                }.awaitAll()
            }
            successCount += results.count { it }
        }
        _statusMessage.value = "Sent $successCount transfer(s) across ${targets.size} device(s)"
        successCount
    }

    private suspend fun sendSingleFileToPeer(
        peer: TrustedPeerRecord,
        meta: OutboundFileMeta
    ): Boolean = withContext(Dispatchers.IO) {
        val transferId = UUID.randomUUID().toString()
        val initialRecord = FileTransferRecord(
            id = transferId,
            peerDeviceId = peer.deviceId,
            peerDeviceName = peer.deviceName,
            fileName = meta.displayName,
            totalBytes = meta.sizeBytes,
            transferredBytes = 0L,
            direction = TransferDirection.OUTGOING,
            status = TransferStatus.IN_PROGRESS,
            autoAccepted = true
        )
        upsertTransferRecord(initialRecord)

        var socket: Socket? = null
        return@withContext try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.soTimeout = TRANSFER_SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(peer.lastKnownIp, peer.port), CONNECT_TIMEOUT_MS)

            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val myId = securityStore.getOrCreateDeviceId()
            val myName = securityStore.getDeviceName()
            val myPub = securityStore.getPublicKeyBase64()
            val myFp = securityStore.getCertFingerprint()
            val nonce = UUID.randomUUID().toString()
            val sig = securityStore.signPayload("$transferId|$myId|$nonce|${meta.sizeBytes}")

            val offer = JSONObject().apply {
                put("type", TYPE_FILE_TRANSFER_INIT)
                put("transferId", transferId)
                put("deviceId", myId)
                put("deviceName", myName)
                put("publicKey", myPub)
                put("fingerprint", myFp)
                put("nonce", nonce)
                put("sig", sig)
                put("fileName", meta.displayName)
                put("sizeBytes", meta.sizeBytes)
            }
            writeFrame(output, offer)

            val ack = readFrame(input)
            if (ack.optString("type") != TYPE_FILE_ACCEPT) {
                val reason = ack.optString("reason", "Transfer rejected")
                upsertTransferRecord(initialRecord.copy(status = TransferStatus.FAILED, errorMessage = reason))
                return@withContext false
            }

            val autoAccepted = ack.optBoolean("autoAccepted", true)
            var sentBytes = 0L
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            openOutboundInputStream(meta.uri).use { stream ->
                var lastUiUpdateBytes = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    sentBytes += read
                    if (sentBytes - lastUiUpdateBytes >= PROGRESS_NOTIFY_STEP_BYTES || sentBytes == meta.sizeBytes) {
                        lastUiUpdateBytes = sentBytes
                        upsertTransferRecord(
                            initialRecord.copy(
                                transferredBytes = sentBytes,
                                autoAccepted = autoAccepted
                            )
                        )
                    }
                }
            }
            output.flush()

            val doneAck = readFrame(input)
            val ok = doneAck.optString("type") == TYPE_FILE_DONE
            upsertTransferRecord(
                initialRecord.copy(
                    transferredBytes = sentBytes,
                    status = if (ok) TransferStatus.COMPLETED else TransferStatus.FAILED,
                    autoAccepted = autoAccepted,
                    errorMessage = if (ok) null else doneAck.optString("reason", "Incomplete ack")
                )
            )
            ok
        } catch (e: Exception) {
            upsertTransferRecord(
                initialRecord.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = e.localizedMessage ?: "Transfer failed"
                )
            )
            false
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun openOutboundInputStream(uri: Uri): InputStream {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: throw IllegalArgumentException("Invalid file URI"))
            return BufferedInputStream(file.inputStream())
        }
        return appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open URI for transfer: $uri")
    }

    private suspend fun handleIncomingClientSocket(socket: Socket) = withContext(Dispatchers.IO) {
        val remoteIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = CONNECT_TIMEOUT_MS
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val firstFrame = readFrame(input)
            when (firstFrame.optString("type")) {
                TYPE_PAIR_REQUEST -> handleIncomingPairRequest(socket, remoteIp, input, output, firstFrame)
                TYPE_NEARBY_PAIR_REQUEST -> handleIncomingNearbyPairRequest(socket, remoteIp, input, output, firstFrame)
                TYPE_TRUSTED_CONNECT -> handleIncomingTrustedConnect(socket, remoteIp, input, output, firstFrame)
                TYPE_FILE_TRANSFER_INIT -> handleIncomingFileTransfer(socket, input, output, firstFrame)
                else -> socket.close()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Incoming socket closed or failed: ${e.message}")
            runCatching { socket.close() }
        }
    }

    private fun handleIncomingPairRequest(
        socket: Socket,
        remoteIp: String,
        input: DataInputStream,
        output: DataOutputStream,
        req: JSONObject
    ) {
        val expectedCode = securityStore.getOrGeneratePairingCode()
        val suppliedCode = req.optString("pairingCode")
        val peerId = req.optString("deviceId").trim()
        val peerName = req.optString("deviceName", "Arwora Peer").ifBlank { "Arwora Peer" }
        val peerPort = req.optInt("listeningPort", ConnectSecurityStore.DEFAULT_PORT)
        val peerPub = req.optString("publicKey")
        val peerFp = req.optString("fingerprint")
        val peerTs = req.optLong("timestamp", 0L)
        val peerSig = req.optString("sig")

        val valid = peerId.isNotEmpty() &&
            suppliedCode == expectedCode &&
            verifyPublicKeyMatchesFingerprint(peerPub, peerFp) &&
            securityStore.verifyPeerSignature(peerPub, "$peerId|$suppliedCode|$peerTs", peerSig)

        if (!valid) {
            writeFrame(
                output,
                JSONObject().apply {
                    put("type", TYPE_PAIR_REJECT)
                    put("reason", "Invalid pairing code or device certificate")
                }
            )
            socket.close()
            return
        }

        val myId = securityStore.getOrCreateDeviceId()
        val myName = securityStore.getDeviceName()
        val myPort = serverSocket?.localPort ?: _localIdentity.value.listeningPort
        val myPub = securityStore.getPublicKeyBase64()
        val myFp = securityStore.getCertFingerprint()
        val myTs = System.currentTimeMillis()
        val mySig = securityStore.signPayload("$myId|$suppliedCode|$myTs")

        writeFrame(
            output,
            JSONObject().apply {
                put("type", TYPE_PAIR_ACCEPT)
                put("deviceId", myId)
                put("deviceName", myName)
                put("listeningPort", myPort)
                put("publicKey", myPub)
                put("fingerprint", myFp)
                put("timestamp", myTs)
                put("sig", mySig)
            }
        )

        val now = System.currentTimeMillis()
        val record = TrustedPeerRecord(
            deviceId = peerId,
            deviceName = peerName,
            lastKnownIp = remoteIp,
            port = peerPort,
            publicKeyBase64 = peerPub,
            certFingerprint = peerFp,
            pairedAtMillis = now,
            lastSeenMillis = now,
            trustedAutoAccept = true
        )
        securityStore.upsertTrustedPeer(record)
        manuallyDisconnectedIds.remove(peerId)
        selectedPeerIds.add(peerId)

        socket.soTimeout = SESSION_READ_TIMEOUT_MS
        attachActiveSession(peerId, socket, input, output, initialRttMs = 10L)
        _statusMessage.value = "Trusted peer paired: $peerName"
    }

    private fun handleIncomingNearbyPairRequest(
        socket: Socket,
        remoteIp: String,
        input: DataInputStream,
        output: DataOutputStream,
        req: JSONObject
    ) {
        val peerId = req.optString("deviceId").trim()
        val peerName = req.optString("deviceName", "Arwora Peer").ifBlank { "Arwora Peer" }
        val peerPort = req.optInt("listeningPort", ConnectSecurityStore.DEFAULT_PORT)
        val peerPub = req.optString("publicKey")
        val peerFp = req.optString("fingerprint")
        val peerTs = req.optLong("timestamp", 0L)
        val peerSig = req.optString("sig")

        val valid = peerId.isNotEmpty() &&
            peerId != securityStore.getOrCreateDeviceId() &&
            verifyPublicKeyMatchesFingerprint(peerPub, peerFp) &&
            securityStore.verifyPeerSignature(peerPub, "$peerId|NEARBY|$peerTs", peerSig)

        if (!valid) {
            writeFrame(
                output,
                JSONObject().apply {
                    put("type", TYPE_PAIR_REJECT)
                    put("reason", "Invalid nearby peer certificate")
                }
            )
            socket.close()
            return
        }

        val myId = securityStore.getOrCreateDeviceId()
        val myName = securityStore.getDeviceName()
        val myPort = serverSocket?.localPort ?: _localIdentity.value.listeningPort
        val myPub = securityStore.getPublicKeyBase64()
        val myFp = securityStore.getCertFingerprint()
        val myTs = System.currentTimeMillis()
        val mySig = securityStore.signPayload("$myId|NEARBY|$myTs")

        writeFrame(
            output,
            JSONObject().apply {
                put("type", TYPE_NEARBY_PAIR_ACCEPT)
                put("deviceId", myId)
                put("deviceName", myName)
                put("listeningPort", myPort)
                put("publicKey", myPub)
                put("fingerprint", myFp)
                put("timestamp", myTs)
                put("sig", mySig)
            }
        )

        val now = System.currentTimeMillis()
        val record = TrustedPeerRecord(
            deviceId = peerId,
            deviceName = peerName,
            lastKnownIp = remoteIp,
            port = peerPort,
            publicKeyBase64 = peerPub,
            certFingerprint = peerFp,
            pairedAtMillis = now,
            lastSeenMillis = now,
            trustedAutoAccept = true
        )
        securityStore.upsertTrustedPeer(record)
        manuallyDisconnectedIds.remove(peerId)
        selectedPeerIds.add(peerId)
        nearbyDiscovery.removeByDeviceId(peerId)

        socket.soTimeout = SESSION_READ_TIMEOUT_MS
        attachActiveSession(peerId, socket, input, output, initialRttMs = 10L)
        _statusMessage.value = "Nearby peer connected: $peerName"
    }

    private fun handleIncomingTrustedConnect(
        socket: Socket,
        remoteIp: String,
        input: DataInputStream,
        output: DataOutputStream,
        req: JSONObject
    ) {
        val peerId = req.optString("deviceId").trim()
        val peerName = req.optString("deviceName", "Arwora Peer").ifBlank { "Arwora Peer" }
        val peerPort = req.optInt("listeningPort", ConnectSecurityStore.DEFAULT_PORT)
        val peerPub = req.optString("publicKey")
        val peerFp = req.optString("fingerprint")
        val peerTs = req.optLong("timestamp", 0L)
        val peerSig = req.optString("sig")

        val trusted = securityStore.isTrustedPeerCertificateValid(peerId, peerPub, peerFp)
        val sigOk = trusted != null &&
            securityStore.verifyPeerSignature(peerPub, "$peerId|$peerTs", peerSig)

        if (!sigOk) {
            writeFrame(
                output,
                JSONObject().apply {
                    put("type", TYPE_TRUSTED_REJECT)
                    put("reason", "Unrecognized or revoked device certificate")
                }
            )
            socket.close()
            return
        }

        val myId = securityStore.getOrCreateDeviceId()
        val myName = securityStore.getDeviceName()
        val myPort = serverSocket?.localPort ?: _localIdentity.value.listeningPort
        val myPub = securityStore.getPublicKeyBase64()
        val myFp = securityStore.getCertFingerprint()
        val myTs = System.currentTimeMillis()
        val mySig = securityStore.signPayload("$myId|$myTs")

        writeFrame(
            output,
            JSONObject().apply {
                put("type", TYPE_TRUSTED_ACCEPT)
                put("deviceId", myId)
                put("deviceName", myName)
                put("listeningPort", myPort)
                put("publicKey", myPub)
                put("fingerprint", myFp)
                put("timestamp", myTs)
                put("sig", mySig)
            }
        )

        securityStore.upsertTrustedPeer(
            trusted!!.copy(
                deviceName = peerName,
                lastKnownIp = remoteIp,
                port = peerPort,
                lastSeenMillis = System.currentTimeMillis()
            )
        )
        manuallyDisconnectedIds.remove(peerId)
        selectedPeerIds.add(peerId)
        socket.soTimeout = SESSION_READ_TIMEOUT_MS
        attachActiveSession(peerId, socket, input, output, initialRttMs = 10L)
    }

    private fun handleIncomingFileTransfer(
        socket: Socket,
        input: DataInputStream,
        output: DataOutputStream,
        offer: JSONObject
    ) {
        val transferId = offer.optString("transferId").ifBlank { UUID.randomUUID().toString() }
        val peerId = offer.optString("deviceId").trim()
        val peerName = offer.optString("deviceName", "Trusted Peer").ifBlank { "Trusted Peer" }
        val peerPub = offer.optString("publicKey")
        val peerFp = offer.optString("fingerprint")
        val nonce = offer.optString("nonce")
        val sig = offer.optString("sig")
        val fileName = offer.optString("fileName", "received_file")
        val sizeBytes = offer.optLong("sizeBytes", -1L)

        val trusted = securityStore.isTrustedPeerCertificateValid(peerId, peerPub, peerFp)
        val verified = trusted != null &&
            sizeBytes >= 0L &&
            securityStore.verifyPeerSignature(peerPub, "$transferId|$peerId|$nonce|$sizeBytes", sig)

        if (!verified) {
            writeFrame(
                output,
                JSONObject().apply {
                    put("type", TYPE_FILE_REJECT)
                    put("reason", "Certificate verification failed; trusted pairing required")
                }
            )
            socket.close()
            return
        }

        // Silent trusted auto-accept without manual user confirmation prompt
        writeFrame(
            output,
            JSONObject().apply {
                put("type", TYPE_FILE_ACCEPT)
                put("autoAccepted", true)
            }
        )

        val dest = ConnectNetworkUtils.prepareReceiveDestination(
            context = appContext,
            rawFileName = fileName,
            overrideReceiveDir = overrideReceiveDir
        )
        val transferRecord = FileTransferRecord(
            id = transferId,
            peerDeviceId = peerId,
            peerDeviceName = trusted!!.deviceName.ifBlank { peerName },
            fileName = ConnectNetworkUtils.sanitizeFileName(fileName),
            totalBytes = sizeBytes,
            transferredBytes = 0L,
            direction = TransferDirection.INCOMING,
            status = TransferStatus.IN_PROGRESS,
            autoAccepted = true,
            savedFilePath = dest.displayPath
        )
        upsertTransferRecord(transferRecord)

        socket.soTimeout = TRANSFER_SOCKET_TIMEOUT_MS
        var received = 0L
        var writeOk = false
        try {
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            dest.openStream().use { fileOut ->
                var lastUiBytes = 0L
                while (received < sizeBytes) {
                    val remaining = sizeBytes - received
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val count = input.read(buffer, 0, toRead)
                    if (count == -1) throw EOFException("Stream ended early ($received / $sizeBytes)")
                    fileOut.write(buffer, 0, count)
                    received += count
                    if (received - lastUiBytes >= PROGRESS_NOTIFY_STEP_BYTES || received == sizeBytes) {
                        lastUiBytes = received
                        upsertTransferRecord(transferRecord.copy(transferredBytes = received))
                    }
                }
                fileOut.flush()
            }
            writeOk = true
            dest.onFinalize(true)

            val completed = transferRecord.copy(
                transferredBytes = received,
                status = TransferStatus.COMPLETED
            )
            upsertTransferRecord(completed)
            writeFrame(
                output,
                JSONObject().apply {
                    put("type", TYPE_FILE_DONE)
                    put("receivedBytes", received)
                }
            )
            _statusMessage.value = "Auto-received ${completed.fileName} from ${completed.peerDeviceName}"
            onIncomingFileCompleted?.invoke(completed)
        } catch (e: Exception) {
            dest.onFinalize(false)
            upsertTransferRecord(
                transferRecord.copy(
                    transferredBytes = received,
                    status = TransferStatus.FAILED,
                    errorMessage = e.localizedMessage ?: "Receive stream error"
                )
            )
            runCatching {
                writeFrame(
                    output,
                    JSONObject().apply {
                        put("type", TYPE_FILE_DONE)
                        put("reason", e.localizedMessage ?: "Failed")
                    }
                )
            }
        } finally {
            if (!writeOk) dest.onFinalize(false)
            runCatching { socket.close() }
        }
    }

    private fun attachActiveSession(
        deviceId: String,
        socket: Socket,
        input: DataInputStream,
        output: DataOutputStream,
        initialRttMs: Long
    ) {
        closeSessionInternal(deviceId, sendDisconnect = false, manual = false)
        val session = ActiveSession(
            deviceId = deviceId,
            socket = socket,
            input = input,
            output = output,
            rttMs = initialRttMs
        )
        activeSessions[deviceId] = session
        session.readJob = engineScope.launch {
            try {
                while (isActive && !socket.isClosed) {
                    val frame = readFrame(input)
                    when (frame.optString("type")) {
                        TYPE_PING -> {
                            val pingTs = frame.optLong("ts", System.currentTimeMillis())
                            session.writeMutex.withLock {
                                writeFrame(
                                    output,
                                    JSONObject().apply {
                                        put("type", TYPE_PONG)
                                        put("ts", pingTs)
                                    }
                                )
                            }
                        }
                        TYPE_PONG -> {
                            val pingTs = frame.optLong("ts", 0L)
                            if (pingTs > 0L) {
                                session.rttMs = (System.currentTimeMillis() - pingTs).coerceAtLeast(1L)
                                securityStore.loadTrustedPeers()
                                    .firstOrNull { it.deviceId == deviceId }
                                    ?.let { peer ->
                                        securityStore.upsertTrustedPeer(peer.copy(lastSeenMillis = System.currentTimeMillis()))
                                    }
                                refreshPeerStates()
                            }
                        }
                        TYPE_RENAME_NOTIFY -> {
                            val newName = frame.optString("deviceName").trim()
                            if (newName.isNotEmpty()) {
                                securityStore.loadTrustedPeers()
                                    .firstOrNull { it.deviceId == deviceId }
                                    ?.let { peer ->
                                        securityStore.upsertTrustedPeer(
                                            peer.copy(
                                                deviceName = newName,
                                                lastSeenMillis = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                refreshPeerStates()
                            }
                        }
                        ArworaConnectChatManager.TYPE_CHAT -> {
                            val peerName = securityStore.loadTrustedPeers()
                                .firstOrNull { it.deviceId == deviceId }?.deviceName ?: "Arwora Peer"
                            chatManager.handleIncomingTextFrame(deviceId, frame, peerName)?.let { msg ->
                                onIncomingChatMessage?.invoke(msg)
                            }
                        }
                        ArworaConnectChatManager.TYPE_IMAGE -> {
                            val peerName = securityStore.loadTrustedPeers()
                                .firstOrNull { it.deviceId == deviceId }?.deviceName ?: "Arwora Peer"
                            chatManager.handleIncomingImageFrame(deviceId, frame, peerName)?.let { msg ->
                                onIncomingChatMessage?.invoke(msg)
                            }
                        }
                        ArworaConnectChatManager.TYPE_VOICE -> {
                            val peerName = securityStore.loadTrustedPeers()
                                .firstOrNull { it.deviceId == deviceId }?.deviceName ?: "Arwora Peer"
                            chatManager.handleIncomingVoiceFrame(deviceId, frame, peerName)?.let { msg ->
                                onIncomingChatMessage?.invoke(msg)
                            }
                        }
                        ArworaConnectChatManager.TYPE_REACTION -> {
                            chatManager.handleIncomingReactionFrame(deviceId, frame)
                        }
                        TYPE_DISCONNECT -> {
                            val manual = frame.optBoolean("manual", false)
                            if (manual) manuallyDisconnectedIds.add(deviceId)
                            break
                        }
                        else -> {
                            val frameType = frame.optString("type")
                            if (frameType.startsWith("CHAT_HUB_")) {
                                onChatHubFrame?.invoke(deviceId, frame)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Socket dropped / peer moved out of range -> eligible for background auto-reconnect
            } finally {
                if (activeSessions[deviceId] === session) {
                    activeSessions.remove(deviceId)
                    runCatching { socket.close() }
                    refreshPeerStates()
                }
            }
        }
        refreshPeerStates()
    }

    private suspend fun pingActiveSessions() {
        val now = System.currentTimeMillis()
        chatManager.pruneExpiredMessages(now)
        activeSessions.values.toList().forEach { session ->
            try {
                session.writeMutex.withLock {
                    writeFrame(
                        session.output,
                        JSONObject().apply {
                            put("type", TYPE_PING)
                            put("ts", now)
                        }
                    )
                }
            } catch (_: Exception) {
                closeSessionInternal(session.deviceId, sendDisconnect = false, manual = false)
            }
        }
        refreshPeerStates()
    }

    private fun closeSessionInternal(deviceId: String, sendDisconnect: Boolean, manual: Boolean) {
        val session = activeSessions.remove(deviceId) ?: return
        if (sendDisconnect) {
            runCatching {
                writeFrame(
                    session.output,
                    JSONObject().apply {
                        put("type", TYPE_DISCONNECT)
                        put("manual", manual)
                    }
                )
            }
        }
        session.readJob?.cancel()
        runCatching { session.socket.close() }
    }

    private fun refreshPeerStates() {
        val trusted = securityStore.loadTrustedPeers()
        _peers.value = trusted.map { peer ->
            val active = activeSessions[peer.deviceId]
            val state = when {
                active != null && !active.socket.isClosed -> PeerConnectionState.CONNECTED
                reconnectingIds.contains(peer.deviceId) -> PeerConnectionState.RECONNECTING
                else -> PeerConnectionState.OFFLINE
            }
            val selected = if (selectedPeerIds.contains(peer.deviceId)) {
                true
            } else {
                // Default new trusted peers to selected once
                false
            }
            PeerUiState(
                peer = peer,
                connectionState = state,
                rttMs = active?.rttMs ?: -1L,
                isSelectedForSend = selected
            )
        }
    }

    private fun upsertTransferRecord(record: FileTransferRecord) {
        synchronized(_transfers) {
            val current = _transfers.value.toMutableList()
            val idx = current.indexOfFirst { it.id == record.id }
            if (idx >= 0) {
                current[idx] = record
            } else {
                current.add(0, record)
            }
            _transfers.value = current.take(MAX_TRANSFER_HISTORY)
        }
    }

    private fun verifyPublicKeyMatchesFingerprint(publicKeyBase64: String, fingerprint: String): Boolean {
        if (publicKeyBase64.isBlank() || fingerprint.isBlank()) return false
        return try {
            val pubBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            ConnectSecurityStore.computeFingerprintHex(pubBytes).equals(fingerprint, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun writeFrame(output: DataOutputStream, json: JSONObject) {
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }

    private fun readFrame(input: DataInputStream): JSONObject {
        val len = input.readInt()
        if (len <= 0 || len > MAX_CONTROL_FRAME_BYTES) {
            throw IllegalStateException("Invalid control frame size: $len")
        }
        val buf = ByteArray(len)
        input.readFully(buf)
        return JSONObject(String(buf, Charsets.UTF_8))
    }

    companion object {
        private const val TAG = "ArworaConnectEngine"
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val RECONNECT_PROBE_TIMEOUT_MS = 1_500
        private const val SESSION_READ_TIMEOUT_MS = 0 // persistent control socket read
        private const val TRANSFER_SOCKET_TIMEOUT_MS = 30_000
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val MAX_CONTROL_FRAME_BYTES = 2_097_152
        private const val STREAM_BUFFER_BYTES = 65_536
        private const val PROGRESS_NOTIFY_STEP_BYTES = 262_144L
        private const val MAX_TRANSFER_HISTORY = 60

        private const val TYPE_PAIR_REQUEST = "PAIR_REQUEST"
        private const val TYPE_PAIR_ACCEPT = "PAIR_ACCEPT"
        private const val TYPE_PAIR_REJECT = "PAIR_REJECT"
        private const val TYPE_NEARBY_PAIR_REQUEST = "NEARBY_PAIR_REQUEST"
        private const val TYPE_NEARBY_PAIR_ACCEPT = "NEARBY_PAIR_ACCEPT"
        private const val TYPE_TRUSTED_CONNECT = "TRUSTED_CONNECT"
        private const val TYPE_TRUSTED_ACCEPT = "TRUSTED_ACCEPT"
        private const val TYPE_TRUSTED_REJECT = "TRUSTED_REJECT"
        private const val TYPE_PING = "PING"
        private const val TYPE_PONG = "PONG"
        private const val TYPE_RENAME_NOTIFY = "RENAME_NOTIFY"
        private const val TYPE_DISCONNECT = "DISCONNECT"
        private const val TYPE_FILE_TRANSFER_INIT = "FILE_TRANSFER_INIT"
        private const val TYPE_FILE_ACCEPT = "FILE_ACCEPT"
        private const val TYPE_FILE_REJECT = "FILE_REJECT"
        private const val TYPE_FILE_DONE = "FILE_DONE"
    }
}
