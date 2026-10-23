pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Только для Tesseract4Android (Apache-2.0). Ограничено группой.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.adaptech-cz") }
        }
    }
}

rootProject.name = "Scanner291"

include(":app")
include(":core:domain")
include(":core:designsystem")
include(":core:data")
include(":core:imaging")
include(":core:ocr")
include(":core:pdf")
include(":core:security")
