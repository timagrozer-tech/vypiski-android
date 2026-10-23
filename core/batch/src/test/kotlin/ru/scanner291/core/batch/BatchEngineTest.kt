package ru.scanner291.core.batch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Этап 15: ошибка в 1 из 30 не теряет 29; отмена; повтор ошибочных; лимит параллелизма OCR; восстановление. */
class BatchEngineTest {
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private suspend fun BatchEngine.await(id: String) = withTimeout(10_000) { jobs.first { l -> l.first { it.id == id }.finished }.first { it.id == id } }

    @Test fun oneFailureDoesNotLoseTheOther29() = runBlocking {
        val s = scope(); val e = BatchEngine(s, MemoryBatchStore())
        val processed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        e.register(TaskKind.PROCESS) { item -> if (item == "doc17") error("повреждённый файл"); delay(2); processed += item }
        val id = e.submit("Обработка", TaskKind.PROCESS, (1..30).map { "doc$it" })
        val j = e.await(id)
        assertEquals(JobState.DONE_WITH_ERRORS, j.state)
        assertEquals(29, j.done.size); assertEquals(29, processed.size)
        assertEquals(listOf(ItemFailure("doc17", "повреждённый файл")), j.failed)
        assertEquals(1f, j.progress)
        s.cancel()
    }

    @Test fun ocrParallelismIsLimited() = runBlocking {
        val s = scope(); val e = BatchEngine(s, MemoryBatchStore(), mapOf(TaskKind.OCR to 2))
        val now = AtomicInteger(); val peak = AtomicInteger()
        e.register(TaskKind.OCR) { val n = now.incrementAndGet(); peak.accumulateAndGet(n, ::maxOf); delay(10); now.decrementAndGet() }
        val id = e.submit("OCR", TaskKind.OCR, (1..12).map { "p$it" })
        e.await(id)
        assertTrue("одновременно ${peak.get()}", peak.get() <= 2)
        s.cancel()
    }

    @Test fun cancelKeepsDoneAndStops() = runBlocking {
        val s = scope(); val e = BatchEngine(s, MemoryBatchStore(), mapOf(TaskKind.EXPORT to 1))
        val gate = CompletableDeferred<Unit>(); val count = AtomicInteger()
        // Третий элемент «зависает» до отмены — проверка не зависит от скорости машины
        e.register(TaskKind.EXPORT) { if (count.incrementAndGet() >= 3) { gate.complete(Unit); kotlinx.coroutines.awaitCancellation() } }
        val id = e.submit("Экспорт", TaskKind.EXPORT, (1..20).map { "d$it" })
        gate.await(); e.cancel(id)
        delay(100)
        val j = e.jobs.value.first { it.id == id }
        assertEquals(JobState.CANCELLED, j.state)
        assertEquals(2, j.done.size)
        assertEquals("после отмены новые элементы не запускаются", 3, count.get())
        s.cancel()
    }

    @Test fun retryRunsOnlyFailed() = runBlocking {
        val s = scope(); val e = BatchEngine(s, MemoryBatchStore())
        var broken = true; val calls = java.util.concurrent.ConcurrentHashMap<String, Int>()
        e.register(TaskKind.PROCESS) { item -> calls.merge(item, 1, Int::plus); if (item == "b" && broken) error("нет места") }
        val id = e.submit("x", TaskKind.PROCESS, listOf("a", "b", "c"))
        assertEquals(JobState.DONE_WITH_ERRORS, e.await(id).state)
        broken = false; e.retryFailed(id)
        val j = e.await(id)
        assertEquals(JobState.DONE, j.state); assertEquals(setOf("a", "b", "c"), j.done)
        assertEquals(mapOf("a" to 1, "b" to 2, "c" to 1), calls.toMap())
        s.cancel()
    }

    @Test fun restoreContinuesUnfinishedWithoutRepeating() = runBlocking {
        val store = MemoryBatchStore()
        store.saved = listOf(BatchJob("j1", "OCR", TaskKind.OCR, listOf("p1", "p2", "p3"), done = setOf("p1"), state = JobState.RUNNING))
        val s = scope(); val e = BatchEngine(s, store)
        val calls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        e.register(TaskKind.OCR) { calls += it }
        e.restore()
        val j = e.await("j1")
        assertEquals(JobState.DONE, j.state); assertEquals(setOf("p2", "p3"), calls.toSet())
        assertEquals(j, store.saved.single())
        s.cancel()
    }
}
