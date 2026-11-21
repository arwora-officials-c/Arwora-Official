package com.arwora.utils

import android.content.Context
import android.provider.Settings
import android.util.Log

object AnimationTweakManager {
    private const val TAG = "AnimationTweakManager"

    fun isAdbAvailable(): Boolean = AdbShellExecutor.isPrivilegedShellReady()

    fun setAnimationScales(context: Context, scale: Float): Boolean {
        val cmd1 = "settings put global window_animation_scale $scale"
        val cmd2 = "settings put global transition_animation_scale $scale"
        val cmd3 = "settings put global animator_duration_scale $scale"

        // 1. Direct Secure Settings Write (if WRITE_SECURE_SETTINGS is granted)
        if (DnsSecureSettingsHelper.hasWriteSecureSettingsPermission(context)) {
            try {
                Settings.Global.putFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, scale)
                Settings.Global.putFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, scale)
                Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set animation scales via Secure Settings", e)
            }
        }

        // 2. Dual-Engine Privilege Bridge (Shizuku or Native Wireless ADB)
        AdbShellExecutor.init(context)
        if (AdbShellExecutor.isPrivilegedShellReady(context)) {
            val s1 = AdbShellExecutor.executeBlocking(cmd1, context = context).success
            val s2 = AdbShellExecutor.executeBlocking(cmd2, context = context).success
            val s3 = AdbShellExecutor.executeBlocking(cmd3, context = context).success
            if (s1 && s2 && s3) {
                return true
            }
        }

        // 3. Fallback to root (su) if available
        if (DnsSecureSettingsHelper.isRootAvailable()) {
            try {
                val p1 = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd1))
                val p2 = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd2))
                val p3 = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd3))
                if (p1.waitFor() == 0 && p2.waitFor() == 0 && p3.waitFor() == 0) {
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set animation scales via Root", e)
            }
        }

        return false
    }

    fun getCurrentAnimationScale(context: Context): Float {
        try {
            return Settings.Global.getFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1.0f)
        } catch (_: Exception) {
        }

        if (AdbShellExecutor.isPrivilegedShellReady(context)) {
            val res = AdbShellExecutor.executeBlocking("settings get global window_animation_scale", context = context)
            if (res.success && res.output.isNotBlank() && res.output != "null") {
                return res.output.trim().toFloatOrNull() ?: 1.0f
            }
        }

        return 1.0f
    }
}
