package ru.scanner291.core.intelligence.fio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FioExtractorTest {

    private val ex = FioExtractor()

    private fun primary(text: String): String? = ex.extract(text).primary?.fio?.display()

    @Test fun fullNominative() {
        val r = ex.extract("СПРАВКА\nИванов Иван Иванович работает в должности инженера.")
        assertEquals("Иванов Иван Иванович", r.primary?.fio?.display())
        assertEquals(FioStatus.FOUND, r.status)
        assertTrue(r.autoApply)
    }

    @Test fun certificateDativeFemale() {
        val t = """
            СПРАВКА № 17
            Дана Кузнецовой Марии Сергеевне в том, что она действительно работает
            в ООО «Север» в должности бухгалтера.
            Главный врач ____________ А.А. Смирнов
        """.trimIndent()
        val r = ex.extract(t)
        assertEquals("Кузнецова Мария Сергеевна", r.primary?.fio?.display())
        assertTrue(r.autoApply)
        assertTrue("Смирнов должен быть в альтернативах", r.alternatives.any { it.fio.surname == "Смирнов" })
    }

    @Test fun orderAccusativeWithCommanderSignature() {
        val t = """
            ПРИКАЗ № 45
            г. Москва 12.10.2026
            Назначить Сидорова Петра Алексеевича на должность инженера отдела связи.
            Основание: рапорт от 10.10.2026.
            Командир войсковой части 65384
            полковник П.П. Петров
        """.trimIndent()
        val r = ex.extract(t)
        assertEquals("Сидоров Пётр Алексеевич", r.primary?.fio?.display())
        assertEquals(FioStatus.FOUND, r.status)
    }

    @Test fun reportAddresseeIsNotSubject() {
        val t = """
            Командиру войсковой части 65384
            полковнику Петрову П.П.
            от рядового Сидорова Алексея Викторовича
            РАПОРТ
            Прошу предоставить мне очередной отпуск с 01.11.2026.
            рядовой А.В. Сидоров
        """.trimIndent()
        val r = ex.extract(t)
        assertEquals("Сидоров Алексей Викторович", r.primary?.fio?.display())
        assertEquals(FioStatus.FOUND, r.status)
        val petrov = r.alternatives.firstOrNull { it.fio.surname.startsWith("Петров") }
        assertNotNull(petrov)
        assertEquals(PersonRole.ADDRESSEE, petrov!!.role)
        assertEquals("упоминания Сидорова объединены", 2, r.primary!!.mentions.size)
    }

    @Test fun allCaps() {
        assertEquals("Иванова Анна Петровна", primary("ЗАЯВЛЕНИЕ\nИВАНОВА АННА ПЕТРОВНА\nпрошу выдать справку"))
    }

    @Test fun surnameInitialsGenitiveByPreposition() {
        val r = ex.extract("Заявление\nот Петрова И.С.\nПрошу принять меня на работу.")
        assertEquals("Петров И.С.", r.primary?.fio?.display())
        assertTrue(r.primary!!.normalizationReliable)
    }

    @Test fun initialsOnlyInSignatureIsNotAutoApplied() {
        val r = ex.extract("Акт сверки\nСверка выполнена без расхождений.\n______________ И.С. Петров")
        assertNotNull(r.primary)
        assertFalse(r.autoApply)
        assertTrue(r.status != FioStatus.FOUND)
    }

    @Test fun withoutPatronymic() {
        assertEquals("Смирнов Алексей", primary("Гражданин Смирнов Алексей, паспорт серии 4510 № 123456"))
    }

    @Test fun doubleSurnameDative() {
        assertEquals("Римская-Корсакова Анна Ивановна", primary("Справка выдана Римской-Корсаковой Анне Ивановне для предъявления по месту требования."))
    }

    @Test fun compoundFirstName() {
        assertEquals("Петрова Анна-Мария Сергеевна", primary("Выдана Петровой Анне-Марии Сергеевне"))
    }

    @Test fun labeledFieldsSeparateLines() {
        val r = ex.extract("АНКЕТА\nФамилия: Ковалёв\nИмя: Дмитрий\nОтчество: Олегович\nДата рождения: 01.02.1990")
        assertEquals("Ковалёв Дмитрий Олегович", r.primary?.fio?.display())
        assertEquals(FioStatus.FOUND, r.status)
    }

    @Test fun fioLabelWithKnownName() {
        assertEquals("Гусейнов Эльдар Рашидович", primary("Ф.И.О.: Гусейнов Эльдар Рашидович\nДолжность: водитель"))
    }

    @Test fun fioLabelRareNameKeptAsIsAndNotAutoNormalized() {
        val r = ex.extract("ФИО: Тхагапсоев Аслан Мухамедович\nАдрес: г. Нальчик")
        assertEquals("Тхагапсоев Аслан Мухамедович", r.primary?.fio?.display())
    }

    @Test fun contractTwoPartiesNeedsChoice() {
        val t = """
            ДОГОВОР ПОДРЯДА № 3
            Петров Пётр Петрович, именуемый в дальнейшем «Заказчик», с одной стороны,
            и Сидоров Сергей Сергеевич, именуемый в дальнейшем «Исполнитель», с другой стороны,
            заключили настоящий договор о нижеследующем.
        """.trimIndent()
        val r = ex.extract(t)
        assertTrue("статус ${r.status}", r.status == FioStatus.NEEDS_CHOICE || r.status == FioStatus.LOW_CONFIDENCE)
        assertFalse(r.autoApply)
        assertEquals(2, r.allCandidates.size)
    }

    @Test fun subjectBeatsDirector() {
        val t = """
            СПРАВКА
            Выдана Иванову Ивану Ивановичу в том, что он работает в АО «Вектор» с 2019 года.
            Директор Петров Пётр Петрович
        """.trimIndent()
        val r = ex.extract(t)
        assertEquals("Иванов Иван Иванович", r.primary?.fio?.display())
        assertEquals(FioStatus.FOUND, r.status)
        assertEquals(PersonRole.OFFICIAL, r.alternatives.first().role)
    }

    @Test fun notFound() {
        val r = ex.extract("Акт приёмки оборудования № 5 от 12.10.2026\nОборудование принято без замечаний.")
        assertNull(r.primary)
        assertEquals(FioStatus.NOT_FOUND, r.status)
    }

    @Test fun unreliableDeclensionKeepsOriginalForms() {
        val r = ex.extract("Справка выдана Шевчуку Олегу Ивановичу")
        val p = r.primary!!
        assertEquals("Шевчуку Олегу Ивановичу", p.fio.display())
        assertFalse(p.normalizationReliable)
        assertEquals("Шевчук Олег Иванович", p.nominativeGuess?.display())
        assertFalse(r.autoApply)
    }

    @Test fun latinHomoglyphsFixed() {
        // «а», «о», «е» латиницей внутри кириллических слов — типовая ошибка OCR.
        assertEquals("Иванов Иван Иванович", primary("Ивaнов Ивaн Ивaнович прибыл"))
    }

    @Test fun hyphenatedLineBreakJoined() {
        assertEquals("Константинов Иван Петрович", primary("Справка выдана Конс-\nтантинову Ивану Петровичу"))
    }

    @Test fun prepositionalFemale() {
        assertEquals("Смирнова Ольга Викторовна", primary("Сведения о Смирновой Ольге Викторовне"))
    }

    @Test fun instrumentalMale() {
        assertEquals("Орлов Игорь Николаевич", primary("Договор заключён с Орловым Игорем Николаевичем"))
    }

    @Test fun nameFirstOrder() {
        assertEquals("Тургенев Иван Сергеевич", primary("Автор: Иван Сергеевич Тургенев"))
    }

    @Test fun skySurnameGenitive() {
        assertEquals("Вишневский Андрей Павлович", primary("Рапорт\nот Вишневского Андрея Павловича"))
    }

    @Test fun skayaSurnameDative() {
        assertEquals("Вишневская Елена Андреевна", primary("Справка выдана Вишневской Елене Андреевне"))
    }

    @Test fun indeclinableEnko() {
        val r = ex.extract("Выдана Шевченко Ирине Петровне")
        assertEquals("Шевченко Ирина Петровна", r.primary?.fio?.display())
        assertTrue(r.primary!!.normalizationReliable)
    }

    @Test fun applicationWithAddresseeDirector() {
        val t = """
            Генеральному директору ООО «Ромашка»
            Сергееву С.С.
            от Кравцовой Л.Н.
            ЗАЯВЛЕНИЕ
            Прошу предоставить мне отпуск.
        """.trimIndent()
        val r = ex.extract(t)
        assertEquals("Кравцова Л.Н.", r.primary?.fio?.display())
    }

    @Test fun yoNamesNormalized() {
        assertEquals("Семёнов Пётр Фёдорович", primary("Выдана Семёнову Петру Фёдоровичу"))
    }

    @Test fun ambiguousNameResolvedByPatronymic() {
        // «Александра» — м. р. Р.п. или ж. р. И.п.; отчество решает.
        assertEquals("Белов Александр Ильич", primary("от Белова Александра Ильича"))
        assertEquals("Белова Александра Ильинична", primary("Белова Александра Ильинична, 1990 г.р."))
    }

    @Test fun repeatedMentionsIncreaseConfidence() {
        val one = ex.extract("Иванов И.И. прибыл.")
        val two = ex.extract("Иванов И.И. прибыл. Иванову И.И. предоставить жильё.")
        assertTrue(two.confidence >= one.confidence)
    }

    @Test fun capsHeadingIsNotSurname() {
        val r = ex.extract("ПРИКАЗ\nВЫПИСКА\nРАПОРТ")
        assertEquals(FioStatus.NOT_FOUND, r.status)
    }

    @Test fun surnameMayorovIsNotRankStopWord() {
        assertEquals("Майоров Денис Игоревич", primary("Выдана Майорову Денису Игоревичу"))
    }

    /** Выписка из приказа с телефона пользователя (2026-10-24): ФИО в родительном после звания, внизу три подписи. */
    @Test fun orderExtractGenitiveAfterRankWithSignatureBlock() {
        val t = """
            ВЫПИСКА ИЗ ПРИКАЗА
            КОМАНДИРА ВОЙСКОВОЙ ЧАСТИ 65384
            по строевой части
            «01» апреля 2026 г. № 101 ДСП
            Полагать убывшими:
            II. В командировку:
            2. Нижепоименованных военнослужащих в г. Токмак Запорожской области для выполнения специальных задач:
            1 мотострелковый батальон
            1. Майора Арефьева Павла Валерьевича - с 27.03.2026 г.
            Основание: рапорт № 6339 от 1 апреля 2026 года.
            ВрИО КОМАНДИРА ВОЙСКОВОЙ ЧАСТИ 65384
            гвардии подполковник
            С.Цахилов
            ВрИО НАЧАЛЬНИКА ШТАБА ВОЙСКОВОЙ ЧАСТИ 65384
            гвардии подполковник
            А. Султанмурадов
            ВЕРНО:
            ВрИО НАЧАЛЬНИКА ОТДЕЛЕНИЯ КОМПЛЕКТОВАНИЯ
            гвардии лейтенант
            С.Демьяненко
        """.trimIndent()
        val r = ex.extract(t)
        assertEquals("Арефьев Павел Валерьевич", r.primary?.fio?.display())
        assertEquals(FioStatus.FOUND, r.status)
        assertTrue("должно примениться автоматически, уверенность ${r.confidence}", r.autoApply)
    }
}
