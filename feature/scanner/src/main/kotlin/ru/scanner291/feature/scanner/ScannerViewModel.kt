package ru.scanner291.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.scanner291.core.storage.prefs.UserPreferencesRepository
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
) : ViewModel() {
    /** Галочка «Назвать файл по ФИО» — то же состояние, что в редакторе, сохранении и экспорте. */
    val nameByFio: StateFlow<Boolean> = prefs.preferences.map { it.nameByFio }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setNameByFio(v: Boolean) { viewModelScope.launch { prefs.setNameByFio(v) } }
}
