package ru.scanner291.core.quality.capture

import ru.scanner291.core.common.geometry.PolygonOps
import ru.scanner291.core.common.geometry.Quad
import kotlin.math.hypot

/** Оценки качества кадра, считаются на уменьшенном сером кадре (≈ 320–640 px). */
object FrameQuality {
    /** Дисперсия лапласиана — мера резкости. Больше — резче. */
    fun laplacianVariance(gray: ByteArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 0.0
        var sum = 0.0; var sumSq = 0.0; var n = 0
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val c = gray[row + x].toInt() and 0xFF
                val l = (gray[row + x - 1].toInt() and 0xFF) + (gray[row + x + 1].toInt() and 0xFF) +
                    (gray[row - w + x].toInt() and 0xFF) + (gray[row + w + x].toInt() and 0xFF) - 4 * c
                sum += l; sumSq += l.toDouble() * l; n++
            }
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    /** Доля пересвеченных пикселей (блик) в прямоугольнике. */
    fun glareFraction(gray: ByteArray, w: Int, h: Int, x0: Int = 0, y0: Int = 0, x1: Int = w, y1: Int = h, threshold: Int = 250): Double {
        var cnt = 0; var tot = 0
        for (y in y0.coerceAtLeast(0) until y1.coerceAtMost(h)) for (x in x0.coerceAtLeast(0) until x1.coerceAtMost(w)) {
            tot++; if ((gray[y * w + x].toInt() and 0xFF) >= threshold) cnt++
        }
        return if (tot == 0) 0.0 else cnt.toDouble() / tot
    }
}

sealed interface CaptureState {
    data object Searching : CaptureState
    data class Aligning(val progress: Float) : CaptureState
    data class Blocked(val reason: Reason) : CaptureState
    data object Ready : CaptureState
    /** После съёмки ждём, пока документ сменят — защита от случайных дублей. */
    data object Cooldown : CaptureState

    enum class Reason { BLURRY, GLARE, TOO_SMALL, NOT_CONVEX }
}

/**
 * Автозатвор: документ должен быть найден, достаточно крупным, неподвижным [holdMs] мс,
 * резким и без бликов. После съёмки — пауза до смены сцены.
 */
class AutoCaptureStabilizer(
    private val frameW: Int,
    private val frameH: Int,
    private val holdMs: Long = 700,
    private val maxDriftFraction: Double = 0.012,
    private val minAreaFraction: Double = 0.18,
    private val minSharpness: Double = 60.0,
    private val maxGlare: Double = 0.015,
    /** IoU с последним снятым документом, ниже которого считаем, что положили новый лист. */
    private val newSceneIou: Double = 0.6,
    /** Сколько кадров подряд без документа сбрасывает паузу. */
    private val lostFramesToReset: Int = 8,
) {
    private val diag = hypot(frameW.toDouble(), frameH.toDouble())
    private var anchor: Quad? = null
    private var stableSince: Long = -1
    private var lastCaptured: Quad? = null
    private var lostFrames = 0
    var state: CaptureState = CaptureState.Searching; private set

    fun onFrame(quad: Quad?, sharpness: Double, glare: Double, timeMs: Long): CaptureState {
        state = compute(quad, sharpness, glare, timeMs)
        return state
    }

    private fun compute(quad: Quad?, sharpness: Double, glare: Double, t: Long): CaptureState {
        if (quad == null) {
            anchor = null; stableSince = -1
            if (++lostFrames >= lostFramesToReset) lastCaptured = null
            return CaptureState.Searching
        }
        lostFrames = 0
        lastCaptured?.let { prev ->
            if (PolygonOps.iou(prev, quad) >= newSceneIou) return CaptureState.Cooldown
            lastCaptured = null
        }
        if (!quad.isConvex()) { reset(); return CaptureState.Blocked(CaptureState.Reason.NOT_CONVEX) }
        if (quad.area() < minAreaFraction * frameW * frameH) { reset(); return CaptureState.Blocked(CaptureState.Reason.TOO_SMALL) }

        val a = anchor
        if (a == null || a.maxCornerDrift(quad) > maxDriftFraction * diag) {
            anchor = quad; stableSince = t
            return CaptureState.Aligning(0f)
        }
        if (sharpness < minSharpness) return CaptureState.Blocked(CaptureState.Reason.BLURRY)
        if (glare > maxGlare) return CaptureState.Blocked(CaptureState.Reason.GLARE)
        val held = t - stableSince
        return if (held >= holdMs) CaptureState.Ready else CaptureState.Aligning((held.toFloat() / holdMs).coerceIn(0f, 1f))
    }

    /** Вызывается после успешной съёмки. */
    fun onCaptured(quad: Quad) { lastCaptured = quad; reset(); state = CaptureState.Cooldown }

    private fun reset() { anchor = null; stableSince = -1 }
}
