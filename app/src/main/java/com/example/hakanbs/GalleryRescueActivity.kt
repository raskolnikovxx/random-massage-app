package com.example.hakanbs

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class GalleryRescueActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery_rescue)

        // Oyun alanı için custom view ekle
        val gameArea = findViewById<FrameLayout>(R.id.game_area)
        val gameView = GalleryRescueGameView(this)
        gameArea.addView(gameView)
    }
}
