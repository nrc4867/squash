package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** How many sectors a hanging garment's rings are read in round the way it hangs. */
private const val SECTORS = 48

/** How tall each ring of a hanging garment is, in cells. */
private const val RING_CELLS = 2f

/** How far round a ring a sector's push reaches its neighbours, as a share of a turn. */
private const val SPREAD_ROUND = 0.25f

/** How many times its own push a ring may pass on to a ring further from where the garment starts to hang. */
private const val SPREAD_DOWN = 2f

/** How far past a ring's widest point, in the garment's ease, the body may lie and still push it; anything further is not under it. */
private const val REACH_EASES = 12f

private const val TURN = (2.0 * PI).toFloat()

/**
 * Cloth belled out of what hangs inside it, as cloth hanging under its own weight is: the vertices at [positions] that [hangs] marks are
 * read in rings across the way they hang, straight down, [cell] times [RING_CELLS] tall, from their top, where the cloth is held; in each
 * ring, each sector is pushed out from the ring's middle until it clears by [clear] the farthest of the points [inside], three values
 * each, lying in that ring and sector. The push spreads round its ring, falling off over a quarter turn, and down the cloth as a cone
 * hinged at its top does, each ring taking at least the push of every ring above it grown with its distance from the hinge, at most
 * [SPREAD_DOWN] times. The vertices moved, or null when nothing moves.
 */
fun hemFlare(positions: FloatArray, hangs: BooleanArray, cell: Float, inside: FloatArray, clear: Float): FloatArray? {
    val corners = hangs.size
    var top = Float.NEGATIVE_INFINITY
    var bottom = Float.POSITIVE_INFINITY
    for (v in 0 until corners) if (hangs[v]) {
        top = max(top, positions[v * 3 + 1])
        bottom = min(bottom, positions[v * 3 + 1])
    }
    if (top <= bottom) return null
    val step = cell * RING_CELLS
    val rings = (floor((top - bottom) / step).toInt() + 1)
    val centerX = FloatArray(rings)
    val centerZ = FloatArray(rings)
    val counts = IntArray(rings)
    fun ringOf(y: Float) = floor((top - y) / step).toInt()
    for (v in 0 until corners) if (hangs[v]) {
        val r = ringOf(positions[v * 3 + 1]).coerceIn(0, rings - 1)
        centerX[r] += positions[v * 3]
        centerZ[r] += positions[v * 3 + 2]
        counts[r]++
    }
    for (r in 0 until rings) if (counts[r] > 0) {
        centerX[r] /= counts[r]
        centerZ[r] /= counts[r]
    }
    fillGaps(centerX, centerZ, counts)
    val widest = FloatArray(rings)
    for (v in 0 until corners) if (hangs[v]) {
        val r = ringOf(positions[v * 3 + 1]).coerceIn(0, rings - 1)
        widest[r] = max(widest[r], radius(positions, v, centerX[r], centerZ[r]))
    }
    val farthest = Array(rings) { FloatArray(SECTORS) }
    val reach = clear * REACH_EASES
    for (q in 0 until inside.size / 3) {
        val y = inside[q * 3 + 1]
        if (y > top || y < bottom) continue
        val r = ringOf(y).coerceIn(0, rings - 1)
        val radius = radius(inside, q, centerX[r], centerZ[r])
        if (radius > widest[r] + reach) continue
        val s = sectorOf(inside, q, centerX[r], centerZ[r])
        for (k in -1..1) {
            val c = (s + k + SECTORS) % SECTORS
            farthest[r][c] = max(farthest[r][c], radius)
        }
    }
    val need = Array(rings) { FloatArray(SECTORS) }
    var any = false
    for (v in 0 until corners) if (hangs[v]) {
        val r = ringOf(positions[v * 3 + 1]).coerceIn(0, rings - 1)
        val s = sectorOf(positions, v, centerX[r], centerZ[r])
        if (farthest[r][s] == 0f) continue
        val push = farthest[r][s] + clear - radius(positions, v, centerX[r], centerZ[r])
        if (push > need[r][s]) {
            need[r][s] = push
            any = true
        }
    }
    if (!any) return null
    val round = Array(rings) { spreadRound(need[it]) }
    val pushes = Array(rings) { r ->
        FloatArray(SECTORS) { s ->
            var push = round[r][s]
            for (k in 0 until r) if (round[k][s] > 0f) push = max(push, round[k][s] * min((r + 0.5f) / (k + 0.5f), SPREAD_DOWN))
            push
        }
    }
    val out = positions.copyOf()
    for (v in 0 until corners) if (hangs[v]) {
        val at = (top - positions[v * 3 + 1]) / step - 0.5f
        val r0 = floor(at).toInt().coerceIn(0, rings - 1)
        val r1 = (r0 + 1).coerceAtMost(rings - 1)
        val t = (at - r0).coerceIn(0f, 1f)
        val cx = centerX[r0] + (centerX[r1] - centerX[r0]) * t
        val cz = centerZ[r0] + (centerZ[r1] - centerZ[r0]) * t
        val dx = positions[v * 3] - cx
        val dz = positions[v * 3 + 2] - cz
        val length = sqrt(dx * dx + dz * dz)
        if (length <= 0f) continue
        val around = ((atan2(dz, dx) / TURN + 1f) * SECTORS) % SECTORS
        val s0 = floor(around).toInt() % SECTORS
        val s1 = (s0 + 1) % SECTORS
        val w = around - floor(around)
        fun at(r: Int) = pushes[r][s0] * (1f - w) + pushes[r][s1] * w
        val push = at(r0) * (1f - t) + at(r1) * t
        out[v * 3] += dx / length * push
        out[v * 3 + 2] += dz / length * push
    }
    return out
}

private fun radius(points: FloatArray, i: Int, cx: Float, cz: Float): Float {
    val dx = points[i * 3] - cx
    val dz = points[i * 3 + 2] - cz
    return sqrt(dx * dx + dz * dz)
}

private fun sectorOf(points: FloatArray, i: Int, cx: Float, cz: Float): Int =
    floor((atan2(points[i * 3 + 2] - cz, points[i * 3] - cx) / TURN + 1f) * SECTORS).toInt().mod(SECTORS)

/** Rings with no vertex take the middle of the nearest ring above them that has one, or below where none above does. */
private fun fillGaps(x: FloatArray, z: FloatArray, counts: IntArray) {
    val first = counts.indexOfFirst { it > 0 }
    if (first < 0) return
    for (r in x.indices) if (counts[r] == 0) {
        val from = if (r < first) first else (r downTo 0).first { counts[it] > 0 }
        x[r] = x[from]
        z[r] = z[from]
    }
}

/** [need] spread round its ring: each sector takes the most of every sector's push, falling off as a squared cosine. */
private fun spreadRound(need: FloatArray): FloatArray {
    val n = need.size
    val width = max(1f, n * SPREAD_ROUND)
    val out = need.copyOf()
    for (k in 0 until n) {
        if (need[k] == 0f) continue
        for (j in 0 until n) {
            val apart = min(abs(j - k), n - abs(j - k)).toFloat()
            if (apart >= width) continue
            val falloff = cos(apart / width * PI.toFloat() / 2f).let { it * it }
            out[j] = max(out[j], need[k] * falloff)
        }
    }
    return out
}
