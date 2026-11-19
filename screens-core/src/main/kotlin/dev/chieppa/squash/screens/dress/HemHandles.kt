package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.SurfaceCurve
import dev.chieppa.squash.geom.CubicBezier
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.fitFewCubics
import kotlin.math.ceil
import kotlin.math.floor

/** How far from a hem, in cells, its few-point fit may stray: half a cell, under what a cell's paint can show. */
const val HEM_FIT_CELLS = 0.5f

/** The fewest handles a closed hem takes, so dragging one bends it rather than moving it whole. */
const val LEAST_LOOP_HANDLES = 4

/** How many points a cubic of a hem is drawn back as, at least, and how many a cell of its length. */
private const val LEAST_SAMPLES = 8
private const val SAMPLES_PER_CELL = 3f

/**
 * A hem as few handles: the joined cubics [cubics] through it on [part]'s chart, on its run [patch], in cells (x round the part from
 * v 0, y along it from u 0), unwrapped round so they run on past a turn, closed when the hem closes. Its [handles] are the cubics' joins.
 */
class HemHandles(val part: dev.chieppa.squash.document.PartId, val patch: String, val cubics: List<CubicBezier>, val closed: Boolean) {
    /** The joins, each where it lies on the part; a closed hem's last join is its first. */
    val handles: List<Anchor> get() = joins().map(::anchorOf)

    private fun joins(): List<Vec2> = cubics.map { it.p0 } + if (closed) emptyList() else listOf(cubics.last().p3)

    /**
     * These handles with handle [index] at [to]: the join moves there, and the cubic handles either side of it move with it, so the hem
     * bends through the new place and the other handles stay.
     */
    fun moved(index: Int, to: Anchor): HemHandles {
        val from = joins()[index]
        val target = Vec2(nearestRound(to.v * CHART_COLUMNS, from.x), to.u * CHART_ROWS)
        val by = target - from
        val next = cubics.toMutableList()
        val after = if (index < cubics.size) index else -1
        val before = if (index > 0) index - 1 else if (closed) cubics.lastIndex else -1
        if (after >= 0) next[after] = next[after].copy(p0 = next[after].p0 + by, p1 = next[after].p1 + by)
        if (before >= 0) next[before] = next[before].copy(p2 = next[before].p2 + by, p3 = next[before].p3 + by)
        return HemHandles(part, patch, next, closed)
    }

    /** The hem these handles draw, as points on the part. */
    fun curve(): SurfaceCurve {
        val points = cubics.flatMap { cubic ->
            val length = (cubic.p1 - cubic.p0).length() + (cubic.p2 - cubic.p1).length() + (cubic.p3 - cubic.p2).length()
            val count = maxOf(LEAST_SAMPLES, ceil(length * SAMPLES_PER_CELL).toInt())
            List(count) { i -> cubic.pointAt(i.toFloat() / count) }
        } + if (closed) emptyList() else listOf(cubics.last().p3)
        return SurfaceCurve(points.map(::anchorOf), closed = closed)
    }

    private fun anchorOf(at: Vec2): Anchor {
        val v = at.x / CHART_COLUMNS
        return Anchor(part, patch, (at.y / CHART_ROWS).coerceIn(0f, 1f), v - floor(v))
    }

    companion object {
        /** [curve], one part's hem, fitted with as few joined cubics as stay within [HEM_FIT_CELLS] of it; null for fewer than two points. */
        fun of(curve: SurfaceCurve): HemHandles? {
            val first = curve.points.firstOrNull() ?: return null
            if (curve.points.size < 2) return null
            var x = first.v * CHART_COLUMNS
            val points = ArrayList<Vec2>()
            for (point in curve.points) {
                x = nearestRound(point.v * CHART_COLUMNS, x)
                points += Vec2(x, point.u * CHART_ROWS)
            }
            if (curve.closed) points += Vec2(nearestRound(first.v * CHART_COLUMNS, x), first.u * CHART_ROWS)
            val across = points[1] - points[points.lastIndex - 1]
            val through = if (curve.closed && across.length() > 0f) across * (1f / across.length()) else null
            val fitted = if (through != null) fitFewCubics(points, HEM_FIT_CELLS, through, through * -1f) else fitFewCubics(points, HEM_FIT_CELLS)
            var cubics = fitted
            while (curve.closed && cubics.size < LEAST_LOOP_HANDLES) cubics = cubics.flatMap { listOf(it.between(0f, 0.5f), it.between(0.5f, 1f)) }
            return HemHandles(first.part, first.patch, cubics, curve.closed)
        }
    }
}

/** [x], a place round a part in columns, moved by whole turns to lie nearest [near]. */
private fun nearestRound(x: Float, near: Float): Float = x - CHART_COLUMNS * floor((x - near) / CHART_COLUMNS + 0.5f)

private fun Vec2.length(): Float = kotlin.math.sqrt(x * x + y * y)
