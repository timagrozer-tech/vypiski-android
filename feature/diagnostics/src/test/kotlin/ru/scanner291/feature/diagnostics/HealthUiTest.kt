package ru.scanner291.feature.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.scanner291.core.storage.HealthFix
import ru.scanner291.core.storage.HealthIssue
import ru.scanner291.core.storage.HealthKind

class HealthUiTest {
    @Test fun summaryText() {
        assertEquals("Проблем не найдено", summary(emptyList()))
        assertEquals("Найдено замечаний: 2, можно исправить: 1", summary(listOf(
            HealthIssue(HealthKind.CORRUPTED, "Страница 1", null), HealthIssue(HealthKind.INDEX_MISSING, "Страниц: 3", HealthFix.REBUILD_INDEX))))
    }
}
