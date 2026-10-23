package ru.scanner291.core.common.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
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
