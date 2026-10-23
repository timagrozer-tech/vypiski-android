plugins {
    alias(libs.plugins.scanner291.android.library)
    alias(libs.plugins.scanner291.android.room)
}

android {
    namespace = "ru.scanner291.core.data"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
