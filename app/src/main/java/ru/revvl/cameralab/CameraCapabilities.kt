package ru.revvl.cameralab

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import java.util.Locale

/** A public camera exposed to CameraX; [id] is the Camera2 ID. */
data class Lens(val id: String, val front: Boolean, val label: String, val focalMm: Float? = null)

/** Ranges for the PRO dialog; present only when MANUAL_SENSOR is declared. */
data class ManualSensorInfo(val minIso: Int, val maxIso: Int, val minExposureNs: Long, val maxExposureNs: Long)

/**
 * Capability registry: every answer is queried from CameraX/Camera2 at runtime, never inferred
 * from the chipset. All queries are defensive and return "not available" on failure.
 * Code moved from MainActivity (v0.3.1) without behavioural changes.
 */
@OptIn(ExperimentalCamera2Interop::class)
class CameraCapabilities(context: Context) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    var provider: ProcessCameraProvider? = null
    var extensions: ExtensionsManager? = null

    fun characteristics(id: String): CameraCharacteristics? =
        runCatching { manager.getCameraCharacteristics(id) }.getOrNull()

    /** Public lenses sorted back-first, then by ID. */
    fun discoverLenses(): List<Lens> {
        val p = provider ?: return emptyList()
        val lenses = mutableListOf<Lens>()
        for (info in p.availableCameraInfos) {
            try {
                val id = Camera2CameraInfo.from(info).cameraId
                val c = manager.getCameraCharacteristics(id)
                val front = c.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
                val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.firstOrNull()
                val label = if (front) "Селфи · $id"
                    else "Задняя · $id" + if (focal != null) " · ${"%.1f".format(Locale.US, focal)}мм" else ""
                lenses.add(Lens(id, front, label, focal))
            } catch (_: Exception) {
                // Some OEM cameras may not expose a Camera2-compatible ID.
            }
        }
        return lenses.sortedWith(compareBy<Lens> { it.front }.thenBy { it.id })
    }

    fun selectorFor(id: String?): CameraSelector {
        if (id == null) return CameraSelector.DEFAULT_BACK_CAMERA
        return CameraSelector.Builder().addCameraFilter { infos ->
            infos.filter { info ->
                try { Camera2CameraInfo.from(info).cameraId == id }
                catch (_: Exception) { false }
            }
        }.build()
    }

    fun isExtensionAvailable(selector: CameraSelector, mode: Int): Boolean =
        mode == ExtensionMode.NONE || runCatching {
            extensions?.isExtensionAvailable(selector, mode) == true
        }.getOrDefault(false)

    fun photoFormats(selector: CameraSelector): Set<Int> {
        val info = runCatching { provider?.getCameraInfo(selector) }.getOrNull()
            ?: return setOf(ImageCapture.OUTPUT_FORMAT_JPEG)
        return runCatching { ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats }
            .getOrDefault(setOf(ImageCapture.OUTPUT_FORMAT_JPEG))
    }

    fun videoQualities(selector: CameraSelector): List<Quality> {
        val info = runCatching { provider?.getCameraInfo(selector) }.getOrNull() ?: return emptyList()
        return runCatching {
            Recorder.getVideoCapabilities(info).getSupportedQualities(DynamicRange.SDR)
        }.getOrDefault(emptyList())
    }

    fun hasFlash(selector: CameraSelector): Boolean =
        runCatching { provider?.getCameraInfo(selector)?.hasFlashUnit() == true }.getOrDefault(false)

    /** PRO tool support for one lens, straight from Camera2 characteristics. */
    fun proSupport(id: String?): ProSupport {
        val c = id?.let { characteristics(it) } ?: return ProSupport()
        return runCatching {
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val manual = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps
            ProSupport(
                manualSensor = manual,
                minFocusDiopters = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
                focusCalibrated = c.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)
                    .let { it != null && it != CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED },
                aeLock = c.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true,
                awbLock = c.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true,
                awbModes = c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toSet() ?: emptySet()
            )
        }.getOrDefault(ProSupport())
    }

    fun manualSensor(id: String): ManualSensorInfo? {
        val c = characteristics(id) ?: return null
        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR !in caps) return null
        val iso = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: return null
        val exp = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: return null
        return ManualSensorInfo(iso.lower, iso.upper, exp.lower, exp.upper)
    }
}
