import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ru.scanner291.core.ocr"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Модели копируются из APK во внутреннее хранилище при первом запуске;
    // без сжатия копирование быстрее, а размер APK почти не меняется (модели уже плотные).
    androidResources { noCompress += "traineddata" }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.tesseract4android)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
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
