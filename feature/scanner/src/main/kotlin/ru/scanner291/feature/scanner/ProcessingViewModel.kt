package ru.scanner291.feature.scanner

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.geometry.QuadCodec
import ru.scanner291.core.common.geometry.QuadTransforms
import ru.scanner291.core.database.CornersSource
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.imageprocessing.BitmapDetection
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.PageImageLoader
import javax.inject.Inject

sealed interface ProcessingState {
    data object Loading : ProcessingState
    data object NotFound : ProcessingState
    data class Ready(
        val title: String,
        val pageIndex: Int,
        val pageCount: Int,
        val page: PageEntity,
        val image: ImageBitmap?,
        /** Редактируемые углы (нормализованные). */
        val corners: Quad,
        val source: CornersSource?,
        val dirty: Boolean,
        val message: String?,
    ) : ProcessingState
}

/**
 * Правка углов листа. Изменяется только рецепт страницы в индексе; оригинал не трогается.
 * Перспектива по этим углам применяется на этапе 7.
 */
@HiltViewModel
class ProcessingViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val loader: PageImageLoader,
    saved: SavedStateHandle,
) : ViewModel() {
    private val route = saved.toRoute<ProcessingRoute>()
    private val _state = MutableStateFlow<ProcessingState>(ProcessingState.Loading)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    init { open(route.pageIndex) }

    fun open(index: Int) {
        viewModelScope.launch {
            val doc = repo.document(route.docId)
            val pages = doc?.orderedPages.orEmpty()
            if (doc == null || pages.isEmpty()) { _state.value = ProcessingState.NotFound; return@launch }
            val i = index.coerceIn(0, pages.lastIndex)
            val page = pages[i]
            val image = loader.load(page, 1600)?.asImageBitmap()
            val stored = QuadCodec.decode(page.corners)
            _state.value = ProcessingState.Ready(
                doc.doc.title, i, pages.size, page, image,
                stored ?: QuadTransforms.FULL, page.cornersSource?.let { runCatching { CornersSource.valueOf(it) }.getOrNull() },
                dirty = false, message = null,
            )
        }
    }

    fun move(corners: Quad) = update { it.copy(corners = corners, source = CornersSource.MANUAL, dirty = true, message = null) }
    fun fullPage() = update { it.copy(corners = QuadTransforms.FULL, source = CornersSource.FULL, dirty = true, message = null) }

    /** Повторный поиск углов на этой странице (OpenCV). */
    fun autoDetect() {
        val s = _state.value as? ProcessingState.Ready ?: return
        viewModelScope.launch {
            val found = withContext(Dispatchers.Default) {
                loader.load(s.page, 1024)?.let { runCatching { BitmapDetection.detectNormalized(it) }.getOrNull() }
            }
            update {
                if (found == null) it.copy(message = "Лист не найден — поправьте углы вручную или выберите «Весь лист»")
                else it.copy(corners = found.first, source = CornersSource.AUTO, dirty = true, message = null)
            }
        }
    }

    fun save() {
        val s = _state.value as? ProcessingState.Ready ?: return
        viewModelScope.launch {
            repo.setCorners(s.page.id, s.corners, s.source ?: CornersSource.MANUAL)
            update { it.copy(dirty = false, message = "Углы сохранены") }
        }
    }

    private fun update(f: (ProcessingState.Ready) -> ProcessingState.Ready) {
        (_state.value as? ProcessingState.Ready)?.let { _state.value = f(it) }
    }
}
