package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor

/** Where a point lies by a run's axis: its [u], its distance [along] the axis from u 0 in body units, and its [turn] round it, 0 to 1. */
class AxisPlace(val u: Float, val along: Float, val turn: Float)

/**
 * A run of the body as a bent axis through its rings' middles, each ring turned to its own first point, in body space at rest: any point
 * is read by the nearest stretch of the axis, so a run's coordinates go on smoothly past its ends and over the surface blended round it.
 */
class RunAxis(run: FieldRun) {
    private val us = run.patch.ringUs
    private val middles: Array<Vec3>
    private val firsts: Array<Vec3>
    private val lengths: FloatArray
    private val turnSign: Float

    init {
        val patch = run.patch
        val part = run.part
        fun world(ring: Int, c: Int): Vec3 = part.origin + part.surface.shown(patch.point(patch.index(ring, c)))
        middles = Array(patch.nu) { ring -> (0 until patch.nv).fold(Vec3.ZERO) { sum, c -> sum + world(ring, c) } * (1f / patch.nv) }
        lengths = FloatArray(patch.nu)
        for (ring in 1 until patch.nu) lengths[ring] = lengths[ring - 1] + (middles[ring] - middles[ring - 1]).length
        val raw = Array(patch.nu) { ring -> world(ring, 0) - middles[ring] }
        val widest = (0 until patch.nu).maxBy { raw[it].length }
        firsts = Array(patch.nu) { ring -> raw.getOrNull(nearestRound(raw, ring)) ?: raw[widest] }
        val quarter = world(widest, patch.nv / 4) - middles[widest]
        turnSign = if (tangent(widest).cross(raw[widest]).dot(quarter) >= 0f) 1f else -1f
    }

    /** The ring nearest [ring] whose first point stands off its middle. */
    private fun nearestRound(raw: Array<Vec3>, ring: Int): Int {
        for (step in raw.indices) {
            if (ring - step >= 0 && raw[ring - step].length > TINY) return ring - step
            if (ring + step < raw.size && raw[ring + step].length > TINY) return ring + step
        }
        return -1
    }

    private fun tangent(ring: Int): Vec3 {
        val a = maxOf(0, minOf(ring, middles.size - 2))
        return (middles[a + 1] - middles[a]).normalized()
    }

    /** Where [point] lies by the axis, read on the stretch between two rings nearest it, run on straight past the first and last. */
    fun place(point: Vec3): AxisPlace {
        var best = 0
        var bestT = 0f
        var nearest = Float.MAX_VALUE
        val last = middles.size - 2
        for (a in 0..last) {
            val d = middles[a + 1] - middles[a]
            val span = d.dot(d)
            if (span <= TINY) continue
            var t = (point - middles[a]).dot(d) / span
            if (a > 0) t = maxOf(t, 0f)
            if (a < last) t = minOf(t, 1f)
            val off = (point - (middles[a] + d * t)).length
            if (off < nearest) {
                nearest = off
                best = a
                bestT = t
            }
        }
        val d = middles[best + 1] - middles[best]
        val along = lengths[best] + d.length * bestT
        val u = us[best] + (us[best + 1] - us[best]) * bestT
        val axis = d.normalized()
        val mixed = firsts[best] * (1f - bestT.coerceIn(0f, 1f)) + firsts[best + 1] * bestT.coerceIn(0f, 1f)
        val e1 = (mixed - axis * mixed.dot(axis)).normalized()
        val e2 = axis.cross(e1)
        val r = point - (middles[best] + d * bestT)
        val angle = atan2(turnSign * r.dot(e2), r.dot(e1)) / (2f * PI.toFloat())
        return AxisPlace(u, along, angle - floor(angle))
    }
}

private const val TINY = 1e-6f
