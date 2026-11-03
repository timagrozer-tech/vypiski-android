package ru.revvl.cameralab

/** ImageCapture constants supplied by the caller, so planning stays free of CameraX. */
data class CaptureConstants(
    val jpeg: Int,
    val rawJpeg: Int,
    val flashOff: Int,
    val flashAuto: Int,
    val flashOn: Int
)

/** Everything the photo session decision depends on, already queried from the hardware. */
data class PhotoSessionRequest(
    val requestedFormat: Int,
    val supportedFormats: Set<Int>,
    val extensionRequested: Boolean,
    val extensionAvailable: Boolean,
    val burst: Boolean,
    val manualExposure: Boolean,
    val flashMode: Int,
    val hasFlash: Boolean
)

data class PhotoSessionPlan(
    val useExtension: Boolean,
    val outputFormat: Int,
    /** Flash mode kept in UI state (normalized for this lens). */
    val flashMode: Int,
    /** Flash mode actually given to ImageCapture (OFF for burst / manual / RAW). */
    val captureFlashMode: Int,
    val minimizeLatency: Boolean,
    val jpegQuality: Int
)

/**
 * Decides how a photo session is configured. Same rules as v0.3.1 MainActivity.bindCamera,
 * moved here so they are unit-tested:
 *  - OEM extension only if requested, declared available and not in burst mode;
 *  - RAW/Ultra HDR only without extension and burst, and only if CameraX declares it;
 *  - flash off for burst, manual exposure and RAW+JPEG; off when the lens has no flash.
 */
object SessionPlanner {
    fun photo(r: PhotoSessionRequest, c: CaptureConstants): PhotoSessionPlan {
        val useExtension = r.extensionRequested && r.extensionAvailable && !r.burst
        val format = CameraPolicy.effectivePhotoFormat(
            r.requestedFormat, r.supportedFormats, useExtension || r.burst, c.jpeg
        )
        val flash = CameraPolicy.normalizeFlashMode(
            r.flashMode, false, r.hasFlash, c.flashOff, c.flashAuto, c.flashOn
        )
        val captureFlash = if (r.burst || r.manualExposure || format == c.rawJpeg) c.flashOff else flash
        return PhotoSessionPlan(
            useExtension = useExtension,
            outputFormat = format,
            flashMode = flash,
            captureFlashMode = captureFlash,
            minimizeLatency = r.burst,
            jpegQuality = if (r.burst) 96 else 100
        )
    }
}
