package ru.scanner291.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.common.storage.Hashing
import ru.scanner291.core.common.storage.OperationJournal
import ru.scanner291.core.common.storage.RecoveryDecision
import ru.scanner291.core.database.DocumentSource
import ru.scanner291.core.database.ScannerDatabase
import java.io.File
import java.nio.file.Files

/**
 * Этап 21: «убийство процесса» посреди операции. Процесс моделируется закрытием БД без завершения операции
 * и открытием новых экземпляров на тех же файлах: документ и оригинал на месте (байты те же), частичный
 * результат незавершённой операции удаляется при восстановлении, временные файлы не остаются.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProcessDeathTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val root: File = Files.createTempDirectory("death").toFile()
    private val name = "death-${System.nanoTime()}.db"

    private fun open(): Triple<ScannerDatabase, DocumentPaths, DocumentRepository> {
        val db = Room.databaseBuilder(ctx, ScannerDatabase::class.java, name).addMigrations(*ScannerDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()
        val paths = DocumentPaths(File(root, "files"), File(root, "cache")).also { it.ensureCreated() }
        return Triple(db, paths, DocumentRepository(db, paths, OperationJournal(paths.journal), ctx.contentResolver))
    }

    @After fun tearDown() { ctx.deleteDatabase(name); root.walkBottomUp().forEach { it.setWritable(true) }; root.deleteRecursively() }

    @Test fun documentsSurviveAndUnfinishedOperationIsRolledBack() = runBlocking {
        val (db1, paths1, repo1) = open()
        val img = File(paths1.work.apply { mkdirs() }, "cap.jpg")
        java.awt.image.BufferedImage(30, 40, java.awt.image.BufferedImage.TYPE_INT_RGB).also { javax.imageio.ImageIO.write(it, "jpg", img) }
        val id = repo1.createDocument(ScanMode.A4, DocumentSource.CAMERA, "Скан до сбоя")
        val page = repo1.addCapturedPage(id, img)
        val sha = Hashing.sha256(repo1.originalFile(page))
        // Начата операция (например, экспорт), записан частичный результат — и процесс «убит»
        val partial = File(paths1.docs, "$id/export-partial.pdf").apply { writeText("%PDF-частично") }
        OperationJournal(paths1.journal).begin("export", listOf(partial))
        db1.close()

        // Новый процесс
        val (db2, paths2, repo2) = open()
        val handled = OperationJournal(paths2.journal).recover { RecoveryDecision.ROLL_BACK }
        assertEquals(1, handled.size)
        assertFalse("частичный результат удалён", partial.exists())
        val doc = repo2.document(id)!!
        assertEquals("Скан до сбоя", doc.doc.title); assertEquals(1, doc.pages.size)
        assertEquals("оригинал побайтно тот же", sha, Hashing.sha256(repo2.originalFile(doc.pages.single())))
        assertTrue(OperationJournal(paths2.journal).pending().isEmpty())
        db2.close()
    }
}
