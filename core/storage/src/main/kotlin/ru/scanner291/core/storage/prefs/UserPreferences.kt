package ru.scanner291.core.storage.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.scanner291.core.common.watermark.WatermarkSettings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeChoice { SYSTEM, DARK, LIGHT }

/** Настройки пользователя. Значения по умолчанию — из ТЗ и дизайн-системы. */
data class UserPreferences(
    /** Тёмная тема — основная (дизайн-система). */
    val theme: ThemeChoice = ThemeChoice.DARK,
    val dynamicColor: Boolean = false,
    /** «Умное название файла» (определять ФИО). */
    val smartNaming: Boolean = true,
    /** ☑ «Назвать файл по ФИО» — одно состояние для камеры, редактора, сохранения и экспорта. */
    val nameByFio: Boolean = true,
    val watermark: WatermarkSettings = WatermarkSettings.DEFAULT,
)

internal object PrefKeys {
    val THEME = stringPreferencesKey("theme")
    val DYNAMIC = booleanPreferencesKey("dynamic_color")
    val SMART_NAMING = booleanPreferencesKey("smart_naming")
    val NAME_BY_FIO = booleanPreferencesKey("name_by_fio")
    val WM_ENABLED = booleanPreferencesKey("wm_enabled")
    val WM_OPACITY = floatPreferencesKey("wm_opacity")
    val WM_FONT_PT = floatPreferencesKey("wm_font_pt")
    val WM_MARGIN_MM = floatPreferencesKey("wm_margin_mm")
}

/** Чтение с значениями по умолчанию; повреждённое/неизвестное значение темы → тёмная. Покрыто тестом. */
fun Preferences.toUserPreferences(): UserPreferences {
    val d = UserPreferences()
    val wm = WatermarkSettings(
        enabled = this[PrefKeys.WM_ENABLED] ?: d.watermark.enabled,
        opacity = this[PrefKeys.WM_OPACITY] ?: d.watermark.opacity,
        fontPt = this[PrefKeys.WM_FONT_PT] ?: d.watermark.fontPt,
        marginMm = this[PrefKeys.WM_MARGIN_MM] ?: d.watermark.marginMm,
    ).normalized()
    return UserPreferences(
        theme = this[PrefKeys.THEME]?.let { v -> ThemeChoice.entries.firstOrNull { it.name == v } } ?: d.theme,
        dynamicColor = this[PrefKeys.DYNAMIC] ?: d.dynamicColor,
        smartNaming = this[PrefKeys.SMART_NAMING] ?: d.smartNaming,
        nameByFio = this[PrefKeys.NAME_BY_FIO] ?: d.nameByFio,
        watermark = wm,
    )
}

fun MutablePreferences.putWatermark(w: WatermarkSettings) {
    val n = w.normalized()
    this[PrefKeys.WM_ENABLED] = n.enabled
    this[PrefKeys.WM_OPACITY] = n.opacity
    this[PrefKeys.WM_FONT_PT] = n.fontPt
    this[PrefKeys.WM_MARGIN_MM] = n.marginMm
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data
        // Повреждённый файл настроек не должен ронять приложение: используем значения по умолчанию.
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { it.toUserPreferences() }
        .distinctUntilChanged()

    suspend fun setTheme(t: ThemeChoice) = dataStore.edit { it[PrefKeys.THEME] = t.name }
    suspend fun setDynamicColor(v: Boolean) = dataStore.edit { it[PrefKeys.DYNAMIC] = v }
    suspend fun setSmartNaming(v: Boolean) = dataStore.edit { it[PrefKeys.SMART_NAMING] = v }
    suspend fun setNameByFio(v: Boolean) = dataStore.edit { it[PrefKeys.NAME_BY_FIO] = v }
    suspend fun setWatermark(w: WatermarkSettings) = dataStore.edit { it.putWatermark(w) }
    /** «Восстановить стандартные»: текст и расположение фиксированы, сбрасываются размер, прозрачность, отступ. */
    suspend fun resetWatermark() = dataStore.edit { it.putWatermark(WatermarkSettings.DEFAULT) }
}
