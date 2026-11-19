package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.OutlineProfile
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.ViewSizing
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Stations along the solid at which its section is measured against the drawing. */
private const val STATIONS = 24

/** A section lower or narrower than this share of the solid's greatest is too near an end for a ratio; it takes its neighbours'. */
private const val END_SHARE = 0.25f

/** The least and the greatest a drawing scales a section by. */
private const val LEAST_SCALE = 0.25f
private const val MOST_SCALE = 4f

/** Passes of a 1-2-1 smoothing over the stations' ratios. */
private const val SMOOTHING = 2

/**
 * A solid fitted to a part's drawn Side and Top. Space is stretched along the axis so the solid's reach is the drawing's, and at each
 * point along it scaled about the section's middle toward the part's up and side, so the section's height and width there are the
 * drawing's and its middle lies on the drawn midlines: Side's toward its up, and Top's toward its side where Top moves it off the axis. The ratios are read at stations along the solid as it stands undrawn and eased
 * between them, held where the section narrows toward an end. A view not drawn takes the other's ratio, keeping the section's
 * proportions. The solid as it stands undrawn is read from its own silhouettes, in the planes the drawing is made in, so the outline
 * Start from template makes fits it back to itself. Points are in the part's own axes: x along, y up, z side.
 */
class FieldFit private constructor(
    private val naturalStart: Float,
    private val drawnStart: Float,
    /** How many times longer the drawing is than the solid. */
    val stretch: Float,
    private val xs: FloatArray,
    private val up: FloatArray,
    private val side: FloatArray,
    private val lift: FloatArray,
    private val shift: FloatArray,
    private val tall: FloatArray,
    private val wide: FloatArray,
) {
    /** The point along the solid as it stands undrawn that [x], along the fitted one, comes from. */
    fun naturalAlong(x: Float): Float = naturalStart + (x - drawnStart) / stretch

    /** Where [x], along the solid as it stands undrawn, lies once fitted. */
    fun drawnAlong(x: Float): Float = drawnStart + (x - naturalStart) * stretch

    /** The point of the undrawn solid that [p] of the fitted one comes from. */
    fun toNatural(p: Vec3): Vec3 {
        val x = naturalAlong(p.x)
        val i = indexOf(x)
        val f = shareOf(x, i)
        val u = lerp(up, i, f)
        val s = lerp(side, i, f)
        return Vec3(x, u + (p.y - u - lerp(lift, i, f)) / lerp(tall, i, f), s + (p.z - s - lerp(shift, i, f)) / lerp(wide, i, f))
    }

    /** Where [p] of the undrawn solid lies once fitted. */
    fun toDrawn(p: Vec3): Vec3 {
        val i = indexOf(p.x)
        val f = shareOf(p.x, i)
        val u = lerp(up, i, f)
        val s = lerp(side, i, f)
        return Vec3(drawnAlong(p.x), u + lerp(lift, i, f) + (p.y - u) * lerp(tall, i, f), s + lerp(shift, i, f) + (p.z - s) * lerp(wide, i, f))
    }

    /** What a distance read on the undrawn solid at [p] of the fitted one is multiplied by: the least stretch there, so it stays no further than the true distance. */
    fun shrink(p: Vec3): Float {
        val x = naturalAlong(p.x)
        val i = indexOf(x)
        val f = shareOf(x, i)
        return min(stretch, min(lerp(tall, i, f), lerp(wide, i, f)))
    }

    /**
     * [axis], a digit of the undrawn solid, carried onto the fitted one as a straight digit: its root and tip moved, its thickness scaled
     * by the mean of the height and width ratios at its root.
     */
    fun axis(axis: DigitAxis): DigitAxis {
        val root = toDrawn(axis.root)
        val reach = toDrawn(axis.at(1f)) - root
        val length = reach.length
        val i = indexOf(axis.root.x)
        val f = shareOf(axis.root.x, i)
        val thick = sqrt(lerp(tall, i, f) * lerp(wide, i, f))
        return DigitAxis(root, reach * (1f / length), length, axis.radius * thick, axis.beyond * length / axis.length)
    }

    /** The box holding what the box from [low] to [high] of the undrawn solid becomes once fitted. */
    fun bounds(low: Vec3, high: Vec3): Pair<Vec3, Vec3> {
        var least = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var most = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        val alongs = listOf(low.x, high.x) + xs.filter { it > low.x && it < high.x }
        for (x in alongs) for (y in listOf(low.y, high.y)) for (z in listOf(low.z, high.z)) {
            val p = toDrawn(Vec3(x, y, z))
            least = Vec3(min(least.x, p.x), min(least.y, p.y), min(least.z, p.z))
            most = Vec3(max(most.x, p.x), max(most.y, p.y), max(most.z, p.z))
        }
        return least to most
    }

    private fun indexOf(x: Float): Int {
        val step = xs[1] - xs[0]
        return ((x - xs[0]) / step).toInt().coerceIn(0, xs.size - 2)
    }

    private fun shareOf(x: Float, i: Int): Float = ((x - xs[i]) / (xs[i + 1] - xs[i])).coerceIn(0f, 1f)

    private fun lerp(values: FloatArray, i: Int, f: Float): Float = values[i] + (values[i + 1] - values[i]) * f

    companion object {
        /**
         * The solid whose undrawn silhouettes are [height] (Side: along, up) and [width] (Top: along, less side) fitted to [drawn]; null
         * where the solid or the drawing has no reach.
         */
        fun of(drawn: ViewSizing, height: OutlineProfile?, width: OutlineProfile?): FieldFit? {
            val ruler = (if (drawn.hasHeight) height else width) ?: height ?: width ?: return null
            val x0 = ruler.start
            val x1 = ruler.end
            val start = drawn.alongAt(0f)
            val end = drawn.alongAt(1f)
            if (x1 <= x0 || end <= start) return null
            val stretch = (end - start) / (x1 - x0)
            val xs = FloatArray(STATIONS) { x0 + (x1 - x0) * (it + 0.5f) / STATIONS }
            val us = xs.map { drawn.uAt(start + (it - x0) * stretch) }
            val heights = FloatArray(STATIONS) { height?.extentAt(xs[it]) ?: 0f }
            val widths = FloatArray(STATIONS) { width?.extentAt(xs[it]) ?: 0f }
            val tall = ratios(heights, us.map { drawn.heightAt(it) })
            val wide = ratios(widths, us.map { drawn.widthAt(it) })
            val ups = FloatArray(STATIONS) { height?.midAt(xs[it]) ?: 0f }
            val sides = FloatArray(STATIONS) { -(width?.midAt(xs[it]) ?: 0f) }
            val lifts = if (drawn.hasHeight && height != null) {
                val valid = sizable(heights, us.map { drawn.heightAt(it) })
                smoothed(held(FloatArray(STATIONS) { drawn.upAt(us[it]) - ups[it] }, valid))
            } else FloatArray(STATIONS)
            val shifts = if (us.any { drawn.sideAt(it) != 0f } && width != null) {
                val valid = sizable(widths, us.map { drawn.widthAt(it) })
                smoothed(held(FloatArray(STATIONS) { drawn.sideAt(us[it]) - sides[it] }, valid))
            } else FloatArray(STATIONS)
            val t = tall ?: wide ?: return null
            val w = wide ?: t
            return FieldFit(x0, start, stretch, xs, ups, sides, lifts, shifts, t, w)
        }

        /** Where a station's ratio says something: its section and the drawing there both well away from an end. */
        private fun sizable(natural: FloatArray, drawn: List<Float?>): BooleanArray {
            val most = natural.max()
            val drawnMost = drawn.maxOf { it ?: 0f }
            return BooleanArray(natural.size) { i ->
                val d = drawn[i]
                d != null && most > 0f && natural[i] >= most * END_SHARE && d >= drawnMost * END_SHARE
            }
        }

        /** The drawing's extent over the solid's at each station, held and smoothed; null where the view is not drawn. */
        private fun ratios(natural: FloatArray, drawn: List<Float?>): FloatArray? {
            if (drawn.all { it == null }) return null
            val valid = sizable(natural, drawn)
            if (valid.none { it }) return null
            val raw = FloatArray(natural.size) { i -> if (valid[i]) (drawn[i]!! / natural[i]).coerceIn(LEAST_SCALE, MOST_SCALE) else 1f }
            return smoothed(held(raw, valid))
        }

        /** [values] with each station not [valid] taking the nearest valid one's value. */
        private fun held(values: FloatArray, valid: BooleanArray): FloatArray {
            val kept = valid.indices.filter { valid[it] }
            if (kept.isEmpty()) return values
            return FloatArray(values.size) { i -> if (valid[i]) values[i] else values[kept.minBy { kotlin.math.abs(it - i) }] }
        }

        private fun smoothed(values: FloatArray): FloatArray {
            var out = values
            repeat(SMOOTHING) {
                val last = out.size - 1
                val before = out
                out = FloatArray(before.size) { i -> (before[max(i - 1, 0)] + 2f * before[i] + before[min(i + 1, last)]) / 4f }
            }
            return out
        }
    }
}
