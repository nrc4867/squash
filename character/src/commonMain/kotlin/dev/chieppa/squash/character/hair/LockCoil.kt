package dev.chieppa.squash.character.hair

import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private const val DEGREES = (PI / 180.0).toFloat()
private const val TWO_PI = (2.0 * PI).toFloat()

/** The steps a coiled lock's path is read in. */
private const val COIL_STEPS = 72

/** The golden angle, in radians: each lock of a bun starts its coil this far round from the one before, so their coils interleave. */
private val GOLDEN = (PI * (3.0 - kotlin.math.sqrt(5.0))).toFloat()

/** How far out from the tie, in coil radii, a lock's coil starts and ends, and how far in it draws toward its end, as a share of its radius. */
private const val COIL_BASE = 0.3f
private const val COIL_TOP = 1.15f
private const val COIL_TUCK = 0.6f

/** The share of a coil's way over which it swings out from the tie to its full radius, and the share before its end where it starts tucking in. */
private const val COIL_OUT = 0.12f
private const val COIL_IN = 0.65f

/** How the coil's rise is spent along it: more at its start, so the lower turns sit wide and the upper ones close over them as a dome. */
private const val RISE_EASE = 0.8f

/**
 * The path of a tied lock coiled round its tie into a bun: from its sunk [root] it swings out round the bun's axis, which leaves the head
 * at the lock's rise, winding [LockSpec.coil] turns as it rises along the axis and drawing in toward the axis over its last turn, so the
 * bun's coils stack into a dome with the end tucked in at its top. Each lock of the group starts a golden angle round from the one before.
 * Its radius is what makes the path the lock's length, and every point keeps the lock's clearance off the scalp.
 */
internal fun coiled(lock: LockSpec, scalp: Scalp, root: Vec3): List<Vec3> {
    val rise = lock.rise * DEGREES
    val axis = (lock.growth * cos(rise) + lock.out * sin(rise)).normalized()
    val across = (lock.growth - axis * (lock.growth dot axis)).let { if (it.length > 1e-4f) it.normalized() else perpendicular(axis) }
    val second = axis cross across
    val start = lock.index * GOLDEN
    fun at(s: Float, radius: Float): Vec3 {
        val angle = start + TWO_PI * lock.coil * s
        val out = smooth(s / COIL_OUT) * (1f - COIL_TUCK * smooth((s - COIL_IN) / (1f - COIL_IN)))
        val up = COIL_BASE + (COIL_TOP - COIL_BASE) * s.pow(RISE_EASE)
        return root + axis * (up * radius) + (across * cos(angle) + second * sin(angle)) * (out * radius)
    }
    var unit = 0f
    var last = at(0f, 1f)
    for (k in 1..COIL_STEPS) {
        val here = at(k.toFloat() / COIL_STEPS, 1f)
        unit += (here - last).length
        last = here
    }
    val radius = (lock.length + lock.sink) / (unit + COIL_BASE).coerceAtLeast(1e-4f)
    val path = ArrayList<Vec3>(COIL_STEPS + 2)
    path += root
    for (k in 0..COIL_STEPS) {
        val p = at(k.toFloat() / COIL_STEPS, radius)
        val from = p - scalp.middle
        val least = scalp.reachToward(p) + lock.clearance
        path += if (from.length in 1e-6f..least) scalp.middle + from * (least / from.length) else p
    }
    return path
}

private fun perpendicular(v: Vec3): Vec3 = (if (abs(v.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)).let { (it - v * (it dot v)).normalized() }

private fun smooth(x: Float): Float {
    val a = x.coerceIn(0f, 1f)
    return a * a * (3f - 2f * a)
}
