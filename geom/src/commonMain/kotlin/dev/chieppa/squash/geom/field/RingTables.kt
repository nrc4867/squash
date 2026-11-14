package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** How many directions round a ring its radius and section place are held at. */
internal const val RING_ANGLES = 96

private const val TWO_PI = (2.0 * PI).toFloat()
private const val HALF = 0.5f

/** A ring of a swept run: its [middle], the [tangent] its plane is square to, its [up], and points round it in order at [vs]. */
internal class SweptRing(val middle: Vec3, val tangent: Vec3, val up: Vec3, val points: List<Vec3>, val vs: FloatArray)

/** A ring's radius in each of [RING_ANGLES] directions round its plane, from up toward side, and its section's v there, null at a point. */
internal class RingTable(val radii: FloatArray, val vs: FloatArray?)

/** Reading rings into tables by direction, and v round a ring. */
internal object RingTables {
    /**
     * The radius of [ring] in each direction round its plane: the farthest its outline, the points round it joined in order, reaches that
     * way; and the v of the outline there. No vs for a ring closed to a point.
     */
    fun of(ring: SweptRing): RingTable {
        val table = FloatArray(RING_ANGLES) { -1f }
        val vTable = FloatArray(RING_ANGLES)
        val side = ring.up cross ring.tangent
        var lastAngle = 0f
        var lastRadius = 0f
        var lastV = 0f
        var any = false
        ring.points.forEachIndexed { k, p ->
            val q = p - ring.middle
            val x = q dot side
            val y = q dot ring.up
            val angle = atan2(x, y)
            val radius = sqrt(x * x + y * y)
            val v = ring.vs[k]
            if (radius > 0f) any = true
            if (k > 0) {
                var to = angle
                while (to - lastAngle > PI) to -= TWO_PI
                while (to - lastAngle < -PI) to += TWO_PI
                mark(table, vTable, lastAngle, lastRadius, lastV, to, radius, v)
            }
            lastAngle = angle
            lastRadius = radius
            lastV = v
        }
        if (!any) return RingTable(FloatArray(RING_ANGLES), null)
        fill(table, vTable)
        return RingTable(table, vTable)
    }

    /** Where [angle] falls among the directions, from 0 up to [RING_ANGLES]. */
    fun slotOf(angle: Float): Float {
        var at = angle / TWO_PI * RING_ANGLES
        at -= floor(at / RING_ANGLES) * RING_ANGLES
        return at
    }

    /** [table]'s value at [angle], eased between the directions either side. */
    fun radiusAt(table: FloatArray, angle: Float): Float {
        val at = slotOf(angle)
        val i = at.toInt().coerceIn(0, RING_ANGLES - 1)
        return radiusAt(table, i, at - i)
    }

    /** [table]'s value [t] of the way from direction [i] to the next. */
    fun radiusAt(table: FloatArray, i: Int, t: Float): Float = table[i] * (1f - t) + table[(i + 1) % RING_ANGLES] * t

    /** [vTable]'s v at [angle], eased round between the directions either side. */
    fun vAt(vTable: FloatArray, angle: Float): Float {
        val at = slotOf(angle)
        val i = at.toInt().coerceIn(0, RING_ANGLES - 1)
        val a = vTable[i]
        return wrapped(a + aroundDifference(a, vTable[(i + 1) % RING_ANGLES]) * (at - i))
    }

    /** The distance in the plane through a run's axis from (a, r) to the stretch of section outline from (a0, r0) to (a1, r1). */
    fun profileDistance(a: Float, r: Float, a0: Float, r0: Float, a1: Float, r1: Float): Float {
        val da = a1 - a0
        val dr = r1 - r0
        val squared = da * da + dr * dr
        val s = if (squared > PROFILE_TINY) (((a - a0) * da + (r - r0) * dr) / squared).coerceIn(0f, 1f) else 0f
        val x = a - (a0 + da * s)
        val y = r - (r0 + dr * s)
        return sqrt(x * x + y * y)
    }

    private const val PROFILE_TINY = 1e-12f

    /** [v] brought into [0, 1). */
    fun wrapped(v: Float): Float = v - floor(v)

    /** How far round from [a] to [b], the short way, as v. */
    fun aroundDifference(a: Float, b: Float): Float {
        var d = b - a
        d -= floor(d + HALF)
        return d
    }

    /** Lays the stretch of a section from ([a0], [r0], [v0]) to ([a1], [r1], [v1]) over the directions it passes, keeping the farthest. */
    private fun mark(table: FloatArray, vTable: FloatArray, a0: Float, r0: Float, v0: Float, a1: Float, r1: Float, v1: Float) {
        val from = minOf(a0, a1) / TWO_PI * RING_ANGLES
        val to = maxOf(a0, a1) / TWO_PI * RING_ANGLES
        var k = ceil(from).toInt()
        while (k <= to) {
            val t = if (to > from) (k - from) / (to - from) else 0f
            val forward = a0 <= a1
            val r = if (forward) r0 + (r1 - r0) * t else r1 + (r0 - r1) * t
            val v = if (forward) v0 + (v1 - v0) * t else v1 + (v0 - v1) * t
            val slot = ((k % RING_ANGLES) + RING_ANGLES) % RING_ANGLES
            if (r > table[slot]) {
                table[slot] = r
                vTable[slot] = wrapped(v)
            }
            k++
        }
    }

    /** Each direction no stretch passed over takes the mean of the nearest that one did either way, its v eased round between theirs. */
    private fun fill(table: FloatArray, vTable: FloatArray) {
        if (table.all { it < 0f }) {
            table.fill(0f)
            return
        }
        val known = table.copyOf()
        val knownV = vTable.copyOf()
        for (k in 0 until RING_ANGLES) {
            if (known[k] >= 0f) continue
            var before = 1
            while (known[((k - before) % RING_ANGLES + RING_ANGLES) % RING_ANGLES] < 0f) before++
            var after = 1
            while (known[(k + after) % RING_ANGLES] < 0f) after++
            val b = ((k - before) % RING_ANGLES + RING_ANGLES) % RING_ANGLES
            val a = (k + after) % RING_ANGLES
            val share = before.toFloat() / (before + after)
            table[k] = known[b] + (known[a] - known[b]) * share
            vTable[k] = wrapped(knownV[b] + aroundDifference(knownV[b], knownV[a]) * share)
        }
    }
}
