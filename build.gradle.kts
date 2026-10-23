plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

/**
 * Запрещённые группы зависимостей: сетевые SDK, аналитика, реклама, облако.
 * Проверяются во всех конфигурациях runtime всех модулей.
 */
val forbiddenGroups = listOf(
    "com.google.firebase", "com.google.android.gms", "com.squareup.okhttp3", "com.squareup.retrofit2",
    "io.ktor", "com.android.volley", "com.google.mlkit", "com.facebook", "com.appsflyer",
    "io.sentry", "com.yandex", "com.microsoft.appcenter", "com.amplitude", "com.mixpanel",
    "com.google.ads", "com.applovin", "com.unity3d.ads",
)

tasks.register("verifyNoNetworkDependencies") {
    group = "verification"
    description = "Падает, если в runtime-зависимостях есть сетевые/аналитические/рекламные SDK."
    // Разрешает конфигурации других проектов во время выполнения — с configuration cache несовместимо.
    notCompatibleWithConfigurationCache("Обходит конфигурации всех подпроектов во время выполнения")
    doLast {
        val offenders = mutableListOf<String>()
        subprojects.forEach { p ->
            p.configurations.filter { it.isCanBeResolved && it.name.endsWith("RuntimeClasspath") }.forEach { cfg ->
                runCatching { cfg.resolvedConfiguration.resolvedArtifacts }.getOrNull()?.forEach { a ->
                    val g = a.moduleVersion.id.group
                    if (forbiddenGroups.any { g == it || g.startsWith("$it.") }) {
                        offenders += "${p.path}:${cfg.name} -> ${a.moduleVersion.id}"
                    }
                }
            }
        }
        if (offenders.isNotEmpty()) throw GradleException("Найдены запрещённые зависимости:\n" + offenders.joinToString("\n"))
        println("OK: запрещённых сетевых зависимостей нет")
    }
}
