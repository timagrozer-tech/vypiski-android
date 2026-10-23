plugins {
    alias(libs.plugins.scanner291.android.library)
    alias(libs.plugins.scanner291.hilt)
}

android {
    namespace = "ru.scanner291.core.storage"
    testOptions {
        unitTests { isIncludeAndroidResources = true }
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:database"))
    implementation(libs.kotlinx.coroutines.android)
    api(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
