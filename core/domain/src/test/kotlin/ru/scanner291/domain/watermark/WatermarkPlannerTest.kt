package ru.scanner291.domain.watermark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class WatermarkPlannerTest {
    // A4 при 150 dpi.
    private val w = 1240
    private val h = 1754
    private val dpi = 150f
    private val mm = dpi / 25.4f
    private val measurer = TextMeasurer { px -> (px * 0.55f * WatermarkSpec.TEXT.length) to (px * 1.2f) }
    private val planner = WatermarkPlanner()

    /** Маска с разрешением в 2 раза меньше страницы. */
    private class Canvas(val pw: Int, val ph: Int) {
        val mw = pw / 2; val mh = ph / 2
        val mask = BooleanArray(mw * mh)
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (y in y0 / 2 until (y1 / 2).coerceAtMost(mh)) for (x in x0 / 2 until (x1 / 2).coerceAtMost(mw)) mask[y * mw + x] = true
        }
        fun map() = GridInkMap(pw, ph, mw, mh, mask)
    }

    @Test fun exactTextAndOrientation() {
        assertEquals("В/ч 65384", WatermarkSpec.TEXT)
        assertNotEquals(WatermarkSpec.LEGACY_TEXT_FORBIDDEN, WatermarkSpec.TEXT)
        assertEquals(-90f, WatermarkSpec.ROTATION_DEGREES)
        assertEquals(0.20f, WatermarkSettings.DEFAULT.opacity)
    }

    @Test fun emptyPageBottomLeftWithMargin() {
        val c = Canvas(w, h)
        val p = planner.plan(w, h, dpi, c.map(), WatermarkSettings.DEFAULT, measurer)
        assertEquals(PlacementKind.FREE_SPACE, p.kind)
        val margin = (8 * mm).roundToInt()
        assertEquals(margin, p.box.left)
        assertEquals(h - margin, p.box.bottom)
        assertTrue("вертикальный: высота > ширины", p.box.height > p.box.width)
        assertTrue("в нижней половине", p.box.top > h / 2)
        assertEquals(0, p.extendLeftPx)
        assertEquals(w, p.outputWidth)
    }

    @Test fun shiftsUpAroundStampWithoutOverlap() {
        val c = Canvas(w, h)
        // Печать в нижнем левом углу.
        c.fill(0, h - 380, 420, h)
        // Обычный текст правее левого поля.
        for (y in 200 until h - 400 step 40) c.fill(200, y, 1100, y + 18)
        val map = c.map()
        val p = planner.plan(w, h, dpi, map, WatermarkSettings.DEFAULT, measurer)
        assertEquals(PlacementKind.SHIFTED_UP, p.kind)
        assertEquals(0.0, map.inkFraction(p.box.left, p.box.top, p.box.right, p.box.bottom), 1e-9)
        assertTrue(p.box.bottom <= h - 380)
        assertTrue(p.box.top >= h / 2)
    }

    @Test fun reducesSizeWhenGapIsSlightlyTooSmall() {
        val c = Canvas(w, h)
        val fullLen = measurer.measure(14f / 72f * dpi).first
        // Свободный промежуток в левом поле ≈ 85 % длины знака; выше и ниже — содержимое у самого края.
        val gapBottom = h - (12 * mm).toInt()
        val gapTop = gapBottom - (fullLen * 0.85f).toInt() - (4 * mm).toInt()
        c.fill(0, h / 2 - 50, 300, gapTop)
        c.fill(0, gapBottom, 300, h)
        val map = c.map()
        val p = planner.plan(w, h, dpi, map, WatermarkSettings.DEFAULT, measurer)
        assertEquals(PlacementKind.REDUCED_SIZE, p.kind)
        assertTrue(p.fontPx >= 14f / 72f * dpi * 0.7f - 0.01f)
        assertEquals(0.0, map.inkFraction(p.box.left, p.box.top, p.box.right, p.box.bottom), 1e-9)
    }

    @Test fun extendsPageWhenNoFreeSpace() {
        val c = Canvas(w, h)
        c.fill(10, 0, w, h) // документ без полей
        val p = planner.plan(w, h, dpi, c.map(), WatermarkSettings.DEFAULT, measurer)
        assertEquals(PlacementKind.PAGE_EXTENDED, p.kind)
        assertTrue(p.pageSizeChanged)
        assertEquals(w + p.extendLeftPx, p.outputWidth)
        assertEquals(h, p.outputHeight)
        assertTrue("знак целиком в добавленном поле", p.box.right <= p.extendLeftPx)
        assertTrue(p.box.top > h / 2)
    }

    @Test fun settingsAreClampedAndRestorable() {
        val s = WatermarkSettings(opacity = 5f, fontPt = 1f, marginMm = 100f).normalized()
        assertEquals(1f, s.opacity)
        assertEquals(6f, s.fontPt)
        assertEquals(30f, s.marginMm)
        assertEquals(WatermarkSettings(), WatermarkSettings.DEFAULT)
    }

    @Test fun ledgerPreventsDoubleWatermark() {
        val l = WatermarkLedger()
        l.markExported("abc")
        assertTrue(l.isAlreadyWatermarked("abc"))
    }
}
