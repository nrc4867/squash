package dev.chieppa.squash.geom

import kotlin.math.abs

/** How far the fade may lie from a straight run along an edge before the edge is split, as a share of its opacity from core to edge. */
private const val FADE_FLATNESS = 1f / 128f

/** The shortest edge split, as a share of a fade's width. */
private const val SHORTEST_IN_FADE = 1f / 256f

/** The narrowest fade that is cut as one, in flattening tolerances; a narrower one is a hard edge until a closer zoom. */
private const val NARROWEST_FADE = 2f

private const val MOST_SPLITS = 28

private const val EDGE_LEVEL = 0f
private const val CORE_LEVEL = 1f

/**
 * A soft brush cut: each of the mesh's triangles the brush reaches, split along its own edges until the fade runs straight across every
 * piece, then cut where the fade starts and ends. Whether an edge splits depends on the edge alone, so both triangles sharing it split it
 * alike and meet vertex to vertex, and each place is covered once. Each vertex holds its opacity, exactly 1 and 0 along the core's
 * contour and the edge's. Edges stay long in the core and wherever the fade runs straight, however narrow it is.
 */
internal fun cutSoftBrush(brush: ColorShape.Brush, grid: PatchGrid, tolerance: Float, checkCancelled: () -> Unit): PatchCut {
    val field = FadeField(brush, grid, tolerance)
    if (field.width < NARROWEST_FADE * tolerance) {
        val hard = cutShape(brush.copy(softness = 0f), grid, tolerance, checkCancelled)
        return PatchCut(hard.patch, hard.mesh, hard.us, hard.vs, FloatArray(hard.us.size) { 1f })
    }
    return FadeTessellation(field, grid).cut(checkCancelled)
}

/** One place on the grid: its position and the fade's level there, which runs on past 1 inside the core and below 0 outside the edge. */
private class FadeSample(val position: Vec3, val distance: Float, val level: Float)

/** A corner of a triangle in grid coordinates as its cell holds them, so a seam's line of points stays at the cell's own v. */
private class FadeCorner(val u: Double, val v: Double, val sample: FadeSample) {
    val level: Float get() = sample.level
}

private class FadeTessellation(private val field: FadeField, private val grid: PatchGrid) {
    private val out = CutOutput(grid)
    private var alphas = FloatArray(INITIAL_VERTICES)
    private var alphaCount = 0
    private val samples = HashMap<Pair<Double, Double>, FadeSample>()
    private val shortest = field.width * SHORTEST_IN_FADE

    fun cut(checkCancelled: () -> Unit): PatchCut {
        for (column in 0 until grid.columns) {
            checkCancelled()
            for (ring in 0 until grid.rings - 1) {
                val c = column.toDouble()
                val r = ring.toDouble()
                val near = corner(r, c)
                val across = corner(r + 1.0, c + 1.0)
                start(near, corner(r + 1.0, c), across)
                start(near, across, corner(r, c + 1.0))
            }
        }
        val built = out.built()
        return PatchCut(built.patch, built.mesh, built.us, built.vs, alphas.copyOf(alphaCount))
    }

    private fun corner(u: Double, v: Double): FadeCorner {
        val around = if (grid.closed && v >= grid.columns) v - grid.columns else v
        val sample = samples.getOrPut(u to around) {
            val position = grid.positionOn(u, around)
            val distance = field.distanceAt(position, around)
            FadeSample(position, distance, field.levelAt(distance))
        }
        return FadeCorner(u, v, sample)
    }

    private fun start(a: FadeCorner, b: FadeCorner, c: FadeCorner) {
        val span = maxOf(length(a, b), length(b, c), length(c, a))
        if (field.isWhollyOutside(minOf(a.sample.distance, b.sample.distance, c.sample.distance), span)) return
        split(a, b, c, 0)
    }

    private fun length(a: FadeCorner, b: FadeCorner) = (a.sample.position - b.sample.position).length

    /** The middle of the edge when the edge splits. Depends only on the edge, whichever way round it is given. */
    private fun middleOf(a: FadeCorner, b: FadeCorner): FadeCorner? {
        val length = length(a, b)
        if (length <= shortest) return null
        val middle = corner((a.u + b.u) / 2.0, (a.v + b.v) / 2.0)
        val distance = middle.sample.distance
        if (field.isWhollyOutside(distance, length) || field.isWhollyInCore(distance, length)) return null
        return if (abs(middle.level - (a.level + b.level) / 2f) > FADE_FLATNESS) middle else null
    }

    private fun split(a: FadeCorner, b: FadeCorner, c: FadeCorner, depth: Int) {
        val ab = if (depth < MOST_SPLITS) middleOf(a, b) else null
        val bc = if (depth < MOST_SPLITS) middleOf(b, c) else null
        val ca = if (depth < MOST_SPLITS) middleOf(c, a) else null
        val next = depth + 1
        when {
            ab != null && bc != null && ca != null -> {
                split(a, ab, ca, next)
                split(ab, b, bc, next)
                split(ca, bc, c, next)
                split(ab, bc, ca, next)
            }
            ab != null && bc != null -> splitTwo(a, ab, b, bc, c, next)
            bc != null && ca != null -> splitTwo(b, bc, c, ca, a, next)
            ca != null && ab != null -> splitTwo(c, ca, a, ab, b, next)
            ab != null -> {
                split(a, ab, c, next)
                split(ab, b, c, next)
            }
            bc != null -> {
                split(b, bc, a, next)
                split(bc, c, a, next)
            }
            ca != null -> {
                split(c, ca, b, next)
                split(ca, a, b, next)
            }
            else -> emit(a, b, c)
        }
    }

    /** A triangle [from], [to], [last] with its first two edges split at [first] and [second]: the corner at [to] comes off, and what is left divides along its shorter diagonal. */
    private fun splitTwo(from: FadeCorner, first: FadeCorner, to: FadeCorner, second: FadeCorner, last: FadeCorner, depth: Int) {
        split(first, to, second, depth)
        if (length(from, second) <= length(first, last)) {
            split(from, first, second, depth)
            split(from, second, last, depth)
        } else {
            split(from, first, last, depth)
            split(first, second, last, depth)
        }
    }

    /** Draws what of the triangle lies inside the edge, cut along the core's contour, since opacity bends there. */
    private fun emit(a: FadeCorner, b: FadeCorner, c: FadeCorner) {
        val inside = clipped(listOf(a, b, c), EDGE_LEVEL, keepAbove = true)
        fan(clipped(inside, CORE_LEVEL, keepAbove = false))
        fan(clipped(inside, CORE_LEVEL, keepAbove = true))
    }

    /** The convex [corners] on one side of [level], with new corners where an edge crosses it. A corner exactly on it is on both sides. */
    private fun clipped(corners: List<FadeCorner>, level: Float, keepAbove: Boolean): List<FadeCorner> {
        if (corners.isEmpty()) return corners
        if (corners.all { (it.level >= level) == keepAbove && it.level != level }) return corners
        val kept = ArrayList<FadeCorner>(corners.size + 1)
        for (i in corners.indices) {
            val p = corners[i]
            val q = corners[(i + 1) % corners.size]
            val pIn = p.level == level || (p.level > level) == keepAbove
            val qIn = q.level == level || (q.level > level) == keepAbove
            if (pIn) kept += p
            if (pIn != qIn && p.level != level && q.level != level) kept += crossing(p, q, level)
        }
        val strictly = kept.any { it.level != level }
        return if (kept.size >= 3 && strictly) kept else emptyList()
    }

    /** Where the edge crosses [level]. Reads the edge from the same end from either triangle, so both find the same place. */
    private fun crossing(p: FadeCorner, q: FadeCorner, level: Float): FadeCorner {
        val pFirst = p.u < q.u || (p.u == q.u && p.v < q.v)
        val low = if (pFirst) p else q
        val high = if (pFirst) q else p
        val t = ((level - low.level) / (high.level - low.level)).toDouble()
        val u = low.u + (high.u - low.u) * t
        val v = low.v + (high.v - low.v) * t
        return FadeCorner(u, v, FadeSample(grid.positionOn(u, v), 0f, level))
    }

    private fun fan(corners: List<FadeCorner>) {
        for (i in 1 until corners.lastIndex) out.triangle(placed(corners[0]), placed(corners[i]), placed(corners[i + 1]))
    }

    private fun placed(corner: FadeCorner): Int {
        val vertex = out.vertex(corner.u, corner.v)
        if (vertex == alphaCount) {
            if (alphaCount == alphas.size) alphas = alphas.copyOf(alphas.size * 2)
            alphas[alphaCount++] = corner.level.coerceIn(EDGE_LEVEL, CORE_LEVEL)
        }
        return vertex
    }

    private companion object {
        const val INITIAL_VERTICES = 256
    }
}
