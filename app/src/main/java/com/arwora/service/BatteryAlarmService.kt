package com.arwora.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arwora.NameGenApplication
import com.arwora.R
import com.arwora.data.datastore.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BatteryAlarmService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRinging = false
    private var activeSoundName = ""
    private var ringingJob: Job? = null

    private var currentRingtone: android.media.Ringtone? = null
    private var toneGenerator: android.media.ToneGenerator? = null
    private var mediaPlayer: android.media.MediaPlayer? = null

    companion object {
        const val NOTIFICATION_ID_FOREGROUND = 4001
        const val NOTIFICATION_ID_ALARM = 4002

        const val ACTION_DISMISS_ALARM = "com.arwora.action.DISMISS_ALARM"
        const val ACTION_STOP_SERVICE = "com.arwora.action.STOP_SERVICE"
        const val ACTION_UPDATE_SETTINGS = "com.arwora.action.UPDATE_SETTINGS"

        private val _activeAlarmMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
        val activeAlarmMessage: kotlinx.coroutines.flow.StateFlow<String?> = _activeAlarmMessage.asStateFlow()

        fun setAlarmMessage(msg: String?) {
            _activeAlarmMessage.value = msg
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Create notification channel
        val channel = NotificationChannel("battery_alarm", "Battery Alarm & Monitor", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "Urgent alerts for high and low battery limits"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        serviceScope.launch {
            com.arwora.feature.system.battery.service.BatteryMonitorManager.batteryLevel.collect { level ->
                val charging = com.arwora.feature.system.battery.service.BatteryMonitorManager.isCharging.value
                checkAlarms(level, charging)
            }
        }
    }

    private suspend fun checkAlarms(pct: Int, isCharging: Boolean) {
        val repository = (application as NameGenApplication).settingsRepository
        
        // 1. High charging limit alarm
        val chargingAlarmEnabled = repository.batteryAlarmEnabledFlow.first()
        val chargingThreshold = repository.batteryAlarmThresholdFlow.first()
        val alarmSound = repository.batteryAlarmSoundFlow.first()
        val repeatAlarm = repository.batteryAlarmRepeatFlow.first()
        val volume = repository.batteryAlarmVolumeFlow.first()
        val volumeBoost = repository.batteryAlarmVolumeBoostFlow.first()

        if (chargingAlarmEnabled && isCharging && pct >= chargingThreshold) {
            triggerAlarm(this, "Battery Charged!", "Battery reached $pct%. Please unplug the charger.", alarmSound, repeatAlarm, volume, volumeBoost)
        }

        // 2. Low battery alarm
        val lowAlarmEnabled = repository.batteryLowAlarmEnabledFlow.first()
        val lowThreshold = repository.batteryLowAlarmThresholdFlow.first()
        if (lowAlarmEnabled && !isCharging && pct in 1..lowThreshold) {
            triggerAlarm(this, "Battery Low Alert!", "Battery is down to $pct%. Connect charger immediately.", alarmSound, repeatAlarm, volume, volumeBoost)
        }

        // Auto-stop alarm if unplugged during high charge alert OR plugged during low battery alert
        if (isRinging) {
            if (!isCharging && pct >= chargingThreshold) {
                stopRinging()
                dismissNotification(NOTIFICATION_ID_ALARM)
            } else if (isCharging && pct <= lowThreshold) {
                stopRinging()
                dismissNotification(NOTIFICATION_ID_ALARM)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_DISMISS_ALARM) {
            stopRinging()
            dismissNotification(NOTIFICATION_ID_ALARM)
        } else if (action == ACTION_STOP_SERVICE) {
            stopRinging()
            dismissNotification(NOTIFICATION_ID_ALARM)
            stopSelf()
            return START_NOT_STICKY
        }

        // Keep service alive in foreground with a clean persistent notification
        showForegroundNotification()
        
        return START_STICKY
    }

    private fun showForegroundNotification() {
        val stopIntent = Intent(this, BatteryAlarmService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 
            0, 
            stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "battery_alarm")
            .setContentTitle("Battery Guard Active")
            .setContentText("Monitoring charging level & battery temperature.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 
                    0, 
                    Intent(this, com.arwora.MainActivity::class.java), 
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(R.drawable.ic_launcher_foreground, "Turn Off Guard", stopPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID_FOREGROUND, notification)
    }

    private fun triggerAlarm(
        context: Context, 
        title: String, 
        message: String, 
        soundName: String, 
        repeat: Boolean,
        volume: Int,
        volumeBoost: Boolean
    ) {
        if (isRinging && activeSoundName == soundName) return // Already ringing with current sound

        // Clean up previous sound if switching or restarting
        stopRinging()

        isRinging = true
        activeSoundName = soundName
        setAlarmMessage("$title: $message")

        // If volume boost is enabled, maximize alarm stream volume
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null && volumeBoost) {
                val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVol, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Play Sound with resilient looping and fallback
        startRingingLoop(context, soundName, repeat, volume, volumeBoost)

        // Show Notification with Dismiss button
        val dismissIntent = Intent(this, BatteryAlarmService::class.java).apply {
            action = ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 
            1, 
            dismissIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, "battery_alarm")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 
                    0, 
                    Intent(this, com.arwora.MainActivity::class.java), 
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(R.drawable.ic_launcher_foreground, "Dismiss Alarm", dismissPendingIntent)

        notificationManager.notify(NOTIFICATION_ID_ALARM, builder.build())
    }

    private fun startRingingLoop(context: Context, soundName: String, repeat: Boolean, volume: Int, volumeBoost: Boolean) {
        ringingJob?.cancel()
        ringingJob = serviceScope.launch {
            val effVolume = if (volumeBoost) 100 else volume.coerceIn(0, 100)
            val volumeFloat = effVolume / 100f

            if (soundName.startsWith("Custom:")) {
                val uriStr = soundName.substringAfter("Custom:").substringBefore("|")
                try {
                    val uri = android.net.Uri.parse(uriStr)
                    mediaPlayer = android.media.MediaPlayer().apply {
                        setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(context, uri)
                        setVolume(volumeFloat, volumeFloat)
                        isLooping = repeat
                        setOnPreparedListener { mp ->
                            mp.start()
                        }
                        setOnErrorListener { mp, _, _ ->
                            try {
                                mp.reset()
                                mp.release()
                            } catch (e: Exception) { }
                            mediaPlayer = null
                            playFallbackTone(effVolume)
                            true
                        }
                        setOnCompletionListener { mp ->
                            if (!repeat) {
                                try {
                                    mp.stop()
                                    mp.release()
                                } catch (e: Exception) {}
                                mediaPlayer = null
                                stopRinging()
                            }
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        mediaPlayer?.release()
                    } catch (ex: Exception) { }
                    mediaPlayer = null
                    // Fallback to tone if custom sound fails to load
                    playFallbackTone(effVolume)
                }
                return@launch
            }

            if (soundName == "System Default Alarm") {
                try {
                    val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM) 
                        ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                    currentRingtone = android.media.RingtoneManager.getRingtone(context, uri)?.apply {
                        audioAttributes = android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            this.volume = volumeFloat
                            this.isLooping = repeat
                        }
                    }
                    currentRingtone?.play()

                    if (repeat && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
                        while (isRinging) {
                            if (currentRingtone?.isPlaying == false) {
                                currentRingtone?.play()
                            }
                            delay(3000)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    playFallbackTone(effVolume)
                }
                return@launch
            }

            // Built-in tone generator sounds
            try {
                toneGenerator?.release()
            } catch (e: Exception) { }
            try {
                toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, effVolume)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            do {
                playToneSound(context, soundName)
                if (repeat && isRinging) {
                    delay(3500)
                }
            } while (repeat && isRinging)
        }
    }

    private fun playToneSound(context: Context, soundName: String) {
        try {
            when (soundName) {
                "Classic Digital Beep" -> {
                    toneGenerator?.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 450)
                }
                "High-Frequency Chime" -> {
                    toneGenerator?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 350)
                }
                "Emergency Siren Alert" -> {
                    toneGenerator?.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
                }
                "Extreme Air Raid Siren" -> {
                    toneGenerator?.startTone(android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1200)
                }
                "Heavy Duty Buzzer" -> {
                    toneGenerator?.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 1000)
                }
                "High-Decibel Pulse Alert" -> {
                    toneGenerator?.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE, 900)
                }
                "Ultra Loud Warning Tone" -> {
                    toneGenerator?.startTone(android.media.ToneGenerator.TONE_SUP_CONGESTION, 1000)
                }
                "Retro Arcade Chime" -> {
                    com.arwora.utils.SoundEffectManager.playCoinToss(context)
                }
                "Harmonic Sparkling Ding!" -> {
                    com.arwora.utils.SoundEffectManager.playCoinLand(context)
                }
                else -> {
                    toneGenerator?.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 350)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playFallbackTone(effVolume: Int) {
        try {
            toneGenerator?.release()
        } catch (e: Exception) { }
        try {
            toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, effVolume)
            toneGenerator?.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRinging() {
        isRinging = false
        setAlarmMessage(null)
        ringingJob?.cancel()
        ringingJob = null
        try {
            currentRingtone?.stop()
        } catch (e: Exception) { }
        currentRingtone = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) { }
        mediaPlayer = null
        try {
            toneGenerator?.release()
        } catch (e: Exception) { }
        toneGenerator = null
    }

    private fun dismissNotification(notificationId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    override fun onDestroy() {
        stopRinging()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
