package ru.scanner291.feature.batch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.ScanTopBar

@Serializable data object BatchSaveRoute

fun NavGraphBuilder.batchSaveScreen(onBack: () -> Unit) {
    composable<BatchSaveRoute> { BatchSaveScreen(onBack) }
}

/**
 * Пакетное сохранение по ФИО: список документов, найденные ФИО, конфликты имён, «Сохранить все (N)».
 * Логика планирования имён готова (BatchNamingPlanner); список появится вместе с документами.
 */
@Composable
fun BatchSaveScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScanTopBar(title = "Пакетное сохранение", onBack = onBack)
        EmptyState(
            Icons.Rounded.LibraryAddCheck,
            "Нет документов для пакета",
            "Отсканируйте несколько документов подряд — здесь появится список с найденными ФИО и предложенными именами файлов.",
        )
    }
}
