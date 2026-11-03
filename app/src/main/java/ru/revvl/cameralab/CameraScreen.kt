package ru.revvl.cameralab

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Viewfinder layout v2 (portrait, one-hand). Only views and their styling live here; every
 * action is a callback into the activity, which owns camera state and decides what is enabled.
 *
 *  ┌ top bar: flash · timer|quality · grid · format|A/B · PRO · i ┐
 *  │ preview 3:4 (photo) / 9:16 (video): grid, focus ring, status, cancel, ZOOM/EV slider │
 *  │ mode chips (Авто HDR Ночь Портрет СТЕК ×3) · ФОТО | ВИДЕО                       │
 *  └ gallery thumbnail · large shutter · lens button ┘
 */
class CameraScreen(private val activity: ComponentActivity, private val actions: Actions) {

    class Actions(
        val onShutter: () -> Unit,
        val onCancel: () -> Unit,
        val onGallery: () -> Unit,
        val onLensNext: () -> Unit,
        val onLensPick: () -> Unit,
        val onPhotoMode: () -> Unit,
        val onVideoMode: () -> Unit,
        val onFlash: () -> Unit,
        val onTimer: () -> Unit,
        val onQuality: () -> Unit,
        val onGrid: () -> Unit,
        val onFormat: () -> Unit,
        val onPro: () -> Unit,
        val onInfo: () -> Unit,
        val onUnlock: () -> Unit,
        val onZoom: (Int) -> Unit,
        val onExposure: (Int) -> Unit
    )

    private val density = activity.resources.displayMetrics.density
    private fun dp(n: Int) = (density * n + .5f).toInt()
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    val root: LinearLayout
    val previewFrame: AspectFrameLayout
    val preview: PreviewView
    val grid: GridOverlay
    val focus: FocusIndicator
    val status: TextView
    val cancelButton: TextView
    val histogram: HistogramView
    /** "AE/AF 🔒" badge after a long press; tapping it unlocks. */
    val lockBadge: TextView
    val zoomBar: SeekBar
    val exposureBar: SeekBar
    val zoomChip: TextView
    val exposureLabel: TextView
    val flashButton: TextView
    val timerButton: TextView
    val qualityButton: TextView
    val gridButton: TextView
    val formatButton: TextView
    val proButton: TextView
    val infoButton: TextView
    val effectsBar: LinearLayout
    val photoTab: TextView
    val videoTab: TextView
    val shutter: ShutterView
    val gallery: FrameLayout
    private val galleryImage: ImageView
    private val galleryPlaceholder: TextView
    val lensButton: TextView
    /** Views turned upright when the phone is rotated (the layout itself stays portrait). */
    private val rotatingIcons: List<View>

    init {
        root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CameraStyle.BG)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // ---- top bar
        val top = row().apply { setPadding(dp(4), dp(2), dp(4), dp(2)) }
        flashButton = topItem("ВСП") { actions.onFlash() }
        timerButton = topItem("ТАЙМЕР") { actions.onTimer() }
        qualityButton = topItem("1080p") { actions.onQuality() }
        gridButton = topItem("СЕТКА") { actions.onGrid() }
        formatButton = topItem("JPEG") { actions.onFormat() }
        proButton = topItem("PRO") { actions.onPro() }
        infoButton = topItem("i") { actions.onInfo() }.apply { contentDescription = "Диагностика камеры" }
        for (v in listOf(flashButton, timerButton, qualityButton, gridButton, formatButton, proButton)) {
            top.addView(v, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        top.addView(infoButton, LinearLayout.LayoutParams(dp(44), dp(48)))
        root.addView(top, LinearLayout.LayoutParams(-1, -2))

        // ---- preview
        val holder = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
        previewFrame = AspectFrameLayout(activity)
        preview = PreviewView(activity).apply {
            // COMPATIBLE (TextureView) keeps rotation/aspect predictable inside the aspect box.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        previewFrame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        grid = GridOverlay(activity)
        previewFrame.addView(grid, FrameLayout.LayoutParams(-1, -1))
        focus = FocusIndicator(activity)
        previewFrame.addView(focus, FrameLayout.LayoutParams(-1, -1))
        status = TextView(activity).apply {
            text = "Запуск камеры…"
            textSize = 12f
            typeface = medium
            setTextColor(CameraStyle.WHITE)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = pill(CameraStyle.PANEL_TRANSLUCENT)
            maxLines = 2
        }
        previewFrame.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(10), dp(10), dp(10), 0)
        })
        cancelButton = TextView(activity).apply {
            text = "✕  ОТМЕНА"
            textSize = 13f
            typeface = medium
            gravity = Gravity.CENTER
            setTextColor(CameraStyle.WHITE)
            setPadding(dp(18), 0, dp(18), 0)
            background = pill(CameraStyle.RED)
            visibility = View.GONE
            isClickable = true
            setOnClickListener { actions.onCancel() }
        }
        previewFrame.addView(cancelButton, FrameLayout.LayoutParams(-2, dp(44), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(64)
        })

        histogram = HistogramView(activity).apply { visibility = View.GONE }
        previewFrame.addView(histogram, FrameLayout.LayoutParams(dp(128), dp(56), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(10), dp(10), 0)
        })
        lockBadge = TextView(activity).apply {
            text = "AE/AF 🔒"
            textSize = 12f
            typeface = medium
            gravity = Gravity.CENTER
            setTextColor(CameraStyle.BG)
            setPadding(dp(12), 0, dp(12), 0)
            background = pill(CameraStyle.MINT)
            visibility = View.GONE
            isClickable = true
            contentDescription = "Экспозиция и фокус зафиксированы. Нажми, чтобы снять фиксацию"
            setOnClickListener { actions.onUnlock() }
        }
        previewFrame.addView(lockBadge, FrameLayout.LayoutParams(-2, dp(32), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(52)
        })

        // Single slider band: ZOOM or EV, chosen by the chips (keeps the preview uncluttered).
        val sliderBand = row().apply {
            setBackgroundColor(CameraStyle.PANEL_TRANSLUCENT)
            setPadding(dp(8), 0, dp(8), 0)
        }
        zoomChip = chip("ZOOM", true) { selectSlider(zoom = true) }
        exposureLabel = chip("EV 0.0", false) { selectSlider(zoom = false) }
        sliderBand.addView(zoomChip, LinearLayout.LayoutParams(-2, dp(32)))
        sliderBand.addView(View(activity), LinearLayout.LayoutParams(dp(6), 1))
        sliderBand.addView(exposureLabel, LinearLayout.LayoutParams(-2, dp(32)))
        zoomBar = slider(100) { actions.onZoom(it) }
        exposureBar = slider(0) { actions.onExposure(it) }.apply { visibility = View.GONE }
        sliderBand.addView(zoomBar, LinearLayout.LayoutParams(0, dp(48), 1f))
        sliderBand.addView(exposureBar, LinearLayout.LayoutParams(0, dp(48), 1f))
        previewFrame.addView(sliderBand, FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM))

        holder.addView(previewFrame, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))

        // ---- bottom controls
        effectsBar = row().apply { setPadding(dp(8), 0, dp(8), 0) }
        val effectsScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(effectsBar)
        }
        root.addView(effectsScroll, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })

        val tabs = row().apply { gravity = Gravity.CENTER }
        photoTab = tab("ФОТО") { actions.onPhotoMode() }
        videoTab = tab("ВИДЕО") { actions.onVideoMode() }
        tabs.addView(photoTab, LinearLayout.LayoutParams(dp(96), dp(40)))
        tabs.addView(videoTab, LinearLayout.LayoutParams(dp(96), dp(40)))
        root.addView(tabs, LinearLayout.LayoutParams(-1, -2))

        val bottom = row().apply { setPadding(0, dp(6), 0, dp(14)) }
        galleryImage = ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        galleryPlaceholder = TextView(activity).apply {
            text = "▢"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(CameraStyle.MUTED)
        }
        gallery = FrameLayout(activity).apply {
            background = rounded(CameraStyle.PANEL, dp(12).toFloat())
            clipToOutline = true
            isClickable = true
            contentDescription = "Открыть последний снимок или ролик"
            addView(galleryImage, FrameLayout.LayoutParams(-1, -1))
            addView(galleryPlaceholder, FrameLayout.LayoutParams(-1, -1))
            setOnClickListener { actions.onGallery() }
        }
        shutter = ShutterView(activity).apply { setOnClickListener { actions.onShutter() } }
        lensButton = TextView(activity).apply {
            text = "…"; textSize = 11f; typeface = medium; gravity = Gravity.CENTER
            setTextColor(CameraStyle.WHITE)
            background = rounded(CameraStyle.PANEL, dp(28).toFloat())
            isClickable = true
            contentDescription = "Сменить объектив. Долгое нажатие — список"
            setOnClickListener { actions.onLensNext() }
            setOnLongClickListener { actions.onLensPick(); true }
        }
        bottom.addView(cell(gallery, dp(56), dp(56)), LinearLayout.LayoutParams(0, dp(84), 1f))
        bottom.addView(cell(shutter, dp(80), dp(80)), LinearLayout.LayoutParams(0, dp(84), 1f))
        bottom.addView(cell(lensButton, dp(56), dp(56)), LinearLayout.LayoutParams(0, dp(84), 1f))
        root.addView(bottom, LinearLayout.LayoutParams(-1, -2))

        rotatingIcons = listOf(gallery, lensButton, shutter, infoButton)
    }

    // ---- state helpers used by the activity

    /** Shows the histogram and keeps the status pill from running underneath it. */
    fun showHistogram(visible: Boolean) {
        histogram.visibility = if (visible) View.VISIBLE else View.GONE
        (status.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.setMargins(dp(10), dp(10), if (visible) dp(148) else dp(10), 0)
            status.layoutParams = it
        }
    }

    fun setPreviewAspect(video: Boolean) {
        previewFrame.aspect = UiModel.previewAspect(video)
    }

    fun setChipActive(view: TextView, active: Boolean) {
        view.background = pill(if (active) CameraStyle.MINT else CameraStyle.PANEL)
        view.setTextColor(if (active) CameraStyle.BG else CameraStyle.WHITE)
    }

    fun setTabActive(view: TextView, active: Boolean) {
        view.setTextColor(if (active) CameraStyle.MINT else CameraStyle.MUTED)
        view.setTypeface(medium, if (active) Typeface.BOLD else Typeface.NORMAL)
    }

    fun setEnabledLook(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else .4f
    }

    fun setLens(short: String, position: String?, description: String?) {
        lensButton.text = if (position == null) short else "$short\n$position"
        if (description != null) lensButton.contentDescription = "Объектив: $description. Нажми — следующий, удерживай — список"
    }

    fun setThumbnail(bitmap: Bitmap?) {
        galleryImage.setImageBitmap(bitmap)
        galleryPlaceholder.visibility = if (bitmap == null) View.VISIBLE else View.GONE
    }

    /** Counter-rotates icons so they stay upright; the layout itself never rotates. */
    fun rotateIcons(surfaceRotation: Int) {
        val target = UiModel.iconRotation(surfaceRotation)
        for (v in rotatingIcons) {
            v.animate().rotation(UiModel.nearestAngle(v.rotation, target)).setDuration(180L).start()
        }
    }

    /** New mode chip for the effects row (Авто / HDR / Ночь / Портрет / СТЕК ×3). */
    fun chip(label: String, active: Boolean, onClick: () -> Unit): TextView = TextView(activity).apply {
        text = label
        textSize = 12f
        typeface = medium
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        isClickable = true
        setOnClickListener { onClick() }
        setChipActive(this, active)
    }

    fun addEffect(view: TextView) {
        effectsBar.addView(view, LinearLayout.LayoutParams(-2, dp(36)).apply { marginEnd = dp(6) })
    }

    fun addEffectNote(text: String) {
        effectsBar.addView(TextView(activity).apply {
            this.text = text; textSize = 12f; setTextColor(CameraStyle.MUTED); gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-2, dp(36)))
    }

    private fun selectSlider(zoom: Boolean) {
        zoomBar.visibility = if (zoom) View.VISIBLE else View.GONE
        exposureBar.visibility = if (zoom) View.GONE else View.VISIBLE
        setChipActive(zoomChip, zoom)
        setChipActive(exposureLabel, !zoom)
    }

    // ---- builders

    private fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun topItem(label: String, onClick: () -> Unit) = TextView(activity).apply {
        text = label
        textSize = 11f
        typeface = medium
        gravity = Gravity.CENTER
        maxLines = 2
        setTextColor(CameraStyle.WHITE)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun tab(label: String, onClick: () -> Unit) = TextView(activity).apply {
        text = label
        textSize = 13f
        letterSpacing = .08f
        gravity = Gravity.CENTER
        isClickable = true
        setOnClickListener { onClick() }
        setTabActive(this, false)
    }

    private fun slider(max: Int, onUser: (Int) -> Unit) = SeekBar(activity).apply {
        this.max = max
        progressTintList = ColorStateList.valueOf(CameraStyle.MINT)
        thumbTintList = ColorStateList.valueOf(CameraStyle.MINT)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) onUser(value)
            }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
    }

    private fun cell(child: View, w: Int, h: Int) = FrameLayout(activity).apply {
        addView(child, FrameLayout.LayoutParams(w, h, Gravity.CENTER))
    }

    private fun pill(color: Int) = rounded(color, dp(18).toFloat())

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }
}
