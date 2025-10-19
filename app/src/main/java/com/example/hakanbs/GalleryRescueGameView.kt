package com.example.hakanbs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Region
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import coil.imageLoader
import coil.request.ImageRequest
import org.json.JSONObject
import java.io.InputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import android.graphics.RadialGradient
import android.graphics.Shader

class GalleryRescueGameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // --- YARDIMCI KODLAR ---
    private enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

    // --- DOKUNUŞ AYARLARI ---
    private val TOUCH_START_RADIUS_MULTIPLIER = 3.5f

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

    private fun isTouchOnPlayer(x: Float, y: Float): Boolean {
        val dx = x - playerX
        val dy = y - playerY
        val touchRadius = playerRadius * TOUCH_START_RADIUS_MULTIPLIER
        return dx * dx + dy * dy < touchRadius * touchRadius
    }

    // --- MANUEL KONTROL ---
    private val dpadButtons = mutableMapOf<String, RectF>()
    private val dpadPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        shader = RadialGradient(0f, 0f, 1f, Color.WHITE, Color.LTGRAY, Shader.TileMode.CLAMP)
    }
    private val dpadBorderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.DKGRAY
    }
    private val dpadArrowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.rgb(60, 60, 60)
    }
    private var playerSpeed = 10f
    private var playerVx = 0f
    private var playerVy = 0f

    // --- TEMEL OYUN DEĞİŞKENLERİ ---
    private var playfield = RectF()
    private var playerX = 0f
    private var playerY = 0f
    private var playerRadius = 32f
    private var backgroundDrawable: Drawable? = null
    private var backgroundUrl: String? = null

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
    private val winPercentage = 20f
    private var timeOver = false

    // --- ÇİZİM ---
    private var currentLine = mutableListOf<PointF>()
    private val revealedPaths = mutableListOf<Path>()
    private val revealedRegion = Region()
    private val drawingPath = Path()

    // --- BOYALAR ---
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
        val isArrowBoss: Boolean = false,
        var directionChangeTimer: Int = 0,
        var arrowCooldown: Int = 120,
        var isPreparingAttack: Boolean = false,
        var prepareAttackTimer: Int = 0
    )
    private data class Arrow(var x: Float, var y: Float, var vx: Float, var vy: Float, val radius: Float = 10f)

    private val enemies = mutableListOf<Enemy>()
    private val arrows = mutableListOf<Arrow>()
    private val enemyPaint = Paint().apply { color = Color.GREEN }
    private val hunterPaint = Paint().apply { color = Color.MAGENTA }
    private val stalkerPaint = Paint().apply { color = Color.CYAN }
    private val megaHunterPaint = Paint().apply { color = Color.rgb(255, 100, 0) }
    private val arrowBossPaint = Paint().apply { color = Color.WHITE }
    private val arrowPaint = Paint().apply { color = Color.LTGRAY }
    private var hunterBoss: Enemy? = null
    private var stalkerBoss: Enemy? = null
    private var megaHunterBoss: Enemy? = null
    private var arrowBoss: Enemy? = null
    private var bossCount = 1

    private val monsterDrawable = AppCompatResources.getDrawable(context, R.drawable.monster_cute)
    private val hunterDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_heart_boss)

    // --- OYUN DÖNGÜSÜ ---
    private val handler = Handler(Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (running && !gameOver && !gameWon) {
                updatePlayer()
                updateEnemies()
                updateArrows()
                checkCollisions()
                invalidate()
                handler.postDelayed(this, 16)
            }
        }
    }

    // --- UI BUTON RECTLARI ---
    private val restartButtonRect = RectF()
    private val nextLevelButtonRect = RectF()
    private val finishRestartButtonRect = RectF()

    // --- PUAN BALONCUKLARI ---
    private data class ScoreBubble(var x: Float, var y: Float, val text: String, var alpha: Float = 1f, var dy: Float = -2f)
    private val scoreBubbles = mutableListOf<ScoreBubble>()

    private val hudPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    interface GameStateListener {
        fun onGameStateChanged(lives: Int, score: Long, timerSeconds: Int, revealPercent: Float)
        fun onLevelChanged(newLevel: Int)
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

    // --- SEVİYE YÖNETİMİ ---
    private var level = 1
    private val maxLevel = 3
    private var showNextLevelButton = false

    // RemoteConfig erişimi
    private lateinit var controlConfig: ControlConfig
    private lateinit var remoteConfig: RemoteConfig

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controlConfig = ControlConfig(context)
        remoteConfig = controlConfig.getLocalConfig()
    }

    private fun getCurrentBackgroundUrl(): String? {
        return when (level) {
            1 -> remoteConfig.backgroundUrl1
            2 -> remoteConfig.backgroundUrl2
            3 -> remoteConfig.backgroundUrl3
            else -> null
        }?.takeIf { it.isNullOrBlank().not() }
    }

    private fun setupNewGame() {
        (backgroundDrawable as? Animatable)?.stop()
        unregisterGifCallbackIfAny()

        val padding = 80f
        playfield.set(padding, padding * 2, width - padding, height - padding - 300f)
        setupDpadButtons()
        playerX = playfield.left
        playerY = playfield.top
        revealedPaths.clear()
        currentLine.clear()
        enemies.clear()
        arrows.clear()
        bossCount = 1

        // Minyonlar
        repeat(3) {
            var ex: Float
            var ey: Float
            do {
                ex = playfield.centerX() + Random.nextFloat() * 200f - 100f
                ey = playfield.centerY() + Random.nextFloat() * 200f - 100f
            } while (!isSafeSpawn(ex, ey))
            enemies.add(
                Enemy(
                    ex, ey,
                    (Random.nextFloat() - 0.5f) * 8f,
                    (Random.nextFloat() - 0.5f) * 8f,
                    18f
                )
            )
        }

        // Hunter
        var hx: Float
        var hy: Float
        do {
            hx = playfield.centerX()
            hy = playfield.centerY()
        } while (!isSafeSpawn(hx, hy, 180f))
        hunterBoss = Enemy(hx, hy, 2f, 2f, 30f, isHunter = true)
        enemies.add(hunterBoss!!)

        // Hunter 2
        do {
            hx = playfield.centerX() - 120f
            hy = playfield.centerY() + 120f
        } while (!isSafeSpawn(hx, hy, 180f))
        val hunterBoss2 = Enemy(hx, hy, -2f, 2f, 30f, isHunter = true)
        enemies.add(hunterBoss2)

        // Stalker
        var sx: Float
        var sy: Float
        do {
            sx = playfield.right
            sy = playfield.bottom
        } while (!isSafeSpawn(sx, sy, 180f))
        stalkerBoss = Enemy(sx, sy, -playerSpeed * 0.8f, 0f, 22f, isStalker = true)
        enemies.add(stalkerBoss!!)

        // MegaHunter 1
        var mx: Float
        var my: Float
        do {
            mx = playfield.centerX() + 100f
            my = playfield.centerY() - 100f
        } while (!isSafeSpawn(mx, my, 200f))
        megaHunterBoss = Enemy(mx, my, 1f, -1f, 85f, isMegaHunter = true, directionChangeTimer = 60)
        enemies.add(megaHunterBoss!!)

        // MegaHunter 2
        do {
            mx = playfield.centerX() - 100f
            my = playfield.centerY() + 100f
        } while (!isSafeSpawn(mx, my, 200f))
        val megaHunterBoss2 = Enemy(mx, my, -1f, 1f, 85f, isMegaHunter = true, directionChangeTimer = 90)
        enemies.add(megaHunterBoss2)

        // Arrow Boss
        var ax: Float
        var ay: Float
        do {
            ax = playfield.centerX()
            ay = playfield.top + 150f
        } while (!isSafeSpawn(ax, ay, 250f))
        arrowBoss = Enemy(ax, ay, 1f, 1f, 40f, isArrowBoss = true, directionChangeTimer = 100, arrowCooldown = 180)
        enemies.add(arrowBoss!!)

        // --- ARKA PLAN ---
        getCurrentBackgroundUrl()?.let { setBackgroundUrl(it) }

        // --- OYUN DURUMLARI ---
        lives = 3
        isDrawingLine = false
        running = true
        gameOver = false
        gameWon = false
        score = 0L
        timerSeconds = 99
        totalRevealedArea = 0f
        revealPercent = 0f
        showNextLevelButton = false

        gameStateListener?.onGameStateChanged(lives, score, timerSeconds, revealPercent)
        invalidate()
        handler.removeCallbacks(gameLoop)
        handler.post(gameLoop)
    }

    // JSON fallback (kullanılmıyor ama dursun)
    private fun loadBackgroundFromJsonOrDefault() {
        val urlFromJson = try {
            val inputStream: InputStream = context.assets.open("default_config.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            json.optString("galleryRescueBackgroundUrl", null)
        } catch (e: Exception) {
            null
        }

        if (!urlFromJson.isNullOrBlank()) {
            setBackgroundUrl(urlFromJson)
        } else {
            val bmp = BitmapFactory.decodeResource(resources, R.drawable.arkaplan_resmi)
            this.backgroundDrawable = BitmapDrawable(resources, bmp)
            invalidate()
        }
    }

    // --- GIF LOOP CALLBACK YÖNETİMİ ---
    private var compatLoopCallback: Animatable2Compat.AnimationCallback? = null
    private var a28LoopCallback: android.graphics.drawable.Animatable2.AnimationCallback? = null

    private fun unregisterGifCallbackIfAny() {
        backgroundDrawable?.let { d ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (d as? android.graphics.drawable.AnimatedImageDrawable)?.let { aid ->
                    a28LoopCallback?.let { cb -> aid.unregisterAnimationCallback(cb) }
                }
                a28LoopCallback = null
            }
            (d as? Animatable2Compat)?.let { a2c ->
                compatLoopCallback?.let { cb -> a2c.unregisterAnimationCallback(cb) }
            }
            compatLoopCallback = null
        }
    }

    /** Decoder çıktısını sonsuz döngüye hazırlar (başlatmaz). */
    private fun prepareDrawableForInfiniteLoop(drawable: Drawable) {
        unregisterGifCallbackIfAny()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.let { aid ->
                // Sonsuz loop
                aid.repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                // Bitince otomatik yeniden başlat
                a28LoopCallback = object : android.graphics.drawable.Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(d: Drawable?) {
                        post {
                            try { (d as? android.graphics.drawable.AnimatedImageDrawable)?.start() } catch (_: Throwable) {}
                        }
                    }
                }
                a28LoopCallback?.let { cb -> aid.registerAnimationCallback(cb) } // instance method
                try { aid.stop() } catch (_: Throwable) {}
                return
            }
        }

        (drawable as? AnimationDrawable)?.let { ad ->
            ad.isOneShot = false
            try { ad.stop() } catch (_: Throwable) {}
            return
        }

        if (drawable is Animatable2Compat) {
            compatLoopCallback = object : Animatable2Compat.AnimationCallback() {
                override fun onAnimationEnd(d: Drawable?) {
                    post {
                        try { (d as? Animatable2Compat)?.start() } catch (_: Throwable) {}
                    }
                }
            }
            // Hakan'ın istediği instance metodları:
            drawable.registerAnimationCallback(compatLoopCallback!!)
            try { drawable.stop() } catch (_: Throwable) {}
            return
        }

        (drawable as? Animatable)?.let {
            try { it.stop() } catch (_: Throwable) {}
        }
    }

    fun setBackgroundUrl(url: String?) {
        if (!url.isNullOrBlank()) {
            this.backgroundUrl = url
            loadBackgroundFromUrlOrDefault()
        }
    }

    private fun loadBackgroundFromUrlOrDefault() {
        val dataToLoad = backgroundUrl ?: return
        val imageLoader = context.imageLoader
        val request = ImageRequest.Builder(context)
            .data(dataToLoad)
            .allowHardware(false) // AnimatedImageDrawable için güvenli
            .target(
                onSuccess = { result ->
                    unregisterGifCallbackIfAny()

                    backgroundDrawable?.callback = null
                    backgroundDrawable = result
                    backgroundDrawable?.callback = this@GalleryRescueGameView

                    // Decode olur olmaz sonsuz döngüye hazırla (başlatma):
                    prepareDrawableForInfiniteLoop(backgroundDrawable!!)

                    invalidate()
                },
                onError = {
                    // Hata olursa mevcut görseli koru
                    invalidate()
                }
            )
            .build()
        imageLoader.enqueue(request)
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return super.verifyDrawable(who) || who === backgroundDrawable
    }

    private fun setupDpadButtons() {
        val margin = height * 0.03f // Daha az margin
        val buttonSize = height * 0.06f // Daha küçük buton
        val bottom = height - margin
        val centerX = width / 2f
        val centerY = bottom - buttonSize * 1.3f // Dpad'i daha aşağıya çek
        dpadButtons.clear()
        dpadButtons["LEFT"] = RectF(centerX - buttonSize * 1.5f, centerY, centerX - buttonSize * 0.5f, centerY + buttonSize)
        dpadButtons["RIGHT"] = RectF(centerX + buttonSize * 0.5f, centerY, centerX + buttonSize * 1.5f, centerY + buttonSize)
        dpadButtons["UP"] = RectF(centerX - buttonSize * 0.5f, centerY - buttonSize, centerX + buttonSize * 0.5f, centerY)
        dpadButtons["DOWN"] = RectF(centerX - buttonSize * 0.5f, centerY + buttonSize, centerX + buttonSize * 0.5f, centerY + buttonSize * 2f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameWon && event.action == MotionEvent.ACTION_DOWN) {
            if (nextLevelButtonRect.contains(event.x, event.y)) {
                goToNextLevel()
                return true
            }
            if (level >= maxLevel && finishRestartButtonRect.contains(event.x, event.y)) {
                level = 1
                setupNewGame()
                gameStateListener?.onLevelChanged(level)
                invalidate()
                return true
            }
        }

        if (gameOver && event.action == MotionEvent.ACTION_DOWN) {
            if (restartButtonRect.contains(event.x, event.y)) {
                restartGame()
                return true
            }
        }

        if (gameOver || gameWon) return false

        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                var buttonPressed = false
                dpadButtons.forEach { (direction, rect) ->
                    if (rect.contains(touchX, touchY)) {
                        movePlayer(direction)
                        buttonPressed = true
                    }
                }

                if (!buttonPressed && !isDrawingLine && isTouchOnPlayer(touchX, touchY)) {
                    isDrawingLine = true
                    currentLine.clear()
                    currentLine.add(PointF(playerX, playerY))
                    drawingPath.reset()
                    drawingPath.moveTo(playerX, playerY)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawingLine) {
                    val clampedX = touchX.coerceIn(playfield.left, playfield.right)
                    val clampedY = touchY.coerceIn(playfield.top, playfield.bottom)

                    if (currentLine.isNotEmpty()) {
                        val lastPoint = currentLine.last()
                        val dx = clampedX - lastPoint.x
                        val dy = clampedY - lastPoint.y
                        val distanceSquared = dx * dx + dy * dy
                        val maxDistancePerFrame = playerSpeed * 52
                        if (distanceSquared > maxDistancePerFrame * maxDistancePerFrame) {
                            isDrawingLine = false
                            currentLine.clear()
                            drawingPath.reset()
                            parent?.requestDisallowInterceptTouchEvent(false)
                            invalidate()
                            return true
                        }
                    }

                    currentLine.add(PointF(clampedX, clampedY))
                    drawingPath.lineTo(clampedX, clampedY)

                    val currentEdge = getEdgeFromPoint(PointF(clampedX, clampedY))
                    if (currentLine.size > 2 && currentEdge != null) {
                        finishDrawing(clampedX, clampedY)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                playerVx = 0f
                playerVy = 0f

                if (isDrawingLine) {
                    val clampedX = touchX.coerceIn(playfield.left, playfield.right)
                    val clampedY = touchY.coerceIn(playfield.top, playfield.bottom)
                    val currentEdge = getEdgeFromPoint(PointF(clampedX, clampedY))
                    if (currentLine.size > 2 && currentEdge != null) {
                        finishDrawing(clampedX, clampedY)
                    } else {
                        isDrawingLine = false
                        currentLine.clear()
                        drawingPath.reset()
                    }
                }

                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate()
        return true
    }

    private fun finishDrawing(finalX: Float, finalY: Float) {
        if (!isDrawingLine) return

        captureAndRevealArea()
        val finalPosition = snapToWall(finalX, finalY)
        playerX = finalPosition.x
        playerY = finalPosition.y
        playerVx = 0f
        playerVy = 0f

        isDrawingLine = false
        currentLine.clear()
        drawingPath.reset()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun movePlayer(direction: String) {
        val onHorizontalWall = (playerY == playfield.top || playerY == playfield.bottom)
        val onVerticalWall = (playerX == playfield.left || playerX == playfield.right)

        when (direction) {
            "LEFT", "RIGHT" -> {
                if (onHorizontalWall) {
                    playerVy = 0f
                    playerVx = if (direction == "LEFT") -playerSpeed else playerSpeed
                }
            }
            "UP", "DOWN" -> {
                if (onVerticalWall) {
                    playerVx = 0f
                    playerVy = if (direction == "UP") -playerSpeed else playerSpeed
                }
            }
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
                if (enemy.isHunter || enemy.isStalker || enemy.isMegaHunter || enemy.isArrowBoss) continue
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
                // GIF sonsuz döngü için önceden hazırlandı; şimdi sadece başlatıyoruz.
                startBackgroundAnimationLooping()
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
        val bmp: Bitmap? = backgroundDrawable?.let { drawable ->
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                    null
                } else {
                    val bitmap = Bitmap.createBitmap(
                        drawable.intrinsicWidth,
                        drawable.intrinsicHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    val tempCanvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, tempCanvas.width, tempCanvas.height)
                    drawable.draw(tempCanvas)
                    bitmap
                }
            }
        }

        bmp?.let {
            val paint = Paint()
            paint.isAntiAlias = true
            paint.alpha = if (gameWon) 255 else (255 * 0.8f).toInt()
            for (path in revealedPaths) {
                canvas.save()
                canvas.clipPath(path)
                canvas.drawBitmap(it, null, playfield, paint)
                canvas.restore()
            }
        }

        canvas.drawRect(playfield, borderPaint)

        for (enemy in enemies) {
            val paintToUse = when {
                enemy.isMegaHunter -> megaHunterPaint
                enemy.isHunter -> hunterPaint
                enemy.isStalker -> stalkerPaint
                enemy.isArrowBoss -> arrowBossPaint
                else -> enemyPaint
            }

            var drawX = enemy.x
            var drawY = enemy.y

            if (enemy.isArrowBoss && enemy.isPreparingAttack) {
                val shakeAmount = 4f
                drawX += Random.nextFloat() * shakeAmount * 2 - shakeAmount
                drawY += Random.nextFloat() * shakeAmount * 2 - shakeAmount
            }

            if (enemy.isMegaHunter) {
                val size = enemy.radius * 4f
                val left = drawX - size / 2f
                val top = drawY - size / 2f
                val right = drawX + size / 2f
                val bottom = drawY + size / 2f
                monsterDrawable?.let {
                    it.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                    it.draw(canvas)
                } ?: run {
                    canvas.drawCircle(drawX, drawY, enemy.radius * 2f, paintToUse)
                }
            } else if (enemy.isHunter) {
                hunterDrawable?.let {
                    val size = enemy.radius * 2f
                    val left = (drawX - size).toInt()
                    val top = (drawY - size).toInt()
                    val right = (drawX + size).toInt()
                    val bottom = (drawY + size).toInt()
                    it.setBounds(left, top, right, bottom)
                    it.draw(canvas)
                } ?: run {
                    canvas.drawCircle(drawX, drawY, enemy.radius, paintToUse)
                }
            } else {
                canvas.drawCircle(drawX, drawY, enemy.radius, paintToUse)
            }
        }

        for (arrow in arrows) {
            canvas.drawCircle(arrow.x, arrow.y, arrow.radius, arrowPaint)
        }

        canvas.drawCircle(playerX, playerY, playerRadius, playerPaint)

        dpadButtons.forEach { (direction, rect) ->
            val cx = rect.centerX()
            val cy = rect.centerY()
            val radius = rect.width().coerceAtMost(rect.height()) * 0.5f
            // Arka plan dairesi
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, radius, dpadPaint)
            canvas.drawCircle(0f, 0f, radius, dpadBorderPaint)
            // Ok çizimi
            val size = radius * 0.6f
            val arrowPath = Path()
            when (direction) {
                "UP" -> { arrowPath.moveTo(0f, -size); arrowPath.lineTo(-size, size); arrowPath.lineTo(size, size); arrowPath.close() }
                "DOWN" -> { arrowPath.moveTo(0f, size); arrowPath.lineTo(-size, -size); arrowPath.lineTo(size, -size); arrowPath.close() }
                "LEFT" -> { arrowPath.moveTo(-size, 0f); arrowPath.lineTo(size, -size); arrowPath.lineTo(size, size); arrowPath.close() }
                "RIGHT" -> { arrowPath.moveTo(size, 0f); arrowPath.lineTo(-size, -size); arrowPath.lineTo(-size, size); arrowPath.close() }
            }
            canvas.drawPath(arrowPath, dpadArrowPaint)
            canvas.restore()
        }

        if (isDrawingLine) {
            canvas.drawPath(drawingPath, linePaint)
        }

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

            val buttonPaint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.FILL }
            val buttonTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 60f
                textAlign = Paint.Align.CENTER
            }
            restartButtonRect.set(centerX - 250f, centerY + 50f, centerX + 250f, centerY + 200f)
            canvas.drawRect(restartButtonRect, buttonPaint)
            canvas.drawText("Yeniden Oyna", centerX, centerY + 135f, buttonTextPaint)

            return
        }

        if (gameWon) {
            val centerX = width / 2f
            val buttonTopY = 120f
            val messageY = buttonTopY - 40f // Butonun hemen üstü
            val paint = Paint().apply {
                color = Color.GREEN
                textSize = 54f // Daha küçük boyut
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                setShadowLayer(8f, 0f, 0f, Color.BLACK)
            }
            canvas.drawText("OYUN TAMAMLANDI!", centerX, messageY, paint)
            if (level < maxLevel) {
                showNextLevelButton = true
            } else {
                val buttonPaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL }
                val buttonTextPaint = Paint().apply { color = Color.WHITE; textSize = 60f; textAlign = Paint.Align.CENTER }
                finishRestartButtonRect.set(centerX - 250f, centerY + 60f, centerX + 250f, centerY + 160f)
                canvas.drawRect(finishRestartButtonRect, buttonPaint)
                canvas.drawText("Yeniden Oyna", centerX, centerY + 125f, buttonTextPaint)
            }
        }

        if (showNextLevelButton) {
            val centerX = width / 2f
            val topY = 120f
            nextLevelButtonRect.set(centerX - 250f, topY, centerX + 250f, topY + 100f)
            val buttonPaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL }
            val buttonTextPaint = Paint().apply { color = Color.WHITE; textSize = 60f; textAlign = Paint.Align.CENTER }
            canvas.drawRect(nextLevelButtonRect, buttonPaint)
            canvas.drawText("${level + 1}. seviyeye geç", centerX, topY + 65f, buttonTextPaint)
        }
    }

    private fun updateEnemies() {
        if (revealPercent >= 50f && bossCount < 3) {
            // ileride eklenebilir
        } else if (revealPercent >= 20f && bossCount < 2) {
            // ileride eklenebilir
        }
        for (enemy in enemies) {
            when {
                enemy.isHunter -> updateHunter(enemy)
                enemy.isStalker -> updateStalker(enemy)
                enemy.isMegaHunter -> updateMegaHunter(enemy)
                enemy.isArrowBoss -> updateArrowBoss(enemy)
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
                if (currentEdge == Edge.TOP || currentEdge == Edge.BOTTOM) stalker.vx = playerSpeed * 0.8f
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
            if (playerEdge == Edge.LEFT || playerEdge == Edge.RIGHT) {
                stalker.vx = 0f
                stalker.vy = playerSpeed * 0.8f * Math.signum(playerY - stalker.y)
            } else {
                stalker.vy = 0f
                stalker.vx = playerSpeed * 0.8f * Math.signum(playerX - stalker.x)
            }
        }
    }

    private fun updateHunter(hunter: Enemy) {
        val hunterAggroSpeed = 8.5f * getLevelMultiplier()
        val hunterIdleSpeed = 2.5f

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
        val megaHunterAggroSpeed = 6f * getLevelMultiplier()
        val megaHunterIdleSpeed = 3.5f

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
                megaHunter.directionChangeTimer = Random.nextInt(40, 120)
            }
        }
        megaHunter.x += megaHunter.vx
        megaHunter.y += megaHunter.vy
        if (megaHunter.x + megaHunter.radius < playfield.left) megaHunter.x = playfield.right + megaHunter.radius
        if (megaHunter.x - megaHunter.radius > playfield.right) megaHunter.x = playfield.left - megaHunter.radius
        if (megaHunter.y + megaHunter.radius < playfield.top) megaHunter.y = playfield.bottom + megaHunter.radius
        if (megaHunter.y - megaHunter.radius > playfield.bottom) megaHunter.y = playfield.top - megaHunter.radius
    }

    private fun updateArrowBoss(boss: Enemy) {
        if (boss.isPreparingAttack) {
            boss.prepareAttackTimer--
            if (boss.prepareAttackTimer <= 0) {
                fireArrows(boss.x, boss.y)
                boss.isPreparingAttack = false
                boss.arrowCooldown = Random.nextInt(240, 420)
            }
            return
        }

        val arrowBossIdleSpeed = 1.5f
        boss.directionChangeTimer--
        if (boss.directionChangeTimer <= 0) {
            boss.vx = (Random.nextFloat() - 0.5f) * 2f * arrowBossIdleSpeed
            boss.vy = (Random.nextFloat() - 0.5f) * 2f * arrowBossIdleSpeed
            boss.directionChangeTimer = Random.nextInt(120, 240)
        }

        boss.x += boss.vx
        boss.y += boss.vy

        if (boss.x - boss.radius < playfield.left || boss.x + boss.radius > playfield.right) {
            boss.vx *= -1
            boss.x = boss.x.coerceIn(playfield.left + boss.radius, playfield.right - boss.radius)
        }
        if (boss.y - boss.radius < playfield.top || boss.y + boss.radius > playfield.bottom) {
            boss.vy *= -1
            boss.y = boss.y.coerceIn(playfield.top + boss.radius, playfield.bottom - boss.radius)
        }

        boss.arrowCooldown--
        if (boss.arrowCooldown <= 0) {
            boss.isPreparingAttack = true
            boss.prepareAttackTimer = 120
        }
    }

    private fun fireArrows(startX: Float, startY: Float) {
        val arrowSpeed = 6f
        val numberOfArrows = 8
        for (i in 0 until numberOfArrows) {
            val angle = (i.toFloat() / numberOfArrows) * 2 * PI.toFloat()
            val vx = cos(angle) * arrowSpeed
            val vy = sin(angle) * arrowSpeed
            arrows.add(Arrow(startX, startY, vx, vy))
        }
    }

    private fun updateArrows() {
        val iterator = arrows.iterator()
        while (iterator.hasNext()) {
            val arrow = iterator.next()
            arrow.x += arrow.vx
            arrow.y += arrow.vy

            if (!playfield.contains(arrow.x, arrow.y)) {
                iterator.remove()
            }
        }
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
        if (running.not()) return

        val arrowIterator = arrows.iterator()
        while (arrowIterator.hasNext()) {
            val arrow = arrowIterator.next()
            if (isDrawingLine && currentLine.size > 1) {
                for (i in 0 until currentLine.size - 1) {
                    val p1 = currentLine[i]
                    val p2 = currentLine[i + 1]
                    val (distanceSquared, _) = distanceToSegmentSquared(p1, p2, PointF(arrow.x, arrow.y), returnPoint = false)
                    if (distanceSquared < (arrow.radius + linePaint.strokeWidth).let { it * it }) {
                        handlePlayerHit()
                        arrowIterator.remove()
                        return
                    }
                }
            }
        }

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
        } else {
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
        drawingPath.reset()
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

    private fun moveEnemiesOffScreen() {
        for (enemy in enemies) {
            enemy.x = -1000f
            enemy.y = -1000f
        }
    }

    private fun getLevelMultiplier(): Float {
        return when (level) {
            1 -> 0.8f
            2 -> 1.0f
            3 -> 1.2f
            else -> 1.0f
        }
    }

    private fun goToNextLevel() {
        if (level < maxLevel) {
            level++
            setupNewGame()
            gameStateListener?.onLevelChanged(level)
            invalidate()
        }
    }

    /** Kazanıldığında döngüyü başlat. Ayarlar decode anında yapılmış durumda. */
    private fun startBackgroundAnimationLooping() {
        backgroundDrawable?.let { d ->
            try {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && d is android.graphics.drawable.AnimatedImageDrawable -> d.start()
                    d is AnimationDrawable -> d.start()
                    d is Animatable2Compat -> d.start()
                    d is Animatable -> d.start()
                }
            } catch (_: Throwable) { }
        }
    }

    /**
     * İki nokta arasındaki çizgi segmentine bir noktanın olan en kısa mesafesini hesaplar.
     * @param p1 Çizgi segmentinin başlangıç noktası.
     * @param p2 Çizgi segmentinin bitiş noktası.
     * @param p Kontrol edilecek nokta.
     * @param returnPoint En yakın noktayı döndürmek için true.
     * @return Mesafenin karesi ve isteğe bağlı olarak en yakın nokta.
     */
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
}
