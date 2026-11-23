package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A head's mouth cut, read in the head's own space about a hinge through [hinge] turning about the unit direction [axis]: a point's
 * distance r from the hinge and angle phi from [forward] toward up, the side a positive turn about [axis] turns [forward] away from. The
 * cut surface lies at angle [phis] (radians) at each distance [radii], ascending, from the hinge; the jaw is everything past
 * [cornerRadius] from the hinge between [lowPhi] and the cut. [built] is the angle, in radians, the jaw is turned open by, a turn about
 * [axis] that takes its front down.
 */
class JawCut(
    val hinge: Vec3,
    val axis: Vec3,
    val forward: Vec3,
    private val radii: FloatArray,
    private val phis: FloatArray,
    val cornerRadius: Float,
    private val lowPhi: Float,
    val built: Float,
) {
    /** The way phi grows, square to [axis] and [forward]. */
    val up: Vec3 = forward cross axis

    /** The turn the jaw is built open by, as a rotation of directions. */
    val opened: Mat3 = rotation(built)

    private val closing: Mat3 = rotation(-built)

    /** The cut's angle at distance [r] from the hinge, held at its ends past them. */
    fun cutPhi(r: Float): Float {
        if (r <= radii.first()) return phis.first()
        if (r >= radii.last()) return phis.last()
        var i = 0
        while (i < radii.size - 2 && radii[i + 1] < r) i++
        val span = radii[i + 1] - radii[i]
        val t = if (span > 1e-6f) (r - radii[i]) / span else 0f
        return phis[i] + (phis[i + 1] - phis[i]) * t
    }

    /** The distance from the hinge, across [axis], and the angle of [p], into [out]. */
    private fun polar(p: Vec3, out: FloatArray) {
        val d = p - hinge
        val x = d dot forward
        val y = d dot up
        out[0] = sqrt(x * x + y * y)
        out[1] = atan2(y, x)
    }

    /** Signed, near-distance field of the jaw region, as it lies shut, at [q]: negative inside. */
    fun region(q: Vec3): Float {
        val rp = FloatArray(2)
        polar(q, rp)
        val r = rp[0]
        val phi = rp[1]
        return maxOf(r * (phi - cutPhi(r)), r * (lowPhi - phi), cornerRadius - r)
    }

    /**
     * How far [p], as built, lies to the palate's side of the middle of the gap the open jaw leaves: negative on the jaw's side, by its
     * distance from the hinge times the angle.
     */
    fun aboveGapMiddle(p: Vec3): Float {
        val rp = FloatArray(2)
        polar(p, rp)
        return rp[0] * (rp[1] - (cutPhi(rp[0]) - built * 0.5f))
    }

    /** [p] as the jaw built open carries it: turned about the hinge by [built]. */
    fun open(p: Vec3): Vec3 = hinge + opened * (p - hinge)

    /** [p] on the jaw built open, taken back to where it lies shut. */
    fun shut(p: Vec3): Vec3 = hinge + closing * (p - hinge)

    /** The gap between the palate and the jaw's floor as built: negative inside, with phi and the cut's angle at [p] written to [out]. */
    fun wedge(p: Vec3, out: FloatArray): Float {
        val rp = FloatArray(2)
        polar(p, rp)
        val r = rp[0]
        val phi = rp[1]
        val cut = cutPhi(r)
        out[0] = phi
        out[1] = cut
        return maxOf(r * (phi - cut), r * (cut - built - phi), cornerRadius - r)
    }

    /** [p] turned about the hinge by [angle] radians in phi: positive toward up. */
    fun turnedInPhi(p: Vec3, angle: Float): Vec3 = hinge + rotation(-angle) * (p - hinge)

    /** A box holding [low] to [high] once the jaw is opened. */
    fun openedBox(low: Vec3, high: Vec3): Pair<Vec3, Vec3> {
        var lo = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var hi = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (x in listOf(low.x, high.x)) for (y in listOf(low.y, high.y)) for (z in listOf(low.z, high.z)) {
            val t = open(Vec3(x, y, z))
            lo = Vec3(minOf(lo.x, t.x), minOf(lo.y, t.y), minOf(lo.z, t.z))
            hi = Vec3(maxOf(hi.x, t.x), maxOf(hi.y, t.y), maxOf(hi.z, t.z))
        }
        return lo to hi
    }

    /** The turn by [radians] about [axis]. */
    private fun rotation(radians: Float): Mat3 {
        val c = cos(radians)
        val s = sin(radians)
        fun turned(v: Vec3) = v * c + (axis cross v) * s + axis * ((axis dot v) * (1f - c))
        return Mat3(turned(Vec3.X), turned(Vec3.Y), turned(Vec3.Z))
    }
}

/**
 * One half of a run split by a [cut]: the [upper] half (snout, palate) or the jaw half turned open by the cut's built angle, each rounded
 * along the cut by [rounding]. Both place a point where the run would place it shut, so the jaw keeps the run's own (u, v).
 */
class JawHalfSolid(
    val inner: Solid,
    val cut: JawCut,
    val upper: Boolean,
    val rounding: Float,
) : PlacingSolid {
    override val low: Vec3
    override val high: Vec3

    init {
        if (upper) {
            low = inner.low
            high = inner.high
        } else {
            val (lo, hi) = cut.openedBox(inner.low, inner.high)
            low = lo
            high = hi
        }
    }

    /** [p] where the run lies shut. */
    fun shut(p: Vec3): Vec3 = if (upper) p else cut.shut(p)

    private fun cutTerm(q: Vec3): Float = if (upper) -cut.region(q) else cut.region(q)

    override fun distance(p: Vec3): Float {
        val q = shut(p)
        return smoothMax(inner.distance(q), cutTerm(q), rounding)
    }

    override fun place(p: Vec3, out: FloatArray) {
        val q = shut(p)
        val placing = inner as? PlacingSolid
        val own: Float
        if (placing != null) {
            placing.place(q, out)
            own = out[PLACE_FIELD]
        } else {
            own = inner.distance(q)
            out[PLACE_U] = 0f
            out[PLACE_V] = 0f
        }
        out[PLACE_FIELD] = smoothMax(own, cutTerm(q), rounding)
    }
}

/**
 * The lining behind an open mouth: the gap between the palate and the jaw's floor as [cut] builds it, kept [inset] inside [hosts] (the
 * head's runs before the cut) where the gap's place is turned back onto the palate, so it stands back from the lips. It places at
 * [faceU], its v running from 0 at the palate to 1 at the floor.
 */
class MouthLiningSolid(
    val hosts: List<Solid>,
    val cut: JawCut,
    val inset: Float,
    val faceU: Float,
) : PlacingSolid {
    override val low: Vec3
    override val high: Vec3

    init {
        var lo = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var hi = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (h in hosts) {
            val (tl, th) = cut.openedBox(h.low, h.high)
            lo = Vec3(minOf(lo.x, h.low.x, tl.x), minOf(lo.y, h.low.y, tl.y), minOf(lo.z, h.low.z, tl.z))
            hi = Vec3(maxOf(hi.x, h.high.x, th.x), maxOf(hi.y, h.high.y, th.y), maxOf(hi.z, h.high.z, th.z))
        }
        low = lo
        high = hi
    }

    private fun lined(p: Vec3, angles: FloatArray): Float {
        val wedge = cut.wedge(p, angles)
        val onPalate = cut.turnedInPhi(p, angles[1] - angles[0])
        var host = Float.MAX_VALUE
        for (h in hosts) host = minOf(host, h.distance(onPalate))
        return maxOf(wedge, host + inset)
    }

    override fun distance(p: Vec3): Float = lined(p, FloatArray(2))

    override fun place(p: Vec3, out: FloatArray) {
        val angles = FloatArray(2)
        out[PLACE_FIELD] = lined(p, angles)
        out[PLACE_U] = faceU
        out[PLACE_V] = if (cut.built > 0f) ((angles[1] - angles[0]) / cut.built).coerceIn(0f, 1f) else 0f
    }
}
