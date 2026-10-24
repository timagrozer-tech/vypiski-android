package ru.scanner291.core.intelligence.fio

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Офлайн-экстрактор ФИО из распознанного текста документа.
 *
 * Алгоритм:
 *  1. Нормализация OCR-текста ([OcrTextNormalizer]).
 *  2. Токенизация с сохранением строк; КАПС приводится к «Заглавной» только для анализа.
 *  3. Поиск упоминаний по шаблонам (Ф И О, И О Ф, Ф И.О., И.О. Ф, Ф И, И Ф, поля «Фамилия/Имя/Отчество», «Ф.И.О.:»)
 *     с проверкой согласования рода и падежа имени, отчества и фамилии.
 *  4. Восстановление именительного падежа только при надёжной модели склонения.
 *  5. Оценка роли по контексту (субъект документа / руководитель / адресат / подписант),
 *     позиции и числу упоминаний; объединение упоминаний одного человека.
 *  6. Уверенность — softmax по оценкам людей с поправкой на абсолютную оценку лидера.
 */
class FioExtractor {

    fun extract(rawText: String): FioResult {
        val text = OcrTextNormalizer.normalize(rawText)
        val lines = text.split('\n')
        val tokens = tokenize(lines)
        if (tokens.isEmpty()) return FioResult.EMPTY

        val matches = mutableListOf<RawMatch>()
        matches += findLabeled(tokens, lines)
        matches += findPatterns(tokens)
        val selected = selectNonOverlapping(matches)
        if (selected.isEmpty()) return FioResult.EMPTY

        val nonEmptyLineCount = max(1, lines.count { it.isNotBlank() })
        val mentions = selected.map { scoreMention(it, tokens, lines, nonEmptyLineCount) }
        val persons = mergePersons(mentions)
        return rank(persons)
    }

    // ---------------------------------------------------------------- токены

    internal enum class Kind { WORD, INITIALS, PUNCT }

    internal data class Token(
        val kind: Kind,
        val text: String,
        /** Для анализа: КАПС → «Заглавная». */
        val norm: String,
        val line: Int,
        val start: Int,
        val end: Int,
        val idx: Int,
        /** Для INITIALS — буквы. */
        val letters: List<Char> = emptyList(),
    ) {
        val isCapitalized: Boolean get() = kind == Kind.WORD && norm.first().isUpperCase()
        val lower: String get() = norm.lowercase()
    }

    private val TOKEN_RE = Regex("([А-ЯЁ])\\.|([А-ЯЁа-яё]+(?:-[А-ЯЁа-яё]+)*)|(\\S)")

    internal fun tokenize(lines: List<String>): List<Token> {
        val out = mutableListOf<Token>()
        lines.forEachIndexed { li, line ->
            val raw = TOKEN_RE.findAll(line).toList()
            var i = 0
            while (i < raw.size) {
                val m = raw[i]
                when {
                    m.groups[1] != null -> {
                        // Группа инициалов: «И.», «И.И.», «И. И.»
                        val letters = mutableListOf(m.groupValues[1].first())
                        var end = m.range.last + 1
                        var j = i + 1
                        while (j < raw.size && raw[j].groups[1] != null && letters.size < 2 &&
                            line.substring(end, raw[j].range.first).isBlank() && raw[j].range.first - end <= 1
                        ) {
                            letters += raw[j].groupValues[1].first(); end = raw[j].range.last + 1; j++
                        }
                        val t = line.substring(m.range.first, end)
                        out += Token(Kind.INITIALS, t, letters.joinToString("") { "$it." }, li, m.range.first, end, out.size, letters)
                        i = j; continue
                    }
                    m.groups[2] != null -> {
                        val w = m.value
                        val isCaps = w.length > 1 && w.all { !it.isLetter() || it.isUpperCase() }
                        val norm = if (isCaps) w.titleCase() else w
                        out += Token(Kind.WORD, w, norm, li, m.range.first, m.range.last + 1, out.size)
                    }
                    else -> out += Token(Kind.PUNCT, m.value, m.value, li, m.range.first, m.range.last + 1, out.size)
                }
                i++
            }
        }
        return out
    }

    // ---------------------------------------------------------------- шаблоны

    internal data class RawMatch(
        val from: Int,          // индекс первого токена
        val to: Int,            // индекс последнего токена (включительно)
        val form: FioForm,
        val surname: Token?,
        val name: Token?,
        val patronymic: Token?,
        val initials: Token?,
        val labeled: Boolean,
        val baseScore: Double,
        val resolved: Resolved,
    )

    internal data class Resolved(
        val surname: String,
        val name: String?,
        val patronymic: String?,
        val gender: Gender,
        val cases: Set<GCase>,
        val reliable: Boolean,
        /** Единственный, но ненадёжный вариант именительного падежа фамилии — для подсказки. */
        val surnameGuess: String? = null,
    )

    private fun isStop(t: Token): Boolean {
        val l = t.lower.foldYo()
        return Lexicon.isStopWord(l)
    }

    /** Может ли слово быть фамилией. */
    private fun surnameLike(t: Token?, strict: Boolean): Boolean {
        if (t == null || t.kind != Kind.WORD || !t.isCapitalized || t.norm.length < 2 || isStop(t)) return false
        if (PatronymicMorphology.analyze(t.norm) != null) return false
        return if (strict) SurnameMorphology.hasFamilySuffix(t.norm) else true
    }

    private fun isName(t: Token?): Boolean =
        t != null && t.kind == Kind.WORD && t.isCapitalized && !isStop(t) && NameMorphology.isKnownName(t.norm)

    private fun isPatr(t: Token?): Boolean =
        t != null && t.kind == Kind.WORD && t.isCapitalized && PatronymicMorphology.analyze(t.norm) != null

    private fun sameLine(vararg ts: Token?): Boolean = ts.filterNotNull().map { it.line }.distinct().size <= 1 ||
        ts.filterNotNull().zipWithNext().all { (a, b) -> b.line - a.line <= 1 }

    internal fun findPatterns(tokens: List<Token>): List<RawMatch> {
        val out = mutableListOf<RawMatch>()
        val words = tokens // индексы совпадают
        for (i in words.indices) {
            val t0 = words[i]
            val t1 = words.getOrNull(i + 1)
            val t2 = words.getOrNull(i + 2)
            val hint = caseHint(tokens, i)

            // Ф И О
            if (surnameLike(t0, strict = false) && isName(t1) && isPatr(t2) && sameLine(t0, t1, t2)) {
                resolve(t0, t1, t2, hint)?.let { out += RawMatch(i, i + 2, FioForm.FULL, t0, t1, t2, null, false, 3.0, it) }
            }
            // И О Ф
            if (isName(t0) && isPatr(t1) && surnameLike(t2, strict = false) && !isName(t2) && sameLine(t0, t1, t2)) {
                resolve(t2!!, t0, t1, hint)?.let { out += RawMatch(i, i + 2, FioForm.FULL, t2, t0, t1, null, false, 2.8, it) }
            }
            // И О (без фамилии)
            if (isName(t0) && isPatr(t1) && sameLine(t0, t1)) {
                resolveNoSurname(t0, t1!!)?.let { out += RawMatch(i, i + 1, FioForm.NAME_PATRONYMIC, null, t0, t1, null, false, 0.6, it) }
            }
            // Ф И.О.
            if (surnameLike(t0, strict = false) && t1?.kind == Kind.INITIALS && t1.line == t0.line) {
                val strong = SurnameMorphology.hasFamilySuffix(t0.norm) || t1.letters.size == 2
                if (strong && !isName(t0)) {
                    out += RawMatch(i, i + 1, FioForm.SURNAME_INITIALS, t0, null, null, t1, false, 2.0, resolveSurnameOnly(t0, hint))
                }
            }
            // И.О. Ф
            if (t0.kind == Kind.INITIALS && surnameLike(t1, strict = false) && t1!!.line == t0.line && !isPatr(t1)) {
                val strong = SurnameMorphology.hasFamilySuffix(t1.norm) || t0.letters.size == 2
                if (strong && !isName(t1)) {
                    out += RawMatch(i, i + 1, FioForm.SURNAME_INITIALS, t1, null, null, t0, false, 2.0, resolveSurnameOnly(t1, hint))
                }
            }
            // Ф И (без отчества)
            if (surnameLike(t0, strict = true) && isName(t1) && !isPatr(t2) && sameLine(t0, t1) && !isName(t0)) {
                resolve(t0, t1, null, hint)?.let { out += RawMatch(i, i + 1, FioForm.SURNAME_NAME, t0, t1, null, null, false, 1.7, it) }
            }
            // И Ф (без отчества)
            if (isName(t0) && surnameLike(t1, strict = true) && !isName(t1) && !isPatr(t2) && sameLine(t0, t1)) {
                resolve(t1!!, t0, null, hint)?.let { out += RawMatch(i, i + 1, FioForm.SURNAME_NAME, t1, t0, null, null, false, 1.4, it) }
            }
        }
        return out
    }

    // ---------------------------------------------------------------- поля «Фамилия / Имя / Отчество», «Ф.И.О.»

    private val FIO_LABEL = Regex("(?iu)(?<![А-ЯЁа-яё])(ф\\.\\s?и\\.\\s?о\\.?|фио|фамилия,\\s*имя,\\s*отчество|фамилия\\s+имя\\s+отчество)(?![А-ЯЁа-яё])\\s*[:\\-–]?")

    internal fun findLabeled(tokens: List<Token>, lines: List<String>): List<RawMatch> {
        val out = mutableListOf<RawMatch>()

        // 1) «Ф.И.О.: Иванов Иван Иванович»
        lines.forEachIndexed { li, line ->
            val lm = FIO_LABEL.find(line) ?: return@forEachIndexed
            val afterCol = lm.range.last + 1
            var value = tokens.filter { it.line == li && it.start >= afterCol }
            if (value.none { it.kind == Kind.WORD }) {
                val next = (li + 1 until lines.size).firstOrNull { lines[it].isNotBlank() } ?: return@forEachIndexed
                value = tokens.filter { it.line == next }
            }
            val vt = value.filter { it.kind != Kind.PUNCT || it.text == "," }.takeWhile { it.kind != Kind.PUNCT }
            if (vt.isEmpty()) return@forEachIndexed
            val inner = findPatterns(tokens).filter {
                it.from >= vt.first().idx && it.to <= vt.last().idx
            }
            val best = inner.maxByOrNull { it.form.completeness * 10 + it.baseScore }
            if (best != null) {
                out += best.copy(labeled = true, baseScore = best.baseScore + 1.5)
            } else {
                // Редкие имена, которых нет в словаре: берём 2–3 слова по порядку «Фамилия Имя Отчество».
                val ws = vt.filter { it.kind == Kind.WORD && it.isCapitalized }.take(3)
                if (ws.size >= 2) {
                    val r = Resolved(ws[0].norm, ws[1].norm, ws.getOrNull(2)?.norm, Gender.UNKNOWN, setOf(GCase.NOM), reliable = false)
                    out += RawMatch(ws.first().idx, ws.last().idx, if (ws.size == 3) FioForm.FULL else FioForm.SURNAME_NAME,
                        ws[0], ws[1], ws.getOrNull(2), null, true, 2.5, r)
                }
            }
        }

        // 2) Отдельные поля «Фамилия», «Имя», «Отчество» (в одной строке или в соседних, до 6 строк).
        val labelIdx = tokens.filter { it.kind == Kind.WORD && it.lower in setOf("фамилия", "имя", "отчество") }
        if (labelIdx.isNotEmpty()) {
            var surname: Token? = null; var name: Token? = null; var patr: Token? = null
            var firstLine = -1
            for (lt in labelIdx) {
                if (firstLine >= 0 && lt.line - firstLine > 6) break
                var j = lt.idx + 1
                while (j < tokens.size && tokens[j].kind == Kind.PUNCT && tokens[j].text in setOf(":", "-", "–", "_")) j++
                val v = tokens.getOrNull(j) ?: continue
                if (v.kind != Kind.WORD || !v.isCapitalized || v.lower in setOf("фамилия", "имя", "отчество")) continue
                if (v.line - lt.line > 1) continue
                if (firstLine < 0) firstLine = lt.line
                when (lt.lower) {
                    "фамилия" -> surname = v
                    "имя" -> name = v
                    "отчество" -> patr = v
                }
            }
            if (surname != null) {
                val r = resolve(surname, name, patr, null)
                    ?: Resolved(surname.norm, name?.norm, patr?.norm, Gender.UNKNOWN, setOf(GCase.NOM), reliable = false)
                val idxs = listOfNotNull(surname, name, patr).map { it.idx }
                out += RawMatch(idxs.min(), idxs.max(), FioForm.LABELED_FIELDS, surname, name, patr, null, true, 3.5, r)
            }
        }
        return out
    }

    // ---------------------------------------------------------------- согласование и нормализация

    /** Подсказка падежа по предшествующему слову (предлог, глагол, звание). */
    private fun caseHint(tokens: List<Token>, i: Int): Set<GCase>? {
        val prev = tokens.getOrNull(i - 1)?.takeIf { it.kind == Kind.WORD && it.line == tokens[i].line } ?: return null
        val p = prev.lower.foldYo()
        Lexicon.CASE_GOVERNORS[p]?.let { return it }
        if (Lexicon.isRankWord(p)) {
            return when {
                p.endsWith("ому") || p.endsWith("ему") || p.endsWith("у") || p.endsWith("ю") -> setOf(GCase.DAT)
                p.endsWith("ого") || p.endsWith("его") || p.endsWith("а") || p.endsWith("я") -> setOf(GCase.GEN, GCase.ACC)
                p.endsWith("ым") || p.endsWith("ом") || p.endsWith("ем") -> setOf(GCase.INS)
                p.endsWith("ой") || p.endsWith("ий") || p.endsWith("ый") -> setOf(GCase.NOM)
                else -> setOf(GCase.NOM)
            }
        }
        return null
    }

    internal fun resolve(surnameT: Token, nameT: Token?, patrT: Token?, hint: Set<GCase>?): Resolved? {
        val patr = patrT?.let { PatronymicMorphology.analyze(it.norm) }
        var gender: Gender? = patr?.gender
        var cases: Set<GCase> = patr?.cases ?: GCase.entries.toSet()

        var nameNom: String? = null
        if (nameT != null) {
            val ni = NameMorphology.analyze(nameT.norm).filter { (gender == null || it.gender == gender) && (it.cases intersect cases).isNotEmpty() }
            if (ni.isEmpty()) return null // имя и отчество не согласованы
            val noms = ni.map { it.nominative }.distinct()
            if (gender == null) {
                val gs = ni.map { it.gender }.distinct()
                if (gs.size == 1) gender = gs.single()
            }
            cases = cases intersect ni.flatMap { it.cases }.toSet()
            nameNom = if (noms.size == 1) noms.single() else null
            if (nameNom == null) {
                // Неоднозначность (Александра: м. р. Р.п. / ж. р. И.п.) — уточняем по фамилии ниже.
                val si = SurnameMorphology.analyze(surnameT.norm)
                val pick = ni.filter { n -> si.any { s -> (s.gender == n.gender || s.gender == Gender.UNKNOWN) && (s.cases intersect n.cases).isNotEmpty() && s.reliable } }
                if (pick.map { it.nominative }.distinct().size == 1) {
                    nameNom = pick.first().nominative; gender = pick.first().gender
                    cases = cases intersect pick.flatMap { it.cases }.toSet()
                }
            }
        }
        if (hint != null && (cases intersect hint).isNotEmpty()) cases = cases intersect hint

        val si = SurnameMorphology.analyze(surnameT.norm).filter {
            (gender == null || it.gender == gender || it.gender == Gender.UNKNOWN) && (it.cases intersect cases).isNotEmpty()
        }
        val surNoms = si.map { it.nominative }.distinct()
        val surnameNom: String
        val surReliable: Boolean
        var surGuess: String? = null
        if (surNoms.size == 1 && si.all { it.reliable }) {
            surnameNom = surNoms.single(); surReliable = true
        } else {
            if (surNoms.size == 1) surGuess = surNoms.single()
            surnameNom = surnameT.norm.titleCase()
            surReliable = GCase.NOM in cases && cases.size == 1
        }
        if (gender == null) {
            val gs = si.map { it.gender }.filter { it != Gender.UNKNOWN }.distinct()
            gender = if (gs.size == 1) gs.single() else Gender.UNKNOWN
        }
        val nameOk = nameT == null || nameNom != null
        return Resolved(
            surname = surnameNom,
            name = nameNom ?: nameT?.norm?.titleCase(),
            patronymic = patr?.nominative,
            gender = gender,
            cases = cases,
            reliable = surReliable && nameOk,
            surnameGuess = surGuess,
        )
    }

    private fun resolveNoSurname(nameT: Token, patrT: Token): Resolved? {
        val patr = PatronymicMorphology.analyze(patrT.norm) ?: return null
        val ni = NameMorphology.analyze(nameT.norm).filter { it.gender == patr.gender && (it.cases intersect patr.cases).isNotEmpty() }
        if (ni.isEmpty()) return null
        return Resolved("", ni.first().nominative, patr.nominative, patr.gender, patr.cases intersect ni.first().cases, reliable = true)
    }

    private fun resolveSurnameOnly(t: Token, hint: Set<GCase>?): Resolved {
        var si = SurnameMorphology.analyze(t.norm)
        if (hint != null) si.filter { (it.cases intersect hint).isNotEmpty() }.takeIf { it.isNotEmpty() }?.let { si = it }
        val noms = si.map { it.nominative }.distinct()
        val gs = si.map { it.gender }.filter { it != Gender.UNKNOWN }.distinct()
        val reliable = noms.size == 1 && si.all { it.reliable }
        return Resolved(
            surname = if (reliable) noms.single() else t.norm.titleCase(),
            name = null, patronymic = null,
            gender = if (gs.size == 1) gs.single() else Gender.UNKNOWN,
            cases = si.flatMap { it.cases }.toSet(),
            reliable = reliable,
        )
    }

    private fun selectNonOverlapping(all: List<RawMatch>): List<RawMatch> {
        val sorted = all.sortedWith(compareByDescending<RawMatch> { it.labeled }
            .thenByDescending { it.form.completeness }.thenByDescending { it.baseScore }.thenBy { it.from })
        val used = BooleanArray((all.maxOfOrNull { it.to } ?: 0) + 2)
        val res = mutableListOf<RawMatch>()
        for (m in sorted) {
            if ((m.from..m.to).any { used[it] }) continue
            (m.from..m.to).forEach { used[it] = true }
            res += m
        }
        return res.sortedBy { it.from }
    }

    // ---------------------------------------------------------------- контекст и роль

    private fun scoreMention(m: RawMatch, tokens: List<Token>, lines: List<String>, nonEmpty: Int): FioMention {
        val first = tokens[m.from]
        val last = tokens[m.to]
        val line = lines[first.line]
        val before = line.substring(0, first.start).lowercase()
        val after = if (last.line == first.line) line.substring(last.end).lowercase() else ""
        val prevLine = (first.line - 1 downTo 0).firstOrNull { lines[it].isNotBlank() }?.let { lines[it].lowercase() } ?: ""
        val reasons = mutableListOf<String>()
        var score = m.baseScore
        reasons += "форма ${m.form} (+${"%.1f".format(m.baseScore)})"

        var subj = 0.0
        var off = 0.0
        for ((cue, w) in Lexicon.SUBJECT_CUES) {
            if (before.contains(cue)) { subj += w; reasons += "«$cue» перед ФИО (+$w)" }
            else if (prevLine.contains(cue)) { subj += w * 0.6; reasons += "«$cue» в строке выше (+${w * 0.6})" }
        }
        if (Regex("^(от|от имени)\\s").containsMatchIn(before.trimStart())) { subj += 2.5; reasons += "«от …» в шапке (+2.5)" }
        if (Regex("(^|\\s)я,?\\s*$").containsMatchIn(before)) { subj += 2.0; reasons += "«Я, …» (+2.0)" }
        for ((cue, w) in Lexicon.OFFICIAL_CUES) {
            if (before.contains(cue)) { off += w; reasons += "«$cue» перед ФИО (−$w)" }
            else if (prevLine.contains(cue) && !prevLine.trimStart().startsWith("от ")) { off += w * 0.6; reasons += "«$cue» в строке выше (−${w * 0.6})" }
        }
        // Звание прямо перед полным ФИО в тексте приказа («1. Майора Арефьева Павла Валерьевича») — о нём документ
        val prevWord = before.trimEnd().split(Regex("\\s+")).lastOrNull()?.replace('ё', 'е')?.trim('.', ',') ?: ""
        if (m.form.completeness >= 5 && Lexicon.isRankWord(prevWord)) { subj += 1.5; reasons += "звание «$prevWord» перед ФИО (+1.5)" }
        // Блок подписей: «гвардии подполковник» строкой выше, затем «С.Цахилов» — подписант, а не субъект
        val prevWords = prevLine.replace('ё', 'е').split(Regex("[\\s,.]+")).filter { it.isNotBlank() }
        if (m.form.completeness <= 3 && prevWords.isNotEmpty() && prevWords.size <= 4 &&
            prevWords.any { Lexicon.isRankWord(it) } && prevWords.all { Lexicon.isRankWord(it) || it in Lexicon.RANK_PREFIXES }) {
            off += 2.0; reasons += "звание подписанта в строке выше (−2.0)"
        }
        if (Regex("^\\s*[,–-]\\s*(" + Lexicon.OFFICIAL_AFTER.joinToString("|") + ")").containsMatchIn(after)) {
            off += 2.0; reasons += "должность руководителя после ФИО (−2.0)"
        }
        var role = PersonRole.UNKNOWN
        val isDative = m.resolved.cases.isNotEmpty() && m.resolved.cases.all { it == GCase.DAT } ||
            (m.resolved.cases == setOf(GCase.DAT, GCase.PREP))
        val addressee = isDative && Lexicon.ADDRESSEE_CUES.any { before.contains(it) || prevLine.contains(it) }
        if (addressee) { score -= 3.0; role = PersonRole.ADDRESSEE; reasons += "адресат документа (−3.0)" }

        val signatureLine = line.contains("___") || line.contains("(подпись)") || Regex("/\\s*[А-ЯЁ]").containsMatchIn(line) ||
            Regex("[А-ЯЁа-яё.]\\s*/\\s*$").containsMatchIn(line)
        val relPos = first.line.toDouble() / max(1, lines.size - 1)
        if (signatureLine) { score -= 1.5; reasons += "строка подписи (−1.5)" }
        else if (relPos > 0.8 && line.split(' ').size <= 6 && m.form == FioForm.SURNAME_INITIALS) {
            score -= 0.7; reasons += "короткая строка в конце документа (−0.7)"
        }
        score += subj - off
        score += 0.3 * (1.0 - relPos)

        if (role == PersonRole.UNKNOWN) role = when {
            Lexicon.PARTY_CUES.any { after.contains(it) || before.contains(it) } -> PersonRole.PARTY
            off > subj && off > 0 -> PersonRole.OFFICIAL
            signatureLine -> PersonRole.SIGNATORY
            subj > 0 || m.labeled -> PersonRole.SUBJECT
            else -> PersonRole.UNKNOWN
        }
        if (role == PersonRole.PARTY) { score += 1.0; reasons += "сторона договора (+1.0)" }

        val r = m.resolved
        val normalized = Fio(
            surname = r.surnameGuess ?: r.surname,
            name = r.name,
            patronymic = r.patronymic,
            initials = m.initials?.norm,
        )
        // Ненадёжное склонение: ФИО остаётся в исходных формах целиком, нормализованный вариант — только подсказка.
        val original = Fio(
            surname = m.surname?.norm?.titleCase() ?: "",
            name = m.name?.norm?.titleCase(),
            patronymic = m.patronymic?.norm?.titleCase(),
            initials = m.initials?.norm,
        )
        val fio = if (r.reliable) normalized else original
        val guess = if (!r.reliable && normalized != original) normalized else null
        val origText = tokens.subList(m.from, m.to + 1).joinToString(" ") { it.text }
        val firstInitial = r.name?.firstOrNull() ?: m.initials?.letters?.firstOrNull()
        return FioMention(
            fio = fio, originalText = origText, line = first.line, form = m.form, gender = r.gender,
            cases = r.cases, normalizationReliable = r.reliable, nominativeGuess = guess, role = role, score = score, reasons = reasons,
            surnameKeys = if (r.surname.isEmpty()) emptySet() else SurnameMorphology.matchKeys(m.surname?.norm ?: r.surname) +
                SurnameMorphology.matchKeys(r.surname),
            firstInitial = firstInitial?.uppercaseChar(),
        )
    }

    // ---------------------------------------------------------------- объединение и ранжирование

    private fun mergePersons(mentions: List<FioMention>): List<PersonCandidate> {
        val groups = mutableListOf<MutableList<FioMention>>()
        val noSurname = mentions.filter { it.surnameKeys.isEmpty() }
        for (m in mentions - noSurname.toSet()) {
            val g = groups.firstOrNull { grp ->
                grp.any { o ->
                    (o.surnameKeys intersect m.surnameKeys).isNotEmpty() &&
                        (o.firstInitial == null || m.firstInitial == null || o.firstInitial == m.firstInitial)
                }
            }
            if (g != null) g += m else groups += mutableListOf(m)
        }
        // «Иван Иванович» без фамилии присоединяем к человеку с тем же именем, иначе — отдельный слабый кандидат.
        for (m in noSurname) {
            val g = groups.firstOrNull { grp -> grp.any { it.fio.name == m.fio.name && (it.fio.patronymic == null || it.fio.patronymic == m.fio.patronymic) } }
            if (g != null) g += m else groups += mutableListOf(m)
        }
        return groups.map { grp ->
            val best = grp.filter { it.fio.surname.isNotEmpty() }.ifEmpty { grp }.maxWith(
                compareBy<FioMention> { it.form.completeness }
                    .thenBy { it.normalizationReliable }
                    .thenBy { if (GCase.NOM in it.cases) 1 else 0 }
                    .thenBy { it.score },
            )
            // Если у лучшего упоминания только инициалы, а в другом есть полное имя — берём полное.
            val top = grp.maxOf { it.score }
            val freqBonus = min(1.4, 0.7 * (grp.size - 1))
            val roles = grp.map { it.role }
            val role = when {
                PersonRole.SUBJECT in roles -> PersonRole.SUBJECT
                PersonRole.PARTY in roles -> PersonRole.PARTY
                PersonRole.ADDRESSEE in roles -> PersonRole.ADDRESSEE
                PersonRole.OFFICIAL in roles -> PersonRole.OFFICIAL
                PersonRole.SIGNATORY in roles -> PersonRole.SIGNATORY
                else -> PersonRole.UNKNOWN
            }
            // Подпись того же человека внизу (рапорт, заявление) — не штраф, а подтверждение.
            val subjectMentionScore = grp.filter { it.role != PersonRole.SIGNATORY }.maxOfOrNull { it.score } ?: top
            val completenessBonus = (best.form.completeness - 3) * 0.25
            PersonCandidate(
                fio = best.fio,
                mentions = grp.sortedBy { it.line },
                role = role,
                score = max(top, subjectMentionScore) + freqBonus + completenessBonus,
                confidence = 0.0,
                normalizationReliable = best.normalizationReliable,
                nominativeGuess = best.nominativeGuess,
            )
        }
    }

    private fun rank(persons: List<PersonCandidate>): FioResult {
        if (persons.isEmpty()) return FioResult.EMPTY
        val sorted = persons.sortedByDescending { it.score }
        val t = 1.0
        val maxS = sorted.first().score
        val exps = sorted.map { exp((it.score - maxS) / t) }
        val sum = exps.sum()
        val probs = exps.map { it / sum }
        val absFactor = ((maxS - 0.5) / 2.5).coerceIn(0.0, 1.0)
        val withConf = sorted.mapIndexed { i, p ->
            p.copy(confidence = (probs[i] * if (i == 0) absFactor else 1.0).coerceIn(0.0, 0.99))
        }
        val primary = withConf.first()
        val conf = primary.confidence
        val status = when {
            primary.role == PersonRole.OFFICIAL || primary.role == PersonRole.ADDRESSEE -> FioStatus.LOW_CONFIDENCE
            conf >= FioResult.THRESHOLD_FOUND -> FioStatus.FOUND
            conf >= FioResult.THRESHOLD_CHOICE -> FioStatus.NEEDS_CHOICE
            else -> FioStatus.LOW_CONFIDENCE
        }
        return FioResult(primary, withConf.drop(1), conf, status)
    }
}
