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

class FactsLogicTest {
    private val rows = listOf(
        FactRow("type", "Тип", "Рапорт", "РАПОРТ", 0.95),
        FactRow("number", "Номер", "№ 12", "№ 12 от 10.01.2026", 0.9),
        FactRow("date", "Дата", "10.01.2026", "№ 12 от 10.01.2026", 0.9),
    )

    @org.junit.Test fun nameOnlyFromConfirmedFields() {
        assertEquals(null, nameFromConfirmed(rows, emptySet(), "Волков И.И."))
        assertEquals("Рапорт Волков И.И. 10.01.2026", nameFromConfirmed(rows, setOf("type", "date"), "Волков И.И."))
        assertEquals("Рапорт № 12 от 10.01.2026", nameFromConfirmed(rows, setOf("type", "number", "date"), null))
    }

    @org.junit.Test fun rowsFromAnalyzer() {
        val f = ru.scanner291.core.intelligence.doc.DocumentAnalyzer().analyze("СПРАВКА\n№ 926 от 17.09.2022\nв/ч 65384", java.time.LocalDate.of(2026, 1, 1))
        assertEquals(listOf("type", "number", "date", "org"), factRows(f).map { it.key }.take(4))
        assertEquals("17.09.2022", factRows(f).first { it.key == "date" }.value)
    }
}
