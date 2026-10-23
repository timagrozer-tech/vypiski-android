package ru.scanner291.feature.documents

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.scanner291.core.database.DocumentSummary
import ru.scanner291.core.database.DocumentWithPages
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.PageImageLoader
import javax.inject.Inject

sealed interface ListState {
    data object Loading : ListState
    data class Ready(val docs: List<DocumentSummary>) : ListState
}

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val loader: PageImageLoader,
) : ViewModel() {
    val state: StateFlow<ListState> = repo.observeDocuments().map<List<DocumentSummary>, ListState> { ListState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState.Loading)

    suspend fun thumbnail(pageId: String?): ImageBitmap? =
        pageId?.let { repo.page(it) }?.let { loader.load(it, 160) }?.asImageBitmap()
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
    saved: SavedStateHandle,
) : ViewModel() {
    val docId: String = saved.toRoute<ViewerRoute>().docId

    val state: StateFlow<ViewerState> = repo.observeDocument(docId)
        .map { if (it == null) ViewerState.NotFound else ViewerState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerState.Loading)

    suspend fun image(page: PageEntity): ImageBitmap? = loader.load(page, 1600)?.asImageBitmap()
}
