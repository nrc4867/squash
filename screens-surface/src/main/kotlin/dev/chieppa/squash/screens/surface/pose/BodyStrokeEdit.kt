package dev.chieppa.squash.screens.surface.pose

import dev.chieppa.squash.character.build.toOutline
import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.BodyStroke
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.ArcPath
import dev.chieppa.squash.geom.BezierPath
import dev.chieppa.squash.geom.CubicBezier
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.StrokeOptions
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.cleanStroke
import dev.chieppa.squash.screens.pose.CurveSpot
import kotlin.math.PI
import kotlin.math.sin

private const val POINTS_PER_SEGMENT = 3

/**
 * The line drawn through [samples] in [view]'s plane as a body stroke: cleaned and fitted as an outline is fitted from a stroke,
 * its ends left where they were drawn, joined cubics in a body stroke's coordinates. [pixel] is a screen pixel's size in the
 * plane. Null for a stroke too short to be one.
 */
fun fitBodyStroke(samples: List<Vec2>, view: View, pixel: Float): BodyStroke? {
    val clean = cleanStroke(samples, StrokeEnds.Closed, pixel, StrokeOptions(snapLine = false, closeEnd = false)) ?: return null
    if (clean.path.segments.isEmpty()) return null
    return BodyStroke(view, clean.path.toOutline().points)
}

/** This curve with [stroke] in place of the one in its view, the other view's kept; a first stroke starts a curve at the body's length. */
fun BodyCurve?.withStroke(stroke: BodyStroke): BodyCurve =
    BodyCurve(this?.strokes.orEmpty().filter { it.view != stroke.view } + stroke, this?.length ?: 1f)

/** Where each of this stroke's handles sits: its first point, its last, and the point halfway along it. */
fun BodyStroke.spots(): Map<CurveSpot, Vec2> {
    val path = arcPath()
    return mapOf(CurveSpot.START to path.points.first(), CurveSpot.MIDDLE to path.pointAt(path.length / 2f), CurveSpot.END to path.points.last())
}

/**
 * This stroke with the handle at [spot] moved [by]: an end moves its point and the handle beside it; the middle bows the curve,
 * every point moving by a share that is whole at the middle and falls to nothing at the ends.
 */
fun BodyStroke.moved(spot: CurveSpot, by: Vec2): BodyStroke {
    val last = points.lastIndex
    val shares = when (spot) {
        CurveSpot.START -> points.indices.map { if (it <= 1) 1f else 0f }
        CurveSpot.END -> points.indices.map { if (it >= last - 1) 1f else 0f }
        CurveSpot.MIDDLE -> points.indices.map { if (it == 0 || it == last) 0f else sin(PI * it / last).toFloat() }
    }
    return copy(points = points.mapIndexed { i, p -> CurvePoint(p.x + by.x * shares[i], p.y + by.y * shares[i]) })
}

/** This stroke as a path measured by arc length. */
fun BodyStroke.arcPath(): ArcPath {
    val p = points.map { Vec2(it.x, it.y) }
    return ArcPath.of(BezierPath((0 until p.size / POINTS_PER_SEGMENT).map { k -> CubicBezier(p[k * 3], p[k * 3 + 1], p[k * 3 + 2], p[k * 3 + 3]) }))
}
