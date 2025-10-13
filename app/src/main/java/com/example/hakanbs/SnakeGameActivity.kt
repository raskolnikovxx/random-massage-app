package com.example.hakanbs

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.ImageView
import androidx.core.content.ContextCompat
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.EditText
import android.widget.Toast
import java.util.UUID

class SnakeGameActivity : AppCompatActivity() {
    private lateinit var gridLayout: GridLayout
    private lateinit var scoreText: TextView
    private lateinit var cells: Array<Array<ImageView>>
    private var snake = mutableListOf<Pair<Int, Int>>()
    private var direction = Pair(0, 1) // Başlangıçta sağa doğru hareket
    private var food = Pair(0, 0)
    private var score = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isGameRunning = false
    private val gameSpeed = 300L // milisaniye

    private val gridSize = 15

    private val snakeHeadColor = 0xFF4CAF50.toInt() // pastel yeşil
    private val snakeBodyColor = 0xFF81C784.toInt() // açık pastel yeşil
    private val foodColor = 0xFFFF8A65.toInt() // pastel turuncu
    private val emptyCellColor = 0xFFF5F5F5.toInt() // çok açık gri
    private var isGameOver = false
    private lateinit var gameOverText: TextView
    private lateinit var restartButton: Button
    private var rewardGiven = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snake_game)

        gridLayout = findViewById(R.id.gridLayout)
        scoreText = findViewById(R.id.scoreText)

        // Game Over ve Restart
        gameOverText = TextView(this).apply {
            text = "Bi daha dene hırslı zilli seni.."
            textSize = 28f
            setTextColor(0xFF222222.toInt())
            visibility = View.GONE
            gravity = android.view.Gravity.CENTER
            setPadding(0, 32, 0, 0)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        restartButton = Button(this).apply {
            text = "Tekrar Oyna"
            visibility = View.GONE
            setOnClickListener { restartGame() }
        }
        val parent = findViewById<LinearLayout>(R.id.rootLayout)
        parent.addView(gameOverText)
        parent.addView(restartButton)

        setupGrid()
        setupControls()
        startGame()
    }

    private fun setupGrid() {
        gridLayout.removeAllViews()
        cells = Array(gridSize) { row ->
            Array(gridSize) { col ->
                ImageView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = 0
                        columnSpec = GridLayout.spec(col, 1f)
                        rowSpec = GridLayout.spec(row, 1f)
                        setMargins(3, 3, 3, 3)
                    }
                    // Yuvarlatılmış pastel arka plan
                    background = ContextCompat.getDrawable(this@SnakeGameActivity, R.drawable.round_cell_bg)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            }
        }
        cells.forEach { row ->
            row.forEach { cell ->
                gridLayout.addView(cell)
            }
        }
    }

    private fun setupControls() {
        findViewById<ImageButton>(R.id.buttonUp).setOnClickListener { direction = Pair(-1, 0) }
        findViewById<ImageButton>(R.id.buttonDown).setOnClickListener { direction = Pair(1, 0) }
        findViewById<ImageButton>(R.id.buttonLeft).setOnClickListener { direction = Pair(0, -1) }
        findViewById<ImageButton>(R.id.buttonRight).setOnClickListener { direction = Pair(0, 1) }
    }

    private fun startGame() {
        isGameRunning = true
        isGameOver = false
        score = 0
        rewardGiven = false
        scoreText.text = "Score: $score"
        snake.clear()
        snake.add(Pair(gridSize / 2, gridSize / 2))
        direction = Pair(0, 1)
        spawnFood()
        updateGrid()
        gameOverText.visibility = View.GONE
        restartButton.visibility = View.GONE
        handler.postDelayed(::gameLoop, gameSpeed)
    }

    private fun moveSnake() {
        val head = snake.first()
        val newHead = Pair(
            (head.first + direction.first + gridSize) % gridSize,
            (head.second + direction.second + gridSize) % gridSize
        )

        // Kendine çarpma kontrolü
        if (snake.contains(newHead)) {
            gameOver()
            return
        }

        snake.add(0, newHead)

        if (newHead == food) {
            score += 10
            updateScore()
            spawnFood()
        } else {
            snake.removeAt(snake.size - 1)
        }

        updateGrid()
    }

    private fun spawnFood() {
        do {
            food = Pair(
                (0 until gridSize).random(),
                (0 until gridSize).random()
            )
        } while (snake.contains(food))

        cells[food.first][food.second].setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_red_light)
        )
    }

    private fun updateGrid() {
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val cell = cells[row][col]
                cell.setImageDrawable(null)
                cell.background = ContextCompat.getDrawable(this, R.drawable.round_cell_bg)
            }
        }
        snake.forEachIndexed { i, (row, col) ->
            val cell = cells[row][col]
            if (i == 0) {
                // Yılan başı için kalp SVG
                cell.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.heart_svgrepo_com))
                cell.imageTintList = null
            } else {
                cell.setImageDrawable(null)
                cell.background = ContextCompat.getDrawable(this, R.drawable.snake_body_bg)
            }
        }
        val (foodRow, foodCol) = food
        val foodCell = cells[foodRow][foodCol]
        foodCell.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.four_leaf_clover_svgrepo_com))
        foodCell.imageTintList = null
    }

    private fun updateScore() {
        scoreText.text = "Score: $score"
        if (score >= 200 && !rewardGiven) {
            rewardGiven = true
            isGameRunning = false // Oyun dursun
            showCouponInputDialogAndSaveToConfig()
        }
    }

    private fun gameOver() {
        isGameRunning = false
        isGameOver = true
        // Oyun bitti mesajını göster
        runOnUiThread {
            gameOverText.visibility = View.VISIBLE
            restartButton.visibility = View.VISIBLE
        }
    }

    private fun gameLoop() {
        if (!isGameRunning || isGameOver) return
        val newHead = Pair(snake.first().first + direction.first, snake.first().second + direction.second)
        if (newHead.first !in 0 until gridSize || newHead.second !in 0 until gridSize || snake.contains(newHead)) {
            isGameOver = true
            isGameRunning = false
            gameOverText.visibility = View.VISIBLE
            restartButton.visibility = View.VISIBLE
            return
        }
        snake.add(0, newHead)
        if (newHead == food) {
            score++
            updateScore()
            spawnFood()
        } else {
            snake.removeAt(snake.size - 1)
        }
        updateGrid()
        handler.postDelayed(::gameLoop, gameSpeed)
    }

    private fun restartGame() {
        startGame()
    }

    private fun showCouponInputDialogAndSaveToConfig() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        val titleInput = EditText(this)
        titleInput.hint = "Kupon başlığı"
        val descInput = EditText(this)
        descInput.hint = "Açıklama (isteğe bağlı)"
        layout.addView(titleInput)
        layout.addView(descInput)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Tebrikler! Kupon kazandın!")
            .setMessage("Kuponunu oluştur ve kaydet:")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Kaydet") { _, _ ->
                val title = titleInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                if (title.isNotEmpty()) {
                    addCouponToGlobalList(title, desc)
                    Toast.makeText(this, "Kupon kaydedildi! Kuponlar ekranında görebilirsin.", Toast.LENGTH_LONG).show()
                    isGameRunning = true // Diyalog kapanınca oyun devam etsin
                    handler.postDelayed(::gameLoop, gameSpeed)
                } else {
                    Toast.makeText(this, "Başlık boş olamaz!", Toast.LENGTH_SHORT).show()
                    // Diyalog tekrar açılsın
                    rewardGiven = false
                    updateScore()
                }
            }
            .setNegativeButton("İptal") { _, _ ->
                isGameRunning = true // İptal edilirse oyun devam etsin
                handler.postDelayed(::gameLoop, gameSpeed)
            }
            .create()
        dialog.show()
    }

    private fun addCouponToGlobalList(title: String, desc: String) {
        val config = ControlConfig(this)
        val currentConfig = config.getLocalConfig()
        val newCoupon = Coupon(
            id = UUID.randomUUID().toString(),
            title = title,
            description = desc
        )
        val updatedCoupons = listOf(newCoupon) + currentConfig.coupons
        val updatedConfig = currentConfig.copy(coupons = updatedCoupons)
        val saveConfigLocally = ControlConfig::class.java.getDeclaredMethod("saveConfigLocally", updatedConfig.javaClass)
        saveConfigLocally.isAccessible = true
        saveConfigLocally.invoke(config, updatedConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        isGameRunning = false
        handler.removeCallbacksAndMessages(null)
    }
}
