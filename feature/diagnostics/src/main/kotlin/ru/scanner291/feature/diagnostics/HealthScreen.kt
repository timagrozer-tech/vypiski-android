package ru.scanner291.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HealthAndSafety
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.scanner291.core.storage.HealthCenter
import ru.scanner291.core.storage.HealthIssue
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.GlassCard
import ru.scanner291.designsystem.ScanButton
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.ScannerTheme
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Status
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.Type
import javax.inject.Inject

@Serializable data object HealthRoute

fun NavGraphBuilder.healthScreen(onBack: () -> Unit) {
    composable<HealthRoute> { HealthRouteScreen(onBack) }
}

data class HealthUi(val scanning: Boolean = false, val issues: List<HealthIssue>? = null, val message: String? = null)

/** Итог проверки одной строкой. Покрыто тестом. */
fun summary(issues: List<HealthIssue>): String =
    if (issues.isEmpty()) "Проблем не найдено" else "Найдено замечаний: ${issues.size}, можно исправить: ${issues.count { it.fix != null }}"

@HiltViewModel
class HealthViewModel @Inject constructor(private val health: HealthCenter) : ViewModel() {
    private val _ui = MutableStateFlow(HealthUi())
    val ui: StateFlow<HealthUi> = _ui
    init { scan() }

    fun scan() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(scanning = true)
            val r = runCatching { health.scan() }
            _ui.value = HealthUi(false, r.getOrNull(), r.exceptionOrNull()?.let { "Проверка не выполнена: ${it.message}" })
        }
    }

    /** Только после подтверждения в диалоге. */
    fun fix(i: HealthIssue) {
        viewModelScope.launch {
            val r = runCatching { health.fix(i) }
            _ui.value = _ui.value.copy(message = r.fold({ "Выполнено: ${i.fix!!.title}" }, { "Не удалось: ${it.message}" }))
            scan()
        }
    }
}

@Composable
internal fun HealthRouteScreen(onBack: () -> Unit, vm: HealthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<HealthIssue?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScanTopBar(title = "Состояние библиотеки", onBack = onBack)
        Column(Modifier.padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            if (ui.scanning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ScannerTheme.colors.accent)
            ui.issues?.let { StatusLine(if (it.isEmpty()) Status.SUCCESS else Status.WARNING, summary(it)) }
            ui.message?.let { StatusLine(Status.SUCCESS, it) }
            Text("Проверка ничего не меняет. Исправления — только по вашему подтверждению; оригиналы страниц не изменяются.",
                style = Type.caption, color = ScannerTheme.colors.textSecondary)
            ScanButton("Проверить снова", vm::scan, Modifier.fillMaxWidth(), enabled = !ui.scanning)
        }
        val issues = ui.issues.orEmpty()
        if (ui.issues != null && issues.isEmpty()) EmptyState(Icons.Rounded.HealthAndSafety, "Всё в порядке", "Файлы на месте, суммы совпадают, поисковый индекс полный.")
        else LazyColumn(contentPadding = PaddingValues(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            itemsIndexed(issues) { _, i ->
                GlassCard {
                    Text(i.kind.title, style = Type.bodyLg, color = ScannerTheme.colors.text)
                    Text(i.detail, style = Type.caption, color = ScannerTheme.colors.textSecondary)
                    i.fix?.let { f -> TextButton({ confirm = i }) { Text(f.title + "…") } }
                }
            }
        }
    }
    confirm?.let { i ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(i.fix!!.title + "?") },
            text = { Text("${i.kind.title}: ${i.detail}. Оригиналы страниц не изменятся.") },
            confirmButton = { TextButton({ vm.fix(i); confirm = null }) { Text("Выполнить") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Отмена") } },
        )
    }
}
