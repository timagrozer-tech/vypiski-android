import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.scanner291.android.application)
    alias(libs.plugins.scanner291.android.application.compose)
    alias(libs.plugins.scanner291.hilt)
    alias(libs.plugins.roborazzi)
}

/**
 * Подпись Release: ключи НЕ хранятся в репозитории.
 * Источник — файл keystore.properties (в .gitignore) или переменные окружения CI.
 * См. docs/SIGNING.md.
 */
val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    signingProps.getProperty(key) ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "SCANNER291_STORE_FILE")
val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).exists()

android {
    namespace = "ru.scanner291.app"

    defaultConfig {
        applicationId = "ru.scanner291"
        versionCode = 22
        versionName = "0.22.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "SCANNER291_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SCANNER291_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SCANNER291_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Без ключей release собирается неподписанным (assembleRelease не падает),
            // такой APK нельзя установить — это осознанно.
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    // Отдельные APK по архитектурам: OpenCV и Tesseract содержат нативные библиотеки (~10 МБ на ABI).
    // universalApk — для простой установки «одним файлом» на любой телефон.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
    testOptions {
        unitTests { isIncludeAndroidResources = true }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Отсутствие INTERNET — требование ТЗ, а не ошибка.
        disable += setOf("MissingPermission")
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:storage"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":core:camera"))
    implementation(project(":core:imageprocessing"))
    implementation(project(":core:hybrid"))
    implementation(project(":core:ocr"))
    implementation(project(":core:pdf"))
    implementation(project(":core:intelligence"))
    implementation(project(":core:quality"))
    implementation(project(":core:batch"))
    implementation(project(":core:performance"))
    implementation(project(":feature:home"))
    implementation(project(":feature:scanner"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:documents"))
    implementation(project(":feature:search"))
    implementation(project(":feature:export"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:batch"))
    implementation(project(":feature:diagnostics"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    kspTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * Проверка ИТОГОВОГО (объединённого со всеми библиотеками) манифеста:
 * разрешение INTERNET не должно появиться ни в одном варианте сборки.
 * Задача подключена к assemble<Variant>, поэтому APK с сетью собрать невозможно.
 */
abstract class VerifyNoInternetTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val text = mergedManifest.get().asFile.readText()
        val forbidden = listOf("android.permission.INTERNET")
        val found = forbidden.filter { p -> Regex("""uses-permission[^>]*android:name="${Regex.escape(p)}"""").containsMatchIn(text) }
        if (found.isNotEmpty()) {
            throw GradleException("В объединённом манифесте найдены запрещённые разрешения: $found (${mergedManifest.get().asFile})")
        }
        logger.lifecycle("OK: в объединённом манифесте нет разрешения INTERNET")
    }
}

/**
 * Этап 19: в объединённом манифесте нет экспортируемых компонентов, кроме экрана запуска (MainActivity с LAUNCHER);
 * исключение — компоненты, закрытые системным разрешением (android:permission, например DUMP у ProfileInstaller).
 * Резервное копирование выключено (allowBackup=false). Проверяется вариант release — то, что устанавливается.
 */
abstract class VerifyManifestSecurityTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val text = mergedManifest.get().asFile.readText()
        val problems = ArrayList<String>()
        val comp = Regex("<(activity|activity-alias|service|receiver|provider)\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL)
        for (m in comp.findAll(text)) {
            val attrs = m.groupValues[2]
            if (!Regex("android:exported=\"true\"").containsMatchIn(attrs)) continue
            val name = Regex("android:name=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1) ?: "?"
            val launcher = name.endsWith(".MainActivity")
            val protected = Regex("android:permission=\"[^\"]+\"").containsMatchIn(attrs)
            if (!launcher && !protected) problems += "${m.groupValues[1]} $name экспортируется без разрешения"
        }
        if (!Regex("android:allowBackup=\"false\"").containsMatchIn(text)) problems += "allowBackup не выключен"
        if (problems.isNotEmpty()) throw GradleException("Манифест небезопасен:\n" + problems.joinToString("\n"))
        logger.lifecycle("OK: экспортируется только экран запуска (и компоненты под системным разрешением), резервное копирование выключено")
    }
}

androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyNoInternetTask>("verifyNoInternet$cap") {
            group = "verification"
            description = "Проверяет отсутствие INTERNET в объединённом манифесте варианта ${variant.name}"
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        tasks.matching { it.name == "assemble$cap" }.configureEach { dependsOn(verify) }
        if (variant.buildType == "release") {
            val sec = tasks.register<VerifyManifestSecurityTask>("verifyManifestSecurity$cap") {
                group = "verification"
                description = "Нет экспортируемых компонентов, кроме экрана запуска; без резервного копирования (${variant.name})"
                mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            }
            tasks.matching { it.name == "assemble$cap" }.configureEach { dependsOn(sec) }
        }
    }
}

tasks.register("verifyNoNetwork") {
    group = "verification"
    description = "INTERNET отсутствует в манифестах всех вариантов + нет сетевых SDK в зависимостях"
    dependsOn(tasks.matching { it.name.startsWith("verifyNoInternet") })
    dependsOn(":verifyNoNetworkDependencies")
}
