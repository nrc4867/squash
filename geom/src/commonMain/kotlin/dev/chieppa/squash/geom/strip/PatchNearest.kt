package dev.chieppa.squash.geom.strip

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt
import kotlin.math.abs
import kotlin.math.floor

/** How many Gauss-Newton steps refine a place on a patch from its nearest grid point. */
private const val REFINE_STEPS = 5

private const val SINGULAR = 1e-12f

/**
 * The (u, v) on this patch nearest [point], both in the patch's own space: its nearest grid point, then steps across the surface along
 * its two tangents, u held to the patch's rings and v read round on a closed patch.
 */
fun Patch.nearestUv(point: Vec3): SurfaceUv {
    var best = 0
    var bestDistance = Float.POSITIVE_INFINITY
    for (i in 0 until nu * nv) {
        val off = point(i) - point
        val distance = off dot off
        if (distance < bestDistance) {
            bestDistance = distance
            best = i
        }
    }
    val rings = ringUs
    var u = rings[best / nv]
    var v = (best % nv).toFloat() / segments
    repeat(REFINE_STEPS) {
        val at = surfaceAt(u, v)
        val off = point - positionAt(u, v)
        val a = at.alongU dot at.alongU
        val b = at.alongU dot at.aroundV
        val c = at.aroundV dot at.aroundV
        val det = a * c - b * b
        if (abs(det) < SINGULAR) return@repeat
        val ru = at.alongU dot off
        val rv = at.aroundV dot off
        u = (u + (c * ru - b * rv) / det).coerceIn(rings.first(), rings.last())
        v += (a * rv - b * ru) / det
        v = if (closedV) v - floor(v) else v.coerceIn(0f, 1f)
    }
    return SurfaceUv(u, v)
}
