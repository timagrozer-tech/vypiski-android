package ru.scanner291.core.common.watermark

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Неизменяемые параметры водяного знака. Текст — строго «В/ч 65384». */
object WatermarkSpec {
    const val TEXT: String = "В/ч 65384"
    /** Поворот на 90° против часовой стрелки (текст читается снизу вверх). */
    const val ROTATION_DEGREES: Float = -90f
    /** Нейтральный серый (токен дизайн-системы watermark-ink). */
    const val COLOR_ARGB: Int = 0xFF6B7280.toInt()
    const val LEGACY_TEXT_FORBIDDEN: String = "в/с 65384"
}

/** Настройки пользователя. Значения по умолчанию — из ТЗ и дизайн-системы. */
data class WatermarkSettings(
    val enabled: Boolean = true,
    /** Непрозрачность 0..1, по умолчанию 20 %. */
    val opacity: Float = 0.20f,
    /** Размер шрифта в пунктах. */
    val fontPt: Float = 14f,
    /** Отступ от левого и нижнего края, мм. */
    val marginMm: Float = 8f,
) {
    fun normalized(): WatermarkSettings = copy(
        opacity = opacity.coerceIn(0.05f, 1f),
        fontPt = fontPt.coerceIn(6f, 36f),
        marginMm = marginMm.coerceIn(2f, 30f),
    )

    companion object { val DEFAULT = WatermarkSettings() }
}

/** Карта «чернил» страницы: доля занятых пикселей в прямоугольнике (координаты страницы, px). */
interface InkMap {
    val width: Int
    val height: Int
    fun inkFraction(x0: Int, y0: Int, x1: Int, y1: Int): Double
}

/**
 * Карта чернил по уменьшенной бинарной маске (true = содержимое: текст, печать, подпись, линии)
 * с интегральным изображением — O(1) на запрос.
 */
class GridInkMap(
    override val width: Int,
    override val height: Int,
    private val maskW: Int,
    private val maskH: Int,
    mask: BooleanArray,
) : InkMap {
    private val integral = IntArray((maskW + 1) * (maskH + 1))

    init {
        require(mask.size == maskW * maskH) { "Размер маски не совпадает" }
        for (y in 0 until maskH) {
            var rowSum = 0
            for (x in 0 until maskW) {
                if (mask[y * maskW + x]) rowSum++
                integral[(y + 1) * (maskW + 1) + (x + 1)] = integral[y * (maskW + 1) + (x + 1)] + rowSum
            }
        }
    }

    override fun inkFraction(x0: Int, y0: Int, x1: Int, y1: Int): Double {
        val mx0 = (x0.toDouble() * maskW / width).toInt().coerceIn(0, maskW)
        val my0 = (y0.toDouble() * maskH / height).toInt().coerceIn(0, maskH)
        val mx1 = ceil(x1.toDouble() * maskW / width).toInt().coerceIn(0, maskW)
        val my1 = ceil(y1.toDouble() * maskH / height).toInt().coerceIn(0, maskH)
        if (mx1 <= mx0 || my1 <= my0) return 0.0
        val w = maskW + 1
        val s = integral[my1 * w + mx1] - integral[my0 * w + mx1] - integral[my1 * w + mx0] + integral[my0 * w + mx0]
        return s.toDouble() / ((mx1 - mx0) * (my1 - my0))
    }
}

data class RectPx(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    fun intersects(o: RectPx) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

enum class PlacementKind { FREE_SPACE, SHIFTED_UP, REDUCED_SIZE, PAGE_EXTENDED }

/**
 * Результат размещения. Координаты — в системе координат ВЫХОДНОЙ страницы
 * (если страница расширена, исходное содержимое сдвинуто вправо на [extendLeftPx]).
 *
 * Отрисовка (Canvas): translate(anchorX, anchorY); rotate(-90f); drawText(TEXT, 0f, 0f) — базовая линия в опорной точке,
 * текст идёт снизу вверх, надстрочная часть букв — левее anchorX.
 * [box] — ограничивающий прямоугольник повёрнутого текста.
 */
data class WatermarkPlacement(
    val box: RectPx,
    val anchorX: Float,
    val anchorY: Float,
    val fontPx: Float,
    val extendLeftPx: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val kind: PlacementKind,
) {
    /** Нужно ли предупредить пользователя об изменении размера экспортируемой страницы. */
    val pageSizeChanged: Boolean get() = extendLeftPx > 0
}

/** Измерение текста платформой: (длина строки, высота строки) в px для заданного размера шрифта в px. */
fun interface TextMeasurer { fun measure(fontPx: Float): Pair<Float, Float> }

/**
 * Поиск свободного места для «В/ч 65384» слева в нижней части страницы.
 *
 *  1. Левое поле с отступом [WatermarkSettings.marginMm], снизу вверх в пределах нижней половины страницы.
 *  2. Если места нет — уменьшение шрифта до 70 % (не меньше 8 pt).
 *  3. Если всё равно нет — страница расширяется полем слева; содержимое не перекрывается и не обрезается.
 */
class WatermarkPlanner(
    /** Допустимая доля «чернил» в зоне знака (шум, точки от сканирования). */
    private val maxInk: Double = 0.002,
    /** Насколько высоко можно подниматься по левому полю (доля высоты от низа). */
    private val searchFraction: Double = 0.5,
) {
    fun plan(pageW: Int, pageH: Int, dpi: Float, ink: InkMap, settings: WatermarkSettings, measure: TextMeasurer): WatermarkPlacement {
        val s = settings.normalized()
        val mmPx = dpi / 25.4f
        val margin = (s.marginMm * mmPx).roundToInt()
        val pad = max(2, (1.5f * mmPx).roundToInt()) // зазор до содержимого
        val step = max(1, (1f * mmPx).roundToInt())
        val baseFontPx = s.fontPt / 72f * dpi
        val minFontPx = max(8f / 72f * dpi, baseFontPx * 0.7f)

        val scales = generateSequence(1.0f) { it - 0.1f }.takeWhile { baseFontPx * it >= minFontPx - 0.01f }.toList()
        for ((si, scale) in scales.withIndex()) {
            val fontPx = baseFontPx * scale
            val (len, hgt) = measure.measure(fontPx)
            val boxW = ceil(hgt).toInt()
            val boxH = ceil(len).toInt()
            if (margin + boxW >= pageW || margin + boxH >= pageH) continue
            val left = margin
            val lowestBottom = pageH - margin
            val highestTop = (pageH * (1 - searchFraction)).toInt()
            var bottom = lowestBottom
            while (bottom - boxH >= highestTop) {
                val box = RectPx(left, bottom - boxH, left + boxW, bottom)
                val probe = RectPx(max(0, box.left - pad), max(0, box.top - pad), min(pageW, box.right + pad), min(pageH, box.bottom + pad))
                if (ink.inkFraction(probe.left, probe.top, probe.right, probe.bottom) <= maxInk) {
                    val kind = when {
                        si > 0 -> PlacementKind.REDUCED_SIZE
                        bottom != lowestBottom -> PlacementKind.SHIFTED_UP
                        else -> PlacementKind.FREE_SPACE
                    }
                    return placement(box, fontPx, hgt, 0, pageW, pageH, kind)
                }
                bottom -= step
            }
        }
        // Расширение страницы: поле слева шириной «отступ + текст + отступ/2».
        val (len, hgt) = measure.measure(baseFontPx)
        val boxW = ceil(hgt).toInt()
        val boxH = min(ceil(len).toInt(), pageH - 2 * margin)
        val ext = margin + boxW + max(pad, margin / 2)
        val box = RectPx(margin, pageH - margin - boxH, margin + boxW, pageH - margin)
        return placement(box, baseFontPx, hgt, ext, pageW + ext, pageH, PlacementKind.PAGE_EXTENDED)
    }

    private fun placement(box: RectPx, fontPx: Float, lineH: Float, ext: Int, outW: Int, outH: Int, kind: PlacementKind) =
        WatermarkPlacement(
            box = box,
            // После поворота на −90° базовая линия текста идёт вертикально; опорная точка — правый нижний угол коробки
            // минус спуск ≈ 22 % высоты строки, чтобы буквы лежали внутри коробки.
            anchorX = box.left + lineH * 0.78f,
            anchorY = box.bottom.toFloat(),
            fontPx = fontPx,
            extendLeftPx = ext,
            outputWidth = outW,
            outputHeight = outH,
            kind = kind,
        )
}

/**
 * Защита от повторного нанесения: экспорт всегда берёт исходник без знака,
 * а этот реестр запрещает использовать как исходник файл, который уже является результатом экспорта.
 */
class WatermarkLedger(private val exportedHashes: MutableSet<String> = mutableSetOf()) {
    fun markExported(sha256: String) { exportedHashes += sha256 }
    fun isAlreadyWatermarked(sha256: String): Boolean = sha256 in exportedHashes
}
