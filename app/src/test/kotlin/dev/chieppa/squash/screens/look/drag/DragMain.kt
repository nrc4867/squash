package dev.chieppa.squash.screens.look.drag

import dev.chieppa.squash.document.CharacterCreases
import dev.chieppa.squash.document.DEFAULT_CREASE_STRENGTH
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetCharacterCreases
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.fieldgpu.GpuFieldReads
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.screens.look.LookArgs
import dev.chieppa.squash.screens.look.exampleDocument
import dev.chieppa.squash.screens.look.measure.RunStats
import dev.chieppa.squash.screens.smoke.CloseUps
import java.io.File
import kotlin.system.exitProcess

/**
 * Times what a drag feels like: an example opened as the app opens it, on the app's dispatcher with every node the app makes, and each
 * scripted gesture played through one transaction as the tools play it, each pointer move timed until the graph has settled on it, then
 * the release, undo and redo; and an outfit switch to the next outfit and back. Each is played once to warm and then [DragArgs.runs]
 * times. Run as `./gradlew :app:drag -Pdrag="example=fennec-dressed outfits=winter drags=move:front-leg-l"`.
 */
fun main(args: Array<String>) {
    val look = LookArgs(args)
    val drag = DragArgs(args)
    for (example in look.examples) {
        val document = exampleDocument(example, look.fromFile, look.examplesFolder)
        if (drag.reads.size > 1 || drag.reads.firstOrNull() == "gpu") {
            compareReads(example, document, look, drag)
            continue
        }
        val opening = System.nanoTime()
        DragSession(document, CloseUps.shippedLibrary()).use { open ->
            val outfit = look.outfits?.firstOrNull()?.let { id -> document.character.wardrobe.outfits.first { it.id.value == id }.id }
            outfit?.let { open.session.send(ShowOutfit(it)) }
            if (drag.creases) open.session.send(SetCharacterCreases(CharacterCreases(DEFAULT_CREASE_STRENGTH, DEFAULT_CREASE_STRENGTH)))
            open.settle()
            val name = "$example ${outfit?.value ?: "as saved"}${if (drag.creases) " creased" else ""}"
            println("$name: open ${(System.nanoTime() - opening) / 1_000_000} ms")
            for (script in drag.scripts(open)) DragTimer(open, drag.runs, drag.record).time(name, script)
            if (drag.switch) DragTimer(open, drag.runs).switchOutfits(name)
        }
    }
    exitProcess(0)
}

/** Plays [document]'s gestures on one session for each of [DragArgs.reads] in turn, play by play. */
private fun compareReads(example: String, document: SquashDocument, look: LookArgs, drag: DragArgs) {
    val gpu = GpuFieldReads()
    val sides = drag.reads.map { it to DragSession(document, CloseUps.shippedLibrary(), if (it == "gpu") gpu else CpuFieldReads) }
    val outfit = look.outfits?.firstOrNull()?.let { id -> document.character.wardrobe.outfits.first { it.id.value == id }.id }
    for ((_, open) in sides) {
        outfit?.let { open.session.send(ShowOutfit(it)) }
        open.settle()
    }
    val name = "$example ${outfit?.value ?: "as saved"}"
    for (n in drag.scriptCount) DragAb(sides, drag.runs).time(name) { open -> drag.scripts(open)[n] }
    gpu.failure?.let { println("$name: GPU reads failed: $it") }
    println("$name: GPU reads in all: ${gpu.stats}")
    sides.forEach { it.second.close() }
    gpu.close()
}

/**
 * What the drag command was asked for: `drags=move:<part>,pose:<part>:<joint>,fit:<garment>,ease:<garment>:<part>`, `switch=true` for the outfit switch, `runs=<n>`,
 * `creases=on` to play them with the character's creases and wrinkles on, and `jfr=<folder>` to record each gesture's timed runs; `reads=cpu,gpu` plays each gesture on a session reading its fields on each, in turn.
 */
class DragArgs(args: Array<String>) {
    private val values: Map<String, String> = args.flatMap { it.split(' ') }.filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    val runs: Int = values["runs"]?.toInt() ?: DEFAULT_RUNS
    val switch: Boolean = values["switch"] == "true"
    val creases: Boolean = values["creases"] == "on"
    val record: File? = values["jfr"]?.let(::File)
    val reads: List<String> = values["reads"]?.split(',').orEmpty()
    private val drags: List<List<String>> = values["drags"]?.split(',')?.map { it.split(':') }.orEmpty()

    val scriptCount: IntRange get() = drags.indices

    fun scripts(open: DragSession): List<DragScript> = drags.map { drag ->
        val character = open.session.document.value.character
        when (drag[0]) {
            "move" -> moveDrag(character, checkNotNull(open.session.placed.output.value), open.session.templates.plan(character.bodyPlan), PartId(drag[1]))
            "pose" -> poseDrag(character, PartId(drag[1]), drag[2])
            "fit" -> fitDrag(character, GarmentId(drag[1]))
            "ease" -> easeDrag(character, GarmentId(drag[1]), PartId(drag[2]))
            else -> error("No drag ${drag[0]}; known: move, pose, fit, ease")
        }
    }

    private companion object {
        const val DEFAULT_RUNS = 3
    }
}

/** Prints [stats] of [label] for [name], with no line when there are none. */
fun printStats(name: String, label: String, stats: List<Double>) {
    if (stats.isNotEmpty()) println("$name: $label ${RunStats(stats)} over ${stats.size}")
}
