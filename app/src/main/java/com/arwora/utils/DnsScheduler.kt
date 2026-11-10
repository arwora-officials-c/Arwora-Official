package com.arwora.utils

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DnsScheduler {
    fun scheduleDns(context: Context, targetMillis: Long, host: String) {
        val delay = targetMillis - System.currentTimeMillis()
        if (delay <= 0) {
            DnsSecureSettingsHelper.setPrivateDns(context, "hostname", host)
            return
        }
        val data = Data.Builder().putString("dns_host", host).build()
        val request = OneTimeWorkRequestBuilder<DnsScheduleWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "scheduled_dns_work",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
