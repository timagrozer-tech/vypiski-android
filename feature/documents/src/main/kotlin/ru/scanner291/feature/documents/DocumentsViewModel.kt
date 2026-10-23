package ru.scanner291.feature.documents

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.scanner291.core.database.DocumentSummary
import ru.scanner291.core.database.FolderSummary
import ru.scanner291.core.database.TagEntity
import ru.scanner291.core.storage.DocCollection
import ru.scanner291.core.database.DocumentWithPages
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.PageImageLoader
import ru.scanner291.core.storage.recipeFor
import javax.inject.Inject

sealed interface ListState {
    data object Loading : ListState
    data class Ready(val docs: List<DocumentSummary>) : ListState
}

/** Что показывать во вкладке «Все»: умные коллекции и теги. */
data class LibraryUi(
    val folders: List<FolderSummary> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val message: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val loader: PageImageLoader,
) : ViewModel() {
    /** Текущая коллекция (вкладка, папка, тег, умный фильтр). */
    val collection = MutableStateFlow<DocCollection>(DocCollection.All)

    val state: StateFlow<ListState> = collection.flatMapLatest { c -> repo.observeCollection(c) }
        .map<List<DocumentSummary>, ListState> { ListState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState.Loading)

    private val message = MutableStateFlow<String?>(null)
    val library: StateFlow<LibraryUi> = combine(repo.observeFolders(), repo.observeTags(), message) { f, t, m -> LibraryUi(f, t, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUi())

    fun show(c: DocCollection) { collection.value = c; message.value = null }

    suspend fun thumbnail(pageId: String?): ImageBitmap? =
        pageId?.let { repo.page(it) }?.let { loader.load(it, 160) }?.asImageBitmap()

    suspend fun tagsOf(docId: String) = repo.tagsOf(docId)

    private fun act(done: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            message.value = runCatching { block() }.fold({ done }, { it.message ?: "Не получилось" })
        }
    }

    fun favorite(d: DocumentSummary) = act { repo.setFavorite(d.doc.id, !d.doc.favorite) }
    fun moveTo(d: DocumentSummary, folderId: String?) = act("Перемещено") { repo.moveToFolder(d.doc.id, folderId) }
    fun trash(d: DocumentSummary) = act("«${d.doc.title}» — в корзине") { repo.trashDocument(d.doc.id) }
    fun restore(d: DocumentSummary) = act("Восстановлено") { repo.restoreDocument(d.doc.id) }
    /** Только после подтверждения в диалоге. */
    fun deleteForever(d: DocumentSummary) = act("Удалено навсегда") { repo.deleteForever(d.doc.id) }
    fun createFolder(name: String) = act("Папка создана") { repo.createFolder(name) }
    fun deleteFolder(id: String) = act("Папка удалена, документы остались") { repo.deleteFolder(id); if ((collection.value as? DocCollection.InFolder)?.folderId == id) collection.value = DocCollection.All }
    fun addTag(d: DocumentSummary, name: String) = act("Тег добавлен") { repo.addTag(d.doc.id, name) }
    fun removeTag(d: DocumentSummary, tagId: String) = act { repo.removeTag(d.doc.id, tagId) }
}

sealed interface ViewerState {
    data object Loading : ViewerState
    data object NotFound : ViewerState
    data class Ready(val doc: DocumentWithPages) : ViewerState
}

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val loader: PageImageLoader,
    private val processed: ru.scanner291.core.storage.ProcessedPageLoader,
    saved: SavedStateHandle,
) : ViewModel() {
    val docId: String = saved.toRoute<ViewerRoute>().docId

    val state: StateFlow<ViewerState> = repo.observeDocument(docId)
        .map { if (it == null) ViewerState.NotFound else ViewerState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerState.Loading)

    /**
     * Страница в просмотре — обработанная по рецепту (углы, фильтр, тени…); если обработка недоступна
     * (OpenCV не загрузился), показывается оригинал.
     */
    suspend fun image(page: PageEntity): ImageBitmap? {
        val m = ru.scanner291.core.storage.scanModeOf((state.value as? ViewerState.Ready)?.doc?.doc?.mode ?: "")
        return (processed.load(page, page.recipeFor(m), m, 1600) ?: loader.load(page, 1600))?.asImageBitmap()
    }
}
