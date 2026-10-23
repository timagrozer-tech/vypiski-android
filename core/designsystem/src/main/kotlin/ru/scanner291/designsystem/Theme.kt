package ru.scanner291.designsystem

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp

enum class ThemeMode { SYSTEM, DARK, LIGHT }

object ScannerTheme {
    val colors: ScannerColors @Composable get() = LocalScannerColors.current
    val type: ScannerType get() = Type
}

/**
 * Тема: тёмная — основная, светлая — полноценная вторая. Dynamic Color не используется
 * по умолчанию (палитра фиксирована дизайн-системой).
 */
@Composable
fun ScannerTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.onAccentSoft,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.field, onSurfaceVariant = c.textSecondary, outline = c.glassStroke, outlineVariant = c.separator,
        error = c.danger, scrim = c.scrim, secondary = c.accentText, tertiary = c.success,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerLow = c.bgSecondary,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.onAccentSoft,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.field, onSurfaceVariant = c.textSecondary, outline = c.glassStroke, outlineVariant = c.separator,
        error = c.danger, scrim = c.scrim, secondary = c.accentText, tertiary = c.success,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerLow = c.bgSecondary,
    )
    CompositionLocalProvider(LocalScannerColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Размытие доступно на API 31+. На API 29–30 стекло заменяется непрозрачной поверхностью. */
val glassBlurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Стеклянная карточка: заливка glass/surface, контур 1 dp, тень shadow-1. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = Radius.lg,
    padding: Dp = Space.s4,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ScannerTheme.colors
    val shape = RoundedCornerShape(radius)
    val fill = when {
        strong -> c.glassStrong
        glassBlurSupported -> c.glass
        else -> c.surface
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .scale(if (pressed) 0.96f else 1f)
            .shadow(if (c.isDark) 8.dp(1) else 6.dp(1), shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f))
            .clip(shape)
            .background(fill)
            .border(BorderStroke(1.dp(1), c.glassStroke), shape)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

private fun Int.dp(@Suppress("UNUSED_PARAMETER") one: Int) = androidx.compose.ui.unit.Dp(this.toFloat())

/** Главная кнопка «Сканировать»/«Сохранить»: заливка accent, подпись не мельче title-md (контраст 3.7:1). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    hero: Boolean = false,
) {
    val c = ScannerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(if (hero) Radius.xl else Radius.lg)
    Row(
        modifier
            .defaultMinSize(minHeight = if (hero) 72.dp(1) else Size.touch)
            .scale(if (pressed) 0.96f else 1f)
            .then(if (hero) Modifier.shadow(12.dp(1), shape, ambientColor = c.accent, spotColor = c.accent) else Modifier)
            .clip(shape)
            .background(if (pressed) c.accentPressed else c.accent.copy(alpha = if (enabled) 1f else OPACITY_DISABLED))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (hero) Space.s6 else Space.s5, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(if (hero) Size.iconLg else Size.icon))
            Box(Modifier.size(Space.s3))
        }
        Text(text, style = Type.titleMd, color = c.onAccent)
    }
}

enum class Status { SUCCESS, WARNING, DANGER }

/** Статус всегда с иконкой: цвет не единственный носитель смысла. */
@Composable
fun StatusLine(status: Status, text: String, modifier: Modifier = Modifier) {
    val c = ScannerTheme.colors
    val (icon, color) = when (status) {
        Status.SUCCESS -> Icons.Rounded.CheckCircle to c.success
        Status.WARNING -> Icons.Rounded.Error to c.warning
        Status.DANGER -> Icons.Rounded.Cancel to c.danger
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp(1)))
        Box(Modifier.size(Space.s2))
        Text(text, style = Type.caption, color = color)
    }
}

/** Галочка «Назвать файл по ФИО» — используется на камере, в редакторе, сохранении и экспорте. */
@Composable
fun FioToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val c = ScannerTheme.colors
    val textColor = if (onDark) Color.White else c.text
    Row(
        modifier
            .defaultMinSize(minHeight = Size.touch)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(role = Role.Checkbox) { onChange(!checked) }
            .semantics { role = Role.Checkbox; stateDescription = if (checked) "включено" else "выключено" }
            .padding(end = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked, onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = if (onDark) Color.White else c.textSecondary, checkmarkColor = c.onAccent),
            modifier = Modifier.padding(Space.s3),
        )
        Text("Назвать файл по ФИО", style = Type.bodyLg, color = textColor)
    }
}

/** Плитка действия: одинаковое стекло, различие — только иконкой. */
@Composable
fun ActionTile(icon: ImageVector, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ScannerTheme.colors
    GlassCard(modifier, radius = Radius.md, padding = Space.s4, onClick = onClick) {
        Box(Modifier.clip(RoundedCornerShape(Radius.sm)).background(c.accentSoft).padding(Space.s2)) {
            Icon(icon, null, tint = c.accentText, modifier = Modifier.size(Size.iconLg))
        }
        Box(Modifier.size(Space.s2))
        Text(title, style = Type.titleSm, color = c.text)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = ScannerTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.titleLg, color = c.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = Type.label, color = c.accentText,
                modifier = Modifier.clip(RoundedCornerShape(Radius.sm)).clickable(onClick = onAction).padding(Space.s3))
        }
    }
}

@Composable
fun ScreenBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(ScannerTheme.colors.bg), content = content)
}
