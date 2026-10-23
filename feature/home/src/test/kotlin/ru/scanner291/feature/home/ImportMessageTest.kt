package ru.scanner291.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.scanner291.core.storage.ImportResult
import ru.scanner291.core.storage.Rejection

class ImportMessageTest {
    @Test fun summarizesImportedAndRejected() {
        assertEquals("Импортировано: 3 файла", importMessage(ImportResult(listOf("a"), 3, emptyList())))
        assertEquals(
            "Импортировано: 1 файл. Не импортировано: x.docx — Формат не поддерживается",
            importMessage(ImportResult(listOf("a"), 1, listOf(Rejection("x.docx", "Формат не поддерживается")))),
        )
        assertNull(importMessage(ImportResult(emptyList(), 0, emptyList())))
    }
}
