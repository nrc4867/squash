package dev.chieppa.squash.screens.look.drag

import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.screens.sheet.PoseView
import dev.chieppa.squash.screens.sheet.drawsSkin
import jdk.jfr.Configuration
import jdk.jfr.Recording
import java.io.File

/**
 * Plays gestures and switches on [open], once to warm and then [runs] times, and prints each step's time until the graph settles; with a
 * [record] folder, the timed runs of each gesture are recorded under the JDK's flight recorder into it; with [poses], each later move's
 * posings are broken down by stage.
 */
class DragTimer(private val open: DragSession, private val runs: Int, private val record: File? = null, private val poses: PoseCollector? = null) {
    private val history get() = open.session.history
    private var lastFrom = 0L

    /**
     * Plays [script] through one transaction, timing each pointer move, the release, the undo and the redo, with the weights shown as the
     * app shows them in the script's stage with the panes posed; the character ends as it began.
     */
    fun time(name: String, script: DragScript) {
        open.session.sessionSurface.skinShown.value = drawsSkin(script.stage, PoseView.POSED, open.session.document.value.character.shownPose)
        open.settle()
        val first = ArrayList<Double>()
        val frames = ArrayList<Double>()
        val shown = ArrayList<Double>()
        val release = ArrayList<Double>()
        val undo = ArrayList<Double>()
        val redo = ArrayList<Double>()
        val ran = HashMap<String, MutableList<Double>>()
        val ranOnRelease = HashMap<String, MutableList<Double>>()
        val ranOnUndo = HashMap<String, MutableList<Double>>()
        val ranOnRedo = HashMap<String, MutableList<Double>>()
        var recording: Recording? = null
        repeat(runs + 1) { run ->
            val timed = run > 0
            if (run == 1) recording = record?.let { Recording(Configuration.getConfiguration("profile")).apply { start() } }
            val gesture = history.begin()
            script.frames(run).forEachIndexed { i, command ->
                val later = timed && i > 0
                if (later) poses?.begin()
                val took = step(if (later) ran else null) { gesture.update(command) }
                if (later) poses?.keep()
                if (timed) (if (i == 0) first else frames) += took
                if (timed && i > 0) shown += open.ran(lastFrom).filterKeys { it in SHOWN }.values.maxOrNull() ?: took
            }
            val released = step(if (timed) ranOnRelease else null) { gesture.commit() }
            val undone = step(if (timed) ranOnUndo else null) { history.undo() }
            val redone = step(if (timed) ranOnRedo else null) { history.redo() }
            step(null) { history.undo() }
            if (timed) {
                release += released
                undo += undone
                redo += redone
            }
        }
        recording?.let {
            it.stop()
            record?.mkdirs()
            it.dump(File(record, "$name ${script.label}".replace(' ', '-') + ".jfr").toPath())
            it.close()
        }
        printStats(name, "${script.label} first move", first)
        printStats(name, "${script.label} each later move", frames)
        printStats(name, "${script.label} each later move shown", shown)
        printStats(name, "${script.label} release", release)
        printStats(name, "${script.label} undo", undo)
        printStats(name, "${script.label} redo", redo)
        printRan(name, "${script.label} moves", ran)
        poses?.print(name, "${script.label} each later move")
        printRan(name, "${script.label} release", ranOnRelease)
        printRan(name, "${script.label} undo", ranOnUndo)
        printRan(name, "${script.label} redo", ranOnRedo)
    }

    /** Switches to the first outfit after the shown one and back, timing each until the graph settles. */
    fun switchOutfits(name: String) {
        val wardrobe = open.session.document.value.character.wardrobe
        val shown = wardrobe.shown ?: return
        val next = wardrobe.outfits.map { it.id }.firstOrNull { it != shown } ?: return
        val to = ArrayList<Double>()
        val back = ArrayList<Double>()
        val ran = HashMap<String, MutableList<Double>>()
        repeat(runs + 1) { run ->
            val there = step(ran.takeIf { run > 0 }) { open.session.send(ShowOutfit(next)) }
            val home = step(ran.takeIf { run > 0 }) { open.session.send(ShowOutfit(shown)) }
            if (run > 0) {
                to += there
                back += home
            }
        }
        printStats(name, "outfit switch to ${next.value}", to)
        printStats(name, "outfit switch back to ${shown.value}", back)
        printRan(name, "outfit switches", ran)
    }

    /** Runs [act] and returns the ms until the graph settles on it, adding to [ran] when each watched node gave its last result. */
    private fun step(ran: MutableMap<String, MutableList<Double>>?, act: () -> Unit): Double {
        open.mark()
        val from = System.nanoTime()
        lastFrom = from
        act()
        open.settle()
        val took = (System.nanoTime() - from) / 1e6
        ran?.let { open.ran(from).forEach { (node, at) -> it.getOrPut(node) { ArrayList() } += at } }
        return took
    }

    private fun printRan(name: String, label: String, ran: Map<String, List<Double>>) {
        val line = ran.entries.sortedBy { it.value.sorted()[it.value.size / 2] }.joinToString { (node, at) ->
            "$node ${at.size}x %.0f".format(at.sorted()[at.size / 2])
        }
        println("$name: $label ran: $line")
    }

    private companion object {
        /** The watched nodes a pane draws from: the surface, its paint as shown, its dress, and its posed form where the pose shows. */
        val SHOWN = setOf("surface", "shown paint", "dress", "posed surface")
    }
}
