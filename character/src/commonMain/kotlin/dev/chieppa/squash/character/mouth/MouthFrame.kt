package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/** How many places the lip line is laid at from corner to corner, and how many more past each corner. */
private const val ALONG = 48
private const val PAST = 14

/** How far up and down from the lip line the frame reaches, a share of the line's length, and in how many steps each way. */
private const val REACH = 0.6f
private const val STEPS = 24

/**
 * How much of the way square to the lip line each line of constant x starts down the face beside the body's down laid on the surface:
 * enough to carry it on where the surface faces down, little enough that a line curving up and down, as a cat's does, drops straight.
 */
private const val SQUARE_SHARE = 0.3f

/** The step of the chart its slopes are read across. */
private const val SLOPE_STEP = 1e-3f

/** How many steps a flat place is refined by from a laid place near it. */
private const val REFINE = 10

private const val TINY = 1e-7f

/**
 * A mouth's flat frame laid along its lip line on a part's patch: x along the line, in body units along the surface from its middle,
 * and y down the face from it, in body units along the surface, each line of constant x walked straight across the head from the line,
 * starting mostly toward the body's down. A muzzle's mouth, its line running back along both sides from the front, is laid out flat along
 * that line, so nothing folds round the muzzle's tip. The +x side is the one the normal crossed with the down points to. Places between
 * the laid ones are read between them.
 */
class MouthFrame private constructor(
    private val holder: PartId,
    private val patch: String,
    private val chart: PoleChart,
    private val grid: Array<Array<Vec2>>,
    private val firstX: Float,
    private val stepX: Float,
    private val stepY: Float,
) {
    private val columns = grid.size
    private val rows = grid[0].size

    /** The chart place at fractional column [i] and row [j], each held to the laid ones. */
    private fun chartAt(i: Float, j: Float): Vec2 {
        val ci = i.coerceIn(0f, (columns - 1).toFloat())
        val cj = j.coerceIn(0f, (rows - 1).toFloat())
        val i0 = floor(ci).toInt().coerceAtMost(columns - 2)
        val j0 = floor(cj).toInt().coerceAtMost(rows - 2)
        val s = ci - i0
        val t = cj - j0
        val a = grid[i0][j0] + (grid[i0 + 1][j0] - grid[i0][j0]) * s
        val b = grid[i0][j0 + 1] + (grid[i0 + 1][j0 + 1] - grid[i0][j0 + 1]) * s
        return a + (b - a) * t
    }

    private fun column(x: Float) = (x - firstX) / stepX

    private fun row(y: Float) = y / stepY + STEPS

    private fun chartOf(p: Vec2) = chartAt(column(p.x), row(p.y))

    fun onPatch(p: Vec2): SurfaceUv = chart.uvOf(chartOf(p))

    /**
     * [anchor]'s place in the frame: refined from the laid places nearest it in each column, the one nearest the lip line among those
     * that reach it, so a place the frame passes twice, as over a muzzle's tip, takes the pass nearer the line.
     */
    fun flat(anchor: Anchor): Vec2 {
        val target = chart.of(SurfaceUv(anchor.u, anchor.v))
        fun miss(i: Float, j: Float) = (chartAt(i, j) - target).let { hypot(it.x, it.y) }
        val seeds = (0 until columns).map { i -> i to (0 until rows).minBy { j -> miss(i.toFloat(), j.toFloat()) } }
            .sortedBy { (i, j) -> miss(i.toFloat(), j.toFloat()) }.take(SEEDS)
        val refined = seeds.map { (i, j) -> refine(i.toFloat(), j.toFloat(), target) }
        val reached = refined.filter { (i, j) -> miss(i, j) < REACHED }
        val (i, j) = reached.minByOrNull { (_, j) -> abs(j - STEPS) } ?: refined.minBy { (i, j) -> miss(i, j) }
        return Vec2(firstX + i * stepX, (j - STEPS) * stepY)
    }

    /** Column and row walked from [i] and [j] toward where the frame reads [target]. */
    private fun refine(i0: Float, j0: Float, target: Vec2): Pair<Float, Float> {
        var i = i0
        var j = j0
        repeat(REFINE) {
            val miss = chartAt(i, j) - target
            if (hypot(miss.x, miss.y) < TINY) return i to j
            val a = (chartAt(i + NUDGE, j) - chartAt(i - NUDGE, j)) * (1f / (2f * NUDGE))
            val b = (chartAt(i, j + NUDGE) - chartAt(i, j - NUDGE)) * (1f / (2f * NUDGE))
            val det = a.x * b.y - b.x * a.y
            if (abs(det) < TINY * TINY) return i to j
            i = (i - ((miss.x * b.y - b.x * miss.y) / det).coerceIn(-1f, 1f)).coerceIn(0f, (columns - 1).toFloat())
            j = (j - ((a.x * miss.y - miss.x * a.y) / det).coerceIn(-1f, 1f)).coerceIn(0f, (rows - 1).toFloat())
        }
        return i to j
    }

    companion object {
        private val BODY_DOWN = Vec3(0f, -1f, 0f)

        /** How many columns' nearest laid places a place is refined from. */
        private const val SEEDS = 8

        /** How near, in the chart, a refined place must read its target to count as reaching it. */
        private const val REACHED = 1e-4f

        /** The share of a laid step a slope between laid places is read across. */
        private const val NUDGE = 0.25f

        /**
         * The frame along [marks], the lip line's marks in any order and either way along, on [patch] of [holder]'s [surface]; null where
         * the line is too short or the surface there is degenerate.
         */
        fun of(holder: PartId, patch: String, marks: List<List<Anchor>>, surface: PartSurface): MouthFrame? {
            val places = marks.flatten()
            if (places.size < 2) return null
            val chart = PoleChart.about(places.map { it.u }.average().toFloat())
            val reader = SurfaceReader(holder, patch, surface, chart)
            val chain = chained(marks.filter { it.isNotEmpty() }.map { mark -> curveThrough(mark.map { chart.of(SurfaceUv(it.u, it.v)) }) })
            if (chain.size < 2) return null
            val line = reader.laidAlong(chain) ?: return null
            val stepY = REACH * line.length / STEPS
            val grid = Array(line.places.size) { i ->
                val down = line.downs[i]
                val up = reader.walk(line.places[i], down * -1f, stepY)
                val below = reader.walk(line.places[i], down, stepY)
                Array(2 * STEPS + 1) { j -> if (j < STEPS) up[STEPS - j] else below[j - STEPS] }
            }
            return MouthFrame(holder, patch, chart, grid, line.firstX, line.step, stepY)
        }

        /** [curves] joined end to end into one, each taken whichever way round brings its end nearest the chain's. */
        private fun chained(curves: List<List<Vec2>>): List<Vec2> {
            if (curves.isEmpty()) return emptyList()
            val left = curves.drop(1).toMutableList()
            var chain = curves.first()
            fun gap(a: Vec2, b: Vec2) = hypot(a.x - b.x, a.y - b.y)
            while (left.isNotEmpty()) {
                var bestIndex = 0
                var bestGap = Float.MAX_VALUE
                var joined = chain
                for ((k, curve) in left.withIndex()) {
                    val options = listOf(
                        gap(chain.last(), curve.first()) to { chain + curve },
                        gap(chain.last(), curve.last()) to { chain + curve.reversed() },
                        gap(chain.first(), curve.last()) to { curve + chain },
                        gap(chain.first(), curve.first()) to { curve.reversed() + chain },
                    )
                    val (g, join) = options.minBy { it.first }
                    if (g < bestGap) {
                        bestGap = g
                        bestIndex = k
                        joined = join()
                    }
                }
                chain = joined
                left.removeAt(bestIndex)
            }
            return chain
        }
    }

    /** The lip line laid at even steps along it: its chart places, the down square to it at each, the x of the first and the step. */
    private class LaidLine(val places: List<Vec2>, val downs: List<Vec3>, val firstX: Float, val step: Float, val length: Float)

    /** Reads [surface] through [chart] on [patch] of [holder]. */
    private class SurfaceReader(private val holder: PartId, private val patch: String, private val surface: PartSurface, private val chart: PoleChart) {
        fun at(c: Vec2): Vec3? = chart.uvOf(c).let { surface.pointAt(Anchor(holder, patch, it.u, it.v))?.position }

        /** The surface's slopes along the chart's two directions at [c], and its point there. */
        fun slopes(c: Vec2): Triple<Vec3, Vec3, Vec3>? {
            val p = at(c) ?: return null
            val a = (at(Vec2(c.x + SLOPE_STEP, c.y)) ?: return null) - (at(Vec2(c.x - SLOPE_STEP, c.y)) ?: return null)
            val b = (at(Vec2(c.x, c.y + SLOPE_STEP)) ?: return null) - (at(Vec2(c.x, c.y - SLOPE_STEP)) ?: return null)
            return Triple(p, a * (1f / (2f * SLOPE_STEP)), b * (1f / (2f * SLOPE_STEP)))
        }

        /** The outward normal at [c]: the slopes' cross, turned to the surface's own normal. */
        fun normal(c: Vec2): Vec3? {
            val (_, a, b) = slopes(c) ?: return null
            val n = (a cross b).takeIf { it.length > TINY }?.normalized() ?: return null
            val own = chart.uvOf(c).let { surface.pointAt(Anchor(holder, patch, it.u, it.v))?.normal } ?: return n
            return if ((n dot own) < 0f) n * -1f else n
        }

        /** [chain] laid at [ALONG] even steps along its length on the surface, and [PAST] more past each end along its end's way, its middle at x 0. */
        fun laidAlong(chain: List<Vec2>): LaidLine? {
            var points = chain.map { at(it) ?: return null }
            var places = chain
            val middle = places.size / 2
            val midNormal = normal(places[middle]) ?: return null
            val tangent = points[minOf(middle + 1, points.lastIndex)] - points[maxOf(middle - 1, 0)]
            val down = (BODY_DOWN - midNormal * (BODY_DOWN dot midNormal)).takeIf { it.length > TINY } ?: return null
            if ((tangent dot (midNormal cross down)) < 0f) {
                places = places.reversed()
                points = points.reversed()
            }
            val lengths = FloatArray(points.size)
            for (k in 1 until points.size) lengths[k] = lengths[k - 1] + (points[k] - points[k - 1]).length
            val length = lengths.last()
            if (length < TINY) return null
            val step = length / (ALONG - 1)
            fun placeAt(s: Float): Vec2 {
                if (s <= 0f) return extended(places[0], places[1], lengths[1], s)
                if (s >= length) return extended(places.last(), places[places.size - 2], length - lengths[lengths.size - 2], length - s)
                val k = (1 until lengths.size).first { lengths[it] >= s }
                val span = lengths[k] - lengths[k - 1]
                val t = if (span < TINY) 0f else (s - lengths[k - 1]) / span
                return places[k - 1] + (places[k] - places[k - 1]) * t
            }
            val laid = (-PAST until ALONG + PAST).map { placeAt(it * step) }
            val found = laid.indices.map { k ->
                val n = normal(laid[k]) ?: midNormal
                val ahead = at(laid[minOf(k + 1, laid.lastIndex)])
                val behind = at(laid[maxOf(k - 1, 0)])
                val along = if (ahead == null || behind == null) null else (ahead - behind).takeIf { it.length > TINY }?.normalized()
                val square = along?.let { (it cross n).takeIf { d -> d.length > TINY }?.normalized() }
                val fall = BODY_DOWN - n * (BODY_DOWN dot n)
                (fall + (square ?: Vec3.ZERO) * SQUARE_SHARE).takeIf { it.length > TINY }?.normalized()
            }
            if (found.all { it == null }) return null
            val downs = found.indices.map { k -> found[k] ?: found.indices.filter { found[it] != null }.minBy { abs(it - k) }.let { checkNotNull(found[it]) } }
            return LaidLine(laid, downs, -PAST * step - length / 2f, step, length)
        }

        /** The chart place [s] past [end], s negative, along the way from [inner] to [end] that [gap] of surface spans. */
        private fun extended(end: Vec2, inner: Vec2, gap: Float, s: Float): Vec2 =
            if (gap < TINY) end else end + (end - inner) * (-s / gap)

        /** The places [STEPS] steps of [step] apart walking across the surface from [start] along [way], straight on the surface from there; [start] first. */
        fun walk(start: Vec2, way: Vec3, step: Float): List<Vec2> {
            val out = ArrayList<Vec2>(STEPS + 1)
            out += start
            var c = start
            var heading = way
            repeat(STEPS) {
                val (p, a, b) = slopes(c) ?: return padded(out)
                val n = (a cross b).takeIf { it.length > TINY }?.normalized() ?: return padded(out)
                val w = (heading - n * (heading dot n)).takeIf { it.length > TINY }?.normalized() ?: return padded(out)
                val aa = a dot a
                val ab = a dot b
                val bb = b dot b
                val det = aa * bb - ab * ab
                if (abs(det) < TINY * TINY) return padded(out)
                val ra = (a dot w) * step
                val rb = (b dot w) * step
                val next = c + Vec2((bb * ra - ab * rb) / det, (aa * rb - ab * ra) / det)
                val moved = (at(next) ?: return padded(out)) - p
                if (moved.length > TINY) heading = moved.normalized()
                c = next
                out += c
            }
            return out
        }

        /** [out] filled to its full count by carrying on as its last step went. */
        private fun padded(out: ArrayList<Vec2>): List<Vec2> {
            while (out.size < STEPS + 1) {
                val last = out.last()
                val before = if (out.size >= 2) out[out.size - 2] else last
                out += last + (last - before)
            }
            return out
        }
    }
}
