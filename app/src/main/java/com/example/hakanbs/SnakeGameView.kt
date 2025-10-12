package com.example.hakanbs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class SnakeGameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val cellSize = 60
    private val numCellsX get() = width / cellSize
    private val numCellsY get() = height / cellSize
    private val snake = mutableListOf<Pair<Int, Int>>()
    private var direction = Direction.RIGHT
    private var food: Pair<Int, Int> = Pair(5, 5)
    private var gameOver = false
    private val paint = Paint()
    private val handler = Handler(Looper.getMainLooper())
    private var lastX = 0f
    private var lastY = 0f

    private enum class Direction { UP, DOWN, LEFT, RIGHT }

    init {
        resetGame()
        startGameLoop()
        setBackgroundColor(Color.BLACK)
    }

    private fun resetGame() {
        snake.clear()
        snake.add(Pair(3, 5))
        snake.add(Pair(2, 5))
        snake.add(Pair(1, 5))
        direction = Direction.RIGHT
        spawnFood()
        gameOver = false
    }

    private fun startGameLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!gameOver) {
                    moveSnake()
                    invalidate()
                    handler.postDelayed(this, 200)
                }
            }
        }, 200)
    }

    private fun moveSnake() {
        val head = snake.first()
        val newHead = when (direction) {
            Direction.UP -> Pair(head.first, head.second - 1)
            Direction.DOWN -> Pair(head.first, head.second + 1)
            Direction.LEFT -> Pair(head.first - 1, head.second)
            Direction.RIGHT -> Pair(head.first + 1, head.second)
        }
        // Check collision
        if (newHead.first < 0 || newHead.second < 0 ||
            newHead.first >= numCellsX || newHead.second >= numCellsY ||
            snake.contains(newHead)
        ) {
            gameOver = true
            return
        }
        snake.add(0, newHead)
        if (newHead == food) {
            spawnFood()
        } else {
            snake.removeAt(snake.size - 1)
        }
    }

    private fun spawnFood() {
        do {
            food = Pair(Random.nextInt(numCellsX), Random.nextInt(numCellsY))
        } while (snake.contains(food))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw snake
        paint.color = Color.GREEN
        for (part in snake) {
            canvas.drawRect(
                (part.first * cellSize).toFloat(),
                (part.second * cellSize).toFloat(),
                ((part.first + 1) * cellSize).toFloat(),
                ((part.second + 1) * cellSize).toFloat(),
                paint
            )
        }
        // Draw food
        paint.color = Color.RED
        canvas.drawRect(
            (food.first * cellSize).toFloat(),
            (food.second * cellSize).toFloat(),
            ((food.first + 1) * cellSize).toFloat(),
            ((food.second + 1) * cellSize).toFloat(),
            paint
        )
        // Draw game over
        if (gameOver) {
            paint.color = Color.WHITE
            paint.textSize = 80f
            canvas.drawText("Game Over!", width / 4f, height / 2f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver && event.action == MotionEvent.ACTION_DOWN) {
            resetGame()
            invalidate()
            startGameLoop()
            return true
        }
        if (event.action == MotionEvent.ACTION_DOWN) {
            lastX = event.x
            lastY = event.y
        } else if (event.action == MotionEvent.ACTION_UP) {
            val dx = event.x - lastX
            val dy = event.y - lastY
            if (Math.abs(dx) > Math.abs(dy)) {
                if (dx > 0 && direction != Direction.LEFT) direction = Direction.RIGHT
                else if (dx < 0 && direction != Direction.RIGHT) direction = Direction.LEFT
            } else {
                if (dy > 0 && direction != Direction.UP) direction = Direction.DOWN
                else if (dy < 0 && direction != Direction.DOWN) direction = Direction.UP
            }
        }
        return true
    }
}

