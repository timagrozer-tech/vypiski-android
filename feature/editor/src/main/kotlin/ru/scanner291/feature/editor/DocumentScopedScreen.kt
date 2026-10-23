package ru.scanner291.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.ScanTopBar

enum class DocScreen(val title: String, val icon: ImageVector, val hint: String) {
    Editor("Редактор страниц", Icons.Rounded.GridView, "Откройте документ, чтобы менять порядок, поворачивать и удалять страницы."),
    Ocr("Распознанный текст", Icons.Rounded.DocumentScanner, "Откройте документ, чтобы распознать и скопировать текст."),
    Fio("Определение ФИО", Icons.Rounded.Badge, "Откройте документ, чтобы определить ФИО и назвать файл."),
}

/**
 * Экраны, работающие с конкретным документом. Хранилища документов пока нет (этапы 5, 14),
 * поэтому любой идентификатор честно приводит к «Документ не найден» — без поддельных данных.
 */
@Composable
fun DocumentScopedScreen(screen: DocScreen, docId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScanTopBar(title = screen.title, onBack = onBack)
        EmptyState(icon = screen.icon, title = "Документ не найден", text = screen.hint)
    }
}
