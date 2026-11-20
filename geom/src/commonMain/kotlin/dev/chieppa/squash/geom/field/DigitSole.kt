package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3

/** One ring of a digit's run as cast: its [centre] and [frame], the rays' ends round it or null for a pole, its [u], and whether it is buried whole. */
internal class LoftRing(val centre: Vec3, val frame: RingFrame, val found: List<RayHit>?, val u: Float, val buriedWhole: Boolean = false)

/**
 * How a digit standing on a floor across its part's axis ends: [uAt] gives the part's u at a distance along the part's axis, so the sole's
 * rings take the u their place gives, and the template's areas past the floor's u paint the sole and nothing above it.
 */
class SoleRings(val uAt: (Float) -> Float)

/** How much of a ring's width, at most, the axis may lie off the floor's normal for the floor to be the digit's end. */
private const val FACING = 0.95f

/** How far above the floor the sole's edge ring stands, as a share of the digit's radius. */
private const val EDGE_LIFT = 0.04f

/** How far above the floor, as a share of the digit's radius, the last ring kept as cast must lie whole. */
private const val CLEAR = 0.15f

/** How far in from the sole's edge toward its middle its inner ring lies. */
private const val INNER = 0.45f

/** How far past the floor's u the sole's inner ring lies, and its middle twice as far, so a border at the floor's u lies near the sole's edge. */
private const val SOLE_U_STEP = 0.02f

/** The least a ring's u rises past the ring before it. */
private const val RISING = 1e-4f

/** The fewest rings a sole takes: its edge, an inner ring and its middle. */
private const val SOLE_RINGS = 3

/**
 * A digit's run ending on a floor across the part's axis, the part's own x: the rings that reach within [CLEAR] of the digit's radius of
 * the floor are cast again level with it, from the axis where it crosses each level, the side of the hoof down to an edge ring just above
 * the floor, then an inner ring and the middle on the floor itself, so the sole is flat, its edge one ring, and its u past the floor's u.
 * [cast] casts a ring of rays from a centre in a frame. The run stays as cast where its axis leaves the solid anywhere but through such a
 * floor, or too few rings reach it.
 */
internal object DigitSole {
    fun flattened(
        rings: List<LoftRing>,
        field: BlendedField,
        digit: DigitAxis,
        sole: SoleRings,
        cast: (Vec3, RingFrame) -> List<RayHit>,
    ): List<LoftRing>? {
        val floor = field.floor?.takeIf { it.across } ?: return null
        if (digit.direction.x <= 0f) return null
        val end = DigitLoft.tip(field, digit, 0f)
        val facing = field.gradient(end, digit.radius * 1e-2f)
        if (facing.length <= 0f || facing.x / facing.length < FACING) return null
        val level = end.x
        val near = level - digit.radius * CLEAR
        val first = rings.indexOfFirst { ring ->
            val found = ring.found
            !ring.buriedWhole && (if (found == null) ring.centre.x > near else found.any { it.point.x > near })
        }
        if (first <= 0 || rings.size - first < SOLE_RINGS || rings[first - 1].found == null) return null
        val above = rings[first - 1].found!!.maxOf { it.point.x }
        val edgeLevel = level - digit.radius * EDGE_LIFT
        if (above >= edgeLevel) return null
        val flat = levelFrame(rings.first().frame)
        val sides = rings.size - first - SOLE_RINGS
        val out = rings.take(first).toMutableList()
        var lastU = rings[first - 1].u
        fun nextU(want: Float): Float = maxOf(want, lastU + RISING).also { lastU = it }
        for (k in 0..sides) {
            val x = above + (edgeLevel - above) * (k + 1) / (sides + 1)
            val centre = onAxisAt(digit, x)
            if (field.value(centre) >= 0f) return null
            out += LoftRing(centre, flat, cast(centre, flat), nextU(sole.uAt(x)))
        }
        val edge = out.last().found!!
        val middle = edge.fold(Vec3.ZERO) { sum, hit -> sum + hit.point } * (1f / edge.size)
        val centre = Vec3(level, middle.y, middle.z)
        val inner = edge.map { hit -> RayHit(Vec3(level, centre.y + (hit.point.y - centre.y) * INNER, centre.z + (hit.point.z - centre.z) * INNER), hit.buried) }
        out += LoftRing(centre, flat, inner, nextU(sole.uAt(level) + SOLE_U_STEP))
        out += LoftRing(centre, flat, null, nextU(sole.uAt(level) + 2f * SOLE_U_STEP))
        check(out.size == rings.size) { "${out.size} rings for ${rings.size}" }
        return out
    }

    /** A frame level with the floor, facing along the part's axis, its up the nearest to [cast]'s, so v keeps its place round the run. */
    private fun levelFrame(cast: RingFrame): RingFrame {
        val along = Vec3(1f, 0f, 0f)
        val up = (cast.up - along * cast.up.x).normalized()
        return RingFrame(along, up, along cross up)
    }

    /** The point on [digit]'s axis at [x] along the part's axis. */
    private fun onAxisAt(digit: DigitAxis, x: Float): Vec3 = digit.at((x - digit.root.x) / (digit.direction.x * digit.length))
}
