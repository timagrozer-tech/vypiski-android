package ru.scanner291.core.common.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuadTransformsTest {
    private val q = Quad(Pt(0.1, 0.2), Pt(0.8, 0.15), Pt(0.85, 0.9), Pt(0.05, 0.95))

    private fun assertPt(e: Pt, a: Pt) { assertEquals(e.x, a.x, 1e-9); assertEquals(e.y, a.y, 1e-9) }

    @Test fun codecRoundTripAndRejectsGarbage() {
        val back = QuadCodec.decode(QuadCodec.encode(q))!!
        q.points.zip(back.points).forEach { (e, a) -> assertEquals(e.x, a.x, 1e-5); assertEquals(e.y, a.y, 1e-5) }
        assertNull(QuadCodec.decode(null)); assertNull(QuadCodec.decode("")); assertNull(QuadCodec.decode("1,2;3,4"))
        assertNull(QuadCodec.decode("0.1,0.1;0.2,0.2;0.3,0.3;5,5")) // вне 0..1
        assertNull(QuadCodec.decode("a,b;c,d;e,f;g,h"))
    }

    @Test fun rotationByCameraDegrees() {
        // Точка в левом верхнем углу сенсора при повороте 90° по часовой оказывается справа сверху.
        val r = QuadTransforms.rotateNormalized(Quad.fullFrame(1.0, 1.0), 90)
        assertPt(Pt(0.0, 0.0), r.tl); assertPt(Pt(1.0, 0.0), r.tr); assertPt(Pt(1.0, 1.0), r.br); assertPt(Pt(0.0, 1.0), r.bl)
        val p = QuadTransforms.rotateNormalized(Quad(Pt(0.0, 0.0), Pt(0.5, 0.0), Pt(0.5, 0.25), Pt(0.0, 0.25)), 90)
        assertPt(Pt(0.75, 0.0), p.tl); assertPt(Pt(1.0, 0.5), p.br)
        // 4 поворота по 90° возвращают исходник
        var x = q; repeat(4) { x = QuadTransforms.rotateNormalized(x, 90) }
        q.points.zip(x.points).forEach { (e, a) -> assertPt(e, a) }
        assertEquals(480 to 640, QuadTransforms.rotatedSize(640, 480, 90))
        assertEquals(640 to 480, QuadTransforms.rotatedSize(640, 480, 180))
    }

    @Test fun fillCenterAndFitMappings() {
        // Кадр 3:4 на экране 9:19.5 (1080×2340): заполняет по высоте, обрезка по бокам.
        val c = QuadTransforms.fillCenter(Pt(0.5, 0.5), 1200.0, 1600.0, 1080.0, 2340.0)
        assertPt(Pt(540.0, 1170.0), c)
        val top = QuadTransforms.fillCenter(Pt(0.0, 0.0), 1200.0, 1600.0, 1080.0, 2340.0)
        assertEquals(0.0, top.y, 1e-9); assertEquals(540 - 1200 * (2340.0 / 1600) / 2, top.x, 1e-9)
        // fit/unfit — взаимно обратны
        val v = QuadTransforms.fit(Pt(0.3, 0.7), 1200.0, 1600.0, 1080.0, 1500.0)
        val back = QuadTransforms.unfit(v, 1200.0, 1600.0, 1080.0, 1500.0)
        assertPt(Pt(0.3, 0.7), back)
        assertPt(Pt(0.0, 0.0), QuadTransforms.unfit(Pt(-50.0, -50.0), 1200.0, 1600.0, 1080.0, 1500.0)) // за пределами — к краю
    }
}
