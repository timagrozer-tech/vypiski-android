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
    /** Углы листа «x,y;x,y;x,y;x,y» в нормализованных координатах прямого изображения (QuadCodec); null — не заданы. */
    val corners: String? = null,
    /** CornersSource.name */
    val cornersSource: String? = null,
    /** Рецепт обработки «v1;f=…;r=…» (RecipeCodec); null — по умолчанию для режима документа. Этап 7. */
    val recipe: String? = null,
)

/** Откуда углы: найдены автоматически, поправлены вручную или выбран весь лист. */
enum class CornersSource { AUTO, MANUAL, FULL }

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

/**
 * Текстовый слой страницы (этап 10): распознанный (или исправленный пользователем) текст и слова
 * с рамками. [words] — строки «текст\tl,t,r,b\tуверенность» (нормализованные координаты обработанной страницы).
 * [recipeKey] — рецепт, по которому распознано: если страницу обработали иначе, текст помечается устаревшим.
 */
@Entity(
    tableName = "page_text",
    foreignKeys = [ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("docId")],
)
data class PageTextEntity(
    @PrimaryKey val pageId: String,
    val docId: String,
    val text: String,
    val words: String,
    /** OcrMode.name */
    val mode: String,
    val recipeKey: String,
    /** Текст исправлен вручную. */
    val edited: Boolean,
    val createdAt: Long,
    val durationMs: Long,
)

/** Полнотекстовый индекс (FTS4, unicode61 — регистр кириллицы не важен). Хранит копию текста страницы. */
@androidx.room.Fts4(tokenizer = androidx.room.FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["pageId", "docId"])
@Entity(tableName = "page_text_fts")
data class PageTextFts(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val pageId: String,
    val docId: String,
    val text: String,
)

/** Результат поиска по тексту: страница и фрагмент с [выделением]. */
data class TextHit(val pageId: String, val docId: String, val snippet: String)
