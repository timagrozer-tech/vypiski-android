package ru.scanner291.core.storage

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.scanner291.core.common.storage.Hashing
import ru.scanner291.core.common.storage.OperationJournal
import ru.scanner291.core.database.ScannerDatabase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthKind(val title: String) {
    MISSING_ORIGINAL("Нет файла страницы"),
    CORRUPTED("Файл страницы повреждён (сумма не совпадает)"),
    UNREADABLE("Изображение не открывается — миниатюры не будет"),
    EMPTY_DOCUMENT("Документ без страниц"),
    ORPHAN_FILE("Файл без документа"),
    UNFINISHED_OPERATION("Незавершённая операция"),
    INDEX_MISSING("Текст не в поисковом индексе"),
    INDEX_ORPHAN("Лишние записи в поисковом индексе"),
    DUPLICATE("Возможный дубликат"),
}

/** Что можно сделать (только после подтверждения пользователя). null — только сведения. */
enum class HealthFix(val title: String) {
    TRASH_DOCUMENT("Переместить документ в корзину"),
    MOVE_FILE_TO_TRASH("Переместить файл в корзину приложения"),
    RECOVER_OPERATIONS("Откатить незавершённые операции"),
    REBUILD_INDEX("Пересоздать поисковый индекс"),
}

data class HealthIssue(val kind: HealthKind, val detail: String, val fix: HealthFix?, val docId: String? = null, val file: File? = null)

/**
 * Document Health Center (этап 18): проверка библиотеки. Ничего не исправляет сам — только находит;
 * исправление запускается пользователем ([fix]) после подтверждения. Ни одно исправление не меняет
 * оригиналы страниц (пересоздание индекса работает только с БД — проверено HealthCenterTest).
 */
@Singleton
class HealthCenter @Inject constructor(
    private val db: ScannerDatabase,
    private val paths: DocumentPaths,
    private val journal: OperationJournal,
    private val repo: DocumentRepository,
) {
    private val dao get() = db.documentDao()

    suspend fun scan(checkSums: Boolean = true): List<HealthIssue> = withContext(Dispatchers.IO) {
        val out = ArrayList<HealthIssue>()
        val pages = dao.allPages()
        val known = HashSet<String>()
        val seenSha = HashMap<String, String>()
        for (p in pages) {
            val f = paths.absolute(p.originalPath); known += f.absolutePath
            when {
                !f.isFile -> out += HealthIssue(HealthKind.MISSING_ORIGINAL, "Страница ${p.position + 1}", null, p.docId)
                checkSums && Hashing.sha256(f) != p.sha256 -> out += HealthIssue(HealthKind.CORRUPTED, "Страница ${p.position + 1}", null, p.docId)
                p.mime.startsWith("image/") && BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(f.path, it) }.outWidth <= 0 && p.width <= 0 ->
                    out += HealthIssue(HealthKind.UNREADABLE, "Страница ${p.position + 1}", null, p.docId)
            }
            val other = seenSha[p.sha256]
            if (other != null && other != p.docId) out += HealthIssue(HealthKind.DUPLICATE, "Одинаковые страницы в двух документах (см. «Дубликаты»)", null, p.docId)
            else seenSha[p.sha256] = p.docId
        }
        for (d in dao.emptyDocuments()) out += HealthIssue(HealthKind.EMPTY_DOCUMENT, d.title, HealthFix.TRASH_DOCUMENT, d.id)
        // Файлы оригиналов, на которые не ссылается ни одна страница
        paths.docs.walkTopDown().filter { it.isFile && it.parentFile?.name == "original" && it.absolutePath !in known }
            .forEach { out += HealthIssue(HealthKind.ORPHAN_FILE, it.name, HealthFix.MOVE_FILE_TO_TRASH, file = it) }
        journal.pending().takeIf { it.isNotEmpty() }?.let { out += HealthIssue(HealthKind.UNFINISHED_OPERATION, "Операций: ${it.size}", HealthFix.RECOVER_OPERATIONS) }
        dao.textsMissingInIndex().takeIf { it.isNotEmpty() }?.let { out += HealthIssue(HealthKind.INDEX_MISSING, "Страниц: ${it.size}", HealthFix.REBUILD_INDEX) }
        dao.orphanIndexRows().takeIf { it > 0 }?.let { out += HealthIssue(HealthKind.INDEX_ORPHAN, "Записей: $it", HealthFix.REBUILD_INDEX) }
        out
    }

    /** Исправление — вызывается только после подтверждения на экране. */
    suspend fun fix(issue: HealthIssue) = withContext(Dispatchers.IO) {
        when (issue.fix) {
            HealthFix.TRASH_DOCUMENT -> repo.trashDocument(issue.docId!!)
            HealthFix.MOVE_FILE_TO_TRASH -> {
                val f = issue.file!!; val dst = File(paths.trash, "orphans/${f.name}")
                dst.parentFile?.mkdirs(); f.setWritable(true)
                if (!f.renameTo(dst)) throw java.io.IOException("Не удалось перенести ${f.name}")
            }
            HealthFix.RECOVER_OPERATIONS -> journal.recover { ru.scanner291.core.common.storage.RecoveryDecision.ROLL_BACK }
            HealthFix.REBUILD_INDEX -> dao.rebuildFts()
            null -> Unit
        }
    }
}
