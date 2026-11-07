package dev.chieppa.squash.screens.surface.pose

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.BodyRay
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SurfaceView
import kotlin.math.abs

/** How squarely a sight must look along the body's side or up to draw a body curve through it, as the cosine of the angle off: 10 degrees. */
private const val SQUARE = 0.985f

/** Below this a line of sight runs along a plane and meets it nowhere. */
private const val GRAZING = 1e-6f

/** The view whose plane [sight] looks squarely at around [at]: Side along the body's z, Top along its y; null for any other. */
fun bodyCurveView(sight: SurfaceView, at: Vec3): View? {
    val toViewer = sight.toViewer(at)
    return when {
        abs(toViewer.z) >= SQUARE -> View.SIDE
        abs(toViewer.y) >= SQUARE -> View.TOP
        else -> null
    }
}

/**
 * The point of [view]'s plane through [middle] under [pixel] on [sight], as a body stroke holds it: body x and y in Side, body x
 * and z in Top. Null where the line of sight misses the plane.
 */
fun strokePoint(sight: SurfaceView, pixel: Offset, view: View, middle: Vec3): Vec2? {
    val ray = rayThrough(sight, pixel) ?: return null
    val normal = normalOf(view)
    val along = ray.direction dot normal
    if (abs(along) < GRAZING) return null
    val p = ray.at(((middle - ray.origin) dot normal) / along)
    return if (view == View.TOP) Vec2(p.x, p.z) else Vec2(p.x, p.y)
}

/** Where [point] of a body stroke in [view] lies in body space, on that view's plane through [middle]. */
fun strokeWorld(point: Vec2, view: View, middle: Vec3): Vec3 =
    if (view == View.TOP) Vec3(point.x, middle.y, point.y) else Vec3(point.x, point.y, middle.z)

private fun normalOf(view: View): Vec3 = if (view == View.TOP) Vec3.Y else Vec3.Z

private fun rayThrough(sight: SurfaceView, pixel: Offset): BodyRay? = when (sight) {
    is FullView -> sight.ray(pixel)
    is LockedCamera -> BodyRay(sight.toWorld(pixel), sight.basis.back * -1f)
    else -> null
}
