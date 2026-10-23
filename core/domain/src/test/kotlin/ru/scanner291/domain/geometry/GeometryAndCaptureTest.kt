package ru.scanner291.domain.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.scanner291.domain.capture.AutoCaptureStabilizer
import ru.scanner291.domain.capture.CaptureState
import ru.scanner291.domain.capture.FrameQuality

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

class AutoCaptureTest {
    private val doc = Quad(Pt(100.0, 100.0), Pt(500.0, 110.0), Pt(510.0, 700.0), Pt(90.0, 690.0))

    @Test fun readyAfterHoldWhenSharpAndNoGlare() {
        val s = AutoCaptureStabilizer(640, 800, holdMs = 700)
        var st: CaptureState = CaptureState.Searching
        for (t in 0..800 step 50) st = s.onFrame(doc, sharpness = 200.0, glare = 0.0, timeMs = t.toLong())
        assertEquals(CaptureState.Ready, st)
    }

    @Test fun movementResetsTimer() {
        val s = AutoCaptureStabilizer(640, 800, holdMs = 700)
        for (t in 0..600 step 50) s.onFrame(doc, 200.0, 0.0, t.toLong())
        val moved = doc.scaled(1.1, 1.1)
        val st = s.onFrame(moved, 200.0, 0.0, 650)
        assertTrue(st is CaptureState.Aligning)
        assertTrue(s.onFrame(moved, 200.0, 0.0, 900) is CaptureState.Aligning)
    }

    @Test fun blurAndGlareBlock() {
        val s = AutoCaptureStabilizer(640, 800)
        s.onFrame(doc, 10.0, 0.0, 0)
        assertEquals(CaptureState.Blocked(CaptureState.Reason.BLURRY), s.onFrame(doc, 10.0, 0.0, 100))
        assertEquals(CaptureState.Blocked(CaptureState.Reason.GLARE), s.onFrame(doc, 200.0, 0.2, 200))
    }

    @Test fun tooSmallBlocks() {
        val s = AutoCaptureStabilizer(640, 800)
        val tiny = Quad.fullFrame(50.0, 50.0)
        assertEquals(CaptureState.Blocked(CaptureState.Reason.TOO_SMALL), s.onFrame(tiny, 200.0, 0.0, 0))
    }

    @Test fun cooldownPreventsDuplicateUntilSceneChanges() {
        val s = AutoCaptureStabilizer(640, 800, holdMs = 100)
        s.onCaptured(doc)
        assertEquals(CaptureState.Cooldown, s.onFrame(doc, 200.0, 0.0, 0))
        assertEquals(CaptureState.Cooldown, s.onFrame(doc, 200.0, 0.0, 500))
        // Лист убрали — несколько кадров без документа.
        repeat(8) { s.onFrame(null, 0.0, 0.0, 600L + it) }
        s.onFrame(doc, 200.0, 0.0, 700)
        assertEquals(CaptureState.Ready, s.onFrame(doc, 200.0, 0.0, 900))
    }

    @Test fun sharpnessMetric() {
        val w = 64; val h = 64
        val checker = ByteArray(w * h) { i -> if (((i % w) / 4 + (i / w) / 4) % 2 == 0) 0 else 255.toByte() }
        val flat = ByteArray(w * h) { 128.toByte() }
        assertTrue(FrameQuality.laplacianVariance(checker, w, h) > 1000)
        assertEquals(0.0, FrameQuality.laplacianVariance(flat, w, h), 1e-9)
        val glare = ByteArray(w * h) { i -> if (i < w * h / 4) 255.toByte() else 100 }
        assertEquals(0.25, FrameQuality.glareFraction(glare, w, h), 1e-9)
    }
}
