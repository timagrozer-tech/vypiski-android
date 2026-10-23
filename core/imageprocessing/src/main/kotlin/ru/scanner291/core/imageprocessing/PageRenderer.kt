package ru.scanner291.core.imageprocessing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.geometry.QuadTransforms
import ru.scanner291.core.common.model.FilterType
import ru.scanner291.core.common.model.HybridProfile
import ru.scanner291.core.common.model.PageRecipe
import ru.scanner291.core.common.render.RenderMath
import ru.scanner291.core.common.render.RenderParams
import ru.scanner291.core.common.render.keepsColor
import ru.scanner291.core.common.render.usesDenoise
import ru.scanner291.core.common.render.usesShadowRemoval
import kotlin.math.max

/**
 * Рендер страницы по рецепту (этап 7): перспектива → поворот → шумоподавление → выравнивание освещения
 * (тени) → фильтр → яркость/контраст. Вход — прямое (по EXIF) изображение оригинала; оригинал не меняется,
 * результат всегда строится заново, поэтому любое изменение рецепта обратимо, а одинаковый рецепт даёт
 * побитно одинаковый результат (проверяет PageRendererTest).
 *
 * Алгоритмы и параметры подобраны и измерены в tools/processing-lab (Python-копия этого файла один к одному).
 */
object PageRenderer {

    /**
     * @param src прямое изображение оригинала (ARGB_8888 или любой формат — будет приведён)
     * @param recipe рецепт; [PageRecipe.corners] — нормализованные углы (null — весь кадр)
     * @param aspectWOverH соотношение сторон формата режима (A4 и т. п.), null — по углам
     * @return новый Bitmap ARGB_8888; null — если OpenCV не загрузился
     */
    fun render(src: Bitmap, recipe: PageRecipe, aspectWOverH: Double? = null): Bitmap? {
        if (!OpenCv.ensureLoaded()) return null
        val r = recipe.normalized()
        val mats = ArrayList<Mat>()
        fun Mat.track() = also { mats += it }
        try {
            val rgba = Mat().track()
            Utils.bitmapToMat(if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false), rgba)
            var img = Mat().track().also { Imgproc.cvtColor(rgba, it, Imgproc.COLOR_RGBA2RGB) }

            // 1. Перспектива
            val corners = r.corners
            if (corners != null && corners != QuadTransforms.FULL) {
                val px = QuadTransforms.denormalize(corners, img.cols().toDouble(), img.rows().toDouble())
                img = warp(img, px, aspectWOverH).track()
            }
            // 2. Поворот
            when (((r.rotation % 360) + 360) % 360) {
                90 -> img = Mat().track().also { Core.rotate(img, it, Core.ROTATE_90_CLOCKWISE) }
                180 -> img = Mat().track().also { Core.rotate(img, it, Core.ROTATE_180) }
                270 -> img = Mat().track().also { Core.rotate(img, it, Core.ROTATE_90_COUNTERCLOCKWISE) }
            }
            val f = r.filter
            if (f == FilterType.ORIGINAL) {
                applyLut(img, RenderMath.brightnessContrastLut(r.brightness, r.contrast))
                return toBitmap(img, mats)
            }
            if (!f.keepsColor()) img = Mat().track().also { Imgproc.cvtColor(img, it, Imgproc.COLOR_RGB2GRAY) }
            // 3. Шум (гаусс 3×3: лучше медианы сохраняет тонкие штрихи — см. лабораторию)
            val hp = r.hybrid
            val hybrid = f == FilterType.HYBRID
            if (r.denoise && f.usesDenoise() && (!hybrid || hp.denoise)) Imgproc.GaussianBlur(img, img, Size(3.0, 3.0), 0.0)
            // 4. Тени / неравномерное освещение — деление на оценку фона, по каждому каналу
            if (r.removeShadows && f.usesShadowRemoval() && (!hybrid || hp.shadows)) removeShadows(img)
            // 5. Фильтр
            when (f) {
                FilterType.PHOTO -> clahe(img)
                FilterType.COLOR, FilterType.BOOK -> {
                    applyLut(img, RenderMath.blackPointLut(lumaHist(img)))
                    if (f == FilterType.BOOK) applyLut(img, RenderMath.gammaLut(RenderParams.BOOK_GAMMA))
                }
                FilterType.GRAY -> applyLut(img, RenderMath.levelsLut(hist(img)))
                FilterType.TEXT_BOOST -> {
                    applyLut(img, RenderMath.levelsLut(hist(img)))
                    applyLut(img, RenderMath.gammaLut(RenderParams.TEXT_GAMMA))
                    unsharp(img)
                }
                FilterType.BW, FilterType.RECEIPT -> {
                    applyLut(img, RenderMath.levelsLut(hist(img)))
                    applyLut(img, RenderMath.brightnessContrastLut(r.brightness, r.contrast))
                    val block = RenderMath.odd(max(img.cols(), img.rows()) / 40.0).coerceAtLeast(3)
                    val c = if (f == FilterType.BW) RenderParams.BW_C else RenderParams.RECEIPT_C
                    Imgproc.adaptiveThreshold(img, img, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, block, c)
                    return toBitmap(img, mats)
                }
                FilterType.HYBRID -> img = hybrid(img, hp).track()
                FilterType.ORIGINAL -> Unit
            }
            // 6. Яркость и контраст пользователя
            applyLut(img, RenderMath.brightnessContrastLut(r.brightness, r.contrast))
            return toBitmap(img, mats)
        } finally {
            mats.forEach { it.release() }
        }
    }

    /**
     * Hybrid HD Pro (этап 8): маски «цветные чернила» (печать, подпись — по насыщенности) и «текст»
     * (адаптивный порог); текст — тёмный и резкий, фон — белый, цветные чернила — в своём цвете с усилением
     * насыщенности. Только детерминированные фильтры: ничего не дорисовывается.
     */
    private fun hybrid(rgb: Mat, p: HybridProfile): Mat {
        val tmp = ArrayList<Mat>()
        fun Mat.t() = also { tmp += it }
        try {
            // Маска цветных чернил
            val hsv = Mat().t(); Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            val color = Mat().t()
            Core.inRange(hsv, Scalar(0.0, p.satMin.toDouble(), 0.0), Scalar(180.0, 255.0, p.inkVMax.toDouble()), color)
            val k2 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0)).t()
            Imgproc.morphologyEx(color, color, Imgproc.MORPH_OPEN, k2)
            if (p.maskGrow > 0) {
                val s = 2.0 * p.maskGrow + 1
                Imgproc.dilate(color, color, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(s, s)).t())
            }
            // Маска текста (без цветных чернил)
            val gray = Mat().t(); Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            val text = Mat().t()
            val block = RenderMath.odd(max(gray.cols(), gray.rows()) / 40.0).coerceAtLeast(3)
            Imgproc.adaptiveThreshold(gray, text, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, block, RenderParams.BW_C)
            Imgproc.dilate(text, text, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)).t())
            text.setTo(Scalar(0.0), color)
            // Слои
            applyLut(gray, RenderMath.levelsLut(hist(gray)))
            val t = Mat().t()
            if (p.textBw) {
                Imgproc.threshold(gray, t, 159.0, 255.0, Imgproc.THRESH_BINARY)
                Imgproc.GaussianBlur(t, t, Size(3.0, 3.0), 0.0)
            } else {
                gray.copyTo(t)
                applyLut(t, RenderMath.gammaLut(p.textGamma))
                val b = Mat().t(); Imgproc.GaussianBlur(t, b, Size(0.0, 0.0), RenderParams.UNSHARP_SIGMA)
                Core.addWeighted(t, 1 + p.sharpen, b, -p.sharpen, 0.0, t)
            }
            // Фон: светлее порога — белый
            val bgMask = Mat().t()
            Imgproc.threshold(gray, bgMask, 255.0 * p.bgWhite - 1, 255.0, Imgproc.THRESH_BINARY)
            gray.setTo(Scalar(255.0), bgMask)
            val out = Mat(); Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2RGB)
            val tRgb = Mat().t(); Imgproc.cvtColor(t, tRgb, Imgproc.COLOR_GRAY2RGB)
            tRgb.copyTo(out, text)
            // Цветные чернила в своём цвете, насыщеннее
            val ch = ArrayList<Mat>(); Core.split(hsv, ch); tmp += ch
            ch[1].convertTo(ch[1], -1, p.colorBoost, 0.0)
            Core.merge(ch, hsv)
            val ci = Mat().t(); Imgproc.cvtColor(hsv, ci, Imgproc.COLOR_HSV2RGB)
            ci.copyTo(out, color)
            return out
        } finally {
            tmp.forEach { it.release() }
        }
    }

    private fun warp(img: Mat, q: Quad, aspect: Double?): Mat {
        val (w, h) = RenderMath.outputSize(q, aspect)
        val srcPts = MatOfPoint2f(*q.points.map { Point(it.x, it.y) }.toTypedArray())
        val dstPts = MatOfPoint2f(Point(0.0, 0.0), Point(w - 1.0, 0.0), Point(w - 1.0, h - 1.0), Point(0.0, h - 1.0))
        val m = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val out = Mat()
        Imgproc.warpPerspective(img, out, m, Size(w.toDouble(), h.toDouble()), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
        srcPts.release(); dstPts.release(); m.release()
        return out
    }

    /** Освещённый фон канала: закрытие (убирает текст) + размытие на копии со стороной 512, затем деление. */
    private fun removeShadows(img: Mat) {
        val channels = ArrayList<Mat>()
        Core.split(img, channels)
        for (ch in channels) {
            val bg = background(ch)
            Core.max(bg, Scalar(1.0), bg)
            Core.divide(ch, bg, ch, 255.0)
            bg.release()
        }
        Core.merge(channels, img)
        channels.forEach { it.release() }
    }

    private fun background(ch: Mat): Mat {
        val s = RenderParams.BACKGROUND_SIDE.toDouble() / max(ch.cols(), ch.rows())
        val small = Mat()
        Imgproc.resize(ch, small, Size(max(1.0, (ch.cols() * s).toInt().toDouble()), max(1.0, (ch.rows() * s).toInt().toDouble())), 0.0, 0.0, Imgproc.INTER_AREA)
        val side = max(small.cols(), small.rows()).toDouble()
        val k = RenderMath.odd(side / 30)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(k.toDouble(), k.toDouble()))
        Imgproc.morphologyEx(small, small, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.GaussianBlur(small, small, Size(0.0, 0.0), side / 40)
        val bg = Mat()
        Imgproc.resize(small, bg, ch.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
        small.release(); kernel.release()
        return bg
    }

    private fun clahe(img: Mat) {
        val lab = Mat(); Imgproc.cvtColor(img, lab, Imgproc.COLOR_RGB2Lab)
        val parts = ArrayList<Mat>(); Core.split(lab, parts)
        Imgproc.createCLAHE(2.0, Size(8.0, 8.0)).apply(parts[0], parts[0])
        Core.merge(parts, lab)
        Imgproc.cvtColor(lab, img, Imgproc.COLOR_Lab2RGB)
        parts.forEach { it.release() }; lab.release()
    }

    private fun unsharp(img: Mat) {
        val b = Mat()
        Imgproc.GaussianBlur(img, b, Size(0.0, 0.0), RenderParams.UNSHARP_SIGMA)
        Core.addWeighted(img, 1 + RenderParams.UNSHARP_AMOUNT, b, -RenderParams.UNSHARP_AMOUNT, 0.0, img)
        b.release()
    }

    /** Гистограмма одноканального 8-битного изображения. */
    private fun hist(gray: Mat): LongArray {
        val bytes = ByteArray((gray.total() * gray.channels()).toInt())
        gray.get(0, 0, bytes)
        val h = LongArray(256)
        for (b in bytes) h[b.toInt() and 0xFF]++
        return h
    }

    private fun lumaHist(rgb: Mat): LongArray {
        val g = Mat(); Imgproc.cvtColor(rgb, g, Imgproc.COLOR_RGB2GRAY)
        return hist(g).also { g.release() }
    }

    private fun applyLut(img: Mat, lut: IntArray?) {
        if (lut == null) return
        val m = Mat(1, 256, CvType.CV_8U)
        m.put(0, 0, ByteArray(256) { lut[it].toByte() })
        Core.LUT(img, m, img)
        m.release()
    }

    private fun toBitmap(img: Mat, mats: MutableList<Mat>): Bitmap {
        val rgba = Mat().also { mats += it }
        Imgproc.cvtColor(img, rgba, if (img.channels() == 1) Imgproc.COLOR_GRAY2RGBA else Imgproc.COLOR_RGB2RGBA)
        val bmp = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bmp)
        return bmp
    }

    /** Размер результата без рендера (для оценок и подписи на экране). */
    fun outputSize(srcW: Int, srcH: Int, recipe: PageRecipe, aspectWOverH: Double?): Pair<Int, Int> {
        val c = recipe.corners
        val base = if (c == null || c == QuadTransforms.FULL) srcW to srcH
        else RenderMath.outputSize(QuadTransforms.denormalize(c, srcW.toDouble(), srcH.toDouble()), aspectWOverH)
        return RenderMath.rotated(base, recipe.rotation)
    }

}
