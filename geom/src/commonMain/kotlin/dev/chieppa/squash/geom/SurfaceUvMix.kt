package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Half a turn round a run in v, and halfway along it in u. */
private const val HALF = 0.5f

/** How near a closed end in u two places must both lie to be mixed across the chart round it. */
private const val NEAR_POLE = 0.15f

/**
 * The place [t] of the way from [a] to [b] on a run: the short way round in v, or, where both lie within [NEAR_POLE] of the same closed
 * end, straight across the flat chart round it, so a mix of places either side of a muzzle's tip does not go round the far side.
 */
fun mixedPlace(a: SurfaceUv, b: SurfaceUv, t: Float): SurfaceUv {
    val far = a.u > HALF && b.u > HALF
    val ra = if (far) 1f - a.u else a.u
    val rb = if (far) 1f - b.u else b.u
    if ((a.u > HALF) == (b.u > HALF) && max(ra, rb) < NEAR_POLE) {
        val turn = 2f * PI.toFloat()
        val x = ra * cos(turn * a.v) + (rb * cos(turn * b.v) - ra * cos(turn * a.v)) * t
        val y = ra * sin(turn * a.v) + (rb * sin(turn * b.v) - ra * sin(turn * a.v)) * t
        val r = hypot(x, y)
        val v = atan2(y, x) / turn
        return SurfaceUv(if (far) 1f - r else r, v - floor(v))
    }
    val dv = (b.v - a.v).let { it - floor(it + HALF) }
    val v = a.v + dv * t
    return SurfaceUv(a.u + (b.u - a.u) * t, v - floor(v))
}
