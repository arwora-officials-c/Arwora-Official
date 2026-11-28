package com.arwora.feature.chat.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Provides a persistent 256-bit passphrase for SQLCipher database encryption,
 * protected via Android Keystore / EncryptedSharedPreferences when available.
 */
object ChatCipherKeyStore {
    private const val PREFS_NAME = "arwora_chat_cipher_keystore"
    private const val FALLBACK_PREFS_NAME = "arwora_chat_cipher_fallback"
    private const val KEY_DB_PASSPHRASE = "chat_sqlcipher_passphrase_b64"

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    @Synchronized
    fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        cachedPassphrase?.let { return it.copyOf() }
        val appCtx = context.applicationContext
        val prefs = openSecurePrefs(appCtx)
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        val decoded = if (!existing.isNullOrBlank()) {
            runCatching { Base64.decode(existing, Base64.NO_WRAP) }.getOrNull()
        } else {
            null
        }
        val keyBytes = if (decoded != null && decoded.size >= 32) {
            decoded
        } else {
            ByteArray(32).also { bytes ->
                SecureRandom().nextBytes(bytes)
                prefs.edit()
                    .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(bytes, Base64.NO_WRAP))
                    .apply()
            }
        }
        cachedPassphrase = keyBytes
        return keyBytes.copyOf()
    }

    @Synchronized
    fun clearForTests(context: Context) {
        cachedPassphrase = null
        val appCtx = context.applicationContext
        runCatching { appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit() }
        runCatching { appCtx.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    private fun openSecurePrefs(context: Context): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            // Safe fallback on Robolectric JVM or Keystore-unavailable test runners
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
}
