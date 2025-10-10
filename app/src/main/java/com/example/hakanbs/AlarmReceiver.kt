package com.example.hakanbs

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver

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

            // 1. Mesajı seç (Bu mesajın içinde artık imageUrl de var)
            val selectedSentence = findAndSelectSentence(config, historyStore, messageId)

            if (selectedSentence == null) {
                Log.w(TAG, "No valid message found for notification.")
                return@launch
            }

            // *** BURASI DEĞİŞTİ: Fotoğrafı doğrudan seçilen cümlenin içinden alıyoruz ***
            val imageUrl = selectedSentence.imageUrl

            // 2. Geçmişe kaydet
            val notificationHistory = saveAndGetHistory(historyStore, selectedSentence, imageUrl)
            val historyId = notificationHistory.id

            // 3. Bildirimi göster
            NotificationHelper.showNotification(context, selectedSentence, imageUrl, historyId)

            // 4. Cümlenin ID'sini "görüldü" olarak işaretle
            historyStore.addSeenSentenceId(selectedSentence.id)

            // 5. Sonraki alarmı planla
            Planner(context, config).scheduleAllNotifications()
        }
    }

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
            context = selectedSentence.context,
            audioUrl = selectedSentence.audioUrl,
            videoUrl = selectedSentence.videoUrl

        )
        historyStore.addNotificationToHistory(history)
        return history
    }
}