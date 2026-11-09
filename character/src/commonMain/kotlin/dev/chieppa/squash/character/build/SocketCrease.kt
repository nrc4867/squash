package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.NearestPoints
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
fun socketCreases(character: AssembledCharacter, checkCancelled: () -> Unit = {}): List<SocketCrease> {
    val parents = CreaseParents()
    return character.parts.mapNotNull { placed ->
        checkCancelled()
        socketCrease(character, placed, parents)
    }
}

/** The parents creases are found against, each read once however many parts sit on it. */
class CreaseParents {
    private val held = HashMap<PlacedPart, List<PlacedGrid>>()

    internal fun of(parent: PlacedPart): List<PlacedGrid> = held.getOrPut(parent) { parent.sweptPatches().map { PlacedGrid(parent, it) } }
}

/**
 * Where [placed] comes out of its parent in [character], or null for a part that does not meet one. For a garment's branch or cuff,
 * the edges it inks instead: see [garmentEdges].
 */
fun socketCrease(character: AssembledCharacter, placed: PlacedPart, parents: CreaseParents = CreaseParents()): SocketCrease? {
    val id = placed.id ?: return null
    if (placed.covering != null) return garmentEdges(character, placed, parents)
    val parent = placed.socket?.parent?.let(character::part) ?: return null
    val runs = crossingRuns(placed, id, parents.of(parent))
    return if (runs.isEmpty()) null else SocketCrease(id, runs)
}

/** The runs on [placed], part [id], where its surface crosses out of the surface [within] holds. */
internal fun crossingRuns(placed: PlacedPart, id: PartId, within: List<PlacedGrid>): List<List<ResolvedPoint>> {
    if (within.isEmpty()) return emptyList()
    fun inBody(local: Vec3) = placed.origin + placed.surface.shown(local)
    return placed.sweptPatches().flatMap { patch ->
        patch.zeroCurves { local -> within.minOf { it.outsideBy(inBody(local)) } }.map { run ->
            calmedPlaces(run, patch.closedV).map { place ->
                val normal = placed.surface.shown(patch.surfaceAt(place.u, place.v).normal)
                ResolvedPoint(Anchor(id, patch.name, place.u, place.v), inBody(patch.positionAt(place.u, place.v)), normal)
            }
        }
    }
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
internal class PlacedGrid(part: PlacedPart, patch: Patch) {
    private val points = Array(patch.nu * patch.nv) { part.origin + part.surface.shown(patch.point(it)) }
    private val normals = Array(points.size) { i ->
        part.surface.shown(patch.surfaceAt(patch.ringUs[i / patch.nv], (i % patch.nv).toFloat() / patch.segments).normal)
    }

    private val tree = NearestPoints(points)
    private val nearest = IntArray(NEAREST_POINTS)
    private val distances = FloatArray(NEAREST_POINTS)

    fun outsideBy(point: Vec3): Float {
        tree.nearest(point, nearest, distances)
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
