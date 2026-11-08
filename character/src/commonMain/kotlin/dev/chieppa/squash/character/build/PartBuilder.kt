package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Block
import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.PatchSlot
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.PatchMesh
import dev.chieppa.squash.geom.blendedOverlaps

/** Builds a part's patches and mesh from its template, parameter values and drawn outlines. */
object PartBuilder {
    /**
     * The patches of [template] with [params] over its defaults, sized by [outlines], with its length along its axis
     * multiplied by [lengthFactor]. [checkCancelled] runs before each block and throws to stop a stale run.
     */
    fun build(
        template: PartTemplate,
        params: Map<String, Float> = emptyMap(),
        outlines: Map<View, OutlineCurve> = emptyMap(),
        checkCancelled: () -> Unit = {},
        lengthFactor: Float = 1f,
    ): List<Patch> {
        val context = context(template, params, outlines, checkCancelled, lengthFactor)
        context.blocksOn.forEach {
            checkCancelled()
            context.add(it.build(context))
        }
        return shadedAsOne(context)
    }

    /** What [context] built, with the runs of its blocks shading as one surface where they overlap. */
    private fun shadedAsOne(context: BuildContext): List<Patch> {
        val runNames = context.blocksOn.filter { it.run() != null }.flatMap { it.patchNames() }.map { it.name }.toSet()
        val runs = context.built.filter { it.name in runNames }.sortedBy { it.ringUs.first() }
        val blended = blendedOverlaps(runs).associateBy { it.name }
        return context.built.map { blended[it.name] ?: it }
    }

    /** What the blocks of [template] build from, with nothing built yet. */
    fun context(
        template: PartTemplate,
        params: Map<String, Float> = emptyMap(),
        outlines: Map<View, OutlineCurve> = emptyMap(),
        checkCancelled: () -> Unit = {},
        lengthFactor: Float = 1f,
    ): BuildContext {
        val values = template.defaults + params
        val shaped = template.shapedBy(values)
        return BuildContext(shaped, values, shaped.blocks.filter { it.isOn(values) }, outlines, checkCancelled, lengthFactor)
    }

    /** The patches of a part that resolves to [data]. */
    fun build(template: PartTemplate, data: PartData, checkCancelled: () -> Unit = {}, lengthFactor: Float = 1f): List<Patch> =
        build(template, data.params, data.outlines, checkCancelled, lengthFactor)

    /** The areas of [template]'s blocks that are on with [params] over its defaults. */
    fun areas(template: PartTemplate, params: Map<String, Float>): List<AreaSpec> =
        (template.defaults + params).let { values -> template.blocks.filter { it.isOn(values) }.flatMap { it.areas() } }

    /** The colored patches of [template]'s blocks that are on with [params] over its defaults. */
    fun patchSlots(template: PartTemplate, params: Map<String, Float>): List<PatchSlot> =
        (template.defaults + params).let { values -> template.blocks.filter { it.isOn(values) }.flatMap { it.patchSlots() } }

    /** The axes an outline of [template] is drawn in and its patches are built in. */
    fun axes(template: PartTemplate): PartAxes = PartAxes.of(template.axis.rest.toVec3(), template.axis.up?.toVec3())

    /**
     * The mesh of [patches], each vertex tagged with its joint's place in [JointTree.specs], a spanned patch's vertex with the one at its u
     * on the patch it rides the more. [checkCancelled] runs before each patch and throws to stop a stale run.
     */
    fun mesh(part: String, template: PartTemplate, patches: List<Patch>, checkCancelled: () -> Unit = {}): PartMesh {
        val tree = JointTree(template, patches.map { it.name })
        return PartMesh(
            part,
            patches.map { patch ->
                checkCancelled()
                val mesh = MeshBuilder.build(patch) { u -> tree.boneAt(patch.name, u) }
                val spanned = patch.spanned ?: return@map mesh
                PatchMesh(patch, mesh.indices, IntArray(mesh.vertexCount) { tree.boneAt(spanned.nearer(it), spanned.nearerU(it)) }, mesh.us, mesh.vs)
            },
            axes(template),
        )
    }

    /** The rings of [patch] that lie on the template's open end. */
    fun openRings(template: PartTemplate, patch: Patch): Set<Int> {
        val slices = patch.slices ?: return emptySet()
        return when (template.openEnd) {
            End.START -> if (slices.first().u <= 0f) setOf(0) else emptySet()
            End.END -> if (slices.last().u >= 1f) setOf(patch.nu - 1) else emptySet()
            null -> emptySet()
        }
    }

    private fun Block.isOn(values: Map<String, Float>): Boolean {
        val flag = condition ?: return true
        return requireNotNull(values[flag.name]) { "No parameter ${flag.name}" } != 0f
    }
}
