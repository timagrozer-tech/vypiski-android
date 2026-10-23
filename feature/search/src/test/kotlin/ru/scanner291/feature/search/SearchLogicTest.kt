package ru.scanner291.feature.search

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.scanner291.core.database.DocumentEntity
import ru.scanner291.core.database.TextHit
import ru.scanner291.core.storage.SearchResults

class SearchLogicTest {
    @Test fun hitsGroupByDocument() {
        val r = SearchResults(
            listOf(DocumentEntity("d1", "Рапорт Иванова", "REPORT", "CAMERA", 1, 2)),
            listOf(TextHit("p1", "d2", "… [Иванов] Иван …"), TextHit("p2", "d2", "… [Иванову] …"), TextHit("p3", "d3", "[иванов]")),
        )
        val ui = toUi("иванов", r, mapOf("d2" to "Справка", "d3" to "Приказ"))
        assertEquals(listOf("d1"), ui.titles.map { it.docId })
        assertEquals(listOf("d2", "d3"), ui.text.map { it.docId })
        assertEquals("Совпадения на 2 стр.", ui.text[0].subtitle)
        assertEquals("Совпадение на 1 странице", ui.text[1].subtitle)
    }

    @Test fun snippetHighlight() {
        val a = highlightSnippet("… выдана [Иванову] И. И. [в] том", Color.Red)
        assertEquals("… выдана Иванову И. И. в том", a.text)
        assertEquals(2, a.spanStyles.size)
        assertEquals("без скобок", highlightSnippet("без скобок", Color.Red).text)
        assertEquals("обрыв [без конца", highlightSnippet("обрыв [без конца", Color.Red).text)
    }
}
