package com.fortexer.zup

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * ZUP – offline endless arcade game drawn entirely with the Android Canvas.
 * World sizes use "u" = 1% of screen width. Menus use "ui" (same, but limited on short screens).
 */
class GameView(context: Context) : View(context) {

    private enum class Screen { MENU, PLAYING, PAUSED, OVER, SHOP, SETTINGS, GUIDE, RECORDS }

    companion object {
        const val B_PLAY = 1
        const val B_SHOP = 2
        const val B_SETTINGS = 3
        const val B_BACK = 4
        const val B_PAUSE = 5
        const val B_RESUME = 6
        const val B_RESTART = 7
        const val B_HOME = 8
        const val B_RETRY = 9
        const val B_GUIDE = 10
        const val B_RECORDS = 11
        const val B_EXIT = 13
        const val B_TAB_SKINS = 20
        const val B_TAB_TRAILS = 21
        const val B_SOUND = 30
        const val B_MUSIC = 31
        const val B_VIB = 32
        const val B_FX = 33
        const val B_RESET = 34
        const val B_RESET_SET = 35
        const val B_MYKET_COMMENT = 50
        const val B_CANCEL = 40
        const val B_ERASE = 41
        const val B_SKIN = 1000
        const val B_TRAIL = 2000
    }

    /** A falling barrier row made of 1-3 solid segments. */
    private class Row(var y: Float, val color: Int) {
        val segs = FloatArray(6)
        var n = 0
        var passed = false
        var moving = false
        var baseX = 0f
        var gapW = 0f
        var amp = 0f
        var ph = 0f
    }

    private class Crystal(var x: Float, var y: Float) {
        var phase = Random.nextFloat() * 6.2832f
    }

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float
    )

    private class Trail(val x: Float, val y: Float, var age: Float)
    private class Ring(val x: Float, val y: Float, val color: Int) { var age = 0f }
    private class FloatText(val x: Float, val y: Float, val text: String) { var age = 0f }

    // ---- palette ----
    private val cyan = 0xFF00F0FF.toInt()
    private val magenta = 0xFFFF2E93.toInt()
    private val violet = 0xFFB14DFF.toInt()
    private val blue = 0xFF4D7CFF.toInt()
    private val pink = 0xFFFF6BD6.toInt()
    private val gold = 0xFFFFE94D.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val softViolet = 0xFFD9B8FF.toInt()
    private val bgTop = 0xFF16043A.toInt()
    private val bgBottom = 0xFF03030C.toInt()
    private val cardColor = 0xFF130E36.toInt()
    private val overlayColor = 0xFF02020A.toInt()

    // ---- services ----
    private val store = Store(context)
    private val audio = Audio()
    private val vib: Vibrator? = try {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } catch (e: Throwable) {
        null
    }

    @Suppress("DEPRECATION")
    private val versionName: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (e: Throwable) {
        "1.0"
    }

    // ---- game state ----
    private var screen = Screen.MENU
    private var shopFrom = Screen.MENU
    private var w = 0f
    private var h = 0f
    private var u = 1f
    private var ui = 1f
    private var ballR = 0f
    private var ballY = 0f
    private var rowH = 0f
    private var crystalR = 0f
    private var ballX = 0f
    private var dir = 1
    private var score = 0
    private var crystalCount = 0
    private var runCoins = 0
    private var bonusCoins = 0
    private var recordCoins = 0
    private var newBest = false
    private var rowCounter = 0
    private var spawnTimer = 0f
    private val lastCenters = FloatArray(2)
    private var lastCount = 1
    private var lastWasDouble = false
    private var scroll = 0f
    private var playTime = 0f
    private var shake = 0f
    private var flipPulse = 0f
    private var countdown = 0f
    private var clock = 0f
    private var screenTime = 0f
    private var lastNanos = 0L
    private var frameDt = 0f
    private var ga = 1f

    // ---- ui state ----
    private var shopTab = 0
    private var scrollY = 0f
    private var maxScroll = 0f
    private var listTopPx = 0f
    private var confirmReset = false
    private var confirmTime = 0f
    private var toastText = ""
    private var toastTime = 0f
    private val swAnim = FloatArray(3)
    private val btnId = IntArray(80)
    private val btnR = FloatArray(320)
    private var btnCount = 0
    private var pressedId = 0
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    private val rows = ArrayList<Row>()
    private val crystals = ArrayList<Crystal>()
    private val particles = ArrayList<Particle>()
    private val trail = ArrayList<Trail>()
    private val rings = ArrayList<Ring>()
    private val texts = ArrayList<FloatText>()

    // ---- reusable drawing objects ----
    private val rect = RectF()
    private val path = Path()
    private val bgPaint = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boldFace: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val normalFace: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

    // Persian faces: an optional Vazirmatn drop-in is preferred, otherwise the bundled DejaVu Sans
    // (full Persian letters + joining); last resort is the system default.
    private val faRegular: Typeface = loadFont(arrayOf("fonts/Vazirmatn-Regular.ttf", "fonts/DejaVuSans.ttf"), false)
    private val faBold: Typeface = loadFont(arrayOf("fonts/Vazirmatn-Bold.ttf", "fonts/DejaVuSans-Bold.ttf"), true)

    private fun loadFont(candidates: Array<String>, bold: Boolean): Typeface {
        for (path in candidates) {
            try {
                return Typeface.createFromAsset(context.assets, path)
            } catch (e: Throwable) {
            }
        }
        return Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Called when the player taps "exit" in the main menu. */
    var onExit: (() -> Unit)? = null

    init {
        audio.soundOn = store.sound
        audio.musicOn = store.music
        audio.setMusic(store.music)
        swAnim[0] = if (store.sound) 1f else 0f
        swAnim[1] = if (store.music) 1f else 0f
        swAnim[2] = if (store.vibration) 1f else 0f
    }

    // =====================================================================
    // lifecycle
    // =====================================================================

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        super.onSizeChanged(nw, nh, ow, oh)
        w = nw.toFloat()
        h = nh.toFloat()
        u = w / 100f
        ui = min(u, h / 170f)
        ballR = 3.4f * u
        ballY = h * 0.78f
        rowH = 6.5f * u
        crystalR = 2.8f * u
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, bgTop, bgBottom, Shader.TileMode.CLAMP)
        resetWorld()
    }

    fun onHostPause() {
        audio.pause()
        if (screen == Screen.PLAYING) gotoScreen(Screen.PAUSED)
    }

    fun onHostResume() {
        audio.resume()
        lastNanos = 0L
    }

    fun release() {
        audio.release()
    }

    /** Returns true when the back press was consumed. */
    fun handleBack(): Boolean {
        if (confirmReset) {
            confirmReset = false
            return true
        }
        return when (screen) {
            Screen.PLAYING -> { pauseGame(); true }
            Screen.PAUSED -> { resumeGame(); true }
            Screen.OVER -> { goHome(); true }
            Screen.SHOP -> { gotoScreen(if (shopFrom == Screen.OVER) Screen.OVER else Screen.MENU); true }
            Screen.SETTINGS, Screen.GUIDE, Screen.RECORDS -> { gotoScreen(Screen.MENU); true }
            Screen.MENU -> false
        }
    }

    private fun gotoScreen(s: Screen) {
        screen = s
        screenTime = 0f
        pressedId = 0
        dragging = false
        if (s == Screen.SHOP) scrollY = 0f
    }

    private fun resetWorld() {
        rows.clear()
        crystals.clear()
        particles.clear()
        trail.clear()
        rings.clear()
        texts.clear()
        ballX = w / 2f
        dir = 1
        lastCenters[0] = w / 2f
        lastCount = 1
        lastWasDouble = false
        rowCounter = 0
        spawnTimer = 0.4f
    }

    private fun startGame() {
        resetWorld()
        score = 0
        crystalCount = 0
        runCoins = 0
        bonusCoins = 0
        recordCoins = 0
        newBest = false
        playTime = 0f
        shake = 0f
        countdown = 0f
        spawnTimer = 0.9f
        gotoScreen(Screen.PLAYING)
        audio.play("start")
    }

    private fun goHome() {
        score = 0
        crystalCount = 0
        runCoins = 0
        resetWorld()
        gotoScreen(Screen.MENU)
    }

    private fun pauseGame() {
        if (screen == Screen.PLAYING) gotoScreen(Screen.PAUSED)
    }

    private fun resumeGame() {
        gotoScreen(Screen.PLAYING)
        countdown = 1.5f
    }

    private fun gameOver() {
        if (screen != Screen.PLAYING) return
        val prevBest = store.best
        newBest = score > prevBest
        bonusCoins = score / 20
        recordCoins = if (newBest && score >= 10) 25 else 0
        if (newBest) store.setBest(score)
        store.recordRun(crystalCount)
        store.addCoins(bonusCoins + recordCoins)
        gotoScreen(Screen.OVER)
        shake = 0.45f
        burst(ballX, ballY, store.let { Cosmetics.glowColor(it.skin) }, if (store.fxHigh) 26 else 12, 1.4f)
        burst(ballX, ballY, white, if (store.fxHigh) 10 else 4, 1f)
        rings.add(Ring(ballX, ballY, white))
        audio.play(if (newBest && score >= 5) "record" else "over")
        buzz(70)
    }

    @Suppress("DEPRECATION")
    private fun buzz(ms: Long) {
        if (!store.vibration) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vib?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vib?.vibrate(ms)
            }
        } catch (e: Throwable) {
        }
    }

    private fun showToast(msg: String) {
        toastText = msg
        toastTime = 1.6f
    }

    // =====================================================================
    // input
    // =====================================================================

    private fun registerBtn(id: Int, l: Float, t: Float, r: Float, b: Float) {
        if (btnCount >= 80) return
        btnId[btnCount] = id
        val o = btnCount * 4
        btnR[o] = l
        btnR[o + 1] = t
        btnR[o + 2] = r
        btnR[o + 3] = b
        btnCount++
    }

    private fun hitTest(x: Float, y: Float): Int {
        val pad = 0.8f * ui
        var i = btnCount - 1
        while (i >= 0) {
            val o = i * 4
            val id = btnId[i]
            val inside = x >= btnR[o] - pad && x <= btnR[o + 2] + pad && y >= btnR[o + 1] - pad && y <= btnR[o + 3] + pad
            val visible = !(id >= B_SKIN && y < listTopPx)
            if (inside && visible) return id
            i--
        }
        return 0
    }

    private fun flipBall() {
        dir = -dir
        flipPulse = 0.18f
        burst(ballX, ballY, Cosmetics.glowColor(store.skin), if (store.fxHigh) 6 else 2, 0.5f)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.y
                lastY = e.y
                dragging = false
                if (screen == Screen.PLAYING) {
                    if (countdown <= 0f) {
                        if (hitTest(e.x, e.y) == B_PAUSE) {
                            audio.play("click")
                            pauseGame()
                        } else {
                            flipBall()
                        }
                    }
                    pressedId = 0
                } else {
                    pressedId = hitTest(e.x, e.y)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (screen == Screen.PLAYING && countdown <= 0f) flipBall()
            }
            MotionEvent.ACTION_MOVE -> {
                if (screen == Screen.SHOP) {
                    if (!dragging && abs(e.y - downY) > 1.8f * u) {
                        dragging = true
                        pressedId = 0
                    }
                    if (dragging) {
                        scrollY = (scrollY - (e.y - lastY)).coerceIn(0f, maxScroll)
                    }
                    lastY = e.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging && pressedId != 0 && screenTime > 0.25f && hitTest(e.x, e.y) == pressedId) {
                    onButton(pressedId)
                }
                pressedId = 0
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedId = 0
                dragging = false
            }
        }
        return true
    }

    private fun onButton(id: Int) {
        if (id < B_SKIN) audio.play("click")
        when {
            id >= B_TRAIL -> trailAction(id - B_TRAIL)
            id >= B_SKIN -> skinAction(id - B_SKIN)
            else -> when (id) {
                B_PLAY, B_RESTART, B_RETRY -> startGame()
                B_SHOP -> {
                    shopFrom = screen
                    gotoScreen(Screen.SHOP)
                }
                B_SETTINGS -> gotoScreen(Screen.SETTINGS)
                B_GUIDE -> gotoScreen(Screen.GUIDE)
                B_RECORDS -> gotoScreen(Screen.RECORDS)
                B_EXIT -> onExit?.invoke()
                B_MYKET_COMMENT -> myketResult(Myket.openComment(context))
                B_RESET_SET -> {
                    store.resetSettings()
                    audio.soundOn = store.sound
                    audio.setMusic(store.music)
                    showToast(Fa.SETTINGS_RESET)
                }
                B_BACK -> handleBack()
                B_PAUSE -> pauseGame()
                B_RESUME -> resumeGame()
                B_HOME -> goHome()
                B_TAB_SKINS -> { shopTab = 0; scrollY = 0f }
                B_TAB_TRAILS -> { shopTab = 1; scrollY = 0f }
                B_SOUND -> {
                    store.setSound(!store.sound)
                    audio.soundOn = store.sound
                    if (store.sound) audio.play("click")
                }
                B_MUSIC -> {
                    store.setMusic(!store.music)
                    audio.setMusic(store.music)
                }
                B_VIB -> {
                    store.setVibration(!store.vibration)
                    buzz(30)
                }
                B_FX -> store.setFx(!store.fxHigh)
                B_RESET -> {
                    confirmReset = true
                    confirmTime = 0f
                }
                B_CANCEL -> confirmReset = false
                B_ERASE -> {
                    store.resetAll()
                    audio.soundOn = store.sound
                    audio.setMusic(store.music)
                    swAnim[0] = 1f
                    swAnim[1] = 1f
                    swAnim[2] = 1f
                    confirmReset = false
                    showToast(Fa.ERASED)
                }
            }
        }
    }

    private fun myketResult(r: Myket.Result) {
        when (r) {
            Myket.Result.OK -> {}
            Myket.Result.NOT_INSTALLED -> { audio.play("error"); showToast(Fa.MYKET_MISSING) }
            Myket.Result.FAILED -> { audio.play("error"); showToast(Fa.MYKET_FAILED) }
        }
    }

    private fun skinAction(i: Int) {
        if (!store.ownsSkin(i)) {
            if (store.buySkin(i)) {
                audio.play("buy")
                buzz(25)
                showToast("«${Cosmetics.skinNames[i]}» خریداری شد")
            } else {
                audio.play("error")
                showToast(Fa.NOT_ENOUGH)
            }
        } else if (store.skin == i) {
            if (i != 0) store.equipSkin(0)
            audio.play("click")
        } else {
            store.equipSkin(i)
            audio.play("click")
        }
    }

    private fun trailAction(i: Int) {
        if (!store.ownsTrail(i)) {
            if (store.buyTrail(i)) {
                audio.play("buy")
                buzz(25)
                showToast("«${Cosmetics.trailNames[i]}» خریداری شد")
            } else {
                audio.play("error")
                showToast(Fa.NOT_ENOUGH)
            }
        } else if (store.trail == i) {
            if (i != 0) store.equipTrail(0)
            audio.play("click")
        } else {
            store.equipTrail(i)
            audio.play("click")
        }
    }

    // =====================================================================
    // simulation
    // =====================================================================

    private fun update(dt: Float) {
        clock += dt
        screenTime += dt
        if (confirmReset) confirmTime += dt
        if (toastTime > 0f) toastTime -= dt
        tickEffects(dt)
        if (screen == Screen.PLAYING && countdown > 0f) {
            countdown -= dt
            return
        }
        if (screen != Screen.PLAYING && screen != Screen.MENU) return
        simulate(dt)
    }

    private fun tickEffects(dt: Float) {
        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.life -= dt
            if (p.life <= 0f) {
                pit.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            val drag = max(0f, 1f - 1.6f * dt)
            p.vx *= drag
            p.vy *= drag
        }
        for (r in rings) r.age += dt
        rings.removeAll { it.age > 0.45f }
        for (t in texts) t.age += dt
        texts.removeAll { it.age > 0.8f }
        shake = max(0f, shake - dt)
        flipPulse = max(0f, flipPulse - dt)
    }

    private fun simulate(dt: Float) {
        val playing = screen == Screen.PLAYING
        val f = if (playing) min(2.3f, 1f + score * 0.011f) else 0.8f
        val fall = 36f * u * f
        val hs = 80f * u * f
        val spacingT = max(1.15f, 1.4f - score * 0.004f)

        scroll = (scroll + fall * 0.6f * dt) % (12f * u)

        ballX += dir * hs * dt
        if (ballX < ballR) {
            ballX = ballR
            dir = 1
        }
        if (ballX > w - ballR) {
            ballX = w - ballR
            dir = -1
        }

        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnRow(fall * spacingT)
            spawnTimer += spacingT
        }
        if (playing) playTime += dt

        for (r in rows) {
            r.y += fall * dt
            if (r.moving) {
                val gx = (r.baseX + r.amp * sin(r.ph + clock * 1.6f)).coerceIn(0f, w - r.gapW)
                setSingle(r, gx, r.gapW)
            }
        }
        for (c in crystals) {
            c.y += fall * dt
            c.phase += dt * 4f
        }
        trail.add(Trail(ballX, ballY, 0f))
        for (t in trail) t.age += dt
        trail.removeAll { it.age > 0.5f }
        rows.removeAll { it.y > h + rowH }
        crystals.removeAll { it.y > h + crystalR * 3f }

        if (playing) {
            for (r in rows) {
                if (!r.passed && r.y > ballY + ballR) {
                    r.passed = true
                    score++
                }
                if (r.y + rowH > ballY - ballR && r.y < ballY + ballR) {
                    var crashed = false
                    for (i in 0 until r.n) {
                        if (hit(r.segs[2 * i], r.y, r.segs[2 * i + 1], r.y + rowH)) crashed = true
                    }
                    if (crashed) {
                        gameOver()
                        break
                    }
                }
            }
        }

        if (screen == Screen.PLAYING) {
            val iter = crystals.iterator()
            val reach = ballR + crystalR + 0.8f * u
            while (iter.hasNext()) {
                val c = iter.next()
                val dx = c.x - ballX
                val dy = c.y - ballY
                if (dx * dx + dy * dy < reach * reach) {
                    iter.remove()
                    score += 5
                    crystalCount++
                    runCoins += 3
                    store.addCoins(3)
                    burst(c.x, c.y, gold, if (store.fxHigh) 14 else 6, 1f)
                    rings.add(Ring(c.x, c.y, gold))
                    texts.add(FloatText(c.x, c.y - ballR, "+3"))
                    audio.play("crystal")
                    buzz(10)
                }
            }
        }
    }

    private fun setSingle(r: Row, gapX: Float, gapW: Float) {
        r.n = 2
        r.segs[0] = 0f
        r.segs[1] = gapX
        r.segs[2] = gapX + gapW
        r.segs[3] = w
    }

    private fun spawnRow(spacingDist: Float) {
        var gapW = max(30f * u, 42f * u - score * 0.25f * u)
        val moving = score >= 20 && !lastWasDouble && Random.nextFloat() < 0.28f
        val wantDouble = !moving && score >= 8 && !lastWasDouble && Random.nextFloat() < 0.22f
        val crystalX: Float
        val row: Row

        val dbl = if (wantDouble) buildDouble(gapW) else null
        if (dbl != null) {
            row = dbl
            val c1 = dbl.segs[1] + (dbl.segs[2] - dbl.segs[1]) / 2f
            val c2 = dbl.segs[3] + (dbl.segs[4] - dbl.segs[3]) / 2f
            lastCenters[0] = c1
            lastCenters[1] = c2
            lastCount = 2
            lastWasDouble = true
            crystalX = if (Random.nextBoolean()) c1 else c2
        } else {
            val amp = if (moving) 6f * u else 0f
            if (moving) gapW = max(gapW, 36f * u)
            val lo = if (lastCount == 2) min(lastCenters[0], lastCenters[1]) else lastCenters[0]
            val hi = if (lastCount == 2) max(lastCenters[0], lastCenters[1]) else lastCenters[0]
            val sh = if (moving) w * 0.44f else w * 0.5f
            val minC = max(gapW / 2f + amp, hi - sh)
            val maxC = min(w - gapW / 2f - amp, lo + sh)
            val center = if (maxC <= minC) (minC + maxC) / 2f else minC + Random.nextFloat() * (maxC - minC)
            row = Row(-rowH, if (moving) pink else if (rowCounter % 2 == 0) magenta else violet)
            row.gapW = gapW
            row.baseX = center - gapW / 2f
            row.amp = amp
            row.ph = Random.nextFloat() * 6.2832f
            row.moving = moving
            setSingle(row, row.baseX, gapW)
            lastCenters[0] = center
            lastCount = 1
            lastWasDouble = false
            crystalX = center + (Random.nextFloat() - 0.5f) * gapW * 0.4f
        }
        rowCounter++
        rows.add(row)

        if (Random.nextFloat() < 0.55f) crystals.add(Crystal(crystalX, -rowH * 0.5f))

        if (screen == Screen.PLAYING) {
            if (score >= 6 && Random.nextFloat() < 0.2f) {
                // crystal stream: follow it with the ball to grab all of them
                val s = 5.5f * u
                val d = (80f / 36f) * s
                val sign = if (Random.nextBoolean()) 1 else -1
                val span = 3f * d
                val margin = 4f * u
                val start = margin + Random.nextFloat() * (w - 2f * margin - span)
                val x0 = if (sign == 1) start else start + span
                val y0 = -rowH - spacingDist * 0.5f + s * 1.5f
                for (i in 0 until 4) {
                    crystals.add(Crystal(x0 + sign * i * d, y0 - i * s))
                }
            } else if (Random.nextFloat() < 0.3f) {
                val x = 2f * ballR + Random.nextFloat() * (w - 4f * ballR)
                crystals.add(Crystal(x, -rowH - spacingDist * 0.5f))
            }
        }
    }

    private fun buildDouble(baseGapW: Float): Row? {
        val gw = max(27f * u, baseGapW * 0.85f)
        val free = w - 2f * gw
        val p = lastCenters[0]
        for (attempt in 0 until 6) {
            val a = 4f * u + Random.nextFloat() * 16f * u
            val m = 10f * u + Random.nextFloat() * 12f * u
            val r = free - a - m
            if (r < 4f * u) continue
            val c1 = a + gw / 2f
            val c2 = a + gw + m + gw / 2f
            if (min(abs(c1 - p), abs(c2 - p)) > 40f * u) continue
            val row = Row(-rowH, blue)
            row.n = 3
            row.segs[0] = 0f
            row.segs[1] = a
            row.segs[2] = a + gw
            row.segs[3] = a + gw + m
            row.segs[4] = a + 2f * gw + m
            row.segs[5] = w
            return row
        }
        return null
    }

    private fun hit(l: Float, t: Float, r: Float, b: Float): Boolean {
        if (r <= l) return false
        val cx = ballX.coerceIn(l, r)
        val cy = ballY.coerceIn(t, b)
        val dx = ballX - cx
        val dy = ballY - cy
        val rr = ballR * 0.88f
        return dx * dx + dy * dy < rr * rr
    }

    private fun burst(x: Float, y: Float, color: Int, n: Int, power: Float) {
        repeat(n) {
            val a = Random.nextFloat() * 6.2832f
            val s = (10f + Random.nextFloat() * 40f) * u * power
            val life = 0.4f + Random.nextFloat() * 0.4f
            particles.add(
                Particle(x, y, cos(a) * s, sin(a) * s, life, life, color, (0.5f + Random.nextFloat() * 0.9f) * u)
            )
        }
    }

    // =====================================================================
    // rendering helpers
    // =====================================================================

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min(0.033f, (now - lastNanos) / 1_000_000_000f)
        lastNanos = now
        frameDt = dt
        if (w > 0f) {
            update(dt)
            render(canvas)
        }
        postInvalidateOnAnimation()
    }

    private fun withAlpha(color: Int, a: Float): Int {
        val ia = ((a * ga).coerceIn(0f, 1f) * 255f).toInt()
        return (color and 0x00FFFFFF) or (ia shl 24)
    }

    private fun ease(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t) * (1f - t)
    }

    private fun back(x: Float): Float {
        val t = x.coerceIn(0f, 1f) - 1f
        return 1f + 2.70158f * t * t * t + 1.70158f * t * t
    }

    private fun stag(i: Int): Float = ease((screenTime - i * 0.07f) / 0.4f)

    /**
     * Prepares [textPaint] (font, size, spacing) for [s] and returns the string to draw:
     * ASCII digits become Persian digits; Persian text gets the Persian font, no letter spacing
     * (spacing would break the letter joining) and a leading RLM so the base direction is RTL.
     */
    private fun prep(s: String, size: Float, bold: Boolean, spacing: Float): String {
        var t = Fa.d(s)
        val arabic = Fa.hasArabic(t)
        if (Fa.hasLetters(t)) t = "\u200F" + t
        textPaint.textSize = size
        textPaint.typeface = if (arabic) (if (bold) faBold else faRegular) else (if (bold) boldFace else normalFace)
        textPaint.letterSpacing = if (arabic) 0f else spacing
        return t
    }

    private fun label(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        bold: Boolean = true, align: Paint.Align = Paint.Align.CENTER, spacing: Float = 0f, maxW: Float = 0f
    ) {
        val t = prep(s, size, bold, spacing)
        if (maxW > 0f) {
            val mw = textPaint.measureText(t)
            if (mw > maxW) textPaint.textSize = size * maxW / mw
        }
        textPaint.color = color
        textPaint.textAlign = align
        c.drawText(t, x, y, textPaint)
    }

    /** Greedy word wrap for right-to-left paragraphs. */
    private fun wrapLines(s: String, size: Float, maxW: Float, bold: Boolean = false): List<String> {
        prep(s, size, bold, 0f)
        val lines = ArrayList<String>()
        var cur = ""
        for (word in Fa.d(s).split(' ')) {
            val trial = if (cur.isEmpty()) word else "$cur $word"
            if (cur.isNotEmpty() && textPaint.measureText("\u200F" + trial) > maxW) {
                lines.add(cur)
                cur = word
            } else {
                cur = trial
            }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        return lines
    }

    private fun drawCard(c: Canvas, l: Float, t: Float, r: Float, b: Float, accent: Int, accentA: Float) {
        rect.set(l, t, r, b)
        val rad = 4f * ui
        fill.color = withAlpha(cardColor, 0.94f)
        c.drawRoundRect(rect, rad, rad, fill)
        stroke.strokeWidth = 0.5f * ui
        stroke.color = withAlpha(accent, accentA)
        c.drawRoundRect(rect, rad, rad, stroke)
    }

    /** style 0 = primary (cyan), 1 = secondary, 2 = danger. Persian text puts the icon on the right. */
    private fun uiButton(
        c: Canvas, id: Int, l: Float, t: Float, r: Float, b: Float,
        style: Int, text: String?, icon: Int, enabled: Boolean = true
    ) {
        if (enabled) registerBtn(id, l, t, r, b)
        val pressed = pressedId == id && enabled
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        val dim = if (enabled) 1f else 0.5f
        c.save()
        if (pressed) c.scale(0.96f, 0.96f, cx, cy)
        val rad = (b - t) * 0.32f
        rect.set(l, t, r, b)
        val base = when (style) {
            0 -> cyan
            2 -> magenta
            else -> violet
        }
        if (style != 1 && enabled) {
            stroke.strokeWidth = 2.4f * ui
            stroke.color = withAlpha(base, 0.10f)
            c.drawRoundRect(rect, rad, rad, stroke)
        }
        fill.color = when (style) {
            0 -> withAlpha(base, (if (pressed) 0.40f else 0.26f) * dim)
            2 -> withAlpha(base, (if (pressed) 0.34f else 0.20f) * dim)
            else -> withAlpha(white, (if (pressed) 0.14f else 0.07f) * dim)
        }
        c.drawRoundRect(rect, rad, rad, fill)
        stroke.strokeWidth = 0.5f * ui
        stroke.color = withAlpha(base, (if (style == 1) 0.55f else 0.95f) * dim)
        c.drawRoundRect(rect, rad, rad, stroke)

        var ts = (b - t) * 0.36f
        var isz = (b - t) * 0.46f
        val col = withAlpha(white, dim)
        if (text != null) {
            var str = prep(text, ts, true, 0.05f)
            var tw = textPaint.measureText(str)
            var gap = ts * 0.5f
            var total = if (icon >= 0) isz + gap + tw else tw
            val avail = (r - l) * 0.9f
            if (total > avail) {
                val k = avail / total
                ts *= k
                isz *= k
                str = prep(text, ts, true, 0.05f)
                tw = textPaint.measureText(str)
                gap = ts * 0.5f
                total = if (icon >= 0) isz + gap + tw else tw
            }
            val rtl = Fa.hasArabic(str)
            var x = cx - total / 2f
            if (icon >= 0 && !rtl) {
                Icons.draw(c, icon, x + isz / 2f, cy, isz, col)
                x += isz + gap
            }
            textPaint.color = col
            textPaint.textAlign = Paint.Align.LEFT
            c.drawText(str, x, cy + ts * 0.35f, textPaint)
            if (icon >= 0 && rtl) {
                Icons.draw(c, icon, x + tw + gap + isz / 2f, cy, isz, col)
            }
        } else if (icon >= 0) {
            if (icon == Icons.BACK) {
                // RTL layout: the "back" arrow points to the right
                c.save()
                c.scale(-1f, 1f, cx, cy)
                Icons.draw(c, icon, cx, cy, isz * 1.15f, col)
                c.restore()
            } else {
                Icons.draw(c, icon, cx, cy, isz * 1.15f, col)
            }
        }
        c.restore()
    }

    /** Coin chip anchored by its right edge, or by its left edge when [leftX] >= 0. */
    private fun drawCoinChip(c: Canvas, rightX: Float, top: Float, hgt: Float, value: Int, prefix: String, leftX: Float = -1f) {
        val ts = hgt * 0.55f
        val txt = prep(prefix + value, ts, true, 0f)
        val tw = textPaint.measureText(txt)
        val pad = hgt * 0.35f
        val isz = hgt * 0.62f
        val chipW = pad + isz + pad * 0.6f + tw + pad
        val l = if (leftX >= 0f) leftX else rightX - chipW
        val r = l + chipW
        rect.set(l, top, r, top + hgt)
        fill.color = withAlpha(white, 0.07f)
        c.drawRoundRect(rect, hgt / 2f, hgt / 2f, fill)
        stroke.strokeWidth = 0.4f * ui
        stroke.color = withAlpha(gold, 0.5f)
        c.drawRoundRect(rect, hgt / 2f, hgt / 2f, stroke)
        Icons.draw(c, Icons.COIN, l + pad + isz / 2f, top + hgt / 2f, isz, withAlpha(gold, 1f), true)
        label(c, txt, l + pad + isz + pad * 0.6f, top + hgt / 2f + ts * 0.36f, ts, withAlpha(white, 1f), align = Paint.Align.LEFT)
    }

    private fun topY(): Float = max(5f * ui, h * 0.03f)

    // =====================================================================
    // rendering
    // =====================================================================

    private fun render(c: Canvas) {
        c.drawRect(0f, 0f, w, h, bgPaint)
        drawGrid(c)
        btnCount = 0
        ga = 1f
        if (screen == Screen.MENU || screen == Screen.PLAYING || screen == Screen.PAUSED || screen == Screen.OVER) drawWorld(c)

        when (screen) {
            Screen.MENU -> drawMenu(c)
            Screen.PLAYING -> {
                drawHud(c)
                if (countdown > 0f) drawCountdown(c)
            }
            Screen.PAUSED -> {
                drawHud(c)
                drawPause(c)
            }
            Screen.OVER -> drawGameOver(c)
            Screen.SHOP -> drawShop(c)
            Screen.SETTINGS -> drawSettings(c)
            Screen.GUIDE -> drawGuide(c)
            Screen.RECORDS -> drawRecords(c)
        }
        ga = 1f
        drawToast(c)
    }

    private fun drawGrid(c: Canvas) {
        stroke.strokeWidth = 0.25f * u
        stroke.color = withAlpha(cyan, 0.07f)
        val step = 12f * u
        var x = 0f
        while (x <= w) {
            c.drawLine(x, 0f, x, h, stroke)
            x += step
        }
        var y = scroll - step
        while (y <= h) {
            c.drawLine(0f, y, w, y, stroke)
            y += step
        }
    }

    private fun drawWorld(c: Canvas) {
        val fx = store.fxHigh
        c.save()
        if (shake > 0f) {
            val amp = shake * 6f * u
            c.translate((Random.nextFloat() - 0.5f) * 2f * amp, (Random.nextFloat() - 0.5f) * 2f * amp)
        }
        val a = if (screen == Screen.MENU) 0.45f else 1f
        for (r in rows) {
            for (i in 0 until r.n) {
                var l = r.segs[2 * i]
                var rr = r.segs[2 * i + 1]
                if (rr - l <= 0.5f * u) continue
                if (l <= 0.01f) l -= 4f * u
                if (rr >= w - 0.01f) rr += 4f * u
                drawBlock(c, l, r.y, rr, r.y + rowH, r.color, a, fx)
            }
        }
        for (cr in crystals) drawCrystal(c, cr.x, cr.y, cr.phase, a, fx)

        for (rg in rings) {
            val k = rg.age / 0.45f
            stroke.strokeWidth = (0.2f + 0.6f * (1f - k)) * u
            stroke.color = withAlpha(rg.color, 1f - k)
            c.drawCircle(rg.x, rg.y, ballR * (0.8f + 4.5f * k), stroke)
        }
        for (p in particles) {
            val k = p.life / p.maxLife
            fill.color = withAlpha(p.color, k)
            c.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * k), fill)
        }

        if (screen != Screen.OVER) {
            val tr = store.trail
            val life = Cosmetics.trailLife(tr)
            var prevX = 0f
            var prevY = 0f
            var has = false
            for (i in trail.indices) {
                val t = trail[i]
                if (t.age < life) {
                    Cosmetics.drawTrail(c, tr, store.skin, t.x, t.y, prevX, prevY, has, t.age, i, ballR, clock, fx)
                }
                prevX = t.x
                prevY = t.y
                has = true
            }
            val br = ballR * (1f + (flipPulse / 0.18f) * 0.25f)
            Cosmetics.drawBall(c, store.skin, ballX, ballY, br, clock, fx)
        }

        for (t in texts) {
            val k = t.age / 0.8f
            label(c, t.text, t.x, t.y - k * 8f * u, 4.2f * u, withAlpha(gold, 1f - k))
        }
        c.restore()
    }

    private fun drawBlock(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, a: Float, fx: Boolean) {
        rect.set(l, t, r, b)
        val rad = rowH * 0.45f
        if (fx) {
            stroke.strokeWidth = 2.6f * u
            stroke.color = withAlpha(color, 0.10f * a)
            c.drawRoundRect(rect, rad, rad, stroke)
            stroke.strokeWidth = 1.3f * u
            stroke.color = withAlpha(color, 0.20f * a)
            c.drawRoundRect(rect, rad, rad, stroke)
        }
        fill.color = withAlpha(color, 0.25f * a)
        c.drawRoundRect(rect, rad, rad, fill)
        stroke.strokeWidth = 0.55f * u
        stroke.color = withAlpha(color, 0.95f * a)
        c.drawRoundRect(rect, rad, rad, stroke)
    }

    private fun diamond(x: Float, y: Float, r: Float) {
        path.reset()
        path.moveTo(x, y - r * 1.35f)
        path.lineTo(x + r * 0.95f, y)
        path.lineTo(x, y + r * 1.35f)
        path.lineTo(x - r * 0.95f, y)
        path.close()
    }

    private fun drawCrystal(c: Canvas, x: Float, y: Float, phase: Float, a: Float, fx: Boolean) {
        val r = crystalR * (1f + 0.1f * sin(phase))
        if (fx) {
            fill.color = withAlpha(gold, 0.10f * a)
            c.drawCircle(x, y, crystalR * 2.4f, fill)
        }
        fill.color = withAlpha(gold, 0.18f * a)
        c.drawCircle(x, y, crystalR * 1.7f, fill)
        diamond(x, y, r)
        fill.color = withAlpha(gold, 0.95f * a)
        c.drawPath(path, fill)
        stroke.strokeWidth = 0.4f * u
        stroke.color = withAlpha(white, 0.9f * a)
        c.drawPath(path, stroke)
        c.drawLine(x, y - r * 1.35f, x, y + r * 1.35f, stroke)
        c.drawLine(x - r * 0.95f, y, x + r * 0.95f, y, stroke)
        if (fx) {
            val sp = 0.5f + 0.5f * sin(phase * 1.3f)
            stroke.strokeWidth = 0.3f * u
            stroke.color = withAlpha(white, 0.7f * sp * a)
            c.drawLine(x + r * 1.2f, y - r * 1.2f - r * 0.4f * sp, x + r * 1.2f, y - r * 1.2f + r * 0.4f * sp, stroke)
            c.drawLine(x + r * 1.2f - r * 0.4f * sp, y - r * 1.2f, x + r * 1.2f + r * 0.4f * sp, y - r * 1.2f, stroke)
        }
    }

    private fun drawHud(c: Canvas) {
        val top = topY()
        label(c, "$score", w / 2f, top + 17f * ui, 15f * ui, withAlpha(white, 1f))
        label(c, "${Fa.BEST} ${max(store.best, score)}", w / 2f, top + 24f * ui, 3.8f * ui, withAlpha(white, 0.55f))
        drawCoinChip(c, w - 5f * ui, top, 8f * ui, runCoins, "+")
        if (screen == Screen.PLAYING) {
            uiButton(c, B_PAUSE, 5f * ui, top, 13f * ui, top + 8f * ui, 1, null, Icons.PAUSE)
        } else {
            rect.set(5f * ui, top, 13f * ui, top + 8f * ui)
            fill.color = withAlpha(white, 0.07f)
            c.drawRoundRect(rect, 2.5f * ui, 2.5f * ui, fill)
            Icons.draw(c, Icons.PAUSE, 9f * ui, top + 4f * ui, 4.6f * ui, withAlpha(white, 0.6f))
        }
        if (screen == Screen.PLAYING && score == 0 && playTime < 4f && countdown <= 0f) {
            val k = 0.5f + 0.5f * sin(clock * 5f)
            label(c, Fa.TAP_TO_FLIP, w / 2f, ballY + 14f * u, 4.2f * ui, withAlpha(white, 0.35f + 0.45f * k), maxW = 90f * ui)
        }
    }

    private fun drawCountdown(c: Canvas) {
        val n = min(3, (countdown / 0.5f).toInt() + 1)
        val frac = (countdown % 0.5f) / 0.5f
        label(c, "$n", w / 2f, h * 0.45f, 26f * ui * (0.8f + 0.2f * frac), withAlpha(white, 0.4f + 0.6f * frac))
    }

    private fun dimScreen(c: Canvas, a: Float) {
        fill.color = withAlpha(overlayColor, a)
        c.drawRect(0f, 0f, w, h, fill)
    }

    private fun drawMenu(c: Canvas) {
        val top = topY()
        ga = stag(0)
        drawCoinChip(c, w - 5f * ui, top, 9f * ui, store.coins, "")
        val ty = h * 0.20f + sin(clock * 2f) * 1.0f * ui + (1f - ga) * 6f * ui
        label(c, "ZUP", w / 2f, ty, 32f * ui, withAlpha(violet, 0.22f), spacing = 0.1f)
        label(c, "ZUP", w / 2f - 0.8f * ui, ty, 30f * ui, withAlpha(cyan, 0.85f), spacing = 0.1f)
        label(c, "ZUP", w / 2f + 0.8f * ui, ty, 30f * ui, withAlpha(magenta, 0.85f), spacing = 0.1f)
        label(c, "ZUP", w / 2f, ty, 30f * ui, withAlpha(white, 1f), spacing = 0.1f)
        label(c, "زوپ", w / 2f, ty + 13f * ui, 10f * ui, withAlpha(softViolet, 1f))
        stroke.strokeWidth = 0.6f * ui
        stroke.color = withAlpha(cyan, 0.7f)
        c.drawLine(w * 0.32f, ty + 19f * ui, w * 0.68f, ty + 19f * ui, stroke)

        ga = stag(1)
        val tagLines = wrapLines(Fa.TAGLINE, 3.8f * ui, 84f * ui)
        for ((i, ln) in tagLines.withIndex()) {
            label(c, ln, w / 2f, ty + 27f * ui + i * 5.2f * ui, 3.8f * ui, withAlpha(white, 0.75f), bold = false, maxW = 88f * ui)
        }

        ga = stag(2)
        val cardT = ty + 38f * ui + (1f - ga) * 5f * ui
        drawCard(c, w / 2f - 32f * ui, cardT, w / 2f + 32f * ui, cardT + 12f * ui, gold, 0.35f)
        Icons.draw(c, Icons.TROPHY, w / 2f + 25f * ui, cardT + 6f * ui, 6f * ui, withAlpha(gold, 1f))
        label(c, Fa.BEST_SCORE, w / 2f + 18f * ui, cardT + 7.4f * ui, 3.9f * ui, withAlpha(white, 0.7f), align = Paint.Align.RIGHT, maxW = 30f * ui)
        label(c, "${store.best}", w / 2f - 29f * ui, cardT + 8.2f * ui, 7f * ui, withAlpha(white, 1f), align = Paint.Align.LEFT)

        ga = stag(3)
        val pt = ty + 54f * ui + (1f - ga) * 5f * ui
        uiButton(c, B_PLAY, w / 2f - 33f * ui, pt, w / 2f + 33f * ui, pt + 15f * ui, 0, Fa.PLAY, Icons.PLAY)

        // Two columns, right-to-left: the first item of each pair sits on the right.
        ga = stag(4)
        val r1 = ty + 73f * ui + (1f - ga) * 5f * ui
        uiButton(c, B_SHOP, w / 2f + 2f * ui, r1, w / 2f + 33f * ui, r1 + 12f * ui, 1, Fa.SHOP, Icons.SHOP)
        uiButton(c, B_SETTINGS, w / 2f - 33f * ui, r1, w / 2f - 2f * ui, r1 + 12f * ui, 1, Fa.SETTINGS, Icons.SETTINGS)
        ga = stag(5)
        val r2 = ty + 88f * ui + (1f - ga) * 5f * ui
        uiButton(c, B_GUIDE, w / 2f + 2f * ui, r2, w / 2f + 33f * ui, r2 + 12f * ui, 1, Fa.MENU_GUIDE, Icons.HELP)
        uiButton(c, B_RECORDS, w / 2f - 33f * ui, r2, w / 2f - 2f * ui, r2 + 12f * ui, 1, Fa.MENU_RECORDS, Icons.TROPHY)
        ga = stag(6)
        val r3 = ty + 103f * ui + (1f - ga) * 5f * ui
        uiButton(c, B_EXIT, w / 2f - 33f * ui, r3, w / 2f + 33f * ui, r3 + 12f * ui, 2, Fa.EXIT, Icons.EXIT)
        ga = 1f
    }

    private fun drawPause(c: Canvas) {
        dimScreen(c, 0.65f)
        val k = back(screenTime / 0.35f)
        ga = ease(screenTime / 0.25f)
        val cw = 76f * ui
        val chh = 62f * ui
        val l = w / 2f - cw / 2f
        val t = (h - chh) / 2f
        c.save()
        c.scale(0.9f + 0.1f * k, 0.9f + 0.1f * k, w / 2f, h / 2f)
        drawCard(c, l, t, l + cw, t + chh, violet, 0.5f)
        label(c, Fa.PAUSED, w / 2f, t + 17f * ui, 8.5f * ui, withAlpha(white, 1f), maxW = cw - 8f * ui)
        uiButton(c, B_RESUME, l + 6f * ui, t + 25f * ui, l + cw - 6f * ui, t + 38f * ui, 0, Fa.RESUME, Icons.PLAY)
        uiButton(c, B_RESTART, l + cw / 2f + 1.5f * ui, t + 43f * ui, l + cw - 6f * ui, t + 55f * ui, 1, Fa.RESTART, Icons.RESTART)
        uiButton(c, B_HOME, l + 6f * ui, t + 43f * ui, l + cw / 2f - 1.5f * ui, t + 55f * ui, 1, Fa.MENU, Icons.HOME)
        c.restore()
        ga = 1f
    }

    private fun drawGameOver(c: Canvas) {
        dimScreen(c, 0.62f * ease(screenTime / 0.3f))
        val k = back(screenTime / 0.5f)
        ga = ease(screenTime / 0.3f)
        val cw = 84f * ui
        val chh = 110f * ui
        val l = w / 2f - cw / 2f
        val t = (h - chh) / 2f
        c.save()
        c.scale(0.85f + 0.15f * k, 0.85f + 0.15f * k, w / 2f, h / 2f)
        drawCard(c, l, t, l + cw, t + chh, if (newBest) gold else magenta, 0.55f)
        val cx = w / 2f
        label(c, Fa.GAME_OVER, cx + 0.6f * ui, t + 16f * ui, 10f * ui, withAlpha(cyan, 0.8f), maxW = cw - 8f * ui)
        label(c, Fa.GAME_OVER, cx - 0.6f * ui, t + 16f * ui, 10f * ui, withAlpha(magenta, 0.8f), maxW = cw - 8f * ui)
        label(c, Fa.GAME_OVER, cx, t + 16f * ui, 10f * ui, withAlpha(white, 1f), maxW = cw - 8f * ui)

        label(c, Fa.SCORE, cx, t + 29f * ui, 4.2f * ui, withAlpha(white, 0.6f))
        label(c, "$score", cx, t + 46f * ui, 17f * ui, withAlpha(white, 1f))

        if (newBest) {
            val p = 0.7f + 0.3f * sin(clock * 6f)
            Icons.draw(c, Icons.TROPHY, cx + 17f * ui, t + 56f * ui, 5.5f * ui, withAlpha(gold, p))
            label(c, Fa.NEW_RECORD, cx + 12f * ui, t + 57.5f * ui, 5.2f * ui, withAlpha(gold, p), align = Paint.Align.RIGHT, maxW = 38f * ui)
        } else {
            label(c, "${Fa.BEST} ${store.best}", cx, t + 57.5f * ui, 5f * ui, withAlpha(gold, 0.9f), maxW = cw - 10f * ui)
        }

        val total = runCoins + bonusCoins + recordCoins
        Icons.draw(c, Icons.COIN, cx - 9f * ui, t + 67f * ui, 6f * ui, withAlpha(gold, 1f), true)
        label(c, "+$total", cx - 4f * ui, t + 69f * ui, 6.2f * ui, withAlpha(white, 1f), align = Paint.Align.LEFT)
        // three small columns (right to left): crystals, bonus, record
        val colW = (cw - 8f * ui) / 3f
        val c1 = cx + colW
        val c3 = cx - colW
        label(c, "${Fa.CRYSTALS}: $runCoins", c1, t + 76f * ui, 3.2f * ui, withAlpha(white, 0.55f), bold = false, maxW = colW - 1f * ui)
        label(c, "${Fa.BONUS}: $bonusCoins", cx, t + 76f * ui, 3.2f * ui, withAlpha(white, 0.55f), bold = false, maxW = colW - 1f * ui)
        label(c, "${Fa.BEST}: $recordCoins", c3, t + 76f * ui, 3.2f * ui, withAlpha(white, 0.55f), bold = false, maxW = colW - 1f * ui)

        uiButton(c, B_RETRY, l + 9f * ui, t + 80f * ui, l + cw - 9f * ui, t + 93f * ui, 0, Fa.RETRY, Icons.RESTART)
        uiButton(c, B_SHOP, l + cw / 2f + 1.5f * ui, t + 96f * ui, l + cw - 6f * ui, t + 107f * ui, 1, Fa.SHOP, Icons.SHOP)
        uiButton(c, B_HOME, l + 6f * ui, t + 96f * ui, l + cw / 2f - 1.5f * ui, t + 107f * ui, 1, Fa.MENU, Icons.HOME)
        c.restore()
        ga = 1f
    }

    /** Back button on the right (RTL), title centred. */
    private fun drawHeader(c: Canvas, title: String, titleMaxW: Float) {
        val top = topY()
        val bh = 10f * ui
        ga = stag(0)
        uiButton(c, B_BACK, w - 5f * ui - bh, top, w - 5f * ui, top + bh, 1, null, Icons.BACK)
        label(c, title, w / 2f, top + bh * 0.72f, 6.5f * ui, withAlpha(white, 1f), maxW = titleMaxW)
    }

    private fun drawShop(c: Canvas) {
        val top = topY()
        val bh = 10f * ui
        drawHeader(c, Fa.SHOP, w - 64f * ui)
        drawCoinChip(c, 0f, top + bh * 0.1f, bh * 0.8f, store.coins, "", leftX = 5f * ui)

        ga = stag(1)
        val tabTop = top + bh + 4f * ui
        val tabH = 9f * ui
        uiButton(c, B_TAB_SKINS, w / 2f + 1.5f * ui, tabTop, w - 5f * ui, tabTop + tabH, if (shopTab == 0) 0 else 1, Fa.SKINS, -1)
        uiButton(c, B_TAB_TRAILS, 5f * ui, tabTop, w / 2f - 1.5f * ui, tabTop + tabH, if (shopTab == 1) 0 else 1, Fa.TRAILS, -1)

        val listTop = tabTop + tabH + 4f * ui
        listTopPx = listTop
        val isSkins = shopTab == 0
        val count = if (isSkins) Cosmetics.SKIN_COUNT else Cosmetics.TRAIL_COUNT
        val cardW = (w - 14f * ui) / 2f
        val cardH = 56f * ui
        val gap = 4f * ui
        val rowsN = (count + 1) / 2
        val contentH = rowsN * (cardH + gap)
        maxScroll = max(0f, contentH - (h - listTop) + gap)
        scrollY = scrollY.coerceIn(0f, maxScroll)

        ga = stag(2)
        c.save()
        c.clipRect(0f, listTop, w, h)
        for (i in 0 until count) {
            val col = i % 2
            val row = i / 2
            // right-to-left grid: column 0 is on the right
            val l = 5f * ui + (1 - col) * (cardW + gap)
            val t = listTop + row * (cardH + gap) - scrollY
            if (t + cardH < listTop || t > h) continue
            drawShopCard(c, i, isSkins, l, t, l + cardW, t + cardH)
        }
        c.restore()
        ga = 1f
    }

    private fun drawShopCard(c: Canvas, i: Int, isSkins: Boolean, l: Float, t: Float, r: Float, b: Float) {
        val owned = if (isSkins) store.ownsSkin(i) else store.ownsTrail(i)
        val equipped = if (isSkins) store.skin == i else store.trail == i
        val price = if (isSkins) Cosmetics.skinPrices[i] else Cosmetics.trailPrices[i]
        val name = if (isSkins) Cosmetics.skinNames[i] else Cosmetics.trailNames[i]
        val cx = (l + r) / 2f

        drawCard(c, l, t, r, b, if (equipped) cyan else violet, if (equipped) 0.9f else 0.3f)

        val pl = l + 3f * ui
        val pr = r - 3f * ui
        val pt = t + 3f * ui
        val pb = t + 31f * ui
        rect.set(pl, pt, pr, pb)
        fill.color = withAlpha(0xFF070418.toInt(), 0.95f)
        c.drawRoundRect(rect, 3f * ui, 3f * ui, fill)
        c.save()
        c.clipRect(pl, max(pt, listTopPx), pr, pb)
        val skinP = if (isSkins) i else store.skin
        val trailP = if (isSkins) store.trail else i
        Cosmetics.drawPreview(c, skinP, trailP, cx, (pt + pb) / 2f, (pr - pl) / 2f - 8f * ui, 3.6f * ui, clock, store.fxHigh)
        c.restore()
        if (!owned) {
            Icons.draw(c, Icons.LOCK, pr - 4f * ui, pt + 4f * ui, 4.6f * ui, withAlpha(white, 0.75f))
        }

        label(c, name, cx, t + 38f * ui, 4.3f * ui, withAlpha(white, 1f), maxW = (r - l) - 6f * ui)

        val bt = t + 42f * ui
        val bb = t + 53f * ui
        val id = (if (isSkins) B_SKIN else B_TRAIL) + i
        if (!owned) {
            uiButton(c, id, l + 3f * ui, bt, r - 3f * ui, bb, if (store.coins >= price) 0 else 1, "$price", Icons.COIN)
        } else if (equipped) {
            if (i == 0) {
                uiButton(c, id, l + 3f * ui, bt, r - 3f * ui, bb, 1, Fa.EQUIPPED, Icons.CHECK, false)
            } else {
                uiButton(c, id, l + 3f * ui, bt, r - 3f * ui, bb, 1, Fa.UNEQUIP, Icons.CHECK)
            }
        } else {
            uiButton(c, id, l + 3f * ui, bt, r - 3f * ui, bb, 0, Fa.EQUIP, -1)
        }
    }

    private fun drawSwitch(c: Canvas, cx: Float, cy: Float, anim: Float) {
        val sw = 14f * ui
        val sh = 7.4f * ui
        rect.set(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)
        fill.color = withAlpha(cyan, 0.12f + 0.38f * anim)
        c.drawRoundRect(rect, sh / 2f, sh / 2f, fill)
        stroke.strokeWidth = 0.4f * ui
        stroke.color = withAlpha(white, 0.25f * (1f - anim))
        c.drawRoundRect(rect, sh / 2f, sh / 2f, stroke)
        stroke.color = withAlpha(cyan, 0.9f * anim)
        c.drawRoundRect(rect, sh / 2f, sh / 2f, stroke)
        val kx = cx - sw / 2f + sh / 2f + (sw - sh) * anim
        fill.color = withAlpha(white, 1f)
        c.drawCircle(kx, cy, sh * 0.36f, fill)
    }

    /** One card row, right-to-left: icon and text on the right, the control/value goes on the left. */
    private fun settingRow(c: Canvas, idx: Int, y: Float, id: Int, icon: Int, text: String, interactive: Boolean) {
        ga = stag(1 + idx)
        val rh = 13f * ui
        val l = 5f * ui
        val r = w - 5f * ui
        val yy = y + (1f - ga) * 4f * ui
        drawCard(c, l, yy, r, yy + rh, violet, 0.3f)
        if (interactive) registerBtn(id, l, yy, r, yy + rh)
        if (pressedId == id && interactive) {
            rect.set(l, yy, r, yy + rh)
            fill.color = withAlpha(white, 0.06f)
            c.drawRoundRect(rect, 4f * ui, 4f * ui, fill)
        }
        Icons.draw(c, icon, r - 7f * ui, yy + rh / 2f, 6f * ui, withAlpha(cyan, 1f))
        label(c, text, r - 14f * ui, yy + rh / 2f + 1.5f * ui, 4.4f * ui, withAlpha(white, 1f), align = Paint.Align.RIGHT, maxW = (r - l) - 14f * ui - 34f * ui)
    }

    /** Value shown on the left side of a [settingRow]. */
    private fun rowValue(c: Canvas, y: Float, text: String, color: Int, maxW: Float = 34f * ui) {
        val rh = 13f * ui
        label(c, text, 10f * ui, y + rh / 2f + 1.5f * ui, 4.4f * ui, color, align = Paint.Align.LEFT, maxW = maxW)
    }

    private fun drawSettings(c: Canvas) {
        drawHeader(c, Fa.SETTINGS, w - 36f * ui)
        val top = topY()
        var y = top + 10f * ui + 6f * ui
        val step = 16f * ui
        val modal = confirmReset
        val rhh = 13f * ui
        val switchX = 5f * ui + 11f * ui

        val targets = floatArrayOf(
            if (store.sound) 1f else 0f,
            if (store.music) 1f else 0f,
            if (store.vibration) 1f else 0f
        )
        for (i in 0 until 3) swAnim[i] += (targets[i] - swAnim[i]) * min(1f, 14f * frameDt)

        settingRow(c, 0, y, B_SOUND, if (store.sound) Icons.VOLUME_ON else Icons.VOLUME_OFF, Fa.SOUND, true)
        drawSwitch(c, switchX, y + rhh / 2f, swAnim[0])
        y += step
        settingRow(c, 1, y, B_MUSIC, if (store.music) Icons.MUSIC_ON else Icons.MUSIC_OFF, Fa.MUSIC, true)
        drawSwitch(c, switchX, y + rhh / 2f, swAnim[1])
        y += step
        settingRow(c, 2, y, B_VIB, Icons.VIBRATE, Fa.VIBRATION, true)
        drawSwitch(c, switchX, y + rhh / 2f, swAnim[2])
        y += step

        settingRow(c, 3, y, B_FX, Icons.ZAP, Fa.FX, true)
        rowValue(c, y, if (store.fxHigh) Fa.FX_HIGH else Fa.FX_LOW, withAlpha(cyan, 1f))
        y += step

        settingRow(c, 4, y, B_RESET_SET, Icons.RESTART, Fa.RESET_SETTINGS, true)
        rowValue(c, y, Fa.RESET_SETTINGS_BTN, withAlpha(cyan, 1f))
        y += step

        settingRow(c, 5, y, B_RESET, Icons.TRASH, Fa.ERASE_DATA, true)
        rowValue(c, y, Fa.ERASE_BTN, withAlpha(magenta, 1f))
        y += step

        settingRow(c, 6, y, B_MYKET_COMMENT, Icons.STAR, Fa.MYKET_COMMENT, true)
        ga = 1f

        if (modal) {
            btnCount = 0
            drawConfirm(c)
        }
    }

    private fun drawConfirm(c: Canvas) {
        dimScreen(c, 0.7f)
        ga = ease(confirmTime / 0.2f)
        val k = back(confirmTime / 0.35f)
        val cw = 80f * ui
        val chh = 54f * ui
        val l = w / 2f - cw / 2f
        val t = (h - chh) / 2f
        c.save()
        c.scale(0.9f + 0.1f * k, 0.9f + 0.1f * k, w / 2f, h / 2f)
        drawCard(c, l, t, l + cw, t + chh, magenta, 0.7f)
        Icons.draw(c, Icons.TRASH, w / 2f, t + 9f * ui, 7f * ui, withAlpha(magenta, 1f))
        label(c, Fa.CONFIRM_TITLE, w / 2f, t + 21f * ui, 5f * ui, withAlpha(white, 1f), maxW = cw - 8f * ui)
        val body = wrapLines(Fa.CONFIRM_BODY, 3.4f * ui, cw - 10f * ui)
        for ((i, ln) in body.withIndex()) {
            label(c, ln, w / 2f, t + 27.5f * ui + i * 4.4f * ui, 3.4f * ui, withAlpha(white, 0.7f), bold = false, maxW = cw - 8f * ui)
        }
        // right-to-left: the safe action ("cancel") sits on the right
        uiButton(c, B_CANCEL, l + cw / 2f + 1.5f * ui, t + 38f * ui, l + cw - 5f * ui, t + 50f * ui, 1, Fa.CANCEL, -1)
        uiButton(c, B_ERASE, l + 5f * ui, t + 38f * ui, l + cw / 2f - 1.5f * ui, t + 50f * ui, 2, Fa.ERASE_NOW, -1)
        c.restore()
        ga = 1f
    }

    private fun drawGuide(c: Canvas) {
        drawHeader(c, Fa.GUIDE, w - 36f * ui)
        val top = topY()
        var y = top + 10f * ui + 5f * ui
        val l = 5f * ui
        val r = w - 5f * ui
        val bodySize = 3.7f * ui
        val textW = (r - l) - 10f * ui
        for (i in Fa.guideTitles.indices) {
            ga = stag(1 + i)
            val lines = wrapLines(Fa.guideBodies[i], bodySize, textW)
            val ch = 3f * ui + 5f * ui + lines.size * 4.8f * ui + 2f * ui
            val yy = y + (1f - ga) * 4f * ui
            drawCard(c, l, yy, r, yy + ch, violet, 0.3f)
            label(c, Fa.guideTitles[i], r - 5f * ui, yy + 3f * ui + 3.6f * ui, 4.4f * ui, withAlpha(cyan, 1f), align = Paint.Align.RIGHT, maxW = textW)
            for ((k, ln) in lines.withIndex()) {
                label(c, ln, r - 5f * ui, yy + 3f * ui + 5f * ui + (k + 1) * 4.8f * ui - 1f * ui, bodySize, withAlpha(white, 0.85f), bold = false, align = Paint.Align.RIGHT)
            }
            y += ch + 2.5f * ui
        }
        ga = 1f
    }

    private fun drawRecords(c: Canvas) {
        drawHeader(c, Fa.RECORDS, w - 36f * ui)
        val top = topY()
        var y = top + 10f * ui + 6f * ui
        val step = 15f * ui
        val items = arrayOf(
            Triple(Icons.TROPHY, Fa.BEST_SCORE, "${store.best}"),
            Triple(Icons.COIN, Fa.COINS, "${store.coins}"),
            Triple(Icons.PLAY, Fa.GAMES_PLAYED, "${store.gamesPlayed}"),
            Triple(Icons.ZAP, Fa.TOTAL_CRYSTALS, "${store.totalCrystals}"),
            Triple(Icons.SHOP, Fa.SKINS_OWNED, "${store.ownedSkinCount()} از ${Cosmetics.SKIN_COUNT}"),
            Triple(Icons.SHOP, Fa.TRAILS_OWNED, "${store.ownedTrailCount()} از ${Cosmetics.TRAIL_COUNT}")
        )
        for ((i, it) in items.withIndex()) {
            settingRow(c, i, y, 0, it.first, it.second, false)
            rowValue(c, y, it.third, withAlpha(if (i == 0) gold else white, 1f))
            y += step
        }
        ga = 1f
    }

    private fun drawToast(c: Canvas) {
        if (toastTime <= 0f) return
        val a = min(1f, toastTime / 0.3f)
        var ts = 4.2f * ui
        var txt = prep(toastText, ts, true, 0f)
        var tw = textPaint.measureText(txt)
        val maxTw = w - 20f * ui
        if (tw > maxTw) {
            ts *= maxTw / tw
            txt = prep(toastText, ts, true, 0f)
            tw = textPaint.measureText(txt)
        }
        val cy = h - 14f * ui
        rect.set(w / 2f - tw / 2f - 5f * ui, cy - 5f * ui, w / 2f + tw / 2f + 5f * ui, cy + 5f * ui)
        fill.color = withAlpha(0xFF0B0720.toInt(), 0.95f * a)
        c.drawRoundRect(rect, 5f * ui, 5f * ui, fill)
        stroke.strokeWidth = 0.4f * ui
        stroke.color = withAlpha(cyan, 0.7f * a)
        c.drawRoundRect(rect, 5f * ui, 5f * ui, stroke)
        label(c, toastText, w / 2f, cy + ts * 0.35f, ts, withAlpha(white, a))
    }
}
