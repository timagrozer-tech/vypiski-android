package ru.scanner291.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Подложка иконки плитки: `accent-text` на `accent-soft`, 44 dp, `radius-sm`. */
@Composable
internal fun TileIcon(icon: ImageVector) {
    val c = ScannerTheme.colors
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(Radius.sm)).background(c.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.accentText, modifier = Modifier.size(Size.icon))
    }
}

/**
 * Плитка второстепенного действия на главном экране («Импорт», «PDF-инструменты»…).
 * Все плитки одного цвета; подпись до двух строк и не обрезается при крупном шрифте.
 */
@Composable
fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier.heightIn(min = 96.dp), shape = RoundedCornerShape(Radius.md), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(Space.s4)) {
            TileIcon(icon)
            Spacer(Modifier.height(Space.s3))
            Text(label, style = Type.titleSm, color = ScannerTheme.colors.text)
        }
    }
}

/**
 * Плитка PDF-инструмента: стекло, различие — только иконкой; описание до двух строк.
 * Недоступная (`enabled = false`) не нажимается вовсе и помечена для доступности как disabled.
 */
@Composable
fun ToolTile(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = ScannerTheme.colors
    GlassSurface(
        modifier = modifier.heightIn(min = 120.dp)
            .then(if (enabled) Modifier else Modifier.alpha(OPACITY_DISABLED).semantics { disabled() }),
        shape = RoundedCornerShape(Radius.md),
        onClick = if (enabled) onClick else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.s4)) {
            TileIcon(icon)
            Spacer(Modifier.height(Space.s3))
            Text(title, style = Type.titleSm, color = c.text)
            Spacer(Modifier.height(Space.s1))
            Text(description, style = Type.body, color = c.textSecondary, maxLines = 2)
        }
    }
}
