package ru.scanner291.core.common.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSignatureTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    @Test fun detectsSupportedFormats() {
        assertEquals(FileKind.JPEG, FileSignature.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)))
        assertEquals(FileKind.PNG, FileSignature.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertEquals(FileKind.WEBP, FileSignature.detect(ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ")))
        assertEquals(FileKind.HEIC, FileSignature.detect(ascii("\u0000\u0000\u0000\u0018ftypheic\u0000\u0000\u0000\u0000")))
        assertEquals(FileKind.HEIC, FileSignature.detect(ascii("\u0000\u0000\u0000\u0018ftypmif1")))
        assertEquals(FileKind.PDF, FileSignature.detect(ascii("%PDF-1.7\n%âãÏÓ")))
        assertEquals(FileKind.PDF, FileSignature.detect(ascii("\n\n%PDF-1.4")))
    }

    @Test fun rejectsDisguisedOrUnknown() {
        assertNull(FileSignature.detect(ascii("<html><body>")))
        assertNull(FileSignature.detect(bytes(0x50, 0x4B, 0x03, 0x04)))      // ZIP/DOCX
        assertNull(FileSignature.detect(ascii("RIFF\u0000\u0000\u0000\u0000WAVE")))
        assertNull(FileSignature.detect(ascii("\u0000\u0000\u0000\u0018ftypmp42"))) // MP4, не HEIF
        assertNull(FileSignature.detect(ByteArray(0)))
        assertNull(FileSignature.detect(bytes(0xFF, 0xD8)))                  // обрезанный JPEG-заголовок
    }

    @Test fun importChecksSchemeSizeAndSignature() {
        val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE1)
        assertEquals(ImportCheck.Ok(FileKind.JPEG), checkImport("content", 1000, jpeg))
        assertTrue(checkImport("file", 1000, jpeg) is ImportCheck.Rejected)          // file:// — запрещено
        assertTrue(checkImport(null, 1000, jpeg) is ImportCheck.Rejected)
        assertTrue(checkImport("content", ImportLimits.MAX_FILE_BYTES + 1, jpeg) is ImportCheck.Rejected)
        assertTrue(checkImport("content", 0, jpeg) is ImportCheck.Rejected)
        assertEquals(ImportCheck.Rejected("Формат не поддерживается"), checkImport("content", null, ascii("GIF89a")))
        assertEquals(ImportCheck.Ok(FileKind.JPEG), checkImport("content", null, jpeg)) // размер неизвестен — проверяется при копировании
    }
}
