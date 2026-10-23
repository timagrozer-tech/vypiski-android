plugins {
    alias(libs.plugins.scanner291.android.feature)
}

android {
    namespace = "ru.scanner291.feature.export"
}
dependencies {
    implementation(project(":core:pdf"))
    implementation(libs.androidx.documentfile)
}
