package ru.scanner291.feature.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.scanner291.core.common.geometry.Pt
import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.geometry.QuadTransforms
import ru.scanner291.core.database.CornersSource
import ru.scanner291.designsystem.ButtonVariant
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Status
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.Type
import kotlin.math.hypot

@Composable
internal fun ProcessingRouteScreen(onBack: () -> Unit, vm: ProcessingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    ProcessingScreen(state, vm::move, vm::autoDetect, vm::fullPage, vm::save, vm::open, onBack)
}

fun sourceText(s: CornersSource?): String = when (s) {
    CornersSource.AUTO -> "Углы найдены автоматически"
    CornersSource.MANUAL -> "Углы заданы вручную"
    CornersSource.FULL -> "Весь лист"
    null -> "Углы не заданы — используется весь кадр"
}

/**
 * Обработка, шаг 1 (этап 6): углы листа. Перспектива, тени и фильтры — этапы 7–9 на этом же экране.
 */
@Composable
fun ProcessingScreen(
    state: ProcessingState,
    onMove: (Quad) -> Unit,
    onAuto: () -> Unit,
    onFull: () -> Unit,
    onSave: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        when (state) {
            ProcessingState.Loading -> {
                ScanTopBar(title = "Обработка", onBack = onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ScannerTheme.colors.accent) }
            }
            ProcessingState.NotFound -> {
                ScanTopBar(title = "Обработка", onBack = onBack)
                EmptyState(Icons.Rounded.AutoFixHigh, "Документ не найден", "Откройте отсканированный документ, чтобы выровнять края.")
            }
            is ProcessingState.Ready -> {
                ScanTopBar(title = "Обработка", onBack = onBack)
                if (state.pageCount > 1) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onOpenPage(state.pageIndex - 1) }, enabled = state.pageIndex > 0) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Предыдущая страница")
                        }
                        Text("Страница ${state.pageIndex + 1} из ${state.pageCount}", style = Type.label, color = ScannerTheme.colors.text,
                            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { onOpenPage(state.pageIndex + 1) }, enabled = state.pageIndex < state.pageCount - 1) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Следующая страница")
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(Space.s4)) {
                    if (state.image != null) CornerEditor(state.image, state.corners, onMove, Modifier.fillMaxSize())
                }
                Column(Modifier.navigationBarsPadding().padding(horizontal = Space.s4, vertical = Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    StatusLine(if (state.message != null && state.message.startsWith("Лист не найден")) Status.WARNING else Status.SUCCESS,
                        state.message ?: sourceText(state.source))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        ScanButton("Найти автоматически", onAuto, Modifier.weight(1f), variant = ButtonVariant.Secondary, icon = Icons.Rounded.AutoFixHigh)
                        ScanButton("Весь лист", onFull, Modifier.weight(1f), variant = ButtonVariant.Secondary, icon = Icons.Rounded.CropFree)
                    }
                    ScanButton("Сохранить", onSave, Modifier.fillMaxWidth(), enabled = state.dirty)
                }
            }
        }
    }
}

/** Ближайший угол к точке касания в пределах [radiusPx]; -1 — нет. Покрыто тестом. */
fun nearestCorner(view: List<Pt>, touch: Pt, radiusPx: Double): Int {
    var best = -1; var bestD = radiusPx
    view.forEachIndexed { i, p -> val d = hypot(p.x - touch.x, p.y - touch.y); if (d <= bestD) { best = i; bestD = d } }
    return best
}

/**
 * Редактор углов: изображение целиком (FIT), рамка 2 dp `accent`, ручки 12 dp. При перетаскивании —
 * лупа ×2.5 с перекрестием в верхнем углу, противоположном пальцу (точная установка на угол листа).
 */
@Composable
fun CornerEditor(image: ImageBitmap, corners: Quad, onChange: (Quad) -> Unit, modifier: Modifier = Modifier) {
    val accent = ScannerTheme.colors.accent
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(corners)
    var drag by remember { mutableStateOf(-1) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.semantics { contentDescription = "Углы листа: перетащите, чтобы поправить" }) {
        val iw = image.width.toDouble(); val ih = image.height.toDouble()
        Canvas(
            Modifier.fillMaxSize().pointerInput(image) {
                val vw = size.width.toDouble(); val vh = size.height.toDouble()
                detectDragGestures(
                    onDragStart = { start ->
                        val view = current.points.map { QuadTransforms.fit(it, iw, ih, vw, vh) }
                        drag = nearestCorner(view, Pt(start.x.toDouble(), start.y.toDouble()), 48.dp.toPx().toDouble())
                        finger = start
                        if (drag >= 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { drag = -1 },
                    onDragCancel = { drag = -1 },
                    onDrag = { change, _ ->
                        if (drag >= 0) {
                            change.consume()
                            finger = change.position
                            val n = QuadTransforms.unfit(Pt(finger.x.toDouble(), finger.y.toDouble()), iw, ih, vw, vh)
                            val pts = current.points.toMutableList().also { it[drag] = n }
                            onChange(Quad(pts[0], pts[1], pts[2], pts[3]))
                        }
                    },
                )
            },
        ) {
            val vw = size.width.toDouble(); val vh = size.height.toDouble()
            val scale = minOf(size.width / image.width, size.height / image.height)
            val dw = image.width * scale; val dh = image.height * scale
            val ox = (size.width - dw) / 2; val oy = (size.height - dh) / 2
            drawImage(image, dstOffset = IntOffset(ox.toInt(), oy.toInt()), dstSize = IntSize(dw.toInt(), dh.toInt()))
            val pts = corners.points.map { QuadTransforms.fit(it, iw, ih, vw, vh) }.map { Offset(it.x.toFloat(), it.y.toFloat()) }
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            drawPath(path, accent.copy(alpha = 0.08f))
            drawPath(path, accent, style = Stroke(2.dp.toPx()))
            pts.forEachIndexed { i, p ->
                drawCircle(Color.White, 12.dp.toPx(), p)
                drawCircle(accent, if (i == drag) 10.dp.toPx() else 8.dp.toPx(), p)
            }
            // Лупа
            if (drag >= 0) {
                val r = 60.dp.toPx(); val zoom = 2.5f
                val center = Offset(if (finger.x > size.width / 2) r + 8.dp.toPx() else size.width - r - 8.dp.toPx(), r + 8.dp.toPx())
                val srcHalf = (r / zoom / scale).toInt()
                val sx = ((finger.x - ox) / scale).toInt(); val sy = ((finger.y - oy) / scale).toInt()
                val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
                clipPath(circle) {
                    drawRect(Color.White, center - Offset(r, r), Size(2 * r, 2 * r))
                    drawImage(
                        image,
                        srcOffset = IntOffset((sx - srcHalf).coerceIn(0, image.width - 1), (sy - srcHalf).coerceIn(0, image.height - 1)),
                        srcSize = IntSize((2 * srcHalf).coerceAtMost(image.width).coerceAtLeast(1), (2 * srcHalf).coerceAtMost(image.height).coerceAtLeast(1)),
                        dstOffset = IntOffset((center.x - r).toInt(), (center.y - r).toInt()),
                        dstSize = IntSize((2 * r).toInt(), (2 * r).toInt()),
                    )
                }
                drawCircle(accent, r, center, style = Stroke(2.dp.toPx()))
                drawLine(accent, center - Offset(10.dp.toPx(), 0f), center + Offset(10.dp.toPx(), 0f), 1.5f.dp.toPx())
                drawLine(accent, center - Offset(0f, 10.dp.toPx()), center + Offset(0f, 10.dp.toPx()), 1.5f.dp.toPx())
            }
        }
    }
}
