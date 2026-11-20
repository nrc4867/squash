package dev.chieppa.squash.character.hair

import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private const val DEGREES = (PI / 180.0).toFloat()
private const val TWO_PI = (2.0 * PI).toFloat()

/** The steps a lock's path is grown in before its few guide points are taken from it. */
private const val GROW_STEPS = 40

/** How far round the head a full sweep turns a lock, in degrees. */
private const val SWEEP_TURN = 70f

/** Where along a lock its curl starts, as a share of its length; a curl of a turn or more starts nearer its root. */
private const val CURL_START = 0.35f

/** How a curl tightens toward its tip: the turn so far grows as this power of the way into the curl, so the lock rolls into a spiral. */
private const val CURL_TIGHTENING = 1.2f

/** How far a curl's loop moves sideways each turn, as a share of the lock's width, so its turns lie beside each other as a ringlet's. */
private const val HELIX_PITCH = 0.35f

/** Where along a lock a flicked tip starts turning out, and how far it turns, in degrees. */
private const val FLICK_START = 0.7f
private const val FLICK_TURN = 50f

/** How far along a lock its clearance off the scalp rises from its sunk root to its full, as a share of its length. */
private const val CLEAR_REACH = 0.18f

/** The fewest guide points, and how many a turn of curl adds. */
private const val LEAST_POINTS = 5
private const val POINTS_PER_TURN = 6
private const val MOST_POINTS = 18

private val DOWN = Vec3(0f, -1f, 0f)

/** The rise in degrees below which a lock lies along the head rather than leaving it, hugging it fully at a rise of 0. */
private const val HUG_RISE = 25f

/** How far below level, as the sine of the angle down from the scalp's middle, a lock stops hugging the head. */
private const val HUG_BELOW = 0.35f

/**
 * The guide curve [lock] grows along over [scalp], a few points from its sunk root to its tip: from its root it rises [LockSpec.rise] off
 * the scalp along its growth, sweeps sideways, falls toward the ground over its length, curls toward its underside toward its tip, and a
 * flicked tip turns out; every point past its root keeps its clearance off the scalp. A lock with a guide of its own takes those points from
 * its root instead.
 */
fun guideOf(lock: LockSpec, scalp: Scalp): List<Vec3> {
    val root = lock.root - lock.out * lock.sink
    lock.guide?.let { own -> return listOf(root) + own.map { lock.root + it } }
    val path = grown(lock, scalp, root)
    val points = (LEAST_POINTS + ceil(abs(lock.curl) * POINTS_PER_TURN).toInt()).coerceAtMost(MOST_POINTS)
    return evenly(path, points)
}

private fun grown(lock: LockSpec, scalp: Scalp, root: Vec3): List<Vec3> {
    val rise = lock.rise * DEGREES
    val side = (lock.growth cross lock.out).normalized()
    val start = (lock.growth * cos(rise) + lock.out * sin(rise)).normalized()
    val step = (lock.length + lock.sink) / GROW_STEPS
    val curlFrom = (CURL_START - 0.3f * abs(lock.curl).coerceAtMost(1f)).coerceAtLeast(0.05f)
    val hug = ((HUG_RISE - lock.rise) / HUG_RISE).coerceIn(0f, 1f)
    val path = ArrayList<Vec3>(GROW_STEPS + 1)
    var at = root
    path += at
    for (k in 1..GROW_STEPS) {
        val t = k.toFloat() / GROW_STEPS
        var d = turned(start, lock.out, lock.sweep * SWEEP_TURN * DEGREES * t)
        val f = lock.fall * t
        d = (d * (1f - f) + DOWN * f).let { if (it.length < 1e-4f) d else it.normalized() }
        val bend = side
        if (lock.curl != 0f && t > curlFrom) d = turned(d, bend, -lock.curl * TWO_PI * ((t - curlFrom) / (1f - curlFrom)).pow(CURL_TIGHTENING))
        if (lock.tip == LockTip.FLICKED && t > FLICK_START) {
            val s = (t - FLICK_START) / (1f - FLICK_START)
            d = turned(d, bend, FLICK_TURN * DEGREES * s * s)
        }
        at += d * step
        if (lock.curl != 0f && t > curlFrom) at += side * (lock.width * HELIX_PITCH * abs(lock.curl) / ((1f - curlFrom) * GROW_STEPS))
        val clear = if (t < CLEAR_REACH) -lock.sink + (lock.clearance + lock.sink) * (t / CLEAR_REACH) else lock.clearance
        at = keptOut(at, scalp, clear, hug)
        path += at
    }
    return path
}

/**
 * [point] moved out from the scalp's middle until it lies [clear] off the scalp; above the head's widest, where the scalp still curves
 * out under it, it is also drawn in toward lying [clear] off it by [hug], so a lying lock follows the head down to where it hangs free.
 */
private fun keptOut(point: Vec3, scalp: Scalp, clear: Float, hug: Float): Vec3 {
    val from = point - scalp.middle
    val distance = from.length
    if (distance == 0f) return point
    val least = scalp.reachToward(point) + clear
    if (distance < least) return scalp.middle + from * (least / distance)
    val over = (from.y / distance / HUG_BELOW + 1f).coerceIn(0f, 1f) * hug
    if (over <= 0f) return point
    return scalp.middle + from * ((distance + (least - distance) * over) / distance)
}

/** [d] turned [angle] radians about [axis], a unit vector. */
private fun turned(d: Vec3, axis: Vec3, angle: Float): Vec3 {
    if (angle == 0f) return d
    val c = cos(angle)
    val s = sin(angle)
    return d * c + (axis cross d) * s + axis * ((axis dot d) * (1f - c))
}

/** [count] points spaced evenly along [path] by length, its first and last among them. */
internal fun evenly(path: List<Vec3>, count: Int): List<Vec3> {
    val lengths = FloatArray(path.size)
    for (i in 1 until path.size) lengths[i] = lengths[i - 1] + (path[i] - path[i - 1]).length
    val total = lengths.last()
    if (total <= 0f) return listOf(path.first(), path.first() + Vec3(0f, -1e-3f, 0f))
    var j = 0
    return List(count) { k ->
        val target = total * k / (count - 1)
        while (j < path.size - 2 && lengths[j + 1] < target) j++
        val span = lengths[j + 1] - lengths[j]
        val f = if (span > 0f) ((target - lengths[j]) / span).coerceIn(0f, 1f) else 0f
        path[j] + (path[j + 1] - path[j]) * f
    }
}
