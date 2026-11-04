package dev.chieppa.squash.geom

/**
 * The curve [t] of the way from [from] to [to], control point by control point: [from] itself at 0 and [to] itself at 1.
 * The two must hold the same number of control points.
 */
fun morphCurve(from: List<Vec2>, to: List<Vec2>, t: Float): List<Vec2> {
    require(from.size == to.size) { "Morphing curves of ${from.size} and ${to.size} control points" }
    return when (t) {
        0f -> from
        1f -> to
        else -> from.indices.map { from[it] + (to[it] - from[it]) * t }
    }
}

/** This path [t] of the way to [other], which must hold the same number of sections. */
fun BezierPath.morphedTo(other: BezierPath, t: Float): BezierPath {
    require(segments.size == other.segments.size) { "Morphing paths of ${segments.size} and ${other.segments.size} sections" }
    return BezierPath(
        segments.indices.map { i ->
            val (p0, p1, p2, p3) = morphCurve(segments[i].controlPoints(), other.segments[i].controlPoints(), t)
            CubicBezier(p0, p1, p2, p3)
        },
    )
}

private fun CubicBezier.controlPoints() = listOf(p0, p1, p2, p3)
