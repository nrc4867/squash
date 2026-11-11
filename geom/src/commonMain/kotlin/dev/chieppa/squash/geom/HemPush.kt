package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A grid of points in body space, [nu] rings of [nv] columns, ring after ring, closed round when [closedV]. */
class PointGrid(val points: FloatArray, val nu: Int, val nv: Int, val closedV: Boolean) {
    init {
        require(points.size == nu * nv * Patch.COMPONENTS) { "A grid of $nu by $nv holds ${points.size} values" }
    }

    fun point(ring: Int, column: Int): Vec3 {
        val at = (ring * nv + column) * Patch.COMPONENTS
        return Vec3(points[at], points[at + 1], points[at + 2])
    }
}

/**
 * Pushes a shell that hangs free, as a skirt from its waist, out of what it hangs over. See HLD: data-model, Garments.
 *
 * Each ring of the shell is cut against the surfaces it hangs over: every grid edge of theirs crossing the ring's plane gives a point of
 * their section there, and each column of the ring is pushed out from the ring's middle until it clears the farthest of those points
 * round it. The push then spreads round the ring, each column taking the most of every other's push falling off over [SPREAD_ROUND]
 * of a turn, so the cloth bells out as one fold rather than tenting on a point; and along the shell as a cone hinged at its held ring
 * does, each ring taking a push that grows with its distance from the hinge, at most [SPREAD_DOWN] times the push it carries on from.
 * No column ends nearer its middle than it was, or nearer than the clearance outside a section's farthest point round it.
 */
object HemPush {
    /** How far round a ring a column's push reaches its neighbours, as a share of a turn. */
    const val SPREAD_ROUND = 0.25f

    /** How many times its own push a ring may pass on to a ring further from the hinge. */
    const val SPREAD_DOWN = 2f

    /** How far past a ring's widest point a section may lie and still push it, in body units; anything further is not under it. */
    const val MAX_REACH = 0.35f

    private const val TURN = (2.0 * PI).toFloat()

    /**
     * How far each point of [shell] moves, ring after ring, to clear [under] by [clearance], or null when none moves. The shell is held
     * at ring [hinge], which never moves, and closed round.
     */
    fun push(shell: PointGrid, hinge: Int, under: List<PointGrid>, clearance: Float): Array<Vec3>? {
        require(shell.closedV) { "A hanging shell goes the whole way round" }
        require(hinge in 0 until shell.nu) { "Ring $hinge is not on a shell of ${shell.nu}" }
        val nu = shell.nu
        val nv = shell.nv
        val rings = Array(nu) { ringOf(shell, it) }
        val need = Array(nu) { FloatArray(nv) }
        var any = false
        for (i in 0 until nu) {
            if (i == hinge) continue
            val ring = rings[i]
            val reach = ring.radii.max() + MAX_REACH
            val farthest = FloatArray(nv)
            for (grid in under) sectionOf(grid, ring) { point ->
                val r = point - ring.center
                val radius = sqrt((r dot ring.e1) * (r dot ring.e1) + (r dot ring.e2) * (r dot ring.e2))
                if (radius > reach) return@sectionOf
                val column = ring.nearestColumn(atan2(r dot ring.e2, r dot ring.e1))
                for (k in -1..1) {
                    val c = (column + k + nv) % nv
                    farthest[c] = max(farthest[c], radius)
                }
            }
            for (j in 0 until nv) {
                if (farthest[j] == 0f) continue
                val push = farthest[j] + clearance - ring.radii[j]
                if (push > 0f) {
                    need[i][j] = push
                    any = true
                }
            }
        }
        if (!any) return null
        val round = Array(nu) { spreadRound(need[it]) }
        val distance = FloatArray(nu)
        for (i in hinge + 1 until nu) distance[i] = distance[i - 1] + (rings[i].center - rings[i - 1].center).length
        for (i in hinge - 1 downTo 0) distance[i] = distance[i + 1] + (rings[i].center - rings[i + 1].center).length
        return Array(nu * nv) { index ->
            val i = index / nv
            val j = index % nv
            if (i == hinge) return@Array Vec3.ZERO
            var push = round[i][j]
            for (k in 0 until nu) {
                if (k == i || k == hinge || round[k][j] == 0f || (k - hinge) * (i - hinge) <= 0 || distance[k] == 0f) continue
                push = max(push, round[k][j] * min(distance[i] / distance[k], SPREAD_DOWN))
            }
            rings[i].ways[j] * push
        }
    }

    /** [need] spread round its ring: each column takes the most of every column's push, falling off as a squared cosine. */
    private fun spreadRound(need: FloatArray): FloatArray {
        val nv = need.size
        val width = max(1f, nv * SPREAD_ROUND)
        val out = need.copyOf()
        for (k in 0 until nv) {
            if (need[k] == 0f) continue
            for (j in 0 until nv) {
                val apart = min(abs(j - k), nv - abs(j - k)).toFloat()
                if (apart >= width) continue
                val falloff = cos(apart / width * PI.toFloat() / 2f).let { it * it }
                out[j] = max(out[j], need[k] * falloff)
            }
        }
        return out
    }

    /**
     * A ring of the shell: its middle, its axis from its neighbours' middles, two ways square to it, and for each column the way out
     * from the middle, square to the axis, its distance out that way and its angle round.
     */
    private class Ring(val center: Vec3, val axis: Vec3, val e1: Vec3, val e2: Vec3, val ways: Array<Vec3>, val radii: FloatArray, val angles: FloatArray) {
        fun nearestColumn(angle: Float): Int {
            var best = 0
            var bestApart = Float.POSITIVE_INFINITY
            for (j in angles.indices) {
                val apart = abs(angle - angles[j]).let { min(it, TURN - it) }
                if (apart < bestApart) {
                    bestApart = apart
                    best = j
                }
            }
            return best
        }
    }

    private fun middleOf(shell: PointGrid, ring: Int): Vec3 {
        var sum = Vec3.ZERO
        for (j in 0 until shell.nv) sum += shell.point(ring, j)
        return sum * (1f / shell.nv)
    }

    private fun ringOf(shell: PointGrid, i: Int): Ring {
        val center = middleOf(shell, i)
        val before = middleOf(shell, max(i - 1, 0))
        val after = middleOf(shell, min(i + 1, shell.nu - 1))
        val axis = (after - before).let { if (it.length > 0f) it.normalized() else Vec3.Y }
        val first = (0 until shell.nv).map { shell.point(i, it) - center }.map { it - axis * (it dot axis) }.firstOrNull { it.length > 0f }
        val e1 = first?.normalized() ?: anySquare(axis)
        val e2 = axis cross e1
        val ways = arrayOfNulls<Vec3>(shell.nv)
        val radii = FloatArray(shell.nv)
        val angles = FloatArray(shell.nv)
        for (j in 0 until shell.nv) {
            val r = (shell.point(i, j) - center).let { it - axis * (it dot axis) }
            radii[j] = r.length
            angles[j] = atan2(r dot e2, r dot e1)
            ways[j] = if (r.length > 0f) r.normalized() else e1
        }
        return Ring(center, axis, e1, e2, Array(shell.nv) { requireNotNull(ways[it]) }, radii, angles)
    }

    private fun anySquare(axis: Vec3): Vec3 = (if (abs(axis.x) < 0.9f) Vec3.X else Vec3.Z).let { (it - axis * (it dot axis)).normalized() }

    /** Each point where an edge of [grid], along it or round it, crosses [ring]'s plane. */
    private inline fun sectionOf(grid: PointGrid, ring: Ring, found: (Vec3) -> Unit) {
        val heights = FloatArray(grid.nu * grid.nv)
        var above = false
        var below = false
        for (v in heights.indices) {
            val at = v * Patch.COMPONENTS
            heights[v] = (grid.points[at] - ring.center.x) * ring.axis.x + (grid.points[at + 1] - ring.center.y) * ring.axis.y +
                (grid.points[at + 2] - ring.center.z) * ring.axis.z
            if (heights[v] >= 0f) above = true else below = true
        }
        if (!above || !below) return
        val around = if (grid.closedV) grid.nv else grid.nv - 1
        for (r in 0 until grid.nu) for (c in 0 until grid.nv) {
            val a = r * grid.nv + c
            if (r + 1 < grid.nu) crossing(grid, heights, a, a + grid.nv)?.let(found)
            if (c < around) crossing(grid, heights, a, r * grid.nv + (c + 1) % grid.nv)?.let(found)
        }
    }

    private fun crossing(grid: PointGrid, heights: FloatArray, a: Int, b: Int): Vec3? {
        val ha = heights[a]
        val hb = heights[b]
        if ((ha >= 0f) == (hb >= 0f)) return null
        val t = ha / (ha - hb)
        val p = Patch.COMPONENTS
        return Vec3(
            grid.points[a * p] + (grid.points[b * p] - grid.points[a * p]) * t,
            grid.points[a * p + 1] + (grid.points[b * p + 1] - grid.points[a * p + 1]) * t,
            grid.points[a * p + 2] + (grid.points[b * p + 2] - grid.points[a * p + 2]) * t,
        )
    }
}
