package com.arwora.feature.system.shell

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.arwora.utils.NativeAdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShellExecutor {
    private const val TAG = "ShellExecutor"

    /**
     * Checks the availability of built-in Wireless ADB.
     */
    fun checkAdbStatus(context: Context): ShellStatus {
        return try {
            NativeAdbManager.refreshConnectionStatus(context)
            when {
                NativeAdbManager.isAdbReady() -> ShellStatus.READY
                !NativeAdbManager.isWifiConnected(context) -> ShellStatus.WIFI_REQUIRED
                else -> ShellStatus.ADB_REQUIRED
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed checking Wireless ADB status", e)
            ShellStatus.DISCONNECTED
        }
    }

    /**
     * Automatically detects active Wi-Fi and binds to the local ADB daemon.
     */
    suspend fun connectAdb(context: Context): Pair<Boolean, String> {
        return NativeAdbManager.connectAutomatically(context)
    }

    /**
     * Inspects if a command could be potentially destructive or unsafe.
     */
    fun isPotentiallyDangerous(command: String): Boolean {
        val trimmed = command.trim()
        val dangerousKeywords = listOf(
            "rm ", "rm -rf", "reboot", "shutdown", "svc power",
            "pm uninstall", "pm clear", "format ", "dd ",
            "wipe", "recovery", "fastboot", "settings put",
            "setprop", "chmod 777", "chmod -R", "chown",
            "kill -9", "pkill"
        )
        return dangerousKeywords.any { keyword ->
            trimmed.startsWith(keyword, ignoreCase = true) ||
                trimmed.contains(" $keyword", ignoreCase = true) ||
                trimmed.contains(";$keyword", ignoreCase = true) ||
                trimmed.contains("&&$keyword", ignoreCase = true) ||
                trimmed.contains("||$keyword", ignoreCase = true)
        }
    }

    /**
     * Executes a shell command via built-in Wireless ADB on Dispatchers.IO.
     */
    suspend fun executeCommand(command: String): ShellCommandResult = withContext(Dispatchers.IO) {
        val startTime = SystemClock.elapsedRealtime()
        try {
            if (!NativeAdbManager.isAdbReady()) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                return@withContext ShellCommandResult(
                    command = command,
                    stdout = "",
                    stderr = "Wireless ADB is not connected. Enable Wi-Fi & Wireless Debugging and tap Connect Now.",
                    exitCode = -1,
                    executionTimeMs = elapsed
                )
            }

            val result = NativeAdbManager.executeShellCommand(command, timeoutMs = 30_000L)
            val executionTimeMs = SystemClock.elapsedRealtime() - startTime
            ShellCommandResult(
                command = command,
                stdout = if (result.success) result.output else "",
                stderr = if (result.success) "" else result.output,
                exitCode = result.exitCode,
                executionTimeMs = executionTimeMs
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing shell command: $command", e)
            val executionTimeMs = SystemClock.elapsedRealtime() - startTime
            ShellCommandResult(
                command = command,
                stdout = "",
                stderr = e.message ?: "Failed to execute command via Wireless ADB",
                exitCode = -1,
                executionTimeMs = executionTimeMs
            )
        }
    }
}
