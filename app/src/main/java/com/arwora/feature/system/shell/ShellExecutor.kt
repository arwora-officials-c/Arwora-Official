package com.arwora.feature.system.shell

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.PrivilegeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShellExecutor {
    private const val TAG = "ShellExecutor"

    /**
     * Checks availability of any active privilege engine (Shizuku first, then Wireless ADB).
     */
    fun checkAdbStatus(context: Context): ShellStatus {
        return try {
            val engine = AdbShellExecutor.refreshEngineStatus(context)
            when {
                engine != PrivilegeEngine.NONE -> ShellStatus.READY
                !NativeAdbManager.isWifiConnected(context) -> ShellStatus.WIFI_REQUIRED
                else -> ShellStatus.ADB_REQUIRED
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed checking privilege engine status", e)
            ShellStatus.DISCONNECTED
        }
    }

    fun getActiveEngineLabel(context: Context): String {
        return AdbShellExecutor.getEngineStatusBadge(context)
    }

    /**
     * Automatically detects active Wi-Fi and binds to the local ADB daemon.
     */
    suspend fun connectAdb(context: Context): Pair<Boolean, String> {
        val result = NativeAdbManager.connectAutomatically(context)
        AdbShellExecutor.refreshEngineStatus(context)
        return result
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
     * Executes a shell command via Shizuku (Priority 1) or Native Wireless ADB (Priority 2) on Dispatchers.IO.
     */
    suspend fun executeCommand(command: String): ShellCommandResult = withContext(Dispatchers.IO) {
        val startTime = SystemClock.elapsedRealtime()
        try {
            if (!AdbShellExecutor.isPrivilegedShellReady()) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                return@withContext ShellCommandResult(
                    command = command,
                    stdout = "",
                    stderr = "Privilege engine not connected. Authorize Shizuku or connect Wireless ADB first.",
                    exitCode = -1,
                    executionTimeMs = elapsed
                )
            }

            val result = AdbShellExecutor.executeWithEngine(command, timeoutMs = 30_000L)
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
                stderr = e.message ?: "Failed to execute privileged command",
                exitCode = -1,
                executionTimeMs = executionTimeMs
            )
        }
    }
}
