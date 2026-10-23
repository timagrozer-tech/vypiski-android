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

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePage(pageId: String)

    /** Только для пустого документа, созданного неудачным импортом (страницы удаляются каскадно). */
    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("SELECT COUNT(*) FROM pages WHERE docId = :docId")
    suspend fun pageCount(docId: String): Int

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): DocumentEntity?
}
