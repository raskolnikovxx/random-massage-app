package com.example.hakanbs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path // Path nesnesini kullanacağız
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withClip
import androidx.appcompat.content.res.AppCompatResources
import kotlin.math.sqrt
import kotlin.random.Random

class GalleryRescueGameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // --- YARDIMCI KODLAR ---
    private enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

    private fun getEdgeFromPoint(p: PointF, tolerance: Float = 15f): Edge? {
        if (p.y <= playfield.top + tolerance) return Edge.TOP
        if (p.y >= playfield.bottom - tolerance) return Edge.BOTTOM
        if (p.x <= playfield.left + tolerance) return Edge.LEFT
        if (p.x >= playfield.right - tolerance) return Edge.RIGHT
        return null
    }

    private fun getPolygonArea(points: List<PointF>): Float {
        if (points.size < 3) return 0f
        var area = 0.0
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            area += (p1.x * p2.y - p2.x * p1.y)
        }
        return (kotlin.math.abs(area) / 2.0).toFloat()
    }

    private fun pathArea(path: Path): Float {
        val measure = android.graphics.PathMeasure(path, false)
        val points = mutableListOf<PointF>()
        val step = 5f
        val coords = FloatArray(2)
        var distance = 0f
        while (distance < measure.length) {
            measure.getPosTan(distance, coords, null)
            points.add(PointF(coords[0], coords[1]))
            distance += step
        }
        if (measure.length > 0) {
            measure.getPosTan(measure.length, coords, null)
            points.add(PointF(coords[0], coords[1]))
        }
        return getPolygonArea(points)
    }

    // --- MANUEL KONTROL İÇİN DEĞİŞKENLER ---
    private val dpadButtons = mutableMapOf<String, RectF>()
    private val dpadPaint = Paint().apply { color = Color.argb(100, 255, 255, 255) }
    private var playerSpeed = 10f
    private var playerVx = 0f
    private var playerVy = 0f


    // --- TEMEL OYUN DEĞİŞKENLERİ ---
    private var playfield = RectF()
    private var playerX = 0f
    private var playerY = 0f
    private var playerRadius = 20f
    private var backgroundBitmap: Bitmap? = null

    // --- OYUN DURUMU ---
    private var isDrawingLine = false
    private var running = true
    private var gameOver = false
    private var gameWon = false
    private var lives = 3
    private var score = 0L
    private var timerSeconds = 99
    private val timerHandler = Handler(Looper.getMainLooper())
    private var totalRevealedArea = 0f
    private var revealPercent = 0f
    private val winPercentage = 80f
    private var timeOver = false

    // --- ÇİZİM İÇİN GEREKLİLER ---
    private var currentLine = mutableListOf<PointF>()
    private val revealedPaths = mutableListOf<Path>()
    private val revealedRegion = Region()

    // --- PAINT NESNELERİ ---
    private val playerPaint = Paint().apply { color = Color.RED }
    private val linePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 10f
        style = Paint.Style.STROKE
    }

    // --- DÜŞMANLAR ---
    private data class Enemy(
        var x: Float, var y: Float, var vx: Float, var vy: Float, val radius: Float,
        val isHunter: Boolean = false,
        val isStalker: Boolean = false,
        val isMegaHunter: Boolean = false,
        var directionChangeTimer: Int = 0
    )
    private val enemies = mutableListOf<Enemy>()
    private val enemyPaint = Paint().apply { color = Color.GREEN }
    private val hunterPaint = Paint().apply { color = Color.MAGENTA }
    private val stalkerPaint = Paint().apply { color = Color.CYAN }
    private val megaHunterPaint = Paint().apply { color = Color.rgb(255, 100, 0) }
    private var hunterBoss: Enemy? = null
    private var stalkerBoss: Enemy? = null
    private var megaHunterBoss: Enemy? = null
    private var bossCount = 1

    // Mega boss için sevimli canavar görseli (güvenli yükleme)
    private val monsterDrawable = AppCompatResources.getDrawable(context, R.drawable.monster_cute)
    private val hunterDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_heart_boss)

    // --- OYUN DÖNGÜSÜ ---
    private val handler = Handler(Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (running && !gameOver && !gameWon) {
                updatePlayer()
                updateEnemies()
                checkCollisions()
                invalidate()
                handler.postDelayed(this, 16)
            }
        }
    }

    // --- PUAN BALONCUKLARI ---
    private data class ScoreBubble(var x: Float, var y: Float, val text: String, var alpha: Float = 1f, var dy: Float = -2f)
    private val scoreBubbles = mutableListOf<ScoreBubble>()

    // GAME OVER butonu için alan
    private val gameOverButtonRect = RectF()

    // HUD için kullanılacak Paint nesnesi
    private val hudPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // Oyun durumu dinleyicisi için arayüz
    interface GameStateListener {
        fun onGameStateChanged(lives: Int, score: Long, timerSeconds: Int, revealPercent: Float)
    }

    private var gameStateListener: GameStateListener? = null

    fun setGameStateListener(listener: GameStateListener) {
        this.gameStateListener = listener
    }

    fun restartGame() {
        setupNewGame()
        timeOver = false
        gameStateListener?.onGameStateChanged(lives, score, timerSeconds, revealPercent)
        invalidate()
    }

    init {
        post {
            setupNewGame()
            handler.post(gameLoop)
        }
    }

    private fun isSafeSpawn(x: Float, y: Float, safeDistance: Float = 120f): Boolean {
        val dx = x - playfield.left
        val dy = y - playfield.top
        return dx * dx + dy * dy > safeDistance * safeDistance
    }

    private fun setupNewGame() {
        val padding = 40f
        playfield.set(padding, padding * 2, width - padding, height - padding - 300f)
        setupDpadButtons()
        playerX = playfield.left
        playerY = playfield.top
        revealedPaths.clear()
        currentLine.clear()
        enemies.clear()
        bossCount = 1
        // Düşmanları güvenli mesafede başlat
        repeat(3) {
            var ex: Float
            var ey: Float
            do {
                ex = playfield.centerX() + Random.nextFloat() * 200f - 100f
                ey = playfield.centerY() + Random.nextFloat() * 200f - 100f
            } while (!isSafeSpawn(ex, ey))
            enemies.add(
                Enemy(
                    ex,
                    ey,
                    (Random.nextFloat() - 0.5f) * 8f,
                    (Random.nextFloat() - 0.5f) * 8f,
                    18f
                )
            )
        }
        // 2 tane Avcı (Hunter) boss
        var hx: Float
        var hy: Float
        do {
            hx = playfield.centerX()
            hy = playfield.centerY()
        } while (!isSafeSpawn(hx, hy, 180f))
        hunterBoss = Enemy(
            hx, hy,
            2f, 2f, 25f, isHunter = true
        )
        enemies.add(hunterBoss!!)
        do {
            hx = playfield.centerX() - 120f
            hy = playfield.centerY() + 120f
        } while (!isSafeSpawn(hx, hy, 180f))
        val hunterBoss2 = Enemy(
            hx, hy,
            -2f, 2f, 25f, isHunter = true
        )
        enemies.add(hunterBoss2)
        // 1 tane Stalker boss -> sadece 1 tane olacak
        var sx: Float
        var sy: Float
        do {
            sx = playfield.right
            sy = playfield.bottom
        } while (!isSafeSpawn(sx, sy, 180f))
        stalkerBoss = Enemy(
            sx, sy,
            -playerSpeed * 0.8f, 0f,
            22f,
            isStalker = true
        )
        enemies.add(stalkerBoss!!)
        // 2 tane Mega Hunter boss
        var mx: Float
        var my: Float
        do {
            mx = playfield.centerX() + 100f
            my = playfield.centerY() - 100f
        } while (!isSafeSpawn(mx, my, 200f))
        megaHunterBoss = Enemy(
            mx, my,
            1f, -1f,
            75f,
            isMegaHunter = true,
            directionChangeTimer = 60
        )
        enemies.add(megaHunterBoss!!)
        do {
            mx = playfield.centerX() - 100f
            my = playfield.centerY() + 100f
        } while (!isSafeSpawn(mx, my, 200f))
        val megaHunterBoss2 = Enemy(
            mx, my,
            -1f, 1f,
            75f,
            isMegaHunter = true,
            directionChangeTimer = 90
        )
        enemies.add(megaHunterBoss2)
        backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.arkaplan_resmi)
        lives = 3
        isDrawingLine = false
        running = true
        gameOver = false
        gameWon = false
        score = 0L
        timerSeconds = 99
        totalRevealedArea = 0f
        revealPercent = 0f
        startTimer()
        gameStateListener?.onGameStateChanged(lives, score, timerSeconds, revealPercent)
        invalidate()
        handler.removeCallbacks(gameLoop)
        handler.post(gameLoop)
    }

    private fun startTimer() {
        timerHandler.removeCallbacksAndMessages(null)
        timerHandler.post(object : Runnable {
            override fun run() {
                if (running && !gameOver && !gameWon && timerSeconds > 0) {
                    timerSeconds--
                    gameStateListener?.onGameStateChanged(lives, score, timerSeconds, revealPercent)
                    timerHandler.postDelayed(this, 1000)
                } else if (timerSeconds <= 0) {
                    handleTimeOver()
                }
            }
        })
    }

    private fun handleTimeOver() {
        timeOver = true
        gameOver = true
        running = false
        gameStateListener?.onGameStateChanged(lives, score, timerSeconds, revealPercent)
        invalidate()
    }

    private fun setupDpadButtons() {
        val buttonSize = 160f
        val margin = 60f
        val bottom = height - margin
        val centerX = width / 2f
        val centerY = bottom - buttonSize
        dpadButtons.clear()
        dpadButtons["LEFT"] = RectF(centerX - buttonSize * 1.5f, centerY, centerX - buttonSize * 0.5f, centerY + buttonSize)
        dpadButtons["RIGHT"] = RectF(centerX + buttonSize * 0.5f, centerY, centerX + buttonSize * 1.5f, centerY + buttonSize)
        dpadButtons["UP"] = RectF(centerX - buttonSize * 0.5f, centerY - buttonSize, centerX + buttonSize * 0.5f, centerY)
        dpadButtons["DOWN"] = RectF(centerX - buttonSize * 0.5f, centerY + buttonSize, centerX + buttonSize * 0.5f, centerY + buttonSize * 2f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Tekrar Oyna butonu için kontrol
        if ((gameOver || gameWon) && event.action == MotionEvent.ACTION_DOWN) {
            if (gameOverButtonRect.contains(event.x, event.y)) {
                setupNewGame()
                invalidate()
                return true
            }
        }

        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                var buttonPressed = false
                dpadButtons.forEach { (direction, rect) ->
                    if (rect.contains(touchX, touchY)) {
                        movePlayer(direction)
                        buttonPressed = true
                    }
                }

                if (!buttonPressed && !isDrawingLine) {
                    isDrawingLine = true
                    currentLine.clear()
                    currentLine.add(PointF(playerX, playerY))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawingLine) {
                    val clampedX = touchX.coerceIn(playfield.left, playfield.right)
                    val clampedY = touchY.coerceIn(playfield.top, playfield.bottom)
                    currentLine.add(PointF(clampedX, clampedY))
                    if (currentLine.size > 2 && isOnEdge(clampedX, clampedY)) {
                        captureAndRevealArea()
                        val finalPosition = snapToWall(clampedX, clampedY)
                        playerX = finalPosition.x
                        playerY = finalPosition.y
                        playerVx = 0f
                        playerVy = 0f

                        isDrawingLine = false
                        currentLine.clear()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                playerVx = 0f
                playerVy = 0f

                if (isDrawingLine) {
                    val clampedX = touchX.coerceIn(playfield.left, playfield.right)
                    val clampedY = touchY.coerceIn(playfield.top, playfield.bottom)
                    currentLine.add(PointF(clampedX, clampedY))

                    if (currentLine.size > 2 && isOnEdge(clampedX, clampedY)) {
                        captureAndRevealArea()
                        val finalPosition = snapToWall(clampedX, clampedY)
                        playerX = finalPosition.x
                        playerY = finalPosition.y
                    }
                }

                isDrawingLine = false
                currentLine.clear()
            }
        }
        invalidate()
        return true
    }

    private fun movePlayer(direction: String) {
        val onLeft = playerX == playfield.left
        val onRight = playerX == playfield.right
        val onTop = playerY == playfield.top
        val onBottom = playerY == playfield.bottom
        val onCorner = (onLeft || onRight) && (onTop || onBottom)

        when (direction) {
            "LEFT" -> if (onTop || onBottom || onCorner) { playerVy = 0f; playerVx = -playerSpeed }
            "RIGHT" -> if (onTop || onBottom || onCorner) { playerVy = 0f; playerVx = playerSpeed }
            "UP" -> if (onLeft || onRight || onCorner) { playerVx = 0f; playerVy = -playerSpeed }
            "DOWN" -> if (onLeft || onRight || onCorner) { playerVx = 0f; playerVy = playerSpeed }
        }
    }

    private fun updatePlayer() {
        if (playerVx == 0f && playerVy == 0f) return

        playerX += playerVx
        playerY += playerVy

        playerX = playerX.coerceIn(playfield.left, playfield.right)
        playerY = playerY.coerceIn(playfield.top, playfield.bottom)

        val onCorner = (playerX == playfield.left || playerX == playfield.right) && (playerY == playfield.top || playerY == playfield.bottom)

        if (onCorner) {
            playerVx = 0f
            playerVy = 0f
        }
    }


    private fun getRevealedArea(): Float {
        if (revealedPaths.isEmpty()) return 0f
        val region = Region()
        val bounds = RectF(playfield)
        val clip = Region(
            bounds.left.toInt(), bounds.top.toInt(),
            bounds.right.toInt(), bounds.bottom.toInt()
        )
        for (path in revealedPaths) {
            val r = Region()
            r.setPath(path, clip)
            region.op(r, Region.Op.UNION)
        }
        var area = 0
        val rect = android.graphics.Rect()
        val it = android.graphics.RegionIterator(region)
        while (it.next(rect)) {
            area += rect.width() * rect.height()
        }
        return area.toFloat()
    }

    private fun captureAndRevealArea() {
        if (currentLine.size < 2) return
        val firstPoint = currentLine.first()
        val lastPoint = currentLine.last()
        val startEdge = getEdgeFromPoint(firstPoint)
        val endEdge = getEdgeFromPoint(lastPoint)
        if (startEdge == null || endEdge == null) return

        val pointsToUse = if (startEdge == endEdge) {
            currentLine
        } else {
            val topLeft = PointF(playfield.left, playfield.top)
            val topRight = PointF(playfield.right, playfield.top)
            val bottomLeft = PointF(playfield.left, playfield.bottom)
            val bottomRight = PointF(playfield.right, playfield.bottom)
            val poly1Points = mutableListOf<PointF>().apply { addAll(currentLine) }
            val poly2Points = mutableListOf<PointF>().apply { addAll(currentLine) }
            val clockwiseNextCorner = mapOf(Edge.TOP to topRight, Edge.RIGHT to bottomRight, Edge.BOTTOM to bottomLeft, Edge.LEFT to topLeft)
            val clockwiseNextEdge = mapOf(Edge.TOP to Edge.RIGHT, Edge.RIGHT to Edge.BOTTOM, Edge.BOTTOM to Edge.LEFT, Edge.LEFT to Edge.TOP)
            val counterClockwiseNextCorner = mapOf(Edge.TOP to topLeft, Edge.LEFT to bottomLeft, Edge.BOTTOM to bottomRight, Edge.RIGHT to topRight)
            val counterClockwiseNextEdge = mapOf(Edge.TOP to Edge.LEFT, Edge.LEFT to Edge.BOTTOM, Edge.BOTTOM to Edge.RIGHT, Edge.RIGHT to Edge.TOP)
            var currentEdge = endEdge
            while (currentEdge != startEdge) {
                clockwiseNextCorner[currentEdge]?.let { poly1Points.add(it) }
                currentEdge = clockwiseNextEdge[currentEdge]!!
            }
            currentEdge = endEdge
            while (currentEdge != startEdge) {
                counterClockwiseNextCorner[currentEdge]?.let { poly2Points.add(it) }
                currentEdge = counterClockwiseNextEdge[currentEdge]!!
            }
            if (getPolygonArea(poly1Points) < getPolygonArea(poly2Points)) poly1Points else poly2Points
        }
        if (pointsToUse.size >= 3) {
            val capturedPath = Path()
            capturedPath.moveTo(pointsToUse.first().x, pointsToUse.first().y)
            for (point in pointsToUse) capturedPath.lineTo(point.x, point.y)
            capturedPath.close()
            revealedPaths.add(capturedPath)
            val newTotalArea = getRevealedArea()
            val capturedArea = (newTotalArea - totalRevealedArea).coerceAtLeast(0f)
            totalRevealedArea = newTotalArea

            val totalPlayfieldArea = playfield.width() * playfield.height()
            val capturedPercent = if (totalPlayfieldArea > 0) (capturedArea / totalPlayfieldArea) * 100f else 0f
            val areaScore = (capturedPercent * 100).toLong()
            score += areaScore
            scoreBubbles.add(ScoreBubble(playerX, playerY - 40f, "+$areaScore"))
            val enemiesToRemove = mutableListOf<Enemy>()
            for (enemy in enemies) {
                if (enemy.isHunter || enemy.isStalker || enemy.isMegaHunter) continue
                val pathBounds = RectF()
                capturedPath.computeBounds(pathBounds, true)
                if (pathBounds.contains(enemy.x, enemy.y)) {
                    score += 5000L
                    scoreBubbles.add(ScoreBubble(enemy.x, enemy.y - 40f, "+5000"))
                    enemiesToRemove.add(enemy)
                }
            }
            enemies.removeAll(enemiesToRemove)
            if (totalPlayfieldArea > 0) {
                revealPercent = (totalRevealedArea / totalPlayfieldArea) * 100f
            }
            gameStateListener?.onGameStateChanged(lives, score, timerSeconds, revealPercent)
            if (revealPercent >= winPercentage) {
                val fullPath = Path()
                fullPath.addRect(playfield, Path.Direction.CW)
                revealedPaths.clear()
                revealedPaths.add(fullPath)
                totalRevealedArea = playfield.width() * playfield.height()
                revealPercent = 100f
                gameWon = true
                score += timerSeconds * 100L
                timerHandler.removeCallbacksAndMessages(null)
                moveEnemiesOffScreen()
            }
        }
    }

    private fun isOnEdge(x: Float, y: Float, tolerance: Float = 15f): Boolean {
        return (x <= playfield.left + tolerance ||
                x >= playfield.right - tolerance ||
                y <= playfield.top + tolerance ||
                y >= playfield.bottom - tolerance)
    }

    private fun snapToWall(x: Float, y: Float): PointF {
        val distToLeft = kotlin.math.abs(x - playfield.left)
        val distToRight = kotlin.math.abs(x - playfield.right)
        val distToTop = kotlin.math.abs(y - playfield.top)
        val distToBottom = kotlin.math.abs(y - playfield.bottom)

        val minDistance = minOf(distToLeft, distToRight, distToTop, distToBottom)

        return when (minDistance) {
            distToLeft -> PointF(playfield.left, y.coerceIn(playfield.top, playfield.bottom))
            distToRight -> PointF(playfield.right, y.coerceIn(playfield.top, playfield.bottom))
            distToTop -> PointF(x.coerceIn(playfield.left, playfield.right), playfield.top)
            else -> PointF(x.coerceIn(playfield.left, playfield.right), playfield.bottom)
        }
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundBitmap?.let { bmp ->
            val paint = Paint()
            paint.isAntiAlias = true
            paint.alpha = if (gameWon) 255 else (255 * 0.8f).toInt()
            for (path in revealedPaths) {
                canvas.withClip(path) {
                    drawBitmap(bmp, null, playfield, paint)
                }
            }
        }
        canvas.drawRect(playfield, borderPaint)
        for (enemy in enemies) {
            if (enemy.isMegaHunter) {
                // Mega boss için görsel çiz (bitmap null ise fallback)
                val size = enemy.radius * 4f // 2 kat büyük
                val left = enemy.x - size / 2f
                val top = enemy.y - size / 2f
                val right = enemy.x + size / 2f
                val bottom = enemy.y + size / 2f
                val destRect = android.graphics.RectF(left, top, right, bottom)
                monsterDrawable?.let {
                    it.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                    it.draw(canvas)
                } ?: run {
                    // Fallback: turuncu yuvarlak çiz
                    canvas.drawCircle(enemy.x, enemy.y, enemy.radius * 2f, megaHunterPaint)
                }
            } else if (enemy.isHunter) {
                // Avcı boss: Kalp vektörü çiz
                hunterDrawable?.let {
                    val size = enemy.radius * 2f
                    val left = (enemy.x - size).toInt()
                    val top = (enemy.y - size).toInt()
                    val right = (enemy.x + size).toInt()
                    val bottom = (enemy.y + size).toInt()
                    it.setBounds(left, top, right, bottom)
                    it.draw(canvas)
                } ?: run {
                    // Fallback: Mor yuvarlak
                    canvas.drawCircle(enemy.x, enemy.y, enemy.radius, hunterPaint)
                }
            } else {
                val paintToUse = when {
                    enemy.isStalker -> stalkerPaint
                    else -> enemyPaint
                }
                canvas.drawCircle(enemy.x, enemy.y, enemy.radius, paintToUse)
            }
        }
        canvas.drawCircle(playerX, playerY, playerRadius, playerPaint)
        dpadButtons.forEach { (direction, rect) ->
            val cx = rect.centerX()
            val cy = rect.centerY()
            val size = rect.width().coerceAtMost(rect.height()) * 0.4f
            val arrowPath = Path()
            when (direction) {
                "UP" -> { arrowPath.moveTo(cx, cy - size); arrowPath.lineTo(cx - size, cy + size); arrowPath.lineTo(cx + size, cy + size); arrowPath.close() }
                "DOWN" -> { arrowPath.moveTo(cx, cy + size); arrowPath.lineTo(cx - size, cy - size); arrowPath.lineTo(cx + size, cy - size); arrowPath.close() }
                "LEFT" -> { arrowPath.moveTo(cx - size, cy); arrowPath.lineTo(cx + size, cy - size); arrowPath.lineTo(cx + size, cy + size); arrowPath.close() }
                "RIGHT" -> { arrowPath.moveTo(cx + size, cy); arrowPath.lineTo(cx - size, cy - size); arrowPath.lineTo(cx - size, cy + size); arrowPath.close() }
            }
            canvas.drawPath(arrowPath, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true; alpha = 180 })
            canvas.drawPath(arrowPath, Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true })
        }
        if (isDrawingLine && currentLine.size > 1) {
            for (i in 0 until currentLine.size - 1) {
                val start = currentLine[i]
                val end = currentLine[i + 1]
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
            }
        }

        // Bu kısım artık sınıfın başka bir yerinde tanımlı olduğu için silindi
        // val revealedArea = getRevealedArea()
        // val totalArea = playfield.width() * playfield.height()
        // val percent = if (totalArea > 0f) (revealedArea / totalArea * 100f).coerceIn(0f, 100f) else 0f
        val percent = revealPercent // Doğrudan revealPercent'i kullan

        // HUD'u onDraw'un sonunda, her şeyin üstünde çiz
        // if (!gameWon && !gameOver) {
        //     val hudTextPaint = Paint(hudPaint).apply { textAlign = Paint.Align.LEFT }
        //     canvas.drawText("CAN: $lives", 50f, 80f, hudTextPaint)
        //     val scoreText = "SKOR: $score"
        //     hudTextPaint.textAlign = Paint.Align.CENTER
        //     canvas.drawText(scoreText, width / 2f, 80f, hudTextPaint)
        //     hudTextPaint.textAlign = Paint.Align.RIGHT
        //     canvas.drawText("ZAMAN: $timerSeconds", width - 50f, 80f, hudTextPaint)
        //     canvas.drawText("%${percent.toInt()}", width - 50f, 140f, hudTextPaint)
        // }


        val bubblePaint = Paint().apply {
            color = Color.YELLOW
            textSize = 48f
            isAntiAlias = true
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
        }
        val iterator = scoreBubbles.iterator()
        while (iterator.hasNext()) {
            val bubble = iterator.next()
            bubble.y += bubble.dy
            bubble.alpha -= 0.03f
            bubblePaint.alpha = (bubble.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawText(bubble.text, bubble.x, bubble.y, bubblePaint)
            if (bubble.alpha <= 0f) iterator.remove()
        }
        if (gameOver) {
            val centerX = width / 2f
            val centerY = height / 2f
            val paint = Paint().apply {
                color = Color.RED
                textSize = 120f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                setShadowLayer(12f, 0f, 0f, Color.BLACK)
            }
            val message = if (timeOver) "SÜREN DOLDU" else "GAME OVER"
            canvas.drawText(message, centerX, centerY - 100f, paint)

            // Tekrar Oyna Butonu
            val buttonPaint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.FILL }
            val buttonTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 60f
                textAlign = Paint.Align.CENTER
            }
            gameOverButtonRect.set(centerX - 250f, centerY + 50f, centerX + 250f, centerY + 200f)
            canvas.drawRect(gameOverButtonRect, buttonPaint)
            canvas.drawText("Yeniden Oyna", centerX, centerY + 135f, buttonTextPaint)

            return
        }
        if (gameWon) {
            val centerX = width / 2f
            val centerY = height / 2f
            val paint = Paint().apply {
                color = Color.GREEN
                textSize = 100f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                setShadowLayer(12f, 0f, 0f, Color.BLACK)
            }
            canvas.drawText("OYUN TAMAMLANDI!", centerX, centerY, paint)
        }
    }

    private fun updateEnemies() {
        if (revealPercent >= 50f && bossCount < 3) {
            // ...
        } else if (revealPercent >= 20f && bossCount < 2) {
            // ...
        }
        for (enemy in enemies) {
            when {
                enemy.isHunter -> updateHunter(enemy)
                enemy.isStalker -> updateStalker(enemy)
                enemy.isMegaHunter -> updateMegaHunter(enemy)
                else -> updateMinion(enemy)
            }
        }
    }

    private fun updateStalker(stalker: Enemy) {
        if (isDrawingLine) {
            stalker.vx = 0f
            stalker.vy = 0f
            return
        }
        val dx = playerX - stalker.x
        val dy = playerY - stalker.y
        val currentEdge = getEdgeFromPoint(PointF(stalker.x, stalker.y))
        val playerEdge = getEdgeFromPoint(PointF(playerX, playerY))
        if (currentEdge == playerEdge) {
            if (currentEdge == Edge.TOP || currentEdge == Edge.BOTTOM) {
                stalker.vy = 0f
                stalker.vx = playerSpeed * 0.8f * Math.signum(dx)
            } else {
                stalker.vx = 0f
                stalker.vy = playerSpeed * 0.8f * Math.signum(dy)
            }
        } else {
            if (stalker.vx == 0f && stalker.vy == 0f) {
                if(currentEdge == Edge.TOP || currentEdge == Edge.BOTTOM) stalker.vx = playerSpeed * 0.8f
                else stalker.vy = playerSpeed * 0.8f
            }
        }
        stalker.x += stalker.vx
        stalker.y += stalker.vy
        stalker.x = stalker.x.coerceIn(playfield.left, playfield.right)
        stalker.y = stalker.y.coerceIn(playfield.top, playfield.bottom)
        val onCorner = (stalker.x == playfield.left || stalker.x == playfield.right) &&
                (stalker.y == playfield.top || stalker.y == playfield.bottom)
        if (onCorner) {
            if(playerEdge == Edge.LEFT || playerEdge == Edge.RIGHT){
                stalker.vx = 0f
                stalker.vy = playerSpeed * 0.8f * Math.signum(playerY - stalker.y)
            } else {
                stalker.vy = 0f
                stalker.vx = playerSpeed * 0.8f * Math.signum(playerX - stalker.x)
            }
        }
    }

    private fun updateHunter(hunter: Enemy) {
        val hunterAggroSpeed = 7f
        val hunterIdleSpeed = 2f
        if (isDrawingLine && currentLine.isNotEmpty()) {
            var closestPoint: PointF? = null
            var minDistanceSq = Float.MAX_VALUE
            for (i in 0 until currentLine.size - 1) {
                val p1 = currentLine[i]
                val p2 = currentLine[i + 1]
                val (distSq, point) = distanceToSegmentSquared(p1, p2, PointF(hunter.x, hunter.y), returnPoint = true)
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq
                    closestPoint = point
                }
            }
            closestPoint?.let {
                val dx = it.x - hunter.x
                val dy = it.y - hunter.y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > 1f) {
                    hunter.vx = (dx / distance) * hunterAggroSpeed
                    hunter.vy = (dy / distance) * hunterAggroSpeed
                }
            }
        } else {
            if (hunter.vx.let { it * it } < 1f) {
                hunter.vx = (Random.nextFloat() - 0.5f) * 2f * hunterIdleSpeed
                hunter.vy = (Random.nextFloat() - 0.5f) * 2f * hunterIdleSpeed
            }
        }
        hunter.x += hunter.vx
        hunter.y += hunter.vy
        if (hunter.x - hunter.radius < playfield.left || hunter.x + hunter.radius > playfield.right) {
            hunter.vx *= -1
            hunter.x = hunter.x.coerceIn(playfield.left + hunter.radius, playfield.right - hunter.radius)
        }
        if (hunter.y - hunter.radius < playfield.top || hunter.y + hunter.radius > playfield.bottom) {
            hunter.vy *= -1
            hunter.y = hunter.y.coerceIn(playfield.top + hunter.radius, playfield.bottom - hunter.radius)
        }
    }

    private fun updateMegaHunter(megaHunter: Enemy) {
        val megaHunterAggroSpeed = 5f
        val megaHunterIdleSpeed = 3f
        if (isDrawingLine && currentLine.isNotEmpty()) {
            var closestPoint: PointF? = null
            var minDistanceSq = Float.MAX_VALUE
            for (i in 0 until currentLine.size - 1) {
                val p1 = currentLine[i]
                val p2 = currentLine[i + 1]
                val (distSq, point) = distanceToSegmentSquared(p1, p2, PointF(megaHunter.x, megaHunter.y), returnPoint = true)
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq
                    closestPoint = point
                }
            }
            closestPoint?.let {
                val dx = it.x - megaHunter.x
                val dy = it.y - megaHunter.y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > 1f) {
                    megaHunter.vx = (dx / distance) * megaHunterAggroSpeed
                    megaHunter.vy = (dy / distance) * megaHunterAggroSpeed
                }
            }
        } else {
            megaHunter.directionChangeTimer--
            if (megaHunter.directionChangeTimer <= 0) {
                megaHunter.vx = (Random.nextFloat() - 0.5f) * 2f * megaHunterIdleSpeed
                megaHunter.vy = (Random.nextFloat() - 0.5f) * 2f * megaHunterIdleSpeed
                megaHunter.directionChangeTimer = Random.nextInt(60, 180)
            }
        }
        megaHunter.x += megaHunter.vx
        megaHunter.y += megaHunter.vy
        if (megaHunter.x + megaHunter.radius < playfield.left) megaHunter.x = playfield.right + megaHunter.radius
        if (megaHunter.x - megaHunter.radius > playfield.right) megaHunter.x = playfield.left - megaHunter.radius
        if (megaHunter.y + megaHunter.radius < playfield.top) megaHunter.y = playfield.bottom + megaHunter.radius
        if (megaHunter.y - megaHunter.radius > playfield.bottom) megaHunter.y = playfield.top - megaHunter.radius
    }


    private fun updateMinion(enemy: Enemy) {
        val speedMultiplier = 1f + 0.2f * (getRevealedArea() / (playfield.width() * playfield.height()))
        enemy.x += enemy.vx * speedMultiplier
        enemy.y += enemy.vy * speedMultiplier
        if (enemy.x - enemy.radius < playfield.left || enemy.x + enemy.radius > playfield.right) {
            enemy.vx *= -1
            enemy.x = enemy.x.coerceIn(playfield.left + enemy.radius, playfield.right - enemy.radius)
        }
        if (enemy.y - enemy.radius < playfield.top || enemy.y + enemy.radius > playfield.bottom) {
            enemy.vy *= -1
            enemy.y = enemy.y.coerceIn(playfield.top + enemy.radius, playfield.bottom - enemy.radius)
        }
    }

    private fun checkCollisions() {
        if(running.not()) return

        if (isDrawingLine && currentLine.size > 1) {
            for (enemy in enemies) {
                for (i in 0 until currentLine.size - 1) {
                    val p1 = currentLine[i]
                    val p2 = currentLine[i + 1]
                    val (distanceSquared, _) = distanceToSegmentSquared(p1, p2, PointF(enemy.x, enemy.y), returnPoint = false)
                    if (distanceSquared < (enemy.radius + linePaint.strokeWidth).let { it * it }) {
                        handlePlayerHit()
                        return
                    }
                }
            }
        }
        else {
            for (enemy in enemies) {
                if (enemy.isStalker) {
                    val dx = enemy.x - playerX
                    val dy = enemy.y - playerY
                    if (dx * dx + dy * dy < (enemy.radius + playerRadius).let { it * it }) {
                        handlePlayerHit()
                        return
                    }
                }
            }
        }
    }

    private fun handlePlayerHit() {
        lives--
        isDrawingLine = false
        currentLine.clear()
        playerX = playfield.left
        playerY = playfield.top
        playerVx = 0f
        playerVy = 0f
        gameStateListener?.onGameStateChanged(lives, score, timerSeconds, revealPercent)
        if (lives <= 0) {
            gameOver = true
            running = false
        }
    }

    private fun checkEnemyLineCollision() {
        // Bu fonksiyon artık checkCollisions içinde yönetiliyor.
    }

    private fun distanceToSegmentSquared(p1: PointF, p2: PointF, p: PointF, returnPoint: Boolean): Pair<Float, PointF?> {
        val l2 = (p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y)
        if (l2 == 0f) {
            val distSq = (p.x - p1.x) * (p.x - p1.x) + (p.y - p1.y) * (p.y - p1.y)
            return distSq to (if (returnPoint) p1 else null)
        }
        val t = ((p.x - p1.x) * (p2.x - p1.x) + (p.y - p1.y) * (p2.y - p1.y)) / l2
        val clampedT = t.coerceIn(0f, 1f)
        val closestX = p1.x + clampedT * (p2.x - p1.x)
        val closestY = p1.y + clampedT * (p2.y - p1.y)
        val distSq = (p.x - closestX) * (p.x - closestX) + (p.y - closestY) * (p.y - closestY)
        val closestPoint = PointF(closestX, closestY)
        return distSq to (if (returnPoint) closestPoint else null)
    }

    private fun moveEnemiesOffScreen() {
        for (enemy in enemies) {
            enemy.x = -1000f
            enemy.y = -1000f
        }
    }
}
