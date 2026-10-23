plugins {
    alias(libs.plugins.scanner291.android.feature)
}

android {
    namespace = "ru.scanner291.feature.diagnostics"
}
dependencies {
    implementation(project(":core:storage"))
}
