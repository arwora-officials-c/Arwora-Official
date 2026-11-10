package com.arwora.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DnsScheduleWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val host = inputData.getString("dns_host") ?: "one.one.one.one"
        val success = DnsSecureSettingsHelper.setPrivateDns(applicationContext, "hostname", host)
        return if (success) Result.success() else Result.retry()
    }
}
