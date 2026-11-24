package com.arwora.feature.system.connect.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.arwora.feature.system.connect.model.TrustedPeerRecord
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

/**
 * Encrypted local store for Arwora Connect:
 * - Persists local EC P-256 identity keypair & SHA-256 certificate fingerprint
 * - Persists custom device broadcast name and 6-digit pairing token
 * - Persists paired [TrustedPeerRecord] entries & verifies ECDSA signatures for trusted auto-accept
 */
class ConnectSecurityStore(
    context: Context,
    private val prefsSuffix: String = ""
) {

    private val appContext = context.applicationContext
    private val secureRandom = SecureRandom()

    private val encryptedPrefs: SharedPreferences by lazy {
        createEncryptedPrefs(appContext)
    }

    private fun createEncryptedPrefs(ctx: Context): SharedPreferences {
        val secureName = if (prefsSuffix.isBlank()) ENCRYPTED_PREFS_NAME else "${ENCRYPTED_PREFS_NAME}_$prefsSuffix"
        val fallbackName = if (prefsSuffix.isBlank()) FALLBACK_PREFS_NAME else "${FALLBACK_PREFS_NAME}_$prefsSuffix"
        return try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                secureName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable in current keystore environment; using fallback", e)
            ctx.getSharedPreferences(fallbackName, Context.MODE_PRIVATE)
        }
    }

    @Synchronized
    fun getOrCreateDeviceId(): String {
        val existing = encryptedPrefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString().replace("-", "").take(16)
        encryptedPrefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    @Synchronized
    fun getDeviceName(): String {
        val custom = encryptedPrefs.getString(KEY_DEVICE_NAME, null)
        if (!custom.isNullOrBlank()) return custom

        val systemName = resolveDefaultSystemDeviceName(appContext)
        encryptedPrefs.edit().putString(KEY_DEVICE_NAME, systemName).apply()
        return systemName
    }

    @Synchronized
    fun setDeviceName(newName: String): String {
        val sanitized = newName.trim().replace(Regex("\\s+"), " ").take(36)
        val finalName = sanitized.ifBlank { resolveDefaultSystemDeviceName(appContext) }
        encryptedPrefs.edit().putString(KEY_DEVICE_NAME, finalName).apply()
        return finalName
    }

    @Synchronized
    fun getOrGeneratePairingCode(forceRegenerate: Boolean = false): String {
        val existing = encryptedPrefs.getString(KEY_PAIRING_CODE, null)
        if (!forceRegenerate && !existing.isNullOrBlank() && existing.length == 6) {
            return existing
        }
        val code = (100000 + secureRandom.nextInt(900000)).toString()
        encryptedPrefs.edit().putString(KEY_PAIRING_CODE, code).apply()
        return code
    }

    @Synchronized
    fun getOrCreateKeyPair(): Pair<PrivateKey, PublicKey> {
        val privB64 = encryptedPrefs.getString(KEY_PRIVATE_KEY_B64, null)
        val pubB64 = encryptedPrefs.getString(KEY_PUBLIC_KEY_B64, null)
        if (!privB64.isNullOrBlank() && !pubB64.isNullOrBlank()) {
            try {
                val kf = KeyFactory.getInstance("EC")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privB64, Base64.NO_WRAP)))
                val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(pubB64, Base64.NO_WRAP)))
                return priv to pub
            } catch (_: Exception) {
                // Regenerate corrupted keys below
            }
        }
        val kp = generateEcKeyPair()
        encryptedPrefs.edit()
            .putString(KEY_PRIVATE_KEY_B64, Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
            .putString(KEY_PUBLIC_KEY_B64, Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP))
            .apply()
        return kp.private to kp.public
    }

    fun getPublicKeyBase64(): String {
        val (_, pub) = getOrCreateKeyPair()
        return Base64.encodeToString(pub.encoded, Base64.NO_WRAP)
    }

    fun getCertFingerprint(): String {
        val (_, pub) = getOrCreateKeyPair()
        return computeFingerprintHex(pub.encoded)
    }

    fun signPayload(payload: String): String {
        val (priv, _) = getOrCreateKeyPair()
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(priv)
        sig.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
    }

    fun verifyPeerSignature(
        publicKeyBase64: String,
        payload: String,
        signatureBase64: String
    ): Boolean {
        if (publicKeyBase64.isBlank() || signatureBase64.isBlank()) return false
        return try {
            val pubBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val sigBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val pubKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pubBytes))
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(pubKey)
            verifier.update(payload.toByteArray(Charsets.UTF_8))
            verifier.verify(sigBytes)
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun loadTrustedPeers(): List<TrustedPeerRecord> {
        val raw = encryptedPrefs.getString(KEY_TRUSTED_PEERS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<TrustedPeerRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("deviceId").trim()
                if (id.isEmpty()) continue
                out.add(
                    TrustedPeerRecord(
                        deviceId = id,
                        deviceName = obj.optString("deviceName", "Arwora Peer").ifBlank { "Arwora Peer" },
                        lastKnownIp = obj.optString("lastKnownIp"),
                        port = obj.optInt("port", DEFAULT_PORT),
                        publicKeyBase64 = obj.optString("publicKeyBase64"),
                        certFingerprint = obj.optString("certFingerprint"),
                        pairedAtMillis = obj.optLong("pairedAtMillis", 0L),
                        lastSeenMillis = obj.optLong("lastSeenMillis", 0L),
                        trustedAutoAccept = obj.optBoolean("trustedAutoAccept", true)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun upsertTrustedPeer(record: TrustedPeerRecord): List<TrustedPeerRecord> {
        val existing = loadTrustedPeers().toMutableList()
        val idx = existing.indexOfFirst { it.deviceId == record.deviceId }
        if (idx >= 0) {
            existing[idx] = record
        } else {
            existing.add(0, record)
        }
        saveTrustedPeers(existing)
        return existing
    }

    @Synchronized
    fun removeTrustedPeer(deviceId: String): List<TrustedPeerRecord> {
        val next = loadTrustedPeers().filterNot { it.deviceId == deviceId }
        saveTrustedPeers(next)
        return next
    }

    @Synchronized
    fun isTrustedPeerCertificateValid(
        deviceId: String,
        publicKeyBase64: String,
        certFingerprint: String
    ): TrustedPeerRecord? {
        val peer = loadTrustedPeers().firstOrNull { it.deviceId == deviceId } ?: return null
        if (!peer.trustedAutoAccept) return null
        if (peer.publicKeyBase64.isBlank() || peer.certFingerprint.isBlank()) return null
        val keyMatches = peer.publicKeyBase64 == publicKeyBase64
        val fpMatches = peer.certFingerprint.equals(certFingerprint, ignoreCase = true)
        return if (keyMatches && fpMatches) peer else null
    }

    @Synchronized
    fun clearAllForTests() {
        encryptedPrefs.edit().clear().apply()
    }

    private fun saveTrustedPeers(peers: List<TrustedPeerRecord>) {
        val arr = JSONArray()
        peers.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("deviceId", p.deviceId)
                    put("deviceName", p.deviceName)
                    put("lastKnownIp", p.lastKnownIp)
                    put("port", p.port)
                    put("publicKeyBase64", p.publicKeyBase64)
                    put("certFingerprint", p.certFingerprint)
                    put("pairedAtMillis", p.pairedAtMillis)
                    put("lastSeenMillis", p.lastSeenMillis)
                    put("trustedAutoAccept", p.trustedAutoAccept)
                }
            )
        }
        encryptedPrefs.edit().putString(KEY_TRUSTED_PEERS_JSON, arr.toString()).apply()
    }

    companion object {
        private const val TAG = "ConnectSecurityStore"
        private const val ENCRYPTED_PREFS_NAME = "arwora_connect_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "arwora_connect_fallback_prefs"

        private const val KEY_DEVICE_ID = "local_device_id"
        private const val KEY_DEVICE_NAME = "local_device_name"
        private const val KEY_PAIRING_CODE = "active_pairing_code"
        private const val KEY_PRIVATE_KEY_B64 = "ec_private_key_b64"
        private const val KEY_PUBLIC_KEY_B64 = "ec_public_key_b64"
        private const val KEY_TRUSTED_PEERS_JSON = "trusted_peers_json"

        const val DEFAULT_PORT = 48291

        fun computeFingerprintHex(publicKeyEncoded: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyEncoded)
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun generateEcKeyPair(): KeyPair {
            val kpg = KeyPairGenerator.getInstance("EC")
            try {
                kpg.initialize(ECGenParameterSpec("secp256r1"))
            } catch (_: Exception) {
                kpg.initialize(256)
            }
            return kpg.generateKeyPair()
        }

        private fun resolveDefaultSystemDeviceName(context: Context): String {
            val secureOrGlobal = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            } ?: try {
                Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            } catch (_: Exception) {
                null
            }
            if (!secureOrGlobal.isNullOrBlank()) return secureOrGlobal.trim().take(36)

            val model = Build.MODEL?.trim().orEmpty()
            val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
            return when {
                model.isNotBlank() && model.startsWith(manufacturer, ignoreCase = true) -> model.take(36)
                manufacturer.isNotBlank() && model.isNotBlank() -> "$manufacturer $model".take(36)
                model.isNotBlank() -> model.take(36)
                else -> "Arwora Device"
            }
        }
    }
}
