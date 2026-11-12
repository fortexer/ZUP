package com.fortexer.zup

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lucide-style line icons (24x24 grid, 2px round stroke) drawn directly with Canvas paths,
 * so no SVG library or extra dependency is needed.
 */
object Icons {
    const val HOME = 0
    const val PLAY = 1
    const val SHOP = 2
    const val SETTINGS = 3
    const val TROPHY = 4
    const val COIN = 5
    const val VOLUME_ON = 6
    const val VOLUME_OFF = 7
    const val MUSIC_ON = 8
    const val MUSIC_OFF = 9
    const val VIBRATE = 10
    const val BACK = 11
    const val CLOSE = 12
    const val PAUSE = 13
    const val RESTART = 14
    const val INFO = 15
    const val LOCK = 16
    const val CHECK = 17
    const val TRASH = 18
    const val ZAP = 19

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()
    private var ox = 0f
    private var oy = 0f
    private var k = 1f

    private fun px(x: Float) = ox + x * k
    private fun py(y: Float) = oy + y * k

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        c.drawLine(px(x1), py(y1), px(x2), py(y2), paint)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float) {
        c.drawCircle(px(x), py(y), r * k, paint)
    }

    private fun poly(c: Canvas, close: Boolean, vararg pts: Float) {
        path.reset()
        path.moveTo(px(pts[0]), py(pts[1]))
        var i = 2
        while (i < pts.size) {
            path.lineTo(px(pts[i]), py(pts[i + 1]))
            i += 2
        }
        if (close) path.close()
        c.drawPath(path, paint)
    }

    private fun arc(c: Canvas, l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float) {
        rect.set(px(l), py(t), px(r), py(b))
        c.drawArc(rect, start, sweep, false, paint)
    }

    private fun roundRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float) {
        rect.set(px(l), py(t), px(r), py(b))
        c.drawRoundRect(rect, rad * k, rad * k, paint)
    }

    fun draw(c: Canvas, id: Int, cx: Float, cy: Float, size: Float, color: Int, filled: Boolean = false) {
        k = size / 24f
        ox = cx - 12f * k
        oy = cy - 12f * k
        paint.color = color
        paint.strokeWidth = 2f * k
        fillP.color = color
        when (id) {
            HOME -> {
                poly(c, false, 3f, 11f, 12f, 3f, 21f, 11f)
                poly(c, false, 5f, 10f, 5f, 21f, 19f, 21f, 19f, 10f)
                poly(c, false, 10f, 21f, 10f, 14f, 14f, 14f, 14f, 21f)
            }
            PLAY -> {
                path.reset()
                path.moveTo(px(6f), py(3f))
                path.lineTo(px(20f), py(12f))
                path.lineTo(px(6f), py(21f))
                path.close()
                c.drawPath(path, if (filled) fillP else paint)
                if (filled) c.drawPath(path, paint)
            }
            SHOP -> {
                roundRect(c, 3f, 6f, 21f, 22f, 2f)
                poly(c, false, 3f, 6f, 6f, 2f, 18f, 2f, 21f, 6f)
                arc(c, 8f, 6f, 16f, 14f, 0f, 180f)
            }
            SETTINGS -> {
                circle(c, 12f, 12f, 3f)
                circle(c, 12f, 12f, 7.2f)
                for (i in 0 until 8) {
                    val a = i * 0.7854f
                    line(c, 12f + 7.2f * cos(a), 12f + 7.2f * sin(a), 12f + 10.4f * cos(a), 12f + 10.4f * sin(a))
                }
            }
            TROPHY -> {
                path.reset()
                path.moveTo(px(7f), py(4f))
                path.lineTo(px(17f), py(4f))
                path.lineTo(px(17f), py(10f))
                rect.set(px(7f), py(5f), px(17f), py(15f))
                path.arcTo(rect, 0f, 180f)
                path.close()
                c.drawPath(path, paint)
                poly(c, false, 7f, 5f, 4f, 5f, 4f, 8f, 7f, 11f)
                poly(c, false, 17f, 5f, 20f, 5f, 20f, 8f, 17f, 11f)
                line(c, 12f, 15f, 12f, 18f)
                line(c, 8f, 21f, 16f, 21f)
                line(c, 9f, 18f, 15f, 18f)
            }
            COIN -> {
                if (filled) c.drawCircle(px(12f), py(12f), 9f * k, fillP)
                paint.color = if (filled) 0xAA6B4B00.toInt() else color
                circle(c, 12f, 12f, if (filled) 5f else 9f)
                if (!filled) circle(c, 12f, 12f, 4.5f)
            }
            VOLUME_ON, VOLUME_OFF -> {
                poly(c, true, 3f, 9f, 7f, 9f, 12f, 4f, 12f, 20f, 7f, 15f, 3f, 15f)
                if (id == VOLUME_ON) {
                    arc(c, 7f, 7f, 17f, 17f, -45f, 90f)
                    arc(c, 3f, 3f, 21f, 21f, -45f, 90f)
                } else {
                    line(c, 16f, 9f, 22f, 15f)
                    line(c, 22f, 9f, 16f, 15f)
                }
            }
            MUSIC_ON, MUSIC_OFF -> {
                line(c, 9f, 18f, 9f, 5f)
                line(c, 9f, 5f, 21f, 3f)
                line(c, 21f, 3f, 21f, 16f)
                circle(c, 6f, 18f, 3f)
                circle(c, 18f, 16f, 3f)
                if (id == MUSIC_OFF) line(c, 3f, 3f, 21f, 21f)
            }
            VIBRATE -> {
                roundRect(c, 8f, 3f, 16f, 21f, 2f)
                line(c, 4f, 8f, 4f, 16f)
                line(c, 20f, 8f, 20f, 16f)
                line(c, 1.5f, 10.5f, 1.5f, 13.5f)
                line(c, 22.5f, 10.5f, 22.5f, 13.5f)
            }
            BACK -> {
                line(c, 19f, 12f, 5f, 12f)
                poly(c, false, 12f, 19f, 5f, 12f, 12f, 5f)
            }
            CLOSE -> {
                line(c, 18f, 6f, 6f, 18f)
                line(c, 6f, 6f, 18f, 18f)
            }
            PAUSE -> {
                line(c, 8f, 5f, 8f, 19f)
                line(c, 16f, 5f, 16f, 19f)
            }
            RESTART -> {
                arc(c, 3f, 3f, 21f, 21f, 215f, 280f)
                poly(c, false, 3f, 3f, 3f, 8f, 8f, 8f)
            }
            INFO -> {
                circle(c, 12f, 12f, 9f)
                line(c, 12f, 16f, 12f, 12f)
                c.drawCircle(px(12f), py(8f), 1.1f * k, fillP)
            }
            LOCK -> {
                roundRect(c, 5f, 11f, 19f, 21f, 2f)
                path.reset()
                path.moveTo(px(8f), py(11f))
                path.lineTo(px(8f), py(7f))
                rect.set(px(8f), py(3f), px(16f), py(11f))
                path.arcTo(rect, 180f, 180f)
                path.lineTo(px(16f), py(11f))
                c.drawPath(path, paint)
            }
            CHECK -> poly(c, false, 4f, 12f, 9f, 17f, 20f, 6f)
            TRASH -> {
                poly(c, false, 6f, 6f, 7f, 21f, 17f, 21f, 18f, 6f)
                line(c, 4f, 6f, 20f, 6f)
                poly(c, false, 9f, 6f, 9f, 3f, 15f, 3f, 15f, 6f)
            }
            ZAP -> poly(c, true, 13f, 2f, 3f, 14f, 12f, 14f, 11f, 22f, 21f, 10f, 12f, 10f)
        }
    }
}
