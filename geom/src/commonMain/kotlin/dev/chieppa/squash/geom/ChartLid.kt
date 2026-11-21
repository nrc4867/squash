package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** How far a lid's mass reaches into the opening under its edge, in opening heights, so it meets the fills with no gap. */
const val LID_INSET = 0.05f

/** How many chart heights one chart width spans as a lid measures distance where it is not told its opening's own, a drawn eye's usual proportion. */
const val LID_ASPECT = 2.5f

/** Where along the upper lid its mass starts thinning toward the inner corner, and where it has thinned to its inner share. */
const val LID_TAPER_FROM = -0.2f
const val LID_TAPER_TO = 1f

/**
 * How thick the upper band still is at the back corner, a share of its heaviest, and from how high the lid stands, in heights, and from
 * where along it, it thins to that.
 */
const val OUTER_END = 0.15f
const val OUTER_HEIGHT = 0.85f
const val OUTER_TAPER_FROM = -0.4f

/** How thick the flick is at its root at the back corner, a share of the upper band's heaviest. */
const val FLICK_ROOT = 0.45f

/** How far the lid's pieces round into each other where they meet, in opening heights, so a clump or the flick joins the band with no notch. */
const val LID_FILLET = 0.06f

/** A lash clump's sweep toward the back corner at its tip, chart widths for each height it rises: the innermost's and how much more the outermost takes. */
const val CLUMP_SWEEP = 0.25f
const val CLUMP_SWEEP_MORE = 0.45f

/** How far in from the back corner, in widths, the clumps' spread starts, where the lid has risen onto its top. */
const val CLUMP_START = 0.2f

/** How much shorter the innermost clump is than the outermost, a share of its length. */
const val CLUMP_SHORTER = 0.3f

/** A lash clump's half width at its root, in chart heights, and its share of the clump's length on top. */
const val CLUMP_ROOT = 0.08f
const val CLUMP_ROOT_SHARE = 0.25f

/** The power a lower lid's thickness falls off by toward the inner end of its extent. */
const val LOWER_TAPER_POWER = 0.6f

/** Over how much of the lid, in widths back from the front corner, the upper band comes to a point. */
const val INNER_POINT = 0.3f

/** How many even steps across a lid its nearest place to a point is first looked for at, and how many thirds then close in on it. */
const val CURVE_SAMPLES = 16
const val CURVE_REFINE = 8

/**
 * An eye's lid drawn as a mass of ink about its opening, read in the opening's own chart: a band over the upper lid [weight] heights thick
 * at its heaviest, thinning to [inner] of that toward the front corner and coming to a point there, and to a line where it drops to the
 * back corner; at the back corner a flick [flick] widths long, running straight out and lifting to a point [flickLift] heights up; a band
 * under the lower lid [lower] of the upper's weight, drawn over [lowerExtent] of the lower lid from the back corner, thinning to nothing at
 * its inner end; and [clumps] lash clumps [clumpLength] heights long, each a curved wedge rising out of the upper band over [clumpSpread]
 * of the lid from [CLUMP_START] in from the back corner, sweeping toward it and kept over the lid. Each band's thickness is measured
 * square to its lid, widths counted as [aspect] heights, the opening's own width for its height as it lies on the head, so the mass
 * follows the opening's shape however wide or tall it is and wraps round where the lid runs steep; the pieces round into each other where
 * they meet.
 */
data class ChartLid(
    val weight: Float,
    val inner: Float = 0.15f,
    val flick: Float = 0f,
    val flickLift: Float = 0f,
    val lower: Float = 0f,
    val lowerExtent: Float = 1f,
    val clumps: Int = 0,
    val clumpLength: Float = 0f,
    val clumpSpread: Float = 0.35f,
    val aspect: Float = LID_ASPECT,
) {
    /** How far outside the mass ([u], [v]) lies about [opening], in its heights: at most zero inside. */
    fun level(opening: ChartAlmond, u: Float, v: Float): Float {
        val x = u / opening.width
        val y = v / opening.height
        var level = Float.MAX_VALUE
        if (weight > 0f) {
            level = upperBand(opening, x, y)
            if (flick > 0f) level = smoothMin(level, flickStroke().level(x, y), LID_FILLET)
            if (lower > 0f && lowerExtent > 0f) level = smoothMin(level, lowerBand(opening, x, y), LID_FILLET)
        }
        if (clumps > 0 && clumpLength > 0f) {
            val overLid = opening.upper(x.coerceIn(-1f, 1f)) - LID_INSET - y
            for (i in 0 until clumps) level = smoothMin(level, max(clumpStroke(opening, i).level(x, y), overLid), LID_FILLET)
        }
        return level * opening.height
    }

    /**
     * The upper band's thickness at [a] along [opening]'s upper lid, in heights: thinning toward the back corner as the lid drops to it,
     * so the band runs down the opening's back side as a line and goes on as the flick.
     */
    fun thickness(opening: ChartAlmond, a: Float): Float {
        val inward = 1f - (1f - inner) * smoothstep(LID_TAPER_FROM, LID_TAPER_TO, a)
        val back = a < 0f
        val drop = smoothstep(0f, OUTER_HEIGHT, opening.upper(a)) * smoothstep(-1f, OUTER_TAPER_FROM, a)
        val outward = if (back) OUTER_END + (1f - OUTER_END) * drop else 1f
        return weight * inward * outward * innerPoint(a)
    }

    private fun innerPoint(a: Float): Float = ((1f - a) / INNER_POINT).coerceIn(0f, 1f)

    private fun upperBand(opening: ChartAlmond, x: Float, y: Float): Float {
        val (a, distance) = nearest(x, y, opening::upper)
        val inside = abs(x) < 1f && y < opening.upper(x)
        val side = if (inside) -distance else distance
        return max(side - thickness(opening, a), -side - LID_INSET * innerPoint(a))
    }

    /** The flick: rooted at the back corner [FLICK_ROOT] of the band's weight thick, running straight out and lifting to its tip. */
    private fun flickStroke(): TaperedStroke {
        val end = weight * FLICK_ROOT
        val rootY = (end - LID_INSET) / 2f
        return TaperedStroke(-1f, rootY, -flick, flickLift - rootY, (end + LID_INSET) / 2f, bendX = 0f, bendY = 1f, aspect = aspect)
    }

    /** Clump [i], rooted in the middle of the upper band, rising straight off it and sweeping toward the back corner at its tip. */
    private fun clumpStroke(opening: ChartAlmond, i: Int): TaperedStroke {
        val k = (i + 0.5f) / clumps
        val rootX = -1f + CLUMP_START + 2f * clumpSpread * k
        val rootY = opening.upper(rootX) + 0.5f * thickness(opening, rootX)
        val length = clumpLength * (1f - CLUMP_SHORTER * k)
        val sweep = CLUMP_SWEEP + CLUMP_SWEEP_MORE * (1f - k)
        return TaperedStroke(rootX, rootY, -sweep * length, length, CLUMP_ROOT + CLUMP_ROOT_SHARE * length, bendX = 1f, bendY = 0f, aspect = aspect)
    }

    private fun lowerBand(opening: ChartAlmond, x: Float, y: Float): Float {
        val bottom = { a: Float -> -opening.lowerLid * opening.lower(a) }
        val (a, distance) = nearest(x, y, bottom)
        val inside = abs(x) < 1f && y > bottom(x)
        val side = if (inside) -distance else distance
        val s = ((a + 1f) / (2f * lowerExtent)).coerceIn(0f, 1f)
        val fade = (1f - s).pow(LOWER_TAPER_POWER)
        return max(max(side - lower * weight * fade, -side - LID_INSET * fade), (a + 1f - 2f * lowerExtent) * aspect)
    }

    /** The place along a lid, [edge] of it its height, nearest ([x], [y]), and how far that is, widths counted as [aspect] heights. */
    private fun nearest(x: Float, y: Float, edge: (Float) -> Float): Pair<Float, Float> {
        fun apart(a: Float): Float {
            val dx = (x - a) * aspect
            val dy = y - edge(a)
            return dx * dx + dy * dy
        }
        var best = -1f
        var bestApart = Float.MAX_VALUE
        for (i in 0..CURVE_SAMPLES) {
            val a = -1f + 2f * i / CURVE_SAMPLES
            val d = apart(a)
            if (d < bestApart) {
                bestApart = d
                best = a
            }
        }
        var low = max(-1f, best - 2f / CURVE_SAMPLES)
        var high = min(1f, best + 2f / CURVE_SAMPLES)
        repeat(CURVE_REFINE) {
            val first = low + (high - low) / 3f
            val second = high - (high - low) / 3f
            if (apart(first) < apart(second)) high = second else low = first
        }
        val a = (low + high) / 2f
        return a to sqrt(apart(a))
    }

    private fun smoothstep(from: Float, to: Float, x: Float): Float {
        val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
