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
        return try {
            val configManager = ControlConfig(applicationContext)

            // 1. Firebase yapılandırmasını çek ve kaydet
            configManager.fetchConfig()

            // 2. Günlük zamanlayıcı işinin sıraya eklendiğinden emin ol
            DailySchedulerWorker.enqueueWork(applicationContext)

            // 3. (ÖNEMLİ!) Planner.scheduleAllNotifications() çağrısı buradan kaldırıldı.
            // Artık planlama sadece DailySchedulerWorker tarafından yapılacak.

            Log.i(TAG, "Remote config sync successful and daily scheduler ensured.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Remote config sync failed: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "RemoteConfigSyncWork"
        private const val SYNC_INTERVAL_MINUTES = 60L // Her 60 dakikada bir

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