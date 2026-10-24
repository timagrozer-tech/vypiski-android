package ru.scanner291.core.common.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.scanner291.core.common.model.NRect
import ru.scanner291.core.common.model.OcrWord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class TextLayoutTest {
    /** Строка слов: x0..x1 по ширине, y — верх строки (высота 0.015). */
    private fun line(y: Float, x0: Float, x1: Float, vararg ws: String, conf: Float = 0.9f): List<OcrWord> {
        val step = (x1 - x0) / ws.size
        return ws.mapIndexed { i, w -> OcrWord(w, NRect(x0 + i * step, y, x0 + (i + 1) * step - 0.01f, y + 0.015f), conf) }
    }

    private val page = line(0.05f, 0.35f, 0.65f, "ВЫПИСКА", "ИЗ", "ПРИКАЗА") +
        line(0.07f, 0.30f, 0.70f, "командира", "войсковой", "части") +
        line(0.12f, 0.15f, 0.85f, "Лейтенанта", "Иванова", "назначенного", "при-") +
        line(0.14f, 0.10f, 0.85f, "казом", "командира", "дивизии", "зачислить") +
        line(0.16f, 0.10f, 0.40f, "в", "списки.") +
        line(0.22f, 0.70f, 0.85f, "А.Петров", conf = 0.4f)

    @Test fun paragraphsAlignmentAndHyphens() {
        val ps = TextLayout.paragraphs(page)
        assertEquals(listOf(Align.CENTER, Align.JUSTIFY, Align.RIGHT), ps.map { it.align })
        assertEquals("ВЫПИСКА ИЗ ПРИКАЗА\nкомандира войсковой части", ps[0].text)
        assertEquals("Лейтенанта Иванова назначенного приказом командира дивизии зачислить в списки.", ps[1].text)
        assertEquals("А.Петров", ps[2].text)
    }

    /** Скриншот пользователя 2026-10-24: «Полагать убывшими: П. В командировку: 2. …» склеивались в один абзац. */
    @Test fun shortLinesEndParagraphsAndOcrFixes() {
        val ws = line(0.05f, 0.45f, 0.55f, "$7") +
            line(0.08f, 0.15f, 0.40f, "Полагать", "убывшими:") +
            line(0.10f, 0.15f, 0.40f, "П.", "В", "командировку:") +
            line(0.12f, 0.15f, 0.90f, "2.", "Нижепоименованных", "военнослужащих", "в", "г.", "Токмак") +
            line(0.14f, 0.10f, 0.90f, "области", "для", "выполнения", "специальных", "задач") +
            line(0.16f, 0.10f, 0.50f, "до", "особого", "распоряжения:")
        val ps = TextLayout.paragraphs(ws)
        assertEquals(listOf("§ 7", "Полагать убывшими:", "II. В командировку:",
            "2. Нижепоименованных военнослужащих в г. Токмак области для выполнения специальных задач до особого распоряжения:"), ps.map { it.text })
        assertEquals(Align.LEFT, ps[1].align)
        assertTrue("исправленное слово подсвечивается", ps[0].lines[0].words[0].confidence < TextLayout.LOW_CONFIDENCE)
        // «S7» и обычное «П.» в середине строки не трогаются
        assertEquals(listOf("S7", "и", "П."), OcrFixes.fixLine(line(0.3f, 0.1f, 0.5f, "S7", "и", "П.")).map { it.text })
    }

    @Test fun docxIsValidZipWithWellFormedXml() {
        val pages = listOf(TextLayout.paragraphs(page), DocxWriter.plainParagraphs("Вторая <страница> & \"кавычки\"\n\nНовый абзац"))
        val bytes = ByteArrayOutputStream().also { DocxWriter.write(pages, it, highlightDoubtful = true) }.toByteArray()
        val entries = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes().toString(Charsets.UTF_8) } }
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml"), entries.keys)
        val doc = entries.getValue("word/document.xml")
        // Разбирается XML-парсером — значит, хорошо сформирован
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(doc.toByteArray()))
        assertTrue(doc.contains("<w:jc w:val=\"center\"/>") && doc.contains("<w:jc w:val=\"both\"/>") && doc.contains("<w:jc w:val=\"right\"/>"))
        assertEquals(1, Regex("<w:pageBreakBefore/>").findAll(doc).count())
        assertTrue(doc.contains("&lt;страница&gt;") && doc.contains(" &amp;"))
        assertTrue("сомнительное слово подсвечено", doc.contains("<w:highlight w:val=\"yellow\"/></w:rPr><w:t xml:space=\"preserve\">А.Петров"))
    }
}
