package ru.scanner291.core.imageprocessing

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import ru.scanner291.core.common.geometry.Pt
import ru.scanner291.core.common.geometry.Quad
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Найденный документ: углы в пикселях входного кадра и оценка 0..1. */
data class Detection(val quad: Quad, val score: Double)

/**
 * Поиск листа документа на сером кадре (ADR-0002). Несколько карт границ — Canny с порогами
 * от медианы яркости, Canny с фиксированными мягкими порогами, порог Оцу с морфологическим
 * закрытием (белый лист на тёмном фоне) — для каждой ищутся выпуклые четырёхугольники.
 * Лучший — по площади × прямоугольности; углы между сторонами 50°–130°.
 * Кадр уменьшается до [workSide] по большей стороне: быстро и устойчиво к шуму.
 */
class DocumentDetector(
    private val workSide: Int = 480,
    private val minAreaFraction: Double = 0.12,
) {
    fun detect(gray: Mat): Detection? {
        require(gray.type() == CvType.CV_8UC1) { "Нужен серый кадр CV_8UC1" }
        val scale = min(1.0, workSide.toDouble() / max(gray.cols(), gray.rows()))
        val small = Mat()
        if (scale < 1.0) Imgproc.resize(gray, small, Size(gray.cols() * scale, gray.rows() * scale), 0.0, 0.0, Imgproc.INTER_AREA)
        else gray.copyTo(small)
        try {
            val blurred = Mat()
            Imgproc.GaussianBlur(small, blurred, Size(5.0, 5.0), 0.0)
            val frameArea = small.cols().toDouble() * small.rows()
            var best: Detection? = null
            for (edges in edgeMaps(blurred)) {
                candidate(edges, frameArea, small.cols(), small.rows())?.let { if (best == null || it.score > best!!.score) best = it }
                edges.release()
            }
            blurred.release()
            return best?.let { Detection(it.quad.scaled(1 / scale, 1 / scale), it.score) }
        } finally {
            small.release()
        }
    }

    private fun edgeMaps(blurred: Mat): List<Mat> {
        val maps = mutableListOf<Mat>()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        // 1) Canny с порогами от медианы
        val med = median(blurred)
        val lo = max(0.0, 0.66 * med); val hi = min(255.0, 1.33 * med).coerceAtLeast(lo + 20)
        maps += Mat().also { Imgproc.Canny(blurred, it, lo, hi); Imgproc.dilate(it, it, kernel) }
        // 2) мягкие фиксированные пороги — для низкоконтрастного фона
        maps += Mat().also { Imgproc.Canny(blurred, it, 20.0, 60.0); Imgproc.dilate(it, it, kernel) }
        // 3) Оцу + закрытие: светлый лист на тёмном/пёстром фоне, края — как граница области
        maps += Mat().also { m ->
            Imgproc.threshold(blurred, m, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            Imgproc.morphologyEx(m, m, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0)))
        }
        kernel.release()
        return maps
    }

    private fun candidate(edges: Mat, frameArea: Double, w: Int, h: Int): Detection? {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        var best: Detection? = null
        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area < minAreaFraction * frameArea) { c.release(); continue }
            val quad = toQuad(c)
            c.release()
            quad ?: continue
            if (!quad.isConvex() || !anglesOk(quad)) continue
            val qa = quad.area()
            if (qa < minAreaFraction * frameArea || qa > 0.995 * frameArea) continue
            val rect = Imgproc.minAreaRect(MatOfPoint2f(*quad.points.map { Point(it.x, it.y) }.toTypedArray()))
            val rectangularity = (qa / (rect.size.area().coerceAtLeast(1.0))).coerceIn(0.0, 1.0)
            // Касание краёв кадра — признак «нашёлся сам кадр», а не лист: штраф.
            val touches = quad.points.count { it.x < 2 || it.y < 2 || it.x > w - 3 || it.y > h - 3 }
            val score = (qa / frameArea) * rectangularity * (1.0 - 0.15 * touches)
            if (best == null || score > best.score) best = Detection(quad, score)
        }
        return best
    }

    private fun toQuad(c: MatOfPoint): Quad? {
        val hullIdx = MatOfInt()
        Imgproc.convexHull(c, hullIdx)
        val pts = c.toArray(); val idx = hullIdx.toArray()
        hullIdx.release()
        val hull = MatOfPoint2f(*idx.map { pts[it] }.toTypedArray())
        val peri = Imgproc.arcLength(hull, true)
        var eps = 0.02
        while (eps <= 0.08) {
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(hull, approx, eps * peri, true)
            val a = approx.toArray()
            approx.release()
            if (a.size == 4) { hull.release(); return Quad.ordered(a.map { Pt(it.x, it.y) }) }
            if (a.size < 4) break
            eps += 0.01
        }
        hull.release()
        return null
    }

    private fun anglesOk(q: Quad): Boolean {
        val p = q.points
        for (i in 0 until 4) {
            val a = p[(i + 3) % 4]; val b = p[i]; val c = p[(i + 1) % 4]
            val v1x = a.x - b.x; val v1y = a.y - b.y; val v2x = c.x - b.x; val v2y = c.y - b.y
            val cos = (v1x * v2x + v1y * v2y) / (hypot(v1x, v1y) * hypot(v2x, v2y) + 1e-9)
            val deg = Math.toDegrees(acos(cos.coerceIn(-1.0, 1.0)))
            if (deg < 50 || deg > 130) return false
        }
        return true
    }

    private fun median(m: Mat): Double {
        val hist = IntArray(256)
        val data = ByteArray((m.total() * m.channels()).toInt())
        m.get(0, 0, data)
        for (b in data) hist[b.toInt() and 0xFF]++
        val half = data.size / 2
        var acc = 0
        for (i in 0 until 256) { acc += hist[i]; if (acc >= half) return i.toDouble() }
        return 128.0
    }
}
