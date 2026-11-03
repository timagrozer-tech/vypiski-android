package ru.revvl.cameralab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

class BurstDenoiserTest {
    private fun grey(v: Int): Int = 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v

    @Test fun stableSceneAveragesSubtleNoise() {
        val w = 96; val h = 96
        val frames = listOf(96, 100, 104).map { v ->
            BurstDenoiser.Frame(w, h, IntArray(w * h) { grey(v) })
        }
        val result = BurstDenoiser.denoise(frames)
        assertEquals(3, result.usedFrames)
        assertEquals(grey(100), result.frame.argb[50 * w + 50])
    }

    @Test fun movingBrightObjectDoesNotGhostIntoReference() {
        val w = 96; val h = 96
        val dark = IntArray(w * h) { grey(40) }
        val moving = dark.clone().apply { this[48 * w + 48] = grey(240) }
        val result = BurstDenoiser.denoise(listOf(
            BurstDenoiser.Frame(w, h, moving),
            BurstDenoiser.Frame(w, h, dark),
            BurstDenoiser.Frame(w, h, dark)
        ))
        assertEquals(grey(40), result.frame.argb[48 * w + 48])
    }

    @Test fun shiftedPatternIsRegistered() {
        val w = 144; val h = 128
        fun patterned(x: Int, y: Int): Int = grey((x * 13 + y * 29 + (x xor y) * 7) and 255)
        val center = IntArray(w * h) { i -> patterned(i % w, i / w) }
        val shifted = IntArray(w * h) { i -> patterned((i % w - 3).coerceAtLeast(0), i / w) }
        val result = BurstDenoiser.denoise(listOf(
            BurstDenoiser.Frame(w, h, shifted),
            BurstDenoiser.Frame(w, h, center),
            BurstDenoiser.Frame(w, h, center)
        ))
        assertTrue(result.shifts[0].first in 2..4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedFrameSizesAreRejected() {
        BurstDenoiser.denoise(listOf(
            BurstDenoiser.Frame(2, 2, IntArray(4)),
            BurstDenoiser.Frame(3, 2, IntArray(6)),
            BurstDenoiser.Frame(2, 2, IntArray(4))
        ))
    }

    @Test fun tinyImageDoesNotCrashAndKeepsSize() {
        val w = 7; val h = 5
        val frames = listOf(50, 52, 54).map { v -> BurstDenoiser.Frame(w, h, IntArray(w * h) { grey(v) }) }
        val result = BurstDenoiser.denoise(frames)
        assertEquals(w, result.frame.width)
        assertEquals(h, result.frame.height)
        assertEquals(0 to 0, result.shifts[0])
    }

    @Test fun unrelatedFrameIsRejectedGlobally() {
        val w = 96; val h = 96
        val flat = IntArray(w * h) { grey(120) }
        // Strong checkerboard: average luma difference far above the acceptance limit.
        val other = IntArray(w * h) { i -> if (((i % w) / 2 + (i / w) / 2) % 2 == 0) grey(0) else grey(255) }
        val result = BurstDenoiser.denoise(listOf(
            BurstDenoiser.Frame(w, h, other),
            BurstDenoiser.Frame(w, h, flat),
            BurstDenoiser.Frame(w, h, flat)
        ))
        assertEquals(1, result.rejectedFrames)
        assertEquals(grey(120), result.frame.argb[40 * w + 40])
    }

    @Test fun saturatedHighlightsStayInRange() {
        val w = 64; val h = 64
        val frames = listOf(255, 255, 250).map { v -> BurstDenoiser.Frame(w, h, IntArray(w * h) { grey(v) }) }
        val px = BurstDenoiser.denoise(frames).frame.argb[32 * w + 32]
        assertEquals(0xFF, px ushr 24)
        assertTrue(((px ushr 16) and 255) in 250..255)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTwoFrameInput() {
        BurstDenoiser.denoise(listOf(
            BurstDenoiser.Frame(2, 2, IntArray(4)),
            BurstDenoiser.Frame(2, 2, IntArray(4))
        ))
    }

    // ---- v2 (0.3.3) -------------------------------------------------------------------

    private fun lum(p: Int) = (p ushr 8) and 255
    private fun clampGrey(v: Int) = grey(v.coerceIn(0, 255))

    /** Smooth texture with edges; [ox], [oy] move the scene. */
    private fun scene(w: Int, h: Int, ox: Int = 0, oy: Int = 0) = IntArray(w * h) { i ->
        val x = i % w + ox; val y = i / w + oy
        clampGrey((125 + 45 * sin(x / 13.0) * sin(y / 17.0) + 30 * sin((x + 2 * y) / 7.0) +
            (if ((x / 40 + y / 40) % 2 == 0) 20 else -20)).toInt())
    }

    private fun noisy(clean: IntArray, sigma: Double, rnd: Random) =
        IntArray(clean.size) { clampGrey(lum(clean[it]) + (rnd.nextGaussian() * sigma).toInt()) }

    private fun mse(a: IntArray, b: IntArray, w: Int, h: Int, m: Int = 40): Double {
        var s = 0.0; var n = 0
        for (y in m until h - m) for (x in m until w - m) {
            val d = lum(a[y * w + x]) - lum(b[y * w + x]); s += d * d; n++
        }
        return s / n
    }

    @Test fun srgbLutsRoundTripEveryCode() {
        for (v in 0..255) assertEquals(v, BurstDenoiser.toGamma8(BurstDenoiser.toLinear8(v)))
    }

    @Test fun largeShiftIsRegisteredByPyramid() {
        val w = 480; val h = 360
        val r = BurstDenoiser.denoise(listOf(
            BurstDenoiser.Frame(w, h, scene(w, h, 30, -12)),
            BurstDenoiser.Frame(w, h, scene(w, h)),
            BurstDenoiser.Frame(w, h, scene(w, h))
        ))
        // reference(x, y) == frame0(x - 30, y + 12)
        assertEquals(-30 to 12, r.shifts[0])
        assertEquals(3, r.usedFrames)
    }

    @Test fun staticNoiseIsReduced() {
        val w = 320; val h = 240; val rnd = Random(3)
        val clean = scene(w, h)
        val frames = List(3) { BurstDenoiser.Frame(w, h, noisy(clean, 6.0, rnd)) }
        val r = BurstDenoiser.denoise(frames)
        val ratio = mse(r.frame.argb, clean, w, h) / mse(frames[1].argb, clean, w, h)
        assertTrue("MSE ratio $ratio", ratio < 0.5)
        assertTrue(r.stats.noiseReduction > 0.2)
    }

    @Test fun lowContrastMovingObjectBarelyGhosts() {
        val w = 256; val h = 192
        val base = IntArray(w * h) { grey(100) }
        val moving = base.clone()
        for (y in 64 until 128) for (x in 64 until 128) moving[y * w + x] = grey(114)
        val r = BurstDenoiser.denoise(listOf(
            BurstDenoiser.Frame(w, h, moving), BurstDenoiser.Frame(w, h, base), BurstDenoiser.Frame(w, h, base.clone())))
        var ghost = 0
        for (y in 64 until 128) for (x in 64 until 128) ghost = max(ghost, abs(lum(r.frame.argb[y * w + x]) - 100))
        assertTrue("ghost $ghost", ghost <= 1) // v1: 3 levels (fully averaged below its 23-level limit)
    }

    @Test fun smallExposureDriftIsCompensated() {
        val w = 320; val h = 240; val rnd = Random(5)
        val clean = scene(w, h)
        val brighter = IntArray(clean.size) {
            clampGrey(BurstDenoiser.toGamma8(BurstDenoiser.toLinear8(lum(clean[it])) * 1.12f))
        }
        val r = BurstDenoiser.denoise(listOf(
            BurstDenoiser.Frame(w, h, noisy(clean, 4.0, rnd)), BurstDenoiser.Frame(w, h, noisy(clean, 4.0, rnd)),
            BurstDenoiser.Frame(w, h, noisy(brighter, 4.0, rnd))))
        assertEquals(3, r.usedFrames)
        assertTrue("gain ${r.stats.gains[2]}", r.stats.gains[2] in 0.85..0.93)
        var bias = 0.0
        for (i in clean.indices) bias += lum(r.frame.argb[i]) - lum(clean[i])
        assertTrue(abs(bias / clean.size) < 0.5)
    }

    @Test fun largeExposureChangeRejectsFrame() {
        val w = 160; val h = 120
        val clean = scene(w, h)
        val dark = IntArray(clean.size) { clampGrey(lum(clean[it]) / 2) }
        val r = BurstDenoiser.denoise(listOf(
            BurstDenoiser.Frame(w, h, dark), BurstDenoiser.Frame(w, h, clean), BurstDenoiser.Frame(w, h, clean.clone())))
        assertEquals(1, r.rejectedFrames)
        assertEquals(0.0, r.stats.acceptedTileShare[0], 0.0)
    }

    @Test fun gammaMergeOptionKeepsV1Average() {
        val w = 96; val h = 96
        val frames = listOf(96, 100, 104).map { v -> BurstDenoiser.Frame(w, h, IntArray(w * h) { grey(v) }) }
        val r = BurstDenoiser.denoise(frames, BurstDenoiser.Options(linearMerge = false))
        assertEquals(grey(100), r.frame.argb[50 * w + 50])
        assertFalse(r.stats.linearMerge)
    }

    @Test(expected = CancellationException::class)
    fun cancellationStopsProcessing() {
        val w = 64; val h = 64
        BurstDenoiser.denoise(List(3) { BurstDenoiser.Frame(w, h, IntArray(w * h) { grey(80) }) },
            BurstDenoiser.Options(isCancelled = { true }))
    }

    @Test fun referenceBufferIsReusedOnlyWithoutAliasing() {
        val w = 32; val h = 32
        val a = IntArray(w * h) { grey(60) }; val b = IntArray(w * h) { grey(62) }; val c = IntArray(w * h) { grey(64) }
        val r = BurstDenoiser.denoise(listOf(BurstDenoiser.Frame(w, h, a), BurstDenoiser.Frame(w, h, b),
            BurstDenoiser.Frame(w, h, c)), BurstDenoiser.Options(reuseReferenceBuffer = true))
        assertTrue(r.frame.argb === b)
        val shared = IntArray(w * h) { grey(62) }
        val r2 = BurstDenoiser.denoise(listOf(BurstDenoiser.Frame(w, h, a), BurstDenoiser.Frame(w, h, shared),
            BurstDenoiser.Frame(w, h, shared)), BurstDenoiser.Options(reuseReferenceBuffer = true))
        assertFalse(r2.frame.argb === shared)
    }

    @Test fun statsAreReported() {
        val w = 64; val h = 64
        val r = BurstDenoiser.denoise(List(3) { BurstDenoiser.Frame(w, h, IntArray(w * h) { grey(90) }) })
        assertEquals(3, r.stats.acceptedTileShare.size)
        assertEquals(1.0, r.stats.acceptedTileShare[1], 0.0)
        assertTrue(r.stats.elapsedMs >= 0)
        assertTrue(r.stats.linearMerge)
    }
}
