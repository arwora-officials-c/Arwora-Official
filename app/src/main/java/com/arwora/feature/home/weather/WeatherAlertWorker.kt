package com.arwora.feature.home.weather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arwora.MainActivity
import com.arwora.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WeatherAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = WeatherRepository(applicationContext)
        val bundle = repo.fetchProWeather().getOrNull()
            ?: repo.loadCachedProWeather()
            ?: return Result.success()

        val settings = repo.getAlertSettingsSuspend()
        val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val triggered = WeatherAdvisoryEngine.evaluateSmartAlerts(bundle, settings, nowHour)
        postEligibleNotifications(applicationContext, triggered, forceAll = false)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "arwora_smart_weather_alerts"
        const val CHANNEL_ALERTS = "arwora_weather_alerts"
        const val CHANNEL_EXTREME = "arwora_weather_extreme"
        private const val COOLDOWN_MILLIS = 3 * 60 * 60 * 1000L

        fun schedulePeriodic(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<WeatherAlertWorker>(30, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (mgr.getNotificationChannel(CHANNEL_ALERTS) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ALERTS,
                        "Smart Weather Alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Brishti, Kapor Shukano, Bazar, and Bike Ride advisories"
                    }
                )
            }
            if (mgr.getNotificationChannel(CHANNEL_EXTREME) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_EXTREME,
                        "Extreme Weather Warnings",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Storm, heatwave, and severe cold alerts"
                    }
                )
            }
        }

        fun postEligibleNotifications(
            context: Context,
            alerts: List<TriggeredWeatherAlert>,
            forceAll: Boolean = false
        ): Int {
            if (alerts.isEmpty()) return 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return 0
            }

            ensureNotificationChannels(context)
            val prefs = context.getSharedPreferences(PrefsWeatherCacheStore.PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                8810,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            var posted = 0
            alerts.forEach { alert ->
                val cooldownKey = "last_alert_${alert.kind.name}"
                val lastPosted = prefs.getLong(cooldownKey, 0L)
                if (!forceAll && (now - lastPosted) < COOLDOWN_MILLIS) return@forEach

                val channelId = if (alert.isHighPriority) CHANNEL_EXTREME else CHANNEL_ALERTS
                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(alert.title)
                    .setContentText(alert.message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
                    .setPriority(
                        if (alert.isHighPriority) NotificationCompat.PRIORITY_HIGH
                        else NotificationCompat.PRIORITY_DEFAULT
                    )
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                runCatching {
                    NotificationManagerCompat.from(context).notify(8900 + alert.kind.ordinal, notification)
                    prefs.edit().putLong(cooldownKey, now).apply()
                    posted++
                }
            }
            return posted
        }
    }
}
