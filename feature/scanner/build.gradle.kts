plugins {
    alias(libs.plugins.scanner291.android.feature)
}

android {
    namespace = "ru.scanner291.feature.scanner"
}

dependencies {
    implementation(project(":core:camera"))
    implementation(project(":core:storage"))
}
