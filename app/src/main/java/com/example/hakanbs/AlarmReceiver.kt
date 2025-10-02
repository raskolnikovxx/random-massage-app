package com.example.hakanbs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmReceiver : BroadcastReceiver() {
    private val TAG = "AlarmReceiver"
    private val CHANNEL_ID = "SENTIMENTAL_NOTIF_CHANNEL"

    // Action sabitleri (Companion object içinde tutulur)
    companion object {
        const val ACTION_REACT = "com.example.hakanbs.ACTION_REACT"
        const val ACTION_PIN = "com.example.hakanbs.ACTION_PIN"
        const val ACTION_COMMENT = "com.example.hakanbs.ACTION_COMMENT"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        const val EXTRA_EMOJI = "extra_emoji"
        const val KEY_TEXT_REPLY = "key_text_reply" // Yorum anahtarı
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Broadcast Receiver'lar çok kısa sürmelidir, bu yüzden Coroutine Scope'u kullanılır.
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Alarm received, preparing notification.")

            val config = ControlConfig(context).getLocalConfig()
            val historyStore = HistoryStore(context)

            val messageId = intent.getStringExtra("EXTRA_MESSAGE_ID")
            val forcedImageUrl = intent.getStringExtra("EXTRA_IMAGE_URL")

            // Mesajı seç (override veya rastgele)
            val selectedSentence = findAndSelectSentence(config, historyStore, messageId)

            if (selectedSentence == null) {
                Log.w(TAG, "No valid message found for notification.")
                return@launch
            }

            // Rastgele görsel seçimi (Eğer override görseli yoksa ve %50 şans varsa)
            val imageUrl = forcedImageUrl ?: if (config.images.isNotEmpty() && Math.random() < 0.5) {
                config.images.random()
            } else {
                null
            }

            // Önce geçmişe kaydedelim, ID alalım
            val notificationHistory = saveAndGetHistory(historyStore, selectedSentence, imageUrl)
            val historyId = notificationHistory.id

            // Bildirimi göster (NotificationHelper'ı kullanır)
            NotificationHelper.showNotification(context, selectedSentence, imageUrl, historyId)

            // Cümlenin ID'sini "görüldü" olarak işaretle
            historyStore.addSeenSentenceId(selectedSentence.id)

            // Sonraki alarmı planla
            Planner(context, config).scheduleAllNotifications()
        }
    }

    // --- YARDIMCI FONKSİYONLAR ---

    private fun findAndSelectSentence(config: RemoteConfig, historyStore: HistoryStore, forcedMessageId: String?): RemoteSentence? {
        return if (forcedMessageId != null) {
            config.sentences.find { it.id == forcedMessageId }
        } else {
            val seenIds = historyStore.getSeenSentenceIds()
            val availableSentences = config.sentences.filter { it.id !in seenIds }
            if (availableSentences.isEmpty()) null else availableSentences.random()
        }
    }

    private fun saveAndGetHistory(historyStore: HistoryStore, selectedSentence: RemoteSentence, imageUrl: String?): NotificationHistory {
        val history = NotificationHistory(
            time = System.currentTimeMillis(),
            messageId = selectedSentence.id,
            message = selectedSentence.text,
            imageUrl = imageUrl,
            isQuote = selectedSentence.isQuote,
            context = selectedSentence.context
        )
        historyStore.addNotificationToHistory(history)
        return history
    }

    // NOTE: createNotificationChannel ve showNotificationWithActions metodları kaldırıldı
    // ve sorumlulukları NotificationHelper'a (hata ayıklama sürecinde) devredildi.
}