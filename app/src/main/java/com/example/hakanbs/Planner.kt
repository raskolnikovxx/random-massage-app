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
        val limit = config.timesPerDay
        val scheduledToday = prefs.getInt("scheduled_count_$todayKey", 0)
        var scheduledCount = 0
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

        // Debug: log config summary
        try {
            val overrideList = config.overrides.map { it.time + " -> " + (it.messageId ?: "<none>") }
            val sentenceIds = config.sentences.map { it.id }
            Log.d(TAG, "Scheduling with config: enabled=${config.enabled}, overrides=${overrideList.size}, overrideDetails=${overrideList}, totalSentences=${sentenceIds.size}, sentenceIds=${sentenceIds}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log config summary: ${e.message}")
        }

        cancelAllNotifications()

        // 1) Schedule overrides (explicit times from remote config)
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

        // 2) Schedule random notifications (timesPerDay adet, startHour-endHour aralığında)
        val overrideIds = config.overrides.mapNotNull { it.messageId }.toSet()
        val localSentences = ControlConfig(context).getLocalConfig().sentences
        val allSentences = config.sentences + localSentences
        val candidateSentences = allSentences.filter { it.id !in overrideIds }
        if (candidateSentences.isNotEmpty() && config.timesPerDay > 0) {
            val startHour = config.startHour
            val endHour = config.endHour
            val timesPerDay = config.timesPerDay
            val totalMinutes = (endHour - startHour) * 60
            val rnd = Random(System.currentTimeMillis())

            // --- RANDOM ALARM KAYIT/OKUMA ---
            val randomPrefsKey = "random_times_${todayKey}_${startHour}_${endHour}_${timesPerDay}"
            val randomPrefs = prefs.getString(randomPrefsKey, null)
            val gson = Gson()
            data class RandomAlarm(val minuteOffset: Int, val messageId: String)
            val randomAlarms: List<RandomAlarm> = if (randomPrefs != null) {
                try {
                    gson.fromJson(randomPrefs, Array<RandomAlarm>::class.java).toList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                val randomMinutes = mutableSetOf<Int>()
                while (randomMinutes.size < timesPerDay && totalMinutes > 0) {
                    val minute = rnd.nextInt(0, totalMinutes)
                    randomMinutes.add(minute)
                }
                val selectedSentences = List(timesPerDay) { idx -> candidateSentences[idx % candidateSentences.size] }
                val alarms = randomMinutes.toList().sorted().mapIndexed { idx, minuteOffset ->
                    RandomAlarm(minuteOffset, selectedSentences[idx].id)
                }
                prefs.edit().putString(randomPrefsKey, gson.toJson(alarms)).apply()
                alarms
            }
            randomAlarms.forEach { alarm ->
                val sentence = candidateSentences.find { it.id == alarm.messageId } ?: candidateSentences.random(rnd)
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.HOUR_OF_DAY, startHour + (alarm.minuteOffset / 60))
                    set(Calendar.MINUTE, alarm.minuteOffset % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (before(Calendar.getInstance())) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                scheduleNotification(calendar.timeInMillis, sentence.id, sentence.imageUrl)
                scheduledCount++
            }
        } else {
            Log.w(TAG, "No candidate sentences available for daily random notifications (all are overrides or empty).")
        }

        // mark today's schedule time to avoid re-scheduling repeatedly
        prefs.edit().putString(PREF_SCHEDULE_DATE, todayKey).apply()
        prefs.edit().putInt("scheduled_count_$todayKey", scheduledCount).apply()

        Log.d(TAG, "Total $scheduledCount notifications scheduled for today (overrides + daily random).")
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

        // Debug: hangi messageId ile planlandığını logla
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        Log.d(TAG, "Scheduling alarm for messageId=${messageId ?: "<random>"} imageUrl=${imageUrl ?: "<none>"} at ${cal.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", cal.get(Calendar.MINUTE))}")

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