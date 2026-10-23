plugins {
    alias(libs.plugins.scanner291.android.library.compose)
}

android {
    namespace = "ru.scanner291.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons)
    api(libs.androidx.compose.ui.tooling.preview)
}
