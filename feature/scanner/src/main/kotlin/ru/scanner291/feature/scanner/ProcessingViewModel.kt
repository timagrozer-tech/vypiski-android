package ru.scanner291.feature.scanner

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.scanner291.core.common.geometry.Quad
import ru.scanner291.core.common.geometry.QuadCodec
import ru.scanner291.core.common.geometry.QuadTransforms
import ru.scanner291.core.common.model.EditHistory
import ru.scanner291.core.common.model.FilterType
import ru.scanner291.core.common.model.PageRecipe
import ru.scanner291.core.common.model.ScanMode
import ru.scanner291.core.common.render.RecipeCodec
import ru.scanner291.core.database.CornersSource
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.imageprocessing.BitmapDetection
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.PageImageLoader
import ru.scanner291.core.storage.ProcessedPageLoader
import ru.scanner291.core.storage.recipeFor
import ru.scanner291.core.storage.scanModeOf
import javax.inject.Inject

/** Шаги экрана обработки: края листа (этап 6) и улучшение (этап 7). */
enum class ProcessingStep(val title: String) { Corners("Края"), Enhance("Улучшение") }

sealed interface ProcessingState {
    data object Loading : ProcessingState
    data object NotFound : ProcessingState
    data class Ready(
        val title: String,
        val pageIndex: Int,
        val pageCount: Int,
        val page: PageEntity,
        val mode: ScanMode,
        val image: ImageBitmap?,
        /** Редактируемые углы (нормализованные). */
        val corners: Quad,
        val source: CornersSource?,
        val recipe: PageRecipe,
        val step: ProcessingStep = ProcessingStep.Corners,
        val processed: ImageBitmap? = null,
        val rendering: Boolean = false,
        val previews: Map<FilterType, ImageBitmap> = emptyMap(),
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val dirty: Boolean,
        val message: String?,
    ) : ProcessingState
}

/**
 * Обработка страницы: углы листа и рецепт улучшения (фильтр, поворот, тени, шум, яркость, контраст).
 * Меняется только рецепт в индексе; оригинал не трогается, результат пересчитывается из оригинала —
 * поэтому всё обратимо («Сбросить», отмена/повтор).
 */
@HiltViewModel
class ProcessingViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val loader: PageImageLoader,
    private val processed: ProcessedPageLoader,
    saved: SavedStateHandle,
) : ViewModel() {
    private val route = saved.toRoute<ProcessingRoute>()
    private val _state = MutableStateFlow<ProcessingState>(ProcessingState.Loading)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()
    private var history = EditHistory(PageRecipe())
    private var renderJob: Job? = null
    private var previewJob: Job? = null

    init { open(route.pageIndex) }

    fun open(index: Int) {
        val step = (_state.value as? ProcessingState.Ready)?.step ?: ProcessingStep.Corners
        viewModelScope.launch {
            val doc = repo.document(route.docId)
            val pages = doc?.orderedPages.orEmpty()
            if (doc == null || pages.isEmpty()) { _state.value = ProcessingState.NotFound; return@launch }
            val i = index.coerceIn(0, pages.lastIndex)
            val page = pages[i]
            val mode = scanModeOf(doc.doc.mode)
            val image = loader.load(page, 1600)?.asImageBitmap()
            val stored = QuadCodec.decode(page.corners)
            val recipe = page.recipeFor(mode)
            history = EditHistory(recipe)
            _state.value = ProcessingState.Ready(
                doc.doc.title, i, pages.size, page, mode, image,
                stored ?: QuadTransforms.FULL, page.cornersSource?.let { runCatching { CornersSource.valueOf(it) }.getOrNull() },
                recipe = recipe, step = step, dirty = false, message = null,
            )
            if (step == ProcessingStep.Enhance) { render(); renderPreviews() }
        }
    }

    fun selectStep(step: ProcessingStep) {
        update { it.copy(step = step) }
        if (step == ProcessingStep.Enhance) { render(); renderPreviews() }
    }

    // ── Края ──
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

    // ── Улучшение ──
    fun setFilter(f: FilterType) = edit { it.copy(filter = f) }
    fun rotate(deg: Int) = edit { it.rotatedBy(deg) }
    fun setShadows(on: Boolean) = edit { it.copy(removeShadows = on) }
    fun setDenoise(on: Boolean) = edit { it.copy(denoise = on) }
    /**
     * Ползунки: одно движение — одна запись в истории (изменения одного ползунка подряд, с паузой меньше
     * 1 с, сливаются); превью — с задержкой 150 мс, чтобы не рендерить каждый шаг.
     */
    fun setBrightness(v: Float) = edit(slider = "b") { it.copy(brightness = v) }
    fun setContrast(v: Float) = edit(slider = "c") { it.copy(contrast = v) }
    private var lastSlider: String? = null
    private var lastSliderAt = 0L

    /** Вернуть рецепт по умолчанию для режима документа (углы не меняются). */
    fun reset() {
        val s = _state.value as? ProcessingState.Ready ?: return
        edit { RecipeCodec.default(s.mode) }
    }

    fun undo() { history.undo(); applyHistory() }
    fun redo() { history.redo(); applyHistory() }

    private fun applyHistory() {
        update { it.copy(recipe = history.current, canUndo = history.canUndo, canRedo = history.canRedo, dirty = true, message = null) }
        render()
    }

    private fun edit(slider: String? = null, f: (PageRecipe) -> PageRecipe) {
        val s = _state.value as? ProcessingState.Ready ?: return
        val next = f(s.recipe).normalized()
        if (next == s.recipe) return
        val now = System.currentTimeMillis()
        if (slider != null && slider == lastSlider && now - lastSliderAt < 1000) history.replaceCurrent(next) else history.apply(next)
        lastSlider = slider; lastSliderAt = now
        update { it.copy(recipe = next, canUndo = history.canUndo, canRedo = history.canRedo, dirty = true, message = null) }
        render(if (slider != null) 150 else 0)
    }

    /** Превью результата (сторона 1400). Предыдущий незавершённый рендер отменяется. */
    private fun render(delayMs: Long = 0) {
        if (_state.value !is ProcessingState.Ready) return
        renderJob?.cancel()
        update { it.copy(rendering = true) }
        renderJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val cur = _state.value as? ProcessingState.Ready ?: return@launch
            val bmp = processed.load(cur.page, cur.recipe.copy(corners = cur.corners), cur.mode, 1400)
            update { it.copy(processed = bmp?.asImageBitmap(), rendering = false, message = if (bmp == null) "Не удалось обработать изображение" else it.message) }
        }
    }

    /** Миниатюры всех фильтров для ленты выбора (сторона 240). */
    private fun renderPreviews() {
        val s = _state.value as? ProcessingState.Ready ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val map = LinkedHashMap<FilterType, ImageBitmap>()
            for (f in FilterType.entries) {
                processed.load(s.page, PageRecipe(corners = s.corners, filter = f), s.mode, 240)?.let { map[f] = it.asImageBitmap() }
                update { it.copy(previews = LinkedHashMap(map)) }
            }
        }
    }

    fun save() {
        val s = _state.value as? ProcessingState.Ready ?: return
        viewModelScope.launch {
            repo.setCorners(s.page.id, s.corners, s.source ?: CornersSource.MANUAL)
            repo.setRecipe(s.page.id, s.recipe)
            update { it.copy(dirty = false, message = "Сохранено. Оригинал не изменён") }
        }
    }

    private fun update(f: (ProcessingState.Ready) -> ProcessingState.Ready) {
        (_state.value as? ProcessingState.Ready)?.let { _state.value = f(it) }
    }
}
