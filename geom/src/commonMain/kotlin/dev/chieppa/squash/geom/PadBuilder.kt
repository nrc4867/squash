package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val RAISE_OF_RADIUS = 0.3f
private const val SINK_OF_RAISE = 0.35f
private const val MAX_V_REACH = 0.22f
private const val TWO_PI = (2.0 * PI).toFloat()

/** A raised area on a surface: a closed lens whose rim follows the surface just under it, so it wraps a curved part. */
object PadBuilder {
    /** The fewest rings a pad can have: its inner point, a rim and its apex. */
    const val MIN_RINGS = 3

    /**
     * The pad on [surface] at [u] and [v], with [radius] across the surface, on a grid of [rings] by [segments]. Its
     * vertices take their bone from [u].
     */
    fun pad(name: String, surface: Patch, u: Float, v: Float, radius: Float, rings: Int, segments: Int): Patch {
        val site = surface.surfaceAt(u, v)
        val reachU = radius / site.alongU.length
        val reachV = (radius / site.aroundV.length).coerceAtMost(MAX_V_REACH)
        val raise = radius * RAISE_OF_RADIUS
        val sink = raise * SINK_OF_RAISE
        val points = FloatArray(rings * segments * Patch.COMPONENTS)
        fun put(ring: Int, segment: Int, p: Vec3) {
            val o = (ring * segments + segment) * Patch.COMPONENTS
            points[o] = p.x
            points[o + 1] = p.y
            points[o + 2] = p.z
        }
        val inner = site.position - site.normal * (sink * 2f)
        val apex = site.position + site.normal * (raise - sink)
        for (segment in 0 until segments) {
            put(0, segment, inner)
            put(rings - 1, segment, apex)
        }
        for (ring in 1 until rings - 1) {
            val rho = 1f - (ring - 1).toFloat() / (rings - 2)
            val lift = raise * sqrt(1f - rho * rho) - sink
            for (segment in 0 until segments) {
                val angle = segment * TWO_PI / segments
                val on = surface.surfaceAt(u + rho * cos(angle) * reachU, v - rho * sin(angle) * reachV)
                put(ring, segment, on.position + on.normal * lift)
            }
        }
        return Patch(name, rings, segments, closedV = true, slices = null, points = points, boneU = u)
    }
}
