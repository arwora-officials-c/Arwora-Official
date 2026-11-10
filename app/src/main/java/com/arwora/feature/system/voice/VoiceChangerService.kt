package com.arwora.feature.system.voice

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.arwora.MainActivity

class VoiceChangerService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                VoiceChangerManager.startAudioEngine()
                if (VoiceChangerManager.isFloatingOverlayEnabled.value && Settings.canDrawOverlays(this)) {
                    showFloatingOverlay()
                }
            }
            ACTION_STOP -> {
                removeFloatingOverlay()
                VoiceChangerManager.stopAudioEngine()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_TOGGLE_PRESET -> {
                val nextPreset = when (VoiceChangerManager.currentPreset.value) {
                    VoicePreset.FEMALE -> VoicePreset.DEEP_MALE
                    VoicePreset.DEEP_MALE -> VoicePreset.ROBOT
                    VoicePreset.ROBOT -> VoicePreset.CHIPMUNK
                    VoicePreset.CHIPMUNK -> VoicePreset.ORIGINAL
                    else -> VoicePreset.FEMALE
                }
                VoiceChangerManager.setPreset(nextPreset)
                Toast.makeText(this, "Voice: ${nextPreset.title}", Toast.LENGTH_SHORT).show()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
            }
        }
        return START_STICKY
    }

    private fun showFloatingOverlay() {
        if (floatingView != null) return
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 60
                y = 220
            }

            val imageView = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_btn_speak_now)
                setBackgroundResource(android.R.drawable.btn_default)
                setPadding(24, 24, 24, 24)
                setOnClickListener {
                    val nextPreset = when (VoiceChangerManager.currentPreset.value) {
                        VoicePreset.FEMALE -> VoicePreset.DEEP_MALE
                        VoicePreset.DEEP_MALE -> VoicePreset.ROBOT
                        VoicePreset.ROBOT -> VoicePreset.CHIPMUNK
                        VoicePreset.CHIPMUNK -> VoicePreset.ORIGINAL
                        else -> VoicePreset.FEMALE
                    }
                    VoiceChangerManager.setPreset(nextPreset)
                    Toast.makeText(this@VoiceChangerService, "Voice: ${nextPreset.title}", Toast.LENGTH_SHORT).show()
                }
            }

            floatingView = imageView
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFloatingOverlay() {
        try {
            floatingView?.let { windowManager?.removeView(it) }
            floatingView = null
        } catch (_: Exception) {}
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingLaunch = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(this, VoiceChangerService::class.java).apply { action = ACTION_TOGGLE_PRESET }
        val pendingToggle = PendingIntent.getService(this, 1, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, VoiceChangerService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val preset = VoiceChangerManager.currentPreset.value

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Changer Active")
            .setContentText("Current Preset: ${preset.title}")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingLaunch)
            .addAction(android.R.drawable.ic_menu_rotate, "Switch", pendingToggle)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Changer Live Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        removeFloatingOverlay()
        VoiceChangerManager.stopAudioEngine()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "voice_changer_channel"
        const val NOTIFICATION_ID = 20261
        const val ACTION_START = "com.arwora.action.VOICE_START"
        const val ACTION_STOP = "com.arwora.action.VOICE_STOP"
        const val ACTION_TOGGLE_PRESET = "com.arwora.action.VOICE_TOGGLE_PRESET"
    }
}
