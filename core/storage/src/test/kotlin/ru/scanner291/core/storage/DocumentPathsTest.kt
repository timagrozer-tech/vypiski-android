package ru.scanner291.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.scanner291.core.testing.withTempDir
import java.io.File

class DocumentPathsTest {
    @Test fun layoutKeepsOriginalsAndDerivativesApart() = withTempDir { root ->
        val p = DocumentPaths(File(root, "files"), File(root, "cache"))
        p.ensureCreated()
        assertTrue(p.docs.isDirectory && p.journal.isDirectory && p.trash.isDirectory && p.work.isDirectory)
        assertEquals(File(p.docs, "d1/original/p1.jpg"), p.original("d1", "p1"))
        assertEquals(File(p.docs, "d1/processed/p1.jpg"), p.processed("d1", "p1"))
        // временные файлы — в cache, а не рядом с оригиналами
        assertTrue(p.work.path.contains("cache"))
    }
}
