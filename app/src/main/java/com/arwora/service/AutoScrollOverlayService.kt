package com.arwora.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.arwora.MainActivity
import com.arwora.utils.AutoScrollMode
import com.arwora.utils.AutoVideoScrollManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

class AutoScrollOverlayService : Service() {

    companion object {
        const val ACTION_START_SCROLL_OVERLAY = "com.arwora.action.START_AUTO_SCROLL_OVERLAY"
        const val ACTION_STOP_SCROLL_OVERLAY = "com.arwora.action.STOP_AUTO_SCROLL_OVERLAY"
        const val ACTION_TOGGLE_PAUSE = "com.arwora.action.TOGGLE_AUTO_SCROLL_PAUSE"
        const val CHANNEL_ID = "auto_scroll_overlay_channel"
        const val NOTIFICATION_ID = 2055

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, AutoScrollOverlayService::class.java).apply {
                action = ACTION_START_SCROLL_OVERLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AutoScrollOverlayService::class.java).apply {
                action = ACTION_STOP_SCROLL_OVERLAY
            }
            context.stopService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var pillContainer: View? = null
    private var statusDotView: View? = null
    private var timerLabelView: TextView? = null
    private var pauseResumeButton: ImageButton? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AutoVideoScrollManager.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        val notification = createNotification(paused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true
        AutoVideoScrollManager.setServiceActive(true)
        createFloatingPill()
        observeScrollState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SCROLL_OVERLAY -> {
                AutoVideoScrollManager.setServiceActive(false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PAUSE -> {
                AutoVideoScrollManager.togglePaused()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        AutoVideoScrollManager.setServiceActive(false)
        serviceScope.cancel()
        removeFloatingPill()
    }

    private fun observeScrollState() {
        serviceScope.launch {
            combine(
                AutoVideoScrollManager.isPaused,
                AutoVideoScrollManager.mode,
                AutoVideoScrollManager.countdownSecondsRemaining
            ) { paused, mode, remaining ->
                Triple(paused, mode, remaining)
            }.collectLatest { (paused, mode, remaining) ->
                updateFloatingPillUi(paused, mode, remaining)
                updateNotification(paused)
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingPill() {
        if (pillContainer != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dpToPx(14)
            y = dpToPx(180)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(6), dpToPx(8), dpToPx(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DF121520"))
                cornerRadius = dpToPx(28).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#556B7280"))
            }
            elevation = dpToPx(8).toFloat()
        }

        // Live Status Dot
        val dotSize = dpToPx(9)
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#10B981"))
            }
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                marginEnd = dpToPx(8)
            }
        }
        statusDotView = dot
        container.addView(dot)

        // Timer / Mode Compact Label
        val label = TextView(this).apply {
            text = "${AutoVideoScrollManager.intervalSeconds.value}s"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dpToPx(6), 0)
        }
        timerLabelView = label
        container.addView(label)

        // Pause / Resume Button (48dp touch target)
        val btnSize = dpToPx(48)
        val pauseBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(Color.parseColor("#A6E3A1"))
            contentDescription = "Pause or resume auto video scroll"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2A2F42"))
            }
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                marginEnd = dpToPx(4)
            }
            setOnClickListener {
                AutoVideoScrollManager.togglePaused()
            }
        }
        pauseResumeButton = pauseBtn
        container.addView(pauseBtn)

        // Stop Button (48dp touch target)
        val stopBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#F38BA8"))
            contentDescription = "Stop auto video scroll"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2A2F42"))
            }
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            setOnClickListener {
                AutoVideoScrollManager.setServiceActive(false)
                stopSelf()
            }
        }
        container.addView(stopBtn)

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (initialTouchX - event.rawX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = (initialX + dx).coerceAtLeast(0)
                        params.y = (initialY + dy).coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(container, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        AutoVideoScrollManager.togglePaused()
                    }
                    true
                }
                else -> false
            }
        }

        pillContainer = container
        runCatching { windowManager.addView(container, params) }
    }

    private fun updateFloatingPillUi(paused: Boolean, mode: AutoScrollMode, remainingSec: Int) {
        val dotBg = statusDotView?.background as? GradientDrawable
        if (paused) {
            dotBg?.setColor(Color.parseColor("#F59E0B"))
            timerLabelView?.text = "PAUSED"
            pauseResumeButton?.setImageResource(android.R.drawable.ic_media_play)
            pauseResumeButton?.setColorFilter(Color.parseColor("#A6E3A1"))
        } else {
            dotBg?.setColor(Color.parseColor("#10B981"))
            timerLabelView?.text = if (mode == AutoScrollMode.TIME_BASED) "${remainingSec}s" else "LOOP"
            pauseResumeButton?.setImageResource(android.R.drawable.ic_media_pause)
            pauseResumeButton?.setColorFilter(Color.parseColor("#F9E2AF"))
        }
    }

    private fun removeFloatingPill() {
        pillContainer?.let { view ->
            runCatching { windowManager.removeView(view) }
            pillContainer = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Video Scroll Controller",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating control pill for Auto Video Scroll"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(paused: Boolean) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, createNotification(paused))
        }
    }

    private fun createNotification(paused: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, AutoScrollOverlayService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AutoScrollOverlayService::class.java).apply {
            action = ACTION_STOP_SCROLL_OVERLAY
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (paused) "Auto Video Scroll Paused" else "Auto Video Scroll Running")
            .setContentText(if (paused) "Tap Resume on floating pill or notification" else "Monitoring short video feed for auto-scroll")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (paused) "Resume" else "Pause",
                togglePendingIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
