package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.CubicBezier
import dev.chieppa.squash.geom.PartExtents
import dev.chieppa.squash.geom.Span
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val POINTS_PER_SEGMENT = 3
private const val SAMPLES_PER_SEGMENT = 16

/** Below this a line's run on one screen axis counts as none, so a square line is not clipped against that axis. */
private const val MIN_RUN = 1e-5f

/** How far Side reaches: along the part's axis, and along its up direction. */
data class SideExtents(val along: Span, val up: Span)

/** A straight line in pane pixels. */
data class PaneLine(val start: Offset, val end: Offset)

/** Lines across a pane. */
data class PaneGuides(val lines: List<PaneLine> = emptyList())

/** Side's reach from the socket in body units: its drawn [outline], multiplied by [scale], when there is one, else what the part's mesh [extents] show there. */
fun sideExtents(outline: OutlineCurve?, extents: PartExtents, scale: Float = 1f): SideExtents {
    val points = outline?.let(::outlineSamples) ?: return SideExtents(extents.along, extents.up)
    return SideExtents(Span(points.minOf { it.x } * scale, points.maxOf { it.x } * scale), Span(points.minOf { it.y } * scale, points.maxOf { it.y } * scale))
}

/** [outline] flattened to a polyline in its view's plane, each join repeated. */
fun outlineSamples(outline: OutlineCurve): List<CurvePoint> {
    val points = outline.points.map { Vec2(it.x, it.y) }
    return (0 until (points.size - 1) / POINTS_PER_SEGMENT).flatMap { segment ->
        val at = segment * POINTS_PER_SEGMENT
        CubicBezier(points[at], points[at + 1], points[at + 2], points[at + 3]).sample(SAMPLES_PER_SEGMENT).map { CurvePoint(it.x, it.y) }
    }
}

/**
 * Side's reach in [camera]'s pane, as lines square to the part's own directions: Side shows its own both ways, Top shares
 * its axis and takes its ends, and Front shares its height direction and takes its top and bottom.
 */
fun paneGuides(camera: LockedCamera, side: SideExtents): PaneGuides {
    val ends = camera.linesAcross(camera.axes.along, side.along)
    val heights = camera.linesAcross(camera.axes.up, side.up)
    return PaneGuides(
        when (camera.view) {
            View.SIDE -> ends + heights
            View.TOP -> ends
            View.FRONT -> heights
        },
    )
}

private fun LockedCamera.linesAcross(direction: Vec3, reach: Span): List<PaneLine> =
    listOf(reach.min, reach.max).map { lineAcross(direction, it) }

/**
 * The line across the pane square to [direction], one of the view's plane directions, where it has come [reach] body units
 * from the socket.
 */
fun LockedCamera.lineAcross(direction: Vec3, reach: Float): PaneLine {
    val at = toPixel(origin + direction * reach)
    val square = Offset(direction dot basis.screenUp, direction dot basis.screenRight)
    return clippedToPane(at, square)
}

/** The stretch inside the pane of the line through [at], a point in it or beyond it, running along [way]. */
private fun LockedCamera.clippedToPane(at: Offset, way: Offset): PaneLine {
    var from = Float.NEGATIVE_INFINITY
    var to = Float.POSITIVE_INFINITY
    fun clip(start: Float, run: Float, size: Float) {
        if (abs(run) < MIN_RUN) return
        val near = (0f - start) / run
        val far = (size - start) / run
        from = max(from, min(near, far))
        to = min(to, max(near, far))
    }
    clip(at.x, way.x, width.toFloat())
    clip(at.y, way.y, height.toFloat())
    return PaneLine(at + way * from, at + way * to)
}
