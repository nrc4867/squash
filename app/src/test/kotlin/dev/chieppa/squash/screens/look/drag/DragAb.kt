package dev.chieppa.squash.screens.look.drag

import dev.chieppa.squash.screens.look.measure.RunStats
import dev.chieppa.squash.screens.sheet.PoseView
import dev.chieppa.squash.screens.sheet.drawsSkin
import java.lang.management.ManagementFactory

/**
 * Plays each gesture on several sessions of one character in turn, play by play, so each is timed beside the others under the same load:
 * each later move until its graph settles, the process's CPU time over it, the release, and when each watched node last finished in a move.
 */
class DragAb(private val sides: List<Pair<String, DragSession>>, private val runs: Int) {
    private val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean

    private class Side {
        val moves = ArrayList<Double>()
        val cpu = ArrayList<Double>()
        val release = ArrayList<Double>()
        val ran = HashMap<String, MutableList<Double>>()
    }

    fun time(name: String, scripts: (DragSession) -> DragScript) {
        val made = sides.map { (_, open) -> scripts(open) }
        sides.forEachIndexed { s, (_, open) ->
            open.session.sessionSurface.skinShown.value = drawsSkin(made[s].stage, PoseView.POSED, open.session.document.value.character.shownPose)
            open.settle()
        }
        val results = sides.map { Side() }
        repeat(runs + 1) { run ->
            sides.forEachIndexed { s, (_, open) -> play(open, made[s], run, results[s].takeIf { run > 0 }) }
        }
        sides.forEachIndexed { s, (label, _) ->
            val r = results[s]
            println("$name ${made[s].label} [$label]: each later move ${RunStats(r.moves)}, CPU a move ${RunStats(r.cpu)}, release ${RunStats(r.release)}")
            println("$name ${made[s].label} [$label] moves ran: " + r.ran.entries.sortedBy { it.value.sorted()[it.value.size / 2] }.joinToString { (node, at) -> "$node %.0f".format(at.sorted()[at.size / 2]) })
        }
        if (sides.size == 2) {
            val (a, b) = results
            val paired = a.moves.indices.map { b.moves[it] - a.moves[it] }
            val pairedCpu = a.cpu.indices.map { b.cpu[it] - a.cpu[it] }
            println("$name ${made[0].label}: ${sides[1].first} less ${sides[0].first}, move by move: median %.1f ms (p10 %.1f, p90 %.1f); CPU median %.1f ms".format(
                RunStats(paired).median, paired.sorted()[paired.size / 10], RunStats(paired).p90, RunStats(pairedCpu).median,
            ))
        }
    }

    private fun play(open: DragSession, script: DragScript, run: Int, into: Side?) {
        val history = open.session.history
        val gesture = history.begin()
        script.frames(run).forEachIndexed { i, command ->
            open.mark()
            val c0 = os.processCpuTime
            val t0 = System.nanoTime()
            gesture.update(command)
            open.settle()
            val took = (System.nanoTime() - t0) / 1e6
            if (into != null && i > 0) {
                into.moves += took
                into.cpu += (os.processCpuTime - c0) / 1e6
                open.ran(t0).forEach { (node, at) -> into.ran.getOrPut(node) { ArrayList() } += at }
            }
        }
        val t0 = System.nanoTime()
        gesture.commit()
        open.settle()
        into?.release?.add((System.nanoTime() - t0) / 1e6)
        history.undo()
        open.settle()
    }
}
