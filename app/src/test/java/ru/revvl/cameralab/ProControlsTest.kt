package ru.revvl.cameralab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class ProControlsTest {
    private val full = ProSupport(
        manualSensor = true, minFocusDiopters = 10f, focusCalibrated = true, aeLock = true, awbLock = true,
        awbModes = setOf(ProControls.AWB_OFF, ProControls.AWB_AUTO, ProControls.AWB_DAYLIGHT, ProControls.AWB_INCANDESCENT)
    )
    private val manual = ProSettings(iso = 400, exposureNs = 16_666_667L)

    @Test fun nothingRequestedGivesEmptyPlan() {
        assertTrue(ProControls.plan(ProSettings(), full, false, false, false).isEmpty)
    }

    @Test fun manualExposureInPhoto() {
        val p = ProControls.plan(manual, full, video = false, extension = false, burst = false)
        assertTrue(p.aeModeOff)
        assertEquals(400, p.iso)
        assertEquals(16_666_667L, p.exposureNs)
    }

    @Test fun manualExposureNeedsDeclaredSupport() {
        assertFalse(ProControls.plan(manual, ProSupport(), false, false, false).aeModeOff)
    }

    @Test fun manualExposureAndFocusNotInVideoOrBurst() {
        val s = manual.copy(focusDiopters = 2f)
        for (p in listOf(ProControls.plan(s, full, true, false, false), ProControls.plan(s, full, false, false, true))) {
            assertFalse(p.aeModeOff)
            assertFalse(p.afModeOff)
        }
    }

    @Test fun extensionsGetNoCamera2Options() {
        val s = manual.copy(awbMode = ProControls.AWB_DAYLIGHT, aeAwbLocked = true)
        assertTrue(ProControls.plan(s, full, false, extension = true, burst = false).isEmpty)
    }

    @Test fun aeLockIgnoredUnderManualExposure() {
        assertFalse(ProControls.plan(manual.copy(aeAwbLocked = true), full, false, false, false).aeLock)
        assertTrue(ProControls.plan(ProSettings(aeAwbLocked = true), full, false, false, false).aeLock)
    }

    @Test fun lockWorksInVideoAndBurstToo() {
        val locked = ProSettings(aeAwbLocked = true)
        assertTrue(ProControls.plan(locked, full, video = true, extension = false, burst = false).aeLock)
        val burst = ProControls.plan(locked, full, video = false, extension = false, burst = true)
        assertTrue(burst.aeLock)
        assertTrue(burst.awbLock)
    }

    @Test fun awbPresetOnlyIfDeclaredAndDisablesAwbLock() {
        val p = ProControls.plan(ProSettings(awbMode = ProControls.AWB_DAYLIGHT, aeAwbLocked = true), full, false, false, false)
        assertEquals(ProControls.AWB_DAYLIGHT, p.awbMode)
        assertFalse(p.awbLock)
        assertEquals(null, ProControls.plan(ProSettings(awbMode = ProControls.AWB_SHADE), full, false, false, false).awbMode)
        assertEquals(null, ProControls.plan(ProSettings(awbMode = ProControls.AWB_AUTO), full, false, false, false).awbMode)
    }

    @Test fun manualFocusClampedToLensRange() {
        val p = ProControls.plan(ProSettings(focusDiopters = 25f), full, false, false, false)
        assertTrue(p.afModeOff)
        assertEquals(10f, p.focusDiopters)
        assertFalse(ProControls.plan(ProSettings(focusDiopters = 2f), full.copy(minFocusDiopters = 0f), false, false, false).afModeOff)
    }

    @Test fun presetsListExcludesAutoAndOff() {
        assertEquals(listOf(ProControls.AWB_INCANDESCENT, ProControls.AWB_DAYLIGHT), full.whiteBalancePresets)
    }

    @Test fun histogramOnlyInPlainJpegPhoto() {
        assertTrue(ProControls.histogramAllowed(false, false, false, 0, 0))
        assertFalse(ProControls.histogramAllowed(true, false, false, 0, 0))
        assertFalse(ProControls.histogramAllowed(false, true, false, 0, 0))
        assertFalse(ProControls.histogramAllowed(false, false, true, 0, 0))
        assertFalse(ProControls.histogramAllowed(false, false, false, 3, 0))
    }

    @Test fun activeIndicator() {
        assertFalse(ProControls.isActive(ProSettings(aeAwbLocked = true)))
        assertFalse(ProControls.isActive(ProSettings(awbMode = ProControls.AWB_AUTO)))
        assertTrue(ProControls.isActive(ProSettings(awbMode = ProControls.AWB_DAYLIGHT)))
        assertTrue(ProControls.isActive(manual))
        assertTrue(ProControls.isActive(ProSettings(focusDiopters = 0f)))
    }

    @Test fun focusSliderMapping() {
        assertEquals(0f, ProControls.focusFromProgress(0, 10f))
        assertEquals(10f, ProControls.focusFromProgress(100, 10f))
        assertEquals(50, ProControls.progressFromFocus(5f, 10f))
        assertEquals(0, ProControls.progressFromFocus(5f, 0f))
    }

    @Test fun focusLabels() {
        assertEquals("∞", ProControls.focusLabel(0f, true))
        assertEquals("2.0 м", ProControls.focusLabel(0.5f, true))
        assertEquals("10 см", ProControls.focusLabel(10f, true))
        assertEquals("4.00 (усл. ед.)", ProControls.focusLabel(4f, false))
    }

    @Test fun lockLabelsDescribeWhatIsLocked() {
        assertEquals("AE/AF 🔒", ProControls.lockLabel(true, false))
        assertEquals("AF 🔒", ProControls.lockLabel(false, false))
        assertEquals("AE 🔒", ProControls.lockLabel(true, true))
    }

    @Test fun awbLabels() {
        assertEquals("Авто", ProControls.awbLabel(null))
        assertEquals("Дневной свет", ProControls.awbLabel(ProControls.AWB_DAYLIGHT))
    }
}

class HistogramTest {
    @Test fun binsAndClipping() {
        val w = 16; val h = 8; val stride = 20 // row padding must be skipped
        val buf = ByteBuffer.allocate(stride * h)
        for (y in 0 until h) for (x in 0 until w) buf.put(y * stride + x, (if (x < 8) 0 else 255).toByte())
        for (y in 0 until h) for (x in w until stride) buf.put(y * stride + x, 128.toByte()) // padding
        val r = Histogram.ofLuma(buf, w, h, stride, 1, 1)
        assertEquals(w * h, r.samples)
        assertEquals(64, r.bins[0])
        assertEquals(64, r.bins[Histogram.BINS - 1])
        assertEquals(0, r.bins[32]) // padding value 128 not counted
        assertTrue(kotlin.math.abs(r.shadowClip - 0.5) < 1e-9)
        assertTrue(kotlin.math.abs(r.highlightClip - 0.5) < 1e-9)
        assertEquals(64, r.peak)
    }

    @Test fun stepSubsamples() {
        val buf = ByteBuffer.allocate(64 * 64)
        val r = Histogram.ofLuma(buf, 64, 64, 64, 1, 4)
        assertEquals(16 * 16, r.samples)
    }

    @Test fun truncatedBufferDoesNotThrow() {
        val buf = ByteBuffer.allocate(10)
        val r = Histogram.ofLuma(buf, 8, 4, 8, 1, 1)
        assertTrue(r.samples <= 10)
    }
}
