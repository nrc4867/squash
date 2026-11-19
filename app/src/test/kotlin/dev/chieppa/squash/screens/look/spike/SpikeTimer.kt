package dev.chieppa.squash.screens.look.spike

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.character.dress.PaintStyle
import dev.chieppa.squash.screens.dress.brushPlaces
import dev.chieppa.squash.character.dress.chartPlaces
import dev.chieppa.squash.character.dress.paintCommand
import dev.chieppa.squash.screens.look.drag.DragSession
import dev.chieppa.squash.screens.look.exampleDocument
import dev.chieppa.squash.screens.smoke.CloseUps
import java.io.File

/** The part each timed example's strokes run down. */
private val STROKE_PART = mapOf("woman-dressed" to PartId("torso"), "fennec-dressed" to PartId("torso"), "woman" to PartId("torso"), "fennec" to PartId("torso"))

private const val BRUSH_SHARE = 0.08f
private const val CLEAN = 0.5f

/**
 * Times a brush stroke on each of [examples], dressed, into a new garment already holding a dab: each frame of the drag sends the stroke
 * so far as the app's Paint does, and is timed from building the command to the dress being ready and to the graph settling; release is
 * the whole stroke sent at once from before it. Undone between runs.
 */
fun timeStrokes(examples: List<String>, runs: Int, root: File) {
    for (example in examples) {
        val open = DragSession(exampleDocument(example, false, File(root, "examples")), CloseUps.shippedLibrary())
        val session = open.session
        val part = STROKE_PART.getValue(example)
        val garment = paintGarment(null, session, { c, b -> listOf(chartPlaces(c, b).within(brushPlaces(c, b, part, 0.5f, 0.5f, listOf(0f)).take(1), 0.05f)) }) { open.settle() }
        open.settle()
        val character = session.document.value.character
        val body = checkNotNull(session.assembly.output.value)
        val cells = partCells(character, body, part)
        val size = cells.size.y * BRUSH_SHARE
        val chart = chartPlaces(character, body)
        val style = PaintStyle(clean = CLEAN)
        fun strokeFrames(v: Float) = brushPlaces(character, body, part, 0.15f, 0.85f, listOf(v)).let { places -> places.indices.map { i -> chart.within(places.subList(0, i + 1), size) } }
        val dressFrame = ArrayList<Double>()
        val settleFrame = ArrayList<Double>()
        val dressRelease = ArrayList<Double>()
        val settleRelease = ArrayList<Double>()
        val dressCold = ArrayList<Double>()
        val settleCold = ArrayList<Double>()
        val ran = HashMap<String, MutableList<Double>>()
        val ranCold = HashMap<String, MutableList<Double>>()
        val remeshed = ArrayList<Double>()
        val kept = ArrayList<Double>()
        fun grown() = session.sessionSurface.dress.output.value?.dress?.garments?.firstOrNull { it.cover.garment.id == garment.id }?.surface
        repeat(runs + 1) { run ->
            val timed = run > 0
            val frames = strokeFrames(0.03f + run * 0.07f)
            val gesture = session.history.begin()
            frames.forEachIndexed { i, stroke ->
                val before = grown()
                val (dress, settled) = step(open, if (timed && i > 0) ran else null) { gesture.update(character.paintCommand(body, garment, null, stroke, style)) }
                if (timed && i > 0) {
                    dressFrame += dress
                    settleFrame += settled
                    if (grown() === before) kept += dress else remeshed += dress
                }
            }
            val (dress, settled) = step(open, null) { gesture.commit() }
            if (timed) {
                dressRelease += dress
                settleRelease += settled
            }
            session.history.undo()
            open.settle()
            val whole = strokeFrames(0.53f + run * 0.07f).last()
            val (coldDress, coldSettled) = step(open, if (timed) ranCold else null) { session.send(character.paintCommand(body, garment, null, whole, style)) }
            if (timed) {
                dressCold += coldDress
                settleCold += coldSettled
            }
            session.history.undo()
            open.settle()
        }
        report(example, "drag frame, dress ready", dressFrame)
        report(example, "drag frame, settled", settleFrame)
        println("spike-time: $example drag frames re-meshed ${remeshed.size} of ${remeshed.size + kept.size}")
        report(example, "drag frame re-meshed, dress ready", remeshed)
        report(example, "drag frame kept, dress ready", kept)
        report(example, "release after drag, dress ready", dressRelease)
        report(example, "release after drag, settled", settleRelease)
        report(example, "whole stroke sent cold, dress ready", dressCold)
        report(example, "whole stroke sent cold, settled", settleCold)
        printRan(example, "drag frames", ran)
        printRan(example, "cold strokes", ranCold)
        open.close()
    }
}

/** Runs [act], returning ms until the dress node gave its result (or the graph settled) and until the graph settled. */
private fun step(open: DragSession, ran: MutableMap<String, MutableList<Double>>?, act: () -> Unit): Pair<Double, Double> {
    open.mark()
    val from = System.nanoTime()
    act()
    open.settle()
    val settled = (System.nanoTime() - from) / 1e6
    val finished = open.ran(from)
    ran?.let { finished.forEach { (node, at) -> it.getOrPut(node) { ArrayList() } += at } }
    val dress = finished["dress"] ?: settled
    return dress to settled
}

private fun report(example: String, label: String, values: List<Double>) {
    val sorted = values.sorted()
    if (sorted.isEmpty()) return
    fun at(q: Double) = sorted[((sorted.size - 1) * q).toInt()]
    println("spike-time: $example $label n=${sorted.size} p50=%.1f p90=%.1f p95=%.1f max=%.1f ms".format(at(0.5), at(0.9), at(0.95), sorted.last()))
}

private fun printRan(example: String, label: String, ran: Map<String, List<Double>>) {
    val line = ran.entries.sortedBy { it.value.sorted()[it.value.size / 2] }.joinToString { (node, at) -> "$node ${at.size}x %.0f".format(at.sorted()[at.size / 2]) }
    println("spike-time: $example $label ran: $line")
}

/** The shapes each subject's painted garments are made from when timing a whole build, one garment a shape. */
private val BUILD_SHAPES = listOf("slanted-hem", "forearm-sleeve", "wavy-boot")

/**
 * Times opening each of [examples] whole, as the app opens a character, once with a garment painted from each of [BUILD_SHAPES] added:
 * ms from making the session until its graphs settle, the first open untimed.
 */
fun timeBuilds(examples: List<String>, shapes: Map<String, List<SpikeShape>>, runs: Int, root: File) {
    for (example in examples) {
        val plain = exampleDocument(example, false, File(root, "examples"))
        val painted = dev.chieppa.squash.screens.TestWork().use { work ->
            val session = dev.chieppa.squash.character.session.CharacterSession(plain, CloseUps.shippedLibrary(), work.scope, work.dispatcher)
            work.settle(session)
            for (shape in shapes.getValue(example).filter { it.name in BUILD_SHAPES }) paintGarment(work, session, shape.areas) { work.settle(session) }
            session.document.value
        }
        for ((label, document) in listOf("bare" to plain, "with painted garments" to painted)) {
            val times = ArrayList<Double>()
            repeat(runs + 1) { run ->
                val from = System.nanoTime()
                DragSession(document, CloseUps.shippedLibrary()).close()
                if (run > 0) times += (System.nanoTime() - from) / 1e6
            }
            report(example, "open $label", times)
        }
    }
}
