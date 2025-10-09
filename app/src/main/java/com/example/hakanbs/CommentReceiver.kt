package com.example.hakanbs

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.RemoteInput

class CommentReceiver : BroadcastReceiver() {
    private val TAG = "CommentReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val historyStore = HistoryStore(context)

        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val commentText = remoteInput?.getCharSequence(AlarmReceiver.Companion.KEY_TEXT_REPLY)?.toString()

        val historyId = intent.getLongExtra(AlarmReceiver.Companion.EXTRA_HISTORY_ID, -1L)

        if (historyId != -1L && !commentText.isNullOrBlank()) {
            // HATA ÇÖZÜMÜ: updateHistoryItem yerine addNoteToHistoryItem çağrıldı
            historyStore.addNoteToHistoryItem(historyId, commentText)

            Log.d(TAG, "Comment saved for ID: $historyId, Text: $commentText")}

        // Bildirimi kapat
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(historyId.toInt())
    }
}