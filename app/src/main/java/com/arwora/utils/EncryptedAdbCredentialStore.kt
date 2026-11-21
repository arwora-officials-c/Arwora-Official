package com.arwora.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyPair
import java.security.MessageDigest

data class AdbPersistedCredentials(
    val isPaired: Boolean = false,
    val host: String = "127.0.0.1",
    val connectPort: Int = -1,
    val pairingPort: Int = -1,
    val clientIdentity: String = NativeAdbProtocol.DEFAULT_CLIENT_IDENTITY,
    val rsaFingerprint: String? = null,
    val userStopped: Boolean = false,
    val lastConnectedAtMs: Long = 0L
)

/**
 * Hardware-backed encrypted credential and session store for persistent Wireless ADB.
 * Uses AndroidX [EncryptedSharedPreferences] (AES256-GCM) when AndroidKeyStore is available,
 * and falls back gracefully to private app storage in constrained/JVM Robolectric environments.
 */
object EncryptedAdbCredentialStore {
    private const val TAG = "EncryptedAdbCredStore"
    private const val ENCRYPTED_PREFS_NAME = "adb_secure_credentials"
    private const val FALLBACK_PREFS_NAME = "adb_secure_credentials_fallback"

    private const val KEY_IS_PAIRED = "is_paired"
    private const val KEY_HOST = "host"
    private const val KEY_CONNECT_PORT = "connect_port"
    private const val KEY_PAIRING_PORT = "pairing_port"
    private const val KEY_CLIENT_IDENTITY = "client_identity"
    private const val KEY_RSA_FINGERPRINT = "rsa_fingerprint"
    private const val KEY_USER_STOPPED = "user_stopped"
    private const val KEY_LAST_CONNECTED_AT_MS = "last_connected_at_ms"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    @Volatile
    private var cachedAppContext: Context? = null

    private fun getPrefs(context: Context): SharedPreferences {
        val appCtx = context.applicationContext
        val existing = cachedPrefs
        if (existing != null && cachedAppContext === appCtx) return existing
        synchronized(this) {
            val current = cachedPrefs
            if (current != null && cachedAppContext === appCtx) return current
            val created = try {
                val masterKey = MasterKey.Builder(appCtx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appCtx,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (t: Throwable) {
                Log.w(TAG, "EncryptedSharedPreferences unavailable in this environment, using MODE_PRIVATE fallback: ${t.message}")
                appCtx.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
            }
            cachedAppContext = appCtx
            cachedPrefs = created
            return created
        }
    }

    internal fun resetCacheForTesting() {
        cachedPrefs = null
    }

    fun computeKeyFingerprint(keyPair: KeyPair): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded)
            digest.joinToString(":") { b -> "%02X".format(b) }
        } catch (_: Throwable) {
            ""
        }
    }

    fun loadState(context: Context): AdbPersistedCredentials {
        val prefs = getPrefs(context)
        val rawHost = prefs.getString(KEY_HOST, "127.0.0.1")?.trim()?.takeIf { it.isNotEmpty() } ?: "127.0.0.1"
        val connectPort = prefs.getInt(KEY_CONNECT_PORT, -1).let { if (it in 1024..65535) it else -1 }
        val pairingPort = prefs.getInt(KEY_PAIRING_PORT, -1).let { if (it in 1024..65535) it else -1 }
        val identity = prefs.getString(KEY_CLIENT_IDENTITY, NativeAdbProtocol.DEFAULT_CLIENT_IDENTITY)
            ?.ifBlank { NativeAdbProtocol.DEFAULT_CLIENT_IDENTITY }
            ?: NativeAdbProtocol.DEFAULT_CLIENT_IDENTITY
        return AdbPersistedCredentials(
            isPaired = prefs.getBoolean(KEY_IS_PAIRED, false),
            host = rawHost,
            connectPort = connectPort,
            pairingPort = pairingPort,
            clientIdentity = identity,
            rsaFingerprint = prefs.getString(KEY_RSA_FINGERPRINT, null),
            userStopped = prefs.getBoolean(KEY_USER_STOPPED, false),
            lastConnectedAtMs = prefs.getLong(KEY_LAST_CONNECTED_AT_MS, 0L)
        )
    }

    fun markPaired(
        context: Context,
        host: String? = null,
        pairingPort: Int? = null,
        rsaFingerprint: String? = null
    ) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            putBoolean(KEY_IS_PAIRED, true)
            putBoolean(KEY_USER_STOPPED, false)
            putString(KEY_CLIENT_IDENTITY, NativeAdbManager.ensureClientIdentity())
            host?.trim()?.takeIf { it.isNotEmpty() }?.let { putString(KEY_HOST, it) }
            pairingPort?.takeIf { it in 1024..65535 }?.let { putInt(KEY_PAIRING_PORT, it) }
            rsaFingerprint?.takeIf { it.isNotBlank() }?.let { putString(KEY_RSA_FINGERPRINT, it) }
        }.apply()
    }

    fun savePairedSession(
        context: Context,
        host: String,
        connectPort: Int,
        pairingPort: Int? = null,
        rsaFingerprint: String? = null
    ) {
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        val validConnectPort = connectPort.takeIf { it in 1024..65535 } ?: -1
        val prefs = getPrefs(context)
        prefs.edit().apply {
            putBoolean(KEY_IS_PAIRED, true)
            putString(KEY_HOST, cleanHost)
            if (validConnectPort in 1024..65535) {
                putInt(KEY_CONNECT_PORT, validConnectPort)
            }
            pairingPort?.takeIf { it in 1024..65535 }?.let { putInt(KEY_PAIRING_PORT, it) }
            putString(KEY_CLIENT_IDENTITY, NativeAdbManager.ensureClientIdentity())
            rsaFingerprint?.takeIf { it.isNotBlank() }?.let { putString(KEY_RSA_FINGERPRINT, it) }
            putBoolean(KEY_USER_STOPPED, false)
            putLong(KEY_LAST_CONNECTED_AT_MS, System.currentTimeMillis())
        }.apply()
    }

    fun setUserStopped(context: Context, stopped: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_USER_STOPPED, stopped)
            .apply()
    }

    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
