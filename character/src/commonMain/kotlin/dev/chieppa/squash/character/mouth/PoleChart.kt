package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec2
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** The u halfway along a run: a chart about the end at u 1 serves places past it, else the end at u 0. */
private const val MIDDLE_U = 0.5f

private val TURN = 2f * PI.toFloat()

/**
 * A run's (u, v) laid out flat about one of its ends: the distance from that end in u at the angle round it in v, so a line that passes
 * round or through a closed end, as a mouth's does round a muzzle's tip, runs smoothly through it, and v needs no unwrapping.
 */
internal class PoleChart(private val far: Boolean) {
    fun of(uv: SurfaceUv): Vec2 {
        val r = if (far) 1f - uv.u else uv.u
        return Vec2(r * cos(TURN * uv.v), r * sin(TURN * uv.v))
    }

    fun uvOf(c: Vec2): SurfaceUv {
        val r = hypot(c.x, c.y).coerceAtMost(1f)
        val v = atan2(c.y, c.x) / TURN
        return SurfaceUv(if (far) 1f - r else r, v - floor(v))
    }

    companion object {
        /** The chart about the end nearer [middleU], the u of the places it serves. */
        fun about(middleU: Float) = PoleChart(far = middleU > MIDDLE_U)
    }
}
