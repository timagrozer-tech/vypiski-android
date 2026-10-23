package ru.scanner291.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.scanner291.core.common.geometry.Pt
import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.database.CornersSource
import ru.scanner291.core.database.DocumentSource
import ru.scanner291.core.storage.DocumentPaths
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.recipeFor
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.ThemeMode
import ru.scanner291.feature.batch.BatchSaveRoute
import ru.scanner291.feature.documents.DocumentsRoute
import ru.scanner291.feature.documents.ViewerRoute
import ru.scanner291.feature.editor.EditorRoute
import ru.scanner291.feature.editor.FioRoute
import ru.scanner291.feature.editor.OcrRoute
import ru.scanner291.feature.export.PdfToolsRoute
import ru.scanner291.feature.home.HomeRoute
import ru.scanner291.feature.scanner.CameraRoute
import ru.scanner291.feature.scanner.ProcessingRoute
import ru.scanner291.feature.search.SearchRoute
import ru.scanner291.feature.settings.SettingsRoute
import ru.scanner291.feature.settings.WatermarkRoute
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Снимки экранов приложения (Roborazzi, без эмулятора) — чтобы видеть, как выглядит каждый экран
 * после каждого этапа. В CI: `:app:recordRoborazziDebug --tests '*AppScreenshotTest'`, снимки
 * публикуются в ветку `scanner291-screenshots`. Без флага записи тест только проверяет, что экраны рисуются.
 *
 * Данные — настоящие: документы создаются через репозиторий из сгенерированных «фотографий» листа.
 * Камера в Robolectric недоступна, поэтому её экран снимается в состоянии без камеры.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppScreenshotTest {
    @javax.inject.Inject lateinit var repo: DocumentRepository
    @javax.inject.Inject lateinit var paths: DocumentPaths
    @javax.inject.Inject lateinit var loader: ru.scanner291.core.storage.PageImageLoader

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var nav: NavHostController
    private var theme by mutableStateOf(ThemeMode.DARK)

    @Before fun setUp() {
        hilt.inject()
        rule.setContent {
            nav = rememberNavController()
            ScannerTheme(theme) { ScannerApp(nav) }
        }
    }

    private fun has(t: String) = rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun shot(name: String, waitFor: String? = null) {
        if (waitFor != null) runCatching { rule.waitUntil(5_000) { has(waitFor) } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun go(route: Any) { rule.runOnUiThread { nav.navigate(route) }; rule.waitForIdle() }
    private fun back() { rule.runOnUiThread { nav.popBackStack() }; rule.waitForIdle() }

    /** «Фото» листа на столе: лист в лёгкой перспективе, текст, таблица, печать, тень сбоку. */
    private fun photo(title: String, lines: List<String>): Pair<File, Quad> {
        val w = 1200; val h = 1600
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.paint = java.awt.GradientPaint(0f, 0f, Color(96, 74, 58), w.toFloat(), h.toFloat(), Color(64, 48, 38))
        g.fillRect(0, 0, w, h)
        val q = Quad(Pt(150.0, 170.0), Pt(1060.0, 120.0), Pt(1110.0, 1450.0), Pt(110.0, 1490.0))
        val poly = java.awt.Polygon(q.points.map { it.x.toInt() }.toIntArray(), q.points.map { it.y.toInt() }.toIntArray(), 4)
        g.paint = java.awt.GradientPaint(0f, 0f, Color(246, 244, 238), w.toFloat(), 0f, Color(196, 194, 188))
        g.fill(poly)
        // Текст — в системе координат листа (лёгкий наклон), без настоящей перспективы: для снимка экрана достаточно.
        val old = g.transform
        g.transform = AffineTransform().apply { translate(160.0, 175.0); rotate(-0.045) }
        g.color = Color(30, 30, 34)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 46); g.drawString(title, 230, 110)
        g.font = Font(Font.SERIF, Font.PLAIN, 30)
        var y = 200
        for (l in lines) { g.drawString(l, 60, y); y += 50 }
        g.stroke = BasicStroke(2f); g.drawRect(60, y + 20, 780, 120); g.drawLine(450, y + 20, 450, y + 140)
        g.color = Color(40, 70, 170); g.stroke = BasicStroke(5f); g.drawOval(560, y + 170, 200, 200)
        g.transform = old
        g.dispose()
        paths.work.mkdirs()
        val f = File(paths.work, "${title.hashCode()}.jpg")
        check(ImageIO.write(img, "jpg", f))
        val n = Quad(q.tl.let { Pt(it.x / w, it.y / h) }, q.tr.let { Pt(it.x / w, it.y / h) },
            q.br.let { Pt(it.x / w, it.y / h) }, q.bl.let { Pt(it.x / w, it.y / h) })
        return f to n
    }

    private fun seed(): String = runBlocking {
        val text = listOf(
            "Выдана Иванову Ивану Ивановичу в том, что он",
            "действительно проходит военную службу по контракту",
            "в войсковой части 65384 с 01.03.2024 по настоящее",
            "время. Справка выдана для предъявления по месту",
            "требования.", "",
            "Командир войсковой части", "полковник                         А. Петров",
        )
        val other = listOf(
            listOf("Рапорт об отпуске") to ScanMode.REPORT,
            listOf("Приказ № 15 по личному составу") to ScanMode.ORDER,
        )
        for ((t, mode) in other) {
            val d = repo.createDocument(mode, DocumentSource.CAMERA, t[0])
            repo.addCapturedPage(d, photo(t[0], text).first)
        }
        val id = repo.createDocument(ScanMode.CERTIFICATE, DocumentSource.CAMERA, "Справка Иванов И. И.")
        val (f1, q1) = photo("СПРАВКА", text)
        repo.addCapturedPage(id, f1)
        repo.addCapturedPage(id, photo("СПРАВКА (оборот)", text.take(4)).first)
        val page = repo.document(id)!!.orderedPages.first()
        repo.setCorners(page.id, q1, CornersSource.AUTO)
        // Текстовый слой (как после OCR) — для снимков «Распознанный текст» и «Поиск»
        val key = ru.scanner291.core.common.render.RecipeCodec.cacheKey(repo.page(page.id)!!.recipeFor(ScanMode.CERTIFICATE))
        repo.saveText(ru.scanner291.core.database.PageTextEntity(page.id, id, "СПРАВКА\n" + text.joinToString("\n"), "", "FAST", key, false, 0, 1840))
        id
    }

    /**
     * Диагностика для снимков: открывается ли JPEG в этой среде (Robolectric) и что видит загрузчик.
     * Пишется рядом со снимками (diag.txt), чтобы отличить ограничение среды от ошибки приложения.
     */
    private fun diagnostics(id: String) {
        val page = runBlocking { repo.document(id)!!.orderedPages.first() }
        val f = paths.absolute(page.originalPath)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.path, bounds)
        val full = runCatching { android.graphics.BitmapFactory.decodeFile(f.path) }
        val viaDecoder = runCatching { android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(f)) }
        val loaded = runBlocking { loader.load(page, 1600) }
        val px = loaded?.let { "%08x".format(it.getPixel(10, 10)) }
        File("build/outputs/roborazzi").mkdirs()
        File("build/outputs/roborazzi/diag.txt").writeText(
            "файл: ${f.length()} байт, существует=${f.exists()}\n" +
                "BitmapFactory границы: ${bounds.outWidth}x${bounds.outHeight} mime=${bounds.outMimeType}\n" +
                "BitmapFactory полностью: ${full.getOrNull()?.let { "${it.width}x${it.height}" }} ошибка=${full.exceptionOrNull()}\n" +
                "ImageDecoder: ${viaDecoder.getOrNull()?.let { "${it.width}x${it.height}" }} ошибка=${viaDecoder.exceptionOrNull()}\n" +
                "PageImageLoader: ${loaded?.let { "${it.width}x${it.height}, пиксель(10,10)=$px" }}\n",
        )
    }

    @Test fun screens() {
        shot("01-home-empty", "Сканировать")
        val id = seed()
        diagnostics(id)
        shot("02-home", "Справка Иванов")
        go(DocumentsRoute); shot("03-documents", "Справка Иванов")
        go(ViewerRoute(id)); shot("04-viewer", "1/2")
        rule.onAllNodesWithText("Word")[0].performClick(); shot("04b-viewer-word", "может содержать ошибки")
        go(ProcessingRoute(id)); shot("05-processing-corners", "Найти автоматически")
        rule.onAllNodesWithText("Улучшение")[0].performClick()
        shot("05b-processing-enhance", "Убрать тени")
        back(); back()
        go(CameraRoute); shot("06-camera", "Назвать файл по ФИО")
        back()
        go(SearchRoute); shot("07-search")
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("иванов")
        rule.mainClock.advanceTimeBy(400)
        shot("07b-search-results", "В тексте документов")
        go(SettingsRoute); shot("08-settings", "Водяной знак")
        go(WatermarkRoute); shot("09-watermark", "В/ч 65384")
        back(); go(ru.scanner291.feature.diagnostics.HealthRoute); shot("09b-health", "Проверить снова")
        back()
        go(PdfToolsRoute); shot("10-pdf-tools", "Объединить")
        back()
        go(BatchSaveRoute); shot("11-batch")
        back()
        go(EditorRoute(id)); shot("12-editor"); back()
        go(OcrRoute(id)); shot("13-ocr"); back()
        go(FioRoute(id)); shot("14-fio"); back()
        go(ru.scanner291.feature.export.ExportRoute(id)); shot("14b-export", "Формат"); back()
        go(HomeRoute)
        theme = ThemeMode.LIGHT
        shot("15-home-light", "Справка Иванов")
        go(ProcessingRoute(id)); shot("16-processing-light", "Найти автоматически")
    }
}
