package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.screens.look.drag.DragSession
import dev.chieppa.squash.screens.look.measure.RunStats
import dev.chieppa.squash.screens.smoke.CloseUps
import dev.chieppa.squash.screens.smoke.PROBE_LOOK
import dev.chieppa.squash.screens.smoke.probeAzimuth
import dev.chieppa.squash.screens.smoke.shownState
import dev.chieppa.squash.screens.surface.FACE_BINS
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import kotlin.time.measureTime

/** Edits timed for each example: a part's size about 2% up and back, each settled through the whole graph. */
private val EDITS = listOf("head", "front-leg-l", "arm-l", "torso")
private const val EDIT_RUNS = 11
private const val VIEW_RUNS = 9
private const val TILE = 600
private const val HEIGHT = 900

/**
 * Prints, for each example and outfit, the triangles a pane draws (the one surface's mesh and render buffer, per part by the run that wins
 * each triangle's first corner; garments; separate parts; colour layers), the time to open the example, a pane's scene built on the CPU,
 * and a few edits settled through the whole graph. Run as `./gradlew :app:tris -Ptris="example=woman-dressed outfits=all"`.
 */
fun main(args: Array<String>) {
    val look = LookArgs(args)
    look.examples.forEach { tris(it, look) }
    kotlin.system.exitProcess(0)
}

private fun tris(example: String, look: LookArgs) {
    lateinit var open: DragSession
    val opening = measureTime { open = DragSession(exampleDocument(example, look.fromFile, look.examplesFolder), CloseUps.shippedLibrary(), look.reads) }
    println("$example: open ${opening.inWholeMilliseconds} ms")
    val session = open.session
    val work = object { fun settle(@Suppress("UNUSED_PARAMETER") s: CharacterSession) = open.settle() }
    val outfits = session.document.value.character.wardrobe.outfits.map { it.id }.filter { look.outfits == null || it.value in look.outfits }.ifEmpty { listOf(null) }
    for (outfit in outfits) {
        if (outfit != null) session.send(ShowOutfit(outfit))
        work.settle(session)
        val name = "$example ${outfit?.value ?: "as-saved"}"
        val state = session.shownState()
        val mesh = state.surface.mesh
        val perPart = sortedMapOf<String, Int>()
        for (t in 0 until mesh.triangleCount) {
            val w = mesh.winners[mesh.triangles[t * 3]]
            val part = if (w < 0) "?" else state.surface.field.runs[w].part.id?.value ?: "?"
            perPart.merge(part, 1, Int::plus)
        }
        println("$name: surface mesh ${mesh.triangleCount} tris ${mesh.vertexCount} verts, render ${state.surface.render.indices.size / 3} tris ${state.surface.render.vertexCount} verts")
        println("$name: surface per part $perPart")
        val parts = state.character.parts.filter { it.id != null }
        val toViewer: Vec3 = probeAzimuth(40f)
        val rect = PixelRect(0, 0, TILE, HEIGHT)
        val camera = framedCamera(parts, toViewer, TILE, HEIGHT, 0.05f)
        val build = { characterView(rect, camera.camera, state, PROBE_LOOK, ObjectShading(Shading.TWO_TONE), camera.pixelsPerUnit) { FACE_BINS.binFor(40f) } }
        val view = build()
        val body = view.draws.last()
        val others = view.draws.dropLast(1)
        fun MeshDraw.tris(): Int = this.mesh.indices.size / 3
        fun MeshDraw.layerTris(): Int = layers.sumOf { it.mesh.indices.size / 3 }
        val ribbons = view.ribbons.size
        println(
            "$name: pane fill body ${body.tris()} + other draws ${others.sumOf { it.tris() }} (${others.size} draws, largest ${others.map { it.tris() }.sortedDescending().take(6)})" +
                " + colour layers ${view.draws.sumOf { it.layerTris() }} (body ${body.layerTris()}) = ${view.draws.sumOf { it.tris() + it.layerTris() }}; $ribbons ribbons",
        )
        println("$name: body layers ${body.layers.map { "${it.mesh.indices.size / 3}${if (it.shape != null) "s" else ""}${if (it.alphas != null) "a" else ""}" }}")
        state.paint?.let { paint -> println("$name: paint layers ${paint.layers.map { "${it.slot?.value}:${it.cut.triangles.size / 3}/${it.candidates.size}" }}") }
        repeat(2) { build() }
        val viewStats = RunStats(List(VIEW_RUNS) { measureTime { build() }.inWholeMicroseconds / 1000.0 })
        println("$name: pane scene on the CPU $viewStats")
        if (outfit != outfits.first() || look.edits == null) continue
        val character = session.document.value.character
        for (id in EDITS.filter { it in look.edits }) {
            val instance = character.part(PartId(id)) ?: continue
            val rest = character.resolved(instance).params["size"] ?: session.builtTemplate(instance).defaults["size"] ?: continue
            val runs = ArrayList<Double>()
            repeat(EDIT_RUNS + 2) { i ->
                val took = measureTime {
                    session.send(SetParameter(instance.id, "size", rest * (1.02f + 0.0005f * i)))
                    work.settle(session)
                }
                session.send(SetParameter(instance.id, "size", rest))
                work.settle(session)
                if (i >= 2) runs += took.inWholeMicroseconds / 1000.0
            }
            println("$name: edit $id size ${RunStats(runs)}")
        }
    }
    open.close()
}
