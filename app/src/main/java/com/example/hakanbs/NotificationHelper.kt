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
import androidx.core.app.NotificationCompat
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

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Shows remote-controlled sentimental messages."
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Bildirimi gösteren ana fonksiyon
    fun showNotification(context: Context, message: String, imageUrl: String?) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Eşinden Not")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (!imageUrl.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = loadBitmapFromUrl(context, imageUrl)
                if (bitmap != null) {
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .setSummaryText(message)
                    )
                }
                notify(context, builder)
            }
        } else {
            notify(context, builder)
        }
    }

    private fun notify(context: Context, builder: NotificationCompat.Builder) {
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
        notificationManager?.notify(System.currentTimeMillis().toInt(), builder.build())
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