import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import ru.scanner291.buildlogic.configureKotlinJvm
import ru.scanner291.buildlogic.libs

/** Чистый Kotlin/JVM-модуль: без Android API, тестируется без эмулятора. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "org.jetbrains.kotlin.jvm")
        configureKotlinJvm()
        dependencies {
            "testImplementation"(libs.findLibrary("junit").get())
            "testImplementation"(libs.findLibrary("kotlin-test").get())
        }
        tasks.withType<Test>().configureEach {
            systemProperty("file.encoding", "UTF-8")
            testLogging {
                events("passed", "failed", "skipped")
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }
}
