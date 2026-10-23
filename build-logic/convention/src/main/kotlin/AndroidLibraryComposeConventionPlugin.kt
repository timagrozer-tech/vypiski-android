import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import ru.scanner291.buildlogic.configureAndroidCompose

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "scanner291.android.library")
        apply(plugin = "org.jetbrains.kotlin.plugin.compose")
        configureAndroidCompose(extensions.getByType<LibraryExtension>())
    }
}
