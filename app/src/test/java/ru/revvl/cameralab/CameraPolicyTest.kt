package ru.revvl.cameralab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPolicyTest {
    // Mirrors ImageCapture.FLASH_MODE_AUTO / ON / OFF.
    private val auto = 0
    private val on = 1
    private val off = 2

    @Test fun supportedRawIsChosenInNormalMode() {
        assertEquals(3, CameraPolicy.effectivePhotoFormat(3, setOf(0, 3), false, 0))
    }

    @Test fun unsupportedRawFallsBackToJpeg() {
        assertEquals(0, CameraPolicy.effectivePhotoFormat(3, setOf(0, 1), false, 0))
    }

    @Test fun oemExtensionForcesJpeg() {
        assertEquals(0, CameraPolicy.effectivePhotoFormat(3, setOf(0, 3), true, 0))
    }

    @Test fun timerOnlyAcceptsSupportedValues() {
        assertEquals(0, CameraPolicy.validTimer(-5))
        assertEquals(0, CameraPolicy.validTimer(900))
        assertEquals(10, CameraPolicy.validTimer(10))
    }

    @Test fun timerCycles() {
        assertEquals(3, CameraPolicy.nextTimer(0))
        assertEquals(10, CameraPolicy.nextTimer(3))
        assertEquals(0, CameraPolicy.nextTimer(10))
    }

    @Test fun rotationMapsQuadrants() {
        assertEquals(0, CameraPolicy.surfaceRotationFor(0))
        assertEquals(0, CameraPolicy.surfaceRotationFor(350))
        assertEquals(3, CameraPolicy.surfaceRotationFor(90))
        assertEquals(2, CameraPolicy.surfaceRotationFor(180))
        assertEquals(1, CameraPolicy.surfaceRotationFor(270))
        assertEquals(0, CameraPolicy.surfaceRotationFor(720))
    }

    @Test fun unknownOrientationIsIgnored() {
        assertNull(CameraPolicy.surfaceRotationFor(-1, 0))
    }

    @Test fun rotationHasHysteresisAtBoundary() {
        // 50° would be ROTATION_270 from scratch, but stays portrait when already portrait.
        assertEquals(3, CameraPolicy.surfaceRotationFor(50))
        assertEquals(0, CameraPolicy.surfaceRotationFor(50, 0))
        assertEquals(3, CameraPolicy.surfaceRotationFor(60, 0))
        // Same around 0/360 wrap for ROTATION_0 and ROTATION_90 (270°).
        assertEquals(0, CameraPolicy.surfaceRotationFor(310, 0))
        assertEquals(1, CameraPolicy.surfaceRotationFor(320, 1))
        assertEquals(0, CameraPolicy.surfaceRotationFor(330, 1))
    }

    @Test fun photoFlashCyclesOffAutoOn() {
        assertEquals(auto, CameraPolicy.nextFlashMode(off, false, true, off, auto, on))
        assertEquals(on, CameraPolicy.nextFlashMode(auto, false, true, off, auto, on))
        assertEquals(off, CameraPolicy.nextFlashMode(on, false, true, off, auto, on))
    }

    @Test fun videoTorchSkipsAuto() {
        assertEquals(on, CameraPolicy.nextFlashMode(off, true, true, off, auto, on))
        assertEquals(off, CameraPolicy.nextFlashMode(on, true, true, off, auto, on))
        assertEquals(on, CameraPolicy.nextFlashMode(auto, true, true, off, auto, on))
    }

    @Test fun noFlashUnitAlwaysOff() {
        assertEquals(off, CameraPolicy.nextFlashMode(off, false, false, off, auto, on))
        assertEquals(off, CameraPolicy.normalizeFlashMode(on, true, false, off, auto, on))
    }

    @Test fun flashNormalization() {
        assertEquals(off, CameraPolicy.normalizeFlashMode(auto, true, true, off, auto, on))
        assertEquals(auto, CameraPolicy.normalizeFlashMode(auto, false, true, off, auto, on))
        assertEquals(off, CameraPolicy.normalizeFlashMode(42, false, true, off, auto, on))
        assertEquals(on, CameraPolicy.normalizeFlashMode(on, true, true, off, auto, on))
    }

    @Test fun videoKeptForLifecycleStopButNotForFatalErrors() {
        val kept = setOf(4, 2, 9, 3) // SOURCE_INACTIVE, FILE_SIZE, DURATION, STORAGE
        assertTrue(CameraPolicy.isVideoKept(0, 0, kept, true))
        assertTrue(CameraPolicy.isVideoKept(4, 0, kept, true))
        assertFalse(CameraPolicy.isVideoKept(8, 0, kept, true)) // NO_VALID_DATA
        assertFalse(CameraPolicy.isVideoKept(0, 0, kept, false))
    }
}
