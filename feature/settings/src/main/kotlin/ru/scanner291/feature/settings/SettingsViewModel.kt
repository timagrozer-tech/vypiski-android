package ru.scanner291.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.scanner291.core.common.watermark.WatermarkSettings
import ru.scanner291.core.storage.prefs.ThemeChoice
import ru.scanner291.core.storage.prefs.UserPreferences
import ru.scanner291.core.storage.prefs.UserPreferencesRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: UserPreferencesRepository) : ViewModel() {
    val prefs: StateFlow<UserPreferences> =
        repo.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setTheme(t: ThemeChoice) = launch { repo.setTheme(t) }
    fun setDynamicColor(v: Boolean) = launch { repo.setDynamicColor(v) }
    fun setSmartNaming(v: Boolean) = launch { repo.setSmartNaming(v) }
    fun setNameByFio(v: Boolean) = launch { repo.setNameByFio(v) }
    fun setWatermark(w: WatermarkSettings) = launch { repo.setWatermark(w) }
    fun resetWatermark() = launch { repo.resetWatermark() }

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }
}
