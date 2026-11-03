package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.Sets
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BezierPath
import dev.chieppa.squash.geom.ClosedSpline
import dev.chieppa.squash.geom.CubicBezier
import dev.chieppa.squash.geom.FrontSection
import dev.chieppa.squash.geom.OutlineProfile
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.ViewSizing

private const val POINTS_PER_SEGMENT = 3

/**
 * An outline point in the part's own axes. Side is (along, up), Top is (along, -side), Front is (side, up) looking down
 * the axis. See HLD: data-model, Part template and part instance.
 */
fun PartAxes.pointOnView(view: View, x: Float, y: Float) = when (view) {
    View.SIDE -> point(x, y, 0f)
    View.TOP -> point(x, 0f, -y)
    View.FRONT -> point(0f, y, x)
}

fun OutlineCurve.toPath(): BezierPath = BezierPath(
    List((points.size - 1) / POINTS_PER_SEGMENT) { i ->
        val o = i * POINTS_PER_SEGMENT
        CubicBezier(points[o].vec(), points[o + 1].vec(), points[o + 2].vec(), points[o + 3].vec())
    },
)

private fun CurvePoint.vec() = Vec2(x, y)

/** What a part's drawn outlines set, each read as the template's views map says. A view with no usable shape sets nothing. */
internal class DrawnViews(template: PartTemplate, outlines: Map<View, OutlineCurve>, axes: PartAxes, scale: Float) {
    val sizing: ViewSizing?
    val front: FrontSection?

    init {
        fun view(sets: Sets): OutlineCurve? = template.views.entries.firstOrNull { it.value == sets }?.let { outlines[it.key] }
        fun profile(sets: Sets) = view(sets)?.let { OutlineProfile.of(it.toPath().flatten()) }
        fun section(sets: Sets) =
            view(sets)?.let { ClosedSpline.fromOutline(it.toPath().flatten(), template.topology.segments) }

        sizing = ViewSizing.of(
            axes,
            height = profile(Sets.HEIGHT),
            width = profile(Sets.WIDTH),
            openStart = template.openEnd == End.START,
            openEnd = template.openEnd == End.END,
            scale = scale,
        )
        val whole = section(Sets.SECTION)
        val cap = section(Sets.CAP)
        front = when {
            whole != null -> FrontSection.whole(whole)
            cap != null -> FrontSection.caps(cap, start = template.openEnd != End.START, end = template.openEnd != End.END)
            else -> null
        }
    }
}
