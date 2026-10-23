package ru.scanner291.core.storage

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.scanner291.core.common.storage.AtomicFileWriter
import ru.scanner291.core.common.storage.OperationJournal
import ru.scanner291.core.common.storage.RecoveryDecision
import ru.scanner291.core.storage.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Восстановление при старте процесса (ТЗ v3.0, ч. X): откат незавершённых операций журнала
 * и удаление брошенных временных файлов. Выполняется в фоне и не задерживает первый кадр.
 * В лог пишутся только счётчики — без ФИО, текста OCR и путей к документам.
 */
@Singleton
class StartupRecovery @Inject constructor(
    private val paths: DocumentPaths,
    private val journal: OperationJournal,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _recoveredOps = MutableStateFlow(0)
    /** Сколько незавершённых операций было найдено и обработано при последнем запуске. */
    val recoveredOps: StateFlow<Int> = _recoveredOps.asStateFlow()

    fun start() {
        scope.launch(Dispatchers.IO) { runNow() }
    }

    internal fun runNow() {
        runCatching {
            paths.ensureCreated()
            // Пока нет операций, которые безопасно доводить до конца, все незавершённые
            // откатываются: удаляются только их частичные НОВЫЕ выходы.
            // Перенос в корзину получит ROLL_FORWARD на этапе 14 (иначе возможна потеря).
            val handled = journal.recover { RecoveryDecision.ROLL_BACK }
            _recoveredOps.value = handled.size
            val cleaned = AtomicFileWriter.cleanupStaleTemps(paths.filesRoot, STALE_TEMP_MS) +
                AtomicFileWriter.cleanupStaleTemps(paths.work, STALE_TEMP_MS)
            Log.i(TAG, "startup recovery: ops=${handled.size}, staleTemps=$cleaned")
        }.onFailure { Log.e(TAG, "startup recovery failed", it) }
    }

    private companion object {
        const val TAG = "Scanner291"
        /** Запас 10 минут: не трогаем временный файл, который пишет только что запущенный воркер. */
        const val STALE_TEMP_MS = 10 * 60 * 1000L
    }
}
