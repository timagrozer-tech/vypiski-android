package ru.scanner291.domain.naming

import ru.scanner291.domain.fio.FioResult
import ru.scanner291.domain.fio.FioStatus
import ru.scanner291.domain.fio.PersonCandidate
import java.time.LocalDateTime

enum class NameSource { MANUAL, FIO, DATE }

/** Предложение имени для окна сохранения/экспорта. */
data class NameProposal(
    val baseName: String,
    val source: NameSource,
    /** Показать окно подтверждения ФИО перед сохранением. */
    val needsConfirmation: Boolean,
    val fioStatus: FioStatus?,
    val primary: PersonCandidate?,
    val alternatives: List<PersonCandidate>,
    val confidence: Double,
)

/**
 * Логика галочки «Назвать файл по ФИО».
 *  - Ручное имя пользователя всегда главнее.
 *  - Галочка выключена → имя по дате.
 *  - Галочка включена: ФИО найдено уверенно → имя по ФИО (подтверждение всё равно показывается, но с готовым вариантом);
 *    неуверенно/несколько → имя по дате + окно выбора; не найдено → имя по дате, сохранение не блокируется.
 */
object SmartNaming {
    fun propose(
        useFio: Boolean,
        fio: FioResult?,
        manualName: String?,
        now: LocalDateTime,
    ): NameProposal {
        val dateName = DefaultNames.byDate(now)
        if (!manualName.isNullOrBlank()) {
            return NameProposal(FileNameSanitizer.sanitizeBase(manualName), NameSource.MANUAL, false,
                fio?.status, fio?.primary, fio?.alternatives.orEmpty(), fio?.confidence ?: 0.0)
        }
        if (!useFio || fio == null) {
            return NameProposal(dateName, NameSource.DATE, false, fio?.status, null, emptyList(), 0.0)
        }
        return when (fio.status) {
            FioStatus.FOUND -> NameProposal(
                FileNameSanitizer.sanitizeBase(fio.primary!!.fio.display()), NameSource.FIO,
                needsConfirmation = !fio.autoApply, fio.status, fio.primary, fio.alternatives, fio.confidence,
            )
            FioStatus.NEEDS_CHOICE, FioStatus.LOW_CONFIDENCE -> NameProposal(
                dateName, NameSource.DATE, needsConfirmation = true, fio.status, fio.primary, fio.alternatives, fio.confidence,
            )
            FioStatus.NOT_FOUND -> NameProposal(dateName, NameSource.DATE, false, fio.status, null, emptyList(), 0.0)
        }
    }

    /** Имя по выбранному пользователем кандидату (кнопка «Выбрать другое ФИО»). */
    fun fromCandidate(c: PersonCandidate): String = FileNameSanitizer.sanitizeBase(c.fio.display())
}
