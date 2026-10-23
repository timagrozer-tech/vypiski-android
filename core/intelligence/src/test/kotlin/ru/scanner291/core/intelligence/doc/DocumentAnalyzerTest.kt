package ru.scanner291.core.intelligence.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Этап 11: реальные по форме, обезличенные фразы служебных документов (ФИО, номера и даты вымышлены). */
class DocumentAnalyzerTest {
    private val a = DocumentAnalyzer()
    private val today = LocalDate.of(2026, 10, 23)
    private fun f(t: String) = a.analyze(t.trimIndent(), today)
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private val report = """
        Командиру войсковой части 65384
        полковнику А. Петрову
        РАПОРТ
        Прошу Вашего ходатайства о предоставлении мне основного
        отпуска за 2026 год продолжительностью 30 суток
        с выездом в город Омск.
        Ефрейтор Волков Иван Иванович
        «18» сентября 2026 г.
    """
    private val order = """
        ВЫПИСКА ИЗ ПРИКАЗА
        командира войсковой части 65384 № 215 от 03.04.2025
        по строевой части
        § 4. Полагать прибывшим и вступившим в должность
        сержанта Сидоренко Алексея Николаевича, назначенного приказом
        от 01.03.2025, зачислить в списки личного состава.
        Командир войсковой части 65384
        полковник В. Соколов
    """
    private val cert = """
        СПРАВКА
        № 926 от 17.09.2022
        Выдана: старший лейтенант Петров Алексей Николаевич, в том, что он
        действительно проходит военную службу по контракту
        в войсковой части 65384 с 17.09.2022 по настоящее время.
        Справка выдана для предъявления по месту требования.
        Командир войсковой части 65384
        полковник Н. Орлов
    """

    // ── Тип (13) ──
    @Test fun typeReport() = assertEquals(DocType.REPORT, f(report).type!!.value)
    @Test fun typeOrderExtract() = assertEquals(DocType.ORDER_EXTRACT, f(order).type!!.value)
    @Test fun typeCertificate() = assertEquals(DocType.CERTIFICATE, f(cert).type!!.value)
    @Test fun typeOrder() = assertEquals(DocType.ORDER, f("ПРИКАЗ\nкомандира войсковой части 65384\n№ 12 от 10.01.2026\nПРИКАЗЫВАЮ:").type!!.value)
    @Test fun typeSpacedLetters() = assertEquals(DocType.ORDER, f("П Р И К А З\n№ 7").type!!.value)
    @Test fun typeContract() = assertEquals(DocType.CONTRACT, f("ДОГОВОР № 14/2026\nоб оказании услуг").type!!.value)
    @Test fun typeStatement() = assertEquals(DocType.STATEMENT, f("Начальнику отдела кадров\nЗаявление\nПрошу принять меня на работу").type!!.value)
    @Test fun typeMemo() = assertEquals(DocType.MEMO, f("Служебная записка\nо выделении транспорта").type!!.value)
    @Test fun typeAct() = assertEquals(DocType.ACT, f("АКТ\nприёма-передачи имущества").type!!.value)
    @Test fun typeCharacteristic() = assertEquals(DocType.CHARACTERISTIC, f("ХАРАКТЕРИСТИКА\nна рядового Кузнецова Д.А.").type!!.value)
    @Test fun typeDirective() = assertEquals(DocType.ORDER_DIRECTIVE, f("РАСПОРЯЖЕНИЕ\n№ 3-р от 05.02.2026").type!!.value)
    @Test fun typeReportByPhraseWithoutHeading() {
        val t = f("Начальнику штаба войсковой части 65384\nПрошу разрешить убыть в командировку.")
        assertEquals(DocType.REPORT, t.type!!.value); assertTrue(t.type!!.confidence < 0.9)
    }
    @Test fun typeUnknownForRandomText() = assertNull(f("Список покупок: хлеб, молоко").type)

    // ── Номер (8) ──
    @Test fun numberCertificate() = assertEquals("926", f(cert).number!!.value)
    @Test fun numberOrder() = assertEquals("215", f(order).number!!.value)
    @Test fun numberWithSlashAndLetters() = assertEquals("14/2026", f("ДОГОВОР № 14/2026").number!!.value)
    @Test fun numberWithDashSuffix() = assertEquals("115-к", f("ПРИКАЗ\n№ 115-к от 12.03.2026").number!!.value)
    @Test fun numberOcrLatinNo() = assertEquals("48", f("СПРАВКА\nNo 48 от 01.02.2026").number!!.value)
    @Test fun numberNoSpace() = assertEquals("301", f("ПРИКАЗ\n№301 от 01.02.2026").number!!.value)
    @Test fun unitNumberIsNotDocumentNumber() = assertNull(f("в/ч 65384\nРАПОРТ\nПрошу отпуск").number)
    @Test fun numberAbsent() = assertNull(f(report).number)

    // ── Дата (9) ──
    @Test fun dateFromNumberLine() = assertEquals(d(2022, 9, 17), f(cert).date!!.value)
    @Test fun dateOrderIsHeaderNotBody() = assertEquals(d(2025, 4, 3), f(order).date!!.value)
    @Test fun dateWordsInSignature() = assertEquals(d(2026, 9, 18), f(report).date!!.value)
    @Test fun dateTwoDigitYear() = assertEquals(d(2026, 2, 5), f("РАСПОРЯЖЕНИЕ\n№ 3 от 05.02.26").date!!.value)
    @Test fun dateMay() = assertEquals(d(2025, 5, 9), f("ПРИКАЗ\nот 9 мая 2025 г. № 40").date!!.value)
    @Test fun dateInvalidIgnored() = assertNull(f("СПРАВКА\nот 31.02.2026").date)
    @Test fun dateFarFutureIgnored() = assertNull(f("АКТ\n01.01.2099").date)
    @Test fun parseSeveralDates() = assertEquals(listOf(d(2024, 3, 1), d(2025, 1, 15)), a.parseDates("с 01.03.2024 по 15 января 2025", today))
    @Test fun dateHeaderPreferredOverBody() = assertEquals(d(2026, 1, 10), f("ПРИКАЗ\n№ 12 от 10.01.2026\nс 01.03.2024 зачислить").date!!.value)

    // ── Организация (5) ──
    @Test fun orgMilitaryUnit() = assertEquals("Войсковая часть 65384", f(report).organization!!.value)
    @Test fun orgShortForm() = assertEquals("Войсковая часть 12345", f("в/ч 12345\nСПРАВКА").organization!!.value)
    @Test fun orgCompany() = assertEquals("ООО «Северный ветер»", f("ДОГОВОР\nООО \"Северный ветер\" в лице директора").organization!!.value)
    @Test fun orgMinistry() = assertEquals("Министерство обороны Российской Федерации", f("Министерство обороны Российской Федерации\nПРИКАЗ").organization!!.value)
    @Test fun orgAbsent() = assertNull(f("Заявление\nПрошу принять меня").organization)

    // ── Заголовок (3) ──
    @Test fun titleSubject() = assertEquals("Об оказании услуг", f("ДОГОВОР № 14\nоб оказании услуг").title!!.value)
    @Test fun titleOrderKind() = assertEquals("По строевой части", f(order).title!!.value)
    @Test fun titleLowConfidenceFallback() = assertTrue(f("Список имущества роты\nстол 2 шт.").title!!.confidence < 0.5)

    // ── Шаблоны (6) ──
    @Test fun reportAddressee() = assertEquals("Командиру войсковой части 65384", f(report).templateFields["Адресат"]!!.value)
    @Test fun reportRequest() = assertTrue(f(report).templateFields["Просьба"]!!.value.startsWith("Прошу Вашего ходатайства"))
    @Test fun reportRequestEndsAtSentence() = assertTrue(f(report).templateFields["Просьба"]!!.value.endsWith("город Омск"))
    @Test fun reportLeaveDays() = assertEquals("30", f(report).templateFields["Срок, суток"]!!.value)
    @Test fun orderParagraphsAndKind() {
        val t = f(order).templateFields
        assertEquals("§ 4", t["Параграфы"]!!.value); assertEquals("по строевой части", t["Вид приказа"]!!.value)
    }
    @Test fun orderSignatory() = assertEquals("В.Соколов", f(order).templateFields["Подписал"]!!.value)

    // ── ФИО, имя файла, подтверждение (6) ──
    @Test fun personFromReport() = assertEquals("Волков И.И.", f(report).person?.value)
    @Test fun suggestedNameReport() = assertEquals("Рапорт Волков И.И. 18.09.2026", f(report).suggestedName())
    @Test fun suggestedNameOrderByNumber() = assertEquals("Приказ № 12 от 10.01.2026", f("ПРИКАЗ\n№ 12 от 10.01.2026\nПРИКАЗЫВАЮ:").suggestedName())
    @Test fun suggestedNameNullWithoutType() = assertNull(f("Список покупок").suggestedName())
    @Test fun everythingNeedsConfirmation() {
        val x = f(order)
        assertTrue(listOfNotNull(x.type, x.date, x.number, x.title, x.organization, x.person).all { it.needsConfirmation })
        assertTrue(x.templateFields.values.all { it.needsConfirmation })
    }
    @Test fun sourcesPointToDocumentLines() {
        val x = f(cert)
        assertEquals("№ 926 от 17.09.2022", x.number!!.source); assertNotNull(x.organization!!.source)
    }

    // ── Ошибки OCR (2) ──
    @Test fun ocrLatinLookalikesInHeading() = assertEquals(DocType.REPORT, f("PAПOPT\nПрошу").type?.value)
    @Test fun ocrHyphenatedLineBreak() = assertEquals(DocType.CERTIFICATE, f("СПРАВ-\nКА\nвыдана в том, что").type?.value)
}
