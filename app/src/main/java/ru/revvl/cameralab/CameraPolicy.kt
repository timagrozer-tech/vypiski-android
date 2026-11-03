package ru.revvl.cameralab

import kotlin.math.abs

/** Pure, device-independent policy, tested without camera hardware. */
object CameraPolicy {
    fun effectivePhotoFormat(
        requested: Int, available: Set<Int>, extensionEnabled: Boolean, jpeg: Int
    ): Int = if (!extensionEnabled && requested in available) requested else jpeg

    fun validTimer(value: Int): Int = if (value in setOf(0, 3, 10)) value else 0

    fun nextTimer(value: Int): Int = when (value) {
        0 -> 3
        3 -> 10
        else -> 0
    }

    /** Degrees of hysteresis around a 45° boundary, so the rotation does not flicker. */
    const val ROTATION_HYSTERESIS = 10

    /**
     * Maps OrientationEventListener degrees to a Surface.ROTATION_* value (0..3),
     * as recommended for CameraX targetRotation when the UI itself does not rotate.
     * Returns null for ORIENTATION_UNKNOWN (negative); keeps [current] near boundaries.
     */
    fun surfaceRotationFor(degrees: Int, current: Int? = null): Int? {
        if (degrees < 0) return null
        val d = degrees % 360
        if (current != null && current in 0..3) {
            // ROTATION_0 ↔ 0°, ROTATION_270 ↔ 90°, ROTATION_180 ↔ 180°, ROTATION_90 ↔ 270°.
            val center = (360 - current * 90) % 360
            var diff = abs(d - center)
            if (diff > 180) diff = 360 - diff
            if (diff <= 45 + ROTATION_HYSTERESIS) return current
        }
        return when (d) {
            in 45..134 -> 3
            in 135..224 -> 2
            in 225..314 -> 1
            else -> 0
        }
    }

    /**
     * Flash cycle. Video uses a continuous torch, so AUTO is meaningless there: OFF ↔ ON.
     * Without a flash unit the mode is always [off]. Constants are passed by the caller
     * (ImageCapture.FLASH_MODE_*) to keep this function free of Android dependencies.
     */
    fun nextFlashMode(current: Int, video: Boolean, hasFlash: Boolean, off: Int, auto: Int, on: Int): Int =
        when {
            !hasFlash -> off
            video -> if (current == on) off else on
            current == off -> auto
            current == auto -> on
            else -> off
        }

    /** Normalizes a stored flash mode to one valid for the current mode and lens. */
    fun normalizeFlashMode(current: Int, video: Boolean, hasFlash: Boolean, off: Int, auto: Int, on: Int): Int =
        when {
            !hasFlash -> off
            current != off && current != auto && current != on -> off
            video && current == auto -> off
            else -> current
        }

    /** Next lens for the one-tap lens button; wraps around; null when there is nothing to switch to. */
    fun nextLens(ids: List<String>, current: String?): String? {
        if (ids.size < 2) return null
        val i = ids.indexOf(current)
        return ids[(i + 1).mod(ids.size)]
    }

    /**
     * A finalized CameraX recording keeps a playable file for some "errors", e.g. when the
     * camera is closed by the lifecycle (SOURCE_INACTIVE) or a size/duration limit is hit.
     */
    fun isVideoKept(error: Int, none: Int, keptErrors: Set<Int>, hasOutputUri: Boolean): Boolean =
        hasOutputUri && (error == none || error in keptErrors)
}
