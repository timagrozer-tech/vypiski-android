package ru.scanner291.core.quality.gate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.scanner291.core.common.geometry.Pt
import ru.scanner291.core.common.geometry.Quad
import kotlin.math.cos
import kotlin.math.sin

/** Этап 16: эталонные снимки (синтетические, с известным дефектом) → ожидаемые проверки и вердикты. */
class QualityGateTest {
    private val W = 600; private val H = 800

    /** Кадр: стол 90, лист 230 в прямоугольнике/повороте, строки текста 40. */
    private fun frame(
        quad: Quad = Quad(Pt(0.12, 0.08), Pt(0.88, 0.08), Pt(0.88, 0.92), Pt(0.12, 0.92)),
        paper: Int = 230, light: (Int, Int) -> Double = { _, _ -> 1.0 }, glare: Boolean = false, blur: Int = 0,
    ): GrayImage {
        val px = IntArray(W * H)
        val ps = quad.points.map { Pt(it.x * W, it.y * H) }
        fun inside(x: Double, y: Double): Boolean {
            var s = 0
            for (i in 0 until 4) { val a = ps[i]; val b = ps[(i + 1) % 4]; val c = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x); s += if (c >= 0) 1 else -1 }
            return s == 4 || s == -4
        }
        // локальные координаты листа (u вдоль верхней стороны) для строк текста
        val ux = ps[1].x - ps[0].x; val uy = ps[1].y - ps[0].y; val vx = ps[3].x - ps[0].x; val vy = ps[3].y - ps[0].y
        val det = ux * vy - uy * vx
        for (y in 0 until H) for (x in 0 until W) {
            var v = 90
            if (inside(x + 0.5, y + 0.5)) {
                val dx = x - ps[0].x; val dy = y - ps[0].y
                val u = (dx * vy - dy * vx) / det; val vv = (ux * dy - uy * dx) / det
                val line = vv in 0.1..0.85 && ((vv * 100).toInt() % 4 == 0) && u in 0.08..0.85
                v = if (line) 40 else paper
            }
            v = (v * light(x, y)).toInt()
            if (glare && (x - 300) * (x - 300) + (y - 300) * (y - 300) < 45 * 45) v = 255
            px[y * W + x] = v.coerceIn(0, 255)
        }
        repeat(blur) { boxBlur(px) }
        return GrayImage(W, H, ByteArray(px.size) { px[it].toByte() })
    }

    private fun boxBlur(p: IntArray) {
        val c = p.copyOf()
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            var s = 0; for (dy in -1..1) for (dx in -1..1) s += c[(y + dy) * W + x + dx]; p[y * W + x] = s / 9
        }
    }

    private val good = Quad(Pt(0.12, 0.08), Pt(0.88, 0.08), Pt(0.88, 0.92), Pt(0.12, 0.92))
    private fun checks(r: QualityReport) = r.issues.map { it.check to it.severity }.toSet()

    @Test fun cleanPageIsGood() {
        val r = QualityGate.evaluate(frame(), good, processed = frame(paper = 250))
        assertEquals("${r.issues} ${r.metrics}", Verdict.GOOD, r.verdict)
        assertFalse(r.blocksSaving)
    }

    @Test fun blurIsDetected() {
        val r = QualityGate.evaluate(frame(blur = 6), good)
        assertTrue("${r.metrics}", Check.SHARPNESS to Severity.ERROR in checks(r)); assertEquals(Verdict.POOR, r.verdict)
        assertFalse("сохранение не блокируется", r.blocksSaving)
    }

    @Test fun mildBlurIsWarning() {
        val r = QualityGate.evaluate(frame(blur = 1), good)
        assertTrue("${r.metrics}", checks(r).any { it.first == Check.SHARPNESS } || r.verdict == Verdict.GOOD)
    }

    @Test fun darkness() {
        assertTrue(Check.LIGHTING to Severity.ERROR in checks(QualityGate.evaluate(frame(light = { _, _ -> 0.25 }), good)))
        val dim = QualityGate.evaluate(frame(light = { _, _ -> 0.42 }), good)
        assertTrue("${dim.metrics}", Check.LIGHTING to Severity.WARNING in checks(dim))
    }

    @Test fun glare() {
        val r = QualityGate.evaluate(frame(glare = true), good)
        assertTrue("${r.metrics}", checks(r).any { it.first == Check.GLARE })
    }

    @Test fun cutOffBorders() {
        val q = Quad(Pt(0.0, 0.0), Pt(0.85, 0.05), Pt(0.9, 0.95), Pt(0.0, 1.0))
        assertTrue(Check.BORDERS to Severity.ERROR in checks(QualityGate.evaluate(frame(q), q)))
        val one = Quad(Pt(0.005, 0.1), Pt(0.85, 0.1), Pt(0.85, 0.9), Pt(0.1, 0.9))
        assertTrue(Check.BORDERS to Severity.WARNING in checks(QualityGate.evaluate(frame(one), one)))
    }

    private fun rotated(deg: Double, scale: Double = 0.6): Quad {
        val a = Math.toRadians(deg); val hw = 0.5 * scale; val hh = 0.5 * scale * 0.75 * W / H * 1.3
        fun p(x: Double, y: Double) = Pt(0.5 + (x * cos(a) - y * sin(a) * H / W), 0.5 + (x * sin(a) * W / H + y * cos(a)))
        return Quad(p(-hw, -hh), p(hw, -hh), p(hw, hh), p(-hw, hh))
    }

    @Test fun skew() {
        val q30 = rotated(30.0); val q15 = rotated(15.0)
        assertTrue(QualityGate.evaluate(frame(q30), q30).metrics.toString(), Check.SKEW to Severity.ERROR in checks(QualityGate.evaluate(frame(q30), q30)))
        assertTrue(Check.SKEW to Severity.WARNING in checks(QualityGate.evaluate(frame(q15), q15)))
        assertFalse(checks(QualityGate.evaluate(frame(), good)).any { it.first == Check.SKEW })
    }

    @Test fun smallDocument() {
        val q = Quad(Pt(0.4, 0.4), Pt(0.65, 0.4), Pt(0.65, 0.68), Pt(0.4, 0.68))   // 7 % кадра
        assertTrue(Check.SIZE to Severity.ERROR in checks(QualityGate.evaluate(frame(q), q)))
    }

    @Test fun unevenLightIsWarningNotError() {
        val r = QualityGate.evaluate(frame(light = { x, _ -> 0.45 + 0.55 * x / W }), good)
        assertTrue("${r.metrics}", Check.BACKGROUND to Severity.WARNING in checks(r))
    }

    @Test fun fadedTextOnProcessedPage() {
        val faded = frame(paper = 250).let { g -> GrayImage(g.w, g.h, ByteArray(g.px.size) { i -> val v = g.px[i].toInt() and 0xFF; (if (v < 100) 215 else v).toByte() }) }
        assertTrue(checks(QualityGate.evaluate(frame(), good, faded)).any { it.first == Check.TEXT })
        val empty = GrayImage(10, 10, ByteArray(100) { 255.toByte() })
        assertTrue(Check.TEXT to Severity.ERROR in checks(QualityGate.evaluate(frame(), good, empty)))
    }

    @Test fun percentiles() {
        val h = IntArray(256).also { it[10] = 1; it[200] = 3 }
        assertEquals(10, QualityGate.percentile(h, 0.25)); assertEquals(200, QualityGate.percentile(h, 0.5))
    }
}
