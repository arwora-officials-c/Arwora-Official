package com.arwora.feature.home.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.arwora.MainActivity
import com.arwora.R
import org.json.JSONObject

class WeatherAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateSingleWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun requestWidgetUpdate(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, WeatherAppWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isNotEmpty()) {
                    ids.forEach { id -> updateSingleWidget(context, manager, id) }
                }
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PrefsWeatherCacheStore.PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString("last_weather_snapshot", null)
            val snapshot = raw?.let { runCatching { WeatherProParser.snapshotFromJson(JSONObject(it)) }.getOrNull() }

            val views = RemoteViews(context.packageName, R.layout.widget_weather_home)
            if (snapshot != null) {
                views.setTextViewText(R.id.widget_weather_city, snapshot.localityName)
                views.setTextViewText(R.id.widget_weather_temp, "${snapshot.temperatureCelsius}°C")
                views.setTextViewText(R.id.widget_weather_condition, snapshot.conditionLabel)
                views.setTextViewText(
                    R.id.widget_weather_metrics,
                    "H:${snapshot.highTempCelsius}° L:${snapshot.lowTempCelsius}° • ${snapshot.humidityPercent}% RH"
                )
            } else {
                views.setTextViewText(R.id.widget_weather_city, "Arwora Weather")
                views.setTextViewText(R.id.widget_weather_temp, "--°C")
                views.setTextViewText(R.id.widget_weather_condition, "Tap to sync live forecast")
                views.setTextViewText(R.id.widget_weather_metrics, "Pro Forecast & Alerts")
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                8801,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_weather_root, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
