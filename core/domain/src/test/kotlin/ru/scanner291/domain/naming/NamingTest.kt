package ru.scanner291.domain.naming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.scanner291.domain.fio.FioExtractor
import java.time.LocalDateTime

class NamingTest {

    @Test fun sanitizerKeepsCyrillicAndRemovesForbidden() {
        assertEquals("Иванов Иван Иванович", FileNameSanitizer.sanitizeBase("  Иванов/Иван:Иванович?  "))
        assertEquals("Справка от 01.02", FileNameSanitizer.sanitizeBase("Справка от 01.02..."))
        assertEquals("Документ", FileNameSanitizer.sanitizeBase(" ... "))
        assertEquals("_CON", FileNameSanitizer.sanitizeBase("CON"))
        assertEquals("Иванов И.И.pdf", FileNameSanitizer.fileName("Иванов И.И.", "PDF"))
        assertEquals("В ч 65384", FileNameSanitizer.sanitizeBase("В/ч 65384"))
    }

    @Test fun truncationRespectsUtf8Bytes() {
        val long = "Ж".repeat(300) // 2 байта на символ
        val s = FileNameSanitizer.sanitizeBase(long)
        assertTrue(s.toByteArray(Charsets.UTF_8).size <= FileNameSanitizer.MAX_BASE_BYTES)
        assertEquals(100, s.length)
        val emoji = "😀".repeat(80) // 4 байта, суррогатные пары
        val e = FileNameSanitizer.truncateUtf8(emoji, 10)
        assertEquals(4, e.length) // 2 эмодзи × 2 char
    }

    @Test fun uniqueNamesNeverOverwrite() {
        val existing = mutableSetOf("Иванов Иван Иванович.pdf", "иванов иван иванович (2).pdf")
        assertEquals("Иванов Иван Иванович (3).pdf", UniqueNameResolver.resolve("Иванов Иван Иванович", "pdf", existing))
        assertEquals("Иванов Иван Иванович (4).pdf", UniqueNameResolver.resolve("Иванов Иван Иванович", "pdf", existing))
        assertEquals("Петров.pdf", UniqueNameResolver.resolve("Петров", "pdf", existing))
    }

    @Test fun uniqueNameFromAlreadySuffixedBase() {
        val taken = mutableSetOf("Отчёт (2).pdf")
        assertEquals("Отчёт (3).pdf", UniqueNameResolver.resolve("Отчёт (2)", "pdf", taken))
    }

    private val now = LocalDateTime.of(2026, 10, 23, 13, 53)

    @Test fun toggleOffUsesDate() {
        val fio = FioExtractor().extract("Иванов Иван Иванович")
        val p = SmartNaming.propose(useFio = false, fio = fio, manualName = null, now = now)
        assertEquals(NameSource.DATE, p.source)
        assertEquals("Скан 2026-10-23 13-53", p.baseName)
        assertFalse(p.needsConfirmation)
    }

    @Test fun toggleOnUsesFioWhenConfident() {
        val fio = FioExtractor().extract("Справка выдана Иванову Ивану Ивановичу")
        val p = SmartNaming.propose(useFio = true, fio = fio, manualName = null, now = now)
        assertEquals(NameSource.FIO, p.source)
        assertEquals("Иванов Иван Иванович", p.baseName)
    }

    @Test fun manualNameWins() {
        val fio = FioExtractor().extract("Иванов Иван Иванович")
        val p = SmartNaming.propose(useFio = true, fio = fio, manualName = "Мой файл", now = now)
        assertEquals(NameSource.MANUAL, p.source)
        assertEquals("Мой файл", p.baseName)
    }

    @Test fun ambiguousFallsBackToDateButAsksUser() {
        val fio = FioExtractor().extract(
            "Петров Пётр Петрович, именуемый «Заказчик», и Сидоров Сергей Сергеевич, именуемый «Исполнитель»",
        )
        val p = SmartNaming.propose(useFio = true, fio = fio, manualName = null, now = now)
        assertEquals(NameSource.DATE, p.source)
        assertTrue(p.needsConfirmation)
        assertTrue(p.primary != null && p.alternatives.isNotEmpty())
    }

    @Test fun notFoundDoesNotBlockSaving() {
        val p = SmartNaming.propose(useFio = true, fio = FioExtractor().extract("Акт № 5"), manualName = null, now = now)
        assertEquals(NameSource.DATE, p.source)
        assertFalse(p.needsConfirmation)
    }
}

class BatchNamingPlannerTest {
    private val ex = FioExtractor()
    private val planner = BatchNamingPlanner("pdf") { i -> "Скан 2026-10-23 №${i + 1}" }

    @Test fun thirtyDocumentsEachGetsOwnFile() {
        val surnames = listOf("Иванову Ивану Ивановичу", "Петрову Петру Петровичу", "Сидорову Сергею Сергеевичу")
        val items = (0 until 30).map { i ->
            BatchItem("doc$i", 1, ex.extract("Справка выдана ${surnames[i % 3]}"))
        }
        val rows = planner.plan(items, existingNames = setOf("Иванов Иван Иванович.pdf"))
        assertEquals(30, rows.size)
        assertEquals("все имена уникальны", 30, rows.map { it.fileName.lowercase() }.toSet().size)
        assertEquals("Иванов Иван Иванович (2).pdf", rows[0].fileName)
        assertTrue(rows[0].flags.contains(BatchFlag.DUPLICATE_PERSON))
    }

    @Test fun flagsAmbiguousAndNotFound() {
        val items = listOf(
            BatchItem("a", 1, ex.extract("Справка выдана Кузнецовой Марии Сергеевне")),
            BatchItem("b", 2, ex.extract("Петров Пётр Петрович, именуемый «Заказчик», и Сидоров Сергей Сергеевич, именуемый «Исполнитель»")),
            BatchItem("c", 1, ex.extract("Акт приёмки")),
            BatchItem("d", 1, null),
        )
        val rows = planner.plan(items, emptySet())
        assertEquals(setOf(BatchFlag.OK), rows[0].flags)
        assertTrue(rows[1].flags.contains(BatchFlag.AMBIGUOUS))
        assertTrue(rows[2].flags.contains(BatchFlag.NOT_FOUND))
        assertEquals("Скан 2026-10-23 №3.pdf", rows[2].fileName)
        assertTrue(rows[3].flags.contains(BatchFlag.NOT_FOUND))
    }

    @Test fun overridesAndMassRename() {
        val items = (1..3).map { BatchItem("d$it", 1, null) }
        val rows = planner.plan(items, emptySet())
        val o = planner.massRename(rows, setOf("d1", "d3"), "Личное дело {n}")
        val rows2 = planner.plan(items, emptySet(), o)
        assertEquals("Личное дело 1.pdf", rows2[0].fileName)
        assertEquals("Личное дело 2.pdf", rows2[2].fileName)
        assertTrue(rows2[0].flags.contains(BatchFlag.EDITED))
    }
}
