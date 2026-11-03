package ru.revvl.cameralab

import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode
import androidx.camera.video.Quality

/** User-facing names of CameraX constants (moved from MainActivity, unchanged). */
object CameraLabels {
    fun photoFormat(format: Int): String = when (format) {
        ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR -> "ULTRA HDR"
        ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> "RAW + JPEG"
        else -> "JPEG"
    }

    fun quality(q: Quality): String = when (q) {
        Quality.UHD -> "4K"
        Quality.FHD -> "1080p"
        Quality.HD -> "720p"
        Quality.SD -> "480p"
        else -> "AUTO"
    }

    fun qualityFromName(name: String?): Quality? = when (name) {
        "4K" -> Quality.UHD
        "1080p" -> Quality.FHD
        "720p" -> Quality.HD
        "480p" -> Quality.SD
        else -> null
    }

    /** Order used in the quality dialog. */
    val VIDEO_CHOICES: List<Pair<Quality, String>> = listOf(
        Quality.UHD to "4K · UHD",
        Quality.FHD to "1080p · FHD",
        Quality.HD to "720p · HD",
        Quality.SD to "480p · SD"
    )

    val EXTENSION_CHOICES: List<Pair<Int, String>> = listOf(
        ExtensionMode.NONE to "Авто",
        ExtensionMode.HDR to "HDR",
        ExtensionMode.NIGHT to "Ночь",
        ExtensionMode.BOKEH to "Портрет"
    )

    val CAPTURE_CONSTANTS = CaptureConstants(
        jpeg = ImageCapture.OUTPUT_FORMAT_JPEG,
        rawJpeg = ImageCapture.OUTPUT_FORMAT_RAW_JPEG,
        flashOff = ImageCapture.FLASH_MODE_OFF,
        flashAuto = ImageCapture.FLASH_MODE_AUTO,
        flashOn = ImageCapture.FLASH_MODE_ON
    )
}
