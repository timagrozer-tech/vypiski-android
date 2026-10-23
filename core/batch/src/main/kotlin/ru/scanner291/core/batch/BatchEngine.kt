package ru.scanner291.core.batch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

/** Вид задачи — у каждого свой лимит параллелизма (OCR тяжёлый: по умолчанию 1). */
enum class TaskKind { OCR, PROCESS, EXPORT, IMPORT }

enum class JobState { QUEUED, RUNNING, DONE, DONE_WITH_ERRORS, CANCELLED }

data class ItemFailure(val item: String, val reason: String)

/**
 * Снимок задачи — он же промежуточное состояние для восстановления после остановки процесса:
 * выполненные элементы не повторяются, ошибочные можно повторить отдельно.
 */
data class BatchJob(
    val id: String,
    val title: String,
    val kind: TaskKind,
    val items: List<String>,
    val done: Set<String> = emptySet(),
    val failed: List<ItemFailure> = emptyList(),
    val state: JobState = JobState.QUEUED,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val remaining: List<String> get() = items.filter { it !in done && failed.none { f -> f.item == it } }
    val progress: Float get() = if (items.isEmpty()) 1f else (done.size + failed.size).toFloat() / items.size
    val finished: Boolean get() = state == JobState.DONE || state == JobState.DONE_WITH_ERRORS || state == JobState.CANCELLED
}

/** Где хранится состояние задач (в приложении — файл JSON во внутреннем хранилище; в тестах — память). */
interface BatchStore {
    suspend fun save(jobs: List<BatchJob>)
    suspend fun load(): List<BatchJob>
}

/** Обработчик одного элемента задачи. Исключение = ошибка этого элемента (остальные продолжаются). */
fun interface ItemWorker { suspend fun run(item: String) }

/**
 * Smart Batch Engine (этап 15): единый менеджер фоновых задач. У задачи — ID, состояние, прогресс, отмена,
 * повтор ошибочных, сохранение промежуточного состояния после каждого элемента. Ошибка одного элемента
 * не останавливает остальные. Параллелизм ограничен по виду задач ([limits]), OCR по умолчанию — 1 поток.
 */
class BatchEngine(
    private val scope: CoroutineScope,
    private val store: BatchStore,
    limits: Map<TaskKind, Int> = mapOf(TaskKind.OCR to 1, TaskKind.PROCESS to 2, TaskKind.EXPORT to 2, TaskKind.IMPORT to 2),
) {
    private val gates = TaskKind.entries.associateWith { Semaphore(limits[it] ?: 1) }
    private val workers = HashMap<TaskKind, ItemWorker>()
    private val running = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val cancelled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val lock = Mutex()
    private val _jobs = MutableStateFlow<List<BatchJob>>(emptyList())
    val jobs: StateFlow<List<BatchJob>> = _jobs

    fun register(kind: TaskKind, worker: ItemWorker) { workers[kind] = worker }

    /** Восстановление после перезапуска: незавершённые задачи продолжаются с невыполненных элементов. */
    suspend fun restore() {
        val saved = store.load()
        lock.withLock { _jobs.value = saved }
        saved.filter { !it.finished }.forEach { start(it.id) }
    }

    suspend fun submit(title: String, kind: TaskKind, items: List<String>): String {
        val job = BatchJob(UUID.randomUUID().toString(), title, kind, items.distinct())
        update { it + job }
        start(job.id)
        return job.id
    }

    /** Отмена: выполненное сохраняется, оставшееся не запускается. */
    suspend fun cancel(id: String) {
        // Флаг ставится до отмены корутин: отмена дочерних доходит не мгновенно, и освободившийся семафор
        // мог достаться следующему элементу раньше, чем тот узнает об отмене.
        cancelled += id
        running.remove(id)?.cancel()
        update { l -> l.map { if (it.id == id && !it.finished) it.copy(state = JobState.CANCELLED) else it } }
    }

    /** Повтор только ошибочных элементов. */
    suspend fun retryFailed(id: String) {
        update { l -> l.map { if (it.id == id) it.copy(failed = emptyList(), state = JobState.QUEUED) else it } }
        start(id)
    }

    /** Убрать завершённую задачу из списка (данные документов не трогаются). */
    suspend fun dismiss(id: String) = update { l -> l.filterNot { it.id == id && it.finished } }

    private fun start(id: String) {
        cancelled -= id
        val j = scope.launch { execute(id) }
        running[id] = j
    }

    private suspend fun execute(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        val worker = workers[job.kind] ?: run { set(id) { it.copy(state = JobState.DONE_WITH_ERRORS, failed = it.remaining.map { r -> ItemFailure(r, "Нет обработчика") }) }; return }
        set(id) { it.copy(state = JobState.RUNNING) }
        try {
            coroutineScope {
                for (item in job.remaining) launch {
                    gates.getValue(job.kind).withPermit {
                        // Быстрый захват семафора не проверяет отмену — без этих проверок после «Отмена»
                        // успевали стартовать ещё элементы (найдено тестом cancelKeepsDoneAndStops).
                        ensureActive()
                        if (id in cancelled) throw CancellationException("Задача отменена")
                        val r = try { worker.run(item); null }
                        catch (e: CancellationException) { throw e }
                        catch (e: Throwable) { e.message ?: e.javaClass.simpleName }
                        set(id) { if (r == null) it.copy(done = it.done + item) else it.copy(failed = it.failed + ItemFailure(item, r)) }
                    }
                }
            }
            set(id) { it.copy(state = if (it.failed.isEmpty()) JobState.DONE else JobState.DONE_WITH_ERRORS) }
        } finally {
            running.remove(id)
        }
    }

    private suspend fun set(id: String, f: (BatchJob) -> BatchJob) = update { l -> l.map { if (it.id == id) f(it) else it } }

    /** Каждое изменение сохраняется — после остановки процесса выполненные элементы не теряются. */
    private suspend fun update(f: (List<BatchJob>) -> List<BatchJob>) = lock.withLock {
        val next = f(_jobs.value)
        _jobs.value = next
        store.save(next)
    }
}

class MemoryBatchStore : BatchStore {
    @Volatile var saved: List<BatchJob> = emptyList()
    override suspend fun save(jobs: List<BatchJob>) { saved = jobs }
    override suspend fun load(): List<BatchJob> = saved
}
