package ru.scanner291.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class FtsQueryTest {
    @Test fun wordsBecomePrefixTermsAndSpecialCharsAreDropped() {
        assertEquals("иванов* отпуск*", ftsQuery("Иванов  ОТПУСК"))
        assertEquals("в* ч* 65384*", ftsQuery("В/ч 65384"))
        assertEquals("", ftsQuery("\" * - OR ( )"))
        assertEquals("приказ* 15*", ftsQuery("приказ №15"))
    }
}
