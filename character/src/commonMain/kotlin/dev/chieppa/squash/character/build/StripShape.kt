package dev.chieppa.squash.character.build

import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.strip.BridgeStrip
import dev.chieppa.squash.geom.strip.StripEnd
import dev.chieppa.squash.geom.surfaceAt

/** How far a strip's foot stands off the surface it lies on, in body units, so it never dips under it. */
internal const val STRIP_LIFT = 0.002f

/** How far each end's way carries a strip's columns, as a share of the distance between their ends: a quarter round's bulge. */
private const val WAY_REACH = 0.6f

/** Below this a way has no length to turn into a direction. */
private const val MIN_WAY = 1e-6f

/** A strip as built: its [patch] in the tube part's own space, and its vertices as that part's mesh holds them. */
internal class StripShape(val patch: Patch, val mesh: RenderMesh)

/** Where a part's patch named [name] starts among its mesh's vertices, with the patch, or null when it has none so named. */
fun PlacedPart.patchWithFirst(name: String): Pair<Patch, Int>? {
    var first = 0
    for (patch in surface.patches) {
        if (patch.name == name) return patch to first
        first += patch.nu * patch.nv
    }
    return null
}

/** The vertex of [patch], starting at [first] in its part's mesh, nearest (u, v). */
fun nearestVertex(patch: Patch, first: Int, u: Float, v: Float): Int {
    val rings = patch.ringUs
    val ring = rings.indices.minBy { kotlin.math.abs(rings[it] - u) }
    val column = kotlin.math.round(v * patch.segments).toInt().mod(patch.nv)
    return first + patch.index(ring, column)
}

/**
 * The strip [join] holds on [tube], from its foot on [host] to [tube]'s ring, where both parts now stand: each column leaves the foot
 * along [host]'s surface toward the ring, and meets the ring along [tube]'s own run, then the strip is faired. Its shading normals
 * run from [host]'s at the foot through its own to [tube]'s at the ring. Null when either part has lost the patch it names.
 */
internal fun stripShape(tube: PlacedPart, join: StripJoin, host: PlacedPart): StripShape? {
    val (own, ownFirst) = tube.patchWithFirst(join.tube) ?: return null
    val (onHost, hostFirst) = host.patchWithFirst(join.hostPatch) ?: return null
    val columns = own.nv
    if (join.foot.size != columns) return null
    val away = if (join.atStart) 1 else -1
    val ringPoint = { ring: Int, j: Int -> tube.origin + tube.surface.shown(own.point(own.index(ring.coerceIn(0, own.nu - 1), j))) }
    val ring = List(columns) { ringPoint(join.ring, it) }
    val ringWays = List(columns) { j -> direction(ringPoint(join.ring + away, j) - ringPoint(join.ring - away, j)) }
    val normals = join.foot.map { host.surface.shown(onHost.surfaceAt(it.u, it.v).normal) }
    val feet = join.foot.mapIndexed { j, at -> host.origin + host.surface.shown(onHost.positionAt(at.u, at.v)) + normals[j] * (STRIP_LIFT + join.lift) }
    val footWays = List(columns) { j ->
        val toward = ring[j] - feet[j]
        direction(toward - normals[j] * (toward dot normals[j]))
    }
    val points = BridgeStrip.build(StripEnd(feet, footWays), StripEnd(ring, ringWays), StripJoin.RINGS, WAY_REACH)
    val rings = StripJoin.RINGS
    val local = FloatArray(rings * columns * Patch.COMPONENTS)
    for (k in 0 until rings) for (j in 0 until columns) {
        val row = if (join.atStart) k else rings - 1 - k
        val p = tube.surface.shown(points[k * columns + j] - tube.origin)
        val at = (row * columns + j) * Patch.COMPONENTS
        local[at] = p.x
        local[at + 1] = p.y
        local[at + 2] = p.z
    }
    val us = FloatArray(rings) { row -> if (join.atStart) row.toFloat() / (rings - 1) - 1f else 1f + row.toFloat() / (rings - 1) }
    val patch = Patch(join.patch, rings, columns, own.closedV, slices = null, points = local, us = us, openEnds = true)
    val built = RenderMesh.of(PartMesh("", listOf(MeshBuilder.build(patch) { 0 })))
    val mesh = if (tube.surface.mirrored) built.mirrored() else built
    for (k in 0 until rings) for (j in 0 until columns) {
        val row = if (join.atStart) k else rings - 1 - k
        val s = k.toFloat() / (rings - 1)
        val hostShade = proxyOf(host.surface.mesh, nearestVertex(onHost, hostFirst, join.foot[j].u, join.foot[j].v))
        val tubeShade = proxyOf(tube.surface.mesh, ownFirst + own.index(join.ring, j))
        val at = (row * columns + j) * RenderMesh.FLOATS_PER_VERTEX
        val own3 = Vec3(mesh.vertices[at + RenderMesh.NORMAL_OFFSET_FLOATS], mesh.vertices[at + RenderMesh.NORMAL_OFFSET_FLOATS + 1], mesh.vertices[at + RenderMesh.NORMAL_OFFSET_FLOATS + 2])
        val shade = direction(hostShade * ((1f - s) * (1f - s)) + own3 * (2f * s * (1f - s)) + tubeShade * (s * s))
        mesh.vertices[at + RenderMesh.PROXY_OFFSET_FLOATS] = shade.x
        mesh.vertices[at + RenderMesh.PROXY_OFFSET_FLOATS + 1] = shade.y
        mesh.vertices[at + RenderMesh.PROXY_OFFSET_FLOATS + 2] = shade.z
    }
    return StripShape(patch, mesh)
}

private fun proxyOf(mesh: RenderMesh, vertex: Int): Vec3 {
    val at = vertex * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.PROXY_OFFSET_FLOATS
    return Vec3(mesh.vertices[at], mesh.vertices[at + 1], mesh.vertices[at + 2])
}

private fun direction(way: Vec3): Vec3 = if (way.length < MIN_WAY) Vec3.ZERO else way.normalized()
