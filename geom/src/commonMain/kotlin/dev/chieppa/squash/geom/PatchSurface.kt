package dev.chieppa.squash.geom

import kotlin.math.floor

/** A point on a patch's surface, with the outward [normal] and the rates of change along u and around v. */
data class SurfacePoint(val position: Vec3, val normal: Vec3, val alongU: Vec3, val aroundV: Vec3)

/** Whether this swept patch's rings reach [u]. */
fun Patch.covers(u: Float): Boolean {
    val rings = slices ?: return false
    return u >= rings.first().u && u <= rings.last().u
}

private fun Patch.grid(ring: Int, segment: Int): Vec3 = point(index(ring, segment))

/** The position at [u] and [v], interpolated over the grid. [u] is the part's, as this patch's slices give it. */
fun Patch.positionAt(u: Float, v: Float): Vec3 {
    val rings = requireNotNull(slices) { "Patch $name has no slices to place a point by" }
    var ring = 0
    while (ring < rings.size - 2 && rings[ring + 1].u < u) ring++
    val span = rings[ring + 1].u - rings[ring].u
    val t = if (span > 0f) ((u - rings[ring].u) / span).coerceIn(0f, 1f) else 0f
    val around = (v - floor(v)) * nv
    val segment = around.toInt().coerceAtMost(nv - 1)
    val s = around - segment
    val next = (segment + 1) % nv
    val near = grid(ring, segment) + (grid(ring, next) - grid(ring, segment)) * s
    val far = grid(ring + 1, segment) + (grid(ring + 1, next) - grid(ring + 1, segment)) * s
    return near + (far - near) * t
}

/**
 * The surface at [u] and [v], measured across one grid cell each way so a normal does not jump between cells. u is held
 * off the end rings, where a pole has no normal.
 */
fun Patch.surfaceAt(u: Float, v: Float): SurfacePoint {
    val rings = requireNotNull(slices) { "Patch $name has no slices to place a point by" }
    val first = rings.first().u
    val last = rings.last().u
    val cell = (last - first) / (rings.size - 1)
    val held = u.coerceIn(first + cell, last - cell)
    val aroundCell = 1f / nv
    val alongU = (positionAt(held + cell, v) - positionAt(held - cell, v)) * (0.5f / cell)
    val aroundV = (positionAt(held, v + aroundCell) - positionAt(held, v - aroundCell)) * (0.5f / aroundCell)
    return SurfacePoint(positionAt(held, v), (alongU cross aroundV).normalized(), alongU, aroundV)
}
