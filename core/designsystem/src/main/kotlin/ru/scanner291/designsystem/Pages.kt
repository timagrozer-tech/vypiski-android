package ru.scanner291.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Миниатюра страницы в редакторе: лист 3:4, номер под ним. Выделена — рамка 2 dp `accent`,
 * бейдж с галочкой, масштаб 0.96; перетаскивание — масштаб 1.05 и тень `shadow-2`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageThumb(
    page: ImageBitmap?,
    number: Int,
    selected: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ScannerTheme.colors
    val reduced = ScannerTheme.reducedMotion
    val scale by animateFloatAsState(
        when {
            reduced -> 1f
            dragging -> 1.05f
            selected -> Motion.PRESS_SCALE
            else -> 1f
        },
        tween(motionMs(Motion.STATE_MS, reduced)), label = "pageScale",
    )
    val shape = RoundedCornerShape(Radius.md)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .semantics {
                contentDescription = "Страница $number"
                stateDescription = if (selected) "выбрана" else "не выбрана"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                .then(if (dragging) Modifier.shadow(Elevation.floating, shape) else Modifier)
                .border(if (selected) 2.dp else 0.dp, c.accent, shape),
        ) {
            PaperThumb(page, Modifier.matchParentSize().padding(if (selected) 2.dp else 0.dp), shape)
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(c.accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(16.dp)) }
            }
        }
        Spacer(Modifier.height(Space.s1))
        Text("$number", style = Type.captionSm, color = c.textSecondary)
    }
}

/** Последняя ячейка сетки страниц: пунктирный контур `glass-stroke`, «Добавить страницу». */
@Composable
fun AddPageCell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ScannerTheme.colors
    val radius = Radius.md
    Column(
        modifier.clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).drawBehind {
                drawRoundRect(
                    color = c.glassStroke.copy(alpha = 0.6f),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = c.accentText, modifier = Modifier.size(Size.iconLg)) }
        Spacer(Modifier.height(Space.s1))
        Text("Добавить страницу", style = Type.captionSm, color = c.accentText, textAlign = TextAlign.Center)
    }
}

/**
 * Лента фильтров: превью 64×82 dp (строятся заранее в фоне), подпись `caption-sm`;
 * выбранный — рамка 2 dp `accent` и подпись `accent-text`.
 */
@Composable
fun <F> FilterStrip(
    filters: List<F>,
    selected: F,
    label: (F) -> String,
    previews: Map<F, ImageBitmap>,
    onSelect: (F) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ScannerTheme.colors
    LazyRow(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Space.s4), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.s3)) {
        items(filters, key = { label(it) }) { f ->
            val isSel = f == selected
            val shape = RoundedCornerShape(Radius.sm)
            Column(
                Modifier.clickable(role = Role.RadioButton) { onSelect(f) }
                    .semantics { stateDescription = if (isSel) "выбран" else "не выбран" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PaperThumb(
                    previews[f],
                    Modifier.size(width = 64.dp, height = 82.dp).border(if (isSel) 2.dp else 0.dp, c.accent, shape),
                    shape,
                )
                Spacer(Modifier.height(Space.s1))
                Text(label(f), style = Type.captionSm, color = if (isSel) c.accentText else c.textSecondary, maxLines = 1)
            }
        }
    }
}
