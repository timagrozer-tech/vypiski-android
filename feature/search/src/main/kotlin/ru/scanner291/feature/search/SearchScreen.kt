package ru.scanner291.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.scanner291.designsystem.EmptyState
import ru.scanner291.designsystem.ScanTextField
import ru.scanner291.designsystem.ScanTopBar
import ru.scanner291.designsystem.Space

/**
 * Поиск по названиям, тегам и распознанному тексту — офлайн. Индекс (Room FTS) — этапы 10 и 14;
 * пока библиотека пуста, результат честно «ничего не найдено».
 */
@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize()) {
        ScanTopBar(title = "Поиск", large = true)
        Column(Modifier.padding(horizontal = Space.s4)) {
            ScanTextField(query, { query = it }, placeholder = "Название, ФИО или текст документа", leadingIcon = Icons.Rounded.Search)
            Spacer(Modifier.height(Space.s4))
            if (query.isBlank()) {
                EmptyState(Icons.Rounded.Search, "Поиск по всей библиотеке", "Ищет по названиям, ФИО и распознанному тексту. Работает без интернета.")
            } else {
                EmptyState(Icons.Rounded.SearchOff, "Ничего не найдено", "По запросу «${query.trim()}» в библиотеке ничего нет.")
            }
        }
    }
}
