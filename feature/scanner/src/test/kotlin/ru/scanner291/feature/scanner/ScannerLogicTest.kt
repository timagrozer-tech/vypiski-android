package ru.scanner291.feature.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.scanner291.core.common.geometry.Pt
import ru.scanner291.core.quality.capture.CaptureState

class ScannerLogicTest {
    @Test fun hintsForEveryCaptureState() {
        assertEquals("Наведите камеру на документ", captureHint(CaptureState.Searching))
        assertEquals("Держите неподвижно…", captureHint(CaptureState.Aligning(0.4f)))
        assertEquals("Поднесите камеру ближе", captureHint(CaptureState.Blocked(CaptureState.Reason.TOO_SMALL)))
        assertEquals("Изображение размыто — держите неподвижно", captureHint(CaptureState.Blocked(CaptureState.Reason.BLURRY)))
        assertEquals("Документ виден не полностью", captureHint(CaptureState.Blocked(CaptureState.Reason.NOT_CONVEX)))
        assertEquals("Положите следующий лист", captureHint(CaptureState.Cooldown))
        CaptureState.Reason.entries.forEach { r -> assert(captureHint(CaptureState.Blocked(r)) != null) }
    }

    @Test fun nearestCornerWithinTouchRadius() {
        val view = listOf(Pt(10.0, 10.0), Pt(300.0, 12.0), Pt(305.0, 400.0), Pt(8.0, 395.0))
        assertEquals(1, nearestCorner(view, Pt(290.0, 30.0), 48.0))
        assertEquals(3, nearestCorner(view, Pt(20.0, 380.0), 48.0))
        assertEquals(-1, nearestCorner(view, Pt(150.0, 200.0), 48.0)) // середина листа — не угол
    }

    @Test fun enhanceLabels() {
        assertEquals("0", brightnessLabel(0f)); assertEquals("+25", brightnessLabel(0.25f)); assertEquals("−100", brightnessLabel(-1f))
        assertEquals("×1,50", contrastLabel(1.5f))
        assertEquals(null, filterNote(ru.scanner291.core.common.model.FilterType.TEXT_BOOST))
        assertTrue(filterNote(ru.scanner291.core.common.model.FilterType.PHOTO)!!.contains("Фото"))
    }

    @Test fun adjustLabels() {
        val tw = ru.scanner291.core.common.filters.Adjustments.SPECS.first { it.key == "tw" }
        val e = ru.scanner291.core.common.filters.Adjustments.SPECS.first { it.key == "e" }
        assertEquals("+1", adjustLabel(tw, 1f)); assertEquals("−1", adjustLabel(tw, -1f))
        assertEquals("+30", adjustLabel(e, 0.3f))
    }
}
