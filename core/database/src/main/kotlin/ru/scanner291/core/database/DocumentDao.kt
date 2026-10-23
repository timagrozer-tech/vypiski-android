package ru.scanner291.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query(
        """
        SELECT d.*,
          (SELECT COUNT(*) FROM pages p WHERE p.docId = d.id) AS pageCount,
          (SELECT p.id FROM pages p WHERE p.docId = d.id ORDER BY p.position LIMIT 1) AS firstPageId
        FROM documents d
        ORDER BY d.updatedAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<DocumentSummary>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDocument(id: String): Flow<DocumentWithPages?>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun document(id: String): DocumentWithPages?

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun page(id: String): PageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(doc: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPages(pages: List<PageEntity>)

    @Query("SELECT COALESCE(MAX(position), -1) FROM pages WHERE docId = :docId")
    suspend fun maxPosition(docId: String): Int

    @Query("UPDATE documents SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("UPDATE pages SET corners = :corners, cornersSource = :source WHERE id = :pageId")
    suspend fun setCorners(pageId: String, corners: String?, source: String?)

    @Query("UPDATE pages SET recipe = :recipe WHERE id = :pageId")
    suspend fun setRecipe(pageId: String, recipe: String?)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePage(pageId: String)

    /** Только для пустого документа, созданного неудачным импортом (страницы удаляются каскадно). */
    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("SELECT COUNT(*) FROM pages WHERE docId = :docId")
    suspend fun pageCount(docId: String): Int

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): DocumentEntity?

    // ── Текстовый слой и поиск (этап 10) ──
    @androidx.room.Upsert
    suspend fun upsertText(t: PageTextEntity)

    @Query("DELETE FROM page_text_fts WHERE pageId = :pageId")
    suspend fun deleteFts(pageId: String)

    @androidx.room.Insert
    suspend fun insertFts(f: PageTextFts)

    /** Текст страницы и индекс обновляются вместе. */
    @Transaction
    suspend fun saveText(t: PageTextEntity) {
        upsertText(t)
        deleteFts(t.pageId)
        insertFts(PageTextFts(pageId = t.pageId, docId = t.docId, text = t.text))
    }

    @Query("SELECT * FROM page_text WHERE docId = :docId")
    fun observeTexts(docId: String): Flow<List<PageTextEntity>>

    @Query("SELECT * FROM page_text WHERE pageId = :pageId")
    suspend fun text(pageId: String): PageTextEntity?

    /**
     * Поиск по тексту: [match] — выражение FTS (слова с «*» на конце). Только страницы, которые есть в БД
     * (удалённые в корзину не находятся). Фрагмент — до 12 слов вокруг совпадения.
     */
    @Query(
        "SELECT f.pageId AS pageId, f.docId AS docId, snippet(page_text_fts, '[', ']', '…', 2, 12) AS snippet " +
            "FROM page_text_fts f JOIN pages p ON p.id = f.pageId WHERE page_text_fts MATCH :match LIMIT 200",
    )
    suspend fun searchText(match: String): List<TextHit>

    /** Все документы (поиск по названию — в Kotlin: LIKE в SQLite не различает регистр только для латиницы). */
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    suspend fun allDocuments(): List<DocumentEntity>
}
