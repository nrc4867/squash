package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** How far a lid's mass reaches into the opening under its edge, in opening heights, so it meets the fills with no gap. */
const val LID_INSET = 0.05f

/** How many chart heights one chart width spans as a lid measures distance where it is not told its opening's own, a drawn eye's usual proportion. */
const val LID_ASPECT = 2.5f

/** Where along the upper lid its mass starts thinning toward the inner corner, and where it has thinned to its inner share. */
const val LID_TAPER_FROM = -0.2f
const val LID_TAPER_TO = 1f

/** Where along the upper lid its mass starts thinning toward the back corner, and the share of its heaviest it keeps at its shoulder. */
const val BACK_TAPER_FROM = -0.2f
const val BACK_END = 0.7f

/** How much thinner the mass is with its lid shut, a share of its weight, so a closed eye is one clean line. */
const val CLOSE_THIN = 0.4f

/** Over what true slope of its open lid, rise over run as the opening lies on the head, a band thins from its own weight to nothing. */
const val STEEP_FROM = 0.8f
const val STEEP_TO = 1.8f

/** How far either side, in widths, a lid's slope is read. */
const val SLOPE_STEP = 0.02f

/** How thick the line is, in opening heights, that carries the mass round the back corner where its bands thin away, so the outline is never bare. */
const val SIDE_LINE = 0.025f

/** Where along the lid, toward the back corner, that line starts and where it is whole. */
const val SIDE_FROM = -0.2f
const val SIDE_TO = -0.5f

/** Over how much of the lid, in widths past its shoulder toward the back corner, the band ends once the lid is shut, so the flick alone runs on. */
const val SHUT_END = 0.1f

/** Over how much of the lower lid, in widths in from its shoulder, the lower band grows to its weight. */
const val LOWER_RAMP = 0.15f

/** How far a lash's root sits in from its lid's shoulder, in widths. */
const val LASH_START = 0.05f

/** How far a lash runs to its tip for each opening height of its length. */
const val LASH_REACH = 2f

/** How much shorter the innermost lash is than the outermost, a share of its length. */
const val LASH_SHORTER = 0.3f

/** The angle above level, in degrees, the outermost and the innermost lash leave the lid at, toward the back corner. */
const val LASH_ANGLE_OUTER = 25f
const val LASH_ANGLE_INNER = 60f

/** How far a lash's way up is bent, so it runs out first and curves up toward its tip. */
const val LASH_BEND = 0.55f

/** How shut a lid is when its lashes start to lower; until then they keep rising off it. */
const val LASH_DROP_FROM = 0.6f

/** The angle above level, in degrees, the outermost and the innermost lash hang at, toward the back corner, with the lid shut. */
const val LASH_CLOSED_OUTER = -30f
const val LASH_CLOSED_INNER = -12f

/** How long a lash hangs with the lid shut, a share of its length open. */
const val LASH_CLOSED_LENGTH = 0.55f

/** How far a lash may dip under the lid's edge, in opening heights, once the lid is shut and the lash hangs from it. */
const val LASH_DIP = 10f

/** The share of its lift a flick keeps with its lid shut, lowering as the lashes do: below 0 it dips, out and down from the closed line. */
const val FLICK_SHUT_LIFT = -0.5f

/** How many degrees a lash drawn as a line swings down, toward the back corner, as its lid lowers it all the way. */
const val LASH_CLOSE_SWING = 70f

/** The power a lash's width falls off by toward its tip, so it ends in a fine point. */
const val LASH_TAPER = 1.6f

/** How far the lid's pieces round into each other where they meet, in opening heights, so a lash or the flick joins the band with no notch. */
const val LID_FILLET = 0.06f

/** The power a lower lid's thickness falls off by toward the inner end of its extent. */
const val LOWER_TAPER_POWER = 0.6f

/** Over how much of the lid, in widths back from the front corner, the upper band comes to a point. */
const val INNER_POINT = 0.3f

/** How many even steps across a lid its nearest place to a point is first looked for at, and how many thirds then close in on it. */
const val CURVE_SAMPLES = 16
const val CURVE_REFINE = 8

/** How many steps a lid's shoulder is looked for in, from its back corner to its middle. */
const val SHOULDER_SAMPLES = 32

/**
 * An eye's lid drawn as a mass of ink about its opening, read in the opening's own chart, riding the opening's lids where they stand
 * (see [ChartLids]): a band over the upper lid's edge [weight] heights thick at its heaviest, thinning to [inner] of that toward the front
 * corner and coming to a point there, and a little toward the back; it thins away where its open lid runs steep, so on an opening taller
 * than wide its weight ends at its [shoulder], where the lid turns down, and a [SIDE_LINE] carries on round the back corner to meet the
 * lower band, so the opening's outline is never bare while it shows; it thins by [CLOSE_THIN] as the lid shuts, so a closed eye is one clean line.
 * From that shoulder a flick [flick] widths past the back corner runs straight out and lifts to a point [flickLift] heights above its
 * root. A band under the lower lid's edge, [lower] of the upper's weight, grows from that line past its own [lowerShoulder], is drawn
 * over [lowerExtent] of the lower lid and thins away where that lid runs steep; and [clumps] lashes reaching [LASH_REACH] times
 * [clumpLength] heights, [clumpWidth] heights thick at the root, rise out of the upper band over [clumpSpread] of the lid in from its
 * shoulder, each leaving the lid toward the back corner and curving up to a fine point, rising so until the lid is [LASH_DROP_FROM]
 * shut and then lowering to hang short, down and out from the shut lid. Each band's thickness is measured square to its lid, widths counted as [aspect] heights, the
 * opening's own width for its height as it lies on the head; the pieces round into each other where they meet.
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
    val clumpWidth: Float = DEFAULT_LASH_WIDTH,
    val aspect: Float = LID_ASPECT,
    val shoulder: Float = -1f,
    val lowerShoulder: Float = -1f,
) {
    /** How far outside the mass ([u], [v]) lies about [opening], in its heights: at most zero inside. */
    fun level(opening: ChartAlmond, u: Float, v: Float): Float {
        val x = u / opening.width
        val y = v / opening.height
        var level = Float.MAX_VALUE
        if (weight > 0f) {
            level = upperBand(opening, x, y)
            if (flick > 0f) level = smoothMin(level, flickStroke(opening).level(x, y), LID_FILLET)
            level = smoothMin(level, lowerBand(opening, x, y), LID_FILLET)
        }
        if (clumps > 0 && clumpLength > 0f) {
            val overLid = opening.topEdge(x.coerceIn(-1f, 1f)) - LID_INSET - LASH_DIP * lashDrop(opening) - y
            for (i in 0 until clumps) level = smoothMin(level, max(lashStroke(opening, i).level(x, y), overLid), LID_FILLET)
        }
        return level * opening.height
    }

    /**
     * The upper band's thickness at [a] along [opening]'s upper lid, in heights: tapering toward each corner, and thinning where the lid
     * runs steep to a [SIDE_LINE] that carries it round the back corner.
     */
    fun thickness(opening: ChartAlmond, a: Float): Float {
        val inward = 1f - (1f - inner) * smoothstep(LID_TAPER_FROM, LID_TAPER_TO, a)
        val back = 1f - (1f - BACK_END) * smoothstep(BACK_TAPER_FROM, min(shoulder, BACK_TAPER_FROM - SLOPE_STEP), a)
        val band = weight * inward * back * flatness(a, aspect, opening::openTop) * innerPoint(a) * closing(opening) * shutEnd(opening, a)
        return max(band, sideLine(opening) * smoothstep(SIDE_FROM, SIDE_TO, a))
    }

    /** How thick the line round the back corner is: [SIDE_LINE] while the opening shows, gone as the lids shut and leave no corner to close. */
    private fun sideLine(opening: ChartAlmond): Float = SIDE_LINE * (1f - lashDrop(opening))

    /** How much of its weight the mass keeps as [opening]'s upper lid closes. */
    private fun closing(opening: ChartAlmond): Float = 1f - CLOSE_THIN * opening.lids.close.coerceIn(0f, 1f)

    private fun lashDrop(opening: ChartAlmond): Float = lashDrop(opening.lids.close)

    /** How much of the upper band is left at [a] once the lid is shut: whole up to the shoulder, ending over [SHUT_END] past it. */
    private fun shutEnd(opening: ChartAlmond, a: Float): Float = 1f - lashDrop(opening) * smoothstep(shoulder, shoulder - SHUT_END, a)

    private fun innerPoint(a: Float): Float = ((1f - a) / INNER_POINT).coerceIn(0f, 1f)

    private fun upperBand(opening: ChartAlmond, x: Float, y: Float): Float {
        val (a, distance) = nearest(x, y, opening::topEdge)
        val inside = abs(x) < 1f && y < opening.topEdge(x)
        val side = if (inside) -distance else distance
        return max(side - thickness(opening, a), -side - LID_INSET * innerPoint(a) * flatness(a, aspect, opening::openTop) * shutEnd(opening, a))
    }

    /** The flick: rooted across the upper band's end at its shoulder, running straight out past the back corner and lifting to its tip. */
    private fun flickStroke(opening: ChartAlmond): TaperedStroke {
        val end = thickness(opening, shoulder)
        val rootY = opening.topEdge(shoulder) + (end - LID_INSET) / 2f
        val lift = flickLift * (1f + (FLICK_SHUT_LIFT - 1f) * lashDrop(opening))
        return TaperedStroke(shoulder, rootY, -1f - flick - shoulder, lift, (end + LID_INSET) / 2f, bendX = 0f, bendY = 1f, aspect = aspect)
    }

    /**
     * Lash [i], rooted in the middle of the upper band, leaving the lid toward the back corner and curving up to a fine point; as the lid
     * nears shut it lowers to hang short, down and out, its root no wider than the band.
     */
    private fun lashStroke(opening: ChartAlmond, i: Int): TaperedStroke {
        val k = (i + 0.5f) / clumps
        val drop = lashDrop(opening)
        val rootX = shoulder + LASH_START + 2f * clumpSpread * k
        val band = thickness(opening, rootX)
        val rootY = opening.topEdge(rootX) + 0.5f * band
        val length = clumpLength * LASH_REACH * (1f - LASH_SHORTER * k) * (1f - (1f - LASH_CLOSED_LENGTH) * drop)
        val open = LASH_ANGLE_OUTER + (LASH_ANGLE_INNER - LASH_ANGLE_OUTER) * k
        val closed = LASH_CLOSED_OUTER + (LASH_CLOSED_INNER - LASH_CLOSED_OUTER) * k
        val angle = (open + (closed - open) * drop) * PI.toFloat() / 180f
        val root = clumpWidth + (min(clumpWidth, 0.5f * band) - clumpWidth) * drop
        return TaperedStroke(rootX, rootY, -length * cos(angle) / aspect, length * sin(angle), root, bendX = 0f, bendY = LASH_BEND * (1f - drop), aspect = aspect, taper = LASH_TAPER)
    }

    /**
     * The band under the lower lid: from a [SIDE_LINE] round the back corner it grows over [LOWER_RAMP] in from the lower shoulder to its
     * weight, then thins toward the inner end of its extent.
     */
    private fun lowerBand(opening: ChartAlmond, x: Float, y: Float): Float {
        val (a, distance) = nearest(x, y, opening::bottomEdge)
        val inside = abs(x) < 1f && y > opening.bottomEdge(x)
        val side = if (inside) -distance else distance
        val s = ((a - lowerShoulder) / (2f * lowerExtent)).coerceIn(0f, 1f)
        val ramp = smoothstep(lowerShoulder, lowerShoulder + LOWER_RAMP, a)
        val banded = lower > 0f && lowerExtent > 0f
        val fade = if (banded) (1f - s).pow(LOWER_TAPER_POWER) * flatness(a, aspect, opening::openBottom) * ramp else 0f
        val thick = max(lower * weight * fade * closing(opening), sideLine(opening) * (1f - ramp))
        if (thick <= 0f) return Float.MAX_VALUE
        val end = (a - lowerShoulder - (if (banded) 2f * lowerExtent else LOWER_RAMP)) * aspect
        return max(max(side - thick, -side - LID_INSET * fade), end)
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

    companion object {
        /** A lash's half width at its root unless told, in opening heights. */
        const val DEFAULT_LASH_WIDTH = 0.07f

        /**
         * Where along [opening]'s open upper lid, or with [lowerLid] its lower, from the back corner toward the middle, the lid first runs
         * no steeper than halfway between [STEEP_FROM] and [STEEP_TO], widths counted as [aspect] heights: the back corner where it never runs steep.
         */
        fun shoulderOf(opening: ChartAlmond, aspect: Float, lowerLid: Boolean = false): Float {
            val edge: (Float) -> Float = if (lowerLid) opening::openBottom else opening::openTop
            for (i in 0..SHOULDER_SAMPLES) {
                val a = -1f + i.toFloat() / SHOULDER_SAMPLES
                if (flatness(a, aspect, edge) >= 0.5f) return a
            }
            return 0f
        }
    }
}

/** How far an eye's lashes have lowered with its upper lid [close] of the way shut: 0 until it is [LASH_DROP_FROM] shut, 1 shut. */
fun lashDrop(close: Float): Float = smoothstep(LASH_DROP_FROM, 1f, close)

/** How level a lid of heights [edge] runs at [a], 1 flat to 0 where its true slope, widths counted as [aspect] heights, passes [STEEP_TO]. */
internal fun flatness(a: Float, aspect: Float, edge: (Float) -> Float): Float {
    val from = max(a - SLOPE_STEP, -1f)
    val to = min(a + SLOPE_STEP, 1f)
    val slope = abs(edge(to) - edge(from)) / ((to - from) * aspect)
    return 1f - smoothstep(STEEP_FROM, STEEP_TO, slope)
}

/** 0 at [from], 1 at [to] and eased between, either way round. */
internal fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
