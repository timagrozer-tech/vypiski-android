package ru.scanner291.core.ocr

import android.graphics.Bitmap
import ru.scanner291.core.common.model.NRect
import ru.scanner291.core.common.model.OcrWord
import java.util.Locale

/** Режим OCR (ADR-0016): «Быстрый» — модели в APK; «Высокая точность» — офлайн-пакет tessdata_best. */
enum class OcrMode(val title: String) { FAST("Быстрый"), BEST("Высокая точность") }

data class OcrResult(val text: String, val words: List<OcrWord>, val durationMs: Long)

/** Распознавание одного изображения. Реализация — Tesseract; в тестах — подделка. */
fun interface TextRecognizer {
    fun recognize(bitmap: Bitmap, mode: OcrMode): OcrResult
}

/** Слова ↔ строка для БД: «текст\tl,t,r,b\tуверенность» по строкам. Табуляции и переводы строк в словах заменяются пробелом. */
object WordsCodec {
    fun encode(words: List<OcrWord>): String = words.joinToString("\n") { w ->
        val b = w.box
        "${w.text.replace('\t', ' ').replace('\n', ' ')}\t${f(b.left)},${f(b.top)},${f(b.right)},${f(b.bottom)}\t${f(w.confidence)}"
    }

    fun decode(s: String): List<OcrWord> = s.lines().mapNotNull { line ->
        val p = line.split('\t'); if (p.size != 3) return@mapNotNull null
        val c = p[1].split(',').mapNotNull { it.toFloatOrNull() }; if (c.size != 4) return@mapNotNull null
        OcrWord(p[0], NRect(c[0], c[1], c[2], c[3]), p[2].toFloatOrNull() ?: 0f)
    }

    private fun f(v: Float) = String.format(Locale.ROOT, "%.4f", v)
}

/**
 * Единственное место, где OCR пишет в журнал (logcat). Только служебные числа — никогда сам текст:
 * распознанные документы содержат персональные данные. Проверяется тестом OcrServiceTest.
 */
internal object OcrLog {
    private const val TAG = "Scanner291Ocr"
    fun done(pageId: String, mode: OcrMode, chars: Int, words: Int, ms: Long) =
        android.util.Log.i(TAG, "Страница ${pageId.take(8)}: $mode, символов $chars, слов $words, $ms мс")
    fun failed(pageId: String, e: Throwable) =
        android.util.Log.w(TAG, "Страница ${pageId.take(8)}: OCR не выполнен (${e.javaClass.simpleName})")
}
