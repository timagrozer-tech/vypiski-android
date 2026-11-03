package ru.revvl.cameralab

import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Conservative, offline three-frame JPEG denoiser (v2, REVVL Camera Lab 0.3.3).
 *
 * This is NOT exposure-bracketed HDR, RAW fusion, Google's HDR+ or a stabilizer. It merges three
 * equally exposed, already ISP-processed JPEG frames and rejects anything that does not match the
 * middle (reference) frame, so moving subjects keep the reference look instead of smearing.
 *
 * Pipeline:
 *  1. Luma planes + coarse-to-fine pyramid registration of a global integer shift (up to
 *     [MAX_SHIFT] px; v1: 24 px full search at full resolution).
 *  2. Per-frame exposure gain in (approximately) linear light; frames outside [MIN_GAIN, MAX_GAIN]
 *     or with a large global error are not merged.
 *  3. Noise estimate of the reference (Immerkær) → thresholds scale with the actual noise.
 *  4. Per-tile ([TILE] px) motion rejection, combined with per-pixel soft weights, so partial
 *     matches of a moving object cannot leak through (v1: hard per-pixel threshold only).
 *  5. Weighted average in linear light via inverse sRGB (an approximation: the ISP tone curve is
 *     unknown), reference weighted ×2. Optional gamma-space merge for A/B.
 *
 * Memory: three input frames + three byte luma planes (+ small pyramids); the output may reuse the
 * reference buffer ([Options.reuseReferenceBuffer]). Cancellation is checked between rows.
 */
object BurstDenoiser {
    const val FRAME_COUNT = 3
    const val MAX_LONG_EDGE = 2304
    const val MAX_SHIFT = 64
    const val TILE = 16
    /** Mean absolute luma error after alignment above which a frame is not merged (as in v1). */
    const val MAX_GLOBAL_ERROR = 29.0
    const val MIN_GAIN = 0.8
    const val MAX_GAIN = 1.25
    /** Max per-channel difference to the reference for a pixel to be merged (as in v1). */
    const val CHANNEL_LIMIT = 42
    private const val REFERENCE_WEIGHT = 2f
    private const val LIN_STEPS = 16384
    private const val REGISTRATION_SAMPLES = 16000.0
    private const val NOISE_SAMPLES = 60000.0

    data class Frame(val width: Int, val height: Int, val argb: IntArray) {
        init {
            require(width > 0 && height > 0)
            require(argb.size == width * height)
        }
    }

    data class Options(
        /** true: average in linear light (default); false: average sRGB codes (v1 behaviour). */
        val linearMerge: Boolean = true,
        /** Write the result into the reference frame's array (saves one full buffer). */
        val reuseReferenceBuffer: Boolean = false,
        val isCancelled: () -> Boolean = { false }
    )

    /** Measured on the image itself; "noise" is high-frequency energy, so it also drops if detail is lost. */
    data class Stats(
        val noiseBefore: Double,
        val noiseAfter: Double,
        /** Per frame, share of tiles merged at full weight (reference = 1.0, rejected frame = 0.0). */
        val acceptedTileShare: List<Double>,
        val gains: List<Double>,
        val elapsedMs: Long,
        val linearMerge: Boolean
    ) {
        val noiseReduction: Double
            get() = if (noiseBefore <= 1e-9) 0.0 else (1.0 - noiseAfter / noiseBefore).coerceIn(-1.0, 1.0)

        companion object {
            val EMPTY = Stats(0.0, 0.0, emptyList(), emptyList(), 0, true)
        }
    }

    data class Result(
        val frame: Frame,
        val usedFrames: Int,
        val rejectedFrames: Int,
        val shifts: List<Pair<Int, Int>>,
        val stats: Stats = Stats.EMPTY
    )

    // ---- colour transfer -------------------------------------------------------------------

    fun srgbToLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    fun linearToSrgb(l: Float): Float =
        if (l <= 0.0031308f) l * 12.92f else 1.055f * l.pow(1f / 2.4f) - 0.055f

    private val TO_LINEAR = FloatArray(256) { srgbToLinear(it / 255f) }
    private val TO_GAMMA = IntArray(LIN_STEPS + 1) {
        (linearToSrgb(it / LIN_STEPS.toFloat()) * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    fun toLinear8(code: Int): Float = TO_LINEAR[code]
    fun toGamma8(linear: Float): Int = TO_GAMMA[(linear.coerceIn(0f, 1f) * LIN_STEPS + 0.5f).toInt()]

    // ---- public entry point ----------------------------------------------------------------

    /** Reference is the middle exposure, rather than a previously rendered composite. */
    fun denoise(frames: List<Frame>, options: Options = Options()): Result {
        val started = System.nanoTime()
        require(frames.size == FRAME_COUNT) { "Require exactly $FRAME_COUNT exposures" }
        val w = frames[1].width
        val h = frames[1].height
        require(frames.all { it.width == w && it.height == h }) { "All frames must have the same size" }
        val ref = frames[1].argb

        val maxShift = min(MAX_SHIFT, min(w, h) / 8)
        val levels = levelCount(w, h, maxShift)
        val refPyramid = pyramid(lumaPlane(ref, w, h), levels)
        cancelCheck(options)

        val offsets = ArrayList<Offset>(FRAME_COUNT)
        val planes = ArrayList<Plane>(FRAME_COUNT)
        for (i in frames.indices) {
            if (i == 1) {
                offsets.add(Offset(0, 0, 0.0)); planes.add(refPyramid[0]); continue
            }
            val pyr = pyramid(lumaPlane(frames[i].argb, w, h), levels)
            offsets.add(estimateOffset(refPyramid, pyr, maxShift))
            planes.add(pyr[0])
            cancelCheck(options)
        }

        val gains = DoubleArray(FRAME_COUNT) { 1.0 }
        val accepted = BooleanArray(FRAME_COUNT) { it == 1 }
        for (i in frames.indices) {
            if (i == 1) continue
            gains[i] = exposureGain(refPyramid[0], planes[i], offsets[i])
            accepted[i] = offsets[i].error <= MAX_GLOBAL_ERROR && gains[i] in MIN_GAIN..MAX_GAIN
        }
        // Per-frame remap of sRGB codes by the exposure gain (identity when gain == 1).
        val remap = Array(FRAME_COUNT) { i ->
            val g = gains[i].toFloat()
            IntArray(256) { v -> if (i == 1 || g == 1f) v else toGamma8(TO_LINEAR[v] * g) }
        }

        val sigma = estimateNoise(ref, w, h)
        val t1 = (3.0 * sigma).coerceIn(8.0, 20.0).toFloat()
        val t2 = t1 + (2.0 * sigma).coerceIn(8.0, 16.0).toFloat()
        val tileT = max(10.0, 3.5 * sigma).toFloat()

        val tilesX = (w + TILE - 1) / TILE
        val tilesY = (h + TILE - 1) / TILE
        val tileWeights = Array(FRAME_COUNT) { i ->
            if (i == 1 || !accepted[i]) null
            else tileWeights(planes[1], planes[i], offsets[i], remap[i], tilesX, tilesY, tileT)
        }
        cancelCheck(options)

        val aliasing = frames.indices.any { it != 1 && frames[it].argb === ref }
        val out = if (options.reuseReferenceBuffer && !aliasing) ref else IntArray(w * h)
        merge(frames, ref, out, w, h, offsets, accepted, remap, tileWeights, tilesX, tilesY, t1, t2, options)

        val share = frames.indices.map { i ->
            when {
                i == 1 -> 1.0
                !accepted[i] -> 0.0
                else -> tileWeights[i]!!.count { it >= 0.999f }.toDouble() / (tilesX * tilesY)
            }
        }
        val noiseAfter = estimateNoise(out, w, h)
        return Result(
            Frame(w, h, out),
            accepted.count { it },
            accepted.count { !it },
            offsets.map { it.dx to it.dy },
            Stats(sigma, noiseAfter, share, gains.toList(), (System.nanoTime() - started) / 1_000_000,
                options.linearMerge)
        )
    }

    // ---- merge -----------------------------------------------------------------------------

    private fun merge(
        frames: List<Frame>, ref: IntArray, out: IntArray, w: Int, h: Int,
        offsets: List<Offset>, accepted: BooleanArray, remap: Array<IntArray>,
        tileWeights: Array<FloatArray?>, tilesX: Int, tilesY: Int, t1: Float, t2: Float, options: Options
    ) {
        val linear = options.linearMerge
        val span = t2 - t1
        for (y in 0 until h) {
            if (y and 63 == 0) cancelCheck(options)
            val row = y * w
            for (x in 0 until w) {
                val idx = row + x
                val root = ref[idx] // read before a possible in-place write
                val r0 = (root ushr 16) and 255
                val g0 = (root ushr 8) and 255
                val b0 = root and 255
                val l0 = luma(r0, g0, b0)
                var wsum = REFERENCE_WEIGHT
                var sr: Float; var sg: Float; var sb: Float
                if (linear) {
                    sr = TO_LINEAR[r0] * REFERENCE_WEIGHT; sg = TO_LINEAR[g0] * REFERENCE_WEIGHT; sb = TO_LINEAR[b0] * REFERENCE_WEIGHT
                } else {
                    sr = r0 * REFERENCE_WEIGHT; sg = g0 * REFERENCE_WEIGHT; sb = b0 * REFERENCE_WEIGHT
                }
                for (i in frames.indices) {
                    if (i == 1 || !accepted[i]) continue
                    val sx = x + offsets[i].dx
                    val sy = y + offsets[i].dy
                    if (sx < 0 || sx >= w || sy < 0 || sy >= h) continue
                    val tw = tileWeightAt(tileWeights[i]!!, tilesX, tilesY, x, y)
                    if (tw <= 0f) continue
                    val sample = frames[i].argb[sy * w + sx]
                    val lut = remap[i]
                    val rr = lut[(sample ushr 16) and 255]
                    val gg = lut[(sample ushr 8) and 255]
                    val bb = lut[sample and 255]
                    if (max(abs(rr - r0), max(abs(gg - g0), abs(bb - b0))) > CHANNEL_LIMIT) continue
                    val d = abs(luma(rr, gg, bb) - l0).toFloat()
                    val pw = if (d <= t1) 1f else if (d >= t2) 0f else 1f - (d - t1) / span
                    val wgt = pw * tw
                    if (wgt <= 0f) continue
                    if (linear) {
                        sr += TO_LINEAR[rr] * wgt; sg += TO_LINEAR[gg] * wgt; sb += TO_LINEAR[bb] * wgt
                    } else {
                        sr += rr * wgt; sg += gg * wgt; sb += bb * wgt
                    }
                    wsum += wgt
                }
                out[idx] = if (linear) {
                    (0xFF shl 24) or (toGamma8(sr / wsum) shl 16) or (toGamma8(sg / wsum) shl 8) or toGamma8(sb / wsum)
                } else {
                    (0xFF shl 24) or (code(sr / wsum) shl 16) or (code(sg / wsum) shl 8) or code(sb / wsum)
                }
            }
        }
    }

    private fun code(v: Float): Int = (v + 0.5f).toInt().coerceIn(0, 255)

    /**
     * Weight 1 for tiles that match within [tileT] mean luma error, 0 above 2×[tileT].
     * Tiles without overlap get 0.
     */
    private fun tileWeights(
        ref: Plane, other: Plane, o: Offset, lut: IntArray, tilesX: Int, tilesY: Int, tileT: Float
    ): FloatArray {
        val result = FloatArray(tilesX * tilesY)
        for (ty in 0 until tilesY) for (tx in 0 until tilesX) {
            var sum = 0L
            var n = 0
            val y1 = min(ref.h, (ty + 1) * TILE)
            val x1 = min(ref.w, (tx + 1) * TILE)
            var y = ty * TILE
            while (y < y1) {
                val sy = y + o.dy
                if (sy in 0 until other.h) {
                    var x = tx * TILE
                    while (x < x1) {
                        val sx = x + o.dx
                        if (sx in 0 until other.w) {
                            sum += abs(lut[other.at(sx, sy)] - ref.at(x, y))
                            n++
                        }
                        x += 2
                    }
                }
                y += 2
            }
            if (n == 0) continue
            val err = sum.toFloat() / n
            result[ty * tilesX + tx] = when {
                err <= tileT -> 1f
                err >= 2 * tileT -> 0f
                else -> 1f - (err - tileT) / tileT
            }
        }
        return result
    }

    /**
     * min(nearest tile, bilinear between tile centres): a rejected tile never leaks into its own
     * area, while accepted tiles fade out smoothly next to rejected ones (no hard seams).
     */
    private fun tileWeightAt(t: FloatArray, tilesX: Int, tilesY: Int, x: Int, y: Int): Float {
        val nearest = t[(y / TILE) * tilesX + (x / TILE)]
        if (nearest <= 0f) return 0f
        val fx = ((x + 0.5f) / TILE - 0.5f).coerceIn(0f, (tilesX - 1).toFloat())
        val fy = ((y + 0.5f) / TILE - 0.5f).coerceIn(0f, (tilesY - 1).toFloat())
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = min(x0 + 1, tilesX - 1); val y1 = min(y0 + 1, tilesY - 1)
        val ax = fx - x0; val ay = fy - y0
        val top = t[y0 * tilesX + x0] * (1 - ax) + t[y0 * tilesX + x1] * ax
        val bottom = t[y1 * tilesX + x0] * (1 - ax) + t[y1 * tilesX + x1] * ax
        return min(nearest, top * (1 - ay) + bottom * ay)
    }

    // ---- registration ----------------------------------------------------------------------

    private class Plane(val w: Int, val h: Int, val px: ByteArray) {
        fun at(x: Int, y: Int): Int = px[y * w + x].toInt() and 255
    }

    private data class Offset(val dx: Int, val dy: Int, val error: Double)

    private fun lumaPlane(argb: IntArray, w: Int, h: Int): Plane =
        Plane(w, h, ByteArray(w * h) { i ->
            val p = argb[i]
            luma((p ushr 16) and 255, (p ushr 8) and 255, p and 255).toByte()
        })

    private fun downsample(p: Plane): Plane {
        val w2 = p.w / 2
        val h2 = p.h / 2
        return Plane(w2, h2, ByteArray(w2 * h2) { i ->
            val x = (i % w2) * 2
            val y = (i / w2) * 2
            ((p.at(x, y) + p.at(x + 1, y) + p.at(x, y + 1) + p.at(x + 1, y + 1) + 2) / 4).toByte()
        })
    }

    /** Coarsest level keeps ≥ 64 px on the short side and still needs a meaningful search. */
    private fun levelCount(w: Int, h: Int, maxShift: Int): Int {
        var l = 0
        while ((min(w, h) shr (l + 1)) >= 64 && (maxShift shr l) > 6) l++
        return l
    }

    private fun pyramid(base: Plane, levels: Int): List<Plane> {
        val list = ArrayList<Plane>(levels + 1)
        list.add(base)
        repeat(levels) { list.add(downsample(list.last())) }
        return list
    }

    private fun square(cx: Int, cy: Int, r: Int): List<Pair<Int, Int>> {
        val list = ArrayList<Pair<Int, Int>>((2 * r + 1) * (2 * r + 1))
        for (dy in cy - r..cy + r) for (dx in cx - r..cx + r) list.add(dx to dy)
        return list
    }

    /**
     * Full search at the coarsest level, ±2 refinement per finer level. At full resolution a
     * ±3 window around zero is also tested, because fine textures alias in the pyramid.
     * Convention: reference(x, y) ≈ frame(x + dx, y + dy).
     */
    private fun estimateOffset(ref: List<Plane>, frm: List<Plane>, maxShift: Int): Offset {
        if (maxShift < 2) return bestOf(ref[0], frm[0], listOf(0 to 0))
        val top = ref.size - 1
        val radius = ceil(maxShift / (1 shl top).toDouble()).toInt() + 1
        var best = bestOf(ref[top], frm[top], square(0, 0, radius))
        for (level in top - 1 downTo 0) {
            val candidates = LinkedHashSet(square(best.dx * 2, best.dy * 2, 2))
            if (level == 0) candidates.addAll(square(0, 0, 3))
            best = bestOf(ref[level], frm[level],
                candidates.filter { abs(it.first) <= maxShift && abs(it.second) <= maxShift })
        }
        if (top == 0) {
            best = bestOf(ref[0], frm[0], square(0, 0, maxShift))
        }
        return best
    }

    /** All candidates are scored on the same interior sample set, so errors are comparable. */
    private fun bestOf(a: Plane, b: Plane, candidates: Collection<Pair<Int, Int>>): Offset {
        val reach = candidates.maxOf { max(abs(it.first), abs(it.second)) }
        val mx = min(reach + 1, a.w / 3)
        val my = min(reach + 1, a.h / 3)
        val area = max(1L, (a.w - 2L * mx) * (a.h - 2L * my))
        val stride = max(1, sqrt(area / REGISTRATION_SAMPLES).toInt())
        var best: Offset? = null
        for ((dx, dy) in candidates) {
            val err = meanAbsDiff(a, b, dx, dy, mx, my, stride)
            val b0 = best
            if (b0 == null || err + 0.05 < b0.error ||
                (abs(err - b0.error) < 0.05 && dx * dx + dy * dy < b0.dx * b0.dx + b0.dy * b0.dy)) {
                best = Offset(dx, dy, err)
            }
        }
        return best!!
    }

    private fun meanAbsDiff(a: Plane, b: Plane, dx: Int, dy: Int, mx: Int, my: Int, stride: Int): Double {
        var sum = 0L
        var n = 0
        var y = my
        while (y < a.h - my) {
            val yy = y + dy
            if (yy in 0 until b.h) {
                var x = mx
                while (x < a.w - mx) {
                    val xx = x + dx
                    if (xx in 0 until b.w) {
                        sum += abs(a.at(x, y) - b.at(xx, yy))
                        n++
                    }
                    x += stride
                }
            }
            y += stride
        }
        return if (n == 0) Double.MAX_VALUE else sum.toDouble() / n
    }

    /** Ratio of mean linear luma (reference / frame) over the aligned overlap. */
    private fun exposureGain(ref: Plane, other: Plane, o: Offset): Double {
        val stride = max(1, sqrt(ref.w.toLong() * ref.h / REGISTRATION_SAMPLES).toInt())
        var a = 0.0
        var b = 0.0
        var y = 0
        while (y < ref.h) {
            val sy = y + o.dy
            if (sy in 0 until other.h) {
                var x = 0
                while (x < ref.w) {
                    val sx = x + o.dx
                    if (sx in 0 until other.w) {
                        a += TO_LINEAR[ref.at(x, y)]
                        b += TO_LINEAR[other.at(sx, sy)]
                    }
                    x += stride
                }
            }
            y += stride
        }
        return if (b < 1e-6 || a < 1e-6) 1.0 else a / b
    }

    // ---- noise -----------------------------------------------------------------------------

    /**
     * Immerkær (1996) fast noise variance estimate on luma, sampled: σ ≈ √(π/2)/6 · mean|L*N|,
     * N = [[1,-2,1],[-2,4,-2],[1,-2,1]]. Texture raises the estimate, so thresholds stay permissive
     * on detailed areas; flat areas get the tight [8, …] floor.
     */
    fun estimateNoise(argb: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 0.0
        val stride = max(1, sqrt((w - 2).toLong() * (h - 2) / NOISE_SAMPLES).toInt())
        var sum = 0L
        var n = 0L
        var y = 1
        while (y < h - 1) {
            var x = 1
            while (x < w - 1) {
                val c = l(argb, w, x - 1, y - 1) - 2 * l(argb, w, x, y - 1) + l(argb, w, x + 1, y - 1) -
                    2 * l(argb, w, x - 1, y) + 4 * l(argb, w, x, y) - 2 * l(argb, w, x + 1, y) +
                    l(argb, w, x - 1, y + 1) - 2 * l(argb, w, x, y + 1) + l(argb, w, x + 1, y + 1)
                sum += abs(c)
                n++
                x += stride
            }
            y += stride
        }
        return if (n == 0L) 0.0 else sqrt(Math.PI / 2) * sum / (6.0 * n)
    }

    private fun l(argb: IntArray, w: Int, x: Int, y: Int): Int {
        val p = argb[y * w + x]
        return luma((p ushr 16) and 255, (p ushr 8) and 255, p and 255)
    }

    private fun luma(r: Int, g: Int, b: Int): Int = (77 * r + 150 * g + 29 * b) ushr 8

    private fun cancelCheck(options: Options) {
        if (options.isCancelled()) throw CancellationException("Обработка серии отменена")
    }
}
