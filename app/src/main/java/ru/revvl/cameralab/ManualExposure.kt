package ru.revvl.cameralab

import kotlin.math.abs

/**
 * Pure math for the PRO dialog (ISO slider, shutter presets). Ranges come from
 * SENSOR_INFO_SENSITIVITY_RANGE / SENSOR_INFO_EXPOSURE_TIME_RANGE of the actual lens.
 */
object ManualExposure {
    const val ISO_STEPS = 1000

    /** Exposure presets in ns: 1/1000 … 1/4 s. */
    val SHUTTER_PRESETS_NS = listOf(
        1_000_000L, 2_000_000L, 4_000_000L, 8_000_000L,
        16_666_667L, 33_333_333L, 66_666_667L, 125_000_000L, 250_000_000L
    )

    fun shutterOptions(minNs: Long, maxNs: Long): List<Long> =
        SHUTTER_PRESETS_NS.filter { it in minNs..maxNs }

    fun initialIso(current: Int?, minIso: Int, maxIso: Int): Int =
        (current ?: minIso.coerceAtLeast(100)).coerceIn(minIso, maxIso)

    /** Nearest preset to the previously applied exposure, or the middle preset. */
    fun initialShutter(current: Long?, options: List<Long>): Long {
        require(options.isNotEmpty())
        return current?.let { ns -> options.minByOrNull { abs(it - ns) } } ?: options[options.size / 2]
    }

    fun isoToProgress(iso: Int, minIso: Int, maxIso: Int): Int =
        ((iso - minIso).toDouble() / (maxIso - minIso).coerceAtLeast(1) * ISO_STEPS)
            .toInt().coerceIn(0, ISO_STEPS)

    fun progressToIso(progress: Int, minIso: Int, maxIso: Int): Int =
        (minIso + ((maxIso - minIso) * progress.coerceIn(0, ISO_STEPS) / ISO_STEPS.toDouble()).toInt())
            .coerceIn(minIso, maxIso)
}
