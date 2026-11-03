package dev.chieppa.squash.screens.edit

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.moved
import dev.chieppa.squash.screens.sheet.PaneChip
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.paneChip
import dev.chieppa.squash.screens.sheet.pixelsLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val REACH = 10f

class CurveReadoutsTest {
    private val curve = edges(0f to 1f, 1f to 1f, 2f to 0f, 0f to -1f)
    private val closed = edges(0f to 0f, 1f to 1f, 2f to 0f, 1f to -1f, 0f to 0f)

    private fun onScreen(point: CurvePoint) = Offset(point.x * 100f, point.y * 100f)

    @Test
    fun aSelectedHandleWorksOnItsOwnSection() {
        assertEquals(setOf(1), curve.selectedSections(4))
        assertEquals(setOf(1), curve.selectedSections(5))
        assertEquals(listOf(4, 5), curve.shownHandles(4))
    }

    @Test
    fun aSelectedAnchorWorksOnBothSections() {
        assertEquals(setOf(0, 1), curve.selectedSections(3))
        assertEquals(setOf(0), curve.selectedSections(0))
        assertEquals(setOf(2), curve.selectedSections(9))
        assertEquals(setOf(0, 3), closed.selectedSections(0), "a closed curve's seam has a section each side")
    }

    @Test
    fun nothingSelectedShowsEveryHandle() {
        assertEquals(emptySet(), curve.selectedSections(null))
        assertEquals(listOf(1, 2, 4, 5, 7, 8), curve.shownHandles(null))
    }

    @Test
    fun aPressFindsTheNearestPointInReach() {
        val handle = onScreen(curve.points[4])
        assertEquals(4, curve.pointAt(handle, REACH, null, ::onScreen))
        assertEquals(3, curve.pointAt(onScreen(curve.points[3]), REACH, null, ::onScreen))
        assertNull(curve.pointAt(Offset(-500f, -500f), REACH, null, ::onScreen))
    }

    @Test
    fun aHandleNotOnShowCannotBeGrabbed() {
        val hidden = onScreen(curve.points[8])
        assertEquals(8, curve.pointAt(hidden, REACH, null, ::onScreen))
        assertNull(curve.pointAt(hidden, REACH, 1, ::onScreen))
    }

    @Test
    fun anAnchorWinsOverAHandleJustBesideIt() {
        val close = edges(0f to 0f, 0.01f to 0f)
        assertEquals(0, close.pointAt(Offset(0.4f, 0f), REACH, null, ::onScreen))
    }

    @Test
    fun aClosedCurvesLastPointBelongsToItsFirstAnchor() {
        assertEquals(0, closed.owningAnchor(closed.points.lastIndex))
        assertEquals(0, closed.owningAnchor(1))
        assertEquals(3, closed.owningAnchor(4))
    }

    @Test
    fun aSpanInsideTheRangeStaysLocalAndTheFarEndAndFrontDoNot() {
        val leg = LEG_SIDE
        assertFalse(reachesWholePart(View.SIDE, leg, leg.moved(4, CurvePoint(0.7f, 0.9f))))
        assertFalse(reachesWholePart(View.TOP, leg, leg.moved(4, CurvePoint(0.7f, 0.9f))))
        val tip = leg.points.indexOf(CurvePoint(2f, 0f))
        assertTrue(reachesWholePart(View.SIDE, leg, leg.moved(tip, CurvePoint(2.4f, 0f))))
        assertTrue(reachesWholePart(View.FRONT, leg, leg.moved(4, CurvePoint(0.7f, 0.9f))))
    }

    @Test
    fun theSelectionOnlyCountsForItsOwnCurve() {
        val part = PartId("p")
        val state = CurveEditState()
        state.selection = CurveSelection(part, View.SIDE, 4)
        assertEquals(4, state.selectionIn(part, View.SIDE, curve)?.index)
        assertNull(state.selectionIn(part, View.TOP, curve))
        assertNull(state.selectionIn(PartId("q"), View.SIDE, curve))
        state.selection = CurveSelection(part, View.SIDE, 99)
        assertNull(state.selectionIn(part, View.SIDE, curve))
        state.clear()
        assertNull(state.selection)
    }

    @Test
    fun theFitErrorBelongsToTheCurveItWasMeasuredOn() {
        val state = CurveEditState()
        assertNull(state.fitError(curve))
        assertNull(state.fitError(null))
        state.recordFit(curve, 0.6f)
        assertEquals(0.6f, state.fitError(curve))
        assertNull(state.fitError(closed))
    }

    @Test
    fun fullShowsRefitOnlyWhileTheGraphWorks() {
        assertEquals(PaneChip.LIVE, paneChip(SheetPane.FULL, emptySet(), null, refitting = false))
        assertEquals(PaneChip.REFIT, paneChip(SheetPane.FULL, emptySet(), null, refitting = true))
        assertEquals(PaneChip.DRAWN, paneChip(SheetPane.SIDE, setOf(View.SIDE), null, refitting = true))
    }

    @Test
    fun theChipReadsOneDecimal() {
        assertEquals("0.6", pixelsLabel(0.6f))
        assertEquals("1.0", pixelsLabel(0.96f))
    }
}
