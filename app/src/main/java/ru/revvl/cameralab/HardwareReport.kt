package ru.revvl.cameralab

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.camera.extensions.ExtensionMode

/**
 * Diagnostic text report: no personal data, no location, shared only by the user (clipboard).
 * Moved from MainActivity.createHardwareReport (v0.3.1) without changes to the content.
 */
object HardwareReport {

    data class SessionInfo(
        val burstEnabled: Boolean,
        val requestedFormat: Int,
        val effectiveFormat: Int
    )

    fun build(caps: CameraCapabilities, lenses: List<Lens>, session: SessionInfo): String = buildString {
        appendLine("REVVL CAMERA LAB v${BuildConfig.VERSION_NAME}")
        appendLine("СТЕК ×3: ${if (session.burstEnabled) "включён" else "выключен"} (экспериментальный, JPEG до ${BurstDenoiser.MAX_LONG_EDGE}px)")
        appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Публичных камер для CameraX: ${lenses.size}")
        appendLine("Формат выбран: ${CameraLabels.photoFormat(session.requestedFormat)}")
        appendLine("Формат текущей сессии: ${CameraLabels.photoFormat(session.effectiveFormat)}")
        for (lens in lenses) {
            appendLine("\n=== CAMERA ID ${lens.id} ===")
            appendLine("${lens.label}; передняя=${lens.front}")
            try {
                val c = caps.characteristics(lens.id)
                if (c == null) {
                    appendLine("Ошибка чтения: характеристики недоступны")
                    continue
                }
                val lvl = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                appendLine("Camera2 hardware level: ${CameraText.hardwareLevelName(lvl)} ($lvl)")
                appendLine("Вспышка: ${c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true}")
                val capsList = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                appendLine("RAW: ${CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in capsList}")
                appendLine("MANUAL_SENSOR: ${CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capsList}")
                appendLine("ISO: ${c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)}")
                appendLine("Exposure ns: ${c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)}")
                appendLine("OIS modes: ${c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.joinToString() ?: "н/д"}")
                val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val sizes = map?.getOutputSizes(ImageFormat.JPEG)
                    ?.sortedByDescending { it.width.toLong() * it.height }
                appendLine("Макс. JPEG: ${sizes?.firstOrNull()?.let { "${it.width}×${it.height}" } ?: "н/д"}")
                val rawSizes = runCatching {
                    map?.getOutputSizes(ImageFormat.RAW_SENSOR)
                        ?.sortedByDescending { it.width.toLong() * it.height }
                }.getOrNull()
                appendLine("Макс. RAW: ${rawSizes?.firstOrNull()?.let { "${it.width}×${it.height}" } ?: "н/д"}")
                val pro = caps.proSupport(lens.id)
                appendLine("PRO: AE lock=${pro.aeLock}, AWB lock=${pro.awbLock}, ручной фокус=${pro.manualFocus} " +
                    "(мин. ${pro.minFocusDiopters} дптр, калибровка=${pro.focusCalibrated})")
                appendLine("WB пресеты: ${pro.whiteBalancePresets.joinToString { ProControls.awbLabel(it) }.ifEmpty { "только авто" }}")
                appendLine("Фокусные расстояния: ${c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString() ?: "н/д"}")
                val s = caps.selectorFor(lens.id)
                if (caps.provider != null) {
                    val qualities = caps.videoQualities(s)
                    appendLine("Видео (SDR): ${qualities.joinToString { CameraLabels.quality(it) }.ifEmpty { "н/д" }}")
                    val formats = caps.photoFormats(s)
                    appendLine("CameraX photo: ${formats.sorted().joinToString { CameraLabels.photoFormat(it) }}")
                }
                if (caps.extensions != null) {
                    val extNames = listOf(
                        ExtensionMode.HDR to "HDR",
                        ExtensionMode.NIGHT to "NIGHT",
                        ExtensionMode.BOKEH to "PORTRAIT"
                    ).filter { (mode, _) -> caps.isExtensionAvailable(s, mode) }.map { it.second }
                    appendLine("OEM extensions: ${extNames.ifEmpty { listOf("нет") }.joinToString()}")
                } else appendLine("OEM extensions: инициализация недоступна")
            } catch (e: Exception) {
                appendLine("Ошибка чтения: ${e.localizedMessage}")
            }
        }
        appendLine("\nПримечание: недоступность расширения не означает отсутствия HDR у штатной камеры.")
        appendLine("DNG и Ultra HDR требуют аппаратной поддержки и проверки на устройстве.")
    }
}
