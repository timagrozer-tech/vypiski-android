package ru.scanner291.core.quality.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrMetricsTest {
    @Test fun cerWer() {
        assertEquals(0.0, OcrMetrics.cer("Иванов Иван", "Иванов  Иван").rate, 1e-9)
        assertEquals(1.0 / 3.0, OcrMetrics.cer("кот", "кит").rate, 1e-9)
        assertEquals(0.5, OcrMetrics.wer("Иванов Иван", "Иванов Ивон").rate, 1e-9)
        assertEquals(1.0 / 6.0, OcrMetrics.cer("Семёна", "Семена").rate, 1e-9)
        assertEquals(0.0, OcrMetrics.cer("Семёна", "Семена", OcrMetrics.Options(foldYo = true)).rate, 1e-9)
        val (c, w) = OcrMetrics.corpus(listOf("аб" to "аб", "вг" to "вд"))
        assertEquals(0.25, c, 1e-9); assertEquals(0.5, w, 1e-9)
    }
}
