package ru.scanner291.core.common.text

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RussianTextTest {
    @Test fun documentsPlural() {
        val expected = mapOf(
            0 to "0 документов", 1 to "1 документ", 2 to "2 документа", 4 to "4 документа", 5 to "5 документов",
            11 to "11 документов", 12 to "12 документов", 14 to "14 документов", 21 to "21 документ",
            22 to "22 документа", 25 to "25 документов", 101 to "101 документ", 111 to "111 документов", 1004 to "1004 документа",
        )
        expected.forEach { (n, s) -> assertEquals(s, RussianPlural.documents(n)) }
    }

    @Test fun humanDate() {
        val today = LocalDate.of(2026, 10, 23)
        assertEquals("Сегодня, 14:32", HumanDate.format(LocalDateTime.of(2026, 10, 23, 14, 32), today))
        assertEquals("Вчера, 10:15", HumanDate.format(LocalDateTime.of(2026, 10, 22, 10, 15), today))
        assertEquals("12.10.2024", HumanDate.format(LocalDateTime.of(2024, 10, 12, 9, 0), today))
        assertEquals("01.01.2026", HumanDate.format(LocalDateTime.of(2026, 1, 1, 0, 5), today))
    }
}
