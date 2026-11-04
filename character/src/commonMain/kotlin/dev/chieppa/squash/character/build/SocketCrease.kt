package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt
import dev.chieppa.squash.geom.zeroCurves

/** Where a part comes out of the part its socket is on: [runs] of points on it in body space. See HLD: look, Where parts meet. */
class SocketCrease(val part: PartId, val runs: List<List<ResolvedPoint>>)

/** Every placed part's crease with its parent, leaving out the parts that do not meet theirs. */
fun socketCreases(character: AssembledCharacter): List<SocketCrease> = character.parts.mapNotNull { placed ->
    val id = placed.id ?: return@mapNotNull null
    val parent = placed.socket?.parent?.let(character::part) ?: return@mapNotNull null
    val within = parent.sweptPatches().map { PlacedGrid(parent, it) }
    fun inBody(local: Vec3) = placed.origin + placed.surface.shown(local)
    val runs = placed.sweptPatches().flatMap { patch ->
        patch.zeroCurves { local -> within.minOf { it.outsideBy(inBody(local)) } }.map { run ->
            run.map { place ->
                val normal = placed.surface.shown(patch.surfaceAt(place.u, place.v).normal)
                ResolvedPoint(Anchor(id, patch.name, place.u, place.v), inBody(patch.positionAt(place.u, place.v)), normal)
            }
        }
    }
    if (runs.isEmpty()) null else SocketCrease(id, runs)
}

private fun PlacedPart.sweptPatches() = surface.patches.filter { it.slices != null }

/** A patch's grid in body space, which tells how far a point lies outside it along the normal of the nearest of its points. */
private class PlacedGrid(private val part: PlacedPart, private val patch: Patch) {
    private val points = Array(patch.nu * patch.nv) { part.origin + part.surface.shown(patch.point(it)) }

    fun outsideBy(point: Vec3): Float {
        var nearest = 0
        var best = Float.MAX_VALUE
        for (i in points.indices) {
            val distance = (points[i] - point).length
            if (distance < best) {
                best = distance
                nearest = i
            }
        }
        val u = patch.ringUs[nearest / patch.nv]
        val v = (nearest % patch.nv).toFloat() / patch.segments
        val normal = part.surface.shown(patch.surfaceAt(u, v).normal)
        return (point - points[nearest]) dot normal
    }
}
