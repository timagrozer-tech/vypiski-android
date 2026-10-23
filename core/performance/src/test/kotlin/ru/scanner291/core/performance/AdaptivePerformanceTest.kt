package ru.scanner291.core.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePerformanceTest {
    private fun s(mem: Int, cores: Int, lowRam: Boolean = false, thermal: Int = 0, save: Boolean = false) = DeviceState(mem, cores, lowRam, thermal, save)

    @Test fun tiers() {
        assertEquals(Tier.LOW, Budgets.tier(s(128, 8)))
        assertEquals(Tier.LOW, Budgets.tier(s(512, 4)))
        assertEquals(Tier.LOW, Budgets.tier(s(512, 8, lowRam = true)))
        assertEquals(Tier.MID, Budgets.tier(s(256, 8)))
        assertEquals(Tier.HIGH, Budgets.tier(s(512, 8)))
    }

    @Test fun budgetsShrinkOnWeakOrHotDevices() {
        val high = Budgets.of(s(512, 8)); val low = Budgets.of(s(128, 4))
        assertEquals(1800, high.previewMaxSide); assertEquals(2, high.ocrParallelism)
        assertEquals(1024, low.previewMaxSide); assertEquals(1, low.ocrParallelism); assertEquals(3000, low.exportMaxLongSide)
        val hot = Budgets.of(s(512, 8, thermal = 3))
        assertTrue(hot.throttle); assertEquals(1, hot.ocrParallelism); assertEquals(1024, hot.previewMaxSide)
        val saving = Budgets.of(s(512, 8, save = true))
        assertTrue(saving.throttle); assertEquals(1, saving.ocrParallelism)
    }
}
