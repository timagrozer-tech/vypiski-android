package ru.scanner291.core.camera

import android.content.Context
import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Состояние камеры для UI. */
enum class CameraStatus { Starting, Running, NoCamera, Failed }

/** Режимы вспышки (README CameraControls): выкл., авто, вкл., фонарик (подсветка без вспышки). */
enum class FlashMode(val label: String) { Off("Выкл."), Auto("Авто"), On("Вкл."), Torch("Фонарик") }

/** Следующий режим вспышки по кругу. */
fun FlashMode.next(): FlashMode = FlashMode.entries[(ordinal + 1) % FlashMode.entries.size]

/**
 * Камера (CameraX): превью, съёмка в файл с максимальным качеством, вспышка, фонарик, фокус по касанию.
 * Анализ кадров (поиск документа, автосъёмка) подключается на этапе 6.
 * Привязка к жизненному циклу экрана: камера освобождается при уходе с экрана.
 */
@Stable
class CameraController internal constructor(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set
    var status: CameraStatus by mutableStateOf(CameraStatus.Starting)
        private set
    var flash: FlashMode by mutableStateOf(FlashMode.Off)
        private set
    var hasFlash: Boolean by mutableStateOf(false)
        private set
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    private val preview = Preview.Builder().build().apply {
        setSurfaceProvider { request -> surfaceRequest = request }
    }

    /** Максимальное качество: документ важнее скорости затвора. */
    private val capture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
        .build()

    fun start() {
        scope.launch {
            try {
                val p = ProcessCameraProvider.getInstance(context.applicationContext).await()
                provider = p
                if (!p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    status = CameraStatus.NoCamera
                    return@launch
                }
                p.unbindAll()
                val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                camera = cam
                hasFlash = cam.cameraInfo.hasFlashUnit()
                status = CameraStatus.Running
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось запустить камеру", e)
                status = CameraStatus.Failed
            }
        }
    }

    fun changeFlash(mode: FlashMode) {
        flash = mode
        capture.flashMode = when (mode) {
            FlashMode.Off, FlashMode.Torch -> ImageCapture.FLASH_MODE_OFF
            FlashMode.Auto -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.On -> ImageCapture.FLASH_MODE_ON
        }
        camera?.cameraControl?.enableTorch(mode == FlashMode.Torch)
    }

    /** Фокус и экспозиция по точке касания: [surfacePoint] — в координатах кадра камеры. */
    fun focus(surfacePoint: Offset) {
        val cam = camera ?: return
        val res = surfaceRequest?.resolution ?: return
        val factory = SurfaceOrientedMeteringPointFactory(res.width.toFloat(), res.height.toFloat())
        val point = surfacePoint
        val action = FocusMeteringAction.Builder(factory.createPoint(point.x, point.y))
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    /** Снимок в файл. JPEG пишется CameraX целиком; EXIF (ориентация, время) сохраняется. */
    suspend fun takePicture(target: File): File = suspendCancellableCoroutine { cont ->
        val options = ImageCapture.OutputFileOptions.Builder(target).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) { cont.resume(target) }
            override fun onError(exception: ImageCaptureException) { cont.resumeWithException(exception) }
        })
    }

    fun release() {
        runCatching { camera?.cameraControl?.enableTorch(false) }
        runCatching { provider?.unbind(preview, capture) }
        scope.cancel()
    }

    private companion object { const val TAG = "Scanner291Camera" }
}

@Composable
fun rememberCameraController(): CameraController {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember(context, owner) { CameraController(context, owner) }
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.release() }
    }
    return controller
}

/**
 * Изображение с камеры на всю площадь (edge-to-edge); касание — фокус. Координаты касания
 * переводятся в координаты кадра через преобразователь видоискателя (как в android/camera-samples).
 */
@Composable
fun CameraPreview(controller: CameraController, modifier: Modifier = Modifier, onTap: ((Offset) -> Unit)? = null) {
    val request = controller.surfaceRequest ?: return
    val transformer = remember { MutableCoordinateTransformer() }
    CameraXViewfinder(
        surfaceRequest = request,
        coordinateTransformer = transformer,
        modifier = modifier.pointerInput(controller, request) {
            detectTapGestures { pos ->
                controller.focus(with(transformer) { pos.transform() })
                onTap?.invoke(pos)
            }
        },
    )
}
