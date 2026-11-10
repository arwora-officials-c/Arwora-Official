package com.arwora.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.arwora.utils.AutoClickerManager
import com.arwora.utils.ClickMode
import com.arwora.utils.StopType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlin.random.Random

class AutoClickerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickerService"
        var instance: AutoClickerAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }

    private sealed class GestureTask {
        data class Tap(val x: Float, val y: Float, val durationMs: Long, val onComplete: (() -> Unit)? = null) : GestureTask()
        data class Swipe(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val durationMs: Long, val onComplete: (() -> Unit)? = null) : GestureTask()
    }

    private var clickJob: Job? = null
    private var gestureConsumerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // High-capacity buffered Channel with DROP_OLDEST policy to prevent Main Thread backpressure and UI freezes at 10ms intervals
    private val gestureChannel = Channel<GestureTask>(capacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val reusablePath = Path()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        startGestureConsumer()
        Log.d(TAG, "AutoClickerAccessibilityService connected with non-blocking 10ms high-frequency coroutine loop")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.d(TAG, "AutoClickerAccessibilityService interrupted")
        stopClicking()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClicking()
        gestureConsumerJob?.cancel()
        serviceScope.cancel()
        if (instance == this) {
            instance = null
        }
    }

    private fun startGestureConsumer() {
        if (gestureConsumerJob?.isActive == true) return
        gestureConsumerJob = serviceScope.launch(Dispatchers.Default) {
            for (task in gestureChannel) {
                if (!isActive) break
                val deferred = CompletableDeferred<Unit>()
                mainHandler.post {
                    when (task) {
                        is GestureTask.Tap -> executeTapInternal(task.x, task.y, task.durationMs) {
                            task.onComplete?.invoke()
                            deferred.complete(Unit)
                        }
                        is GestureTask.Swipe -> executeSwipeInternal(task.startX, task.startY, task.endX, task.endY, task.durationMs) {
                            task.onComplete?.invoke()
                            deferred.complete(Unit)
                        }
                    }
                }
                try {
                    withTimeout(100L) { deferred.await() }
                } catch (e: Exception) {
                    // Prevent thread stalling if accessibility callback is delayed
                }
            }
        }
    }

    private fun executeTapInternal(x: Float, y: Float, durationMs: Long, onComplete: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete()
            return
        }

        reusablePath.reset()
        reusablePath.moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))

        val stroke = GestureDescription.StrokeDescription(reusablePath, 0, durationMs.coerceAtLeast(2L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                AutoClickerManager.incrementClicks(1)
                onComplete()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onComplete()
            }
        }, mainHandler)
    }

    private fun executeSwipeInternal(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long, onComplete: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onComplete()
            return
        }

        reusablePath.reset()
        reusablePath.moveTo(startX.coerceAtLeast(0f), startY.coerceAtLeast(0f))
        reusablePath.lineTo(endX.coerceAtLeast(0f), endY.coerceAtLeast(0f))

        val stroke = GestureDescription.StrokeDescription(reusablePath, 0, durationMs.coerceAtLeast(10L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                AutoClickerManager.incrementClicks(1)
                onComplete()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onComplete()
            }
        }, mainHandler)
    }

    fun dispatchTap(x: Float, y: Float, durationMs: Long = 10L, onComplete: (() -> Unit)? = null) {
        gestureChannel.trySend(GestureTask.Tap(x, y, durationMs, onComplete))
    }

    fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 100L, onComplete: (() -> Unit)? = null) {
        gestureChannel.trySend(GestureTask.Swipe(startX, startY, endX, endY, durationMs, onComplete))
    }

    // Optimized Non-Blocking High-Frequency Coroutine Click Loop with Elapsed Time Correction
    fun startClicking() {
        if (clickJob?.isActive == true) return
        AutoClickerManager.setClickingState(true)

        clickJob = serviceScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            var executedCycles = 0

            val stopType = AutoClickerManager.stopType.value
            val maxMins = AutoClickerManager.stopTimerMinutes.value
            val maxCycles = AutoClickerManager.stopRepeatCount.value
            val maxDurationMs = maxMins * 60 * 1000L

            while (isActive) {
                val loopStart = System.currentTimeMillis()

                if (stopType == StopType.TIMER) {
                    if (loopStart - startTime >= maxDurationMs) break
                } else if (stopType == StopType.REPEAT_COUNT) {
                    if (executedCycles >= maxCycles) break
                }

                val mode = AutoClickerManager.mode.value
                val baseInterval = AutoClickerManager.clickIntervalMs.value.coerceAtLeast(10L)
                val tapDuration = AutoClickerManager.tapDurationMs.value.coerceAtLeast(5L)
                val randomize = AutoClickerManager.randomizeJitter.value

                when (mode) {
                    ClickMode.SINGLE -> {
                        val target = AutoClickerManager.targets.value.firstOrNull()
                        if (target != null) {
                            val targetX = if (randomize) target.x + Random.nextInt(-2, 3) else target.x
                            val targetY = if (randomize) target.y + Random.nextInt(-2, 3) else target.y

                            // Non-blocking fire-and-forget dispatch to Channel to avoid main thread freeze
                            dispatchTap(targetX, targetY, tapDuration)
                        }
                    }

                    ClickMode.MULTI -> {
                        val targets = AutoClickerManager.targets.value
                        for (target in targets) {
                            if (!isActive) break
                            val targetX = if (randomize) target.x + Random.nextInt(-2, 3) else target.x
                            val targetY = if (randomize) target.y + Random.nextInt(-2, 3) else target.y

                            dispatchTap(targetX, targetY, tapDuration)
                            delay(target.delayMs.coerceAtLeast(5L))
                        }
                    }

                    ClickMode.SWIPE -> {
                        val swipe = AutoClickerManager.swipe.value
                        dispatchSwipe(swipe.startX, swipe.startY, swipe.endX, swipe.endY, swipe.durationMs)
                    }
                }

                // Precise Elapsed-Time Correction for 10ms intervals without drifting or UI blocking
                val elapsed = System.currentTimeMillis() - loopStart
                val remainingDelay = baseInterval - elapsed
                if (remainingDelay > 0) {
                    delay(remainingDelay)
                } else {
                    yield() // Yield control to prevent CPU starvation and allow cancellation
                }

                executedCycles++
            }

            withContext(Dispatchers.Main) {
                AutoClickerManager.setClickingState(false)
            }
        }
    }

    fun stopClicking() {
        clickJob?.cancel()
        clickJob = null
        AutoClickerManager.setClickingState(false)
    }

    fun toggleClicking() {
        if (AutoClickerManager.isClicking.value) {
            stopClicking()
        } else {
            startClicking()
        }
    }
}
