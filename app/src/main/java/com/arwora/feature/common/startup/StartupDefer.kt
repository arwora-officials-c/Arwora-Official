package com.arwora.feature.common.startup

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object StartupDefer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isFirstFrameRendered = false
    private val pendingTasks = mutableListOf<suspend () -> Unit>()

    fun postFirstFrame(task: suspend () -> Unit) = scope.launch { task() }

    /**
     * Marks that MainActivity has completed its first drawn frame and flushes deferred startup tasks.
     */
    fun markFirstFrameDrawn() {
        flushTasks()
    }

    /**
     * Schedules a block to run strictly after the first frame has rendered.
     * Uses Choreographer postFrameCallback on the main thread,
     * executing tasks on Dispatchers.Default without blocking cold start or UI paint.
     */
    fun schedulePostFirstFrame(app: Application, block: suspend () -> Unit) {
        if (isFirstFrameRendered) {
            scope.launch {
                try {
                    block()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            return
        }

        synchronized(pendingTasks) {
            if (isFirstFrameRendered) {
                scope.launch {
                    try {
                        block()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                return
            }
            pendingTasks.add(block)
        }

        Handler(Looper.getMainLooper()).post {
            try {
                Choreographer.getInstance().postFrameCallback {
                    // Let the frame finish drawing before executing deferred jobs
                    Handler(Looper.getMainLooper()).post {
                        flushTasks()
                    }
                }
            } catch (e: Throwable) {
                // Fallback for background or headless environments
                Handler(Looper.getMainLooper()).postDelayed({
                    flushTasks()
                }, 100)
            }
        }
    }

    private fun flushTasks() {
        val tasks = synchronized(pendingTasks) {
            if (isFirstFrameRendered) return
            isFirstFrameRendered = true
            val copy = pendingTasks.toList()
            pendingTasks.clear()
            copy
        }

        scope.launch {
            tasks.forEach { task ->
                try {
                    task()
                } catch (err: Throwable) {
                    err.printStackTrace()
                }
            }
        }
    }
}
