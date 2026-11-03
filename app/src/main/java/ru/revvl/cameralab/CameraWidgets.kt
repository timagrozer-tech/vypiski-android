package ru.revvl.cameralab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min

/** Colours of the v2 interface: deep dark UI, one moderate accent, red only for recording. */
object CameraStyle {
    const val BG = 0xFF0B101A.toInt()
    const val PANEL = 0xFF172232.toInt()
    const val PANEL_TRANSLUCENT = 0x99101722.toInt()
    const val MUTED = 0xFF93A4BB.toInt()
    const val MINT = 0xFF74E4C7.toInt()
    const val WHITE = Color.WHITE
    const val RED = 0xFFF05B70.toInt()
}

/**
 * Large round shutter drawn on a Canvas (no bitmaps, no blur: cheap for preview FPS).
 * PHOTO: white disc; VIDEO: red disc; RECORDING: red rounded square. A mint ring shows
 * СТЕК ×3 progress; a digit shows the self-timer countdown.
 */
class ShutterView(context: Context) : View(context) {
    var mode: ShutterMode = ShutterMode.PHOTO
        set(value) { field = value; updateDescription(); invalidate() }
    var progress: Float? = null
        set(value) { field = value; invalidate() }
    var label: String? = null
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = CameraStyle.WHITE; strokeWidth = 4 * density
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = CameraStyle.MINT; strokeWidth = 4 * density
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CameraStyle.BG; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        textSize = 26 * density
    }
    private val oval = RectF()

    init {
        isClickable = true
        isFocusable = true
        updateDescription()
    }

    private fun updateDescription() {
        contentDescription = when (mode) {
            ShutterMode.PHOTO -> "Снять фото"
            ShutterMode.VIDEO -> "Начать запись видео"
            ShutterMode.RECORDING -> "Остановить запись"
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else .55f
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate() // pressed state shrinks the inner disc
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(cx, cy) - ring.strokeWidth
        canvas.drawCircle(cx, cy, outer, ring)
        val inner = outer - 7 * density - (if (isPressed) 3 * density else 0f)
        when (mode) {
            ShutterMode.PHOTO -> {
                fill.color = CameraStyle.WHITE
                canvas.drawCircle(cx, cy, inner, fill)
            }
            ShutterMode.VIDEO -> {
                fill.color = CameraStyle.RED
                canvas.drawCircle(cx, cy, inner, fill)
            }
            ShutterMode.RECORDING -> {
                fill.color = CameraStyle.RED
                val half = inner * .55f
                oval.set(cx - half, cy - half, cx + half, cy + half)
                canvas.drawRoundRect(oval, 6 * density, 6 * density, fill)
            }
        }
        progress?.let { p ->
            oval.set(cx - outer, cy - outer, cx + outer, cy + outer)
            canvas.drawArc(oval, -90f, 360f * p.coerceIn(0f, 1f), false, arc)
        }
        label?.let { canvas.drawText(it, cx, cy - (text.descent() + text.ascent()) / 2, text) }
    }
}

/** Short-lived focus ring at the tap point; purely visual, does not touch the camera. */
class FocusIndicator(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = CameraStyle.MINT; strokeWidth = 2 * density
    }
    private var ringX = -1f
    private var ringY = -1f
    private val hide = Runnable { ringX = -1f; invalidate() }

    init { isClickable = false; isFocusable = false }

    fun show(atX: Float, atY: Float) {
        ringX = atX; ringY = atY
        removeCallbacks(hide)
        invalidate()
        postDelayed(hide, 1200L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (ringX < 0f) return
        canvas.drawCircle(ringX, ringY, 34 * density, paint)
    }
}

/**
 * Compact luma histogram (64 bins, sqrt scale). Clipped highlights/shadows above 1 % tint the
 * outer bins red so over/under-exposure is visible at a glance. Drawing only; data comes from
 * the analysis thread at ≤ 4 fps.
 */
class HistogramView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xCCFFFFFF.toInt() }
    private val clip = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = CameraStyle.RED }
    private val bg = Paint().apply { style = Paint.Style.FILL; color = CameraStyle.PANEL_TRANSLUCENT }
    private val rect = RectF()
    private var data: Histogram.Result? = null

    init { isClickable = false; isFocusable = false; contentDescription = "Гистограмма яркости" }

    fun update(result: Histogram.Result?) {
        data = result
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = data ?: return
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 6 * density, 6 * density, bg)
        val peak = kotlin.math.sqrt(r.peak.toFloat()).coerceAtLeast(1f)
        val pad = 4 * density
        val w = (width - 2 * pad) / Histogram.BINS
        val h = height - 2 * pad
        for (i in 0 until Histogram.BINS) {
            val v = kotlin.math.sqrt(r.bins[i].toFloat()) / peak
            val paint = when {
                i == 0 && r.shadowClip > 0.01 -> clip
                i == Histogram.BINS - 1 && r.highlightClip > 0.01 -> clip
                else -> bar
            }
            rect.set(pad + i * w, pad + h * (1 - v), pad + (i + 1) * w, pad + h)
            canvas.drawRect(rect, paint)
        }
    }
}

/** FrameLayout that takes the largest size with [aspect] (w/h) fitting its measure specs. */
class AspectFrameLayout(context: Context) : FrameLayout(context) {
    var aspect: Float = UiModel.PHOTO_ASPECT
        set(value) {
            if (field != value) { field = value; requestLayout() }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val (w, h) = UiModel.fit(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec), aspect)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
    }
}
