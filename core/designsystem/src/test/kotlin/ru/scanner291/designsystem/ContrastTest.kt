package ru.scanner291.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контраст по WCAG 2.1 для пар, на которые опирается дизайн-система.
 * Пороги: 4.5 — обычный текст, 3.0 — крупный текст (≥ 18 sp 600). Значения пересчитаны 2026-10-23.
 */
class ContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble(); val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun assertAtLeast(name: String, min: Double, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("$name: %.2f < %.1f".format(r, min), r >= min)
    }

    @Test fun textPairsPassAA() {
        for ((theme, c) in listOf("dark" to DarkColors, "light" to LightColors)) {
            assertAtLeast("$theme text/bg", 15.0, c.text, c.bg)
            assertAtLeast("$theme text/surface", 15.0, c.text, c.surface)
            assertAtLeast("$theme text/field", 15.0, c.text, c.field)
            assertAtLeast("$theme text-secondary/surface", 4.5, c.textSecondary, c.surface)
            assertAtLeast("$theme accent-text/surface", 4.5, c.accentText, c.surface)
            assertAtLeast("$theme on-accent-soft/accent-soft", 4.5, c.onAccentSoft, c.accentSoft)
            assertAtLeast("$theme success/surface", 4.5, c.success, c.surface)
            assertAtLeast("$theme warning/surface", 4.5, c.warning, c.surface)
            assertAtLeast("$theme danger/surface", 4.5, c.danger, c.surface)
            assertAtLeast("$theme focus-ring/bg", 3.0, c.focusRing, c.bg)
            // Белый на accent — только крупный текст (подписи на заливке — title-md 18 sp 600).
            assertAtLeast("$theme on-accent/accent (крупный)", 3.0, c.onAccent, c.accent)
        }
    }

    /**
     * Известное ограничение палитры (не исправляется в коде, чтобы не расходиться с дизайн-системой):
     * в светлой теме плейсхолдер `text-secondary` на `field` даёт 4.3:1 (< 4.5), на `bg` — 4.4:1.
     * Тест фиксирует факт — если палитру поправят, тест подскажет снять оговорку.
     */
    @Test fun knownLightThemeLimitations() {
        assertTrue(ratio(LightColors.textSecondary, LightColors.field) < 4.5)
        assertTrue(ratio(LightColors.textSecondary, LightColors.bg) < 4.5)
        assertTrue(ratio(DarkColors.textSecondary, DarkColors.field) >= 4.5)
    }
}
