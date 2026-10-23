package ru.scanner291.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Этап 14: папки, теги, избранное, корзина, дубликаты и умные коллекции — запросами к индексу. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DocumentManagerDaoTest {
    private lateinit var db: ScannerDatabase
    private val dao get() = db.documentDao()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ScannerDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            for ((id, at) in listOf("a" to 1_000L, "b" to 2_000L, "c" to 3_000L)) {
                dao.insertDocument(DocumentEntity(id, "Документ $id", "A4", "CAMERA", at, at))
            }
            dao.insertPages(listOf(
                PageEntity("pa", "a", 0, "x", "sha-same", "image/jpeg", 1, 1, 1, null, 0),
                PageEntity("pb", "b", 0, "x", "sha-same", "image/jpeg", 1, 1, 1, null, 0),
                PageEntity("pc", "c", 0, "x", "sha-c", "image/jpeg", 1, 1, 1, null, 0),
            ))
        }
    }

    @After fun close() = db.close()

    private suspend fun ids(trash: Int = 0, folder: String? = null, fav: Int = 0, tag: String? = null, since: Long = 0, noText: Int = 0, dup: Int = 0) =
        dao.observeFiltered(trash, folder, fav, tag, since, noText, dup).first().map { it.doc.id }

    @Test fun foldersKeepDocumentsWhenDeleted() = runBlocking {
        dao.insertFolder(FolderEntity("f1", "Рапорты", 0))
        dao.moveToFolder("a", "f1"); dao.moveToFolder("c", "f1")
        assertEquals(listOf("c", "a"), ids(folder = "f1"))
        assertEquals(2, dao.observeFolders().first().single().count)
        dao.deleteFolder("f1")
        assertEquals(0, dao.observeFolders().first().size)
        assertEquals(3, dao.observeSummaries().first().size)          // документы не удалены
        assertEquals(null, dao.document("a")!!.doc.folderId)
    }

    @Test fun favoritesAndTrash() = runBlocking {
        dao.setFavorite("b", true)
        assertEquals(listOf("b"), ids(fav = 1))
        dao.setTrashed("b", 5_000)
        assertEquals(listOf("c", "a"), dao.observeSummaries().first().map { it.doc.id })
        assertEquals(listOf("b"), ids(trash = 1))
        assertEquals(emptyList<String>(), ids(fav = 1))              // в корзине не показывается в избранном
        assertEquals(listOf("b"), dao.trashedBefore(6_000))
        dao.setTrashed("b", null)
        assertEquals(3, ids().size)
    }

    @Test fun tagsAreUniqueAndFilter() = runBlocking {
        dao.insertTag(TagEntity("t1", "Отпуск")); dao.insertTag(TagEntity("t2", "Отпуск"))   // второй с тем же именем игнорируется
        assertEquals(1, dao.observeTags().first().size)
        dao.addTag(DocumentTag("a", "t1")); dao.addTag(DocumentTag("a", "t1"))
        assertEquals(listOf("a"), ids(tag = "t1"))
        assertEquals(listOf("Отпуск"), dao.tagsOf("a").map { it.name })
        dao.removeTag("a", "t1")
        assertEquals(emptyList<String>(), ids(tag = "t1"))
    }

    @Test fun smartCollections() = runBlocking {
        assertEquals(listOf("b", "a"), ids(dup = 1))                 // одинаковый SHA-256 страницы
        dao.setTrashed("b", 1)
        assertEquals(emptyList<String>(), ids(dup = 1))              // дубликат в корзине не считается
        dao.setTrashed("b", null)
        assertEquals(listOf("c", "b"), ids(since = 2_000))           // «Недавние»
        dao.saveText(PageTextEntity("pc", "c", "текст", "", "FAST", "k", false, 0, 0))
        assertEquals(listOf("b", "a"), ids(noText = 1))              // «Без текста»
        assertTrue(dao.searchText("текст*").isNotEmpty())
        dao.setTrashed("c", 1)
        assertTrue("корзина не попадает в поиск", dao.searchText("текст*").isEmpty())
    }
}
