plugins {
    alias(libs.plugins.scanner291.jvm.library)
}

dependencies {
    api(project(":core:common"))
    api(libs.junit)
    api(libs.kotlin.test)
    api(libs.kotlinx.coroutines.test)
}
