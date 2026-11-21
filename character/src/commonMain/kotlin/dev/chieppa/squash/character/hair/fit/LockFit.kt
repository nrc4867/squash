package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.hair.GroupLocks
import dev.chieppa.squash.character.hair.LockSpec
import dev.chieppa.squash.character.hair.evenly
import dev.chieppa.squash.character.hair.guideOf
import dev.chieppa.squash.document.LONGEST_LOCK
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.SHORTEST_LOCK
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.ln
import kotlin.math.sqrt

/** How many places along a lock its fit reads. */
private const val SAMPLES = 14

/** How far out toward each side a lock is read, as a share of its half width: its edges lie under its neighbours' and soften as it turns. */
private const val SIDE_SHARE = 0.5f

/** How far along a lock, as a share of its length, its tip closes to a point. */
private const val TIP_CLOSE = 0.3f

/** How wide a lock is [t] of the way along it, as a share of its width: narrowing to [taper] of it, then closing to a point over its tip. */
internal fun lockShare(t: Float, taper: Float): Float = (1f - (1f - taper) * t) * ((1f - t) / TIP_CLOSE).coerceAtMost(1f)

/** How much a missed tip weighs, per the outline's height. */
private const val TIP_WEIGHT = 4f

/** How much a stray outside the outline weighs, on the whole and at its worst, and an edge off the outline between tips. */
private const val CONTAIN_WEIGHT = 20f
private const val WORST_WEIGHT = 10f
private const val EDGE_WEIGHT = 0.6f

/** How much moving a root weighs, per 20 degrees, so a root moves only where that helps. */
private const val ROOT_WEIGHT = 0.004f
private const val ROOT_UNIT = 20f

/**
 * How much bending a lock from its group's way weighs: per [RISE_UNIT] degrees of rise, per whole sweep, fall and lie, so a lock lifts
 * and turns only as far as its tip needs.
 */
private const val SHAPE_WEIGHT = 0.01f
private const val RISE_UNIT = 45f

/** How much growing or shortening a lock weighs, per e times its group's length: a lock unseen in depth stays the length its group gives it. */
private const val LENGTH_WEIGHT = 0.06f

/** How far a root may move round and up the head, in degrees. */
private const val MOST_AROUND = 30f
private const val MOST_UP = 25f

/** The costings a fit spends from each start. */
private const val BUDGET = 240

/** The rises a fit starts from beside the lock's own: lying, lifted and standing. */
private val START_RISES = floatArrayOf(35f, 70f)

/** How far outside the outline, as a share of its height, a lock may stray before it is shortened. */
private const val STRAY_TOLERANCE = 0.012f

/** The shortest a lock is shortened to, as a share of its length. */
private const val SHORTEST_KEPT = 0.3f

/** The halvings that find how far a straying lock is shortened. */
private const val SHORTEN_STEPS = 8

/** How far from its tip, as a share of the outline's height, a fitted lock's tip may land and still reach it. */
private const val REACHED = 0.06f

/** How near the longest a search may grow it, as a share, a fitted lock's length counts as at its longest. */
private const val AT_LONGEST = 0.98f

/** Where each value of a lock lies in the values a fit searches. */
private const val LENGTH = 0
private const val RISE = 1
private const val SWEEP = 2
private const val FALL = 3
private const val AROUND = 4
private const val UP = 5
private const val LIE = 6

/** The values a search over [x], laid out as [LockFitter] lays them, gives, with [width] and [tip]. */
private fun fittedOf(x: FloatArray, width: Float?, tip: LockTip?) = FittedLock(x[LENGTH], x[RISE], x[SWEEP], x[FALL], x[AROUND], x[UP], width, x[LIE], tip)

/**
 * Fits the lock at [index] of a group laid out as [laid], from [base], its override with nothing fitted, to the outline's [views]: each
 * guess grown along its guide as a build grows it, so what is fitted is what shows.
 */
internal class LockFitter(private val laid: GroupLocks, private val index: Int, private val base: LockOverride?, private val views: List<FitView>) {
    private val scalp = laid.scalp
    val start: LockSpec = laid.spec(index, base)
    val guide: List<Vec3> by lazy { guideOf(start, scalp) }

    /** The values that land the lock's tip on [target]'s and keep it inside the outline, searched by compass steps from a few starts. */
    fun fitTo(target: LockTarget): FittedLock {
        val grown = grownFor(target)
        val lower = floatArrayOf(maxOf(SHORTEST_LOCK, grown * 0.25f), -30f, -1f, 0f, -MOST_AROUND, -MOST_UP, LEAST_LIE)
        val upper = floatArrayOf(longestFor(grown), 90f, 1f, 1f, MOST_AROUND, MOST_UP, MOST_LIE)
        val steps = floatArrayOf(grown * 0.25f, 15f, 0.25f, 0.2f, 6f, 5f, 0.15f)
        val starts = listOf(start.rise to start.lie) + START_RISES.map { it to LEAST_LIE + (start.lie - LEAST_LIE) * (1f - it / 90f) }
        var best: Pair<FloatArray, Float>? = null
        for ((rise, lie) in starts.distinct()) {
            val from = floatArrayOf(grown, rise, start.sweep.coerceIn(-1f, 1f), start.fall, 0f, 0f, lie)
            val found = patternSearch(from, steps, lower, upper, BUDGET) { cost(it, target) }
            if (best == null || found.second < best.second) best = found
        }
        val x = checkNotNull(best).first
        return fittedOf(x, target.width, target.shape).rounded()
    }

    /** Why [fitted], what [fitTo] gave for [target], ends short of its tip, or null where its tip lands within [REACHED] of the view's height. */
    fun shortOf(fitted: FittedLock, target: LockTarget): UnreachedTip.Why? {
        val view = target.view
        val tip = view.head.flat(guideOf(laid.spec(index, fitted.over(base, index)), scalp).last())
        if (length(tip, target.tip) <= view.height * REACHED) return null
        val longest = longestFor(grownFor(target))
        return if ((fitted.length ?: start.length) >= longest * AT_LONGEST) UnreachedTip.Why.OUT_OF_REACH else UnreachedTip.Why.KEPT_INSIDE
    }

    /** The length a search for [target] starts from: the lock's own, scaled by how far its tip lands from its root against how far the target lies. */
    private fun grownFor(target: LockTarget): Float {
        val view = target.view
        val root = view.head.flat(start.root)
        val reached = (view.head.flat(guide.last()) - root).let { sqrt(it.x * it.x + it.y * it.y) }
        val wanted = length(target.tip, root)
        return (start.length * (wanted / reached.coerceAtLeast(1e-4f)).coerceIn(0.4f, 3f)).coerceIn(SHORTEST_LOCK, LONGEST_LOCK)
    }

    private fun longestFor(grown: Float): Float = minOf(LONGEST_LOCK, grown * 3f)

    /** The lock shortened, and laid down where that is not enough, until it strays no further outside the outline than it may; null where it stays inside. */
    fun keptIn(): FittedLock? {
        if (worstStray(start, guide) <= tolerance()) return null
        val lowered = if (start.rise > 0f) floatArrayOf(start.rise, 0f) else floatArrayOf(start.rise)
        for (rise in lowered) {
            val riseOverride = if (rise == start.rise) null else rise
            fun strays(share: Float): Float {
                val spec = laid.spec(index, FittedLock(length = start.length * share, rise = riseOverride).over(base, index))
                return worstStray(spec, guideOf(spec, scalp))
            }
            if (strays(SHORTEST_KEPT) > tolerance()) continue
            var inside = SHORTEST_KEPT
            var outside = 1f
            repeat(SHORTEN_STEPS) {
                val middle = (inside + outside) / 2f
                if (strays(middle) <= tolerance()) inside = middle else outside = middle
            }
            return FittedLock(length = start.length * inside, rise = riseOverride).rounded()
        }
        return FittedLock(length = start.length * SHORTEST_KEPT, rise = if (start.rise > 0f) 0f else null).rounded()
    }

    private fun tolerance(): Float = views.minOf { it.height } * STRAY_TOLERANCE

    private fun specOf(x: FloatArray, target: LockTarget): LockSpec = laid.spec(index, fittedOf(x, target.width, target.shape).over(base, index))

    private fun cost(x: FloatArray, target: LockTarget): Float {
        val spec = specOf(x, target)
        val guide = guideOf(spec, scalp)
        val view = target.view
        val h = view.height
        val tip = view.head.flat(guide.last())
        var c = TIP_WEIGHT * sq(length(tip, target.tip) / h)
        for (v in views) {
            val (mean, worst) = strays(spec, guide, v)
            c += (CONTAIN_WEIGHT * mean + WORST_WEIGHT * worst * worst) / (v.height * v.height)
        }
        c += EDGE_WEIGHT * edgeCost(spec, guide, target)
        c += ROOT_WEIGHT * (sq(x[AROUND] / ROOT_UNIT) + sq(x[UP] / ROOT_UNIT))
        c += SHAPE_WEIGHT * (sq((x[RISE] - start.rise) / RISE_UNIT) + sq(x[SWEEP] - start.sweep) + sq(x[FALL] - start.fall) + sq(x[LIE] - start.lie))
        c += LENGTH_WEIGHT * sq(ln(x[LENGTH] / start.length))
        return c
    }

    /** The mean square of how far the lock strays outside the outline in [view], and the furthest it strays. */
    private fun strays(spec: LockSpec, guide: List<Vec3>, view: FitView): Pair<Float, Float> {
        var sum = 0f
        var worst = 0f
        var n = 0
        sampled(spec, guide, view) { at, depth, _ ->
            val s = view.stray(at, depth)
            sum += s * s
            if (s > worst) worst = s
            n++
        }
        return (if (n == 0) 0f else sum / n) to worst
    }

    private fun worstStray(spec: LockSpec, guide: List<Vec3>): Float {
        var worst = 0f
        for (view in views) sampled(spec, guide, view) { at, depth, _ -> worst = maxOf(worst, view.stray(at, depth)) }
        return worst
    }

    /** How far, on the whole, the outline round the target's tip lies outside the lock as its view sees it. */
    private fun edgeCost(spec: LockSpec, guide: List<Vec3>, target: LockTarget): Float {
        if (target.edge.isEmpty()) return 0f
        val view = target.view
        val centre = ArrayList<Vec2>(SAMPLES)
        val half = ArrayList<Float>(SAMPLES)
        sampled(spec, guide, view, sides = false) { at, _, w ->
            centre += at
            half += w
        }
        var sum = 0f
        for (z in target.edge) {
            var nearest = Float.MAX_VALUE
            for (k in 0 until centre.size - 1) {
                val d = toSegment(z, centre[k], centre[k + 1]) - (half[k] + half[k + 1]) / 2f
                if (d < nearest) nearest = d
            }
            sum += sq(nearest.coerceAtLeast(0f) / view.height)
        }
        return sum / target.edge.size
    }

    /** Each place along the lock as [view] sees it, with its depth and half width, and with [sides] its two edges too. */
    private inline fun sampled(spec: LockSpec, guide: List<Vec3>, view: FitView, sides: Boolean = true, each: (Vec2, Float, Float) -> Unit) {
        val along = evenly(guide, SAMPLES)
        val flats = along.map(view.head::flat)
        for (k in along.indices) {
            val t = k.toFloat() / (along.size - 1)
            val half = view.head.drawn(spec.width) / 2f * (1f - (1f - spec.taper) * t) * ((1f - t) / TIP_CLOSE).coerceAtMost(1f)
            val depth = view.head.depth(along[k])
            val at = flats[k]
            each(at, depth, half)
            if (!sides || half <= 0f) continue
            val a = flats[(k - 1).coerceAtLeast(0)]
            val b = flats[(k + 1).coerceAtMost(flats.size - 1)]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val l = sqrt(dx * dx + dy * dy)
            if (l < 1e-6f) continue
            val n = Vec2(-dy / l * half * SIDE_SHARE, dx / l * half * SIDE_SHARE)
            each(at + n, depth, half)
            each(at - n, depth, half)
        }
    }
}

private fun sq(x: Float) = x * x
