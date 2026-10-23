package ru.scanner291.core.quality.metrics

/**
 * CER и WER: расстояние Левенштейна, делённое на длину эталона.
 * Нормализация (по умолчанию): пробелы схлопываются; регистр и «ё» сохраняются,
 * чтобы метрика не завышала качество. Опционально — без учёта регистра/«ё».
 */
object OcrMetrics {

    data class Options(val ignoreCase: Boolean = false, val foldYo: Boolean = false)

    data class Score(val errors: Int, val referenceLength: Int) {
        val rate: Double get() = if (referenceLength == 0) (if (errors == 0) 0.0 else 1.0) else errors.toDouble() / referenceLength
    }

    private fun norm(s: String, o: Options): String {
        var t = s.replace(Regex("\\s+"), " ").trim()
        if (o.foldYo) t = t.replace('ё', 'е').replace('Ё', 'Е')
        if (o.ignoreCase) t = t.lowercase()
        return t
    }

    fun cer(reference: String, hypothesis: String, o: Options = Options()): Score {
        val r = norm(reference, o).codePoints().toArray().toList()
        val h = norm(hypothesis, o).codePoints().toArray().toList()
        return Score(levenshtein(r, h), r.size)
    }

    fun wer(reference: String, hypothesis: String, o: Options = Options()): Score {
        val r = norm(reference, o).split(' ').filter { it.isNotEmpty() }
        val h = norm(hypothesis, o).split(' ').filter { it.isNotEmpty() }
        return Score(levenshtein(r, h), r.size)
    }

    /** Суммарная метрика по набору (микро-усреднение: сумма ошибок / сумма длин). */
    fun corpus(pairs: List<Pair<String, String>>, o: Options = Options()): Pair<Double, Double> {
        val c = pairs.map { cer(it.first, it.second, o) }
        val w = pairs.map { wer(it.first, it.second, o) }
        fun agg(l: List<Score>) = l.sumOf { it.errors }.toDouble() / l.sumOf { it.referenceLength }.coerceAtLeast(1)
        return agg(c) to agg(w)
    }

    fun <T> levenshtein(a: List<T>, b: List<T>): Int {
        if (a.isEmpty()) return b.size
        if (b.isEmpty()) return a.size
        var prev = IntArray(b.size + 1) { it }
        var cur = IntArray(b.size + 1)
        for (i in 1..a.size) {
            cur[0] = i
            for (j in 1..b.size) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.size]
    }
}
