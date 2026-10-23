plugins {
    alias(libs.plugins.scanner291.android.library.compose)
}

android {
    namespace = "ru.scanner291.core.camera"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:quality"))
    implementation(project(":core:imageprocessing"))
    implementation(libs.opencv)
    api(libs.androidx.camera.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.viewfinder.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
}
