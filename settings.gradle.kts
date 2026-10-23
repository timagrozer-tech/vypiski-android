pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // Только для Tesseract4Android (Apache-2.0). Ограничено группой.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("cz.adaptech.tesseract4android") }
        }
    }
}

rootProject.name = "Scanner291"

include(":app")

// core — инфраструктура и логика (ТЗ v3.0, ч. XX). Чистые JVM-модули: common, intelligence, quality, testing.
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:storage")
include(":core:security")
include(":core:camera")
include(":core:imageprocessing")
include(":core:hybrid")
include(":core:ocr")
include(":core:pdf")
include(":core:intelligence")
include(":core:batch")
include(":core:performance")
include(":core:quality")
include(":core:testing")

// feature — экраны и сценарии
include(":feature:home")
include(":feature:scanner")
include(":feature:editor")
include(":feature:documents")
include(":feature:search")
include(":feature:export")
include(":feature:settings")
include(":feature:batch")
include(":feature:diagnostics")
