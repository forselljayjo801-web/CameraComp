package com.example.composeshot

import kotlin.math.hypot
import kotlin.math.min

/** Subject box in normalized 0..1 coordinates of the upright frame. */
data class SubjectBox(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val cx: Double get() = (left + right) / 2
    val cy: Double get() = (top + bottom) / 2
    val area: Double get() = (right - left) * (bottom - top)
}

enum class Technique(val title: String) {
    CENTRE("Centre composition"),
    GOLDEN("Golden ratio"),
    FILL_FRAME("Fill the frame"),
    NEGATIVE_SPACE("Negative space")
}

data class TechniqueScore(val technique: Technique, val score: Int, val hint: String)

object CompositionTechniques {

    private val goldenPoints = listOf(
        0.382 to 0.382, 0.618 to 0.382,
        0.382 to 0.618, 0.618 to 0.618
    )

    /** Scores every technique for the subject. A null subject scores zero everywhere. */
    fun scoreAll(box: SubjectBox?): List<TechniqueScore> {
        if (box == null) return Technique.values().map { TechniqueScore(it, 0, "No subject") }
        return listOf(centre(box), golden(box), fillFrame(box), negativeSpace(box))
    }

    private fun centre(b: SubjectBox): TechniqueScore {
        val d = hypot(b.cx - 0.5, b.cy - 0.5)
        val score = (100 * (1 - min(d / 0.25, 1.0))).toInt()
        val hint = when {
            score >= 80 -> "Centred"
            b.cx < 0.5 -> "Move subject right"
            b.cx > 0.5 -> "Move subject left"
            b.cy < 0.5 -> "Move subject down"
            else -> "Move subject up"
        }
        return TechniqueScore(Technique.CENTRE, score, hint)
    }

    private fun golden(b: SubjectBox): TechniqueScore {
        val (px, py) = goldenPoints.minBy { hypot(it.first - b.cx, it.second - b.cy) }
        val d = hypot(px - b.cx, py - b.cy)
        val score = (100 * (1 - min(d / 0.2, 1.0))).toInt()
        val hint = if (score >= 80) "On a golden point" else "Move toward the golden point"
        return TechniqueScore(Technique.GOLDEN, score, hint)
    }

    private fun fillFrame(b: SubjectBox): TechniqueScore {
        val a = b.area
        val ratio = when {
            a < 0.25 -> a / 0.25
            a > 0.6 -> maxOf(0.0, 1 - (a - 0.6) / 0.4)
            else -> 1.0
        }
        val score = (100 * ratio).toInt()
        val hint = when {
            score >= 80 -> "Good fill"
            a < 0.25 -> "Move closer"
            else -> "Step back a little"
        }
        return TechniqueScore(Technique.FILL_FRAME, score, hint)
    }

    private fun negativeSpace(b: SubjectBox): TechniqueScore {
        val largestMargin = maxOf(b.left, 1 - b.right, b.top, 1 - b.bottom)
        val roomScore = min(largestMargin / 0.3, 1.0)
        val sizeOk = if (b.area <= 0.3) 1.0 else 0.5
        val score = (100 * roomScore * sizeOk).toInt()
        val hint = if (score >= 80) "Good open space" else "Leave more empty space"
        return TechniqueScore(Technique.NEGATIVE_SPACE, score, hint)
    }
}
