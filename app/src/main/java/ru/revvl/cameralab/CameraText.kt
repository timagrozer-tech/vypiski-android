package ru.revvl.cameralab

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/** Pure text formatting shared by the UI, diagnostics and file naming. No Android types. */
object CameraText {

    /**
     * Human shutter label. v0.3 truncated 1e9/16_666_667 = 59.99 to "1/59";
     * the denominator is now rounded, so presets read as 1/60, 1/30, 1/15.
     */
    fun formatShutter(ns: Long): String {
        require(ns > 0) { "Exposure must be positive" }
        return if (ns >= 1_000_000_000L) {
            val tenths = (ns / 100_000_000.0).roundToLong()
            if (tenths % 10 == 0L) "${tenths / 10} с" else "${tenths / 10}.${tenths % 10} с"
        } else {
            "1/${(1_000_000_000.0 / ns).roundToLong()} с"
        }
    }

    /** INFO_SUPPORTED_HARDWARE_LEVEL values are stable public Camera2 API constants. */
    fun hardwareLevelName(level: Int?): String = when (level) {
        0 -> "LIMITED"
        1 -> "FULL"
        2 -> "LEGACY"
        3 -> "LEVEL_3"
        4 -> "EXTERNAL"
        else -> "?"
    }

    /** Compact lens label for the round lens button: "СЕЛФИ", "4.7 мм" or "ID 2". */
    fun lensShort(front: Boolean, focalMm: Float?, id: String): String = when {
        front -> "СЕЛФИ"
        focalMm != null -> "${"%.1f".format(Locale.US, focalMm)} мм"
        else -> "ID $id"
    }

    /**
     * CameraX linear zoom (0..1) for a zoom ratio: linear in the cropped field width, i.e.
     * (1/min − 1/ratio) / (1/min − 1/max). Used to keep the slider in sync with pinch zoom.
     */
    fun linearZoom(ratio: Float, min: Float, max: Float): Float {
        if (max <= min || min <= 0f) return 0f
        return ((1f / min - 1f / ratio) / (1f / min - 1f / max)).coerceIn(0f, 1f)
    }

    /** "m:ss" for the recording indicator; also correct beyond one hour ("75:03"). */
    fun recordingClock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** Russian plural for 1..4 frames: "1 кадр", "2 кадра", "5 кадров". */
    fun frames(n: Int): String {
        val m10 = n % 10; val m100 = n % 100
        val word = when {
            m10 == 1 && m100 != 11 -> "кадр"
            m10 in 2..4 && m100 !in 12..14 -> "кадра"
            else -> "кадров"
        }
        return "$n $word"
    }

    /** Status line after СТЕК ×3: frames, size, estimated noise change, processing time. */
    fun burstSummary(used: Int, width: Int, height: Int, noiseReduction: Double, elapsedMs: Long): String {
        val pct = Math.round(noiseReduction * 100).toInt()
        val noise = when {
            pct > 0 -> "шум −$pct%"
            pct < 0 -> "шум +${-pct}%"
            else -> "шум ±0%"
        }
        val seconds = "%.1f".format(Locale.US, elapsedMs / 1000.0).replace('.', ',')
        return "СТЕК ×3 · ${frames(used)} · ${width}×$height · $noise · $seconds с"
    }

    fun evLabel(ev: Float): String = "EV ${"%+.1f".format(Locale.US, ev)}"

    fun fileStamp(millis: Long): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(millis))
}
