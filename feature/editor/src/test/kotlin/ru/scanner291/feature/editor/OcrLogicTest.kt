package ru.scanner291.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.database.PageTextEntity

class OcrLogicTest {
    private fun t(edited: Boolean = false, key: String = "k", ms: Long = 1234) = PageTextEntity("p", "d", "текст", "", "FAST", key, edited, 0, ms)
    private val page = PageEntity("p", "d", 0, "x", "s", "image/jpeg", 1, 1, 1, null, 0)

    @Test fun statuses() {
        assertEquals(TextStatus.NONE, textStatus(null, "k"))
        assertEquals(TextStatus.DONE, textStatus(t(), "k"))
        assertEquals(TextStatus.STALE, textStatus(t(), "другой"))
        assertEquals(TextStatus.EDITED, textStatus(t(edited = true), "другой"))  // правки не помечаются устаревшими
        assertEquals("Распознана: Быстрый, 1,2 с", statusText(OcrPageUi(page, 0, t(), TextStatus.DONE)))
        assertEquals("Не распознана", statusText(OcrPageUi(page, 0, null, TextStatus.NONE)))
    }
}
