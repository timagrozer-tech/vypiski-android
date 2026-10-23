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
        val src = loader.load(page, maxSide) ?: return null
        return gate.withPermit {
            cache.get(key) ?: withContext(Dispatchers.Default) {
                runCatching { PageRenderer.render(src, recipe, mode.aspectWOverH) }.getOrNull()
            }?.also { cache.put(key, it) }
        }
    }
}
