package ru.scanner291.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.common.storage.Hashing
import ru.scanner291.core.common.storage.OperationJournal
import ru.scanner291.core.database.DocumentSource
import ru.scanner291.core.database.PageTextEntity
import ru.scanner291.core.database.ScannerDatabase
import java.io.File
import java.nio.file.Files

/** Этап 18: Health Center находит проблемы, ничего не исправляет сам; исправления не трогают оригиналы. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HealthCenterTest {
    private lateinit var db: ScannerDatabase
    private lateinit var paths: DocumentPaths
    private lateinit var journal: OperationJournal
    private lateinit var repo: DocumentRepository
    private lateinit var health: HealthCenter
    private lateinit var root: File

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ScannerDatabase::class.java).allowMainThreadQueries().build()
        root = Files.createTempDirectory("health").toFile()
        paths = DocumentPaths(File(root, "files"), File(root, "cache")).also { it.ensureCreated() }
        journal = OperationJournal(paths.journal)
        repo = DocumentRepository(db, paths, journal, ctx.contentResolver)
        health = HealthCenter(db, paths, journal, repo)
    }

    @After fun tearDown() { db.close(); root.walkBottomUp().forEach { it.setWritable(true) }; root.deleteRecursively() }

    private fun jpeg(shade: Int): File {
        val f = File(paths.work.apply { mkdirs() }, "cap-${System.nanoTime()}.jpg")
        val img = java.awt.image.BufferedImage(40, 60, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics(); g.color = java.awt.Color(shade, shade, shade); g.fillRect(0, 0, 40, 60); g.dispose()
        javax.imageio.ImageIO.write(img, "jpg", f); return f
    }

    private suspend fun doc(vararg shades: Int): String {
        val id = repo.createDocument(ScanMode.A4, DocumentSource.CAMERA, "Док")
        for (s in shades) repo.addCapturedPage(id, jpeg(s))
        return id
    }

    @Test fun healthyLibraryHasNoIssues() = runBlocking {
        doc(250, 200)
        assertEquals(emptyList<HealthIssue>(), health.scan())
    }

    @Test fun findsMissingCorruptedEmptyOrphanUnfinished() = runBlocking {
        val id = doc(250, 200)
        val pages = repo.document(id)!!.orderedPages
        val f0 = paths.absolute(pages[0].originalPath).apply { setWritable(true) }; f0.delete()
        paths.absolute(pages[1].originalPath).apply { setWritable(true) }.appendBytes(byteArrayOf(1, 2, 3))
        val empty = repo.createDocument(ScanMode.A4, DocumentSource.CAMERA, "Пустой")
        val orphan = File(paths.docs, "lost/original/x.jpg").apply { parentFile!!.mkdirs(); writeText("x") }
        journal.begin("test", listOf(File(paths.work, "out.tmp")))
        val kinds = health.scan().map { it.kind }.toSet()
        assertEquals(setOf(HealthKind.MISSING_ORIGINAL, HealthKind.CORRUPTED, HealthKind.EMPTY_DOCUMENT, HealthKind.ORPHAN_FILE, HealthKind.UNFINISHED_OPERATION), kinds)
        // Сканирование ничего не исправило
        assertTrue(orphan.exists()); assertTrue(repo.document(empty)!!.doc.trashedAt == null)
        // Исправления — по одному, как после подтверждения
        for (i in health.scan().filter { it.fix != null }) health.fix(i)
        assertTrue("файл не удалён, а перенесён", !orphan.exists() && File(paths.trash, "orphans/x.jpg").exists())
        assertTrue(repo.document(empty)!!.doc.trashedAt != null)
        assertTrue(journal.pending().isEmpty())
        // Проблемы оригиналов «исправлением» не скрываются — их решает пользователь
        assertEquals(setOf(HealthKind.MISSING_ORIGINAL, HealthKind.CORRUPTED), health.scan().map { it.kind }.toSet())
    }

    @Test fun rebuildIndexDoesNotTouchOriginals() = runBlocking {
        val id = doc(240)
        val page = repo.document(id)!!.orderedPages.single()
        val f = paths.absolute(page.originalPath)
        val before = Hashing.sha256(f); val mtime = f.lastModified()
        repo.saveText(PageTextEntity(page.id, id, "Рапорт Волкова об отпуске", "", "FAST", "k", false, 0, 0))
        db.documentDao().clearFts()
        val issue = health.scan().single()
        assertEquals(HealthKind.INDEX_MISSING, issue.kind)
        assertEquals(0, repo.search("отпуск").text.size)
        health.fix(issue)
        assertEquals(1, repo.search("отпуск").text.size)
        assertEquals(before, Hashing.sha256(f)); assertEquals(mtime, f.lastModified())
        assertEquals(emptyList<HealthIssue>(), health.scan())
    }
}
