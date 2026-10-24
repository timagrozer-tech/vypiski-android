package ru.scanner291.feature.home

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.common.text.RussianPlural
import ru.scanner291.core.database.DocumentSummary
import ru.scanner291.core.intelligence.naming.DefaultNames
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.ImportResult
import ru.scanner291.core.storage.PageImageLoader
import java.time.LocalDateTime
import javax.inject.Inject

/** Итог импорта для пользователя. Чистая функция — покрыта тестом. */
fun importMessage(r: ImportResult): String? {
    val parts = mutableListOf<String>()
    if (r.importedFiles > 0) parts += "Импортировано: ${RussianPlural.files(r.importedFiles)}"
    if (r.rejected.isNotEmpty()) {
        parts += "Не импортировано: " + r.rejected.joinToString("; ") { "${it.name} — ${it.reason}" }
    }
    return parts.joinToString(". ").ifBlank { null }
}

sealed interface ImportEvent {
    data class Open(val docId: String) : ImportEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val thumbs: ru.scanner291.core.storage.ThumbnailLoader,
) : ViewModel() {
    /** Последние 5 документов (виртуальное представление индекса, без копий). */
    val recent: StateFlow<List<DocumentSummary>?> = repo.observeDocuments().map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _event = MutableStateFlow<ImportEvent?>(null)
    val event: StateFlow<ImportEvent?> = _event.asStateFlow()

    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || _importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                val r = repo.import(uris, ScanMode.CUSTOM, DefaultNames.byDate(LocalDateTime.now(), prefix = "Импорт"))
                _message.value = importMessage(r)
                if (r.documentIds.size == 1 && r.rejected.isEmpty()) _event.value = ImportEvent.Open(r.documentIds.single())
            } finally {
                _importing.value = false
            }
        }
    }

    fun consumeEvent() { _event.value = null }
    fun clearMessage() { _message.value = null }

    suspend fun thumbnail(pageId: String?): ImageBitmap? = thumbs.load(pageId)?.asImageBitmap()
}
