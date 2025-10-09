package com.example.hakanbs

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {
    private val TAG = "ActionReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        // Bu Receiver'ın çok kısa sürede çalışması gerektiği için CoroutineScope kullanıldı.
        CoroutineScope(Dispatchers.IO).launch {
            val historyStore = HistoryStore(context)

            // AlarmReceiver'da tanımlanan sabitleri kullanırız
            val historyId = intent.getLongExtra(AlarmReceiver.Companion.EXTRA_HISTORY_ID, -1L)
            val action = intent.action

            if (historyId == -1L) return@launch

            when (action) {
                // Kalp Tepkisi aksiyonu
                AlarmReceiver.Companion.ACTION_REACT -> {
                    val emoji = intent.getStringExtra(AlarmReceiver.Companion.EXTRA_EMOJI)
                    // newComment = null ve newPinState = null olarak varsayılan çağrı yapılır.
                    historyStore.updateHistoryItem(historyId, newReaction = emoji)
                    Log.d(TAG, "Reacted with $emoji to ID: $historyId")
                }
                // Sabitleme aksiyonu
                AlarmReceiver.Companion.ACTION_PIN -> {
                    val currentHistory = historyStore.getHistory().find { it.id == historyId }
                    val newPinState = !(currentHistory?.isPinned ?: false)
                    historyStore.updateHistoryItem(historyId, newPinState = newPinState)
                    Log.d(TAG, "Pin toggled to $newPinState for ID: $historyId")
                }
            }

            // Bildirimi kapat
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(historyId.toInt())
        }
    }
}