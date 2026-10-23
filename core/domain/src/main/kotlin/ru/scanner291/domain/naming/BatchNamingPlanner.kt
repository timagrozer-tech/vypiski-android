package ru.scanner291.domain.naming

import ru.scanner291.domain.fio.FioResult
import ru.scanner291.domain.fio.FioStatus

/** Документ в пакете: идентификатор и результат определения ФИО по всем его страницам. */
data class BatchItem(val documentId: String, val pageCount: Int, val fio: FioResult?)

enum class BatchFlag {
    /** ФИО найдено уверенно. */
    OK,
    /** Несколько кандидатов или средняя уверенность — проверить. */
    AMBIGUOUS,
    /** ФИО не найдено или уверенность низкая — используется имя по дате. */
    NOT_FOUND,
    /** Такое же ФИО у другого документа пакета — имена разведены суффиксом. */
    DUPLICATE_PERSON,
    /** Пользователь исправил имя вручную. */
    EDITED,
}

data class BatchRow(
    val documentId: String,
    val proposedBase: String,
    val fileName: String,
    val flags: Set<BatchFlag>,
    val confidence: Double,
)

/**
 * Пакетное именование: каждый документ — отдельный файл. Многостраничный документ
 * никогда не делится на людей автоматически — только по явной команде пользователя (вне этого класса).
 */
class BatchNamingPlanner(
    private val extension: String = "pdf",
    private val datePrefix: (index: Int) -> String,
) {

    private data class Planned(val item: BatchItem, val base: String, val flag: BatchFlag, val fromFio: Boolean)

    fun plan(items: List<BatchItem>, existingNames: Set<String>, overrides: Map<String, String> = emptyMap()): List<BatchRow> {
        val taken = existingNames.toMutableSet()
        val baseByDoc = items.mapIndexed { i, it ->
            val override = overrides[it.documentId]
            val fio = it.fio
            val (base, flag) = when {
                !override.isNullOrBlank() -> FileNameSanitizer.sanitizeBase(override) to BatchFlag.EDITED
                fio == null || fio.status == FioStatus.NOT_FOUND || fio.primary == null ->
                    datePrefix(i) to BatchFlag.NOT_FOUND
                // Низкая уверенность: ФИО не подставляем, но строку отмечаем для проверки.
                fio.status == FioStatus.LOW_CONFIDENCE -> datePrefix(i) to BatchFlag.AMBIGUOUS
                fio.status == FioStatus.NEEDS_CHOICE -> FileNameSanitizer.sanitizeBase(fio.primary!!.fio.display()) to BatchFlag.AMBIGUOUS
                !fio.autoApply -> FileNameSanitizer.sanitizeBase(fio.primary!!.fio.display()) to BatchFlag.AMBIGUOUS
                else -> FileNameSanitizer.sanitizeBase(fio.primary!!.fio.display()) to BatchFlag.OK
            }
            val fromFio = flag == BatchFlag.OK || (flag == BatchFlag.AMBIGUOUS && fio?.status != FioStatus.LOW_CONFIDENCE)
            Planned(it, base, flag, fromFio)
        }
        val dupBases = baseByDoc.filter { it.fromFio }.groupBy { it.base.lowercase() }.filter { it.value.size > 1 }.keys
        return baseByDoc.map { (item, base, flag, fromFio) ->
            val flags = mutableSetOf(flag)
            if (fromFio && base.lowercase() in dupBases) flags += BatchFlag.DUPLICATE_PERSON
            val name = UniqueNameResolver.resolve(base, extension, taken)
            BatchRow(item.documentId, base, name, flags, item.fio?.confidence ?: 0.0)
        }
    }

    /**
     * Массовая правка: применяет шаблон ко всем выбранным строкам.
     * Поддерживаемые подстановки: {fio}, {n} (порядковый номер с 1), {base} (текущее имя).
     */
    fun massRename(rows: List<BatchRow>, selected: Set<String>, template: String): Map<String, String> {
        var n = 0
        return rows.filter { it.documentId in selected }.associate { r ->
            n++
            r.documentId to template.replace("{n}", n.toString()).replace("{base}", r.proposedBase).replace("{fio}", r.proposedBase)
        }
    }
}
