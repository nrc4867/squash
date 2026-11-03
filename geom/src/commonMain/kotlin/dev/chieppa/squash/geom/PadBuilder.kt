package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val RINGS = 7
private const val SEGMENTS = 12
private const val RAISE_OF_RADIUS = 0.3f
private const val SINK_OF_RAISE = 0.35f
private const val MAX_V_REACH = 0.22f
private const val TWO_PI = (2.0 * PI).toFloat()

/** A raised area on a surface: a closed lens whose rim follows the surface just under it, so it wraps a curved part. */
object PadBuilder {
    /** The pad on [surface] at [u] and [v], with [radius] across the surface. Its vertices take their bone from [u]. */
    fun pad(name: String, surface: Patch, u: Float, v: Float, radius: Float): Patch {
        val site = surface.surfaceAt(u, v)
        val reachU = radius / site.alongU.length
        val reachV = (radius / site.aroundV.length).coerceAtMost(MAX_V_REACH)
        val raise = radius * RAISE_OF_RADIUS
        val sink = raise * SINK_OF_RAISE
        val points = FloatArray(RINGS * SEGMENTS * Patch.COMPONENTS)
        fun put(ring: Int, segment: Int, p: Vec3) {
            val o = (ring * SEGMENTS + segment) * Patch.COMPONENTS
            points[o] = p.x
            points[o + 1] = p.y
            points[o + 2] = p.z
        }
        val inner = site.position - site.normal * (sink * 2f)
        val apex = site.position + site.normal * (raise - sink)
        for (segment in 0 until SEGMENTS) {
            put(0, segment, inner)
            put(RINGS - 1, segment, apex)
        }
        for (ring in 1 until RINGS - 1) {
            val rho = 1f - (ring - 1).toFloat() / (RINGS - 2)
            val lift = raise * sqrt(1f - rho * rho) - sink
            for (segment in 0 until SEGMENTS) {
                val angle = segment * TWO_PI / SEGMENTS
                val on = surface.surfaceAt(u + rho * cos(angle) * reachU, v - rho * sin(angle) * reachV)
                put(ring, segment, on.position + on.normal * lift)
            }
        }
        return Patch(name, RINGS, SEGMENTS, closedV = true, slices = null, points = points, boneU = u)
    }
}
