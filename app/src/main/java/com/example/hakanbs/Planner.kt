package com.example.hakanbs

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class Planner(private val context: Context, private val config: RemoteConfig) {
    private val TAG = "Planner"
    private val ALARM_ID_BASE = 100
    private val PREFS_NAME = "AlarmSchedulerPrefs"
    private val PREF_SCHEDULE_KEY = "daily_scheduled_times"
    private val PREF_SCHEDULE_DATE = "last_schedule_date"

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    data class OverrideData(val messageId: String?, val imageUrl: String?)

    fun scheduleAllNotifications() {
        if (!config.enabled) {
            cancelAllNotifications()
            Log.d(TAG, "Config disabled. No alarms scheduled.")
            return
        }

        cancelAllNotifications()

        // 1. Sadece saat 23:00 için olan rastgele bildirimi al
        val dailyFixedTime = getOrCreateDailyFixedTime()
        val finalScheduledTimes = dailyFixedTime.toMutableMap()

        // 2. JSON'daki Override Saatlerini bu listeye Ekle/Üzerine Yaz
        config.overrides.forEach { remoteOverride ->
            val parts = remoteOverride.time.split(":")
            if (parts.size == 2) {
                try {
                    val hour = parts[0].toInt()
                    val minute = parts[1].toInt()
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)

                        if (before(Calendar.getInstance())) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                    finalScheduledTimes[calendar.timeInMillis] = OverrideData(remoteOverride.messageId, remoteOverride.imageUrl)

                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Invalid override time format: ${remoteOverride.time}", e)
                }
            }
        }

        // 3. Birleştirilmiş listedeki tüm alarmları planla
        finalScheduledTimes.toSortedMap().forEach { (timeMillis, data) ->
            scheduleNotification(timeMillis, data.messageId, data.imageUrl)
        }

        Log.d(TAG, "Total ${finalScheduledTimes.size} notifications scheduled.")
    }

    private fun getOrCreateDailyFixedTime(): Map<Long, OverrideData> {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastScheduledDay = prefs.getInt(PREF_SCHEDULE_DATE, 0)

        if (today == lastScheduledDay) {
            val json = prefs.getString(PREF_SCHEDULE_KEY, null)
            if (json != null) {
                val type = object : TypeToken<Map<Long, OverrideData>>() {}.type
                Log.d(TAG, "Using existing 23:00 schedule for today.")
                return gson.fromJson(json, type) ?: emptyMap()
            }
        }

        Log.d(TAG, "Calculating new 23:00 schedule for today.")
        val newScheduledTime = calculateDailyFixedTime()
        val json = gson.toJson(newScheduledTime)
        prefs.edit()
            .putString(PREF_SCHEDULE_KEY, json)
            .putInt(PREF_SCHEDULE_DATE, today)
            .apply()

        return newScheduledTime
    }

    private fun calculateDailyFixedTime(): Map<Long, OverrideData> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return mapOf(calendar.timeInMillis to OverrideData(null, null))
    }

    private fun scheduleNotification(timeMillis: Long, messageId: String?, imageUrl: String?) {
        val requestCode = (timeMillis / 1000).toInt() + ALARM_ID_BASE

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            messageId?.let { id -> putExtra("EXTRA_MESSAGE_ID", id) }
            imageUrl?.let { url -> putExtra("EXTRA_IMAGE_URL", url) }
        }

        // DÜZELTME BURADA YAPILDI
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            timeMillis,
            pendingIntent
        )

        val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
        Log.d(TAG, "Alarm scheduled for: ${calendar.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", calendar.get(Calendar.MINUTE))}")
    }

    private fun cancelAllNotifications() {
        for (i in ALARM_ID_BASE until ALARM_ID_BASE + 300) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                i,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
            }
        }
        Log.d(TAG, "All previous alarms canceled.")
    }
}