package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.SurfaceBuilder
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.TestWork
import java.util.concurrent.Executors
import kotlin.time.measureTime

/** Runs timed once warm, the median kept. */
private const val TIMED_RUNS = 21

/** Builds run before timing starts. */
private const val WARM_RUNS = 5

/** How much larger an edit makes a part. */
private const val EDIT_GROWTH = 1.02f

/** One part edit to time: the part, the parameter that sizes it and that parameter's value at rest. */
class SurfaceEdit(val label: String, val part: PartId, val parameter: String, val rest: Float)

/** A character's one surface as numbers: its size, whether it is closed, its outline and join lines per view, and how long builds take. */
class SurfaceChecks(private val session: CharacterSession, private val work: TestWork) {
    private val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val runAll: (List<() -> Unit>) -> Unit = { tasks -> tasks.map { pool.submit(it) }.forEach { it.get() } }

    fun report(name: String, surface: CharacterSurface, views: List<Pair<String, Vec3>>) {
        val mesh = surface.mesh
        val (open, crowded) = edgeUse(mesh.triangles)
        println("$name: one surface ${mesh.vertexCount} vertices, ${mesh.triangleCount} triangles, ${mesh.blocks} blocks at cell %.5f, $open open edges, $crowded edges on more than two triangles".format(mesh.cell))
        for ((view, toViewer) in views) {
            val runs = surface.lines.smoothContours { toViewer }
            println("$name: one surface outline $view length %.2f in ${runs.size} pieces".format(runs.sumOf { run -> length(run.points.map { it.position }) }))
        }
        println("$name: join lines ${surface.joins.size}, length %.3f".format(surface.joins.sumOf { line -> length(line.map { it.position }) }))
    }

    /** Median times of a whole build and of each of [edits], 2% larger and back, at the full cell, and with [coarse] also at the coarse one. */
    fun timings(name: String, edits: List<SurfaceEdit>, coarse: Set<String>) {
        val rest = placed()
        val whole = median { SurfaceBuilder().build(rest, runAll = runAll) }
        println("$name: one surface whole build %.1f ms".format(whole))
        for (edit in edits) {
            session.send(SetParameter(edit.part, edit.parameter, edit.rest * EDIT_GROWTH))
            val larger = placed()
            session.send(SetParameter(edit.part, edit.parameter, edit.rest))
            work.settle(session)
            for (dragging in listOf(false, true)) {
                if (dragging && edit.label !in coarse) continue
                val builder = SurfaceBuilder()
                builder.build(rest, dragging, runAll)
                var flip = false
                val took = median {
                    flip = !flip
                    builder.build(if (flip) larger else rest, dragging, runAll)
                }
                println("$name: one surface edit ${edit.label} ${if (dragging) "coarse" else "full"} cell %.1f ms".format(took))
            }
        }
    }

    /** Where the time of a whole build and of each of [edits] goes, as [SurfaceProfile] prints it. */
    fun profile(name: String, edits: List<SurfaceEdit>) {
        val rest = placed()
        for (edit in edits) {
            session.send(SetParameter(edit.part, edit.parameter, edit.rest * EDIT_GROWTH))
            val larger = placed()
            session.send(SetParameter(edit.part, edit.parameter, edit.rest))
            work.settle(session)
            SurfaceProfile.run("$name ${edit.label}", rest, larger)
        }
    }

    fun close() = pool.shutdown()

    private fun placed(): AssembledCharacter {
        work.settle(session)
        return checkNotNull(session.placed.output.value)
    }

    private fun median(run: () -> Unit): Double {
        repeat(WARM_RUNS) { run() }
        val runs = List(TIMED_RUNS) { measureTime(run).inWholeMicroseconds / 1000.0 }
        return runs.sorted()[TIMED_RUNS / 2]
    }
}

/** How many edges of [triangles] lie on one triangle only, and how many on more than two. */
private fun edgeUse(triangles: IntArray): Pair<Int, Int> {
    val uses = HashMap<Long, Int>()
    for (t in 0 until triangles.size / 3) for (c in 0 until 3) {
        val a = triangles[t * 3 + c]
        val b = triangles[t * 3 + (c + 1) % 3]
        val key = minOf(a, b).toLong() shl 32 or maxOf(a, b).toLong()
        uses[key] = (uses[key] ?: 0) + 1
    }
    return uses.values.count { it == 1 } to uses.values.count { it > 2 }
}

private fun length(points: List<Vec3>): Double = points.zipWithNext { a, b -> (b - a).length.toDouble() }.sum()
