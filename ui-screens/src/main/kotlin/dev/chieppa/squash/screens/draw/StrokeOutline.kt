package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.cleanStroke

/** A stroke as the outline it makes, with how far that strays from the stroke at worst, in screen pixels. */
data class FittedOutline(val curve: OutlineCurve, val errorPixels: Float)

/**
 * How a stroke in [view] ends on a part whose template opens at [openEnd]. Side and Top run along the axis, so they meet
 * the socket plane at along = 0; Front looks down the axis and never does.
 */
fun strokeEnds(view: View, openEnd: End?): StrokeEnds = when {
    view == View.FRONT || openEnd == null -> StrokeEnds.Closed
    else -> StrokeEnds.CutAtPlane(above = openEnd == End.START)
}

/** [samples] in a view's plane, cleaned up as [ends] says, or null if they make no outline. [pixel] is a screen pixel's size in the plane. */
fun fittedOutline(samples: List<CurvePoint>, ends: StrokeEnds, pixel: Float): FittedOutline? {
    val stroke = cleanStroke(samples.map { Vec2(it.x, it.y) }, ends, pixel) ?: return null
    val segments = stroke.path.segments
    val points = buildList {
        add(segments.first().p0.toCurvePoint())
        for (segment in segments) {
            add(segment.p1.toCurvePoint())
            add(segment.p2.toCurvePoint())
            add(segment.p3.toCurvePoint())
        }
    }
    return FittedOutline(OutlineCurve(points), stroke.maxError / pixel)
}

private fun Vec2.toCurvePoint() = CurvePoint(x, y)
