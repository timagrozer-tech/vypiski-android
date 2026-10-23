package ru.scanner291.core.ocr

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.scanner291.core.common.model.FilterType
import ru.scanner291.core.common.model.PageRecipe
import ru.scanner291.core.common.render.RecipeCodec
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.database.PageTextEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Откуда OCR берёт изображение страницы и куда кладёт результат (в приложении — хранилище и рендер). */
interface OcrPageSource {
    /** Обработанная страница для OCR по рецепту [recipe] (сторона до ~2400 px); null — не открылась. */
    suspend fun image(page: PageEntity, recipe: PageRecipe): Bitmap?
    suspend fun save(t: PageTextEntity)
}

/**
 * OCR страниц. Вход — страница после обработки: фильтры «Усиленный текст», Hybrid, ЧБ и «Чек» берутся
 * как есть, остальные заменяются на «Усиленный текст» (замер: CER 14.9 % на снимке → 1.8 % после обработки,
 * reports/ocr-metrics.md). Ручные параметры для OCR не применяются. Текст в журнал не пишется.
 */
@Singleton
class OcrService @Inject constructor(
    private val recognizer: TextRecognizer,
    private val source: OcrPageSource,
) {
    suspend fun recognizePage(page: PageEntity, pageRecipe: PageRecipe, mode: OcrMode): Result<PageTextEntity> = withContext(Dispatchers.Default) {
        runCatching {
            val r = ocrRecipe(pageRecipe)
            val bmp = source.image(page, r) ?: error("Изображение страницы не открылось")
            val res = recognizer.recognize(bmp, mode)
            val t = PageTextEntity(
                pageId = page.id, docId = page.docId, text = res.text, words = WordsCodec.encode(res.words),
                mode = mode.name, recipeKey = RecipeCodec.cacheKey(pageRecipe), edited = false,
                createdAt = System.currentTimeMillis(), durationMs = res.durationMs,
            )
            source.save(t)
            OcrLog.done(page.id, mode, res.text.length, res.words.size, res.durationMs)
            t
        }.onFailure { OcrLog.failed(page.id, it) }
    }

    companion object {
        fun ocrRecipe(r: PageRecipe): PageRecipe = r.copy(
            filter = if (r.filter in setOf(FilterType.TEXT_BOOST, FilterType.HYBRID, FilterType.BW, FilterType.RECEIPT)) r.filter else FilterType.TEXT_BOOST,
            adjust = ru.scanner291.core.common.filters.Adjustments.NEUTRAL, brightness = 0f, contrast = 1f,
        )
    }
}
