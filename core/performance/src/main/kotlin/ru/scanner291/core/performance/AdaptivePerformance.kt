package ru.scanner291.core.performance

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Параметры устройства, от которых зависят бюджеты. */
data class DeviceState(
    /** Лимит кучи приложения (ActivityManager.memoryClass), МБ. */
    val memoryClassMb: Int,
    val cores: Int,
    val lowRam: Boolean,
    /** PowerManager.THERMAL_STATUS_* (0 — норма … 6 — отключение). */
    val thermalStatus: Int,
    val powerSave: Boolean,
)

enum class Tier { LOW, MID, HIGH }

/**
 * Бюджеты работы. Превью и миниатюры уменьшаются на слабых устройствах; OCR — один поток при нагреве
 * и экономии энергии; экспорт ограничивает сторону, чтобы не упасть по памяти (кадр 12 Мп ARGB = 48 МБ).
 */
data class Budget(
    val tier: Tier,
    val previewMaxSide: Int,
    val thumbnailCacheFraction: Int,
    val ocrParallelism: Int,
    val exportMaxLongSide: Int,
    /** Тяжёлые фоновые задачи стоит отложить (перегрев/экономия). */
    val throttle: Boolean,
)

/** Чистая логика выбора бюджета — покрыта AdaptivePerformanceTest. */
object Budgets {
    fun tier(s: DeviceState): Tier = when {
        s.lowRam || s.memoryClassMb < 192 || s.cores <= 4 -> Tier.LOW
        s.memoryClassMb >= 384 && s.cores >= 8 -> Tier.HIGH
        else -> Tier.MID
    }

    fun of(s: DeviceState): Budget {
        val t = tier(s)
        // THERMAL_STATUS_MODERATE = 2: дальше — снижаем нагрузку
        val hot = s.thermalStatus >= 2
        val throttle = hot || s.powerSave
        val ocr = when {
            throttle || t == Tier.LOW -> 1
            t == Tier.MID -> 1
            else -> minOf(2, s.cores / 4)
        }
        return Budget(
            tier = t,
            previewMaxSide = if (hot) 1024 else when (t) { Tier.LOW -> 1024; Tier.MID -> 1400; Tier.HIGH -> 1800 },
            thumbnailCacheFraction = when (t) { Tier.LOW -> 16; Tier.MID -> 8; Tier.HIGH -> 8 },
            ocrParallelism = ocr,
            exportMaxLongSide = when (t) { Tier.LOW -> 3000; else -> 4200 },
            throttle = throttle,
        )
    }
}

/** Наблюдение за устройством: память, ядра, нагрев (OnThermalStatusChangedListener), экономия энергии. */
@Singleton
class AdaptivePerformance @Inject constructor(@ApplicationContext context: Context) {
    private val am = context.getSystemService(ActivityManager::class.java)
    private val pm = context.getSystemService(PowerManager::class.java)
    private fun read() = DeviceState(
        memoryClassMb = runCatching { am?.memoryClass }.getOrNull() ?: 128,
        cores = Runtime.getRuntime().availableProcessors(),
        lowRam = runCatching { am?.isLowRamDevice }.getOrNull() ?: false,
        thermalStatus = runCatching { pm?.currentThermalStatus }.getOrNull() ?: 0,
        powerSave = runCatching { pm?.isPowerSaveMode }.getOrNull() ?: false,
    )

    private val _budget = MutableStateFlow(Budgets.of(read()))
    val budget: StateFlow<Budget> = _budget

    init {
        runCatching { pm?.addThermalStatusListener { _budget.value = Budgets.of(read()) } }
    }

    fun refresh() { _budget.value = Budgets.of(read()) }
}
