package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Block
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.Patch

/** Builds a part's patches and mesh from its template and parameter values. */
object PartBuilder {
    /** The patches of [template] with [params] over its defaults. */
    fun build(template: PartTemplate, params: Map<String, Float> = emptyMap()): List<Patch> {
        val values = template.defaults + params
        val active = template.blocks.filter { it.isOn(values) }
        val context = BuildContext(template, values, active)
        return active.flatMap { it.build(context) }
    }

    fun mesh(part: String, template: PartTemplate, patches: List<Patch>): PartMesh {
        val joints = template.blocks.flatMap { it.joints() }.map { it.u }.sorted()
        val lastBone = (joints.size - 2).coerceAtLeast(0)
        val boneAt = { u: Float -> (joints.count { it <= u } - 1).coerceIn(0, lastBone) }
        return PartMesh(part, patches.map { MeshBuilder.build(it, boneAt) })
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
