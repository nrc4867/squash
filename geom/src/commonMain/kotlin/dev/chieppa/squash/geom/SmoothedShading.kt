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
 * thin sheet never mix. A mesh like one smoothed before takes that result from [SmoothedMeshes].
 */
fun RenderMesh.withSmoothedShading(): RenderMesh = SmoothedMeshes.of(this) { smoothedNow() }

private fun RenderMesh.smoothedNow(): RenderMesh {
    val count = vertexCount
    if (count == 0) return this
    val positions = FloatArray(count * 3)
    val proxies = FloatArray(count * 3)
    for (v in 0 until count) {
        val at = v * RenderMesh.FLOATS_PER_VERTEX
        for (k in 0..2) {
            positions[v * 3 + k] = vertices[at + k]
            proxies[v * 3 + k] = vertices[at + RenderMesh.PROXY_OFFSET_FLOATS + k]
        }
    }
    var lowX = positions[0]
    var lowY = positions[1]
    var lowZ = positions[2]
    var highX = lowX
    var highY = lowY
    var highZ = lowZ
    for (v in 0 until count) {
        lowX = min(lowX, positions[v * 3]); highX = max(highX, positions[v * 3])
        lowY = min(lowY, positions[v * 3 + 1]); highY = max(highY, positions[v * 3 + 1])
        lowZ = min(lowZ, positions[v * 3 + 2]); highZ = max(highZ, positions[v * 3 + 2])
    }
    val reach = min(highX - lowX, min(highY - lowY, highZ - lowZ)) * REACH_SHARE
    if (reach < TINY) return this
    val area = vertexAreas(positions)
    val grid = CellBuckets(positions, reach)
    val reachSquared = reach * reach
    val spread = reach * SPREAD_SHARE
    val falloff = 1f / (2f * spread * spread)
    val out = vertices.copyOf()
    for (v in 0 until count) {
        val px = positions[v * 3]
        val py = positions[v * 3 + 1]
        val pz = positions[v * 3 + 2]
        val ox = proxies[v * 3]
        val oy = proxies[v * 3 + 1]
        val oz = proxies[v * 3 + 2]
        var sx = 0f
        var sy = 0f
        var sz = 0f
        grid.near(px, py, pz) { other ->
            val dx = positions[other * 3] - px
            val dy = positions[other * 3 + 1] - py
            val dz = positions[other * 3 + 2] - pz
            val squared = dx * dx + dy * dy + dz * dz
            if (squared <= reachSquared) {
                val qx = proxies[other * 3]
                val qy = proxies[other * 3 + 1]
                val qz = proxies[other * 3 + 2]
                if (ox * qx + oy * qy + oz * qz > 0f) {
                    val weight = exp(-squared * falloff) * area[other]
                    sx += qx * weight
                    sy += qy * weight
                    sz += qz * weight
                }
            }
        }
        val sum = Vec3(sx, sy, sz)
        val smoothed = if (sum.length < TINY) Vec3(ox, oy, oz) else sum.normalized()
        val at = v * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.PROXY_OFFSET_FLOATS
        out[at] = smoothed.x
        out[at + 1] = smoothed.y
        out[at + 2] = smoothed.z
    }
    return RenderMesh(out, indices)
}

/** A third of the area of every face round each vertex, so dense rings near a pole do not outweigh the rest. */
private fun RenderMesh.vertexAreas(positions: FloatArray): FloatArray {
    val area = FloatArray(positions.size / 3)
    for (t in 0 until indices.size / 3) {
        val a = indices[t * 3]
        val b = indices[t * 3 + 1]
        val c = indices[t * 3 + 2]
        val third = ((point(positions, b) - point(positions, a)) cross (point(positions, c) - point(positions, a))).length / 6f
        area[a] += third
        area[b] += third
        area[c] += third
    }
    return area
}

private fun point(positions: FloatArray, vertex: Int) = Vec3(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])

/**
 * Points of a flat xyz array bucketed in cubes of [cell], for the points near one. Each cube's points are held in index order, in
 * one array sorted by cube, and a cube is found by binary search over the cubes' keys.
 */
private class CellBuckets(positions: FloatArray, private val cell: Float) {
    private val keys: LongArray
    private val starts: IntArray
    private val members: IntArray

    init {
        val count = positions.size / 3
        val own = LongArray(count) { key(cellOf(positions[it * 3]), cellOf(positions[it * 3 + 1]), cellOf(positions[it * 3 + 2])) }
        val order = (0 until count).sortedWith(compareBy<Int> { own[it] }.thenBy { it })
        members = order.toIntArray()
        val distinct = ArrayList<Long>()
        val firsts = ArrayList<Int>()
        members.forEachIndexed { i, member ->
            if (distinct.isEmpty() || distinct.last() != own[member]) {
                distinct += own[member]
                firsts += i
            }
        }
        keys = distinct.toLongArray()
        starts = IntArray(keys.size + 1) { if (it < firsts.size) firsts[it] else count }
    }

    private fun cellOf(value: Float) = floor(value / cell).toInt()

    private fun key(x: Int, y: Int, z: Int): Long = ((x.toLong() and MASK) shl 42) or ((y.toLong() and MASK) shl 21) or (z.toLong() and MASK)

    private fun bucket(key: Long): Int {
        var low = 0
        var high = keys.size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val at = keys[middle]
            when {
                at < key -> low = middle + 1
                at > key -> high = middle - 1
                else -> return middle
            }
        }
        return -1
    }

    /** Calls [each] with every point in the cubes round the point at [x], [y], [z]'s cube, cube by cube in x, y, z order. */
    inline fun near(x: Float, y: Float, z: Float, each: (Int) -> Unit) {
        val cx = cellOf(x)
        val cy = cellOf(y)
        val cz = cellOf(z)
        for (i in cx - 1..cx + 1) for (j in cy - 1..cy + 1) for (k in cz - 1..cz + 1) {
            val found = bucket(key(i, j, k))
            if (found >= 0) for (m in starts[found] until starts[found + 1]) each(members[m])
        }
    }

    private companion object {
        const val MASK = (1L shl 21) - 1
    }
}
