package ru.scanner291.feature.scanner

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.database.DocumentSource
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.intelligence.naming.DefaultNames
import ru.scanner291.core.storage.DocumentPaths
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.PageImageLoader
import ru.scanner291.core.storage.prefs.UserPreferencesRepository
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/** Текущая сессия пакетной съёмки: все снимки — страницы одного документа. */
data class SessionUi(val docId: String?, val pageCount: Int, val lastPage: PageEntity?)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val repo: DocumentRepository,
    private val paths: DocumentPaths,
    private val loader: PageImageLoader,
    private val saved: SavedStateHandle,
) : ViewModel() {
    /** Галочка «Назвать файл по ФИО» — то же состояние, что в редакторе, сохранении и экспорте. */
    val nameByFio: StateFlow<Boolean> = prefs.preferences.map { it.nameByFio }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val mode: StateFlow<ScanMode> = saved.getStateFlow(KEY_MODE, ScanMode.A4)

    /** Идентификатор документа сессии переживает выгрузку процесса (SavedStateHandle). */
    private val docId: StateFlow<String?> = saved.getStateFlow(KEY_DOC, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<SessionUi> = docId.flatMapLatest { id ->
        if (id == null) flowOf(SessionUi(null, 0, null))
        else repo.observeDocument(id).map { d ->
            val pages = d?.orderedPages.orEmpty()
            SessionUi(id, pages.size, pages.lastOrNull())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUi(null, 0, null))

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setNameByFio(v: Boolean) { viewModelScope.launch { prefs.setNameByFio(v) } }
    fun setMode(m: ScanMode) { saved[KEY_MODE] = m }
    fun clearError() { _error.value = null }

    /**
     * Снимок: камера пишет JPEG во временный файл, репозиторий превращает его в неизменяемый оригинал
     * новой страницы. Первый снимок создаёт документ с именем по дате.
     */
    fun onShutter(take: suspend (File) -> File) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val id = docId.value ?: repo.createDocument(mode.value, DocumentSource.CAMERA, DefaultNames.byDate(LocalDateTime.now()))
                    .also { saved[KEY_DOC] = it }
                paths.work.mkdirs()
                val temp = File(paths.work, "capture-${UUID.randomUUID()}.jpg")
                try {
                    take(temp)
                } catch (e: Exception) {
                    temp.delete(); throw e
                }
                repo.addCapturedPage(id, temp)
            } catch (e: Exception) {
                Log.w(TAG, "capture failed", e)
                _error.value = "Не удалось сохранить снимок. Попробуйте ещё раз."
            } finally {
                _busy.value = false
            }
        }
    }

    /** Переснять последнюю страницу: её оригинал уходит в корзину (не удаляется). */
    fun retakeLast() {
        val last = session.value.lastPage ?: return
        viewModelScope.launch {
            runCatching { repo.trashPage(last.id) }.onFailure { _error.value = "Не удалось убрать страницу" }
        }
    }

    suspend fun thumbnail(page: PageEntity): ImageBitmap? = loader.load(page, 256)?.asImageBitmap()

    private companion object {
        const val KEY_DOC = "scan.docId"
        const val KEY_MODE = "scan.mode"
        const val TAG = "Scanner291Scan"
    }
}
