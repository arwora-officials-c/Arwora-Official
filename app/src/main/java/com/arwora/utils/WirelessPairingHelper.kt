package com.arwora.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WirelessPairingHelper {
    fun extractPairingCode(text: String): String? {
        return WirelessPairingManager.extractPairingCode(text)
    }

    fun extractPairingPort(text: String): Int? {
        return WirelessPairingManager.extractPairingPort(text)
    }

    suspend fun executeAdbCommand(command: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (AdbShellExecutor.isPrivilegedShellReady()) {
            val res = AdbShellExecutor.execute(command)
            return@withContext Pair(res.success, res.output.ifBlank { if (res.success) "Success" else "Command failed" })
        }
        try {
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
            Pair(false, e.message ?: "Unknown exception")
        }
    }
}
