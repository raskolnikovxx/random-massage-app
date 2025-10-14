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
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withClip
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

    // --- ÇİZİM İÇİN GEREKLİLER ---
    private var currentLine = mutableListOf<PointF>()
    private val revealedPaths = mutableListOf<Path>()

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
    private data class Enemy(var x: Float, var y: Float, var vx: Float, var vy: Float, val radius: Float)
    private val enemies = mutableListOf<Enemy>()
    private val enemyPaint = Paint().apply { color = Color.GREEN }

    // --- OYUN DÖNGÜSÜ ---
    private val handler = Handler(Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (running) {
                updatePlayer() // Oyuncu hareketini her frame'de güncelle
                updateEnemies()
                checkEnemyLineCollision()
                invalidate()
                handler.postDelayed(this, 16) // ~60 FPS
            }
        }
    }

    init {
        post {
            setupNewGame()
            handler.post(gameLoop)
        }
    }

    private fun setupNewGame() {
        val padding = 40f
        playfield.set(padding, padding * 2, width - padding, height - padding - 300f) // YENİ: Tuşlar için altta daha fazla yer bırak

        // Yönlendirme tuşlarını oluştur
        setupDpadButtons()

        playerX = playfield.left
        playerY = playfield.top

        revealedPaths.clear()
        currentLine.clear()
        enemies.clear()

        repeat(3) {
            enemies.add(
                Enemy(
                    playfield.centerX() + Random.nextFloat() * 200f - 100f,
                    playfield.centerY() + Random.nextFloat() * 200f - 100f,
                    (Random.nextFloat() - 0.5f) * 8f,
                    (Random.nextFloat() - 0.5f) * 8f,
                    18f
                )
            )
        }
        backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.arkaplan_resmi)
        lives = 3
        isDrawingLine = false
        running = true
        gameOver = false
        invalidate()
    }

    // DEĞİŞTİRİLDİ: Yön tuşları artık 4 yönlü ve daha büyük.
    private fun setupDpadButtons() {
        val buttonSize = 180f // Tuşlar artık daha büyük
        val gap = 20f
        val centerX = width / 2f
        val bottomAreaY = height - buttonSize - gap

        // D-Pad'i ekranın alt ortasına yerleştiriyoruz.
        dpadButtons["UP"] = RectF(centerX - buttonSize / 2, bottomAreaY - buttonSize, centerX + buttonSize / 2, bottomAreaY)
        dpadButtons["DOWN"] = RectF(centerX - buttonSize / 2, bottomAreaY, centerX + buttonSize / 2, bottomAreaY + buttonSize)
        dpadButtons["LEFT"] = RectF(centerX - buttonSize * 1.5f, bottomAreaY, centerX - buttonSize / 2, bottomAreaY + buttonSize)
        dpadButtons["RIGHT"] = RectF(centerX + buttonSize / 2, bottomAreaY, centerX + buttonSize * 1.5f, bottomAreaY + buttonSize)
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver) {
            if (event.action == MotionEvent.ACTION_DOWN) setupNewGame()
            return true
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
                        isDrawingLine = false
                        currentLine.clear()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                playerVx = 0f
                playerVy = 0f

                if (isDrawingLine && currentLine.size > 2 && isOnEdge(touchX, touchY)) {
                    captureAndRevealArea()
                }
                isDrawingLine = false
                currentLine.clear()
            }
        }
        invalidate()
        return true
    }

    // DEĞİŞTİRİLDİ: Oyuncunun hangi duvarda olduğuna göre hareket etmesini sağlar.
    private fun movePlayer(direction: String) {
        val edge = getEdgeFromPoint(PointF(playerX, playerY))
        val onLeft = playerX == playfield.left
        val onRight = playerX == playfield.right
        val onTop = playerY == playfield.top
        val onBottom = playerY == playfield.bottom
        val onCorner = (onLeft || onRight) && (onTop || onBottom)

        when (direction) {
            "LEFT" -> if (onTop || onBottom || onCorner) {
                playerVy = 0f
                playerVx = -playerSpeed
            }
            "RIGHT" -> if (onTop || onBottom || onCorner) {
                playerVy = 0f
                playerVx = playerSpeed
            }
            "UP" -> if (onLeft || onRight || onCorner) {
                playerVx = 0f
                playerVy = -playerSpeed
            }
            "DOWN" -> if (onLeft || onRight || onCorner) {
                playerVx = 0f
                playerVy = playerSpeed
            }
        }
    }

    // DEĞİŞTİRİLDİ: Oyuncunun hareketini ve köşeye geldiğinde durmasını yönetir.
    private fun updatePlayer() {
        if (playerVx == 0f && playerVy == 0f) return

        playerX += playerVx
        playerY += playerVy

        // Oyuncuyu oyun alanı içinde tut
        playerX = playerX.coerceIn(playfield.left, playfield.right)
        playerY = playerY.coerceIn(playfield.top, playfield.bottom)

        // Köşeye ulaşıldı mı kontrol et
        val onLeft = playerX == playfield.left
        val onRight = playerX == playfield.right
        val onTop = playerY == playfield.top
        val onBottom = playerY == playfield.bottom

        val onCorner = (onLeft || onRight) && (onTop || onBottom)

        if (onCorner) {
            // Köşeye ulaşıldığında dur. Yeni yön tuşu basımını bekle.
            playerVx = 0f
            playerVy = 0f
        }
    }


    private fun captureAndRevealArea() {
        if (currentLine.size < 2) return

        val firstPoint = currentLine.first()
        val lastPoint = currentLine.last()

        val startEdge = getEdgeFromPoint(firstPoint)
        val endEdge = getEdgeFromPoint(lastPoint)

        if (startEdge == null || endEdge == null) {
            return
        }

        if (startEdge == endEdge) {
            val capturedPath = Path()
            capturedPath.moveTo(firstPoint.x, firstPoint.y)
            for (point in currentLine) capturedPath.lineTo(point.x, point.y)
            capturedPath.close()
            revealedPaths.add(capturedPath)
            return
        }

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

        val area1 = getPolygonArea(poly1Points)
        val area2 = getPolygonArea(poly2Points)
        val pointsToUse = if (area1 < area2) poly1Points else poly2Points

        if (pointsToUse.size >= 3) {
            val capturedPath = Path()
            capturedPath.moveTo(pointsToUse.first().x, pointsToUse.first().y)
            for (point in pointsToUse) capturedPath.lineTo(point.x, point.y)
            capturedPath.close()
            revealedPaths.add(capturedPath)
        }
    }


    private fun isOnEdge(x: Float, y: Float, tolerance: Float = 15f): Boolean {
        return (x <= playfield.left + tolerance ||
                x >= playfield.right - tolerance ||
                y <= playfield.top + tolerance ||
                y >= playfield.bottom - tolerance)
    }

    private fun snapToWall(x: Float, y: Float): PointF {
        val distToLeft = x - playfield.left
        val distToRight = playfield.right - x
        val distToTop = y - playfield.top
        val distToBottom = playfield.bottom - y

        val minHorizontal = minOf(distToLeft, distToRight)
        val minVertical = minOf(distToTop, distToBottom)

        return if (minHorizontal < minVertical) {
            if (distToLeft < distToRight) PointF(playfield.left, y) else PointF(playfield.right, y)
        } else {
            if (distToTop < distToBottom) PointF(x, playfield.top) else PointF(x, playfield.bottom)
        }
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        backgroundBitmap?.let { bmp ->
            for (path in revealedPaths) {
                canvas.withClip(path) {
                    drawBitmap(bmp, null, playfield, null)
                }
            }
        }

        canvas.drawRect(playfield, borderPaint)

        for (enemy in enemies) {
            canvas.drawCircle(enemy.x, enemy.y, enemy.radius, enemyPaint)
        }

        canvas.drawCircle(playerX, playerY, playerRadius, playerPaint)

        // Yön tuşlarını çiz (artık dikdörtgen değil, vektör ok)
        dpadButtons.forEach { (direction, rect) ->
            val cx = rect.centerX()
            val cy = rect.centerY()
            val size = rect.width().coerceAtMost(rect.height()) * 0.4f

            val arrowPath = Path()
            when (direction) {
                "UP" -> {
                    arrowPath.moveTo(cx, cy - size) // tepe
                    arrowPath.lineTo(cx - size, cy + size)
                    arrowPath.lineTo(cx + size, cy + size)
                    arrowPath.close()
                }
                "DOWN" -> {
                    arrowPath.moveTo(cx, cy + size) // tepe
                    arrowPath.lineTo(cx - size, cy - size)
                    arrowPath.lineTo(cx + size, cy - size)
                    arrowPath.close()
                }
                "LEFT" -> {
                    arrowPath.moveTo(cx - size, cy) // tepe
                    arrowPath.lineTo(cx + size, cy - size)
                    arrowPath.lineTo(cx + size, cy + size)
                    arrowPath.close()
                }
                "RIGHT" -> {
                    arrowPath.moveTo(cx + size, cy) // tepe
                    arrowPath.lineTo(cx - size, cy - size)
                    arrowPath.lineTo(cx - size, cy + size)
                    arrowPath.close()
                }
            }
            canvas.drawPath(arrowPath, Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
                alpha = 180
            })
            // Okun kenarına hafif bir kontur
            canvas.drawPath(arrowPath, Paint().apply {
                color = Color.DKGRAY
                style = Paint.Style.STROKE
                strokeWidth = 6f
                isAntiAlias = true
            })
        }

        if (isDrawingLine && currentLine.size > 1) {
            for (i in 0 until currentLine.size - 1) {
                val start = currentLine[i]
                val end = currentLine[i + 1]
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
            }
        }
    }

    private fun updateEnemies() {
        for (enemy in enemies) {
            enemy.x += enemy.vx
            enemy.y += enemy.vy

            if (enemy.x - enemy.radius < playfield.left || enemy.x + enemy.radius > playfield.right) {
                enemy.vx *= -1
                enemy.x = enemy.x.coerceIn(playfield.left + enemy.radius, playfield.right - enemy.radius)
            }
            if (enemy.y - enemy.radius < playfield.top || enemy.y + enemy.radius > playfield.bottom) {
                enemy.vy *= -1
                enemy.y = enemy.y.coerceIn(playfield.top + enemy.radius, playfield.bottom - enemy.radius)
            }
        }
    }

    private fun checkEnemyLineCollision() {
        if (!isDrawingLine || currentLine.size < 2) return

        for (enemy in enemies) {
            for (i in 0 until currentLine.size - 1) {
                val p1 = currentLine[i]
                val p2 = currentLine[i + 1]
                val enemyPoint = PointF(enemy.x, enemy.y)
                val distanceSquared = distanceToSegmentSquared(enemyPoint, p1, p2)

                if (distanceSquared < (enemy.radius + linePaint.strokeWidth) * (enemy.radius + linePaint.strokeWidth)) {
                    lives--
                    isDrawingLine = false
                    currentLine.clear()
                    if (lives <= 0) {
                        gameOver = true
                    }
                    return
                }
            }
        }
    }

    private fun distanceToSegmentSquared(p: PointF, v: PointF, w: PointF): Float {
        val l2 = (v.x - w.x) * (v.x - w.x) + (v.y - w.y) * (v.y - w.y)
        if (l2 == 0f) return (p.x - v.x) * (p.x - v.x) + (p.y - v.y) * (p.y - v.y)
        var t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
        t = t.coerceIn(0f, 1f)
        val closestX = v.x + t * (w.x - v.x)
        val closestY = v.y + t * (w.y - v.y)
        return (p.x - closestX) * (p.x - closestX) + (p.y - closestY) * (p.y - closestY)
    }
}
