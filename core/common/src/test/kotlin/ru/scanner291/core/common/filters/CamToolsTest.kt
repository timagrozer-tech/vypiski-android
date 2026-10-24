package ru.scanner291.core.common.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.scanner291.core.common.model.FILTER_ORDER
import ru.scanner291.core.common.model.FilterType
import ru.scanner291.core.common.model.PageRecipe
import ru.scanner291.core.common.render.RecipeCodec
import ru.scanner291.core.common.render.RenderMath
import ru.scanner291.core.common.render.keepsColor
import ru.scanner291.core.common.render.usesShadowRemoval
import kotlin.math.sqrt

/** Аналоги фильтров и инструментов CamScanner (2026-10-24): чистая математика, без OpenCV. */
class CamToolsTest {
    private val w = 120; private val h = 160
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun ch(p: Int, s: Int) = (p shr s) and 0xFF
    private fun isBlueInk(p: Int) = ch(p, 0) > ch(p, 16) + 40 && ch(p, 0) > ch(p, 8) + 20

    /** Бумага, чёрный печатный текст и синяя подпись поверх. */
    private fun page() = IntArray(w * h) { i -> val x = i % w; val y = i / w
        when {
            (y - 100) * (y - 100) / 4 + (x - 60) * (x - 60) / 9 in 80..120 -> argb(40, 60, 170)
            y % 12 in 4..6 && x in 10..100 -> argb(20, 20, 22)
            else -> argb(245, 244, 240)
        }
    }

    @Test fun inkRemovalErasesBlueAndKeepsBlackText() {
        val src = page()
        val out = src.copyOf().also { AdjustEngine.apply(it, w, h, Adjustments(inkRemoval = 1f)) }
        assertTrue("синие пиксели до", src.count(::isBlueInk) > 100)
        assertEquals("синие пиксели после", 0, out.count(::isBlueInk))
        // чёрный текст вне подписи не тронут
        var same = 0; var text = 0
        for (i in src.indices) if (ch(src[i], 16) == 20) { text++; if (out[i] == src[i]) same++ }
        assertTrue("текст сохранён: $same из $text", same > text * 0.9)
        // на странице без цветных чернил — ничего не меняется
        val plain = IntArray(w * h) { if (it % 7 == 0) argb(20, 20, 20) else argb(240, 240, 240) }
        assertEquals(plain.toList(), plain.copyOf().also { AdjustEngine.apply(it, w, h, Adjustments(inkRemoval = 1f)) }.toList())
    }

    @Test fun descreenSuppressesDotPattern() {
        // точечный растр периода 4 px (как серый 25 % в офсетной печати)
        val src = IntArray(w * h) { i -> val x = i % w; val y = i / w; if (x % 4 < 2 && y % 4 < 2) argb(120, 120, 120) else argb(230, 230, 230) }
        fun std(a: IntArray): Double { val v = a.map { ch(it, 8).toDouble() }; val m = v.average(); return sqrt(v.sumOf { (it - m) * (it - m) } / v.size) }
        val out = src.copyOf().also { AdjustEngine.apply(it, w, h, Adjustments(descreen = 1f)) }
        assertTrue("растр: ${std(src)} → ${std(out)}", std(out) < std(src) * 0.6)
        val flat = IntArray(w * h) { argb(200, 190, 180) }
        assertEquals(flat.toList(), flat.copyOf().also { AdjustEngine.apply(it, w, h, Adjustments(descreen = 1f)) }.toList())
    }

    @Test fun newFiltersMathOrderAndCodec() {
        val hist = LongArray(256).also { it[30] = 50; it[200] = 900; it[210] = 50 }
        val lut = RenderMath.lightenLut(hist)
        assertEquals(255, lut[210]); assertTrue(lut[100] > 100); assertTrue((1..255).all { lut[it] >= lut[it - 1] })
        assertEquals(240.0 / 200.0, RenderMath.paperLift(doubleArrayOf(150.0, 200.0, 180.0)), 1e-9)
        assertEquals(1.0, RenderMath.paperLift(doubleArrayOf(250.0, 245.0, 248.0)), 1e-9)
        assertEquals(listOf(FilterType.HYBRID, FilterType.BW, FilterType.GRAY, FilterType.COLOR, FilterType.ORIGINAL,
            FilterType.MAGIC_COLOR, FilterType.NO_SHADOW, FilterType.LIGHTEN), FILTER_ORDER.take(8))
        assertEquals(FilterType.entries.toSet(), FILTER_ORDER.toSet())
        for (f in listOf(FilterType.LIGHTEN, FilterType.NO_SHADOW, FilterType.MAGIC_COLOR)) assertTrue(f.keepsColor())
        assertTrue(FilterType.MAGIC_COLOR.usesShadowRemoval())
        assertTrue(!FilterType.LIGHTEN.usesShadowRemoval() && !FilterType.NO_SHADOW.usesShadowRemoval())
        val r = PageRecipe(filter = FilterType.MAGIC_COLOR, adjust = Adjustments(descreen = 0.5f, inkRemoval = 1f))
        val s = RecipeCodec.encode(r)
        assertTrue(s, s.contains("dm:0.50") && s.contains("ink:1.00"))
        assertEquals(r.normalized(), RecipeCodec.decode(s))
    }

    /** «Авто»: текстовая страница с синей печатью → «Документ», цветная графика → «Яркий цвет», цветной кадр → «Фото». */
    @Test fun autoFilterChoosesByContent() {
        assertEquals(FilterType.HYBRID, AutoFilter.choose(page(), w, h).filter)
        // тень на половине листа не делает документ фотографией
        val shadowed = page().mapIndexed { i, p -> if (i % w < w / 2) argb(ch(p, 16) * 6 / 10, ch(p, 8) * 6 / 10, ch(p, 0) * 6 / 10) else p }.toIntArray()
        assertEquals(FilterType.HYBRID, AutoFilter.choose(shadowed, w, h).filter)
        val graphics = page().mapIndexed { i, p -> val x = i % w; val y = i / w; if (y < 20 && x in 10..110) argb(30, 120, 200) else p }.toIntArray()
        assertEquals(FilterType.MAGIC_COLOR, AutoFilter.choose(graphics, w, h).filter)
        val photo = IntArray(w * h) { i -> val x = i % w; val y = i / w; argb(40 + x * 180 / w, 60 + y * 150 / h, 200 - x * 120 / w) }
        assertEquals(FilterType.PHOTO, AutoFilter.choose(photo, w, h).filter)
    }
}
