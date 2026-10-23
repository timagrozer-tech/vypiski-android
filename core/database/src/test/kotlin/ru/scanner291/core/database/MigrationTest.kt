package ru.scanner291.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * База версии 1 (как её создала сборка этапа 5) открывается текущей версией (через все миграции) без потери документов.
 * SQL v1 повторяет то, что генерирует Room для сущностей этапа 5.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    @Test fun v1DatabaseKeepsDocumentsAfterUpgrade() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        ctx.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `documents` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `mode` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_updatedAt` ON `documents` (`updatedAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `pages` (`id` TEXT NOT NULL, `docId` TEXT NOT NULL, `position` INTEGER NOT NULL, `originalPath` TEXT NOT NULL, `sha256` TEXT NOT NULL, `mime` TEXT NOT NULL, `byteSize` INTEGER NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `pdfPageIndex` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`docId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pages_docId` ON `pages` (`docId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pages_docId_position` ON `pages` (`docId`, `position`)")
            db.execSQL("INSERT INTO documents VALUES ('d1', 'Рапорт Иванова', 'REPORT', 'CAMERA', 1, 2)")
            db.execSQL("INSERT INTO pages VALUES ('p1', 'd1', 0, 'docs/d1/original/p1.jpg', 'abc', 'image/jpeg', 10, 300, 400, NULL, 1)")
            db.version = 1
        }
        val room = Room.databaseBuilder(ctx, ScannerDatabase::class.java, name)
            .addMigrations(*ScannerDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val doc = room.documentDao().document("d1")!!
            assertEquals("Рапорт Иванова", doc.doc.title)
            assertEquals(1, doc.pages.size)
            assertNull(doc.pages.single().corners)
            room.documentDao().setCorners("p1", "0.1,0.1;0.9,0.1;0.9,0.9;0.1,0.9", CornersSource.MANUAL.name)
            assertEquals(CornersSource.MANUAL.name, room.documentDao().page("p1")!!.cornersSource)
            assertNull(doc.pages.single().recipe)
            room.documentDao().setRecipe("p1", "v1;f=BW;r=90;b=0.00;c=1.00;s=1;n=1")
            assertEquals("v1;f=BW;r=90;b=0.00;c=1.00;s=1;n=1", room.documentDao().page("p1")!!.recipe)
            assertEquals("Рапорт Иванова", room.documentDao().document("d1")!!.doc.title)
            // v4: текстовый слой и поиск без учёта регистра кириллицы, по началу слова
            val dao = room.documentDao()
            dao.saveText(PageTextEntity("p1", "d1", "Рапорт об отпуске. Ефрейтор Иванов Иван Иванович", "", "FAST", "k", false, 1, 10))
            assertEquals(1, dao.searchText("иванов*").size)
            assertEquals(1, dao.searchText("ОТПУСК*").size)
            assertEquals(0, dao.searchText("петров*").size)
            assertTrue(dao.searchText("ефрейтор*").single().snippet.contains("[Ефрейтор]"))
            // повторное сохранение не дублирует индекс
            dao.saveText(PageTextEntity("p1", "d1", "Исправленный текст", "", "FAST", "k", true, 2, 0))
            assertEquals(0, dao.searchText("иванов*").size); assertEquals(1, dao.searchText("исправлен*").size)
            assertEquals(1, dao.allDocuments().size)
        } finally {
            room.close()
            ctx.deleteDatabase(name)
        }
    }
}
