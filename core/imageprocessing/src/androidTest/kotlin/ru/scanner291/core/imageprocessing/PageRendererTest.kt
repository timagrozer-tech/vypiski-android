package ru.scanner291.core.imageprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.scanner291.core.common.geometry.Pt
import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.model.FilterType
import ru.scanner291.core.common.model.PageRecipe
import kotlin.math.sqrt

/**
 * Рендер по рецепту на устройстве (OpenCV): повторяемость, размер, тени, все фильтры.
 * Те же сцены, что в tools/processing-lab, но упрощённые (рисуются Canvas).
 */
@RunWith(AndroidJUnit4::class)
class PageRendererTest {
    private val w = 900; private val h = 1200
    private val quad = Quad(Pt(100.0, 120.0), Pt(820.0, 90.0), Pt(850.0, 1110.0), Pt(70.0, 1130.0))

    /** Лист на столе: тень слева направо (глубина ~50 %), строки «текста». */
    private fun scene(): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(90, 70, 55))
        val path = Path().apply { quad.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x.toFloat(), p.y.toFloat()) else lineTo(p.x.toFloat(), p.y.toFloat()) }; close() }
        val paper = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Color.rgb(120, 118, 112), Color.rgb(245, 243, 238), Shader.TileMode.CLAMP) }
        c.drawPath(path, paper)
        val ink = Paint().apply { color = Color.rgb(25, 25, 30) }
        var y = 220f
        while (y < 1000f) { c.drawRect(170f, y, 720f - (y % 130f), y + 10f, ink); y += 34f }
        return b
    }

    private fun gray(px: Int) = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000

    /** Разброс яркости бумаги: полоса между строками текста по всей ширине. */
    private fun paperStd(b: Bitmap): Double {
        val y0 = (b.height * 0.08).toInt()
        val v = (b.width / 10 until b.width * 9 / 10).map { gray(b.getPixel(it, y0)).toDouble() }
        val m = v.average(); return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
    }

    @Test fun sameRecipeGivesBitIdenticalResult() {
        val src = scene()
        val r = PageRecipe(corners = Quad(Pt(100.0 / w, 120.0 / h), Pt(820.0 / w, 90.0 / h), Pt(850.0 / w, 1110.0 / h), Pt(70.0 / w, 1130.0 / h)))
        val a = PageRenderer.render(src, r)!!; val b = PageRenderer.render(src, r)!!
        assertTrue("повторный рендер должен совпадать побитно", a.sameAs(b))
    }

    @Test fun perspectiveSizeRotationAndShadows() {
        val src = scene()
        val n = Quad(Pt(100.0 / w, 120.0 / h), Pt(820.0 / w, 90.0 / h), Pt(850.0 / w, 1110.0 / h), Pt(70.0 / w, 1130.0 / h))
        val orig = PageRenderer.render(src, PageRecipe(corners = n, filter = FilterType.ORIGINAL))!!
        val (ew, eh) = PageRenderer.outputSize(w, h, PageRecipe(corners = n), null)
        assertEquals(ew, orig.width); assertEquals(eh, orig.height)
        val rot = PageRenderer.render(src, PageRecipe(corners = n, filter = FilterType.ORIGINAL, rotation = 90))!!
        assertEquals(orig.height, rot.width); assertEquals(orig.width, rot.height)
        val gray = PageRenderer.render(src, PageRecipe(corners = n, filter = FilterType.GRAY))!!
        val before = paperStd(orig); val after = paperStd(gray)
        android.util.Log.i("PageRenderer", "разброс яркости бумаги: до ${"%.1f".format(before)}, после ${"%.1f".format(after)}")
        assertTrue("тень должна выравниваться: до $before, после $after", after < before / 4 && after < 8)
    }

    @Test fun everyFilterRendersAndBwIsBinary() {
        val src = scene()
        for (f in FilterType.entries) {
            val out = PageRenderer.render(src, PageRecipe(filter = f, brightness = 0.1f, contrast = 1.2f))
            assertTrue("фильтр $f", out != null && out.width == w && out.height == h)
            if (f == FilterType.BW || f == FilterType.RECEIPT) {
                val vals = (0 until 2000).map { gray(out!!.getPixel((it * 37) % w, (it * 53) % h)) }.toSet()
                assertTrue("$f: только чёрный и белый, а есть $vals", vals.all { it == 0 || it == 255 })
            }
        }
    }
}
