package ru.scanner291.domain.fio

/**
 * Нормализация распознанного текста перед поиском ФИО.
 * Ничего не «додумывает»: исправляет только типовые ошибки OCR,
 * которые однозначно восстанавливаются.
 */
object OcrTextNormalizer {

    private val LATIN_TO_CYR: Map<Char, Char> = mapOf(
        'A' to 'А', 'B' to 'В', 'C' to 'С', 'E' to 'Е', 'H' to 'Н', 'K' to 'К', 'M' to 'М',
        'O' to 'О', 'P' to 'Р', 'T' to 'Т', 'X' to 'Х', 'Y' to 'У',
        'a' to 'а', 'c' to 'с', 'e' to 'е', 'o' to 'о', 'p' to 'р', 'x' to 'х', 'y' to 'у', 'k' to 'к',
    )
    private val CYR = Regex("[А-ЯЁа-яё]")
    private val MIXED_WORD = Regex("[A-Za-zА-ЯЁа-яё0]+")

    fun normalize(text: String): String {
        var t = text.replace("\r\n", "\n").replace('\r', '\n')
            .replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')
            .replace('—', '–').replace('‐', '-').replace('‑', '-')
        // Перенос внутри слова: «Ива-\nнов» → «Иванов»; «Римский-\nКорсаков» → «Римский-Корсаков».
        t = Regex("([А-ЯЁа-яё])-[ \\t]*\\n[ \\t]*([а-яё])").replace(t) { it.groupValues[1] + it.groupValues[2] }
        t = Regex("([А-ЯЁа-яё])-[ \\t]*\\n[ \\t]*([А-ЯЁ])").replace(t) { it.groupValues[1] + "-" + it.groupValues[2] }
        // Латинские двойники и ноль внутри кириллических слов.
        t = MIXED_WORD.replace(t) { m ->
            val w = m.value
            if (!CYR.containsMatchIn(w)) return@replace w
            buildString(w.length) {
                for (ch in w) append(LATIN_TO_CYR[ch] ?: if (ch == '0') 'о' else ch)
            }
        }
        // Пробелы.
        t = t.lines().joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() }
        return t
    }
}
