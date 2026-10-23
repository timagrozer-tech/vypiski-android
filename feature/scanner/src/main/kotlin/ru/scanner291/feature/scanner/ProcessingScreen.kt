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
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.scanner291.core.common.geometry.Pt
import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.geometry.QuadTransforms
import ru.scanner291.core.database.CornersSource
import ru.scanner291.designsystem.ButtonVariant
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.FilterStrip
import ru.scanner291.designsystem.ScanSegmented
import ru.scanner291.designsystem.SliderRow
import ru.scanner291.designsystem.ToggleRow
import ru.scanner291.core.common.model.FilterType
import ru.scanner291.core.common.render.usesDenoise
import ru.scanner291.core.common.render.usesShadowRemoval
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Status
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.Type
import kotlin.math.hypot
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import ru.scanner291.core.common.filters.Adjustments
import ru.scanner291.core.common.filters.Preset
import ru.scanner291.core.common.filters.PresetGroup
import ru.scanner291.core.common.filters.Presets
import ru.scanner291.designsystem.ScanChip
import ru.scanner291.designsystem.ScanTextField
import kotlin.math.roundToInt

/** Вкладки панели «Улучшение». */
enum class EnhanceTab(val title: String) { Presets("Пресеты"), Filter("Фильтр"), Manual("Вручную") }

/** Действия экрана обработки (один объект вместо длинного списка параметров). */
data class ProcessingActions(
    val onMove: (Quad) -> Unit = {},
    val onAuto: () -> Unit = {},
    val onFull: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onOpenPage: (Int) -> Unit = {},
    val onStep: (ProcessingStep) -> Unit = {},
    val onFilter: (FilterType) -> Unit = {},
    val onRotate: (Int) -> Unit = {},
    val onShadows: (Boolean) -> Unit = {},
    val onDenoise: (Boolean) -> Unit = {},
    val onBrightness: (Float) -> Unit = {},
    val onContrast: (Float) -> Unit = {},
    val onReset: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onHybrid: (ru.scanner291.core.common.model.HybridProfile) -> Unit = {},
    val onPreset: (Preset) -> Unit = {},
    val onAdjust: (String, Float) -> Unit = { _, _ -> },
    val onSavePreset: (String) -> Unit = {},
    val onDeletePreset: (String) -> Unit = {},
)

@Composable
internal fun ProcessingRouteScreen(onBack: () -> Unit, vm: ProcessingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    ProcessingScreen(
        state,
        ProcessingActions(
            vm::move, vm::autoDetect, vm::fullPage, vm::save, vm::open, vm::selectStep, vm::setFilter, vm::rotate,
            vm::setShadows, vm::setDenoise, vm::setBrightness, vm::setContrast, vm::reset, vm::undo, vm::redo, onBack, vm::setHybrid,
            vm::applyPreset, vm::setAdjust, vm::saveAsPreset, vm::deleteUserPreset,
        ),
    )
}

fun sourceText(s: CornersSource?): String = when (s) {
    CornersSource.AUTO -> "Углы найдены автоматически"
    CornersSource.MANUAL -> "Углы заданы вручную"
    CornersSource.FULL -> "Весь лист"
    null -> "Углы не заданы — используется весь кадр"
}

/** Подпись под ползунком яркости: −100…+100. Покрыто тестом. */
fun brightnessLabel(v: Float): String = (v * 100).roundToInt().let { if (it > 0) "+$it" else if (it < 0) "−${-it}" else "0" }
fun contrastLabel(v: Float): String = "×" + String.format(java.util.Locale.ROOT, "%.2f", v).replace('.', ',')

/** Почему переключатель недоступен для фильтра (или null). */
fun filterNote(f: FilterType): String? = when (f) {
    FilterType.ORIGINAL -> "«Оригинал»: только перспектива, поворот, яркость и контраст"
    FilterType.PHOTO -> "«Фото»: освещение и шум не выравниваются, чтобы сохранить снимок естественным"
    else -> null
}

/**
 * Обработка страницы: «Края» (углы листа, этап 6) и «Улучшение» (фильтр, поворот, тени, шум,
 * яркость, контраст — этап 7). Оригинал не меняется; «Сохранить» записывает рецепт.
 */
@Composable
fun ProcessingScreen(state: ProcessingState, a: ProcessingActions) {
    Column(Modifier.fillMaxSize()) {
        ScanTopBar(title = stringResource(R.string.proc_title), onBack = a.onBack)
        when (state) {
            ProcessingState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ScannerTheme.colors.accent) }
            ProcessingState.NotFound ->
                EmptyState(Icons.Rounded.AutoFixHigh, stringResource(R.string.proc_not_found), stringResource(R.string.proc_not_found_hint))
            is ProcessingState.Ready -> {
                ScanSegmented(ProcessingStep.entries, state.step, { it.title }, a.onStep, Modifier.padding(horizontal = Space.s4))
                if (state.pageCount > 1) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { a.onOpenPage(state.pageIndex - 1) }, enabled = state.pageIndex > 0) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.proc_prev_page))
                        }
                        Text(stringResource(R.string.proc_page_of, state.pageIndex + 1, state.pageCount), style = Type.label, color = ScannerTheme.colors.text,
                            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { a.onOpenPage(state.pageIndex + 1) }, enabled = state.pageIndex < state.pageCount - 1) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.proc_next_page))
                        }
                    }
                }
                when (state.step) {
                    ProcessingStep.Corners -> CornersStep(state, a)
                    ProcessingStep.Enhance -> EnhanceStep(state, a)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CornersStep(state: ProcessingState.Ready, a: ProcessingActions) {
    Box(Modifier.weight(1f).fillMaxWidth().padding(Space.s4)) {
        if (state.image != null) CornerEditor(state.image, state.corners, a.onMove, Modifier.fillMaxSize())
        else ImageMissing()
    }
    Column(Modifier.navigationBarsPadding().padding(horizontal = Space.s4, vertical = Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        // Одно актуальное состояние: последнее действие → замечание о качестве → откуда углы
        processingStatus(state).let { (st, text) -> StatusLine(st, text) }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            ScanButton(stringResource(R.string.proc_find_auto), a.onAuto, Modifier.weight(1f), variant = ButtonVariant.Secondary, icon = Icons.Rounded.AutoFixHigh)
            ScanButton(stringResource(R.string.proc_full_page), a.onFull, Modifier.weight(1f), variant = ButtonVariant.Secondary, icon = Icons.Rounded.CropFree)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            ScanButton(stringResource(R.string.proc_enhance), { a.onStep(ProcessingStep.Enhance) }, Modifier.weight(1f), variant = ButtonVariant.Secondary, icon = Icons.Rounded.Tune)
            ScanButton(stringResource(R.string.proc_save), a.onSave, Modifier.weight(1f), enabled = state.image != null)
        }
    }
}

@Composable
private fun ImageMissing() {
    EmptyState(Icons.Rounded.BrokenImage, stringResource(R.string.proc_image_failed), stringResource(R.string.proc_image_failed_hint))
}

@Composable
private fun ColumnScope.EnhanceStep(state: ProcessingState.Ready, a: ProcessingActions) {
    val c = ScannerTheme.colors
    val r = state.recipe
    var showOriginal by remember { mutableStateOf(false) }
    val previewDesc = stringResource(R.string.proc_preview_desc)
    // Превью: результат; удержание пальцем — исходный снимок для сравнения.
    Box(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2)
            .pointerInput(Unit) { detectTapGestures(onPress = { showOriginal = true; tryAwaitRelease(); showOriginal = false }) }
            .semantics { contentDescription = previewDesc },
        contentAlignment = Alignment.Center,
    ) {
        val shown = if (showOriginal) state.image else state.processed ?: state.image
        // Плавная смена результата при переключении фильтра — без скачков интерфейса
        androidx.compose.animation.Crossfade(shown, label = "preview") { img ->
            if (img != null) androidx.compose.foundation.Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            else ImageMissing()
        }
        if (state.rendering) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).size(24.dp), color = c.accent, strokeWidth = 2.dp)
        state.renderMs?.let { ms -> Text(stringResource(R.string.proc_render_time, ms), style = Type.captionSm, color = c.textSecondary,
            modifier = Modifier.align(Alignment.BottomEnd).background(c.field, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) }
        if (showOriginal) Text(stringResource(R.string.proc_original_shot), style = Type.captionSm, color = c.text,
            modifier = Modifier.align(Alignment.TopStart).background(c.field, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
    }
    // Панель настроек: одна вкладка за раз, высота ограничена — превью остаётся крупным.
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(EnhanceTab.Presets) }
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { a.onRotate(-90) }) { Icon(Icons.Rounded.RotateLeft, contentDescription = stringResource(R.string.proc_rotate_left)) }
        IconButton(onClick = { a.onRotate(90) }) { Icon(Icons.Rounded.RotateRight, contentDescription = stringResource(R.string.proc_rotate_right)) }
        ScanSegmented(EnhanceTab.entries, tab, { it.title }, { tab = it }, Modifier.weight(1f))
        IconButton(onClick = a.onUndo, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.proc_undo)) }
        IconButton(onClick = a.onRedo, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.proc_redo)) }
    }
    Column(Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState()).padding(top = Space.s1)) {
        when (tab) {
            EnhanceTab.Presets -> PresetStrip(state, a)
            EnhanceTab.Filter -> {
                FilterStrip(ru.scanner291.core.common.model.FILTER_ORDER, r.filter, { it.title }, state.previews, a.onFilter, Modifier.padding(vertical = Space.s1))
                if (r.filter == FilterType.HYBRID) {
                    ScanSegmented(ru.scanner291.core.common.model.HybridProfile.entries, r.hybrid, { it.title }, a.onHybrid,
                        Modifier.padding(horizontal = Space.s4, vertical = Space.s1))
                    Text(stringResource(R.string.proc_document_hint), style = Type.caption, color = c.textSecondary,
                        modifier = Modifier.padding(horizontal = Space.s4))
                }
                filterNote(r.filter)?.let { Text(it, style = Type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = Space.s4)) }
                ToggleRow(stringResource(R.string.proc_shadows), r.removeShadows, a.onShadows, description = stringResource(R.string.proc_shadows_hint),
                    enabled = r.filter.usesShadowRemoval())
                ToggleRow(stringResource(R.string.proc_denoise), r.denoise, a.onDenoise, description = stringResource(R.string.proc_denoise_hint), enabled = r.filter.usesDenoise())
            }
            EnhanceTab.Manual -> {
                SliderRow(stringResource(R.string.proc_brightness), r.brightness, a.onBrightness, -1f..1f, ::brightnessLabel)
                SliderRow(stringResource(R.string.proc_contrast), r.contrast, a.onContrast, 0.5f..2f, ::contrastLabel)
                ManualParameters(r.adjust, a.onAdjust)
                SavePresetRow(a.onSavePreset)
            }
        }
    }
    Column(Modifier.navigationBarsPadding().padding(horizontal = Space.s4, vertical = Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        state.message?.let { processingStatus(state).let { (st, text) -> StatusLine(st, text) } }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            ScanButton(stringResource(R.string.proc_reset), a.onReset, Modifier.weight(1f), variant = ButtonVariant.Secondary, icon = Icons.Rounded.RestartAlt)
            ScanButton(stringResource(R.string.proc_save), a.onSave, Modifier.weight(1f), enabled = state.image != null)
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
    val cornersDesc = stringResource(R.string.proc_corners_desc)
    BoxWithConstraints(modifier.semantics { contentDescription = cornersDesc }) {
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

/** Значение ручного параметра на экране: −100…+100 (толщина текста — −1/0/+1). Покрыто тестом. */
fun adjustLabel(spec: Adjustments.Spec, v: Float): String =
    if (spec.steps > 0) v.roundToInt().let { if (it > 0) "+$it" else if (it < 0) "−${-it}" else "0" } else brightnessLabel(v)

/** Пресеты: группы и пресеты выбранной группы; выбранный подсвечен. «Мои» — удаляются долгим нажатием с подтверждением. */
@Composable
private fun PresetStrip(state: ProcessingState.Ready, a: ProcessingActions) {
    val groups = PresetGroup.entries.filter { it != PresetGroup.USER || state.userPresets.isNotEmpty() }
    var group by remember { mutableStateOf(PresetGroup.DOCUMENT) }
    var deleting by remember { mutableStateOf<Preset?>(null) }
    LazyRow(contentPadding = PaddingValues(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        items(groups) { g -> ScanChip(g.title, g == group, { group = g }) }
    }
    val list = if (group == PresetGroup.USER) state.userPresets else Presets.ALL.filter { it.group == group }
    LazyRow(Modifier.padding(top = Space.s1), contentPadding = PaddingValues(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        items(list, key = { it.id }) { p ->
            val m = if (p.group == PresetGroup.USER) Modifier.pointerInput(p.id) { detectTapGestures(onLongPress = { deleting = p }, onTap = { a.onPreset(p) }) } else Modifier
            ScanChip(p.title, p.matches(state.recipe), { a.onPreset(p) }, m, checkmark = true)
        }
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.proc_preset_delete_title, p.title)) },
            text = { Text(stringResource(R.string.proc_preset_delete_hint)) },
            confirmButton = { TextButton({ a.onDeletePreset(p.id); deleting = null }) { Text(stringResource(R.string.proc_delete)) } },
            dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.proc_cancel)) } },
        )
    }
}

/** 16 ручных параметров (с яркостью и контрастом — 18). */
@Composable
private fun ManualParameters(adj: Adjustments, onAdjust: (String, Float) -> Unit) {
    for (s in Adjustments.SPECS) {
        SliderRow(s.title, adj.get(s.key), { onAdjust(s.key, it) }, s.min..s.max, { adjustLabel(s, it) }, steps = if (s.steps > 0) 1 else 0)
    }
}

@Composable
private fun SavePresetRow(onSave: (String) -> Unit) {
    var asking by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    ScanButton(stringResource(R.string.proc_save_preset), { asking = true }, Modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2),
        variant = ButtonVariant.Secondary)
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(stringResource(R.string.proc_my_preset)) },
        text = { ScanTextField(name, { name = it.take(40) }, stringResource(R.string.proc_preset_name_hint)) },
        confirmButton = { TextButton({ onSave(name); name = ""; asking = false }) { Text(stringResource(R.string.proc_save)) } },
        dismissButton = { TextButton({ asking = false }) { Text(stringResource(R.string.proc_cancel)) } },
    )
}

/** Строка Quality Gate: «Качество: хорошо» или первое замечание; сохранение не блокируется. Покрыто тестом. */
fun qualityLine(q: ru.scanner291.core.quality.gate.QualityReport): Pair<Status, String>? = when (q.verdict) {
    ru.scanner291.core.quality.gate.Verdict.GOOD -> Status.SUCCESS to "Качество снимка: хорошо"
    ru.scanner291.core.quality.gate.Verdict.ACCEPTABLE -> Status.WARNING to q.issues.first().message
    ru.scanner291.core.quality.gate.Verdict.POOR -> Status.DANGER to "Качество плохое: " + q.issues.first { it.severity == ru.scanner291.core.quality.gate.Severity.ERROR }.message +
        (if (q.issues.size > 1) " (ещё замечаний: ${q.issues.size - 1})" else "")
}

/**
 * Строка состояния экрана «Обработка» — всегда одна: результат последнего действия (сохранено, не найдено),
 * иначе — главное замечание о качестве, иначе — откуда углы. Покрыто тестом.
 */
fun processingStatus(s: ProcessingState.Ready): Pair<Status, String> {
    s.message?.let { m ->
        val bad = m.startsWith("Лист не найден") || m.startsWith("Не удалось")
        return (if (bad) Status.WARNING else Status.SUCCESS) to m
    }
    s.quality?.let(::qualityLine)?.let { return it }
    return Status.SUCCESS to sourceText(s.source)
}
