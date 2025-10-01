package com.example.hakanbs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Boot received. Re-scheduling alarms and sync worker.")

            // 1. Tüm günün alarmlarını yeniden planla (Yerel config ile)
            val config = ControlConfig(context).getLocalConfig()
            Planner(context, config).scheduleAllNotifications()

            // 2. WorkManager'ı yeniden başlat
            SyncRemoteWorker.schedule(context)
        }
    }
}