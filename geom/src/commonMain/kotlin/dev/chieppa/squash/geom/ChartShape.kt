package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An almond about the origin of a chart, u forward and v up: its corners [width] either side along u, its top [height] above the middle
 * and its bottom [lowerLid] of that below, both narrowed toward its back corner (negative u) by [frontNarrowing].
 */
data class ChartAlmond(val width: Float, val height: Float, val lowerLid: Float, val frontNarrowing: Float) {
    /** Where its rim lies at [angle] radians round it, from its back corner over the top. */
    fun rim(angle: Double): SurfaceUv {
        val along = cos(angle).toFloat()
        val over = sin(angle).toFloat()
        val open = over * (if (over > 0f) 1f else lowerLid) * (1f - frontNarrowing * along)
        return SurfaceUv(-width * along, height * open)
    }

    /** How far outside it ([u], [v]) lies, along v where it lies between the corners: at most zero inside. */
    fun level(u: Float, v: Float): Float {
        val a = (u / width).coerceIn(-1f, 1f)
        val open = sqrt(max(1f - a * a, 0f)) * (1f + frontNarrowing * a)
        return max(max(v - height * open, -height * lowerLid * open - v), (abs(u) - width) * height / width)
    }
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
 * [turn] degrees about the chart's origin, and, where given, inside [oval], turned [ovalTurn] degrees about the chart's origin. A
 * renderer reads it at each fragment, so its edge is smooth however the mesh's cells cross it.
 */
class ChartShape(val chart: FloatArray, val opening: ChartAlmond, val oval: ChartOval?, val turn: Float, val ovalTurn: Float = 0f) {
    /** How far outside the shape a point at ([u], [v]) in the chart lies: at most zero inside. */
    fun level(u: Float, v: Float): Float {
        val inOpening = unturned(u, v, turn) { pu, pv -> opening.level(pu, pv) }
        return if (oval == null) inOpening else max(inOpening, unturned(u, v, ovalTurn) { pu, pv -> oval.level(pu, pv) })
    }

    private inline fun unturned(u: Float, v: Float, degrees: Float, level: (Float, Float) -> Float): Float {
        val angle = degrees * PI.toFloat() / 180f
        val c = cos(angle)
        val s = sin(angle)
        return level(u * c + v * s, -u * s + v * c)
    }
}
