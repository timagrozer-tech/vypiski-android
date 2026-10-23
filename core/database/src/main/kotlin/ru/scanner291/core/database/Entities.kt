package ru.scanner291.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Документ. Файлы лежат отдельно от БД (filesDir/docs/<id>/…); здесь — только индекс и метаданные. */
@Entity(tableName = "documents", indices = [Index("updatedAt")])
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** ScanMode.name */
    val mode: String,
    /** DocumentSource.name */
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class DocumentSource { CAMERA, IMPORT_IMAGE, IMPORT_PDF }

/**
 * Страница. [originalPath] — путь к неизменяемому оригиналу относительно filesDir;
 * [sha256] — сумма оригинала в момент сохранения (проверка целостности).
 * У страниц импортированного PDF общий оригинал и разный [pdfPageIndex].
 */
@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["docId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("docId"), Index(value = ["docId", "position"])],
)
data class PageEntity(
    @PrimaryKey val id: String,
    val docId: String,
    val position: Int,
    val originalPath: String,
    val sha256: String,
    val mime: String,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val pdfPageIndex: Int?,
    val createdAt: Long,
)

/** Строка списка документов. */
data class DocumentSummary(
    @Embedded val doc: DocumentEntity,
    @ColumnInfo(name = "pageCount") val pageCount: Int,
    @ColumnInfo(name = "firstPageId") val firstPageId: String?,
)

data class DocumentWithPages(
    @Embedded val doc: DocumentEntity,
    @Relation(parentColumn = "id", entityColumn = "docId") val pages: List<PageEntity>,
) {
    val orderedPages: List<PageEntity> get() = pages.sortedBy { it.position }
}
