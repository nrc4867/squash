package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.nearestOnTriangle

/**
 * A surface of [points] and their outward [normals] joined by [triangles], read for how far a point stands off it: the distance to its
 * nearest point, signed by the surface's normal there, below 0 inside.
 */
internal class SurfaceSide(private val points: List<Vec3>, private val normals: List<Vec3>, private val triangles: IntArray) {
    /** How far [p] stands off the surface, below 0 inside it; null with no triangle within [reach]. */
    fun standoff(p: Vec3, reach: Float): Float? {
        var best = Float.MAX_VALUE
        var signed: Float? = null
        for (t in 0 until triangles.size / 3) {
            val i = triangles[t * 3]
            val j = triangles[t * 3 + 1]
            val k = triangles[t * 3 + 2]
            if ((points[i] - p).length > reach) continue
            val (q, w) = nearestOnTriangle(p, points[i], points[j], points[k])
            val distance = (p - q).length
            if (distance >= best) continue
            best = distance
            val normal = normals[i] * w.x + normals[j] * w.y + normals[k] * w.z
            signed = if (((p - q) dot normal) < 0f) -distance else distance
        }
        return signed
    }
}
