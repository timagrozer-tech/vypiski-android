package ru.scanner291.feature.export

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import ru.scanner291.core.common.files.FileKind
import ru.scanner291.core.common.files.ImportCheck
import ru.scanner291.core.common.files.checkImport
import ru.scanner291.core.pdf.PdfEngine
import ru.scanner291.core.pdf.rasterCompress
import ru.scanner291.designsystem.ScanSegmented
import ru.scanner291.designsystem.ScanTextField
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Status
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.ToolTile
import ru.scanner291.designsystem.Type
import java.io.File
import java.util.UUID
import javax.inject.Inject

@Serializable data object PdfToolsRoute

fun NavGraphBuilder.pdfToolsScreen(onBack: () -> Unit) {
    composable<PdfToolsRoute> { PdfToolsRouteScreen(onBack) }
}

enum class ToolKind { MERGE, SPLIT, COMPRESS, ROTATE, EXTRACT, INSERT, PROTECT, EXPORT }

/** [ready] = false — инструмент появится на указанном этапе (плитка не нажимается, причина видна). */
data class PdfTool(val kind: ToolKind, val icon: ImageVector, val title: String, val description: String, val ready: Boolean = true)

val PdfTools = listOf(
    PdfTool(ToolKind.MERGE, Icons.AutoMirrored.Rounded.CallMerge, "Объединить", "Несколько PDF в один"),
    PdfTool(ToolKind.SPLIT, Icons.AutoMirrored.Rounded.CallSplit, "Разделить", "По диапазонам страниц в папку"),
    PdfTool(ToolKind.COMPRESS, Icons.Rounded.Compress, "Сжать", "Уменьшить размер (страницы станут изображениями)"),
    PdfTool(ToolKind.ROTATE, Icons.Rounded.RotateRight, "Повернуть", "Страницы на 90°, 180°, 270°"),
    PdfTool(ToolKind.EXTRACT, Icons.Rounded.FileUpload, "Извлечь и переставить", "Выбранные страницы в новом порядке"),
    PdfTool(ToolKind.INSERT, Icons.Rounded.NoteAdd, "Добавить", "Страницы другого PDF в нужное место"),
    PdfTool(ToolKind.PROTECT, Icons.Rounded.Lock, "Защитить", "Пароль — этап 18 (безопасность)", ready = false),
    PdfTool(ToolKind.EXPORT, Icons.Rounded.IosShare, "Экспорт", "PDF, JPG, PNG, TXT — этап 17", ready = false),
)

/**
 * Страницы по строке «1-3, 5, 9-7» (с 1; обратный диапазон — в обратном порядке; пусто или «все» — все).
 * null — ошибка (с текстом в [onError]). Покрыто тестом.
 */
fun parsePages(spec: String, count: Int, onError: (String) -> Unit = {}): List<Int>? {
    val s = spec.trim().lowercase()
    if (s.isEmpty() || s == "все") return (0 until count).toList()
    val out = ArrayList<Int>()
    for (part in s.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }) {
        val m = Regex("^(\\d+)\\s*[-–]\\s*(\\d+)$").find(part)
        val range = when {
            m != null -> { val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); if (a <= b) (a..b).toList() else (a downTo b).toList() }
            part.matches(Regex("\\d+")) -> listOf(part.toInt())
            else -> { onError("Не понял «$part»: пишите номера и диапазоны, например 1-3, 5"); return null }
        }
        if (range.any { it !in 1..count }) { onError("В документе $count стр.: «$part» вне диапазона"); return null }
        out += range.map { it - 1 }
    }
    return out.ifEmpty { null }
}

/** Диапазоны для «Разделить»: каждая часть списка через запятую — отдельный файл. Покрыто тестом. */
fun parseRanges(spec: String, count: Int, onError: (String) -> Unit = {}): List<List<Int>>? =
    spec.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.map { parsePages(it, count, onError) ?: return null }.ifEmpty { null }

data class Pending(val kind: ToolKind, val files: List<File>, val names: List<String>, val pageCount: Int)
data class ToolsUi(val busy: Boolean = false, val pending: Pending? = null, val message: String? = null, val error: Boolean = false)

@HiltViewModel
class PdfToolsViewModel @Inject constructor(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val engine by lazy { PdfEngine(ctx) }
    private val work get() = File(ctx.cacheDir, "pdf-tools").apply { mkdirs() }
    private val _ui = MutableStateFlow(ToolsUi())
    val ui: StateFlow<ToolsUi> = _ui

    /** Копия выбранных PDF во временную папку с проверкой (только PDF, ≤ 200 МБ, ≤ 2000 стр.). Оригиналы не меняются. */
    fun picked(kind: ToolKind, uris: List<Uri>) {
        if (uris.isEmpty()) return
        job {
            val files = uris.map { copyIn(it) }
            val count = engine.pageCount(files.first())
            val prev = _ui.value.pending
            if (kind == ToolKind.INSERT && prev?.kind == ToolKind.INSERT && prev.files.size == 1)
                _ui.value = _ui.value.copy(pending = prev.copy(files = prev.files + files, names = prev.names + uris.map(::nameOf)))
            else _ui.value = _ui.value.copy(pending = Pending(kind, files, uris.map(::nameOf), count))
        }
    }

    fun cancel() { _ui.value = ToolsUi() }

    /** Выполнить и записать результат в выбранное пользователем место (файл или папку для «Разделить»). */
    fun execute(target: Uri, spec: String, degrees: Int, position: Int) {
        val p = _ui.value.pending ?: return
        var err: String? = null
        val onErr: (String) -> Unit = { err = it }
        job {
            val out = File(work, "out-${UUID.randomUUID()}.pdf")
            when (p.kind) {
                ToolKind.MERGE -> engine.merge(p.files, out)
                ToolKind.COMPRESS -> engine.rasterCompress(p.files.first(), out)
                ToolKind.ROTATE -> { val pages = parsePages(spec, p.pageCount, onErr) ?: error(err!!); engine.rotate(p.files.first(), pages.toSet(), degrees, out) }
                ToolKind.EXTRACT -> { val pages = parsePages(spec, p.pageCount, onErr) ?: error(err!!); engine.select(p.files.first(), pages, out) }
                ToolKind.INSERT -> { require(p.files.size == 2) { "Выберите PDF для вставки" }; engine.insert(p.files[0], p.files[1], position.coerceIn(0, p.pageCount), out) }
                ToolKind.SPLIT -> {
                    val ranges = parseRanges(spec, p.pageCount, onErr) ?: error(err!!)
                    val dir = DocumentFile.fromTreeUri(ctx, target) ?: error("Папка недоступна")
                    val base = p.names.first().removeSuffix(".pdf")
                    ranges.forEachIndexed { i, r ->
                        val part = File(work, "part-${UUID.randomUUID()}.pdf"); engine.select(p.files.first(), r, part)
                        val name = uniqueName(dir, "$base (часть ${i + 1}).pdf")
                        val doc = dir.createFile("application/pdf", name) ?: error("Не удалось создать $name")
                        copyOut(part, doc.uri); part.delete()
                    }
                    return@job "Сохранено файлов: ${ranges.size}"
                }
                else -> error("Инструмент недоступен")
            }
            copyOut(out, target); out.delete()
            "Готово: ${nameOf(target)}"
        }
    }

    private fun uniqueName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val base = name.removeSuffix(".pdf")
        var i = 2
        while (dir.findFile("$base ($i).pdf") != null) i++
        return "$base ($i).pdf"
    }

    private fun job(block: suspend () -> Any?) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, message = null, error = false)
            val r = withContext(Dispatchers.IO) { runCatching { block() } }
            _ui.value = r.fold(
                { v -> if (v is String) ToolsUi(message = v) else _ui.value.copy(busy = false) },
                { e -> _ui.value.copy(busy = false, message = e.message ?: "Ошибка обработки PDF", error = true) },
            )
        }
    }

    private fun copyIn(uri: Uri): File {
        val cr = ctx.contentResolver
        val size = cr.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it >= 0 }
        val header = cr.openInputStream(uri)?.use { s -> ByteArray(16).let { b -> val n = s.read(b); if (n <= 0) ByteArray(0) else b.copyOf(n) } } ?: ByteArray(0)
        when (val c = checkImport(uri.scheme, size, header)) {
            is ImportCheck.Rejected -> error(c.reason)
            is ImportCheck.Ok -> require(c.kind == FileKind.PDF) { "Нужен PDF-файл" }
        }
        val f = File(work, "in-${UUID.randomUUID()}.pdf")
        cr.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return f
    }

    private fun copyOut(f: File, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri, "w")!!.use { o -> f.inputStream().use { it.copyTo(o) } }
    }

    private fun nameOf(uri: Uri): String = DocumentFile.fromSingleUri(ctx, uri)?.name ?: "Документ.pdf"
}

@Composable
internal fun PdfToolsRouteScreen(onBack: () -> Unit, vm: PdfToolsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(ToolKind.MERGE) }
    var spec by remember { mutableStateOf("") }
    var degrees by remember { mutableStateOf(90) }
    var position by remember { mutableStateOf("") }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.picked(kind, it) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.picked(kind, listOf(u)) } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        it?.let { u -> vm.execute(u, spec, degrees, (position.toIntOrNull() ?: Int.MAX_VALUE) - 1) }
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { u -> vm.execute(u, spec, degrees, 0) } }
    PdfToolsScreen(ui, onBack, onTool = { t ->
        kind = t.kind; spec = ""; position = ""
        if (t.kind == ToolKind.MERGE) pickMany.launch(arrayOf("application/pdf")) else pickOne.launch(arrayOf("application/pdf"))
    })
    ui.pending?.let { p ->
        PendingDialog(p, spec, { spec = it }, degrees, { degrees = it }, position, { position = it },
            onPickSecond = { pickOne.launch(arrayOf("application/pdf")) },
            onRun = {
                if (p.kind == ToolKind.SPLIT) folder.launch(null)
                else save.launch(p.names.first().removeSuffix(".pdf") + when (p.kind) {
                    ToolKind.MERGE -> " (объединённый).pdf"; ToolKind.COMPRESS -> " (сжатый).pdf"; ToolKind.ROTATE -> " (повёрнутый).pdf"
                    ToolKind.EXTRACT -> " (страницы).pdf"; else -> " (изменённый).pdf"
                })
            },
            onCancel = vm::cancel)
    }
}

/** PDF-инструменты (этап 12). Результат всегда сохраняется новым файлом в выбранное место; исходники не меняются. */
@Composable
fun PdfToolsScreen(ui: ToolsUi, onBack: () -> Unit, onTool: (PdfTool) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScanTopBar(title = "PDF-инструменты", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(Space.s4),
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ScannerTheme.colors.accent)
                    ui.message?.let { StatusLine(if (ui.error) Status.WARNING else Status.SUCCESS, it) }
                    Text("Исходные файлы не меняются: результат сохраняется новым файлом.", style = Type.caption, color = ScannerTheme.colors.textSecondary)
                }
            }
            items(PdfTools, key = { it.title }) { t ->
                ToolTile(t.icon, t.title, t.description, onClick = { onTool(t) }, enabled = t.ready && !ui.busy)
            }
        }
    }
}

@Composable
private fun PendingDialog(
    p: Pending, spec: String, onSpec: (String) -> Unit, degrees: Int, onDegrees: (Int) -> Unit, position: String, onPosition: (String) -> Unit,
    onPickSecond: () -> Unit, onRun: () -> Unit, onCancel: () -> Unit,
) {
    val title = PdfTools.first { it.kind == p.kind }.title
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text("${p.names.joinToString(", ")} · ${p.pageCount} стр.", style = Type.caption)
                when (p.kind) {
                    ToolKind.ROTATE -> {
                        ScanTextField(spec, onSpec, "Страницы: все или 1-3, 5")
                        ScanSegmented(listOf(90, 180, 270), degrees, { "$it°" }, onDegrees)
                    }
                    ToolKind.EXTRACT -> ScanTextField(spec, onSpec, "Страницы в нужном порядке: 3, 1-2")
                    ToolKind.SPLIT -> ScanTextField(spec, onSpec, "Части через запятую: 1-3, 4-6")
                    ToolKind.INSERT -> {
                        if (p.files.size < 2) TextButton(onPickSecond) { Text("Выбрать PDF для вставки…") }
                        else Text("Вставляется: ${p.names[1]}", style = Type.body)
                        ScanTextField(position, onPosition, "Перед страницей № (пусто — в конец)")
                    }
                    ToolKind.COMPRESS -> StatusLine(Status.WARNING, "Страницы станут изображениями 150 dpi: текст перестанет выделяться и искаться")
                    else -> Unit
                }
            }
        },
        confirmButton = { TextButton(onRun, enabled = p.kind != ToolKind.INSERT || p.files.size == 2) { Text(if (p.kind == ToolKind.SPLIT) "Выбрать папку…" else "Сохранить как…") } },
        dismissButton = { TextButton(onCancel) { Text("Отмена") } },
    )
}
