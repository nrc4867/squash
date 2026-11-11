package dev.chieppa.squash.geom

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How far round each vertex its shading normal is averaged, as a share of the part's thinnest extent. */
private const val REACH_SHARE = 0.8f

/** The Gaussian's spread, as a share of the reach. */
private const val SPREAD_SHARE = 0.5f

/** Below this a length counts as none. */
private const val TINY = 1e-9f

/**
 * This mesh with its shading normals averaged over a ball round each vertex as wide as 0.8 of its thinnest extent, so two-tone's
 * terminator follows the part's large form and not the bumps a drawn outline puts in it: each neighbour weighs by a Gaussian of its
 * distance and the area round it, and one facing away from the vertex's own normal is left out, so the facing sides of two digits or a
 * thin sheet never mix.
 */
fun RenderMesh.withSmoothedShading(): RenderMesh {
    val count = vertexCount
    if (count == 0) return this
    val positions = Array(count) { position(it) }
    var low = positions[0]
    var high = positions[0]
    for (p in positions) {
        low = Vec3(min(low.x, p.x), min(low.y, p.y), min(low.z, p.z))
        high = Vec3(max(high.x, p.x), max(high.y, p.y), max(high.z, p.z))
    }
    val size = high - low
    val reach = min(size.x, min(size.y, size.z)) * REACH_SHARE
    if (reach < TINY) return this
    val area = vertexAreas(positions)
    val proxies = Array(count) { proxyAt(it) }
    val grid = SpatialHash(positions, reach)
    val spread = reach * SPREAD_SHARE
    val falloff = 1f / (2f * spread * spread)
    val out = vertices.copyOf()
    for (v in 0 until count) {
        val own = proxies[v]
        var sum = Vec3.ZERO
        grid.near(positions[v]) { other ->
            val d = positions[other] - positions[v]
            val squared = d dot d
            if (squared <= reach * reach) {
                if ((own dot proxies[other]) > 0f) sum += proxies[other] * (exp(-squared * falloff) * area[other])
            }
        }
        val smoothed = if (sum.length < TINY) own else sum.normalized()
        val at = v * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.PROXY_OFFSET_FLOATS
        out[at] = smoothed.x
        out[at + 1] = smoothed.y
        out[at + 2] = smoothed.z
    }
    return RenderMesh(out, indices)
}

private fun RenderMesh.proxyAt(vertex: Int): Vec3 {
    val at = vertex * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.PROXY_OFFSET_FLOATS
    return Vec3(vertices[at], vertices[at + 1], vertices[at + 2])
}

/** A third of the area of every face round each vertex, so dense rings near a pole do not outweigh the rest. */
private fun RenderMesh.vertexAreas(positions: Array<Vec3>): FloatArray {
    val area = FloatArray(positions.size)
    for (t in 0 until indices.size / 3) {
        val a = indices[t * 3]
        val b = indices[t * 3 + 1]
        val c = indices[t * 3 + 2]
        val third = ((positions[b] - positions[a]) cross (positions[c] - positions[a])).length / 6f
        area[a] += third
        area[b] += third
        area[c] += third
    }
    return area
}

/** Points bucketed in cubes of [cell], for the points near one. */
private class SpatialHash(private val points: Array<Vec3>, private val cell: Float) {
    private val buckets = HashMap<Long, MutableList<Int>>()

    init {
        points.forEachIndexed { i, p -> buckets.getOrPut(key(cellOf(p.x), cellOf(p.y), cellOf(p.z))) { ArrayList() } += i }
    }

    private fun cellOf(value: Float) = floor(value / cell).toInt()

    private fun key(x: Int, y: Int, z: Int): Long = ((x.toLong() and MASK) shl 42) or ((y.toLong() and MASK) shl 21) or (z.toLong() and MASK)

    /** Calls [each] with every point in the cubes round [p]'s. */
    inline fun near(p: Vec3, each: (Int) -> Unit) {
        val cx = cellOf(p.x)
        val cy = cellOf(p.y)
        val cz = cellOf(p.z)
        for (x in cx - 1..cx + 1) for (y in cy - 1..cy + 1) for (z in cz - 1..cz + 1) buckets[key(x, y, z)]?.forEach(each)
    }

    private companion object {
        const val MASK = (1L shl 21) - 1
    }
}
