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
        WHERE d.trashedAt IS NULL
        ORDER BY d.updatedAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<DocumentSummary>>

    /**
     * Список с фильтрами (этап 14) — умные коллекции как запросы, без копий файлов.
     * [trash] = 1 — только корзина; [since] > 0 — изменённые после; [noText] = 1 — без распознанного текста;
     * [duplicates] = 1 — есть страница с таким же SHA-256 в другом документе.
     */
    @Query(
        """
        SELECT d.*,
          (SELECT COUNT(*) FROM pages p WHERE p.docId = d.id) AS pageCount,
          (SELECT p.id FROM pages p WHERE p.docId = d.id ORDER BY p.position LIMIT 1) AS firstPageId
        FROM documents d
        WHERE ((:trash = 1 AND d.trashedAt IS NOT NULL) OR (:trash = 0 AND d.trashedAt IS NULL))
          AND (:folderId IS NULL OR d.folderId = :folderId)
          AND (:favorite = 0 OR d.favorite = 1)
          AND (:tagId IS NULL OR EXISTS (SELECT 1 FROM document_tags t WHERE t.docId = d.id AND t.tagId = :tagId))
          AND (:since = 0 OR d.updatedAt >= :since)
          AND (:noText = 0 OR NOT EXISTS (SELECT 1 FROM pages p JOIN page_text x ON x.pageId = p.id WHERE p.docId = d.id))
          AND (:duplicates = 0 OR d.id IN (SELECT p.docId FROM pages p WHERE p.sha256 IN
                (SELECT q.sha256 FROM pages q JOIN documents e ON e.id = q.docId WHERE e.trashedAt IS NULL GROUP BY q.sha256 HAVING COUNT(DISTINCT q.docId) > 1)))
        ORDER BY d.updatedAt DESC
        """,
    )
    fun observeFiltered(trash: Int, folderId: String?, favorite: Int, tagId: String?, since: Long, noText: Int, duplicates: Int): Flow<List<DocumentSummary>>

    @Query("UPDATE documents SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE documents SET folderId = :folderId WHERE id = :id")
    suspend fun moveToFolder(id: String, folderId: String?)

    @Query("UPDATE documents SET trashedAt = :at WHERE id = :id")
    suspend fun setTrashed(id: String, at: Long?)

    @Insert suspend fun insertFolder(f: FolderEntity)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: String, name: String)

    /** Папка удаляется, документы остаются (без папки). */
    @Transaction
    suspend fun deleteFolder(id: String) { clearFolder(id); deleteFolderRow(id) }

    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :id")
    suspend fun clearFolder(id: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderRow(id: String)

    @Query("SELECT f.*, (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id AND d.trashedAt IS NULL) AS count FROM folders f ORDER BY f.name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderSummary>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTag(t: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun tagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addTag(x: DocumentTag)

    @Query("DELETE FROM document_tags WHERE docId = :docId AND tagId = :tagId")
    suspend fun removeTag(docId: String, tagId: String)

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT t.* FROM tags t JOIN document_tags x ON x.tagId = t.id WHERE x.docId = :docId ORDER BY t.name")
    suspend fun tagsOf(docId: String): List<TagEntity>

    /** Корзина старше [before] (для очистки — только по подтверждению пользователя). */
    @Query("SELECT id FROM documents WHERE trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun trashedBefore(before: Long): List<String>

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

    @Query("UPDATE documents SET title = :title, updatedAt = :at WHERE id = :id")
    suspend fun rename(id: String, title: String, at: Long)

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
        insertFts(PageTextFts(pageId = t.pageId, documentId = t.docId, text = t.text))
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
        "SELECT f.pageId AS pageId, f.documentId AS docId, snippet(page_text_fts, '[', ']', '…', 2, 12) AS snippet " +
            "FROM page_text_fts f JOIN pages p ON p.id = f.pageId JOIN documents d ON d.id = p.docId AND d.trashedAt IS NULL " +
            "WHERE page_text_fts MATCH :match LIMIT 200",
    )
    suspend fun searchText(match: String): List<TextHit>

    /** Все документы (поиск по названию — в Kotlin: LIKE в SQLite не различает регистр только для латиницы). */
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    suspend fun allDocuments(): List<DocumentEntity>
}
