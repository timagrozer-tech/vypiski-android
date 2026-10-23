package ru.scanner291.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Чип режима или фильтра: видимая высота 36 dp при зоне нажатия 48 dp, `radius-sm`.
 * Выбранный — `accent-soft`, контур `accent`, текст `on-accent-soft`; `checkmark` — для множественного выбора.
 */
@Composable
fun ScanChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    checkmark: Boolean = false,
) {
    val c = ScannerTheme.colors
    val ms = motionMs(Motion.STATE_MS, ScannerTheme.reducedMotion)
    val bg by animateColorAsState(if (selected) c.accentSoft else c.field, tween(ms), label = "chipBg")
    val fg = if (selected) c.onAccentSoft else c.text
    val shape = RoundedCornerShape(Radius.sm)
    Box(
        modifier
            .heightIn(min = Size.touch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(36.dp).clip(shape).background(bg)
                .border(1.dp, if (selected) c.accent else c.glassStroke, shape)
                .padding(horizontal = Space.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = if (selected && checkmark) Icons.Rounded.Check else leadingIcon
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.s1))
            }
            Text(label, style = Type.label, color = fg, maxLines = 1)
        }
    }
}

/**
 * Сегменты для 2–4 взаимоисключающих видов. Трек `field`, выбранный — `accent` с белым `label`;
 * переход — сдвиг 200 мс без изменения высоты.
 */
@Composable
fun <T> ScanSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.size in 2..4) { "Сегменты — для 2–4 вариантов; больше — используйте ScanChip" }
    val c = ScannerTheme.colors
    val ms = motionMs(Motion.STATE_MS, ScannerTheme.reducedMotion)
    val shape = RoundedCornerShape(Radius.sm)
    BoxWithConstraints(
        modifier.fillMaxWidth().heightIn(min = Size.touch).clip(shape).background(c.field).padding(4.dp),
    ) {
        val segW = maxWidth / options.size
        val index = options.indexOf(selected).coerceAtLeast(0)
        val x by animateDpAsState(segW * index, tween(ms), label = "segment")
        // Подложка выбранного сегмента: размер — по родителю (высоту задаёт ряд с подписями).
        Box(Modifier.matchParentSize()) {
            Box(Modifier.offset(x = x).width(segW).fillMaxHeight().clip(RoundedCornerShape(Radius.sm - 4.dp)).background(c.accent))
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { o ->
                val isSel = o == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp)
                        .selectable(selected = isSel, role = Role.Tab) { onSelect(o) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(o), style = Type.label, color = if (isSel) c.onAccent else c.text,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Поле ввода и поиска: фон `field`, контур `glass-stroke` (в фокусе — `accent`, при ошибке — `danger`),
 * текст `body-lg`, плейсхолдер `text-secondary`, кнопка очистки при непустом значении.
 * [suffix] — нередактируемое окончание (например, «.pdf»).
 */
@Composable
fun ScanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    suffix: String? = null,
    clearable: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = ScannerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Radius.sm)
    val stroke = when {
        isError -> c.danger
        focused -> c.accent
        else -> c.glassStroke
    }
    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = Type.bodyLg.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
            interactionSource = interaction,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth().semantics { if (isError) error(supportingText ?: "Ошибка") },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Size.touch).clip(shape).background(c.field)
                        .border(1.dp, stroke, shape).padding(start = Space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(leadingIcon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(Size.icon))
                        Spacer(Modifier.width(Space.s2))
                    }
                    Box(Modifier.weight(1f).padding(vertical = Space.s3)) {
                        if (value.isEmpty()) Text(placeholder, style = Type.bodyLg, color = c.textSecondary, maxLines = 1)
                        inner()
                    }
                    if (suffix != null) Text(suffix, style = Type.bodyLg, color = c.textSecondary)
                    if (clearable && value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(Icons.Rounded.Cancel, contentDescription = "Очистить", tint = c.textSecondary)
                        }
                    } else {
                        Spacer(Modifier.width(Space.s3))
                    }
                }
            },
        )
        if (supportingText != null) {
            Row(Modifier.padding(start = Space.s1, top = Space.s1), verticalAlignment = Alignment.CenterVertically) {
                if (isError) {
                    Icon(Icons.Rounded.Error, contentDescription = null, tint = c.danger, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.s1))
                }
                Text(supportingText, style = Type.caption, color = if (isError) c.danger else c.textSecondary)
            }
        }
    }
}
