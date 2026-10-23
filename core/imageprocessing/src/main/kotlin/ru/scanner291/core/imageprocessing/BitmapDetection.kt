package ru.scanner291.core.imageprocessing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.geometry.QuadTransforms

/** Поиск документа на изображении (снимок, импорт). Возвращает углы в нормализованных координатах. */
object BitmapDetection {
    fun detectNormalized(bitmap: Bitmap, detector: DocumentDetector = DocumentDetector()): Pair<Quad, Double>? {
        if (!OpenCv.ensureLoaded()) return null
        val rgba = Mat(); val gray = Mat()
        try {
            val src = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            Utils.bitmapToMat(src, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val d = detector.detect(gray) ?: return null
            return QuadTransforms.normalize(d.quad, bitmap.width.toDouble(), bitmap.height.toDouble()) to d.score
        } finally {
            rgba.release(); gray.release()
        }
    }
}
