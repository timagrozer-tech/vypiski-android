package ru.scanner291.domain.naming

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Безопасные имена файлов с сохранением кириллицы.
 * Удаляются символы, недопустимые в Android/FAT/NTFS/ext4: < > : " / \ | ? * и управляющие.
 */
object FileNameSanitizer {
    private val FORBIDDEN = Regex("[<>:\"/\\\\|?*\\u0000-\\u001F\\u007F]")
    private val RESERVED = setOf(
        "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
    )
    /** Лимит имени файла в большинстве ФС — 255 байт UTF-8; оставляем запас под « (999)» и расширение. */
    const val MAX_BASE_BYTES = 200

    fun sanitizeBase(input: String, fallback: String = "Документ"): String {
        var s = input.replace(FORBIDDEN, " ")
            .replace(' ', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', ' ')
        if (s.isEmpty()) s = fallback
        if (s.lowercase() in RESERVED) s = "_$s"
        return truncateUtf8(s, MAX_BASE_BYTES).trimEnd('.', ' ').ifEmpty { fallback }
    }

    fun sanitizeExtension(ext: String): String =
        ext.trim().trimStart('.').lowercase().filter { it.isLetterOrDigit() }.take(10)

    /** Обрезка по границе символа (в т.ч. суррогатных пар), не превышая [maxBytes] в UTF-8. */
    fun truncateUtf8(s: String, maxBytes: Int): String {
        if (s.toByteArray(Charsets.UTF_8).size <= maxBytes) return s
        val sb = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val len = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + len > maxBytes) break
            sb.appendCodePoint(cp); bytes += len; i += Character.charCount(cp)
        }
        return sb.toString().trimEnd()
    }

    fun fileName(base: String, ext: String): String {
        val e = sanitizeExtension(ext)
        val b = sanitizeBase(base)
        return if (e.isEmpty()) b else "$b.$e"
    }
}

/**
 * Подбор уникального имени без перезаписи: «Иванов Иван Иванович.pdf» →
 * «Иванов Иван Иванович (2).pdf» → «… (3).pdf». Сравнение без учёта регистра
 * (многие ФС и SAF-провайдеры регистронезависимы).
 */
object UniqueNameResolver {
    private val SUFFIX = Regex("^(.*) \\((\\d{1,4})\\)$")

    fun resolve(base: String, ext: String, exists: (String) -> Boolean): String {
        val cleanBase = FileNameSanitizer.sanitizeBase(base)
        val first = FileNameSanitizer.fileName(cleanBase, ext)
        if (!exists(first)) return first
        val root = SUFFIX.matchEntire(cleanBase)?.groupValues?.get(1) ?: cleanBase
        for (n in 2..9999) {
            val candidate = FileNameSanitizer.fileName("$root ($n)", ext)
            if (!exists(candidate)) return candidate
        }
        error("Не удалось подобрать уникальное имя для «$first»")
    }

    /** Вариант с набором уже занятых имён (включая имена, выданные в этом же пакете). */
    fun resolve(base: String, ext: String, taken: MutableSet<String>): String {
        val lower = taken.map { it.lowercase() }.toMutableSet()
        val name = resolve(base, ext) { it.lowercase() in lower }
        taken += name
        return name
    }
}

/** Политика имени по умолчанию, если ФИО не используется или не найдено. */
object DefaultNames {
    private val FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm")
    fun byDate(now: LocalDateTime, prefix: String = "Скан"): String = "$prefix ${FMT.format(now)}"
}
