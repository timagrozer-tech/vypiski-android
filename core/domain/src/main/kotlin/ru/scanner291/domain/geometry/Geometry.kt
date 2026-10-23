package ru.scanner291.domain.geometry

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

data class Pt(val x: Double, val y: Double) {
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    fun dist(o: Pt) = hypot(x - o.x, y - o.y)
}

/** Четырёхугольник документа: углы по часовой стрелке от левого верхнего (ось Y вниз). */
data class Quad(val tl: Pt, val tr: Pt, val br: Pt, val bl: Pt) {
    val points: List<Pt> get() = listOf(tl, tr, br, bl)

    /** Площадь (формула Гаусса). */
    fun area(): Double {
        val p = points
        var s = 0.0
        for (i in p.indices) { val a = p[i]; val b = p[(i + 1) % 4]; s += a.x * b.y - b.x * a.y }
        return abs(s) / 2
    }

    fun isConvex(): Boolean {
        val p = points
        var sign = 0
        for (i in 0 until 4) {
            val a = p[i]; val b = p[(i + 1) % 4]; val c = p[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 1e-9) 1 else if (cross < -1e-9) -1 else 0
            if (s == 0) return false
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /** Размер выпрямленного изображения: максимум противоположных сторон. */
    fun outputSize(): Pair<Int, Int> {
        val w = max(tl.dist(tr), bl.dist(br))
        val h = max(tl.dist(bl), tr.dist(br))
        return w.roundToInt() to h.roundToInt()
    }

    /** Размер с учётом известного соотношения сторон формата (A4 = 1/√2 и т.д.). */
    fun outputSize(aspectWOverH: Double?): Pair<Int, Int> {
        val (w, h) = outputSize()
        if (aspectWOverH == null) return w to h
        val portrait = h >= w
        val a = if (portrait) aspectWOverH else 1 / aspectWOverH
        return if (portrait) (h * a).roundToInt() to h else w to (w / a).roundToInt()
    }

    fun scaled(sx: Double, sy: Double) = Quad(
        Pt(tl.x * sx, tl.y * sy), Pt(tr.x * sx, tr.y * sy), Pt(br.x * sx, br.y * sy), Pt(bl.x * sx, bl.y * sy),
    )

    /** Максимальное смещение углов относительно другого четырёхугольника. */
    fun maxCornerDrift(o: Quad): Double = points.zip(o.points).maxOf { (a, b) -> a.dist(b) }

    companion object {
        /** Упорядочить 4 произвольные точки: TL, TR, BR, BL. Устойчиво к повороту до ±45°. */
        fun ordered(pts: List<Pt>): Quad {
            require(pts.size == 4) { "Нужно 4 точки" }
            val cx = pts.sumOf { it.x } / 4; val cy = pts.sumOf { it.y } / 4
            // Сортировка по углу вокруг центра (по часовой стрелке при оси Y вниз).
            val byAngle = pts.sortedBy { atan2(it.y - cy, it.x - cx) }
            // Начинаем с точки с минимальной суммой x+y (левая верхняя).
            val start = byAngle.indices.minBy { byAngle[it].x + byAngle[it].y }
            val r = (0 until 4).map { byAngle[(start + it) % 4] }
            return Quad(r[0], r[1], r[2], r[3])
        }

        fun fullFrame(w: Double, h: Double) = Quad(Pt(0.0, 0.0), Pt(w, 0.0), Pt(w, h), Pt(0.0, h))
    }
}

/** Площадь пересечения выпуклых многоугольников (Сазерленд — Ходжман). */
object PolygonOps {
    fun area(poly: List<Pt>): Double {
        var s = 0.0
        for (i in poly.indices) { val a = poly[i]; val b = poly[(i + 1) % poly.size]; s += a.x * b.y - b.x * a.y }
        return abs(s) / 2
    }

    private fun ensureCcwForClip(p: List<Pt>): List<Pt> {
        var s = 0.0
        for (i in p.indices) { val a = p[i]; val b = p[(i + 1) % p.size]; s += a.x * b.y - b.x * a.y }
        return if (s < 0) p.reversed() else p
    }

    fun clip(subject: List<Pt>, clipPoly: List<Pt>): List<Pt> {
        var output = subject
        val c = ensureCcwForClip(clipPoly)
        for (i in c.indices) {
            val a = c[i]; val b = c[(i + 1) % c.size]
            val input = output
            if (input.isEmpty()) break
            val res = mutableListOf<Pt>()
            fun inside(p: Pt) = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x) >= 0
            fun inter(p: Pt, q: Pt): Pt {
                val a1 = q.y - p.y; val b1 = p.x - q.x; val c1 = a1 * p.x + b1 * p.y
                val a2 = b.y - a.y; val b2 = a.x - b.x; val c2 = a2 * a.x + b2 * a.y
                val det = a1 * b2 - a2 * b1
                if (abs(det) < 1e-12) return q
                return Pt((b2 * c1 - b1 * c2) / det, (a1 * c2 - a2 * c1) / det)
            }
            for (j in input.indices) {
                val cur = input[j]; val prev = input[(j + input.size - 1) % input.size]
                if (inside(cur)) { if (!inside(prev)) res += inter(prev, cur); res += cur }
                else if (inside(prev)) res += inter(prev, cur)
            }
            output = res
        }
        return output
    }

    fun iou(a: Quad, b: Quad): Double {
        val inter = area(clip(a.points, b.points))
        val union = a.area() + b.area() - inter
        return if (union <= 0) 0.0 else inter / union
    }
}

/**
 * Гомография 3×3 (h33 = 1) по четырём парам точек — для переноса координат
 * (OCR-слова, закрашивания) между исходным снимком и выпрямленной страницей.
 */
class Homography private constructor(private val h: DoubleArray) {
    fun map(p: Pt): Pt {
        val w = h[6] * p.x + h[7] * p.y + 1.0
        return Pt((h[0] * p.x + h[1] * p.y + h[2]) / w, (h[3] * p.x + h[4] * p.y + h[5]) / w)
    }

    companion object {
        fun fromQuads(src: Quad, dst: Quad): Homography {
            val a = Array(8) { DoubleArray(9) }
            val s = src.points; val d = dst.points
            for (i in 0 until 4) {
                val (x, y) = s[i].x to s[i].y
                val (u, v) = d[i].x to d[i].y
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
            }
            // Гаусс с выбором главного элемента.
            for (col in 0 until 8) {
                val piv = (col until 8).maxBy { abs(a[it][col]) }
                require(abs(a[piv][col]) > 1e-12) { "Вырожденный четырёхугольник" }
                val t = a[col]; a[col] = a[piv]; a[piv] = t
                for (r in 0 until 8) if (r != col) {
                    val f = a[r][col] / a[col][col]
                    for (k in col..8) a[r][k] -= f * a[col][k]
                }
            }
            return Homography(DoubleArray(8) { a[it][8] / a[it][it] })
        }
    }
}
