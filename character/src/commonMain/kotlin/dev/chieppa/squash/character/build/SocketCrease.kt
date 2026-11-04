package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt
import dev.chieppa.squash.geom.zeroCurves
import kotlin.math.floor
import kotlin.math.round

/** Where a part comes out of the part its socket is on: [runs] of points on it in body space. See HLD: look, Where parts meet. */
class SocketCrease(val part: PartId, val runs: List<List<ResolvedPoint>>)

/** Passes that calm a crease's steps from one grid edge to the next. Each holds its point on the surface. */
private const val CREASE_SMOOTH_PASSES = 3

/** How many of a grid's nearest points set how far a point lies outside it, so the answer does not jump as the nearest changes. */
private const val NEAREST_POINTS = 4

private const val DISTANCE_FLOOR = 1e-4f

/** Every placed part's crease with its parent, leaving out the parts that do not meet theirs. [checkCancelled] runs before each part. */
fun socketCreases(character: AssembledCharacter, checkCancelled: () -> Unit = {}): List<SocketCrease> = character.parts.mapNotNull { placed ->
    val id = placed.id ?: return@mapNotNull null
    val parent = placed.socket?.parent?.let(character::part) ?: return@mapNotNull null
    checkCancelled()
    val within = parent.sweptPatches().map { PlacedGrid(parent, it) }
    fun inBody(local: Vec3) = placed.origin + placed.surface.shown(local)
    val runs = placed.sweptPatches().flatMap { patch ->
        patch.zeroCurves { local -> within.minOf { it.outsideBy(inBody(local)) } }.map { run ->
            calmedPlaces(run, patch.closedV).map { place ->
                val normal = placed.surface.shown(patch.surfaceAt(place.u, place.v).normal)
                ResolvedPoint(Anchor(id, patch.name, place.u, place.v), inBody(patch.positionAt(place.u, place.v)), normal)
            }
        }
    }
    if (runs.isEmpty()) null else SocketCrease(id, runs)
}

/** [run] with each place moved toward the middle of its neighbours across any wrap of v. A closed run stays closed; an open one keeps its ends. */
private fun calmedPlaces(run: List<SurfaceUv>, closedV: Boolean): List<SurfaceUv> {
    if (run.size < 3) return run
    val closed = run.first() == run.last()
    fun near(v: Float, to: Float): Float = if (closedV) v - round(v - to) else v
    var places = run
    repeat(CREASE_SMOOTH_PASSES) {
        val last = places.lastIndex
        val held = places
        places = List(held.size) { i ->
            val before = if (i > 0) held[i - 1] else if (closed) held[last - 1] else null
            val after = if (i < last) held[i + 1] else if (closed) held[1] else null
            if (before == null || after == null) {
                held[i]
            } else {
                val v = (near(before.v, held[i].v) + near(after.v, held[i].v)) * 0.25f + held[i].v * 0.5f
                SurfaceUv((before.u + after.u) * 0.25f + held[i].u * 0.5f, if (closedV) v - floor(v) else v)
            }
        }
    }
    return places
}

private fun PlacedPart.sweptPatches() = surface.patches.filter { it.slices != null }

/** A patch's grid in body space, which tells how far a point lies outside it along the normals of the nearest of its points. */
private class PlacedGrid(part: PlacedPart, patch: Patch) {
    private val points = Array(patch.nu * patch.nv) { part.origin + part.surface.shown(patch.point(it)) }
    private val normals = Array(points.size) { i ->
        part.surface.shown(patch.surfaceAt(patch.ringUs[i / patch.nv], (i % patch.nv).toFloat() / patch.segments).normal)
    }

    fun outsideBy(point: Vec3): Float {
        val nearest = IntArray(NEAREST_POINTS) { -1 }
        val distances = FloatArray(NEAREST_POINTS) { Float.MAX_VALUE }
        for (i in points.indices) {
            val distance = (points[i] - point).length
            var at = NEAREST_POINTS - 1
            if (distance >= distances[at]) continue
            while (at > 0 && distance < distances[at - 1]) {
                distances[at] = distances[at - 1]
                nearest[at] = nearest[at - 1]
                at--
            }
            distances[at] = distance
            nearest[at] = i
        }
        var total = 0f
        var side = 0f
        for (k in 0 until NEAREST_POINTS) {
            val i = nearest[k]
            if (i < 0) continue
            val share = 1f / (distances[k] + DISTANCE_FLOOR)
            total += share
            side += share * ((point - points[i]) dot normals[i])
        }
        return side / total
    }
}
