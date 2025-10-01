package com.example.hakanbs

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar
import kotlin.random.Random

class Planner(private val context: Context, private val config: RemoteConfig) {
    private val TAG = "Planner"
    private val ALARM_ID_BASE = 100
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Tüm alarmları iptal edip yeniden planlar
    fun scheduleAllNotifications() {
        if (!config.enabled) {
            cancelAllNotifications()
            Log.d(TAG, "Config disabled. No alarms scheduled.")
            return
        }

        cancelAllNotifications()

        val timesPerDay = config.timesPerDay
        val startHour = config.startHour
        val endHour = config.endHour
        val scheduledTimes = mutableMapOf<Long, String?>()

        // --- 1. Rastgele/Eşit Aralıklı Slotları Planla ---
        val distributionHours = endHour - startHour
        if (distributionHours <= 0 || timesPerDay <= 0) {
            Log.d(TAG, "Planning skipped due to invalid config.")
        } else {
            val totalMinutes = distributionHours * 60
            val slotDuration = totalMinutes / timesPerDay

            for (i in 0 until timesPerDay) {
                val slotStartMinute = startHour * 60 + i * slotDuration
                val slotEndMinute = startHour * 60 + (i + 1) * slotDuration
                val randomMinuteInSlot = Random.nextInt(slotStartMinute, slotEndMinute)

                // Yerel saat dilimi kullanılır (Cihazın saati)
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.HOUR_OF_DAY, randomMinuteInSlot / 60)
                    set(Calendar.MINUTE, randomMinuteInSlot % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)

                    if (before(Calendar.getInstance())) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                scheduledTimes[calendar.timeInMillis] = null
            }
        }

        // --- 2. Override Saatleri Ekle ---
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
                    scheduledTimes[calendar.timeInMillis] = remoteOverride.messageId
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Invalid override time format: ${remoteOverride.time}")
                }
            }
        }

        // --- 3. Alarmları Planla ---
        scheduledTimes.toSortedMap().forEach { (timeMillis, messageId) ->
            scheduleNotification(timeMillis, messageId)
        }

        Log.d(TAG, "Total ${scheduledTimes.size} notifications scheduled.")
    }

    // Tek bir bildirimi planlar (setExact kullanır)
    private fun scheduleNotification(timeMillis: Long, messageId: String?) {
        val requestCode = (timeMillis / 1000).toInt() + ALARM_ID_BASE

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            messageId?.let { id ->
                putExtra("EXTRA_MESSAGE_ID", id)
            }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setExact kullanıldı: İzin gerektirmez, küçük gecikmeler kabul edilir.
        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            timeMillis,
            pendingIntent
        )
        val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
        Log.d(TAG, "Alarm scheduled for: ${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)} (Message ID: $messageId)")
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