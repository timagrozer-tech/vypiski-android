package ru.scanner291.core.common.filters

import ru.scanner291.core.common.model.FilterType
import ru.scanner291.core.common.model.HybridProfile
import ru.scanner291.core.common.model.PageRecipe

/** Группа пресетов на экране. */
enum class PresetGroup(val title: String) { DOCUMENT("Документы"), BW("Чёрно-белые"), COLOR("Цвет"), PHOTO("Фото"), SPECIAL("Особые"), USER("Мои") }

/**
 * Пресет — базовый фильтр + профиль Hybrid + ручные параметры. [id] стабилен (пишется в рецепт);
 * пользовательские пресеты имеют id «u:…».
 */
data class Preset(
    val id: String,
    val title: String,
    val group: PresetGroup,
    val filter: FilterType,
    val adjust: Adjustments = Adjustments.NEUTRAL,
    val hybrid: HybridProfile = HybridProfile.DOCUMENT,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
) {
    /** Применить к рецепту: углы, поворот, тени и шум остаются как были. */
    fun applyTo(r: PageRecipe): PageRecipe =
        r.copy(filter = filter, hybrid = hybrid, adjust = adjust, brightness = brightness, contrast = contrast, preset = id).normalized()

    /** Совпадает ли рецепт с пресетом (для подсветки выбранного). */
    fun matches(r: PageRecipe): Boolean =
        r.filter == filter && r.adjust == adjust && r.brightness == brightness && r.contrast == contrast &&
            (filter != FilterType.HYBRID || r.hybrid == hybrid)

    /** «Мой» пресет в строку для хранения: `u:<id>|<название>|<рецепт>`; название без «|». */
    fun encodeUser(): String = "$id|${title.replace('|', ' ')}|" + listOf(filter.name, hybrid.name, adjust.encode(), brightness, contrast).joinToString(";")

    companion object {
        fun decodeUser(s: String): Preset? = runCatching {
            val (id, title, body) = s.split('|', limit = 3)
            val p = body.split(';')
            Preset(id, title, PresetGroup.USER, FilterType.valueOf(p[0]), decodeAdjustments(p[2]), HybridProfile.valueOf(p[1]), p[3].toFloat(), p[4].toFloat())
        }.getOrNull()

        fun fromRecipe(id: String, title: String, r: PageRecipe) =
            Preset(id, title.trim().ifEmpty { "Мой пресет" }, PresetGroup.USER, r.filter, r.adjust, r.hybrid, r.brightness, r.contrast)
    }
}

/**
 * Встроенная библиотека (этап 9): 36 пресетов. Любые два различимы: средняя разница ≥ [Presets.MIN_DIFF]
 * хотя бы на одном эталоне (PresetsTest — для пресетов с одинаковым базовым фильтром; разные базовые
 * фильтры различаются сами — tools/processing-lab).
 */
object Presets {
    const val MIN_DIFF = 2.0

    private fun p(id: String, title: String, g: PresetGroup, f: FilterType, a: Adjustments = Adjustments.NEUTRAL, h: HybridProfile = HybridProfile.DOCUMENT) =
        Preset(id, title, g, f, a, h)

    val ALL: List<Preset> = listOf(
        // Документы
        p("doc", "Документ", PresetGroup.DOCUMENT, FilterType.TEXT_BOOST),
        p("doc_bright", "Документ — светлее", PresetGroup.DOCUMENT, FilterType.TEXT_BOOST, Adjustments(exposure = 0.3f, shadows = 0.3f)),
        p("doc_sharp", "Документ — чёткий", PresetGroup.DOCUMENT, FilterType.TEXT_BOOST, Adjustments(sharpness = 0.8f, clarity = 0.4f)),
        p("doc_bold", "Документ — жирный текст", PresetGroup.DOCUMENT, FilterType.TEXT_BOOST, Adjustments(textWeight = 1, blackPoint = 0.2f)),
        p("hybrid", "Hybrid HD — документ", PresetGroup.DOCUMENT, FilterType.HYBRID),
        p("hybrid_contrast", "Hybrid HD — контраст", PresetGroup.DOCUMENT, FilterType.HYBRID, h = HybridProfile.CONTRAST),
        p("hybrid_soft", "Hybrid HD — мягкий", PresetGroup.DOCUMENT, FilterType.HYBRID, h = HybridProfile.SOFT),
        p("hybrid_stamp", "Hybrid HD — яркая печать", PresetGroup.DOCUMENT, FilterType.HYBRID, Adjustments(saturation = 0.8f), HybridProfile.CONTRAST),
        p("table", "Таблица", PresetGroup.DOCUMENT, FilterType.TEXT_BOOST, Adjustments(textWeight = 1, sharpness = 0.5f, whitePoint = 0.3f)),
        p("passport", "Паспорт / удостоверение", PresetGroup.DOCUMENT, FilterType.COLOR, Adjustments(sharpness = 0.4f, clarity = 0.2f, vibrance = 0.3f)),
        // Чёрно-белые
        p("bw", "Чёрно-белый", PresetGroup.BW, FilterType.BW),
        p("bw_xerox", "Ксерокопия", PresetGroup.BW, FilterType.BW, Adjustments(textWeight = 1)),
        p("bw_thin", "Чёрно-белый — тонкий", PresetGroup.BW, FilterType.BW, Adjustments(textWeight = -1)),
        p("receipt", "Чек", PresetGroup.BW, FilterType.RECEIPT),
        p("receipt_faded", "Чек — выцветший", PresetGroup.BW, FilterType.RECEIPT, Adjustments(textWeight = 1)),
        p("gray", "Серый", PresetGroup.BW, FilterType.GRAY),
        p("gray_contrast", "Серый — контрастный", PresetGroup.BW, FilterType.GRAY, Adjustments(blackPoint = 0.6f, whitePoint = 0.5f)),
        p("toner_saver", "Экономия тонера", PresetGroup.BW, FilterType.GRAY, Adjustments(fade = 0.6f, textWeight = -1)),
        // Цвет
        p("color", "Цветной", PresetGroup.COLOR, FilterType.COLOR),
        p("color_vivid", "Цветной — насыщенный", PresetGroup.COLOR, FilterType.COLOR, Adjustments(saturation = 0.6f)),
        p("color_warm", "Цветной — тёплый", PresetGroup.COLOR, FilterType.COLOR, Adjustments(warmth = 0.6f)),
        p("color_cool", "Цветной — холодный", PresetGroup.COLOR, FilterType.COLOR, Adjustments(warmth = -0.6f)),
        p("blue_ink", "Синие чернила", PresetGroup.COLOR, FilterType.COLOR, Adjustments(vibrance = 0.8f, blackPoint = 0.3f)),
        p("whiteboard", "Доска и маркер", PresetGroup.COLOR, FilterType.COLOR, Adjustments(clarity = 0.8f, saturation = 0.5f, whitePoint = 0.6f)),
        p("book", "Книга", PresetGroup.COLOR, FilterType.BOOK),
        p("book_sepia", "Книга — сепия", PresetGroup.COLOR, FilterType.BOOK, Adjustments(monochrome = 1f, warmth = 0.7f)),
        p("old_doc", "Старый документ", PresetGroup.COLOR, FilterType.COLOR, Adjustments(clarity = 0.6f, shadows = 0.5f, vibrance = -0.5f)),
        // Фото
        p("photo", "Фото", PresetGroup.PHOTO, FilterType.PHOTO),
        p("photo_bright", "Фото — яркое", PresetGroup.PHOTO, FilterType.PHOTO, Adjustments(exposure = 0.3f, vibrance = 0.4f)),
        p("photo_bw", "Фото — ЧБ", PresetGroup.PHOTO, FilterType.PHOTO, Adjustments(monochrome = 1f, clarity = 0.3f)),
        p("photo_soft", "Фото — мягкое", PresetGroup.PHOTO, FilterType.PHOTO, Adjustments(fade = 0.5f, smoothing = 0.6f)),
        p("photo_punch", "Фото — контрастное", PresetGroup.PHOTO, FilterType.PHOTO, Adjustments(blackPoint = 0.4f, whitePoint = 0.4f, clarity = 0.5f)),
        // Особые
        p("original", "Оригинал", PresetGroup.SPECIAL, FilterType.ORIGINAL),
        p("original_lift", "Оригинал — светлее", PresetGroup.SPECIAL, FilterType.ORIGINAL, Adjustments(exposure = 0.4f, shadows = 0.6f)),
        p("night", "Снимок при слабом свете", PresetGroup.SPECIAL, FilterType.ORIGINAL, Adjustments(exposure = 0.7f, smoothing = 0.8f, shadows = 0.6f)),
        p("drawing", "Чертёж", PresetGroup.SPECIAL, FilterType.GRAY, Adjustments(blackPoint = 1f, sharpness = 1f, textWeight = 1)),
    )

    fun byId(id: String?, user: List<Preset> = emptyList()): Preset? = id?.let { i -> ALL.firstOrNull { it.id == i } ?: user.firstOrNull { it.id == i } }
}
