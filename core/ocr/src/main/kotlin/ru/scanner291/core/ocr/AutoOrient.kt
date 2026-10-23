package ru.scanner291.core.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.imageprocessing.PageOrientation
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.ProcessedPageLoader
import ru.scanner291.core.storage.recipeFor
import ru.scanner291.core.storage.scanModeOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Автоповорот страницы (2026-10-23): EXIF учитывается при чтении оригинала; здесь — поворот самого текста.
 * Только для страниц без сохранённого рецепта (пользователь ещё ничего не настраивал); результат — поворот
 * в рецепте, оригинал не меняется. Шаг 1 — строки горизонтальны/вертикальны (PageOrientation),
 * шаг 2 — 0° или 180° по уверенности OCR на средней полосе страницы. Проверено: 48/48 (lab_orient.py).
 */
@Singleton
class AutoOrient @Inject constructor(
    private val repo: DocumentRepository,
    private val processed: ProcessedPageLoader,
    private val ocr: TesseractRecognizer,
) {
    private val lock = Mutex()

    /** @return поворот, который был применён (0 — страница уже прямая), или null — проверка не выполнялась. */
    suspend fun orient(page: PageEntity): Int? = lock.withLock {
        if (page.recipe != null) return@withLock null
        withContext(Dispatchers.Default) {
            runCatching {
                val d = repo.document(page.docId) ?: return@runCatching null
                val mode = scanModeOf(d.doc.mode)
                val base = page.recipeFor(mode)
                val bmp = processed.load(page, base, mode, 1000) ?: return@runCatching null
                val vertical = PageOrientation.linesVertical(bmp) ?: return@runCatching null
                val candidates = if (vertical) listOf(90, 270) else listOf(0, 180)
                val scores = candidates.associateWith { r -> ocr.confidence(strip(rotate(bmp, r))) }
                val best = scores.maxBy { it.value }
                // Уверенно, только если разница заметна; иначе оставить как есть
                val other = scores.values.min()
                val rot = if (best.value - other >= 10) best.key else candidates.first()
                repo.setRecipe(page.id, base.copy(corners = null).rotatedBy(rot))
                rot
            }.getOrNull()
        }
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap = if (deg == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
    private fun strip(b: Bitmap): Bitmap = Bitmap.createBitmap(b, 0, (b.height * 0.2).toInt(), b.width, (b.height * 0.4).toInt())
}
