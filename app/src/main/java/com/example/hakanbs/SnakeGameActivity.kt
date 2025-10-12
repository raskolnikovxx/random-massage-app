package com.example.hakanbs

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snake_game)

        gridLayout = findViewById(R.id.gridLayout)
        scoreText = findViewById(R.id.scoreText)

        setupGrid()
        setupControls()
        startGame()
    }

    private fun setupGrid() {
        cells = Array(gridSize) { row ->
            Array(gridSize) { col ->
                ImageView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = 0
                        columnSpec = GridLayout.spec(col, 1f)
                        rowSpec = GridLayout.spec(row, 1f)
                        setMargins(1, 1, 1, 1)
                    }
                    setBackgroundColor(ContextCompat.getColor(this@SnakeGameActivity, android.R.color.darker_gray))
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
        findViewById<Button>(R.id.buttonUp).setOnClickListener { direction = Pair(-1, 0) }
        findViewById<Button>(R.id.buttonDown).setOnClickListener { direction = Pair(1, 0) }
        findViewById<Button>(R.id.buttonLeft).setOnClickListener { direction = Pair(0, -1) }
        findViewById<Button>(R.id.buttonRight).setOnClickListener { direction = Pair(0, 1) }
    }

    private fun startGame() {
        // Yılanı başlangıç pozisyonuna yerleştir
        snake.clear()
        snake.add(Pair(gridSize/2, gridSize/2))
        placeFood()
        score = 0
        updateScore()
        isGameRunning = true

        handler.post(object : Runnable {
            override fun run() {
                if (isGameRunning) {
                    moveSnake()
                    handler.postDelayed(this, gameSpeed)
                }
            }
        })
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
            placeFood()
        } else {
            snake.removeAt(snake.size - 1)
        }

        updateGrid()
    }

    private fun placeFood() {
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
        // Tüm hücreleri temizle
        cells.forEach { row ->
            row.forEach { cell ->
                cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }

        // Yılanı çiz
        snake.forEach { (row, col) ->
            cells[row][col].setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        }

        // Yemeği çiz
        cells[food.first][food.second].setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_red_light)
        )
    }

    private fun updateScore() {
        scoreText.text = "Score: $score"
    }

    private fun gameOver() {
        isGameRunning = false
        // Oyun bitti mesajını göster
        runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Game Over")
                .setMessage("Score: $score")
                .setPositiveButton("Restart") { _, _ -> startGame() }
                .setNegativeButton("Exit") { _, _ -> finish() }
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isGameRunning = false
        handler.removeCallbacksAndMessages(null)
    }
}
