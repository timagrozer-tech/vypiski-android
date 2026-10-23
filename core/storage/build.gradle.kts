plugins {
    alias(libs.plugins.scanner291.android.library)
    alias(libs.plugins.scanner291.hilt)
}

android {
    namespace = "ru.scanner291.core.storage"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    testImplementation(project(":core:testing"))
}
