package ru.scanner291.core.common.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

class InsufficientSpaceException(val required: Long, val available: Long) :
    IOException("Недостаточно места: нужно ${required / 1024} КиБ, доступно ${available / 1024} КиБ")

/**
 * Безопасная запись файла: временный файл в том же каталоге → fsync → атомарное переименование.
 * Существующий файл НЕ перезаписывается, если явно не передано [overwrite] = true
 * (это значение UI передаёт только после подтверждения пользователя).
 */
object AtomicFileWriter {
    const val TEMP_PREFIX = ".tmp-"

    fun write(target: File, overwrite: Boolean = false, expectedSize: Long = 0, block: (OutputStream) -> Unit): File {
        val dir = target.absoluteFile.parentFile ?: throw IOException("Нет каталога для $target")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Не удалось создать каталог $dir")
        if (target.exists() && !overwrite) throw FileAlreadyExistsException(target.path, null, "Файл уже существует")
        requireFreeSpace(dir, expectedSize)
        // Имя временного файла — только ASCII: не зависит от кодировки имён ФС.
        val tmp = File(dir, "$TEMP_PREFIX${UUID.randomUUID()}")
        try {
            FileOutputStream(tmp).use { fos ->
                block(fos)
                fos.flush()
                fos.fd.sync()
            }
            move(tmp, target, overwrite)
            return target
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    private fun move(tmp: File, target: File, overwrite: Boolean) {
        val opts = buildList {
            add(StandardCopyOption.ATOMIC_MOVE)
            if (overwrite) add(StandardCopyOption.REPLACE_EXISTING)
        }.toTypedArray()
        try {
            if (!overwrite && target.exists()) throw FileAlreadyExistsException(target.path)
            Files.move(tmp.toPath(), target.toPath(), *opts)
        } catch (e: AtomicMoveNotSupportedException) {
            // На некоторых ФС атомарное перемещение недоступно — переносим обычным rename в том же каталоге.
            if (!overwrite && target.exists()) throw FileAlreadyExistsException(target.path)
            if (!tmp.renameTo(target)) throw IOException("Не удалось переименовать ${tmp.name}")
        }
    }

    /** Проверка свободного места с запасом 1,5× и минимальным резервом 20 МБ. */
    fun requireFreeSpace(dir: File, expectedBytes: Long) {
        if (expectedBytes <= 0) return
        val need = (expectedBytes * 1.5).toLong() + 20L * 1024 * 1024
        val free = dir.usableSpace
        if (free in 1 until need) throw InsufficientSpaceException(need, free)
    }

    /** Удаляет брошенные временные файлы (после сбоя) старше [olderThanMs]. */
    fun cleanupStaleTemps(dir: File, olderThanMs: Long, now: Long = System.currentTimeMillis()): Int {
        var n = 0
        dir.walkTopDown().filter { it.isFile && it.name.startsWith(TEMP_PREFIX) && now - it.lastModified() >= olderThanMs }
            .forEach { if (it.delete()) n++ }
        return n
    }
}

/**
 * Хранилище оригиналов: файл записывается один раз и становится только для чтения.
 * Повторная запись в тот же путь запрещена — оригинал не может быть изменён.
 */
object OriginalStore {
    fun store(target: File, block: (OutputStream) -> Unit): File {
        val f = AtomicFileWriter.write(target, overwrite = false, block = block)
        f.setWritable(false, false)
        return f
    }
}

object Hashing {
    fun sha256(file: File): String = file.inputStream().use { input ->
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}

// ---------------------------------------------------------------- журнал операций

enum class OpState { STARTED, COMMITTED }

/** Запись журнала: что операция собиралась создать и какие временные файлы использует. */
data class OpEntry(
    val id: String,
    val type: String,
    val state: OpState,
    val createdAt: Long,
    /** Файлы, которые операция создаёт; при откате незавершённой операции удаляются. */
    val outputs: List<String>,
    /** Произвольные данные операции (например, docId). Без переводов строк. */
    val payload: Map<String, String>,
)

enum class RecoveryDecision { ROLL_BACK, ROLL_FORWARD }

/**
 * Журнал многошаговых операций (сохранение документа, экспорт, пакет, перенос в корзину).
 * Порядок: begin() пишет намерение (fsync) → операция → commit() удаляет запись.
 * При старте [recover] находит незавершённые операции и откатывает их (удаляет частичные выходы)
 * или доводит до конца — решает вызывающий код по типу операции.
 */
class OperationJournal(private val dir: File) {
    init { dir.mkdirs() }

    fun begin(type: String, outputs: List<File>, payload: Map<String, String> = emptyMap(), now: Long = System.currentTimeMillis()): OpEntry {
        val e = OpEntry(UUID.randomUUID().toString(), type, OpState.STARTED, now, outputs.map { it.absolutePath }, payload)
        persist(e)
        return e
    }

    fun commit(e: OpEntry) {
        persist(e.copy(state = OpState.COMMITTED))
        File(dir, "${e.id}.op").delete()
    }

    fun pending(): List<OpEntry> = dir.listFiles { f -> f.name.endsWith(".op") }.orEmpty()
        .mapNotNull { runCatching { parse(it.readText(Charsets.UTF_8)) }.getOrNull() }
        .filter { it.state == OpState.STARTED }
        .sortedBy { it.createdAt }

    /** @return список обработанных операций с принятым решением. */
    fun recover(decide: (OpEntry) -> RecoveryDecision): List<Pair<OpEntry, RecoveryDecision>> =
        pending().map { e ->
            val d = runCatching { decide(e) }.getOrDefault(RecoveryDecision.ROLL_BACK)
            if (d == RecoveryDecision.ROLL_BACK) e.outputs.forEach { p -> File(p).let { f -> f.setWritable(true); f.delete() } }
            File(dir, "${e.id}.op").delete()
            e to d
        }

    private fun persist(e: OpEntry) {
        val f = File(dir, "${e.id}.op")
        val text = serialize(e)
        val tmp = File(dir, "${e.id}.op.tmp")
        FileOutputStream(tmp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw IOException("Журнал: не удалось записать ${f.name}") }
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("=", "\\e").replace("|", "\\p")
    private fun unesc(s: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 'n' -> sb.append('\n'); 'e' -> sb.append('='); 'p' -> sb.append('|'); else -> sb.append(s[i + 1]) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun serialize(e: OpEntry): String = buildString {
        appendLine("id=${esc(e.id)}"); appendLine("type=${esc(e.type)}"); appendLine("state=${e.state}")
        appendLine("created=${e.createdAt}")
        appendLine("outputs=${e.outputs.joinToString("|") { esc(it) }}")
        e.payload.forEach { (k, v) -> appendLine("p.${esc(k)}=${esc(v)}") }
    }

    private fun parse(text: String): OpEntry {
        val m = text.lines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
        return OpEntry(
            id = unesc(m.getValue("id")), type = unesc(m.getValue("type")), state = OpState.valueOf(m.getValue("state")),
            createdAt = m.getValue("created").toLong(),
            outputs = m["outputs"].orEmpty().split('|').filter { it.isNotEmpty() }.map { unesc(it) },
            payload = m.filterKeys { it.startsWith("p.") }.map { (k, v) -> unesc(k.removePrefix("p.")) to unesc(v) }.toMap(),
        )
    }
}
