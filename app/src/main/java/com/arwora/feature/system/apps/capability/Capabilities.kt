package com.arwora.feature.system.apps.capability

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.arwora.deviceadmin.AppDeviceAdminReceiver
import com.arwora.utils.NativeAdbManager

enum class AppManagerCapability {
    WIRELESS_ADB,
    DEVICE_OWNER,
    NONE
}

object Capabilities {
    fun detectCapability(context: Context): AppManagerCapability {
        // 1. Check built-in Wireless ADB
        try {
            NativeAdbManager.refreshConnectionStatus(context)
            if (NativeAdbManager.isAdbReady()) {
                return AppManagerCapability.WIRELESS_ADB
            }
        } catch (_: Throwable) {}

        // 2. Check Device Owner / Profile Owner
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            if (dpm != null) {
                if (dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)) {
                    return AppManagerCapability.DEVICE_OWNER
                }
            }
        } catch (_: Throwable) {}

        return AppManagerCapability.NONE
    }

    fun isAdbConnected(context: Context): Boolean {
        NativeAdbManager.refreshConnectionStatus(context)
        return NativeAdbManager.isAdbReady()
    }

    suspend fun connectWirelessAdb(context: Context): Pair<Boolean, String> {
        return NativeAdbManager.connectAutomatically(context)
    }

    fun getAdminComponent(context: Context): ComponentName {
        return ComponentName(context, AppDeviceAdminReceiver::class.java)
    }
}
