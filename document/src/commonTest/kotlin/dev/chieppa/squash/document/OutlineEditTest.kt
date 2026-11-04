package dev.chieppa.squash.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun curve(vararg values: Pair<Float, Float>) = OutlineCurve(values.map { CurvePoint(it.first, it.second) })

class OutlineEditTest {
    @Test
    fun erasedSectionsBecomeOneStraightSectionBetweenTheirAnchors() {
        val curve = OutlineCurve(List(13) { CurvePoint(it.toFloat(), (it % 3).toFloat()) })
        val erased = assertNotNull(curve.withoutSections(setOf(1, 2)))
        assertEquals(3 * 3 + 1, erased.points.size)
        assertEquals(curve.points.subList(0, 4), erased.points.subList(0, 4), "the section before is the old curve's")
        assertEquals(curve.points.last(), erased.points.last(), "the far end stays")
        val (from, to) = erased.points[3] to erased.points[6]
        assertEquals(CurvePoint(from.x + (to.x - from.x) / 3f, from.y + (to.y - from.y) / 3f), erased.points[4], "the bridge runs straight")
        assertEquals(null, curve.withoutSections(setOf(0, 1, 2, 3)), "taking all of it leaves nothing")
    }

    @Test
    fun erasingKeepsAClosedCurveClosed() {
        val a = CurvePoint(0f, 0f)
        val curve = OutlineCurve(listOf(a) + List(8) { CurvePoint(it + 1f, 3f) } + a)
        val erased = assertNotNull(curve.withoutSections(setOf(0)))
        assertEquals(a, erased.points.first())
        assertEquals(a, erased.points.last())
        assertEquals(curve.sections, erased.sections)
    }

    /** Two sections with a smooth anchor at (2, 0). */
    private val two = curve(0f to 1f, 1f to 1f, 1f to 0f, 2f to 0f, 3f to 0f, 3f to 1f, 4f to 1f)

    /** A closed curve of two sections, smooth at its seam (0, 0). */
    private val loop = curve(0f to 0f, 0f to 1f, 2f to 1f, 2f to 0f, 2f to -1f, 0f to -1f, 0f to 0f)

    @Test
    fun anAnchorCarriesItsHandles() {
        val moved = two.moved(3, CurvePoint(2f, 1f))
        assertEquals(CurvePoint(2f, 1f), moved.points[3])
        assertEquals(CurvePoint(1f, 1f), moved.points[2])
        assertEquals(CurvePoint(3f, 1f), moved.points[4])
        assertEquals(two.points[0], moved.points[0])
        assertEquals(two.points[1], moved.points[1])
        assertEquals(two.points[5], moved.points[5])
    }

    @Test
    fun aHandleAtASmoothAnchorSwingsTheOtherKeepingItsLength() {
        val moved = two.moved(4, CurvePoint(2f, 3f))
        assertEquals(CurvePoint(2f, 3f), moved.points[4])
        assertEquals(2f, moved.points[2].x, 1e-5f)
        assertEquals(-1f, moved.points[2].y, 1e-5f)
        assertTrue(moved.isSmooth(3))
    }

    @Test
    fun aCornerAnchorsHandlesMoveAlone() {
        val moved = two.moved(4, CurvePoint(2f, 3f), corner = true)
        assertEquals(two.points[2], moved.points[2])
        assertFalse(moved.isSmooth(3))
    }

    @Test
    fun aHandleAtAnOpenEndHasNoPartner() {
        assertNull(two.oppositeHandle(0, 1))
        assertEquals(CurvePoint(5f, 5f), two.moved(1, CurvePoint(5f, 5f)).points[1])
    }

    @Test
    fun aClosedCurvesSeamMovesAsOnePoint() {
        val moved = loop.moved(0, CurvePoint(-1f, 0f))
        assertEquals(moved.points.first(), moved.points.last())
        assertEquals(CurvePoint(-1f, 1f), moved.points[1])
        assertEquals(CurvePoint(-1f, -1f), moved.points[5])
    }

    @Test
    fun aHandleAtTheSeamSwingsItsPartnerAcrossIt() {
        val moved = loop.moved(1, CurvePoint(1f, 1f))
        assertEquals(-0.70710677f, moved.points[5].x, 1e-5f)
        assertEquals(-0.70710677f, moved.points[5].y, 1e-5f)
    }

    @Test
    fun anOpenEndStaysOnThePlaneAndNothingGoesPastIt() {
        val limit = CurveLimit(0f, above = true)
        val end = two.moved(0, CurvePoint(5f, 3f), limit)
        assertEquals(CurvePoint(0f, 3f), end.points[0])
        val handle = two.moved(1, CurvePoint(-2f, 2f), limit)
        assertEquals(CurvePoint(0f, 2f), handle.points[1])
        val anchor = two.moved(3, CurvePoint(-1f, 0f), limit)
        assertEquals(CurvePoint(0f, 0f), anchor.points[3])
    }

    @Test
    fun theLimitLeavesAClosedCurveAlone() {
        val moved = loop.moved(3, CurvePoint(-3f, 0f), CurveLimit(0f, above = true))
        assertEquals(CurvePoint(-3f, 0f), moved.points[3])
    }

    @Test
    fun smoothingLinesUpTheSecondHandle() {
        val kinked = two.moved(4, CurvePoint(3f, 2f), corner = true)
        assertFalse(kinked.isSmooth(3))
        assertTrue(kinked.smoothed(3).isSmooth(3))
        assertEquals(kinked.points[2], kinked.smoothed(3).points[2], "the first handle holds")
    }

    @Test
    fun changedSectionsNamesOnlyWhatDiffers() {
        assertNull(two.changedSections(two))
        assertEquals(1..1, two.changedSections(two.moved(5, CurvePoint(9f, 9f))))
        assertEquals(0..1, two.changedSections(two.moved(3, CurvePoint(2f, 1f))))
        assertNotEquals(two, two.moved(5, CurvePoint(9f, 9f)))
    }

    @Test
    fun anOpenCurvesAnchorsAreEveryThirdPoint() {
        assertEquals(listOf(0, 3, 6), two.anchors)
        assertEquals(listOf(0, 3), loop.anchors)
        assertTrue(isAnchor(3) && !isAnchor(4))
        assertEquals(3, anchorOf(2))
        assertEquals(3, anchorOf(4))
        assertEquals(1, sectionOf(4, two.sections))
        assertEquals(1, sectionOf(6, two.sections))
    }
}
