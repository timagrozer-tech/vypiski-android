package ru.scanner291.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Разделы нижней навигации — одинаковые во всём приложении. Камера в навигацию не входит. */
enum class Destination(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    Home("Главная", Icons.Rounded.Home, Icons.Outlined.Home),
    Documents("Документы", Icons.Rounded.Description, Icons.Outlined.Description),
    Search("Поиск", Icons.Rounded.Search, Icons.Outlined.Search),
    Settings("Настройки", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

/**
 * Плавающая нижняя навигация: `glass-strong`, `radius-xl`, тень `shadow-2`, высота `size-nav`
 * + системный отступ. Подписи видны у всех пунктов.
 */
@Composable
fun ScanBottomNav(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = Space.s3, vertical = Space.s2)
            .fillMaxWidth()
            .height(Size.nav),
        level = GlassLevel.Bar,
        shape = RoundedCornerShape(Radius.xl),
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            Destination.entries.forEach { d ->
                NavItem(d, d == selected) { onSelect(d) }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(d: Destination, selected: Boolean, onClick: () -> Unit) {
    val c = ScannerTheme.colors
    val ms = motionMs(Motion.STATE_MS, ScannerTheme.reducedMotion)
    val tint by animateColorAsState(if (selected) c.accentText else c.textSecondary, tween(ms), label = "navTint")
    val pill by animateColorAsState(if (selected) c.accentSoft else Color.Transparent, tween(ms), label = "navPill")
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(Radius.full)).background(pill).padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (selected) d.selectedIcon else d.icon, contentDescription = null, tint = tint, modifier = Modifier.size(Size.icon))
        }
        Spacer(Modifier.height(Space.s1))
        Text(d.label, style = Type.captionSm, color = tint, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/**
 * Верхняя панель. Корневые экраны: крупный заголовок (`display` на главном, иначе `headline`),
 * фон прозрачный; `collapsed = true` — компактная панель `glass-strong` с `title-lg`.
 * Вложенные экраны: «Назад» слева, `title-lg` (до двух строк при крупном шрифте), до трёх действий справа.
 */
@Composable
fun ScanTopBar(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    display: Boolean = false,
    collapsed: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = ScannerTheme.colors
    val compact = !large || collapsed
    Row(
        modifier
            .fillMaxWidth()
            .background(if (compact && collapsed) glassFill(c, GlassLevel.Bar, true) else Color.Transparent)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp)
            .padding(start = if (onBack != null) Space.s1 else Space.s4, end = Space.s1, top = if (compact) 0.dp else Space.s8),
        verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Bottom,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = c.text)
            }
        }
        Text(
            title,
            style = when {
                compact -> Type.titleLg
                display -> Type.display
                else -> Type.headline
            },
            color = c.text,
            maxLines = if (compact) 2 else 3,
            modifier = Modifier.weight(1f).padding(vertical = Space.s2).semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Заголовок раздела с необязательной ссылкой («Все ›»). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = ScannerTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.titleLg, color = c.text, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction != null) {
            ScanButton(action, onAction, variant = ButtonVariant.Ghost)
        }
    }
}
