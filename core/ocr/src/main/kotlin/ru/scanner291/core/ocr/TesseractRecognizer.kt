package ru.scanner291.core.ocr

import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import ru.scanner291.core.common.model.NRect
import ru.scanner291.core.common.model.OcrWord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tesseract (Tesseract4Android, LSTM), языки rus+eng, разметка «одна колонка» (psm 4) — как в замерах
 * reports/ocr-metrics.md. Один экземпляр движка на режим; вызовы последовательные (движок не потокобезопасен).
 */
@Singleton
class TesseractRecognizer @Inject constructor(private val tessdata: Tessdata) : TextRecognizer {
    private val engines = HashMap<OcrMode, TessBaseAPI>()

    @Synchronized
    override fun recognize(bitmap: Bitmap, mode: OcrMode): OcrResult {
        val t0 = System.currentTimeMillis()
        val api = engine(mode)
        api.setImage(bitmap)
        val text = api.utF8Text.orEmpty()
        val words = ArrayList<OcrWord>()
        val it = api.resultIterator
        if (it != null) {
            val level = TessBaseAPI.PageIteratorLevel.RIL_WORD
            it.begin()
            do {
                val w = it.getUTF8Text(level) ?: continue
                val r = it.getBoundingRect(level)
                val bw = bitmap.width.toFloat(); val bh = bitmap.height.toFloat()
                words += OcrWord(w, NRect(r.left / bw, r.top / bh, r.right / bw, r.bottom / bh), it.confidence(level) / 100f)
            } while (it.next(level))
            it.delete()
        }
        api.clear()
        return OcrResult(text.trim(), words, System.currentTimeMillis() - t0)
    }

    private fun engine(mode: OcrMode): TessBaseAPI = engines.getOrPut(mode) {
        if (mode == OcrMode.FAST) tessdata.ensureFast()
        else check(tessdata.isInstalled(OcrMode.BEST)) { "Пакет «Высокая точность» не установлен" }
        TessBaseAPI().also { api ->
            check(api.init(tessdata.root(mode).absolutePath, "rus+eng", TessBaseAPI.OEM_LSTM_ONLY)) { "Модели OCR не загрузились" }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN
        }
    }
}
