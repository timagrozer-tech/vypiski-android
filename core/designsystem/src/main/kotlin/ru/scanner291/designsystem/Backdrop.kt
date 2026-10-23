package ru.scanner291.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Размытие подложки (ADR-0018): содержимое экрана записывается в слой ([backdropSource]),
 * плавающая панель рисует под собой размытую копию своего участка ([backdropBlur]).
 * Только при включённом стекле (Android 12+, без энергосбережения и «Убрать анимацию»).
 */
@Stable
class BackdropState internal constructor(internal val layer: GraphicsLayer) {
    internal var sourceOrigin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdrop(): BackdropState {
    val layer = rememberGraphicsLayer()
    return androidx.compose.runtime.remember(layer) { BackdropState(layer) }
}

/** Источник: содержимое под панелями (прокручиваемый экран). */
fun Modifier.backdropSource(state: BackdropState): Modifier = this
    .onGloballyPositioned { state.sourceOrigin = it.positionInRoot() }
    .drawWithContent {
        state.layer.record { this@drawWithContent.drawContent() }
        drawLayer(state.layer)
    }

/** Панель: под ней — размытый участок источника. Без стекла модификатор ничего не делает. */
@Composable
fun Modifier.backdropBlur(state: BackdropState, shape: Shape, radius: Dp = 24.dp): Modifier {
    if (!ScannerTheme.glassEnabled) return this
    val blurLayer = rememberGraphicsLayer()
    var origin by androidx.compose.runtime.remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            val r = radius.toPx()
            blurLayer.renderEffect = BlurEffect(r, r, TileMode.Clamp)
            val d = origin - state.sourceOrigin
            blurLayer.record { translate(-d.x, -d.y) { drawLayer(state.layer) } }
            drawLayer(blurLayer)
        }
}
