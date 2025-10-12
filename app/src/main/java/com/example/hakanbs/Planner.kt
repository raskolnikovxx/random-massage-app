package com.example.hakanbs

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

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

    /**
     * Schedule notifications for the day.
     * If forceReschedule == false and we've already scheduled for today, this becomes a no-op.
     */
    fun scheduleAllNotifications(forceReschedule: Boolean = true) {
        val todayKey = getTodayString()
        if (!forceReschedule) {
            val lastDate = prefs.getString(PREF_SCHEDULE_DATE, null)
            if (lastDate == todayKey) {
                Log.d(TAG, "Already scheduled for today; skipping reschedule as not forced.")
                return
            }
        }

        if (!config.enabled) {
            cancelAllNotifications()
            Log.d(TAG, "Config disabled. No alarms scheduled.")
            prefs.edit().putString(PREF_SCHEDULE_DATE, todayKey).apply()
            return
        }

        cancelAllNotifications()

        // 1) Schedule overrides (explicit times from remote config)
        var scheduledCount = 0
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
                    scheduleNotification(calendar.timeInMillis, remoteOverride.messageId, remoteOverride.imageUrl)
                    scheduledCount++
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Invalid override time format: ${remoteOverride.time}", e)
                }
            }
        }

        // 2) Schedule a single daily random notification (22:00-23:00) from the sentences
        // Exclude sentences referenced by overrides when selecting the random daily message
        val overrideIds = config.overrides.mapNotNull { it.messageId }.toSet()
        val candidateSentences = config.sentences.filter { it.id !in overrideIds }

        if (candidateSentences.isNotEmpty()) {
            // Choose a random minute between 22:00 and 22:59
            val randomMinute = (0..59).random()
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 22)
                set(Calendar.MINUTE, randomMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            // No fixed messageId -> AlarmReceiver will pick an available sentence (not in seen list)
            scheduleNotification(calendar.timeInMillis, null, null)
            scheduledCount++
        } else {
            Log.w(TAG, "No candidate sentences available for daily random notification (all are overrides or empty).")
        }

        // mark today's schedule time to avoid re-scheduling repeatedly
        prefs.edit().putString(PREF_SCHEDULE_DATE, todayKey).apply()

        Log.d(TAG, "Total $scheduledCount notifications scheduled for today (including overrides and daily random).")
    }

    private fun getTodayString(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Calendar.getInstance().time)
    }

    private fun scheduleNotification(timeMillis: Long, messageId: String?, imageUrl: String?) {
        val requestCode = (timeMillis / 1000).toInt() + ALARM_ID_BASE
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            messageId?.let { id -> putExtra("EXTRA_MESSAGE_ID", id) }
            imageUrl?.let { url -> putExtra("EXTRA_IMAGE_URL", url) }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setExactAndAllowWhileIdle when available so the alarm fires close to the chosen time
        // First, check if we are allowed to schedule exact alarms (Android S/API 31+).
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                    } else {
                        Log.w(TAG, "App cannot schedule exact alarms (missing SCHEDULE_EXACT_ALARM). Scheduling with set() as fallback.")
                        alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                }
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception while checking/setting exact alarm, falling back to set(): ${e.message}")
            alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
        }

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