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
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.runBlocking
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.database.DocumentSource
import ru.scanner291.core.storage.DocumentPaths
import ru.scanner291.core.storage.DocumentRepository
import java.io.File
import javax.inject.Inject
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {
    @Inject lateinit var repo: DocumentRepository
    @Inject lateinit var paths: DocumentPaths

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
        rule.onNodeWithText("Пакет").performClick()
        assertTrue(title("Нет документов для пакета"))
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Документы").performClick()
        assertEquals(Destination.Documents, current())
        rule.onNodeWithText("Главная").performClick()
        assertTrue(title("Импорт"))                                   // плитка импорта — системный выбор файлов
    }

    @Test fun documentScopedScreensOpenAndReportMissingDocument() {
        val screens = listOf(
            ProcessingRoute("x") to "Обработка", EditorRoute("x") to "Редактор страниц",
            OcrRoute("x") to "Распознанный текст", FioRoute("x") to "Определение ФИО", ViewerRoute("x") to "Образ",
        )
        for ((route, t) in screens) {
            rule.runOnUiThread { nav.navigate(route) }
            // Экраны загружают документ асинхронно (Room) — ждём, а не проверяем мгновенно.
            rule.waitUntil(5_000) { title(t) && title("Документ не найден") }
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

    @Test fun scannedDocumentAppearsInListAndOpens() {
        val id = runBlocking {
            val doc = repo.createDocument(ScanMode.ORDER, DocumentSource.CAMERA, "Приказ 15")
            paths.work.mkdirs()
            val f = File(paths.work, "t.jpg")
            val img = java.awt.image.BufferedImage(120, 160, java.awt.image.BufferedImage.TYPE_INT_RGB)
            check(javax.imageio.ImageIO.write(img, "jpg", f))
            repo.addCapturedPage(doc, f)
            doc
        }
        rule.waitUntil(5_000) { title("Приказ 15") }        // «Недавние документы» на главной
        rule.onNodeWithText("Документы").performClick()
        rule.waitUntil(5_000) { title("Приказ 15") }
        rule.onNodeWithText("Приказ 15").performClick()
        rule.waitUntil(5_000) { title("1/1") }          // просмотр «Образ / Word», счётчик страниц
        assertTrue(nav.currentBackStackEntry?.destination?.route?.contains("ViewerRoute") == true)
        // Просмотр → обработка (правка углов)
        rule.onNodeWithText("Редактировать").performClick()
        rule.waitUntil(5_000) { title("Найти углы") }
        assertTrue(title("Весь лист"))
        assertTrue(id.isNotEmpty())
    }
}
