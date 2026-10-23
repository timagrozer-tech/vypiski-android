package ru.scanner291.core.common.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModelsTest {
    @Test fun undoRedo() {
        val h = EditHistory(PageRecipe())
        h.apply(h.current.rotatedBy(90))
        h.apply(h.current.copy(filter = FilterType.BW))
        assertEquals(FilterType.BW, h.current.filter)
        h.undo(); assertEquals(FilterType.TEXT_BOOST, h.current.filter); assertEquals(90, h.current.rotation)
        h.undo(); assertEquals(PageRecipe(), h.current)
        h.redo(); assertEquals(90, h.current.rotation)
        h.apply(h.current.rotatedBy(-180)); assertEquals(270, h.current.rotation)
        assertFalse(h.canRedo)
    }

    @Test fun redactionRemovesWordsFromTextLayer() {
        val words = listOf(
            OcrWord("Паспорт", NRect(0.1f, 0.1f, 0.3f, 0.15f), 0.9f),
            OcrWord("4510", NRect(0.35f, 0.1f, 0.45f, 0.15f), 0.9f),
            OcrWord("123456", NRect(0.5f, 0.1f, 0.65f, 0.15f), 0.9f),
        )
        val left = Redaction.filterWords(words, listOf(NRect(0.66f, 0.16f, 0.34f, 0.09f)))
        assertEquals(listOf("Паспорт"), left.map { it.text })
    }
}
