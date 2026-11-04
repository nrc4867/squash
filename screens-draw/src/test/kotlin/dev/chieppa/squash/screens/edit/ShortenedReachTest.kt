package dev.chieppa.squash.screens.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PartAxes
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.shownCurve
import dev.chieppa.squash.screens.sheet.shownPoint
import dev.chieppa.squash.screens.sheet.storedCurve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SHORT = 0.5f
private const val BRUSH_SIZE = 40f

/** The Smooth tool's brush radius as a share of its size. */
private const val RADIUS_OF_SIZE_IN_TEST = 0.5f

/** Smooth and Erase on a leg built at half its length reach what their rings show on screen. */
@OptIn(ExperimentalTestApi::class)
class ShortenedReachTest {
    private fun short(center: Vec3 = Vec3(0.5f, 0f, 0f), scale: Float = SCALE) =
        LockedCamera(View.SIDE, PartAxes.of(Vec3.X), SheetFit(center, scale), PANE_WIDTH, PANE_HEIGHT, lengthFactor = SHORT)

    @Test
    fun theShownPlaneHasOnePixelBothWaysAndAnUntouchedCurveComesBackExactly() {
        val camera = short()
        val a = camera.toPixel(CurvePoint(0.3f, 0.1f))
        val apart = camera.toPixel(camera.run { CurvePoint(0.3f + 10f * planePixelX, 0.1f + 10f * planePixel) }) - a
        assertEquals(apart.getDistance(), 10f * 1.41421f, 1e-1f)
        val outline = RIPPLED
        assertEquals(outline, camera.storedCurve(camera.shownCurve(outline), outline))
    }

    @Test
    fun theStretchUnderTheRingReachesTheRingOnScreen() = runComposeUiTest {
        val bench = CurveToolBench(LEG_SIDE)
        val camera = short(Vec3(0.5f, 0.5f, 0f), SCALE)
        val tool = SmoothTool(bench.state, TEST_CURVE_LOOK)
        bench.state.curves.brushSize = BRUSH_SIZE
        showPane(tool, camera)
        val at = camera.toPixel(CurvePoint(1f, 0.5f))
        onNodeWithTag(PANE_TAG).performMouseInput { moveTo(at) }
        waitForIdle()
        val ends = tool.overlay(View.SIDE, camera).lines.filter { it.color == TEST_CURVE_LOOK.affected }.flatMap { listOf(it.from, it.to) }
        val reach = ends.maxOf { (it - at).getDistance() }
        assertEquals(BRUSH_SIZE * RADIUS_OF_SIZE_IN_TEST, reach, 3f, "the dashed stretch stops at the ring either way along the axis")
    }

    @Test
    fun aDabOnAShortenedLegLeavesWhatItDoesNotTouchExactly() = runComposeUiTest {
        val bench = CurveToolBench(RIPPLED)
        val before = assertNotNull(bench.outline())
        val peak = before.points[18]
        val camera = short(Vec3(peak.x * SHORT, peak.y, 0f), SCALE * 4f)
        bench.state.curves.brushSize = BRUSH_SIZE
        showPane(SmoothTool(bench.state, TEST_CURVE_LOOK), camera)
        val at = camera.toPixel(peak)
        mouseDrag(listOf(at, at + Offset(2f, 0f)))
        val after = assertNotNull(bench.outline())
        assertNotEquals(before, after)
        assertEquals(before.points.first(), after.points.first(), "sections it left keep their stored values")
        assertEquals(before.points.last(), after.points.last())
    }

    @Test
    fun eraseTakesWhatLiesWithinItsReachOnScreenAlongTheAxis() {
        val camera = short()
        val reach = TEST_CURVE_LOOK.grabReach * camera.planePixel
        val outline = LEG_SIDE
        val end = outline.points[18]
        val pixels = TEST_CURVE_LOOK.grabReach * 1.5f
        val beyond = camera.toPlane(camera.toPixel(end) + Offset(pixels, 0f))
        assertNull(
            erasure(outline, listOf(camera.shownPoint(beyond)), reach, thisSide = false, overridden = false, camera.shownCurve(outline)),
            "a stroke half as far again as the reach on screen takes nothing",
        )
        val within = camera.toPlane(camera.toPixel(end) + Offset(pixels / 2f, 0f))
        assertNotNull(erasure(outline, listOf(camera.shownPoint(within)), reach, thisSide = false, overridden = false, camera.shownCurve(outline)))
    }
}
