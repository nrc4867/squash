package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyColor
import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.character.build.PartColor
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.PatchCut
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node

/**
 * The whole of [patch] on [surface] as a colour cut: its own triangles over the vertices it holds in the surface's mesh, each with its
 * place on the patch and, where given, its opacity in [alphas], one a vertex of the patch's grid. Null when the surface holds no such patch.
 */
fun wholePatchCut(surface: PartSurface, patch: String, alphas: FloatArray? = null): PatchCut? {
    var start = 0
    for (held in surface.patches) {
        val count = held.nu * held.nv
        if (held.name != patch) {
            start += count
            continue
        }
        val built = MeshBuilder.build(held) { 0 }
        val floats = RenderMesh.FLOATS_PER_VERTEX
        val vertices = surface.mesh.vertices.copyOfRange(start * floats, (start + count) * floats)
        return PatchCut(patch, RenderMesh(vertices, built.indices), built.us, built.vs, alphas?.copyOf())
    }
    return null
}

/** [colors] with the layers each lock part of [character] carries ([LockPlacing.layers]) over its own: a braid's ties, a shell's skin. */
fun Graph.lockLayersNode(colors: Node<*, BodyColor>, character: Node<*, AssembledCharacter>): Node<*, BodyColor> {
    val colorSource = follow<BodyColor, LayersInput>(colors, cacheSize = 1, stage = BuildStages.SURFACE_WORK) { LayersInput.Colors(it) }
    val bodySource = follow<AssembledCharacter, LayersInput>(character, cacheSize = 1, stage = BuildStages.SURFACE_WORK) { LayersInput.Body(it) }
    return gather(
        kotlinx.coroutines.flow.flowOf(Unit),
        sourcesOf = { mapOf(COLORS to colorSource, BODY to bodySource) },
        cacheSize = 1,
        stage = BuildStages.SURFACE_WORK,
    ) { (_, built) ->
        val body = (built.getValue(COLORS) as LayersInput.Colors).colors
        val placed = (built.getValue(BODY) as LayersInput.Body).body
        val laid = placed.parts.mapNotNull { part -> part.id?.let { id -> part.locks?.layers?.takeIf { it.isNotEmpty() }?.let { id to it } } }
        if (laid.isEmpty()) return@gather body
        BodyColor(
            body.parts + laid.associate { (id, layers) ->
                val own = body.parts[id]
                id to PartColor(own?.baseSlot, own?.layers.orEmpty() + layers, own?.copies)
            },
        )
    }
}

/** What the layers node reads: the body's colour, or the placed character. */
private sealed interface LayersInput {
    class Colors(val colors: BodyColor) : LayersInput

    class Body(val body: AssembledCharacter) : LayersInput
}

private const val COLORS = "colors"
private const val BODY = "body"
