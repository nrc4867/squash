package dev.chieppa.squash.screens.references

import dev.chieppa.squash.document.ImageId
import dev.chieppa.squash.document.ReferenceImage
import dev.chieppa.squash.document.Trace
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Where an image sits in a view when no character has built yet: centred, this many body units tall. */
private const val EMPTY_FIT_HEIGHT = 1f

/** The character's own direction a trace in [view] faces the viewer from. */
fun bodyViewOf(view: View): BodyView = when (view) {
    View.SIDE -> BodyView.SIDE
    View.TOP -> BodyView.TOP
    View.FRONT -> BodyView.FRONT
}

/** A point in [view]'s plane: across it, toward the screen's right, and up it, in body units. */
data class PlanePoint(val x: Float, val y: Float)

/** Where [world] falls in [view]'s plane. */
fun planePoint(world: Vec3, view: View): PlanePoint {
    val body = bodyViewOf(view)
    return PlanePoint(world.dot(body.right), world.dot(body.up))
}

/** The point of [view]'s plane at [x] across and [y] up, at no depth. */
fun planeWorld(x: Float, y: Float, view: View): Vec3 {
    val body = bodyViewOf(view)
    return body.right * x + body.up * y
}

/** The character's view a camera looking from [toViewer] sees most squarely. */
fun viewFacing(toViewer: Vec3): View = View.entries.maxBy { abs(bodyViewOf(it).toViewer.dot(toViewer.normalized())) }

/**
 * [trace]'s corners in the scene for an image [width] by [height] pixels, top-left, top-right, bottom-right then bottom-left,
 * turned by its rotation and mirrored across by its flip.
 */
fun traceCorners(trace: Trace, view: View, width: Int, height: Int): List<Vec3> {
    val halfWidth = width * trace.units / 2f
    val halfHeight = height * trace.units / 2f
    val turn = Math.toRadians(trace.rotation.toDouble())
    val c = cos(turn).toFloat()
    val s = sin(turn).toFloat()
    val across = if (trace.flip) -1f else 1f
    return listOf(-1f to 1f, 1f to 1f, 1f to -1f, -1f to -1f).map { (u, v) ->
        val x = u * halfWidth * across
        val y = v * halfHeight
        planeWorld(trace.centerX + x * c - y * s, trace.centerY + x * s + y * c, view)
    }
}

/** The trace's bottom edge in [view]'s plane before rotation, up from its centre by minus half its height. */
fun traceBottom(trace: Trace, height: Int): Float = trace.centerY - height * trace.units / 2f

/**
 * A trace of [image] in [view] fitted over the character in [box]: as tall as the character stands in that view, centred across
 * it, its bottom on the character's lowest point. With nothing built it sits at the middle.
 */
fun fittedTrace(image: ReferenceImage, view: View, box: BodyBox?): Trace {
    if (box == null) return Trace(image.id, 0f, 0f, EMPTY_FIT_HEIGHT / image.height)
    val points = box.corners.map { planePoint(it, view) }
    val low = PlanePoint(points.minOf { it.x }, points.minOf { it.y })
    val high = PlanePoint(points.maxOf { it.x }, points.maxOf { it.y })
    val units = (high.y - low.y).coerceAtLeast(Float.MIN_VALUE) / image.height
    return Trace(image.id, (low.x + high.x) / 2f, (low.y + high.y) / 2f, units)
}

/** The views [image] is traced in. */
fun tracedIn(traces: Map<View, Trace>, image: ImageId): Set<View> = traces.filterValues { it.image == image }.keys
