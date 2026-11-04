package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.CubicBezier
import dev.chieppa.squash.geom.PartExtents
import dev.chieppa.squash.geom.Span
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs

private const val POINTS_PER_SEGMENT = 3
private const val SAMPLES_PER_SEGMENT = 16

/** How far Side reaches: along the part's axis, and along its up direction. */
data class SideExtents(val along: Span, val up: Span)

/** Lines across a pane, at pane pixels along [verticals] x and [horizontals] y. */
data class PaneGuides(val verticals: List<Float> = emptyList(), val horizontals: List<Float> = emptyList())

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
 * Side's reach projected into [camera]'s pane: Side shows its own both ways, Top shares its axis direction and takes its
 * ends, and Front shares its height direction and takes its top and bottom.
 */
fun paneGuides(camera: LockedCamera, side: SideExtents): PaneGuides {
    val ends = camera.linesAcross(camera.axes.along, side.along)
    val heights = camera.linesAcross(camera.axes.up, side.up)
    return when (camera.view) {
        View.SIDE -> ends + heights
        View.TOP -> ends
        View.FRONT -> heights
    }
}

private operator fun PaneGuides.plus(other: PaneGuides) = PaneGuides(verticals + other.verticals, horizontals + other.horizontals)

/** Lines at the ends of [reach], a range along [direction] from the socket, each square to the way that direction runs on screen. */
private fun LockedCamera.linesAcross(direction: Vec3, reach: Span): PaneGuides {
    val ends = listOf(toPixel(origin + direction * reach.min), toPixel(origin + direction * reach.max))
    return if (runsAcross(direction)) PaneGuides(verticals = ends.map { it.x }) else PaneGuides(horizontals = ends.map { it.y })
}

/** Whether [direction] runs more across this pane than up it. */
fun LockedCamera.runsAcross(direction: Vec3): Boolean =
    abs(direction dot basis.screenRight) >= abs(direction dot basis.screenUp)
