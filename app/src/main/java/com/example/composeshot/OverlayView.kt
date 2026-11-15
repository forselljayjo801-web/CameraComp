package com.example.composeshot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class OverlayView(context: Context) : View(context) {
    var result: CompositionResult? = null
        set(v) { field = v; invalidate() }
    var levelAngleDeg: Float = 0f
        set(v) { field = v; invalidate() }
    var focusPoint: Pair<Float, Float>? = null
        set(v) { field = v; invalidate() }

    private val grid = Paint().apply { color = Color.argb(110, 255, 255, 255); strokeWidth = 2f }
    private val box = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 6f }
    private val dot = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f }
    private val level = Paint().apply { color = Color.rgb(255, 204, 0); strokeWidth = 4f }
    private val focusRing = Paint().apply {
        color = Color.rgb(255, 204, 0); style = Paint.Style.STROKE; strokeWidth = 3f
    }

    private fun content(): RectF {
        val ar = 3f / 4f
        val vw = width.toFloat(); val vh = height.toFloat()
        return if (vw / vh > ar) {
            val w = vh * ar; RectF((vw - w) / 2, 0f, (vw + w) / 2, vh)
        } else {
            val h = vw / ar; RectF(0f, (vh - h) / 2, vw, (vh + h) / 2)
        }
    }

    override fun onDraw(c: Canvas) {
        val r = content()
        for (i in 1..2) {
            val x = r.left + r.width() * i / 3f
            val y = r.top + r.height() * i / 3f
            c.drawLine(x, r.top, x, r.bottom, grid)
            c.drawLine(r.left, y, r.right, y, grid)
        }

        // Horizon level: short line at screen centre, tilts with the phone.
        val cx = r.centerX(); val cy = r.centerY()
        val half = r.width() * 0.12f
        val rad = Math.toRadians(levelAngleDeg.toDouble())
        val dx = (half * Math.cos(rad)).toFloat()
        val dy = (half * Math.sin(rad)).toFloat()
        level.color = if (kotlin.math.abs(levelAngleDeg) < 2f) Color.rgb(76, 217, 100) else Color.rgb(255, 204, 0)
        c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, level)

        // Tap-to-focus ring.
        focusPoint?.let { (fx, fy) ->
            c.drawCircle(r.left + fx * r.width(), r.top + fy * r.height(), 70f, focusRing)
        }

        val res = result ?: return
        box.color = when {
            res.score >= 80 -> Color.rgb(76, 217, 100)
            res.score >= 50 -> Color.rgb(255, 204, 0)
            else -> Color.rgb(255, 69, 58)
        }
        res.subject?.let {
            c.drawRoundRect(
                r.left + it.left * r.width(), r.top + it.top * r.height(),
                r.left + it.right * r.width(), r.top + it.bottom * r.height(), 16f, 16f, box
            )
        }
        res.targetPoint?.let { (tx, ty) ->
            c.drawCircle(r.left + tx * r.width(), r.top + ty * r.height(), 28f, dot)
        }
    }
}
