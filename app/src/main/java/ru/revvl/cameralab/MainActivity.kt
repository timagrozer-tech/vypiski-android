package ru.revvl.cameralab

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.Gravity
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * REVVL Camera Lab. Hardware-aware CameraX camera app.
 * Each feature is queried at runtime rather than assumed from the chipset.
 * Unavailable OEM extension modes never masquerade as Google's HDR+ algorithm.
 */
@OptIn(ExperimentalCamera2Interop::class)
class MainActivity : ComponentActivity() {

    companion object {
        private const val WHITE = CameraStyle.WHITE

        /** Finalize codes that still leave a playable file (CameraX keeps the output). */
        private val VIDEO_KEPT_ERRORS = setOf(
            VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,
            VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
            VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE
        )

        private const val STATE_LENS = "lens"
        private const val STATE_VIDEO = "video"
        private const val STATE_EXTENSION = "extension"
        private const val STATE_BURST = "burst"
        private const val STATE_QUALITY = "quality"
        private const val STATE_FLASH = "flash"
    }

    private val caps by lazy { CameraCapabilities(this) }
    private val binder by lazy { CameraSessionBinder(this) }

    /** v2 viewfinder layout; the fields below are aliases into it. */
    private lateinit var screen: CameraScreen
    private lateinit var previewView: PreviewView
    private lateinit var statusView: TextView
    private lateinit var zoomBar: SeekBar
    private lateinit var exposureBar: SeekBar
    private lateinit var exposureLabel: TextView
    private lateinit var shutterButton: ShutterView
    private lateinit var photoButton: TextView
    private lateinit var videoButton: TextView
    private lateinit var flashButton: TextView
    private lateinit var qualityButton: TextView
    private lateinit var proButton: TextView
    private lateinit var formatButton: TextView
    private lateinit var timerButton: TextView
    private lateinit var gridButton: TextView
    private lateinit var gridOverlay: GridOverlay
    /** Seconds left on the self-timer (0 = no countdown); drives the digit in the shutter. */
    private var timerLeft = 0
    private var burstFramesDone = 0
    private var burstProcessing = false
    private var pinchActive = false
    /** Zoom ratio tracked during a pinch: ZoomState LiveData lags behind setZoomRatio calls. */
    private var pinchRatio = 1f
    private val thumbExecutor = Executors.newSingleThreadExecutor()

    /** Owned by the capability registry so every capability query sees the same instances. */
    private var cameraProvider: ProcessCameraProvider?
        get() = caps.provider
        set(value) { caps.provider = value }
    private var extensions: ExtensionsManager?
        get() = caps.extensions
        set(value) { caps.extensions = value }
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private val lenses = mutableListOf<Lens>()
    private var currentLensId: String? = null
    private var isVideoMode = false
    private var extensionMode = ExtensionMode.NONE
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var videoQuality = Quality.FHD
    private var lastMediaUri: Uri? = null
        set(value) {
            field = value
            if (value != null) updateThumbnail(value)
        }
    private var manualIso: Int? = null
    private var manualExposureNs: Long? = null
    /** Manual focus in diopters (0 = ∞), null = autofocus. */
    private var manualFocusDiopters: Float? = null
    /** CONTROL_AWB_MODE preset, null = auto. */
    private var awbMode: Int? = null
    /** AE (+AWB) locked by a long press; AF lock comes from a non-cancelling metering action. */
    private var aeAwbLocked = false
    private var histogramEnabled = false
    /** Set when the camera rejected the extra analysis stream; histogram stays off for the session. */
    private var histogramRejected = false
    private var proSupport = ProSupport()
    private var probing = false
    /** The probe was interrupted by onStop: restore the user's session on the next onStart. */
    private var probeNeedsRebind = false
    private var lastProbe: List<ProbeResult> = emptyList()
    /** Incremented per probe run, so a delayed step of an interrupted run can never continue. */
    private var probeGeneration = 0
    private val log get() = AppLog.log
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    /** Accessed on [analysisExecutor] only. */
    private var lastHistogramNs = 0L
    private var initializing = false
    private var effectivePhotoFormat = ImageCapture.OUTPUT_FORMAT_JPEG
    private var requestedPhotoFormat = ImageCapture.OUTPUT_FORMAT_JPEG
    private var timerSeconds = 0
    private var gridEnabled = false
    private var photoBusy = false
    private var softwareBurstEnabled = false
    private var burstController: BurstCaptureController? = null
    /** A/B: also save the unmerged reference frame next to the СТЕК result. */
    private var burstAbEnabled = false
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private val prefs by lazy { getSharedPreferences("camera_prefs_v2", MODE_PRIVATE) }
    /** Surface.ROTATION_* derived from the physical device orientation, null until known. */
    private var deviceRotation: Int? = null
    private var orientationListener: OrientationEventListener? = null
    /** Extension mode restored after recreation; applied once ExtensionsManager is ready. */
    private var pendingExtensionMode = ExtensionMode.NONE

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        if (allowed) initializeCamera() else {
            statusView.text = "Нет доступа к камере · нажми «СНЯТЬ», чтобы запросить снова"
            log.warn("perm", "камера: отказ")
            message("Разрешение на камеру необходимо для съёмки")
        }
    }

    private val audioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        if (!allowed) message("Нет доступа к микрофону: видео будет записано без звука")
        if (isVideoMode && recording == null && videoCapture != null) startRecording()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15/16 enforce edge-to-edge for targetSdk >= 35; make older versions match.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { s ->
            currentLensId = s.getString(STATE_LENS)
            isVideoMode = s.getBoolean(STATE_VIDEO, false)
            pendingExtensionMode = s.getInt(STATE_EXTENSION, ExtensionMode.NONE)
            softwareBurstEnabled = s.getBoolean(STATE_BURST, false) && !isVideoMode
            videoQuality = CameraLabels.qualityFromName(s.getString(STATE_QUALITY)) ?: Quality.FHD
            flashMode = s.getInt(STATE_FLASH, ImageCapture.FLASH_MODE_OFF)
            if (isVideoMode || softwareBurstEnabled) pendingExtensionMode = ExtensionMode.NONE
        }
        requestedPhotoFormat = prefs.getInt("photo_format", ImageCapture.OUTPUT_FORMAT_JPEG)
        if (requestedPhotoFormat !in listOf(ImageCapture.OUTPUT_FORMAT_JPEG,
                ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR, ImageCapture.OUTPUT_FORMAT_RAW_JPEG)) {
            requestedPhotoFormat = ImageCapture.OUTPUT_FORMAT_JPEG
        }
        timerSeconds = CameraPolicy.validTimer(prefs.getInt("timer", 0))
        gridEnabled = prefs.getBoolean("grid", false)
        burstAbEnabled = prefs.getBoolean("burst_ab", false)
        histogramEnabled = prefs.getBoolean("histogram", false)
        buildInterface()
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                val rotation = CameraPolicy.surfaceRotationFor(orientation, deviceRotation) ?: return
                if (rotation == deviceRotation) return
                deviceRotation = rotation
                applyTargetRotation()
                if (::screen.isInitialized) screen.rotateIcons(rotation)
            }
        }
        if (hasPermission(Manifest.permission.CAMERA)) initializeCamera()
        else if (savedInstanceState == null) cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onStart() {
        super.onStart()
        orientationListener?.takeIf { it.canDetectOrientation() }?.enable()
        // Permission may have been granted from system settings while we were stopped.
        if (cameraProvider == null && hasPermission(Manifest.permission.CAMERA)) initializeCamera()
        if (probeNeedsRebind && cameraProvider != null) {
            probeNeedsRebind = false
            bindCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        // The torch is switched off whenever the camera closes (Home, screen lock);
        // CameraX re-opens the same session without our bindCamera(), so restore it here.
        if (isVideoMode && flashMode == ImageCapture.FLASH_MODE_ON && hasFlashUnit()) {
            camera?.cameraControl?.enableTorch(true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_LENS, currentLensId)
        outState.putBoolean(STATE_VIDEO, isVideoMode)
        outState.putInt(STATE_EXTENSION,
            if (extensionMode != ExtensionMode.NONE) extensionMode else pendingExtensionMode)
        outState.putBoolean(STATE_BURST, softwareBurstEnabled)
        outState.putString(STATE_QUALITY, qualityName(videoQuality))
        outState.putInt(STATE_FLASH, flashMode)
    }

    override fun onStop() {
        orientationListener?.disable()
        if (probing) {
            probing = false
            probeNeedsRebind = true
            log.warn("probe", "прервана сворачиванием")
        }
        cancelPhotoTimer()
        abortBurst("СТЕК ×3 прерван · файл не сохранён")
        // CameraX drops a metering lock when the camera closes; do not keep showing 🔒.
        if (aeAwbLocked) unlockAeAf()
        // Recording is intentionally foreground-only; no background camera service.
        // CameraX normally finalizes it already (ON_STOP precedes onStop) with
        // ERROR_SOURCE_INACTIVE and a playable file; stop() is a harmless safety net.
        recording?.stop()
        // Keep the handle until VideoRecordEvent.Finalize confirms completion.
        super.onStop()
    }

    override fun onDestroy() {
        orientationListener?.disable()
        orientationListener = null
        timerHandler.removeCallbacksAndMessages(null)
        burstController?.cancel()
        burstController = null
        thumbExecutor.shutdown()
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    /** Updates output rotation, but never in the middle of a burst (frame sizes must match). */
    private fun applyTargetRotation() {
        val rotation = deviceRotation ?: return
        if (!photoBusy) imageCapture?.targetRotation = rotation
        // A rotation change during recording is ignored by CameraX until the next clip.
        if (recording == null) videoCapture?.targetRotation = rotation
    }

    private fun hasFlashUnit(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun dp(n: Int) = (resources.displayMetrics.density * n + .5f).toInt()

    private fun vText(text: String, size: Float = 14f, color: Int = WHITE): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }

    private fun buildInterface() {
        screen = CameraScreen(this, CameraScreen.Actions(
            onShutter = { onShutterPressed() },
            onCancel = { cancelCapture() },
            onGallery = { openLastMedia() },
            onLensNext = { nextLens() },
            onLensPick = { pickLens() },
            onPhotoMode = { switchMode(false) },
            onVideoMode = { switchMode(true) },
            onFlash = { cycleFlash() },
            onTimer = { cycleTimer() },
            onQuality = { chooseQuality() },
            onGrid = { toggleGrid() },
            onFormat = { choosePhotoFormat() },
            onPro = { showProMenu() },
            onInfo = { showDiagnostics() },
            onUnlock = { unlockAeAf() },
            onZoom = { value -> camera?.cameraControl?.setLinearZoom(value / 100f) },
            onExposure = { value -> onExposureSlider(value) }
        ))
        setContentView(screen.root)
        previewView = screen.preview
        statusView = screen.status
        zoomBar = screen.zoomBar
        exposureBar = screen.exposureBar
        exposureLabel = screen.exposureLabel
        shutterButton = screen.shutter
        photoButton = screen.photoTab
        videoButton = screen.videoTab
        flashButton = screen.flashButton
        qualityButton = screen.qualityButton
        proButton = screen.proButton
        formatButton = screen.formatButton
        timerButton = screen.timerButton
        gridButton = screen.gridButton
        gridOverlay = screen.grid
        gridOverlay.isEnabledForPreview = gridEnabled
        screen.setPreviewAspect(isVideoMode)
        renderControls()

        // Pinch = zoom ratio within the camera's real range; single tap = focus/metering.
        val pinch = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pinchActive = true
                val zoom = camera?.cameraInfo?.zoomState?.value ?: return false
                pinchRatio = zoom.zoomRatio
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val c = camera ?: return false
                val zoom = c.cameraInfo.zoomState.value ?: return false
                val ratio = (pinchRatio * detector.scaleFactor).coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
                pinchRatio = ratio
                c.cameraControl.setZoomRatio(ratio)
                zoomBar.progress = (CameraText.linearZoom(ratio, zoom.minZoomRatio, zoom.maxZoomRatio) * 100).toInt()
                return true
            }
        })
        // Tap = focus/metering (releases an AE/AF lock); long press = AE/AF lock.
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!pinchActive) tapToFocus(e.x, e.y)
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (!pinchActive) lockAeAf(e.x, e.y)
            }
        })
        previewView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) pinchActive = false
            pinch.onTouchEvent(event)
            gestures.onTouchEvent(event)
            true
        }
    }

    private fun onShutterPressed() {
        if (probing) return message("Идёт проверка сочетаний — дождись окончания")
        if (!hasPermission(Manifest.permission.CAMERA)) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        shutterButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (isVideoMode) toggleRecording() else schedulePhoto()
    }

    private fun onExposureSlider(value: Int) {
        val state = camera?.cameraInfo?.exposureState ?: return
        if (manualIso != null || !state.isExposureCompensationSupported) return
        val index = value + state.exposureCompensationRange.lower
        camera?.cameraControl?.setExposureCompensationIndex(index)
        val ev = index * state.exposureCompensationStep.toFloat()
        exposureLabel.text = CameraText.evLabel(ev)
    }

    /** Volume keys and the hardware camera key act as a shutter (one-hand use); repeats ignored. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_CAMERA) {
            if (event.repeatCount == 0 && shutterButton.isEnabled) onShutterPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Explicit cancel for the self-timer and СТЕК ×3 (previously only "Home" stopped them). */
    private fun cancelCapture() {
        if (timerRunnable != null) {
            cancelPhotoTimer()
            statusView.text = "Таймер отменён"
            refreshCaptureUi()
            return
        }
        if (photoBusy && softwareBurstEnabled) abortBurst("СТЕК ×3 отменён · файл не сохранён")
    }

    private fun abortBurst(statusText: String) {
        burstController?.cancel()
        burstController = null
        if (photoBusy && softwareBurstEnabled) {
            photoBusy = false
            shutterButton.isEnabled = true
            statusView.text = statusText
        }
        burstFramesDone = 0
        burstProcessing = false
        applyTargetRotation()
        refreshCaptureUi()
    }

    /** Shutter look, countdown digit, burst ring and the cancel control. */
    private fun refreshCaptureUi() {
        if (!::screen.isInitialized) return
        val m = UiModel.shutter(isVideoMode, recording != null, photoBusy, softwareBurstEnabled,
            timerLeft, burstFramesDone, burstProcessing)
        shutterButton.mode = m.mode
        shutterButton.progress = m.progress
        shutterButton.label = m.label
        screen.cancelButton.visibility = if (m.cancellable) View.VISIBLE else View.GONE
    }

    private fun updateThumbnail(uri: Uri) {
        // Late CameraX callbacks (photo saved, video finalized) can arrive after onDestroy,
        // when the executor is already shut down: never throw on the main thread for that.
        if (isDestroyed || thumbExecutor.isShutdown) return
        val size = dp(112)
        runCatching { thumbExecutor.execute {
            // DNG or a just-finalized file may have no thumbnail yet: show the placeholder.
            val bitmap = runCatching { contentResolver.loadThumbnail(uri, Size(size, size), null) }.getOrNull()
            runOnUiThread { if (!isDestroyed && ::screen.isInitialized) screen.setThumbnail(bitmap) }
        } }
    }

    private fun initializeCamera() {
        if (initializing || cameraProvider != null) return
        initializing = true
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (isDestroyed) {
                initializing = false
                return@addListener
            }
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                discoverLenses()
                initializing = false
                // Faster start: open a plain preview now; OEM extensions are queried in parallel.
                bindCamera()
                val extFuture = ExtensionsManager.getInstanceAsync(this, provider)
                extFuture.addListener({
                    if (isDestroyed) return@addListener
                    extensions = try { extFuture.get() } catch (_: Exception) { null }
                    val restored = pendingExtensionMode
                    pendingExtensionMode = ExtensionMode.NONE
                    if (restored != ExtensionMode.NONE && !isVideoMode && !softwareBurstEnabled &&
                        manualIso == null && isExtensionAvailable(selector(), restored)) {
                        extensionMode = restored
                        bindCamera()
                    } else {
                        renderControls()
                    }
                }, ContextCompat.getMainExecutor(this))
            } catch (e: Exception) {
                initializing = false
                cameraProvider = null
                log.error("init", "${e.javaClass.simpleName}: ${e.localizedMessage}")
                message("Ошибка инициализации: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun discoverLenses() {
        lenses.clear()
        lenses.addAll(caps.discoverLenses())
        if (lenses.none { it.id == currentLensId }) currentLensId = lenses.firstOrNull()?.id
        renderLensBar()
    }

    private fun selector(): CameraSelector = caps.selectorFor(currentLensId)

    /** Single source for "something is in progress" (timer, photo, burst, recording). */
    private fun uiState(): CameraUiState = CameraUiState.from(
        hasPermission = hasPermission(Manifest.permission.CAMERA),
        initializing = initializing,
        hasCamera = camera != null,
        recording = recording != null,
        photoBusy = photoBusy,
        burst = softwareBurstEnabled,
        countdown = timerRunnable != null,
        probing = probing
    )

    private fun isBusy(): Boolean = uiState().isBusy

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val selected = selector()
        if (isBusy()) return
        // A CameraX focus lock does not survive a rebind; never show a stale "locked" badge.
        if (aeAwbLocked) {
            aeAwbLocked = false
            if (::screen.isInitialized) screen.lockBadge.visibility = View.GONE
        }
        proSupport = caps.proSupport(currentLensId)
        var usedAnalysis = false
        val bindStarted = SystemClock.elapsedRealtime()
        try {
            camera = null
            videoCapture = null
            imageCapture = null
            val surface = previewView.surfaceProvider
            if (isVideoMode) {
                val bound = binder.bindVideo(provider, selected, surface, videoQuality, deviceRotation)
                videoCapture = bound.videoCapture
                camera = bound.camera
                flashMode = CameraPolicy.normalizeFlashMode(flashMode, true, hasFlashUnit(),
                    ImageCapture.FLASH_MODE_OFF, ImageCapture.FLASH_MODE_AUTO, ImageCapture.FLASH_MODE_ON)
                // Torch state is lost on every rebind; restore what the button shows.
                if (flashMode == ImageCapture.FLASH_MODE_ON) bound.camera.cameraControl.enableTorch(true)
            } else {
                // RAW+JPEG and OEM extensions are separate paths. Do not promise their combination.
                val plan = SessionPlanner.photo(PhotoSessionRequest(
                    requestedFormat = requestedPhotoFormat,
                    supportedFormats = caps.photoFormats(selected),
                    extensionRequested = extensionMode != ExtensionMode.NONE,
                    extensionAvailable = extensions != null && caps.isExtensionAvailable(selected, extensionMode),
                    burst = softwareBurstEnabled,
                    manualExposure = manualIso != null,
                    flashMode = flashMode,
                    hasFlash = caps.hasFlash(selected)
                ), CameraLabels.CAPTURE_CONSTANTS)
                if (!plan.useExtension) extensionMode = ExtensionMode.NONE
                effectivePhotoFormat = plan.outputFormat
                flashMode = plan.flashMode
                val wantHistogram = histogramEnabled && !histogramRejected && ProControls.histogramAllowed(
                    false, plan.useExtension, softwareBurstEnabled, plan.outputFormat, ImageCapture.OUTPUT_FORMAT_JPEG)
                usedAnalysis = wantHistogram
                val bound = binder.bindPhoto(provider, selected, surface, plan,
                    extensionMode, extensions, deviceRotation,
                    if (wantHistogram) histogramAnalyzer else null, analysisExecutor)
                imageCapture = bound.imageCapture
                camera = bound.camera
            }
            camera?.cameraControl?.setLinearZoom(zoomBar.progress / 100f)
            if (::screen.isInitialized) {
                screen.showHistogram(usedAnalysis)
                if (!usedAnalysis) screen.histogram.update(null)
            }
            if (extensionMode == ExtensionMode.NONE) applyRequestOptions()
            configureExposureBar()
            renderControls()
            val bindMs = SystemClock.elapsedRealtime() - bindStarted
            log.latency("bind", bindMs)
            log.info("bind", "камера ${currentLensId ?: "?"} · " + (if (isVideoMode) "видео ${qualityName(videoQuality)}"
                else "${formatName(effectivePhotoFormat)} ext=$extensionMode стек=$softwareBurstEnabled гист=$usedAnalysis") +
                " · $bindMs мс")
            statusView.text = if (isVideoMode) "ВИДЕО · ${qualityName(videoQuality)}"
                else if (softwareBurstEnabled) "ФОТО · СТЕК ×3 (JPEG ДО 2304 PX)"
                else when (extensionMode) {
                    ExtensionMode.HDR -> "ФОТО · HDR (OEM)"
                    ExtensionMode.NIGHT -> "ФОТО · НОЧЬ (OEM)"
                    ExtensionMode.BOKEH -> "ФОТО · ПОРТРЕТ (OEM)"
                    else -> "ФОТО · ${formatName(effectivePhotoFormat)}" +
                        (if (manualIso != null) " · PRO ISO $manualIso" else "") +
                        (if (manualFocusDiopters != null && proSupport.manualFocus) " · MF" else "") +
                        (if (awbMode != null) " · WB ${ProControls.awbLabel(awbMode)}" else "")
                }
        } catch (e: Exception) {
            camera = null
            log.error("bind", "камера ${currentLensId ?: "?"} видео=$isVideoMode формат=${formatName(requestedPhotoFormat)} " +
                "ext=$extensionMode гист=$usedAnalysis: ${e.javaClass.simpleName}: ${e.localizedMessage}")
            statusView.text = "Не удалось подключить объектив: ${e.localizedMessage}"
            // On unsupported stream combinations: first drop the optional histogram stream,
            // then fall back to basic JPEG, then leave the OEM extension.
            if (usedAnalysis) {
                histogramRejected = true
                message("Камера не принимает поток гистограммы вместе с этим режимом — гистограмма выключена")
                bindCamera()
            } else if (!isVideoMode && extensionMode == ExtensionMode.NONE &&
                requestedPhotoFormat != ImageCapture.OUTPUT_FORMAT_JPEG) {
                requestedPhotoFormat = ImageCapture.OUTPUT_FORMAT_JPEG
                prefs.edit().putInt("photo_format", requestedPhotoFormat).apply()
                message("Расширенный формат отклонён камерой, выбран обычный JPEG")
                bindCamera()
            } else if (!isVideoMode && extensionMode != ExtensionMode.NONE) {
                extensionMode = ExtensionMode.NONE
                message("Расширение камеры не запустилось, возврат к обычной съёмке")
                bindCamera()
            } else message("Не удалось открыть камеру. Проверь диагностику или другой объектив")
        }
    }

    private fun isExtensionAvailable(selected: CameraSelector, mode: Int): Boolean =
        caps.isExtensionAvailable(selected, mode)

    private fun formatName(format: Int): String = CameraLabels.photoFormat(format)

    private fun qualityName(q: Quality): String = CameraLabels.quality(q)

    private fun configureExposureBar() {
        val state = camera?.cameraInfo?.exposureState
        val valid = state?.isExposureCompensationSupported == true && manualIso == null
        val range = state?.exposureCompensationRange
        exposureBar.isEnabled = valid
        if (valid && range != null) {
            // Show the camera's real index instead of assuming that a rebind reset it to 0.
            val index = state.exposureCompensationIndex.coerceIn(range.lower, range.upper)
            exposureBar.max = range.upper - range.lower
            exposureBar.progress = index - range.lower
            exposureLabel.text = CameraText.evLabel(index * state.exposureCompensationStep.toFloat())
        } else {
            exposureBar.max = 0
            exposureBar.progress = 0
            exposureLabel.text = if (manualIso != null) "ISO" else "EV  —"
        }
    }

    private fun renderLensBar() {
        if (!::screen.isInitialized) return
        val index = lenses.indexOfFirst { it.id == currentLensId }
        val lens = lenses.getOrNull(index)
        if (lens == null) {
            screen.setLens("…", null, null)
        } else {
            screen.setLens(CameraText.lensShort(lens.front, lens.focalMm, lens.id),
                if (lenses.size > 1) "${index + 1}/${lenses.size}" else null, lens.label)
        }
        screen.setEnabledLook(screen.lensButton, lenses.size > 1 && !isBusy())
    }

    private fun selectLens(id: String) {
        if (isBusy()) {
            message("Дождись окончания съёмки перед переключением")
            return
        }
        if (id == currentLensId) return
        currentLensId = id
        resetProForReconfigure()
        extensionMode = ExtensionMode.NONE
        softwareBurstEnabled = false
        zoomBar.progress = 0
        bindCamera()
    }

    private fun nextLens() {
        val next = CameraPolicy.nextLens(lenses.map { it.id }, currentLensId)
            ?: return message("Прошивка открывает приложению только этот объектив")
        selectLens(next)
    }

    /** Long press: full list with Camera2 IDs and focal lengths (as the v0.3 lens bar). */
    private fun pickLens() {
        if (lenses.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Объективы, доступные приложению")
            .setItems(lenses.map { (if (it.id == currentLensId) "● " else "") + it.label }.toTypedArray()) { _, i ->
                selectLens(lenses[i].id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun renderEffects() {
        screen.effectsBar.removeAllViews()
        if (isVideoMode) {
            screen.addEffectNote("Видео · качество — кнопка вверху, по возможностям объектива")
            return
        }
        val modes = CameraLabels.EXTENSION_CHOICES
        val currentSelector = selector()
        for ((mode, title) in modes) {
            val isAvailable = isExtensionAvailable(currentSelector, mode)
            val chip = screen.chip(if (isAvailable) title else "$title —", mode == extensionMode && !softwareBurstEnabled) {
                if (!isAvailable) {
                    message("Этот режим недоступен сторонним приложениям на выбранной камере")
                    return@chip
                }
                if (isBusy()) return@chip
                extensionMode = mode
                softwareBurstEnabled = false
                if (mode != ExtensionMode.NONE) {
                    resetProForReconfigure()
                }
                bindCamera()
            }
            // Unavailable modes stay tappable to explain why, but look disabled.
            chip.alpha = if (isAvailable) 1f else .45f
            screen.addEffect(chip)
        }
        val stack = screen.chip("СТЕК ×3", softwareBurstEnabled) {
            if (isBusy()) return@chip
            softwareBurstEnabled = !softwareBurstEnabled
            if (softwareBurstEnabled) {
                extensionMode = ExtensionMode.NONE
                resetProForReconfigure()
                flashMode = ImageCapture.FLASH_MODE_OFF
                message("3 кадра со снижением шума. Снимай неподвижную сцену")
            }
            bindCamera()
        }
        screen.addEffect(stack)
    }

    private fun renderControls() {
        if (!::screen.isInitialized) return
        screen.setTabActive(photoButton, !isVideoMode)
        screen.setTabActive(videoButton, isVideoMode)
        screen.setPreviewAspect(isVideoMode)
        refreshCaptureUi()
        val hasFlash = hasFlashUnit()
        val flashBlocked = !isVideoMode && (softwareBurstEnabled || manualIso != null ||
            effectivePhotoFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
        // Unavailable controls are dimmed and disabled; the label says why ("нет").
        screen.setEnabledLook(flashButton, hasFlash && !flashBlocked)
        flashButton.text = when {
            !hasFlash || flashBlocked -> "ВСП\nнет"
            isVideoMode -> if (flashMode == ImageCapture.FLASH_MODE_ON) "ФОНАРЬ\nON" else "ФОНАРЬ\nOFF"
            flashMode == ImageCapture.FLASH_MODE_AUTO -> "ВСП\nAUTO"
            flashMode == ImageCapture.FLASH_MODE_ON -> "ВСП\nON"
            else -> "ВСП\nOFF"
        }
        qualityButton.visibility = if (isVideoMode) View.VISIBLE else View.GONE
        qualityButton.text = "ВИДЕО\n${qualityName(videoQuality)}"
        proButton.visibility = View.VISIBLE // WB and AE/AF lock also work in video
        screen.setEnabledLook(proButton, true)
        // In СТЕК ×3 the format is fixed (JPEG); the same slot toggles the A/B reference.
        formatButton.isEnabled = true
        proButton.text = if (ProControls.isActive(proSettings())) "PRO ●" else "PRO"
        formatButton.visibility = if (isVideoMode) View.GONE else View.VISIBLE
        formatButton.text = if (softwareBurstEnabled) (if (burstAbEnabled) "A/B\nВКЛ" else "A/B\nВЫКЛ")
            else formatName(effectivePhotoFormat).replace(" + ", "\n+")
        timerButton.visibility = if (isVideoMode) View.GONE else View.VISIBLE
        timerButton.text = if (timerSeconds == 0) "ТАЙМЕР\nВЫКЛ" else "ТАЙМЕР\n${timerSeconds} с"
        gridButton.text = if (gridEnabled) "СЕТКА\nВКЛ" else "СЕТКА\nВЫКЛ"
        gridOverlay.isEnabledForPreview = gridEnabled
        renderLensBar()
        renderEffects()
    }

    private fun switchMode(video: Boolean) {
        if (isBusy() || isVideoMode == video) return
        isVideoMode = video
        resetProForReconfigure()
        if (video) {
            extensionMode = ExtensionMode.NONE
            softwareBurstEnabled = false
        }
        bindCamera()
    }

    private fun cycleFlash() {
        if (!hasFlashUnit()) return message("У выбранного объектива нет вспышки")
        if (!isVideoMode && softwareBurstEnabled) return message("В серии ×3 вспышка отключена")
        if (!isVideoMode && manualIso != null) return message("В ручном режиме PRO вспышка отключена")
        if (!isVideoMode && effectivePhotoFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG) {
            message("В режиме RAW + JPEG вспышка отключена")
            return
        }
        flashMode = CameraPolicy.nextFlashMode(flashMode, isVideoMode, true,
            ImageCapture.FLASH_MODE_OFF, ImageCapture.FLASH_MODE_AUTO, ImageCapture.FLASH_MODE_ON)
        if (isVideoMode) {
            // Video uses continuous torch, not an image-capture flash pulse.
            camera?.cameraControl?.enableTorch(flashMode == ImageCapture.FLASH_MODE_ON)
        } else {
            imageCapture?.flashMode = if (manualIso != null ||
                effectivePhotoFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
                ImageCapture.FLASH_MODE_OFF else flashMode
        }
        renderControls()
    }

    private fun choosePhotoFormat() {
        if (isVideoMode || isBusy()) return
        if (softwareBurstEnabled) return toggleBurstAb()
        if (extensionMode != ExtensionMode.NONE) {
            message("Выбери «Авто», чтобы открыть RAW и Ultra HDR")
            return
        }
        val supported = caps.photoFormats(selector())
        val choices = listOf(
            ImageCapture.OUTPUT_FORMAT_JPEG to "JPEG · совместимый формат",
            ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR to "Ultra HDR · JPEG с HDR-данными",
            ImageCapture.OUTPUT_FORMAT_RAW_JPEG to "RAW (DNG) + JPEG · для обработки"
        ).filter { it.first in supported }
        AlertDialog.Builder(this)
            .setTitle("Формат снимков")
            .setItems(choices.map { it.second }.toTypedArray()) { _, index ->
                requestedPhotoFormat = choices[index].first
                prefs.edit().putInt("photo_format", requestedPhotoFormat).apply()
                bindCamera()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun toggleBurstAb() {
        burstAbEnabled = !burstAbEnabled
        prefs.edit().putBoolean("burst_ab", burstAbEnabled).apply()
        message(if (burstAbEnabled) "A/B: рядом с СТЕК сохранится опорный кадр без обработки (_REF), тот же размер и сжатие"
            else "A/B выключен: сохраняется только результат СТЕК")
        renderControls()
    }

    private fun cycleTimer() {
        timerSeconds = CameraPolicy.nextTimer(timerSeconds)
        prefs.edit().putInt("timer", timerSeconds).apply()
        renderControls()
    }

    private fun toggleGrid() {
        gridEnabled = !gridEnabled
        prefs.edit().putBoolean("grid", gridEnabled).apply()
        renderControls()
    }

    private fun cancelPhotoTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
        timerLeft = 0
        if (::shutterButton.isInitialized && !photoBusy) shutterButton.isEnabled = true
    }

    private fun schedulePhoto() {
        if (photoBusy || timerRunnable != null) return
        if (imageCapture == null) return message("Камера пока недоступна")
        if (timerSeconds == 0) {
            takePhoto()
            return
        }
        var left = timerSeconds
        // Shutter is disabled during the countdown; the cancel button stops it.
        shutterButton.isEnabled = false
        val task = object : Runnable {
            override fun run() {
                if (left == 0) {
                    timerRunnable = null
                    timerLeft = 0
                    refreshCaptureUi()
                    takePhoto()
                } else {
                    statusView.text = "СНИМОК ЧЕРЕЗ ${left} С"
                    timerLeft = left
                    refreshCaptureUi()
                    left--
                    timerHandler.postDelayed(this, 1000L)
                }
            }
        }
        timerRunnable = task
        task.run()
    }


    private fun takePhoto() {
        val capture = imageCapture ?: return message("Камера пока недоступна")
        if (photoBusy) return
        if (softwareBurstEnabled) {
            takeBurstPhoto(capture)
            return
        }
        photoBusy = true
        shutterButton.isEnabled = false
        val shotStarted = SystemClock.elapsedRealtime()
        val filename = "REVVL_${CameraText.fileStamp(System.currentTimeMillis())}"
        val executor = ContextCompat.getMainExecutor(this)
        val jpegOptions = MediaOutputs.photo(contentResolver, "$filename.jpg", MediaOutputs.JPEG_MIME)
        val savingPair = effectivePhotoFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG
        var remaining = if (savingPair) 2 else 1
        var finalized = false
        var jpegUri: Uri? = null
        var dngUri: Uri? = null
        val callback = object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                if (finalized) return
                // Identify the file by the MIME type we inserted, independent of
                // OutputFileResults.imageFormat availability in this CameraX version.
                val uri = result.savedUri
                val isDng = savingPair && uri != null &&
                    runCatching { contentResolver.getType(uri) }.getOrNull() == MediaOutputs.DNG_MIME
                if (isDng) dngUri = uri else jpegUri = uri
                remaining--
                if (remaining <= 0) {
                    finalized = true
                    photoBusy = false
                    shutterButton.isEnabled = true
                    applyTargetRotation()
                    lastMediaUri = jpegUri ?: dngUri
                    val shotMs = SystemClock.elapsedRealtime() - shotStarted
                    log.latency(if (savingPair) "photo_raw" else "photo", shotMs)
                    log.info("photo", "${if (savingPair) "DNG + JPEG" else formatName(effectivePhotoFormat)} сохранено за $shotMs мс")
                    statusView.text = "Сохранено: ${if (savingPair) "DNG + JPEG" else formatName(effectivePhotoFormat)}"
                    message(if (savingPair) "DNG и JPEG сохранены" else "Фото сохранено в галерею")
                }
            }
            override fun onError(exception: ImageCaptureException) {
                if (finalized) return
                finalized = true
                photoBusy = false
                shutterButton.isEnabled = true
                applyTargetRotation()
                // In the RAW pair one file may already be saved; keep it reachable.
                (jpegUri ?: dngUri)?.let { lastMediaUri = it }
                log.error("photo", "код ${exception.imageCaptureError}: ${exception.localizedMessage}")
                statusView.text = "Ошибка фото: ${exception.localizedMessage}"
                message("Не удалось сохранить снимок: ${exception.localizedMessage}")
            }
        }
        try {
            if (savingPair) {
                // CameraX invokes callback twice; the order is not guaranteed.
                capture.takePicture(
                    MediaOutputs.photo(contentResolver, "$filename.dng", MediaOutputs.DNG_MIME),
                    jpegOptions, executor, callback
                )
            } else {
                capture.takePicture(jpegOptions, executor, callback)
            }
        } catch (e: Exception) {
            photoBusy = false
            shutterButton.isEnabled = true
            message("Ошибка запуска снимка: ${e.localizedMessage}")
        }
    }

    private fun takeBurstPhoto(capture: ImageCapture) {
        if (photoBusy || effectivePhotoFormat != ImageCapture.OUTPUT_FORMAT_JPEG) return
        photoBusy = true
        shutterButton.isEnabled = false
        val session = BurstCaptureController(
            context = applicationContext,
            capture = capture,
            onProgress = { statusView.text = it },
            onStage = { done, processing ->
                burstFramesDone = done
                burstProcessing = processing
                refreshCaptureUi()
            },
            saveReference = burstAbEnabled,
            onSuccess = { uri, refUri, result ->
                burstController = null
                photoBusy = false
                shutterButton.isEnabled = true
                burstFramesDone = 0
                burstProcessing = false
                refreshCaptureUi()
                applyTargetRotation()
                lastMediaUri = uri
                statusView.text = CameraText.burstSummary(result.usedFrames, result.frame.width,
                    result.frame.height, result.stats.noiseReduction, result.stats.elapsedMs)
                log.latency("burst_merge", result.stats.elapsedMs)
                log.info("burst", "кадров ${result.usedFrames}, сдвиги ${result.shifts}, " +
                    "экспозиция ${result.stats.gains.joinToString { "%.3f".format(java.util.Locale.US, it) }}, " +
                    "шум ${"%.2f".format(java.util.Locale.US, result.stats.noiseBefore)}→${"%.2f".format(java.util.Locale.US, result.stats.noiseAfter)}, " +
                    "${result.frame.width}×${result.frame.height}, ${result.stats.elapsedMs} мс, A/B=${refUri != null}")
                message(when {
                    !burstAbEnabled -> "Серия сохранена в JPEG; объединено кадров: ${result.usedFrames}"
                    refUri != null -> "Сохранены СТЕК и опорный кадр _REF для сравнения A/B"
                    else -> "СТЕК сохранён; опорный кадр для A/B сохранить не удалось"
                })
            },
            onFailure = { error ->
                burstController = null
                photoBusy = false
                shutterButton.isEnabled = true
                burstFramesDone = 0
                burstProcessing = false
                refreshCaptureUi()
                applyTargetRotation()
                log.error("burst", error)
                statusView.text = "Ошибка СТЕК ×3: $error"
                message(error)
            }
        )
        burstController = session
        burstFramesDone = 0
        burstProcessing = false
        refreshCaptureUi()
        session.start()
    }

    private fun toggleRecording() {
        if (recording != null) {
            recording?.stop()
            // Do not null the reference until Finalize arrives; prevents overlapping sessions.
            return
        }
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else startRecording()
    }

    private fun startRecording() {
        val video = videoCapture ?: return message("Видеокамера пока недоступна")
        if (recording != null) return
        val filename = "REVVL_${CameraText.fileStamp(System.currentTimeMillis())}.mp4"
        val options = MediaOutputs.video(contentResolver, filename)
        deviceRotation?.let { video.targetRotation = it }
        try {
            var pending = video.output.prepareRecording(this, options)
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                pending = pending.withAudioEnabled()
            }
            shutterButton.isEnabled = false
            recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        shutterButton.isEnabled = true
                        statusView.text = "● ИДЁТ ЗАПИСЬ  ${qualityName(videoQuality)}"
                        renderControls()
                    }
                    is VideoRecordEvent.Status -> {
                        val seconds = TimeUnit.NANOSECONDS.toSeconds(
                            event.recordingStats.recordedDurationNanos
                        )
                        statusView.text = "● ЗАПИСЬ ${CameraText.recordingClock(seconds)}"
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        shutterButton.isEnabled = true
                        val uri = event.outputResults.outputUri
                        val kept = CameraPolicy.isVideoKept(event.error,
                            VideoRecordEvent.Finalize.ERROR_NONE, VIDEO_KEPT_ERRORS, uri != Uri.EMPTY)
                        log.info("video", "finalize error=${event.error} сохранено=$kept")
                        if (kept) {
                            lastMediaUri = uri
                            val interrupted = event.error != VideoRecordEvent.Finalize.ERROR_NONE
                            statusView.text = if (interrupted) "Видео сохранено (запись прервана): $filename"
                                else "Сохранено видео: $filename"
                            message(if (interrupted) "Запись прервана, ролик сохранён" else "Видео сохранено в галерею")
                        } else {
                            statusView.text = "Ошибка видео: ${event.cause?.localizedMessage ?: event.error}"
                            message("Видео не сохранено")
                        }
                        applyTargetRotation()
                        renderControls()
                    }
                }
            }
        } catch (e: Exception) {
            recording = null
            shutterButton.isEnabled = true
            message("Запись не началась: ${e.localizedMessage}")
        }
    }

    private fun chooseQuality() {
        if (cameraProvider == null || !isVideoMode || recording != null) return
        val preferences = CameraLabels.VIDEO_CHOICES
        val supported = caps.videoQualities(selector())
        val available = preferences.filter { it.first in supported }
        if (available.isEmpty()) return message("Нет данных о доступных качествах")
        AlertDialog.Builder(this)
            .setTitle("Качество видеозаписи")
            .setItems(available.map { it.second }.toTypedArray()) { _, i ->
                videoQuality = available[i].first
                bindCamera()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }



    private fun showManualExposure() {
        if (isVideoMode || isBusy()) return
        if (softwareBurstEnabled) return message("Отключи СТЕК ×3 перед ручной экспозицией")
        val id = currentLensId ?: return
        val sensor = caps.manualSensor(id)
        if (sensor == null) {
            AlertDialog.Builder(this)
                .setTitle("PRO недоступен")
                .setMessage("Прошивка не открывает MANUAL_SENSOR для этой камеры. Можно использовать экспокоррекцию EV на главном экране.")
                .setPositiveButton("Понятно", null)
                .show()
            return
        }
        val shutterOptions = ManualExposure.shutterOptions(sensor.minExposureNs, sensor.maxExposureNs)
        if (shutterOptions.isEmpty()) return message("Не нашлось допустимых выдержек")

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(8))
        }
        var pickedIso = ManualExposure.initialIso(manualIso, sensor.minIso, sensor.maxIso)
        var pickedShutter = ManualExposure.initialShutter(manualExposureNs, shutterOptions)
        val isoTitle = vText("ISO $pickedIso", 15f, Color.BLACK)
        form.addView(isoTitle)
        val isoBar = SeekBar(this).apply {
            max = ManualExposure.ISO_STEPS
            progress = ManualExposure.isoToProgress(pickedIso, sensor.minIso, sensor.maxIso)
            setOnSeekBarChangeListener(simpleSeek { p ->
                pickedIso = ManualExposure.progressToIso(p, sensor.minIso, sensor.maxIso)
                isoTitle.text = "ISO $pickedIso"
            })
        }
        form.addView(isoBar)
        val shutterTitle = vText("Выдержка ${CameraText.formatShutter(pickedShutter)}", 15f, Color.BLACK)
        form.addView(shutterTitle)
        val shutterBar = SeekBar(this).apply {
            max = shutterOptions.size - 1
            progress = shutterOptions.indexOf(pickedShutter)
            setOnSeekBarChangeListener(simpleSeek { p ->
                pickedShutter = shutterOptions[p]
                shutterTitle.text = "Выдержка ${CameraText.formatShutter(pickedShutter)}"
            })
        }
        form.addView(shutterBar)
        AlertDialog.Builder(this)
            .setTitle("Ручная экспозиция · PRO")
            .setView(form)
            .setPositiveButton("Применить") { _, _ ->
                if (isBusy()) {
                    message("Дождись окончания съёмки")
                    return@setPositiveButton
                }
                manualIso = pickedIso
                manualExposureNs = pickedShutter
                if (extensionMode != ExtensionMode.NONE) {
                    // OEM extensions do not promise manual Camera2 requests; leave them only
                    // when the user actually applies manual exposure (v0.3 left on dialog open).
                    extensionMode = ExtensionMode.NONE
                    bindCamera() // applies manual settings after binding
                } else {
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                    applyRequestOptions()
                    configureExposureBar()
                    renderControls()
                }
            }
            .setNeutralButton("Вернуть авто") { _, _ ->
                manualIso = null
                manualExposureNs = null
                applyRequestOptions()
                configureExposureBar()
                renderControls()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /** Lens, mode, extension or СТЕК change: per-lens manual values and the lock no longer apply. */
    private fun resetProForReconfigure() {
        manualIso = null
        manualExposureNs = null
        manualFocusDiopters = null
        aeAwbLocked = false
        if (::screen.isInitialized) screen.lockBadge.visibility = View.GONE
    }

    private fun simpleSeek(callback: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seek: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) callback(progress)
        }
        override fun onStartTrackingTouch(seek: SeekBar?) = Unit
        override fun onStopTrackingTouch(seek: SeekBar?) = Unit
    }


    private fun proSettings() = ProSettings(manualIso, manualExposureNs, manualFocusDiopters, awbMode, aeAwbLocked)

    /**
     * Applies ALL PRO request options as one bundle: setCaptureRequestOptions replaces the
     * previous bundle, so ISO/shutter, WB, AE/AWB lock and manual focus are composed together
     * by [ProControls.plan] (v0.3.4 applied manual exposure alone).
     */
    private fun applyRequestOptions() {
        val c = camera ?: return
        if (extensionMode != ExtensionMode.NONE) return
        val plan = ProControls.plan(proSettings(), proSupport, isVideoMode, false, softwareBurstEnabled)
        try {
            val controller = Camera2CameraControl.from(c.cameraControl)
            if (plan.isEmpty) {
                controller.clearCaptureRequestOptions()
            } else {
                val b = CaptureRequestOptions.Builder()
                val iso = plan.iso
                val shutterNs = plan.exposureNs
                if (plan.aeModeOff && iso != null && shutterNs != null) {
                    b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
                    b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterNs)
                }
                if (plan.aeLock) b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                plan.awbMode?.let { b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, it) }
                if (plan.awbLock) b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                val focus = plan.focusDiopters
                if (plan.afModeOff && focus != null) {
                    b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focus)
                }
                controller.setCaptureRequestOptions(b.build())
            }
            imageCapture?.flashMode = if (softwareBurstEnabled || plan.aeModeOff ||
                effectivePhotoFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG) ImageCapture.FLASH_MODE_OFF else flashMode
            if (plan.aeModeOff && plan.iso != null) {
                statusView.text = "PRO · ISO ${plan.iso} · ${CameraText.formatShutter(plan.exposureNs ?: 1L)}"
            }
        } catch (e: Exception) {
            manualIso = null
            manualExposureNs = null
            manualFocusDiopters = null
            awbMode = null
            aeAwbLocked = false
            if (::screen.isInitialized) screen.lockBadge.visibility = View.GONE
            message("Параметры PRO отклонены камерой: ${e.localizedMessage}")
        }
    }

    // ---- focus, AE/AF lock

    private fun manualFocusActive(): Boolean =
        manualFocusDiopters != null && proSupport.manualFocus && !isVideoMode && !softwareBurstEnabled

    private fun meteringFlags(): Int = if (manualFocusActive())
        FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
    else FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB

    private fun tapToFocus(x: Float, y: Float) {
        val c = camera ?: return
        if (aeAwbLocked) unlockAeAf()
        val point = previewView.meteringPointFactory.createPoint(x, y)
        // With manual focus the tap only meters exposure/WB: AF must not fight LENS_FOCUS_DISTANCE.
        val action = FocusMeteringAction.Builder(point, meteringFlags())
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        c.cameraControl.startFocusAndMetering(action)
        screen.focus.show(x, y)
    }

    private fun lockAeAf(x: Float, y: Float) {
        val c = camera ?: return
        if (extensionMode != ExtensionMode.NONE) return message("В режимах OEM фиксация AE/AF недоступна")
        // Allowed while recording (steady exposure during a pan); not during a photo/burst/probe.
        if (photoBusy || timerRunnable != null || probing) return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        // Meter at the point first, then lock: locking immediately would freeze the old exposure.
        val action = FocusMeteringAction.Builder(point, meteringFlags()).disableAutoCancel().build()
        screen.focus.show(x, y)
        shutterButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val future = c.cameraControl.startFocusAndMetering(action)
        future.addListener({
            if (isDestroyed || camera !== c) return@addListener
            aeAwbLocked = true
            applyRequestOptions()
            if (!aeAwbLocked) return@addListener // rejected by the camera
            screen.lockBadge.text = ProControls.lockLabel(proSupport.aeLock, manualFocusActive())
            screen.lockBadge.visibility = View.VISIBLE
            statusView.text = "Зафиксировано · касание превью или значка — сброс"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun unlockAeAf() {
        aeAwbLocked = false
        camera?.cameraControl?.cancelFocusAndMetering()
        applyRequestOptions()
        if (::screen.isInitialized) screen.lockBadge.visibility = View.GONE
    }

    // ---- PRO menu

    private fun showProMenu() {
        if (isBusy()) return
        val sup = proSupport
        val items = mutableListOf<Pair<String, () -> Unit>>()
        val busyReason = when {
            isVideoMode -> "только для фото"
            softwareBurstEnabled -> "недоступно в СТЕК ×3"
            else -> null
        }
        items += (when {
            busyReason != null -> "Ручная экспозиция — $busyReason"
            !sup.manualSensor -> "Ручная экспозиция — нет MANUAL_SENSOR"
            manualIso != null -> "Ручная экспозиция · ISO $manualIso"
            else -> "Ручная экспозиция · ISO / выдержка"
        }) to {
            if (busyReason == null && sup.manualSensor) showManualExposure()
            else explain("Ручная экспозиция", busyReason?.replaceFirstChar { it.uppercase() }
                ?: "Прошивка не открывает MANUAL_SENSOR для этой камеры. Используй EV на главном экране.")
        }
        items += (when {
            busyReason != null -> "Ручной фокус — $busyReason"
            !sup.manualFocus -> "Ручной фокус — недоступен на этом объективе"
            else -> "Ручной фокус · " + (manualFocusDiopters?.let { ProControls.focusLabel(it, sup.focusCalibrated) } ?: "АВТО")
        }) to {
            if (busyReason == null && sup.manualFocus) showManualFocus()
            else explain("Ручной фокус", busyReason?.replaceFirstChar { it.uppercase() }
                ?: "Объектив не объявляет LENS_INFO_MINIMUM_FOCUS_DISTANCE > 0 вместе с MANUAL_SENSOR (фикс-фокус или закрытый доступ).")
        }
        items += (if (sup.whiteBalancePresets.isEmpty()) "Баланс белого — пресеты недоступны"
            else "Баланс белого · ${ProControls.awbLabel(awbMode)}") to {
            if (sup.whiteBalancePresets.isEmpty()) explain("Баланс белого", "Камера объявляет только автоматический баланс белого.")
            else showWhiteBalance()
        }
        val histogramNote = when {
            histogramRejected -> " (камера отклонила поток)"
            !ProControls.histogramAllowed(isVideoMode, extensionMode != ExtensionMode.NONE, softwareBurstEnabled,
                effectivePhotoFormat, ImageCapture.OUTPUT_FORMAT_JPEG) -> " (только в обычном JPEG-фото)"
            else -> ""
        }
        items += "Гистограмма · ${if (histogramEnabled) "ВКЛ" else "ВЫКЛ"}$histogramNote" to { toggleHistogram() }
        items += "Фиксация AE/AF — удерживай палец на превью" to {
            explain("Фиксация AE/AF", "Долгое нажатие на превью замеряет экспозицию и фокус в точке и фиксирует их" +
                (if (sup.aeLock) "" else " (AE lock камера не объявляет — фиксируется только фокус)") +
                ". Касание превью или значка 🔒 снимает фиксацию.")
        }
        if (ProControls.isActive(proSettings()) || aeAwbLocked) {
            items += "Сбросить все настройки PRO" to { resetAllPro() }
        }
        AlertDialog.Builder(this)
            .setTitle("PRO · ${lenses.firstOrNull { it.id == currentLensId }?.label ?: "камера"}")
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun explain(title: String, text: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("Понятно", null).show()
    }

    private fun showManualFocus() {
        if (extensionMode != ExtensionMode.NONE) return message("Выбери «Авто», чтобы использовать ручной фокус")
        val sup = proSupport
        val previous = manualFocusDiopters
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(8))
        }
        var picked = previous ?: 0f
        val title = vText("Фокус: ${ProControls.focusLabel(picked, sup.focusCalibrated)}", 15f, Color.BLACK)
        form.addView(title)
        form.addView(SeekBar(this).apply {
            max = ProControls.FOCUS_STEPS
            progress = ProControls.progressFromFocus(picked, sup.minFocusDiopters)
            setOnSeekBarChangeListener(simpleSeek { p ->
                picked = ProControls.focusFromProgress(p, sup.minFocusDiopters)
                title.text = "Фокус: ${ProControls.focusLabel(picked, sup.focusCalibrated)}"
                // Live preview: the lens moves while the slider moves.
                manualFocusDiopters = picked
                applyRequestOptions()
            })
        })
        form.addView(vText("← бесконечность · близко →", 12f, Color.GRAY))
        AlertDialog.Builder(this)
            .setTitle("Ручной фокус · PRO")
            .setView(form)
            .setPositiveButton("Применить") { _, _ ->
                manualFocusDiopters = picked
                applyRequestOptions()
                renderControls()
            }
            .setNeutralButton("Автофокус") { _, _ ->
                manualFocusDiopters = null
                applyRequestOptions()
                renderControls()
            }
            .setNegativeButton("Отмена") { _, _ ->
                manualFocusDiopters = previous
                applyRequestOptions()
            }
            .show()
    }

    private fun showWhiteBalance() {
        if (extensionMode != ExtensionMode.NONE) return message("Выбери «Авто», чтобы задать баланс белого")
        val modes = listOf(ProControls.AWB_AUTO) + proSupport.whiteBalancePresets
        AlertDialog.Builder(this)
            .setTitle("Баланс белого")
            .setItems(modes.map { (if ((awbMode ?: ProControls.AWB_AUTO) == it) "● " else "") + ProControls.awbLabel(it) }
                .toTypedArray()) { _, i ->
                awbMode = modes[i].takeIf { it != ProControls.AWB_AUTO }
                applyRequestOptions()
                renderControls()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun toggleHistogram() {
        if (isBusy()) return
        histogramEnabled = !histogramEnabled
        if (histogramEnabled) histogramRejected = false
        prefs.edit().putBoolean("histogram", histogramEnabled).apply()
        bindCamera() // the analysis stream is part of the session
    }

    private fun resetAllPro() {
        manualIso = null
        manualExposureNs = null
        manualFocusDiopters = null
        awbMode = null
        if (aeAwbLocked) unlockAeAf()
        applyRequestOptions()
        configureExposureBar()
        renderControls()
    }

    /** Live histogram: ≤ 4 fps, every image closed immediately. */
    private val histogramAnalyzer = ImageAnalysis.Analyzer { image ->
        try {
            val now = System.nanoTime()
            if (now - lastHistogramNs >= 250_000_000L) {
                lastHistogramNs = now
                val plane = image.planes[0]
                val result = Histogram.ofLuma(plane.buffer, image.width, image.height,
                    plane.rowStride, plane.pixelStride, 4)
                runOnUiThread { if (!isDestroyed && ::screen.isInitialized) screen.histogram.update(result) }
            }
        } catch (_: Exception) {
            // A malformed frame must never crash the viewfinder.
        } finally {
            image.close()
        }
    }

    private fun showDiagnostics() {
        if (probing) return message("Идёт проверка сочетаний")
        val text = createHardwareReport()
        AlertDialog.Builder(this)
            .setTitle("Диагностика камеры")
            .setMessage(text)
            .setPositiveButton("Скопировать") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("REVVL Camera Lab report", text))
                message("Отчёт скопирован — можно отправить разработчику")
            }
            .setNeutralButton("Ещё…") { _, _ -> showDiagnosticsActions(text) }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    /** Export and test actions; everything runs only on explicit user action. */
    private fun showDiagnosticsActions(report: String) {
        val plan = probePlan()
        val actions = listOf<Pair<String, () -> Unit>>(
            "Сохранить отчёт в «Загрузки»" to { saveReport(report) },
            "Поделиться отчётом…" to { shareReport(report) },
            "Проверить сочетания режимов (${plan.size} шт., ~${ProbePlan.estimatedSeconds(plan.size)} с)" to { runSessionProbe(plan) },
            "Очистить журнал событий" to {
                log.clear()
                lastProbe = emptyList()
                message("Журнал очищен")
            }
        )
        AlertDialog.Builder(this)
            .setTitle("Диагностика · действия")
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun saveReport(report: String) {
        val name = "REVVL_diagnostics_${CameraText.fileStamp(System.currentTimeMillis())}.txt"
        if (thumbExecutor.isShutdown) return
        thumbExecutor.execute {
            val result = runCatching { MediaOutputs.saveText(contentResolver, name, report) }
            runOnUiThread {
                result.onSuccess {
                    log.info("export", name)
                    message("Сохранено: Загрузки/${MediaOutputs.FOLDER}/$name")
                }.onFailure {
                    log.error("export", "${it.javaClass.simpleName}: ${it.localizedMessage}")
                    message("Не удалось сохранить отчёт: ${it.localizedMessage}")
                }
            }
        }
    }

    /** Plain-text share sheet; the user chooses the target app (the camera app itself has no network). */
    private fun shareReport(report: String) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "REVVL Camera Lab ${BuildConfig.VERSION_NAME} — диагностика")
                putExtra(Intent.EXTRA_TEXT, report)
            }
            startActivity(Intent.createChooser(send, "Отправить отчёт"))
        } catch (e: Exception) {
            message("Нет приложения для отправки текста: ${e.localizedMessage}")
        }
    }

    private fun probePlan(): List<ProbeCase> = ProbePlan.build(
        lenses.map { lens ->
            val sel = caps.selectorFor(lens.id)
            ProbeLens(
                id = lens.id,
                label = lens.label,
                photoFormats = caps.photoFormats(sel),
                extensions = listOf(ExtensionMode.HDR, ExtensionMode.NIGHT, ExtensionMode.BOKEH)
                    .filter { extensions != null && caps.isExtensionAvailable(sel, it) },
                videoQualities = caps.videoQualities(sel).map { qualityName(it) }
            )
        },
        formatName = { formatName(it) },
        extensionName = { extensionName(it) },
        jpeg = ImageCapture.OUTPUT_FORMAT_JPEG
    )

    private fun extensionName(mode: Int): String =
        CameraLabels.EXTENSION_CHOICES.firstOrNull { it.first == mode }?.second ?: "режим $mode"

    /**
     * Binds every declared combination once (no capture, nothing saved) and waits briefly for an
     * asynchronous camera error. Declared ≠ working on many firmwares; this measures the difference.
     * The user's session is restored afterwards.
     */
    private fun runSessionProbe(cases: List<ProbeCase>) {
        val provider = cameraProvider ?: return message("Камера ещё не готова")
        if (isBusy()) return message("Дождись окончания съёмки")
        if (cases.isEmpty()) return message("Нет объявленных сочетаний для проверки")
        if (aeAwbLocked) unlockAeAf()
        probing = true
        val generation = ++probeGeneration
        camera = null
        imageCapture = null
        videoCapture = null
        renderControls()
        log.info("probe", "старт: ${cases.size} сочетаний")
        val results = ArrayList<ProbeResult>()
        val surface = previewView.surfaceProvider

        fun finish() {
            probing = false
            lastProbe = results.toList()
            log.info("probe", "готово: принято ${results.count { it.ok }} из ${results.size}")
            bindCamera() // restore the user's lens/mode/format
            val report = ProbePlan.report(lastProbe)
            AlertDialog.Builder(this)
                .setTitle("Проверка сочетаний")
                .setMessage(report)
                .setPositiveButton("Скопировать") { _, _ ->
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("REVVL probe", report))
                    message("Результат скопирован")
                }
                .setNegativeButton("Закрыть", null)
                .show()
        }

        fun step(index: Int) {
            if (!probing || isDestroyed || generation != probeGeneration) return // interrupted (onStop restores the session)
            if (index >= cases.size) return finish()
            val c = cases[index]
            statusView.text = "Проверка ${index + 1}/${cases.size}: ${c.name}"
            val started = SystemClock.elapsedRealtime()
            val sel = caps.selectorFor(c.lensId)
            val bound = try {
                when (c.kind) {
                    ProbeKind.VIDEO -> {
                        val quality = CameraLabels.qualityFromName(c.name.removePrefix("видео ")) ?: Quality.FHD
                        binder.bindVideo(provider, sel, surface, quality, deviceRotation)
                    }
                    ProbeKind.PHOTO, ProbeKind.EXTENSION -> {
                        val ext = c.kind == ProbeKind.EXTENSION
                        val plan = SessionPlanner.photo(PhotoSessionRequest(
                            requestedFormat = if (ext) ImageCapture.OUTPUT_FORMAT_JPEG else c.value,
                            supportedFormats = caps.photoFormats(sel),
                            extensionRequested = ext,
                            extensionAvailable = ext,
                            burst = false,
                            manualExposure = false,
                            flashMode = ImageCapture.FLASH_MODE_OFF,
                            hasFlash = false
                        ), CameraLabels.CAPTURE_CONSTANTS)
                        binder.bindPhoto(provider, sel, surface, plan,
                            if (ext) c.value else ExtensionMode.NONE, extensions, deviceRotation)
                    }
                }
            } catch (e: Exception) {
                results += ProbeResult(c, false, "${e.javaClass.simpleName}: ${e.localizedMessage}",
                    SystemClock.elapsedRealtime() - started)
                log.warn("probe", "✗ ${c.lensId} ${c.name}: ${e.localizedMessage}")
                timerHandler.post { step(index + 1) }
                return
            }
            timerHandler.postDelayed({
                if (!probing || isDestroyed || generation != probeGeneration) return@postDelayed
                val error = bound.camera.cameraInfo.cameraState.value?.error
                val ok = error == null
                results += ProbeResult(c, ok, if (ok) "ok" else "CameraState ошибка ${error?.code}",
                    SystemClock.elapsedRealtime() - started)
                if (!ok) log.warn("probe", "✗ ${c.lensId} ${c.name}: CameraState ${error?.code}")
                step(index + 1)
            }, ProbePlan.SETTLE_MS)
        }
        step(0)
    }

    private fun createHardwareReport(): String = buildString {
        append(HardwareReport.build(
            caps, lenses.toList(),
            HardwareReport.SessionInfo(softwareBurstEnabled, requestedPhotoFormat, effectivePhotoFormat)
        ))
        appendLine()
        appendLine("=== ЗАДЕРЖКИ (эта сессия) ===")
        appendLine(log.latencyReport())
        appendLine()
        appendLine("=== ${ProbePlan.report(lastProbe).lineSequence().first()} ===")
        if (lastProbe.isNotEmpty()) appendLine(ProbePlan.report(lastProbe).lineSequence().drop(1).joinToString("\n"))
        appendLine()
        appendLine("=== ЖУРНАЛ (последние 120 событий) ===")
        append(log.format(120).ifEmpty { "пусто" })
    }

    private fun openLastMedia() {
        val uri = lastMediaUri ?: return message("Сначала сделай фото или запиши видео")
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val mime = contentResolver.getType(uri) ?: "image/*"
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            message("Не удалось открыть файл: ${e.localizedMessage}")
        }
    }

    private fun message(msg: String) {
        // Late CameraX callbacks may arrive after the Activity is gone.
        if (isFinishing || isDestroyed) return
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
