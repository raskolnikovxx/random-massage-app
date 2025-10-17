package com.example.hakanbs

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.hakanbs.RemoteConfig
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
    private var currentLevel: Int = 1 // 1. seviye ile başla

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery_rescue)

        tvScore = findViewById(R.id.tv_score)
        tvLives = findViewById(R.id.tv_lives)
        tvTime = findViewById(R.id.tv_time)
        tvPercent = findViewById(R.id.tv_percent)
        btnRestart = findViewById(R.id.btn_restart)

        val gameArea = findViewById<FrameLayout>(R.id.game_area)
        gameView = GalleryRescueGameView(this).apply {
            setGameStateListener(this@GalleryRescueActivity)
        }
        gameArea.addView(gameView)

        // İlk açılışta level 1 için RemoteConfig arka planı uygula + başlat:
        applyRemoteBgAndRestart(currentLevel)

        // Aynı seviyeyi yeniden başlatmak istersen:
        btnRestart.setOnClickListener {
            applyRemoteBgAndRestart(currentLevel)
        }
    }

    // --- Yardımcılar ---

    // Hatalı fonksiyonun doğru hali:
    private fun pickBgUrlFor(level: Int, config: RemoteConfig): String? {
        return when (level) {
            1 -> config.backgroundUrl1
            2 -> config.backgroundUrl2
            3 -> config.backgroundUrl3
            else -> config.backgroundUrl1
        }
    }

    /**
     * Remote Config’i çek, ilgili seviye için URL’i (boş değilse) uygula, sonra oyunu başlat/yeniden başlat.
     * Önemli: URL boş/yanlış gelirse yerel fallback’e dönmemek için setBackgroundUrl()’e boş değer göndermiyoruz.
     */
    private fun applyRemoteBgAndRestart(level: Int) {
        val controlConfig = ControlConfig(this)
        activityScope.launch {
            val config = controlConfig.fetchConfig()
            val url = config?.let { pickBgUrlFor(level, it) }

            // 1) Her zaman önce URL’i uygula (boşsa dokunma — önceki görsel kalsın)
            if (url != null) {
                gameView.setBackgroundUrl(url)
            }

            // 2) Sonra oyunu başlat / yeniden başlat
            gameView.restartGame()
        }
    }

    // --- GameStateListener ---

    override fun onGameStateChanged(lives: Int, score: Long, timerSeconds: Int, revealPercent: Float) {
        tvScore.text = "★ $score"
        tvLives.text = "❤ $lives"
        tvTime.text = "⏰ $timerSeconds"
        tvPercent.text = "%${revealPercent.toInt()}"
    }

    /**
     * GameView tarafı seviye atladı dediğinde çağrılacak.
     * (Senin GameView’ında zaten bu callback var; burada yeni seviye için RemoteConfig arka planı alıp restart ediyoruz)
     */
    override fun onLevelChanged(newLevel: Int) {
        currentLevel = newLevel
        applyRemoteBgAndRestart(currentLevel)
    }
}
