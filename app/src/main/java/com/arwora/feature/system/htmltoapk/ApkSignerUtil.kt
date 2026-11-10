package com.arwora.feature.system.htmltoapk

import android.content.Context
import android.util.Base64
import android.util.Log
import com.android.apksig.ApkSigner
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Collections

object ApkSignerUtil {
    private const val TAG = "ApkSignerUtil"
    private const val KEYSTORE_PASS = "android"
    private const val KEY_ALIAS = "androiddebugkey"

    // Embedded fallback PKCS12 debug keystore (CN=Android Debug, valid until 2054)
    private const val EMBEDDED_KEYSTORE_B64 =
        "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" +
        "BBRN1s7o97SCx4a7drK4/IGUIWHIxgICJxA="

    private fun safeLogD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun safeLogW(tag: String, msg: String, tr: Throwable? = null) {
        try {
            Log.w(tag, msg, tr)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun safeLogE(tag: String, msg: String, tr: Throwable? = null) {
        try {
            Log.e(tag, msg, tr)
        } catch (_: Throwable) {
            System.err.println("[$tag] $msg: $tr")
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.DEFAULT)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str.replace("\n", "").replace("\r", "").trim())
        }
    }

    fun signApk(unsignedApk: File, signedApk: File, context: Context? = null): Boolean {
        return try {
            val keyStore = loadKeyStore(context)
            val privateKey = keyStore.getKey(KEY_ALIAS, KEYSTORE_PASS.toCharArray()) as PrivateKey
            val cert = keyStore.getCertificate(KEY_ALIAS) as X509Certificate

            val signerConfig = ApkSigner.SignerConfig.Builder(
                "CERT",
                privateKey,
                Collections.singletonList(cert)
            ).build()

            val apkSigner = ApkSigner.Builder(Collections.singletonList(signerConfig))
                .setInputApk(unsignedApk)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()

            apkSigner.sign()
            val success = signedApk.exists() && signedApk.length() > 0
            safeLogD(TAG, "ApkSigner signed APK successfully: $success (${signedApk.length()} bytes)")
            success
        } catch (e: Exception) {
            safeLogE(TAG, "Failed to sign APK using ApkSigner", e)
            e.printStackTrace()
            false
        }
    }

    private fun loadKeyStore(context: Context?): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        // Try loading from assets first
        if (context != null) {
            try {
                context.assets.open("signing/debug.keystore").use { stream ->
                    ks.load(stream, KEYSTORE_PASS.toCharArray())
                    safeLogD(TAG, "Loaded debug keystore from app assets")
                    return ks
                }
            } catch (e: Exception) {
                safeLogW(TAG, "Could not load debug keystore from assets, falling back to embedded bytes", e)
            }
        }
        // Fallback to embedded base64 keystore
        val bytes = decodeBase64(EMBEDDED_KEYSTORE_B64)
        ByteArrayInputStream(bytes).use { stream ->
            ks.load(stream, KEYSTORE_PASS.toCharArray())
            safeLogD(TAG, "Loaded debug keystore from embedded base64")
        }
        return ks
    }
}

