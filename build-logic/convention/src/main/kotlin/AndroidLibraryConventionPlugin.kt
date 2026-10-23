import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import ru.scanner291.buildlogic.configureKotlinAndroid
import ru.scanner291.buildlogic.int
import ru.scanner291.buildlogic.libs

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.android.library")
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            testOptions.targetSdk = libs.int("targetSdk")
            lint.targetSdk = libs.int("targetSdk")
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
            testOptions.animationsDisabled = true
        }
        // Полный текст ошибок тестов в логе — CI выносит его в аннотации.
        tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
            testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showStackTraces = true
            }
        }
        dependencies {
            "testImplementation"(libs.findLibrary("junit").get())
            "testImplementation"(libs.findLibrary("kotlin-test").get())
            "androidTestImplementation"(libs.findLibrary("androidx-test-junit").get())
            "androidTestImplementation"(libs.findLibrary("androidx-test-runner").get())
        }
    }
}
