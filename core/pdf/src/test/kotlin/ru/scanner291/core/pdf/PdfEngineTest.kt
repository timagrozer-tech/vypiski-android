package ru.scanner291.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.scanner291.core.common.model.Annotation
import ru.scanner291.core.common.model.NRect
import ru.scanner291.core.common.model.OcrWord
import java.io.File

/**
 * Этап 12: число страниц и текст сохраняются при всех операциях, кириллица извлекается,
 * исходные файлы не меняются, существующий результат не перезаписывается.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfEngineTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val engine by lazy { PdfEngine(ctx) }

    private fun page(words: List<String>): PdfPageInput {
        val b = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val ws = words.mapIndexed { i, w -> OcrWord(w, NRect(0.1f, 0.1f + i * 0.08f, 0.6f, 0.16f + i * 0.08f), 0.9f) }
        return PdfPageInput(b, 25.4f, ws, jpegQuality = null)
    }

    private fun make(name: String, vararg pages: List<String>): File =
        File(tmp.root, name).also { engine.create(pages.map { page(it) }, it, "Тест") }

    @Test fun createKeepsPagesAndCyrillicText() {
        val f = make("a.pdf", listOf("СПРАВКА", "Иванов", "Иван"), listOf("Рапорт", "отпуск"))
        assertEquals(2, engine.pageCount(f))
        val t = engine.pageTexts(f)
        assertTrue(t[0], t[0].contains("Иванов") && t[0].contains("СПРАВКА"))
        assertTrue(t[1], t[1].contains("отпуск"))
    }

    @Test fun mergeSelectSplitInsertRotate() {
        val a = make("a.pdf", listOf("первая"), listOf("вторая"))
        val b = make("b.pdf", listOf("третья"))
        val aBytes = a.readBytes()
        val m = File(tmp.root, "m.pdf"); engine.merge(listOf(a, b), m)
        assertEquals(listOf("первая", "вторая", "третья"), engine.pageTexts(m))
        val r = File(tmp.root, "r.pdf"); engine.select(m, listOf(2, 0), r)
        assertEquals(listOf("третья", "первая"), engine.pageTexts(r))
        val s1 = File(tmp.root, "s1.pdf"); val s2 = File(tmp.root, "s2.pdf")
        engine.split(m, listOf(0..0, 1..2), listOf(s1, s2))
        assertEquals(1, engine.pageCount(s1)); assertEquals(listOf("вторая", "третья"), engine.pageTexts(s2))
        val i = File(tmp.root, "i.pdf"); engine.insert(a, b, 1, i)
        assertEquals(listOf("первая", "третья", "вторая"), engine.pageTexts(i))
        val rot = File(tmp.root, "rot.pdf"); engine.rotate(m, setOf(1), 90, rot)
        assertEquals(listOf(0, 90, 0), engine.rotationOf(rot))
        // текст не теряется (повёрнутая страница извлекается по буквам столбиком — сравниваем без пробелов)
        assertEquals(listOf("первая", "вторая", "третья"), engine.pageTexts(rot).map { it.replace(Regex("\\s+"), "") })
        assertTrue("исходный файл не изменён", aBytes.contentEquals(a.readBytes()))
    }

    @Test fun neverOverwritesOrReplacesInput() {
        val a = make("a.pdf", listOf("x"))
        val existing = File(tmp.root, "exists.pdf").apply { writeText("не трогать") }
        assertTrue(runCatching { engine.select(a, listOf(0), existing) }.isFailure)
        assertEquals("не трогать", existing.readText())
        assertTrue(runCatching { engine.select(a, listOf(0), a) }.isFailure)
        assertTrue(runCatching { engine.select(a, listOf(5), File(tmp.root, "z.pdf")) }.isFailure)
    }

    @Test fun annotationsAreDrawnOnCopy() {
        val src = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val out = AnnotationRenderer.render(src, listOf(Annotation.Ink("1", listOf(0f to 0.5f, 1f to 0.5f), 0.05f, Color.BLACK)), { null })
        assertEquals(Color.WHITE, src.getPixel(50, 50))
        assertTrue(Color.red(out.getPixel(50, 50)) < 50)
    }

    /** ТЗ: закрашивание — настоящее удаление: текст из закрашенной области не находится в PDF. */
    @Test fun redactedTextIsNotInPdf() {
        val words = listOf(
            OcrWord("Паспорт", NRect(0.1f, 0.10f, 0.4f, 0.15f), 0.9f),
            OcrWord("4512345678", NRect(0.45f, 0.10f, 0.9f, 0.15f), 0.9f),
            OcrWord("Справка", NRect(0.1f, 0.30f, 0.5f, 0.35f), 0.9f),
        )
        val area = listOf(NRect(0.44f, 0.08f, 0.95f, 0.17f))
        val kept = ru.scanner291.core.common.model.Redaction.filterWords(words, area)
        val bmp = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val red = AnnotationRenderer.redact(bmp, area)
        assertEquals(Color.BLACK, red.getPixel((0.7f * 200).toInt(), (0.12f * 280).toInt()))
        val f = File(tmp.root, "red.pdf"); engine.create(listOf(PdfPageInput(red, 25.4f, kept, jpegQuality = null)), f)
        val t = engine.pageTexts(f).single()
        assertTrue(t, t.contains("Паспорт") && t.contains("Справка"))
        assertTrue("номер из закрашенной области не должен находиться: $t", !t.contains("4512345678"))
    }
}
