package ru.scanner291.core.common.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private val q = Quad(Pt(10.0, 20.0), Pt(110.0, 15.0), Pt(120.0, 160.0), Pt(5.0, 150.0))

    @Test fun orderingFromShuffled() {
        val shuffled = listOf(q.br, q.tl, q.bl, q.tr)
        assertEquals(q, Quad.ordered(shuffled))
    }

    @Test fun convexity() {
        assertTrue(q.isConvex())
        assertFalse(Quad(Pt(0.0, 0.0), Pt(100.0, 0.0), Pt(10.0, 10.0), Pt(0.0, 100.0)).isConvex())
    }

    @Test fun iou() {
        val a = Quad.fullFrame(100.0, 100.0)
        val b = Quad(Pt(50.0, 0.0), Pt(150.0, 0.0), Pt(150.0, 100.0), Pt(50.0, 100.0))
        assertEquals(1.0, PolygonOps.iou(a, a), 1e-9)
        assertEquals(1.0 / 3.0, PolygonOps.iou(a, b), 1e-9)
        val c = Quad(Pt(200.0, 200.0), Pt(300.0, 200.0), Pt(300.0, 300.0), Pt(200.0, 300.0))
        assertEquals(0.0, PolygonOps.iou(a, c), 1e-9)
    }

    @Test fun homographyMapsCorners() {
        val (ow, oh) = q.outputSize()
        val dst = Quad.fullFrame(ow.toDouble(), oh.toDouble())
        val hm = Homography.fromQuads(q, dst)
        q.points.zip(dst.points).forEach { (s, d) ->
            val m = hm.map(s)
            assertEquals(d.x, m.x, 1e-6); assertEquals(d.y, m.y, 1e-6)
        }
    }

    @Test fun outputSizeWithA4Aspect() {
        val r = Quad.fullFrame(1000.0, 1500.0).outputSize(210.0 / 297.0)
        assertEquals(1500, r.second)
        assertEquals(1061, r.first)
    }
}
