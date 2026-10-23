plugins {
    alias(libs.plugins.scanner291.android.library)
    alias(libs.plugins.scanner291.hilt)
}

android {
    namespace = "ru.scanner291.core.batch"
}

dependencies {
    api(project(":core:common"))
    implementation(project(":core:storage"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.ext.work)
    ksp(libs.hilt.ext.compiler)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
