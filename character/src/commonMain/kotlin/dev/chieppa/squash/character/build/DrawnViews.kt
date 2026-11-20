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
import dev.chieppa.squash.geom.ProfileKeel
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

/** These outlines on a part built [factor] times its length along its axis, which is x on every view but Front. */
internal fun Map<View, OutlineCurve>.shortenedBy(factor: Float): Map<View, OutlineCurve> {
    if (factor == 1f) return this
    return mapValues { (view, outline) ->
        if (view == View.FRONT) outline else OutlineCurve(outline.points.map { CurvePoint(it.x * factor, it.y) })
    }
}

/**
 * What a part's drawn outlines set, each read as the template's views map says. A view with no usable shape sets nothing. With
 * [sideways] the width view's midline moves the part's middle toward its side.
 */
internal class DrawnViews(template: PartTemplate, outlines: Map<View, OutlineCurve>, axes: PartAxes, scale: Float, sideways: Boolean = false) {
    val sizing: ViewSizing?
    val front: FrontSection?

    /** The drawn view that sets height, read along its x, in drawing units; null where it is not drawn. */
    val heightProfile: OutlineProfile?

    /** The drawn view that sets width, read along its x, in drawing units; null where it is not drawn. */
    val widthProfile: OutlineProfile?

    /** The drawn Front outline, flattened, where the template reads it as the whole section or a cap; null where it is not drawn. */
    val frontOutline: List<Vec2>?

    init {
        fun view(sets: Sets): OutlineCurve? = template.views.entries.firstOrNull { it.value == sets }?.let { outlines[it.key] }
        fun profile(sets: Sets) = view(sets)?.let { OutlineProfile.of(it.toPath().flatten()) }
        fun section(sets: Sets) =
            view(sets)?.let { ClosedSpline.fromOutline(it.toPath().flatten(), template.topology.segments) }

        heightProfile = profile(Sets.HEIGHT)
        widthProfile = profile(Sets.WIDTH)
        frontOutline = (view(Sets.SECTION) ?: view(Sets.CAP))?.toPath()?.flatten()?.let { line -> List(line.xs.size) { Vec2(line.xs[it], line.ys[it]) } }?.takeIf { it.size >= 3 }
        sizing = ViewSizing.of(
            axes,
            height = heightProfile,
            width = widthProfile,
            openStart = template.openEnd == End.START,
            openEnd = template.openEnd == End.END,
            scale = scale,
            profile = template.profile?.let { spec -> { profile -> ProfileKeel.of(profile, spec.span, spec.keel) } },
            sideways = sideways,
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
