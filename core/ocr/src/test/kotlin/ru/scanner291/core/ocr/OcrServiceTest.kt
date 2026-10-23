package ru.scanner291.core.ocr

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import ru.scanner291.core.common.model.FilterType
import ru.scanner291.core.common.model.NRect
import ru.scanner291.core.common.model.OcrWord
import ru.scanner291.core.common.model.PageRecipe
import ru.scanner291.core.database.PageEntity
import ru.scanner291.core.database.PageTextEntity

/** Этап 10: распознанный текст не попадает в logcat (ни при успехе, ни при ошибке); кодеки слоёв. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OcrServiceTest {
    private val secret = "Ефрейтор Секретов Тайна Иванович паспорт 4512 345678"
    private val page = PageEntity("page-123456789", "doc-1", 0, "docs/doc-1/original/p.jpg", "sha", "image/jpeg", 1, 10, 10, null, 0)
    private val saved = ArrayList<PageTextEntity>()
    private var lastRecipe: PageRecipe? = null

    private val source = object : OcrPageSource {
        override suspend fun image(page: PageEntity, recipe: PageRecipe): Bitmap { lastRecipe = recipe; return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888) }
        override suspend fun save(t: PageTextEntity) { saved += t }
    }

    @Before fun clearLog() { ShadowLog.clear(); ShadowLog.stream = null }

    private fun allLogs() = ShadowLog.getLogs().joinToString("\n") { "${it.tag}: ${it.msg} ${it.throwable?.message.orEmpty()}" }

    @Test fun recognizedTextNeverReachesLogcat() = runBlocking {
        val words = secret.split(' ').mapIndexed { i, w -> OcrWord(w, NRect(i * 0.1f, 0f, i * 0.1f + 0.09f, 0.05f), 0.9f) }
        val svc = OcrService({ _, _ -> OcrResult(secret, words, 42) }, source)
        val t = svc.recognizePage(page, PageRecipe(filter = FilterType.PHOTO), OcrMode.FAST).getOrThrow()
        assertEquals(secret, t.text); assertEquals(1, saved.size)
        assertEquals(words, WordsCodec.decode(t.words))
        val logs = allLogs()
        assertTrue("служебная запись должна быть: $logs", ShadowLog.getLogsForTag("Scanner291Ocr").isNotEmpty())
        for (w in secret.split(' ')) assertFalse("в журнале найдено «$w»: $logs", logs.contains(w))
        // Для OCR «Фото» заменяется на «Усиленный текст», ручные параметры сбрасываются
        assertEquals(FilterType.TEXT_BOOST, lastRecipe!!.filter)
    }

    @Test fun failureIsLoggedWithoutText() = runBlocking {
        val svc = OcrService({ _, _ -> throw IllegalStateException("сбой на словах: $secret") }, source)
        assertTrue(svc.recognizePage(page, PageRecipe(), OcrMode.FAST).isFailure)
        val logs = allLogs()
        assertTrue(ShadowLog.getLogsForTag("Scanner291Ocr").isNotEmpty())
        assertFalse("текст исключения не должен попадать в журнал: $logs", logs.contains("Секретов"))
    }

    @Test fun checksumsAndRecipeChoice() {
        val m = Tessdata.parseChecksums("# комментарий\nABC  rus.traineddata\n\n def eng.traineddata ")
        assertEquals(mapOf("rus.traineddata" to "abc", "eng.traineddata" to "def"), m)
        assertEquals(FilterType.HYBRID, OcrService.ocrRecipe(PageRecipe(filter = FilterType.HYBRID)).filter)
        assertEquals(FilterType.TEXT_BOOST, OcrService.ocrRecipe(PageRecipe(filter = FilterType.COLOR, brightness = 0.5f)).filter)
        assertEquals(0f, OcrService.ocrRecipe(PageRecipe(brightness = 0.5f)).brightness)
        assertEquals(emptyList<OcrWord>(), WordsCodec.decode("мусор\nбез\tполей"))
    }
}
