package dev.chieppa.squash.screens.single

import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.fitSheet
import dev.chieppa.squash.screens.sheet.sheetPartOf
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.screens.shell.shipped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val WIDTH = 900
private const val HEIGHT = 700

/** Enough pixels of drag to leave any snap: the orbit turns 0.008 radians a pixel. */
private const val PAST_A_SNAP = 80f

class SingleCameraTest {
    private val part = sheetPartOf(shipped.parts.first { it.id.value == "leg" })
    private val selection = SheetSelection(part, emptyMap())

    private fun framed() = SingleCamera().apply { frame(part.box, part.axes, WIDTH, HEIGHT) }

    @Test
    fun itOpensSnappedToSideAtTheScaleASheetPaneOfThatSizeFitsThePart() {
        val camera = framed()
        assertEquals(View.SIDE, camera.snap(part.axes, hold = false))
        val locked = camera.lockedCamera(View.SIDE, selection, WIDTH, HEIGHT, hold = false)
        val expected = fitSheet(part.box, List(4) { PixelRect(0, 0, WIDTH, HEIGHT) }).pixelsPerUnit
        assertEquals(expected, locked.fit.pixelsPerUnit, expected * 0.001f)
    }

    @Test
    fun orbitingLetsGoOfTheViewAndOrbitingBackSnapsAgain() {
        val camera = framed()
        camera.snap(part.axes, hold = false)
        camera.orbit.orbitBy(PAST_A_SNAP, 0f)
        assertNull(camera.snap(part.axes, hold = false))
        camera.orbit.orbitBy(-PAST_A_SNAP, 0f)
        assertEquals(View.SIDE, camera.snap(part.axes, hold = false))
    }

    @Test
    fun aHeldStrokeKeepsTheViewAndCameraWhateverTheOrbitDoes() {
        val camera = framed()
        camera.snap(part.axes, hold = false)
        val locked = camera.lockedCamera(View.SIDE, selection, WIDTH, HEIGHT, hold = false)
        camera.orbit.orbitBy(PAST_A_SNAP, 0f)
        camera.orbit.dollyBy(2f)
        assertEquals(View.SIDE, camera.snap(part.axes, hold = true))
        assertSame(locked, camera.lockedCamera(View.SIDE, selection, WIDTH, HEIGHT, hold = true))
    }

    @Test
    fun theLockedCameraStaysTheSameObjectUntilSomethingItHoldsChanges() {
        val camera = framed()
        val locked = camera.lockedCamera(View.SIDE, selection, WIDTH, HEIGHT, hold = false)
        assertSame(locked, camera.lockedCamera(View.SIDE, selection, WIDTH, HEIGHT, hold = false))
        camera.orbit.dollyBy(2f)
        val zoomed = camera.lockedCamera(View.SIDE, selection, WIDTH, HEIGHT, hold = false)
        assertNotSame(locked, zoomed)
        assertTrue(zoomed.fit.pixelsPerUnit > locked.fit.pixelsPerUnit, "the wheel zooms a snapped view")
    }

    @Test
    fun snappingToAViewKeepsWhereTheCameraIsAimedAndHowFar() {
        val camera = framed()
        val (target, distance) = camera.orbit.target to camera.orbit.distance
        for (view in listOf(View.TOP, View.FRONT, View.SIDE)) {
            camera.snapTo(view, part.axes)
            assertEquals(view, camera.snap(part.axes, hold = false))
            assertEquals(target, camera.orbit.target)
            assertEquals(distance, camera.orbit.distance)
        }
    }

    @Test
    fun framingAnotherPartKeepsTheDirection() {
        val camera = framed()
        camera.snapTo(View.TOP, part.axes)
        camera.snap(part.axes, hold = false)
        val paw = sheetPartOf(shipped.parts.first { it.id.value == "paw" })
        camera.frame(paw.box, paw.axes, WIDTH, HEIGHT)
        assertEquals(paw.box.center.x, camera.orbit.target.x)
        assertTrue(camera.snap(paw.axes, hold = false) != null, "the body's direction is still one of the part's views")
    }
}
