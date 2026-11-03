package ru.revvl.cameralab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraTextTest {
    @Test fun shutterPresetsAreRounded() {
        assertEquals("1/60 с", CameraText.formatShutter(16_666_667L))
        assertEquals("1/30 с", CameraText.formatShutter(33_333_333L))
        assertEquals("1/15 с", CameraText.formatShutter(66_666_667L))
        assertEquals("1/1000 с", CameraText.formatShutter(1_000_000L))
        assertEquals("1/4 с", CameraText.formatShutter(250_000_000L))
    }

    @Test fun longExposureUsesSeconds() {
        assertEquals("1 с", CameraText.formatShutter(1_000_000_000L))
        assertEquals("2.5 с", CameraText.formatShutter(2_500_000_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroExposureIsRejected() {
        CameraText.formatShutter(0)
    }

    @Test fun hardwareLevels() {
        assertEquals("LEGACY", CameraText.hardwareLevelName(2))
        assertEquals("LEVEL_3", CameraText.hardwareLevelName(3))
        assertEquals("?", CameraText.hardwareLevelName(null))
    }

    @Test fun recordingClock() {
        assertEquals("0:05", CameraText.recordingClock(5))
        assertEquals("1:00", CameraText.recordingClock(60))
        assertEquals("75:03", CameraText.recordingClock(4503))
        assertEquals("0:00", CameraText.recordingClock(-3))
    }

    @Test fun evLabelUsesDotAndSign() {
        assertEquals("EV +0.7", CameraText.evLabel(0.6667f))
        assertEquals("EV -1.0", CameraText.evLabel(-1f))
    }

    @Test fun russianFramePlural() {
        assertEquals("1 кадр", CameraText.frames(1))
        assertEquals("3 кадра", CameraText.frames(3))
        assertEquals("5 кадров", CameraText.frames(5))
        assertEquals("11 кадров", CameraText.frames(11))
    }

    @Test fun burstSummary() {
        assertEquals("СТЕК ×3 · 3 кадра · 2048×1536 · шум −38% · 0,4 с",
            CameraText.burstSummary(3, 2048, 1536, 0.381, 420))
        assertEquals("СТЕК ×3 · 1 кадр · 10×10 · шум ±0% · 0,0 с",
            CameraText.burstSummary(1, 10, 10, 0.0, 12))
    }

    @Test fun fileStampShape() {
        assertTrue(Regex("\\d{8}_\\d{6}_\\d{3}").matches(CameraText.fileStamp(0)))
    }
}

class ManualExposureTest {
    @Test fun shutterOptionsRespectSensorRange() {
        assertEquals(listOf(8_000_000L, 16_666_667L, 33_333_333L),
            ManualExposure.shutterOptions(5_000_000L, 40_000_000L))
        assertTrue(ManualExposure.shutterOptions(1L, 10L).isEmpty())
    }

    @Test fun initialIsoIsClamped() {
        assertEquals(100, ManualExposure.initialIso(null, 50, 3200))
        assertEquals(120, ManualExposure.initialIso(null, 120, 3200))
        assertEquals(3200, ManualExposure.initialIso(6400, 100, 3200))
    }

    @Test fun initialShutterPicksNearestOrMiddle() {
        val o = listOf(1L, 10L, 100L)
        assertEquals(10L, ManualExposure.initialShutter(null, o))
        assertEquals(100L, ManualExposure.initialShutter(80L, o))
    }

    @Test fun isoProgressRoundTripsWithinOneStep() {
        for (iso in listOf(100, 400, 1600, 3200)) {
            val p = ManualExposure.isoToProgress(iso, 100, 3200)
            val back = ManualExposure.progressToIso(p, 100, 3200)
            assertTrue(kotlin.math.abs(back - iso) <= 4)
        }
        assertEquals(100, ManualExposure.progressToIso(-5, 100, 3200))
        assertEquals(3200, ManualExposure.progressToIso(5000, 100, 3200))
    }

    @Test fun degenerateIsoRangeDoesNotDivideByZero() {
        assertEquals(0, ManualExposure.isoToProgress(100, 100, 100))
        assertEquals(100, ManualExposure.progressToIso(500, 100, 100))
    }
}

class SessionPlannerTest {
    // ImageCapture: OUTPUT_FORMAT_JPEG=0, ULTRA_HDR=1, RAW_JPEG=3; FLASH_MODE_AUTO=0, ON=1, OFF=2.
    private val c = CaptureConstants(jpeg = 0, rawJpeg = 3, flashOff = 2, flashAuto = 0, flashOn = 1)
    private fun req(
        format: Int = 0, supported: Set<Int> = setOf(0, 1, 3), extReq: Boolean = false,
        extAvail: Boolean = false, burst: Boolean = false, manual: Boolean = false,
        flash: Int = 0, hasFlash: Boolean = true
    ) = PhotoSessionRequest(format, supported, extReq, extAvail, burst, manual, flash, hasFlash)

    @Test fun plainJpegKeepsFlash() {
        val p = SessionPlanner.photo(req(), c)
        assertFalse(p.useExtension)
        assertEquals(0, p.outputFormat)
        assertEquals(0, p.captureFlashMode)
        assertEquals(100, p.jpegQuality)
        assertFalse(p.minimizeLatency)
    }

    @Test fun rawDisablesCaptureFlashButKeepsUiChoice() {
        val p = SessionPlanner.photo(req(format = 3, flash = 1), c)
        assertEquals(3, p.outputFormat)
        assertEquals(1, p.flashMode)
        assertEquals(2, p.captureFlashMode)
    }

    @Test fun extensionForcesJpeg() {
        val p = SessionPlanner.photo(req(format = 3, extReq = true, extAvail = true), c)
        assertTrue(p.useExtension)
        assertEquals(0, p.outputFormat)
    }

    @Test fun unavailableExtensionIsNotUsed() {
        assertFalse(SessionPlanner.photo(req(extReq = true, extAvail = false), c).useExtension)
    }

    @Test fun burstOverridesExtensionRawAndFlash() {
        val p = SessionPlanner.photo(req(format = 1, extReq = true, extAvail = true, burst = true, flash = 1), c)
        assertFalse(p.useExtension)
        assertEquals(0, p.outputFormat)
        assertEquals(2, p.captureFlashMode)
        assertTrue(p.minimizeLatency)
        assertEquals(96, p.jpegQuality)
    }

    @Test fun manualExposureTurnsCaptureFlashOff() {
        assertEquals(2, SessionPlanner.photo(req(manual = true, flash = 1), c).captureFlashMode)
    }

    @Test fun lensWithoutFlashForcesOff() {
        val p = SessionPlanner.photo(req(flash = 1, hasFlash = false), c)
        assertEquals(2, p.flashMode)
        assertEquals(2, p.captureFlashMode)
    }

    @Test fun unsupportedUltraHdrFallsBackToJpeg() {
        assertEquals(0, SessionPlanner.photo(req(format = 1, supported = setOf(0)), c).outputFormat)
    }
}

class CameraUiStateTest {
    private fun s(perm: Boolean = true, init: Boolean = false, cam: Boolean = true, rec: Boolean = false,
                  busy: Boolean = false, burst: Boolean = false, timer: Boolean = false) =
        CameraUiState.from(perm, init, cam, rec, busy, burst, timer)

    @Test fun readyWhenIdle() {
        assertEquals(CameraUiState.READY, s())
        assertTrue(s().canReconfigure)
        assertFalse(s().isBusy)
    }

    @Test fun busyStatesBlockReconfiguration() {
        assertEquals(CameraUiState.RECORDING, s(rec = true))
        assertEquals(CameraUiState.PROCESSING, s(busy = true, burst = true))
        assertEquals(CameraUiState.CAPTURING, s(busy = true))
        assertEquals(CameraUiState.COUNTDOWN, s(timer = true))
        for (st in listOf(s(rec = true), s(busy = true), s(timer = true))) {
            assertTrue(st.isBusy)
            assertFalse(st.canReconfigure)
        }
    }

    @Test fun busyWinsEvenIfCameraIsLost() {
        assertEquals(CameraUiState.RECORDING, s(cam = false, rec = true))
    }

    @Test fun setupStates() {
        assertEquals(CameraUiState.NO_PERMISSION, s(perm = false, cam = false))
        assertEquals(CameraUiState.INITIALIZING, s(init = true, cam = false))
        assertEquals(CameraUiState.ERROR, s(cam = false))
        assertFalse(s(perm = false).isBusy)
    }
}

class UiModelTest {
    @Test fun fitKeepsAspectInsideBox() {
        assertEquals(1080 to 1440, UiModel.fit(1080, 1700, UiModel.PHOTO_ASPECT))
        // 9:16 video on a 1080×1700 area is height-limited.
        assertEquals(956 to 1700, UiModel.fit(1080, 1700, UiModel.VIDEO_ASPECT))
        assertEquals(0 to 0, UiModel.fit(0, 100, 0.75f))
    }

    @Test fun shutterModes() {
        assertEquals(ShutterMode.PHOTO, UiModel.shutter(false, false, false, false, 0, 0, false).mode)
        assertEquals(ShutterMode.VIDEO, UiModel.shutter(true, false, false, false, 0, 0, false).mode)
        assertEquals(ShutterMode.RECORDING, UiModel.shutter(true, true, false, false, 0, 0, false).mode)
    }

    @Test fun burstProgressAndCancel() {
        val idle = UiModel.shutter(false, false, false, true, 0, 0, false)
        assertEquals(null, idle.progress)
        assertFalse(idle.cancellable)
        val frame2 = UiModel.shutter(false, false, true, true, 0, 2, false)
        assertTrue(kotlin.math.abs(frame2.progress!! - 2f / 3f) < 1e-6f)
        assertTrue(frame2.cancellable)
        assertEquals(1f, UiModel.shutter(false, false, true, true, 0, 3, true).progress)
        // A normal (non-burst) photo in flight is not cancellable from the UI.
        assertFalse(UiModel.shutter(false, false, true, false, 0, 0, false).cancellable)
    }

    @Test fun countdownLabel() {
        val m = UiModel.shutter(false, false, false, false, 3, 0, false)
        assertEquals("3", m.label)
        assertTrue(m.cancellable)
    }

    @Test fun iconRotationFollowsDevice() {
        assertEquals(0f, UiModel.iconRotation(0))
        assertEquals(90f, UiModel.iconRotation(1))
        assertEquals(180f, UiModel.iconRotation(2))
        assertEquals(-90f, UiModel.iconRotation(3))
    }

    @Test fun nearestAngleTurnsShortWay() {
        assertEquals(-180f, UiModel.nearestAngle(-90f, 180f))
        assertEquals(90f, UiModel.nearestAngle(0f, 90f))
        assertEquals(270f, UiModel.nearestAngle(180f, -90f))
    }
}

class LensUiTest {
    @Test fun nextLensWraps() {
        assertEquals("1", CameraPolicy.nextLens(listOf("0", "1"), "0"))
        assertEquals("0", CameraPolicy.nextLens(listOf("0", "1"), "1"))
        assertEquals("0", CameraPolicy.nextLens(listOf("0", "1"), "x"))
        assertEquals(null, CameraPolicy.nextLens(listOf("0"), "0"))
    }

    @Test fun linearZoomMatchesCameraXFormula() {
        assertTrue(CameraText.linearZoom(1f, 1f, 8f) == 0f)
        assertTrue(CameraText.linearZoom(8f, 1f, 8f) == 1f)
        // ratio 2 between 1 and 8: (1 - 0.5) / (1 - 0.125) = 0.5714
        assertTrue(kotlin.math.abs(CameraText.linearZoom(2f, 1f, 8f) - 0.5714f) < 1e-3f)
        assertTrue(CameraText.linearZoom(3f, 1f, 1f) == 0f)
    }

    @Test fun lensShortLabels() {
        assertEquals("СЕЛФИ", CameraText.lensShort(true, 3.0f, "1"))
        assertEquals("4.7 мм", CameraText.lensShort(false, 4.71f, "0"))
        assertEquals("ID 5", CameraText.lensShort(false, null, "5"))
    }
}
