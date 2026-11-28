package com.arwora.feature.chat.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import com.arwora.feature.chat.model.ChatHubSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Persists standalone Chat Hub settings (Ghost Mode, Screenshot Alerts, DND Night Schedule,
 * Data Saver / Low Battery auto-compression, Themes, Wallpapers, and Custom Ringtones).
 */
class ChatPreferencesStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ChatHubSettings> = _settings.asStateFlow()

    private fun loadSettings(): ChatHubSettings {
        return ChatHubSettings(
            ghostModeEnabled = prefs.getBoolean(KEY_GHOST_MODE, false),
            screenshotAlertsEnabled = prefs.getBoolean(KEY_SCREENSHOT_ALERTS, true),
            flagSecureLockedChats = prefs.getBoolean(KEY_FLAG_SECURE_LOCKED, true),
            dndEnabled = prefs.getBoolean(KEY_DND_ENABLED, false),
            dndStartHour = prefs.getInt(KEY_DND_START_HOUR, 22).coerceIn(0, 23),
            dndStartMinute = prefs.getInt(KEY_DND_START_MIN, 0).coerceIn(0, 59),
            dndEndHour = prefs.getInt(KEY_DND_END_HOUR, 7).coerceIn(0, 23),
            dndEndMinute = prefs.getInt(KEY_DND_END_MIN, 0).coerceIn(0, 59),
            dataSaverMode = prefs.getBoolean(KEY_DATA_SAVER, false),
            lowBatteryAutoCompress = prefs.getBoolean(KEY_LOW_BATTERY_COMPRESS, true),
            disableHeavyAutoDownload = prefs.getBoolean(KEY_DISABLE_HEAVY_AUTODL, false),
            defaultThemeId = prefs.getString(KEY_DEFAULT_THEME, "indigo_pulse") ?: "indigo_pulse",
            defaultWallpaperId = prefs.getString(KEY_DEFAULT_WALLPAPER, "midnight_obsidian") ?: "midnight_obsidian",
            defaultSoundUri = prefs.getString(KEY_DEFAULT_SOUND_URI, null),
            defaultSoundName = prefs.getString(KEY_DEFAULT_SOUND_NAME, "System Default") ?: "System Default"
        )
    }

    fun update(transform: (ChatHubSettings) -> ChatHubSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_GHOST_MODE, next.ghostModeEnabled)
            .putBoolean(KEY_SCREENSHOT_ALERTS, next.screenshotAlertsEnabled)
            .putBoolean(KEY_FLAG_SECURE_LOCKED, next.flagSecureLockedChats)
            .putBoolean(KEY_DND_ENABLED, next.dndEnabled)
            .putInt(KEY_DND_START_HOUR, next.dndStartHour)
            .putInt(KEY_DND_START_MIN, next.dndStartMinute)
            .putInt(KEY_DND_END_HOUR, next.dndEndHour)
            .putInt(KEY_DND_END_MIN, next.dndEndMinute)
            .putBoolean(KEY_DATA_SAVER, next.dataSaverMode)
            .putBoolean(KEY_LOW_BATTERY_COMPRESS, next.lowBatteryAutoCompress)
            .putBoolean(KEY_DISABLE_HEAVY_AUTODL, next.disableHeavyAutoDownload)
            .putString(KEY_DEFAULT_THEME, next.defaultThemeId)
            .putString(KEY_DEFAULT_WALLPAPER, next.defaultWallpaperId)
            .putString(KEY_DEFAULT_SOUND_URI, next.defaultSoundUri)
            .putString(KEY_DEFAULT_SOUND_NAME, next.defaultSoundName)
            .apply()
    }

    fun reload() {
        _settings.value = loadSettings()
    }

    fun clearForTests() {
        prefs.edit().clear().commit()
        _settings.value = ChatHubSettings()
    }

    fun isWithinDndSchedule(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val s = _settings.value
        if (!s.dndEnabled) return false
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = s.dndStartHour * 60 + s.dndStartMinute
        val endMinutes = s.dndEndHour * 60 + s.dndEndMinute
        return if (startMinutes == endMinutes) {
            true
        } else if (startMinutes < endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            // Overnight window e.g. 22:00 -> 07:00
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    fun isLowBatteryOrDataSaverActive(): Boolean {
        val s = _settings.value
        if (s.dataSaverMode) return true
        if (isSystemDataSaverEnabled()) return true
        if (s.lowBatteryAutoCompress && isBatteryLow()) return true
        return false
    }

    fun shouldBlockHeavyAutoDownload(): Boolean {
        val s = _settings.value
        return s.disableHeavyAutoDownload || isLowBatteryOrDataSaverActive()
    }

    private fun isBatteryLow(): Boolean {
        return runCatching {
            val status = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                (level * 100f / scale.toFloat()) <= 20f
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun isSystemDataSaverEnabled(): Boolean {
        return runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        }.getOrDefault(false)
    }

    companion object {
        private const val PREFS_NAME = "arwora_chat_hub_prefs"
        private const val KEY_GHOST_MODE = "ghost_mode_enabled"
        private const val KEY_SCREENSHOT_ALERTS = "screenshot_alerts_enabled"
        private const val KEY_FLAG_SECURE_LOCKED = "flag_secure_locked_chats"
        private const val KEY_DND_ENABLED = "dnd_enabled"
        private const val KEY_DND_START_HOUR = "dnd_start_hour"
        private const val KEY_DND_START_MIN = "dnd_start_min"
        private const val KEY_DND_END_HOUR = "dnd_end_hour"
        private const val KEY_DND_END_MIN = "dnd_end_min"
        private const val KEY_DATA_SAVER = "data_saver_mode"
        private const val KEY_LOW_BATTERY_COMPRESS = "low_battery_auto_compress"
        private const val KEY_DISABLE_HEAVY_AUTODL = "disable_heavy_auto_download"
        private const val KEY_DEFAULT_THEME = "default_theme_id"
        private const val KEY_DEFAULT_WALLPAPER = "default_wallpaper_id"
        private const val KEY_DEFAULT_SOUND_URI = "default_sound_uri"
        private const val KEY_DEFAULT_SOUND_NAME = "default_sound_name"
    }
}
