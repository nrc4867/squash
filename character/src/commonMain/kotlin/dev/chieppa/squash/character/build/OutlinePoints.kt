package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.nearest
import dev.chieppa.squash.geom.splitAt
import dev.chieppa.squash.geom.withoutAnchor

private const val PER_SECTION = 3

/** An outline with an anchor added, and that anchor's index among its points. */
data class AddedPoint(val curve: OutlineCurve, val index: Int)

/**
 * [curve] with an anchor added at its place nearest [at], its section split there so the curve's shape stays exactly as it
 * was; null if that place is further than [reach] from [at] or is an anchor already.
 */
fun withPointAdded(curve: OutlineCurve, at: CurvePoint, reach: Float): AddedPoint? {
    val path = curve.toPath()
    val spot = path.nearest(Vec2(at.x, at.y))
    if (spot.distance > reach || spot.t <= 0f || spot.t >= 1f) return null
    return AddedPoint(path.splitAt(spot.section, spot.t).toOutline(), (spot.section + 1) * PER_SECTION)
}

/** [curve] without the anchor at point [index], its two sections refitted as one; null for an open end, a handle or too few sections. */
fun withPointDeleted(curve: OutlineCurve, index: Int): OutlineCurve? {
    if (index % PER_SECTION != 0) return null
    val closed = curve.points.first() == curve.points.last()
    val join = if (closed && index == curve.points.lastIndex) 0 else index / PER_SECTION
    return curve.toPath().withoutAnchor(join)?.toOutline()
}
