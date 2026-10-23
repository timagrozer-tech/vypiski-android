package ru.scanner291.core.intelligence.fio

/** Грамматический падеж. */
enum class GCase { NOM, GEN, DAT, ACC, INS, PREP }

enum class Gender { MALE, FEMALE, UNKNOWN }

/** Одна грамматическая интерпретация словоформы. */
data class Interpretation(
    /** Начальная форма (именительный падеж), в регистре «Заглавная». */
    val nominative: String,
    val gender: Gender,
    val cases: Set<GCase>,
    /** Надёжно ли восстановление именительного падежа по этой модели склонения. */
    val reliable: Boolean = true,
)

internal fun String.foldYo(): String = replace('ё', 'е').replace('Ё', 'Е')

internal fun String.titleCase(): String =
    split('-').joinToString("-") { part ->
        if (part.isEmpty()) part else part.substring(0, 1).uppercase() + part.substring(1).lowercase()
    }

private val VELAR_SIBILANT = setOf('г', 'к', 'х', 'ж', 'ш', 'ч', 'щ')
private val SIBILANT_C = setOf('ж', 'ш', 'ч', 'щ', 'ц')

/**
 * Склонение личных имён. Генерирует все падежные формы из именительного,
 * затем строится обратный индекс «форма → интерпретации».
 */
object NameMorphology {

    fun declineMale(n: String): Map<GCase, String> {
        val low = n.lowercase()
        MALE_IRREGULAR[low]?.let { forms ->
            return mapOf(
                GCase.NOM to n, GCase.GEN to forms[0], GCase.DAT to forms[1],
                GCase.ACC to forms[0], GCase.INS to forms[2], GCase.PREP to forms[3],
            ).mapValues { it.value.titleCase() }
        }
        val out = linkedMapOf(GCase.NOM to n)
        when {
            low.endsWith("ий") -> {
                val s = n.dropLast(1)
                out[GCase.GEN] = s + "я"; out[GCase.DAT] = s + "ю"; out[GCase.ACC] = s + "я"
                out[GCase.INS] = s + "ем"; out[GCase.PREP] = s + "и"
            }
            low.endsWith("й") || low.endsWith("ь") -> {
                val s = n.dropLast(1)
                out[GCase.GEN] = s + "я"; out[GCase.DAT] = s + "ю"; out[GCase.ACC] = s + "я"
                out[GCase.INS] = s + "ем"; out[GCase.PREP] = s + "е"
            }
            low.endsWith("я") -> {
                val s = n.dropLast(1)
                out[GCase.GEN] = s + "и"; out[GCase.DAT] = s + "е"; out[GCase.ACC] = s + "ю"
                out[GCase.INS] = s + "ей"; out[GCase.PREP] = s + "е"
            }
            low.endsWith("а") -> {
                val s = n.dropLast(1)
                val gen = if (s.last().lowercaseChar() in VELAR_SIBILANT) "и" else "ы"
                out[GCase.GEN] = s + gen; out[GCase.DAT] = s + "е"; out[GCase.ACC] = s + "у"
                out[GCase.INS] = s + "ой"; out[GCase.PREP] = s + "е"
            }
            else -> {
                out[GCase.GEN] = n + "а"; out[GCase.DAT] = n + "у"; out[GCase.ACC] = n + "а"
                out[GCase.INS] = n + if (low.last() in SIBILANT_C) "ем" else "ом"; out[GCase.PREP] = n + "е"
            }
        }
        return out
    }

    fun declineFemale(n: String): Map<GCase, String> {
        val low = n.lowercase()
        val out = linkedMapOf(GCase.NOM to n)
        when {
            low.endsWith("ия") -> {
                val s = n.dropLast(1)
                out[GCase.GEN] = s + "и"; out[GCase.DAT] = s + "и"; out[GCase.ACC] = s + "ю"
                out[GCase.INS] = s + "ей"; out[GCase.PREP] = s + "и"
            }
            low.endsWith("я") -> {
                val s = n.dropLast(1)
                out[GCase.GEN] = s + "и"; out[GCase.DAT] = s + "е"; out[GCase.ACC] = s + "ю"
                out[GCase.INS] = s + "ей"; out[GCase.PREP] = s + "е"
            }
            low.endsWith("а") -> {
                val s = n.dropLast(1)
                val gen = if (s.last().lowercaseChar() in VELAR_SIBILANT) "и" else "ы"
                out[GCase.GEN] = s + gen; out[GCase.DAT] = s + "е"; out[GCase.ACC] = s + "у"
                out[GCase.INS] = s + if (s.last().lowercaseChar() in SIBILANT_C) "ей" else "ой"
                out[GCase.PREP] = s + "е"
            }
            low.endsWith("ь") -> {
                val s = n.dropLast(1)
                out[GCase.GEN] = s + "и"; out[GCase.DAT] = s + "и"; out[GCase.ACC] = n
                out[GCase.INS] = s + "ью"; out[GCase.PREP] = s + "и"
            }
            else -> GCase.entries.forEach { out[it] = n } // несклоняемые: Элеонор, Кармен и т.п.
        }
        return out
    }

    /** gen, dat, ins, prep (acc = gen) */
    private val MALE_IRREGULAR = mapOf(
        "павел" to listOf("павла", "павлу", "павлом", "павле"),
        "лев" to listOf("льва", "льву", "львом", "льве"),
        "пётр" to listOf("петра", "петру", "петром", "петре"),
        "петр" to listOf("петра", "петру", "петром", "петре"),
    )

    private val index: Map<String, List<Interpretation>> by lazy { buildIndex() }

    private fun buildIndex(): Map<String, List<Interpretation>> {
        val raw = HashMap<String, MutableMap<Pair<String, Gender>, MutableSet<GCase>>>()
        fun add(nom: String, g: Gender, forms: Map<GCase, String>) {
            forms.forEach { (c, f) ->
                raw.getOrPut(f.lowercase().foldYo()) { HashMap() }
                    .getOrPut(nom to g) { mutableSetOf() }.add(c)
            }
        }
        NameLexicon.MALE.forEach { add(it, Gender.MALE, declineMale(it)) }
        NameLexicon.FEMALE.forEach { add(it, Gender.FEMALE, declineFemale(it)) }
        // «Пётр»/«Петр», «Семён»/«Семен» — одно имя: оставляем вариант с «ё».
        return raw.mapValues { (_, m) ->
            m.map { (k, v) -> Interpretation(k.first, k.second, v) }
                .groupBy { it.nominative.foldYo() to it.gender }
                .map { (_, list) ->
                    val pick = list.firstOrNull { it.nominative.contains('ё') || it.nominative.contains('Ё') } ?: list.first()
                    pick.copy(cases = list.flatMap { it.cases }.toSet())
                }
        }
    }

    /** Интерпретации словоформы как личного имени (пустой список, если не имя). Поддерживает составные «Анна-Мария». */
    fun analyze(word: String): List<Interpretation> {
        if (word.contains('-')) {
            val parts = word.split('-').map { analyze(it) }
            if (parts.any { it.isEmpty() }) return emptyList()
            return parts.reduce { acc, next ->
                acc.flatMap { a ->
                    next.mapNotNull { b ->
                        val c = a.cases intersect b.cases
                        if (a.gender != b.gender || c.isEmpty()) null
                        else Interpretation("${a.nominative}-${b.nominative}", a.gender, c, a.reliable && b.reliable)
                    }
                }
            }
        }
        return index[word.lowercase().foldYo()].orEmpty()
    }

    fun isKnownName(word: String): Boolean = analyze(word).isNotEmpty()
}

/** Отчества: распознаются по суффиксам во всех падежах. */
object PatronymicMorphology {
    private val MALE_SUFFIX = Regex("^(.{2,}?)(ович|евич|ёвич)(а|у|ем|е)?$")
    // Короткие мужские отчества на -ич: Ильич, Кузьмич, Лукич, Фомич, Никитич, Саввич.
    private val MALE_SHORT = setOf("ильич", "кузьмич", "лукич", "фомич", "никитич", "саввич", "ильичем")
    private val FEMALE_SUFFIX = Regex("^(.{2,}?)(овн|евн|ёвн|ичн|иничн)(а|ы|е|у|ой)$")

    private val maleEnd = mapOf(
        "" to setOf(GCase.NOM), "а" to setOf(GCase.GEN, GCase.ACC), "у" to setOf(GCase.DAT),
        "ем" to setOf(GCase.INS), "е" to setOf(GCase.PREP),
    )
    private val femaleEnd = mapOf(
        "а" to setOf(GCase.NOM), "ы" to setOf(GCase.GEN), "е" to setOf(GCase.DAT, GCase.PREP),
        "у" to setOf(GCase.ACC), "ой" to setOf(GCase.INS),
    )

    fun analyze(word: String): Interpretation? {
        val w = word.lowercase()
        MALE_SUFFIX.matchEntire(w)?.let { m ->
            val end = m.groupValues[3]
            val nom = (m.groupValues[1] + m.groupValues[2]).titleCase()
            return Interpretation(nom, Gender.MALE, maleEnd.getValue(end))
        }
        val shortBase = MALE_SHORT.firstOrNull { w == it || (w.startsWith(it) && w.length - it.length <= 2) }
        if (shortBase != null) {
            val base = if (shortBase.endsWith("ем")) shortBase.dropLast(2) else shortBase
            val end = w.removePrefix(base)
            val cases = maleEnd[end] ?: return null
            return Interpretation(base.titleCase(), Gender.MALE, cases)
        }
        FEMALE_SUFFIX.matchEntire(w)?.let { m ->
            val end = m.groupValues[3]
            val nom = (m.groupValues[1] + m.groupValues[2] + "а").titleCase()
            return Interpretation(nom, Gender.FEMALE, femaleEnd.getValue(end))
        }
        return null
    }
}

/**
 * Фамилии: интерпретации по продуктивным моделям склонения.
 * Модели -ов/-ев/-ин/-ын и -ский/-цкий/-ской восстанавливаются надёжно;
 * остальные склоняемые формы — ненадёжно; несклоняемые (-ко, -их, -ых, -дзе, на гласную) — как есть.
 */
object SurnameMorphology {

    private data class Rule(val suffix: String, val replacement: String, val gender: Gender, val cases: Set<GCase>)

    private val ov = listOf("ов", "ев", "ёв", "ин", "ын")
    private val RULES: List<Rule> = buildList {
        ov.forEach { s ->
            add(Rule(s, s, Gender.MALE, setOf(GCase.NOM)))
            add(Rule(s + "а", s, Gender.MALE, setOf(GCase.GEN, GCase.ACC)))
            add(Rule(s + "у", s, Gender.MALE, setOf(GCase.DAT)))
            add(Rule(s + "ым", s, Gender.MALE, setOf(GCase.INS)))
            add(Rule(s + "е", s, Gender.MALE, setOf(GCase.PREP)))
            add(Rule(s + "а", s + "а", Gender.FEMALE, setOf(GCase.NOM)))
            add(Rule(s + "ой", s + "а", Gender.FEMALE, setOf(GCase.GEN, GCase.DAT, GCase.INS, GCase.PREP)))
            add(Rule(s + "у", s + "а", Gender.FEMALE, setOf(GCase.ACC)))
        }
        listOf("ск", "цк", "зк").forEach { s ->
            add(Rule(s + "ий", s + "ий", Gender.MALE, setOf(GCase.NOM)))
            add(Rule(s + "ой", s + "ой", Gender.MALE, setOf(GCase.NOM)))
            add(Rule(s + "ого", s + "ий", Gender.MALE, setOf(GCase.GEN, GCase.ACC)))
            add(Rule(s + "ому", s + "ий", Gender.MALE, setOf(GCase.DAT)))
            add(Rule(s + "им", s + "ий", Gender.MALE, setOf(GCase.INS)))
            add(Rule(s + "ом", s + "ий", Gender.MALE, setOf(GCase.PREP)))
            add(Rule(s + "ая", s + "ая", Gender.FEMALE, setOf(GCase.NOM)))
            add(Rule(s + "ой", s + "ая", Gender.FEMALE, setOf(GCase.GEN, GCase.DAT, GCase.INS, GCase.PREP)))
            add(Rule(s + "ую", s + "ая", Gender.FEMALE, setOf(GCase.ACC)))
        }
    }

    // Только окончания, которые не совпадают с падежными формами склоняемых фамилий.
    private val INDECLINABLE = Regex(".*(ко|их|ых|аго|яго|дзе|швили|о|э|и)$")
    private val CONSONANT_END = Regex(".*[бвгджзклмнпрстфхцчшщ]$")
    private val CONSONANT_SOFT = Regex(".*[бвгджзклмнпрстфхцчшщ]ь$")

    /** Признак «похоже на фамилию» по суффиксу продуктивной модели. */
    fun hasFamilySuffix(word: String): Boolean {
        val w = word.lowercase().substringAfterLast('-')
        return RULES.any { w.endsWith(it.suffix) && w.length > it.suffix.length + 1 } ||
            w.endsWith("енко") || w.endsWith("дзе") || w.endsWith("швили") || w.endsWith("ян") ||
            w.endsWith("ук") || w.endsWith("юк") || w.endsWith("ых") || w.endsWith("их")
    }

    fun analyze(word: String): List<Interpretation> {
        // Двойная фамилия: склоняется каждая часть; анализируем последнюю, первые части приводим по той же модели.
        if (word.contains('-')) {
            val parts = word.split('-')
            val lastInterps = analyze(parts.last())
            if (parts.size == 2) {
                val firstInterps = analyze(parts.first())
                val combined = mutableListOf<Interpretation>()
                for (a in firstInterps) for (b in lastInterps) {
                    if (a.gender != b.gender && a.gender != Gender.UNKNOWN && b.gender != Gender.UNKNOWN) continue
                    val cases = a.cases intersect b.cases
                    if (cases.isEmpty()) continue
                    val g = if (b.gender != Gender.UNKNOWN) b.gender else a.gender
                    combined += Interpretation("${a.nominative}-${b.nominative}", g, cases, a.reliable && b.reliable)
                }
                if (combined.isNotEmpty()) return combined
            }
            return lastInterps.map { it.copy(nominative = (parts.dropLast(1) + it.nominative).joinToString("-") { p -> p.titleCase() }) }
        }
        val w = word.lowercase()
        val res = mutableListOf<Interpretation>()
        for (r in RULES) {
            if (w.endsWith(r.suffix) && w.length >= r.suffix.length + 2) {
                val nom = (w.dropLast(r.suffix.length) + r.replacement).titleCase()
                res += Interpretation(nom, r.gender, r.cases, reliable = true)
            }
        }
        if (res.isNotEmpty()) return res.distinct()

        if (INDECLINABLE.matches(w)) {
            return listOf(Interpretation(word.titleCase(), Gender.UNKNOWN, GCase.entries.toSet(), reliable = true))
        }
        // Прочие склоняемые (Шевчук, Бондарь, Кравец, Шульга): именительный — как есть,
        // косвенные формы восстанавливаются ненадёжно.
        val out = mutableListOf<Interpretation>()
        if (CONSONANT_END.matches(w) || CONSONANT_SOFT.matches(w)) {
            out += Interpretation(word.titleCase(), Gender.MALE, setOf(GCase.NOM), reliable = true)
            out += Interpretation(word.titleCase(), Gender.FEMALE, GCase.entries.toSet(), reliable = true)
        }
        val obliqueEnds = listOf("а" to setOf(GCase.GEN, GCase.ACC), "у" to setOf(GCase.DAT), "ом" to setOf(GCase.INS),
            "ем" to setOf(GCase.INS), "е" to setOf(GCase.PREP), "я" to setOf(GCase.GEN, GCase.ACC), "ю" to setOf(GCase.DAT))
        for ((end, cases) in obliqueEnds) {
            if (w.endsWith(end) && w.length > end.length + 2) {
                val stem = w.dropLast(end.length)
                if (CONSONANT_END.matches(stem)) {
                    out += Interpretation(stem.titleCase(), Gender.MALE, cases, reliable = false)
                }
            }
        }
        if (w.endsWith("а") || w.endsWith("я")) {
            out += Interpretation(word.titleCase(), Gender.UNKNOWN, setOf(GCase.NOM), reliable = true)
        }
        if (out.isEmpty()) out += Interpretation(word.titleCase(), Gender.UNKNOWN, setOf(GCase.NOM), reliable = true)
        return out
    }

    /**
     * Ключ сопоставления упоминаний одного человека в разных падежах и родах:
     * «Иванова», «Иванову», «Ивановым» → «иванов».
     */
    fun matchKeys(word: String): Set<String> =
        analyze(word).map { neutralKey(it.nominative) }.toSet() + neutralKey(word)

    private fun neutralKey(nom: String): String {
        val w = nom.lowercase().foldYo()
        return when {
            w.endsWith("ская") || w.endsWith("цкая") -> w.dropLast(2) + "ий"
            w.endsWith("ова") || w.endsWith("ева") || w.endsWith("ина") || w.endsWith("ына") -> w.dropLast(1)
            else -> w
        }
    }
}
