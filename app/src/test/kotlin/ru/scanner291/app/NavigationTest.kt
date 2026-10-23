package ru.scanner291.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.scanner291.designsystem.Destination
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.feature.documents.ViewerRoute
import ru.scanner291.feature.editor.EditorRoute
import ru.scanner291.feature.editor.FioRoute
import ru.scanner291.feature.editor.OcrRoute
import ru.scanner291.feature.scanner.ProcessingRoute

/**
 * Этап 4: все 13 экранов открываются, нижняя навигация работает, каждая плитка главного экрана
 * ведёт на свой экран (нет кнопок без действия), старт — сразу «Главная».
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class NavigationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var nav: NavHostController

    @Before fun setUp() {
        hilt.inject()
        rule.setContent {
            nav = rememberNavController()
            ScannerTheme { ScannerApp(nav) }
        }
    }

    private fun current(): Destination? = nav.currentBackStackEntry?.destination.topLevel()
    private fun title(t: String) = rule.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()

    @Test fun startsOnHomeWithoutSplashScreen() {
        assertEquals(Destination.Home, current())
        rule.onNodeWithText("291 Сканер").assertExists()
        rule.onNodeWithText("Сканировать").assertExists()
    }

    @Test fun bottomNavigationReachesAllRootSections() {
        rule.onNodeWithText("Документы").performClick(); assertEquals(Destination.Documents, current())
        rule.onNodeWithText("Мои документы").assertExists()
        rule.onNodeWithText("Поиск").performClick(); assertEquals(Destination.Search, current())
        rule.onNodeWithText("Настройки").performClick(); assertEquals(Destination.Settings, current())
        rule.onNodeWithText("Главная").performClick(); assertEquals(Destination.Home, current())
    }

    @Test fun everyHomeTileLeadsSomewhere() {
        rule.onNodeWithText("Сканировать").performClick()
        rule.onNodeWithContentDescription("Назад").assertExists()            // камера
        assertTrue(title("Назвать файл по ФИО"))                              // галочка на экране сканирования
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("PDF-инструменты").performClick()
        assertTrue(title("Объединить"))
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Пакетное сохранение").performClick()
        assertTrue(title("Нет документов для пакета"))
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Мои документы").performClick()
        assertEquals(Destination.Documents, current())
    }

    @Test fun documentScopedScreensOpenAndReportMissingDocument() {
        val screens = listOf(
            ProcessingRoute("x") to "Обработка", EditorRoute("x") to "Редактор страниц",
            OcrRoute("x") to "Распознанный текст", FioRoute("x") to "Определение ФИО", ViewerRoute("x") to "Просмотр",
        )
        for ((route, t) in screens) {
            rule.runOnUiThread { nav.navigate(route) }
            rule.waitForIdle()
            assertTrue("экран «$t»", title(t))
            assertTrue(title("Документ не найден"))
            rule.runOnUiThread { nav.popBackStack() }
            rule.waitForIdle()
        }
    }

    @Test fun watermarkSettingsOpenFromSettings() {
        rule.onNodeWithText("Настройки").performClick()
        rule.onNodeWithText("Водяной знак").performScrollTo().performClick()
        assertTrue(title("В/ч 65384"))
        assertTrue(title("Восстановить стандартные"))
    }
}
