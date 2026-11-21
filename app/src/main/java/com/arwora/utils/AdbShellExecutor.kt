package com.arwora.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class PrivilegeEngine(val statusBadgeLabel: String) {
    SHIZUKU("Active via Shizuku"),
    WIRELESS_ADB("Active via Wireless ADB"),
    NONE("Permission Required")
}

data class DualEngineShellResult(
    val success: Boolean,
    val output: String,
    val exitCode: Int,
    val engineUsed: PrivilegeEngine
)

/**
 * Universal Dual-Engine Permission & Shell Execution Bridge.
 *
 * Automatically routes privileged shell commands, AppOps calls, `pm grant/revoke`,
 * and system settings writes through the highest-priority active engine:
 *  1. Shizuku / RISH binder (zero-pairing shell privilege when active & authorized)
 *  2. Native Wireless ADB TCP/TLS socket (`NativeAdbManager`)
 *  3. Graceful unprivileged fallback (`PrivilegeEngine.NONE`)
 */
object AdbShellExecutor {

    private const val TAG = "AdbShellExecutor"
    const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    const val SHIZUKU_PERMISSION_REQUEST_CODE = ShizukuBridge.REQUEST_CODE
    private const val SHIZUKU_GITHUB_RELEASES_URL = "https://github.com/RikkaApps/Shizuku/releases"

    private val initialized = AtomicBoolean(false)

    // Test hooks for deterministic Robolectric unit testing
    internal var shizukuBinderProbeOverride: (() -> Boolean)? = null
    internal var shizukuPermissionProbeOverride: (() -> Boolean)? = null
    internal var shizukuCommandRunnerOverride: ((String, Long) -> NativeShellResult)? = null
    internal var adbReadyProbeOverride: (() -> Boolean)? = null
    internal var adbCommandRunnerOverride: (suspend (String, Long) -> NativeShellResult)? = null

    private val _activeEngine = MutableStateFlow(PrivilegeEngine.NONE)
    val activeEngine: StateFlow<PrivilegeEngine> = _activeEngine.asStateFlow()

    private val _isShizukuBinderAlive = MutableStateFlow(false)
    val isShizukuBinderAlive: StateFlow<Boolean> = _isShizukuBinderAlive.asStateFlow()

    private val _isShizukuPermissionGranted = MutableStateFlow(false)
    val isShizukuPermissionGranted: StateFlow<Boolean> = _isShizukuPermissionGranted.asStateFlow()

    private val _isAnyEngineReady = MutableStateFlow(false)
    val isAnyEngineReady: StateFlow<Boolean> = _isAnyEngineReady.asStateFlow()

    fun init(context: Context? = null) {
        initialized.set(true)
        ShizukuBridge.init(context, autoRequestIfNeeded = false)
        if (context != null) {
            runCatching { NativeAdbManager.init(context.applicationContext) }
        }
        refreshEngineStatus(context)
    }

    fun resetTestOverrides() {
        shizukuBinderProbeOverride = null
        shizukuPermissionProbeOverride = null
        shizukuCommandRunnerOverride = null
        adbReadyProbeOverride = null
        adbCommandRunnerOverride = null
        ShizukuBridge.resetTestOverrides()
    }

    fun isShizukuRunning(): Boolean {
        shizukuBinderProbeOverride?.let { return it() }
        return ShizukuBridge.isRunning()
    }

    fun hasShizukuPermission(): Boolean {
        if (!isShizukuRunning()) return false
        shizukuPermissionProbeOverride?.let { return it() }
        return ShizukuBridge.isPermissionGrantedNow()
    }

    fun isWirelessAdbActive(context: Context? = null): Boolean {
        adbReadyProbeOverride?.let { return it() }
        if (context != null) {
            runCatching { NativeAdbManager.refreshConnectionStatus(context) }
        }
        return try {
            NativeAdbManager.isAdbReady()
        } catch (_: Throwable) {
            false
        }
    }

    internal fun onShizukuPermissionResolved(granted: Boolean, context: Context? = null) {
        val binderAlive = granted || isShizukuRunning()
        _isShizukuBinderAlive.value = binderAlive
        _isShizukuPermissionGranted.value = granted
        val adbReady = isWirelessAdbActive(context)
        val engine = when {
            binderAlive && granted -> PrivilegeEngine.SHIZUKU
            adbReady -> PrivilegeEngine.WIRELESS_ADB
            else -> PrivilegeEngine.NONE
        }
        _activeEngine.value = engine
        _isAnyEngineReady.value = engine != PrivilegeEngine.NONE
    }

    fun resolveActiveEngine(context: Context? = null): PrivilegeEngine {
        val binderAlive = isShizukuRunning()
        val shizukuPerm = if (binderAlive) hasShizukuPermission() else false
        val adbReady = isWirelessAdbActive(context)

        val engine = when {
            binderAlive && shizukuPerm -> PrivilegeEngine.SHIZUKU
            adbReady -> PrivilegeEngine.WIRELESS_ADB
            else -> PrivilegeEngine.NONE
        }

        _isShizukuBinderAlive.value = binderAlive
        _isShizukuPermissionGranted.value = shizukuPerm
        _activeEngine.value = engine
        _isAnyEngineReady.value = engine != PrivilegeEngine.NONE
        return engine
    }

    fun refreshEngineStatus(context: Context? = null): PrivilegeEngine {
        ShizukuBridge.refreshState(context)
        return resolveActiveEngine(context)
    }

    fun isPrivilegedShellReady(context: Context? = null): Boolean {
        return resolveActiveEngine(context) != PrivilegeEngine.NONE
    }

    fun getEngineStatusBadge(context: Context? = null): String {
        return resolveActiveEngine(context).statusBadgeLabel
    }

    fun isShizukuInstalled(context: Context): Boolean {
        return ShizukuBridge.isShizukuInstalled(context)
    }

    fun requestShizukuPermission(requestCode: Int = ShizukuBridge.REQUEST_CODE): Boolean {
        return ShizukuBridge.requestPermission(requestCode)
    }

    fun openOrInstallShizuku(context: Context): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
            if (launchIntent != null) {
                if (context !is android.app.Activity) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launchIntent)
                true
            } else {
                val playIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$SHIZUKU_PACKAGE_NAME")
                ).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                try {
                    context.startActivity(playIntent)
                    true
                } catch (_: Exception) {
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(SHIZUKU_GITHUB_RELEASES_URL)
                    ).apply {
                        if (context !is android.app.Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(webIntent)
                    true
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to open or install Shizuku: ${t.message}")
            false
        }
    }

    suspend fun execute(
        command: String,
        timeoutMs: Long = 10_000L,
        context: Context? = null
    ): NativeShellResult {
        val dualResult = executeWithEngine(command = command, timeoutMs = timeoutMs, context = context)
        return NativeShellResult(
            success = dualResult.success,
            output = dualResult.output,
            exitCode = dualResult.exitCode
        )
    }

    fun executeBlocking(
        command: String,
        timeoutMs: Long = 10_000L,
        context: Context? = null
    ): NativeShellResult = runBlocking(Dispatchers.IO) {
        execute(command = command, timeoutMs = timeoutMs, context = context)
    }

    suspend fun grantPermission(
        packageName: String,
        permission: String,
        timeoutMs: Long = 10_000L
    ): NativeShellResult {
        return execute("pm grant $packageName $permission", timeoutMs = timeoutMs)
    }

    suspend fun executeWithEngine(
        command: String,
        timeoutMs: Long = 10_000L,
        context: Context? = null
    ): DualEngineShellResult = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return@withContext DualEngineShellResult(
                success = false,
                output = "Empty shell command",
                exitCode = -1,
                engineUsed = resolveActiveEngine(context)
            )
        }

        // Priority 1: Shizuku / RISH
        if (hasShizukuPermission()) {
            val shizukuRes = executeViaShizuku(trimmed, timeoutMs)
            refreshEngineStatus(context)
            return@withContext DualEngineShellResult(
                success = shizukuRes.success,
                output = shizukuRes.output,
                exitCode = shizukuRes.exitCode,
                engineUsed = PrivilegeEngine.SHIZUKU
            )
        }

        // Priority 2: Native Wireless ADB
        if (isWirelessAdbActive(context)) {
            val adbRes = executeViaWirelessAdb(trimmed, timeoutMs)
            refreshEngineStatus(context)
            return@withContext DualEngineShellResult(
                success = adbRes.success,
                output = adbRes.output,
                exitCode = adbRes.exitCode,
                engineUsed = PrivilegeEngine.WIRELESS_ADB
            )
        }

        // Fallback: Graceful unprivileged return
        refreshEngineStatus(context)
        DualEngineShellResult(
            success = false,
            output = "No active privilege engine. Enable Wireless ADB or authorize Shizuku to run privileged commands.",
            exitCode = -1,
            engineUsed = PrivilegeEngine.NONE
        )
    }

    private suspend fun executeViaWirelessAdb(command: String, timeoutMs: Long): NativeShellResult {
        adbCommandRunnerOverride?.let { runner ->
            return try {
                runner(command, timeoutMs)
            } catch (t: Throwable) {
                NativeShellResult(false, t.message ?: "Wireless ADB execution failed", -1)
            }
        }
        return try {
            NativeAdbManager.executeShellCommand(command, timeoutMs)
        } catch (t: Throwable) {
            NativeShellResult(false, t.message ?: "Wireless ADB execution failed", -1)
        }
    }

    private fun executeViaShizuku(command: String, timeoutMs: Long): NativeShellResult {
        shizukuCommandRunnerOverride?.let { runner ->
            return try {
                runner(command, timeoutMs)
            } catch (t: Throwable) {
                NativeShellResult(false, t.message ?: "Shizuku execution failed", -1)
            }
        }

        var process: Process? = null
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as? Process
                ?: return NativeShellResult(false, "Shizuku process returned null", -1)

            val stdoutBuf = ByteArrayOutputStream()
            val stderrBuf = ByteArrayOutputStream()
            val outThread = Thread {
                copyStreamQuietly(process.inputStream, stdoutBuf)
            }.apply { isDaemon = true; start() }
            val errThread = Thread {
                copyStreamQuietly(process.errorStream, stderrBuf)
            }.apply { isDaemon = true; start() }

            val finished = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            } else {
                val deadline = System.currentTimeMillis() + timeoutMs
                var done = false
                while (System.currentTimeMillis() < deadline) {
                    try {
                        process.exitValue()
                        done = true
                        break
                    } catch (_: IllegalThreadStateException) {
                        Thread.sleep(25L)
                    }
                }
                done
            }

            if (!finished) {
                process.destroy()
                return NativeShellResult(
                    success = false,
                    output = "Shizuku command timed out after ${timeoutMs}ms",
                    exitCode = -1
                )
            }

            runCatching { outThread.join(1_000L) }
            runCatching { errThread.join(1_000L) }

            val exitCode = process.exitValue()
            val stdout = stdoutBuf.toString(Charsets.UTF_8.name()).trim()
            val stderr = stderrBuf.toString(Charsets.UTF_8.name()).trim()
            val combined = when {
                stdout.isNotEmpty() && stderr.isNotEmpty() -> "$stdout\n$stderr"
                stdout.isNotEmpty() -> stdout
                else -> stderr
            }

            NativeShellResult(
                success = exitCode == 0,
                output = combined,
                exitCode = exitCode
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Shizuku command error for [$command]: ${t.message}", t)
            NativeShellResult(
                success = false,
                output = t.message ?: "Shizuku execution failed",
                exitCode = -1
            )
        } finally {
            runCatching { process?.destroy() }
        }
    }

    private fun copyStreamQuietly(input: InputStream, target: ByteArrayOutputStream) {
        try {
            val buffer = ByteArray(4096)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                target.write(buffer, 0, read)
            }
        } catch (_: Throwable) {
        }
    }
}
