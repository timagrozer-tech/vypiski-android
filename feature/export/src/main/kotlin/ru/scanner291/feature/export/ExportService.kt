package ru.scanner291.feature.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ru.scanner291.core.common.export.ExportMath
import ru.scanner291.core.common.export.ExportMode
import ru.scanner291.core.common.model.QualityProfile
import ru.scanner291.core.common.model.Redaction
import ru.scanner291.core.common.storage.AtomicFileWriter
import ru.scanner291.core.common.storage.Hashing
import ru.scanner291.core.common.watermark.WatermarkSettings
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.imageprocessing.PageRenderer
import ru.scanner291.core.imageprocessing.WatermarkRenderer
import ru.scanner291.core.ocr.WordsCodec
import ru.scanner291.core.pdf.AnnotationRenderer
import ru.scanner291.core.pdf.PdfEngine
import ru.scanner291.core.pdf.PdfPageInput
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.PageImageLoader
import ru.scanner291.core.storage.ProcessedPageLoader
import ru.scanner291.core.storage.prefs.UserPreferencesRepository
import ru.scanner291.core.storage.recipeFor
import ru.scanner291.core.storage.scanModeOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

data class ExportOptions(
    val mode: ExportMode,
    val profile: QualityProfile,
    val watermark: WatermarkSettings,
    /** Имя без расширения (уже очищенное). */
    val baseName: String,
)

/** Готовый (проверенный) результат во временной папке; дальше — копирование в выбранное место. */
data class ExportResult(val files: List<Pair<String, File>>, val warnings: List<String>, val totalBytes: Long, val pageSizeChanged: Boolean, val msPerPage: Long = 0)

/**
 * Smart Export (этап 17). Страницы строятся заново из оригиналов: рецепт → закрашивание → аннотации →
 * водяной знак → формат. Перед «успешно» каждый файл проверяется (PDF переоткрывается, число страниц и
 * текст; изображения декодируются; TXT сверяется). Знак не наносится повторно: реестр SHA-256 экспортов и
 * метки в метаданных (PDF Info / EXIF).
 */
@Singleton
class ExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: DocumentRepository,
    private val loader: PageImageLoader,
    private val processed: ProcessedPageLoader,
    private val prefs: UserPreferencesRepository,
    private val perf: ru.scanner291.core.performance.AdaptivePerformance,
) {
    private val pdf by lazy { PdfEngine(context) }
    private fun workDir() = File(context.cacheDir, "export/${UUID.randomUUID()}").apply { mkdirs() }

    /** Оценка размера (байт) по пробному сжатию копий 600 px; null — для PNG/«без сжатия» не оценивается. */
    suspend fun estimate(docId: String, opts: ExportOptions): Long? = withContext(Dispatchers.Default) {
        val q = opts.profile.jpegQuality ?: return@withContext null
        if (opts.mode == ExportMode.PNG) return@withContext null
        val d = repo.document(docId) ?: return@withContext null
        val mode = scanModeOf(d.doc.mode)
        val pages = d.orderedPages
        if (opts.mode == ExportMode.TXT) return@withContext pages.sumOf { (repo.text(it.id)?.text?.toByteArray()?.size ?: 30).toLong() + 20 }
        var total = 0L
        // До 5 страниц пробуются, остальные — по среднему (оценка документа из 5 стр. ±11 % в 90 % случаев)
        val sample = pages.take(5)
        for (p in sample) {
            val small = processed.load(p, p.recipeFor(mode), mode, ExportMath.SAMPLE_SIDE) ?: return@withContext null
            val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, q, it) }.size().toLong()
            val long = targetLongSide(p, opts.profile, mode.aspectWOverH)
            val scale = long.toDouble() / max(small.width, small.height)
            val targetPx = (small.width * scale * small.height * scale).toLong()
            total += ExportMath.estimateJpegBytes(bytes, small.width.toLong() * small.height, targetPx) +
                if (opts.mode != ExportMode.JPG) ExportMath.PDF_PAGE_OVERHEAD else 0
        }
        if (sample.isEmpty()) 0L else total * pages.size / sample.size
    }

    private fun targetLongSide(p: PageEntity, profile: QualityProfile, aspect: Double?): Int {
        val nativeLong = max(p.width, p.height).coerceAtLeast(1)
        val wMm = ExportMath.pageWidthMm(aspect, p.width.coerceAtLeast(1), p.height.coerceAtLeast(1))
        val longMm = if (p.width <= p.height) wMm * p.height / p.width.coerceAtLeast(1) else wMm
        // Слабое устройство: сторона не больше 3000 px (страница ARGB 4200×5900 = 99 МБ — риск нехватки памяти)
        return minOf(ExportMath.targetLongSide(profile, longMm, nativeLong), perf.budget.value.exportMaxLongSide)
    }

    /** Подготовленная страница: изображение, слова (с учётом закрашивания и поля знака), dpi. */
    private class Ready(val bitmap: Bitmap, val dpi: Float, val words: List<ru.scanner291.core.common.model.OcrWord>, val text: String?, val extended: Boolean)

    private suspend fun preparePage(p: PageEntity, mode: ru.scanner291.core.common.model.ScanMode, opts: ExportOptions, ledger: Set<String>): Ready {
        var long = targetLongSide(p, opts.profile, mode.aspectWOverH)
        // Нехватка памяти на большой странице: разрешение понижается (×0.7) и попытка повторяется (до 3 раз)
        repeat(2) {
            try { return preparePageAt(p, mode, opts, ledger, long) } catch (e: OutOfMemoryError) { long = (long * 0.7).toInt(); System.gc() }
        }
        return preparePageAt(p, mode, opts, ledger, long)
    }

    private suspend fun preparePageAt(p: PageEntity, mode: ru.scanner291.core.common.model.ScanMode, opts: ExportOptions, ledger: Set<String>, long: Int): Ready {
        val recipe = p.recipeFor(mode)
        val src = loader.load(p, long) ?: error("Страница ${p.position + 1}: файл не открылся")
        var bmp = PageRenderer.render(src, recipe, mode.aspectWOverH) ?: error("Обработка недоступна (OpenCV не загрузился)")
        val txt = repo.text(p.id)
        var words = txt?.words?.let(WordsCodec::decode).orEmpty()
        if (recipe.redactions.isNotEmpty()) { bmp = AnnotationRenderer.redact(bmp, recipe.redactions); words = Redaction.filterWords(words, recipe.redactions) }
        if (recipe.annotations.isNotEmpty()) bmp = AnnotationRenderer.render(bmp, recipe.annotations) { id -> File(context.filesDir, "vault/signatures/$id.png").takeIf { it.isFile } }
        val wMm = ExportMath.pageWidthMm(mode.aspectWOverH, bmp.width, bmp.height)
        val dpi = ExportMath.dpi(bmp.width, wMm)
        val already = p.sha256 in ledger || alreadyMarked(p)
        val wm = WatermarkRenderer.apply(bmp, dpi, opts.watermark, already)
        val ext = wm.placement?.extendLeftPx ?: 0
        words = ExportMath.shiftWords(words, bmp.width, ext)
        // Текст закрашенных областей не попадает и в TXT: берём только слова вне областей (если они есть)
        val text = if (recipe.redactions.isNotEmpty() && words.isNotEmpty()) words.joinToString(" ") { it.text } else txt?.text
        return Ready(wm.bitmap, dpi, words, text, ext > 0)
    }

    /** Оригинал сам является результатом экспорта с водяным знаком (метка в метаданных). */
    private fun alreadyMarked(p: PageEntity): Boolean {
        val f = repo.originalFile(p)
        return if (p.mime == "application/pdf") pdf.isWatermarkedExport(f)
        else runCatching { ExifInterface(f.path).getAttribute(ExifInterface.TAG_USER_COMMENT)?.contains(MARK) == true }.getOrDefault(false)
    }

    suspend fun export(docId: String, opts: ExportOptions, progress: (Int, Int) -> Unit = { _, _ -> }): ExportResult = withContext(Dispatchers.Default) {
        val d = repo.document(docId) ?: error("Документ не найден")
        val mode = scanModeOf(d.doc.mode)
        val pages = d.orderedPages
        val dir = workDir()
        val ledger = prefs.exportedHashes()
        val warnings = ArrayList<String>()
        val files = ArrayList<Pair<String, File>>()
        var extended = false
        val wmOn = opts.watermark.enabled
        val started = System.currentTimeMillis()
        when (opts.mode) {
            ExportMode.TXT -> {
                val texts = pages.map { p -> repo.text(p.id)?.let { t ->
                    val r = p.recipeFor(mode)
                    if (r.redactions.isNotEmpty()) Redaction.filterWords(WordsCodec.decode(t.words), r.redactions).joinToString(" ") { it.text } else t.text
                } }
                if (texts.all { it.isNullOrBlank() }) error("Текст не распознан — откройте «Распознанный текст» и распознайте страницы")
                val content = ExportMath.txt(texts)
                val f = File(dir, "out.txt"); AtomicFileWriter.write(f) { it.write(content.toByteArray()) }
                check(f.readText() == content) { "Проверка TXT не прошла" }
                files += "${opts.baseName}.txt" to f
                if (texts.any { it.isNullOrBlank() }) warnings += "Страниц без текста: ${texts.count { it.isNullOrBlank() }}"
            }
            ExportMode.PDF, ExportMode.PDF_OCR -> {
                val f = File(dir, "out.pdf"); var withText = 0
                pdf.create(pages.size, f, opts.baseName, watermarked = wmOn) { i ->
                    progress(i, pages.size)
                    val r = kotlinx.coroutines.runBlocking { preparePage(pages[i], mode, opts, ledger) }
                    extended = extended || r.extended
                    val words = if (opts.mode == ExportMode.PDF_OCR) r.words else emptyList()
                    if (words.isNotEmpty()) withText++
                    PdfPageInput(r.bitmap, r.dpi, words, opts.profile.jpegQuality)
                }
                check(pdf.pageCount(f) == pages.size) { "Проверка PDF: число страниц не совпало" }
                if (opts.mode == ExportMode.PDF_OCR) {
                    if (withText == 0) warnings += "Ни одна страница не распознана — PDF без текстового слоя"
                    else if (withText < pages.size) warnings += "Без текстового слоя: ${pages.size - withText} стр. из ${pages.size}"
                    if (withText > 0) check(pdf.pageTexts(f).any { it.isNotBlank() }) { "Проверка PDF: текстовый слой не найден" }
                }
                files += "${opts.baseName}.pdf" to f
            }
            ExportMode.PDF_PER_PAGE, ExportMode.JPG, ExportMode.PNG -> for ((i, p) in pages.withIndex()) {
                coroutineContext.ensureActive(); progress(i, pages.size)
                val r = preparePage(p, mode, opts, ledger); extended = extended || r.extended
                val name = ExportMath.pageBase(opts.baseName, i, pages.size) + "." + opts.mode.ext
                val f = File(dir, "p$i.${opts.mode.ext}")
                when (opts.mode) {
                    ExportMode.PDF_PER_PAGE -> {
                        pdf.create(1, f, name, watermarked = wmOn) { PdfPageInput(r.bitmap, r.dpi, r.words, opts.profile.jpegQuality) }
                        check(pdf.pageCount(f) == 1) { "Проверка PDF не прошла" }
                    }
                    else -> {
                        val w = r.bitmap.width; val h = r.bitmap.height
                        AtomicFileWriter.write(f) { os ->
                            if (opts.mode == ExportMode.PNG) r.bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                            else r.bitmap.compress(Bitmap.CompressFormat.JPEG, opts.profile.jpegQuality ?: 95, os)
                        }
                        r.bitmap.recycle()
                        if (wmOn && opts.mode == ExportMode.JPG) runCatching { ExifInterface(f.path).apply { setAttribute(ExifInterface.TAG_USER_COMMENT, MARK); saveAttributes() } }
                        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, b)
                        check(b.outWidth == w && b.outHeight == h) { "Проверка изображения ${i + 1} не прошла" }
                    }
                }
                files += name to f
            }
        }
        progress(pages.size, pages.size)
        if (extended) warnings += "Для водяного знака на части страниц добавлено поле слева (свободного места не было)"
        if (wmOn) prefs.markExported(files.map { Hashing.sha256(it.second) })
        ExportResult(files, warnings, files.sumOf { it.second.length() }, extended, (System.currentTimeMillis() - started) / pages.size.coerceAtLeast(1))
    }

    companion object { const val MARK = "Scanner291Watermark" }
}

