package com.arwora.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.arwora.NameGenApplication
import com.arwora.R
import com.arwora.ui.components.SystemOverlayContent
import com.arwora.ui.theme.NameGenProTheme
import kotlinx.coroutines.*

open class FloatingWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        private var activeServiceInstance: FloatingWindowService? = null

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FloatingWindowService::class.java)
                context.stopService(intent)
                val intentOverlay = Intent(context, FloatingOverlayService::class.java)
                context.stopService(intentOverlay)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    protected lateinit var windowManager: WindowManager
    protected var rootContainer: FrameLayout? = null
    protected var bubbleView: ComposeView? = null
    protected var windowCardView: ComposeView? = null
    protected var resizeHandleView: FrameLayout? = null
    protected var layoutParams: WindowManager.LayoutParams? = null

    private var isPanelExpanded: Boolean = false
    private var hasUserResized: Boolean = false
    private var currentWindowWidthPx: Int = 0
    private var currentWindowHeightPx: Int = 0

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()

        synchronized(FloatingWindowService::class.java) {
            if (activeServiceInstance != null && activeServiceInstance != this) {
                activeServiceInstance?.cleanupOverlay()
                activeServiceInstance?.stopSelf()
            }
            activeServiceInstance = this
        }
        isRunning = true

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupDualWindowOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        synchronized(FloatingWindowService::class.java) {
            if (rootContainer == null) {
                setupDualWindowOverlay()
            } else {
                rootContainer?.let { view ->
                    layoutParams?.let { params ->
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            try {
                                windowManager.removeView(view)
                                windowManager.addView(view, params)
                            } catch (e2: Exception) {
                                e2.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun setupDualWindowOverlay() {
        synchronized(FloatingWindowService::class.java) {
            if (rootContainer != null) return

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val app = application as NameGenApplication
            val viewModel = app.sharedViewModel

            val density = resources.displayMetrics.density
            val savedX = viewModel.floatingPosX.value.toInt().coerceAtLeast(0)
            val savedY = viewModel.floatingPosY.value.toInt().coerceAtLeast(0)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = if (savedX > 0) savedX else 80
                y = if (savedY > 0) savedY else 160
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            layoutParams = params

            serviceScope.launch {
                viewModel.isFloatingWindowFocusable.collect { focusable ->
                    updateWindowFocusability(focusable)
                }
            }

            val container = FrameLayout(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWindowService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            }

            // 1. Bubble View (Floating circular icon: size 56.dp x 56.dp)
            val bubble = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWindowService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
                setContent {
                    val darkModeState by viewModel.darkMode.collectAsStateWithLifecycle()
                    val isDarkTheme = darkModeState ?: false
                    val uiColorLong by viewModel.uiColor.collectAsStateWithLifecycle()
                    val buttonColorLong by viewModel.buttonColor.collectAsStateWithLifecycle()

                    NameGenProTheme(
                        darkTheme = isDarkTheme,
                        uiColor = Color(uiColorLong.toInt()),
                        buttonColor = Color(buttonColorLong.toInt())
                    ) {
                        BubbleContent(
                            onTap = { expandWindow() },
                            onDrag = { dx, dy -> updateOverlayPosition(dx, dy) }
                        )
                    }
                }
                visibility = View.VISIBLE
            }

            // 2. Window Card View (Expanded studio panel layout)
            val windowCard = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWindowService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 18 * density)
                    }
                }
                setContent {
                    val darkModeState by viewModel.darkMode.collectAsStateWithLifecycle()
                    val isDarkTheme = darkModeState ?: false
                    val uiColorLong by viewModel.uiColor.collectAsStateWithLifecycle()
                    val buttonColorLong by viewModel.buttonColor.collectAsStateWithLifecycle()

                    NameGenProTheme(
                        darkTheme = isDarkTheme,
                        uiColor = Color(uiColorLong.toInt()),
                        buttonColor = Color(buttonColorLong.toInt())
                    ) {
                        SystemOverlayContent(
                            viewModel = viewModel,
                            onDragDelta = { dx, dy -> updateOverlayPosition(dx, dy) },
                            onMinimize = { minimizeWindow() },
                            onTogglePanel = { expanded -> togglePanelOptions(expanded) },
                            onLaunchApp = { launchMainActivity() },
                            onClose = { closeOverlay() }
                        )
                    }
                }
                visibility = View.GONE
            }

            // 3. Touch-friendly Bottom-Right Corner Resize Handle (48dp touch target)
            val handleTouchPx = (48 * density).toInt()
            val badgeSizePx = (24 * density).toInt()
            val badgeMarginPx = (4 * density).toInt()
            val iconPadPx = (4 * density).toInt()

            val badgeBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    10f * density, 10f * density,
                    2f * density, 2f * density,
                    14f * density, 14f * density,
                    2f * density, 2f * density
                )
                setColor(viewModel.uiColor.value.toInt())
                alpha = 215
            }

            val resizeIcon = ImageView(this).apply {
                setImageResource(R.drawable.ic_resize_handle)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(iconPadPx, iconPadPx, iconPadPx, iconPadPx)
                background = badgeBackground
            }

            val resizeHandle = FrameLayout(this).apply {
                contentDescription = "Resize Floating Window"
                isClickable = true
                isFocusable = true
                addView(
                    resizeIcon,
                    FrameLayout.LayoutParams(badgeSizePx, badgeSizePx, Gravity.BOTTOM or Gravity.END).apply {
                        rightMargin = badgeMarginPx
                        bottomMargin = badgeMarginPx
                    }
                )
                visibility = View.GONE
            }

            serviceScope.launch {
                viewModel.uiColor.collect { colorLong ->
                    badgeBackground.setColor(colorLong.toInt())
                    badgeBackground.alpha = 215
                }
            }

            var resizeInitialWidth = 0
            var resizeInitialHeight = 0
            var resizeInitialTouchX = 0f
            var resizeInitialTouchY = 0f

            @SuppressLint("ClickableViewAccessibility")
            resizeHandle.setOnTouchListener { v, event ->
                val params = layoutParams ?: return@setOnTouchListener false
                val containerView = rootContainer ?: return@setOnTouchListener false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        resizeInitialWidth = params.width.takeIf { it > 0 } ?: containerView.width
                        resizeInitialHeight = params.height.takeIf { it > 0 } ?: containerView.height
                        resizeInitialTouchX = event.rawX
                        resizeInitialTouchY = event.rawY
                        resizeIcon.animate().scaleX(1.15f).scaleY(1.15f).setDuration(80).start()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - resizeInitialTouchX).toInt()
                        val dy = (event.rawY - resizeInitialTouchY).toInt()
                        val (minW, maxW, minH, maxH) = computeWindowSizeBounds(params, isPanelExpanded)

                        val newWidth = (resizeInitialWidth + dx).coerceIn(minW, maxW)
                        val newHeight = (resizeInitialHeight + dy).coerceIn(minH, maxH)

                        if (newWidth != params.width || newHeight != params.height) {
                            params.width = newWidth
                            params.height = newHeight
                            currentWindowWidthPx = newWidth
                            currentWindowHeightPx = newHeight
                            hasUserResized = true
                            try {
                                windowManager.updateViewLayout(containerView, params)
                                windowCardView?.invalidateOutline()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        resizeIcon.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                        true
                    }
                    else -> false
                }
            }

            container.addView(bubble, FrameLayout.LayoutParams(
                (52 * density).toInt(),
                (52 * density).toInt()
            ))

            container.addView(windowCard, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            container.addView(resizeHandle, FrameLayout.LayoutParams(
                handleTouchPx,
                handleTouchPx,
                Gravity.BOTTOM or Gravity.END
            ))

            this.rootContainer = container
            this.bubbleView = bubble
            this.windowCardView = windowCard
            this.resizeHandleView = resizeHandle

            try {
                windowManager.addView(container, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private data class WindowSizeBounds(
        val minWidth: Int,
        val maxWidth: Int,
        val minHeight: Int,
        val maxHeight: Int
    )

    private fun computeWindowSizeBounds(
        params: WindowManager.LayoutParams,
        expanded: Boolean
    ): WindowSizeBounds {
        val metrics = resources.displayMetrics
        val density = metrics.density
        val minW = (240 * density).toInt()
        val minH = if (expanded) (240 * density).toInt() else (180 * density).toInt()
        val maxW = (metrics.widthPixels - params.x)
            .coerceAtLeast(minW)
            .coerceAtMost((metrics.widthPixels * 0.96f).toInt().coerceAtLeast(minW))
        val maxH = (metrics.heightPixels - params.y)
            .coerceAtLeast(minH)
            .coerceAtMost((metrics.heightPixels * 0.88f).toInt().coerceAtLeast(minH))
        return WindowSizeBounds(minW, maxW, minH, maxH)
    }

    private fun getDefaultWindowDimensions(expanded: Boolean): Pair<Int, Int> {
        val density = resources.displayMetrics.density
        val app = application as? NameGenApplication
        val sizePref = app?.sharedViewModel?.defaultFloatingSize?.value ?: "Compact"
        return if (expanded) {
            when (sizePref) {
                "Large" -> (380 * density).toInt() to (560 * density).toInt()
                "Compact" -> (340 * density).toInt() to (460 * density).toInt()
                else -> (360 * density).toInt() to (520 * density).toInt()
            }
        } else {
            when (sizePref) {
                "Large" -> (360 * density).toInt() to (225 * density).toInt()
                "Medium" -> (345 * density).toInt() to (210 * density).toInt()
                else -> (330 * density).toInt() to (196 * density).toInt()
            }
        }
    }

    protected fun expandWindow() {
        bubbleView?.visibility = View.GONE
        windowCardView?.visibility = View.VISIBLE
        resizeHandleView?.visibility = View.VISIBLE

        layoutParams?.let { params ->
            val bounds = computeWindowSizeBounds(params, isPanelExpanded)
            val (defaultW, defaultH) = getDefaultWindowDimensions(isPanelExpanded)
            val targetW = (if (hasUserResized && currentWindowWidthPx > 0) currentWindowWidthPx else defaultW)
                .coerceIn(bounds.minWidth, bounds.maxWidth)
            val targetH = (if (hasUserResized && currentWindowHeightPx > 0) currentWindowHeightPx else defaultH)
                .coerceIn(bounds.minHeight, bounds.maxHeight)

            params.width = targetW
            params.height = targetH
            currentWindowWidthPx = targetW
            currentWindowHeightPx = targetH

            rootContainer?.let { view ->
                try {
                    windowManager.updateViewLayout(view, params)
                    windowCardView?.invalidateOutline()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    protected fun minimizeWindow() {
        windowCardView?.visibility = View.GONE
        resizeHandleView?.visibility = View.GONE
        bubbleView?.visibility = View.VISIBLE

        val density = resources.displayMetrics.density
        layoutParams?.let { params ->
            params.width = (52 * density).toInt()
            params.height = (52 * density).toInt()
            rootContainer?.let { view ->
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    protected fun togglePanelOptions(expanded: Boolean = !isPanelExpanded) {
        isPanelExpanded = expanded
        layoutParams?.let { params ->
            val bounds = computeWindowSizeBounds(params, expanded)
            val (defaultW, defaultH) = getDefaultWindowDimensions(expanded)
            val targetW = (if (hasUserResized && currentWindowWidthPx > 0) currentWindowWidthPx else defaultW)
                .coerceIn(bounds.minWidth, bounds.maxWidth)
            val targetH = (if (expanded) {
                if (hasUserResized && currentWindowHeightPx > defaultH) currentWindowHeightPx else defaultH
            } else {
                defaultH
            }).coerceIn(bounds.minHeight, bounds.maxHeight)

            params.width = targetW
            params.height = targetH
            currentWindowWidthPx = targetW
            currentWindowHeightPx = targetH

            rootContainer?.let { view ->
                try {
                    windowManager.updateViewLayout(view, params)
                    windowCardView?.invalidateOutline()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    protected fun launchMainActivity() {
        try {
            val intent = Intent(this, com.arwora.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    protected fun closeOverlay() {
        try {
            val app = application as? NameGenApplication
            app?.sharedViewModel?.setFloatingMode(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cleanupOverlay()
        stopSelf()
    }

    private fun updateOverlayPosition(dx: Float, dy: Float) {
        val view = rootContainer ?: return
        val params = layoutParams ?: return

        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val viewW = params.width.takeIf { it > 0 } ?: view.width.takeIf { it > 0 } ?: 200
        val viewH = params.height.takeIf { it > 0 } ?: view.height.takeIf { it > 0 } ?: 200

        val maxX = (screenWidth - viewW).coerceAtLeast(0)
        val maxY = (screenHeight - viewH).coerceAtLeast(0)

        params.x = (params.x + dx.toInt()).coerceIn(0, maxX)
        params.y = (params.y + dy.toInt()).coerceIn(0, maxY)

        try {
            windowManager.updateViewLayout(view, params)
            val app = application as? NameGenApplication
            app?.sharedViewModel?.setFloatingPosition(params.x.toFloat(), params.y.toFloat())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateWindowFocusability(focusable: Boolean) {
        val view = rootContainer ?: return
        val params = layoutParams ?: return

        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanupOverlay() {
        rootContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            rootContainer = null
            bubbleView = null
            windowCardView = null
            resizeHandleView = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceJob.cancel()
        cleanupOverlay()
        if (activeServiceInstance == this) {
            activeServiceInstance = null
            isRunning = false
        }
    }
}

@Composable
private fun BubbleContent(
    onTap: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appIconBitmap = remember(context) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap.asImageBitmap()
            } else {
                val bmp = android.graphics.Bitmap.createBitmap(
                    if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128,
                    if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    Surface(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onTap() },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = "Floating Studio Icon",
                    modifier = Modifier.fillMaxSize().padding(4.dp).clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Floating Studio Icon",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
