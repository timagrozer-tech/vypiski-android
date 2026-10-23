package ru.scanner291.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.scanner291.core.common.text.RussianPlural
import kotlin.math.roundToInt

/** Разделитель строк внутри общей стеклянной карточки, с отступом от миниатюры/иконки. */
@Composable
fun RowDivider(startIndent: androidx.compose.ui.unit.Dp = 76.dp) {
    HorizontalDivider(Modifier.padding(start = startIndent), thickness = 1.dp, color = ScannerTheme.colors.separator)
}

/** Подсветка совпадений поиска подложкой `highlight` (без смены цвета текста), без учёта регистра и «ё». */
fun highlightMatches(text: String, query: String?, background: Color): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(text)
    val norm = { s: String -> s.lowercase().replace('ё', 'е') }
    val hay = norm(text); val needle = norm(query.trim())
    return buildAnnotatedString {
        append(text)
        var from = 0
        while (true) {
            val i = hay.indexOf(needle, from)
            if (i < 0) break
            addStyle(SpanStyle(background = background), i, i + needle.length)
            from = i + needle.length
        }
    }
}

/**
 * Строка документа: миниатюра 44×57 dp на белом листе, название до двух строк, подзаголовок в одну.
 * В режиме пакетного выделения — флажок слева и подложка `accent-soft`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentRow(
    title: String,
    subtitle: String,
    thumbnail: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val c = ScannerTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(if (selected) c.accentSoft else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .semantics { if (selectionMode) stateDescription = if (selected) "выбран" else "не выбран" }
            .padding(horizontal = Space.s4, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = null, colors = checkboxColors())
            Spacer(Modifier.width(Space.s2))
        }
        PaperThumb(thumbnail, Modifier.size(width = Size.rowThumb, height = 57.dp), RoundedCornerShape(Radius.xs))
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(highlightMatches(title, highlight, c.highlight), style = Type.titleSm, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(highlightMatches(subtitle, highlight, c.highlight), style = Type.body, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Белый лист (`paper`) с тонким контуром; без изображения — пустой лист. */
@Composable
internal fun PaperThumb(image: ImageBitmap?, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    val c = ScannerTheme.colors
    Box(modifier.clip(shape).background(c.paper).border(1.dp, c.glassStroke, shape)) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** Папка: все одного цвета, «Избранное» отличается иконкой; число документов склоняется. */
@Composable
fun FolderRow(
    name: String,
    count: Int,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    icon: ImageVector = if (favorite) Icons.Rounded.Star else Icons.Rounded.Folder,
) {
    val c = ScannerTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(start = Space.s4, end = Space.s1, top = Space.s2, bottom = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TileIcon(icon)
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(name, style = Type.titleSm, color = c.text, maxLines = 2)
            Text(RussianPlural.documents(count), style = Type.body, color = c.textSecondary)
        }
        IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, contentDescription = "Действия с папкой «$name»", tint = c.textSecondary) }
    }
}

/** Пункт настроек: значение показывает текущее состояние («В/ч 65384 (вкл.)»). */
@Composable
fun SettingsRow(icon: ImageVector, title: String, value: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ScannerTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s4, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TileIcon(icon)
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.titleSm, color = c.text)
            if (value != null) Text(value, style = Type.body, color = c.textSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary)
    }
}

enum class ToggleControl { Switch, Checkbox }

@Composable
private fun checkboxColors(onCamera: Boolean = false) = with(ScannerTheme.colors) {
    CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = if (onCamera) Color.White else textSecondary, checkmarkColor = onAccent)
}

/**
 * Строка с переключателем или флажком. Вся строка — зона нажатия (`toggleable`),
 * роль доступности — Switch или Checkbox.
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    control: ToggleControl = ToggleControl.Switch,
    onCamera: Boolean = false,
    enabled: Boolean = true,
) {
    val c = ScannerTheme.colors
    val haptic = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Size.touch)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = if (control == ToggleControl.Switch) Role.Switch else Role.Checkbox,
                onValueChange = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCheckedChange(it) },
            )
            .padding(horizontal = Space.s4, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (control == ToggleControl.Checkbox) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, colors = checkboxColors(onCamera))
            Spacer(Modifier.width(Space.s3))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.bodyLg, color = if (onCamera) Color.White else c.text)
            if (description != null) Text(description, style = Type.body, color = if (onCamera) Color.White.copy(alpha = 0.8f) else c.textSecondary)
        }
        if (control == ToggleControl.Switch) {
            Spacer(Modifier.width(Space.s3))
            Switch(
                checked = checked, onCheckedChange = null, enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.accent, checkedThumbColor = Color.White,
                    uncheckedTrackColor = c.field, uncheckedThumbColor = c.textSecondary, uncheckedBorderColor = c.glassStroke,
                ),
            )
        }
    }
}

/**
 * Флажок «Назвать файл по ФИО» — один и тот же на камере, в редакторе, в окне сохранения
 * и в окне экспорта (ТЗ v1.3 §7.4, v3.0 ч. VI). Состояние хранит вызывающий код (DataStore).
 */
@Composable
fun FioToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, onCamera: Boolean = false) {
    ToggleRow(
        title = "Назвать файл по ФИО",
        checked = checked,
        onCheckedChange = onChange,
        control = ToggleControl.Checkbox,
        onCamera = onCamera,
        modifier = modifier,
    )
}

/**
 * Слайдер с названием и значением справа (всегда видно). Тактильный отклик на шагах.
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val c = ScannerTheme.colors
    val haptic = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth().padding(horizontal = Space.s4, vertical = Space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.bodyLg, color = c.text, modifier = Modifier.weight(1f))
            Text(format(value), style = Type.caption, color = c.textSecondary)
        }
        Slider(
            value = value,
            onValueChange = { v ->
                if (steps > 0 && stepIndex(v, valueRange, steps) != stepIndex(value, valueRange, steps)) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onValueChange(v)
            },
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = c.accent, inactiveTrackColor = c.field,
                activeTickColor = c.onAccent, inactiveTickColor = c.textSecondary,
            ),
            modifier = Modifier.semantics { contentDescription = "$label: ${format(value)}" },
        )
    }
}

internal fun stepIndex(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Int {
    val span = range.endInclusive - range.start
    if (span <= 0f) return 0
    return ((v - range.start) / span * (steps + 1)).roundToInt()
}
