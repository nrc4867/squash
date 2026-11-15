package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.quickAtan2
import kotlin.math.sqrt

private const val TINY = 1e-12f

/** Where [SweptSolid.place] writes the field, u and v in its out array, which holds [PLACE_ARRAY] values. */
const val PLACE_FIELD = 0
const val PLACE_U = 1
const val PLACE_V = 2
private const val PLACE_ANGLE = 3
private const val PLACE_ALONG = 4
const val PLACE_ARRAY = 5

/**
 * The solid a run of rings sweeps out, as a distance-like field in the space its rings lie in: the least, over each pair of neighbouring
 * rings, of the point's signed distance from that pair's stretch of the run. Between the two rings' planes the stretch's middle, axis, up
 * and radius in each direction round are eased from one ring to the next by the point's distances to both planes, and outside them the
 * nearer ring's hold; the distance is taken in the plane through that axis and the point, from the section outline between the two rings,
 * closed by the flat face of an end ring, so a run whose end rings close to a point closes round there. Outside the solid it is the
 * distance from the nearest stretch and runs on smoothly from pair to pair; inside it is negative.
 *
 * Each ring keeps its u along the part and, beside its radius in each direction, the v of its section there, so [place] reads where a
 * point lies on the run: u eased between the two rings of the nearest stretch, carried on along the end ring's axis past either end by the
 * run's length per u, and v the section's in the point's direction round.
 */
class SweptSolid internal constructor(
    private val middles: Array<Vec3>,
    private val tangents: Array<Vec3>,
    private val ups: Array<Vec3>,
    private val tables: Array<RingTable>,
    private val us: FloatArray,
    override val low: Vec3,
    override val high: Vec3,
) : Solid {
    private val count = middles.size
    private val reaches = FloatArray(count) { tables[it].radii.max() }
    private val mx = FloatArray(count) { middles[it].x }
    private val my = FloatArray(count) { middles[it].y }
    private val mz = FloatArray(count) { middles[it].z }
    private val tx = FloatArray(count) { tangents[it].x }
    private val ty = FloatArray(count) { tangents[it].y }
    private val tz = FloatArray(count) { tangents[it].z }
    private val ux = FloatArray(count) { ups[it].x }
    private val uy = FloatArray(count) { ups[it].y }
    private val uz = FloatArray(count) { ups[it].z }
    private val gaps = FloatArray(count) { if (it + 1 < count) (middles[it + 1] - middles[it]).length else 0f }
    private val spreads = FloatArray(count) { spreadOf(tx[it], ty[it], tz[it]) }
    private val grid = PairGrid(this)

    /** How many pairs of neighbouring rings the run has; how far ring [i]'s plane moves across a cube of half side one; how wide pair [i]. */
    internal val pairCount: Int get() = count - 1
    internal fun planeSpread(i: Int): Float = spreads[i]
    internal fun pairReach(i: Int): Float = maxOf(reaches[i], reaches[i + 1])

    /** How long the run is per unit of u at its first and its last pair of rings. */
    private val startPerU = perU(0)
    private val endPerU = perU(count - 2)

    private fun perU(i: Int): Float {
        val du = us[i + 1] - us[i]
        return if (du > TINY && gaps[i] > TINY) gaps[i] / du else 1f
    }

    override fun distance(p: Vec3): Float = read(p, null)

    /** Writes the field at [p], and its u and v on the run, into [out] at [PLACE_FIELD], [PLACE_U] and [PLACE_V]. */
    fun place(p: Vec3, out: FloatArray) {
        out[PLACE_FIELD] = read(p, out)
    }

    /**
     * The field at [p]; with [out], also where on the run it lies. The pairs the [PairGrid] keeps for the point's cell are read in its
     * order until the least a pair can be passes the least so far, and every pair where the cell cannot be trusted there (see [scan]).
     */
    private fun read(p: Vec3, out: FloatArray?): Float {
        val px = p.x
        val py = p.y
        val pz = p.z
        val cell = grid.cellAt(px, py, pz) ?: return scan(px, py, pz, out)
        var best = Float.MAX_VALUE
        var bestPair = -1
        var bestAlong = 0f
        var bestAngle = 0f
        val pairs = cell.pairs
        for (n in pairs.indices) {
            if (n > 0 && cell.lows[n] > best) break
            val i = pairs[n]
            val before = plane(px, py, pz, i)
            val after = plane(px, py, pz, i + 1)
            val inSlab = before >= 0f && after <= 0f
            if (bestPair >= 0 && skips(px, py, pz, i, before, after, inSlab, best)) continue
            val field = pairField(px, py, pz, i, before, after, inSlab, out)
            if (field < best) {
                best = field
                bestPair = i
                if (out != null) {
                    bestAlong = out[PLACE_ALONG]
                    bestAngle = out[PLACE_ANGLE]
                }
            }
        }
        if (bestPair < 0 || best > cell.bound) return scan(px, py, pz, out)
        if (out != null) placeAt(bestPair, bestAlong, bestAngle, out)
        return best
    }

    /**
     * Whether pair [i] cannot give less than [best] at ([px], [py], [pz]): inside, where its planes do not hold the point; outside,
     * where the point lies farther than [best] beyond its planes (a lower bound on its distance) or from its middles' segment.
     */
    private fun skips(px: Float, py: Float, pz: Float, i: Int, before: Float, after: Float, inSlab: Boolean, best: Float): Boolean {
        if (best <= 0f) return !inSlab
        val past = if (before < 0f) -before else if (after > 0f) after else 0f
        val reach = best + pairReach(i)
        return past > best || segmentSquared(px, py, pz, i) > reach * reach
    }

    /** The field read over every pair: the one whose planes the point lies least far beyond first, the others where [skips] allows. */
    private fun scan(px: Float, py: Float, pz: Float, out: FloatArray?): Float {
        var first = 0
        var nearest = Float.MAX_VALUE
        var previous = plane(px, py, pz, 0)
        for (i in 0 until count - 1) {
            val next = plane(px, py, pz, i + 1)
            val past = if (previous < 0f) -previous else if (next > 0f) next else 0f
            if (past < nearest) {
                nearest = past
                first = i
            }
            previous = next
        }
        var best = pairAt(px, py, pz, first, out)
        var bestPair = first
        var bestAlong = if (out != null) out[PLACE_ALONG] else 0f
        var bestAngle = if (out != null) out[PLACE_ANGLE] else 0f
        var before = plane(px, py, pz, 0)
        for (i in 0 until count - 1) {
            val after = plane(px, py, pz, i + 1)
            val inSlab = before >= 0f && after <= 0f
            if (i != first && !skips(px, py, pz, i, before, after, inSlab, best)) {
                val field = pairField(px, py, pz, i, before, after, inSlab, out)
                if (field < best) {
                    best = field
                    bestPair = i
                    if (out != null) {
                        bestAlong = out[PLACE_ALONG]
                        bestAngle = out[PLACE_ANGLE]
                    }
                }
            }
            before = after
        }
        if (out != null) placeAt(bestPair, bestAlong, bestAngle, out)
        return best
    }

    /** How far ([px], [py], [pz]) lies along ring [i]'s tangent from its plane. */
    internal fun plane(px: Float, py: Float, pz: Float, i: Int): Float = (px - mx[i]) * tx[i] + (py - my[i]) * ty[i] + (pz - mz[i]) * tz[i]

    /** Pair [i]'s field at ([px], [py], [pz]). */
    internal fun pairAt(px: Float, py: Float, pz: Float, i: Int, out: FloatArray?): Float {
        val before = plane(px, py, pz, i)
        val after = plane(px, py, pz, i + 1)
        return pairField(px, py, pz, i, before, after, before >= 0f && after <= 0f, out)
    }

    /** The square of how far ([px], [py], [pz]) lies from the segment between pair [i]'s two rings' middles. */
    internal fun segmentSquared(px: Float, py: Float, pz: Float, i: Int): Float {
        val k = i + 1
        val dx = mx[k] - mx[i]
        val dy = my[k] - my[i]
        val dz = mz[k] - mz[i]
        val qx = px - mx[i]
        val qy = py - my[i]
        val qz = pz - mz[i]
        val squared = dx * dx + dy * dy + dz * dz
        val s = if (squared > TINY) ((qx * dx + qy * dy + qz * dz) / squared).coerceIn(0f, 1f) else 0f
        val ex = qx - dx * s
        val ey = qy - dy * s
        val ez = qz - dz * s
        return ex * ex + ey * ey + ez * ez
    }

    /**
     * The signed distance from ([px], [py], [pz]) to pair [i]'s stretch of the run, in the plane through its axis: between the rings'
     * planes ([inSlab]) the frame is eased between theirs as far as the point's plane distances [before] and [after] put it, and outside
     * them it is the nearer ring's own, the point lying [before] short of the first or [after] past the second along it. Negative only
     * between the planes and inside the outline. With [out], how far along the pair the point lies and its angle round go there.
     */
    private fun pairField(px: Float, py: Float, pz: Float, i: Int, before: Float, after: Float, inSlab: Boolean, out: FloatArray?): Float {
        val k = i + 1
        val gap = gaps[i]
        val t: Float
        val along: Float
        when {
            inSlab -> {
                t = if (before - after > TINY) before / (before - after) else 0f
                along = t * gap
            }
            before < 0f -> {
                t = 0f
                along = before
            }
            else -> {
                t = 1f
                along = gap + after
            }
        }
        val s = 1f - t
        var ax = tx[i] * s + tx[k] * t
        var ay = ty[i] * s + ty[k] * t
        var az = tz[i] * s + tz[k] * t
        val alongLength = sqrt(ax * ax + ay * ay + az * az)
        if (alongLength > 0f) {
            val inverse = 1f / alongLength
            ax *= inverse
            ay *= inverse
            az *= inverse
        } else {
            ax = tx[i]
            ay = ty[i]
            az = tz[i]
        }
        val qx = px - (mx[i] * s + mx[k] * t)
        val qy = py - (my[i] * s + my[k] * t)
        val qz = pz - (mz[i] * s + mz[k] * t)
        val qa = qx * ax + qy * ay + qz * az
        val acrossX = qx - ax * qa
        val acrossY = qy - ay * qa
        val acrossZ = qz - az * qa
        val mixedX = ux[i] * s + ux[k] * t
        val mixedY = uy[i] * s + uy[k] * t
        val mixedZ = uz[i] * s + uz[k] * t
        val ma = mixedX * ax + mixedY * ay + mixedZ * az
        var upX = mixedX - ax * ma
        var upY = mixedY - ay * ma
        var upZ = mixedZ - az * ma
        val upLength = sqrt(upX * upX + upY * upY + upZ * upZ)
        if (upLength > 0f) {
            val inverse = 1f / upLength
            upX *= inverse
            upY *= inverse
            upZ *= inverse
        } else {
            upX = ux[i]
            upY = uy[i]
            upZ = uz[i]
        }
        val sideX = upY * az - upZ * ay
        val sideY = upZ * ax - upX * az
        val sideZ = upX * ay - upY * ax
        val r = sqrt(acrossX * acrossX + acrossY * acrossY + acrossZ * acrossZ)
        val angle = if (r > 0f) quickAtan2(acrossX * sideX + acrossY * sideY + acrossZ * sideZ, acrossX * upX + acrossY * upY + acrossZ * upZ) else 0f
        if (out != null) {
            out[PLACE_ANGLE] = angle
            out[PLACE_ALONG] = along
        }
        val slot = RingTables.slotOf(angle)
        val at = slot.toInt().coerceIn(0, RING_ANGLES - 1)
        val r0 = RingTables.radiusAt(tables[i].radii, at, slot - at)
        val r1 = RingTables.radiusAt(tables[k].radii, at, slot - at)
        var nearest = RingTables.profileDistance(along, r, 0f, r0, gap, r1)
        if (i == 0) nearest = minOf(nearest, RingTables.profileDistance(along, r, 0f, 0f, 0f, r0))
        if (k == count - 1) nearest = minOf(nearest, RingTables.profileDistance(along, r, gap, r1, gap, 0f))
        return if (inSlab && r < r0 + (r1 - r0) * t) -nearest else nearest
    }

    /** The run's rings as flat arrays, for reading its field elsewhere with the same values it reads by. */
    fun rings(): RunRings = RunRings(
        count,
        FloatArray(count * 3) { floatArrayOf(mx[it / 3], my[it / 3], mz[it / 3])[it % 3] },
        FloatArray(count * 3) { floatArrayOf(tx[it / 3], ty[it / 3], tz[it / 3])[it % 3] },
        FloatArray(count * 3) { floatArrayOf(ux[it / 3], uy[it / 3], uz[it / 3])[it % 3] },
        us.copyOf(),
        gaps.copyOf(),
        reaches.copyOf(),
        FloatArray(count * RING_ANGLES).also { all -> for (i in 0 until count) tables[i].radii.copyInto(all, i * RING_ANGLES) },
        FloatArray(count * RING_ANGLES).also { all -> for (i in 0 until count) tables[i].vs?.copyInto(all, i * RING_ANGLES) },
        BooleanArray(count) { tables[it].vs != null },
        startPerU,
        endPerU,
    )

    /** Writes u and v for a point [along] pair [pair] at [angle] round, its u carried on past either end of the run. */
    private fun placeAt(pair: Int, along: Float, angle: Float, out: FloatArray) {
        val k = pair + 1
        val gap = gaps[pair]
        val t = if (gap > TINY) (along / gap).coerceIn(0f, 1f) else 0f
        out[PLACE_U] = when {
            pair == 0 && along < 0f -> us[0] + along / startPerU
            k == count - 1 && along > gap -> us[k] + (along - gap) / endPerU
            else -> us[pair] + (us[k] - us[pair]) * t
        }
        val first = sectionV(pair, angle)
        val second = sectionV(k, angle)
        out[PLACE_V] = when {
            first == null -> second ?: 0f
            second == null -> first
            else -> RingTables.wrapped(first + RingTables.aroundDifference(first, second) * t)
        }
    }

    /** The v of ring [ring]'s section at [angle] round, or null for a ring closed to a point, which has none. */
    private fun sectionV(ring: Int, angle: Float): Float? = tables[ring].vs?.let { RingTables.vAt(it, angle) }

    companion object {
        /** The solid a patch's rings sweep out, read from its points themselves (see [sweptOfRings]); null for fewer than two rings. */
        fun ofRings(points: List<List<Vec3>>, vs: FloatArray, tangents: List<Vec3>, ups: List<Vec3>, us: FloatArray): SweptSolid? =
            sweptOfRings(points, vs, tangents, ups, us)
    }
}
