package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.stretch.gpu.Mat4
import kotlin.math.max
import kotlin.math.min

/** How much of a pane the part fills along its tighter direction. */
private const val PANE_FILL = 0.66f

private const val NEAR_FRACTION = 0.01f

/** What the locked panes share: the point in part space at their middle, and the pixels per part unit. */
data class SheetFit(val center: Vec3, val pixelsPerUnit: Float)

/** The scale at which [extents] fit every locked pane in [rects], centred on the part. */
fun fitSheet(axes: PartAxes, extents: PartExtents, rects: List<PixelRect>): SheetFit {
    var scale = Float.POSITIVE_INFINITY
    for (pane in SheetPane.entries) {
        val view = pane.view ?: continue
        val rect = rects[pane.ordinal]
        val (x, y) = extents.spansIn(view)
        if (x.length > 0f) scale = min(scale, rect.width * PANE_FILL / x.length)
        if (y.length > 0f) scale = min(scale, rect.height * PANE_FILL / y.length)
    }
    return SheetFit(axes.middle(extents), if (scale.isFinite()) scale else 1f)
}

/** [previous] while [extents] still show whole in every locked pane, so the view holds still as a part is reshaped; else a new fit. */
fun keepFit(previous: SheetFit?, axes: PartAxes, extents: PartExtents, rects: List<PixelRect>): SheetFit {
    if (previous != null && SheetPane.entries.all { fitsPane(it, previous, axes, extents, rects) }) return previous
    return fitSheet(axes, extents, rects)
}

private fun fitsPane(pane: SheetPane, fit: SheetFit, axes: PartAxes, extents: PartExtents, rects: List<PixelRect>): Boolean {
    val view = pane.view ?: return true
    val rect = rects[pane.ordinal]
    val camera = LockedCamera(view, axes, fit, rect.width, rect.height)
    val (x, y) = extents.spansIn(view)
    val corners = listOf(camera.toPixel(CurvePoint(x.min, y.min)), camera.toPixel(CurvePoint(x.max, y.max)))
    return corners.all { it.x in 0f..rect.width.toFloat() && it.y in 0f..rect.height.toFloat() }
}

/**
 * The orthographic camera of a locked pane [width] by [height] pixels, and the conversions a tool needs between a pixel
 * in the pane, measured from its top-left corner, and part space. Pure, so a tool's logic is tested without a window.
 */
class LockedCamera(val view: View, val axes: PartAxes, val fit: SheetFit, val width: Int, val height: Int) {
    val basis: ViewBasis = axes.basis(view)
    private val centerX = fit.center dot basis.xAxis
    private val centerY = fit.center dot basis.yAxis
    private val scale = fit.pixelsPerUnit

    /** How far the camera sits from the middle of the part; everything the pane can show lies closer than that. */
    val eyeDistance: Float = 2f * max(width, height) / scale

    /** The point in the view's plane under [pixel], in the coordinates an outline for this view holds. */
    fun toPlane(pixel: Offset): CurvePoint = CurvePoint(
        centerX + basis.xSign * (pixel.x - width / 2f) / scale,
        centerY + (height / 2f - pixel.y) / scale,
    )

    /** The pane pixel showing [plane], a point in the view's plane. */
    fun toPixel(plane: CurvePoint): Offset = Offset(
        width / 2f + basis.xSign * (plane.x - centerX) * scale,
        height / 2f - (plane.y - centerY) * scale,
    )

    /** The point in part space under [pixel], on the plane through the part's origin that the view looks straight at. */
    fun toPart(pixel: Offset): Vec3 {
        val plane = toPlane(pixel)
        return basis.xAxis * plane.x + basis.yAxis * plane.y
    }

    /** The pane pixel showing [point], a point in part space. */
    fun toPixel(point: Vec3): Offset = toPixel(CurvePoint(point dot basis.xAxis, point dot basis.yAxis))

    /** The camera as a matrix for this pane's viewport. */
    fun viewProjection(): Mat4 {
        val eye = fit.center + basis.back * eyeDistance
        val look = Mat4.lookAt(eye.toGpu(), fit.center.toGpu(), basis.screenUp.toGpu())
        val lens = Mat4.orthographic(width / 2f / scale, height / 2f / scale, eyeDistance * NEAR_FRACTION, 2f * eyeDistance)
        return lens * look
    }
}

/** The same vector for `stretch-gpu`'s camera and matrix types. */
internal fun Vec3.toGpu() = dev.chieppa.stretch.gpu.Vec3(x, y, z)
