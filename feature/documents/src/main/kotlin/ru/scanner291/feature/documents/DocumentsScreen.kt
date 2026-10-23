package ru.scanner291.feature.documents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.scanner291.core.common.text.documentSubtitle
import ru.scanner291.core.database.DocumentSummary
import ru.scanner291.designsystem.DocumentRow
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.GlassCard
import ru.scanner291.designsystem.RowDivider
import ru.scanner291.designsystem.ScanSegmented
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.Space

enum class DocumentsTab(val label: String) { All("Все"), Folders("Папки"), Favorites("Избранное") }

@Composable
internal fun DocumentsRouteScreen(onOpen: (String) -> Unit, vm: DocumentsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    DocumentsScreen(state, onOpen, vm::thumbnail)
}

/** «Мои документы»: список из индекса (Room). Папки и избранное — этап 14. */
@Composable
fun DocumentsScreen(state: ListState, onOpen: (String) -> Unit, thumbnail: suspend (String?) -> ImageBitmap?, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(DocumentsTab.All) }
    Column(modifier.fillMaxSize()) {
        ScanTopBar(title = "Мои документы", large = true)
        ScanSegmented(DocumentsTab.entries, tab, { it.label }, { tab = it }, Modifier.padding(horizontal = Space.s4))
        Spacer(Modifier.height(Space.s4))
        when {
            tab == DocumentsTab.Folders -> GlassCard(Modifier.padding(horizontal = Space.s4)) {
                EmptyState(Icons.Rounded.Folder, "Папок пока нет", "Папки появятся на этапе 14 вместе с менеджером документов.")
            }
            tab == DocumentsTab.Favorites -> GlassCard(Modifier.padding(horizontal = Space.s4)) {
                EmptyState(Icons.Rounded.Star, "В избранном пусто", "Отмечать документы звёздочкой можно будет на этапе 14.")
            }
            state is ListState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ScannerTheme.colors.accent)
            }
            state is ListState.Ready && state.docs.isEmpty() -> GlassCard(Modifier.padding(horizontal = Space.s4)) {
                EmptyState(Icons.Rounded.PictureAsPdf, "Документов пока нет", "Отсканированные и импортированные документы появятся здесь.")
            }
            state is ListState.Ready -> DocumentList(state.docs, onOpen, thumbnail)
        }
    }
}

@Composable
fun DocumentList(docs: List<DocumentSummary>, onOpen: (String) -> Unit, thumbnail: suspend (String?) -> ImageBitmap?, modifier: Modifier = Modifier) {
    GlassCard(modifier.padding(horizontal = Space.s4), padding = Space.s1) {
        LazyColumn(contentPadding = PaddingValues(bottom = Space.s10 * 2)) {
            items(docs, key = { it.doc.id }) { d ->
                DocumentSummaryRow(d, onOpen, thumbnail)
                if (d != docs.last()) RowDivider()
            }
        }
    }
}

@Composable
fun DocumentSummaryRow(d: DocumentSummary, onOpen: (String) -> Unit, thumbnail: suspend (String?) -> ImageBitmap?) {
    val bmp by produceState<ImageBitmap?>(null, d.firstPageId) { value = thumbnail(d.firstPageId) }
    DocumentRow(
        title = d.doc.title,
        subtitle = documentSubtitle(d.doc.updatedAt, d.pageCount),
        thumbnail = bmp,
        onClick = { onOpen(d.doc.id) },
    )
}
