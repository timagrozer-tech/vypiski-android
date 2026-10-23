package ru.scanner291.core.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ru.scanner291.core.database.PageEntity
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** Декодирование файла изображения с уменьшением в [sample] раз (степень двойки). */
fun interface PageDecoder { fun decode(file: File, sample: Int): Bitmap? }

/**
 * Уменьшенные изображения страниц для экрана (миниатюры, просмотр). Оригинал только читается.
 * Декодирование с уменьшением (`inSampleSize`) — полноразмерный снимок не попадает в память целиком;
 * поворот по EXIF; страницы PDF рисуются системным PdfRenderer. Ограниченный LRU-кэш (1/8 памяти
 * процесса) и не больше двух декодирований одновременно.
 */
@Singleton
class PageImageLoader @Inject constructor(private val paths: DocumentPaths, private val decoder: PageDecoder) {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val gate = Semaphore(2)

    suspend fun load(page: PageEntity, maxSide: Int): Bitmap? {
        val key = "${page.originalPath}#${page.pdfPageIndex ?: -1}@$maxSide"
        cache.get(key)?.let { return it }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                cache.get(key) ?: runCatching {
                    val f = paths.absolute(page.originalPath)
                    if (page.pdfPageIndex != null) renderPdf(f, page.pdfPageIndex!!, maxSide) else decodeImage(f, maxSide)
                }.getOrNull()?.also { cache.put(key, it) }
            }
        }
    }

    private fun decodeImage(f: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        val bmp = decoder.decode(f, sample)
            // Запасной путь: ImageDecoder (сам поворачивает по EXIF) — для файлов, которые не открыл BitmapFactory.
            ?: return runCatching {
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(f)) { d, info, _ ->
                    d.setTargetSampleSize(sampleSize(info.size.width, info.size.height, maxSide))
                    d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
        val rot = runCatching { ExifInterface(f.path).rotationDegrees }.getOrDefault(0)
        if (rot == 0) return bmp
        val m = Matrix().apply { postRotate(rot.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
    }

    private fun renderPdf(f: File, index: Int, maxSide: Int): Bitmap? =
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(index).use { p ->
                    val scale = maxSide.toFloat() / max(p.width, p.height)
                    val w = (p.width * scale).toInt().coerceAtLeast(1)
                    val h = (p.height * scale).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                        b.eraseColor(Color.WHITE)
                        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    companion object {
        /** Наибольшая степень двойки, при которой большая сторона остаётся ≥ [maxSide]. Покрыто тестом. */
        fun sampleSize(w: Int, h: Int, maxSide: Int): Int {
            var s = 1
            val side = max(w, h)
            while (side / (s * 2) >= maxSide) s *= 2
            return s
        }
    }
}
