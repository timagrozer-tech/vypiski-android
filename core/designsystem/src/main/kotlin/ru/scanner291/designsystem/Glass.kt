package ru.scanner291.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassLevel {
    /** Карточка: `glass`, без стекла — `surface`. */
    Card,
    /** Панель над содержимым (навигация, панели камеры): `glass-strong` в обоих режимах. */
    Bar,
}

/** Заливка поверхности по правилам дизайн-системы. Чистая функция — покрыта тестом. */
fun glassFill(colors: ScannerColors, level: GlassLevel, glassEnabled: Boolean): Color = when (level) {
    GlassLevel.Bar -> colors.glassStrong
    GlassLevel.Card -> if (glassEnabled) colors.glass else colors.surface
}

/**
 * Базовая поверхность Liquid Glass: заливка → контур 1 dp `glass-stroke` → блик 1 dp
 * `glass-highlight` по верхнему краю → тень `shadow-1`/`shadow-2`.
 *
 * Размытие подложки (RenderEffect 24 dp) подключается на экранах, где под стеклом прокручивается
 * содержимое (ADR-0018). На однотонном фоне `bg` размытие визуально ничего не меняет и не включается.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Card,
    shape: Shape = RoundedCornerShape(Radius.lg),
    elevation: Dp = if (level == GlassLevel.Bar) Elevation.floating else Elevation.card,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = ScannerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .shadow(elevation, shape, clip = false, ambientColor = shadowColor(c), spotColor = shadowColor(c))
            .clip(shape)
            .background(glassFill(c, level, ScannerTheme.glassEnabled))
            .border(BorderStroke(1.dp, c.glassStroke), shape)
            .topHighlight(c.glassHighlight)
            .then(
                if (onClick != null) {
                    Modifier
                        .focusRing(interaction, shape)
                        .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                } else Modifier,
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Стеклянная карточка с колонкой внутри — самый частый случай. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = Radius.lg,
    padding: Dp = Space.s4,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        level = if (strong) GlassLevel.Bar else GlassLevel.Card,
        shape = RoundedCornerShape(radius),
        onClick = onClick,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

internal fun shadowColor(c: ScannerColors): Color =
    if (c.isDark) Color.Black.copy(alpha = 0.35f) else Color(0xFF111827).copy(alpha = 0.08f)

/** Внутренняя линия-блик 1 dp по верхнему краю. */
internal fun Modifier.topHighlight(color: Color): Modifier = drawWithContent {
    drawContent()
    val inset = 12.dp.toPx()
    val y = 1.dp.toPx() / 2f
    drawLine(color, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
}
