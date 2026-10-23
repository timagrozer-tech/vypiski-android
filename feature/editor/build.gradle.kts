plugins {
    alias(libs.plugins.scanner291.android.feature)
}

android {
    namespace = "ru.scanner291.feature.editor"
}
dependencies {
    implementation(project(":core:storage"))
    implementation(project(":core:ocr"))
    implementation(project(":core:intelligence"))
}
