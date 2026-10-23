package ru.scanner291.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import ru.scanner291.core.storage.StartupRecovery
import javax.inject.Inject

/**
 * Точка входа процесса. Восстановление после сбоя запускается в фоне и не задерживает
 * первый кадр (ТЗ v3.0: без искусственной задержки запуска).
 */
@HiltAndroidApp
class Scanner291App : Application() {
    @Inject lateinit var startupRecovery: StartupRecovery

    override fun onCreate() {
        super.onCreate()
        startupRecovery.start()
    }
}
