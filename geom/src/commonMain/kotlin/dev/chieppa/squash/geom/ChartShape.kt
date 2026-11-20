package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The power of the place across a round lid closes by: 1 less its square, an ellipse's. */
const val ROUND_SPAN = 2f

/** How much higher that power runs on a flat top lid at a flatness of 1, so the lid stays level longer and closes nearer its corners. */
const val FLAT_SPAN = 3f

/** The power a round lid's closing is raised to: an ellipse's half. */
const val ROUND_POWER = 0.5f

/**
 * An almond about the origin of a chart, u forward and v up: its corners [width] either side along u, its top [height] above the middle
 * and its bottom [lowerLid] of that below, both narrowed toward its back corner (negative u) by [frontNarrowing]. A [topFlat] top lid
 * runs flatter, closing nearer its corners, from 0 an ellipse's curve to 1 nearly level; an [outerCorner] sharpens the back corner where
 * the lids meet, from 0 round to 1 a point where they meet at an angle.
 */
data class ChartAlmond(
    val width: Float,
    val height: Float,
    val lowerLid: Float,
    val frontNarrowing: Float,
    val topFlat: Float = 0f,
    val outerCorner: Float = 0f,
) {
    /** Whether its lids are the plain ellipse's, neither flattened nor sharpened. */
    val isPlain: Boolean get() = topFlat == 0f && outerCorner == 0f

    /** Where its rim lies at [angle] radians round it, from its back corner over the top. */
    fun rim(angle: Double): SurfaceUv {
        val along = cos(angle).toFloat()
        val over = sin(angle).toFloat()
        if (isPlain) {
            val open = over * (if (over > 0f) 1f else lowerLid) * (1f - frontNarrowing * along)
            return SurfaceUv(-width * along, height * open)
        }
        val a = -along
        return SurfaceUv(-width * along, if (over > 0f) height * upper(a) else -height * lowerLid * lower(a))
    }

    /** How far its top lid rises at [a], a share of the way from its middle to its front corner (negative toward its back), in heights. */
    fun upper(a: Float): Float = open(a, closing(a, ROUND_SPAN + FLAT_SPAN * topFlat, cornerPower(a)))

    /** How far its bottom lid falls at [a] as [upper] reads it, before its lower lid's share. */
    fun lower(a: Float): Float = open(a, closing(a, ROUND_SPAN, cornerPower(a)))

    /** How far outside it ([u], [v]) lies, along v where it lies between the corners: at most zero inside. */
    fun level(u: Float, v: Float): Float {
        val a = (u / width).coerceIn(-1f, 1f)
        if (isPlain) {
            val open = sqrt(max(1f - a * a, 0f)) * (1f + frontNarrowing * a)
            return max(max(v - height * open, -height * lowerLid * open - v), (abs(u) - width) * height / width)
        }
        return max(max(v - height * upper(a), -height * lowerLid * lower(a) - v), (abs(u) - width) * height / width)
    }

    private fun open(a: Float, closed: Float): Float = closed * (1f + frontNarrowing * a)

    /** How open a lid is at [a] before its narrowing: 1 less [a]'s size to the [span], raised to [power]. */
    private fun closing(a: Float, span: Float, power: Float): Float {
        val c = max(1f - abs(a).pow(span), 0f)
        return if (c <= 0f) 0f else c.pow(power)
    }

    /** The power a lid's closing is raised to at [a]: a half, an ellipse's, rising toward 1 at the back corner as it sharpens. */
    private fun cornerPower(a: Float): Float = ROUND_POWER + (1f - ROUND_POWER) * outerCorner * (-a).coerceIn(0f, 1f)
}

/** An oval about ([u], [v]), [halfU] across and [halfV] up from its middle. */
data class ChartOval(val u: Float, val v: Float, val halfU: Float, val halfV: Float) {
    /** How far outside it ([pu], [pv]) lies, in shares of its lesser half: at most zero inside. */
    fun level(pu: Float, pv: Float): Float {
        val x = (pu - u) / halfU
        val y = (pv - v) / halfV
        return (sqrt(x * x + y * y) - 1f) * min(halfU, halfV)
    }
}

/**
 * A fill kept to a shape read in a chart each vertex of its mesh carries, [chart], two values a vertex, u then v: inside [opening], turned
 * [turn] degrees about the chart's origin, and, where given, inside [oval], turned [ovalTurn] degrees about the chart's origin, and kept
 * by its [cut]. A shape with a [lid] is that lid's ink mass about the opening in place of the opening's inside. A renderer reads it at
 * each fragment, so its edge is smooth however the mesh's cells cross it.
 */
class ChartShape(
    val chart: FloatArray,
    val opening: ChartAlmond,
    val oval: ChartOval?,
    val turn: Float,
    val ovalTurn: Float = 0f,
    val cut: ChartCut? = null,
    val lid: ChartLid? = null,
) {
    /** How far outside the shape a point at ([u], [v]) in the chart lies, its cut's soft edge left out: at most zero inside. */
    fun level(u: Float, v: Float): Float {
        val own = if (lid != null) {
            unturned(u, v, turn) { pu, pv -> lid.level(opening, pu, pv) }
        } else {
            val inOpening = unturned(u, v, turn) { pu, pv -> opening.level(pu, pv) }
            if (oval == null) inOpening else max(inOpening, unturned(u, v, ovalTurn) { pu, pv -> oval.level(pu, pv) })
        }
        val cut = cut ?: return own
        val cutLevel = if (cut.oval != null) unturned(u, v, ovalTurn) { pu, pv -> cut.level(opening, pu, pv) } else unturned(u, v, turn) { pu, pv -> cut.level(opening, pu, pv) }
        return max(own, cutLevel)
    }

    private inline fun unturned(u: Float, v: Float, degrees: Float, level: (Float, Float) -> Float): Float {
        val angle = degrees * PI.toFloat() / 180f
        val c = cos(angle)
        val s = sin(angle)
        return level(u * c + v * s, -u * s + v * c)
    }
}
