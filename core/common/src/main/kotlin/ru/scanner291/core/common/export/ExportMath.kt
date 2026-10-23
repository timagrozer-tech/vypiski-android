package ru.scanner291.core.common.export

import ru.scanner291.core.common.model.NRect
import ru.scanner291.core.common.model.OcrWord
import ru.scanner291.core.common.model.QualityProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Шесть режимов экспорта (этап 17). DOCX/XLSX не предлагаются, пока их качество не измерено. */
enum class ExportMode(val title: String, val ext: String, val multiFile: Boolean) {
    PDF("PDF — один файл", "pdf", false),
    PDF_OCR("PDF с текстом (OCR)", "pdf", false),
    PDF_PER_PAGE("PDF — каждая страница отдельно", "pdf", true),
    JPG("JPG — по странице", "jpg", true),
    PNG("PNG — по странице", "png", true),
    TXT("Текст (TXT)", "txt", false),
}

/** Чистая математика экспорта — покрыта ExportMathTest. */
object ExportMath {
    /**
     * Оценка размера JPEG по пробному сжатию копии ([sampleBytes] при [samplePx] пикселях) того же качества:
     * размер ≈ A · пробный · (пиксели/пробные)^B. A и B подобраны в tools/processing-lab/lab_size2.py
     * (576 проверочных страниц): погрешность страницы — медиана 7.9 %, 90 % случаев ≤ 23.8 %;
     * документа из 5 страниц — медиана 4.2 %, 90 % ≤ 11.1 %. Простая модель «пиксели × k» давала 45 %.
     */
    const val SIZE_A = 0.8845
    const val SIZE_B = 0.8
    /** Сторона пробной копии, px. */
    const val SAMPLE_SIDE = 600
    /** Граница погрешности, которую показывает экран (90-й процентиль для страницы, округлён вверх). */
    const val SIZE_ERROR_PERCENT = 25

    fun estimateJpegBytes(sampleBytes: Long, samplePx: Long, targetPx: Long): Long =
        (SIZE_A * sampleBytes * (targetPx.toDouble() / max(1L, samplePx)).pow(SIZE_B)).roundToInt().toLong()

    /** Накладные расходы PDF на страницу (объекты страницы и XObject) — по созданным в тестах файлам ~1–2 КБ. */
    const val PDF_PAGE_OVERHEAD = 2_048L

    /** Ширина страницы в мм: формат режима (A4 по умолчанию), ориентация — по пропорциям изображения. */
    fun pageWidthMm(aspectWOverH: Double?, pxW: Int, pxH: Int): Double {
        val portrait = pxW <= pxH
        return when {
            aspectWOverH == null -> if (portrait) 210.0 else 297.0
            aspectWOverH <= 1.0 -> if (portrait) longSideMmFor(aspectWOverH) * aspectWOverH else longSideMmFor(aspectWOverH)
            else -> if (portrait) longSideMmFor(aspectWOverH) / aspectWOverH else longSideMmFor(aspectWOverH)
        }
    }

    /** Длинная сторона известных форматов (ScanMode): A4, A5, паспорт, удостоверение; иначе — A4. */
    private val FORMATS = listOf(210.0 / 297.0 to 297.0, 148.0 / 210.0 to 210.0, 125.0 / 176.0 to 176.0, 85.6 / 54.0 to 85.6)
    private fun longSideMmFor(aspect: Double): Double = FORMATS.firstOrNull { kotlin.math.abs(it.first - aspect) < 0.0005 }?.second ?: 297.0

    /** Длинная сторона результата в пикселях для профиля качества (без профиля — до 4200 px, без увеличения). */
    fun targetLongSide(profile: QualityProfile, longSideMm: Double, nativeLongSide: Int): Int {
        val byDpi = profile.maxDpi?.let { (it * longSideMm / 25.4).roundToInt() } ?: 4200
        return min(min(byDpi, 4200), nativeLongSide).coerceAtLeast(1)
    }

    fun dpi(pxW: Int, widthMm: Double): Float = (pxW / (widthMm / 25.4)).toFloat()

    /** Слова OCR после расширения страницы полем слева на [extendPx] (водяной знак): координаты сдвигаются. */
    fun shiftWords(words: List<OcrWord>, oldW: Int, extendPx: Int): List<OcrWord> {
        if (extendPx <= 0) return words
        val nw = (oldW + extendPx).toFloat()
        return words.map { w -> w.copy(box = NRect((w.box.left * oldW + extendPx) / nw, w.box.top, (w.box.right * oldW + extendPx) / nw, w.box.bottom)) }
    }

    /** Имя файла страницы в многофайловом режиме: «Иванов И.И. (стр. 2)». */
    fun pageBase(base: String, index: Int, total: Int): String = if (total == 1) base else "$base (стр. ${index + 1})"

    /** Текст для TXT: страницы с заголовками; пустые страницы помечаются. */
    fun txt(pages: List<String?>): String = pages.mapIndexed { i, t ->
        "— Страница ${i + 1} —\n" + (t?.takeIf { it.isNotBlank() } ?: "(текст не распознан)")
    }.joinToString("\n\n") + "\n"
}
