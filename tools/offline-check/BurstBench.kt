// Synthetic benchmark for BurstDenoiser (not a unit test; prints numbers for A/B between versions).
// Uses only the stable API: BurstDenoiser.Frame and BurstDenoiser.denoise(frames).
import ru.revvl.cameralab.BurstDenoiser
import java.util.Random
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private fun grey(v: Int): Int { val c = v.coerceIn(0, 255); return (0xFF shl 24) or (c shl 16) or (c shl 8) or c }
private fun lum(p: Int) = (p ushr 8) and 255

/** Smooth texture with edges, values ~[30, 220]. */
private fun scene(w: Int, h: Int, ox: Int = 0, oy: Int = 0): IntArray = IntArray(w * h) { i ->
    val x = i % w + ox; val y = i / w + oy
    val v = 125 + 45 * sin(x / 13.0) * sin(y / 17.0) + 30 * sin((x + 2 * y) / 7.0) +
        (if ((x / 40 + y / 40) % 2 == 0) 20 else -20)
    grey(v.toInt())
}

private fun noisy(clean: IntArray, sigma: Double, rnd: Random) =
    IntArray(clean.size) { grey(lum(clean[it]) + (rnd.nextGaussian() * sigma).toInt()) }

private fun mse(a: IntArray, b: IntArray, w: Int, h: Int, margin: Int = 40): Double {
    var s = 0.0; var n = 0
    for (y in margin until h - margin) for (x in margin until w - margin) {
        val d = lum(a[y * w + x]) - lum(b[y * w + x]); s += d * d; n++
    }
    return s / n
}

private fun psnr(m: Double) = 10 * log10(255.0 * 255.0 / max(m, 1e-9))

fun main() {
    val rnd = Random(7)
    val w = 480; val h = 360

    // 1. Static scene, independent noise sigma=6.
    run {
        val clean = scene(w, h)
        val frames = List(3) { BurstDenoiser.Frame(w, h, noisy(clean, 6.0, rnd)) }
        val r = BurstDenoiser.denoise(frames)
        val before = mse(frames[1].argb, clean, w, h); val after = mse(r.frame.argb, clean, w, h)
        println("static-noise: PSNR ref %.2f dB -> out %.2f dB (MSE ratio %.3f)".format(psnr(before), psnr(after), after / before))
    }
    // 2. Large shift (30, -12): frame0 sees the scene moved.
    run {
        val clean = scene(w, h)
        val shifted = scene(w, h, 30, -12)
        val frames = listOf(BurstDenoiser.Frame(w, h, noisy(shifted, 6.0, rnd)),
            BurstDenoiser.Frame(w, h, noisy(clean, 6.0, rnd)), BurstDenoiser.Frame(w, h, noisy(clean, 6.0, rnd)))
        val r = BurstDenoiser.denoise(frames)
        val before = mse(frames[1].argb, clean, w, h); val after = mse(r.frame.argb, clean, w, h)
        println("shift(30,-12): estimated ${r.shifts[0]}, used ${r.usedFrames}, MSE ratio %.3f".format(after / before))
    }
    // 3. Low-contrast moving square (+14 levels) only in frame0, no noise.
    run {
        val base = IntArray(w * h) { grey(100) }
        val moving = base.clone(); for (y in 96 until 160) for (x in 96 until 160) moving[y * w + x] = grey(114)
        val r = BurstDenoiser.denoise(listOf(BurstDenoiser.Frame(w, h, moving),
            BurstDenoiser.Frame(w, h, base), BurstDenoiser.Frame(w, h, base.clone())))
        var ghost = 0; for (y in 96 until 160) for (x in 96 until 160) ghost = max(ghost, abs(lum(r.frame.argb[y * w + x]) - 100))
        println("low-contrast ghost: max %d levels (0 = none)".format(ghost))
    }
    // 4. Exposure drift: frame2 is 12%% brighter in linear light.
    run {
        val clean = scene(w, h)
        fun lin(v: Int) = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        fun gam(l: Double) = (if (l <= 0.0031308) l * 12.92 else 1.055 * l.pow(1 / 2.4) - 0.055) * 255
        val bright = IntArray(w * h) { grey(gam(lin(lum(clean[it])) * 1.12).toInt()) }
        val frames = listOf(BurstDenoiser.Frame(w, h, noisy(clean, 6.0, rnd)),
            BurstDenoiser.Frame(w, h, noisy(clean, 6.0, rnd)), BurstDenoiser.Frame(w, h, noisy(bright, 6.0, rnd)))
        val r = BurstDenoiser.denoise(frames)
        var shift = 0.0; for (i in clean.indices) shift += lum(r.frame.argb[i]) - lum(clean[i])
        val before = mse(frames[1].argb, clean, w, h); val after = mse(r.frame.argb, clean, w, h)
        println("exposure-drift: used ${r.usedFrames}, mean brightness bias %.2f levels, MSE ratio %.3f".format(shift / clean.size, after / before))
    }
    // 5. Speed at the real working size.
    run {
        val W = 2304; val H = 1728
        val clean = scene(W, H)
        val frames = List(3) { BurstDenoiser.Frame(W, H, noisy(clean, 5.0, rnd)) }
        BurstDenoiser.denoise(frames) // warm-up
        val t = System.nanoTime(); BurstDenoiser.denoise(frames)
        println("speed 2304x1728 (desktop JVM): %d ms".format((System.nanoTime() - t) / 1_000_000))
    }
}
