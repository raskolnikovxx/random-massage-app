package com.example.hakanbs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Boot received. Re-scheduling alarms and sync worker.")

            // Suspend fonksiyon coroutine ile çağrılmalı
            CoroutineScope(Dispatchers.IO).launch {
                val fetched = ControlConfig(context).fetchConfig()
                if (fetched != null) {
                    Planner(context, fetched).scheduleAllNotifications()
                    Log.i(TAG, "Planner.scheduleAllNotifications triggered after fetch.")
                } else {
                    Log.w(TAG, "Firebase config alınamadı, alarm planlanmadı!")
                }
            }

            // 2. WorkManager'ı yeniden başlat
            SyncRemoteWorker.schedule(context)
        }
    }
}