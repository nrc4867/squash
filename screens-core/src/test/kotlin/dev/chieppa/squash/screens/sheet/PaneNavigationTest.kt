package dev.chieppa.squash.screens.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val PANE = "pane"
private const val CLOSE = 0.01f

@OptIn(ExperimentalTestApi::class)
class PaneNavigationTest {
    private val axes = PartAxes.of(Vec3(1f, 0f, 0f))
    private val base = SheetFit(Vec3.ZERO, 100f, 4f)

    private fun camera(nav: PaneNav = PaneNav()) = LockedCamera(View.SIDE, axes, nav.applied(base), 600, 400)

    private fun assertSamePixel(expected: Offset, actual: Offset) =
        assertTrue((expected - actual).getDistance() < CLOSE, "$expected and $actual")

    @Test
    fun zoomingKeepsWhatIsUnderThePointerStill() {
        val pixel = Offset(420f, 90f)
        val point = camera().toWorld(pixel)
        val zoomed = PaneNav().zoomed(3f, pixel, camera())
        assertEquals(3f, zoomed.zoom)
        assertSamePixel(pixel, camera(zoomed).toPixel(point))
        val again = zoomed.zoomed(0.5f, pixel, camera(zoomed))
        assertSamePixel(pixel, camera(again).toPixel(point))
    }

    @Test
    fun zoomStopsAtItsLimitsWithoutMovingThePicture() {
        val pixel = Offset(100f, 100f)
        val point = camera().toWorld(pixel)
        val huge = PaneNav().zoomed(1000f, pixel, camera())
        assertEquals(16f, huge.zoom)
        assertSamePixel(pixel, camera(huge).toPixel(point))
        assertEquals(0.25f, PaneNav().zoomed(0.001f, pixel, camera()).zoom)
    }

    @Test
    fun panningMovesThePictureByThosePixelsAtAnyZoom() {
        for (zoom in listOf(1f, 4f)) {
            val nav = PaneNav(zoom)
            val point = Vec3(0.2f, 0.1f, 0f)
            val before = camera(nav).toPixel(point)
            val panned = nav.panned(Offset(30f, -12f), camera(nav))
            assertSamePixel(before + Offset(30f, -12f), camera(panned).toPixel(point))
        }
    }

    @Test
    fun aPointDrawnInAZoomedPaneLandsUnderThePointer() {
        val nav = PaneNav().zoomed(5f, Offset(200f, 300f), camera()).panned(Offset(40f, 25f), camera())
        val zoomed = camera(nav)
        val pixel = Offset(333f, 77f)
        val plane: CurvePoint = zoomed.toPlane(pixel)
        assertSamePixel(pixel, zoomed.toPixel(plane))
    }

    @Test
    fun fittingOnePaneLeavesTheOthers() {
        val navigation = PaneNavigation()
        navigation.update(View.SIDE) { PaneNav(2f) }
        navigation.update(View.TOP) { PaneNav(3f) }
        navigation.fit(View.SIDE)
        assertEquals(1f, navigation[View.SIDE].zoom)
        assertEquals(3f, navigation[View.TOP].zoom)
        navigation.fitAll()
        assertEquals(1f, navigation[View.TOP].zoom)
    }

    @Test
    fun theWheelZoomsAndAMiddleDragPansAndNeitherWhileHeld() = runComposeUiTest {
        val navigation = PaneNavigation()
        var held by mutableStateOf(false)
        setContent {
            Box(Modifier.size(600.dp, 400.dp).testTag(PANE).paneNavigation(navigation, camera(navigation[View.SIDE]), held))
        }
        val pane = onNodeWithTag(PANE)
        pane.performMouseInput {
            moveTo(Offset(100f, 100f))
            scroll(-2f)
        }
        waitForIdle()
        assertTrue(navigation[View.SIDE].zoom > 1f, "the wheel zooms in")
        val zoom = navigation[View.SIDE].zoom
        pane.performMouseInput {
            moveTo(Offset(200f, 200f))
            press(MouseButton.Tertiary)
            moveBy(Offset(40f, 0f))
            release(MouseButton.Tertiary)
        }
        waitForIdle()
        val panned = navigation[View.SIDE]
        assertEquals(zoom, panned.zoom)
        assertNotEquals(Vec3.ZERO, panned.offset)
        held = true
        waitForIdle()
        pane.performMouseInput {
            moveTo(Offset(100f, 100f))
            scroll(-2f)
        }
        waitForIdle()
        assertEquals(panned, navigation[View.SIDE])
        assertTrue(abs(zoom - panned.zoom) < CLOSE)
    }
}
