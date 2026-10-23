package ru.scanner291.core.common.render

import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.model.FilterType
import ru.scanner291.core.common.model.HybridProfile
import ru.scanner291.core.common.model.PageRecipe
import ru.scanner291.core.common.model.ScanMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Чистая математика рендера страницы (без OpenCV) — покрыта JVM-тестами. */
object RenderMath {
    /** Допуск, в котором пропорции углов считаются совпадающими с форматом режима (измерено: tools/processing-lab). */
    const val ASPECT_SNAP_TOLERANCE = 0.15

    /**
     * Размер выпрямленного листа по углам в пикселях: максимум длин противоположных сторон.
     * Если формат известен ([aspectWOverH], например A4) и пропорции углов отличаются от него не больше
     * чем на 15 %, берётся точное соотношение формата при той же площади (перспектива искажает длины сторон:
     * без формата ошибка пропорций в среднем 2.9 %, до 8.9 %). Ориентация — по углам.
     */
    fun outputSize(q: Quad, aspectWOverH: Double? = null): Pair<Int, Int> {
        var w = max(hypot(q.tr.x - q.tl.x, q.tr.y - q.tl.y), hypot(q.br.x - q.bl.x, q.br.y - q.bl.y))
        var h = max(hypot(q.bl.x - q.tl.x, q.bl.y - q.tl.y), hypot(q.br.x - q.tr.x, q.br.y - q.tr.y))
        if (aspectWOverH != null && w > 0 && h > 0) {
            val a = if ((w <= h) == (aspectWOverH <= 1.0)) aspectWOverH else 1.0 / aspectWOverH
            if (abs((w / h) / a - 1.0) <= ASPECT_SNAP_TOLERANCE) {
                val area = w * h
                w = sqrt(area * a); h = area / w
            }
        }
        return max(1, w.roundToInt()) to max(1, h.roundToInt())
    }

    /** Размер после поворота на 90/270 — стороны меняются местами. */
    fun rotated(size: Pair<Int, Int>, rotation: Int): Pair<Int, Int> =
        if (((rotation % 360) + 360) % 360 % 180 == 90) size.second to size.first else size

    /** Нечётный размер ядра (OpenCV требует нечётные ядра). */
    fun odd(v: Double): Int { val r = v.roundToInt().coerceAtLeast(1); return if (r % 2 == 1) r else r + 1 }

    /**
     * Таблица «уровней» по гистограмме: [lo]..[hi] растягиваются на 0..255. Процентили считаются по
     * гистограмме [hist] (256 корзин). Если диапазон меньше 16 уровней — тождество (пустая страница).
     */
    fun levelsLut(hist: LongArray, loPct: Double = 1.0, hiPct: Double = 99.5): IntArray {
        val lo = percentile(hist, loPct); val hi = percentile(hist, hiPct)
        if (hi - lo < 16) return IntArray(256) { it }
        return IntArray(256) { ((it - lo) * 255.0 / (hi - lo)).clamp() }
    }

    /** Чёрная точка цветного режима: 1-й процентиль яркости, не выше 120 (чтобы не «выжечь» тёмные фото). */
    fun blackPointLut(hist: LongArray): IntArray {
        val lo = minOf(percentile(hist, 1.0), 120)
        return IntArray(256) { ((it - lo) * 255.0 / (255 - lo)).clamp() }
    }

    /** Кривая x^gamma: тёмное темнее, белое остаётся белым. */
    fun gammaLut(gamma: Double): IntArray = IntArray(256) { (255.0 * Math.pow(it / 255.0, gamma)).clamp() }

    /** Яркость −1..1 (сдвиг до ±100 уровней) и контраст 0.5..2 вокруг середины. */
    fun brightnessContrastLut(brightness: Float, contrast: Float): IntArray? {
        if (brightness == 0f && contrast == 1f) return null
        return IntArray(256) { ((it - 128.0) * contrast + 128.0 + brightness * 100.0).clamp() }
    }

    fun percentile(hist: LongArray, pct: Double): Int {
        val n = hist.sum(); if (n == 0L) return 0
        val target = n * pct / 100.0
        var acc = 0L
        for (i in hist.indices) { acc += hist[i]; if (acc >= target) return i }
        return 255
    }

    private fun Double.clamp(): Int = when { this < 0 -> 0; this > 255 -> 255; else -> this.toInt() }
}

/** Параметры фильтров (выбраны в tools/processing-lab, см. README там). */
object RenderParams {
    const val TEXT_GAMMA = 1.8
    const val BOOK_GAMMA = 1.3
    const val UNSHARP_SIGMA = 1.0
    const val UNSHARP_AMOUNT = 0.5
    const val BW_C = 12.0
    const val RECEIPT_C = 8.0
    /** Сторона уменьшенной копии для оценки фона (освещения). */
    const val BACKGROUND_SIDE = 512
}

/** Что делает фильтр: цвет, выравнивание освещения, шумоподавление. Одно место правды для рендера и экрана. */
fun FilterType.keepsColor(): Boolean = this == FilterType.COLOR || this == FilterType.PHOTO || this == FilterType.BOOK || this == FilterType.ORIGINAL || this == FilterType.HYBRID
fun FilterType.usesShadowRemoval(): Boolean = this != FilterType.ORIGINAL && this != FilterType.PHOTO
fun FilterType.usesDenoise(): Boolean = this != FilterType.ORIGINAL && this != FilterType.PHOTO

/**
 * Рецепт страницы в строке для БД: только параметры обработки (углы хранятся отдельными столбцами,
 * аннотации и закраска — на этапах 10/13). Формат стабилен и версионирован: `v1;f=…;r=…;b=…;c=…;s=…;n=…`.
 */
object RecipeCodec {
    fun encode(r: PageRecipe): String {
        val n = r.normalized()
        val base = "v1;f=${n.filter.name};r=${n.rotation};b=${fmt(n.brightness)};c=${fmt(n.contrast)};s=${if (n.removeShadows) 1 else 0};n=${if (n.denoise) 1 else 0}"
        // h= пишется только для Hybrid: строки рецептов этапа 7 остаются прежними.
        val sb = StringBuilder(base)
        if (n.filter == FilterType.HYBRID) sb.append(";h=").append(n.hybrid.name)
        // a= и p= — только если заданы (этап 9): строки рецептов этапов 7–8 не меняются.
        if (!n.adjust.isNeutral) sb.append(";a=").append(n.adjust.encode())
        n.preset?.let { sb.append(";p=").append(it.replace(";", "").replace("=", "")) }
        return sb.toString()
    }

    /** null или повреждённая строка → null (используется рецепт по умолчанию для режима). */
    fun decode(s: String?, corners: Quad? = null): PageRecipe? {
        if (s.isNullOrBlank()) return null
        val parts = s.split(';')
        if (parts.firstOrNull() != "v1") return null
        val m = parts.drop(1).mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        return runCatching {
            PageRecipe(
                corners = corners,
                rotation = m.getValue("r").toInt(),
                filter = FilterType.valueOf(m.getValue("f")),
                brightness = m.getValue("b").toFloat(),
                contrast = m.getValue("c").toFloat(),
                removeShadows = m.getValue("s") == "1",
                denoise = m.getValue("n") == "1",
                hybrid = m["h"]?.let { HybridProfile.valueOf(it) } ?: HybridProfile.DOCUMENT,
                adjust = ru.scanner291.core.common.filters.decodeAdjustments(m["a"]),
                preset = m["p"],
            ).normalized().rotatedBy(0)
        }.getOrNull()
    }

    /** Рецепт по умолчанию: фильтр режима, тени и шум убираются. */
    fun default(mode: ScanMode, corners: Quad? = null) = PageRecipe(corners = corners, filter = mode.defaultFilter)

    /** Ключ для кэша изображений: одинаковый рецепт → одинаковая картинка. */
    fun cacheKey(r: PageRecipe): String = encode(r) + ";q=" + (r.corners?.points?.joinToString(",") { fmt3(it.x) + ":" + fmt3(it.y) } ?: "full")

    private fun fmt(v: Float) = String.format(Locale.ROOT, "%.2f", v)
    private fun fmt3(v: Double) = String.format(Locale.ROOT, "%.4f", v)
}
