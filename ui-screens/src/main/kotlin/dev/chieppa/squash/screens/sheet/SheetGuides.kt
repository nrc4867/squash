package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.CubicBezier
import dev.chieppa.squash.geom.Vec2

private const val POINTS_PER_SEGMENT = 3
private const val SAMPLES_PER_SEGMENT = 16

/** How far Side reaches: along the part's axis, and along its up direction. */
data class SideExtents(val along: Span, val up: Span)

/** Lines across a pane, at pane pixels along [verticals] x and [horizontals] y. */
data class PaneGuides(val verticals: List<Float> = emptyList(), val horizontals: List<Float> = emptyList())

/** Side's reach: its drawn [outline] when there is one, else what the part's mesh [extents] show there. */
fun sideExtents(outline: OutlineCurve?, extents: PartExtents): SideExtents {
    val points = outline?.let(::samples) ?: return SideExtents(extents.along, extents.up)
    return SideExtents(Span(points.minOf { it.x }, points.maxOf { it.x }), Span(points.minOf { it.y }, points.maxOf { it.y }))
}

private fun samples(outline: OutlineCurve): List<Vec2> {
    val points = outline.points.map { Vec2(it.x, it.y) }
    return (0 until (points.size - 1) / POINTS_PER_SEGMENT).flatMap { segment ->
        val at = segment * POINTS_PER_SEGMENT
        CubicBezier(points[at], points[at + 1], points[at + 2], points[at + 3]).sample(SAMPLES_PER_SEGMENT)
    }
}

/**
 * Side's reach projected into [camera]'s pane: Side shows its own both ways, Top shares its axis direction and takes its
 * ends, and Front shares its height direction and takes its top and bottom.
 */
fun paneGuides(camera: LockedCamera, side: SideExtents): PaneGuides {
    fun column(along: Float) = camera.toPixel(camera.axes.along * along).x
    fun row(up: Float) = camera.toPixel(camera.axes.up * up).y
    val ends = listOf(column(side.along.min), column(side.along.max))
    val heights = listOf(row(side.up.max), row(side.up.min))
    return when (camera.view) {
        View.SIDE -> PaneGuides(ends, heights)
        View.TOP -> PaneGuides(verticals = ends)
        View.FRONT -> PaneGuides(horizontals = heights)
    }
}
