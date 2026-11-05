package dev.chieppa.squash.screens.single

import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.PartAxes
import dev.chieppa.squash.character.catalog.VIEW_ORDER
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos

/** How close in degrees a free camera comes to a view to snap to it, and how far a snapped one goes to let go of it. */
const val SNAP_IN_DEGREES = 10f
const val SNAP_OUT_DEGREES = 15f

/** Below this horizontal reach a direction looks straight up or down, and has no yaw of its own. */
private const val POLE_REACH = 0.001f

private fun cosineOf(degrees: Float) = cos(degrees * PI.toFloat() / 180f)

private val SNAP_IN_COSINE = cosineOf(SNAP_IN_DEGREES)
private val SNAP_OUT_COSINE = cosineOf(SNAP_OUT_DEGREES)

/** From the part towards the viewer of [view]. */
fun PartAxes.viewDirection(view: View): Vec3 = basis(view).back

/**
 * The view a camera at [toViewer], a unit vector from what it looks at to its eye, shows from either side: [current] until it turns further
 * than [SNAP_OUT_DEGREES] from it, the nearest view within [SNAP_IN_DEGREES], or null for a free camera. The gap between
 * the two keeps a slow orbit from flickering at the edge of a snap.
 */
fun snappedView(current: View?, toViewer: Vec3, axes: PartAxes): View? {
    fun closeness(view: View) = abs(toViewer dot axes.viewDirection(view))
    if (current != null && closeness(current) >= SNAP_OUT_COSINE) return current
    return VIEW_ORDER.maxBy(::closeness).takeIf { closeness(it) >= SNAP_IN_COSINE }
}

/** A direction as an orbit camera's angles. */
data class OrbitAngles(val yaw: Float, val pitch: Float)

/** The angles of a camera at [toViewer], a unit vector; straight above or below, its yaw is the one that keeps [screenUp] up. */
fun orbitAngles(toViewer: Vec3, screenUp: Vec3): OrbitAngles {
    val pitch = asin(toViewer.y.coerceIn(-1f, 1f))
    val atPole = abs(toViewer.x) < POLE_REACH && abs(toViewer.z) < POLE_REACH
    val toward = if (toViewer.y > 0f) -1f else 1f
    val yaw = if (atPole) atan2(screenUp.x * toward, screenUp.z * toward) else atan2(toViewer.x, toViewer.z)
    return OrbitAngles(yaw, pitch)
}
