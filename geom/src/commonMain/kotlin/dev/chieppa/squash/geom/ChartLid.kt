package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** How far a lid's mass reaches into the opening under its edge, in opening heights, so it meets the fills with no gap. */
const val LID_INSET = 0.05f

/** How far, in widths, the flick runs back into the upper band at the back corner, so no seam shows where they meet. */
const val LID_SEAM = 0.03f

/** How many chart heights one chart width spans as a lid measures distance, a drawn eye's usual proportion. */
const val LID_ASPECT = 2.5f

/** Where along the upper lid its mass starts thinning toward the inner corner, and where it has thinned to its inner share. */
const val LID_TAPER_FROM = -0.2f
const val LID_TAPER_TO = 1f

/** A lash clump's sweep toward the back corner, chart widths for each height it rises: the innermost's and how much more the outermost takes. */
const val CLUMP_SWEEP = 0.12f
const val CLUMP_SWEEP_MORE = 0.38f

/** How much shorter the innermost clump is than the outermost, a share of its length. */
const val CLUMP_SHORTER = 0.3f

/** A lash clump's half width at its root, in chart heights, and its share of the clump's length on top. */
const val CLUMP_ROOT = 0.12f
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
 * at its heaviest, thinning to [inner] of that toward the front corner and coming to a point there; past the back corner a flick [flick]
 * widths long whose tip rises [flickLift] heights; a band under the lower lid [lower] of the upper's weight, drawn over [lowerExtent] of the
 * lower lid from the back corner, thinning to nothing at its inner end; and [clumps] lash wedges [clumpLength] heights long, growing out of
 * the upper band over [clumpSpread] of the lid from the back corner, each swept toward it. Each band's thickness is measured square to its
 * lid, widths counted as [LID_ASPECT] heights, so the mass wraps round where the lid runs steep near a corner.
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
) {
    /** How far outside the mass ([u], [v]) lies about [opening], in its heights: at most zero inside. */
    fun level(opening: ChartAlmond, u: Float, v: Float): Float {
        val x = u / opening.width
        val y = v / opening.height
        var level = Float.MAX_VALUE
        if (weight > 0f) {
            level = min(level, upperBand(opening, x, y))
            if (flick > 0f) level = min(level, flickBand(x, y))
            if (lower > 0f && lowerExtent > 0f) level = min(level, lowerBand(opening, x, y))
        }
        if (clumps > 0 && clumpLength > 0f) for (i in 0 until clumps) level = min(level, clump(opening, i, x, y))
        return level * opening.height
    }

    /** The upper band's thickness at [a] along the lid, in heights. */
    fun thickness(a: Float): Float = weight * (1f - (1f - inner) * smoothstep(LID_TAPER_FROM, LID_TAPER_TO, a)) * innerPoint(a)

    private fun innerPoint(a: Float): Float = ((1f - a) / INNER_POINT).coerceIn(0f, 1f)

    private fun upperBand(opening: ChartAlmond, x: Float, y: Float): Float {
        val (a, distance) = nearest(x, y, opening::upper)
        val inside = abs(x) < 1f && y < opening.upper(x)
        val side = if (inside) -distance else distance
        return max(side - thickness(a), -side - LID_INSET * innerPoint(a))
    }

    private fun flickBand(x: Float, y: Float): Float {
        val s = ((-1f - x) / flick).coerceIn(0f, 1f)
        val low = -LID_INSET + (flickLift + LID_INSET) * s * s
        val start = thickness(-1f)
        val high = start + (flickLift - start) * s
        return max(max(low - y, y - high), max((x + 1f - LID_SEAM) * LID_ASPECT, (-1f - flick - x) * LID_ASPECT))
    }

    private fun lowerBand(opening: ChartAlmond, x: Float, y: Float): Float {
        val bottom = { a: Float -> -opening.lowerLid * opening.lower(a) }
        val (a, distance) = nearest(x, y, bottom)
        val inside = abs(x) < 1f && y > bottom(x)
        val side = if (inside) -distance else distance
        val s = ((a + 1f) / (2f * lowerExtent)).coerceIn(0f, 1f)
        val fade = (1f - s).pow(LOWER_TAPER_POWER)
        return max(max(side - lower * weight * fade, -side - LID_INSET * fade), (a + 1f - 2f * lowerExtent) * LID_ASPECT)
    }

    private fun clump(opening: ChartAlmond, i: Int, x: Float, y: Float): Float {
        val k = (i + 0.5f) / clumps
        val rootX = -1f + 2f * clumpSpread * k
        val rootY = opening.upper(rootX) + 0.5f * thickness(rootX)
        val length = clumpLength * (1f - CLUMP_SHORTER * k)
        val sweep = CLUMP_SWEEP + CLUMP_SWEEP_MORE * (1f - k)
        val dx = -sweep * length * LID_ASPECT
        val dy = length
        val reach = sqrt(dx * dx + dy * dy)
        val px = (x - rootX) * LID_ASPECT
        val py = y - rootY
        val t = (px * dx + py * dy) / (reach * reach)
        val across = abs(px * dy - py * dx) / reach
        val half = (CLUMP_ROOT + CLUMP_ROOT_SHARE * length) * (1f - t)
        return max(across - half, max(-t * reach, (t - 1f) * reach))
    }

    /** The place along a lid, [edge] of it its height, nearest ([x], [y]), and how far that is, widths counted as [LID_ASPECT] heights. */
    private fun nearest(x: Float, y: Float, edge: (Float) -> Float): Pair<Float, Float> {
        fun apart(a: Float): Float {
            val dx = (x - a) * LID_ASPECT
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
