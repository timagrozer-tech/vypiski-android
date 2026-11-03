package ru.revvl.cameralab

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory, offline event journal (ring buffer). Nothing is written to disk or sent anywhere
 * unless the user exports the diagnostics report. No personal data, no location.
 */
class EventLog(private val capacity: Int = 300, private val clock: () -> Long = { System.currentTimeMillis() }) {
    enum class Level { INFO, WARN, ERROR }

    data class Entry(val timeMs: Long, val level: Level, val tag: String, val message: String)

    private val entries = ArrayDeque<Entry>()
    private val latencies = HashMap<String, ArrayDeque<Long>>()

    init { require(capacity > 0) }

    @Synchronized fun info(tag: String, message: String) = add(Level.INFO, tag, message)
    @Synchronized fun warn(tag: String, message: String) = add(Level.WARN, tag, message)
    @Synchronized fun error(tag: String, message: String) = add(Level.ERROR, tag, message)

    private fun add(level: Level, tag: String, message: String) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(Entry(clock(), level, tag, message.take(300)))
    }

    /** Keeps the last [LATENCY_SAMPLES] samples per kind ("photo", "bind", "burst", …). */
    @Synchronized fun latency(kind: String, ms: Long) {
        val q = latencies.getOrPut(kind) { ArrayDeque() }
        if (q.size == LATENCY_SAMPLES) q.removeFirst()
        q.addLast(ms.coerceAtLeast(0))
    }

    @Synchronized fun snapshot(): List<Entry> = entries.toList()

    @Synchronized fun latencySamples(kind: String): List<Long> = latencies[kind]?.toList() ?: emptyList()

    @Synchronized fun latencyKinds(): List<String> = latencies.keys.sorted()

    @Synchronized fun clear() {
        entries.clear()
        latencies.clear()
    }

    /** Newest last; at most [limit] lines. */
    fun format(limit: Int = 120): String {
        val df = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return snapshot().takeLast(limit).joinToString("\n") { e ->
            val mark = when (e.level) { Level.INFO -> " "; Level.WARN -> "!"; Level.ERROR -> "✗" }
            "${df.format(Date(e.timeMs))} $mark ${e.tag}: ${e.message}"
        }
    }

    fun latencyReport(): String = latencyKinds().joinToString("\n") { kind ->
        val s = LatencyStats.of(latencySamples(kind))
        "$kind: n=${s.count}, медиана ${s.median} мс, p90 ${s.p90} мс, макс ${s.max} мс"
    }.ifEmpty { "замеров пока нет" }

    companion object {
        const val LATENCY_SAMPLES = 50
    }
}

/** Process-wide journal used by the activity and the burst controller. */
object AppLog {
    val log = EventLog()
}

data class LatencyStats(val count: Int, val median: Long, val p90: Long, val max: Long) {
    companion object {
        /** Nearest-rank percentiles; empty input gives zeros. */
        fun of(samples: List<Long>): LatencyStats {
            if (samples.isEmpty()) return LatencyStats(0, 0, 0, 0)
            val s = samples.sorted()
            fun rank(p: Double): Long = s[(Math.ceil(p * s.size).toInt() - 1).coerceIn(0, s.size - 1)]
            return LatencyStats(s.size, rank(0.5), rank(0.9), s.last())
        }
    }
}

// ---- session probe: which declared combinations does the firmware really accept? ----

/** What a lens declares (from CameraX/Camera2), input to the probe plan. */
data class ProbeLens(
    val id: String,
    val label: String,
    val photoFormats: Set<Int>,
    val extensions: List<Int>,
    val videoQualities: List<String>
)

enum class ProbeKind { PHOTO, EXTENSION, VIDEO }

data class ProbeCase(val lensId: String, val lensLabel: String, val kind: ProbeKind, val value: Int, val name: String)

data class ProbeResult(val case: ProbeCase, val ok: Boolean, val detail: String, val elapsedMs: Long)

object ProbePlan {
    const val MAX_CASES = 40
    /** Time to let an asynchronous camera error surface after a successful bind. */
    const val SETTLE_MS = 900L

    /**
     * Per lens: every declared photo format (JPEG first), every declared OEM extension,
     * every declared SDR video quality. Capped at [MAX_CASES] to bound the run time.
     */
    fun build(
        lenses: List<ProbeLens>,
        formatName: (Int) -> String,
        extensionName: (Int) -> String,
        jpeg: Int
    ): List<ProbeCase> {
        val out = ArrayList<ProbeCase>()
        for (l in lenses) {
            val formats = l.photoFormats.sortedWith(compareBy<Int> { it != jpeg }.thenBy { it })
            for (f in formats) out += ProbeCase(l.id, l.label, ProbeKind.PHOTO, f, "фото ${formatName(f)}")
            for (e in l.extensions) out += ProbeCase(l.id, l.label, ProbeKind.EXTENSION, e, "OEM ${extensionName(e)}")
            l.videoQualities.forEachIndexed { i, q -> out += ProbeCase(l.id, l.label, ProbeKind.VIDEO, i, "видео $q") }
        }
        return out.take(MAX_CASES)
    }

    fun estimatedSeconds(cases: Int): Int = ((cases * (SETTLE_MS + 400)) / 1000).toInt() + 1

    fun report(results: List<ProbeResult>): String {
        if (results.isEmpty()) return "Проверка сочетаний не запускалась"
        val ok = results.count { it.ok }
        return buildString {
            appendLine("Проверка сочетаний: принято $ok из ${results.size}")
            for ((lens, group) in results.groupBy { it.case.lensId to it.case.lensLabel }) {
                appendLine("— ${lens.second}")
                for (r in group) {
                    appendLine("   ${if (r.ok) "✓" else "✗"} ${r.case.name} · ${r.elapsedMs} мс" +
                        (if (r.ok) "" else " · ${r.detail}"))
                }
            }
        }.trimEnd()
    }
}
