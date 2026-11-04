package dev.chieppa.squash.screens.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.closed
import dev.chieppa.squash.screens.edit.CurveToolBench
import dev.chieppa.squash.screens.edit.PANE_TAG
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.edit.TEST_STROKE_LOOK
import dev.chieppa.squash.screens.edit.showPane
import dev.chieppa.stretch.frame.OverlayDot
import dev.chieppa.stretch.frame.ViewportOverlay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val POINTS = 100
private const val NEAR_PIXELS = 2f

@OptIn(ExperimentalTestApi::class)
class StrokeGuideTest {
    private val look = TEST_STROKE_LOOK

    private fun tool(bench: CurveToolBench): DrawOutlineTool {
        bench.state.curves.snapGuides = false
        return DrawOutlineTool(bench.state, look, TEST_CURVE_LOOK)
    }

    private fun CurveToolBench.arc(view: View, turns: Double, cx: Float = 1f, count: Int = POINTS): List<Offset> = List(count) {
        val angle = turns * 2.0 * PI * it / (count - 1)
        camera(view).toPixel(CurvePoint(cx + 0.5f * cos(angle).toFloat(), 0.5f * sin(angle).toFloat()))
    }

    private fun ComposeUiTest.pressThrough(path: List<Offset>) {
        onNodeWithTag(PANE_TAG).performMouseInput {
            moveTo(path.first())
            press()
            path.drop(1).forEach { moveTo(it) }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.letGo() {
        onNodeWithTag(PANE_TAG).performMouseInput { release() }
        waitForIdle()
    }

    private fun ViewportOverlay.connectors() = lines.filter { it.color == look.connector }

    private fun ViewportOverlay.dropped() = lines.filter { it.color == look.dropped }

    private fun ViewportOverlay.markerAt(at: Offset): OverlayDot? =
        dots.firstOrNull { (it.color == look.startFill || it.radius == look.startLitRadius) && (it.center - at).getDistance() < NEAR_PIXELS }

    private fun ViewportOverlay.touches(at: Offset) = connectors().any {
        (it.from - at).getDistance() < look.connectorDash || (it.to - at).getDistance() < look.connectorDash
    }

    @Test
    fun aStrokeInAClosedViewShowsItsRunBackToTheStartAndAHollowMarker() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        val tool = tool(bench)
        val camera = bench.camera(View.FRONT)
        showPane(tool, camera)
        val path = bench.arc(View.FRONT, 0.5)
        pressThrough(path)
        val overlay = tool.overlay(View.FRONT, camera)
        assertTrue(overlay.connectors().isNotEmpty(), "the dashed run")
        assertTrue(overlay.touches(path.first()) && overlay.touches(path.last()), "from the tip to the start")
        val marker = assertNotNull(overlay.markerAt(path.first()), "the start's marker")
        assertEquals(look.startFill, marker.color)
        assertEquals(look.startRadius, marker.radius)
        letGo()
        assertTrue(assertNotNull(bench.outline(View.FRONT)).closed)
    }

    @Test
    fun theMarkerLightsOnceTheStrokeIsWithinClosingReach() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        val tool = tool(bench)
        val camera = bench.camera(View.FRONT)
        showPane(tool, camera)
        val path = bench.arc(View.FRONT, 1.0)
        val nearlyRound = path.subList(0, POINTS - POINTS / 4)
        pressThrough(nearlyRound)
        assertEquals(look.startRadius, assertNotNull(tool.overlay(View.FRONT, camera).markerAt(path.first())).radius, "not yet")
        onNodeWithTag(PANE_TAG).performMouseInput { path.subList(POINTS - POINTS / 4, POINTS - 1).forEach { moveTo(it) } }
        waitForIdle()
        val marker = assertNotNull(tool.overlay(View.FRONT, camera).markerAt(path.first()))
        assertEquals(look.tip, marker.color)
        assertEquals(look.startLitRadius, marker.radius)
    }

    @Test
    fun aStrokeOnAnOpenPartShowsRunsToTheSocketPlane() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        val tool = tool(bench)
        val camera = bench.camera()
        showPane(tool, camera)
        val path = bench.arc(View.SIDE, 0.5, cx = 1f)
        pressThrough(path)
        val overlay = tool.overlay(View.SIDE, camera)
        val startPlane = camera.toPlane(path.first())
        val tipPlane = camera.toPlane(path.last())
        assertTrue(overlay.connectors().any { (it.to - camera.toPixel(CurvePoint(0f, startPlane.y))).getDistance() < look.connectorDash || (it.from - camera.toPixel(CurvePoint(0f, startPlane.y))).getDistance() < look.connectorDash })
        assertTrue(overlay.connectors().any { (it.to - camera.toPixel(CurvePoint(0f, tipPlane.y))).getDistance() < look.connectorDash || (it.from - camera.toPixel(CurvePoint(0f, tipPlane.y))).getDistance() < look.connectorDash })
    }

    @Test
    fun whatGoesPastThePlaneShowsAsDropped() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        val tool = tool(bench)
        val camera = bench.camera()
        showPane(tool, camera)
        pressThrough(bench.arc(View.SIDE, 0.9, cx = 0.3f))
        assertTrue(tool.overlay(View.SIDE, camera).dropped().isNotEmpty())
    }

    @Test
    fun closeEndOffShowsNoRunAndLeavesTheEndsApart() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        bench.state.curves.closeEnd = false
        val tool = tool(bench)
        val camera = bench.camera(View.FRONT)
        showPane(tool, camera)
        val path = bench.arc(View.FRONT, 0.5)
        pressThrough(path)
        val overlay = tool.overlay(View.FRONT, camera)
        assertTrue(overlay.connectors().isEmpty())
        assertNotNull(overlay.markerAt(path.first()), "the start still shows")
        letGo()
        assertFalse(assertNotNull(bench.outline(View.FRONT)).closed)
    }

    @Test
    fun overADrawnCurveTheMarkerLightsWhenTheTipCanJoinIt() = runComposeUiTest {
        val bench = CurveToolBench()
        val tool = tool(bench)
        val camera = bench.camera()
        showPane(tool, camera)
        val start = camera.toPixel(CurvePoint(0.5f, 0.5f))
        val away = camera.toPixel(CurvePoint(1f, 1.4f))
        val back = camera.toPixel(CurvePoint(1.2f, 0.52f))
        pressThrough(listOf(start, Offset((start.x + away.x) / 2f, (start.y + away.y) / 2f), away))
        val far = tool.overlay(View.SIDE, camera)
        assertEquals(look.startRadius, assertNotNull(far.markerAt(start)).radius, "too far to join")
        assertTrue(far.connectors().isEmpty())
        onNodeWithTag(PANE_TAG).performMouseInput { moveTo(back) }
        waitForIdle()
        val near = tool.overlay(View.SIDE, camera)
        assertEquals(look.startLitRadius, assertNotNull(near.markerAt(start)).radius, "within reach")
        assertTrue(near.lines.any { it.color == TEST_CURVE_LOOK.affected }, "the old span still shows")
    }

    @Test
    fun aStrokeTooFarFromADrawnCurveIsAllDropped() = runComposeUiTest {
        val bench = CurveToolBench()
        val tool = tool(bench)
        val camera = bench.camera()
        showPane(tool, camera)
        val path = List(20) { camera.toPixel(CurvePoint(0.5f + it * 0.02f, 1.3f)) }
        pressThrough(path)
        val overlay = tool.overlay(View.SIDE, camera)
        assertTrue(overlay.dropped().isNotEmpty())
        assertTrue(overlay.lines.none { it.color == look.ink && it.width == look.width }, "nothing of it stays")
    }
}
