package ru.scanner291.core.ocr

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Модели Tesseract во внутреннем хранилище: filesDir/ocr/<режим>/tessdata. «Быстрые» копируются из APK
 * при первом использовании, каждая проверяется по SHA-256 (assets/tessdata.sha256). Пакет «Высокая точность»
 * импортируется из zip (выбор файла пользователем, без сети) и принимается, только если SHA-256 моделей
 * совпадают с закреплёнными в приложении (assets/tessdata_best.sha256).
 */
@Singleton
class Tessdata @Inject constructor(@ApplicationContext private val context: Context) {
    fun root(mode: OcrMode): File = File(context.filesDir, "ocr/${mode.name.lowercase()}")
    private fun dir(mode: OcrMode) = File(root(mode), "tessdata")

    fun expected(mode: OcrMode): Map<String, String> =
        parseChecksums(context.assets.open(if (mode == OcrMode.FAST) "tessdata.sha256" else "tessdata_best.sha256").bufferedReader().readText())
            .filterKeys { it != "osd.traineddata" }

    /** Готовы ли модели режима (файлы есть и размеры совпадают; полная проверка суммы — при установке). */
    fun isInstalled(mode: OcrMode): Boolean = expected(mode).keys.all { File(dir(mode), it).isFile } && File(dir(mode), ".verified").isFile

    @Synchronized
    fun ensureFast() {
        if (isInstalled(OcrMode.FAST)) return
        val d = dir(OcrMode.FAST).apply { mkdirs() }
        for ((name, sha) in expected(OcrMode.FAST)) {
            context.assets.open("tessdata/$name").use { install(it, File(d, name), sha) }
        }
        File(d, ".verified").writeText("ok")
    }

    /** Импорт пакета «Высокая точность» (zip c rus/eng.traineddata). Ошибка — понятным текстом. */
    fun importBestPack(uri: Uri) {
        val want = expected(OcrMode.BEST)
        val d = dir(OcrMode.BEST).apply { mkdirs() }
        File(d, ".verified").delete()
        val found = HashSet<String>()
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Файл не открывается")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/')
                val sha = want[name]
                if (!e.isDirectory && sha != null) { install(zip, File(d, name), sha, closeInput = false); found += name }
            }
        }
        val missing = want.keys - found
        if (missing.isNotEmpty()) throw IOException("В пакете нет моделей: ${missing.joinToString()}")
        File(d, ".verified").writeText("ok")
    }

    /** Копия с проверкой SHA-256 до замены файла: повреждённая или чужая модель не устанавливается. */
    private fun install(src: InputStream, dst: File, sha: String, closeInput: Boolean = true) {
        val tmp = File(dst.parentFile, dst.name + ".part")
        val md = MessageDigest.getInstance("SHA-256")
        tmp.outputStream().use { out ->
            val b = ByteArray(1 shl 16)
            while (true) { val n = src.read(b); if (n < 0) break; md.update(b, 0, n); out.write(b, 0, n) }
            out.fd.sync()
        }
        if (closeInput) src.close()
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        if (actual != sha) { tmp.delete(); throw IOException("Модель ${dst.name} не совпадает с ожидаемой (SHA-256)") }
        if (!tmp.renameTo(dst)) { tmp.delete(); throw IOException("Не удалось сохранить ${dst.name}") }
    }

    companion object {
        /** «sha  имя» по строкам; комментарии «#» пропускаются. Покрыто тестом. */
        fun parseChecksums(text: String): Map<String, String> = text.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { l -> l.split(Regex("\\s+")).let { it[1] to it[0].lowercase() } }
    }
}
