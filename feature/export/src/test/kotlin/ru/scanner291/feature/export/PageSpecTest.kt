package ru.scanner291.feature.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageSpecTest {
    @Test fun pages() {
        assertEquals(listOf(0, 1, 2, 3), parsePages("", 4))
        assertEquals(listOf(0, 1, 2, 3), parsePages("Все", 4))
        assertEquals(listOf(0, 1, 2, 4), parsePages("1-3, 5", 5))
        assertEquals(listOf(4, 3, 2, 0), parsePages("5-3; 1", 5))
        assertEquals(listOf(2, 0, 1), parsePages("3, 1–2", 3))
    }

    @Test fun errorsAreExplained() {
        var e = ""
        assertNull(parsePages("1-9", 5) { e = it }); assertEquals("В документе 5 стр.: «1-9» вне диапазона", e)
        assertNull(parsePages("первая", 5) { e = it }); assertEquals(true, e.startsWith("Не понял «первая»"))
        assertNull(parsePages("0", 5))
    }

    @Test fun ranges() {
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4)), parseRanges("1-3, 4-5", 5))
        assertNull(parseRanges("", 5))
        assertEquals(listOf("Объединить", "Разделить"), PdfTools.take(2).map { it.title })
        assertEquals(2, PdfTools.count { !it.ready })
    }
}
