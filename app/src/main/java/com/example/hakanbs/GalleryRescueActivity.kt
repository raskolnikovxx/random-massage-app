package com.example.hakanbs

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class GalleryRescueActivity : AppCompatActivity(), GalleryRescueGameView.GameStateListener {
    // Artık sadece restart butonu ve oyun alanı var, TextView'lar kaldırıldı
    private lateinit var btnRestart: Button
    private lateinit var gameView: GalleryRescueGameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery_rescue)

        btnRestart = findViewById(R.id.btn_restart)
        val gameArea = findViewById<FrameLayout>(R.id.game_area)
        gameView = GalleryRescueGameView(this)
        gameArea.addView(gameView)
        gameView.setGameStateListener(this)

        btnRestart.setOnClickListener {
            gameView.restartGame()
        }
    }

    // Artık üstteki TextView'lar olmadığı için bu fonksiyona gerek yok
    override fun onGameStateChanged(lives: Int, score: Long, timer: Int, revealPercent: Float) {
        // Bilgiler sadece HUD'da gösterilecek, burada güncelleme yapılmayacak
    }
}
