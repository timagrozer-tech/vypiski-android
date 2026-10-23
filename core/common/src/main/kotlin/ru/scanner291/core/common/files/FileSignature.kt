package ru.scanner291.core.common.files

/** Поддерживаемые для импорта форматы, определяемые по содержимому, а не по расширению или MIME. */
enum class FileKind(val mime: String, val extension: String, val isImage: Boolean) {
    JPEG("image/jpeg", "jpg", true),
    PNG("image/png", "png", true),
    WEBP("image/webp", "webp", true),
    HEIC("image/heic", "heic", true),
    PDF("application/pdf", "pdf", false),
}

/**
 * Определение формата по сигнатуре (первые байты файла). Защищает от подмены расширения:
 * «справка.pdf», который на самом деле HTML или ZIP, будет отклонён.
 */
object FileSignature {
    /** Сколько байт нужно прочитать для определения. */
    const val HEADER_SIZE = 32

    fun detect(header: ByteArray): FileKind? {
        fun at(i: Int, vararg b: Int) = header.size >= i + b.size && b.indices.all { (header[i + it].toInt() and 0xFF) == b[it] }
        fun ascii(i: Int, s: String) = header.size >= i + s.length && s.indices.all { header[i + it].toInt().toChar() == s[it] }
        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> FileKind.JPEG
            at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> FileKind.PNG
            ascii(0, "RIFF") && ascii(8, "WEBP") -> FileKind.WEBP
            ascii(4, "ftyp") && HEIF_BRANDS.any { ascii(8, it) } -> FileKind.HEIC
            pdfHeaderOffset(header) >= 0 -> FileKind.PDF
            else -> null
        }
    }

    /** «%PDF-» допускается не с нулевого байта (до 1024 байт мусора разрешает спецификация); ищем в заголовке. */
    private fun pdfHeaderOffset(h: ByteArray): Int {
        val sig = "%PDF-".toByteArray()
        for (i in 0..(h.size - sig.size)) if (sig.indices.all { h[i + it] == sig[it] }) return i
        return -1
    }

    private val HEIF_BRANDS = listOf("heic", "heix", "hevc", "hevx", "mif1", "msf1", "heim", "heis", "avif")
}

/** Ограничения импорта (ТЗ v3.0 ч. XVIII: проверка входных данных, ограничение размеров). */
object ImportLimits {
    const val MAX_FILE_BYTES: Long = 200L * 1024 * 1024
    const val MAX_PDF_PAGES: Int = 2000
    /** Сторона изображения в пикселях: защита от «бомб» с огромным разрешением. */
    const val MAX_IMAGE_SIDE: Int = 20_000
}

sealed interface ImportCheck {
    data class Ok(val kind: FileKind) : ImportCheck
    data class Rejected(val reason: String) : ImportCheck
}

/** Проверка до копирования: схема URI, размер, сигнатура. Чистая функция — покрыта тестом. */
fun checkImport(scheme: String?, size: Long?, header: ByteArray): ImportCheck {
    if (scheme != "content") return ImportCheck.Rejected("Файл должен быть выбран через системный диалог")
    if (size != null && size > ImportLimits.MAX_FILE_BYTES) return ImportCheck.Rejected("Файл больше 200 МБ")
    if (size == 0L || header.isEmpty()) return ImportCheck.Rejected("Файл пустой")
    val kind = FileSignature.detect(header) ?: return ImportCheck.Rejected("Формат не поддерживается")
    return ImportCheck.Ok(kind)
}
