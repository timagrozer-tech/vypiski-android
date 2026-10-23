package ru.scanner291.core.quality.gate

import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.quality.capture.FrameQuality
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Серое изображение (0..255 в беззнаковом смысле). */
class GrayImage(val w: Int, val h: Int, val px: ByteArray) {
    init { require(px.size == w * h) }
    operator fun get(x: Int, y: Int): Int = px[y * w + x].toInt() and 0xFF
}

enum class Verdict(val title: String) { GOOD("Хорошо"), ACCEPTABLE("Допустимо"), POOR("Плохо") }
enum class Severity { WARNING, ERROR }

enum class Check(val title: String) {
    SHARPNESS("Резкость"), LIGHTING("Освещённость"), GLARE("Блики"), BORDERS("Полнота границ"), SKEW("Наклон"),
    TEXT("Видимость текста"), SIZE("Размер листа в кадре"), BACKGROUND("Равномерность фона"),
}

data class Issue(val check: Check, val severity: Severity, val value: Double, val message: String)

data class QualityReport(val verdict: Verdict, val issues: List<Issue>, val metrics: Map<Check, Double>) {
    /** Сохранение никогда не блокируется — отчёт только советует (ТЗ). */
    val blocksSaving: Boolean get() = false
}

/**
 * Quality Gate (этап 16): оценка снимка страницы до и после обработки. Пороги — в [T], проверены
 * QualityGateTest на эталонных изображениях. Три вердикта: хорошо / допустимо (предупреждения) /
 * плохо (есть ошибка). Сохранение не блокируется.
 */
object QualityGate {
    /** Пороги. Резкость — дисперсия лапласиана на копии со стороной ~800 px. */
    object T {
        const val SHARP_ERROR = 40.0; const val SHARP_WARN = 120.0
        const val DARK_ERROR = 70.0; const val DARK_WARN = 110.0; const val BRIGHT_WARN = 248.0
        const val GLARE_WARN = 0.004; const val GLARE_ERROR = 0.02
        const val EDGE_MARGIN = 0.01
        const val SKEW_WARN_DEG = 12.0; const val SKEW_ERROR_DEG = 25.0
        const val TEXT_CONTRAST_ERROR = 60.0; const val TEXT_CONTRAST_WARN = 110.0
        const val AREA_ERROR = 0.12; const val AREA_WARN = 0.25
        const val BG_STD_WARN = 15.0
    }

    /**
     * @param frame исходный снимок (серый, уменьшенный ~800 px)
     * @param corners нормализованные углы листа (null — весь кадр)
     * @param processed обработанная страница (серый), если уже есть — для видимости текста
     */
    fun evaluate(frame: GrayImage, corners: Quad?, processed: GrayImage? = null): QualityReport {
        val issues = ArrayList<Issue>(); val m = LinkedHashMap<Check, Double>()
        val q = corners ?: Quad(ru.scanner291.core.common.geometry.Pt(0.0, 0.0), ru.scanner291.core.common.geometry.Pt(1.0, 0.0),
            ru.scanner291.core.common.geometry.Pt(1.0, 1.0), ru.scanner291.core.common.geometry.Pt(0.0, 1.0))
        val bx0 = (q.points.minOf { it.x } * frame.w).toInt().coerceIn(0, frame.w - 1)
        val bx1 = (q.points.maxOf { it.x } * frame.w).toInt().coerceIn(bx0 + 1, frame.w)
        val by0 = (q.points.minOf { it.y } * frame.h).toInt().coerceIn(0, frame.h - 1)
        val by1 = (q.points.maxOf { it.y } * frame.h).toInt().coerceIn(by0 + 1, frame.h)

        // Резкость — внутри листа
        val sharp = FrameQuality.laplacianVariance(crop(frame, bx0, by0, bx1, by1).px, bx1 - bx0, by1 - by0)
        m[Check.SHARPNESS] = sharp
        when {
            sharp < T.SHARP_ERROR -> issues += Issue(Check.SHARPNESS, Severity.ERROR, sharp, "Снимок размыт — текст может не распознаться. Переснимите, держа телефон неподвижно")
            sharp < T.SHARP_WARN -> issues += Issue(Check.SHARPNESS, Severity.WARNING, sharp, "Резкость ниже обычной")
        }
        // Освещённость бумаги: 75-й процентиль яркости внутри листа (бумага светлее текста)
        val hist = histogram(frame, bx0, by0, bx1, by1)
        val paper = percentile(hist, 0.75).toDouble()
        m[Check.LIGHTING] = paper
        when {
            paper < T.DARK_ERROR -> issues += Issue(Check.LIGHTING, Severity.ERROR, paper, "Слишком темно — включите свет или вспышку")
            paper < T.DARK_WARN -> issues += Issue(Check.LIGHTING, Severity.WARNING, paper, "Темновато — лучше добавить света")
            paper > T.BRIGHT_WARN && percentile(hist, 0.25) > 240 -> issues += Issue(Check.LIGHTING, Severity.WARNING, paper, "Пересвет — детали могут потеряться")
        }
        // Блики
        val glare = FrameQuality.glareFraction(frame.px, frame.w, frame.h, bx0, by0, bx1, by1)
        m[Check.GLARE] = glare
        when {
            glare > T.GLARE_ERROR -> issues += Issue(Check.GLARE, Severity.ERROR, glare, "Сильный блик — измените угол съёмки или выключите вспышку")
            glare > T.GLARE_WARN -> issues += Issue(Check.GLARE, Severity.WARNING, glare, "Есть блик")
        }
        // Полнота границ: угол у края кадра — лист, возможно, обрезан
        if (corners != null) {
            val touching = q.points.count { it.x < T.EDGE_MARGIN || it.y < T.EDGE_MARGIN || it.x > 1 - T.EDGE_MARGIN || it.y > 1 - T.EDGE_MARGIN }
            m[Check.BORDERS] = touching.toDouble()
            // Срез краем кадра — предупреждение, не ошибка: часто обрезано только поле, а сохранение не блокируется
            cutSide(q)?.let { side -> issues += Issue(Check.BORDERS, Severity.WARNING, touching.toDouble(), "$side может быть обрезан. Переснимите или продолжите") }
            // Наклон: верхняя сторона относительно горизонтали (в пикселях кадра)
            val ang = Math.toDegrees(atan2((q.tr.y - q.tl.y) * frame.h, (q.tr.x - q.tl.x) * frame.w)).let { abs(it) }.let { min(it, abs(180 - it)) }
            m[Check.SKEW] = ang
            if (ang > T.SKEW_ERROR_DEG) issues += Issue(Check.SKEW, Severity.ERROR, ang, "Сильный наклон — текст после выравнивания будет менее чётким")
            else if (ang > T.SKEW_WARN_DEG) issues += Issue(Check.SKEW, Severity.WARNING, ang, "Лист наклонён — держите телефон ровнее")
            // Размер листа в кадре (площадь четырёхугольника)
            val area = abs((q.tl.x * q.tr.y - q.tr.x * q.tl.y) + (q.tr.x * q.br.y - q.br.x * q.tr.y) + (q.br.x * q.bl.y - q.bl.x * q.br.y) + (q.bl.x * q.tl.y - q.tl.x * q.bl.y)) / 2
            m[Check.SIZE] = area
            if (area < T.AREA_ERROR) issues += Issue(Check.SIZE, Severity.ERROR, area, "Лист слишком далеко — поднесите телефон ближе")
            else if (area < T.AREA_WARN) issues += Issue(Check.SIZE, Severity.WARNING, area, "Лист мелко в кадре — мелкий текст может не распознаться")
        }
        // Равномерность фона: разброс яркости «бумаги» по крупным клеткам листа
        val bg = paperUnevenness(frame, bx0, by0, bx1, by1)
        m[Check.BACKGROUND] = bg
        if (bg > T.BG_STD_WARN) issues += Issue(Check.BACKGROUND, Severity.WARNING, bg, "Неравномерный свет или тень — фильтр выровняет, но лучше снять при ровном свете")
        // Видимость текста — по обработанной странице: разница бумага − текст
        if (processed != null) {
            val ph = histogram(processed, 0, 0, processed.w, processed.h)
            val dark = ph.take(200).sum()
            val contrast = if (dark < processed.w * processed.h * 0.002) 0.0 else (percentile(ph, 0.9) - percentile(ph, 0.01)).toDouble()
            m[Check.TEXT] = contrast
            when {
                contrast < T.TEXT_CONTRAST_ERROR -> issues += Issue(Check.TEXT, Severity.ERROR, contrast, "Текст почти не виден — попробуйте фильтр «Усиленный текст» или переснимите")
                contrast < T.TEXT_CONTRAST_WARN -> issues += Issue(Check.TEXT, Severity.WARNING, contrast, "Текст бледный")
            }
        }
        val verdict = when {
            issues.any { it.severity == Severity.ERROR } -> Verdict.POOR
            issues.isNotEmpty() -> Verdict.ACCEPTABLE
            else -> Verdict.GOOD
        }
        return QualityReport(verdict, issues, m)
    }

    /** Какая сторона листа у края кадра: «Низ листа», «Верх листа», «Левый край листа», «Правый край листа». */
    fun cutSide(q: Quad): String? {
        val e = T.EDGE_MARGIN
        val bottom = q.points.count { it.y > 1 - e }; val top = q.points.count { it.y < e }
        val left = q.points.count { it.x < e }; val right = q.points.count { it.x > 1 - e }
        val best = listOf("Низ листа" to bottom, "Верх листа" to top, "Левый край листа" to left, "Правый край листа" to right).maxBy { it.second }
        return best.first.takeIf { best.second > 0 }
    }

    private fun crop(g: GrayImage, x0: Int, y0: Int, x1: Int, y1: Int): GrayImage {
        val w = x1 - x0; val h = y1 - y0
        val out = ByteArray(w * h)
        for (y in 0 until h) System.arraycopy(g.px, (y0 + y) * g.w + x0, out, y * w, w)
        return GrayImage(w, h, out)
    }

    private fun histogram(g: GrayImage, x0: Int, y0: Int, x1: Int, y1: Int): IntArray {
        val hst = IntArray(256)
        for (y in y0 until y1) for (x in x0 until x1) hst[g[x, y]]++
        return hst
    }

    fun percentile(hist: IntArray, p: Double): Int {
        val n = hist.sum(); if (n == 0) return 0
        var acc = 0L; val target = n * p
        for (i in hist.indices) { acc += hist[i]; if (acc >= target) return i }
        return 255
    }

    /** Ст. отклонение «бумаги» по клеткам 6×6: в каждой клетке берётся 80-й процентиль (текст не влияет). */
    private fun paperUnevenness(g: GrayImage, x0: Int, y0: Int, x1: Int, y1: Int): Double {
        val n = 6; val vals = ArrayList<Double>()
        val cw = max(1, (x1 - x0) / n); val ch = max(1, (y1 - y0) / n)
        // Отступ 10 % от краёв листа — границы с фоном стола не учитываются
        for (j in 1 until n - 1) for (i in 1 until n - 1) {
            val h = histogram(g, x0 + i * cw, y0 + j * ch, min(x1, x0 + (i + 1) * cw), min(y1, y0 + (j + 1) * ch))
            vals += percentile(h, 0.8).toDouble()
        }
        val mean = vals.average()
        return sqrt(vals.sumOf { (it - mean) * (it - mean) } / vals.size)
    }

    @Suppress("unused") private fun dist(ax: Double, ay: Double, bx: Double, by: Double) = hypot(ax - bx, ay - by)
}
