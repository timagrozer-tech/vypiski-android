package ru.scanner291.core.pdf

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import ru.scanner291.core.common.model.OcrWord
import ru.scanner291.core.common.storage.AtomicFileWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Страница нового PDF: готовое изображение (обработка, водяной знак, аннотации уже применены),
 * плотность [dpi] (размер страницы = пиксели / dpi), слова OCR для невидимого текстового слоя.
 * [jpegQuality] = null — без пережатия (lossless).
 */
data class PdfPageInput(val bitmap: Bitmap, val dpi: Float, val words: List<OcrWord> = emptyList(), val jpegQuality: Int? = 85)

/**
 * PDF-движок (этап 12) на PdfBox-Android. Исходные файлы только читаются; результат пишется во
 * временный файл и атомарно переносится ([AtomicFileWriter]), существующий файл не перезаписывается.
 * Чужие PDF открываются с буфером во временных файлах (ADR-0017) и проверкой лимитов.
 */
class PdfEngine(private val context: Context) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }

    private fun font(doc: PDDocument): PDFont =
        PDType0Font.load(doc, PDFBoxResourceLoader.getStream("com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf"))

    private fun open(f: File): PDDocument {
        if (f.length() > ru.scanner291.core.common.files.ImportLimits.MAX_FILE_BYTES) throw IOException("Файл больше 200 МБ")
        val d = PDDocument.load(f, MemoryUsageSetting.setupTempFileOnly())
        if (d.numberOfPages > ru.scanner291.core.common.files.ImportLimits.MAX_PDF_PAGES) { d.close(); throw IOException("Больше 2000 страниц") }
        return d
    }

    private fun save(doc: PDDocument, out: File) {
        AtomicFileWriter.write(out) { doc.save(it) }
    }

    /** Новый PDF из изображений страниц с текстовым слоем (кириллица — шрифт Liberation Sans, встраивается). */
    fun create(pages: List<PdfPageInput>, out: File, title: String? = null) {
        require(pages.isNotEmpty()) { "Нет страниц" }
        PDDocument().use { doc ->
            title?.let { doc.documentInformation.title = it }
            doc.documentInformation.producer = "291 Сканер"
            val f by lazy { font(doc) }
            for (p in pages) {
                val w = p.bitmap.width * 72f / p.dpi; val h = p.bitmap.height * 72f / p.dpi
                val page = PDPage(PDRectangle(w, h)); doc.addPage(page)
                val img = if (p.jpegQuality == null) LosslessFactory.createFromImage(doc, p.bitmap)
                else JPEGFactory.createFromByteArray(doc, ByteArrayOutputStream().also { p.bitmap.compress(Bitmap.CompressFormat.JPEG, p.jpegQuality, it) }.toByteArray())
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.drawImage(img, 0f, 0f, w, h)
                    if (p.words.isNotEmpty()) textLayer(cs, f, p.words, w, h)
                }
            }
            save(doc, out)
        }
    }

    /**
     * Невидимый текст (режим отрисовки NEITHER) поверх слов: поиск и копирование в любом просмотрщике.
     * Слово растягивается по ширине рамки; символы, которых нет в шрифте, пропускаются вместе со словом.
     */
    private fun textLayer(cs: PDPageContentStream, font: PDFont, words: List<OcrWord>, w: Float, h: Float) {
        cs.beginText()
        cs.setRenderingMode(RenderingMode.NEITHER)
        for (word in words) {
            val t = word.text.trim(); if (t.isEmpty()) continue
            val b = word.box.normalized()
            val size = ((b.bottom - b.top) * h * 0.85f).coerceIn(2f, 200f)
            val natural = runCatching { font.getStringWidth(t) / 1000f * size }.getOrNull() ?: continue
            if (natural <= 0f) continue
            try {
                cs.setFont(font, size)
                cs.setHorizontalScaling(((b.right - b.left) * w / natural * 100f).coerceIn(10f, 400f))
                cs.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(b.left * w, (1f - b.bottom) * h + size * 0.15f))
                cs.showText("$t ")
            } catch (e: IllegalArgumentException) {
                // Нет глифа в шрифте — слово не попадает в слой (изображение страницы не меняется)
            }
        }
        cs.endText()
    }

    fun pageCount(f: File): Int = open(f).use { it.numberOfPages }

    /** Текст страниц (для проверки и поиска). */
    fun pageTexts(f: File): List<String> = open(f).use { d ->
        (1..d.numberOfPages).map { i -> PDFTextStripper().apply { startPage = i; endPage = i }.getText(d).trim() }
    }

    /** Объединение файлов в порядке списка. */
    fun merge(inputs: List<File>, out: File) {
        require(inputs.size >= 2) { "Нужно минимум два файла" }
        require(inputs.none { it.absoluteFile == out.absoluteFile }) { "Результат не может заменить исходный файл" }
        PDDocument().use { dst ->
            val opened = inputs.map { open(it) }
            try {
                val m = PDFMergerUtility()
                for (src in opened) m.appendDocument(dst, src)
                save(dst, out)
            } finally { opened.forEach { it.close() } }
        }
    }

    /** Новый документ из страниц [order] (индексы с 0, можно повторять и пропускать): перестановка, извлечение, удаление. */
    fun select(input: File, order: List<Int>, out: File) {
        require(order.isNotEmpty()) { "Не выбраны страницы" }
        require(input.absoluteFile != out.absoluteFile) { "Результат не может заменить исходный файл" }
        open(input).use { src ->
            require(order.all { it in 0 until src.numberOfPages }) { "Нет такой страницы" }
            PDDocument().use { dst ->
                for (i in order) dst.importPage(src.getPage(i))
                save(dst, out)
            }
        }
    }

    /** Разделение по диапазонам (с 0, включительно) — по файлу на диапазон. */
    fun split(input: File, ranges: List<IntRange>, outFiles: List<File>) {
        require(ranges.size == outFiles.size && ranges.isNotEmpty())
        ranges.zip(outFiles).forEach { (r, f) -> select(input, r.toList(), f) }
    }

    /** Поворот страниц [pages] (с 0) на [degrees] (кратно 90) — меняется только атрибут /Rotate. */
    fun rotate(input: File, pages: Set<Int>, degrees: Int, out: File) {
        require(degrees % 90 == 0) { "Поворот кратен 90°" }
        require(input.absoluteFile != out.absoluteFile) { "Результат не может заменить исходный файл" }
        open(input).use { d ->
            for (i in pages) { val p = d.getPage(i); p.rotation = ((p.rotation + degrees) % 360 + 360) % 360 }
            save(d, out)
        }
    }

    /** Вставка всех страниц [other] перед страницей [at] (с 0; = числу страниц — в конец). */
    fun insert(input: File, other: File, at: Int, out: File) {
        require(input.absoluteFile != out.absoluteFile && other.absoluteFile != out.absoluteFile) { "Результат не может заменить исходный файл" }
        open(input).use { a -> open(other).use { b ->
            require(at in 0..a.numberOfPages) { "Нет такого места" }
            PDDocument().use { dst ->
                for (i in 0 until at) dst.importPage(a.getPage(i))
                for (i in 0 until b.numberOfPages) dst.importPage(b.getPage(i))
                for (i in at until a.numberOfPages) dst.importPage(a.getPage(i))
                save(dst, out)
            }
        } }
    }

    fun rotationOf(f: File): List<Int> = open(f).use { d -> (0 until d.numberOfPages).map { d.getPage(it).rotation } }
}
