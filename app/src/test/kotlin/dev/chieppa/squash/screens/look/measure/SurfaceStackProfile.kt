package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.HeldShapes
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.DressStage
import dev.chieppa.squash.character.surface.GarmentBuilder
import dev.chieppa.squash.character.surface.GarmentCover
import dev.chieppa.squash.character.surface.HiddenSkin
import dev.chieppa.squash.character.surface.PaintMemo
import dev.chieppa.squash.character.surface.SurfaceBuilder
import dev.chieppa.squash.character.surface.SurfaceDress
import dev.chieppa.squash.character.surface.SurfaceEyes
import dev.chieppa.squash.character.surface.SurfacePaint
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.rig.GarmentSkins
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.SurfaceSkin
import jdk.jfr.Configuration
import jdk.jfr.Recording
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.time.TimeSource

/** Runs profiled once warm, the median of each stage kept. */
private const val PROFILED_RUNS = 11

/** Edits run before the stages are timed. */
private const val WARM_EDITS = 4

/** Edits run under a flight recording, after the timed ones. */
private const val RECORDED_EDITS = 40

/** What the stack reads besides the surface: each part's shapes, the painted eyes, the garments worn and the rig. */
class StackInputs(val shapes: Map<PartId, HeldShapes>, val eyes: List<PaintedEye>, val covers: List<GarmentCover>, val rig: Rig)

/**
 * Where an edit's stack time goes, stage by stage: the surface, its paint, its eyes, its weights, its dress and the dress's weights, each
 * run alone on all of [runAll]'s threads, the median of [PROFILED_RUNS] edits from [rest] to [edited] and back once warm; with [record],
 * [RECORDED_EDITS] more edits under a flight recording with the JDK's profile settings, kept in that folder by the run's label.
 */
class SurfaceStackProfile(private val runAll: (List<() -> Unit>) -> Unit, private val inputs: StackInputs, private val record: File? = null) {
    private var lastBatches = ""
    private var lastKept: Int? = null
    private var lastCount = 0
    private val parallel: suspend (List<() -> Unit>) -> Unit = { tasks -> runAll(tasks) }

    fun run(label: String, rest: AssembledCharacter, edited: AssembledCharacter, dragging: Boolean) {
        val builder = SurfaceBuilder()
        val garments = GarmentBuilder()
        val skins = GarmentSkins()
        val memo = PaintMemo()
        val stages = LinkedHashMap<String, MutableList<Double>>()
        fun <T> timed(stage: String, block: () -> T): T {
            val t = TimeSource.Monotonic.markNow()
            return block().also { stages.getOrPut(stage) { ArrayList() } += t.elapsedNow().inWholeMicroseconds / 1000.0 }
        }
        var flip = false
        val recording = record?.let { Recording(Configuration.getConfiguration("profile")) }
        repeat(WARM_EDITS + PROFILED_RUNS + (if (recording != null) RECORDED_EDITS else 0)) { i ->
            if (i == WARM_EDITS) stages.clear()
            if (i == WARM_EDITS + PROFILED_RUNS) recording?.start()
            flip = !flip
            val all = TimeSource.Monotonic.markNow()
            val surface = timed("surface") { checkNotNull(builder.build(if (flip) edited else rest, dragging, runAll)) }
            lastKept = surface.mesh.change?.kept
            lastCount = surface.mesh.vertexCount
            timed("paint") { runBlocking { SurfacePaint.of(surface, inputs.shapes, parallel, memo) } }
            timed("eyes") { runBlocking { SurfaceEyes.of(surface, inputs.eyes, parallel) } }
            timed("weights") { runBlocking { SurfaceSkin.of(inputs.rig, surface, parallel) } }
            val dress = timed("dress") { dress(garments, surface, if (flip) edited else rest) { stage, ms -> stages.getOrPut("dress ${stage.name.lowercase()}") { ArrayList() }.addPart(ms) } }
            timed("dress weights") { runBlocking { dress.garments.forEach { skins.of(inputs.rig, it, parallel) } } }
            stages.getOrPut("all, one after another") { ArrayList() } += all.elapsedNow().inWholeMicroseconds / 1000.0
        }
        recording?.let {
            it.stop()
            checkNotNull(record).mkdirs()
            it.dump(File(record, label.replace(' ', '-') + ".jfr").toPath())
            it.close()
        }
        println("$label: last dress batches $lastBatches")
        println("$label: vertices kept from the last mesh ${lastKept ?: "none"} of $lastCount")
        println("$label: stack profile " + stages.entries.joinToString { (stage, runs) -> "$stage %.1f ms".format(runs.sorted()[runs.size / 2]) })
    }

    private fun dress(garments: GarmentBuilder, surface: CharacterSurface, placed: AssembledCharacter, took: (DressStage, Double) -> Unit): SurfaceDress {
        val build = garments.start(surface.field, surface.mesh, placed, inputs.covers)
        val each = DressStage.entries.associateWith { 0.0 }.toMutableMap()
        val batches = ArrayList<String>()
        while (true) {
            val t = TimeSource.Monotonic.markNow()
            val tasks = build.next()
            each[build.stage] = each.getValue(build.stage) + t.elapsedNow().inWholeMicroseconds / 1000.0
            tasks ?: break
            val r = TimeSource.Monotonic.markNow()
            runAll(tasks)
            val ms = r.elapsedNow().inWholeMicroseconds / 1000.0
            each[build.stage] = each.getValue(build.stage) + ms
            batches += "${build.stage.name.lowercase()} ${tasks.size}: %.1f".format(ms)
        }
        val t = TimeSource.Monotonic.markNow()
        val hiding = HiddenSkin(surface, build.garments)
        while (true) runAll(hiding.next() ?: break)
        each[DressStage.HIDE] = t.elapsedNow().inWholeMicroseconds / 1000.0
        each.forEach { (stage, ms) -> took(stage, ms) }
        lastBatches = batches.joinToString()
        return SurfaceDress(hiding.worn, hiding.skin)
    }

    /** Adds [ms] as this run's time, the list holding one entry a run. */
    private fun MutableList<Double>.addPart(ms: Double) {
        add(ms)
    }
}
