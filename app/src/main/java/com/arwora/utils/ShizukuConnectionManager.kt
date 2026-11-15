package com.arwora.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import rikka.shizuku.Shizuku

enum class ShizukuConnectionState {
    NOT_INSTALLED,
    INACTIVE,
    PERMISSION_REQUIRED,
    CONNECTED
}

data class ShizukuListenerRegistration(
    val binderReceivedListener: Shizuku.OnBinderReceivedListener,
    val binderDeadListener: Shizuku.OnBinderDeadListener,
    val permissionResultListener: Shizuku.OnRequestPermissionResultListener
)

object ShizukuConnectionManager {
    private const val TAG = "ShizukuConnectionMgr"
    const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    const val SHIZUKU_PLAY_STORE_MARKET_URI = "market://details?id=$SHIZUKU_PACKAGE_NAME"
    const val SHIZUKU_PLAY_STORE_WEB_URI = "https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE_NAME"
    const val DEFAULT_REQUEST_CODE = 1001

    /**
     * Deterministic state resolution from probe flags.
     * Note: If the Shizuku binder is already alive (including privileged forks/Sui),
     * connection/permission takes precedence over package launcher visibility.
     */
    internal fun resolveConnectionState(
        isInstalled: Boolean,
        isBinderAlive: Boolean,
        hasPermission: Boolean
    ): ShizukuConnectionState {
        return when {
            isBinderAlive && hasPermission -> ShizukuConnectionState.CONNECTED
            isBinderAlive -> ShizukuConnectionState.PERMISSION_REQUIRED
            !isInstalled -> ShizukuConnectionState.NOT_INSTALLED
            else -> ShizukuConnectionState.INACTIVE
        }
    }

    /**
     * Step 1: Checks if Shizuku (moe.shizuku.privileged.api) is installed via PackageManager.
     */
    fun isShizukuInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(SHIZUKU_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking Shizuku package info", e)
            try {
                pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME) != null
            } catch (_: Throwable) {
                false
            }
        }
    }

    /**
     * Step 2: Checks if the Shizuku binder service is currently running / active.
     */
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks if the Shizuku binder is running AND permission is granted to this app.
     */
    fun hasShizukuPermission(): Boolean {
        if (!isShizukuRunning()) return false
        return try {
            if (Shizuku.getLatestServiceVersion() >= 11) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun getConnectionState(context: Context): ShizukuConnectionState {
        val binderAlive = isShizukuRunning()
        if (binderAlive) {
            return resolveConnectionState(
                isInstalled = true,
                isBinderAlive = true,
                hasPermission = hasShizukuPermission()
            )
        }
        val installed = isShizukuInstalled(context)
        return resolveConnectionState(
            isInstalled = installed,
            isBinderAlive = false,
            hasPermission = false
        )
    }

    /**
     * Redirects user to Google Play Store (market:// first, then HTTPS web fallback) to install Shizuku.
     */
    fun openPlayStoreForShizuku(context: Context, showToast: Boolean = true): Boolean {
        if (showToast) {
            Toast.makeText(
                context,
                "Shizuku app not installed. Opening Play Store...",
                Toast.LENGTH_SHORT
            ).show()
        }

        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_PLAY_STORE_MARKET_URI)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(marketIntent)
            return true
        } catch (_: ActivityNotFoundException) {
            // Fall back to HTTPS Play Store web link
        } catch (e: Exception) {
            Log.w(TAG, "Play Store app market:// launch failed, trying web URI", e)
        }

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_PLAY_STORE_WEB_URI)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(webIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Play Store link for Shizuku", e)
            if (showToast) {
                Toast.makeText(
                    context,
                    "Unable to open Play Store for Shizuku.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            false
        }
    }

    /**
     * Launches the installed Shizuku app (moe.shizuku.privileged.api) so the user can start the service.
     * Falls back to App Info settings or Play Store if no launchable activity is returned.
     */
    fun launchShizukuApp(context: Context, showToast: Boolean = true): Boolean {
        if (!isShizukuInstalled(context)) {
            return openPlayStoreForShizuku(context, showToast = showToast)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                if (showToast) {
                    Toast.makeText(
                        context,
                        "Shizuku service is inactive. Opening Shizuku...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                context.startActivity(launchIntent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed launching Shizuku app", e)
                openShizukuAppDetails(context, showToast)
            }
        }

        return openShizukuAppDetails(context, showToast)
    }

    private fun openShizukuAppDetails(context: Context, showToast: Boolean): Boolean {
        return try {
            val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", SHIZUKU_PACKAGE_NAME, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (showToast) {
                Toast.makeText(
                    context,
                    "Open Shizuku to start the service.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            context.startActivity(detailsIntent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed opening Shizuku app details", e)
            openPlayStoreForShizuku(context, showToast = showToast)
        }
    }

    /**
     * Smart "Connect" handler:
     * Step 1: Verify if moe.shizuku.privileged.api is installed (or binder already active).
     *         If NOT installed -> open Play Store link for moe.shizuku.privileged.api.
     * Step 2: Check Shizuku.pingBinder().
     *         If installed but NOT running -> launch moe.shizuku.privileged.api directly.
     *         If running & active -> bind / verify or request Shizuku permission immediately.
     */
    fun connect(
        context: Context,
        requestCode: Int = DEFAULT_REQUEST_CODE,
        showFeedback: Boolean = true,
        onConnected: () -> Unit = {},
        onPermissionRequested: () -> Unit = {}
    ): ShizukuConnectionState {
        val binderAlive = isShizukuRunning()

        // Step 1: Installation check (if binder is not already active)
        if (!binderAlive && !isShizukuInstalled(context)) {
            openPlayStoreForShizuku(context, showToast = showFeedback)
            return ShizukuConnectionState.NOT_INSTALLED
        }

        // Step 2: Service running check
        if (!binderAlive) {
            launchShizukuApp(context, showToast = showFeedback)
            return ShizukuConnectionState.INACTIVE
        }

        // Service is running: connect immediately if permitted, else request Shizuku permission
        if (hasShizukuPermission()) {
            if (showFeedback) {
                Toast.makeText(
                    context,
                    "Shizuku connected and ready.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            onConnected()
            return ShizukuConnectionState.CONNECTED
        }

        return try {
            Shizuku.requestPermission(requestCode)
            onPermissionRequested()
            ShizukuConnectionState.PERMISSION_REQUIRED
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
            if (showFeedback) {
                Toast.makeText(
                    context,
                    "Shizuku binder unavailable. Opening Shizuku...",
                    Toast.LENGTH_SHORT
                ).show()
            }
            launchShizukuApp(context, showToast = false)
            ShizukuConnectionState.INACTIVE
        }
    }

    fun registerListeners(
        onBinderChanged: () -> Unit,
        onPermissionResult: (requestCode: Int, granted: Boolean) -> Unit
    ): ShizukuListenerRegistration {
        val binderReceived = Shizuku.OnBinderReceivedListener { onBinderChanged() }
        val binderDead = Shizuku.OnBinderDeadListener { onBinderChanged() }
        val permListener = Shizuku.OnRequestPermissionResultListener { reqCode, grantResult ->
            onPermissionResult(reqCode, grantResult == PackageManager.PERMISSION_GRANTED)
        }
        try {
            Shizuku.addBinderReceivedListener(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to register Shizuku listeners", e)
        }
        return ShizukuListenerRegistration(
            binderReceivedListener = binderReceived,
            binderDeadListener = binderDead,
            permissionResultListener = permListener
        )
    }

    fun unregisterListeners(registration: ShizukuListenerRegistration?) {
        if (registration == null) return
        try {
            Shizuku.removeBinderReceivedListener(registration.binderReceivedListener)
            Shizuku.removeBinderDeadListener(registration.binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(registration.permissionResultListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to unregister Shizuku listeners", e)
        }
    }
}
