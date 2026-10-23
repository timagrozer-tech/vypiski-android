import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import ru.scanner291.buildlogic.libs

/** Фича-модуль: Compose-библиотека + Hilt + дизайн-система + ViewModel + навигация + Robolectric-тесты экранов. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "scanner291.android.library.compose")
        apply(plugin = "scanner291.hilt")
        // Типобезопасные маршруты Navigation Compose (@Serializable).
        apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            "implementation"(project(":core:designsystem"))
            "implementation"(project(":core:common"))
            "implementation"(libs.findLibrary("androidx-hilt-navigation-compose").get())
            "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            "implementation"(libs.findLibrary("androidx-navigation-compose").get())
            "implementation"(libs.findLibrary("kotlinx-serialization-json").get())
            "testImplementation"(libs.findLibrary("robolectric").get())
            "testImplementation"(libs.findLibrary("androidx-test-junit").get())
            "testImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
            "androidTestImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
            "debugImplementation"(libs.findLibrary("androidx-compose-ui-test-manifest").get())
        }
    }
}
