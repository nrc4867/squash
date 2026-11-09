package dev.chieppa.squash.screens.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetBranchOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.draw.DrawOutlineTool
import dev.chieppa.squash.screens.shell.BuildTool
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val SLEEVE = GarmentId("sleeve")

/** The outline tools in Dress: they draw on the picked garment's branch over the selected part, or its Redraw of it, never the part. */
@OptIn(ExperimentalTestApi::class)
class GarmentOutlineToolTest {
    /** A bench in Dress with [tool] armed and a sleeve over the leg, its branch holding [branchSide] in Side. */
    private fun dressed(tool: BuildTool, branchSide: OutlineCurve?): CurveToolBench {
        val bench = CurveToolBench()
        val outlines = branchSide?.let { mapOf(View.SIDE to it) }.orEmpty()
        bench.session.send(AddGarment(Garment(SLEEVE, "Sleeve", branches = listOf(GarmentBranch(bench.id, outlines = outlines)))))
        bench.state.tool.value = tool
        bench.state.dress.garment.value = SLEEVE
        return bench
    }

    private fun CurveToolBench.branchSide(): OutlineCurve? =
        session.document.value.character.wardrobe.garment(SLEEVE)?.branch(id)?.outlines?.get(View.SIDE)

    private fun CurveToolBench.redrawSide(): OutlineCurve? =
        (session.document.value.character.wardrobe.garment(SLEEVE)?.changes?.get(id) as? BodyPartChange.Redraw)?.data?.outlines?.get(View.SIDE)

    private fun loop(bench: CurveToolBench, cx: Float, count: Int = 120): List<Offset> = List(count) {
        val angle = 2.0 * PI * it / (count - 1)
        bench.camera().toPixel(CurvePoint(cx + cos(angle).toFloat(), 0.8f * sin(angle).toFloat()))
    }

    private fun across(bench: CurveToolBench, from: CurvePoint, to: CurvePoint): List<Offset> = List(12) { i ->
        val t = i / 11f
        bench.camera().toPixel(CurvePoint(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t))
    }

    @Test
    fun drawGarmentWritesTheBranchAndLeavesThePart() = runComposeUiTest {
        val bench = dressed(BuildTool.DRAW_GARMENT, branchSide = null)
        val part = bench.outline()
        showPane(DrawOutlineTool(bench.state, TEST_STROKE_LOOK, TEST_CURVE_LOOK), bench.camera())
        mouseDrag(loop(bench, cx = 0.6f))
        assertNotNull(bench.branchSide(), "the stroke is the branch's Side outline")
        assertIs<SetBranchOutline>(bench.session.history.steps.value.done.last())
        assertEquals(part, bench.outline(), "the leg keeps its own outline")
        bench.session.history.undo()
        assertNull(bench.branchSide())
    }

    @Test
    fun eraseGarmentTakesASectionOfTheBranch() = runComposeUiTest {
        val bench = dressed(BuildTool.ERASE_GARMENT, branchSide = LEG_SIDE)
        showPane(EraseTool(bench.state, TEST_STROKE_LOOK, TEST_CURVE_LOOK), bench.camera())
        mouseDrag(across(bench, CurvePoint(0.75f, 0.45f), CurvePoint(0.75f, 0.55f)))
        assertNotEquals(LEG_SIDE, assertNotNull(bench.branchSide()))
        assertEquals(LEG_SIDE, bench.outline(), "the leg keeps its own outline")
    }

    @Test
    fun aDrawingInDressWithNoGarmentPickedDoesNothing() = runComposeUiTest {
        val bench = dressed(BuildTool.DRAW_GARMENT, branchSide = null)
        bench.state.dress.garment.value = null
        val steps = bench.steps()
        showPane(DrawOutlineTool(bench.state, TEST_STROKE_LOOK, TEST_CURVE_LOOK), bench.camera())
        mouseDrag(loop(bench, cx = 0.6f))
        assertEquals(steps, bench.steps())
        assertEquals(LEG_SIDE, bench.outline())
    }

    @Test
    fun whileRedrawingTheToolsWriteTheRedrawAndKeepItsOtherViews() = runComposeUiTest {
        val bench = dressed(BuildTool.DRAW_GARMENT, branchSide = LEG_SIDE)
        val top = edges(0f to 0.3f, 1f to 0f, 0f to -0.3f)
        bench.session.send(SetBodyPartChange(SLEEVE, bench.id, BodyPartChange.Redraw(PartData(outlines = mapOf(View.TOP to top)))))
        bench.state.dress.redrawing.value = bench.id
        showPane(DrawOutlineTool(bench.state, TEST_STROKE_LOOK, TEST_CURVE_LOOK), bench.camera())
        mouseDrag(loop(bench, cx = 0.6f))
        assertNotNull(bench.redrawSide(), "the stroke is the Redraw's Side outline")
        val redraw = assertIs<BodyPartChange.Redraw>(bench.session.document.value.character.wardrobe.garment(SLEEVE)?.changes?.get(bench.id))
        assertEquals(top, redraw.data.outlines[View.TOP])
        assertEquals(LEG_SIDE, bench.branchSide(), "the branch is left as it was")
        assertEquals(LEG_SIDE, bench.outline())
    }
}
