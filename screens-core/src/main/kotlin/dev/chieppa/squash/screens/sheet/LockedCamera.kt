package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.stretch.gpu.Mat4
import kotlin.math.max
import kotlin.math.min

/** How much of a pane the part fills along its tighter direction, leaving some of what is around it in view. */
private const val PANE_FILL = 0.6f

private const val NEAR_FRACTION = 0.01f

/** How far the camera stands off, as a multiple of what it must hold, so the depth range never clips it. */
private const val EYE_OF_REACH = 2f

/**
 * What the locked panes share: the point in body space at their middle, the pixels per body unit, and [reach], the radius
 * around that point that must stay inside the panes' depth range.
 */
data class SheetFit(val center: Vec3, val pixelsPerUnit: Float, val reach: Float = 0f)

/** The scale at which [box] fits every locked pane of a part with [axes] in [rects], centred on it, holding everything within [reach] in depth. */
fun fitSheet(box: BodyBox, axes: PartAxes, rects: List<PixelRect>, reach: Float = 0f): SheetFit {
    var scale = Float.POSITIVE_INFINITY
    for (pane in SheetPane.entries) {
        val body = pane.body ?: continue
        val rect = rects[pane.ordinal]
        val basis = axes.basis(axes.framing.getValue(body))
        val across = box.spanAlong(basis.screenRight)
        val tall = box.spanAlong(basis.screenUp)
        if (across > 0f) scale = min(scale, rect.width * PANE_FILL / across)
        if (tall > 0f) scale = min(scale, rect.height * PANE_FILL / tall)
    }
    return SheetFit(box.center, if (scale.isFinite()) scale else 1f, reach)
}

/** [previous] while [box] still shows whole in every locked pane and its depth holds [reach], so the view stays still as a part is reshaped; else a new fit. */
fun keepFit(previous: SheetFit?, box: BodyBox, axes: PartAxes, rects: List<PixelRect>, reach: Float = 0f): SheetFit {
    if (previous != null && reach <= previous.reach && SheetPane.entries.all { fitsPane(it, previous, box, axes, rects) }) return previous
    return fitSheet(box, axes, rects, reach)
}

private fun fitsPane(pane: SheetPane, fit: SheetFit, box: BodyBox, axes: PartAxes, rects: List<PixelRect>): Boolean {
    val body = pane.body ?: return true
    val rect = rects[pane.ordinal]
    val basis = axes.basis(axes.framing.getValue(body))
    val camera = PaneCamera(basis.screenRight, basis.screenUp, fit, rect.width, rect.height)
    return box.corners.all { corner ->
        val pixel = camera.toPixel(corner)
        pixel.x in 0f..rect.width.toFloat() && pixel.y in 0f..rect.height.toFloat()
    }
}

/** An orthographic camera with [right] and [up] on screen, in a pane [width] by [height] pixels: body space to pane pixels and to the GPU. */
class PaneCamera(val right: Vec3, val up: Vec3, val fit: SheetFit, val width: Int, val height: Int) {
    private val scale = fit.pixelsPerUnit
    private val eyeDistance: Float = max(2f * max(width, height) / scale, EYE_OF_REACH * fit.reach)

    /** From the pane towards the viewer. */
    val toViewer: Vec3 get() = right cross up

    /** The pane pixel showing [point], a point in body space. */
    fun toPixel(point: Vec3): Offset {
        val from = point - fit.center
        return Offset(width / 2f + (from dot right) * scale, height / 2f - (from dot up) * scale)
    }

    /** The camera as a matrix for this pane's viewport. */
    fun viewProjection(): Mat4 {
        val eye = fit.center + toViewer * eyeDistance
        val look = Mat4.lookAt(eye.toGpu(), fit.center.toGpu(), up.toGpu())
        val lens = Mat4.orthographic(width / 2f / scale, height / 2f / scale, eyeDistance * NEAR_FRACTION, 2f * eyeDistance)
        return lens * look
    }
}

/** The camera on the body from [view]. */
fun bodyCamera(view: BodyView, fit: SheetFit, width: Int, height: Int) = PaneCamera(view.right, view.up, fit, width, height)

/**
 * A locked pane's camera on a part, and the conversions a tool needs between a pixel in the pane, measured from its top-left
 * corner, and the coordinates an outline for [view] holds. The pane looks straight at the outline's plane, which passes
 * through the part's socket at [origin] along [axes], so the map between them is a rotation and a scale, and a drawing is
 * the mesh's silhouette on screen. [drawingScale] is the part's size parameter, which multiplies a drawing, so a stroke
 * still builds under the pointer at any size. [lengthFactor] is what the part is built at along its axis, which multiplies an
 * outline's x on every view but Front, so a stored outline lies on a shortened part and a stroke on one stores what builds
 * under the pointer. Pure, so a tool's logic is tested without a window.
 */
class LockedCamera(
    val view: View,
    val axes: PartAxes,
    val fit: SheetFit,
    val width: Int,
    val height: Int,
    val origin: Vec3 = Vec3.ZERO,
    val drawingScale: Float = 1f,
    val lengthFactor: Float = 1f,
) {
    val basis: ViewBasis = axes.basis(view)
    private val pane = PaneCamera(basis.screenRight, basis.screenUp, fit, width, height)

    /** What an outline's x is multiplied by on the built part. */
    val xFactor: Float = if (view == View.FRONT) 1f else lengthFactor

    /** A screen pixel's size in the view's plane, as an outline's y holds it, and both ways in [toShown]'s plane. */
    val planePixel: Float get() = 1f / (fit.pixelsPerUnit * drawingScale)

    /** A screen pixel's size in the view's plane, as an outline's x holds it. */
    val planePixelX: Float get() = planePixel / xFactor

    /** How many body units from the socket a length along the part's axis, as an outline holds it, reaches. */
    val alongScale: Float get() = drawingScale * lengthFactor

    /** The point in the view's plane under [pixel], in the coordinates an outline for this view holds. */
    fun toPlane(pixel: Offset): CurvePoint {
        val socket = toPixel(origin)
        val onScreen = basis.screenRight * ((pixel.x - socket.x) * planePixel) + basis.screenUp * ((socket.y - pixel.y) * planePixel)
        return CurvePoint((onScreen dot basis.xAxis) / xFactor, onScreen dot basis.yAxis)
    }

    /** The pane pixel showing [plane], a point in the view's plane. */
    fun toPixel(plane: CurvePoint): Offset = toPixel(toWorld(plane))

    /** The point in body space at [plane], a point in the view's plane. */
    fun toWorld(plane: CurvePoint): Vec3 = origin + (basis.xAxis * (plane.x * xFactor) + basis.yAxis * plane.y) * drawingScale

    /** The point in body space under [pixel], on the view's plane through the socket. */
    fun toWorld(pixel: Offset): Vec3 = toWorld(toPlane(pixel))

    /** The pane pixel showing [point], a point in body space. */
    fun toPixel(point: Vec3): Offset = pane.toPixel(point)

    /** The camera as a matrix for this pane's viewport. */
    fun viewProjection(): Mat4 = pane.viewProjection()
}

/** The same vector for `stretch-gpu`'s camera and matrix types. */
fun Vec3.toGpu() = dev.chieppa.stretch.gpu.Vec3(x, y, z)
