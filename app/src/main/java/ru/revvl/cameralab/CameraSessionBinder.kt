package ru.revvl.cameralab

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.util.Size
import java.util.concurrent.Executor
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.lifecycle.LifecycleOwner

/**
 * Builds CameraX use cases and binds them to the lifecycle. Holds no UI and no policy:
 * decisions come from [SessionPlanner]; fallbacks and messages stay in the activity.
 * Moved from MainActivity.bindCamera (v0.3.1) without behavioural changes.
 * Every bind throws on an unsupported stream combination; the caller handles fallback.
 */
class CameraSessionBinder(private val owner: LifecycleOwner) {

    class Bound(
        val camera: Camera,
        val imageCapture: ImageCapture? = null,
        val videoCapture: VideoCapture<Recorder>? = null
    )

    fun bindVideo(
        provider: ProcessCameraProvider,
        selector: CameraSelector,
        surfaceProvider: Preview.SurfaceProvider,
        quality: Quality,
        rotation: Int?
    ): Bound {
        provider.unbindAll()
        // 16:9 preview so the viewfinder shows what FHD/UHD/HD video records (WYSIWYG).
        val preview = preview(surfaceProvider, AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        val q = QualitySelector.fromOrderedList(
            listOf(quality, Quality.FHD, Quality.HD, Quality.SD).distinct(),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        )
        val recorder = Recorder.Builder().setQualitySelector(q).build()
        val video = VideoCapture.withOutput(recorder)
        rotation?.let { video.targetRotation = it }
        val camera = provider.bindToLifecycle(owner, selector, preview, video)
        return Bound(camera, videoCapture = video)
    }

    fun bindPhoto(
        provider: ProcessCameraProvider,
        selector: CameraSelector,
        surfaceProvider: Preview.SurfaceProvider,
        plan: PhotoSessionPlan,
        extensionMode: Int,
        extensions: ExtensionsManager?,
        rotation: Int?,
        /** Optional live-histogram analyzer: small YUV stream, latest frame only. */
        analyzer: ImageAnalysis.Analyzer? = null,
        analyzerExecutor: Executor? = null
    ): Bound {
        provider.unbindAll()
        // 4:3 preview matches the default 4:3 still output (and the 3:4 viewfinder box).
        val preview = preview(surfaceProvider, AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        val builder = ImageCapture.Builder()
            .setCaptureMode(if (plan.minimizeLatency) ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                else ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(plan.jpegQuality)
            .setFlashMode(plan.captureFlashMode)
            .setOutputFormat(plan.outputFormat)
        rotation?.let { builder.setTargetRotation(it) }
        val capture = builder.build()
        // CameraX 1.6: the stable way to enable an OEM extension is an
        // extension-enabled selector: works with any use-case list (incl. ImageAnalysis) in 1.6.
        // ExtensionSessionConfig is also available in 1.6 and replaces this in 1.7 (deprecated there).
        val bindSelector = if (plan.useExtension && extensions != null) {
            extensions.getExtensionEnabledCameraSelector(selector, extensionMode)
        } else selector
        val analysis = if (analyzer != null && analyzerExecutor != null && !plan.useExtension) {
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build())
                .build()
                .also { it.setAnalyzer(analyzerExecutor, analyzer) }
        } else null
        val camera = if (analysis != null) {
            provider.bindToLifecycle(owner, bindSelector, preview, capture, analysis)
        } else {
            provider.bindToLifecycle(owner, bindSelector, preview, capture)
        }
        return Bound(camera, imageCapture = capture)
    }

    private fun preview(surfaceProvider: Preview.SurfaceProvider, aspect: AspectRatioStrategy): Preview =
        Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
            .build()
            .also { it.setSurfaceProvider(surfaceProvider) }
}
