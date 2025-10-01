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

class AlarmReceiver : BroadcastReceiver() {
    private val TAG = "AlarmReceiver"
    private val CHANNEL_ID = "SENTIMENTAL_NOTIF_CHANNEL"

    // Action sabitleri
    companion object {
        const val ACTION_REACT = "com.example.hakanbs.ACTION_REACT"
        const val ACTION_PIN = "com.example.hakanbs.ACTION_PIN"
        const val ACTION_COMMENT = "com.example.hakanbs.ACTION_COMMENT"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        const val EXTRA_EMOJI = "extra_emoji"
        const val KEY_TEXT_REPLY = "key_text_reply" // Yorum anahtarı
    }

    override fun onReceive(context: Context, intent: Intent) {
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

            // Bildirimi göster
            showNotificationWithActions(context, selectedSentence, imageUrl, historyId)

            // Cümlenin ID'sini "görüldü" olarak işaretle (Hata veren metodlar artık burada düzgün çağrılıyor)
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
            // Hata veren metod artık düzgün çağrılıyor.
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

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Anı Bildirimleri"
            val descriptionText = "Eşinizden gelen günlük anı bildirimleri"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotificationWithActions(context: Context, sentence: RemoteSentence, imageUrl: String?, historyId: Long) {
        createNotificationChannel(context)

        // Main Activity Intent
        val mainIntent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sentence.text)
            .setContentText(sentence.context ?: "Yeni bir anı!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVibrate(longArrayOf(100, 200, 300, 400))
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)

        // --- 1. Tepki Butonları ---

        // Kalp Tepkisi (ActionReceiver hatası çözüldü)
        val reactIntent = Intent(context, ActionReceiver::class.java).apply {
            action = Companion.ACTION_REACT
            putExtra(Companion.EXTRA_HISTORY_ID, historyId)
            putExtra(Companion.EXTRA_EMOJI, "❤️")
        }
        val reactPendingIntent = PendingIntent.getBroadcast(context, (historyId + 100).toInt(), reactIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(R.drawable.ic_react_heart, "Sevdim", reactPendingIntent)

        // Sabitleme Butonu
        val pinIntent = Intent(context, ActionReceiver::class.java).apply {
            action = Companion.ACTION_PIN
            putExtra(Companion.EXTRA_HISTORY_ID, historyId)
        }
        val pinPendingIntent = PendingIntent.getBroadcast(context, (historyId + 200).toInt(), pinIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(R.drawable.ic_pin, "Sabitle", pinPendingIntent)


        // --- 2. YORUM BUTONU EKLENİYOR (CommentReceiver hatası çözüldü) ---
        val replyLabel = "Yorum Yaz"

        val remoteInput: RemoteInput = RemoteInput.Builder(Companion.KEY_TEXT_REPLY).run {
            setLabel(replyLabel)
            build()
        }

        // Yorumu işleyecek olan Intent
        val replyIntent = Intent(context, CommentReceiver::class.java).apply {
            action = Companion.ACTION_COMMENT
            putExtra(Companion.EXTRA_HISTORY_ID, historyId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(context, (historyId + 300).toInt(), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Bildirime Yorum Cevap Aksiyonu ekleniyor
        val action: NotificationCompat.Action =
            NotificationCompat.Action.Builder(R.drawable.ic_comment, replyLabel, replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build()

        builder.addAction(action)

        // --- 3. Görsel Kontrolü ve Yükleme ---
        if (imageUrl != null && imageUrl.isNotBlank()) {
            // (Coil/Glide görsel yükleme mantığı NotificationHelper'da yapılıyor)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(historyId.toInt(), builder.build())
    }
}