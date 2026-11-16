package com.arwora.utils

import android.content.Context
import android.provider.Settings
import android.util.Log

object RefreshRateManager {
    private const val TAG = "RefreshRateManager"

    fun isAdbAvailable(): Boolean = NativeAdbManager.isAdbReady()

    fun setRefreshRates(context: Context, peak: Float?, min: Float?): Boolean {
        val cmds = mutableListOf<String>()
        if (peak != null) {
            cmds.add("settings put system peak_refresh_rate $peak")
        } else {
            cmds.add("settings delete system peak_refresh_rate")
        }

        if (min != null) {
            cmds.add("settings put system min_refresh_rate $min")
        } else {
            cmds.add("settings delete system min_refresh_rate")
        }

        // 1. Native Wireless ADB execution
        NativeAdbManager.init(context)
        if (NativeAdbManager.isAdbReady()) {
            val allSuccess = cmds.all { NativeAdbManager.executeCommandBlocking(it).success }
            if (allSuccess) {
                return true
            }
        }

        // 2. Try Secure Settings / WRITE_SETTINGS
        try {
            val cr = context.contentResolver
            if (peak != null) {
                Settings.System.putFloat(cr, "peak_refresh_rate", peak)
            } else {
                Settings.System.putString(cr, "peak_refresh_rate", null)
            }
            if (min != null) {
                Settings.System.putFloat(cr, "min_refresh_rate", min)
            } else {
                Settings.System.putString(cr, "min_refresh_rate", null)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update refresh rates via System Settings", e)
        }

        // 3. Try Root (su)
        if (DnsSecureSettingsHelper.isRootAvailable()) {
            try {
                var allSuccess = true
                for (cmd in cmds) {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                    if (process.waitFor() != 0) {
                        allSuccess = false
                    }
                }
                if (allSuccess) {
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update refresh rates via Root", e)
            }
        }

        return false
    }

    fun getCurrentPeakRefreshRate(context: Context): Float {
        try {
            val valStr = Settings.System.getString(context.contentResolver, "peak_refresh_rate")
            if (!valStr.isNullOrEmpty()) {
                val parsed = valStr.toFloatOrNull()
                if (parsed != null && parsed > 0f) return parsed
            }
        } catch (_: Exception) {
        }

        if (NativeAdbManager.isAdbReady()) {
            val res = NativeAdbManager.executeCommandBlocking("settings get system peak_refresh_rate")
            if (res.success && res.output.isNotBlank() && res.output != "null") {
                return res.output.trim().toFloatOrNull() ?: 120.0f
            }
        }

        return 120.0f
    }

    fun getCurrentMinRefreshRate(context: Context): Float {
        try {
            val valStr = Settings.System.getString(context.contentResolver, "min_refresh_rate")
            if (!valStr.isNullOrEmpty()) {
                val parsed = valStr.toFloatOrNull()
                if (parsed != null && parsed > 0f) return parsed
            }
        } catch (_: Exception) {
        }

        if (NativeAdbManager.isAdbReady()) {
            val res = NativeAdbManager.executeCommandBlocking("settings get system min_refresh_rate")
            if (res.success && res.output.isNotBlank() && res.output != "null") {
                return res.output.trim().toFloatOrNull() ?: 60.0f
            }
        }

        return 60.0f
    }
}
