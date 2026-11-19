package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

/** How far past a patch's end rings a border closing past one runs, in u. */
private const val PAST_RING = 4f

/** What a whole patch reads as: far inside. */
private const val FAR = 1e6f

/** How far, in u and v, a curved border's straight edges lie from its curve at most. */
private const val CURVE_TOLERANCE = 1e-4f

/** A unit of u and of v read as one long, for flattening a curve in u and v alone. */
private val UNIT_RATES = SurfaceRates(1f, 1f)

/**
 * [shape], a color shape in a patch's own u and v, read as a signed distance at any (u, v): positive inside, negative outside, measured
 * with a stretch of u counting [uScale] times and of v [vScale] times its length, so with each set to how long a unit of it is on the
 * surface there the distance is in body units. v wraps once round when [closedV]. A border is filled by its winding, a border that goes
 * once round the patch holds the side toward the last ring, and one closing past an end ring holds what lies toward that ring.
 */
class UvShapeDistance(private val shape: ColorShape, private val closedV: Boolean) {
    /** The border's points with v carried on across the seam, and whether each segment counts as an edge of the shape. */
    private val us: FloatArray
    private val vs: FloatArray
    private val edges: BooleanArray

    init {
        val outline = when (shape) {
            is ColorShape.Border -> borderOutline(shape)
            is ColorShape.Brush -> unwrapped(shape.points)
            else -> emptyList()
        }
        us = FloatArray(outline.size) { outline[it].first.u }
        vs = FloatArray(outline.size) { outline[it].first.v }
        edges = BooleanArray(outline.size) { outline[it].second }
    }

    /** The signed distance at ([u], [v]). */
    fun at(u: Float, v: Float, uScale: Float, vScale: Float): Float = when (shape) {
        ColorShape.WholePatch -> FAR
        is ColorShape.Rectangle -> rectangle(shape, u, v, uScale, vScale)
        is ColorShape.Border -> border(u, v, uScale, vScale)
        is ColorShape.Brush -> shape.radius - nearest(u, v, uScale, vScale, closed = false)
        is ColorShape.Level -> shape.level.at(u, v, uScale, vScale)
    }

    private fun rectangle(r: ColorShape.Rectangle, u: Float, v: Float, uScale: Float, vScale: Float): Float {
        val du = maxOf(r.uStart - u, u - r.uEnd) * uScale
        val span = r.vEnd - r.vStart
        val dv = if (closedV && span >= 1f) {
            -FAR
        } else {
            val middle = (r.vStart + r.vEnd) / 2f
            val off = if (closedV) wrapped(v - middle) else v - middle
            (abs(off) - span / 2f) * vScale
        }
        val outside = sqrt(maxOf(du, 0f).let { it * it } + maxOf(dv, 0f).let { it * it })
        return if (outside > 0f) -outside else -maxOf(du, dv)
    }

    private fun border(u: Float, v: Float, uScale: Float, vScale: Float): Float {
        if (us.size < 3) return -FAR
        var inside = false
        for (copy in copies()) if (winding(u, v + copy) != 0) inside = true
        val distance = nearest(u, v, uScale, vScale, closed = true)
        return if (inside) distance else -distance
    }

    /** The v shifts a point is tried at: one period either way on a closed patch. */
    private fun copies(): FloatArray = if (closedV) floatArrayOf(-1f, 0f, 1f) else floatArrayOf(0f)

    /** How many times the outline winds round ([u], [v]). */
    private fun winding(u: Float, v: Float): Int {
        var count = 0
        val n = us.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ui = us[i]
            val vi = vs[i]
            val uj = us[j]
            val vj = vs[j]
            if (vi <= v) {
                if (vj > v && cross(ui, vi, uj, vj, u, v) > 0f) count++
            } else if (vj <= v && cross(ui, vi, uj, vj, u, v) < 0f) {
                count--
            }
        }
        return count
    }

    private fun cross(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float = (bx - ax) * (py - ay) - (px - ax) * (by - ay)

    /** The least distance from ([u], [v]) to the shape's edges, every copy round a closed patch tried. */
    private fun nearest(u: Float, v: Float, uScale: Float, vScale: Float, closed: Boolean): Float {
        val n = us.size
        if (n == 0) return FAR
        var best = Float.MAX_VALUE
        val last = if (closed) n else n - 1
        for (copy in copies()) {
            val pv = v + copy
            if (n == 1) best = minOf(best, hypot((u - us[0]) * uScale, (pv - vs[0]) * vScale))
            for (i in 0 until last) {
                if (!edges[i]) continue
                val j = (i + 1) % n
                best = minOf(best, segment(u * uScale, pv * vScale, us[i] * uScale, vs[i] * vScale, us[j] * uScale, vs[j] * vScale))
            }
        }
        return best
    }

    /** The border as one closed outline in unwrapped v, each segment flagged whether it is the border's own or a closing past the patch. */
    private fun borderOutline(border: ColorShape.Border): List<Pair<SurfaceUv, Boolean>> {
        val points = if (border.curved) curve(border) else unwrapped(border.points)
        if (points.isEmpty()) return points
        val first = points.first().first
        val last = points.last().first
        return when (border.closing) {
            BorderClosing.ON_ITSELF -> {
                val turns = if (closedV) round(last.v + wrapped(first.v - last.v) - first.v) else 0f
                if (turns == 0f) {
                    points
                } else {
                    val far = 1f + PAST_RING
                    points + (SurfaceUv(first.u, first.v + turns) to false) + (SurfaceUv(far, first.v + turns) to false) + (SurfaceUv(far, first.v) to false)
                }
            }
            BorderClosing.PAST_FIRST_RING -> points.markLast() + (SurfaceUv(-PAST_RING, last.v) to false) + (SurfaceUv(-PAST_RING, first.v) to false)
            BorderClosing.PAST_LAST_RING -> points.markLast() + (SurfaceUv(1f + PAST_RING, last.v) to false) + (SurfaceUv(1f + PAST_RING, first.v) to false)
        }
    }

    /** [points] with v carried across the seam so no step jumps by more than half round; each segment the border's own. */
    private fun unwrapped(points: List<SurfaceUv>): List<Pair<SurfaceUv, Boolean>> {
        val out = ArrayList<Pair<SurfaceUv, Boolean>>(points.size)
        var v = 0f
        points.forEachIndexed { i, p ->
            v = if (i == 0 || !closedV) p.v else v + wrapped(p.v - points[i - 1].v)
            out += SurfaceUv(p.u, v) to true
        }
        return out
    }

    /**
     * [border]'s curve as straight edges, its v carried across the seam as [unwrapped] carries it, without the point that would close it: a
     * closed curve ends just short of its first point, one closing past a ring at its last point.
     */
    private fun curve(border: ColorShape.Border): List<Pair<SurfaceUv, Boolean>> {
        val through = unwrapped(border.points).map { UnwrappedUv(it.first.u.toDouble(), it.first.v.toDouble()) }
        if (through.size < 3) return unwrapped(border.points)
        val closes = border.closing == BorderClosing.ON_ITSELF
        val turn = if (closes && closedV) round(through.last().v + wrapped((through.first().v - through.last().v).toFloat()) - through.first().v) else 0.0
        val flat = flattenedThrough(through, { _, _ -> UNIT_RATES }, CURVE_TOLERANCE, if (closes) turn else null, border.corners)
        val kept = if (closes) flat.dropLast(1) else flat
        return kept.map { SurfaceUv(it.u.toFloat(), it.v.toFloat()) to true }
    }

    /** The outline's last point, whose segment back to the first would close it, leads into the closing past a ring instead. */
    private fun List<Pair<SurfaceUv, Boolean>>.markLast(): List<Pair<SurfaceUv, Boolean>> = mapIndexed { i, p -> if (i == lastIndex) p.first to false else p }

    private fun wrapped(dv: Float): Float = dv - round(dv)

    private fun hypot(x: Float, y: Float): Float = sqrt(x * x + y * y)

    private fun segment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val length = dx * dx + dy * dy
        val t = if (length > 0f) (((px - ax) * dx + (py - ay) * dy) / length).coerceIn(0f, 1f) else 0f
        return hypot(px - ax - t * dx, py - ay - t * dy)
    }
}
