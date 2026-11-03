package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.PartAxes as BuildAxes
import dev.chieppa.squash.render.RenderMesh

/**
 * A part's own axes in the space its mesh is built in: [along] its axis from the socket, [up] where its slices' height
 * is measured, and [side] where their width is. They come from the template's rest axis alone, so they stay put while
 * the outlines change.
 */
data class PartAxes(val along: Vec3, val up: Vec3, val side: Vec3) {
    /** The directions that locked [view] shows. */
    fun basis(view: View): ViewBasis = when (view) {
        View.SIDE -> ViewBasis(xAxis = along, yAxis = up, screenRight = along * -1f, screenUp = up)
        View.TOP -> ViewBasis(xAxis = along, yAxis = side * -1f, screenRight = along * -1f, screenUp = side * -1f)
        View.FRONT -> ViewBasis(xAxis = side, yAxis = up, screenRight = side, screenUp = up)
    }

    /** The point at [extents]' middle. */
    fun middle(extents: PartExtents): Vec3 =
        along * extents.along.center + up * extents.up.center + side * extents.side.center

    companion object {
        /** The axes of a template with rest axis [rest], the same ones its swept patches' first slice has. */
        fun of(rest: Vec3): PartAxes {
            val axes = BuildAxes.of(rest)
            return PartAxes(axes.along, axes.up, axes.side)
        }
    }
}

/**
 * How a locked view sits in part space. A point in the view's plane is (x, y) along [xAxis] and [yAxis]; those are the
 * coordinates an [dev.chieppa.squash.document.OutlineCurve] for the view holds. [screenRight] and [screenUp] are where
 * the pane's right and up point, so Side and Top run x right to left, as a part reads with its socket on the right.
 */
data class ViewBasis(val xAxis: Vec3, val yAxis: Vec3, val screenRight: Vec3, val screenUp: Vec3) {
    /** From the part towards the viewer. */
    val back: Vec3 get() = screenRight cross screenUp

    /** 1 when x grows to the pane's right, -1 when it grows to its left. */
    val xSign: Float get() = screenRight dot xAxis
}

/** A range along one axis. */
data class Span(val min: Float, val max: Float) {
    val length: Float get() = max - min
    val center: Float get() = (min + max) / 2f

    /** The range seen along the axis pointing the other way. */
    operator fun unaryMinus() = Span(-max, -min)
}

/** How far a part reaches along each of its axes. */
data class PartExtents(val along: Span, val up: Span, val side: Span) {
    /** The ranges locked [view] shows, along its x and y. */
    fun spansIn(view: View): Pair<Span, Span> = when (view) {
        View.SIDE -> along to up
        View.TOP -> along to -side
        View.FRONT -> side to up
    }
}

/** How far [mesh] reaches along these axes, or null for a mesh with no vertices. */
fun PartAxes.extentsOf(mesh: RenderMesh): PartExtents? {
    if (mesh.vertexCount == 0) return null
    fun span(axis: Vec3): Span {
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        for (v in 0 until mesh.vertexCount) {
            val value = mesh.position(v) dot axis
            if (value < min) min = value
            if (value > max) max = value
        }
        return Span(min, max)
    }
    return PartExtents(span(along), span(up), span(side))
}
