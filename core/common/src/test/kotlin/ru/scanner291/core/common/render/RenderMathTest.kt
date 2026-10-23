package ru.scanner291.core.common.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.scanner291.core.common.geometry.Pt
import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.model.FilterType
import ru.scanner291.core.common.model.PageRecipe
import ru.scanner291.core.common.model.ScanMode

class RenderMathTest {
    private fun rect(w: Double, h: Double) = Quad(Pt(0.0, 0.0), Pt(w, 0.0), Pt(w, h), Pt(0.0, h))

    @Test fun outputSizeUsesLongestOppositeSides() {
        val q = Quad(Pt(10.0, 10.0), Pt(410.0, 20.0), Pt(400.0, 600.0), Pt(20.0, 590.0))
        val (w, h) = RenderMath.outputSize(q)
        assertEquals(210.0 / 297.0, w.toDouble() / h, 0.003)   // 400×580 (0.69) близко к A4 → приведено, площадь та же
        assertEquals(400.0 * 580, w.toDouble() * h, 2_000.0)
        // Далеко от A4 (квадрат) — пропорции по углам
        assertEquals(500 to 500, RenderMath.outputSize(rect(500.0, 500.0)))
    }

    @Test fun knownFormatSnapsAspectWithinTolerance() {
        val a4 = 210.0 / 297.0
        val (w, h) = RenderMath.outputSize(rect(700.0, 1000.0), a4)   // 0.70 против 0.707 — подгоняется
        assertEquals(a4, w.toDouble() / h, 0.002)
        assertEquals(700_000.0, w.toDouble() * h, 2_000.0)              // площадь сохраняется
        // Альбомная ориентация по углам — формат поворачивается
        val (lw, lh) = RenderMath.outputSize(rect(1000.0, 700.0), a4)
        assertEquals(1 / a4, lw.toDouble() / lh, 0.003)
        // Пропорции сильно отличаются (квадрат) — формату не верим, оставляем как есть
        assertEquals(500 to 500, RenderMath.outputSize(rect(500.0, 500.0), a4))
    }

    @Test fun rotationSwapsSides() {
        assertEquals(3 to 2, RenderMath.rotated(2 to 3, 90))
        assertEquals(2 to 3, RenderMath.rotated(2 to 3, 180))
        assertEquals(3 to 2, RenderMath.rotated(2 to 3, -90))
    }

    @Test fun luts() {
        val hist = LongArray(256).also { it[40] = 10; it[200] = 990 }
        val lv = RenderMath.levelsLut(hist, 0.5, 99.5)
        assertEquals(0, lv[40]); assertEquals(255, lv[200]); assertEquals(127, lv[120])
        // Пустая страница (один уровень) — без изменений
        assertEquals(IntArray(256) { it }.toList(), RenderMath.levelsLut(LongArray(256).also { it[250] = 100 }).toList())
        val g = RenderMath.gammaLut(1.8)
        assertEquals(0, g[0]); assertEquals(255, g[255]); assertTrue(g[128] < 128)
        assertNull(RenderMath.brightnessContrastLut(0f, 1f))
        val bc = RenderMath.brightnessContrastLut(0.1f, 2f)!!
        assertEquals(138, bc[128]); assertEquals(0, bc[0]); assertEquals(255, bc[255])
        assertEquals(120, RenderMath.percentile(LongArray(256).also { it[120] = 5 }, 50.0).coerceAtMost(120))
        assertEquals(0, RenderMath.blackPointLut(LongArray(256).also { it[30] = 1; it[250] = 99 })[30])
    }

    @Test fun oddKernels() {
        assertEquals(3, RenderMath.odd(2.6)); assertEquals(5, RenderMath.odd(4.0)); assertEquals(1, RenderMath.odd(0.1))
    }

    @Test fun recipeRoundTripAndDefaults() {
        val r = PageRecipe(filter = FilterType.BW, rotation = 270, brightness = 0.25f, contrast = 1.5f, removeShadows = false, denoise = true)
        val s = RecipeCodec.encode(r)
        assertEquals("v1;f=BW;r=270;b=0.25;c=1.50;s=0;n=1", s)
        assertEquals(r, RecipeCodec.decode(s))
        assertNull(RecipeCodec.decode(null)); assertNull(RecipeCodec.decode("v9;f=BW")); assertNull(RecipeCodec.decode("v1;f=NOPE;r=0;b=0;c=1;s=1;n=1"))
        // Значения вне диапазона приводятся к допустимым
        assertEquals(2f, RecipeCodec.decode("v1;f=GRAY;r=0;b=0;c=9;s=1;n=1")!!.contrast)
        assertEquals(FilterType.RECEIPT, RecipeCodec.default(ScanMode.RECEIPT).filter)
        assertTrue(RecipeCodec.default(ScanMode.A4).removeShadows)
    }

    @Test fun cacheKeyChangesWithCornersAndRecipe() {
        val a = PageRecipe(corners = rect(0.5, 0.5))
        assertFalse(RecipeCodec.cacheKey(a) == RecipeCodec.cacheKey(a.copy(corners = rect(0.6, 0.5))))
        assertFalse(RecipeCodec.cacheKey(a) == RecipeCodec.cacheKey(a.rotatedBy(90)))
        assertEquals(RecipeCodec.cacheKey(a), RecipeCodec.cacheKey(a.copy()))
    }

    @Test fun filterCapabilities() {
        assertFalse(FilterType.ORIGINAL.usesShadowRemoval()); assertFalse(FilterType.PHOTO.usesDenoise())
        assertTrue(FilterType.TEXT_BOOST.usesShadowRemoval()); assertFalse(FilterType.BW.keepsColor())
        assertTrue(FilterType.BOOK.keepsColor())
    }

    @Test fun hybridRecipeAndProfiles() {
        val r = PageRecipe(filter = FilterType.HYBRID, hybrid = ru.scanner291.core.common.model.HybridProfile.CONTRAST)
        val s = RecipeCodec.encode(r)
        assertTrue(s.endsWith(";h=CONTRAST"))
        assertEquals(r, RecipeCodec.decode(s))
        // Рецепт этапа 7 без h= читается, профиль по умолчанию
        assertEquals(ru.scanner291.core.common.model.HybridProfile.DOCUMENT, RecipeCodec.decode("v1;f=HYBRID;r=0;b=0.00;c=1.00;s=1;n=1")!!.hybrid)
        // Для других фильтров строка не меняется
        assertFalse(RecipeCodec.encode(PageRecipe(filter = FilterType.BW)).contains("h="))
        assertEquals(FilterType.HYBRID, RecipeCodec.default(ScanMode.ORDER).filter)
        assertEquals(3, ru.scanner291.core.common.model.HybridProfile.entries.size)
        assertTrue(FilterType.HYBRID.keepsColor() && FilterType.HYBRID.usesShadowRemoval())
    }

    @Test fun scannerLevelsWhitenBackgroundAndBlackenText() {
        val hist = LongArray(256).also { it[30] = 50; it[215] = 300; it[240] = 650 }   // текст, сгиб, фон
        val lut = RenderMath.scannerLut(hist, 0.88, 1.4)
        assertEquals(0, lut[30]); assertEquals(255, lut[240]); assertEquals(255, lut[230])
        assertTrue("сгиб почти белый: ${lut[215]}", lut[215] > 225)
        assertTrue("середина темнее линейной (гамма)", lut[120] < 255 * (120 - 30) / (224 - 30))
    }
}
