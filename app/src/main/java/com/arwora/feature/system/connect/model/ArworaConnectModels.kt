package com.arwora.feature.system.connect.model

import org.json.JSONObject

data class PairingQrPayload(
    val deviceId: String,
    val deviceName: String,
    val hostIp: String,
    val port: Int,
    val pairingCode: String,
    val publicKeyBase64: String,
    val certFingerprint: String
) {
    fun toUriString(): String {
        return JSONObject().apply {
            put("proto", PROTOCOL_SCHEME)
            put("id", deviceId)
            put("name", deviceName)
            put("ip", hostIp)
            put("port", port)
            put("code", pairingCode)
            put("pk", publicKeyBase64)
            put("fp", certFingerprint)
        }.toString()
    }

    companion object {
        const val PROTOCOL_SCHEME = "arwora-connect-v1"

        fun parse(raw: String): PairingQrPayload? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            // Support JSON payload or arwora://connect?... URI
            if (trimmed.startsWith("{")) {
                return try {
                    val obj = JSONObject(trimmed)
                    if (obj.optString("proto") != PROTOCOL_SCHEME) return null
                    val id = obj.optString("id").trim()
                    val ip = obj.optString("ip").trim()
                    val port = obj.optInt("port", 0)
                    if (id.isEmpty() || ip.isEmpty() || port !in 1..65535) return null
                    PairingQrPayload(
                        deviceId = id,
                        deviceName = obj.optString("name", "Arwora Peer").ifBlank { "Arwora Peer" },
                        hostIp = ip,
                        port = port,
                        pairingCode = obj.optString("code", ""),
                        publicKeyBase64 = obj.optString("pk", ""),
                        certFingerprint = obj.optString("fp", "")
                    )
                } catch (_: Exception) {
                    null
                }
            }

            if (trimmed.startsWith("arwora://connect", ignoreCase = true)) {
                return try {
                    val uri = android.net.Uri.parse(trimmed)
                    val id = uri.getQueryParameter("id")?.trim().orEmpty()
                    val ip = uri.getQueryParameter("ip")?.trim().orEmpty()
                    val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 0
                    if (id.isEmpty() || ip.isEmpty() || port !in 1..65535) return null
                    PairingQrPayload(
                        deviceId = id,
                        deviceName = uri.getQueryParameter("name")?.takeIf { it.isNotBlank() } ?: "Arwora Peer",
                        hostIp = ip,
                        port = port,
                        pairingCode = uri.getQueryParameter("code").orEmpty(),
                        publicKeyBase64 = uri.getQueryParameter("pk").orEmpty(),
                        certFingerprint = uri.getQueryParameter("fp").orEmpty()
                    )
                } catch (_: Exception) {
                    null
                }
            }

            return null
        }
    }
}

enum class PeerConnectionState {
    CONNECTED,
    RECONNECTING,
    OFFLINE
}

enum class PeerSignalQuality(val label: String) {
    STRONG("Strong"),
    GOOD("Good"),
    WEAK("Weak"),
    OUT_OF_RANGE("Out of Range")
}

data class TrustedPeerRecord(
    val deviceId: String,
    val deviceName: String,
    val lastKnownIp: String,
    val port: Int,
    val publicKeyBase64: String,
    val certFingerprint: String,
    val pairedAtMillis: Long,
    val lastSeenMillis: Long,
    val trustedAutoAccept: Boolean = true
)

data class PeerUiState(
    val peer: TrustedPeerRecord,
    val connectionState: PeerConnectionState,
    val rttMs: Long = -1L,
    val isSelectedForSend: Boolean = true
) {
    val signalQuality: PeerSignalQuality
        get() = when {
            connectionState != PeerConnectionState.CONNECTED -> PeerSignalQuality.OUT_OF_RANGE
            rttMs in 0..35 -> PeerSignalQuality.STRONG
            rttMs in 36..120 -> PeerSignalQuality.GOOD
            else -> PeerSignalQuality.WEAK
        }
}

enum class TransferDirection {
    OUTGOING,
    INCOMING
}

enum class TransferStatus {
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

data class FileTransferRecord(
    val id: String,
    val peerDeviceId: String,
    val peerDeviceName: String,
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val direction: TransferDirection,
    val status: TransferStatus,
    val autoAccepted: Boolean,
    val savedFilePath: String? = null,
    val errorMessage: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (totalBytes <= 0L) 0f else (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

data class LocalIdentityState(
    val deviceId: String = "",
    val deviceName: String = "Arwora Device",
    val localIp: String = "0.0.0.0",
    val listeningPort: Int = 48291,
    val pairingCode: String = "000000",
    val publicKeyBase64: String = "",
    val certFingerprint: String = "",
    val isServerRunning: Boolean = false
)
