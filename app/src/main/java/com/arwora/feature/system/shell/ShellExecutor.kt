package com.arwora.feature.system.shell

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShellExecutor {
    private const val TAG = "ShellExecutor"

    /**
     * Checks the availability and permission of Shizuku.
     */
    fun checkShizukuStatus(context: Context): ShellStatus {
        return try {
            if (!Shizuku.pingBinder()) {
                ShellStatus.SHIZUKU_REQUIRED
            } else {
                val hasPermission = if (Shizuku.getLatestServiceVersion() >= 11) {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                if (hasPermission) {
                    ShellStatus.READY
                } else {
                    ShellStatus.PERMISSION_DENIED
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed checking Shizuku status", e)
            ShellStatus.SHIZUKU_REQUIRED
        }
    }

    /**
     * Request Shizuku permission.
     */
    fun requestPermission(requestCode: Int = 1002) {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
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
     * Executes a shell command via Shizuku on Dispatchers.IO with a timeout.
     */
    suspend fun executeCommand(command: String): ShellCommandResult = withContext(Dispatchers.IO) {
        val startTime = SystemClock.elapsedRealtime()
        var process: Process? = null
        try {
            val cmdArgs = arrayOf("sh", "-c", command)
            
            // Invoke Shizuku newProcess via reflection
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            process = newProcessMethod.invoke(null, cmdArgs, null, null) as Process

            kotlinx.coroutines.withTimeoutOrNull(30000L) {
                coroutineScope {
                    val p = process ?: throw IllegalStateException("Process creation failed")
                    val stdoutDeferred = async(Dispatchers.IO) {
                        try {
                            val reader = BufferedReader(InputStreamReader(p.inputStream))
                            val sb = StringBuilder()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                sb.append(line).append("\n")
                            }
                            reader.close()
                            sb.toString().trimEnd()
                        } catch (e: Exception) { "" }
                    }

                    val stderrDeferred = async(Dispatchers.IO) {
                        try {
                            val reader = BufferedReader(InputStreamReader(p.errorStream))
                            val sb = StringBuilder()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                sb.append(line).append("\n")
                            }
                            reader.close()
                            sb.toString().trimEnd()
                        } catch (e: Exception) { "" }
                    }

                    val exitCode = try { p.waitFor() } catch (_: Exception) { -1 }
                    val stdout = stdoutDeferred.await()
                    val stderr = stderrDeferred.await()
                    val executionTimeMs = SystemClock.elapsedRealtime() - startTime

                    ShellCommandResult(
                        command = command,
                        stdout = stdout,
                        stderr = stderr,
                        exitCode = exitCode,
                        executionTimeMs = executionTimeMs
                    )
                }
            } ?: throw java.util.concurrent.TimeoutException("Command execution timed out after 30s")
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing shell command: $command", e)
            val executionTimeMs = SystemClock.elapsedRealtime() - startTime
            ShellCommandResult(
                command = command,
                stdout = "",
                stderr = e.message ?: "Failed to execute command via Shizuku binder",
                exitCode = -1,
                executionTimeMs = executionTimeMs
            )
        } finally {
            try {
                process?.destroy()
            } catch (ignored: Exception) {}
        }
    }
}
