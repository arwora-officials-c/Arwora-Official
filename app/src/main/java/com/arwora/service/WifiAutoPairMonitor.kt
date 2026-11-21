package com.arwora.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import com.arwora.utils.EncryptedAdbCredentialStore
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.WirelessPairingManager

/**
 * Monitors device Wi-Fi connectivity using ConnectivityManager NetworkCapabilities.TRANSPORT_WIFI
 * so Mode 3 (Auto-Pair on Any Wi-Fi Connection) and previously paired Wireless ADB sessions
 * automatically resume whenever Wi-Fi connects or reconnects.
 */
object WifiAutoPairMonitor {
    private const val TAG = "WifiAutoPairMonitor"

    private var registeredCallback: ConnectivityManager.NetworkCallback? = null
    private var appContextRef: Context? = null

    fun syncWithPreference(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        updateCurrentWifiState(appContext)
        val creds = EncryptedAdbCredentialStore.loadState(appContext)
        val shouldMonitor = WirelessPairingManager.autoPairOnWifiEnabled.value ||
            (creds.isPaired && !creds.userStopped)

        if (shouldMonitor) {
            register(appContext)
        } else {
            unregister()
        }
    }

    fun isWifiCurrentlyConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed checking Wi-Fi transport state", e)
            false
        }
    }

    fun updateCurrentWifiState(context: Context) {
        WirelessPairingManager.updateWifiConnectivityState(isWifiCurrentlyConnected(context))
    }

    @Synchronized
    fun register(context: Context) {
        if (registeredCallback != null) return
        val appContext = context.applicationContext
        appContextRef = appContext

        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "TRANSPORT_WIFI available")
                    WirelessPairingManager.onWifiNetworkConnected(appContext)
                    val creds = EncryptedAdbCredentialStore.loadState(appContext)
                    if (creds.isPaired && !creds.userStopped && !NativeAdbManager.isAdbReady()) {
                        AdbConnectionService.scheduleReconnect(appContext, "network_callback_available")
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        WirelessPairingManager.updateWifiConnectivityState(true)
                    }
                }

                override fun onLost(network: Network) {
                    Log.i(TAG, "TRANSPORT_WIFI lost")
                    WirelessPairingManager.updateWifiConnectivityState(isWifiCurrentlyConnected(appContext))
                    NativeAdbManager.refreshConnectionStatus(appContext)
                }
            }

            cm.registerNetworkCallback(request, callback)
            registeredCallback = callback
            updateCurrentWifiState(appContext)
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to register TRANSPORT_WIFI NetworkCallback", e)
        }
    }

    @Synchronized
    fun unregister() {
        val cb = registeredCallback ?: return
        val ctx = appContextRef
        registeredCallback = null
        try {
            val cm = ctx?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
        } catch (e: Throwable) {
            Log.w(TAG, "Error unregistering NetworkCallback", e)
        }
    }
}

/**
 * BroadcastReceiver companion for Wi-Fi state changes, validating TRANSPORT_WIFI capabilities
 * and triggering automatic pairing or silent ADB session reconnection when paired.
 */
class WifiAutoPairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        if (action != WifiManager.NETWORK_STATE_CHANGED_ACTION &&
            action != ConnectivityManager.CONNECTIVITY_ACTION
        ) {
            return
        }

        val appCtx = context.applicationContext
        NativeAdbManager.init(appCtx)
        WirelessPairingManager.init(appCtx)
        val wifiConnected = WifiAutoPairMonitor.isWifiCurrentlyConnected(appCtx)
        WirelessPairingManager.updateWifiConnectivityState(wifiConnected)

        if (wifiConnected) {
            if (WirelessPairingManager.autoPairOnWifiEnabled.value) {
                WirelessPairingManager.onWifiNetworkConnected(appCtx)
            }
            val creds = EncryptedAdbCredentialStore.loadState(appCtx)
            if (creds.isPaired && !creds.userStopped && !NativeAdbManager.isAdbReady()) {
                AdbConnectionService.scheduleReconnect(appCtx, "connectivity_broadcast")
            }
        }
    }
}
