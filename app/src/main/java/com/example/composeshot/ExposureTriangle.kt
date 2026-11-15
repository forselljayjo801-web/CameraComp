package com.example.composeshot

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ExposureMode { AUTO_MIX, SHUTTER_PRIORITY, APERTURE_PRIORITY }

data class ExposureSettings(
    val aperture: Double,
    val shutterSeconds: Double,
    val iso: Int,
    val note: String
) {
    fun apertureLabel(): String = "f/${"%.1f".format(aperture)}"

    fun shutterLabel(): String =
        if (shutterSeconds >= 1.0) "${"%.1f".format(shutterSeconds)}s"
        else "1/${(1.0 / shutterSeconds).roundToInt()}"

    fun summary(): String = "${apertureLabel()}  ·  ${shutterLabel()}  ·  ISO $iso"
}

object ExposureTriangle {
    val ISO_STEPS = listOf(100, 200, 400, 800, 1600, 3200, 6400)

    private val SHUTTER_STEPS = listOf(
        1.0 / 4000, 1.0 / 2000, 1.0 / 1000, 1.0 / 500, 1.0 / 250, 1.0 / 125,
        1.0 / 60, 1.0 / 30, 1.0 / 15, 1.0 / 8, 1.0 / 4, 1.0 / 2, 1.0
    )

    private const val MIN_SHUTTER = 1.0 / 4000
    private const val MAX_HANDHELD_SHUTTER = 1.0 / 60

    fun shutterFor(ev100: Double, aperture: Double, iso: Int): Double =
        aperture * aperture * 100.0 / (iso * 2.0.pow(ev100))

    fun isoFor(ev100: Double, aperture: Double, shutter: Double): Int {
        val raw = aperture * aperture * 100.0 / (shutter * 2.0.pow(ev100))
        return ISO_STEPS.minByOrNull { abs(it - raw) } ?: 100
    }

    fun solve(
        ev100: Double,
        mode: ExposureMode,
        aperture: Double,
        userShutter: Double = 1.0 / 125,
        userIso: Int = 100,
        evCompensation: Double = 0.0
    ): ExposureSettings {
        val ev = ev100 + evCompensation
        return when (mode) {
            ExposureMode.AUTO_MIX -> autoMix(ev, aperture)
            ExposureMode.SHUTTER_PRIORITY -> {
                val iso = isoFor(ev, aperture, userShutter)
                ExposureSettings(aperture, snap(userShutter), iso, "Shutter fixed, ISO follows")
            }
            ExposureMode.APERTURE_PRIORITY -> {
                val t = shutterFor(ev, aperture, userIso)
                ExposureSettings(aperture, snap(t), userIso, "ISO fixed, shutter follows")
            }
        }
    }

    private fun autoMix(ev: Double, aperture: Double): ExposureSettings {
        var iso = ISO_STEPS.first()
        var t = shutterFor(ev, aperture, iso)

        while (t > MAX_HANDHELD_SHUTTER && iso != ISO_STEPS.last()) {
            iso = ISO_STEPS[ISO_STEPS.indexOf(iso) + 1]
            t = shutterFor(ev, aperture, iso)
        }

        val note = when {
            t < MIN_SHUTTER -> "Too bright: shutter at the fastest limit"
            t > MAX_HANDHELD_SHUTTER -> "Too dark: even max ISO needs a tripod"
            iso == ISO_STEPS.first() -> "Balanced at base ISO"
            else -> "ISO raised to keep the shot sharp"
        }
        return ExposureSettings(aperture, snap(t.coerceIn(MIN_SHUTTER, 1.0)), iso, note)
    }

    private fun snap(t: Double): Double =
        SHUTTER_STEPS.minByOrNull { abs(log2(it / t)) } ?: t
}
