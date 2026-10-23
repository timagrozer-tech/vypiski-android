package ru.scanner291.core.common.filters

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Ручные параметры (этап 9). Вместе с яркостью и контрастом рецепта — 18 ручных параметров.
 * Все значения −1..1 (кроме [textWeight] −1..1 с шагом 1), 0 — без изменений. Чистая математика на
 * массиве ARGB: работает одинаково на телефоне и в JVM-тестах, результат детерминирован.
 */
data class Adjustments(
    /** Экспозиция: ±1 ступень. */
    val exposure: Float = 0f,
    /** Гамма средних тонов: >0 светлее. */
    val gamma: Float = 0f,
    /** Света: <0 приглушить, >0 усилить. */
    val highlights: Float = 0f,
    /** Тени: >0 высветлить. */
    val shadows: Float = 0f,
    /** Точка белого: >0 — больше уходит в белое. */
    val whitePoint: Float = 0f,
    /** Точка чёрного: >0 — больше уходит в чёрное. */
    val blackPoint: Float = 0f,
    val saturation: Float = 0f,
    /** Насыщенность бледных цветов (насыщенные почти не меняются). */
    val vibrance: Float = 0f,
    /** Теплее (>0) / холоднее (<0). */
    val warmth: Float = 0f,
    /** Пурпурнее (>0) / зеленее (<0). */
    val tint: Float = 0f,
    /** Резкость мелких деталей (радиус 1). */
    val sharpness: Float = 0f,
    /** Локальный контраст (радиус ~1 % изображения). */
    val clarity: Float = 0f,
    /** Сглаживание шума. */
    val smoothing: Float = 0f,
    /** 1 — монохром. */
    val monochrome: Float = 0f,
    /** Блёклость: подъём чёрного. */
    val fade: Float = 0f,
    /** Толщина тёмных штрихов: −1 тоньше, +1 толще. */
    val textWeight: Int = 0,
    /** «Убрать муар и растр» 0..1 (аналог CamScanner: удаление муара / точечного растра). */
    val descreen: Float = 0f,
    /** «Убрать цветные пометки» 0..1: ручка, маркер, печать (аналог CamScanner: удаление рукописных пометок). */
    val inkRemoval: Float = 0f,
) {
    val isNeutral: Boolean get() = this == NEUTRAL

    fun normalized(): Adjustments = Adjustments(
        c(exposure), c(gamma), c(highlights), c(shadows), c(whitePoint), c(blackPoint), c(saturation), c(vibrance),
        c(warmth), c(tint), c01(sharpness), c01(clarity), c01(smoothing), c01(monochrome), c01(fade), textWeight.coerceIn(-1, 1),
        c01(descreen), c01(inkRemoval),
    )

    companion object {
        val NEUTRAL = Adjustments()
        const val COUNT = 18
        private fun c(v: Float) = v.coerceIn(-1f, 1f)
        private fun c01(v: Float) = v.coerceIn(0f, 1f)

        /** Описание для экрана: ключ, название, диапазон. Порядок — порядок ползунков. */
        val SPECS: List<Spec> = listOf(
            Spec("e", "Экспозиция", -1f, 1f), Spec("g", "Средние тона", -1f, 1f),
            Spec("hi", "Света", -1f, 1f), Spec("sh", "Тени", -1f, 1f),
            Spec("wp", "Точка белого", -1f, 1f), Spec("bp", "Точка чёрного", -1f, 1f),
            Spec("sat", "Насыщенность", -1f, 1f), Spec("vib", "Сочность", -1f, 1f),
            Spec("wa", "Теплота", -1f, 1f), Spec("ti", "Оттенок", -1f, 1f),
            Spec("sp", "Резкость", 0f, 1f), Spec("cl", "Чёткость", 0f, 1f),
            Spec("sm", "Сглаживание", 0f, 1f), Spec("mo", "Монохром", 0f, 1f),
            Spec("fa", "Блёклость", 0f, 1f), Spec("tw", "Толщина текста", -1f, 1f, steps = 1),
            Spec("dm", "Убрать муар и растр", 0f, 1f), Spec("ink", "Убрать цветные пометки и печати", 0f, 1f),
        )
    }

    data class Spec(val key: String, val title: String, val min: Float, val max: Float, val steps: Int = 0)

    fun get(key: String): Float = when (key) {
        "e" -> exposure; "g" -> gamma; "hi" -> highlights; "sh" -> shadows; "wp" -> whitePoint; "bp" -> blackPoint
        "sat" -> saturation; "vib" -> vibrance; "wa" -> warmth; "ti" -> tint; "sp" -> sharpness; "cl" -> clarity
        "sm" -> smoothing; "mo" -> monochrome; "fa" -> fade; "tw" -> textWeight.toFloat()
        "dm" -> descreen; "ink" -> inkRemoval
        else -> error("неизвестный параметр $key")
    }

    fun with(key: String, v: Float): Adjustments = when (key) {
        "e" -> copy(exposure = v); "g" -> copy(gamma = v); "hi" -> copy(highlights = v); "sh" -> copy(shadows = v)
        "wp" -> copy(whitePoint = v); "bp" -> copy(blackPoint = v); "sat" -> copy(saturation = v); "vib" -> copy(vibrance = v)
        "wa" -> copy(warmth = v); "ti" -> copy(tint = v); "sp" -> copy(sharpness = v); "cl" -> copy(clarity = v)
        "sm" -> copy(smoothing = v); "mo" -> copy(monochrome = v); "fa" -> copy(fade = v); "tw" -> copy(textWeight = v.roundToInt())
        "dm" -> copy(descreen = v); "ink" -> copy(inkRemoval = v)
        else -> error("неизвестный параметр $key")
    }.normalized()

    /** «e:0.30,sat:-0.20» — только ненулевые; пустая строка для нейтральных. */
    fun encode(): String = SPECS.mapNotNull { s -> get(s.key).takeIf { it != 0f }?.let { "${s.key}:${String.format(Locale.ROOT, "%.2f", it)}" } }
        .joinToString(",")
}

fun decodeAdjustments(s: String?): Adjustments {
    if (s.isNullOrBlank()) return Adjustments.NEUTRAL
    var a = Adjustments.NEUTRAL
    for (part in s.split(',')) {
        val kv = part.split(':'); if (kv.size != 2) continue
        val v = kv[1].toFloatOrNull() ?: continue
        if (Adjustments.SPECS.any { it.key == kv[0] }) a = a.with(kv[0], v)
    }
    return a.normalized()
}

/** Применение [Adjustments] к пикселям ARGB (на месте). Альфа сохраняется. */
object AdjustEngine {
    fun apply(px: IntArray, w: Int, h: Int, a0: Adjustments) {
        val a = a0.normalized()
        if (a.isNeutral) return
        require(px.size == w * h)
        var r = FloatArray(px.size) { ((px[it] shr 16) and 0xFF).toFloat() }
        var g = FloatArray(px.size) { ((px[it] shr 8) and 0xFF).toFloat() }
        var b = FloatArray(px.size) { (px[it] and 0xFF).toFloat() }

        // 0. Очистка (аналоги CamScanner, замер tools/processing-lab/lab_cam.py): сначала цветные пометки, затем растр
        if (a.inkRemoval > 0) removeInk(r, g, b, w, h, a.inkRemoval)
        if (a.descreen > 0) {
            val k = a.descreen
            r = descreen(r, w, h, k); g = descreen(g, w, h, k); b = descreen(b, w, h, k)
        }
        // 1. Сглаживание (до тональных правок — шум не усиливается)
        if (a.smoothing > 0) {
            val k = a.smoothing
            val rb = boxBlur(r, w, h, 1); val gb = boxBlur(g, w, h, 1); val bb = boxBlur(b, w, h, 1)
            for (i in r.indices) { r[i] += (rb[i] - r[i]) * k; g[i] += (gb[i] - g[i]) * k; b[i] += (bb[i] - b[i]) * k }
        }
        // 2. Тональные и цветовые правки — таблица на канал + операции в точке
        val expo = 2.0.pow(a.exposure.toDouble()).toFloat()
        val bp = a.blackPoint * 60f          // до 60 уровней в чёрное
        val wp = 255f - a.whitePoint * 60f   // до 60 уровней в белое
        val gam = 2.0.pow(-a.gamma.toDouble()) // >0 → гамма < 1 → светлее
        val lut = FloatArray(256) { v ->
            var x = v * expo
            x = if (wp - bp > 1f) (x - bp) * 255f / (wp - bp) else x
            x = x.coerceIn(0f, 255f)
            x = (255.0 * (x / 255.0).pow(gam)).toFloat()
            val l = x / 255f
            x += a.shadows * 80f * (1 - l) * (1 - l) * l * 4f   // подъём теней, середина и края почти не трогаются
            x += a.highlights * 60f * l * l * (1 - l) * 4f
            x = x * (1 - a.fade * 0.25f) + 255f * a.fade * 0.25f * 0.35f
            x.coerceIn(0f, 255f)
        }
        val warmR = a.warmth * 18f; val warmB = -a.warmth * 18f; val tintG = -a.tint * 14f
        for (i in r.indices) {
            var rr = lut[r[i].roundToInt().coerceIn(0, 255)] + warmR
            var gg = lut[g[i].roundToInt().coerceIn(0, 255)] + tintG
            var bb = lut[b[i].roundToInt().coerceIn(0, 255)] + warmB
            val l = 0.299f * rr + 0.587f * gg + 0.114f * bb
            if (a.saturation != 0f || a.vibrance != 0f) {
                val mx = max(rr, max(gg, bb)); val mn = min(rr, min(gg, bb))
                val curSat = if (mx <= 0f) 0f else (mx - mn) / mx
                val f = 1f + a.saturation + a.vibrance * (1f - curSat)
                rr = l + (rr - l) * f; gg = l + (gg - l) * f; bb = l + (bb - l) * f
            }
            if (a.monochrome > 0f) {
                val m = a.monochrome
                rr += (l - rr) * m; gg += (l - gg) * m; bb += (l - bb) * m
            }
            r[i] = rr.coerceIn(0f, 255f); g[i] = gg.coerceIn(0f, 255f); b[i] = bb.coerceIn(0f, 255f)
        }
        // 3. Резкость (радиус 1) и чёткость (радиус ~1 % стороны)
        if (a.sharpness > 0) { r = unsharp(r, w, h, 1, a.sharpness * 1.2f); g = unsharp(g, w, h, 1, a.sharpness * 1.2f); b = unsharp(b, w, h, 1, a.sharpness * 1.2f) }
        if (a.clarity > 0) {
            val rad = max(2, max(w, h) / 100)
            r = unsharp(r, w, h, rad, a.clarity * 0.6f); g = unsharp(g, w, h, rad, a.clarity * 0.6f); b = unsharp(b, w, h, rad, a.clarity * 0.6f)
        }
        // 4. Толщина штрихов: минимум/максимум 3×3 (тёмное расширяется/сужается)
        if (a.textWeight != 0) {
            val thicker = a.textWeight > 0
            r = minMax3(r, w, h, thicker); g = minMax3(g, w, h, thicker); b = minMax3(b, w, h, thicker)
        }
        for (i in px.indices) {
            px[i] = (px[i] and 0xFF000000.toInt()) or (q(r[i]) shl 16) or (q(g[i]) shl 8) or q(b[i])
        }
    }

    private fun q(v: Float) = v.roundToInt().coerceIn(0, 255)

    /**
     * Муар и растр: среднее 3×3 с силой k (гасит точки печати и муар периода 3–4 px), затем нерезкая маска
     * радиус 1, сила 0.6·k — края букв возвращаются. Радиус 2+ размывал текст (lab_cam.py: CER до 42 %);
     * так — шум растровой плашки 38 → 11.6, CER 0.84 → 0.68 %.
     */
    fun descreen(c: FloatArray, w: Int, h: Int, k: Float): FloatArray {
        val bl = boxBlur(c, w, h, 1)
        val x = FloatArray(c.size) { c[it] + (bl[it] - c[it]) * k }
        val bl2 = boxBlur(x, w, h, 1)
        return FloatArray(c.size) { (x[it] + (x[it] - bl2[it]) * 0.6f * k).coerceIn(0f, 255f) }
    }

    /** Цветные чернила: насыщенность ≥ 0.30, ярче 40 (не чёрный текст), темнее 245 (не бумага). */
    fun inkMask(r: FloatArray, g: FloatArray, b: FloatArray): BooleanArray = BooleanArray(r.size) {
        val mx = max(r[it], max(g[it], b[it])); val mn = min(r[it], min(g[it], b[it]))
        mx > 40f && mx < 245f && (mx - mn) / mx >= 0.30f
    }

    /**
     * Цветные пометки: пиксели цветных чернил (+1 px вокруг) заменяются фоном рядом — нормированное среднее
     * радиусом 7 по пикселям без чернил (нет таких — белое); k — сила смешивания. Убирает и синие печати/подписи:
     * это выбор пользователя. lab_cam.py: синих пикселей 0.98 → 0, CER печатного текста 3.09 → 1.79 %.
     */
    fun removeInk(r: FloatArray, g: FloatArray, b: FloatArray, w: Int, h: Int, k: Float) {
        val m0 = inkMask(r, g, b)
        if (m0.none { it }) return
        val m = FloatArray(m0.size)
        for (y in 0 until h) for (x in 0 until w) {
            var hit = false
            for (dy in -1..1) { for (dx in -1..1) { if (m0[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]) { hit = true; break } }; if (hit) break }
            if (hit) m[y * w + x] = 1f
        }
        val keep = FloatArray(m.size) { 1f - m[it] }
        val den = boxBlur(keep, w, h, 7)
        for (c in listOf(r, g, b)) {
            val num = boxBlur(FloatArray(c.size) { c[it] * keep[it] }, w, h, 7)
            for (i in c.indices) if (m[i] > 0f) {
                val fill = if (den[i] > 0.05f) num[i] / den[i] else 255f
                c[i] += (fill - c[i]) * k
            }
        }
    }

    /** Сепарабельное среднее по окну (2r+1)², края — повтор крайнего пикселя. O(n) на пиксель-канал. */
    fun boxBlur(src: FloatArray, w: Int, h: Int, rad: Int): FloatArray {
        val tmp = FloatArray(src.size); val out = FloatArray(src.size)
        val n = 2 * rad + 1
        for (y in 0 until h) {
            val o = y * w
            var acc = 0f
            for (k in -rad..rad) acc += src[o + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[o + x] = acc / n
                acc += src[o + (x + rad + 1).coerceAtMost(w - 1)] - src[o + (x - rad).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var acc = 0f
            for (k in -rad..rad) acc += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = acc / n
                acc += tmp[(y + rad + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - rad).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }

    private fun unsharp(c: FloatArray, w: Int, h: Int, rad: Int, amount: Float): FloatArray {
        val bl = boxBlur(c, w, h, rad)
        return FloatArray(c.size) { (c[it] + (c[it] - bl[it]) * amount).coerceIn(0f, 255f) }
    }

    private fun minMax3(c: FloatArray, w: Int, h: Int, min: Boolean): FloatArray {
        val out = FloatArray(c.size)
        for (y in 0 until h) for (x in 0 until w) {
            var v = c[y * w + x]
            for (dy in -1..1) for (dx in -1..1) {
                val s = c[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
                v = if (min) min(v, s) else max(v, s)
            }
            out[y * w + x] = v
        }
        return out
    }

    /** Средняя абсолютная разница двух изображений (0..255) по всем каналам — метрика различимости пресетов. */
    fun meanAbsDiff(a: IntArray, b: IntArray): Double {
        require(a.size == b.size)
        var s = 0L
        for (i in a.indices) {
            val x = a[i]; val y = b[i]
            s += abs(((x shr 16) and 0xFF) - ((y shr 16) and 0xFF)) + abs(((x shr 8) and 0xFF) - ((y shr 8) and 0xFF)) + abs((x and 0xFF) - (y and 0xFF))
        }
        return s.toDouble() / (a.size * 3)
    }
}
