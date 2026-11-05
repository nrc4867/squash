package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import dev.chieppa.squash.geom.PartAxes as BuildAxes

/**
 * A part's own axes in body space: [along] its axis from the socket, [up] where its slices' height is measured, and [side]
 * where their width is. They come from the template's rest axis and the part's side alone, so they stay put while the
 * outlines change. A mirrored part's are reflected across the body's plane, which is why [side] is its own field.
 */
data class PartAxes(val along: Vec3, val up: Vec3, val side: Vec3) {
    /** Which view each [BodyView] holds: the arrangement that shows every view's plane the most squarely, the plainest on a tie. */
    val framing: Map<BodyView, View> = FRAMINGS.maxBy { arrangement -> arrangement.entries.sumOf { (body, view) -> squareness(view, body).toDouble() } }

    /** The body view holding [view]. */
    fun bodyViewOf(view: View): BodyView = framing.entries.first { it.value == view }.key

    /** The direction an outline's x and y run in for [view]. */
    fun planeOf(view: View): Pair<Vec3, Vec3> = when (view) {
        View.SIDE -> along to up
        View.TOP -> along to side * -1f
        View.FRONT -> side to up
    }

    /**
     * How [view] sits on screen: looking straight at its plane from the side of its pane's body view, with the pane's up
     * as up as the plane lets it be. [framing] never gives a pane the view whose normal is that pane's up, since swapping
     * that view with the pane looking along its up shows every view more squarely.
     */
    fun basis(view: View): ViewBasis {
        val pane = bodyViewOf(view)
        val (x, y) = planeOf(view)
        val normal = (x cross y).normalized()
        val toViewer = if ((normal dot pane.toViewer) < 0f) normal * -1f else normal
        val up = (pane.up - toViewer * (pane.up dot toViewer)).normalized()
        return ViewBasis(xAxis = x, yAxis = y, screenRight = up cross toViewer, screenUp = up)
    }

    /** How little [view]'s plane is foreshortened in [body]: 1 when it faces it, 0 when seen edge on. */
    private fun squareness(view: View, body: BodyView): Float {
        val (x, y) = planeOf(view)
        return abs((x dot body.right) * (y dot body.up) - (x dot body.up) * (y dot body.right))
    }

    companion object {
        private val PLAIN = mapOf(BodyView.TOP to View.TOP, BodyView.SIDE to View.SIDE, BodyView.FRONT to View.FRONT)

        /** Every way to give the three body views the three views, the plain one first so it wins a tie. */
        private val FRAMINGS: List<Map<BodyView, View>> = listOf(
            PLAIN,
            mapOf(BodyView.TOP to View.FRONT, BodyView.SIDE to View.SIDE, BodyView.FRONT to View.TOP),
            mapOf(BodyView.TOP to View.SIDE, BodyView.SIDE to View.TOP, BodyView.FRONT to View.FRONT),
            mapOf(BodyView.TOP to View.TOP, BodyView.SIDE to View.FRONT, BodyView.FRONT to View.SIDE),
            mapOf(BodyView.TOP to View.SIDE, BodyView.SIDE to View.FRONT, BodyView.FRONT to View.TOP),
            mapOf(BodyView.TOP to View.FRONT, BodyView.SIDE to View.TOP, BodyView.FRONT to View.SIDE),
        )

        /** The axes of a template with rest axis [rest], the same ones its swept patches' first slice has, reflected when [mirrored]. */
        fun of(rest: Vec3, mirrored: Boolean = false, towards: Vec3? = null): PartAxes {
            val axes = BuildAxes.of(rest, towards)
            if (!mirrored) return PartAxes(axes.along, axes.up, axes.side)
            return PartAxes(axes.along.mirrored, axes.up.mirrored, axes.side.mirrored)
        }
    }
}

/**
 * How a locked view sits in the body. A point in the view's plane is (x, y) along [xAxis] and [yAxis]; those are the
 * coordinates an [dev.chieppa.squash.document.OutlineCurve] for the view holds. [screenRight] and [screenUp] are where the
 * pane's right and up point, in the view's plane, so x and y keep their length on screen and turn whichever way the part lies.
 */
data class ViewBasis(val xAxis: Vec3, val yAxis: Vec3, val screenRight: Vec3, val screenUp: Vec3) {
    /** From the part towards the viewer. */
    val back: Vec3 get() = screenRight cross screenUp

    /** The same plane looked at from its other side, with the same up. */
    val flipped: ViewBasis get() = copy(screenRight = screenRight * -1f)
}
