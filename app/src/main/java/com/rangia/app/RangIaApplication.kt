package com.rangia.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class RangIaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val work = PeriodicWorkRequestBuilder<ScanWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "rangia_periodic_scan",
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }
}
