package ru.scanner291.core.common.model

import ru.scanner291.core.common.geometry.Quad

/** Режим сканирования: подсказка формата и фильтра. Не ограничивает типы файлов. */
enum class ScanMode(val title: String, val aspectWOverH: Double?, val defaultFilter: FilterType) {
    A4("Документ A4", 210.0 / 297.0, FilterType.TEXT_BOOST),
    A5("Документ A5", 148.0 / 210.0, FilterType.TEXT_BOOST),
    PASSPORT("Паспорт", 125.0 / 176.0, FilterType.COLOR),
    ID_CARD("Удостоверение", 85.6 / 54.0, FilterType.COLOR),
    CERTIFICATE("Справка", 210.0 / 297.0, FilterType.TEXT_BOOST),
    ORDER("Приказ", 210.0 / 297.0, FilterType.TEXT_BOOST),
    REPORT("Рапорт", 210.0 / 297.0, FilterType.TEXT_BOOST),
    CONTRACT("Договор", 210.0 / 297.0, FilterType.TEXT_BOOST),
    RECEIPT("Чек", null, FilterType.RECEIPT),
    BOOK("Книга", null, FilterType.BOOK),
    TABLE("Таблица", null, FilterType.TEXT_BOOST),
    DRAWING("Чертёж", null, FilterType.BW),
    CUSTOM("Произвольный документ", null, FilterType.COLOR),
}

enum class FilterType(val title: String) {
    ORIGINAL("Оригинал"),
    COLOR("Цветной"),
    BW("Чёрно-белый"),
    GRAY("Серый"),
    TEXT_BOOST("Усиленный текст"),
    PHOTO("Фото"),
    RECEIPT("Чек"),
    BOOK("Книга"),
}

/** Профиль качества экспорта PDF/изображений. */
enum class QualityProfile(val title: String, val maxDpi: Int?, val jpegQuality: Int?) {
    COMPACT("Компактный", 150, 60),
    STANDARD("Стандартный", 200, 80),
    HIGH("Высокое качество", 300, 92),
    /** Без дополнительного сжатия: исходные пиксели, без пережатия JPEG. */
    LOSSLESS("Без дополнительного сжатия", null, null),
}

/** Прямоугольник в нормализованных координатах страницы (0..1). */
data class NRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun intersects(o: NRect): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    fun normalized() = NRect(minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom))
}

sealed interface Annotation {
    data class Text(val id: String, val at: NRect, val text: String, val sizeSp: Float, val argb: Int) : Annotation
    data class Ink(val id: String, val points: List<Pair<Float, Float>>, val widthNorm: Float, val argb: Int) : Annotation
    /** Подпись — растровое изображение из защищённого хранилища, вставленное в рамку. */
    data class Signature(val id: String, val at: NRect, val signatureFileId: String) : Annotation
}

/**
 * Рецепт обработки страницы. Изображение-оригинал не меняется никогда;
 * обработанная версия всегда пересоздаётся по рецепту — поэтому любые изменения обратимы.
 */
data class PageRecipe(
    val corners: Quad? = null,
    val rotation: Int = 0,
    val filter: FilterType = FilterType.TEXT_BOOST,
    /** −1..1 */
    val brightness: Float = 0f,
    /** 0.5..2 */
    val contrast: Float = 1f,
    val removeShadows: Boolean = true,
    val denoise: Boolean = true,
    val annotations: List<Annotation> = emptyList(),
    /** Закрашенные (скрытые) области: вырезаются из изображения И из текстового слоя. */
    val redactions: List<NRect> = emptyList(),
) {
    fun rotatedBy(deg: Int) = copy(rotation = ((rotation + deg) % 360 + 360) % 360)
    fun normalized() = copy(brightness = brightness.coerceIn(-1f, 1f), contrast = contrast.coerceIn(0.5f, 2f))
}

/** Слово OCR с рамкой в нормализованных координатах обработанной страницы. */
data class OcrWord(val text: String, val box: NRect, val confidence: Float)

object Redaction {
    /** Удаляет из текстового слоя все слова, пересекающиеся с закрашенными областями. */
    fun filterWords(words: List<OcrWord>, redactions: List<NRect>): List<OcrWord> =
        if (redactions.isEmpty()) words else words.filter { w -> redactions.none { it.normalized().intersects(w.box) } }
}

/** История правок с отменой и повтором. */
class EditHistory<T>(initial: T, private val limit: Int = 100) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()
    var current: T = initial; private set

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun apply(next: T) {
        if (next == current) return
        undo.addLast(current); if (undo.size > limit) undo.removeFirst()
        current = next; redo.clear()
    }

    fun undo(): T { if (canUndo) { redo.addLast(current); current = undo.removeLast() }; return current }
    fun redo(): T { if (canRedo) { undo.addLast(current); current = redo.removeLast() }; return current }
}
