package dev.chieppa.squash.rig

import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * Where one posing of the one surface spends its time: wall time by stage, in the order stages were first met, and counts such as the
 * field reads it made. One breakdown is filled by one coroutine at a time; work run in parallel fills breakdowns of its own, [merged]
 * after. Stages may nest, so their times need not sum to the whole.
 */
class PoseBreakdown {
    private val nanos = LinkedHashMap<String, Long>()
    private val counts = LinkedHashMap<String, Long>()

    /** Each stage's wall time in nanoseconds. */
    val stages: Map<String, Long> get() = nanos

    /** Each count's total. */
    val tallies: Map<String, Long> get() = counts

    fun add(stage: String, took: Long) {
        nanos[stage] = (nanos[stage] ?: 0L) + took
    }

    fun count(what: String, n: Long) {
        counts[what] = (counts[what] ?: 0L) + n
    }

    /** Adds every stage and count of [other], each name after [prefix]. */
    fun merged(other: PoseBreakdown, prefix: String = "") {
        other.nanos.forEach { (stage, took) -> add(prefix + stage, took) }
        other.counts.forEach { (what, n) -> count(prefix + what, n) }
    }
}

/** Runs [block], adding its wall time to [stage] of this breakdown when there is one. */
inline fun <T> PoseBreakdown?.time(stage: String, block: () -> T): T {
    if (this == null) return block()
    val start = TimeSource.Monotonic.markNow()
    val result = block()
    add(stage, start.elapsedNow().inWholeNanoseconds)
    return result
}

/**
 * Where each posing's [PoseBreakdown] goes: nowhere while [sink] is null, which keeps posing free of the timing; the drag tool's
 * `pose-report=true` sets it.
 */
object PoseReport {
    @Volatile
    var sink: ((PoseBreakdown) -> Unit)? = null

    /** A breakdown to fill when one is wanted, else null. */
    fun start(): PoseBreakdown? = if (sink != null) PoseBreakdown() else null
}
