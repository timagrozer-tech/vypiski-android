plugins {
    alias(libs.plugins.scanner291.android.library)
}

android {
    namespace = "ru.scanner291.core.security"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
}
