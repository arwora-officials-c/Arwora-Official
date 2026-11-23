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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.arwora.MainActivity
import com.arwora.utils.AutoClickerManager
import com.arwora.utils.ClickMode
import com.arwora.utils.ClickPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class AutoClickerOverlayService : Service() {

    companion object {
        const val ACTION_START_OVERLAY = "com.arwora.action.START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.arwora.action.STOP_OVERLAY"
        const val CHANNEL_ID = "auto_clicker_overlay_channel"
        const val NOTIFICATION_ID = 2050

        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var controllerView: View? = null
    private val targetViews = mutableMapOf<Int, View>()
    private var swipeStartView: View? = null
    private var swipeEndView: View? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        isRunning = true
        AutoClickerManager.setOverlayActive(true)

        createFloatingController()
        observeManagerState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        AutoClickerManager.setOverlayActive(false)
        AutoClickerAccessibilityService.instance?.stopClicking()
        serviceScope.cancel()

        removeFloatingController()
        removeAllTargets()
        removeSwipeViews()
    }

    private fun observeManagerState() {
        serviceScope.launch {
            AutoClickerManager.isClicking.collectLatest { clicking ->
                updatePlayPauseButton(clicking)
            }
        }

        serviceScope.launch {
            AutoClickerManager.mode.collectLatest { mode ->
                when (mode) {
                    ClickMode.SINGLE, ClickMode.MULTI -> {
                        removeSwipeViews()
                        syncTargetViews(AutoClickerManager.targets.value)
                    }
                    ClickMode.SWIPE -> {
                        removeAllTargets()
                        createSwipeViews()
                    }
                }
            }
        }

        serviceScope.launch {
            AutoClickerManager.targets.collectLatest { targets ->
                if (AutoClickerManager.mode.value != ClickMode.SWIPE) {
                    syncTargetViews(targets)
                }
            }
        }
    }

    // ==================== FLOATING CONTROLLER ====================

    private var playPauseButton: ImageView? = null

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingController() {
        if (controllerView != null) return

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
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 300
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61E1E2E"))
                cornerRadius = 32f
                setStroke(3, Color.parseColor("#4C566A"))
            }
            elevation = 16f
        }

        // Drag Handle / Header Icon
        val dragHandle = TextView(this).apply {
            text = "::: AUTO"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }
        container.addView(dragHandle)

        // 1. Play / Pause Button
        playPauseButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(Color.parseColor("#A6E3A1"))
            setPadding(16, 16, 16, 16)
            background = createCircleBackground(Color.parseColor("#313244"))
            setOnClickListener {
                if (AutoClickerAccessibilityService.isRunning) {
                    AutoClickerAccessibilityService.instance?.toggleClicking()
                } else {
                    openAccessibilitySettings()
                }
            }
        }
        container.addView(playPauseButton, createButtonLayoutParams())

        // 2. Add Target Button (+)
        val addTargetBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setColorFilter(Color.WHITE)
            setPadding(16, 16, 16, 16)
            background = createCircleBackground(Color.parseColor("#313244"))
            setOnClickListener {
                if (AutoClickerManager.mode.value == ClickMode.SWIPE) {
                    AutoClickerManager.setMode(ClickMode.MULTI)
                }
                AutoClickerManager.addTarget(
                    x = (params.x + 200).toFloat(),
                    y = (params.y + 100).toFloat()
                )
            }
        }
        container.addView(addTargetBtn, createButtonLayoutParams())

        // 3. Remove Target Button (-)
        val removeTargetBtn = TextView(this).apply {
            text = "—"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = createCircleBackground(Color.parseColor("#313244"))
            setOnClickListener {
                AutoClickerManager.removeLastTarget()
            }
        }
        container.addView(removeTargetBtn, createButtonLayoutParams())

        // 4. Swipe Toggle Button (⇄)
        val swipeBtn = TextView(this).apply {
            text = "⇄"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#89B4FA"))
            gravity = Gravity.CENTER
            background = createCircleBackground(Color.parseColor("#313244"))
            setOnClickListener {
                if (AutoClickerManager.mode.value == ClickMode.SWIPE) {
                    AutoClickerManager.setMode(ClickMode.SINGLE)
                } else {
                    AutoClickerManager.setMode(ClickMode.SWIPE)
                }
            }
        }
        container.addView(swipeBtn, createButtonLayoutParams())

        // 5. Settings Button (Gear)
        val settingsBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setColorFilter(Color.parseColor("#FAB387"))
            setPadding(16, 16, 16, 16)
            background = createCircleBackground(Color.parseColor("#313244"))
            setOnClickListener {
                val intent = Intent(this@AutoClickerOverlayService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
        }
        container.addView(settingsBtn, createButtonLayoutParams())

        // Speed Up Button (⚡+)
        val speedUpBtn = TextView(this).apply {
            text = "⚡+"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#A6E3A1"))
            gravity = Gravity.CENTER
            background = createCircleBackground(Color.parseColor("#313244"))
            setOnClickListener {
                val current = AutoClickerManager.clickIntervalMs.value
                val next = (current - 20L).coerceAtLeast(10L)
                AutoClickerManager.setInterval(next)
            }
        }
        container.addView(speedUpBtn, createButtonLayoutParams())

        // Speed Down Button (⚡-)
        val speedDownBtn = TextView(this).apply {
            text = "⚡-"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F38BA8"))
            gravity = Gravity.CENTER
            background = createCircleBackground(Color.parseColor("#313244"))
            setOnClickListener {
                val current = AutoClickerManager.clickIntervalMs.value
                val next = (current + 50L).coerceAtMost(2000L)
                AutoClickerManager.setInterval(next)
            }
        }
        container.addView(speedDownBtn, createButtonLayoutParams())

        // 6. Close Overlay Button (X)
        val closeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#F38BA8"))
            setPadding(16, 16, 16, 16)
            background = createCircleBackground(Color.parseColor("#313244"))
            setOnClickListener {
                stopSelf()
            }
        }
        container.addView(closeBtn, createButtonLayoutParams())

        // Draggable Touch Handling for the controller
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }

        controllerView = container
        runCatching { windowManager.addView(container, params) }
    }

    private fun updatePlayPauseButton(clicking: Boolean) {
        playPauseButton?.let { btn ->
            if (clicking) {
                btn.setImageResource(android.R.drawable.ic_media_pause)
                btn.setColorFilter(Color.parseColor("#F38BA8"))
            } else {
                btn.setImageResource(android.R.drawable.ic_media_play)
                btn.setColorFilter(Color.parseColor("#A6E3A1"))
            }
        }
    }

    private fun removeFloatingController() {
        controllerView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
            controllerView = null
        }
    }

    // ==================== FLOATING TARGET VIEWS ====================

    @SuppressLint("ClickableViewAccessibility")
    private fun syncTargetViews(targets: List<ClickPoint>) {
        // Remove old targets not present
        val currentIds = targets.map { it.id }.toSet()
        val toRemove = targetViews.keys.filter { it !in currentIds }
        toRemove.forEach { id ->
            targetViews[id]?.let {
                try { windowManager.removeView(it) } catch (e: Exception) {}
            }
            targetViews.remove(id)
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Add or update views for each target
        targets.forEach { target ->
            var view = targetViews[target.id]
            if (view == null) {
                val sizePx = (44 * resources.displayMetrics.density).toInt()
                val params = WindowManager.LayoutParams(
                    sizePx,
                    sizePx,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = target.x.toInt() - sizePx / 2
                    y = target.y.toInt() - sizePx / 2
                }

                val frame = FrameLayout(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#CC1E1E2E"))
                        setStroke(4, Color.parseColor("#89B4FA"))
                    }
                    elevation = 20f
                }

                val crosshairH = View(this).apply {
                    setBackgroundColor(Color.parseColor("#66FFFFFF"))
                    val hParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 2).apply {
                        gravity = Gravity.CENTER
                    }
                    layoutParams = hParams
                }
                val crosshairV = View(this).apply {
                    setBackgroundColor(Color.parseColor("#66FFFFFF"))
                    val vParams = FrameLayout.LayoutParams(2, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                        gravity = Gravity.CENTER
                    }
                    layoutParams = vParams
                }
                frame.addView(crosshairH)
                frame.addView(crosshairV)

                val label = TextView(this).apply {
                    text = "${target.id}"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    val lParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    layoutParams = lParams
                }
                frame.addView(label)

                var initialX = 0
                var initialY = 0
                var initialTouchX = 0f
                var initialTouchY = 0f

                frame.setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(frame, params)
                            // Center coordinates
                            val centerX = params.x + sizePx / 2f
                            val centerY = params.y + sizePx / 2f
                            AutoClickerManager.updateTargetPosition(target.id, centerX, centerY)
                            true
                        }
                        else -> false
                    }
                }

                targetViews[target.id] = frame
                runCatching { windowManager.addView(frame, params) }
            }
        }
    }

    private fun removeAllTargets() {
        targetViews.values.forEach {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        targetViews.clear()
    }

    // ==================== FLOATING SWIPE VIEWS ====================

    @SuppressLint("ClickableViewAccessibility")
    private fun createSwipeViews() {
        if (swipeStartView != null || swipeEndView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val sizePx = (44 * resources.displayMetrics.density).toInt()
        val currentSwipe = AutoClickerManager.swipe.value

        // Start View (S)
        val startParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentSwipe.startX.toInt() - sizePx / 2
            y = currentSwipe.startY.toInt() - sizePx / 2
        }

        val startFrame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E6A6E3A1"))
                setStroke(4, Color.parseColor("#2E7D32"))
            }
            val text = TextView(context).apply {
                text = "START"
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER
            }
            addView(text, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        var sInitX = 0; var sInitY = 0; var sTouchX = 0f; var sTouchY = 0f
        startFrame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sInitX = startParams.x; sInitY = startParams.y
                    sTouchX = event.rawX; sTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    startParams.x = sInitX + (event.rawX - sTouchX).toInt()
                    startParams.y = sInitY + (event.rawY - sTouchY).toInt()
                    windowManager.updateViewLayout(startFrame, startParams)
                    val sX = startParams.x + sizePx / 2f
                    val sY = startParams.y + sizePx / 2f
                    AutoClickerManager.updateSwipe(sX, sY, AutoClickerManager.swipe.value.endX, AutoClickerManager.swipe.value.endY)
                    true
                }
                else -> false
            }
        }

        // End View (E)
        val endParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentSwipe.endX.toInt() - sizePx / 2
            y = currentSwipe.endY.toInt() - sizePx / 2
        }

        val endFrame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E6F38BA8"))
                setStroke(4, Color.parseColor("#C62828"))
            }
            val text = TextView(context).apply {
                text = "END"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }
            addView(text, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        var eInitX = 0; var eInitY = 0; var eTouchX = 0f; var eTouchY = 0f
        endFrame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    eInitX = endParams.x; eInitY = endParams.y
                    eTouchX = event.rawX; eTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    endParams.x = eInitX + (event.rawX - eTouchX).toInt()
                    endParams.y = eInitY + (event.rawY - eTouchY).toInt()
                    windowManager.updateViewLayout(endFrame, endParams)
                    val eX = endParams.x + sizePx / 2f
                    val eY = endParams.y + sizePx / 2f
                    AutoClickerManager.updateSwipe(AutoClickerManager.swipe.value.startX, AutoClickerManager.swipe.value.startY, eX, eY)
                    true
                }
                else -> false
            }
        }

        swipeStartView = startFrame
        swipeEndView = endFrame
        windowManager.addView(startFrame, startParams)
        windowManager.addView(endFrame, endParams)
    }

    private fun removeSwipeViews() {
        swipeStartView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
            swipeStartView = null
        }
        swipeEndView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
            swipeEndView = null
        }
    }

    // ==================== HELPERS & NOTIFICATION ====================

    private fun createCircleBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun createButtonLayoutParams(): LinearLayout.LayoutParams {
        val size = (38 * resources.displayMetrics.density).toInt()
        return LinearLayout.LayoutParams(size, size).apply {
            setMargins(0, 6, 0, 6)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Clicker Overlay Controller",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running floating overlay controls for Auto Clicker"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AutoClickerOverlayService::class.java).apply {
            action = ACTION_STOP_OVERLAY
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Clicker Active")
            .setContentText("Floating overlay controller is visible on screen")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Overlay", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
