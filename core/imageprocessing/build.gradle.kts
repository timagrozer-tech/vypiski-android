plugins {
    alias(libs.plugins.scanner291.android.library)
}

android {
    namespace = "ru.scanner291.core.imageprocessing"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.opencv)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
}

android {
    testOptions { unitTests { isIncludeAndroidResources = true } }
}
