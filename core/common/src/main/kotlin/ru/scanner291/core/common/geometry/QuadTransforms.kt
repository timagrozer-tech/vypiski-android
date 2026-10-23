package ru.scanner291.core.common.geometry

import kotlin.math.max
import kotlin.math.min

/**
 * Преобразования четырёхугольника документа. Внутри приложения углы хранятся в НОРМАЛИЗОВАННЫХ
 * координатах (0..1) «прямого» изображения — после поворота по EXIF/сенсору. Так одни и те же углы
 * подходят и к превью, и к миниатюре, и к оригиналу любого разрешения.
 */
object QuadTransforms {
    fun normalize(q: Quad, w: Double, h: Double): Quad = q.scaled(1.0 / w, 1.0 / h)
    fun denormalize(q: Quad, w: Double, h: Double): Quad = q.scaled(w, h)

    /**
     * Поворот нормализованных координат кадра сенсора в «прямые» на [degrees] по часовой стрелке
     * (как `ImageInfo.rotationDegrees` в CameraX). Порядок углов восстанавливается (tl, tr, br, bl).
     */
    fun rotateNormalized(q: Quad, degrees: Int): Quad {
        val f: (Pt) -> Pt = when (((degrees % 360) + 360) % 360) {
            90 -> { p -> Pt(1 - p.y, p.x) }
            180 -> { p -> Pt(1 - p.x, 1 - p.y) }
            270 -> { p -> Pt(p.y, 1 - p.x) }
            else -> { p -> p }
        }
        return Quad.ordered(q.points.map(f))
    }

    /** Размеры кадра после поворота. */
    fun rotatedSize(w: Int, h: Int, degrees: Int): Pair<Int, Int> =
        if (((degrees % 360) + 360) % 360 % 180 == 90) h to w else w to h

    /**
     * Проекция нормализованных координат кадра на экран при масштабировании FILL_CENTER
     * (кадр заполняет экран, лишнее обрезается по центру) — так показывает превью CameraX.
     */
    fun fillCenter(p: Pt, frameW: Double, frameH: Double, viewW: Double, viewH: Double): Pt {
        val scale = max(viewW / frameW, viewH / frameH)
        val dx = (viewW - frameW * scale) / 2
        val dy = (viewH - frameH * scale) / 2
        return Pt(p.x * frameW * scale + dx, p.y * frameH * scale + dy)
    }

    /** Проекция при масштабировании FIT (изображение целиком внутри области) — редактор углов. */
    fun fit(p: Pt, imgW: Double, imgH: Double, viewW: Double, viewH: Double): Pt {
        val scale = min(viewW / imgW, viewH / imgH)
        val dx = (viewW - imgW * scale) / 2
        val dy = (viewH - imgH * scale) / 2
        return Pt(p.x * imgW * scale + dx, p.y * imgH * scale + dy)
    }

    /** Обратная к [fit]: экран → нормализованные координаты изображения, с ограничением 0..1. */
    fun unfit(v: Pt, imgW: Double, imgH: Double, viewW: Double, viewH: Double): Pt {
        val scale = min(viewW / imgW, viewH / imgH)
        val dx = (viewW - imgW * scale) / 2
        val dy = (viewH - imgH * scale) / 2
        return Pt(((v.x - dx) / (imgW * scale)).coerceIn(0.0, 1.0), ((v.y - dy) / (imgH * scale)).coerceIn(0.0, 1.0))
    }

    val FULL: Quad = Quad.fullFrame(1.0, 1.0)
}

/** Компактная строка для хранения в БД: «x,y;x,y;x,y;x,y» (tl, tr, br, bl), 5 знаков. */
object QuadCodec {
    fun encode(q: Quad): String = q.points.joinToString(";") { "%.5f,%.5f".format(java.util.Locale.ROOT, it.x, it.y) }

    fun decode(s: String?): Quad? {
        if (s.isNullOrBlank()) return null
        val pts = s.split(";").mapNotNull { part ->
            val xy = part.split(",")
            if (xy.size != 2) null else {
                val x = xy[0].toDoubleOrNull(); val y = xy[1].toDoubleOrNull()
                if (x == null || y == null || x !in -0.001..1.001 || y !in -0.001..1.001) null else Pt(x, y)
            }
        }
        return if (pts.size == 4) Quad(pts[0], pts[1], pts[2], pts[3]) else null
    }
}
