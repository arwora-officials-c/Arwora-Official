package com.arwora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.arwora.feature.system.installassistant.data.SmartInstallAssistantManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Listens for newly installed and updated packages ([Intent.ACTION_PACKAGE_ADDED],
 * [Intent.ACTION_PACKAGE_REPLACED]), categorizes them, persists the setup checklist state,
 * and triggers a gentle local notification opening the Smart App Installation Assistant.
 */
class AppInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_REPLACED) {
            return
        }

        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName.isBlank() || packageName == context.packageName) return

        val isReplacingExtra = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        // Android broadcasts both PACKAGE_ADDED (with EXTRA_REPLACING=true) and PACKAGE_REPLACED on updates.
        // Skip the intermediate PACKAGE_ADDED when EXTRA_REPLACING=true so PACKAGE_REPLACED handles updates cleanly once.
        if (action == Intent.ACTION_PACKAGE_ADDED && isReplacingExtra) {
            return
        }

        if (!SmartInstallAssistantManager.shouldHandleBroadcast(packageName)) {
            return
        }

        val isUpdate = action == Intent.ACTION_PACKAGE_REPLACED
        val appContext = context.applicationContext
        val pendingResult: PendingResult? = try {
            goAsync()
        } catch (_: Exception) {
            null
        }

        receiverScope.launch {
            try {
                handlePackageEventSuspend(appContext, packageName, isUpdate)
            } finally {
                try {
                    pendingResult?.finish()
                } catch (_: Exception) {
                    // Already finished
                }
            }
        }
    }

    internal suspend fun handlePackageEventSuspend(
        context: Context,
        packageName: String,
        isUpdate: Boolean
    ) {
        try {
            withTimeoutOrNull(8_000L) {
                val record = SmartInstallAssistantManager.recordPackageEvent(
                    context = context,
                    packageName = packageName,
                    isUpdate = isUpdate
                )
                if (record != null) {
                    SmartInstallAssistantManager.postInstallNotification(context, record)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling package install broadcast for $packageName", e)
        }
    }

    companion object {
        private const val TAG = "AppInstallReceiver"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        }

        fun registerDynamic(context: Context): AppInstallReceiver {
            val receiver = AppInstallReceiver()
            val filter = createIntentFilter()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                context.applicationContext.registerReceiver(receiver, filter)
            }
            return receiver
        }

        fun unregisterDynamic(context: Context, receiver: AppInstallReceiver?) {
            if (receiver == null) return
            try {
                context.applicationContext.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
    }
}
