package com.example.hakanbs

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class GalleryRescueActivity : AppCompatActivity(), GalleryRescueGameView.GameStateListener {
    private lateinit var tvScore: TextView
    private lateinit var tvLives: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvPercent: TextView
    private lateinit var btnRestart: Button
    private lateinit var gameView: GalleryRescueGameView
    private val activityScope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery_rescue)

        tvScore = findViewById(R.id.tv_score)
        tvLives = findViewById(R.id.tv_lives)
        tvTime = findViewById(R.id.tv_time)
        tvPercent = findViewById(R.id.tv_percent)
        btnRestart = findViewById(R.id.btn_restart)
        val gameArea = findViewById<FrameLayout>(R.id.game_area)
        gameView = GalleryRescueGameView(this)
        gameArea.addView(gameView)
        gameView.setGameStateListener(this)

        // RemoteConfig'ten arka plan url'sini çek
        val controlConfig = ControlConfig(this)
        activityScope.launch {
            val config = controlConfig.fetchConfig() ?: controlConfig.getLocalConfig()
            gameView.setBackgroundUrl(config.galleryRescueBackgroundUrl)
        }

        btnRestart.setOnClickListener {
            gameView.restartGame()
        }
    }

    override fun onGameStateChanged(lives: Int, score: Long, timerSeconds: Int, revealPercent: Float) {
        tvScore.text = "★ $score"
        tvLives.text = "❤ $lives"
        tvTime.text = "⏰ $timerSeconds"
        tvPercent.text = "%${revealPercent.toInt()}"
    }
}
