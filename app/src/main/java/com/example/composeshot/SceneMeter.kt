package com.example.composeshot

import androidx.camera.core.ImageProxy
import kotlin.math.ln

/** Reads the preview's brightness and turns it into an approximate EV at ISO 100. Does not close the frame. */
class SceneMeter {

    fun measure(image: ImageProxy): Double? {
        val plane = image.planes[0]
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = image.width
        val h = image.height
        var sum = 0L
        var n = 0
        val step = 8
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val idx = y * rowStride + x * pixelStride
                if (idx < buf.limit()) {
                    sum += buf.get(idx).toInt() and 0xFF
                    n++
                }
                x += step
            }
            y += step
        }
        if (n == 0) return null
        return meanLumaToEv(sum.toDouble() / n)
    }

    companion object {
        /** Middle grey (about 118) is treated as EV 10. Rough estimate only. */
        fun meanLumaToEv(mean: Double): Double =
            (10.0 + ln(mean.coerceAtLeast(1.0) / 118.0) / ln(2.0)).coerceIn(0.0, 16.0)
    }
}
