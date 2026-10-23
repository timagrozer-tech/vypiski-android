package ru.scanner291.feature.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.Space
import ru.scanner291.designsystem.Status
import ru.scanner291.designsystem.StatusLine
import ru.scanner291.designsystem.ToolTile

@Serializable data object PdfToolsRoute

fun NavGraphBuilder.pdfToolsScreen(onBack: () -> Unit) {
    composable<PdfToolsRoute> { PdfToolsScreen(onBack) }
}

data class PdfTool(val icon: ImageVector, val title: String, val description: String)

val PdfTools = listOf(
    PdfTool(Icons.AutoMirrored.Rounded.CallMerge, "Объединить", "Несколько PDF в один"),
    PdfTool(Icons.AutoMirrored.Rounded.CallSplit, "Разделить", "По страницам или диапазонам"),
    PdfTool(Icons.Rounded.Compress, "Сжать", "Уменьшить размер файла"),
    PdfTool(Icons.Rounded.RotateRight, "Повернуть", "Страницы на 90°"),
    PdfTool(Icons.Rounded.FileUpload, "Извлечь", "Выбранные страницы в новый PDF"),
    PdfTool(Icons.Rounded.NoteAdd, "Добавить", "Страницы из камеры или файлов"),
    PdfTool(Icons.Rounded.Lock, "Защитить", "Пароль на открытие"),
    PdfTool(Icons.Rounded.IosShare, "Экспорт", "PDF, JPG, PNG, TXT"),
)

/**
 * PDF-инструменты. Движок PDF — этап 12: до него плитки показаны недоступными (не нажимаются),
 * а вверху прямо сказано, когда они заработают. Ни одна плитка не ведёт в заглушку.
 */
@Composable
fun PdfToolsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScanTopBar(title = "PDF-инструменты", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(Space.s4),
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StatusLine(Status.WARNING, "Инструменты заработают вместе с PDF-движком (этап 12)", Modifier.padding(bottom = Space.s2))
            }
            items(PdfTools, key = { it.title }) { t ->
                ToolTile(t.icon, t.title, t.description, onClick = {}, enabled = false)
            }
        }
    }
}
