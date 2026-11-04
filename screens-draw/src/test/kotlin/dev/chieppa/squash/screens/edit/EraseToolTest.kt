package dev.chieppa.squash.screens.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.sections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class EraseToolTest {
    private fun tool(bench: CurveToolBench) = EraseTool(bench.state, TEST_STROKE_LOOK, TEST_CURVE_LOOK)

    /** A stroke along [from] to [to] of the Side plane, where section 1 of the outline lies between its anchors. */
    private fun stroke(bench: CurveToolBench, from: CurvePoint, to: CurvePoint): List<Offset> {
        val camera = bench.camera()
        return List(12) { i ->
            val t = i / 11f
            camera.toPixel(CurvePoint(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t))
        }
    }

    @Test
    fun aStrokeOverOneSectionTakesItAsOneUndoStep() = runComposeUiTest {
        val bench = CurveToolBench()
        val before = assertNotNull(bench.outline())
        val steps = bench.steps()
        showPane(tool(bench), bench.camera())
        mouseDrag(stroke(bench, CurvePoint(0.75f, 0.45f), CurvePoint(0.75f, 0.55f)))
        val after = assertNotNull(bench.outline())
        assertNotEquals(before, after)
        assertEquals(before.sections, after.sections, "a section between two others becomes one straight section")
        assertEquals(before.points.first(), after.points.first(), "the ends stay on the socket plane")
        assertEquals(before.points.last(), after.points.last())
        assertEquals(steps + 1, bench.steps())
        assertEquals(1, assertNotNull(bench.state.curves.refit).sections)
        bench.session.history.undo()
        assertEquals(before, bench.outline())
    }

    @Test
    fun aStrokeOverMostOfTheOutlineClearsIt() = runComposeUiTest {
        val bench = CurveToolBench()
        val steps = bench.steps()
        showPane(tool(bench), bench.camera())
        val top = bench.camera().let { it.toPixel(CurvePoint(0f, 0.5f)) to it.toPixel(CurvePoint(2f, 0f)) }
        val bottom = bench.camera().let { it.toPixel(CurvePoint(1.5f, -0.5f)) to it.toPixel(CurvePoint(0f, -0.5f)) }
        mouseDrag(listOf(top.first, top.second, bottom.first, bottom.second))
        assertNull(bench.outline())
        assertEquals(steps + 1, bench.steps())
        assertNull(bench.state.curves.refit)
    }

    @Test
    fun aStrokeFarFromTheOutlineDoesNothing() = runComposeUiTest {
        val bench = CurveToolBench()
        val before = bench.outline()
        val steps = bench.steps()
        showPane(tool(bench), bench.camera())
        mouseDrag(stroke(bench, CurvePoint(0.5f, 1.5f), CurvePoint(1.5f, 1.5f)))
        assertEquals(before, bench.outline())
        assertEquals(steps, bench.steps())
    }

    @Test
    fun oneSideHasNoWayToEraseWhatThePairHoldsButCanTakeBackAnOverride() {
        val outline = LEG_SIDE
        val along = listOf(CurvePoint(0f, 0.5f), CurvePoint(2f, 0f), CurvePoint(1.5f, -0.5f), CurvePoint(0f, -0.5f))
        val reach = 0.1f
        assertNull(erasure(outline, along, reach, thisSide = true, overridden = false), "nothing to take back")
        val clearing = assertNotNull(erasure(outline, along, reach, thisSide = true, overridden = true))
        assertNull(clearing.curve, "the override goes and the pair's outline shows again")
        val paired = assertNotNull(erasure(outline, along, reach, thisSide = false, overridden = false))
        assertNull(paired.curve, "with Mirror on the pair's outline goes")
    }

    @Test
    fun theHighlightShowsWhatReleaseTakes() {
        val outline = LEG_SIDE
        val dab = listOf(CurvePoint(0.75f, 0.5f))
        val going = assertNotNull(erasure(outline, dab, 0.1f, thisSide = false, overridden = false))
        assertEquals(setOf(1), going.sections)
        assertEquals(outline.sections, assertNotNull(going.curve).sections)
        assertEquals(View.SIDE, View.SIDE)
    }
}
