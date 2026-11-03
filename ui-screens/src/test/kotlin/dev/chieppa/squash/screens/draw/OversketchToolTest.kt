package dev.chieppa.squash.screens.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.edit.CurveToolBench
import dev.chieppa.squash.screens.edit.EditCurvesTool
import dev.chieppa.squash.screens.edit.LEG_SIDE
import dev.chieppa.squash.screens.edit.PANE_TAG
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.edit.TEST_STROKE_LOOK
import dev.chieppa.squash.screens.edit.edges
import dev.chieppa.squash.screens.edit.mouseDrag
import dev.chieppa.squash.screens.edit.showPane
import dev.chieppa.squash.screens.sheet.PartAxis
import dev.chieppa.squash.screens.sheet.viewsDisagree
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val STROKE_POINTS = 40

@OptIn(ExperimentalTestApi::class)
class OversketchToolTest {
    private fun tool(bench: CurveToolBench) = DrawOutlineTool(bench.state, TEST_STROKE_LOOK, TEST_CURVE_LOOK)

    /** A stroke along the upper run from [fromX] to [toX], bowing up by [height] in the plane, as pane pixels. */
    private fun bow(bench: CurveToolBench, fromX: Float, toX: Float, height: Float): List<Offset> = List(STROKE_POINTS) { i ->
        val t = i.toFloat() / (STROKE_POINTS - 1)
        bench.camera().toPixel(CurvePoint(fromX + (toX - fromX) * t, 0.5f + height * sin(Math.PI.toFloat() * t)))
    }

    @Test
    fun aStrokeOverACurveReplacesOnlyItsSpanInOneStep() = runComposeUiTest {
        val bench = CurveToolBench()
        showPane(tool(bench), bench.camera())
        val before = bench.steps()
        mouseDrag(bow(bench, 0.5f, 1f, 0.2f))
        assertEquals(before + 1, bench.steps())
        val curve = assertNotNull(bench.outline())
        assertEquals(LEG_SIDE.points.take(4), curve.points.take(4), "the section before the span is exact")
        assertEquals(LEG_SIDE.points.takeLast(10), curve.points.takeLast(10), "the sections after it are exact")
        assertEquals(0.7f, curve.points.maxOf { it.y }, 0.1f)
        bench.session.history.undo()
        assertEquals(LEG_SIDE, bench.outline())
    }

    @Test
    fun aStrokeFarFromTheCurveDoesNothing() = runComposeUiTest {
        val bench = CurveToolBench()
        showPane(tool(bench), bench.camera())
        val before = bench.steps()
        val far = List(STROKE_POINTS) { bench.camera().toPixel(CurvePoint(0.5f + it * 0.02f, 1.3f)) }
        mouseDrag(far)
        assertEquals(before, bench.steps())
        assertEquals(LEG_SIDE, bench.outline())
    }

    @Test
    fun theLiveStrokeIsTheWholeOutlineAsItWouldBeSoTheHighlightFollows() = runComposeUiTest {
        val bench = CurveToolBench()
        val top = edges(0f to 0.4f, 1f to 0.4f, 2f to 0f, 1f to -0.4f, 0f to -0.4f)
        bench.session.send(SetOutline(bench.id, View.TOP, top))
        val tool = tool(bench)
        showPane(tool, bench.camera())
        assertTrue(viewsDisagree(mapOf(View.SIDE to LEG_SIDE, View.TOP to top)).isEmpty())
        val path = List(STROKE_POINTS) { i ->
            val t = i.toFloat() / (STROKE_POINTS - 1)
            bench.camera().toPixel(CurvePoint(1.5f + 0.9f * t, 0.5f - 0.3f * t))
        }
        onNodeWithTag(PANE_TAG).performMouseInput {
            moveTo(path.first())
            press()
            path.drop(1).forEach { moveTo(it) }
        }
        waitForIdle()
        val live = assertNotNull(tool.liveStroke)
        assertEquals(View.SIDE, live.view)
        assertEquals(View.SIDE, tool.drawingIn)
        assertEquals(LEG_SIDE.points.first(), live.points.first(), "the kept curve stands in around the stroke")
        assertEquals(LEG_SIDE.points.last(), live.points.last())
        val found = viewsDisagree(mapOf(View.SIDE to LEG_SIDE, View.TOP to top), live)
        assertTrue(found.any { it.view == View.SIDE && it.axis == PartAxis.ALONG }, "past Top's end as the stroke goes")
        assertEquals(LEG_SIDE, bench.outline(), "nothing reaches the document until it ends")
        assertTrue(tool.overlay(View.SIDE, bench.camera()).lines.size > LEG_SIDE.points.size, "the stroke shows over the points")
        onNodeWithTag(PANE_TAG).performMouseInput { release() }
        waitForIdle()
        assertNull(tool.liveStroke)
    }

    @Test
    fun aDrawnViewShowsItsPointsWithoutAStroke() = runComposeUiTest {
        val bench = CurveToolBench()
        val tool = tool(bench)
        assertEquals(7, tool.overlay(View.SIDE, bench.camera()).rects.size)
        assertTrue(tool.overlay(View.TOP, bench.camera(View.TOP)).isEmpty)
    }

    @Test
    fun theChipCarriesTheFitsRealErrorUntilTheCurveIsMovedByHand() = runComposeUiTest {
        val bench = CurveToolBench(side = null)
        val tool = tool(bench)
        showPane(tool, bench.camera())
        val loop = List(120) { i ->
            val angle = 2.0 * Math.PI * i / 119
            bench.camera().toPixel(CurvePoint(0.6f + kotlin.math.cos(angle).toFloat(), 0.8f * sin(angle).toFloat()))
        }
        mouseDrag(loop)
        val drawn = assertNotNull(tool.fitErrorPixels(View.SIDE))
        assertTrue(drawn > 0f && drawn < 3f, "the fitter holds to about a pixel, got $drawn")
        assertNull(tool.fitErrorPixels(View.TOP))

        val curve = assertNotNull(bench.outline())
        val edit = EditCurvesTool(bench.state, TEST_CURVE_LOOK)
        assertEquals(drawn, edit.fitErrorPixels(View.SIDE), "the chip reads the same under Edit curves")
        bench.session.send(SetOutline(bench.id, View.SIDE, curve.copy(points = curve.points.map { it.copy(y = it.y * 1.01f) })))
        assertNull(edit.fitErrorPixels(View.SIDE), "a curve moved by hand no longer has a fit to report")
        bench.session.history.undo()
        assertEquals(drawn, edit.fitErrorPixels(View.SIDE))
    }

    @Test
    fun anOversketchLeavesItsRefitForTheReadouts() = runComposeUiTest {
        val bench = CurveToolBench()
        showPane(tool(bench), bench.camera())
        mouseDrag(bow(bench, 0.5f, 1f, 0.2f))
        val refit = assertNotNull(bench.state.curves.refit)
        assertEquals(1, refit.firstSection)
        assertEquals(1, refit.sections)
        assertEquals(false, refit.wholePart)
        assertEquals(bench.outline(), refit.curve)
        assertNotEquals(0f, assertNotNull(bench.state.curves.fitError(refit.curve)))
    }
}
