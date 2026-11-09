package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Blend
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.blend.FIELD_SAMPLES
import dev.chieppa.squash.geom.blend.GradientBlend
import dev.chieppa.squash.geom.blend.ImplicitBlend
import dev.chieppa.squash.geom.blend.PatchSamples
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck

private const val CHILD = "child/"
private const val PARENT = "parent/"

/** [assembly] with fillets where a part whose template's `blend` block says `socket` comes out of the part it sits on. */
fun Graph.socketFilletNode(assembly: Node<*, AssembledCharacter>): Node<AssembledCharacter, AssembledCharacter> =
    follow(assembly, stage = BuildStages.ASSEMBLY) { it.withSocketFillets(cancellationCheck()) }

/**
 * This character with a fillet grown where each part asking for one comes out of its parent, in body space at rest: the vertices
 * of both near the crossing move, every grid and every (u, v) stays. A part on a paired parent's other side is left as it is.
 */
fun AssembledCharacter.withSocketFillets(checkCancelled: () -> Unit = {}): AssembledCharacter {
    val shown = parts.toMutableList()
    var changed = false
    for (i in shown.indices) {
        val child = shown[i]
        val blend = socketBlend(child) ?: continue
        val parentId = child.socket?.parent ?: continue
        val p = shown.indexOfFirst { it.id == parentId }
        if (p < 0) continue
        val parent = shown[p]
        if (parent.side != null && parent.side != child.side) continue
        checkCancelled()
        val radius = blend.socketRadius(child.template.defaults + child.placement, child.surface.axisLength)
        val joined = filleted(child, parent, radius, checkCancelled) ?: continue
        shown[i] = joined.first
        shown[p] = joined.second
        changed = true
    }
    return if (changed) AssembledCharacter(shown, sockets) else this
}

private fun socketBlend(part: PlacedPart): Blend? {
    if (part.id == null) return null
    val values = part.template.defaults + part.placement
    return part.template.blocks.filterIsInstance<Blend>().firstOrNull { blend ->
        blend.socket && blend.condition?.let { (values[it.name] ?: 0f) != 0f } != false
    }
}

/** [child] and [parent] with the fillet between them, [child] moving and [parent] still, or null when it moves nothing. */
internal fun filleted(child: PlacedPart, parent: PlacedPart, radius: Float, checkCancelled: () -> Unit): Pair<PlacedPart, PlacedPart>? {
    val own = child.surface.patches.map { it to CHILD } + parent.surface.patches.map { it to PARENT }
    val placed = own.mapIndexed { i, (patch, side) -> inBody(patch, side + i, if (side == CHILD) child else parent) }
    val blend = ImplicitBlend(GradientBlend(radius), joins = { a, b -> a.startsWith(CHILD) != b.startsWith(CHILD) }, moves = { it.startsWith(CHILD) })
    val sampled = { i: Int ->
        val (patch, side) = own[i]
        val part = if (side == CHILD) child else parent
        PatchSamples.of(patch, FIELD_SAMPLES).moved({ part.origin + part.surface.shown(it) }, part.surface::shown)
    }
    val moved = blend.apply(placed, sampled, checkCancelled)
    if (moved.indices.all { moved[it] === placed[it] }) return null
    val back = moved.mapIndexed { i, patch ->
        val (original, side) = own[i]
        if (patch === placed[i]) original else inPart(original, patch, if (side == CHILD) child else parent)
    }
    val count = child.surface.patches.size
    return child.withPatches(back.take(count)) to parent.withPatches(back.drop(count))
}

/** [patch] of [part] named [name], placed in body space. */
private fun inBody(patch: Patch, name: String, part: PlacedPart): Patch =
    Patch(name, patch.nu, patch.nv, patch.closedV, patch.slices, mapped(patch.points) { part.origin + part.surface.shown(it) }, patch.boneU,
        patch.proxyNormals?.let { normals -> mapped(normals, part.surface::shown) }, patch.spanned)

/** [original] with the points and shading normals of [moved], a copy of it in body space, back in [part]'s own space. */
private fun inPart(original: Patch, moved: Patch, part: PlacedPart): Patch =
    original.withPoints(mapped(moved.points) { part.surface.shown(it - part.origin) }, moved.proxyNormals?.let { mapped(it, part.surface::shown) })

private fun mapped(values: FloatArray, map: (Vec3) -> Vec3): FloatArray {
    val out = FloatArray(values.size)
    for (v in 0 until values.size / 3) {
        val p = map(Vec3(values[v * 3], values[v * 3 + 1], values[v * 3 + 2]))
        out[v * 3] = p.x
        out[v * 3 + 1] = p.y
        out[v * 3 + 2] = p.z
    }
    return out
}

/** This part showing [patches], its own moved, in place of its surface's. */
private fun PlacedPart.withPatches(patches: List<Patch>): PlacedPart {
    if (patches.indices.all { patches[it] === surface.patches[it] }) return this
    val mesh = PartMesh(id?.value ?: template.id.value, patches.map { patch -> MeshBuilder.build(patch) { 0 } })
    return showing(PartSurface.of(mesh, PartBuilder.axes(template), mirrored), turn)
}
