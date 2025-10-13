package com.example.hakanbs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    private val TAG = "AlarmReceiver"

    companion object {
        const val ACTION_REACT = "com.example.hakanbs.ACTION_REACT"
        const val ACTION_PIN = "com.example.hakanbs.ACTION_PIN"
        const val ACTION_COMMENT = "com.example.hakanbs.ACTION_COMMENT"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        const val EXTRA_EMOJI = "extra_emoji"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }

    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Alarm received, preparing notification.")

            val config = ControlConfig(context).getLocalConfig()
            val historyStore = HistoryStore(context)

            val messageId = intent.getStringExtra("EXTRA_MESSAGE_ID")
            val forcedImageUrl = intent.getStringExtra("EXTRA_IMAGE_URL")

            val selectedSentence = findAndSelectSentence(config, historyStore, messageId)

            if (selectedSentence == null) {
                Log.w(TAG, "No valid message found for notification.")
                return@launch
            }

            // imageUrl'i artık doğrudan cümlenin kendisinden alıyoruz
            val imageUrl = forcedImageUrl ?: selectedSentence.imageUrl

            // Önce geçmişe kaydedelim, ID alalım
            val notificationHistory = saveAndGetHistory(historyStore, selectedSentence, imageUrl)
            val historyId = notificationHistory.id

            // Bildirimi göster
            NotificationHelper.showNotification(context, selectedSentence, imageUrl, historyId)

            // Cümlenin ID'sini "görüldü" olarak işaretle
            historyStore.addSeenSentenceId(selectedSentence.id)

            // NOT: Artık her alarm sonrası tüm planları yeniden kurmuyoruz.
            // Günlük yeniden planlama için DailySchedulerWorker kullanılıyor (4:00'te).
        }
    }

    private fun findAndSelectSentence(config: RemoteConfig, historyStore: HistoryStore, forcedMessageId: String?): RemoteSentence? {
        if (forcedMessageId != null) {
            return config.sentences.find { it.id == forcedMessageId }
        }

        val seenIds = historyStore.getSeenSentenceIds()

        // Eğer randomDaily etkinse, seçim pool içinden yapılır; değilse tüm sentences içinden
        val poolSentences: List<RemoteSentence> = if (config.randomDaily.enabled) {
            // Map pool ids to actual sentences (ignore unknown ids)
            config.randomDaily.pool.mapNotNull { pid -> config.sentences.find { it.id == pid } }
        } else {
            config.sentences.toList()
        }

        var availableSentences = poolSentences.filter { it.id !in seenIds }

        // Eğer gösterilecek cümle kalmadıysa, "seen" listesini temizle ve tekrar baştan gösterime başla
        if (availableSentences.isEmpty()) {
            historyStore.clearSeenSentenceIds()
            // Yeniden hesapla: şimdi poolSentences içindeki tüm cümleler tekrar kullanılabilir
            availableSentences = poolSentences.toList()
        }

        if (availableSentences.isEmpty()) return null
        return availableSentences.random()
    }

    // --- DÜZELTİLMİŞ FONKSİYON ---
    private fun saveAndGetHistory(historyStore: HistoryStore, selectedSentence: RemoteSentence, imageUrl: String?): NotificationHistory {
        val history = NotificationHistory(
            time = System.currentTimeMillis(),
            messageId = selectedSentence.id,
            message = selectedSentence.text,
            imageUrl = imageUrl,
            isQuote = selectedSentence.isQuote,
            context = selectedSentence.context,
            // EKSİK OLAN SATIRLAR BURAYA EKLENDİ
            audioUrl = selectedSentence.audioUrl,
            videoUrl = selectedSentence.videoUrl
        )
        historyStore.addNotificationToHistory(history)
        return history
    }
}