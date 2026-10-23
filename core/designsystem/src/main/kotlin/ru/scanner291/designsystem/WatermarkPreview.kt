package ru.scanner291.designsystem

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.scanner291.core.common.watermark.GridInkMap
import ru.scanner291.core.common.watermark.PlacementKind
import ru.scanner291.core.common.watermark.TextMeasurer
import ru.scanner291.core.common.watermark.WatermarkPlacement
import ru.scanner291.core.common.watermark.WatermarkPlanner
import ru.scanner291.core.common.watermark.WatermarkSettings
import ru.scanner291.core.common.watermark.WatermarkSpec

/** Прямоугольник содержимого образца листа в долях страницы (0..1). */
data class SampleBlock(val l: Float, val t: Float, val r: Float, val b: Float, val kind: Kind) {
    enum class Kind { TEXT, STAMP, SIGNATURE }
}

/** Образцы листов для предпросмотра: обычный документ и документ с текстом до левого нижнего края. */
object SamplePages {
    val regular: List<SampleBlock> = buildList {
        add(SampleBlock(0.30f, 0.07f, 0.70f, 0.10f, SampleBlock.Kind.TEXT)) // заголовок
        var y = 0.15f
        repeat(14) { i -> add(SampleBlock(0.12f, y, if (i % 4 == 3) 0.62f else 0.88f, y + 0.012f, SampleBlock.Kind.TEXT)); y += 0.03f }
        add(SampleBlock(0.55f, 0.72f, 0.88f, 0.76f, SampleBlock.Kind.SIGNATURE))
        add(SampleBlock(0.60f, 0.77f, 0.80f, 0.91f, SampleBlock.Kind.STAMP))
    }
    val dense: List<SampleBlock> = buildList {
        var y = 0.04f
        repeat(31) { add(SampleBlock(0.02f, y, 0.97f, y + 0.012f, SampleBlock.Kind.TEXT)); y += 0.031f }
    }
}

/** Размещение знака на образце листа A4 (595×842 pt, 72 dpi) — тот же планировщик, что и при экспорте. */
fun planSample(blocks: List<SampleBlock>, settings: WatermarkSettings, measure: TextMeasurer): WatermarkPlacement {
    val pw = 595; val ph = 842; val mw = 119; val mh = 168
    val mask = BooleanArray(mw * mh)
    for (b in blocks) {
        val x0 = (b.l * mw).toInt(); val x1 = kotlin.math.ceil(b.r * mw).toInt().coerceAtMost(mw)
        val y0 = (b.t * mh).toInt(); val y1 = kotlin.math.ceil(b.b * mh).toInt().coerceAtMost(mh)
        for (y in y0 until y1) for (x in x0 until x1) mask[y * mw + x] = true
    }
    return WatermarkPlanner().plan(pw, ph, 72f, GridInkMap(pw, ph, mw, mh, mask), settings, measure)
}

/** Человекочитаемый итог размещения для статуса под предпросмотром. */
fun placementStatus(p: WatermarkPlacement): Pair<Status, String> = when (p.kind) {
    PlacementKind.FREE_SPACE -> Status.SUCCESS to "В свободном поле слева внизу"
    PlacementKind.SHIFTED_UP -> Status.SUCCESS to "Сдвинут вверх по свободному полю"
    PlacementKind.REDUCED_SIZE -> Status.WARNING to "Уменьшен, чтобы не перекрывать текст"
    PlacementKind.PAGE_EXTENDED -> Status.WARNING to "Места нет — при экспорте слева добавится поле"
}

private fun paint(px: Float, alpha: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.SANS_SERIF
    textSize = px
    color = WatermarkSpec.COLOR_ARGB
    this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
}

/** Измерение текста платформой Android: длина строки и высота строки. */
val AndroidTextMeasurer = TextMeasurer { px ->
    val p = paint(px, 1f)
    val fm = p.fontMetrics
    p.measureText(WatermarkSpec.TEXT) to (fm.descent - fm.ascent)
}

/**
 * Предпросмотр листа с водяным знаком «В/ч 65384»: серый, 20 %, повёрнут на 90° против часовой,
 * слева в нижнем свободном поле. При расширении страницы показывается добавленное поле.
 */
@Composable
fun WatermarkPreview(
    settings: WatermarkSettings,
    modifier: Modifier = Modifier,
    blocks: List<SampleBlock> = SamplePages.regular,
) {
    val c = ScannerTheme.colors
    val placement = remember(settings, blocks) { planSample(blocks, settings, AndroidTextMeasurer) }
    Canvas(
        modifier.fillMaxWidth().aspectRatio(placement.outputWidth / placement.outputHeight.toFloat())
            .semantics { contentDescription = "Предпросмотр листа с водяным знаком ${WatermarkSpec.TEXT}" },
    ) {
        val k = size.width / placement.outputWidth
        val ext = placement.extendLeftPx * k
        drawRoundRect(c.paper, cornerRadius = CornerRadius(6f * k))
        if (ext > 0) drawRect(Color(0xFFF1F3F6), size = GSize(ext, size.height))
        val pageW = 595 * k; val pageH = 842 * k
        for (b in blocks) {
            val color = when (b.kind) {
                SampleBlock.Kind.TEXT -> Color(0xFFCBD2DC)
                SampleBlock.Kind.SIGNATURE -> Color(0xFF3B5BA9)
                SampleBlock.Kind.STAMP -> Color(0x553B5BA9)
            }
            val tl = Offset(ext + b.l * pageW, b.t * pageH)
            val sz = GSize((b.r - b.l) * pageW, (b.b - b.t) * pageH)
            if (b.kind == SampleBlock.Kind.STAMP) {
                drawCircle(color, radius = minOf(sz.width, sz.height) / 2, center = tl + Offset(sz.width / 2, sz.height / 2))
            } else drawRect(color, tl, sz)
        }
        if (settings.enabled) {
            drawIntoCanvas { cv ->
                val n = cv.nativeCanvas
                n.save()
                n.translate(placement.anchorX * k, placement.anchorY * k)
                n.rotate(WatermarkSpec.ROTATION_DEGREES)
                n.drawText(WatermarkSpec.TEXT, 0f, 0f, paint(placement.fontPx * k, settings.normalized().opacity))
                n.restore()
            }
        }
    }
}
