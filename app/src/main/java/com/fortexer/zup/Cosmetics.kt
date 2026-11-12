package com.fortexer.zup

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin

/** Ball skins and trail effects: data, prices and all drawing code. */
object Cosmetics {
    const val SKIN_COUNT = 8
    const val TRAIL_COUNT = 6

    val skinNames = arrayOf("Neon Blue", "Purple Core", "Toxic Green", "Solar Gold", "Crimson", "Ice", "Galaxy", "Shadow")
    val skinPrices = intArrayOf(0, 120, 160, 220, 280, 350, 500, 650)
    val trailNames = arrayOf("Classic", "Blue Flame", "Purple Trail", "Electric", "Rainbow", "Plasma")
    val trailPrices = intArrayOf(0, 150, 220, 320, 450, 600)

    private val skinCore = intArrayOf(
        0xFF00F0FF.toInt(), 0xFFB14DFF.toInt(), 0xFF7CFF3A.toInt(), 0xFFFFD83D.toInt(),
        0xFFFF2B4D.toInt(), 0xFFBDF4FF.toInt(), 0xFF3B1FA8.toInt(), 0xFF0B0B18.toInt()
    )
    private val skinGlow = intArrayOf(
        0xFF00F0FF.toInt(), 0xFFB14DFF.toInt(), 0xFF7CFF3A.toInt(), 0xFFFF9E1B.toInt(),
        0xFFFF2B4D.toInt(), 0xFF7FD8FF.toInt(), 0xFF8A4DFF.toInt(), 0xFF6A2BFF.toInt()
    )

    private val white = 0xFFFFFFFF.toInt()
    private val cyan = 0xFF00F0FF.toInt()
    private val magenta = 0xFFFF2E93.toInt()
    private val violet = 0xFFB14DFF.toInt()
    private val softViolet = 0xFFD9B8FF.toInt()
    private val rainbow = IntArray(24) { Color.HSVToColor(floatArrayOf(it * 15f, 1f, 1f)) }

    private val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private fun al(color: Int, a: Float): Int {
        val ia = (a.coerceIn(0f, 1f) * 255f).toInt()
        return (color and 0x00FFFFFF) or (ia shl 24)
    }

    fun glowColor(skin: Int): Int = skinGlow[if (skin in 0 until SKIN_COUNT) skin else 0]

    private fun highlight(c: Canvas, x: Float, y: Float, r: Float) {
        fp.color = al(white, 0.85f)
        c.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.42f, fp)
    }

    fun drawBall(c: Canvas, skin: Int, x: Float, y: Float, r: Float, t: Float, high: Boolean) {
        val s = if (skin in 0 until SKIN_COUNT) skin else 0
        val glow = skinGlow[s]
        if (high) {
            fp.color = al(glow, 0.08f); c.drawCircle(x, y, r * 2.8f, fp)
            fp.color = al(glow, 0.14f); c.drawCircle(x, y, r * 2.0f, fp)
            fp.color = al(glow, 0.28f); c.drawCircle(x, y, r * 1.5f, fp)
        } else {
            fp.color = al(glow, 0.22f); c.drawCircle(x, y, r * 1.7f, fp)
        }
        fp.color = skinCore[s]
        c.drawCircle(x, y, r, fp)
        when (s) {
            0 -> highlight(c, x, y, r)
            1 -> {
                fp.color = al(0xFFFFD6FF.toInt(), 0.95f)
                c.drawCircle(x, y, r * (0.45f + 0.1f * sin(t * 4f)), fp)
            }
            2 -> {
                sp.strokeWidth = r * 0.3f
                sp.color = al(0xFF1B6B00.toInt(), 0.9f)
                c.drawCircle(x, y, r * 0.62f, sp)
                highlight(c, x, y, r)
            }
            3 -> {
                fp.color = al(0xFFFFF3B0.toInt(), 0.95f)
                c.drawCircle(x, y, r * 0.55f, fp)
                sp.strokeWidth = r * 0.16f
                sp.color = al(0xFFFFD83D.toInt(), 0.9f)
                for (i in 0 until 8) {
                    val a = t * 2f + i * 0.7854f
                    c.drawLine(x + cos(a) * r * 1.25f, y + sin(a) * r * 1.25f, x + cos(a) * r * 1.6f, y + sin(a) * r * 1.6f, sp)
                }
            }
            4 -> {
                fp.color = al(0xFF8A0020.toInt(), 0.85f)
                c.drawCircle(x + r * 0.15f, y + r * 0.15f, r * 0.55f, fp)
                highlight(c, x, y, r)
            }
            5 -> {
                sp.strokeWidth = r * 0.14f
                sp.color = al(white, 0.9f)
                c.drawLine(x - r * 0.7f, y, x + r * 0.7f, y, sp)
                c.drawLine(x, y - r * 0.7f, x, y + r * 0.7f, sp)
                highlight(c, x, y, r)
            }
            6 -> {
                val cols = intArrayOf(white, magenta, cyan)
                for (i in 0 until 3) {
                    val a = t * 2.5f + i * 2.094f
                    fp.color = al(cols[i], 0.95f)
                    c.drawCircle(x + cos(a) * r * 0.55f, y + sin(a) * r * 0.55f, r * 0.2f, fp)
                }
            }
            else -> {
                sp.strokeWidth = r * 0.28f
                sp.color = al(violet, 0.95f)
                c.drawCircle(x, y, r * 0.86f, sp)
                fp.color = al(violet, 0.7f)
                c.drawCircle(x - r * 0.2f, y - r * 0.2f, r * 0.2f, fp)
            }
        }
    }

    fun trailLife(style: Int): Float = when (style) {
        0 -> 0.28f
        1 -> 0.38f
        2 -> 0.45f
        3 -> 0.30f
        4 -> 0.50f
        else -> 0.40f
    }

    fun drawTrail(
        c: Canvas, style: Int, skin: Int, x: Float, y: Float, px: Float, py: Float, hasPrev: Boolean,
        age: Float, idx: Int, r: Float, t: Float, high: Boolean
    ) {
        val k = 1f - age / trailLife(style)
        if (k <= 0f) return
        when (style) {
            1 -> {
                val col = if (k > 0.66f) 0xFFD6F6FF.toInt() else if (k > 0.33f) 0xFF00F0FF.toInt() else 0xFF2A5BFF.toInt()
                val yy = y - (1f - k) * r * 1.8f + sin(idx * 1.7f + t * 20f) * r * 0.25f
                fp.color = al(col, 0.6f * k + 0.1f)
                c.drawCircle(x, yy, r * (0.35f + 0.75f * k), fp)
                if (high) {
                    fp.color = al(0xFF2A5BFF.toInt(), 0.18f * k)
                    c.drawCircle(x, yy, r * 1.5f * k, fp)
                }
            }
            2 -> {
                if (hasPrev) {
                    sp.strokeWidth = r * 1.4f * k
                    sp.color = al(violet, 0.5f * k)
                    c.drawLine(px, py, x, y, sp)
                }
                fp.color = al(softViolet, 0.35f * k)
                c.drawCircle(x, y, r * 0.5f * k, fp)
            }
            3 -> {
                if (hasPrev) {
                    val j = sin(idx * 12.9898f + t * 40f) * r * 1.3f
                    val jp = sin((idx - 1) * 12.9898f + t * 40f) * r * 1.3f
                    sp.strokeWidth = r * 0.55f
                    sp.color = al(cyan, 0.35f * k)
                    c.drawLine(px, py + jp, x, y + j, sp)
                    sp.strokeWidth = r * 0.22f
                    sp.color = al(white, k)
                    c.drawLine(px, py + jp, x, y + j, sp)
                }
            }
            4 -> {
                val col = rainbow[(idx + (t * 20f).toInt()) % rainbow.size]
                if (hasPrev) {
                    sp.strokeWidth = r * 1.2f * k
                    sp.color = al(col, 0.85f * k)
                    c.drawLine(px, py, x, y, sp)
                } else {
                    fp.color = al(col, 0.85f * k)
                    c.drawCircle(x, y, r * 0.6f * k, fp)
                }
            }
            5 -> {
                val rad = r * (0.5f + 0.6f * k) * (1f + 0.25f * sin(idx * 0.9f + t * 12f))
                val col = if (idx % 2 == 0) magenta else cyan
                if (high) {
                    fp.color = al(col, 0.15f * k)
                    c.drawCircle(x, y, rad * 1.8f, fp)
                }
                fp.color = al(col, 0.55f * k)
                c.drawCircle(x, y, rad, fp)
            }
            else -> {
                fp.color = al(glowColor(skin), 0.35f * k)
                c.drawCircle(x, y, r * (0.4f + 0.6f * k), fp)
            }
        }
    }

    /** Animated ball + trail used by the shop cards. */
    fun drawPreview(c: Canvas, skin: Int, trail: Int, cx: Float, cy: Float, halfW: Float, r: Float, t: Float, high: Boolean) {
        val life = trailLife(trail)
        val n = 18
        var px = cx
        for (i in n downTo 1) {
            val age = life * i / n
            val x = cx + halfW * sin((t - age) * 2.4f)
            drawTrail(c, trail, skin, x, cy, px, cy, i < n, age, n - i, r, t, high)
            px = x
        }
        drawBall(c, skin, cx + halfW * sin(t * 2.4f), cy, r, t, high)
    }
}
