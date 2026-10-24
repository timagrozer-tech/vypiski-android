package ru.scanner291.core.storage

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ru.scanner291.core.common.geometry.QuadCodec
import ru.scanner291.core.common.model.PageRecipe
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.common.render.RecipeCodec
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.imageprocessing.PageRenderer
import javax.inject.Inject
import javax.inject.Singleton

/** Рецепт страницы: сохранённый или по умолчанию для режима документа; углы — из столбцов страницы. */
fun PageEntity.recipeFor(mode: ScanMode): PageRecipe {
    val q = QuadCodec.decode(corners)
    return RecipeCodec.decode(recipe, q) ?: RecipeCodec.default(mode, q)
}

/** Режим документа по строке из БД (неизвестное значение → произвольный документ). */
fun scanModeOf(name: String): ScanMode = runCatching { ScanMode.valueOf(name) }.getOrDefault(ScanMode.CUSTOM)

/**
 * Обработанные изображения страниц для экрана: рендер рецепта по уменьшенной копии оригинала
 * (сторона [maxSide]). Оригинал только читается. Кэш по ключу рецепта: одинаковый рецепт → тот же Bitmap;
 * не больше одного рендера одновременно (память).
 */
@Singleton
class ProcessedPageLoader @Inject constructor(private val loader: PageImageLoader) {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val gate = Semaphore(1)

    suspend fun load(page: PageEntity, recipe: PageRecipe, mode: ScanMode, maxSide: Int): Bitmap? {
        val key = "${page.id}#${page.pdfPageIndex ?: -1}@$maxSide|${RecipeCodec.cacheKey(recipe)}|${mode.name}"
        cache.get(key)?.let { return it }
        return gate.withPermit {
            cache.get(key) ?: withContext(Dispatchers.Default) {
                // Нехватка памяти: размер понижается (×0.7) и рендер повторяется, до 3 раз
                var side = maxSide; var out: Bitmap? = null
                for (attempt in 0 until 3) {
                    try {
                        val src = loader.load(page, side) ?: break
                        out = PageRenderer.render(src, recipe, mode.aspectWOverH); break
                    } catch (e: OutOfMemoryError) { side = (side * 0.7).toInt(); System.gc() }
                    catch (e: Exception) { break }
                }
                out
            }?.also { cache.put(key, it) }
        }
    }
}

/**
 * Миниатюры для списков (2026-10-24): обработанная страница по рецепту — так, как документ выглядит в
 * просмотре и в PDF; если обработка недоступна — уменьшенный оригинал.
 */
@Singleton
class ThumbnailLoader @Inject constructor(
    private val repo: DocumentRepository,
    private val loader: PageImageLoader,
    private val processed: ProcessedPageLoader,
) {
    suspend fun load(pageId: String?, side: Int = 240): Bitmap? {
        val page = pageId?.let { repo.page(it) } ?: return null
        val mode = scanModeOf(repo.document(page.docId)?.doc?.mode ?: "")
        return processed.load(page, page.recipeFor(mode), mode, side) ?: loader.load(page, side)
    }
}
