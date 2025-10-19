package com.example.hakanbs

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailySchedulerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // TAG tanımı eklendi
    private val TAG = "DailySchedulerWorker"

    override suspend fun doWork(): Result {
        return try {
            val configManager = ControlConfig(applicationContext)

            // 1. Firebase yapılandırmasını çek
            val fetched = configManager.fetchConfig()
            if (fetched != null) {
                Planner(applicationContext, fetched).scheduleAllNotifications()
                Log.i(TAG, "Planner.scheduleAllNotifications triggered after fetch.")
            } else {
                Log.w(TAG, "Firebase config alınamadı, alarm planlanmadı!")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in daily scheduling: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_TAG = "DailyScheduleJob"

        // Çalışma isteğini oluşturur ve her gece 4:00 AM'e ayarlar
        fun enqueueWork(context: Context) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 4) // 4 AM
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

            // Eğer şu an 4:00 AM'den sonra ise, işi yarının 4:00 AM'ine ayarla.
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val initialDelay = target.timeInMillis - now.timeInMillis

            val dailyRequest = androidx.work.PeriodicWorkRequestBuilder<DailySchedulerWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                dailyRequest
            )
            Log.d("DailySchedulerWorker", "Daily Scheduler Worker enqueued successfully to run at 4:00 AM daily.")
        }
    }
}