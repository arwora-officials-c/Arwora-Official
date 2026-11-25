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
    RATIONALE_REQUIRED("Permission Denied"),
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
    private val permissionDeniedInSession = AtomicBoolean(false)
    private val pendingRequestCodes: MutableSet<Int> = java.util.Collections.synchronizedSet(
        mutableSetOf(REQUEST_CODE, 4201)
    )

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

    private val _isRemoteBinderReady = MutableStateFlow(false)
    val isRemoteBinderReady: StateFlow<Boolean> = _isRemoteBinderReady.asStateFlow()

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
        if (listenersRegistered.get()) return
        synchronized(this) {
            if (listenersRegistered.get()) return
            runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
            }.onSuccess {
                listenersRegistered.set(true)
            }.onFailure { err ->
                // Clean up any partial listener attachment and keep unregistered so later calls can retry
                runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
                runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
                runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
                listenersRegistered.set(false)
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
        permissionDeniedInSession.set(false)
        _isRemoteBinderReady.value = false
    }

    internal fun resetTestOverrides() {
        binderProbeOverride = null
        permissionProbeOverride = null
        rationaleProbeOverride = null
        preV11ProbeOverride = null
        installedProbeOverride = null
        requestPermissionInvokerOverride = null
        hasAutoRequestedForSession.set(false)
        permissionDeniedInSession.set(false)
        autoRequestPermissionOnBinder = false
        _lastPermissionResult.value = null
        _isRemoteBinderReady.value = false
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

        registerListenersIfNeeded()

        val installed = isShizukuInstalled(ctx)
        val binderAlive = isRunning()
        val granted = if (binderAlive) isPermissionGrantedNow() else false
        val rationale = if (binderAlive && !granted) shouldShowRequestPermissionRationaleNow() else false

        if (granted || !binderAlive) {
            hasAutoRequestedForSession.set(false)
            permissionDeniedInSession.set(false)
        }

        val resolved = when {
            binderAlive && granted -> ShizukuBridgeStatus.AUTHORIZED
            binderAlive && (rationale || permissionDeniedInSession.get()) -> ShizukuBridgeStatus.RATIONALE_REQUIRED
            binderAlive -> ShizukuBridgeStatus.PERMISSION_REQUIRED
            installed -> ShizukuBridgeStatus.BINDER_DEAD
            else -> ShizukuBridgeStatus.NOT_INSTALLED
        }

        _isInstalled.value = installed
        _isBinderAlive.value = binderAlive
        _isPermissionGranted.value = granted
        _shouldShowRationale.value = rationale
        _isRemoteBinderReady.value = if (binderAlive && granted) checkRemoteBinderHealth() else false
        _status.value = resolved
        ShizukuBridgeManager.syncFromBridgeStatus(resolved)
        AdbShellExecutor.syncFromShizukuState(binderAlive = binderAlive, granted = granted, context = ctx)
        return resolved
    }

    /**
     * Verifies and initializes remote binder readiness once Shizuku permission is granted.
     */
    fun verifyRemoteBinderConnection(): Boolean {
        val binderAlive = isRunning()
        val granted = if (binderAlive) isPermissionGrantedNow() else false
        val healthy = binderAlive && granted && checkRemoteBinderHealth()
        _isRemoteBinderReady.value = healthy
        return healthy
    }

    private fun checkRemoteBinderHealth(): Boolean {
        if (binderProbeOverride != null || AdbShellExecutor.shizukuBinderProbeOverride != null) {
            return true
        }
        return try {
            val binder = Shizuku.getBinder()
            binder == null || binder.isBinderAlive
        } catch (_: Throwable) {
            true
        }
    }

    fun requestPermission(requestCode: Int = REQUEST_CODE): Boolean {
        registerListenersIfNeeded()
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
            _isRemoteBinderReady.value = checkRemoteBinderHealth()
            _status.value = ShizukuBridgeStatus.AUTHORIZED
            ShizukuBridgeManager.syncFromBridgeStatus(ShizukuBridgeStatus.AUTHORIZED)
            AdbShellExecutor.onShizukuPermissionResolved(granted = true, context = appContext)
            return true
        }
        pendingRequestCodes.add(requestCode)
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
            permissionDeniedInSession.set(false)
            _isPermissionGranted.value = true
            _shouldShowRationale.value = false
            _isRemoteBinderReady.value = checkRemoteBinderHealth()
            _status.value = ShizukuBridgeStatus.AUTHORIZED
            ShizukuBridgeManager.syncFromBridgeStatus(ShizukuBridgeStatus.AUTHORIZED)
            hasAutoRequestedForSession.set(false)
            AdbShellExecutor.onShizukuPermissionResolved(granted = true, context = appContext)
        } else {
            val rationale = shouldShowRequestPermissionRationaleNow()
            val denied = rationale || permissionDeniedInSession.get()
            _isPermissionGranted.value = false
            _shouldShowRationale.value = rationale
            _isRemoteBinderReady.value = false
            val nextStatus = if (denied) {
                ShizukuBridgeStatus.RATIONALE_REQUIRED
            } else {
                ShizukuBridgeStatus.PERMISSION_REQUIRED
            }
            _status.value = nextStatus
            ShizukuBridgeManager.syncFromBridgeStatus(nextStatus)
            AdbShellExecutor.syncFromShizukuState(binderAlive = true, granted = false, context = appContext)
            if (!denied) {
                tryAutoRequestPermissionOnce()
            }
        }
    }

    internal fun onBinderDead() {
        Log.w(TAG, "Shizuku binder died")
        hasAutoRequestedForSession.set(false)
        permissionDeniedInSession.set(false)
        _isBinderAlive.value = false
        _isPermissionGranted.value = false
        _isRemoteBinderReady.value = false
        _shouldShowRationale.value = false
        val deadStatus = if (isShizukuInstalled(appContext)) {
            ShizukuBridgeStatus.BINDER_DEAD
        } else {
            ShizukuBridgeStatus.NOT_INSTALLED
        }
        _status.value = deadStatus
        ShizukuBridgeManager.syncFromBridgeStatus(deadStatus)
        AdbShellExecutor.onShizukuPermissionResolved(granted = false, context = appContext)
    }

    internal fun onPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode !in pendingRequestCodes && requestCode != REQUEST_CODE && requestCode != 4201) {
            return
        }
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        val binderAlive = if (granted) true else isRunning()
        if (granted) {
            permissionDeniedInSession.set(false)
            hasAutoRequestedForSession.set(false)
        } else if (binderAlive) {
            permissionDeniedInSession.set(true)
        } else {
            permissionDeniedInSession.set(false)
        }
        _lastPermissionResult.value = grantResult
        _isBinderAlive.value = binderAlive
        _isPermissionGranted.value = granted && binderAlive
        val rationale = if (binderAlive && !granted) shouldShowRequestPermissionRationaleNow() else false
        _shouldShowRationale.value = rationale
        _isRemoteBinderReady.value = if (granted && binderAlive) checkRemoteBinderHealth() else false
        val resolvedStatus = when {
            granted && binderAlive -> ShizukuBridgeStatus.AUTHORIZED
            binderAlive && (rationale || permissionDeniedInSession.get()) -> ShizukuBridgeStatus.RATIONALE_REQUIRED
            binderAlive -> ShizukuBridgeStatus.PERMISSION_REQUIRED
            isShizukuInstalled(appContext) -> ShizukuBridgeStatus.BINDER_DEAD
            else -> ShizukuBridgeStatus.NOT_INSTALLED
        }
        _status.value = resolvedStatus
        ShizukuBridgeManager.syncFromBridgeStatus(resolvedStatus)
        Log.i(TAG, "Shizuku permission result (req=$requestCode): granted=$granted binderAlive=$binderAlive")
        AdbShellExecutor.onShizukuPermissionResolved(granted = granted && binderAlive, context = appContext)
    }
}
