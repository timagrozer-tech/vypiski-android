plugins {
    alias(libs.plugins.scanner291.android.library)
    alias(libs.plugins.scanner291.hilt)
}

android {
    namespace = "ru.scanner291.core.performance"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
