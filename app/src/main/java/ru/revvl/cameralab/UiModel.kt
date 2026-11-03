package ru.revvl.cameralab

import kotlin.math.roundToInt

/** Visual mode of the shutter button. */
enum class ShutterMode { PHOTO, VIDEO, RECORDING }

/** Everything the shutter and the cancel button show, derived from activity flags. */
data class ShutterModel(
    val mode: ShutterMode,
    /** 0..1 progress ring (СТЕК ×3 frames / processing), null when idle. */
    val progress: Float?,
    /** Countdown digit inside the shutter, null when no timer runs. */
    val label: String?,
    /** Show the explicit "cancel" control (timer or СТЕК ×3 in progress). */
    val cancellable: Boolean
)

/** Pure UI decisions for the v2 viewfinder (no Android types). */
object UiModel {
    /** Portrait preview aspect (width / height): photo 3:4 (ImageCapture 4:3), video 9:16. */
    const val PHOTO_ASPECT = 3f / 4f
    const val VIDEO_ASPECT = 9f / 16f

    fun previewAspect(video: Boolean): Float = if (video) VIDEO_ASPECT else PHOTO_ASPECT

    /** Largest width×height with [aspect] (w/h) that fits into the available box. */
    fun fit(availableW: Int, availableH: Int, aspect: Float): Pair<Int, Int> {
        if (availableW <= 0 || availableH <= 0 || aspect <= 0f) return 0 to 0
        val hForW = (availableW / aspect).roundToInt()
        return if (hForW <= availableH) availableW to hForW
        else (availableH * aspect).roundToInt().coerceAtMost(availableW) to availableH
    }

    fun shutter(
        video: Boolean,
        recording: Boolean,
        photoBusy: Boolean,
        burst: Boolean,
        countdownLeft: Int,
        burstFramesDone: Int,
        burstProcessing: Boolean,
        frameCount: Int = BurstDenoiser.FRAME_COUNT
    ): ShutterModel {
        val mode = when {
            recording -> ShutterMode.RECORDING
            video -> ShutterMode.VIDEO
            else -> ShutterMode.PHOTO
        }
        val burstRunning = photoBusy && burst
        val progress = when {
            !burstRunning -> null
            burstProcessing -> 1f
            else -> (burstFramesDone.toFloat() / frameCount).coerceIn(0f, 1f)
        }
        val label = if (countdownLeft > 0) countdownLeft.toString() else null
        return ShutterModel(mode, progress, label, countdownLeft > 0 || burstRunning)
    }

    /**
     * The UI is locked to portrait; on-screen icons are counter-rotated so they stay upright.
     * Surface.ROTATION_90 (device turned with its top to the left) → +90° (clockwise).
     */
    fun iconRotation(surfaceRotation: Int): Float = when (surfaceRotation) {
        1 -> 90f
        2 -> 180f
        3 -> -90f
        else -> 0f
    }

    /** Equivalent of [target] (±360°) closest to [current], so icons turn the short way. */
    fun nearestAngle(current: Float, target: Float): Float {
        var t = target
        while (t - current > 180f) t -= 360f
        while (t - current < -180f) t += 360f
        return t
    }
}
