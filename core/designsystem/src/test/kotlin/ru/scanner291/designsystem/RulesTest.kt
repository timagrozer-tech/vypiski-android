package ru.scanner291.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Правила дизайн-системы, выраженные чистыми функциями. */
class RulesTest {
    @Test fun glassOnlyOnAndroid12WithoutPowerSaveOrReducedMotion() {
        assertTrue(glassAllowed(31, powerSave = false, reducedMotion = false))
        assertTrue(glassAllowed(36, powerSave = false, reducedMotion = false))
        assertFalse(glassAllowed(29, powerSave = false, reducedMotion = false))
        assertFalse(glassAllowed(30, powerSave = false, reducedMotion = false))
        assertFalse(glassAllowed(34, powerSave = true, reducedMotion = false))
        assertFalse(glassAllowed(34, powerSave = false, reducedMotion = true))
    }

    @Test fun glassFallbackIsSurfaceForCardsAndStrongForBars() {
        for (c in listOf(DarkColors, LightColors)) {
            assertEquals(c.glass, glassFill(c, GlassLevel.Card, glassEnabled = true))
            assertEquals(c.surface, glassFill(c, GlassLevel.Card, glassEnabled = false))
            assertEquals(c.glassStrong, glassFill(c, GlassLevel.Bar, glassEnabled = false))
        }
    }

    @Test fun primaryButtonUsesLargeTextOnAccent() {
        for (c in listOf(DarkColors, LightColors)) {
            val s = buttonStyle(c, ButtonVariant.Primary)
            assertEquals(c.accent, s.container)
            assertTrue("подпись на accent не мельче 18 sp", s.text.fontSize.value >= 18f)
            assertTrue((s.text.fontWeight?.weight ?: 0) >= 600)
            assertEquals(56f, s.minHeight.value, 0f)
            ButtonVariant.entries.forEach { v -> assertTrue("$v: зона нажатия ≥ 48 dp", buttonStyle(c, v).minHeight.value >= 48f) }
        }
    }

    @Test fun fioStatusFollowsThresholds() {
        assertEquals(Status.SUCCESS, fioStatus(FioUi.Found("Иванов Иван Иванович", 96)).first)
        assertEquals("ФИО определено · Уверенность: 96%", fioStatus(FioUi.Found("Иванов Иван Иванович", 96)).second)
        assertEquals(Status.WARNING, fioStatus(FioUi.NeedsChoice("Петров П.П.", 2, 74)).first)
        assertEquals("Проверьте ФИО · 2 варианта", fioStatus(FioUi.NeedsChoice("Петров П.П.", 2, 74)).second)
        assertEquals("Проверьте ФИО · 5 вариантов", fioStatus(FioUi.NeedsChoice(null, 5, 40)).second)
        assertEquals("Проверьте ФИО · Уверенность: 74%", fioStatus(FioUi.NeedsChoice("Петров П.П.", 1, 74)).second)
        assertEquals(Status.DANGER to "ФИО не найдено", fioStatus(FioUi.NotFound))
    }

    @Test fun searchHighlightIgnoresCaseAndYo() {
        val a = highlightMatches("Семёнов Пётр — СЕМЕНОВ", "семенов", Color.Yellow)
        assertEquals(2, a.spanStyles.size)
        assertEquals(0, a.spanStyles[0].start); assertEquals(7, a.spanStyles[0].end)
        assertEquals(0, highlightMatches("текст", null, Color.Yellow).spanStyles.size)
        assertEquals(0, highlightMatches("текст", "  ", Color.Yellow).spanStyles.size)
    }

    @Test fun sliderSteps() {
        assertEquals(0, stepIndex(0f, 0f..1f, 9))
        assertEquals(10, stepIndex(1f, 0f..1f, 9))
        assertEquals(2, stepIndex(0.2f, 0f..1f, 9))
    }
}
