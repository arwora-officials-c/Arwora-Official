package com.arwora.utils

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuUiStatus(val badgeText: String) {
    ACTIVE_CONNECTED("Active / Connected"),
    PERMISSION_REQUIRED("Permission Required"),
    SERVICE_STOPPED("Shizuku Service Stopped")
}

enum class ShizukuConnectOutcome {
    ALREADY_CONNECTED,
    PERMISSION_REQUESTED,
    SERVICE_STOPPED_OPENED_APP,
    SERVICE_STOPPED_UNAVAILABLE
}

/**
 * Lifecycle manager for Shizuku binder connection, permission authorization,
 * and remote shell binder initialization.
 *
 * Bridges [ShizukuBridge] (`OnBinderReceivedListener`, `OnBinderDeadListener`,
 * `OnRequestPermissionResultListener`) with [AdbShellExecutor] and the System Dashboard UI.
 */
object ShizukuBridgeManager {
    const val REQUEST_CODE = ShizukuBridge.REQUEST_CODE

    private val _connectionStatus = MutableStateFlow(ShizukuUiStatus.SERVICE_STOPPED)
    val connectionStatus: StateFlow<ShizukuUiStatus> = _connectionStatus.asStateFlow()

    private val _isRemoteBinderInitialized = MutableStateFlow(false)
    val isRemoteBinderInitialized: StateFlow<Boolean> = _isRemoteBinderInitialized.asStateFlow()

    /**
     * Initializes Shizuku binder & permission listeners idempotently and queries live state.
     */
    fun initialize(context: Context? = null, autoRequestIfNeeded: Boolean = false): ShizukuUiStatus {
        ShizukuBridge.init(context, autoRequestIfNeeded = autoRequestIfNeeded)
        return checkShizukuStatus(context)
    }

    /**
     * Queries live [Shizuku.pingBinder] and [Shizuku.checkSelfPermission] == [PackageManager.PERMISSION_GRANTED],
     * initializes remote binder readiness if granted, and returns the synchronized [ShizukuUiStatus].
     */
    fun checkShizukuStatus(context: Context? = null): ShizukuUiStatus {
        val bridgeStatus = ShizukuBridge.refreshState(context)
        if (bridgeStatus == ShizukuBridgeStatus.AUTHORIZED) {
            _isRemoteBinderInitialized.value = ShizukuBridge.verifyRemoteBinderConnection()
        } else {
            _isRemoteBinderInitialized.value = false
        }
        return syncFromBridgeStatus(bridgeStatus)
    }

    /**
     * Handles Shizuku binder death cleanly so UI and shell executor reset immediately.
     */
    fun handleBinderDeath() {
        _isRemoteBinderInitialized.value = false
        ShizukuBridge.onBinderDead()
        _connectionStatus.value = ShizukuUiStatus.SERVICE_STOPPED
    }

    /**
     * Handles the "Connect Shizuku" user CTA click:
     * 1. Checks live binder via [Shizuku.pingBinder] (`ShizukuBridge.isRunning`).
     * 2. If running & granted -> initializes remote binder and marks Active / Connected.
     * 3. If running & ungranted -> invokes `Shizuku.requestPermission(REQUEST_CODE)`.
     * 4. If service stopped -> opens Shizuku / store to start the service.
     */
    fun connectShizuku(context: Context): ShizukuConnectOutcome {
        val current = checkShizukuStatus(context)
        if (current == ShizukuUiStatus.ACTIVE_CONNECTED) {
            return ShizukuConnectOutcome.ALREADY_CONNECTED
        }

        if (ShizukuBridge.isRunning()) {
            val requested = ShizukuBridge.requestPermission(REQUEST_CODE)
            if (requested) {
                if (ShizukuBridge.isPermissionGrantedNow()) {
                    checkShizukuStatus(context)
                    return ShizukuConnectOutcome.ALREADY_CONNECTED
                }
                return ShizukuConnectOutcome.PERMISSION_REQUESTED
            }
        }

        val opened = AdbShellExecutor.openOrInstallShizuku(context)
        return if (opened) {
            ShizukuConnectOutcome.SERVICE_STOPPED_OPENED_APP
        } else {
            ShizukuConnectOutcome.SERVICE_STOPPED_UNAVAILABLE
        }
    }

    fun cleanup() {
        _isRemoteBinderInitialized.value = false
        ShizukuBridge.cleanup()
        _connectionStatus.value = ShizukuUiStatus.SERVICE_STOPPED
    }

    internal fun syncFromBridgeStatus(bridgeStatus: ShizukuBridgeStatus): ShizukuUiStatus {
        val uiStatus = when (bridgeStatus) {
            ShizukuBridgeStatus.AUTHORIZED -> ShizukuUiStatus.ACTIVE_CONNECTED
            ShizukuBridgeStatus.PERMISSION_REQUIRED,
            ShizukuBridgeStatus.RATIONALE_REQUIRED -> ShizukuUiStatus.PERMISSION_REQUIRED
            ShizukuBridgeStatus.BINDER_DEAD,
            ShizukuBridgeStatus.NOT_INSTALLED -> ShizukuUiStatus.SERVICE_STOPPED
        }
        _isRemoteBinderInitialized.value =
            bridgeStatus == ShizukuBridgeStatus.AUTHORIZED && ShizukuBridge.isRemoteBinderReady.value
        _connectionStatus.value = uiStatus
        return uiStatus
    }
}
