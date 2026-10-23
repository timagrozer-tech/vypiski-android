package ru.scanner291.feature.documents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.GlassCard
import ru.scanner291.designsystem.ScanSegmented
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.Space

enum class DocumentsTab(val label: String) { All("Все"), Folders("Папки"), Favorites("Избранное") }

/** «Мои документы». Индекс документов (Room) — этап 14; до него список честно пуст. */
@Composable
fun DocumentsScreen(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(DocumentsTab.All) }
    Column(modifier.fillMaxSize()) {
        ScanTopBar(title = "Мои документы", large = true)
        Column(Modifier.padding(horizontal = Space.s4)) {
            ScanSegmented(DocumentsTab.entries, tab, { it.label }, { tab = it })
            Spacer(Modifier.height(Space.s4))
            GlassCard {
                when (tab) {
                    DocumentsTab.All -> EmptyState(Icons.Rounded.PictureAsPdf, "Документов пока нет", "Отсканированные и импортированные документы появятся здесь.")
                    DocumentsTab.Folders -> EmptyState(Icons.Rounded.Folder, "Папок пока нет", "Папки появятся, когда в библиотеке будут документы.")
                    DocumentsTab.Favorites -> EmptyState(Icons.Rounded.Star, "В избранном пусто", "Отмеченные звёздочкой документы будут здесь.")
                }
            }
        }
    }
}

/** Просмотр документа. Пока документов нет — «не найден». */
@Composable
fun ViewerScreen(docId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScanTopBar(title = "Просмотр", onBack = onBack)
        EmptyState(Icons.Rounded.PictureAsPdf, "Документ не найден", "Возможно, он удалён или перемещён в корзину.")
    }
}
