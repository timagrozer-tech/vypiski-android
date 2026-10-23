import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ru.scanner291.core.imaging"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.opencv)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
