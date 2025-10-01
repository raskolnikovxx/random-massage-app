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

    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Alarm received, preparing notification.")
            val config = ControlConfig(context).getLocalConfig()
            val historyStore = HistoryStore(context)

            val forcedMessageId = intent.getStringExtra("EXTRA_MESSAGE_ID")

            val selectedSentence: RemoteSentence? = if (forcedMessageId != null) {
                // 1. Zorunlu Mesajı Kullan
                Log.d(TAG, "Override triggered for message ID: $forcedMessageId")
                config.sentences.find { it.id == forcedMessageId }
            } else {
                // 2. Rastgele Mesajı Kullan (Standard mantık)
                val seenIds = historyStore.getSeenSentenceIds()
                val availableSentences = config.sentences.filter { it.id !in seenIds }

                if (availableSentences.isEmpty()) { null } else { availableSentences.random() }
            }

            if (selectedSentence == null) {
                Log.w(TAG, "No available sentence found or forced message ID invalid.")
                return@launch
            }

            // Rastgele görsel seçimi
            val imageUrl = if (config.images.isNotEmpty() && Math.random() < 0.5) {
                config.images.random()
            } else {
                null
            }

            // Bildirimi göster
            NotificationHelper.showNotification(context, selectedSentence.text, imageUrl)

            // Cümlenin ID'sini "görüldü" olarak işaretle
            historyStore.addSeenSentenceId(selectedSentence.id)

            // Bildirim geçmişine ekle
            val history = NotificationHistory(
                time = System.currentTimeMillis(),
                messageId = selectedSentence.id,
                message = selectedSentence.text,
                imageUrl = imageUrl,
                isQuote = selectedSentence.isQuote,
                context = selectedSentence.context
            )
            historyStore.addNotificationToHistory(history)

            Log.d(TAG, "Notification shown: ${selectedSentence.id}, Image: $imageUrl")
        }
    }
}