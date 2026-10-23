package ru.scanner291.core.storage

import java.io.File

/**
 * Раскладка файлов приложения (см. ARCHITECTURE.md, раздел «Хранение»).
 * Всё — во внутреннем хранилище приложения, исключённом из резервного копирования.
 */
class DocumentPaths(filesDir: File, cacheDir: File) {
    val docs: File = File(filesDir, "docs")
    val journal: File = File(filesDir, "journal")
    val trash: File = File(filesDir, "trash")
    val vault: File = File(filesDir, "vault")
    val work: File = File(cacheDir, "work")
    /** Корень для поиска брошенных временных файлов атомарной записи. */
    val filesRoot: File = filesDir

    fun original(docId: String, pageId: String, ext: String = "jpg"): File = File(docs, "$docId/original/$pageId.$ext")
    fun trashed(docId: String, fileName: String): File = File(trash, "$docId/$fileName")
    /** Путь относительно filesDir — так он хранится в БД (переживает смену абсолютного пути). */
    fun relative(f: File): String = f.relativeTo(filesRoot).path
    fun absolute(relative: String): File = File(filesRoot, relative)
    fun processed(docId: String, pageId: String): File = File(docs, "$docId/processed/$pageId.jpg")
    fun thumbnail(docId: String, pageId: String): File = File(docs, "$docId/thumbs/$pageId.webp")
    fun recipe(docId: String, pageId: String): File = File(docs, "$docId/recipe/$pageId.json")

    fun ensureCreated() {
        listOf(docs, journal, trash, vault, work).forEach { it.mkdirs() }
    }
}
