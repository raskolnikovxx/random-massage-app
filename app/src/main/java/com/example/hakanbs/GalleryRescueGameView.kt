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
    // YENİ: isStalker bayrağı eklendi
    private data class Enemy(
        var x: Float, var y: Float, var vx: Float, var vy: Float, val radius: Float,
        val isHunter: Boolean = false,
        val isStalker: Boolean = false, // YENİ
        val isMegaHunter: Boolean = false,
        var directionChangeTimer: Int = 0
    )
    private val enemies = mutableListOf<Enemy>()
    private val enemyPaint = Paint().apply { color = Color.GREEN }
    private val hunterPaint = Paint().apply { color = Color.MAGENTA }
    private val stalkerPaint = Paint().apply { color = Color.CYAN } // YENİ: Stalker için boya
    private val megaHunterPaint = Paint().apply { color = Color.rgb(255, 100, 0) }
    private var hunterBoss: Enemy? = null
    private var stalkerBoss: Enemy? = null // YENİ
    private var megaHunterBoss: Enemy? = null
    private var bossCount = 1

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

    init {
        post {
            setupNewGame()
            handler.post(gameLoop)
        }
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
        // Normal düşmanları oluştur
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
        // Avcı (Hunter) boss'u oluştur
        hunterBoss = Enemy(
            playfield.centerX(), playfield.centerY(),
            2f, 2f, 25f, isHunter = true
        )
        enemies.add(hunterBoss!!)

        // YENİ: Takipçi (Stalker) boss'u oluştur
        stalkerBoss = Enemy(
            playfield.right, playfield.bottom, // Başlangıç konumu
            -playerSpeed * 0.8f, 0f, // Başlangıç hızı
            22f, // Boyutu
            isStalker = true
        )
        enemies.add(stalkerBoss!!)

        // Mega Avcı (Mega Hunter) boss'u oluştur
        megaHunterBoss = Enemy(
            playfield.centerX() + 100f, playfield.centerY() - 100f,
            1f, -1f,
            75f,
            isMegaHunter = true,
            directionChangeTimer = 60
        )
        enemies.add(megaHunterBoss!!)


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
        invalidate()
    }

    private fun startTimer() {
        timerHandler.removeCallbacksAndMessages(null)
        timerHandler.post(object : Runnable {
            override fun run() {
                if (running && !gameOver && !gameWon && timerSeconds > 0) {
                    timerSeconds--
                    timerHandler.postDelayed(this, 1000)
                } else if (timerSeconds <= 0) {
                    handlePlayerHit()
                }
            }
        })
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
        if (gameOver || gameWon) {
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


    private fun getRevealPercent(): Float {
        val revealedArea = getRevealedArea()
        val totalArea = playfield.width() * playfield.height()
        return if (totalArea > 0f) (revealedArea / totalArea * 100f) else 0f
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
                // YENİ: Stalker'ın da hapsedilemeyeceğini belirt
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
            if (revealPercent >= winPercentage) {
                gameWon = true
                score += timerSeconds * 100L
                timerHandler.removeCallbacksAndMessages(null)
            }
        }
        invalidate()
    }

    private fun isOnEdge(x: Float, y: Float, tolerance: Float = 15f): Boolean {
        return (x <= playfield.left + tolerance ||
                x >= playfield.right - tolerance ||
                y <= playfield.top + tolerance ||
                y >= playfield.bottom - tolerance)
    }

    private fun snapToWall(x: Float, y: Float): PointF {
        return PointF()
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
            // YENİ: Stalker'ı farklı renkte çiz
            val paintToUse = when {
                enemy.isHunter -> hunterPaint
                enemy.isStalker -> stalkerPaint
                enemy.isMegaHunter -> megaHunterPaint
                else -> enemyPaint
            }
            canvas.drawCircle(enemy.x, enemy.y, enemy.radius, paintToUse)
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

        val hudPaint = Paint().apply {
            color = Color.WHITE
            textSize = 54f
            isAntiAlias = true
            style = Paint.Style.FILL
            setShadowLayer(6f, 2f, 2f, Color.BLACK)
        }
        val percent = getRevealPercent()
        val hudTextPaint = Paint(hudPaint)
        hudTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("CAN: $lives", 50f, 80f, hudTextPaint)
        val scoreText = "SKOR: $score"
        hudTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(scoreText, width / 2f, 80f, hudTextPaint)
        hudTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("ZAMAN: $timerSeconds", width - 50f, 80f, hudTextPaint)
        canvas.drawText("%${percent.toInt()}", width - 50f, 140f, hudTextPaint)
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
    }

    // DEĞİŞTİRİLDİ: Artık tüm boss türlerini güncelliyor
    private fun updateEnemies() {
        if (revealPercent >= 50f && bossCount < 3) {
            // ...
        } else if (revealPercent >= 20f && bossCount < 2) {
            // ...
        }
        for (enemy in enemies) {
            when {
                enemy.isHunter -> updateHunter(enemy)
                enemy.isStalker -> updateStalker(enemy) // YENİ
                enemy.isMegaHunter -> updateMegaHunter(enemy)
                else -> updateMinion(enemy)
            }
        }
    }

    // YENİ: Stalker boss'un özel hareket mantığı
    private fun updateStalker(stalker: Enemy) {
        if (isDrawingLine) {
            // Oyuncu çizim yaparken Stalker durur.
            stalker.vx = 0f
            stalker.vy = 0f
            return
        }

        // Oyuncuya doğru duvar üzerinde hareket et
        val dx = playerX - stalker.x
        val dy = playerY - stalker.y

        val currentEdge = getEdgeFromPoint(PointF(stalker.x, stalker.y))
        val playerEdge = getEdgeFromPoint(PointF(playerX, playerY))

        // Hareket yönünü belirle
        if (currentEdge == playerEdge) {
            // Eğer aynı duvardaysalar, direkt takip et
            if (currentEdge == Edge.TOP || currentEdge == Edge.BOTTOM) {
                stalker.vy = 0f
                stalker.vx = playerSpeed * 0.8f * Math.signum(dx)
            } else { // LEFT veya RIGHT
                stalker.vx = 0f
                stalker.vy = playerSpeed * 0.8f * Math.signum(dy)
            }
        } else {
            // Farklı duvarlardaysalar, en yakın köşeye git. Mevcut hızını koru.
            // Bu mantık, stalker'ın köşeye ulaştığında yön değiştirmesiyle çalışır.
            if (stalker.vx == 0f && stalker.vy == 0f) {
                if(currentEdge == Edge.TOP || currentEdge == Edge.BOTTOM) stalker.vx = playerSpeed * 0.8f
                else stalker.vy = playerSpeed * 0.8f
            }
        }

        stalker.x += stalker.vx
        stalker.y += stalker.vy

        // Duvar sınırlarında kalmasını sağla
        stalker.x = stalker.x.coerceIn(playfield.left, playfield.right)
        stalker.y = stalker.y.coerceIn(playfield.top, playfield.bottom)

        val onCorner = (stalker.x == playfield.left || stalker.x == playfield.right) &&
                (stalker.y == playfield.top || stalker.y == playfield.bottom)

        if (onCorner) {
            // Köşeye ulaştığında, oyuncunun olduğu duvara göre yönünü akıllıca değiştir
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

    // YENİ: Merkezi çarpışma kontrolü
    private fun checkCollisions() {
        if(running.not()) return

        // Çizgiye çarpma
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
        // Oyuncuya çarpma (duvar üzerindeyken)
        else {
            for (enemy in enemies) {
                // Sadece Stalker duvar üzerindeyken oyuncuya çarpabilir
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

    // YENİ: Can kaybetme ve sıfırlama işlemlerini merkezileştiren fonksiyon
    private fun handlePlayerHit() {
        lives--
        isDrawingLine = false
        currentLine.clear()
        playerX = playfield.left
        playerY = playfield.top
        playerVx = 0f
        playerVy = 0f
        if (lives <= 0) {
            gameOver = true
            running = false
        }
    }

    // Bu fonksiyon artık checkCollisions içinde yönetiliyor.
    private fun checkEnemyLineCollision() {
        // Bu fonksiyon artık kullanılmıyor. gameLoop'tan çağrısını silebiliriz.
    }

    private fun distanceToSegmentSquared(p1: PointF, p2: PointF, p: PointF, returnPoint: Boolean): Pair<Float, PointF?> {
        val l2 = (p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y)
        if (l2 == 0f) {
            val distSq = (p.x - p1.x) * (p.x - p1.x) + (p.y - p1.y) * (p.y - p1.y)
            return distSq to (if (returnPoint) p1 else null)
        }
        var t = ((p.x - p1.x) * (p2.x - p1.x) + (p.y - p1.y) * (p2.y - p1.y)) / l2
        t = t.coerceIn(0f, 1f)
        val closestX = p1.x + t * (p2.x - p1.x)
        val closestY = p1.y + t * (p2.y - p1.y)
        val closestPoint = if (returnPoint) PointF(closestX, closestY) else null
        val distSq = (p.x - closestX) * (p.x - closestX) + (p.y - closestY) * (p.y - closestY)
        return distSq to closestPoint
    }
}

