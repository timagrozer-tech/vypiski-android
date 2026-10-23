package ru.scanner291.core.imageprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.scanner291.core.common.watermark.PlacementKind
import ru.scanner291.core.common.watermark.WatermarkSettings
import ru.scanner291.core.common.watermark.WatermarkSpec
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Этап 13: «В/ч 65384» слева внизу, повёрнут на 90° против часовой, серый с непрозрачностью 20 %,
 * не перекрывает чернила; при занятом поле — страница расширяется; повтор не наносится.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatermarkRendererTest {
    private val dpi = 100f
    private val w = 827; private val h = 1169       // A4 при 100 dpi

    /** Страница: текстовый блок в центре (левое поле 25 мм свободно). */
    private fun page(fullInk: Boolean = false): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); c.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.BLACK }
        if (fullInk) { var x = 0f; while (x < w) { c.drawRect(x, 0f, x + 3f, h.toFloat(), p); x += 6f } }
        else { var y = 120f; while (y < h - 120) { c.drawRect(120f, y, w - 100f, y + 8f, p); y += 30f } }
    }

    private fun changed(a: Int, b: Int) = abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b)) > 6

    @Test fun placedBottomLeftRotatedGrayTwentyPercentWithoutTouchingInk() {
        val src = page(); val before = src.copy(Bitmap.Config.ARGB_8888, false)
        val r = WatermarkRenderer.apply(src, dpi, WatermarkSettings(), alreadyWatermarked = false)
        val plan = r.placement!!
        assertEquals(PlacementKind.FREE_SPACE, plan.kind); assertEquals(w, r.bitmap.width)
        var minX = w; var maxX = 0; var minY = h; var maxY = 0; var n = 0; var darkest = 255
        for (y in 0 until h) for (x in 0 until w) {
            val o = before.getPixel(x, y); val v = r.bitmap.getPixel(x, y)
            if (!changed(o, v)) continue
            n++; minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
            assertEquals("знак не должен перекрывать чернила ($x,$y)", Color.WHITE, o)
            assertTrue("серый (без оттенка)", abs(Color.red(v) - Color.blue(v)) <= 6)
            darkest = minOf(darkest, Color.green(v))
        }
        assertTrue("знак нарисован", n > 50)
        assertTrue("слева: x=$minX..$maxX", maxX < w * 0.12)
        assertTrue("внизу: y=$minY..$maxY", minY > h * 0.5)
        assertTrue("вертикально (повёрнут): ${maxX - minX}×${maxY - minY}", (maxY - minY) > 3 * (maxX - minX))
        // 20 % серого 0x6B7280 на белом: самый тёмный пиксель ≈ 255 − 0.2·(255 − 114) ≈ 227
        assertTrue("непрозрачность ~20 %: $darkest", darkest in 215..240)
        assertTrue("исходник не изменён", before.sameAs(src))
    }

    /** Направление: совпадает с текстом, повёрнутым против часовой (а не по часовой). */
    @Test fun rotationIsCounterClockwise() {
        val r = WatermarkRenderer.apply(page(), dpi, WatermarkSettings(opacity = 1f), false)
        val p = r.placement!!
        val paint = WatermarkRenderer.paint(p.fontPx, 1f)
        val fm = paint.fontMetrics
        val tw = paint.measureText(WatermarkSpec.TEXT).toInt() + 2; val th = (fm.descent - fm.ascent).toInt() + 2
        val horiz = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(Color.WHITE); drawText(WatermarkSpec.TEXT, 0f, -fm.ascent, paint) } }
        fun rot(deg: Float) = Bitmap.createBitmap(horiz, 0, 0, tw, th, Matrix().apply { postRotate(deg) }, true)
        val crop = Bitmap.createBitmap(r.bitmap, p.box.left, p.box.top, p.box.width, p.box.height)
        fun corr(t: Bitmap): Double {
            val s = Bitmap.createScaledBitmap(t, crop.width, crop.height, true)
            var a = 0.0; var b = 0.0; var ab = 0.0; val n = crop.width * crop.height
            val xs = DoubleArray(n) { 255.0 - Color.green(crop.getPixel(it % crop.width, it / crop.width)) }
            val ys = DoubleArray(n) { 255.0 - Color.green(s.getPixel(it % crop.width, it / crop.width)) }
            val mx = xs.average(); val my = ys.average()
            for (i in 0 until n) { ab += (xs[i] - mx) * (ys[i] - my); a += (xs[i] - mx) * (xs[i] - mx); b += (ys[i] - my) * (ys[i] - my) }
            return ab / sqrt(a * b + 1e-9)
        }
        val ccw = corr(rot(-90f)); val cw = corr(rot(90f))
        assertTrue("против часовой: corr(ccw)=$ccw, corr(cw)=$cw", ccw > cw + 0.2)
    }

    @Test fun fullPageGetsLeftMarginInsteadOfOverlap() {
        val r = WatermarkRenderer.apply(page(fullInk = true), dpi, WatermarkSettings(), false)
        assertEquals(PlacementKind.PAGE_EXTENDED, r.placement!!.kind)
        assertTrue(r.bitmap.width > w)
    }

    @Test fun notAppliedTwiceOrWhenDisabled() {
        val src = page()
        val again = WatermarkRenderer.apply(src, dpi, WatermarkSettings(), alreadyWatermarked = true)
        assertNull(again.placement); assertTrue(again.bitmap === src)
        assertNull(WatermarkRenderer.apply(src, dpi, WatermarkSettings(enabled = false), false).placement)
        assertEquals("В/ч 65384", WatermarkSpec.TEXT)
    }
}
