package ru.scanner291.core.common.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.scanner291.core.common.model.FilterType
import ru.scanner291.core.common.model.PageRecipe
import ru.scanner291.core.common.render.RecipeCodec

class PresetsTest {
    private val w = 120; private val h = 160

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Эталоны: документ (серый текст, неровный фон), цветное фото (градиенты), синие чернила на бумаге. */
    private val references: List<IntArray> = listOf(
        IntArray(w * h) { i -> val x = i % w; val y = i / w
            val ink = (y % 12 in 4..6) && x in 10..(100 - (y % 30))
            val v = if (ink) 40 + (x % 7) * 3 else 225 + (x * 20 / w); argb(v, v, v - 3) },
        IntArray(w * h) { i -> val x = i % w; val y = i / w; argb(40 + x * 180 / w, 60 + y * 150 / h, 200 - x * 120 / w) },
        IntArray(w * h) { i -> val x = i % w; val y = i / w
            val ink = (y % 16 in 5..7) || (x - 60) * (x - 60) + (y - 80) * (y - 80) in 900..1100
            if (ink) argb(50, 70, 170) else argb(238, 236, 228) },
    )

    private fun render(p: Preset, ref: IntArray): IntArray = ref.copyOf().also { AdjustEngine.apply(it, w, h, p.adjust) }

    @Test fun libraryHasAtLeast30PresetsAnd18ManualParameters() {
        assertTrue("пресетов ${Presets.ALL.size}", Presets.ALL.size >= 30)
        assertEquals(Presets.ALL.size, Presets.ALL.map { it.id }.toSet().size)
        assertEquals(Presets.ALL.size, Presets.ALL.map { it.title }.toSet().size)
        // 16 параметров Adjustments + яркость и контраст рецепта
        assertEquals(16, Adjustments.SPECS.size); assertEquals(Adjustments.COUNT, Adjustments.SPECS.size)
        assertEquals(18, Adjustments.SPECS.size + 2)
        assertTrue(FilterType.entries.all { f -> Presets.ALL.any { it.filter == f } })
    }

    /**
     * Различимость: два пресета с одинаковой основой (фильтр + профиль Hybrid) различаются только ручными
     * параметрами — средняя разница ≥ 2 уровней хотя бы на одном эталоне. Пресеты с разными основами
     * различаются самим фильтром (tools/processing-lab).
     */
    @Test fun presetsWithSameBaseAreDistinguishable() {
        val groups = Presets.ALL.groupBy { it.filter to (if (it.filter == FilterType.HYBRID) it.hybrid else null) }
        val report = StringBuilder()
        var minPair = Double.MAX_VALUE
        for ((_, ps) in groups) for (i in ps.indices) for (j in i + 1 until ps.size) {
            val d = references.maxOf { AdjustEngine.meanAbsDiff(render(ps[i], it), render(ps[j], it)) }
            minPair = minOf(minPair, d)
            if (d < Presets.MIN_DIFF) report.append("${ps[i].id} ≈ ${ps[j].id}: $d\n")
        }
        assertTrue("неразличимые пары:\n$report", report.isEmpty())
        println("Минимальная разница пары пресетов с общей основой: ${"%.2f".format(minPair)}")
    }

    @Test fun everyParameterChangesTheImage() {
        for (s in Adjustments.SPECS) {
            val a = Adjustments.NEUTRAL.with(s.key, if (s.max > 0) s.max else s.min)
            val d = references.maxOf { r -> AdjustEngine.meanAbsDiff(r, r.copyOf().also { AdjustEngine.apply(it, w, h, a) }) }
            assertTrue("${s.title} не меняет изображение ($d)", d >= 1.0)
        }
    }

    @Test fun reproducibleAndNeutralIsIdentity() {
        for (p in Presets.ALL) for (r in references) assertEquals(p.id, render(p, r).toList(), render(p, r).toList())
        val r = references[1]
        assertEquals(r.toList(), r.copyOf().also { AdjustEngine.apply(it, w, h, Adjustments.NEUTRAL) }.toList())
    }

    @Test fun codecsRoundTrip() {
        val a = Adjustments(exposure = 0.3f, saturation = -0.25f, textWeight = 1, clarity = 0.5f)
        assertEquals("e:0.30,sat:-0.25,cl:0.50,tw:1.00", a.encode())
        assertEquals(a, decodeAdjustments(a.encode()))
        assertEquals(Adjustments.NEUTRAL, decodeAdjustments("мусор,zz:1"))
        val r = Presets.byId("whiteboard")!!.applyTo(PageRecipe(rotation = 90))
        assertEquals(r, RecipeCodec.decode(RecipeCodec.encode(r)))
        assertEquals(90, r.rotation)
        assertTrue(Presets.byId("whiteboard")!!.matches(r))
        assertFalse(Presets.byId("color")!!.matches(r))
        // Рецепт этапа 7 без a=/p= не меняется
        assertEquals("v1;f=BW;r=0;b=0.00;c=1.00;s=1;n=1", RecipeCodec.encode(PageRecipe(filter = FilterType.BW)))
        val u = Preset.fromRecipe("u:1", "Мой | скан", r)
        val back = Preset.decodeUser(u.encodeUser())!!
        assertEquals("Мой   скан", back.title); assertEquals(r.adjust, back.adjust); assertEquals(PresetGroup.USER, back.group)
        assertTrue(back.matches(r))
    }

    @Test fun boxBlurKeepsMean() {
        val src = FloatArray(w * h) { (it % 17).toFloat() }
        val out = AdjustEngine.boxBlur(src, w, h, 2)
        assertEquals(src.average(), out.average(), 0.2)
    }
}
