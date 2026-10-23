package ru.scanner291.domain.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.scanner291.domain.metrics.OcrMetrics
import ru.scanner291.domain.model.EditHistory
import ru.scanner291.domain.model.FilterType
import ru.scanner291.domain.model.NRect
import ru.scanner291.domain.model.OcrWord
import ru.scanner291.domain.model.PageRecipe
import ru.scanner291.domain.model.Redaction
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

class StorageTest {
    private fun tmpDir(): File = Files.createTempDirectory("s291").toFile()

    @Test fun atomicWriteCreatesAndRefusesOverwrite() {
        val d = tmpDir()
        val f = File(d, "Иванов Иван Иванович.pdf")
        AtomicFileWriter.write(f) { it.write("первый".toByteArray()) }
        try {
            AtomicFileWriter.write(f) { it.write("второй".toByteArray()) }
            fail("должно быть исключение")
        } catch (_: FileAlreadyExistsException) { }
        assertEquals("первый", f.readText())
        AtomicFileWriter.write(f, overwrite = true) { it.write("второй".toByteArray()) }
        assertEquals("второй", f.readText())
        assertEquals("нет временных файлов", 1, d.listFiles()!!.size)
    }

    @Test fun failedWriteLeavesNothing() {
        val d = tmpDir()
        val f = File(d, "x.jpg")
        try {
            AtomicFileWriter.write(f) { it.write(1); throw IOException("сбой") }
        } catch (_: IOException) { }
        assertFalse(f.exists())
        assertEquals(0, d.listFiles()!!.size)
    }

    @Test fun originalsAreWriteOnce() {
        val d = tmpDir()
        val f = OriginalStore.store(File(d, "p1.jpg")) { it.write(byteArrayOf(1, 2, 3)) }
        // Проверяем бит прав, а не canWrite(): под root canWrite() всегда true.
        val perms = Files.getPosixFilePermissions(f.toPath())
        assertFalse(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE))
        try { OriginalStore.store(f) { it.write(9) }; fail() } catch (_: FileAlreadyExistsException) { }
        assertEquals(3L, f.length())
    }

    @Test fun staleTempsCleaned() {
        val d = tmpDir()
        val t = File(d, AtomicFileWriter.TEMP_PREFIX + "abc"); t.writeText("x"); t.setLastModified(1000)
        assertEquals(1, AtomicFileWriter.cleanupStaleTemps(d, olderThanMs = 10, now = 100_000))
        assertFalse(t.exists())
    }

    @Test fun journalRollsBackIncompleteOperation() {
        val d = tmpDir()
        val j = OperationJournal(File(d, "journal"))
        val out = File(d, "export.pdf")
        val e = j.begin("export", listOf(out), mapOf("docId" to "42", "note" to "a=b|c\nd"))
        out.writeText("частично")
        // «Сбой»: commit не вызван. Новый экземпляр журнала при старте приложения:
        val j2 = OperationJournal(File(d, "journal"))
        val p = j2.pending().single()
        assertEquals(e.id, p.id)
        assertEquals("a=b|c\nd", p.payload["note"])
        val res = j2.recover { RecoveryDecision.ROLL_BACK }
        assertEquals(1, res.size)
        assertFalse(out.exists())
        assertTrue(j2.pending().isEmpty())
    }

    @Test fun journalCommitRemovesEntryAndKeepsOutput() {
        val d = tmpDir()
        val j = OperationJournal(File(d, "journal"))
        val out = File(d, "doc.pdf")
        val e = j.begin("save", listOf(out))
        out.writeText("ok"); j.commit(e)
        assertTrue(j.pending().isEmpty())
        assertTrue(out.exists())
    }

    @Test fun sha256() {
        val f = File(tmpDir(), "a.txt"); f.writeText("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hashing.sha256(f))
    }
}

class ModelsAndMetricsTest {
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

    @Test fun cerWer() {
        assertEquals(0.0, OcrMetrics.cer("Иванов Иван", "Иванов  Иван").rate, 1e-9)
        assertEquals(1.0 / 3.0, OcrMetrics.cer("кот", "кит").rate, 1e-9)
        assertEquals(0.5, OcrMetrics.wer("Иванов Иван", "Иванов Ивон").rate, 1e-9)
        assertEquals(1.0 / 6.0, OcrMetrics.cer("Семёна", "Семена").rate, 1e-9)
        assertEquals(0.0, OcrMetrics.cer("Семёна", "Семена", OcrMetrics.Options(foldYo = true)).rate, 1e-9)
        val (c, w) = OcrMetrics.corpus(listOf("аб" to "аб", "вг" to "вд"))
        assertEquals(0.25, c, 1e-9); assertEquals(0.5, w, 1e-9)
    }
}
