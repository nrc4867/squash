package dev.chieppa.squash.geom

import kotlin.math.max

private const val CAP_SPAN = 1f / 6f

/** A section a view sets: for the whole run, or blended in toward the ends a cap closes. */
class FrontSection private constructor(
    private val spline: ClosedSpline,
    private val whole: Boolean,
    private val capStart: Boolean,
    private val capEnd: Boolean,
) {
    /** The section at [u] over [base]. */
    fun at(u: Float, base: ClosedSpline): ClosedSpline {
        if (whole) return spline
        val fromStart = if (capStart) 1f - u / CAP_SPAN else 0f
        val fromEnd = if (capEnd) 1f - (1f - u) / CAP_SPAN else 0f
        val weight = max(fromStart, fromEnd)
        return when {
            weight <= 0f -> base
            weight >= 1f -> spline
            else -> base.lerp(spline, weight)
        }
    }

    companion object {
        fun whole(spline: ClosedSpline) = FrontSection(spline, whole = true, capStart = false, capEnd = false)

        fun caps(spline: ClosedSpline, start: Boolean, end: Boolean) = FrontSection(spline, whole = false, start, end)
    }
}
