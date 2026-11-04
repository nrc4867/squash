package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/** The least share of a soft brush's radius that stays solid around its curve, so even a fade across the whole radius reaches full color. */
private const val LEAST_CORE_SHARE = 1f / 32f

/** How far a fade's curve may lie from true, as a share of the fade's width; the share of opacity it could be off by, which no eye reads. */
private const val CURVE_FLATNESS = 1f / 200f

/**
 * A soft brush's opacity over its patch: full within its core, nothing past its radius, and linear in the distance to its curve between. The
 * distance is in space, so it needs no case for a seam or a pole, and is held from reaching further around v than a brush's edge does:
 * past [BRUSH_V_REACH] of a turn from the curve the opacity is nothing, and it falls to that in proportion. A brush with a pole in reach
 * covers that pole by distance alone, as its cap does.
 */
internal class FadeField(brush: ColorShape.Brush, private val grid: PatchGrid, tolerance: Float) {
    private val radius = brush.radius
    private val core = max(radius * (1f - brush.softness), radius * LEAST_CORE_SHARE)

    /** How wide the fade is, from its core to its edge. */
    val width: Float = radius - core

    private val curve = flattenedThrough(unwrapped(brush.points, grid.closed), grid, max(tolerance, width * CURVE_FLATNESS))
    private val centers = curve.map { grid.positionOn(grid.gridU(it.u), grid.gridV(it.v)) }
    private val aroundV = DoubleArray(curve.size) { grid.gridV(curve[it].v) }
    private val reachV = BRUSH_V_REACH * grid.columns
    private val holdsReach = !reachesPole()

    private fun reachesPole(): Boolean {
        if (!grid.closed) return false
        fun near(ring: Int) = grid.isPole(ring) && distanceFrom(grid.positionOn(ring.toDouble(), 0.0), 0.0, false) <= radius
        return near(0) || near(grid.rings - 1)
    }

    /** How far the place at [at], [gridV] around the patch, lies from the curve. */
    fun distanceAt(at: Vec3, gridV: Double): Float = distanceFrom(at, gridV, holdsReach)

    /** Where a [distance] from the curve lies in the fade: 0 at its edge and 1 at its core, running on past both. */
    fun levelAt(distance: Float): Float = (radius - distance) / width

    /** Whether a stretch [length] long around a place at [distance] lies wholly past the brush's edge. */
    fun isWhollyOutside(distance: Float, length: Float): Boolean = distance - length >= radius

    /** Whether a stretch [length] long around a place at [distance] lies wholly within the solid core. */
    fun isWhollyInCore(distance: Float, length: Float): Boolean = distance + length <= core

    private fun distanceFrom(at: Vec3, gridV: Double, holding: Boolean): Float {
        if (centers.size == 1) return held((at - centers[0]).length, aroundV[0], gridV, holding)
        var best = Float.MAX_VALUE
        for (i in 0 until centers.lastIndex) {
            val a = centers[i]
            val along = centers[i + 1] - a
            val square = along dot along
            val t = if (square > 0f) (((at - a) dot along) / square).coerceIn(0f, 1f) else 0f
            val distance = held((a + along * t - at).length, aroundV[i] + (aroundV[i + 1] - aroundV[i]) * t, gridV, holding)
            if (distance < best) best = distance
        }
        return best
    }

    private fun held(distance: Float, fromV: Double, gridV: Double, holding: Boolean): Float {
        if (!holding) return distance
        val gap = gridV - fromV
        val around = if (grid.closed) abs(gap - round(gap / grid.columns) * grid.columns) else abs(gap)
        return max(distance, (radius * around / reachV).toFloat())
    }
}
