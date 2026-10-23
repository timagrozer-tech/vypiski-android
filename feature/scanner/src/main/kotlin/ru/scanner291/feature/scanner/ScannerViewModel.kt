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
import kotlinx.coroutines.Dispatchers
import ru.scanner291.core.camera.FrameResult
import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.geometry.QuadTransforms
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.database.CornersSource
import ru.scanner291.core.imageprocessing.BitmapDetection
import ru.scanner291.core.quality.capture.AutoCaptureStabilizer
import ru.scanner291.core.quality.capture.CaptureState
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

/** Что показать поверх превью: рамка документа (нормализованные координаты), подсказка, прогресс автосъёмки. */
data class FrameUi(val quad: Quad?, val frameW: Int, val frameH: Int, val hint: String?, val progress: Float?)

/** Подсказка по состоянию автосъёмки (README CameraControls / ТЗ v3.0 ч. VII). Покрыто тестом. */
fun captureHint(state: CaptureState): String? = when (state) {
    CaptureState.Searching -> "Наведите камеру на документ"
    is CaptureState.Aligning -> "Держите неподвижно…"
    CaptureState.Ready -> "Снимаю"
    CaptureState.Cooldown -> "Положите следующий лист"
    is CaptureState.Blocked -> when (state.reason) {
        CaptureState.Reason.TOO_SMALL -> "Поднесите камеру ближе"
        CaptureState.Reason.BLURRY -> "Изображение размыто — держите неподвижно"
        CaptureState.Reason.GLARE -> "Блик на документе — измените угол или выключите вспышку"
        CaptureState.Reason.NOT_CONVEX -> "Документ виден не полностью"
    }
}

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

    /** Автосъёмка после стабилизации; по умолчанию включена. */
    val autoCapture: StateFlow<Boolean> = saved.getStateFlow(KEY_AUTO, true)
    fun setAutoCapture(v: Boolean) { saved[KEY_AUTO] = v }

    private val _frame = MutableStateFlow<FrameUi?>(null)
    val frame: StateFlow<FrameUi?> = _frame.asStateFlow()
    private val _autoShot = MutableStateFlow(0)
    /** Увеличивается, когда стабилизатор разрешил автоснимок — экран нажимает затвор. */
    val autoShot: StateFlow<Int> = _autoShot.asStateFlow()
    private var stabilizer: AutoCaptureStabilizer? = null
    private var lastPixelQuad: Quad? = null

    /** Кадр анализа: обновить рамку и решить, пора ли снимать. */
    fun onFrame(r: FrameResult) {
        val st = stabilizer?.takeIf { it.frameW == r.frameW && it.frameH == r.frameH }
            ?: AutoCaptureStabilizer(r.frameW, r.frameH).also { stabilizer = it }
        val pixel = r.quad?.let { QuadTransforms.denormalize(it, r.frameW.toDouble(), r.frameH.toDouble()) }
        lastPixelQuad = pixel
        val state = st.onFrame(pixel, r.sharpness, r.glare, r.timeMs)
        val progress = (state as? CaptureState.Aligning)?.progress ?: if (state == CaptureState.Ready) 1f else null
        _frame.value = FrameUi(r.quad, r.frameW, r.frameH, if (autoCapture.value) captureHint(state) else null, if (autoCapture.value) progress else null)
        if (autoCapture.value && state == CaptureState.Ready && !_busy.value) {
            pixel?.let { st.onCaptured(it) }
            _autoShot.value++
        }
    }

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
                val page = repo.addCapturedPage(id, temp)
                lastPixelQuad?.let { q -> stabilizer?.onCaptured(q) }
                findCorners(page)
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

    /** Углы листа на сохранённом оригинале (уменьшенная копия ≤ 1024 px) → рецепт страницы. */
    private fun findCorners(page: PageEntity) {
        viewModelScope.launch(Dispatchers.Default) {
            val bmp = loader.load(page, 1024) ?: return@launch
            val found = runCatching { BitmapDetection.detectNormalized(bmp) }.getOrNull()
            if (found != null) repo.setCorners(page.id, found.first, CornersSource.AUTO)
        }
    }

    private companion object {
        const val KEY_DOC = "scan.docId"
        const val KEY_MODE = "scan.mode"
        const val KEY_AUTO = "scan.auto"
        const val TAG = "Scanner291Scan"
    }
}
