package ru.scanner291.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import ru.scanner291.core.common.model.Annotation
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Аннотации и графическая подпись (этап 12) «впекаются» в изображение страницы перед созданием PDF:
 * так они одинаково видны в любом просмотрщике. Исходное изображение не меняется — рисуется копия.
 * Подпись — PNG из защищённого хранилища ([signatureFile] по id).
 */
object AnnotationRenderer {
    fun render(src: Bitmap, annotations: List<Annotation>, signatureFile: (String) -> File?): Bitmap {
        if (annotations.isEmpty()) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out); val w = out.width.toFloat(); val h = out.height.toFloat()
        for (a in annotations) when (a) {
            is Annotation.Text -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a.argb; textSize = a.sizeSp * h / 800f }
                c.drawText(a.text, a.at.left * w, a.at.top * h + p.textSize, p)
            }
            is Annotation.Ink -> if (a.points.size >= 2) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = a.argb; style = Paint.Style.STROKE; strokeWidth = a.widthNorm * w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                }
                val path = Path().apply { moveTo(a.points[0].first * w, a.points[0].second * h); a.points.drop(1).forEach { lineTo(it.first * w, it.second * h) } }
                c.drawPath(path, p)
            }
            is Annotation.Signature -> signatureFile(a.signatureFileId)?.let { f ->
                BitmapFactory.decodeFile(f.path)?.let { s ->
                    c.drawBitmap(s, null, RectF(a.at.left * w, a.at.top * h, a.at.right * w, a.at.bottom * h), Paint(Paint.FILTER_BITMAP_FLAG))
                }
            }
        }
        return out
    }
}

/**
 * Сжатие любого PDF через растр: страницы рисуются системным PdfRenderer с [dpi] и сохраняются JPEG.
 * Текстовый слой и ссылки при этом теряются — экран предупреждает об этом до запуска.
 */
fun PdfEngine.rasterCompress(input: File, out: File, dpi: Int = 150, quality: Int = 70) {
    require(input.absoluteFile != out.absoluteFile) { "Результат не может заменить исходный файл" }
    val pages = ArrayList<PdfPageInput>()
    ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { r ->
            for (i in 0 until r.pageCount) r.openPage(i).use { p ->
                val bw = (p.width / 72f * dpi).toInt().coerceAtLeast(1); val bh = (p.height / 72f * dpi).toInt().coerceAtLeast(1)
                val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                pages += PdfPageInput(b, dpi.toFloat(), jpegQuality = quality)
            }
        }
    }
    create(pages, out)
}

/** Печать PDF через системную службу печати (без сети со стороны приложения). */
object PdfPrint {
    fun print(context: Context, file: File, jobName: String) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }
            override fun onWrite(pages: Array<out PageRange>, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
                try {
                    FileInputStream(file).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) { cb.onWriteFailed(e.message) }
            }
        }, null)
    }
}
