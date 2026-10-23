package ru.scanner291.designsystem

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Снимки галереи компонентов в обеих темах. В CI: `recordRoborazziDebug` → артефакт design-screenshots.
 * Без флагов Roborazzi тест только проверяет, что галерея рисуется.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xhdpi")
class GalleryScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun galleryDark() {
        rule.setContent { ScannerTheme(ThemeMode.DARK) { ComponentGallery() } }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/gallery-dark.png")
    }

    @Test fun galleryLight() {
        rule.setContent { ScannerTheme(ThemeMode.LIGHT) { ComponentGallery() } }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/gallery-light.png")
    }
}
