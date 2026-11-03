package ru.revvl.cameralab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLogTest {
    @Test fun ringBufferDropsOldest() {
        var t = 0L
        val log = EventLog(capacity = 3) { t++ }
        for (i in 1..5) log.info("t", "m$i")
        assertEquals(listOf("m3", "m4", "m5"), log.snapshot().map { it.message })
    }

    @Test fun levelsAreMarked() {
        val log = EventLog(10) { 0L }
        log.warn("bind", "fallback")
        log.error("photo", "fail")
        val text = log.format()
        assertTrue(text.contains("! bind: fallback"))
        assertTrue(text.contains("✗ photo: fail"))
    }

    @Test fun longMessagesAreTruncated() {
        val log = EventLog(10) { 0L }
        log.info("x", "a".repeat(1000))
        assertEquals(300, log.snapshot()[0].message.length)
    }

    @Test fun latencyKeepsLastSamplesPerKind() {
        val log = EventLog()
        repeat(EventLog.LATENCY_SAMPLES + 10) { log.latency("photo", it.toLong()) }
        log.latency("bind", 5)
        assertEquals(EventLog.LATENCY_SAMPLES, log.latencySamples("photo").size)
        assertEquals(10L, log.latencySamples("photo").first())
        assertEquals(listOf("bind", "photo"), log.latencyKinds())
        assertTrue(log.latencyReport().contains("bind: n=1, медиана 5 мс"))
    }

    @Test fun clearEmptiesEverything() {
        val log = EventLog()
        log.info("a", "b"); log.latency("photo", 3)
        log.clear()
        assertTrue(log.snapshot().isEmpty())
        assertEquals("замеров пока нет", log.latencyReport())
    }
}

class LatencyStatsTest {
    @Test fun percentiles() {
        val s = LatencyStats.of(listOf(100L, 300L, 200L, 400L, 1000L, 250L, 220L, 230L, 210L, 240L))
        assertEquals(10, s.count)
        assertEquals(230L, s.median)
        assertEquals(400L, s.p90)
        assertEquals(1000L, s.max)
    }

    @Test fun emptyAndSingle() {
        assertEquals(LatencyStats(0, 0, 0, 0), LatencyStats.of(emptyList()))
        assertEquals(LatencyStats(1, 7, 7, 7), LatencyStats.of(listOf(7L)))
    }
}

class ProbePlanTest {
    private val back = ProbeLens("0", "Задняя · 0", setOf(3, 0, 1), listOf(2), listOf("4K", "1080p"))
    private val front = ProbeLens("1", "Селфи · 1", setOf(0), emptyList(), listOf("1080p"))
    private fun plan(l: List<ProbeLens>) = ProbePlan.build(l, { "F$it" }, { "E$it" }, jpeg = 0)

    @Test fun jpegFirstThenOtherFormatsExtensionsVideo() {
        val p = plan(listOf(back, front))
        assertEquals(listOf("фото F0", "фото F1", "фото F3", "OEM E2", "видео 4K", "видео 1080p", "фото F0", "видео 1080p"),
            p.map { it.name })
        assertEquals(ProbeKind.VIDEO, p[4].kind)
        assertEquals("1", p.last().lensId)
    }

    @Test fun planIsCapped() {
        val many = List(20) { ProbeLens("$it", "L$it", setOf(0, 1, 3), listOf(1, 2, 3), listOf("4K", "1080p")) }
        assertEquals(ProbePlan.MAX_CASES, plan(many).size)
    }

    @Test fun reportGroupsByLensAndCountsAccepted() {
        val p = plan(listOf(front))
        val r = ProbePlan.report(listOf(ProbeResult(p[0], true, "ok", 300), ProbeResult(p[1], false, "ERROR 5", 900)))
        assertTrue(r.startsWith("Проверка сочетаний: принято 1 из 2"))
        assertTrue(r.contains("✓ фото F0 · 300 мс"))
        assertTrue(r.contains("✗ видео 1080p · 900 мс · ERROR 5"))
        assertFalse(r.contains("✓ видео"))
    }

    @Test fun emptyReport() {
        assertEquals("Проверка сочетаний не запускалась", ProbePlan.report(emptyList()))
    }

    @Test fun estimate() {
        assertEquals(14, ProbePlan.estimatedSeconds(10))
    }
}
