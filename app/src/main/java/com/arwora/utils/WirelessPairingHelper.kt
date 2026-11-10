package com.arwora.utils

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

object WirelessPairingHelper {
    private const val TAG = "WirelessPairingHelper"

    // Regex to match 6-digit wireless debugging pairing codes
    private val PAIRING_CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b")

    fun extractPairingCode(text: String): String? {
        return WirelessPairingManager.extractPairingCode(text)
    }

    fun extractPairingPort(text: String): Int? {
        return WirelessPairingManager.extractPairingPort(text)
    }

    suspend fun executeShizukuCommand(command: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (rikka.shizuku.Shizuku.pingBinder() && rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // Use ShizukuUserService if available, or fallback to standard runtime / shizuku API
                    val userServiceArgs = rikka.shizuku.Shizuku.UserServiceArgs(
                        android.content.ComponentName("com.arwora", "com.arwora.service.ShizukuUserService")
                    ).daemon(false)
                    // Fallback execution via standard runtime process if Shizuku remote process invocation requires wrapper
                }
            }
            // Fallback to standard runtime shell process
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Pair(true, output.ifBlank { "Success" })
            } else {
                Pair(false, error.ifBlank { "Exit code: $exitCode" })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute shell command", e)
            Pair(false, e.message ?: "Unknown exception")
        }
    }
}
