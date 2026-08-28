package com.rangia.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.automaticScan) return Result.success()

        val store = IndexStore(applicationContext)
        val scanner = DocumentScanner(applicationContext)
        val allFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

        return runCatching {
            val updated = if (prefs.wholePhoneMode && allFiles) {
                scanner.scanWholePhone(store.load())
            } else {
                val raw = prefs.treeUri ?: return Result.success()
                scanner.scanTree(Uri.parse(raw), store.load())
            }
            store.save(updated)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
