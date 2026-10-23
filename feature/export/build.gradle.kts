plugins {
    alias(libs.plugins.scanner291.android.feature)
}

android {
    namespace = "ru.scanner291.feature.export"
}

dependencies {
    implementation(project(":core:pdf"))
    implementation(project(":core:storage"))
    implementation(project(":core:imageprocessing"))
    implementation(project(":core:ocr"))
    implementation(project(":core:intelligence"))
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.ktx)
}
