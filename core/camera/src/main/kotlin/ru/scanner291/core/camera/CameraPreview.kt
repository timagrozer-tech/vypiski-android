package ru.scanner291.core.camera

import android.content.Context
import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** Состояние камеры для UI. */
enum class CameraStatus { Starting, Running, NoCamera, Failed }

/**
 * Превью задней камеры (CameraX). Съёмка, анализ кадров и вспышка добавляются на этапах 5–6
 * в этот же контроллер. Привязка к жизненному циклу экрана: камера освобождается при уходе с экрана.
 */
@Stable
class CameraPreviewController internal constructor(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set
    var status: CameraStatus by mutableStateOf(CameraStatus.Starting)
        private set
    private var provider: ProcessCameraProvider? = null

    private val preview = Preview.Builder().build().apply {
        setSurfaceProvider { request -> surfaceRequest = request }
    }

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
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                status = CameraStatus.Running
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось запустить камеру", e)
                status = CameraStatus.Failed
            }
        }
    }

    fun release() {
        runCatching { provider?.unbind(preview) }
        scope.cancel()
    }

    private companion object { const val TAG = "Scanner291Camera" }
}

@Composable
fun rememberCameraPreviewController(): CameraPreviewController {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember(context, owner) { CameraPreviewController(context, owner) }
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.release() }
    }
    return controller
}

/** Изображение с камеры на всю площадь (edge-to-edge). */
@Composable
fun CameraPreview(controller: CameraPreviewController, modifier: Modifier = Modifier) {
    val request = controller.surfaceRequest ?: return
    CameraXViewfinder(surfaceRequest = request, modifier = modifier)
}
