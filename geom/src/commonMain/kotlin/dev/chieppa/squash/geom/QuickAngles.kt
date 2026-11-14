package dev.chieppa.squash.geom

import kotlin.math.abs

private const val HALF_PI = (kotlin.math.PI / 2.0).toFloat()
private const val PI_F = kotlin.math.PI.toFloat()

/**
 * The angle of ([x], [y]) from the y axis toward the x axis, in radians, as `atan2(x, y)` gives it, to within about 1e-5: the arctangent
 * of the smaller over the larger of |x| and |y| by Abramowitz and Stegun's polynomial 4.4.47, turned into the right quarter.
 */
fun quickAtan2(x: Float, y: Float): Float {
    val ax = abs(x)
    val ay = abs(y)
    if (ax == 0f && ay == 0f) return 0f
    val swapped = ax > ay
    val z = if (swapped) ay / ax else ax / ay
    val z2 = z * z
    var angle = z * (0.9998660f + z2 * (-0.3302995f + z2 * (0.1801410f + z2 * (-0.0851330f + z2 * 0.0208351f))))
    if (swapped) angle = HALF_PI - angle
    if (y < 0f) angle = PI_F - angle
    return if (x < 0f) -angle else angle
}
