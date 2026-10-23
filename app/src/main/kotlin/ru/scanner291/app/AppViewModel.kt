package ru.scanner291.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.scanner291.core.storage.prefs.ThemeChoice
import ru.scanner291.core.storage.prefs.UserPreferences
import ru.scanner291.core.storage.prefs.UserPreferencesRepository
import ru.scanner291.designsystem.ThemeMode
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(repo: UserPreferencesRepository) : ViewModel() {
    val prefs: StateFlow<UserPreferences> =
        repo.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
}

internal fun ThemeChoice.toThemeMode(): ThemeMode = when (this) {
    ThemeChoice.SYSTEM -> ThemeMode.SYSTEM
    ThemeChoice.DARK -> ThemeMode.DARK
    ThemeChoice.LIGHT -> ThemeMode.LIGHT
}
