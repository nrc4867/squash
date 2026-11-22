package dev.chieppa.squash.screens.look.drag

import dev.chieppa.squash.rig.PoseBreakdown
import dev.chieppa.squash.rig.PoseReport
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The drag tool's `pose-report=true`: every posing's [PoseBreakdown] while a timed later move runs, printed per gesture as each stage's
 * median and 90th percentile wall time in ms and each count's median, over the posings that finished.
 */
class PoseCollector : AutoCloseable {
    private val arrived = ConcurrentLinkedQueue<PoseBreakdown>()
    private val kept = ArrayList<PoseBreakdown>()

    init {
        PoseReport.sink = { arrived += it }
    }

    /** Drops what arrived outside a timed move. */
    fun begin() = arrived.clear()

    /** Keeps what arrived since [begin]. */
    fun keep() {
        while (true) kept += arrived.poll() ?: break
    }

    /** Prints what was kept for [label] of [name] and forgets it. */
    fun print(name: String, label: String) {
        if (kept.isEmpty()) return
        val stages = kept.flatMap { it.stages.keys }.distinct()
        println("$name: $label posings ${kept.size}, ms median / 90th:")
        for (stage in stages) {
            val times = kept.map { (it.stages[stage] ?: 0L) / 1e6 }.sorted()
            println("    %-44s %8.1f %8.1f".format(stage, times[times.size / 2], times[(times.size * 9 / 10).coerceAtMost(times.size - 1)]))
        }
        val counts = kept.flatMap { it.tallies.keys }.distinct()
        for (what in counts) {
            val values = kept.map { it.tallies[what] ?: 0L }.sorted()
            println("    %-44s %8d".format("# $what", values[values.size / 2]))
        }
        kept.clear()
    }

    override fun close() {
        PoseReport.sink = null
    }
}
