package ru.revvl.cameralab

import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** What the user asked for in PRO (null = automatic). */
data class ProSettings(
    val iso: Int? = null,
    val exposureNs: Long? = null,
    /** Lens focus distance in diopters (0 = infinity), null = autofocus. */
    val focusDiopters: Float? = null,
    /** CONTROL_AWB_MODE preset, null = AUTO. */
    val awbMode: Int? = null,
    /** AE (+AWB when auto) locked by a long press on the preview. */
    val aeAwbLocked: Boolean = false
)

/** What the selected lens declares (Camera2 characteristics), never inferred from the chipset. */
data class ProSupport(
    val manualSensor: Boolean = false,
    /** LENS_INFO_MINIMUM_FOCUS_DISTANCE in diopters; 0 = fixed focus / no manual focus. */
    val minFocusDiopters: Float = 0f,
    /** False when LENS_INFO_FOCUS_DISTANCE_CALIBRATION is UNCALIBRATED (units are relative). */
    val focusCalibrated: Boolean = false,
    val aeLock: Boolean = false,
    val awbLock: Boolean = false,
    val awbModes: Set<Int> = emptySet()
) {
    val manualFocus: Boolean get() = manualSensor && minFocusDiopters > 0f
    /** Presets other than AUTO and OFF. */
    val whiteBalancePresets: List<Int>
        get() = ProControls.AWB_ORDER.filter { it in awbModes && it != ProControls.AWB_AUTO }
}

/** Camera2 request options to apply as ONE bundle (setCaptureRequestOptions replaces the bundle). */
data class RequestPlan(
    val aeModeOff: Boolean = false,
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val aeLock: Boolean = false,
    val awbMode: Int? = null,
    val awbLock: Boolean = false,
    val afModeOff: Boolean = false,
    val focusDiopters: Float? = null
) {
    val isEmpty: Boolean get() = this == EMPTY

    companion object {
        val EMPTY = RequestPlan()
    }
}

/** Pure decisions for PRO tools; constants mirror stable Camera2 values. */
object ProControls {
    // CaptureRequest.CONTROL_AWB_MODE_* (public API constants).
    const val AWB_OFF = 0
    const val AWB_AUTO = 1
    const val AWB_INCANDESCENT = 2
    const val AWB_FLUORESCENT = 3
    const val AWB_WARM_FLUORESCENT = 4
    const val AWB_DAYLIGHT = 5
    const val AWB_CLOUDY_DAYLIGHT = 6
    const val AWB_TWILIGHT = 7
    const val AWB_SHADE = 8

    /** Display order: warm → cold light sources, then sky conditions. */
    val AWB_ORDER = listOf(AWB_AUTO, AWB_INCANDESCENT, AWB_WARM_FLUORESCENT, AWB_FLUORESCENT,
        AWB_DAYLIGHT, AWB_CLOUDY_DAYLIGHT, AWB_SHADE, AWB_TWILIGHT)

    fun awbLabel(mode: Int?): String = when (mode) {
        null, AWB_AUTO -> "Авто"
        AWB_INCANDESCENT -> "Лампа накаливания"
        AWB_FLUORESCENT -> "Люминесцентная"
        AWB_WARM_FLUORESCENT -> "Тёплая люминесц."
        AWB_DAYLIGHT -> "Дневной свет"
        AWB_CLOUDY_DAYLIGHT -> "Облачно"
        AWB_TWILIGHT -> "Сумерки"
        AWB_SHADE -> "Тень"
        else -> "Режим $mode"
    }

    /**
     * One bundle for Camera2CameraControl:
     *  - nothing under OEM extensions (they do not promise Camera2 request support);
     *  - manual ISO/shutter and manual focus only in photo, not in СТЕК ×3, only if declared;
     *  - AE lock is meaningless under manual exposure; AWB lock only with AUTO white balance;
     *  - WB preset only if the lens lists it in CONTROL_AWB_AVAILABLE_MODES.
     */
    fun plan(s: ProSettings, sup: ProSupport, video: Boolean, extension: Boolean, burst: Boolean): RequestPlan {
        if (extension) return RequestPlan.EMPTY
        val manualExposure = !video && !burst && sup.manualSensor && s.iso != null && s.exposureNs != null
        val focus = s.focusDiopters?.takeIf { !video && !burst && sup.manualFocus }
            ?.coerceIn(0f, sup.minFocusDiopters)
        val awb = s.awbMode?.takeIf { it != AWB_AUTO && it in sup.awbModes }
        return RequestPlan(
            aeModeOff = manualExposure,
            iso = if (manualExposure) s.iso else null,
            exposureNs = if (manualExposure) s.exposureNs else null,
            aeLock = s.aeAwbLocked && sup.aeLock && !manualExposure,
            awbMode = awb,
            awbLock = s.aeAwbLocked && sup.awbLock && awb == null,
            afModeOff = focus != null,
            focusDiopters = focus
        )
    }

    /**
     * The live histogram adds an ImageAnalysis stream. Only in plain JPEG photo mode: OEM
     * extensions, RAW+JPEG, Ultra HDR, video and СТЕК ×3 already use the stream budget.
     */
    fun histogramAllowed(video: Boolean, extension: Boolean, burst: Boolean, format: Int, jpeg: Int): Boolean =
        !video && !extension && !burst && format == jpeg

    /** Badge text after a long press: what is actually locked on this lens. */
    fun lockLabel(aeLockSupported: Boolean, manualFocus: Boolean): String = when {
        manualFocus && aeLockSupported -> "AE 🔒"
        manualFocus -> "🔒 замер"
        aeLockSupported -> "AE/AF 🔒"
        else -> "AF 🔒"
    }

    /** Any PRO setting that changes the picture (for the "PRO ●" indicator). */
    fun isActive(s: ProSettings): Boolean =
        (s.iso != null && s.exposureNs != null) || s.focusDiopters != null ||
            (s.awbMode != null && s.awbMode != AWB_AUTO)

    // ---- manual focus slider

    const val FOCUS_STEPS = 100

    /** Slider 0 = infinity … FOCUS_STEPS = closest focus (linear in diopters). */
    fun focusFromProgress(progress: Int, minFocusDiopters: Float): Float =
        minFocusDiopters * progress.coerceIn(0, FOCUS_STEPS) / FOCUS_STEPS

    fun progressFromFocus(diopters: Float, minFocusDiopters: Float): Int =
        if (minFocusDiopters <= 0f) 0
        else (diopters / minFocusDiopters * FOCUS_STEPS).roundToInt().coerceIn(0, FOCUS_STEPS)

    fun focusLabel(diopters: Float, calibrated: Boolean): String {
        if (diopters <= 0.001f) return "∞"
        if (!calibrated) return "${"%.2f".format(Locale.US, diopters)} (усл. ед.)"
        val m = 1f / diopters
        return if (m >= 1f) "${"%.1f".format(Locale.US, m)} м" else "${(m * 100).roundToInt()} см"
    }
}

/** Luma histogram of a downscaled analysis frame; computed off the main thread. */
object Histogram {
    const val BINS = 64

    data class Result(val bins: IntArray, val samples: Int, val shadowClip: Double, val highlightClip: Double) {
        val peak: Int get() = bins.maxOrNull() ?: 0
    }

    /**
     * Y plane of a YUV_420_888 image: [rowStride]/[pixelStride] as reported by the plane.
     * Samples every [step]-th pixel in both directions to keep the cost tiny.
     */
    fun ofLuma(y: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int = 1, step: Int = 4): Result {
        require(width > 0 && height > 0 && rowStride > 0 && pixelStride > 0 && step > 0)
        val bins = IntArray(BINS)
        var n = 0
        var dark = 0
        var bright = 0
        val limit = y.limit()
        var row = 0
        while (row < height) {
            var col = 0
            val base = row * rowStride
            while (col < width) {
                val i = base + col * pixelStride
                if (i >= limit) break
                val v = y.get(i).toInt() and 255
                bins[v * BINS / 256]++
                if (v <= 2) dark++
                if (v >= 253) bright++
                n++
                col += step
            }
            row += step
        }
        val total = max(n, 1).toDouble()
        return Result(bins, n, dark / total, bright / total)
    }
}
