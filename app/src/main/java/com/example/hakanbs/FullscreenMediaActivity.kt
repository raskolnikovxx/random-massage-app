package com.example.hakanbs

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.android.material.snackbar.Snackbar
import android.view.WindowManager // Tam ekran için

class FullscreenMediaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEDIA_URL = "extra_media_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tam ekran yap (Status barı gizler)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContentView(R.layout.activity_fullscreen_media)

        val mediaUrl = intent.getStringExtra(EXTRA_MEDIA_URL)

        val mediaView: ImageView = findViewById(R.id.iv_fullscreen_media)
        val closeButton: ImageButton = findViewById(R.id.btn_close)

        closeButton.setOnClickListener { finish() }

        if (mediaUrl.isNullOrEmpty()) {
            Snackbar.make(mediaView, "Görsel yüklenemedi.", Snackbar.LENGTH_LONG).show()
            // Hata ikonunu yükle
            mediaView.load(R.drawable.ic_image_error)
            return
        }

        // Coil, GIF'leri ve resimleri otomatik olarak oynatır/yükler
        mediaView.load(mediaUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_error)
        }
    }
}