package ru.scanner291.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import ru.scanner291.core.common.text.HumanDate
import ru.scanner291.core.storage.DocumentRepository
import ru.scanner291.core.storage.SearchResults
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.ScanTextField
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.SectionTitle
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Type
import javax.inject.Inject

/** Строка результата поиска: документ, подпись, фрагмент текста. */
data class SearchItem(val docId: String, val title: String, val subtitle: String, val snippet: String?)

sealed interface SearchUi {
    data object Idle : SearchUi
    data class Results(val query: String, val titles: List<SearchItem>, val text: List<SearchItem>) : SearchUi
}

/** Объединение результатов: совпадения в тексте группируются по документу (первый фрагмент + число страниц). Покрыто тестом. */
fun toUi(query: String, r: SearchResults, titleOf: Map<String, String>): SearchUi.Results {
    val titles = r.titles.map { SearchItem(it.id, it.title, HumanDate.format(
        java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.updatedAt), java.time.ZoneId.systemDefault()),
        java.time.LocalDate.now()), null) }
    val text = r.text.groupBy { it.docId }.map { (docId, hits) ->
        SearchItem(docId, titleOf[docId] ?: "Документ", if (hits.size == 1) "Совпадение на 1 странице" else "Совпадения на ${hits.size} стр.", hits.first().snippet)
    }
    return SearchUi.Results(query, titles, text)
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val repo: DocumentRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val ui: StateFlow<SearchUi> = query.debounce(250).mapLatest { q ->
        if (q.isBlank()) SearchUi.Idle
        else {
            val r = repo.search(q)
            val titles = r.text.map { it.docId }.distinct().associateWith { repo.document(it)?.doc?.title ?: "Документ" }
            toUi(q.trim(), r, titles)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUi.Idle)
}

/** «[совпадение]» во фрагменте FTS → полужирный акцентный текст. */
fun highlightSnippet(s: String, accent: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        val a = s.indexOf('[', i); if (a < 0) { append(s.substring(i)); break }
        val b = s.indexOf(']', a); if (b < 0) { append(s.substring(i)); break }
        append(s.substring(i, a))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = accent)) { append(s.substring(a + 1, b)) }
        i = b + 1
    }
}

@Composable
internal fun SearchRouteScreen(onOpenDocument: (String) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val q by vm.query.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    SearchScreen(q, { vm.query.value = it }, ui, onOpenDocument)
}

/** Поиск по названиям и распознанному тексту — офлайн (Room FTS4, без учёта регистра, по началу слов). */
@Composable
fun SearchScreen(query: String, onQuery: (String) -> Unit, ui: SearchUi, onOpenDocument: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScanTopBar(title = "Поиск", large = true)
        Column(Modifier.padding(horizontal = Space.s4)) {
            ScanTextField(query, onQuery, placeholder = "Название, ФИО или текст документа", leadingIcon = Icons.Rounded.Search)
            Spacer(Modifier.height(Space.s4))
        }
        when (ui) {
            SearchUi.Idle -> EmptyState(Icons.Rounded.Search, "Поиск по всей библиотеке", "Ищет по названиям и распознанному тексту. Работает без интернета.")
            is SearchUi.Results -> if (ui.titles.isEmpty() && ui.text.isEmpty()) {
                EmptyState(Icons.Rounded.SearchOff, "Ничего не найдено", "По запросу «${ui.query}» ничего нет. Текст ищется только на распознанных страницах.")
            } else LazyColumn(contentPadding = PaddingValues(bottom = Space.s8 * 2)) {
                if (ui.titles.isNotEmpty()) item { SectionTitle("В названиях", Modifier.padding(horizontal = Space.s4)) }
                items(ui.titles, key = { "t" + it.docId }) { Item(it, onOpenDocument) }
                if (ui.text.isNotEmpty()) item { SectionTitle("В тексте документов", Modifier.padding(horizontal = Space.s4)) }
                items(ui.text, key = { "x" + it.docId }) { Item(it, onOpenDocument) }
            }
        }
    }
}

@Composable
private fun Item(it: SearchItem, onOpen: (String) -> Unit) {
    val c = ScannerTheme.colors
    Column(Modifier.fillMaxWidth().clickable { onOpen(it.docId) }.padding(horizontal = Space.s4, vertical = Space.s3)) {
        Text(it.title, style = Type.bodyLg, color = c.text, maxLines = 1)
        Text(it.subtitle, style = Type.caption, color = c.textSecondary)
        it.snippet?.let { s -> Text(highlightSnippet(s, c.accentText), style = Type.body, color = c.textSecondary, maxLines = 3) }
    }
}
