package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A stroke this much shallower than its width across the plane is a bump, not a shape cut by the plane. */
private const val MIN_DEPTH_OF_WIDTH = 0.25f

/**
 * How far from the plane an ellipse through a stroke may centre, as shares of the stroke's depth: behind the plane leaves
 * less than half of the ellipse, ahead of it more. The behind limit keeps a third of the ellipse's way round, and the ahead
 * limit keeps its ends from pinching shut.
 */
private const val MOST_CENTRE_BEHIND = -1f
private const val MOST_CENTRE_AHEAD = 0.4f
private const val CENTRE_STEPS = 14

/** How much coarser the search for an ellipse's centre samples the stroke and the ellipse than the final comparison. */
private const val SEARCH_COARSENESS = 4

/**
 * The ellipse or capsule that a cut at [plane] would leave as [line], or null if it reads as neither. Both of [line]'s ends
 * must pass the plane or come within closing reach of it, with nothing else of it on the kept side. The shape is whole, so it
 * reaches behind the plane and takes the cut every stroke takes. The midpoints of an ellipse's parallel chords lie on one
 * diameter, as does the centre of what any of those chords cuts off, so the line from the middle of [line]'s mouth through
 * the centre of what it encloses holds the ellipse's centre, and what is left to search is where along it the centre lies.
 * A capsule has that same line for its axis.
 */
internal fun shapeAcrossPlane(line: List<Vec2>, plane: StrokeEnds.CutAtPlane, pixel: Float): StrokeShape? {
    val sign = if (plane.above) 1f else -1f
    fun past(p: Vec2) = (p.x - plane.plane) * sign
    val cut = cutAtPlane(line, closed = false, plane, strokeSpacing(pixel)) ?: return null
    val run = cut.run.points
    val reach = closeGap(pixel)
    if (past(run.first()) > reach || past(run.last()) > reach) return null
    if (cut.run.rest.any { piece -> piece.line.any { past(it) > reach } }) return null
    val open = cut.line
    val moments = RingMoments.of(open + open.first()) ?: return null
    val mouth = (open.first() + open.last()) * 0.5f
    val halfWidth = (open.last() - open.first()) * 0.5f
    val depth = open.maxOf(::past)
    if (depth < MIN_DEPTH_OF_WIDTH * 2f * halfWidth.magnitude || past(moments.center) <= 0f) return null
    val toDeepest = (moments.center - mouth) * (depth / past(moments.center))
    val ellipse = ellipseThrough(run, mouth, toDeepest, halfWidth, line.first(), moments.counterClockwise, pixel)
    val capsule = capsuleFrom(run, mouth, toDeepest.unit(), line.first(), moments.counterClockwise)
    return betterFit(run, ellipse, capsule, sqrt(2f * moments.area), pixel)
}

/** Of the ellipses through the mouth's ends and the mouth's middle plus [toDeepest], the one [run] strays from least. */
private fun ellipseThrough(run: List<Vec2>, mouth: Vec2, toDeepest: Vec2, halfWidth: Vec2, start: Vec2, counterClockwise: Boolean, pixel: Float): EllipseShape {
    val spacing = SEARCH_COARSENESS * strokeSpacing(pixel)
    val coarse = run.filterIndexed { i, _ -> i % SEARCH_COARSENESS == 0 }
    return (0..CENTRE_STEPS)
        .map { step ->
            val ahead = MOST_CENTRE_BEHIND + (MOST_CENTRE_AHEAD - MOST_CENTRE_BEHIND) * step / CENTRE_STEPS
            ellipseCentredAt(ahead, mouth, toDeepest, halfWidth, start, counterClockwise)
        }
        .minBy { Stray.of(coarse, it.outline(spacing)).rms }
}

/** The ellipse centred the share [ahead] of [toDeepest] from [mouth], reaching the mouth's ends and the deepest point. */
private fun ellipseCentredAt(ahead: Float, mouth: Vec2, toDeepest: Vec2, halfWidth: Vec2, start: Vec2, counterClockwise: Boolean): EllipseShape {
    val along = toDeepest * (1f - ahead)
    val mouthCosine = ahead / (1f - ahead)
    val across = halfWidth * (1f / sqrt(1f - mouthCosine * mouthCosine))
    val xx = along.x * along.x + across.x * across.x
    val yy = along.y * along.y + across.y * across.y
    val xy = along.x * along.y + across.x * across.y
    val angle = atan2(2f * xy, xx - yy) / 2f
    val spread = sqrt((xx - yy) * (xx - yy) / 4f + xy * xy)
    val mean = (xx + yy) / 2f
    val major = Vec2(cos(angle), sin(angle)) * sqrt(mean + spread)
    return EllipseShape(mouth + toDeepest * ahead, major, sqrt(max(mean - spread, 0f)), start, counterClockwise)
}

/** The capsule on the axis from [mouth] along [axis], centred on the mouth, reaching [run]'s far extent, with the mean width of its straight stretch; null if that leaves no straight stretch. */
private fun capsuleFrom(run: List<Vec2>, mouth: Vec2, axis: Vec2, start: Vec2, counterClockwise: Boolean): CapsuleShape? {
    val across = Vec2(0f - axis.y, axis.x)
    val alongs = run.map { (it - mouth) dotWith axis }
    val offsets = run.map { abs((it - mouth) dotWith across) }
    val far = alongs.max()
    val widest = offsets.max()
    val straight = offsets.filterIndexed { i, _ -> alongs[i] in 0f..(far - widest) }
    if (straight.isEmpty()) return null
    val radius = straight.average().toFloat()
    val halfRun = far - radius
    if (halfRun < MIN_CAPSULE_RUN_OF_RADIUS * radius) return null
    return CapsuleShape(mouth - axis * halfRun, mouth + axis * halfRun, radius, start, counterClockwise)
}
