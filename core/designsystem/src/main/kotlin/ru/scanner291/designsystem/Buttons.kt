package ru.scanner291.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class ButtonVariant { Primary, Secondary, Tonal, Ghost, Danger }

/** Цвета и стиль варианта кнопки. Вынесено отдельно, чтобы правила проверялись тестом. */
data class ButtonStyle(val container: Color, val pressed: Color, val content: Color, val text: TextStyle, val minHeight: androidx.compose.ui.unit.Dp)

fun buttonStyle(c: ScannerColors, variant: ButtonVariant): ButtonStyle = when (variant) {
    // Белый на accent проходит контраст только как крупный текст → title-md (18 sp, 600).
    ButtonVariant.Primary -> ButtonStyle(c.accent, c.accentPressed, c.onAccent, Type.titleMd, 56.dp)
    ButtonVariant.Secondary -> ButtonStyle(c.field, c.separator, c.text, Type.label, Size.touch)
    ButtonVariant.Tonal -> ButtonStyle(c.accentSoft, c.accentSoft, c.onAccentSoft, Type.label, Size.touch)
    ButtonVariant.Ghost -> ButtonStyle(Color.Transparent, c.accentSoft, c.accentText, Type.label, Size.touch)
    ButtonVariant.Danger -> ButtonStyle(c.field, c.separator, c.danger, Type.label, Size.touch)
}

/**
 * Кнопка действия. Одна Primary на экране («Сохранить», «Сохранить все (5)») — во всю ширину внизу.
 * Нажатие: масштаб 0.96 за 120 мс + тактильный отклик; недоступная — opacity-disabled.
 */
@Composable
fun ScanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = ScannerTheme.colors
    val style = buttonStyle(c, variant)
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(if (variant == ButtonVariant.Primary) Radius.lg else Radius.sm)
    Row(
        modifier
            .defaultMinSize(minHeight = style.minHeight, minWidth = Size.touch)
            .alpha(if (enabled) 1f else OPACITY_DISABLED)
            .pressScale(interaction)
            .clip(shape)
            .background(if (pressed) style.pressed else style.container)
            .focusRing(interaction, shape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = if (variant == ButtonVariant.Primary) Space.s5 else Space.s4, vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = style.content, modifier = Modifier.size(Size.icon))
            Spacer(Modifier.width(Space.s2))
        }
        Text(text, style = style.text, color = style.content, textAlign = TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
    }
}

/**
 * Главная кнопка главного экрана — единственный крупный синий акцент. Открывает камеру.
 * Без градиентов и свечения: только блик 1 dp (белый 18 %) по верхнему краю.
 */
@Composable
fun ScanHero(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Сканировать",
    subtitle: String = "Документ, паспорт, чек",
    icon: ImageVector = Icons.Rounded.PhotoCamera,
) {
    val c = ScannerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(Radius.xl)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .pressScale(interaction)
            .shadow(Elevation.accent, shape, clip = false, ambientColor = c.accent.copy(alpha = 0.28f), spotColor = c.accent.copy(alpha = 0.28f))
            .clip(shape)
            .background(if (pressed) c.accentPressed else c.accent)
            .topHighlight(Color.White.copy(alpha = 0.18f))
            .focusRing(interaction, shape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(Space.s6),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(Size.iconLg))
        }
        Spacer(Modifier.size(Space.s4))
        Text(title, style = Type.titleLg, color = c.onAccent)
        Text(subtitle, style = Type.body, color = c.onAccent.copy(alpha = 0.85f))
    }
}
