package com.example.hakanbs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object NotificationHelper {
    private const val CHANNEL_ID = "SENTIMENTAL_NOTIF_CHANNEL"
    private const val CHANNEL_NAME = "Sentimental Messages"
    private const val TAG = "NotificationHelper"

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Yüksek Öncelik (Bildirimin ekranda görünmesini sağlar)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Shows remote-controlled sentimental messages."
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Bildirimi gösteren ana fonksiyon
    fun showNotification(context: Context, sentence: RemoteSentence, imageUrl: String?, historyId: Long) {
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
            .setPriority(NotificationCompat.PRIORITY_MAX) // En yüksek öncelik
            .setVibrate(longArrayOf(100, 200, 300, 400))
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)

        // Aksiyonları ekle
        addActions(context, builder, historyId)

        // Görsel Kontrolü ve Yükleme
        if (!imageUrl.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = loadBitmapFromUrl(context, imageUrl)
                if (bitmap != null) {
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .setSummaryText(sentence.context ?: sentence.text)
                    )
                }
                notify(context, builder, historyId)
            }
        } else {
            notify(context, builder, historyId)
        }
    }

    // YENİ FONKSİYON: Tüm Aksiyon Butonlarını Ekler
    private fun addActions(context: Context, builder: NotificationCompat.Builder, historyId: Long) {
        // Kalp Tepkisi
        val reactIntent = Intent(context, ActionReceiver::class.java).apply {
            action = AlarmReceiver.Companion.ACTION_REACT
            putExtra(AlarmReceiver.Companion.EXTRA_HISTORY_ID, historyId)
            putExtra(AlarmReceiver.Companion.EXTRA_EMOJI, "❤️")
        }
        val reactPendingIntent = PendingIntent.getBroadcast(context, (historyId + 100).toInt(), reactIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(R.drawable.ic_react_heart, "Sevdim", reactPendingIntent)

        // Yorum Butonu (RemoteInput)
        val replyIntent = Intent(context, CommentReceiver::class.java).apply {
            action = AlarmReceiver.Companion.ACTION_COMMENT
            putExtra(AlarmReceiver.Companion.EXTRA_HISTORY_ID, historyId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(context, (historyId + 300).toInt(), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val remoteInput: RemoteInput = RemoteInput.Builder(AlarmReceiver.Companion.KEY_TEXT_REPLY).run {
            setLabel("Yorum Yaz")
            build()
        }

        val action: NotificationCompat.Action =
            NotificationCompat.Action.Builder(R.drawable.ic_comment, "Yorum Yaz", replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build()

        builder.addAction(action)
    }


    private fun notify(context: Context, builder: NotificationCompat.Builder, notificationId: Long) {
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
        notificationManager?.notify(notificationId.toInt(), builder.build())
    }

    // Coil kullanarak URL'den Bitmap yükler
    private suspend fun loadBitmapFromUrl(context: Context, url: String): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            val imageLoader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .target(object : Target {
                    override fun onStart(placeholder: Drawable?) {}
                    override fun onError(error: Drawable?) {
                        continuation.resume(null)
                    }
                    override fun onSuccess(result: Drawable) {
                        val bitmap = (result as? BitmapDrawable)?.bitmap
                        continuation.resume(bitmap)
                    }
                })
                .build()

            val disposable = imageLoader.enqueue(request)
            continuation.invokeOnCancellation { disposable.dispose() }
        }
}