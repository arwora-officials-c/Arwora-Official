package com.arwora.feature.system.connect.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arwora.feature.system.connect.data.ArworaConnectManager

/**
 * Background [CoroutineWorker] executed when local Wi-Fi / network connectivity returns,
 * silently probing disconnected trusted peers to restore Arwora Connect links.
 */
class AutoReconnectWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!ArworaConnectManager.autoReconnectEnabled.value) {
            return Result.success()
        }
        val engine = ArworaConnectManager.getEngine(applicationContext)
        engine.probeAndAutoReconnectDisconnectedPeers()
        return Result.success()
    }
}
