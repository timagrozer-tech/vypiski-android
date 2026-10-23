package ru.scanner291.feature.export

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import ru.scanner291.core.common.export.ExportMath
import ru.scanner291.core.common.export.ExportMode
import ru.scanner291.core.common.model.QualityProfile
import ru.scanner291.core.common.watermark.WatermarkSettings
import ru.scanner291.core.intelligence.fio.FioExtractor
import ru.scanner291.core.intelligence.naming.FileNameSanitizer
import ru.scanner291.core.intelligence.naming.SmartNaming
import ru.scanner291.core.intelligence.naming.UniqueNameResolver
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.prefs.UserPreferencesRepository
import ru.scanner291.designsystem.ButtonVariant
import ru.scanner291.designsystem.FioToggle
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScanSegmented
import ru.scanner291.designsystem.ScanTextField
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.SectionTitle
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Status
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.ToggleRow
import ru.scanner291.designsystem.Type
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject

@Serializable data class ExportRoute(val docId: String)

fun NavGraphBuilder.exportScreen(onBack: () -> Unit) {
    composable<ExportRoute> { ExportRouteScreen(onBack) }
}

/** «≈ 1,2 МБ (±25 %)»; null → «размер будет известен после экспорта». Покрыто тестом. */
fun sizeText(bytes: Long?): String = if (bytes == null) "Размер будет известен после экспорта" else {
    val s = when {
        bytes >= 1024L * 1024 -> String.format(java.util.Locale.ROOT, "%.1f МБ", bytes / 1048576.0).replace('.', ',')
        else -> "${(bytes + 1023) / 1024} КБ"
    }
    "Примерно $s (±${ExportMath.SIZE_ERROR_PERCENT} %)"
}

fun profileShort(p: QualityProfile) = when (p) {
    QualityProfile.COMPACT -> "Компакт"; QualityProfile.STANDARD -> "Стандарт"; QualityProfile.HIGH -> "Высокое"; QualityProfile.LOSSLESS -> "Без сжатия"
}

data class ExportUi(
    val title: String = "", val mode: ExportMode = ExportMode.PDF_OCR, val profile: QualityProfile = QualityProfile.HIGH,
    val watermark: WatermarkSettings = WatermarkSettings.DEFAULT, val nameByFio: Boolean = true, val name: String = "",
    val nameHint: String? = null, val estimate: Long? = null, val estimating: Boolean = false,
    val running: Boolean = false, val done: Int = 0, val total: Int = 0,
    val message: String? = null, val warnings: List<String> = emptyList(), val error: Boolean = false,
    val ready: ExportResult? = null,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val repo: DocumentRepository,
    private val service: ExportService,
    private val prefs: UserPreferencesRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    private val docId = saved.toRoute<ExportRoute>().docId
    private val _ui = MutableStateFlow(ExportUi())
    val ui: StateFlow<ExportUi> = _ui
    private var job: Job? = null; private var estJob: Job? = null

    init {
        viewModelScope.launch {
            val p = prefs.preferences.first()
            val d = repo.document(docId)
            _ui.value = _ui.value.copy(title = d?.doc?.title ?: "", watermark = p.watermark, nameByFio = p.nameByFio)
            proposeName(); estimate()
        }
    }

    /** Имя: ФИО из распознанного текста (если галочка включена и найдено уверенно), иначе — название документа. */
    private suspend fun proposeName() {
        val d = repo.document(docId) ?: return
        val text = d.orderedPages.mapNotNull { repo.text(it.id)?.text }.joinToString("\n")
        val fio = if (text.isBlank()) null else withContext(Dispatchers.Default) { FioExtractor().extract(text) }
        val prop = SmartNaming.propose(_ui.value.nameByFio, fio, null, LocalDateTime.now())
        val name = if (prop.source == ru.scanner291.core.intelligence.naming.NameSource.FIO) prop.baseName else FileNameSanitizer.sanitizeBase(d.doc.title)
        val hint = when {
            !_ui.value.nameByFio -> null
            text.isBlank() -> "ФИО не определено: текст не распознан. Имя — по названию документа"
            prop.source != ru.scanner291.core.intelligence.naming.NameSource.FIO -> "ФИО не найдено уверенно — проверьте имя"
            else -> "Имя по ФИО — проверьте перед сохранением"
        }
        _ui.value = _ui.value.copy(name = name, nameHint = hint)
    }

    fun setMode(m: ExportMode) { _ui.value = _ui.value.copy(mode = m, ready = null); estimate() }
    fun setProfile(p: QualityProfile) { _ui.value = _ui.value.copy(profile = p, ready = null); estimate() }
    fun setWatermark(on: Boolean) { _ui.value = _ui.value.copy(watermark = _ui.value.watermark.copy(enabled = on), ready = null) }
    fun setName(n: String) { _ui.value = _ui.value.copy(name = n, ready = null) }
    fun setNameByFio(v: Boolean) { viewModelScope.launch { prefs.setNameByFio(v); _ui.value = _ui.value.copy(nameByFio = v); proposeName() } }

    private fun estimate() {
        estJob?.cancel()
        estJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(estimating = true)
            val e = runCatching { service.estimate(docId, opts()) }.getOrNull()
            _ui.value = _ui.value.copy(estimate = e, estimating = false)
        }
    }

    private fun opts() = _ui.value.let { ExportOptions(it.mode, it.profile, it.watermark, FileNameSanitizer.sanitizeBase(it.name.ifBlank { it.title })) }

    /** Подготовка файлов (с проверкой) → дальше «Сохранить» или «Поделиться». */
    fun prepare(then: (ExportResult) -> Unit) {
        if (_ui.value.running) return
        job = viewModelScope.launch {
            _ui.value = _ui.value.copy(running = true, message = null, warnings = emptyList(), error = false, ready = null)
            val r = runCatching { service.export(docId, opts()) { d, t -> _ui.value = _ui.value.copy(done = d, total = t) } }
            _ui.value = r.fold(
                { _ui.value.copy(running = false, ready = it, warnings = it.warnings, message = "Готово и проверено: ${it.files.size} файл(ов), ${sizeText(it.totalBytes).removePrefix("Примерно ").substringBefore(" (")}, " +
                    "обработка ${String.format(java.util.Locale.ROOT, "%.1f", it.msPerPage / 1000.0).replace('.', ',')} с/стр.") },
                { _ui.value.copy(running = false, error = true, message = it.message ?: "Экспорт не удался") },
            )
            r.getOrNull()?.let(then)
        }
    }

    fun cancel() { job?.cancel(); _ui.value = _ui.value.copy(running = false, message = "Экспорт отменён") }

    /** Запись в выбранное место: один файл — в созданный документ, несколько — в папку с уникальными именами. */
    fun saveTo(uri: Uri, isFolder: Boolean) {
        val r = _ui.value.ready ?: return
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { runCatching {
                if (!isFolder) copy(r.files.single().second, uri)
                else {
                    val dir = DocumentFile.fromTreeUri(context, uri) ?: error("Папка недоступна")
                    val taken = dir.listFiles().mapNotNull { it.name }.toMutableSet()
                    for ((name, f) in r.files) {
                        val unique = UniqueNameResolver.resolve(name.substringBeforeLast('.'), name.substringAfterLast('.'), taken)
                        val doc = dir.createFile(mimeOf(unique), unique) ?: error("Не удалось создать $unique")
                        copy(f, doc.uri)
                    }
                }
            } }
            _ui.value = _ui.value.copy(message = res.fold({ "Сохранено" }, { "Не сохранено: ${it.message}" }), error = res.isFailure)
        }
    }

    private fun copy(f: File, uri: Uri) { context.contentResolver.openOutputStream(uri, "w")!!.use { o -> f.inputStream().use { it.copyTo(o) } } }

    fun shareIntent(r: ExportResult): Intent {
        val uris = ArrayList(r.files.map { (name, f) ->
            val named = File(f.parentFile, name).also { if (!it.exists()) f.copyTo(it) }
            FileProvider.getUriForFile(context, context.packageName + ".export", named)
        })
        val mime = mimeOf(r.files.first().first)
        return (if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris))
            .setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun mimeOf(name: String) = when (name.substringAfterLast('.').lowercase()) {
    "pdf" -> "application/pdf"; "jpg" -> "image/jpeg"; "png" -> "image/png"; "txt" -> "text/plain"; else -> "application/octet-stream"
}

@Composable
internal fun ExportRouteScreen(onBack: () -> Unit, vm: ExportViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val saveOne = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { it?.let { u -> vm.saveTo(u, false) } }
    val saveMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { u -> vm.saveTo(u, true) } }
    val onSave = { vm.prepare { r -> if (r.files.size == 1) saveOne.launch(r.files.single().first) else saveMany.launch(null) } }
    val onShare = { vm.prepare { r -> ctx.startActivity(Intent.createChooser(vm.shareIntent(r), "Поделиться")) } }
    Column(Modifier.fillMaxSize()) {
        ScanTopBar(title = "Экспорт", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(ui.title, style = Type.body, color = ScannerTheme.colors.textSecondary)
            SectionTitle("Формат")
            ExportMode.entries.forEach { m ->
                Row(Modifier.fillMaxWidth().clickable { vm.setMode(m) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(ui.mode == m, { vm.setMode(m) }, colors = RadioButtonDefaults.colors(selectedColor = ScannerTheme.colors.accent))
                    Text(m.title, style = Type.bodyLg, color = ScannerTheme.colors.text)
                }
            }
            Text("DOCX и XLSX появятся, когда их качество будет измерено.", style = Type.caption, color = ScannerTheme.colors.textSecondary)
            SectionTitle("Качество")
            ScanSegmented(QualityProfile.entries, ui.profile, ::profileShort, vm::setProfile)
            Text(if (ui.estimating) "Оценка размера…" else sizeText(ui.estimate), style = Type.caption, color = ScannerTheme.colors.textSecondary)
            ToggleRow("Водяной знак «В/ч 65384»", ui.watermark.enabled, vm::setWatermark, description = "Слева внизу, 20 %. Оригиналы не меняются")
            SectionTitle("Имя файла")
            FioToggle(ui.nameByFio, vm::setNameByFio)
            ScanTextField(ui.name, vm::setName, "Имя файла")
            ui.nameHint?.let { Text(it, style = Type.caption, color = ScannerTheme.colors.textSecondary) }
        }
        Column(Modifier.navigationBarsPadding().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            if (ui.running) {
                Text("Страница ${minOf(ui.done + 1, ui.total)} из ${ui.total}…", style = Type.body, color = ScannerTheme.colors.text)
                LinearProgressIndicator(progress = { if (ui.total == 0) 0f else ui.done.toFloat() / ui.total }, modifier = Modifier.fillMaxWidth(), color = ScannerTheme.colors.accent)
                ScanButton("Отменить", vm::cancel, Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary)
            } else {
                ui.message?.let { StatusLine(if (ui.error) Status.WARNING else Status.SUCCESS, it) }
                ui.warnings.forEach { StatusLine(Status.WARNING, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    ScanButton("Поделиться", onShare, Modifier.weight(1f), variant = ButtonVariant.Secondary, icon = Icons.Rounded.IosShare)
                    ScanButton("Сохранить…", onSave, Modifier.weight(1f), icon = Icons.Rounded.Save)
                }
            }
        }
    }
}
