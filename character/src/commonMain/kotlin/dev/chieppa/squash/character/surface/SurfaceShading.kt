package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh
import kotlin.math.floor

private const val TINY = 1e-12f

/** How far a smooth union fills out from the nearer solid at most, as a share of its blend. */
private const val FILL_SHARE = 0.25f

/**
 * Each vertex [from] until [to] of [mesh]'s own shading: each owning run's own build's shading normal at the vertex's (u, v), weighed by
 * the run's share and by how near the vertex lies to the run's own surface, summed into [out], three values each, and the sum of the
 * weights into [held]: 1 where a run's own shading holds the vertex whole, so it shades exactly as that part does, falling to 0 across a
 * join, where the surface's own normal takes over. Two parts' normals are never mixed into a normal of neither: across a join each
 * gives way to the joined surface's.
 */
internal fun ownShading(field: CharacterField, mesh: SurfaceMesh, from: Int, to: Int, out: FloatArray, held: FloatArray) {
    val readers = HashMap<Int, RunShading>()
    fun reader(member: Int) = readers.getOrPut(member) { RunShading(field.runs[member].part.surface, field.runs[member].patchIndex) }
    for (v in from until to) {
        var shading = Vec3.ZERO
        var weights = 0f
        for (k in 0 until HELD) {
            val member = mesh.held[v * HELD + k]
            if (member < 0) continue
            val fill = maxOf(field.field.reaches[member] * FILL_SHARE, mesh.cell)
            val run = field.runs[member]
            val jaw = run.jawSide?.let { field.openJawOf(run.partId) }
            val skin = if (jaw == null) 1f else skinShare(jaw, mesh.position(v))
            if (run.jawSide == JawSide.LINING || skin <= 0f) continue
            val weight = mesh.heldShares[v * HELD + k] * nearness(mesh.heldOwn[v * HELD + k], fill) * skin
            val own = reader(member).shading(mesh.heldU[v * HELD + k], mesh.heldV[v * HELD + k])
            shading += (if (run.jawSide == JawSide.JAW && jaw != null) jaw.cut.opened * own else own) * weight
            weights += weight
        }
        held[v] = weights
        out[v * 3] = shading.x
        out[v * 3 + 1] = shading.y
        out[v * 3 + 2] = shading.z
    }
}

/**
 * How much of a run's own shading a point [p] on a head built with its jaw open, [jaw], takes: all of it on the skin, none on the cut's
 * faces and the lining, which shade by the surface's own normal, easing between across the lips' edge.
 */
private fun skinShare(jaw: OpenJaw, p: Vec3): Float {
    val x = (-jaw.inside(p) / jaw.edge).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** 1 on a run's own surface, falling smoothly to 0 where its own field reaches [fill] outside it. */
private fun nearness(own: Float, fill: Float): Float {
    if (fill <= TINY) return if (own <= 0f) 1f else 0f
    val x = (own / fill).coerceIn(0f, 1f)
    return 1f - x * x * (3f - 2f * x)
}

/** Reads a part's own shading normal on its run [patchIndex] at any (u, v), eased between the four grid points round it. */
private class RunShading(private val surface: PartSurface, patchIndex: Int) {
    private val patch = surface.patches[patchIndex]
    private val first = surface.patches.take(patchIndex).sumOf { it.nu * it.nv }
    private val us = patch.ringUs

    /** The part's shading normal at (u, v). */
    fun shading(u: Float, v: Float): Vec3 = at(u, v, RenderMesh.PROXY_OFFSET_FLOATS)

    private fun at(u: Float, v: Float, offset: Int): Vec3 {
        val nu = patch.nu
        var ring = 0
        while (ring < nu - 2 && us[ring + 1] < u) ring++
        val span = us[ring + 1] - us[ring]
        val t = (if (span > TINY) (u - us[ring]) / span else 0f).coerceIn(0f, 1f)
        val around = v * patch.segments
        val column = floor(around).toInt()
        val s = around - column
        val c0 = column.mod(patch.nv)
        val c1 = if (patch.closedV) (column + 1).mod(patch.nv) else (column + 1).coerceAtMost(patch.nv - 1)
        val a = read(ring, c0, offset) * (1f - s) + read(ring, c1, offset) * s
        val b = read(ring + 1, c0, offset) * (1f - s) + read(ring + 1, c1, offset) * s
        return a * (1f - t) + b * t
    }

    private fun read(ring: Int, column: Int, offset: Int): Vec3 {
        val at = (first + patch.index(ring, column)) * RenderMesh.FLOATS_PER_VERTEX + offset
        val values = surface.mesh.vertices
        return Vec3(values[at], values[at + 1], values[at + 2])
    }
}
