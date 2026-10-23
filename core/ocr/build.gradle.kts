import java.security.MessageDigest
plugins {
    alias(libs.plugins.scanner291.android.library)
}

android {
    namespace = "ru.scanner291.core.ocr"
    // Модели копируются из APK во внутреннее хранилище при первом запуске;
    // без сжатия копирование быстрее, а размер APK почти не меняется (модели уже плотные).
    androidResources { noCompress += "traineddata" }
}

dependencies {
    api(project(":core:common"))
    implementation(libs.tesseract4android)
    implementation(libs.kotlinx.coroutines.android)
}

/**
 * Проверка встроенных OCR-моделей: каждая модель из tessdata.sha256 присутствует в assets
 * и совпадает по SHA-256. Защищает от подмены/порчи файлов и от сборки APK без моделей.
 */
abstract class VerifyTessdataTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE) abstract val checksums: RegularFileProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val tessdataDir: DirectoryProperty

    @TaskAction
    fun verify() {
        val expected = checksums.get().asFile.readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line -> line.split(Regex("\\s+")).let { it[1] to it[0].lowercase() } }
        val problems = expected.mapNotNull { (name, sha) ->
            val f = tessdataDir.get().asFile.resolve(name)
            when {
                !f.isFile -> "нет файла $name"
                else -> {
                    val md = MessageDigest.getInstance("SHA-256")
                    f.inputStream().use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
                    val actual = md.digest().joinToString("") { "%02x".format(it) }
                    if (actual != sha) "$name: SHA-256 $actual, ожидалось $sha" else null
                }
            }
        }
        if (problems.isNotEmpty()) throw GradleException("OCR-модели не прошли проверку:\n" + problems.joinToString("\n"))
        logger.lifecycle("OK: OCR-модели (${expected.keys.sorted().joinToString()}) совпадают с tessdata.sha256")
    }
}

val verifyTessdata = tasks.register<VerifyTessdataTask>("verifyTessdata") {
    group = "verification"
    checksums.set(layout.projectDirectory.file("tessdata.sha256"))
    tessdataDir.set(layout.projectDirectory.dir("src/main/assets/tessdata"))
}
tasks.named("preBuild") { dependsOn(verifyTessdata) }
