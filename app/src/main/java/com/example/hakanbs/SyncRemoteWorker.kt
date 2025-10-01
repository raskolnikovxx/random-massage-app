package com.example.hakanbs

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncRemoteWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val TAG = "SyncRemoteWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncRemoteWorker started.")
        val controlConfig = ControlConfig(applicationContext)

        // 1. Firebase'den konfigürasyonu çek
        val newConfig = controlConfig.fetchConfig() ?: controlConfig.getLocalConfig()

        // 2. Alarmları yeni/eski konfigürasyon ile yeniden planla
        Planner(applicationContext, newConfig).scheduleAllNotifications()

        Log.d(TAG, "SyncRemoteWorker finished. Alarms re-scheduled.")
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "RemoteConfigSyncWork"
        private const val SYNC_INTERVAL_MINUTES = 3L // Her 3 dakikada bir

        fun schedule(context: Context) {
            val syncRequest = PeriodicWorkRequestBuilder<SyncRemoteWorker>(
                SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
            Log.d("SyncRemoteWorker", "WorkManager scheduled for $SYNC_INTERVAL_MINUTES minutes.")
        }
    }
}