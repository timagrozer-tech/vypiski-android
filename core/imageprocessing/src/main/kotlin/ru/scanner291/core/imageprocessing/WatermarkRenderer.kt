package ru.scanner291.core.imageprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import ru.scanner291.core.common.watermark.GridInkMap
import ru.scanner291.core.common.watermark.InkMap
import ru.scanner291.core.common.watermark.TextMeasurer
import ru.scanner291.core.common.watermark.WatermarkPlacement
import ru.scanner291.core.common.watermark.WatermarkPlanner
import ru.scanner291.core.common.watermark.WatermarkSettings
import ru.scanner291.core.common.watermark.WatermarkSpec
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Водяной знак «В/ч 65384» (этап 13) на экспортируемой странице: место выбирает [WatermarkPlanner]
 * (свободное левое поле внизу; при нехватке — меньше шрифт или поле слева), текст повёрнут на 90° против
 * часовой стрелки, серый, непрозрачность из настроек (20 %). Исходный Bitmap не меняется.
 * Повтор исключён: экспорт всегда строится из оригинала, а файл, уже бывший результатом экспорта,
 * помечается [alreadyWatermarked] (реестр хешей и метка в метаданных) — знак второй раз не наносится.
 */
object WatermarkRenderer {
    data class Result(val bitmap: Bitmap, val placement: WatermarkPlacement?)

    fun paint(fontPx: Float, opacity: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textSize = fontPx
        color = WatermarkSpec.COLOR_ARGB
        alpha = (opacity.coerceIn(0f, 1f) * 255).roundToInt()
    }

    /** Карта чернил: всё заметно темнее бумаги или цветное (печать, подпись) — на копии ≤ 400 px. */
    fun inkMap(b: Bitmap): InkMap {
        val s = minOf(1f, 400f / max(b.width, b.height))
        val w = max(1, (b.width * s).roundToInt()); val h = max(1, (b.height * s).roundToInt())
        val small = Bitmap.createScaledBitmap(b, w, h, true)
        val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h)
        val mask = BooleanArray(px.size) { i ->
            val c = px[i]; val r = Color.red(c); val g = Color.green(c); val bl = Color.blue(c)
            val mx = maxOf(r, g, bl); val mn = minOf(r, g, bl)
            (r * 299 + g * 587 + bl * 114) / 1000 < 200 || (mx - mn > 60 && mx < 235)
        }
        if (small !== b) small.recycle()
        return GridInkMap(b.width, b.height, w, h, mask)
    }

    fun apply(page: Bitmap, dpi: Float, settings: WatermarkSettings, alreadyWatermarked: Boolean): Result {
        val s = settings.normalized()
        if (!s.enabled || alreadyWatermarked) return Result(page, null)
        val plan = WatermarkPlanner().plan(page.width, page.height, dpi, inkMap(page), s, TextMeasurer { fontPx ->
            val p = paint(fontPx, 1f); val fm = p.fontMetrics
            p.measureText(WatermarkSpec.TEXT) to (fm.descent - fm.ascent)
        })
        val out = Bitmap.createBitmap(plan.outputWidth, plan.outputHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(page, plan.extendLeftPx.toFloat(), 0f, null)
        c.save()
        c.translate(plan.anchorX, plan.anchorY)
        c.rotate(WatermarkSpec.ROTATION_DEGREES)   // −90°: против часовой, текст читается снизу вверх
        c.drawText(WatermarkSpec.TEXT, 0f, 0f, paint(plan.fontPx, s.opacity))
        c.restore()
        return Result(out, plan)
    }
}
