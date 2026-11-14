package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.HeldShapes
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CharacterField
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.HiddenSkin
import dev.chieppa.squash.character.surface.PaintMemo
import dev.chieppa.squash.character.surface.GarmentBuilder
import dev.chieppa.squash.character.surface.GarmentCover
import dev.chieppa.squash.character.surface.SurfaceBuilder
import dev.chieppa.squash.character.surface.SurfaceEyes
import dev.chieppa.squash.character.surface.SurfacePaint
import dev.chieppa.squash.character.surface.surfaceCovers
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.rig.GarmentSkins
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.SessionRig
import dev.chieppa.squash.rig.SurfaceSkin
import dev.chieppa.squash.rig.rigOf
import dev.chieppa.squash.screens.TestWork
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.time.measureTime

private const val TIMED_RUNS = 21
private const val WARM_RUNS = 5
private const val EDIT_GROWTH = 1.02f

/**
 * Times a one surface with everything on it, as the graph runs it: the surface meshed; then its garments grown, meshed and cut beside
 * the surface's finishing; then beside one another its paint and eyes, its weights, and the skin the garments hide and their weights;
 * each median of [TIMED_RUNS] warm runs. The rig itself is the parts' own and is reported apart.
 */
class SurfaceStackTimings(private val session: CharacterSession, private val work: TestWork) {
    private val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val beside = Executors.newFixedThreadPool(STAGES)
    private val runAll: (List<() -> Unit>) -> Unit = { tasks -> tasks.map { pool.submit(it) }.forEach { it.get() } }
    private val parallel: suspend (List<() -> Unit>) -> Unit = { tasks -> runAll(tasks) }
    private val rigNode = SessionRig(session)
    private val skins = GarmentSkins()

    fun report(name: String, edits: List<SurfaceEdit>, coarse: Set<String>, profile: Boolean = false, record: java.io.File? = null) {
        val rest = placed()
        val rig = checkNotNull(rigNode.rig.output.value)
        val character = session.document.value.character
        val shapes = character.parts.mapNotNull { part -> session.heldShapes(part.id).output.value?.let { part.id to it } }.toMap()
        val eyes = character.paintedEyes
        val covers = surfaceCovers(character)
        val surface = checkNotNull(SurfaceBuilder().build(rest, runAll = runAll))
        val profiler = SurfaceStackProfile(runAll, StackInputs(shapes, eyes, covers, rig), record).takeIf { profile }
        println("$name: stack stages on the whole surface: paint and eyes %.1f ms, weights %.1f ms, dress %.1f ms".format(
            median { runBlocking { SurfacePaint.of(surface, shapes, parallel) } + runBlocking { SurfaceEyes.of(surface, eyes, parallel) } },
            median { runBlocking { SurfaceSkin.of(rig, surface, parallel) } },
            median { dress(GarmentBuilder(), surface.field, surface.mesh, rest, covers, rig) { surface } },
        ))
        println("$name: stack whole build %.1f ms".format(median { stack(SurfaceBuilder(), GarmentBuilder(), rest, false, shapes, eyes, covers, rig) }))
        for (edit in edits) {
            session.send(SetParameter(edit.part, edit.parameter, edit.rest * EDIT_GROWTH))
            val larger = placed()
            session.send(SetParameter(edit.part, edit.parameter, edit.rest))
            work.settle(session)
            for (dragging in listOf(false, true)) {
                if (dragging && edit.label !in coarse) continue
                profiler?.run("$name ${edit.label} ${if (dragging) "coarse" else "full"}", rest, larger, dragging)
                val builder = SurfaceBuilder()
                val garments = GarmentBuilder()
                val memo = PaintMemo()
                stack(builder, garments, rest, dragging, shapes, eyes, covers, rig, memo)
                var flip = false
                val took = median {
                    flip = !flip
                    stack(builder, garments, if (flip) larger else rest, dragging, shapes, eyes, covers, rig, memo)
                }
                println("$name: stack edit ${edit.label} ${if (dragging) "coarse" else "full"} cell %.1f ms".format(took))
            }
        }
        println("$name: rig of the parts' own meshes %.1f ms".format(median { rigOf(rest) }))
    }

    fun close() {
        pool.shutdown()
        beside.shutdown()
    }

    private fun stack(
        builder: SurfaceBuilder,
        garments: GarmentBuilder,
        placed: AssembledCharacter,
        dragging: Boolean,
        shapes: Map<PartId, HeldShapes>,
        eyes: List<PaintedEye>,
        covers: List<GarmentCover>,
        rig: Rig,
        memo: PaintMemo = PaintMemo(),
    ) {
        val build = checkNotNull(builder.start(placed, dragging))
        while (true) runAll(build.nextMeshing() ?: break)
        val finished = CompletableFuture<CharacterSurface>()
        val worn = beside.submit { dress(garments, build.field, build.mesh, placed, covers, rig) { finished.get() } }
        while (true) runAll(build.next() ?: break)
        val surface = build.surface
        finished.complete(surface)
        listOf(
            beside.submit { runBlocking { SurfacePaint.of(surface, shapes, parallel, memo) } + runBlocking { SurfaceEyes.of(surface, eyes, parallel) } },
            beside.submit { runBlocking { SurfaceSkin.of(rig, surface, parallel) } },
            worn,
        ).forEach { it.get() }
    }

    /** The garments grown from the body [mesh] meshes from [field], then the skin they hide on the body [body] gives once finished, and their weights. */
    private fun dress(garments: GarmentBuilder, field: CharacterField, mesh: SurfaceMesh, placed: AssembledCharacter, covers: List<GarmentCover>, rig: Rig, body: () -> CharacterSurface) {
        val build = garments.start(field, mesh, placed, covers)
        while (true) runAll(build.next() ?: break)
        val made = build.garments
        val hiding = HiddenSkin(body(), made)
        while (true) runAll(hiding.next() ?: break)
        runBlocking { hiding.worn.forEach { skins.of(rig, it, parallel) } }
    }

    private fun placed(): AssembledCharacter {
        work.settle(session)
        return checkNotNull(session.placed.output.value)
    }

    private fun median(run: () -> Unit): Double {
        repeat(WARM_RUNS) { run() }
        val runs = List(TIMED_RUNS) { measureTime(run).inWholeMicroseconds / 1000.0 }
        return runs.sorted()[TIMED_RUNS / 2]
    }

    private companion object {
        const val STAGES = 4
    }
}
