package ru.scanner291.feature.scanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.ScanTopBar

/**
 * Обработка изображения (углы, перспектива, фильтры). Открывается для конкретного документа;
 * пока хранилища документов нет (этапы 5, 14), любой идентификатор честно показывает «не найден».
 */
@Composable
fun ProcessingScreen(docId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScanTopBar(title = "Обработка", onBack = onBack)
        EmptyState(
            icon = Icons.Rounded.AutoFixHigh,
            title = "Документ не найден",
            text = "Откройте отсканированный документ, чтобы выровнять края и выбрать фильтр.",
        )
    }
}
