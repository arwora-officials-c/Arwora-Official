package com.arwora.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.arwora.AppLockActivity
import com.arwora.receiver.UninstallReceiver
import com.arwora.utils.AppLockerManager
import com.arwora.utils.AutoScrollMode
import com.arwora.utils.AutoVideoScrollManager
import com.arwora.utils.VideoCompletionDetector
import com.arwora.utils.VideoProgressSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SwipeCoordinates(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float
)

/**
 * Unified Core Accessibility Service for Arwora Hyper Toolkit.
 *
 * Consolidates:
 * 1. App Locker Protection Guard: foreground locked-app enforcement, uninstall protection,
 *    Device Admin tamper interception, and PackageInstaller / Play Store install blocking.
 * 2. Auto Video Scroll Engine: short-video feed detection, playback completion inspection,
 *    and automated vertical swipe gesture dispatching.
 */
class ArworaCoreAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val reusablePath = Path()
    private val inspectionLock = Any()

    private var tickerJob: Job? = null
    private var observationJob: Job? = null

    @Volatile
    private var lastGuardEventHandledMs: Long = 0L

    @Volatile
    private var lastLockedPromptPkg: String? = null

    @Volatile
    private var lastLockedPromptMs: Long = 0L

    @Volatile
    private var lastScrollEventInspectMs: Long = 0L

    @Volatile
    private var lastSwipeTimestampMs: Long = 0L

    @Volatile
    private var previousVideoRatio: Float? = null

    @Volatile
    private var previousElapsedSeconds: Int? = null

    @Volatile
    private var lastObservedWindowPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppLockerManager.init(applicationContext)
        AutoVideoScrollManager.init(applicationContext)
        AutoVideoScrollManager.setAccessibilityServiceRunning(true)
        observeAutoScrollState()
        Log.d(TAG, "ArworaCoreAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Duty 1: App Locker foreground monitoring, install block, and uninstall protection
        handleAppProtectionEvent(event)

        // Duty 2: Auto Video Scroll platform recognition and playback completion inspection
        handleAutoScrollEvent(event)
    }

    private fun handleAppProtectionEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        AppLockerManager.init(applicationContext)
        val lockerOn = AppLockerManager.isLockerEnabled.value
        val uninstallGuardOn = AppLockerManager.isUninstallProtectionEnabled.value
        val installBlockOn = AppLockerManager.isInstallBlockEnabled.value
        if (!lockerOn && !uninstallGuardOn && !installBlockOn) return

        val foregroundPkg = event.packageName?.toString()?.trim().orEmpty()
        if (foregroundPkg.isEmpty() || AppLockerManager.isSelfPackageName(foregroundPkg)) return

        val now = System.currentTimeMillis()

        // Enforce App Locker on foreground window state transitions
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            !isTransientSystemWindow(foregroundPkg)
        ) {
            AppLockerManager.onForegroundPackageChanged(foregroundPkg)
            if (lockerOn &&
                AppLockerManager.isAppLocked(foregroundPkg) &&
                !AppLockerManager.isPackageUnlocked(foregroundPkg)
            ) {
                if (lastLockedPromptPkg != foregroundPkg || (now - lastLockedPromptMs) >= LOCK_PROMPT_COOLDOWN_MS) {
                    lastLockedPromptPkg = foregroundPkg
                    lastLockedPromptMs = now
                    launchAppLockScreen(foregroundPkg)
                    return
                }
            } else {
                lastLockedPromptPkg = null
            }
        }

        if (!uninstallGuardOn && !installBlockOn) return
        if (now - lastGuardEventHandledMs < GUARD_EVENT_THROTTLE_MS) return

        val isInstallerUi = foregroundPkg in INSTALLER_PACKAGES
        val isSettingsUi = foregroundPkg == SETTINGS_PACKAGE
        if (!isInstallerUi && !isSettingsUi) return

        val root = runCatching { rootInActiveWindow }.getOrNull()
        val visibleText = collectWindowTextLower(root, maxNodes = 40)
        if (root != null) recycleSafely(root)

        // 1. Check Install Block on PackageInstaller / Play Store install prompt
        if (installBlockOn &&
            isInstallerUi &&
            !AppLockerManager.isInstallAuthorized(AppInstallMonitorService.INSTALLER_PLACEHOLDER_PKG)
        ) {
            val looksLikeInstall = visibleText.contains("do you want to install") ||
                visibleText.contains("install this app") ||
                visibleText.contains("install anyway") ||
                (visibleText.contains("install") && !visibleText.contains("uninstall"))
            if (looksLikeInstall) {
                lastGuardEventHandledMs = now
                performGlobalAction(GLOBAL_ACTION_BACK)
                AppInstallMonitorService.handleNewInstallDetected(
                    context = applicationContext,
                    packageName = AppInstallMonitorService.INSTALLER_PLACEHOLDER_PKG
                )
                return
            }
        }

        // 2. Check Uninstall Protection on PackageInstaller or Settings (App Info / Device Admin)
        if (uninstallGuardOn && !AppLockerManager.isUninstallAuthorized()) {
            val looksLikeUninstall = visibleText.contains("uninstall") ||
                visibleText.contains("deactivate") ||
                visibleText.contains("remove device admin") ||
                visibleText.contains("delete app")

            if (looksLikeUninstall) {
                val matchedPkg = findProtectedPackageInText(visibleText)
                val mentionsArwora = visibleText.contains("arwora") || visibleText.contains("hyper toolkit")
                if (mentionsArwora || matchedPkg != null || isInstallerUi) {
                    lastGuardEventHandledMs = now
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    UninstallReceiver.interceptUninstallIntent(
                        context = applicationContext,
                        targetPackage = matchedPkg ?: applicationContext.packageName
                    )
                }
            }
        }
    }

    private fun handleAutoScrollEvent(event: AccessibilityEvent) {
        if (!AutoVideoScrollManager.isServiceActive.value || AutoVideoScrollManager.isPaused.value) {
            return
        }

        val eventPkg = event.packageName?.toString()
        if (!eventPkg.isNullOrBlank()) {
            val isWindowChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            if (eventPkg != packageName || isWindowChange) {
                lastObservedWindowPackage = eventPkg
                if (eventPkg != packageName) {
                    val platform = VideoCompletionDetector.recognizePlatformLabel(eventPkg)
                    AutoVideoScrollManager.updateDetectedPlatform(platform)
                }
            }
        }

        if (AutoVideoScrollManager.mode.value != AutoScrollMode.COMPLETION_BASED) {
            return
        }

        inspectActiveWindowForCompletion(force = false)
    }

    override fun onInterrupt() {
        Log.d(TAG, "ArworaCoreAccessibilityService interrupted")
        stopAutoScrollLoop()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopAutoScrollLoop()
        observationJob?.cancel()
        if (instance === this) {
            instance = null
        }
        AutoVideoScrollManager.setAccessibilityServiceRunning(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopAutoScrollLoop()
        observationJob?.cancel()
        serviceScope.cancel()
        if (instance === this) {
            instance = null
        }
        AutoVideoScrollManager.setAccessibilityServiceRunning(false)
        super.onDestroy()
    }

    private fun launchAppLockScreen(lockedPackage: String) {
        if (AppLockerManager.isSelfPackageName(lockedPackage)) return
        if (!AppLockerManager.isLockerEnabled.value) return
        if (!AppLockerManager.isAppLocked(lockedPackage)) return
        if (AppLockerManager.isPackageUnlocked(lockedPackage)) return

        runCatching {
            val intent = Intent(this, AppLockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, lockedPackage)
            }
            startActivity(intent)
        }.onFailure { err ->
            Log.e(TAG, "Failed to launch AppLockActivity for $lockedPackage", err)
        }
    }

    private fun isTransientSystemWindow(packageName: String): Boolean {
        return packageName == "com.android.systemui" ||
            packageName.contains("inputmethod", ignoreCase = true)
    }

    private fun findProtectedPackageInText(visibleText: String): String? {
        if (visibleText.contains(applicationContext.packageName.lowercase())) {
            return applicationContext.packageName
        }
        val pm = packageManager
        for (pkg in AppLockerManager.lockedPackages.value) {
            if (visibleText.contains(pkg.lowercase())) return pkg
            val appLabel = runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString().lowercase()
            }.getOrNull()
            if (!appLabel.isNullOrBlank() && visibleText.contains(appLabel)) {
                return pkg
            }
        }
        return null
    }

    private fun collectWindowTextLower(root: AccessibilityNodeInfo?, maxNodes: Int): String {
        if (root == null) return ""
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            if (!text.isNullOrBlank()) {
                sb.append(text.lowercase()).append(' ')
            }
            if (!desc.isNullOrBlank()) {
                sb.append(desc.lowercase()).append(' ')
            }
            val childCount = node.childCount.coerceAtMost(12)
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                queue.add(child)
            }
            if (node !== root) {
                recycleSafely(node)
            }
        }
        while (queue.isNotEmpty()) {
            val leftover = queue.removeFirst()
            if (leftover !== root) recycleSafely(leftover)
        }
        return sb.toString()
    }

    private fun observeAutoScrollState() {
        observationJob?.cancel()
        observationJob = serviceScope.launch {
            combine(
                AutoVideoScrollManager.isServiceActive,
                AutoVideoScrollManager.isPaused,
                AutoVideoScrollManager.mode,
                AutoVideoScrollManager.intervalSeconds
            ) { active, paused, mode, interval ->
                Triple(active && !paused, mode, interval)
            }.collectLatest { (shouldRun, mode, interval) ->
                stopAutoScrollLoop()
                if (shouldRun) {
                    startAutoScrollLoop(mode, interval)
                }
            }
        }
    }

    private fun stopAutoScrollLoop() {
        tickerJob?.cancel()
        tickerJob = null
        synchronized(inspectionLock) {
            previousVideoRatio = null
            previousElapsedSeconds = null
        }
    }

    private fun startAutoScrollLoop(mode: AutoScrollMode, intervalSeconds: Int) {
        val safeInterval = intervalSeconds.coerceIn(5, 180)
        tickerJob = serviceScope.launch {
            when (mode) {
                AutoScrollMode.TIME_BASED -> runTimeBasedTicker(safeInterval)
                AutoScrollMode.COMPLETION_BASED -> runCompletionMonitorLoop()
            }
        }
    }

    private suspend fun runTimeBasedTicker(intervalSeconds: Int) {
        var remaining = intervalSeconds
        AutoVideoScrollManager.updateCountdown(remaining)
        while (currentCoroutineContext().isActive &&
            AutoVideoScrollManager.isServiceActive.value &&
            !AutoVideoScrollManager.isPaused.value
        ) {
            val inToolkit = isForegroundAppToolkit()
            if (inToolkit) {
                AutoVideoScrollManager.updateStatusDetail("Open TikTok, Reels, or Shorts")
                delay(1_000L)
                continue
            }

            val platform = VideoCompletionDetector.recognizePlatformLabel(lastObservedWindowPackage)
            val prefix = if (platform != null) "$platform • " else ""
            AutoVideoScrollManager.updateCountdown(remaining)
            AutoVideoScrollManager.updateStatusDetail("${prefix}Next scroll in ${remaining}s")

            delay(1_000L)
            remaining--
            if (remaining <= 0) {
                triggerAutoScrollSwipe()
                remaining = AutoVideoScrollManager.intervalSeconds.value.coerceIn(5, 180)
                AutoVideoScrollManager.updateCountdown(remaining)
            }
        }
    }

    private suspend fun runCompletionMonitorLoop() {
        AutoVideoScrollManager.updateCountdown(0)
        while (currentCoroutineContext().isActive &&
            AutoVideoScrollManager.isServiceActive.value &&
            !AutoVideoScrollManager.isPaused.value
        ) {
            if (isForegroundAppToolkit()) {
                AutoVideoScrollManager.updateStatusDetail("Open TikTok, Reels, or Shorts")
                delay(1_000L)
                continue
            }

            val platform = VideoCompletionDetector.recognizePlatformLabel(lastObservedWindowPackage)
            val prefix = if (platform != null) "$platform • " else ""
            AutoVideoScrollManager.updateCountdown(0)
            AutoVideoScrollManager.updateStatusDetail("${prefix}Waiting for video completion")

            inspectActiveWindowForCompletion(force = true)
            delay(COMPLETION_POLL_INTERVAL_MS)
        }
    }

    private fun inspectActiveWindowForCompletion(force: Boolean) {
        if (!AutoVideoScrollManager.isServiceActive.value ||
            AutoVideoScrollManager.isPaused.value ||
            AutoVideoScrollManager.mode.value != AutoScrollMode.COMPLETION_BASED
        ) {
            return
        }

        synchronized(inspectionLock) {
            val now = System.currentTimeMillis()
            if (!force && now - lastScrollEventInspectMs < EVENT_INSPECT_THROTTLE_MS) return
            if (now - lastSwipeTimestampMs < COMPLETION_COOLDOWN_MS) return

            val rootNode = runCatching { rootInActiveWindow }.getOrNull() ?: return
            val rootPkg = rootNode.packageName?.toString()
            if (rootPkg == packageName) {
                recycleSafely(rootNode)
                return
            }
            if (!rootPkg.isNullOrBlank()) {
                lastObservedWindowPackage = rootPkg
                AutoVideoScrollManager.updateDetectedPlatform(
                    VideoCompletionDetector.recognizePlatformLabel(rootPkg)
                )
            }

            lastScrollEventInspectMs = now
            val snapshot = try {
                extractVideoProgress(rootNode)
            } finally {
                recycleSafely(rootNode)
            }

            processCompletionSnapshotLocked(snapshot)
        }
    }

    internal fun evaluateCompletionSnapshotForTest(
        snapshot: VideoProgressSnapshot,
        targetPackageName: String? = null,
        dispatchGesture: Boolean = false
    ): Boolean {
        if (!targetPackageName.isNullOrBlank()) {
            lastObservedWindowPackage = targetPackageName
            AutoVideoScrollManager.updateDetectedPlatform(
                VideoCompletionDetector.recognizePlatformLabel(targetPackageName)
            )
        }
        synchronized(inspectionLock) {
            return processCompletionSnapshotLocked(snapshot, dispatchGesture = dispatchGesture)
        }
    }

    private fun processCompletionSnapshotLocked(
        snapshot: VideoProgressSnapshot,
        dispatchGesture: Boolean = true
    ): Boolean {
        val completed = VideoCompletionDetector.isPlaybackCompletedOrLooped(
            previousRatio = previousVideoRatio,
            current = snapshot,
            previousElapsedSeconds = previousElapsedSeconds
        )
        if (completed) {
            previousVideoRatio = null
            previousElapsedSeconds = null
            val platform = VideoCompletionDetector.recognizePlatformLabel(lastObservedWindowPackage)
            val prefix = if (platform != null) "$platform • " else ""
            AutoVideoScrollManager.updateStatusDetail("${prefix}Playback ended • Scrolling next")
            if (dispatchGesture) {
                triggerAutoScrollSwipe()
            }
            return true
        }

        val currentRatio = snapshot.ratio ?: run {
            val e = snapshot.elapsedSeconds
            val t = snapshot.totalSeconds
            if (e != null && t != null && t > 0) e.toFloat() / t.toFloat() else null
        }
        if (currentRatio != null) {
            previousVideoRatio = currentRatio
        }
        if (snapshot.elapsedSeconds != null) {
            previousElapsedSeconds = snapshot.elapsedSeconds
        }
        return false
    }

    private fun isForegroundAppToolkit(): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull()
        val rootPkg = if (root != null) {
            try {
                root.packageName?.toString()
            } finally {
                recycleSafely(root)
            }
        } else {
            null
        }
        val pkg = rootPkg ?: lastObservedWindowPackage
        return pkg == packageName
    }

    private fun triggerAutoScrollSwipe() {
        lastSwipeTimestampMs = System.currentTimeMillis()
        mainHandler.post {
            performSwipeUpGesture { success ->
                if (success) {
                    AutoVideoScrollManager.incrementScrollCount()
                }
            }
        }
    }

    fun performSwipeUpGesture(onComplete: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete?.invoke(false)
            return
        }

        val (widthPx, heightPx) = resolveDisplayDimensions()
        val coords = computeSwipeCoordinates(widthPx, heightPx)

        reusablePath.reset()
        reusablePath.moveTo(coords.startX, coords.startY)
        reusablePath.lineTo(coords.endX, coords.endY)

        val stroke = GestureDescription.StrokeDescription(reusablePath, 0L, SWIPE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onComplete?.invoke(false)
            }
        }, mainHandler)

        if (!dispatched) {
            onComplete?.invoke(false)
        }
    }

    private fun resolveDisplayDimensions(): Pair<Int, Int> {
        return runCatching {
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                bounds.width() to bounds.height()
            } else {
                val dm = resources.displayMetrics
                dm.widthPixels to dm.heightPixels
            }
        }.getOrElse {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun extractVideoProgress(root: AccessibilityNodeInfo): VideoProgressSnapshot {
        var preferredSeekRatio: Float? = null
        var fallbackRangeRatio: Float? = null
        var elapsed: Int? = null
        var total: Int? = null
        var replayCue = false

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var visited = 0
        val maxNodes = 100
        val maxDepth = 10

        while (queue.isNotEmpty() && visited < maxNodes) {
            val (node, depth) = queue.removeFirst()
            visited++

            val className = node.className?.toString().orEmpty()
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()
            val isSeekOrProgressNode = className.contains("SeekBar", ignoreCase = true) ||
                className.contains("ProgressBar", ignoreCase = true) ||
                className.contains("Slider", ignoreCase = true) ||
                viewId.contains("seek") ||
                viewId.contains("progress") ||
                viewId.contains("scrubber") ||
                viewId.contains("timeline")

            val range = node.rangeInfo
            if (range != null && range.max > range.min) {
                val normalized = ((range.current - range.min) / (range.max - range.min)).coerceIn(0f, 1f)
                if (isSeekOrProgressNode) {
                    preferredSeekRatio = normalized
                } else if (fallbackRangeRatio == null) {
                    fallbackRangeRatio = normalized
                }
            }

            val text = node.text
            val desc = node.contentDescription
            if (VideoCompletionDetector.hasReplayCue(text) || VideoCompletionDetector.hasReplayCue(desc)) {
                replayCue = true
            }

            val parsed = VideoCompletionDetector.parseElapsedAndTotalSeconds(text)
                ?: VideoCompletionDetector.parseElapsedAndTotalSeconds(desc)
            if (parsed != null) {
                elapsed = parsed.first
                total = parsed.second
            }

            if (replayCue || (preferredSeekRatio != null && preferredSeekRatio >= 0.96f)) {
                if (node !== root) recycleSafely(node)
                break
            }

            if (depth < maxDepth) {
                val childCount = node.childCount.coerceAtMost(12)
                for (i in 0 until childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(child to depth + 1)
                    }
                }
            }

            if (node !== root) {
                recycleSafely(node)
            }
        }

        while (queue.isNotEmpty()) {
            val leftover = queue.removeFirst().first
            if (leftover !== root) recycleSafely(leftover)
        }

        return VideoProgressSnapshot(
            ratio = preferredSeekRatio ?: fallbackRangeRatio,
            elapsedSeconds = elapsed,
            totalSeconds = total,
            hasReplayCue = replayCue
        )
    }

    @Suppress("DEPRECATION")
    private fun recycleSafely(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { node.recycle() }
        }
    }

    companion object {
        private const val TAG = "ArworaCoreAccessibility"
        private const val SETTINGS_PACKAGE = "com.android.settings"
        private const val GUARD_EVENT_THROTTLE_MS = 750L
        private const val LOCK_PROMPT_COOLDOWN_MS = 900L
        private const val SWIPE_DURATION_MS = 340L
        private const val COMPLETION_COOLDOWN_MS = 2_500L
        private const val EVENT_INSPECT_THROTTLE_MS = 350L
        private const val COMPLETION_POLL_INTERVAL_MS = 500L

        private val INSTALLER_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller",
            "com.oplus.packageinstaller",
            "com.oneplus.packageinstaller",
            "com.vivo.packageinstaller"
        )

        @Volatile
        var instance: ArworaCoreAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null

        fun computeSwipeCoordinates(widthPx: Int, heightPx: Int): SwipeCoordinates {
            val safeWidth = widthPx.coerceAtLeast(320).toFloat()
            val safeHeight = heightPx.coerceAtLeast(480).toFloat()
            val centerX = safeWidth * 0.5f
            return SwipeCoordinates(
                startX = centerX,
                startY = safeHeight * 0.78f,
                endX = centerX,
                endY = safeHeight * 0.22f
            )
        }

        fun isAccessibilityEnabled(context: Context): Boolean {
            if (instance != null) return true
            return runCatching {
                val expectedComponent = ComponentName(
                    context.packageName,
                    ArworaCoreAccessibilityService::class.java.name
                )
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                if (am != null && am.isEnabled) {
                    val enabledList = am.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                    ).orEmpty()
                    val matchedInManager = enabledList.any { info ->
                        val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                        serviceInfo.packageName == expectedComponent.packageName &&
                            serviceInfo.name == expectedComponent.className
                    }
                    if (matchedInManager) {
                        return@runCatching true
                    }
                }

                val accessibilityMasterEnabled = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                ) == 1
                if (!accessibilityMasterEnabled && am?.isEnabled != true) {
                    return@runCatching false
                }

                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return@runCatching false

                val expectedFull = expectedComponent.flattenToString()
                val expectedShort = expectedComponent.flattenToShortString()
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServices)
                while (colonSplitter.hasNext()) {
                    val entry = colonSplitter.next()
                    if (entry.equals(expectedFull, ignoreCase = true) ||
                        entry.equals(expectedShort, ignoreCase = true)
                    ) {
                        return@runCatching true
                    }
                }
                false
            }.getOrDefault(false)
        }

        fun openAccessibilitySettings(context: Context) {
            runCatching {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
}
