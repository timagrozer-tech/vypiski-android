package ru.scanner291.core.common.model

import ru.scanner291.core.common.geometry.Quad

/** Режим сканирования: подсказка формата и фильтра. Не ограничивает типы файлов. */
enum class ScanMode(val title: String, val aspectWOverH: Double?, val defaultFilter: FilterType) {
    A4("Документ A4", 210.0 / 297.0, FilterType.HYBRID),
    A5("Документ A5", 148.0 / 210.0, FilterType.HYBRID),
    PASSPORT("Паспорт", 125.0 / 176.0, FilterType.COLOR),
    ID_CARD("Удостоверение", 85.6 / 54.0, FilterType.COLOR),
    CERTIFICATE("Справка", 210.0 / 297.0, FilterType.HYBRID),
    ORDER("Приказ", 210.0 / 297.0, FilterType.HYBRID),
    REPORT("Рапорт", 210.0 / 297.0, FilterType.HYBRID),
    CONTRACT("Договор", 210.0 / 297.0, FilterType.HYBRID),
    RECEIPT("Чек", null, FilterType.RECEIPT),
    BOOK("Книга", null, FilterType.BOOK),
    TABLE("Таблица", null, FilterType.HYBRID),
    DRAWING("Чертёж", null, FilterType.BW),
    CUSTOM("Произвольный документ", null, FilterType.HYBRID),
}

/** Пять основных фильтров экрана «Улучшение» (по ТЗ) — первыми; остальные — дальше в ленте. */
val MAIN_FILTERS: List<FilterType> get() = listOf(FilterType.HYBRID, FilterType.BW, FilterType.GRAY, FilterType.COLOR, FilterType.ORIGINAL)
val FILTER_ORDER: List<FilterType> get() = (MAIN_FILTERS + listOf(FilterType.MAGIC_COLOR, FilterType.NO_SHADOW, FilterType.LIGHTEN)).let { m -> m + FilterType.entries.filter { it !in m } }

enum class FilterType(val title: String) {
    ORIGINAL("Оригинал"),
    COLOR("Цветной"),
    BW("Ч/Б"),
    GRAY("Серый"),
    TEXT_BOOST("Усиленный текст"),
    PHOTO("Фото"),
    RECEIPT("Чек"),
    BOOK("Книга"),
    /** Hybrid HD Pro (этап 8): текст чёрный, фон белый, печати и подписи — в своём цвете. */
    HYBRID("Документ"),
    // Аналоги фильтров CamScanner (2026-10-24), измерены в tools/processing-lab/lab_cam.py. Добавлены в конец —
    // имена в сохранённых рецептах не меняются.
    /** Lighten: светлее, свет и тени не выравниваются — похоже на исходный снимок. */
    LIGHTEN("Осветлить"),
    /** No Shadow: тени убраны, оттенок бумаги сохранён. */
    NO_SHADOW("Без теней"),
    /** Magic Color: белая бумага, сочные цвета везде (логотипы, маркер, фото), резкий текст. */
    MAGIC_COLOR("Яркий цвет"),
}

/**
 * Профиль Hybrid HD Pro — 10 параметров. Значения подобраны и измерены в tools/processing-lab
 * (lab_hybrid.py, README): печати и подписи сохраняются ≥ 0,98 даже в худшем случае.
 */
enum class HybridProfile(
    val title: String,
    val shadows: Boolean,
    val denoise: Boolean,
    /** Гамма текста (больше — темнее). */
    val textGamma: Double,
    /** Сила нерезкой маски для текста. */
    val sharpen: Double,
    /** Доля яркости, выше которой фон становится чисто белым. */
    val bgWhite: Double,
    /** Минимальная насыщенность (0..255) цветных чернил — печать, подпись. */
    val satMin: Int,
    /** Максимальная яркость (0..255) цветных чернил: светлее — это бумага. */
    val inkVMax: Int,
    /** Усиление насыщенности цветных чернил. */
    val colorBoost: Double,
    /** Расширение маски цветных чернил, px (захват сглаженного края). */
    val maskGrow: Int,
    /** Текст строго чёрно-белый (с мягким краем). */
    val textBw: Boolean,
    /**
     * Уровни «как у сканера» (2026-10-23): белая точка — по фону ([bgWhite]), чёрная — по тексту, гамма [textGamma];
     * сгибы и зерно бумаги уходят в белое. Измерено (tools/processing-lab/lab_doc.py): фон 255, CER 6.4 % → 2.4 %.
     */
    val scannerLevels: Boolean = false,
) {
    DOCUMENT("Документ", true, true, 1.4, 0.6, 0.88, 60, 235, 1.3, 2, false, scannerLevels = true),
    CONTRAST("Контраст", true, true, 2.4, 0.7, 0.88, 60, 235, 1.4, 2, true),
    SOFT("Мягкий", true, true, 1.3, 0.3, 0.95, 60, 235, 1.1, 1, false),
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
    /** Профиль Hybrid HD Pro (используется при filter = HYBRID). */
    val hybrid: HybridProfile = HybridProfile.DOCUMENT,
    /** Ручные параметры (этап 9) поверх фильтра. */
    val adjust: ru.scanner291.core.common.filters.Adjustments = ru.scanner291.core.common.filters.Adjustments.NEUTRAL,
    /** id выбранного пресета (для подсветки на экране); null — настроено вручную. */
    val preset: String? = null,
    val annotations: List<Annotation> = emptyList(),
    /** Закрашенные (скрытые) области: вырезаются из изображения И из текстового слоя. */
    val redactions: List<NRect> = emptyList(),
) {
    fun rotatedBy(deg: Int) = copy(rotation = ((rotation + deg) % 360 + 360) % 360)
    fun normalized() = copy(brightness = brightness.coerceIn(-1f, 1f), contrast = contrast.coerceIn(0.5f, 2f), adjust = adjust.normalized())
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

    /** Заменить текущее значение без новой записи в истории (непрерывное движение ползунка). */
    fun replaceCurrent(next: T) { current = next; redo.clear() }

    fun undo(): T { if (canUndo) { redo.addLast(current); current = undo.removeLast() }; return current }
    fun redo(): T { if (canRedo) { undo.addLast(current); current = redo.removeLast() }; return current }
}
