package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.nearestOnTriangle

/** Where the body lies near a point: a [point] on it and its outward [normal] there, unit. */
internal class BodyPlane(val point: Vec3, val normal: Vec3) {
    /** How far [p] stands off the body along its normal; below 0 inside it. */
    fun standoff(p: Vec3): Float = (p - point) dot normal
}

/**
 * Body parts' vertices, [positions] three values each with their [normals], joined by [triangles], read near any point through a grid
 * of [reach]-sized cells: every vertex within [reach] past the nearest one, each weighed by a kernel easing from 1 at the nearest
 * one's distance to 0 at [reach] past it ([near]), or the nearest point on the triangles at those vertices ([planeAt]).
 */
internal class BodySurface(private val positions: FloatArray, private val normals: FloatArray, private val triangles: IntArray, val reach: Float) {
    private val grid = NearestVertex(positions, reach)

    /** The triangles at each vertex. */
    private val corners: Array<IntArray> = run {
        val lists = Array(positions.size / 3) { ArrayList<Int>() }
        for (t in 0 until triangles.size / 3) for (c in 0 until 3) lists[triangles[t * 3 + c]] += t
        Array(lists.size) { lists[it].toIntArray() }
    }

    /** Hands [action] each body vertex near [p] with its kernel weight; false, handing none, when no vertex lies within a few reaches. */
    fun near(p: Vec3, action: (vertex: Int, kernel: Float) -> Unit): Boolean {
        var nearest = grid.of(p) ?: return false
        var least = (positions.vec(nearest * 3) - p).length
        grid.forEachWithin(p, least) { k, distance ->
            if (distance < least) {
                least = distance
                nearest = k
            }
        }
        var any = false
        grid.forEachWithin(p, least + reach) { k, distance ->
            val t = ((distance - least) / reach).coerceIn(0f, 1f)
            val kernel = (1f - t * t).let { it * it }
            if (kernel > 0f) {
                action(k, kernel)
                any = true
            }
        }
        if (!any) action(nearest, 1f)
        return true
    }

    /**
     * The body near [p]: of its triangles at the vertices within [reach] past its nearest vertex, the point nearest [p], with the normal
     * its corners' normals give there. With no triangles, the vertices round it eased ([eased]); null where none lies within a few reaches.
     */
    fun planeAt(p: Vec3): BodyPlane? {
        val nearest = grid.of(p) ?: return null
        var least = (positions.vec(nearest * 3) - p).length
        grid.forEachWithin(p, least) { _, distance -> least = minOf(least, distance) }
        var best = Float.MAX_VALUE
        var plane: BodyPlane? = null
        grid.forEachWithin(p, least + reach) { k, _ ->
            for (t in corners[k]) {
                val a = triangles[t * 3]
                val b = triangles[t * 3 + 1]
                val c = triangles[t * 3 + 2]
                val (q, w) = nearestOnTriangle(p, positions.vec(a * 3), positions.vec(b * 3), positions.vec(c * 3))
                val normal = (normals.vec(a * 3) * w.x + normals.vec(b * 3) * w.y + normals.vec(c * 3) * w.z).unitOr(Vec3.ZERO)
                if (normal == Vec3.ZERO) continue
                val distance = (p - q).length
                if (distance >= best) continue
                best = distance
                plane = BodyPlane(q, normal)
            }
        }
        return plane ?: eased(p)
    }

    /** The body near [p] read on the vertices round it, each weighed by its kernel. */
    private fun eased(p: Vec3): BodyPlane? {
        var point = Vec3.ZERO
        var normal = Vec3.ZERO
        var total = 0f
        if (!near(p) { k, kernel ->
                point += positions.vec(k * 3) * kernel
                normal += normals.vec(k * 3) * kernel
                total += kernel
            }
        ) return null
        val unit = normal.unitOr(Vec3.ZERO)
        if (total <= 0f || unit == Vec3.ZERO) return null
        return BodyPlane(point * (1f / total), unit)
    }

    companion object {
        /** The body made of [positions], body space three values a vertex, and the normals of [meshes], one of each a part. */
        fun of(positions: List<FloatArray>, meshes: List<RenderMesh>, reach: Float): BodySurface {
            val count = positions.sumOf { it.size }
            val all = FloatArray(count)
            val normals = FloatArray(count)
            val triangles = IntArray(meshes.sumOf { it.indices.size })
            var at = 0
            var corner = 0
            positions.forEachIndexed { i, part ->
                part.copyInto(all, at)
                for (index in meshes[i].indices) triangles[corner++] = at / 3 + index
                val vertices = meshes[i].vertices
                for (v in 0 until part.size / 3) {
                    val n = vertices.vec(v * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.NORMAL_OFFSET_FLOATS).unitOr(Vec3.ZERO)
                    normals.put(at + v * 3, n)
                }
                at += part.size
            }
            return BodySurface(all, normals, triangles, reach)
        }
    }
}
