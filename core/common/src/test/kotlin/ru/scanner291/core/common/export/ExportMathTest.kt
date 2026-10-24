package ru.scanner291.core.common.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.scanner291.core.common.model.NRect
import ru.scanner291.core.common.model.OcrWord
import ru.scanner291.core.common.model.QualityProfile

class ExportMathTest {
    @Test fun sixModesWithoutUnmeasuredFormats() {
        assertEquals(7, ExportMode.entries.size)
        assertTrue(ExportMode.entries.any { it.ext == "docx" } && ExportMode.entries.none { it.ext == "xlsx" })
    }

    @Test fun sizeEstimateScalesSublinearly() {
        val s = ExportMath.estimateJpegBytes(50_000, 600L * 848, 600L * 848)
        assertEquals(44_225L, s)                                   // A · пробный при равных размерах
        val big = ExportMath.estimateJpegBytes(50_000, 600L * 848, 2480L * 3508)
        assertTrue(big in 400_000L..600_000L)                       // в 17 раз больше пикселей → ×9.7 по размеру (B=0.8)
    }

    @Test fun pageSizesAndDpi() {
        assertEquals(210.0, ExportMath.pageWidthMm(210.0 / 297.0, 1000, 1414), 0.5)
        assertEquals(297.0, ExportMath.pageWidthMm(210.0 / 297.0, 1414, 1000), 0.5)
        assertEquals(148.0, ExportMath.pageWidthMm(148.0 / 210.0, 700, 1000), 1.0)
        assertEquals(85.6, ExportMath.pageWidthMm(85.6 / 54.0, 856, 540), 0.1)
        assertEquals(210.0, ExportMath.pageWidthMm(null, 900, 1300), 0.1)
        assertEquals(3508, ExportMath.targetLongSide(QualityProfile.HIGH, 297.0, 5000))
        assertEquals(1754, ExportMath.targetLongSide(QualityProfile.COMPACT, 297.0, 5000))
        assertEquals(3000, ExportMath.targetLongSide(QualityProfile.HIGH, 297.0, 3000))   // без увеличения
        assertEquals(4200, ExportMath.targetLongSide(QualityProfile.LOSSLESS, 297.0, 9000))
        assertEquals(300.0, ExportMath.dpi(2480, 210.0).toDouble(), 1.0)
    }

    @Test fun wordsShiftWithWatermarkMargin() {
        val w = OcrWord("Иванов", NRect(0.5f, 0.1f, 0.6f, 0.12f), 0.9f)
        val s = ExportMath.shiftWords(listOf(w), 1000, 100).single()
        assertEquals((500.0 + 100.0) / 1100.0, s.box.left.toDouble(), 1e-5)
        assertEquals(0.1, s.box.top.toDouble(), 1e-6)
        assertEquals(listOf(w), ExportMath.shiftWords(listOf(w), 1000, 0))
    }

    @Test fun namesAndTxt() {
        assertEquals("Иванов И.И. (стр. 2)", ExportMath.pageBase("Иванов И.И.", 1, 3))
        assertEquals("Иванов И.И.", ExportMath.pageBase("Иванов И.И.", 0, 1))
        assertEquals("— Страница 1 —\nтекст\n\n— Страница 2 —\n(текст не распознан)\n", ExportMath.txt(listOf("текст", null)))
    }
}
