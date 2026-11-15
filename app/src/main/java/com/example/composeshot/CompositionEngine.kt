package com.example.composeshot

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** subject is normalized 0..1 in the upright (rotated) frame. */
data class CompositionResult(
    val subject: RectF?,
    val score: Int,
    val targetZoom: Float?,
    val targetPoint: Pair<Float, Float>?,
    val hint: String
)

object CompositionEngine {
    private val powerPoints = listOf(
        1f / 3 to 1f / 3, 2f / 3 to 1f / 3,
        1f / 3 to 2f / 3, 2f / 3 to 2f / 3
    )
    private const val IDEAL_AREA = 0.30f
    private const val MAX_AUTO_ZOOM = 6f

    fun evaluate(subject: RectF?, zoom: Float, minZoom: Float, maxZoom: Float): CompositionResult {
        if (subject == null) {
            return CompositionResult(null, 0, null, null, "Point at a subject")
        }
        val cx = subject.centerX()
        val cy = subject.centerY()
        val area = subject.width() * subject.height()

        val target = powerPoints.minBy { hypot(it.first - cx, it.second - cy) }
        val dist = hypot(target.first - cx, target.second - cy)
        val posScore = 1f - min(dist / 0.35f, 1f)

        val sizeScore = when {
            area < 0.18f -> area / 0.18f
            area > 0.45f -> max(0f, 1f - (area - 0.45f) / 0.35f)
            else -> 1f
        }
        val margin = minOf(subject.left, subject.top, 1f - subject.right, 1f - subject.bottom)
        val edgeScore = if (margin < 0.02f) 0.5f else 1f

        val score = (100f * (0.45f * posScore + 0.40f * sizeScore + 0.15f * edgeScore)).toInt()

        val ratio = sqrt(IDEAL_AREA / max(area, 0.001f))
        val upperZoom = min(maxZoom, MAX_AUTO_ZOOM)
        val targetZoom = if (abs(ratio - 1f) > 0.12f)
            (zoom * ratio).coerceIn(minZoom, upperZoom) else null

        val dx = target.first - cx
        val dy = target.second - cy
        val hint = when {
            ratio > 1.12f && zoom < upperZoom -> "Zoom in"
            ratio < 0.89f -> "Zoom out"
            abs(dx) > 0.06f && abs(dx) >= abs(dy) -> if (dx > 0) "Turn camera left" else "Turn camera right"
            abs(dy) > 0.06f -> if (dy > 0) "Tilt camera up" else "Tilt camera down"
            else -> "Perfect — hold it"
        }
        return CompositionResult(subject, score, targetZoom, target, hint)
    }
}
