plugins {
    alias(libs.plugins.scanner291.android.library)
}

android {
    namespace = "ru.scanner291.core.pdf"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.pdfbox.android)
    implementation(libs.kotlinx.coroutines.android)
}
