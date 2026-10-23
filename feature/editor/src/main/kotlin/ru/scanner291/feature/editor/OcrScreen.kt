package ru.scanner291.feature.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.scanner291.core.common.render.RecipeCodec
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.database.PageTextEntity
import ru.scanner291.core.ocr.OcrMode
import ru.scanner291.core.ocr.OcrService
import ru.scanner291.core.ocr.Tessdata
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.recipeFor
import ru.scanner291.core.storage.scanModeOf
import ru.scanner291.designsystem.ButtonVariant
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScanSegmented
import ru.scanner291.designsystem.ScanTextField
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Status
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.Type
import javax.inject.Inject

/** Состояние текста страницы для экрана. */
enum class TextStatus { NONE, DONE, EDITED, STALE }

data class OcrPageUi(val page: PageEntity, val index: Int, val text: PageTextEntity?, val status: TextStatus)

sealed interface OcrUi {
    data object Loading : OcrUi
    data object NotFound : OcrUi
    data class Ready(val title: String, val pages: List<OcrPageUi>) : OcrUi
}

data class OcrRun(val mode: OcrMode = OcrMode.FAST, val bestInstalled: Boolean = false, val running: Boolean = false,
                  val done: Int = 0, val total: Int = 0, val message: String? = null, val error: Boolean = false)

/** Статус текста: нет / распознан / исправлен / устарел (страницу обработали иначе после OCR). Покрыто тестом. */
fun textStatus(t: PageTextEntity?, currentRecipeKey: String): TextStatus = when {
    t == null -> TextStatus.NONE
    t.edited -> TextStatus.EDITED
    t.recipeKey != currentRecipeKey -> TextStatus.STALE
    else -> TextStatus.DONE
}

fun statusText(p: OcrPageUi): String = when (p.status) {
    TextStatus.NONE -> "Не распознана"
    TextStatus.DONE -> "Распознана: ${OcrMode.entries.firstOrNull { it.name == p.text?.mode }?.title ?: ""}, ${"%.1f".format((p.text?.durationMs ?: 0) / 1000.0).replace('.', ',')} с"
    TextStatus.EDITED -> "Исправлена вручную"
    TextStatus.STALE -> "Страница обработана иначе — распознайте заново"
}

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val ocr: OcrService,
    private val tessdata: Tessdata,
    saved: SavedStateHandle,
) : ViewModel() {
    private val docId = saved.toRoute<OcrRoute>().docId
    private val _run = MutableStateFlow(OcrRun())
    val run: StateFlow<OcrRun> = _run
    private var job: Job? = null

    val ui: StateFlow<OcrUi> = combine(repo.observeDocument(docId), repo.observeTexts(docId)) { d, texts ->
        if (d == null || d.pages.isEmpty()) OcrUi.NotFound
        else {
            val mode = scanModeOf(d.doc.mode)
            val byPage = texts.associateBy { it.pageId }
            OcrUi.Ready(d.doc.title, d.orderedPages.mapIndexed { i, p ->
                val t = byPage[p.id]
                OcrPageUi(p, i, t, textStatus(t, RecipeCodec.cacheKey(p.recipeFor(mode))))
            })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OcrUi.Loading)

    init { viewModelScope.launch { refreshBest() } }

    private suspend fun refreshBest() {
        val ok = withContext(Dispatchers.IO) { runCatching { tessdata.isInstalled(OcrMode.BEST) }.getOrDefault(false) }
        _run.value = _run.value.copy(bestInstalled = ok, mode = if (!ok && _run.value.mode == OcrMode.BEST) OcrMode.FAST else _run.value.mode)
    }

    fun setMode(m: OcrMode) { _run.value = _run.value.copy(mode = m, message = null) }

    /** Распознать страницы (все или одну). Исправленные вручную пропускаются при «все» — правки не теряются. */
    fun recognize(only: PageEntity? = null) {
        val st = ui.value as? OcrUi.Ready ?: return
        if (_run.value.running) return
        val d = st.pages.filter { only == null && it.status != TextStatus.EDITED || it.page.id == only?.id }
        job = viewModelScope.launch {
            val doc = repo.document(docId) ?: return@launch
            val sm = scanModeOf(doc.doc.mode)
            _run.value = _run.value.copy(running = true, done = 0, total = d.size, message = null, error = false)
            var failed = 0
            for ((i, p) in d.withIndex()) {
                if (ocr.recognizePage(p.page, p.page.recipeFor(sm), _run.value.mode).isFailure) failed++
                _run.value = _run.value.copy(done = i + 1)
            }
            _run.value = _run.value.copy(running = false, error = failed > 0,
                message = if (failed == 0) "Готово: ${d.size} стр." else "Не удалось распознать: $failed из ${d.size}")
        }
    }

    fun cancel() { job?.cancel(); _run.value = _run.value.copy(running = false, message = "Остановлено") }

    fun saveText(pageId: String, text: String) { viewModelScope.launch { repo.editText(pageId, text); _run.value = _run.value.copy(message = "Текст сохранён") } }

    fun importBest(uri: Uri) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { tessdata.importBestPack(uri) } }
            refreshBest()
            _run.value = _run.value.copy(
                message = r.exceptionOrNull()?.let { "Пакет не установлен: ${it.message}" } ?: "Пакет «Высокая точность» установлен",
                error = r.isFailure, mode = if (r.isSuccess) OcrMode.BEST else _run.value.mode,
            )
        }
    }
}

@Composable
internal fun OcrRouteScreen(onBack: () -> Unit, vm: OcrViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val run by vm.run.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importBest) }
    OcrScreen(ui, run, onBack, vm::setMode, { vm.recognize() }, { vm.recognize(it) }, vm::cancel, vm::saveText,
        { pick.launch(arrayOf("application/zip", "application/octet-stream")) })
}

/** Распознанный текст документа: OCR страниц и всего документа, правка, копирование. Офлайн. */
@Composable
fun OcrScreen(
    ui: OcrUi, run: OcrRun, onBack: () -> Unit, onMode: (OcrMode) -> Unit, onAll: () -> Unit,
    onPage: (PageEntity) -> Unit, onCancel: () -> Unit, onSave: (String, String) -> Unit, onImportBest: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize()) {
        when (ui) {
            OcrUi.Loading -> { ScanTopBar(title = "Распознанный текст", onBack = onBack)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ScannerTheme.colors.accent) } }
            OcrUi.NotFound -> { ScanTopBar(title = "Распознанный текст", onBack = onBack)
                EmptyState(Icons.Rounded.DocumentScanner, "Документ не найден", DocScreen.Ocr.hint) }
            is OcrUi.Ready -> {
                val all = ui.pages.mapNotNull { it.text?.text?.takeIf(String::isNotBlank) }.joinToString("\n\n")
                ScanTopBar(title = "Распознанный текст", onBack = onBack) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(all)) }, enabled = all.isNotEmpty()) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Копировать весь текст")
                    }
                }
                Column(Modifier.padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    ScanSegmented(OcrMode.entries, run.mode, { it.title }, onMode)
                    if (run.mode == OcrMode.BEST && !run.bestInstalled) {
                        StatusLine(Status.WARNING, "Нужен пакет моделей «Высокая точность» (файл .zip, ~30 МБ, без интернета)")
                        ScanButton("Установить пакет из файла…", onImportBest, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary, icon = Icons.Rounded.Download)
                    } else if (run.running) {
                        Text("Распознаётся страница ${minOf(run.done + 1, run.total)} из ${run.total}…", style = Type.body, color = ScannerTheme.colors.text)
                        LinearProgressIndicator(progress = { if (run.total == 0) 0f else run.done.toFloat() / run.total }, modifier = Modifier.fillMaxWidth(), color = ScannerTheme.colors.accent)
                        ScanButton("Остановить", onCancel, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
                    } else {
                        ScanButton("Распознать все страницы (${ui.pages.size})", onAll, Modifier.fillMaxWidth(), icon = Icons.Rounded.DocumentScanner)
                    }
                    run.message?.let { StatusLine(if (run.error) Status.WARNING else Status.SUCCESS, it) }
                }
                LazyColumn(contentPadding = PaddingValues(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                    items(ui.pages, key = { it.page.id }) { p -> PageText(p, run.running, onPage, onSave) }
                }
            }
        }
    }
}

@Composable
private fun PageText(p: OcrPageUi, busy: Boolean, onPage: (PageEntity) -> Unit, onSave: (String, String) -> Unit) {
    val c = ScannerTheme.colors
    var draft by remember(p.text?.text) { mutableStateOf(p.text?.text.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Страница ${p.index + 1}", style = Type.titleSm, color = c.text)
                Text(statusText(p), style = Type.caption, color = if (p.status == TextStatus.STALE) c.warning else c.textSecondary)
            }
            ScanButton(if (p.status == TextStatus.NONE) "Распознать" else "Заново", { onPage(p.page) }, enabled = !busy, variant = ButtonVariant.Secondary)
        }
        if (p.text != null) {
            ScanTextField(draft, { draft = it }, placeholder = "Текст не найден", singleLine = false, clearable = false)
            if (draft != p.text.text) ScanButton("Сохранить исправления", { onSave(p.page.id, draft) }, Modifier.fillMaxWidth())
        }
    }
}
