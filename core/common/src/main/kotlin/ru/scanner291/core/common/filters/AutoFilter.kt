package ru.scanner291.core.common.filters

import ru.scanner291.core.common.model.FilterType
import kotlin.math.max
import kotlin.math.min

/**
 * «Авто» (2026-10-24, по образцу Auto Color в Scanner Pro и Auto-color в Adobe Scan): фильтр выбирается по
 * выпрямленной странице без фильтра (уменьшенной до ~256 px). Признаки — доля «цветных» пикселей
 * (насыщенность ≥ 0.35, не бумага и не чёрный) и доля бумаги (ярче 75 % от 95-го процентиля яркости, без цвета —
 * тени не мешают). Копия tools/processing-lab/cam.py `auto_filter`; замер: документы 36/36 → «Документ»,
 * цветные документы 12/12 → «Яркий цвет», фото 6/6 → «Фото».
 */
object AutoFilter {
    data class Choice(val filter: FilterType, val colorful: Double, val paper: Double, val reason: String)

    fun choose(px: IntArray, w: Int, h: Int): Choice {
        require(px.size == w * h && px.isNotEmpty())
        var colorful = 0
        val hist = LongArray(256)
        val luma = IntArray(px.size)
        val sat = FloatArray(px.size)
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            val s = if (mx > 0) (mx - mn).toFloat() / mx else 0f
            sat[i] = s
            if (s >= 0.35f && mx > 40 && mx < 245) colorful++
            val l = (r * 299 + g * 587 + b * 114) / 1000
            luma[i] = l; hist[l]++
        }
        val p95 = ru.scanner291.core.common.render.RenderMath.percentile(hist, 95.0)
        var paper = 0
        for (i in px.indices) if (luma[i] > 0.75 * p95 && sat[i] < 0.2f) paper++
        val c = colorful.toDouble() / px.size; val pa = paper.toDouble() / px.size
        return when {
            c > 0.25 || (pa < 0.15 && c > 0.02) -> Choice(FilterType.PHOTO, c, pa, "похоже на фотографию")
            c > 0.04 -> Choice(FilterType.MAGIC_COLOR, c, pa, "на странице цветная графика")
            else -> Choice(FilterType.HYBRID, c, pa, "текстовый документ")
        }
    }
}
