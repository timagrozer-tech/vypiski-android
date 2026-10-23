package ru.scanner291.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.scanner291.domain.storage.AtomicFileWriter
import ru.scanner291.domain.storage.OperationJournal
import ru.scanner291.domain.storage.RecoveryDecision
import java.io.File

/**
 * Точка входа процесса. На старте (в фоне, не блокируя UI):
 *  1) восстанавливает незавершённые операции из журнала (ADR-0004, раздел 16 ТЗ);
 *  2) удаляет брошенные временные файлы атомарной записи.
 *
 * Логи не содержат ФИО, текста OCR и путей к документам — только счётчики.
 */
class Scanner291App : Application() {

    /** Ручной DI-контейнер (ADR-0006). Наполняется по мере появления модулей. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch(Dispatchers.IO) { container.runStartupRecovery() }
    }
}

class AppContainer(app: Application) {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val docsDir: File = File(app.filesDir, "docs")
    val journalDir: File = File(app.filesDir, "journal")
    val workDir: File = File(app.cacheDir, "work")
    private val filesRoot: File = app.filesDir

    val journal: OperationJournal by lazy { OperationJournal(journalDir) }

    private val _recoveredOps = MutableStateFlow(0)
    /** Сколько незавершённых операций было найдено и обработано при последнем запуске. */
    val recoveredOps: StateFlow<Int> = _recoveredOps.asStateFlow()

    internal fun runStartupRecovery() {
        runCatching {
            docsDir.mkdirs(); workDir.mkdirs()
            // Пока в приложении нет операций, которые безопасно доводить до конца,
            // все незавершённые откатываются: удаляются только их частичные НОВЫЕ выходы.
            // Конкретные типы операций (перенос в корзину и т.п.) получат ROLL_FORWARD на этапе 6.
            val handled = journal.recover { RecoveryDecision.ROLL_BACK }
            _recoveredOps.value = handled.size
            // Запас 10 минут: не трогаем временный файл, который пишет только что запущенный воркер.
            val cleaned = AtomicFileWriter.cleanupStaleTemps(filesRoot, olderThanMs = STALE_TEMP_MS) +
                AtomicFileWriter.cleanupStaleTemps(workDir, olderThanMs = STALE_TEMP_MS)
            Log.i(TAG, "startup recovery: ops=${handled.size}, staleTemps=$cleaned")
        }.onFailure { Log.e(TAG, "startup recovery failed", it) }
    }

    private companion object {
        const val TAG = "Scanner291"
        const val STALE_TEMP_MS = 10 * 60 * 1000L
    }
}
