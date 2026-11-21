package com.arwora.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

enum class ShizukuBridgeStatus(val label: String) {
    AUTHORIZED("Authorized"),
    PERMISSION_REQUIRED("Permission Required"),
    RATIONALE_REQUIRED("Permission Denied — Tap to Grant"),
    BINDER_DEAD("Service Stopped"),
    NOT_INSTALLED("Not Installed")
}

/**
 * Persistent lifecycle-safe Shizuku state & permission bridge.
 *
 * Binds `OnBinderReceivedListener`, `OnBinderDeadListener`, and `OnRequestPermissionResultListener`
 * idempotently using [REQUEST_CODE] (1001) so permission grants are immediately reflected across all
 * screens without stale "Connect Shizuku" prompts.
 */
object ShizukuBridge {
    private const val TAG = "ShizukuBridge"
    const val REQUEST_CODE = 1001
    private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

    private val listenersRegistered = AtomicBoolean(false)
    private val hasAutoRequestedForSession = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var autoRequestPermissionOnBinder: Boolean = false

    // Deterministic Robolectric / unit-test hooks
    internal var binderProbeOverride: (() -> Boolean)? = null
    internal var permissionProbeOverride: (() -> Boolean)? = null
    internal var rationaleProbeOverride: (() -> Boolean)? = null
    internal var preV11ProbeOverride: (() -> Boolean)? = null
    internal var installedProbeOverride: ((Context) -> Boolean)? = null
    internal var requestPermissionInvokerOverride: ((Int) -> Unit)? = null

    private val _isBinderAlive = MutableStateFlow(false)
    val isBinderAlive: StateFlow<Boolean> = _isBinderAlive.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val _shouldShowRationale = MutableStateFlow(false)
    val shouldShowRationale: StateFlow<Boolean> = _shouldShowRationale.asStateFlow()

    private val _isInstalled = MutableStateFlow(false)
    val isInstalled: StateFlow<Boolean> = _isInstalled.asStateFlow()

    private val _status = MutableStateFlow(ShizukuBridgeStatus.BINDER_DEAD)
    val status: StateFlow<ShizukuBridgeStatus> = _status.asStateFlow()

    private val _lastPermissionResult = MutableStateFlow<Int?>(null)
    val lastPermissionResult: StateFlow<Int?> = _lastPermissionResult.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        onBinderReceived()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        onBinderDead()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            onPermissionResult(requestCode, grantResult)
        }

    fun init(context: Context? = null, autoRequestIfNeeded: Boolean = false) {
        if (context != null) {
            appContext = context.applicationContext
        }
        if (autoRequestIfNeeded) {
            autoRequestPermissionOnBinder = true
        }
        registerListenersIfNeeded()
        val currentStatus = refreshState(appContext)
        if (autoRequestIfNeeded && currentStatus == ShizukuBridgeStatus.PERMISSION_REQUIRED) {
            tryAutoRequestPermissionOnce()
        }
    }

    private fun registerListenersIfNeeded() {
        if (listenersRegistered.compareAndSet(false, true)) {
            runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
            }.onFailure { err ->
                Log.d(TAG, "Shizuku listeners unavailable in current environment: ${err.message}")
            }
        }
    }

    fun cleanup() {
        if (listenersRegistered.compareAndSet(true, false)) {
            runCatching {
                Shizuku.removeBinderReceivedListener(binderReceivedListener)
                Shizuku.removeBinderDeadListener(binderDeadListener)
                Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            }
        }
        hasAutoRequestedForSession.set(false)
    }

    internal fun resetTestOverrides() {
        binderProbeOverride = null
        permissionProbeOverride = null
        rationaleProbeOverride = null
        preV11ProbeOverride = null
        installedProbeOverride = null
        requestPermissionInvokerOverride = null
        hasAutoRequestedForSession.set(false)
        autoRequestPermissionOnBinder = false
        _lastPermissionResult.value = null
    }

    fun isRunning(): Boolean {
        binderProbeOverride?.let { return it() }
        AdbShellExecutor.shizukuBinderProbeOverride?.let { return it() }
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPreV11(): Boolean {
        preV11ProbeOverride?.let { return it() }
        return try {
            Shizuku.isPreV11()
        } catch (_: Throwable) {
            false
        }
    }

    fun isPermissionGrantedNow(): Boolean {
        if (!isRunning()) return false
        permissionProbeOverride?.let { return it() }
        AdbShellExecutor.shizukuPermissionProbeOverride?.let { return it() }
        return try {
            if (isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun shouldShowRequestPermissionRationaleNow(): Boolean {
        if (!isRunning() || isPreV11()) return false
        rationaleProbeOverride?.let { return it() }
        return try {
            Shizuku.shouldShowRequestPermissionRationale()
        } catch (_: Throwable) {
            false
        }
    }

    fun isShizukuInstalled(context: Context?): Boolean {
        val ctx = context ?: appContext ?: return false
        installedProbeOverride?.let { return it(ctx) }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.packageManager.getPackageInfo(
                    SHIZUKU_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun refreshState(context: Context? = null): ShizukuBridgeStatus {
        val ctx = context?.applicationContext ?: appContext
        if (ctx != null) appContext = ctx

        val installed = isShizukuInstalled(ctx)
        val binderAlive = isRunning()
        val granted = if (binderAlive) isPermissionGrantedNow() else false
        val rationale = if (binderAlive && !granted) shouldShowRequestPermissionRationaleNow() else false

        if (granted) {
            hasAutoRequestedForSession.set(false)
        }

        val resolved = when {
            binderAlive && granted -> ShizukuBridgeStatus.AUTHORIZED
            binderAlive && rationale -> ShizukuBridgeStatus.RATIONALE_REQUIRED
            binderAlive -> ShizukuBridgeStatus.PERMISSION_REQUIRED
            installed -> ShizukuBridgeStatus.BINDER_DEAD
            else -> ShizukuBridgeStatus.NOT_INSTALLED
        }

        _isInstalled.value = installed
        _isBinderAlive.value = binderAlive
        _isPermissionGranted.value = granted
        _shouldShowRationale.value = rationale
        _status.value = resolved
        return resolved
    }

    fun requestPermission(requestCode: Int = REQUEST_CODE): Boolean {
        if (!isRunning()) {
            refreshState(appContext)
            return false
        }
        if (isPreV11()) {
            return false
        }
        if (isPermissionGrantedNow()) {
            _isBinderAlive.value = true
            _isPermissionGranted.value = true
            _shouldShowRationale.value = false
            _status.value = ShizukuBridgeStatus.AUTHORIZED
            AdbShellExecutor.onShizukuPermissionResolved(granted = true, context = appContext)
            return true
        }
        return try {
            val invoker = requestPermissionInvokerOverride
            if (invoker != null) {
                invoker(requestCode)
            } else {
                Shizuku.requestPermission(requestCode)
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed requesting Shizuku permission: ${t.message}")
            false
        }
    }

    private fun tryAutoRequestPermissionOnce() {
        if (!autoRequestPermissionOnBinder) return
        if (!isRunning() || isPreV11()) return
        if (isPermissionGrantedNow()) return
        if (shouldShowRequestPermissionRationaleNow()) return
        if (hasAutoRequestedForSession.compareAndSet(false, true)) {
            requestPermission(REQUEST_CODE)
        }
    }

    internal fun onBinderReceived() {
        Log.i(TAG, "Shizuku binder received")
        _isBinderAlive.value = true
        val granted = isPermissionGrantedNow()
        if (granted) {
            _isPermissionGranted.value = true
            _shouldShowRationale.value = false
            _status.value = ShizukuBridgeStatus.AUTHORIZED
            hasAutoRequestedForSession.set(false)
            AdbShellExecutor.onShizukuPermissionResolved(granted = true, context = appContext)
        } else {
            val rationale = shouldShowRequestPermissionRationaleNow()
            _isPermissionGranted.value = false
            _shouldShowRationale.value = rationale
            _status.value = if (rationale) {
                ShizukuBridgeStatus.RATIONALE_REQUIRED
            } else {
                ShizukuBridgeStatus.PERMISSION_REQUIRED
            }
            AdbShellExecutor.refreshEngineStatus(appContext)
            if (!rationale) {
                tryAutoRequestPermissionOnce()
            }
        }
    }

    internal fun onBinderDead() {
        Log.w(TAG, "Shizuku binder died")
        hasAutoRequestedForSession.set(false)
        _isBinderAlive.value = false
        _isPermissionGranted.value = false
        _shouldShowRationale.value = false
        _status.value = if (isShizukuInstalled(appContext)) {
            ShizukuBridgeStatus.BINDER_DEAD
        } else {
            ShizukuBridgeStatus.NOT_INSTALLED
        }
        AdbShellExecutor.onShizukuPermissionResolved(granted = false, context = appContext)
    }

    internal fun onPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode != REQUEST_CODE && requestCode != 4201) {
            return
        }
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        _lastPermissionResult.value = grantResult
        _isBinderAlive.value = true
        _isPermissionGranted.value = granted
        val rationale = if (!granted) shouldShowRequestPermissionRationaleNow() else false
        _shouldShowRationale.value = rationale
        _status.value = when {
            granted -> ShizukuBridgeStatus.AUTHORIZED
            rationale -> ShizukuBridgeStatus.RATIONALE_REQUIRED
            else -> ShizukuBridgeStatus.PERMISSION_REQUIRED
        }
        Log.i(TAG, "Shizuku permission result (req=$requestCode): granted=$granted")
        AdbShellExecutor.onShizukuPermissionResolved(granted = granted, context = appContext)
    }
}
