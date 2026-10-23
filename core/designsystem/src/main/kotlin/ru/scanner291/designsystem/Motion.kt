package ru.scanner291.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Нажатие: масштаб 0.96 за 120 мс (README «Движение»). При «Убрать анимацию» — без масштаба.
 */
fun Modifier.pressScale(
    interaction: InteractionSource,
    pressedScale: Float = Motion.PRESS_SCALE,
): Modifier = composed {
    val reduced = ScannerTheme.reducedMotion
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = tween(if (reduced) 0 else Motion.PRESS_MS),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Кольцо фокуса 2 dp `focus-ring` с отступом 2 dp — для клавиатуры и D-pad. */
fun Modifier.focusRing(interaction: InteractionSource, shape: Shape): Modifier = composed {
    val focused by interaction.collectIsFocusedAsState()
    if (focused) this.border(2.dp, ScannerTheme.colors.focusRing, shape).padding(2.dp) else this
}

/** Длительность анимации с учётом «Убрать анимацию». */
internal fun motionMs(ms: Int, reduced: Boolean): Int = if (reduced) 0 else ms
