package dev.chieppa.squash.screens.look.drag

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ShowOutfit
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
        DragSession(document, CloseUps.shippedLibrary()).use { open ->
            val outfit = look.outfits?.firstOrNull()?.let { id -> document.character.wardrobe.outfits.first { it.id.value == id }.id }
            outfit?.let { open.session.send(ShowOutfit(it)) }
            open.settle()
            val name = "$example ${outfit?.value ?: "as saved"}"
            for (script in drag.scripts(open)) DragTimer(open, drag.runs, drag.record).time(name, script)
            if (drag.switch) DragTimer(open, drag.runs).switchOutfits(name)
        }
    }
    exitProcess(0)
}

/**
 * What the drag command was asked for: `drags=move:<part>,pose:<part>:<joint>`, `switch=true` for the outfit switch, `runs=<n>`, and
 * `jfr=<folder>` to record each gesture's timed runs.
 */
class DragArgs(args: Array<String>) {
    private val values: Map<String, String> = args.flatMap { it.split(' ') }.filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    val runs: Int = values["runs"]?.toInt() ?: DEFAULT_RUNS
    val switch: Boolean = values["switch"] == "true"
    val record: File? = values["jfr"]?.let(::File)
    private val drags: List<List<String>> = values["drags"]?.split(',')?.map { it.split(':') }.orEmpty()

    fun scripts(open: DragSession): List<DragScript> = drags.map { drag ->
        val character = open.session.document.value.character
        when (drag[0]) {
            "move" -> moveDrag(character, checkNotNull(open.session.placed.output.value), open.session.templates.plan(character.bodyPlan), PartId(drag[1]))
            "pose" -> poseDrag(character, PartId(drag[1]), drag[2])
            else -> error("No drag ${drag[0]}; known: move, pose")
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
