plugins {
    alias(libs.plugins.scanner291.android.feature)
}

android {
    namespace = "ru.scanner291.feature.settings"
}

dependencies {
    implementation(project(":core:storage"))
}
