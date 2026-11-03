package ru.revvl.cameralab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Composition aid only: does not modify the saved photo or video. */
class GridOverlay(context: Context) : View(context) {
    var isEnabledForPreview: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(155, 255, 255, 255)
        strokeWidth = resources.displayMetrics.density
    }

    init { isClickable = false; isFocusable = false }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isEnabledForPreview) return
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..2) {
            val f = i / 3f
            canvas.drawLine(w * f, 0f, w * f, h, paint)
            canvas.drawLine(0f, h * f, w, h * f, paint)
        }
    }
}
