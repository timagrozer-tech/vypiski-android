package ru.scanner291.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Высота нижней навигации с отступами — столько оставляют снизу прокручиваемые экраны с панелью. */
val BottomBarInset = 120.dp

/**
 * Карточка документа (2026-10-24): отдельная карточка на каждый документ вместо общей «простыни» — список
 * не оставляет пустоты под последним документом. Миниатюра 52×70 на белом листе, название до двух строк,
 * подзаголовок, метка страниц, стрелка. Долгое нажатие — действия.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    title: String,
    subtitle: String,
    thumbnail: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    badge: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val c = ScannerTheme.colors
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.glassStroke, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .padding(Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaperThumb(thumbnail, Modifier.size(width = 52.dp, height = 70.dp), RoundedCornerShape(Radius.xs))
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favorite) {
                    Icon(Icons.Rounded.Star, contentDescription = "В избранном", tint = c.warning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.s1))
                }
                Text(title, style = Type.titleSm, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(subtitle, style = Type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (badge != null) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.clip(RoundedCornerShape(Radius.full)).background(c.accentSoft).padding(horizontal = Space.s2, vertical = 2.dp)) {
                    Text(badge, style = Type.captionSm, color = c.onAccentSoft, maxLines = 1)
                }
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary)
    }
}

/** Компактное действие (три в ряд): иконка в круге и подпись под ней, подпись до двух строк по центру. */
@Composable
fun CompactAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ScannerTheme.colors
    GlassSurface(modifier = modifier.heightIn(min = 104.dp), shape = RoundedCornerShape(Radius.lg), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.s4, horizontal = Space.s2), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radius.full)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = c.accentText, modifier = Modifier.size(Size.icon))
            }
            Spacer(Modifier.height(Space.s2))
            Text(label, style = Type.label, color = c.text, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** Плашка-подсказка с действием (например, «Переименовать в …»): мягкий акцентный фон, кнопка справа. */
@Composable
fun SuggestionBanner(text: String, action: String, onAction: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = ScannerTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.accentSoft).padding(start = Space.s4, end = Space.s1, top = Space.s1, bottom = Space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Type.caption, color = c.onAccentSoft, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        androidx.compose.material3.TextButton(onAction) { Text(action, style = Type.label, color = c.accentText) }
        androidx.compose.material3.TextButton(onDismiss) { Text("✕", style = Type.label, color = c.textSecondary) }
    }
}

/** Небольшая цветная метка (статус, тип документа). */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, bg: Color = ScannerTheme.colors.accentSoft, fg: Color = ScannerTheme.colors.onAccentSoft) {
    Box(modifier.clip(RoundedCornerShape(Radius.full)).background(bg).padding(horizontal = Space.s3, vertical = Space.s1)) {
        Text(text, style = Type.captionSm, color = fg, maxLines = 1)
    }
}

/**
 * Карточка выбора (формат экспорта и т. п.): иконка, название, пояснение; выбранная — акцентная рамка 2 dp и
 * мягкий акцентный фон. Для доступности — роль «переключатель» с состоянием.
 */
@Composable
fun OptionCard(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ScannerTheme.colors
    val shape = RoundedCornerShape(Radius.md)
    Column(
        modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .background(if (selected) c.accentSoft else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.glassStroke, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(Space.s3),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) c.accentText else c.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(Space.s1))
        Text(title, style = Type.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = Type.captionSm, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
