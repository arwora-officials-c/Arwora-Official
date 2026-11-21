package com.arwora.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast

object DnsSecureSettingsHelper {
    private const val TAG = "DnsSecureSettings"

    /**
     * Launches Android's native Private DNS settings with a direct intent or fallback sequence.
     */
    fun openPrivateDnsSettings(context: Context) {
        try {
            val intent = Intent("android.settings.PRIVATE_DNS_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open settings.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun isAdbAvailable(): Boolean = AdbShellExecutor.isPrivilegedShellReady()

    /**
     * Checks if the app has WRITE_SECURE_SETTINGS permission natively.
     */
    fun hasWriteSecureSettingsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if root access (su) is available.
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Attempts to update Private DNS settings using:
     * 1. Direct secure settings write (if WRITE_SECURE_SETTINGS is granted)
     * 2. Native Wireless ADB connection (NativeAdbManager)
     * 3. Root superuser (if su is available)
     */
    fun setPrivateDns(context: Context, mode: String, specifier: String?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(TAG, "Private DNS is not supported on this Android version (API ${Build.VERSION.SDK_INT})")
            return false
        }

        // 1. Direct Secure Settings Write (requires WRITE_SECURE_SETTINGS ADB permission)
        if (hasWriteSecureSettingsPermission(context)) {
            try {
                Settings.Global.putString(context.contentResolver, "private_dns_mode", mode)
                if (mode == "hostname" && specifier != null) {
                    Settings.Global.putString(context.contentResolver, "private_dns_specifier", specifier)
                }
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Direct Secure Settings write failed", e)
            }
        }

        // 2. Dual-Engine Privilege Bridge (Shizuku or Native Wireless ADB)
        AdbShellExecutor.init(context)
        if (AdbShellExecutor.isPrivilegedShellReady(context)) {
            val modeSuccess = AdbShellExecutor.executeBlocking("settings put global private_dns_mode $mode", context = context).success
            val specifierSuccess = if (mode == "hostname" && specifier != null) {
                AdbShellExecutor.executeBlocking("settings put global private_dns_specifier $specifier", context = context).success
            } else {
                true
            }
            if (modeSuccess && specifierSuccess) {
                return true
            }
        }

        // 3. Root SU fallback execution
        if (isRootAvailable()) {
            try {
                val modeCmd = "settings put global private_dns_mode $mode"
                val specifierCmd = if (mode == "hostname" && specifier != null) {
                    "settings put global private_dns_specifier $specifier"
                } else {
                    null
                }
                val modeSuccess = executeRootCommand(modeCmd)
                val specifierSuccess = if (specifierCmd != null) executeRootCommand(specifierCmd) else true
                if (modeSuccess && specifierSuccess) {
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Root command execution failed", e)
            }
        }

        return false
    }

    private fun executeRootCommand(command: String): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error executing root command: $command", e)
            false
        } finally {
            process?.destroy()
        }
    }
}
