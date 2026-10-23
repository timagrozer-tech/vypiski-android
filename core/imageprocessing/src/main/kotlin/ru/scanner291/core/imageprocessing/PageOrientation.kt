package ru.scanner291.core.imageprocessing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Ориентация страницы, шаг 1 (2026-10-23): идут ли строки текста горизонтально или вертикально — по дисперсии
 * профилей проекций (строки, слитые по горизонтали, дают «полосатый» профиль по высоте). Шаг 2 (0° или 180°)
 * решает OCR по уверенности (AutoOrient). Проверено в tools/processing-lab/lab_orient.py: 48/48.
 */
object PageOrientation {
    fun linesVertical(bitmap: Bitmap): Boolean? {
        if (!OpenCv.ensureLoaded()) return null
        val rgba = Mat(); val g = Mat(); val b = Mat(); val h = Mat(); val v = Mat()
        try {
            Utils.bitmapToMat(if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false), rgba)
            Imgproc.cvtColor(rgba, g, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.adaptiveThreshold(g, b, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 25, 15.0)
            Imgproc.dilate(b, h, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 1.0)))
            Imgproc.dilate(b, v, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, 9.0)))
            return variance(v, byRows = false) > variance(h, byRows = true)
        } finally { listOf(rgba, g, b, h, v).forEach { it.release() } }
    }

    private fun variance(m: Mat, byRows: Boolean): Double {
        val sums = Mat()
        Core.reduce(m, sums, if (byRows) 1 else 0, Core.REDUCE_SUM, org.opencv.core.CvType.CV_64F)
        val n = sums.total().toInt(); val arr = DoubleArray(n); sums.get(0, 0, arr); sums.release()
        val mean = arr.average(); return arr.sumOf { (it - mean) * (it - mean) } / n
    }
}
