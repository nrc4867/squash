package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3

/** The least length a unit of v counts for, as a share of the run's widest ring's, so a ring closed to a point still measures v. */
private const val LEAST_GIRTH_SHARE = 0.1f

private const val TINY = 1e-6f

/**
 * How long a unit of u and of v is on a run's surface at any u: along its rings' middles, and round its ring there. A shape in the run's
 * (u, v) measured with these is measured in body units.
 */
class RunScales(patch: Patch) {
    private val us = patch.ringUs
    private val alongs: FloatArray
    private val girths: FloatArray

    init {
        val rings = patch.nu
        val middles = List(rings) { ring -> (0 until patch.nv).fold(Vec3.ZERO) { sum, c -> sum + patch.point(patch.index(ring, c)) } * (1f / patch.nv) }
        val perimeters = FloatArray(rings) { ring ->
            var total = 0f
            val last = if (patch.closedV) patch.nv else patch.nv - 1
            for (c in 0 until last) total += (patch.point(patch.index(ring, (c + 1) % patch.nv)) - patch.point(patch.index(ring, c))).length
            if (patch.closedV) total else total * patch.segments / maxOf(patch.nv - 1, 1)
        }
        val least = (perimeters.maxOrNull() ?: 0f) * LEAST_GIRTH_SHARE
        girths = FloatArray(rings) { maxOf(perimeters[it], least, TINY) }
        alongs = FloatArray(rings) { ring ->
            val a = minOf(ring, rings - 2)
            val span = us[a + 1] - us[a]
            if (span > TINY) maxOf((middles[a + 1] - middles[a]).length / span, TINY) else TINY
        }
    }

    /** How long a unit of u is at [u]. */
    fun along(u: Float): Float = at(alongs, u)

    /** How long a unit of v is at [u]: the ring's girth there. */
    fun around(u: Float): Float = at(girths, u)

    /** The mean girth of the run's rings. */
    val meanGirth: Float get() = girths.average().toFloat()

    /** How far along the run's rings' middles [u] lies from u 0, in body units, running on straight past its first and last ring. */
    fun lengthTo(u: Float): Float {
        val n = us.size
        if (u <= us[0]) return (u - us[0]) * alongs[0] + startLength
        var total = startLength
        for (ring in 0 until n - 1) {
            val to = us[ring + 1]
            if (u <= to) return total + (u - us[ring]) * alongs[ring]
            total += (to - us[ring]) * alongs[ring]
        }
        return total + (u - us[n - 1]) * alongs[n - 1]
    }

    private val startLength: Float get() = us[0] * alongs[0]

    private fun at(values: FloatArray, u: Float): Float {
        val n = us.size
        if (u <= us[0]) return values[0]
        if (u >= us[n - 1]) return values[n - 1]
        var ring = 0
        while (ring < n - 2 && us[ring + 1] < u) ring++
        val span = us[ring + 1] - us[ring]
        val t = if (span > TINY) (u - us[ring]) / span else 0f
        return values[ring] * (1f - t) + values[ring + 1] * t
    }
}
