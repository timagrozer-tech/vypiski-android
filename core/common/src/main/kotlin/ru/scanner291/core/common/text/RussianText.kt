package ru.scanner291.core.common.text

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Склонение существительного после числа: 1 документ, 4 документа, 12 документов. */
object RussianPlural {
    /**
     * @param one форма для 1, 21, 101… («документ»)
     * @param few форма для 2–4, 22–24… («документа»)
     * @param many форма для 0, 5–20, 25–30… («документов»)
     */
    fun choose(n: Long, one: String, few: String, many: String): String {
        val abs = if (n < 0) -n else n
        val mod100 = abs % 100
        val mod10 = abs % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1L -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    fun format(n: Long, one: String, few: String, many: String): String = "$n ${choose(n, one, few, many)}"

    fun documents(n: Int): String = format(n.toLong(), "документ", "документа", "документов")
    fun pages(n: Int): String = format(n.toLong(), "стр.", "стр.", "стр.")
    fun files(n: Int): String = format(n.toLong(), "файл", "файла", "файлов")
}

/** Даты в списках: «Сегодня, 14:32», «Вчера, 10:15», «12.10.2024» (README дизайн-системы). */
object HumanDate {
    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val date = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun format(at: LocalDateTime, today: LocalDate): String = when (at.toLocalDate()) {
        today -> "Сегодня, ${at.format(time)}"
        today.minusDays(1) -> "Вчера, ${at.format(time)}"
        else -> at.format(date)
    }
}

/** Подзаголовок строки документа: «Сегодня, 14:32 · 3 стр.». */
fun documentSubtitle(updatedAtMillis: Long, pages: Int, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
    val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(updatedAtMillis), zone)
    return "${HumanDate.format(at, today)} · ${RussianPlural.pages(pages)}"
}
