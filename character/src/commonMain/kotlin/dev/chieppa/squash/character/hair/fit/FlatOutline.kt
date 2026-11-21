package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.document.HairOutlineView
import dev.chieppa.squash.geom.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** How far either side of a point its turn is read, as a share of the outline's length round. */
private const val TURN_REACH = 0.02f

/** The least turn, in degrees, that makes a point standing out of the region a tip, and one cutting into it a valley. */
private const val TIP_TURN = 50f
private const val VALLEY_TURN = 35f

private const val RADIANS_TO_DEGREES = (180.0 / PI).toFloat()

/**
 * A hair outline's view as a closed curve in its flat frame (x to the view's right, y up, in the head's drawn units), with the [tips] of
 * its spikes and flicks and the [valleys] between them, by index into [points].
 */
class FlatOutline(val points: List<Vec2>, val tips: List<Int>, val valleys: List<Int>) {
    /** Its length round. */
    val perimeter: Float = points.indices.sumOf { length(points[it], points[(it + 1) % points.size]).toDouble() }.toFloat()

    /** Its lowest and highest corners. */
    val low: Vec2 = Vec2(points.minOf { it.x }, points.minOf { it.y })
    val high: Vec2 = Vec2(points.maxOf { it.x }, points.maxOf { it.y })

    /** Whether [p] lies inside it. */
    fun contains(p: Vec2): Boolean {
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    /** Points spaced [step] apart round it, its first among them. */
    fun resampled(step: Float): List<Vec2> = walk(0, points.size, step)

    /** Points spaced [step] apart along it from point [from] forward to point [to], both among them. */
    fun between(from: Int, to: Int, step: Float): List<Vec2> {
        val span = ((to - from) % points.size + points.size) % points.size
        return walk(from, span, step) + points[to]
    }

    /** How far along it, forward, point [to] lies from point [from]. */
    fun arc(from: Int, to: Int): Float {
        var sum = 0f
        var i = from
        while (i != to) {
            val next = (i + 1) % points.size
            sum += length(points[i], points[next])
            i = next
        }
        return sum
    }

    private fun walk(from: Int, steps: Int, step: Float): List<Vec2> {
        val out = ArrayList<Vec2>()
        var carried = 0f
        for (k in 0 until steps) {
            val a = points[(from + k) % points.size]
            val b = points[(from + k + 1) % points.size]
            val span = length(a, b)
            var at = carried
            while (at < span) {
                val t = at / span
                out += Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                at += step
            }
            carried = at - span
        }
        if (out.isEmpty()) out += points[from % points.size]
        return out
    }

    companion object {
        /** [view] in its flat frame, its tips and valleys found from its shape where it does not give them. */
        fun of(view: HairOutlineView): FlatOutline {
            val points = view.points.map { Vec2(it.x, it.y) }
            val found by lazy { features(points) }
            return FlatOutline(points, view.tips ?: found.first, view.valleys ?: found.second)
        }

        /**
         * The tips and valleys of the closed curve [points]: each point where the curve, read a short way either side of it, turns at
         * least [TIP_TURN] degrees about the region (a spike's or a flick's point) or [VALLEY_TURN] degrees into it (a notch), the
         * sharpest within that reach.
         */
        fun features(points: List<Vec2>): Pair<List<Int>, List<Int>> {
            val n = points.size
            if (n < 3) return emptyList<Int>() to emptyList()
            val lengths = FloatArray(n + 1)
            for (i in 0 until n) lengths[i + 1] = lengths[i] + length(points[i], points[(i + 1) % n])
            val total = lengths[n]
            val reach = total * TURN_REACH
            val orientation = if (signedArea(points) >= 0f) 1f else -1f
            val turns = FloatArray(n) { i ->
                val back = pointAt(points, lengths, total, lengths[i] - reach, i, -1)
                val ahead = pointAt(points, lengths, total, lengths[i] + reach, i, 1)
                val p = points[i]
                val ax = p.x - back.x
                val ay = p.y - back.y
                val bx = ahead.x - p.x
                val by = ahead.y - p.y
                atan2(ax * by - ay * bx, ax * bx + ay * by) * RADIANS_TO_DEGREES * orientation
            }
            fun strongest(i: Int, sign: Float): Boolean {
                val mine = turns[i] * sign
                for (j in 0 until n) {
                    if (j == i) continue
                    val gap = abs(lengths[j] - lengths[i]).let { minOf(it, total - it) }
                    if (gap > reach) continue
                    val theirs = turns[j] * sign
                    if (theirs > mine || (theirs == mine && j < i)) return false
                }
                return true
            }
            val tips = (0 until n).filter { turns[it] >= TIP_TURN && strongest(it, 1f) }
            val valleys = (0 until n).filter { -turns[it] >= VALLEY_TURN && strongest(it, -1f) }
            return tips to valleys
        }

        /** The point [at] along the curve, or the next point the way [direction] walks where that lands on point [from]. */
        private fun pointAt(points: List<Vec2>, lengths: FloatArray, total: Float, at: Float, from: Int, direction: Int): Vec2 {
            val n = points.size
            val neighbour = points[((from + direction) % n + n) % n]
            val wrapped = ((at % total) + total) % total
            var k = 0
            while (k < n - 1 && lengths[k + 1] < wrapped) k++
            val span = lengths[k + 1] - lengths[k]
            val t = if (span > 0f) (wrapped - lengths[k]) / span else 0f
            val a = points[k]
            val b = points[(k + 1) % n]
            val p = Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            return if (length(points[from], p) < total * 1e-5f) neighbour else p
        }

        /** Twice the area [points] closes, positive when it runs anticlockwise with y up. */
        fun signedArea(points: List<Vec2>): Float {
            var sum = 0f
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]
                sum += a.x * b.y - b.x * a.y
            }
            return sum
        }
    }
}

/** The distance between [a] and [b]. */
internal fun length(a: Vec2, b: Vec2): Float = hypot(b.x - a.x, b.y - a.y)

/** The distance from [p] to the segment from [a] to [b]. */
internal fun toSegment(p: Vec2, a: Vec2, b: Vec2): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val l = dx * dx + dy * dy
    val t = if (l == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / l).coerceIn(0f, 1f)
    val ex = p.x - a.x - t * dx
    val ey = p.y - a.y - t * dy
    return sqrt(ex * ex + ey * ey)
}
