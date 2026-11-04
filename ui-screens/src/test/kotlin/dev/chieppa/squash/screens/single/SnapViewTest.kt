package dev.chieppa.squash.screens.single

import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.PartAxes
import dev.chieppa.squash.screens.shell.VIEW_ORDER
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val TOLERANCE = 0.001f

class SnapViewTest {
    /** A leg's axes: it hangs down, so its Front looks from above and its Top from the body's front. */
    private val leg = PartAxes.of(Vec3(0f, -1f, 0f))

    /** A part lying along the body. */
    private val lying = PartAxes.of(Vec3(1f, 0f, 0f))

    /** [direction] turned [degrees] towards a direction square to it. */
    private fun turned(direction: Vec3, degrees: Float): Vec3 {
        val square = (direction cross (if (kotlin.math.abs(direction.y) > 0.9f) Vec3.X else Vec3.Y)).normalized()
        val angle = degrees * PI.toFloat() / 180f
        return direction * cos(angle) + square * sin(angle)
    }

    @Test
    fun aFreeCameraSnapsToAViewOnlyOnceItIsNear() {
        for (axes in listOf(leg, lying)) {
            for (view in VIEW_ORDER) {
                val direction = axes.viewDirection(view)
                assertEquals(view, snappedView(null, direction, axes), "$view on it")
                assertEquals(view, snappedView(null, turned(direction, SNAP_IN_DEGREES - 1f), axes), "$view just inside")
                assertNull(snappedView(null, turned(direction, SNAP_IN_DEGREES + 1f), axes), "$view just outside")
            }
        }
    }

    @Test
    fun aSnappedCameraHoldsItsViewFurtherThanItTookToSnap() {
        val direction = leg.viewDirection(View.SIDE)
        val between = turned(direction, (SNAP_IN_DEGREES + SNAP_OUT_DEGREES) / 2f)
        assertNull(snappedView(null, between, leg), "too far to snap")
        assertEquals(View.SIDE, snappedView(View.SIDE, between, leg), "near enough to stay")
        assertNull(snappedView(View.SIDE, turned(direction, SNAP_OUT_DEGREES + 1f), leg), "past the hold it lets go")
    }

    @Test
    fun aCameraLeavingOneViewForAnotherSnapsToTheOther() {
        val top = leg.viewDirection(View.TOP)
        assertEquals(View.TOP, snappedView(View.SIDE, top, leg))
    }

    @Test
    fun aCameraBehindAViewIsFree() {
        for (view in VIEW_ORDER) assertNull(snappedView(null, leg.viewDirection(view) * -1f, leg), "behind $view")
    }

    @Test
    fun aViewIsReachedFromThePartsOwnFraming() {
        assertEquals(1f, leg.viewDirection(View.FRONT) dot Vec3.Y, TOLERANCE, "a leg's Front is looked at from above")
        for (axes in listOf(leg, lying)) {
            for (view in VIEW_ORDER) assertEquals(1f, axes.viewDirection(view) dot axes.bodyViewOf(view).toViewer, TOLERANCE, "$view")
        }
    }

    @Test
    fun anglesPointAnOrbitCameraAlongADirection() {
        for (axes in listOf(leg, lying, PartAxes.of(Vec3(-0.8f, 0.6f, 0f)), PartAxes.of(Vec3(0f, 0.96f, -0.28f)))) {
            for (view in VIEW_ORDER) {
                val direction = axes.viewDirection(view)
                val (yaw, pitch) = orbitAngles(direction, axes.basis(view).screenUp)
                val eye = Vec3(cos(pitch) * sin(yaw), sin(pitch), cos(pitch) * cos(yaw))
                assertEquals(1f, eye dot direction, TOLERANCE, "$view")
            }
        }
    }

    @Test
    fun straightAboveTheYawKeepsTheViewsUpAtTheTopOfTheScreen() {
        val up = Vec3.Z
        val (yaw, _) = orbitAngles(Vec3.Y, up)
        val towardEye = Vec3(sin(yaw), 0f, cos(yaw))
        assertEquals(-1f, towardEye dot up, TOLERANCE, "seen from above, what is far from the eye is up the screen")
    }
}
