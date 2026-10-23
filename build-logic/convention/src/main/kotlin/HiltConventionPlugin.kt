import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import ru.scanner291.buildlogic.libs

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.google.devtools.ksp")
        dependencies {
            "ksp"(libs.findLibrary("hilt-compiler").get())
            "ksp"(libs.findLibrary("kotlin-metadata").get())
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            dependencies { "implementation"(libs.findLibrary("hilt-core").get()) }
        }
        pluginManager.withPlugin("com.android.base") {
            apply(plugin = "dagger.hilt.android.plugin")
            dependencies { "implementation"(libs.findLibrary("hilt-android").get()) }
        }
    }
}
