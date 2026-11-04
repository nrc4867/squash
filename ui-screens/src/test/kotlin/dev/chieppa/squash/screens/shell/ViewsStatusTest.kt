package dev.chieppa.squash.screens.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.graph.GraphProgress
import dev.chieppa.squash.graph.StageCount
import dev.chieppa.squash.screens.draw.penPressure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViewsStatusTest {
    @Test
    fun theLineFollowsWhatIsHappening() {
        assertEquals(ViewsLine.DRAWN_FROM_TEMPLATE, viewsLine(ViewCounts(1, 2), drawing = false, working = false))
        assertEquals(ViewsLine.WORKING, viewsLine(ViewCounts(2, 1), drawing = false, working = true))
        assertEquals(ViewsLine.ALL_DRAWN, viewsLine(ViewCounts(3, 0), drawing = false, working = true))
        assertEquals(ViewsLine.DRAWING, viewsLine(ViewCounts(1, 1), drawing = true, working = true))
    }

    @Test
    fun aStrokeInANewViewNoLongerComesFromTheTemplate() {
        assertEquals(ViewCounts(1, 1), countsWhileDrawing(ViewCounts(1, 2), drawingNewView = true))
        assertEquals(ViewCounts(1, 2), countsWhileDrawing(ViewCounts(1, 2), drawingNewView = false))
    }

    @Test
    fun theFitRowsCountTheirOwnStages() {
        val progress = GraphProgress(
            3,
            7,
            mapOf(
                BuildStages.SLICES to StageCount(2, 2),
                BuildStages.MESH to StageCount(1, 2),
                BuildStages.SURFACE to StageCount(0, 2),
                BuildStages.ASSEMBLY to StageCount(0, 1),
            ),
        )
        assertEquals(true, progress.count(FitStages.SKELETON).finished)
        assertEquals(StageCount(1, 5), progress.count(FitStages.SURFACE))
        assertEquals(BuildStages.MESH, progress.working(BuildStages.ORDER))
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun change(type: PointerType, pressure: Float) = PointerInputChange(
        id = PointerId(1),
        uptimeMillis = 1,
        position = Offset.Zero,
        pressed = true,
        pressure = pressure,
        previousUptimeMillis = 0,
        previousPosition = Offset.Zero,
        previousPressed = true,
        isInitiallyConsumed = false,
        type = type,
    )

    @Test
    fun onlyAPenReportsPressure() {
        assertEquals(0.62f, penPressure(change(PointerType.Stylus, 0.62f)))
        assertNull(penPressure(change(PointerType.Mouse, 1f)))
        assertNull(penPressure(change(PointerType.Touch, 0.4f)))
        assertEquals("0.62", pressureLabel(0.62f))
    }
}
