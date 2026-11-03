package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Block
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.Patch

/** Builds a part's patches and mesh from its template, parameter values and drawn outlines. */
object PartBuilder {
    /**
     * The patches of [template] with [params] over its defaults, sized by [outlines]. [checkCancelled] runs before each
     * block and throws to stop a stale run.
     */
    fun build(
        template: PartTemplate,
        params: Map<String, Float> = emptyMap(),
        outlines: Map<View, OutlineCurve> = emptyMap(),
        checkCancelled: () -> Unit = {},
    ): List<Patch> {
        val values = template.defaults + params
        val active = template.blocks.filter { it.isOn(values) }
        val context = BuildContext(template, values, active, outlines, checkCancelled)
        active.forEach {
            checkCancelled()
            context.add(it.build(context))
        }
        return context.built
    }

    /** The patches of a part that resolves to [data]. */
    fun build(template: PartTemplate, data: PartData, checkCancelled: () -> Unit = {}): List<Patch> =
        build(template, data.params, data.outlines, checkCancelled)

    /** The axes an outline of [template] is drawn in and its patches are built in. */
    fun axes(template: PartTemplate): PartAxes = PartAxes.of(template.axis.rest.toVec3())

    /** The mesh of [patches]. [checkCancelled] runs before each patch and throws to stop a stale run. */
    fun mesh(part: String, template: PartTemplate, patches: List<Patch>, checkCancelled: () -> Unit = {}): PartMesh {
        val joints = template.blocks.flatMap { it.joints() }.map { it.u }.sorted()
        val lastBone = (joints.size - 2).coerceAtLeast(0)
        val boneAt = { u: Float -> (joints.count { it <= u } - 1).coerceIn(0, lastBone) }
        return PartMesh(
            part,
            patches.map {
                checkCancelled()
                MeshBuilder.build(it, boneAt)
            },
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
