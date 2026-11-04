package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private const val MIN_RATE = 1e-5f
private const val HALF_CELL = 0.5

/** How far a surface moves per unit of [alongU] and [aroundV]. */
data class SurfaceRates(val alongU: Float, val aroundV: Float)

/**
 * A patch's grid as its part's mesh holds it, addressed in grid coordinates: a whole number is a ring or a line of points
 * around v, and a cell is the unit square between. Inside a cell a point lies on one of the mesh's two triangles, split along
 * the cell's diagonal, so what this gives is exactly the drawn surface with its attributes interpolated as the GPU does. Grid
 * coordinates past the rings hold at the end ring; v wraps on a closed patch and holds at the edge of an open one. A cell against a
 * pole is one flat triangle in the mesh, so the whole of its square maps smoothly onto that triangle, with no diagonal.
 */
class PatchGrid internal constructor(
    val patch: Patch,
    private val mesh: RenderMesh,
    private val firstVertex: Int,
    val mirrored: Boolean,
) {
    val rings: Int = patch.nu
    val columns: Int = patch.segments
    val closed: Boolean = patch.closedV
    private val ringUs = patch.ringUs
    private val poles = BooleanArray(rings) { patch.isPole(it) }

    /** How far the surface runs across each cell along its rings, per ring and line of points. */
    private val spans: FloatArray by lazy {
        FloatArray((rings - 1) * patch.nv) { i ->
            val ring = i / patch.nv
            val column = i % patch.nv
            (patch.point(patch.index(ring + 1, column)) - patch.point(patch.index(ring, column))).length
        }
    }

    fun isPole(ring: Int): Boolean = poles[ring]

    /** Where u lands on the rings. Equal rings at an end, as a flat cap has, read as the outermost; past an end it runs on at the rings' mean rate. */
    fun gridU(u: Double): Double {
        val last = rings - 1
        val first = ringUs[0].toDouble()
        val end = ringUs[last].toDouble()
        val rate = (end - first) / last
        if (u <= first) return if (rate > 0.0) (u - first) / rate else 0.0
        if (u >= end) return last + if (rate > 0.0) (u - end) / rate else 0.0
        var low = 0
        var high = last
        while (high - low > 1) {
            val middle = (low + high) / 2
            if (ringUs[middle] <= u) low = middle else high = middle
        }
        return low + (u - ringUs[low]) / (ringUs[high] - ringUs[low])
    }

    fun gridV(v: Double): Double = v * columns

    fun uAt(gridU: Double): Float {
        val ring = floor(gridU).toInt().coerceIn(0, rings - 2)
        return (ringUs[ring] + (gridU - ring).coerceIn(0.0, 1.0) * (ringUs[ring + 1] - ringUs[ring])).toFloat()
    }

    fun vAt(gridV: Double): Float = (held(gridV) / columns).toFloat()

    /** The index of the mesh vertex at [ring] and [column], which on a pole is the one vertex the pole's triangles share. */
    private fun vertex(ring: Int, column: Int): Int = firstVertex + patch.index(ring, if (poles[ring]) 0 else column % patch.nv)

    /** [gridV] wrapped onto a closed patch or held to an open one. */
    private fun held(gridV: Double): Double {
        if (!closed) return gridV.coerceIn(0.0, columns.toDouble())
        val wrapped = gridV - floor(gridV / columns) * columns
        return if (wrapped >= columns) 0.0 else wrapped
    }

    /** Writes a mesh vertex for the point at ([gridU], [gridV]) into [out] from [at]: every attribute as the cell's triangle has it. */
    fun writeVertex(gridU: Double, gridV: Double, out: FloatArray, at: Int) {
        val along = gridU.coerceIn(0.0, rings - 1.0)
        val around = held(gridV)
        val ring = floor(along).toInt().coerceAtMost(rings - 2)
        val column = floor(around).toInt().coerceAtMost(columns - 1)
        val x = along - ring
        val y = around - column
        val a = vertex(ring, column) * RenderMesh.FLOATS_PER_VERTEX
        val b = vertex(ring, column + 1) * RenderMesh.FLOATS_PER_VERTEX
        val c = vertex(ring + 1, column + 1) * RenderMesh.FLOATS_PER_VERTEX
        val d = vertex(ring + 1, column) * RenderMesh.FLOATS_PER_VERTEX
        val all = mesh.vertices
        val fan = isPoleCell(ring)
        for (i in 0 until RenderMesh.FLOATS_PER_VERTEX) {
            val start = all[a + i].toDouble()
            if (fan) {
                val near = start + y * (all[b + i].toDouble() - all[a + i])
                val far = all[d + i].toDouble() + y * (all[c + i].toDouble() - all[d + i])
                out[at + i] = (near + x * (far - near)).toFloat()
                continue
            }
            out[at + i] = if (y >= x) {
                (start + y * (all[b + i].toDouble() - all[a + i]) + x * (all[c + i].toDouble() - all[b + i])).toFloat()
            } else {
                (start + x * (all[d + i].toDouble() - all[a + i]) + y * (all[c + i].toDouble() - all[d + i])).toFloat()
            }
        }
    }

    /** The position on the mesh at ([gridU], [gridV]). */
    fun positionOn(gridU: Double, gridV: Double): Vec3 {
        val out = FloatArray(RenderMesh.FLOATS_PER_VERTEX)
        writeVertex(gridU, gridV, out, 0)
        return Vec3(out[0], out[1], out[2])
    }

    /** Whether the cells from [ring] to the next ring lie against a pole. */
    fun isPoleCell(ring: Int): Boolean = poles[ring] || poles[ring + 1]

    /** How far the surface runs across the cell at [ring] along the rings, at [gridV] around them. */
    private fun spanAt(ring: Int, gridV: Double): Float {
        val around = held(gridV)
        val column = floor(around).toInt().coerceAtMost(columns - 1)
        val t = (around - column).toFloat()
        val low = spans[ring * patch.nv + column % patch.nv]
        val high = spans[ring * patch.nv + (column + 1) % patch.nv]
        return low + (high - low) * t
    }

    /**
     * Where the surface has run [distance] from [gridU] along the rings at [gridV], going toward later rings when it is positive, cell
     * by cell, so a flat cap's one wide cell counts for what it is. Past an end ring it lies a whole ring beyond.
     */
    fun marchedU(gridU: Double, gridV: Double, distance: Float): Double {
        val forward = distance >= 0f
        var left = abs(distance).toDouble()
        var at = gridU.coerceIn(0.0, rings - 1.0)
        while (left > 0.0) {
            if (if (forward) at >= rings - 1.0 else at <= 0.0) return if (forward) at + 1.0 else at - 1.0
            val ring = if (forward) floor(at).toInt() else ceil(at).toInt() - 1
            val span = spanAt(ring, gridV).toDouble()
            val edge = if (forward) ring + 1.0 else ring.toDouble()
            val room = abs(edge - at) * span
            if (left <= room && span > 0.0) return at + (if (forward) left else -left) / span
            left -= room
            at = edge
        }
        return at
    }

    /** How far the surface moves at ([gridU], [gridV]) per unit of each grid coordinate, never quite 0. */
    fun gridRatesAt(gridU: Double, gridV: Double): SurfaceRates {
        val along = gridU.coerceIn(0.0, rings - 1.0)
        val ring = floor(along).toInt().coerceAtMost(rings - 2)
        val before = if (closed) gridV - HALF_CELL else (gridV - HALF_CELL).coerceAtLeast(0.0)
        val after = if (closed) gridV + HALF_CELL else (gridV + HALF_CELL).coerceAtMost(columns.toDouble())
        val aroundV = (positionOn(along, after) - positionOn(along, before)).length / (after - before).toFloat()
        return SurfaceRates(spanAt(ring, gridV).coerceAtLeast(MIN_RATE), aroundV.coerceAtLeast(MIN_RATE))
    }

    /** How far the surface moves at (u, v) per unit of u and per unit of v, measured across a grid cell, never quite 0. */
    fun ratesAt(u: Double, v: Double): SurfaceRates {
        val along = gridU(u).coerceIn(0.0, rings - 1.0)
        val around = gridV(v)
        val low = (along - HALF_CELL).coerceAtLeast(0.0)
        val high = (along + HALF_CELL).coerceAtMost(rings - 1.0)
        val span = uAt(high) - uAt(low)
        val alongU = if (span > 0f) (positionOn(high, around) - positionOn(low, around)).length / span else 0f
        val before = if (closed) around - HALF_CELL else (around - HALF_CELL).coerceAtLeast(0.0)
        val after = if (closed) around + HALF_CELL else (around + HALF_CELL).coerceAtMost(columns.toDouble())
        val aroundV = (positionOn(along, after) - positionOn(along, before)).length * columns / (after - before).toFloat()
        return SurfaceRates(alongU.coerceAtLeast(MIN_RATE), aroundV.coerceAtLeast(MIN_RATE))
    }
}

/** This patch's length along its rings for a whole run of u from 0 to 1. */
internal fun Patch.lengthPerU(): Float {
    val rings = slices ?: return 0f
    val span = rings.last().u - rings.first().u
    if (span <= 0f) return 0f
    return rings.zipWithNext { a, b -> (b.frame.center - a.frame.center).length }.sum() / span
}
