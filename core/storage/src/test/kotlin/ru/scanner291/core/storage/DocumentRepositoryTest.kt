package ru.scanner291.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.common.storage.Hashing
import ru.scanner291.core.common.storage.OperationJournal
import ru.scanner291.core.database.DocumentSource
import ru.scanner291.core.database.ScannerDatabase
import java.io.File
import java.nio.file.Files

/** Сохранность оригиналов (ТЗ v3.0 ч. X, XXI п. 1): байты не меняются, файл только для чтения, SHA-256 в индексе. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentRepositoryTest {
    private lateinit var db: ScannerDatabase
    private lateinit var paths: DocumentPaths
    private lateinit var journal: OperationJournal
    private lateinit var repo: DocumentRepository
    private lateinit var root: File

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ScannerDatabase::class.java).allowMainThreadQueries().build()
        root = Files.createTempDirectory("repo").toFile()
        paths = DocumentPaths(File(root, "files"), File(root, "cache")).also { it.ensureCreated() }
        journal = OperationJournal(paths.journal)
        repo = DocumentRepository(db, paths, journal, ctx.contentResolver)
    }

    @After fun tearDown() {
        db.close()
        root.walkBottomUp().forEach { it.setWritable(true) }
        root.deleteRecursively()
    }

    private fun jpeg(w: Int, h: Int): File {
        val f = File(paths.work, "cap-${System.nanoTime()}.jpg")
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return f
    }

    @Test fun capturedPageBecomesImmutableOriginal() = runTest {
        val id = repo.createDocument(ScanMode.A4, DocumentSource.CAMERA, "Скан 2026-10-23 14-28")
        val temp = jpeg(300, 400)
        val bytes = temp.readBytes()
        val page = repo.addCapturedPage(id, temp)

        assertFalse("временный файл удалён", temp.exists())
        val original = repo.originalFile(page)
        assertArrayEquals("оригинал побайтно равен снимку", bytes, original.readBytes())
        assertEquals(Hashing.sha256(original), page.sha256)
        assertFalse("оригинал только для чтения", original.canWrite())
        assertEquals(300, page.width); assertEquals(400, page.height)
        assertEquals(0, page.position)
        assertTrue("журнал закрыт", journal.pending().isEmpty())

        val second = repo.addCapturedPage(id, jpeg(300, 400))
        assertEquals(1, second.position)
        val doc = repo.observeDocument(id).first()!!
        assertEquals(listOf(page.id, second.id), doc.orderedPages.map { it.id })
        assertEquals(page.sha256, Hashing.sha256(original)) // после второй съёмки первый оригинал не изменился
    }

    @Test fun retakeMovesOriginalToTrashUnchanged() = runTest {
        val id = repo.createDocument(ScanMode.A4, DocumentSource.CAMERA, "Скан")
        val page = repo.addCapturedPage(id, jpeg(200, 300))
        val sha = page.sha256
        val name = repo.originalFile(page).name
        repo.trashPage(page.id)

        assertNull(repo.page(page.id))
        assertFalse(repo.originalFile(page).exists())
        val trashed = paths.trashed(id, name)
        assertTrue("оригинал в корзине", trashed.exists())
        assertEquals("байты не изменились", sha, Hashing.sha256(trashed))
    }

    @Test fun corruptImageIsRolledBackWithoutLeftovers() = runTest {
        val id = repo.createDocument(ScanMode.A4, DocumentSource.CAMERA, "Скан")
        val bad = File(paths.work, "bad.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(64) { 7 }) }
        try {
            repo.addCapturedPage(id, bad)
            fail("повреждённый JPEG должен быть отклонён")
        } catch (e: ImportRejectedException) {
            // ожидаемо
        }
        assertEquals(0, repo.observeDocument(id).first()!!.pages.size)
        val originals = File(paths.docs, "$id/original").listFiles().orEmpty()
        assertTrue("частичных оригиналов не осталось", originals.isEmpty())
        assertTrue(journal.pending().isEmpty())
    }

    @Test fun importRejectsFileUriAndLeavesNoDocument() = runTest {
        val f = jpeg(100, 100)
        val r = repo.import(listOf(Uri.fromFile(f)), ScanMode.CUSTOM, "Импорт")
        assertEquals(0, r.importedFiles)
        assertEquals(1, r.rejected.size)
        assertTrue(r.documentIds.isEmpty())
        assertTrue(repo.observeDocuments().first().isEmpty())
    }

    @Test fun sampleSizeKeepsLongSideAboveTarget() {
        assertEquals(1, PageImageLoader.sampleSize(1000, 800, 1000))
        assertEquals(2, PageImageLoader.sampleSize(4000, 3000, 1600))
        assertEquals(16, PageImageLoader.sampleSize(4000, 3000, 160))
        assertEquals(1, PageImageLoader.sampleSize(100, 100, 256))
    }
}
