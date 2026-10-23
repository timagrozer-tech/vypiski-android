package ru.scanner291.core.intelligence.fio

/** Форма записи ФИО в тексте. */
enum class FioForm(val completeness: Int) {
    FULL(5),              // Фамилия Имя Отчество / Имя Отчество Фамилия
    LABELED_FIELDS(5),    // «Фамилия: … Имя: … Отчество: …»
    SURNAME_INITIALS(3),  // Иванов И.И. / И.И. Иванов
    SURNAME_NAME(3),      // Иванов Иван / Иван Иванов
    NAME_PATRONYMIC(1),   // Иван Иванович (без фамилии)
}

/** Предполагаемая роль человека в документе. */
enum class PersonRole { SUBJECT, OFFICIAL, ADDRESSEE, SIGNATORY, PARTY, UNKNOWN }

/** Нормализованное ФИО. */
data class Fio(
    val surname: String,
    val name: String? = null,
    val patronymic: String? = null,
    /** Инициалы вида «И.И.», если полного имени нет. */
    val initials: String? = null,
) {
    /** «Иванов Иван Иванович», «Иванов И.И.», «Иванов Иван». */
    fun display(): String = buildString {
        append(surname)
        when {
            name != null -> {
                append(' ').append(name)
                if (patronymic != null) append(' ').append(patronymic)
            }
            initials != null -> append(' ').append(initials)
        }
    }

    /** «Иванов И.И.» — для списков документов. */
    fun short(): String {
        val ini = initials ?: listOfNotNull(name, patronymic).joinToString("") { it.first().uppercase() + "." }
        return if (ini.isEmpty()) surname else "$surname $ini"
    }
}

/** Одно упоминание человека в тексте. */
data class FioMention(
    val fio: Fio,
    val originalText: String,
    val line: Int,
    val form: FioForm,
    val gender: Gender,
    val cases: Set<GCase>,
    /** false — падеж не удалось надёжно привести к именительному; оставлена исходная форма. */
    val normalizationReliable: Boolean,
    /** Предполагаемая форма в именительном падеже, если [normalizationReliable] = false. Показывается как подсказка, не подставляется сама. */
    val nominativeGuess: Fio?,
    val role: PersonRole,
    val score: Double,
    val reasons: List<String>,
    internal val surnameKeys: Set<String>,
    internal val firstInitial: Char?,
)

/** Человек (объединённые упоминания). */
data class PersonCandidate(
    val fio: Fio,
    val mentions: List<FioMention>,
    val role: PersonRole,
    val score: Double,
    /** Вероятность того, что это основной человек документа, 0..1. */
    val confidence: Double,
    val normalizationReliable: Boolean,
    val nominativeGuess: Fio? = null,
)

enum class FioStatus {
    /** Уверенность ≥ 0,90 — можно подставлять в имя файла. */
    FOUND,
    /** 0,60–0,89 или несколько близких кандидатов — нужен выбор пользователя. */
    NEEDS_CHOICE,
    /** < 0,60 — не подставлять без подтверждения. */
    LOW_CONFIDENCE,
    NOT_FOUND,
}

data class FioResult(
    val primary: PersonCandidate?,
    val alternatives: List<PersonCandidate>,
    val confidence: Double,
    val status: FioStatus,
) {
    /** Можно ли автоматически подставить ФИО в имя файла. */
    val autoApply: Boolean get() = status == FioStatus.FOUND && primary?.normalizationReliable == true

    val allCandidates: List<PersonCandidate> get() = listOfNotNull(primary) + alternatives

    companion object {
        const val THRESHOLD_FOUND = 0.90
        const val THRESHOLD_CHOICE = 0.60
        val EMPTY = FioResult(null, emptyList(), 0.0, FioStatus.NOT_FOUND)
    }
}
