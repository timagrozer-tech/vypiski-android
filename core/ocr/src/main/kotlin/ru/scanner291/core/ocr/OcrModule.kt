package ru.scanner291.core.ocr

import android.graphics.Bitmap
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.scanner291.core.common.model.PageRecipe
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.database.PageTextEntity
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.ProcessedPageLoader
import ru.scanner291.core.storage.scanModeOf
import javax.inject.Inject

/** Страница для OCR — рендер рецепта по копии оригинала до 2400 px; результат — в текстовый слой и индекс. */
class StorageOcrPageSource @Inject constructor(
    private val repo: DocumentRepository,
    private val processed: ProcessedPageLoader,
) : OcrPageSource {
    override suspend fun image(page: PageEntity, recipe: PageRecipe): Bitmap? {
        val mode = scanModeOf(repo.document(page.docId)?.doc?.mode ?: "")
        return processed.load(page, recipe, mode, 2400)
    }
    override suspend fun save(t: PageTextEntity) = repo.saveText(t)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {
    @Binds abstract fun recognizer(impl: TesseractRecognizer): TextRecognizer
    @Binds abstract fun source(impl: StorageOcrPageSource): OcrPageSource
}
