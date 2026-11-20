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

/** Where along a lock its curl starts, as a share of its length, and how much nearer its root each turn of curl, up to [MOST_EARLY_TURNS], starts it. */
private const val CURL_START = 0.5f
private const val CURL_EARLIER = 0.1f
private const val MOST_EARLY_TURNS = 1.5f

/** How a curl tightens toward its tip: the turn so far grows as this power of the way into the curl, so the lock rolls into a spiral. */
private const val CURL_TIGHTENING = 1.7f

/** How far a curl's loop moves sideways each turn, as a share of the lock's width, so its turns lie beside each other as a ringlet's. */
private const val HELIX_PITCH = 0.35f

/** Where along a lock a flicked tip starts turning out, and how far it turns, in degrees. */
private const val FLICK_START = 0.7f
private const val FLICK_TURN = 50f

/**
 * How far along a lock its clearance off the scalp rises from its sunk root to its full: over the way it takes to climb about one in
 * [CLEAR_SLOPE], so a long lock comes out of the scalp near its root and a short one lies low rather than stands up, at least
 * [CLEAR_REACH] and at most [CLEAR_MOST] of its length.
 */
private const val CLEAR_REACH = 0.03f
private const val CLEAR_SLOPE = 2f
private const val CLEAR_MOST = 0.5f

/** The fewest guide points, and how many a turn of curl adds. */
private const val LEAST_POINTS = 5
private const val POINTS_PER_TURN = 6
private const val MOST_POINTS = 18

private val DOWN = Vec3(0f, -1f, 0f)

/** Where along a lying lock it starts to lift off the head, as a share of its length; a curl starting sooner lifts it there. */
private const val LIFT_FROM = 0.55f

/** How far below level, as the sine of the angle down from the scalp's middle, the head stops holding a lock, which hangs free from there. */
private const val HUG_BELOW = 0.35f

/** A curl under this many turns only turns a lock's end in or out; it starts later along the lock the less it turns. */
private const val TURN_IN = 0.25f
private const val TURN_IN_START = 0.75f

/**
 * The guide curve [lock] grows along over [scalp], a few points from its sunk root to its tip. From its root it lies along the head the
 * way it grows, sweeps sideways and falls toward the ground over its length, held on the head down to just below its widest; past
 * [LIFT_FROM] of its length it lifts off by [LockSpec.rise] degrees. It curls about its side toward its tip, and a flicked tip turns out.
 * A tied lock leaves its tie at its rise instead and hangs free. Every point past its root keeps its clearance off the scalp, and where
 * the scalp falls away below what it roots in, as past a mass's hem, it takes its full clearance at once rather than run on under it. A lock with
 * a guide of its own takes those points from its root instead.
 */
fun guideOf(lock: LockSpec, scalp: Scalp): List<Vec3> {
    val root = lock.root - lock.out * lock.sink
    lock.guide?.let { own -> return listOf(root) + own.map { lock.root + it } }
    val path = grown(lock, scalp, root)
    val points = (LEAST_POINTS + ceil(abs(lock.curl) * POINTS_PER_TURN).toInt()).coerceAtMost(MOST_POINTS)
    return evenly(path, points)
}

/** Where along a lock its curl starts: nearer the root the more it turns, and only toward the end for a curl that just turns it in. */
internal fun curlStart(curl: Float): Float {
    val turns = abs(curl)
    if (turns < TURN_IN) return TURN_IN_START - (TURN_IN_START - (CURL_START - CURL_EARLIER * TURN_IN)) * (turns / TURN_IN)
    return CURL_START - CURL_EARLIER * turns.coerceAtMost(MOST_EARLY_TURNS)
}

private fun grown(lock: LockSpec, scalp: Scalp, root: Vec3): List<Vec3> {
    val rise = lock.rise * DEGREES
    val side = (lock.growth cross lock.out).normalized()
    val start = if (lock.tied) (lock.growth * cos(rise) + lock.out * sin(rise)).normalized() else lock.growth
    val step = (lock.length + lock.sink) / GROW_STEPS
    val curlFrom = curlStart(lock.curl)
    val clearReach = ((lock.clearance + lock.sink) * CLEAR_SLOPE / lock.length.coerceAtLeast(1e-4f)).coerceIn(CLEAR_REACH, CLEAR_MOST)
    val liftFrom = if (lock.curl == 0f) LIFT_FROM else minOf(LIFT_FROM, curlFrom)
    val path = ArrayList<Vec3>(GROW_STEPS + 1)
    var at = root
    var lift = 0f
    val rootReach = scalp.reachToward(lock.root)
    path += at
    for (k in 1..GROW_STEPS) {
        val t = k.toFloat() / GROW_STEPS
        var d = turned(start, lock.out, lock.sweep * SWEEP_TURN * DEGREES * t)
        val f = lock.fall * t
        d = (d * (1f - f) + DOWN * f).let { if (it.length < 1e-4f) d else it.normalized() }
        val here = (at - scalp.middle).let { if (it.length < 1e-6f) lock.out else it.normalized() }
        val lifted = if (lock.tied) 1f else smooth((t - liftFrom) / (1f - liftFrom))
        val over = if (lock.tied) 0f else (here.y / HUG_BELOW + 1f).coerceIn(0f, 1f)
        if (over > 0f) {
            val along = d - here * (d dot here)
            if (along.length > 1e-4f) d = (d * (1f - over) + along.normalized() * over).normalized()
        }
        if (!lock.tied && lifted > 0f) lift += step * sin(rise * lifted)
        if (!lock.tied && lifted > 0f) d = tiltedOut(d, here, rise * lifted)
        val held = if (lock.curl != 0f && t > curlFrom) 0f else over
        if (lock.curl != 0f && t > curlFrom) d = turned(d, side, -lock.curl * TWO_PI * ((t - curlFrom) / (1f - curlFrom)).pow(CURL_TIGHTENING))
        if (lock.tip == LockTip.FLICKED && t > FLICK_START) {
            val s = (t - FLICK_START) / (1f - FLICK_START)
            d = turned(d, side, FLICK_TURN * DEGREES * s * s)
        }
        at += d * step
        if (lock.curl != 0f && t > curlFrom) at += side * (lock.width * HELIX_PITCH * abs(lock.curl) / ((1f - curlFrom) * GROW_STEPS))
        val rising = t < clearReach && scalp.reachToward(at) > rootReach - lock.sink
        val clear = if (rising) -lock.sink + (lock.clearance + lock.sink) * (t / clearReach) else lock.clearance
        at = keptOut(at, scalp, clear + lift, held)
        path += at
    }
    return path
}

/** 0 at or below 0, 1 at or above 1, easing in and out between. */
private fun smooth(x: Float): Float {
    val a = x.coerceIn(0f, 1f)
    return a * a * (3f - 2f * a)
}

/** [d] turned [angle] radians toward [out], the way off the head, about the axis across both. */
private fun tiltedOut(d: Vec3, out: Vec3, angle: Float): Vec3 {
    if (angle == 0f) return d
    val axis = d cross out
    if (axis.length < 1e-4f) return d
    return turned(d, axis.normalized(), angle)
}

/**
 * [point] moved out from the scalp's middle until it lies [clear] off the scalp, and drawn in toward lying [clear] off it by [held], so a
 * lying lock follows the head.
 */
private fun keptOut(point: Vec3, scalp: Scalp, clear: Float, held: Float): Vec3 {
    val from = point - scalp.middle
    val distance = from.length
    if (distance == 0f) return point
    val least = scalp.reachToward(point) + clear
    if (distance < least) return scalp.middle + from * (least / distance)
    if (held <= 0f) return point
    return scalp.middle + from * ((distance + (least - distance) * held) / distance)
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
