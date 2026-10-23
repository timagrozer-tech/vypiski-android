package ru.scanner291.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Затвор камеры: `size-shutter` (76 dp), белое кольцо 4 dp и белый круг, тень `shadow-accent`;
 * нажатие — масштаб 0.92 и тактильный отклик. Во время съёмки (`enabled = false`) приглушён.
 * [progress] — прогресс автосъёмки 0..1 (кольцо заполняется `accent`).
 */
@Composable
fun ShutterButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, progress: Float? = null) {
    val c = ScannerTheme.colors
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(Size.shutter)
            .alpha(if (enabled) 1f else 0.6f)
            .pressScale(interaction, Motion.SHUTTER_PRESS_SCALE)
            .shadow(Elevation.accent, CircleShape, clip = false, ambientColor = c.accent.copy(alpha = 0.28f), spotColor = c.accent.copy(alpha = 0.28f))
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            // Автосъёмка: кольцо заполняется `accent` по прогрессу стабилизации.
            .drawWithContent {
                drawContent()
                if (progress != null && progress > 0f) {
                    val sw = 4.dp.toPx()
                    drawArc(c.accent, -90f, 360f * progress.coerceIn(0f, 1f), useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw))
                }
            }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { contentDescription = "Сделать снимок" }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(Size.shutter - 16.dp).clip(CircleShape).background(Color.White))
    }
}
