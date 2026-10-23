package ru.scanner291.core.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.geometry.QuadTransforms
import ru.scanner291.core.imageprocessing.DocumentDetector
import ru.scanner291.core.imageprocessing.OpenCv
import ru.scanner291.core.quality.capture.FrameQuality
import kotlin.math.max

/**
 * Результат анализа кадра. [quad] — в нормализованных координатах «прямого» кадра (0..1);
 * [frameW]×[frameH] — размер прямого рабочего кадра в пикселях (для стабилизатора автосъёмки).
 */
data class FrameResult(
    val quad: Quad?,
    val sharpness: Double,
    val glare: Double,
    val frameW: Int,
    val frameH: Int,
    val timeMs: Long,
)

/**
 * Анализ кадров превью: яркостная плоскость YUV → уменьшенный серый кадр (≤ [workSide]) →
 * поиск документа (OpenCV) + резкость и блики (FrameQuality). Не чаще [minIntervalMs]:
 * экономия батареи, достаточная плавность рамки.
 */
class DocumentFrameAnalyzer(
    private val onResult: (FrameResult) -> Unit,
    private val workSide: Int = 360,
    private val minIntervalMs: Long = 66,
) : ImageAnalysis.Analyzer {
    private val detector = DocumentDetector(workSide = workSide)
    private var last = 0L

    override fun analyze(image: ImageProxy) {
        image.use { img ->
            val now = SystemClock.uptimeMillis()
            if (now - last < minIntervalMs || !OpenCv.ensureLoaded()) return
            last = now
            val plane = img.planes[0]
            val buf = plane.buffer
            val w = img.width; val h = img.height
            val step = max(1, max(w, h) / workSide)
            val sw = w / step; val sh = h / step
            val gray = ByteArray(sw * sh)
            val rowStride = plane.rowStride; val pixStride = plane.pixelStride
            for (y in 0 until sh) {
                val row = y * step * rowStride
                for (x in 0 until sw) gray[y * sw + x] = buf.get(row + x * step * pixStride)
            }
            val mat = Mat(sh, sw, CvType.CV_8UC1).apply { put(0, 0, gray) }
            val det = try { detector.detect(mat) } finally { mat.release() }
            val sharp = FrameQuality.laplacianVariance(gray, sw, sh)
            val glare = det?.quad?.let { q ->
                val xs = q.points.map { it.x }; val ys = q.points.map { it.y }
                FrameQuality.glareFraction(gray, sw, sh, xs.min().toInt(), ys.min().toInt(), xs.max().toInt(), ys.max().toInt())
            } ?: 0.0
            val rot = img.imageInfo.rotationDegrees
            val quad = det?.let { QuadTransforms.rotateNormalized(QuadTransforms.normalize(it.quad, sw.toDouble(), sh.toDouble()), rot) }
            val (uw, uh) = QuadTransforms.rotatedSize(sw, sh, rot)
            onResult(FrameResult(quad, sharp, glare, uw, uh, now))
        }
    }
}
