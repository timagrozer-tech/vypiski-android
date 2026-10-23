package ru.scanner291.feature.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportUiTest {
    @Test fun sizes() {
        assertEquals("Размер будет известен после экспорта", sizeText(null))
        assertEquals("Примерно 512 КБ (±25 %)", sizeText(512L * 1024))
        assertEquals("Примерно 1,5 МБ (±25 %)", sizeText(1_572_864))
        assertEquals("application/pdf", mimeOf("Иванов.pdf")); assertEquals("image/jpeg", mimeOf("a (стр. 1).JPG"))
        assertEquals("Без сжатия", profileShort(ru.scanner291.core.common.model.QualityProfile.LOSSLESS))
    }
}
