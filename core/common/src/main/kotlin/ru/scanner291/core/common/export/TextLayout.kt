package ru.scanner291.core.common.export

import ru.scanner291.core.common.model.OcrWord
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

enum class Align { LEFT, CENTER, RIGHT, JUSTIFY }

data class LayoutLine(val words: List<OcrWord>, val left: Float, val right: Float, val top: Float, val bottom: Float) {
    val text: String get() = words.joinToString(" ") { it.text }
    val height: Float get() = bottom - top
}

data class Paragraph(val lines: List<LayoutLine>, val align: Align) {
    /** Текст абзаца: по центру/справа — строки сохраняются (шапка, подписи); иначе — сплошной текст, переносы склеиваются. */
    val text: String get() = if (align == Align.CENTER || align == Align.RIGHT) lines.joinToString("\n") { it.text }
        else lines.map { it.text }.reduce { acc, s -> if (acc.endsWith("-") && acc.length > 1 && acc[acc.length - 2].isLetter()) acc.dropLast(1) + s else "$acc $s" }
}

/**
 * Восстановление вёрстки страницы по словам OCR (вкладка «Word», экспорт DOCX): строки — по вертикальному
 * перекрытию рамок, абзацы — по увеличенному интервалу и смене выравнивания, выравнивание — по положению
 * строки на странице. Слова с уверенностью < [LOW_CONFIDENCE] считаются сомнительными. Покрыто тестами.
 */
object TextLayout {
    const val LOW_CONFIDENCE = 0.6f

    fun lines(words: List<OcrWord>): List<LayoutLine> {
        val ws = words.filter { it.text.isNotBlank() }.sortedBy { (it.box.top + it.box.bottom) / 2 }
        val out = ArrayList<MutableList<OcrWord>>()
        for (w in ws) {
            val cy = (w.box.top + w.box.bottom) / 2
            val line = out.lastOrNull()
            if (line != null) {
                val t = line.minOf { it.box.top }; val b = line.maxOf { it.box.bottom }
                if (cy in t..b) { line += w; continue }
            }
            out += mutableListOf(w)
        }
        return out.map { l -> val s = l.sortedBy { it.box.left }.let(OcrFixes::fixLine)
            LayoutLine(s, s.minOf { it.box.left }, s.maxOf { it.box.right }, s.minOf { it.box.top }, s.maxOf { it.box.bottom }) }
    }

    fun align(l: LayoutLine, textLeft: Float, textRight: Float): Align {
        val center = (l.left + l.right) / 2; val mid = (textLeft + textRight) / 2; val width = textRight - textLeft
        return when {
            l.right - l.left < width * 0.85f && abs(center - mid) < width * 0.06f -> Align.CENTER
            l.left > mid && abs(l.right - textRight) < width * 0.04f -> Align.RIGHT
            l.right - l.left >= width * 0.85f -> Align.JUSTIFY
            else -> Align.LEFT
        }
    }

    fun paragraphs(words: List<OcrWord>): List<Paragraph> {
        val ls = lines(words); if (ls.isEmpty()) return emptyList()
        val left = ls.minOf { it.left }; val right = ls.maxOf { it.right }
        val medH = ls.map { it.height }.sorted()[ls.size / 2]
        val out = ArrayList<Paragraph>(); var cur = mutableListOf(ls[0]); var curAlign = align(ls[0], left, right)
        for (i in 1 until ls.size) {
            val l = ls[i]; val a = align(l, left, right)
            val gap = l.top - ls[i - 1].bottom
            // Новый абзац: большой интервал, смена выравнивания, красная строка (отступ слева) после полной строки
            val indent = l.left - left > (right - left) * 0.04f && ls[i - 1].right - ls[i - 1].left > (right - left) * 0.8f
            val sameBlock = (curAlign == Align.JUSTIFY || curAlign == Align.LEFT) && (a == Align.JUSTIFY || a == Align.LEFT)
            // Предыдущая строка закончилась раньше правого края или двоеточием — абзац закончен
            // («Полагать убывшими:» / «II. В командировку:» не склеиваются со следующим пунктом)
            val prev = ls[i - 1]
            val prevEnded = sameBlock && (prev.right < left + (right - left) * 0.8f || prev.text.trimEnd().endsWith(":"))
            if (gap > medH * 0.9f || (a != curAlign && !sameBlock) || indent || prevEnded) {
                out += Paragraph(cur, curAlign); cur = mutableListOf(l); curAlign = a
            } else {
                cur += l; if (curAlign == Align.LEFT && a == Align.JUSTIFY) curAlign = Align.JUSTIFY
            }
        }
        out += Paragraph(cur, curAlign)
        return out
    }
}

/**
 * Частые ошибки распознавания в служебных документах, исправляемые по контексту (только однозначные случаи):
 * «$7» → «§ 7» (знак параграфа), «П.»/«Ш.» в начале пункта перед словом с заглавной → «II.»/«III.»
 * (римская нумерация разделов приказа). Исправленные слова получают пониженную уверенность — подсвечиваются.
 */
object OcrFixes {
    private val PARAGRAPH = Regex("^[\\$§]\\s?(\\d+)([.,:]?)$")
    private val ROMAN = mapOf("П." to "II.", "Ш." to "III.", "ПI." to "III.", "IП." to "III.")

    fun fixLine(words: List<OcrWord>): List<OcrWord> = words.mapIndexed { i, w ->
        val t = w.text
        val m = PARAGRAPH.find(t)
        when {
            m != null && (t.startsWith("$") || t.startsWith("§")) -> w.copy(text = "§ " + m.groupValues[1] + m.groupValues[2], confidence = minOf(w.confidence, 0.59f))
            t == "$" && words.getOrNull(i + 1)?.text?.firstOrNull()?.isDigit() == true -> w.copy(text = "§", confidence = minOf(w.confidence, 0.59f))
            i == 0 && t in ROMAN && words.getOrNull(1)?.text?.firstOrNull()?.isUpperCase() == true ->
                w.copy(text = ROMAN.getValue(t), confidence = minOf(w.confidence, 0.59f))
            else -> w
        }
    }
}

/** Минимальный DOCX (Office Open XML): абзацы с выравниванием, Times New Roman 12, A4, разрыв страницы между страницами. */
object DocxWriter {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\n' || it == '\t' || it >= ' ' }

    private const val RPR = "<w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\" w:cs=\"Times New Roman\"/><w:sz w:val=\"24\"/></w:rPr>"

    fun documentXml(pages: List<List<Paragraph>>, highlightDoubtful: Boolean = false): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
        pages.forEachIndexed { pi, paras ->
            for ((i, p) in paras.withIndex()) {
                append("<w:p><w:pPr>")
                if (pi > 0 && i == 0) append("<w:pageBreakBefore/>")
                append("<w:jc w:val=\"").append(when (p.align) { Align.LEFT -> "left"; Align.CENTER -> "center"; Align.RIGHT -> "right"; Align.JUSTIFY -> "both" }).append("\"/>")
                append("</w:pPr>")
                val keepLines = p.align == Align.CENTER || p.align == Align.RIGHT
                p.lines.forEachIndexed { li, line ->
                    if (li > 0) append(if (keepLines) "<w:r>$RPR<w:br/></w:r>" else "<w:r>$RPR<w:t xml:space=\"preserve\"> </w:t></w:r>")
                    line.words.forEachIndexed { wi, w ->
                        val doubtful = highlightDoubtful && w.confidence < TextLayout.LOW_CONFIDENCE
                        append("<w:r>")
                        append(if (doubtful) RPR.replace("</w:rPr>", "<w:highlight w:val=\"yellow\"/></w:rPr>") else RPR)
                        append("<w:t xml:space=\"preserve\">").append(esc(if (wi > 0) " " + w.text else w.text)).append("</w:t></w:r>")
                    }
                }
                append("</w:p>")
            }
            if (paras.isEmpty()) append(if (pi > 0) "<w:p><w:pPr><w:pageBreakBefore/></w:pPr></w:p>" else "<w:p/>")
        }
        append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"850\" w:bottom=\"1134\" w:left=\"1701\" w:header=\"709\" w:footer=\"709\" w:gutter=\"0\"/></w:sectPr>")
        append("</w:body></w:document>")
    }

    fun write(pages: List<List<Paragraph>>, out: OutputStream, highlightDoubtful: Boolean = false) {
        ZipOutputStream(out).use { z ->
            fun put(name: String, s: String) { z.putNextEntry(ZipEntry(name)); z.write(s.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>")
            put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
            put("word/document.xml", documentXml(pages, highlightDoubtful))
        }
    }

    /** Абзацы из простого текста (страница исправлена вручную — рамок слов нет): пустая строка — новый абзац. */
    fun plainParagraphs(text: String): List<Paragraph> = text.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.map { block ->
        Paragraph(block.lines().filter { it.isNotBlank() }.map { l ->
            LayoutLine(l.trim().split(Regex("\\s+")).map { OcrWord(it, ru.scanner291.core.common.model.NRect(0f, 0f, 0f, 0f), 1f) }, 0f, 0f, 0f, 0f)
        }, Align.LEFT)
    }
}
