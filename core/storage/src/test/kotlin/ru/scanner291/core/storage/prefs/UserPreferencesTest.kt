package ru.scanner291.core.storage.prefs

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.scanner291.core.common.watermark.WatermarkSettings

class UserPreferencesTest {
    @Test fun defaultsMatchSpec() {
        val p = emptyPreferences().toUserPreferences()
        assertEquals(ThemeChoice.DARK, p.theme)
        assertEquals(true, p.nameByFio)        // галочка «Назвать файл по ФИО» включена по умолчанию
        assertEquals(true, p.smartNaming)
        assertEquals(false, p.dynamicColor)
        assertEquals(0.20f, p.watermark.opacity, 0f)
        assertEquals(14f, p.watermark.fontPt, 0f)
        assertEquals(8f, p.watermark.marginMm, 0f)
        assertEquals(true, p.watermark.enabled)
    }

    @Test fun watermarkRoundTripIsNormalized() {
        val m = mutablePreferencesOf()
        m.putWatermark(WatermarkSettings(enabled = false, opacity = 5f, fontPt = 2f, marginMm = 12f))
        val w = m.toUserPreferences().watermark
        assertEquals(false, w.enabled)
        assertEquals(1f, w.opacity, 0f)   // ограничено сверху
        assertEquals(6f, w.fontPt, 0f)    // ограничено снизу
        assertEquals(12f, w.marginMm, 0f)
    }

    @Test fun unknownThemeFallsBackToDark() {
        val m = mutablePreferencesOf(PrefKeys.THEME to "PURPLE")
        assertEquals(ThemeChoice.DARK, m.toUserPreferences().theme)
    }
}
