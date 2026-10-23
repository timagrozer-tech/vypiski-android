package ru.scanner291.core.imageprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.scanner291.core.common.geometry.PolygonOps
import ru.scanner291.core.common.geometry.Pt
import ru.scanner291.core.common.geometry.Quad
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Точность поиска документа на СИНТЕТИЧЕСКОМ наборе (известные углы). Это не замена реальным фото:
 * набор с фотографиями пользователя (testdata/scans) — отдельная задача. Отчёт — в
 * detector-report.txt (CI выводит его в сводку запуска).
 */
@RunWith(AndroidJUnit4::class)
class DetectorAccuracyTest {
    private data class Case(val bitmap: Bitmap, val truth: Quad?, val kind: String)

    @Test fun syntheticDocuments() {
        val loaded = OpenCv.ensureLoaded()
        if (!loaded) writeReport("OpenCV не загрузился на эмуляторе")
        assertTrue("OpenCV не загрузился", loaded)
        try { run() } catch (e: Throwable) {
            writeReport("Исключение в тесте: ${e::class.java.simpleName}: ${e.message} @ ${e.stackTrace.take(4).joinToString(" < ")}")
            throw e
        }
    }

    private fun run() {
        val rnd = Random(291)
        val positives = (0 until 60).map { makeCase(rnd, it) }
        val negatives = (0 until 15).map { makeNegative(rnd) }

        val ious = positives.map { c ->
            val det = BitmapDetection.detectNormalized(c.bitmap)?.first
            c.kind to (det?.let { PolygonOps.iou(it, c.truth!!) } ?: 0.0)
        }
        val falsePositives = negatives.count { BitmapDetection.detectNormalized(it.bitmap) != null }
        val good = ious.count { it.second >= 0.9 }
        val byKind = ious.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> "%d/%d".format(v.count { it >= 0.9 }, v.size) }
        val mean = ious.map { it.second }.average()
        val report = buildString {
            append("Синтетический набор: ${ious.size} документов, IoU≥0.9: $good (${100 * good / ious.size}%), средний IoU %.3f; ".format(mean))
            append("по типам: $byKind; ложные срабатывания на ${negatives.size} кадрах без документа: $falsePositives")
        }
        writeReport(report)
        assertTrue(report, good >= (0.9 * ious.size).toInt())
    }

    private fun writeReport(text: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        runCatching { File(ctx.getExternalFilesDir(null), "detector-report.txt").writeText(text) }
        android.util.Log.i("DetectorAccuracy", text)
    }

    private fun paper(rnd: Random): Bitmap {
        val w = 420; val h = 594
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val tone = 235 + rnd.nextInt(21)
        c.drawColor(Color.rgb(tone, tone, tone - rnd.nextInt(10)))
        val p = Paint().apply { color = Color.rgb(70, 70, 80) }
        var y = 60f
        while (y < h - 80) {
            c.drawRect(40f, y, 40f + (200 + rnd.nextInt(140)).toFloat(), y + 7f, p); y += 22f + rnd.nextInt(8)
        }
        if (rnd.nextBoolean()) c.drawCircle(300f, h - 110f, 45f, Paint().apply { color = Color.argb(140, 40, 70, 180); style = Paint.Style.STROKE; strokeWidth = 5f })
        return b
    }

    private fun background(rnd: Random, w: Int, h: Int, kind: String): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val base = when (kind) {
            "низкий контраст" -> 175 + rnd.nextInt(30)
            else -> 30 + rnd.nextInt(90)
        }
        c.drawColor(Color.rgb(base, (base * 0.9).toInt(), (base * 0.8).toInt()))
        val p = Paint()
        repeat(if (kind == "пёстрый фон") 400 else 120) {
            val v = (base + rnd.nextInt(-40, 41)).coerceIn(0, 255)
            p.color = Color.rgb(v, v, (v * 0.9).toInt())
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
            c.drawRect(x, y, x + rnd.nextFloat() * 40, y + rnd.nextFloat() * 6, p)
        }
        return b
    }

    private fun makeCase(rnd: Random, i: Int): Case {
        val portrait = rnd.nextBoolean()
        val w = if (portrait) 600 else 800; val h = if (portrait) 800 else 600
        val kind = when (i % 5) { 0 -> "низкий контраст"; 1 -> "пёстрый фон"; 2 -> "тень"; 3 -> "размытие"; else -> "обычный" }
        val bmp = background(rnd, w, h, kind)
        val c = Canvas(bmp)
        // Лист: поворот ±25°, размер 45–80 % короткой стороны, перспективные искажения до 7 %.
        val s = min(w, h) * (0.45f + rnd.nextFloat() * 0.35f)
        val pw = s * 0.75f; val ph = s * 1.06f
        val cx = w / 2f + rnd.nextFloat() * w * 0.12f - w * 0.06f
        val cy = h / 2f + rnd.nextFloat() * h * 0.12f - h * 0.06f
        val ang = Math.toRadians((rnd.nextDouble() * 50 - 25))
        fun corner(dx: Float, dy: Float): Pt {
            val jx = (rnd.nextFloat() - 0.5f) * 0.14f * pw; val jy = (rnd.nextFloat() - 0.5f) * 0.14f * ph
            val x = dx + jx; val y = dy + jy
            return Pt(cx + x * cos(ang) - y * sin(ang), cy + x * sin(ang) + y * cos(ang))
        }
        var pts = listOf(corner(-pw / 2, -ph / 2), corner(pw / 2, -ph / 2), corner(pw / 2, ph / 2), corner(-pw / 2, ph / 2))
        // держим лист внутри кадра с полем ≥ 3 %
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }; val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
        val sx = ((w * 0.94) / (maxX - minX)).coerceAtMost(1.0); val sy = ((h * 0.94) / (maxY - minY)).coerceAtMost(1.0)
        val k = min(sx, sy)
        pts = pts.map { Pt(cx + (it.x - cx) * k, cy + (it.y - cy) * k) }
        val shiftX = when { pts.minOf { it.x } < w * 0.03 -> w * 0.03 - pts.minOf { it.x }; pts.maxOf { it.x } > w * 0.97 -> w * 0.97 - pts.maxOf { it.x }; else -> 0.0 }
        val shiftY = when { pts.minOf { it.y } < h * 0.03 -> h * 0.03 - pts.minOf { it.y }; pts.maxOf { it.y } > h * 0.97 -> h * 0.97 - pts.maxOf { it.y }; else -> 0.0 }
        pts = pts.map { Pt(it.x + shiftX, it.y + shiftY) }

        val paper = paper(rnd)
        val m = Matrix()
        val src = floatArrayOf(0f, 0f, paper.width.toFloat(), 0f, paper.width.toFloat(), paper.height.toFloat(), 0f, paper.height.toFloat())
        val dst = pts.flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }.toFloatArray()
        m.setPolyToPoly(src, 0, dst, 0, 4)
        c.drawBitmap(paper, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        if (kind == "тень") {
            val sh = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Color.argb(110, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP) }
            c.drawRect(0f, 0f, w * 0.6f, h.toFloat(), sh)
        }
        val out = if (kind == "размытие") {
            val small = Bitmap.createScaledBitmap(bmp, w / 4, h / 4, true)
            Bitmap.createScaledBitmap(small, w, h, true)
        } else bmp
        val truth = Quad.ordered(pts.map { Pt(it.x / w, it.y / h) })
        return Case(out, truth, kind)
    }

    private fun makeNegative(rnd: Random): Case {
        val w = 800; val h = 600
        return Case(background(rnd, w, h, if (rnd.nextBoolean()) "пёстрый фон" else "обычный"), null, "без документа")
    }
}
