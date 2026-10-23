package ru.scanner291.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign

/** Пустое состояние: иконка в подложке, заголовок, пояснение. Без кнопок-заглушек. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = ScannerTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.s6, vertical = Space.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TileIcon(icon)
        Spacer(Modifier.height(Space.s4))
        Text(title, style = Type.titleMd, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.s2))
        Text(text, style = Type.body, color = c.textSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Space.s4))
            action()
        }
    }
}
