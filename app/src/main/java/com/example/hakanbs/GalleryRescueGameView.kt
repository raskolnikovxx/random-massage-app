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

    // --- YENİ YARDIMCI KODLAR ---
    // Bir noktanın hangi duvarda olduğunu anlamak için.
    private enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

    private fun getEdgeFromPoint(p: PointF, tolerance: Float = 15f): Edge? {
        if (p.y <= playfield.top + tolerance) return Edge.TOP
        if (p.y >= playfield.bottom - tolerance) return Edge.BOTTOM
        if (p.x <= playfield.left + tolerance) return Edge.LEFT
        if (p.x >= playfield.right - tolerance) return Edge.RIGHT
        return null
    }

    // Bir poligonun alanını hesaplamak için (küçük alanı seçmemizi sağlayacak).
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
    // --- YENİ YARDIMCI KODLARIN SONU ---


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
        playfield.set(padding, padding * 2, width - padding, height - padding)

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

        // drawable klasörünüzde "arkaplan_resmi.png" veya ".jpg" adında bir dosya olduğundan emin olun.
        backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.arkaplan_resmi)

        lives = 3
        isDrawingLine = false
        running = true
        gameOver = false
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                setupNewGame()
            }
            return true
        }

        val touchX = event.x.coerceIn(playfield.left, playfield.right)
        val touchY = event.y.coerceIn(playfield.top, playfield.bottom)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isDrawingLine && isOnEdge(touchX, touchY)) {
                    isDrawingLine = true
                    currentLine.clear()
                    currentLine.add(PointF(touchX, touchY))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawingLine) {
                    currentLine.add(PointF(touchX, touchY))
                    if (currentLine.size > 2 && isOnEdge(touchX, touchY)) {
                        captureAndRevealArea()
                        isDrawingLine = false
                        currentLine.clear()
                    }
                } else {
                    playerX = snapToWall(touchX, touchY).x
                    playerY = snapToWall(touchX, touchY).y
                }
            }
            MotionEvent.ACTION_UP -> {
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

    // DEĞİŞTİRİLDİ: Bu fonksiyon artık duvar köşelerini hesaba katan akıllı bir mantık kullanıyor.
    private fun captureAndRevealArea() {
        if (currentLine.size < 2) return

        val firstPoint = currentLine.first()
        val lastPoint = currentLine.last()

        val startEdge = getEdgeFromPoint(firstPoint)
        val endEdge = getEdgeFromPoint(lastPoint)

        if (startEdge == null || endEdge == null) {
            return
        }

        // Eğer çizgi aynı duvarda başlayıp biterse, basitçe kapat.
        if (startEdge == endEdge) {
            val capturedPath = Path()
            capturedPath.moveTo(firstPoint.x, firstPoint.y)
            for (point in currentLine) {
                capturedPath.lineTo(point.x, point.y)
            }
            capturedPath.close()
            revealedPaths.add(capturedPath)
            return
        }

        // Farklı duvarlar için, köşeleri takip ederek iki olası alan oluştur.
        val topLeft = PointF(playfield.left, playfield.top)
        val topRight = PointF(playfield.right, playfield.top)
        val bottomLeft = PointF(playfield.left, playfield.bottom)
        val bottomRight = PointF(playfield.right, playfield.bottom)

        val poly1Points = mutableListOf<PointF>().apply { addAll(currentLine) }
        val poly2Points = mutableListOf<PointF>().apply { addAll(currentLine) }

        // Duvarları takip etmek için yardımcı haritalar
        val clockwiseNextCorner = mapOf(Edge.TOP to topRight, Edge.RIGHT to bottomRight, Edge.BOTTOM to bottomLeft, Edge.LEFT to topLeft)
        val clockwiseNextEdge = mapOf(Edge.TOP to Edge.RIGHT, Edge.RIGHT to Edge.BOTTOM, Edge.BOTTOM to Edge.LEFT, Edge.LEFT to Edge.TOP)

        val counterClockwiseNextCorner = mapOf(Edge.TOP to topLeft, Edge.LEFT to bottomLeft, Edge.BOTTOM to bottomRight, Edge.RIGHT to topRight)
        val counterClockwiseNextEdge = mapOf(Edge.TOP to Edge.LEFT, Edge.LEFT to Edge.BOTTOM, Edge.BOTTOM to Edge.RIGHT, Edge.RIGHT to Edge.TOP)

        // Poligon 1: Saat yönünde duvarları takip et
        var currentEdge = endEdge
        while (currentEdge != startEdge) {
            clockwiseNextCorner[currentEdge]?.let { poly1Points.add(it) }
            currentEdge = clockwiseNextEdge[currentEdge]!!
        }

        // Poligon 2: Saat yönünün tersinde duvarları takip et
        currentEdge = endEdge
        while (currentEdge != startEdge) {
            counterClockwiseNextCorner[currentEdge]?.let { poly2Points.add(it) }
            currentEdge = counterClockwiseNextEdge[currentEdge]!!
        }

        // Alanları hesapla ve küçük olanı seç (Qix kuralı)
        val area1 = getPolygonArea(poly1Points)
        val area2 = getPolygonArea(poly2Points)
        val pointsToUse = if (area1 < area2) poly1Points else poly2Points

        // Seçilen poligonu Path'e dönüştür
        if (pointsToUse.size >= 3) {
            val capturedPath = Path()
            capturedPath.moveTo(pointsToUse.first().x, pointsToUse.first().y)
            for (point in pointsToUse) {
                capturedPath.lineTo(point.x, point.y)
            }
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

        if (!isDrawingLine) {
            canvas.drawCircle(playerX, playerY, playerRadius, playerPaint)
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
