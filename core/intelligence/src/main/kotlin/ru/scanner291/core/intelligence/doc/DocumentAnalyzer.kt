package ru.scanner291.core.intelligence.doc

import ru.scanner291.core.intelligence.fio.FioExtractor
import ru.scanner291.core.intelligence.fio.FioStatus
import ru.scanner291.core.intelligence.fio.OcrTextNormalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Граница слова с учётом кириллицы. `\\b` в java.util.regex (JDK 19+) считает буквами только латиницу,
 * поэтому «РАПОРТ\\b» не находился; ICU на Android ведёт себя иначе — здесь одинаково везде.
 */
private const val WB = "(?:(?<![\\p{L}\\p{N}])(?=[\\p{L}\\p{N}])|(?<=[\\p{L}\\p{N}])(?![\\p{L}\\p{N}]))"

/** Тип документа. [nameWord] — слово для имени файла. */
enum class DocType(val title: String, val nameWord: String) {
    ORDER("Приказ", "Приказ"),
    ORDER_EXTRACT("Выписка из приказа", "Выписка из приказа"),
    REPORT("Рапорт", "Рапорт"),
    CERTIFICATE("Справка", "Справка"),
    CONTRACT("Договор", "Договор"),
    STATEMENT("Заявление", "Заявление"),
    MEMO("Служебная записка", "Служебная записка"),
    PROTOCOL("Протокол", "Протокол"),
    ACT("Акт", "Акт"),
    CHARACTERISTIC("Характеристика", "Характеристика"),
    ORDER_DIRECTIVE("Распоряжение", "Распоряжение"),
    POWER_OF_ATTORNEY("Доверенность", "Доверенность"),
    UNKNOWN("Документ", "Документ"),
}

/**
 * Найденное значение — всегда только предложение: на экране показывается с источником и подтверждается
 * пользователем (ТЗ: «всё требует подтверждения»). [source] — строка документа, откуда взято значение.
 */
data class Suggestion<T>(val value: T, val confidence: Double, val source: String) {
    val needsConfirmation: Boolean get() = true
}

data class DocumentFacts(
    val type: Suggestion<DocType>?,
    val date: Suggestion<LocalDate>?,
    val number: Suggestion<String>?,
    val title: Suggestion<String>?,
    val organization: Suggestion<String>?,
    /** Поля шаблона (приказ, рапорт…): «Адресат», «Просьба», «Параграфы»… */
    val templateFields: Map<String, Suggestion<String>>,
    /** ФИО главного лица (из FioExtractor), если найдено уверенно. */
    val person: Suggestion<String>?,
) {
    /** Предлагаемое имя: «Рапорт Волков И.И. 18.09.2024», «Приказ № 215 от 03.04.2025». */
    fun suggestedName(): String? {
        val t = type?.value?.takeIf { it != DocType.UNKNOWN } ?: return null
        val d = date?.value?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        return when {
            person != null && t in setOf(DocType.REPORT, DocType.CERTIFICATE, DocType.STATEMENT, DocType.CHARACTERISTIC, DocType.ORDER_EXTRACT) ->
                listOfNotNull(t.nameWord, person.value, d).joinToString(" ")
            number != null -> listOfNotNull(t.nameWord, "№ ${number.value}", d?.let { "от $it" }).joinToString(" ")
            else -> listOfNotNull(t.nameWord, d).joinToString(" ")
        }
    }
}

/**
 * Разбор служебного документа (этап 11): тип, дата, номер, заголовок, организация, поля шаблона приказа и
 * рапорта, ФИО. Только правила и словари — без сети и без «додумывания»: что не найдено, то null.
 */
class DocumentAnalyzer(private val fio: FioExtractor = FioExtractor()) {

    fun analyze(raw: String, today: LocalDate = LocalDate.now()): DocumentFacts {
        val text = OcrTextNormalizer.normalize(raw)
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val type = detectType(lines)
        val number = detectNumber(lines)
        val date = detectDate(lines, today)
        val org = detectOrganization(lines)
        val title = detectTitle(lines, type?.value)
        val person = fio.extract(raw).let { r ->
            if (r.status == FioStatus.FOUND) r.primary?.let { Suggestion(it.fio.short(), r.confidence, it.mentions.first().originalText) } else null
        }
        val tpl = when (type?.value) {
            DocType.ORDER, DocType.ORDER_EXTRACT, DocType.ORDER_DIRECTIVE -> orderFields(lines)
            DocType.REPORT -> reportFields(lines)
            else -> emptyMap()
        }
        return DocumentFacts(type, date, number, title, org, tpl, person)
    }

    // ── Тип ──
    private val typeRules: List<Pair<DocType, Regex>> = listOf(
        DocType.ORDER_EXTRACT to Regex("^в\\s*ы\\s*п\\s*и\\s*с\\s*к\\s*а\\s+из\\s+приказа", RegexOption.IGNORE_CASE),
        DocType.ORDER to Regex("^п\\s*р\\s*и\\s*к\\s*а\\s*з${WB}", RegexOption.IGNORE_CASE),
        DocType.ORDER_DIRECTIVE to Regex("^р\\s*а\\s*с\\s*п\\s*о\\s*р\\s*я\\s*ж\\s*е\\s*н\\s*и\\s*е${WB}", RegexOption.IGNORE_CASE),
        DocType.REPORT to Regex("^р\\s*а\\s*п\\s*о\\s*р\\s*т${WB}", RegexOption.IGNORE_CASE),
        DocType.CERTIFICATE to Regex("^с\\s*п\\s*р\\s*а\\s*в\\s*к\\s*а${WB}", RegexOption.IGNORE_CASE),
        DocType.CONTRACT to Regex("^д\\s*о\\s*г\\s*о\\s*в\\s*о\\s*р${WB}", RegexOption.IGNORE_CASE),
        DocType.STATEMENT to Regex("^з\\s*а\\s*я\\s*в\\s*л\\s*е\\s*н\\s*и\\s*е${WB}", RegexOption.IGNORE_CASE),
        DocType.MEMO to Regex("^служебная\\s+записка${WB}", RegexOption.IGNORE_CASE),
        DocType.PROTOCOL to Regex("^п\\s*р\\s*о\\s*т\\s*о\\s*к\\s*о\\s*л${WB}", RegexOption.IGNORE_CASE),
        DocType.ACT to Regex("^а\\s*к\\s*т${WB}", RegexOption.IGNORE_CASE),
        DocType.CHARACTERISTIC to Regex("^х\\s*а\\s*р\\s*а\\s*к\\s*т\\s*е\\s*р\\s*и\\s*с\\s*т\\s*и\\s*к\\s*а${WB}", RegexOption.IGNORE_CASE),
        DocType.POWER_OF_ATTORNEY to Regex("^д\\s*о\\s*в\\s*е\\s*р\\s*е\\s*н\\s*н\\s*о\\s*с\\s*т\\s*ь${WB}", RegexOption.IGNORE_CASE),
    )

    /** Заголовок-слово в первых 12 строках (уверенно, если строка короткая/прописная); иначе — по характерным фразам. */
    private fun detectType(lines: List<String>): Suggestion<DocType>? {
        for ((i, l) in lines.take(12).withIndex()) for ((t, re) in typeRules) if (re.containsMatchIn(l)) {
            val caps = l.count { it.isUpperCase() } >= l.count { it.isLetter() } * 0.7
            val conf = (if (caps || l.length < 40) 0.95 else 0.8) - i * 0.01
            return Suggestion(t, conf, l)
        }
        val all = lines.joinToString("\n").lowercase()
        return when {
            Regex("${WB}прошу${WB}").containsMatchIn(all) && Regex("командир|начальник").containsMatchIn(all) ->
                Suggestion(DocType.REPORT, 0.6, lines.first { it.lowercase().contains("прошу") })
            Regex("${WB}приказываю${WB}").containsMatchIn(all) -> Suggestion(DocType.ORDER, 0.7, lines.first { it.lowercase().contains("приказываю") })
            Regex("выдана${WB}.*в\\s+том,?\\s+что").containsMatchIn(all) -> Suggestion(DocType.CERTIFICATE, 0.6, lines.first { it.lowercase().contains("выдан") })
            else -> null
        }
    }

    // ── Номер ──
    private val numberRe = Regex("(?:№|N[oо°]?\\.?|Ns|Мо)\\s*([0-9]{1,6}(?:\\s*[-/]\\s*[0-9A-Za-zА-Яа-я]{1,6}){0,3})")

    private fun detectNumber(lines: List<String>): Suggestion<String>? {
        for (l in lines.take(15)) {
            val m = numberRe.find(l) ?: continue
            val v = m.groupValues[1].replace(Regex("\\s+"), "")
            // «№» рядом с «от»/датой или в шапке — номер документа; «в/ч 65384» номером не считается
            val near = l.contains(Regex("${WB}от${WB}", RegexOption.IGNORE_CASE)) || lines.indexOf(l) < 6
            return Suggestion(v, if (near) 0.9 else 0.7, l)
        }
        return null
    }

    // ── Дата ──
    private val months = listOf("январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр")
    private val numericDate = Regex("(?<!\\d)(\\d{1,2})[.](\\d{1,2})[.](\\d{4}|\\d{2})(?!\\d)")
    private val wordDate = Regex("«?\\s*(\\d{1,2})\\s*»?\\s+([а-яё]+)\\s+(\\d{4})", RegexOption.IGNORE_CASE)

    fun parseDates(line: String, today: LocalDate): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        for (m in numericDate.findAll(line)) {
            var y = m.groupValues[3].toInt(); if (y < 100) y += 2000
            runCatching { LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()?.let(out::add)
        }
        for (m in wordDate.findAll(line)) {
            val w = m.groupValues[2].lowercase()
            val mi = months.indexOfFirst { w.startsWith(it) && (it != "ма" || w == "мая" || w == "май") }
            if (mi < 0) continue
            runCatching { LocalDate.of(m.groupValues[3].toInt(), mi + 1, m.groupValues[1].toInt()) }.getOrNull()?.let(out::add)
        }
        return out.filter { it.year in 1990..(today.year + 1) }
    }

    /** Дата документа: «от <дата>» рядом с номером; иначе первая дата в шапке; иначе — в подписи (последние строки). */
    private fun detectDate(lines: List<String>, today: LocalDate): Suggestion<LocalDate>? {
        for (l in lines.take(15)) if (Regex("${WB}от${WB}", RegexOption.IGNORE_CASE).containsMatchIn(l) && numberRe.containsMatchIn(l))
            parseDates(l.substringAfter("от"), today).firstOrNull()?.let { return Suggestion(it, 0.92, l) }
        for (l in lines.take(8)) parseDates(l, today).firstOrNull()?.let { return Suggestion(it, 0.75, l) }
        for (l in lines.takeLast(5).reversed()) parseDates(l, today).firstOrNull()?.let { return Suggestion(it, 0.65, l) }
        return null
    }

    // ── Организация ──
    private val orgRes = listOf(
        Regex("(?:войсков(?:ая|ой|ую)\\s+част(?:ь|и|ью)|в/ч)\\s*(\\d{4,6})", RegexOption.IGNORE_CASE) to { m: MatchResult -> "Войсковая часть ${m.groupValues[1]}" },
        Regex("${WB}(ООО|АО|ПАО|ЗАО|ФГКУ|ФГБУ|ГБУЗ|МБОУ|ИП)\\s*[«\"]([^»\"]{2,60})[»\"]") to { m: MatchResult -> "${m.groupValues[1]} «${m.groupValues[2].trim()}»" },
        Regex("(Министерств[оа]\\s+[А-ЯЁа-яё ]{4,60}?(?:Российской Федерации|РФ))") to { m: MatchResult -> m.groupValues[1].replace("Министерства", "Министерство") },
    )

    private fun detectOrganization(lines: List<String>): Suggestion<String>? {
        for ((i, l) in lines.withIndex()) for ((re, f) in orgRes) re.find(l)?.let { return Suggestion(f(it), if (i < 10) 0.85 else 0.7, l) }
        return null
    }

    // ── Заголовок ──
    /** «О предоставлении отпуска» / «по строевой части» после типа; иначе первая содержательная строка. */
    private fun detectTitle(lines: List<String>, type: DocType?): Suggestion<String>? {
        for (l in lines.take(15)) {
            val m = Regex("^(о|об|по)\\s+[а-яё].{5,90}$", RegexOption.IGNORE_CASE).find(l) ?: continue
            if (!l.endsWith(",")) return Suggestion(l.trimEnd('.').replaceFirstChar { it.uppercase() }, 0.8, l)
        }
        val first = lines.take(6).firstOrNull { it.length in 4..90 && it.any(Char::isLetter) && (type == null || !it.lowercase().startsWith(type.title.lowercase())) }
        return first?.let { Suggestion(it, 0.4, it) }
    }

    // ── Шаблоны ──
    private fun orderFields(lines: List<String>): Map<String, Suggestion<String>> {
        val out = LinkedHashMap<String, Suggestion<String>>()
        lines.firstOrNull { it.lowercase().matches(Regex("по\\s+(строевой|личному составу|основной деятельности|боевой подготовке)${WB}.*")) }
            ?.let { out["Вид приказа"] = Suggestion(it.trimEnd('.', ','), 0.85, it) }
        val paras = lines.flatMap { l -> Regex("§\\s*(\\d+)").findAll(l).map { it.groupValues[1] }.toList() }.distinct()
        if (paras.isNotEmpty()) out["Параграфы"] = Suggestion(paras.joinToString(", ") { "§ $it" }, 0.8, lines.first { it.contains("§") })
        lines.firstOrNull { it.lowercase().startsWith("приказываю") }?.let { out["Распорядительная часть"] = Suggestion("Приказываю", 0.9, it) }
        signatory(lines)?.let { out["Подписал"] = it }
        return out
    }

    private fun reportFields(lines: List<String>): Map<String, Suggestion<String>> {
        val out = LinkedHashMap<String, Suggestion<String>>()
        lines.take(8).firstOrNull { Regex("^(командиру|начальнику|заместителю|врио)${WB}", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            ?.let { out["Адресат"] = Suggestion(it.trimEnd(','), 0.85, it) }
        val i = lines.indexOfFirst { Regex("^прошу${WB}", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        if (i >= 0) {
            // Просьба — от «Прошу» до первой строки с точкой в конце (не больше 4 строк)
            val part = ArrayList<String>()
            for (l in lines.drop(i).take(4)) { part += l; if (l.endsWith(".")) break }
            out["Просьба"] = Suggestion(part.joinToString(" ").trimEnd('.'), 0.85, lines[i])
        }
        Regex("отпуск\\w*.{0,60}?(\\d{1,3})\\s+(?:сут|кален|дн)", RegexOption.IGNORE_CASE).find(lines.joinToString(" "))
            ?.let { out["Срок, суток"] = Suggestion(it.groupValues[1], 0.8, it.value) }
        signatory(lines)?.let { out["Подписал"] = it }
        return out
    }

    /** Подпись: строка с воинским званием/должностью и «И. Фамилия» или «Фамилия И.И.» в последних 6 строках. */
    private fun signatory(lines: List<String>): Suggestion<String>? {
        val re = Regex("([А-ЯЁ]\\.\\s?(?:[А-ЯЁ]\\.\\s?)?[А-ЯЁ][а-яё]+(?:-[А-ЯЁ][а-яё]+)?|[А-ЯЁ][а-яё]+\\s+[А-ЯЁ]\\.\\s?[А-ЯЁ]\\.)\\s*$")
        for (l in lines.takeLast(6).reversed()) re.find(l)?.let { return Suggestion(it.groupValues[1].replace(Regex("\\.\\s"), "."), 0.75, l) }
        return null
    }
}
