package ru.scanner291.feature.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.scanner291.core.common.text.documentSubtitle
import ru.scanner291.core.database.DocumentSummary
import ru.scanner291.core.database.TagEntity
import ru.scanner291.core.storage.DocCollection
import ru.scanner291.designsystem.ButtonVariant
import ru.scanner291.designsystem.DocumentRow
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.FolderRow
import ru.scanner291.designsystem.GlassCard
import ru.scanner291.designsystem.RowDivider
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScanChip
import ru.scanner291.designsystem.ScanSegmented
import ru.scanner291.designsystem.ScanTextField
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Status
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.Type

enum class DocumentsTab(val label: String) { All("Все"), Folders("Папки"), Favorites("Избранное"), Trash("Корзина") }

/** Умные коллекции вкладки «Все» (этап 14). */
enum class SmartFilter(val label: String, val c: DocCollection) {
    All("Все", DocCollection.All), Recent("За неделю", DocCollection.Recent),
    NoText("Без текста", DocCollection.NoText), Duplicates("Дубликаты", DocCollection.Duplicates),
}

@Composable
internal fun DocumentsRouteScreen(onOpen: (String) -> Unit, vm: DocumentsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val current by vm.collection.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(DocumentsTab.All) }
    var actionsFor by remember { mutableStateOf<DocumentSummary?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScanTopBar(title = "Мои документы", large = true)
        ScanSegmented(DocumentsTab.entries, tab, { it.label }, { t ->
            tab = t
            vm.show(when (t) { DocumentsTab.All -> DocCollection.All; DocumentsTab.Favorites -> DocCollection.Favorites; DocumentsTab.Trash -> DocCollection.Trash; DocumentsTab.Folders -> DocCollection.All })
        }, Modifier.padding(horizontal = Space.s4))
        Spacer(Modifier.height(Space.s2))
        lib.message?.let { StatusLine(Status.SUCCESS, it, Modifier.padding(horizontal = Space.s4, vertical = Space.s1)) }
        when (tab) {
            DocumentsTab.All -> {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    items(SmartFilter.entries) { f -> ScanChip(f.label, current == f.c, { vm.show(f.c) }) }
                    items(lib.tags, key = { it.id }) { t -> ScanChip("#${t.name}", current == DocCollection.Tagged(t.id), { vm.show(DocCollection.Tagged(t.id)) }) }
                }
                Spacer(Modifier.height(Space.s2))
                Docs(state, vm::thumbnail, onOpen, { actionsFor = it }, empty = "Документов пока нет" to "Отсканированные и импортированные документы появятся здесь.")
            }
            DocumentsTab.Folders -> {
                val folder = (current as? DocCollection.InFolder)?.folderId
                if (folder == null) {
                    ScanButton("Новая папка", { newFolder = true }, Modifier.fillMaxWidth().padding(horizontal = Space.s4), variant = ButtonVariant.Secondary, icon = Icons.Rounded.CreateNewFolder)
                    Spacer(Modifier.height(Space.s2))
                    if (lib.folders.isEmpty()) GlassCard(Modifier.padding(horizontal = Space.s4)) {
                        EmptyState(Icons.Rounded.Folder, "Папок пока нет", "Создайте папку и переместите в неё документы (долгое нажатие на документе).")
                    } else GlassCard(Modifier.padding(horizontal = Space.s4), padding = Space.s1) {
                        LazyColumn { items(lib.folders, key = { it.folder.id }) { f ->
                            FolderRow(f.folder.name, f.count, { vm.show(DocCollection.InFolder(f.folder.id)) }, { vm.show(DocCollection.InFolder(f.folder.id)) })
                        } }
                    }
                } else {
                    val name = lib.folders.firstOrNull { it.folder.id == folder }?.folder?.name ?: "Папка"
                    var confirmDelete by remember { mutableStateOf(false) }
                    LazyRow(contentPadding = PaddingValues(horizontal = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        item { ScanChip("← Все папки", false, { vm.show(DocCollection.All) }) }
                        item { ScanChip("Удалить папку «$name»", false, { confirmDelete = true }, leadingIcon = Icons.Rounded.Delete) }
                    }
                    Spacer(Modifier.height(Space.s2))
                    Docs(state, vm::thumbnail, onOpen, { actionsFor = it }, empty = "Папка пуста" to "Переместите сюда документ: долгое нажатие на документе → «В папку».")
                    if (confirmDelete) Confirm("Удалить папку «$name»?", "Документы останутся в библиотеке без папки.", "Удалить", { vm.deleteFolder(folder); confirmDelete = false }) { confirmDelete = false }
                }
            }
            DocumentsTab.Favorites -> Docs(state, vm::thumbnail, onOpen, { actionsFor = it }, empty = "В избранном пусто" to "Долгое нажатие на документе → «В избранное».", icon = Icons.Rounded.Star)
            DocumentsTab.Trash -> Docs(state, vm::thumbnail, onOpen, { actionsFor = it }, empty = "Корзина пуста" to "Удалённые документы хранятся здесь, пока вы не удалите их навсегда.", icon = Icons.Rounded.Delete)
        }
    }
    actionsFor?.let { d -> DocumentActions(d, lib.folders.map { it.folder.id to it.folder.name }, vm, onDismiss = { actionsFor = null }) }
    if (newFolder) NameDialog("Новая папка", "Название папки", { vm.createFolder(it); newFolder = false }) { newFolder = false }
}

@Composable
private fun Docs(
    state: ListState, thumbnail: suspend (String?) -> ImageBitmap?, onOpen: (String) -> Unit, onLong: (DocumentSummary) -> Unit,
    empty: Pair<String, String>, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.PictureAsPdf,
) {
    when {
        state is ListState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ScannerTheme.colors.accent) }
        state is ListState.Ready && state.docs.isEmpty() -> GlassCard(Modifier.padding(horizontal = Space.s4)) { EmptyState(icon, empty.first, empty.second) }
        state is ListState.Ready -> DocumentList(state.docs, onOpen, thumbnail, onLong = onLong)
    }
}

@Composable
fun DocumentList(docs: List<DocumentSummary>, onOpen: (String) -> Unit, thumbnail: suspend (String?) -> ImageBitmap?, modifier: Modifier = Modifier, onLong: (DocumentSummary) -> Unit = {}) {
    GlassCard(modifier.padding(horizontal = Space.s4), padding = Space.s1) {
        LazyColumn(contentPadding = PaddingValues(bottom = Space.s10 * 2)) {
            items(docs, key = { it.doc.id }) { d ->
                DocumentSummaryRow(d, onOpen, thumbnail, onLong)
                if (d != docs.last()) RowDivider()
            }
        }
    }
}

@Composable
fun DocumentSummaryRow(d: DocumentSummary, onOpen: (String) -> Unit, thumbnail: suspend (String?) -> ImageBitmap?, onLong: (DocumentSummary) -> Unit = {}) {
    val bmp by produceState<ImageBitmap?>(null, d.firstPageId) { value = thumbnail(d.firstPageId) }
    DocumentRow(
        title = (if (d.doc.favorite) "★ " else "") + d.doc.title,
        subtitle = documentSubtitle(d.doc.updatedAt, d.pageCount),
        thumbnail = bmp,
        onClick = { onOpen(d.doc.id) },
        onLongClick = { onLong(d) },
    )
}

/** Действия с документом (долгое нажатие). Удаление навсегда — только из корзины и с подтверждением. */
@Composable
private fun DocumentActions(d: DocumentSummary, folders: List<Pair<String, String>>, vm: DocumentsViewModel, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf("menu") }
    var tags by remember { mutableStateOf<List<TagEntity>>(emptyList()) }
    LaunchedEffect(d.doc.id, mode) { tags = vm.tagsOf(d.doc.id) }
    val trashed = d.doc.trashedAt != null
    when (mode) {
        "confirm" -> Confirm("Удалить «${d.doc.title}» навсегда?", "Файлы страниц будут удалены с устройства. Отменить это нельзя.", "Удалить навсегда",
            { vm.deleteForever(d); onDismiss() }, onDismiss)
        "tag" -> NameDialog("Тег для «${d.doc.title}»", "Например: отпуск", { vm.addTag(d, it); mode = "menu" }) { mode = "menu" }
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(d.doc.title, maxLines = 2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                    if (trashed) {
                        TextButton({ vm.restore(d); onDismiss() }) { Text("Восстановить") }
                        TextButton({ mode = "confirm" }) { Text("Удалить навсегда…", color = ScannerTheme.colors.danger) }
                    } else {
                        TextButton({ vm.favorite(d); onDismiss() }) { Text(if (d.doc.favorite) "Убрать из избранного" else "В избранное") }
                        if (folders.isNotEmpty()) Text("В папку:", style = Type.caption, color = ScannerTheme.colors.textSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            items(folders) { (id, name) -> ScanChip(name, d.doc.folderId == id, { vm.moveTo(d, if (d.doc.folderId == id) null else id); onDismiss() }) }
                        }
                        Text("Теги:", style = Type.caption, color = ScannerTheme.colors.textSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            items(tags, key = { it.id }) { t -> ScanChip("#${t.name} ✕", true, { vm.removeTag(d, t.id); mode = "menu" + t.id }) }
                            item { ScanChip("+ тег", false, { mode = "tag" }) }
                        }
                        TextButton({ vm.trash(d); onDismiss() }) { Text("В корзину") }
                    }
                }
            },
            confirmButton = { TextButton(onDismiss) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun Confirm(title: String, text: String, action: String, onYes: () -> Unit, onNo: () -> Unit) = AlertDialog(
    onDismissRequest = onNo, title = { Text(title) }, text = { Text(text) },
    confirmButton = { TextButton(onYes) { Text(action, color = ScannerTheme.colors.danger) } },
    dismissButton = { TextButton(onNo) { Text("Отмена") } },
)

@Composable
private fun NameDialog(title: String, hint: String, onOk: (String) -> Unit, onCancel: () -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel, title = { Text(title) },
        text = { ScanTextField(v, { v = it.take(60) }, hint) },
        confirmButton = { TextButton({ onOk(v) }, enabled = v.isNotBlank()) { Text("Готово") } },
        dismissButton = { TextButton(onCancel) { Text("Отмена") } },
    )
}
