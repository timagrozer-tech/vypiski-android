package ru.scanner291.core.storage

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import ru.scanner291.core.common.files.FileKind
import ru.scanner291.core.common.files.ImportCheck
import ru.scanner291.core.common.files.ImportLimits
import ru.scanner291.core.common.files.FileSignature
import ru.scanner291.core.common.files.checkImport
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.common.storage.AtomicFileWriter
import ru.scanner291.core.common.storage.Hashing
import ru.scanner291.core.common.storage.OperationJournal
import ru.scanner291.core.common.storage.OriginalStore
import ru.scanner291.core.database.DocumentEntity
import ru.scanner291.core.database.DocumentSource
import ru.scanner291.core.database.DocumentSummary
import ru.scanner291.core.database.DocumentWithPages
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.database.ScannerDatabase
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Отклонённый при импорте файл с понятной причиной. */
data class Rejection(val name: String, val reason: String)

data class ImportResult(val documentIds: List<String>, val importedFiles: Int, val rejected: List<Rejection>)

/** Отказ импорта, обнаруженный уже после копирования (не читается, слишком большой и т. п.). */
class ImportRejectedException(val reason: String) : IOException(reason)

/**
 * Документы и их неизменяемые оригиналы (ТЗ v3.0 ч. X: Original → Recipe → Preview → Export).
 *
 * Сохранение страницы: журнал «намерение» → атомарная запись оригинала (только чтение) → SHA-256 →
 * запись в БД → закрытие журнала. Сбой на любом шаге не оставляет «полудокументов»: при старте
 * незавершённая операция откатывается (удаляется только её новый файл).
 */
@Singleton
class DocumentRepository @Inject constructor(
    private val db: ScannerDatabase,
    private val paths: DocumentPaths,
    private val journal: OperationJournal,
    private val resolver: ContentResolver,
) {
    private val dao = db.documentDao()

    fun observeDocuments(): Flow<List<DocumentSummary>> = dao.observeSummaries()
    fun observeDocument(id: String): Flow<DocumentWithPages?> = dao.observeDocument(id)
    suspend fun document(id: String): DocumentWithPages? = dao.document(id)
    suspend fun page(id: String): PageEntity? = dao.page(id)

    /** Углы листа (рецепт обработки). Оригинал не меняется — меняется только индекс. */
    suspend fun setCorners(pageId: String, quad: ru.scanner291.core.common.geometry.Quad?, source: ru.scanner291.core.database.CornersSource?) =
        withContext(Dispatchers.IO) {
            dao.setCorners(pageId, quad?.let { ru.scanner291.core.common.geometry.QuadCodec.encode(it) }, source?.name)
        }
    fun originalFile(page: PageEntity): File = paths.absolute(page.originalPath)

    suspend fun createDocument(mode: ScanMode, source: DocumentSource, title: String, now: Long = System.currentTimeMillis()): String =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            dao.insertDocument(DocumentEntity(id, title, mode.name, source.name, now, now))
            id
        }

    /** Страница с камеры: JPEG из временного файла становится оригиналом без изменений. Временный файл удаляется. */
    suspend fun addCapturedPage(docId: String, temp: File, now: Long = System.currentTimeMillis()): PageEntity =
        withContext(Dispatchers.IO) {
            try {
                AtomicFileWriter.requireFreeSpace(paths.docs, temp.length())
                storeOriginalAndIndex(docId, FileKind.JPEG, now) { out -> temp.inputStream().use { it.copyTo(out) } }.single()
            } finally {
                temp.delete()
            }
        }

    /**
     * Пересъёмка: страница убирается из документа, а её оригинал переносится в корзину (не удаляется).
     * Порядок «сначала перенос, потом БД»: при сбое между шагами файл цел в корзине, а «потерянная
     * ссылка» будет найдена центром диагностики (этап 18).
     */
    suspend fun trashPage(pageId: String) = withContext(Dispatchers.IO) {
        val page = dao.page(pageId) ?: return@withContext
        val src = paths.absolute(page.originalPath)
        val dst = paths.trashed(page.docId, src.name)
        dst.parentFile?.mkdirs()
        if (src.exists() && !src.renameTo(dst)) throw IOException("Не удалось перенести страницу в корзину")
        db.withTransaction {
            dao.deletePage(pageId)
            dao.touch(page.docId, System.currentTimeMillis())
        }
    }

    /**
     * Импорт файлов, выбранных через системный диалог. Все изображения — один многостраничный документ,
     * каждый PDF — отдельный документ. Каждый файл проверяется по схеме URI, размеру и сигнатуре
     * до копирования и по содержимому после; отклонённые возвращаются с причиной.
     */
    suspend fun import(uris: List<Uri>, mode: ScanMode, imageDocTitle: String, now: Long = System.currentTimeMillis()): ImportResult =
        withContext(Dispatchers.IO) {
            val rejected = mutableListOf<Rejection>()
            val docIds = mutableListOf<String>()
            var imageDoc: String? = null
            var imported = 0
            for (uri in uris) {
                val name = displayName(uri)
                try {
                    val header = readHeader(uri)
                    when (val check = checkImport(uri.scheme, size(uri), header)) {
                        is ImportCheck.Rejected -> rejected += Rejection(name, check.reason)
                        is ImportCheck.Ok -> {
                            if (check.kind.isImage) {
                                val doc = imageDoc ?: createDocument(mode, DocumentSource.IMPORT_IMAGE, imageDocTitle, now).also {
                                    imageDoc = it; docIds += it
                                }
                                storeOriginalAndIndex(doc, check.kind, now) { out -> copyLimited(uri, out) }
                            } else {
                                val title = name.substringBeforeLast('.').ifBlank { imageDocTitle }
                                val doc = createDocument(mode, DocumentSource.IMPORT_PDF, title, now)
                                try {
                                    storeOriginalAndIndex(doc, FileKind.PDF, now) { out -> copyLimited(uri, out) }
                                    docIds += doc
                                } catch (e: Exception) {
                                    dao.deleteDocument(doc) // документ создан этим же импортом и пуст
                                    throw e
                                }
                            }
                            imported++
                        }
                    }
                } catch (e: ImportRejectedException) {
                    rejected += Rejection(name, e.reason)
                } catch (e: SecurityException) {
                    rejected += Rejection(name, "Нет доступа к файлу")
                } catch (e: IOException) {
                    Log.w(TAG, "import failed", e)
                    rejected += Rejection(name, "Не удалось прочитать файл")
                }
            }
            // Все изображения отклонены — пустой документ не оставляем.
            imageDoc?.let { id -> if (dao.pageCount(id) == 0) { dao.deleteDocument(id); docIds.remove(id) } }
            ImportResult(docIds, imported, rejected)
        }

    /** Сохранение оригинала и индексация страниц (для PDF — по странице на каждую страницу файла). */
    private suspend fun storeOriginalAndIndex(docId: String, kind: FileKind, now: Long, write: (OutputStream) -> Unit): List<PageEntity> {
        val fileId = UUID.randomUUID().toString()
        val target = paths.original(docId, fileId, kind.extension)
        target.parentFile?.mkdirs()
        val op = journal.begin("store_original", listOf(target), mapOf("docId" to docId))
        try {
            OriginalStore.store(target, write)
            val sha = Hashing.sha256(target)
            val rel = paths.relative(target)
            val pages: List<PageEntity> = if (kind == FileKind.PDF) {
                pdfPageSizes(target).mapIndexed { i, (w, h) -> PageEntity(UUID.randomUUID().toString(), docId, 0, rel, sha, kind.mime, target.length(), w, h, i, now) }
            } else {
                val (w, h) = imageSize(target)
                listOf(PageEntity(fileId, docId, 0, rel, sha, kind.mime, target.length(), w, h, null, now))
            }
            val stored = db.withTransaction {
                val start = dao.maxPosition(docId) + 1
                val withPos = pages.mapIndexed { i, p -> p.copy(position = start + i) }
                dao.insertPages(withPos)
                dao.touch(docId, now)
                withPos
            }
            journal.commit(op)
            return stored
        } catch (e: Exception) {
            target.setWritable(true); target.delete()
            journal.commit(op)
            throw e
        }
    }

    /** Размеры с учётом EXIF-поворота; файл, который не декодируется, отклоняется. */
    private fun imageSize(f: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var w = o.outWidth
        var h = o.outHeight
        val exif = runCatching { ExifInterface(f.path) }.getOrNull()
        if (w <= 0 || h <= 0) {
            // Декодер платформы не всегда сообщает размеры (некоторые HEIC/JPEG-варианты); берём их из
            // заголовка (SOF) / EXIF. Файл без размеров — повреждён.
            w = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0) ?: 0
            h = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0) ?: 0
        }
        if (w <= 0 || h <= 0) throw ImportRejectedException("Изображение повреждено или формат не поддерживается устройством")
        if (w > ImportLimits.MAX_IMAGE_SIDE || h > ImportLimits.MAX_IMAGE_SIDE) {
            throw ImportRejectedException("Слишком большое разрешение изображения")
        }
        val rot = exif?.rotationDegrees ?: 0
        return if (rot == 90 || rot == 270) h to w else w to h
    }

    /** Размеры страниц PDF в пунктах; зашифрованный или повреждённый PDF отклоняется. */
    private fun pdfPageSizes(f: File): List<Pair<Int, Int>> = try {
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                if (r.pageCount <= 0) throw ImportRejectedException("В PDF нет страниц")
                if (r.pageCount > ImportLimits.MAX_PDF_PAGES) throw ImportRejectedException("В PDF больше ${ImportLimits.MAX_PDF_PAGES} страниц")
                (0 until r.pageCount).map { i -> r.openPage(i).use { it.width to it.height } }
            }
        }
    } catch (e: SecurityException) {
        throw ImportRejectedException("PDF защищён паролем")
    } catch (e: ImportRejectedException) {
        throw e
    } catch (e: Exception) {
        throw ImportRejectedException("PDF повреждён")
    }

    private fun readHeader(uri: Uri): ByteArray =
        resolver.openInputStream(uri)?.use { s -> s.readNBytesCompat(FileSignature.HEADER_SIZE) } ?: ByteArray(0)

    /** Копирование с жёстким лимитом: размер из провайдера может быть неизвестен или неверен. */
    private fun copyLimited(uri: Uri, out: OutputStream) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Нет потока")
        input.use { s ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                total += n
                if (total > ImportLimits.MAX_FILE_BYTES) throw ImportRejectedException("Файл больше 200 МБ")
                out.write(buf, 0, n)
            }
        }
    }

    private fun size(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    private fun displayName(uri: Uri): String = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "файл"

    private companion object { const val TAG = "Scanner291Docs" }
}

internal fun InputStream.readNBytesCompat(n: Int): ByteArray {
    val buf = ByteArray(n)
    var off = 0
    while (off < n) {
        val r = read(buf, off, n - off)
        if (r < 0) break
        off += r
    }
    return buf.copyOf(off)
}
