package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.HeldShapes
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CharacterField
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.CoverageMemo
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

/**
 * Times a one surface with everything on it, as the graph runs it: the surface meshed; then its garments grown, meshed and cut beside
 * the surface's finishing; then beside one another its paint and eyes, its weights, and the skin the garments hide and their weights;
 * each timed over [EDIT_TIMED_RUNS] warm runs, an edit reaching each run a size none of the seven builds before it was built at, as a
 * drag does; [EDIT_STATES] sizes in turn. The rig itself is the parts' own and is reported apart.
 */
class SurfaceStackTimings(private val session: CharacterSession, private val work: TestWork) {
    private val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val beside = Executors.newFixedThreadPool(STAGES)
    private val runAll: (List<() -> Unit>) -> Unit = { tasks -> tasks.map { pool.submit(it) }.forEach { it.get() } }
    private val parallel: suspend (List<() -> Unit>) -> Unit = { tasks -> runAll(tasks) }
    private val rigNode = SessionRig(session)
    private val skins = GarmentSkins()
    private val hidings = HashMap<GarmentBuilder, CoverageMemo>()

    fun report(name: String, edits: List<SurfaceEdit>, coarse: Set<String>, profile: Boolean = false, record: java.io.File? = null, switchTo: List<GarmentCover>? = null) {
        val rest = placed()
        val rig = checkNotNull(rigNode.rig.output.value)
        val character = session.document.value.character
        val shapes = character.parts.mapNotNull { part -> session.heldShapes(part.id).output.value?.let { part.id to it } }.toMap()
        val eyes = character.paintedEyes
        val covers = surfaceCovers(character)
        val surface = checkNotNull(SurfaceBuilder().build(rest, runAll = runAll))
        val profiler = SurfaceStackProfile(runAll, StackInputs(shapes, eyes, covers, rig), record).takeIf { profile }
        println("$name: stack stages on the whole surface: paint and eyes %.1f ms, weights %.1f ms, dress %.1f ms".format(
            timedRuns { runBlocking { SurfacePaint.of(surface, shapes, parallel) } + runBlocking { SurfaceEyes.of(surface, eyes, parallel) } }.median,
            timedRuns { runBlocking { SurfaceSkin.of(rig, surface, parallel) } }.median,
            timedRuns { dress(GarmentBuilder(), surface.field, surface.mesh, rest, covers, rig) { surface } }.median,
        ))
        println("$name: stack whole build ${timedRuns { stack(SurfaceBuilder(), GarmentBuilder(), rest, false, shapes, eyes, covers, rig) }}")
        for (edit in edits) {
            val states = editStates(session, work, edit, EDIT_STATES)
            for (dragging in listOf(false, true)) {
                if (dragging && edit.label !in coarse) continue
                profiler?.run("$name ${edit.label} ${if (dragging) "coarse" else "full"}", states, dragging)
                val builder = SurfaceBuilder()
                val garments = GarmentBuilder()
                val memo = PaintMemo()
                stack(builder, garments, states.first(), dragging, shapes, eyes, covers, rig, memo)
                val took = timedRuns { stack(builder, garments, states[(it + 1) % EDIT_STATES], dragging, shapes, eyes, covers, rig, memo) }
                println("$name: stack edit ${edit.label} ${if (dragging) "coarse" else "full"} cell $took")
                if (dragging) continue
                val undo = timedRuns(before = { stack(builder, garments, states[(it + 1) % EDIT_STATES], false, shapes, eyes, covers, rig, memo) }) {
                    stack(builder, garments, states[it % EDIT_STATES], false, shapes, eyes, covers, rig, memo)
                }
                println("$name: stack undo ${edit.label} $undo")
            }
        }
        switchTo?.let { other ->
            val builder = SurfaceBuilder()
            val garments = GarmentBuilder()
            val memo = PaintMemo()
            stack(builder, garments, rest, false, shapes, eyes, covers, rig, memo)
            val to = timedRuns(before = { stack(builder, garments, rest, false, shapes, eyes, covers, rig, memo) }) { stack(builder, garments, rest, false, shapes, eyes, other, rig, memo) }
            val back = timedRuns(before = { stack(builder, garments, rest, false, shapes, eyes, other, rig, memo) }) { stack(builder, garments, rest, false, shapes, eyes, covers, rig, memo) }
            println("$name: stack outfit switch to the next outfit $to, back $back")
        }
        println("$name: rig of the parts' own meshes %.1f ms".format(timedRuns { rigOf(rest) }.median))
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
        val hiding = HiddenSkin(body(), made, hidings.getOrPut(garments) { CoverageMemo() })
        while (true) runAll(hiding.next() ?: break)
        runBlocking { hiding.worn.forEach { skins.of(rig, it, parallel) } }
    }

    private fun placed(): AssembledCharacter {
        work.settle(session)
        return checkNotNull(session.placed.output.value)
    }

    private companion object {
        const val STAGES = 4
    }
}
