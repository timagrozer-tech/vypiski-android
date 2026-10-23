plugins {
    alias(libs.plugins.scanner291.android.library)
    alias(libs.plugins.scanner291.android.room)
    alias(libs.plugins.scanner291.hilt)
}

android {
    namespace = "ru.scanner291.core.database"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
