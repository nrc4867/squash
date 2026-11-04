package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StrokeSnapTest {
    @Test
    fun guidesCatchAStrokesWidestPointWithinReachAndNoFurther() {
        val hump = arc(1f, 0f, 0.5f, PI)
        val guide = listOf(SnapGuide(Vec2(0f, 0.52f), Vec2(2f, 0.52f)))
        val caught = guideSnap(hump, guide, 8f * TEST_PIXEL)
        assertEquals(setOf(0), caught.caught)
        val moved = hump.map(caught::moved)
        assertEquals(0.52f, moved.maxOf { it.y }, 1e-4f)
        assertEquals(hump.first().y, moved.first().y, 1e-4f, "what is not caught holds")
        val far = guideSnap(hump, listOf(SnapGuide(Vec2(0f, 0.6f), Vec2(2f, 0.6f))), 8f * TEST_PIXEL)
        assertTrue(far.caught.isEmpty())
        assertEquals(hump, hump.map(far::moved))
    }

    @Test
    fun guidesCatchAStrokesEndsAndMoveItWithNoBreakByNoMoreThanTheReach() {
        val line = through(0.33f to 0.1f, 0.33f to 0.5f, 1.2f to 0.8f)
        val snap = guideSnap(line, listOf(SnapGuide(Vec2(1.22f, -1f), Vec2(1.22f, 1f))), 8f * TEST_PIXEL)
        assertEquals(1.22f, snap.moved(line.last()).x, 1e-4f)
        val moved = line.map(snap::moved)
        for (i in line.indices) assertTrue((moved[i] - line[i]).magnitude <= 8f * TEST_PIXEL + 1e-5f)
        for (i in 1 until moved.size) assertTrue((moved[i] - moved[i - 1]).magnitude < 2f * TEST_PIXEL, "no jump at $i")
    }

    @Test
    fun guidesCatchOnATiltedLineAsOnASquareOne() {
        val tilt = 0.6f
        val across = Vec2(cos(tilt), sin(tilt))
        val along = Vec2(-sin(tilt), cos(tilt))
        val hump = arc(0f, 0f, 0.5f, PI, from = -PI / 2.0).map { across * it.x + along * it.y }
        val guide = SnapGuide(across * 0.53f - along, across * 0.53f + along)
        val snap = guideSnap(hump, listOf(guide), 8f * TEST_PIXEL)
        assertEquals(setOf(0), snap.caught)
        assertEquals(0.53f, hump.map(snap::moved).maxOf { it dotWith across }, 1e-3f)
    }

    @Test
    fun onlyTheNamedPointIsCaughtAndTheStartHolds() {
        val line = through(0f to 0f, 0.5f to 0.4f, 1f to 0f)
        val guides = listOf(SnapGuide(Vec2(1.02f, -1f), Vec2(1.02f, 1f)), SnapGuide(Vec2(0f, 0.42f), Vec2(2f, 0.42f)))
        val snap = guideSnap(line, guides, 8f * TEST_PIXEL, only = line.last())
        assertEquals(setOf(0), snap.caught, "the far end's guide, and not the one by the stroke's widest point")
        assertEquals(1.02f, snap.moved(line.last()).x, 1e-4f)
        assertEquals(line.first(), snap.moved(line.first()))
        assertEquals(0.4f, line.map(snap::moved).maxOf { it.y }, 1e-3f)
    }

    @Test
    fun aSnappedStrokeReleasesOnItsGuide() {
        val edge = arc(1f, 0f, 0.5f, PI, from = -PI / 2.0 + PI / 2.0)
        val snap = guideSnap(edge, listOf(SnapGuide(Vec2(1.53f, -2f), Vec2(1.53f, 2f))), 8f * TEST_PIXEL)
        val stroke = assertNotNull(cleanStroke(edge.map(snap::moved), StrokeEnds.CutAtPlane(), TEST_PIXEL))
        assertEquals(1.53f, stroke.path.flatten().xs.max(), 1.5f * TEST_PIXEL)
    }
}
