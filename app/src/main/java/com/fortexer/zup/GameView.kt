package com.fortexer.zup

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * ZUP – offline endless arcade game drawn entirely with the Android Canvas.
 * All sizes are expressed in "u" = 1% of the screen width so it scales to any phone.
 */
class GameView(context: Context) : View(context) {

    private enum class State { MENU, PLAYING, OVER }

    private class Row(var y: Float, val gapX: Float, val gapW: Float, val color: Int) {
        var passed = false
    }

    private class Crystal(var x: Float, var y: Float) {
        var phase = Random.nextFloat() * 6.2832f
    }

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float
    )

    private class Trail(val x: Float, val y: Float, var age: Float)

    // ---- palette ----
    private val cyan = 0xFF00F0FF.toInt()
    private val magenta = 0xFFFF2E93.toInt()
    private val violet = 0xFFB14DFF.toInt()
    private val gold = 0xFFFFE94D.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val softViolet = 0xFFD9B8FF.toInt()
    private val bgTop = 0xFF16043A.toInt()
    private val bgBottom = 0xFF03030C.toInt()
    private val overlayColor = 0xFF02020A.toInt()

    // ---- persistence ----
    private val prefs = context.getSharedPreferences("zup_prefs", Context.MODE_PRIVATE)
    private var best = prefs.getInt("best", 0)

    // ---- game state ----
    private var state = State.MENU
    private var w = 0f
    private var h = 0f
    private var u = 1f
    private var ballR = 0f
    private var ballY = 0f
    private var rowH = 0f
    private var crystalR = 0f
    private var ballX = 0f
    private var dir = 1
    private var score = 0
    private var crystalCount = 0
    private var newBest = false
    private var rowCounter = 0
    private var spawnTimer = 0f
    private var lastGapCenter = 0f
    private var scroll = 0f
    private var overTime = 0f
    private var playTime = 0f
    private var shake = 0f
    private var clock = 0f
    private var lastNanos = 0L

    private val rows = ArrayList<Row>()
    private val crystals = ArrayList<Crystal>()
    private val particles = ArrayList<Particle>()
    private val trail = ArrayList<Trail>()

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

    // =====================================================================
    // setup
    // =====================================================================

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        super.onSizeChanged(nw, nh, ow, oh)
        w = nw.toFloat()
        h = nh.toFloat()
        u = w / 100f
        ballR = 3.4f * u
        ballY = h * 0.78f
        rowH = 6.5f * u
        crystalR = 2.8f * u
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, bgTop, bgBottom, Shader.TileMode.CLAMP)
        resetWorld()
    }

    private fun resetWorld() {
        rows.clear()
        crystals.clear()
        particles.clear()
        trail.clear()
        ballX = w / 2f
        dir = 1
        lastGapCenter = w / 2f
        rowCounter = 0
        spawnTimer = 0.4f
    }

    private fun startGame() {
        resetWorld()
        score = 0
        crystalCount = 0
        newBest = false
        playTime = 0f
        shake = 0f
        state = State.PLAYING
    }

    private fun gameOver() {
        state = State.OVER
        overTime = 0f
        shake = 0.45f
        burst(ballX, ballY, cyan, 26, 1.4f)
        burst(ballX, ballY, white, 10, 1f)
        if (score > best) {
            best = score
            newBest = true
            prefs.edit().putInt("best", best).apply()
        } else {
            newBest = false
        }
    }

    // =====================================================================
    // input
    // =====================================================================

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val action = e.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            when (state) {
                State.MENU -> startGame()
                State.PLAYING -> {
                    dir = -dir
                    burst(ballX, ballY, cyan, 6, 0.5f)
                }
                State.OVER -> if (overTime > 0.5f) startGame()
            }
        }
        return true
    }

    // =====================================================================
    // simulation
    // =====================================================================

    private fun update(dt: Float) {
        clock += dt
        val playing = state == State.PLAYING
        val f = if (playing) min(2.4f, 1f + score * 0.012f) else 0.8f
        val fall = 36f * u * f
        val hs = 80f * u * f
        val spacingT = max(1.15f, 1.4f - score * 0.004f)

        if (state != State.OVER) {
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

            for (r in rows) r.y += fall * dt
            for (c in crystals) {
                c.y += fall * dt
                c.phase += dt * 4f
            }
            trail.add(Trail(ballX, ballY, 0f))
        }

        for (t in trail) t.age += dt
        trail.removeAll { it.age > 0.28f }
        rows.removeAll { it.y > h + rowH }
        crystals.removeAll { it.y > h + crystalR * 3f }

        if (playing) {
            for (r in rows) {
                if (!r.passed && r.y > ballY + ballR) {
                    r.passed = true
                    score++
                }
                if (r.y + rowH > ballY - ballR && r.y < ballY + ballR) {
                    if (hit(0f, r.y, r.gapX, r.y + rowH) ||
                        hit(r.gapX + r.gapW, r.y, w, r.y + rowH)
                    ) {
                        gameOver()
                        break
                    }
                }
            }
        }

        if (state == State.PLAYING) {
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
                    burst(c.x, c.y, gold, 14, 1f)
                }
            }
        }

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

        shake = max(0f, shake - dt)
        if (state == State.OVER) overTime += dt
    }

    private fun spawnRow(spacingDist: Float) {
        val gapW = max(30f * u, 42f * u - score * 0.25f * u)
        val maxShift = w * 0.5f
        val minC = max(gapW / 2f, lastGapCenter - maxShift)
        val maxC = min(w - gapW / 2f, lastGapCenter + maxShift)
        val center = minC + Random.nextFloat() * max(0f, maxC - minC)
        lastGapCenter = center
        val gapX = center - gapW / 2f

        val color = if (rowCounter % 2 == 0) magenta else violet
        rowCounter++
        rows.add(Row(-rowH, gapX, gapW, color))

        if (Random.nextFloat() < 0.6f) {
            crystals.add(Crystal(gapX + gapW * (0.3f + 0.4f * Random.nextFloat()), -rowH * 0.5f))
        }
        if (state == State.PLAYING && Random.nextFloat() < 0.3f) {
            val x = 2f * ballR + Random.nextFloat() * (w - 4f * ballR)
            crystals.add(Crystal(x, -rowH - spacingDist * 0.5f))
        }
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
                Particle(
                    x, y, cos(a) * s, sin(a) * s, life, life, color,
                    (0.5f + Random.nextFloat() * 0.9f) * u
                )
            )
        }
    }

    // =====================================================================
    // rendering
    // =====================================================================

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min(0.033f, (now - lastNanos) / 1_000_000_000f)
        lastNanos = now
        if (w > 0f) {
            update(dt)
            render(canvas)
        }
        postInvalidateOnAnimation()
    }

    private fun withAlpha(color: Int, a: Float): Int {
        val ia = (a.coerceIn(0f, 1f) * 255f).toInt()
        return (color and 0x00FFFFFF) or (ia shl 24)
    }

    private fun label(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        bold: Boolean = true, align: Paint.Align = Paint.Align.CENTER, spacing: Float = 0f
    ) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.typeface = if (bold) boldFace else normalFace
        textPaint.textAlign = align
        textPaint.letterSpacing = spacing
        c.drawText(s, x, y, textPaint)
    }

    private fun render(c: Canvas) {
        c.drawRect(0f, 0f, w, h, bgPaint)
        drawGrid(c)

        c.save()
        if (shake > 0f) {
            val amp = shake * 6f * u
            c.translate((Random.nextFloat() - 0.5f) * 2f * amp, (Random.nextFloat() - 0.5f) * 2f * amp)
        }

        val a = if (state == State.MENU) 0.45f else 1f
        for (r in rows) {
            if (r.gapX > 0.5f * u) drawBlock(c, -4f * u, r.y, r.gapX, r.y + rowH, r.color, a)
            val rightStart = r.gapX + r.gapW
            if (w - rightStart > 0.5f * u) drawBlock(c, rightStart, r.y, w + 4f * u, r.y + rowH, r.color, a)
        }
        for (cr in crystals) drawCrystal(c, cr.x, cr.y, cr.phase, a)

        for (p in particles) {
            val k = p.life / p.maxLife
            fill.color = withAlpha(p.color, k)
            c.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * k), fill)
        }

        if (state != State.OVER) {
            for (t in trail) {
                val k = 1f - t.age / 0.28f
                fill.color = withAlpha(cyan, 0.35f * k)
                c.drawCircle(t.x, t.y, ballR * (0.4f + 0.6f * k), fill)
            }
            drawBall(c)
        }
        c.restore()

        when (state) {
            State.MENU -> drawMenu(c)
            State.PLAYING -> drawHud(c)
            State.OVER -> {
                drawHud(c)
                drawGameOver(c)
            }
        }
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

    private fun drawBlock(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, a: Float) {
        rect.set(l, t, r, b)
        val rad = rowH * 0.45f
        stroke.strokeWidth = 2.6f * u
        stroke.color = withAlpha(color, 0.10f * a)
        c.drawRoundRect(rect, rad, rad, stroke)
        stroke.strokeWidth = 1.3f * u
        stroke.color = withAlpha(color, 0.20f * a)
        c.drawRoundRect(rect, rad, rad, stroke)
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

    private fun drawCrystal(c: Canvas, x: Float, y: Float, phase: Float, a: Float) {
        val r = crystalR * (1f + 0.1f * sin(phase))
        fill.color = withAlpha(gold, 0.10f * a)
        c.drawCircle(x, y, crystalR * 2.4f, fill)
        fill.color = withAlpha(gold, 0.18f * a)
        c.drawCircle(x, y, crystalR * 1.7f, fill)
        diamond(x, y, r)
        fill.color = withAlpha(gold, 0.95f * a)
        c.drawPath(path, fill)
        stroke.strokeWidth = 0.4f * u
        stroke.color = withAlpha(white, 0.9f * a)
        c.drawPath(path, stroke)
        c.drawLine(x, y - r * 1.35f, x, y + r * 1.35f, stroke)
    }

    private fun drawBall(c: Canvas) {
        fill.color = withAlpha(cyan, 0.08f)
        c.drawCircle(ballX, ballY, ballR * 2.8f, fill)
        fill.color = withAlpha(cyan, 0.14f)
        c.drawCircle(ballX, ballY, ballR * 2.0f, fill)
        fill.color = withAlpha(cyan, 0.28f)
        c.drawCircle(ballX, ballY, ballR * 1.5f, fill)
        fill.color = cyan
        c.drawCircle(ballX, ballY, ballR, fill)
        fill.color = withAlpha(white, 0.9f)
        c.drawCircle(ballX - ballR * 0.2f, ballY - ballR * 0.2f, ballR * 0.5f, fill)
    }

    private fun drawHud(c: Canvas) {
        label(c, "$score", w / 2f, 20f * u, 16f * u, white)
        label(c, "BEST $best", w / 2f, 27f * u, 3.8f * u, withAlpha(white, 0.55f), spacing = 0.15f)
        // crystal counter
        diamond(w - 17f * u, 12.5f * u, 1.8f * u)
        fill.color = withAlpha(gold, 0.95f)
        c.drawPath(path, fill)
        label(c, "$crystalCount", w - 6f * u, 14.5f * u, 5f * u, white, align = Paint.Align.RIGHT)

        if (state == State.PLAYING && score == 0 && playTime < 4f) {
            val k = 0.5f + 0.5f * sin(clock * 5f)
            label(c, "TAP TO FLIP", w / 2f, ballY + 14f * u, 4.2f * u, withAlpha(white, 0.35f + 0.45f * k), spacing = 0.2f)
        }
    }

    private fun drawMenu(c: Canvas) {
        val ty = h * 0.30f + sin(clock * 2f) * 1.2f * u
        // soft halo + chromatic offset
        label(c, "ZUP", w / 2f, ty, 36f * u, withAlpha(violet, 0.22f), spacing = 0.1f)
        label(c, "ZUP", w / 2f - 0.8f * u, ty, 34f * u, withAlpha(cyan, 0.85f), spacing = 0.1f)
        label(c, "ZUP", w / 2f + 0.8f * u, ty, 34f * u, withAlpha(magenta, 0.85f), spacing = 0.1f)
        label(c, "ZUP", w / 2f, ty, 34f * u, white, spacing = 0.1f)

        label(c, "زوپ", w / 2f, ty + 15f * u, 11f * u, softViolet)

        stroke.strokeWidth = 0.6f * u
        stroke.color = withAlpha(cyan, 0.7f)
        c.drawLine(w * 0.32f, ty + 22f * u, w * 0.68f, ty + 22f * u, stroke)

        val pulse = 0.55f + 0.45f * sin(clock * 3f)
        label(c, "TAP TO PLAY", w / 2f, h * 0.56f, 6.2f * u, withAlpha(white, pulse), spacing = 0.2f)
        label(c, "برای شروع لمس کنید", w / 2f, h * 0.56f + 7.5f * u, 4.6f * u, withAlpha(softViolet, pulse), bold = false)

        label(c, "BEST $best", w / 2f, h * 0.66f, 4.4f * u, withAlpha(gold, 0.9f), spacing = 0.2f)
    }

    private fun drawGameOver(c: Canvas) {
        fill.color = withAlpha(overlayColor, 0.62f)
        c.drawRect(0f, 0f, w, h, fill)

        label(c, "GAME OVER", w / 2f + 0.6f * u, h * 0.28f, 12.5f * u, withAlpha(cyan, 0.8f), spacing = 0.05f)
        label(c, "GAME OVER", w / 2f - 0.6f * u, h * 0.28f, 12.5f * u, withAlpha(magenta, 0.8f), spacing = 0.05f)
        label(c, "GAME OVER", w / 2f, h * 0.28f, 12.5f * u, white, spacing = 0.05f)
        label(c, "بازی تمام شد", w / 2f, h * 0.28f + 9f * u, 6f * u, softViolet)

        label(c, "SCORE", w / 2f, h * 0.40f, 4f * u, withAlpha(white, 0.6f), spacing = 0.3f)
        label(c, "$score", w / 2f, h * 0.40f + 20f * u, 22f * u, white)

        val by = h * 0.40f + 30f * u
        if (newBest) {
            val k = 0.7f + 0.3f * sin(clock * 6f)
            label(c, "NEW BEST!", w / 2f, by, 6f * u, withAlpha(gold, k), spacing = 0.2f)
        } else {
            label(c, "BEST $best", w / 2f, by, 5f * u, withAlpha(gold, 0.9f), spacing = 0.2f)
        }

        if (overTime > 0.5f) {
            val pulse = 0.55f + 0.45f * sin(clock * 3f)
            label(c, "TAP TO RETRY", w / 2f, h * 0.70f, 6f * u, withAlpha(white, pulse), spacing = 0.2f)
            label(c, "برای شروع مجدد لمس کنید", w / 2f, h * 0.70f + 7.5f * u, 4.6f * u, withAlpha(softViolet, pulse), bold = false)
        }
    }
}
