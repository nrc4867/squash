package dev.chieppa.squash.screens.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.sheet.PaneViewport
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val HANDLE = 4
private const val ANCHOR = 3
private const val OPEN_END = 0

@OptIn(ExperimentalTestApi::class)
class EditCurvesToolTest {
    private fun tool(bench: CurveToolBench) = EditCurvesTool(bench.state, TEST_CURVE_LOOK)

    @Test
    fun aHandleDragShowsLiveAndIsOneUndoStep() = runComposeUiTest {
        val bench = CurveToolBench()
        val tool = tool(bench)
        showPane(tool, bench.camera())
        val start = assertNotNull(bench.outline())
        val before = bench.steps()
        val from = bench.pixelOf(HANDLE)
        onNodeWithTag(PANE_TAG).performMouseInput {
            moveTo(from)
            press()
            moveTo(from + Offset(0f, -20f))
        }
        waitForIdle()
        assertTrue(tool.holdsFit, "the cameras hold still under a drag")
        val live = assertNotNull(bench.outline())
        assertNotEquals(start.points[HANDLE], live.points[HANDLE], "the document follows the drag")
        assertEquals(before, bench.steps())

        onNodeWithTag(PANE_TAG).performMouseInput {
            moveTo(from + Offset(0f, -40f))
            release()
        }
        waitForIdle()
        assertFalse(tool.holdsFit)
        assertEquals(before + 1, bench.steps())
        val moved = assertNotNull(bench.outline())
        assertEquals(start.points[HANDLE].y + 40f / SCALE, moved.points[HANDLE].y, 1e-3f)
        assertEquals(start.points[HANDLE].x, moved.points[HANDLE].x, 1e-3f)

        bench.session.history.undo()
        assertEquals(start, bench.outline())
    }

    @Test
    fun aClickSelectsAndChangesNothingInTheDocument() = runComposeUiTest {
        val bench = CurveToolBench()
        showPane(tool(bench), bench.camera())
        val document = bench.session.document.value
        val before = bench.steps()
        mouseDrag(listOf(bench.pixelOf(ANCHOR)))
        assertEquals(CurveSelection(bench.id, View.SIDE, ANCHOR), bench.state.curves.selection)
        assertEquals(document, bench.session.document.value)
        assertEquals(before, bench.steps())
    }

    @Test
    fun aPressOnNothingClearsTheSelection() = runComposeUiTest {
        val bench = CurveToolBench()
        showPane(tool(bench), bench.camera())
        mouseDrag(listOf(bench.pixelOf(ANCHOR)))
        assertNotNull(bench.state.curves.selection)
        val before = bench.outline()
        mouseDrag(listOf(Offset(5f, 5f), Offset(30f, 5f)))
        assertNull(bench.state.curves.selection)
        assertEquals(before, bench.outline())
    }

    @Test
    fun anAnchorDragCarriesItsHandlesAndLeavesTheRestExact() = runComposeUiTest {
        val bench = CurveToolBench()
        showPane(tool(bench), bench.camera())
        val start = assertNotNull(bench.outline())
        val from = bench.pixelOf(ANCHOR)
        mouseDrag(listOf(from, from + Offset(0f, -30f)))
        val moved = assertNotNull(bench.outline())
        for (i in ANCHOR - 1..ANCHOR + 1) assertEquals(start.points[i].y + 0.3f, moved.points[i].y, 1e-3f)
        assertEquals(start.points.take(ANCHOR - 1), moved.points.take(ANCHOR - 1))
        assertEquals(start.points.drop(ANCHOR + 2), moved.points.drop(ANCHOR + 2))
    }

    @Test
    fun anOpenEndStaysOnTheSocketPlane() = runComposeUiTest {
        val bench = CurveToolBench()
        showPane(tool(bench), bench.camera())
        val from = bench.pixelOf(OPEN_END)
        mouseDrag(listOf(from, from + Offset(40f, -30f), from + Offset(-40f, -30f)))
        val moved = assertNotNull(bench.outline()).points[OPEN_END]
        assertEquals(0f, moved.x)
        assertEquals(0.8f, moved.y, 1e-3f)
    }

    @Test
    fun theSecondButtonDrawsNothing() = runComposeUiTest {
        val bench = CurveToolBench()
        showPane(tool(bench), bench.camera())
        val start = bench.outline()
        val from = bench.pixelOf(ANCHOR)
        onNodeWithTag(PANE_TAG).performMouseInput {
            moveTo(from)
            press(MouseButton.Secondary)
            moveTo(from + Offset(0f, -30f))
            release(MouseButton.Secondary)
        }
        waitForIdle()
        assertEquals(start, bench.outline())
        assertNull(bench.state.curves.selection)
    }

    @Test
    fun aDragThePaneLosesRestoresTheCurve() = runComposeUiTest {
        val bench = CurveToolBench()
        val tool = tool(bench)
        val first = bench.camera()
        var camera by mutableStateOf(first)
        setStretchContent {
            with(density) {
                Box(Modifier.size(PANE_WIDTH.toDp(), PANE_HEIGHT.toDp())) {
                    PaneViewport(SheetPane.SIDE, null, tool.pointerInput(camera.view, camera).testTag(PANE_TAG))
                }
            }
        }
        val start = bench.outline()
        val before = bench.steps()
        val from = bench.pixelOf(HANDLE)
        onNodeWithTag(PANE_TAG).performMouseInput {
            moveTo(from)
            press()
            moveTo(from + Offset(0f, -30f))
        }
        waitForIdle()
        assertNotEquals(start, bench.outline())
        camera = bench.camera()
        waitForIdle()
        assertEquals(start, bench.outline())
        assertEquals(before, bench.steps())
        assertFalse(bench.state.curves.dragging)
    }

    @Test
    fun undoMidDragDoesNothingAndTheDragStillLands() = runComposeUiTest {
        val bench = CurveToolBench()
        showPane(tool(bench), bench.camera())
        val before = bench.steps()
        val from = bench.pixelOf(HANDLE)
        onNodeWithTag(PANE_TAG).performMouseInput {
            moveTo(from)
            press()
            moveTo(from + Offset(0f, -30f))
        }
        waitForIdle()
        bench.state.undo()
        bench.state.redo()
        onNodeWithTag(PANE_TAG).performMouseInput { release() }
        waitForIdle()
        assertEquals(before + 1, bench.steps())
    }

    @Test
    fun aDragLeavesItsRefitForTheReadouts() = runComposeUiTest {
        val bench = CurveToolBench()
        showPane(tool(bench), bench.camera())
        val from = bench.pixelOf(ANCHOR)
        mouseDrag(listOf(from, from + Offset(0f, -30f)))
        val refit = assertNotNull(bench.state.curves.refit)
        assertEquals(0, refit.firstSection)
        assertEquals(2, refit.sections, "an anchor shapes the sections either side")
        assertEquals(bench.outline(), refit.curve)
        assertFalse(refit.wholePart)
    }

    @Test
    fun theOverlayShowsTheStoredPointsInTheirOwnPane() = runComposeUiTest {
        val bench = CurveToolBench()
        val tool = tool(bench)
        val all = tool.overlay(View.SIDE, bench.camera())
        assertEquals(12, all.dots.size, "every section's handles with nothing selected")
        assertEquals(7, all.rects.size, "every anchor")
        assertTrue(tool.overlay(View.TOP, bench.camera(View.TOP)).isEmpty, "Top has no outline")
        bench.state.curves.selection = CurveSelection(bench.id, View.SIDE, HANDLE)
        assertEquals(2, tool.overlay(View.SIDE, bench.camera()).dots.size, "a selected handle's section only")
        bench.state.curves.selection = CurveSelection(bench.id, View.SIDE, ANCHOR)
        assertEquals(4, tool.overlay(View.SIDE, bench.camera()).dots.size, "both sections at a selected anchor")
    }
}
