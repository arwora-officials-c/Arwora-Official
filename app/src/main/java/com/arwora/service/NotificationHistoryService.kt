package com.arwora.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.arwora.NameGenApplication
import com.arwora.data.db.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationHistoryService : NotificationListenerService() {
    
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val packageName = it.packageName ?: return
            val notification = it.notification ?: return
            val title = notification.extras?.getCharSequence("android.title")?.toString() ?: "Unknown"
            val text = notification.extras?.getCharSequence("android.text")?.toString() ?: ""
            
            scope.launch {
                try {
                    // Check if this is a Wireless Debugging pairing notification from Android system or Settings
                    if (packageName == "com.android.settings" || title.contains("Wireless debugging", true) || text.contains("pairing code", true) || title.contains("pairing", true) || text.contains("pairing", true)) {
                        val combined = "$title $text"
                        val code = com.arwora.utils.WirelessPairingManager.extractPairingCode(combined)
                        val port = com.arwora.utils.WirelessPairingManager.extractPairingPort(combined)
                        if (code != null) {
                            val prefs = getSharedPreferences("wireless_pairing_cache", MODE_PRIVATE)
                            prefs.edit().putString("latest_pairing_code", code).putLong("timestamp", System.currentTimeMillis()).apply()
                            android.util.Log.i("NotificationHistoryService", "Captured Wireless Debugging Pairing Code: $code, Port: $port")
                            com.arwora.utils.WirelessPairingManager.onPairingNotificationCaptured(this@NotificationHistoryService, code, port)
                        }
                    }

                    val app = application as? NameGenApplication ?: return@launch
                    val db = app.database
                    db.notificationDao().insertNotification(
                        NotificationEntity(
                            packageName = packageName,
                            appName = getAppName(packageName),
                            title = title,
                            text = text,
                            timestamp = System.currentTimeMillis(),
                            isDismissed = false
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("NotificationHistoryService", "Failed to store notification", e)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            val packageName = it.packageName ?: return
            scope.launch {
                try {
                    val app = application as? NameGenApplication ?: return@launch
                    val db = app.database
                    db.notificationDao().markDismissedByPackage(packageName)
                } catch (e: Exception) {
                    android.util.Log.e("NotificationHistoryService", "Failed to mark notification dismissed", e)
                }
            }
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }
    }
}
