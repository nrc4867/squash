package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How many cells a grid has along its longest side. */
private const val CELLS = 64

/** How far a ray starts from its point, and how near a hit may lie, as a share of the grid's longest side. */
private const val START = 1e-4f

/**
 * Triangles in body space bucketed in a uniform grid, and whether a ray from a point reaches the grid's edge without crossing any of
 * them: whether the point can be seen from outside along that way.
 */
class RayGrid(private val corners: FloatArray) {
    private val low: Vec3
    private val cell: Float
    private val counts = IntArray(3)
    private val buckets: Array<IntArray>
    private val start: Float

    init {
        var lx = Float.POSITIVE_INFINITY; var ly = Float.POSITIVE_INFINITY; var lz = Float.POSITIVE_INFINITY
        var hx = Float.NEGATIVE_INFINITY; var hy = Float.NEGATIVE_INFINITY; var hz = Float.NEGATIVE_INFINITY
        for (i in corners.indices step 3) {
            lx = min(lx, corners[i]); ly = min(ly, corners[i + 1]); lz = min(lz, corners[i + 2])
            hx = max(hx, corners[i]); hy = max(hy, corners[i + 1]); hz = max(hz, corners[i + 2])
        }
        val longest = max(hx - lx, max(hy - ly, hz - lz)) * 1.001f
        cell = longest / CELLS
        start = longest * START
        low = Vec3(lx, ly, lz)
        counts[0] = max(1, ((hx - lx) / cell).toInt() + 1)
        counts[1] = max(1, ((hy - ly) / cell).toInt() + 1)
        counts[2] = max(1, ((hz - lz) / cell).toInt() + 1)
        val lists = Array(counts[0] * counts[1] * counts[2]) { ArrayList<Int>() }
        for (t in 0 until corners.size / 9) {
            val at = t * 9
            val from = IntArray(3) { axis -> clampCell(axis, min(corners[at + axis], min(corners[at + 3 + axis], corners[at + 6 + axis]))) }
            val to = IntArray(3) { axis -> clampCell(axis, max(corners[at + axis], max(corners[at + 3 + axis], corners[at + 6 + axis]))) }
            for (x in from[0]..to[0]) for (y in from[1]..to[1]) for (z in from[2]..to[2]) lists[index(x, y, z)] += t
        }
        buckets = Array(lists.size) { lists[it].toIntArray() }
    }

    private fun lowOf(axis: Int): Float = when (axis) { 0 -> low.x; 1 -> low.y; else -> low.z }

    private fun clampCell(axis: Int, value: Float): Int = floor((value - lowOf(axis)) / cell).toInt().coerceIn(0, counts[axis] - 1)

    private fun index(x: Int, y: Int, z: Int): Int = (x * counts[1] + y) * counts[2] + z

    /** Whether a ray from [point] along [direction], a unit vector, leaves the grid crossing no triangle. */
    fun escapes(point: Vec3, direction: Vec3): Boolean {
        val origin = doubleArrayOf(point.x.toDouble(), point.y.toDouble(), point.z.toDouble())
        val dir = doubleArrayOf(direction.x.toDouble(), direction.y.toDouble(), direction.z.toDouble())
        val at = IntArray(3)
        val step = IntArray(3)
        val next = DoubleArray(3)
        val delta = DoubleArray(3)
        for (axis in 0..2) {
            val c = floor((origin[axis] - lowOf(axis)) / cell).toInt()
            if (c < 0 || c >= counts[axis]) return true
            at[axis] = c
            step[axis] = if (dir[axis] > 0) 1 else -1
            val edge = lowOf(axis) + (c + if (dir[axis] > 0) 1 else 0) * cell
            next[axis] = if (dir[axis] == 0.0) Double.POSITIVE_INFINITY else (edge - origin[axis]) / dir[axis]
            delta[axis] = if (dir[axis] == 0.0) Double.POSITIVE_INFINITY else cell / abs(dir[axis])
        }
        while (true) {
            for (t in buckets[index(at[0], at[1], at[2])]) if (hits(t, origin, dir)) return false
            val axis = if (next[0] < next[1]) (if (next[0] < next[2]) 0 else 2) else (if (next[1] < next[2]) 1 else 2)
            at[axis] += step[axis]
            if (at[axis] < 0 || at[axis] >= counts[axis]) return true
            next[axis] += delta[axis]
        }
    }

    /** Whether the ray crosses triangle [t] further than the start, by the Möller–Trumbore test. */
    private fun hits(t: Int, o: DoubleArray, d: DoubleArray): Boolean {
        val i = t * 9
        val ax = corners[i].toDouble(); val ay = corners[i + 1].toDouble(); val az = corners[i + 2].toDouble()
        val e1x = corners[i + 3] - ax; val e1y = corners[i + 4] - ay; val e1z = corners[i + 5] - az
        val e2x = corners[i + 6] - ax; val e2y = corners[i + 7] - ay; val e2z = corners[i + 8] - az
        val px = d[1] * e2z - d[2] * e2y; val py = d[2] * e2x - d[0] * e2z; val pz = d[0] * e2y - d[1] * e2x
        val det = e1x * px + e1y * py + e1z * pz
        if (abs(det) < 1e-18) return false
        val inv = 1.0 / det
        val sx = o[0] - ax; val sy = o[1] - ay; val sz = o[2] - az
        val u = (sx * px + sy * py + sz * pz) * inv
        if (u < 0.0 || u > 1.0) return false
        val qx = sy * e1z - sz * e1y; val qy = sz * e1x - sx * e1z; val qz = sx * e1y - sy * e1x
        val v = (d[0] * qx + d[1] * qy + d[2] * qz) * inv
        if (v < 0.0 || u + v > 1.0) return false
        return (e2x * qx + e2y * qy + e2z * qz) * inv > start
    }
}
