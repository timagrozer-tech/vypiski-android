plugins {
    alias(libs.plugins.scanner291.android.library)
}

android {
    namespace = "ru.scanner291.core.hybrid"
}

dependencies {
    api(project(":core:common"))
    implementation(project(":core:imageprocessing"))
    implementation(libs.opencv)
    implementation(libs.kotlinx.coroutines.android)
}
