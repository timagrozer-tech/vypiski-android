package ru.scanner291.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.scanner291.core.intelligence.doc.DocumentAnalyzer
import ru.scanner291.core.intelligence.doc.DocumentFacts
import ru.scanner291.core.intelligence.doc.Suggestion
import ru.scanner291.core.intelligence.fio.FioExtractor
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.SectionTitle
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Status
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.Type
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Строка-предложение: ключ, поле, значение, источник, уверенность. */
data class FactRow(val key: String, val label: String, val value: String, val source: String, val confidence: Double)

sealed interface FactsUi {
    data object Loading : FactsUi
    data object NotFound : FactsUi
    data object NoText : FactsUi
    data class Ready(
        val title: String, val rows: List<FactRow>, val people: List<String>, val confirmed: Set<String>,
        val person: String?, val message: String?,
    ) : FactsUi
}

private val DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Сведения → строки экрана (порядок фиксирован). Покрыто тестом. */
fun factRows(f: DocumentFacts): List<FactRow> {
    fun <T> r(key: String, label: String, s: Suggestion<T>?, show: (T) -> String) =
        s?.let { FactRow(key, label, show(it.value), it.source, it.confidence) }
    return listOfNotNull(
        r("type", "Тип", f.type) { it.title },
        r("number", "Номер", f.number) { "№ $it" },
        r("date", "Дата", f.date) { it.format(DMY) },
        r("org", "Организация", f.organization) { it },
        r("title", "Заголовок", f.title) { it },
    ) + f.templateFields.map { (k, s) -> FactRow("tpl:$k", k, s.value, s.source, s.confidence) }
}

/** Название из подтверждённых полей: «Рапорт Волков И.И. 18.09.2026», «Приказ № 12 от 10.01.2026»; без типа — null. Покрыто тестом. */
fun nameFromConfirmed(rows: List<FactRow>, confirmed: Set<String>, person: String?): String? {
    val ok = rows.filter { it.key in confirmed }.associateBy { it.key }
    val type = ok["type"]?.value ?: return null
    val number = ok["number"]?.value
    return listOfNotNull(type, person, number, ok["date"]?.value?.let { if (number != null) "от $it" else it }).joinToString(" ")
}

@HiltViewModel
class FactsViewModel @Inject constructor(private val repo: DocumentRepository, saved: SavedStateHandle) : ViewModel() {
    private val docId = saved.toRoute<FioRoute>().docId
    private val _ui = MutableStateFlow<FactsUi>(FactsUi.Loading)
    val ui: StateFlow<FactsUi> = _ui

    init {
        viewModelScope.launch {
            val doc = repo.document(docId) ?: run { _ui.value = FactsUi.NotFound; return@launch }
            val text = doc.orderedPages.mapNotNull { repo.text(it.id)?.text }.joinToString("\n")
            if (text.isBlank()) { _ui.value = FactsUi.NoText; return@launch }
            val (facts, fio) = withContext(Dispatchers.Default) { DocumentAnalyzer().analyze(text) to FioExtractor().extract(text) }
            val people = fio.allCandidates.map { it.fio.short() }.distinct().take(5)
            _ui.value = FactsUi.Ready(doc.doc.title, factRows(facts), people, emptySet(),
                person = if (fio.autoApply) fio.primary?.fio?.short() else null, message = null)
        }
    }

    fun toggle(key: String) = update { it.copy(confirmed = if (key in it.confirmed) it.confirmed - key else it.confirmed + key, message = null) }
    fun pickPerson(p: String) = update { it.copy(person = if (it.person == p) null else p, message = null) }

    /** Переименование — только по нажатию, из подтверждённых полей. */
    fun applyName() {
        val s = _ui.value as? FactsUi.Ready ?: return
        val name = nameFromConfirmed(s.rows, s.confirmed, s.person) ?: return
        viewModelScope.launch { repo.rename(docId, name); update { it.copy(title = name, message = "Документ переименован: «$name»") } }
    }

    private fun update(f: (FactsUi.Ready) -> FactsUi.Ready) { (_ui.value as? FactsUi.Ready)?.let { _ui.value = f(it) } }
}

@Composable
internal fun FactsRouteScreen(onBack: () -> Unit, vm: FactsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    FactsScreen(ui, onBack, vm::toggle, vm::pickPerson, vm::applyName)
}

/**
 * ФИО и сведения о документе (этап 11): всё — предложения с источником; ничего не применяется само.
 * Переименование — из отмеченных пользователем полей.
 */
@Composable
fun FactsScreen(ui: FactsUi, onBack: () -> Unit, onToggle: (String) -> Unit, onPerson: (String) -> Unit, onApply: () -> Unit) {
    val c = ScannerTheme.colors
    Column(Modifier.fillMaxSize()) {
        ScanTopBar(title = "Определение ФИО", onBack = onBack)
        when (ui) {
            FactsUi.Loading -> Unit
            FactsUi.NotFound -> EmptyState(Icons.Rounded.Badge, "Документ не найден", DocScreen.Fio.hint)
            FactsUi.NoText -> EmptyState(Icons.Rounded.Badge, "Текст ещё не распознан",
                "Откройте «Распознанный текст» и распознайте страницы — ФИО и сведения определяются по тексту.")
            is FactsUi.Ready -> LazyColumn(contentPadding = PaddingValues(bottom = Space.s8)) {
                item { StatusLine(Status.WARNING, "Проверьте и отметьте верные сведения — без подтверждения ничего не применяется", Modifier.padding(Space.s4)) }
                item { SectionTitle("ФИО", Modifier.padding(horizontal = Space.s4)) }
                if (ui.people.isEmpty()) item { Text("ФИО в тексте не найдено", style = Type.body, color = c.textSecondary, modifier = Modifier.padding(horizontal = Space.s4)) }
                items(ui.people) { p -> CheckRow(p, null, ui.person == p) { onPerson(p) } }
                item { SectionTitle("Сведения о документе", Modifier.padding(horizontal = Space.s4)) }
                if (ui.rows.isEmpty()) item { Text("Тип, номер и дата не найдены", style = Type.body, color = c.textSecondary, modifier = Modifier.padding(horizontal = Space.s4)) }
                items(ui.rows, key = { it.key }) { r ->
                    CheckRow("${r.label}: ${r.value}", "Из строки: «${r.source}» · ${(r.confidence * 100).toInt()} %", r.key in ui.confirmed) { onToggle(r.key) }
                }
                item {
                    val name = nameFromConfirmed(ui.rows, ui.confirmed, ui.person)
                    Column(Modifier.padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                        Text("Название: ${name ?: "отметьте хотя бы тип документа"}", style = Type.body, color = c.text)
                        ScanButton("Переименовать документ", onApply, Modifier.fillMaxWidth(), enabled = name != null && name != ui.title)
                        ui.message?.let { StatusLine(Status.SUCCESS, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(title: String, subtitle: String?, checked: Boolean, onClick: () -> Unit) {
    val c = ScannerTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.s2, vertical = Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, { onClick() }, colors = CheckboxDefaults.colors(checkedColor = c.accent))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.bodyLg, color = c.text)
            subtitle?.let { Text(it, style = Type.caption, color = c.textSecondary, maxLines = 2) }
        }
    }
}
