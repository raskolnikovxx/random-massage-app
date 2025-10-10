package com.example.hakanbs

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.snackbar.Snackbar
import android.view.WindowManager

class FullscreenMediaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEDIA_URL = "extra_media_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_fullscreen_media)

        val mediaUrl = intent.getStringExtra(EXTRA_MEDIA_URL)

        val photoView: PhotoView = findViewById(R.id.photo_view)
        val gifView: ImageView = findViewById(R.id.gif_view)
        val closeButton: ImageButton = findViewById(R.id.btn_close)

        closeButton.setOnClickListener { finish() }

        if (mediaUrl.isNullOrEmpty()) {
            Snackbar.make(photoView, "Görsel yüklenemedi.", Snackbar.LENGTH_LONG).show()
            photoView.visibility = View.VISIBLE
            photoView.load(R.drawable.ic_image_error)
            return
        }

        // --- AKILLI YÜKLEME MANTIĞI ---
        if (mediaUrl.endsWith(".gif", ignoreCase = true)) {
            // Eğer link GIF ise, ImageView'i göster ve PhotoView'i gizle
            gifView.visibility = View.VISIBLE
            photoView.visibility = View.GONE

            gifView.load(mediaUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_image_placeholder)
                error(R.drawable.ic_image_error)
            }
        } else {
            // Eğer normal resim ise, PhotoView'i göster ve ImageView'i gizle
            photoView.visibility = View.VISIBLE
            gifView.visibility = View.GONE

            photoView.load(mediaUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_image_placeholder)
                error(R.drawable.ic_image_error)
            }
        }
    }
}