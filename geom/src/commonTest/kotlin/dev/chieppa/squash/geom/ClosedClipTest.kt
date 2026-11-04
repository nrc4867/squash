package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClosedClipTest {
    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float) =
        listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x1, y1), Vec2(x0, y1))

    private fun area(loop: List<Vec2>): Float {
        var sum = 0f
        for (i in loop.indices) {
            val p = loop[i]
            val q = loop[(i + 1) % loop.size]
            sum += p.x * q.y - q.x * p.y
        }
        return sum / 2f
    }

    private val square = rect(0f, 0f, 4f, 4f)

    private fun assertOneLoop(expectedArea: Float, loops: List<List<Vec2>>) {
        assertEquals(1, loops.size, "$loops")
        assertEquals(expectedArea, area(loops.single()), 1e-4f)
    }

    @Test
    fun disjointCurvesShareNothing() {
        assertEquals(emptyList(), clipClosed(square, rect(10f, 10f, 12f, 12f)))
    }

    @Test
    fun aCurveInsideComesBackWhole() {
        val inner = rect(1f, 1f, 2f, 2f)
        val loops = clipClosed(inner, square)
        assertOneLoop(1f, loops)
        assertEquals(inner.toSet(), loops.single().toSet())
    }

    @Test
    fun aCurveAroundTheClipBecomesTheClip() {
        val inner = rect(1f, 1f, 2f, 2f)
        val loops = clipClosed(square, inner)
        assertOneLoop(1f, loops)
        assertEquals(inner.toSet(), loops.single().toSet())
    }

    @Test
    fun coincidentCurvesComeBackWhole() {
        assertOneLoop(16f, clipClosed(square, square))
    }

    @Test
    fun coincidentCurvesAgreeHoweverTheyRunOrStart() {
        val reversed = square.reversed()
        val turned = square.drop(2) + square.take(2)
        assertOneLoop(16f, clipClosed(square, reversed))
        assertOneLoop(16f, clipClosed(square, turned))
    }

    @Test
    fun coincidentCurvesWithDifferentVerticesAgree() {
        val moreVertices = listOf(Vec2(0f, 0f), Vec2(2f, 0f), Vec2(4f, 0f), Vec2(4f, 4f), Vec2(0f, 4f), Vec2(0f, 2f))
        assertOneLoop(16f, clipClosed(square, moreVertices))
    }

    @Test
    fun curvesTouchingAlongAnEdgeShareNothing() {
        assertEquals(emptyList(), clipClosed(square, rect(4f, 0f, 8f, 4f)))
    }

    @Test
    fun curvesTouchingAlongPartOfAnEdgeShareNothing() {
        assertEquals(emptyList(), clipClosed(square, rect(4f, 1f, 8f, 3f)))
    }

    @Test
    fun curvesTouchingAtACornerShareNothing() {
        assertEquals(emptyList(), clipClosed(square, rect(4f, 4f, 8f, 8f)))
    }

    @Test
    fun aCornerTouchingAnEdgeFromOutsideSharesNothing() {
        val diamond = listOf(Vec2(4f, 2f), Vec2(6f, 0f), Vec2(8f, 2f), Vec2(6f, 4f))
        assertEquals(emptyList(), clipClosed(square, diamond))
    }

    @Test
    fun aCurveTouchingFromInsideComesBackWhole() {
        val diamond = listOf(Vec2(4f, 2f), Vec2(3f, 1f), Vec2(2f, 2f), Vec2(3f, 3f))
        assertOneLoop(2f, clipClosed(diamond, square))
        assertOneLoop(2f, clipClosed(square, diamond))
    }

    @Test
    fun aCurveSharingAnEdgeFromInsideComesBackWhole() {
        assertOneLoop(4f, clipClosed(rect(2f, 0f, 4f, 2f), square))
        assertOneLoop(4f, clipClosed(square, rect(2f, 0f, 4f, 2f)))
    }

    @Test
    fun overlappingCurvesKeepTheOverlap() {
        val loops = clipClosed(square, rect(2f, 2f, 6f, 6f))
        assertOneLoop(4f, loops)
        assertEquals(rect(2f, 2f, 4f, 4f).toSet(), loops.single().toSet())
    }

    @Test
    fun aCrossMakesTheMiddle() {
        assertOneLoop(4f, clipClosed(rect(-2f, 1f, 6f, 3f), rect(1f, -2f, 3f, 6f)))
    }

    @Test
    fun aHollowedShapeClippedAcrossItsMouthGivesTwoPieces() {
        val horseshoe = listOf(
            Vec2(0f, 0f), Vec2(6f, 0f), Vec2(6f, 4f), Vec2(4f, 4f), Vec2(4f, 2f), Vec2(2f, 2f), Vec2(2f, 4f), Vec2(0f, 4f),
        )
        val loops = clipClosed(horseshoe, rect(-1f, 3f, 7f, 5f))
        assertEquals(2, loops.size, "$loops")
        loops.forEach { assertEquals(2f, area(it), 1e-4f) }
    }

    @Test
    fun theResultRunsCounterClockwiseWhateverTheInputDoes() {
        val loops = clipClosed(square.reversed(), rect(2f, 2f, 6f, 6f).reversed())
        assertTrue(area(loops.single()) > 0f)
    }

    @Test
    fun aCurveWithNoAreaClipsToNothing() {
        val flat = listOf(Vec2(0f, 1f), Vec2(4f, 1f), Vec2(2f, 1f))
        assertEquals(emptyList(), clipClosed(flat, square))
        assertEquals(emptyList(), clipClosed(square, flat))
        assertEquals(emptyList(), clipClosed(emptyList(), emptyList()))
        assertEquals(emptyList(), clipClosed(listOf(Vec2(1f, 1f)), listOf(Vec2(1f, 1f))))
    }

    @Test
    fun aPupilPastTheLidIsCutByIt() {
        val lid = closedBezierEllipse(0f, 0f, 3f, 1f)
        val pupil = closedBezierEllipse(0f, 1f, 0.8f, 0.8f)
        val loops = clipClosed(pupil, lid)
        assertEquals(1, loops.size)
        val cut = area(loops.single())
        val whole = area(clipClosed(pupil, closedBezierEllipse(0f, 0f, 10f, 10f)).single())
        assertTrue(cut > 0.1f && cut < whole, "cut $cut of $whole")
        assertTrue(loops.single().all { (it.x * it.x) / 9f + it.y * it.y <= 1.01f && it.y <= 1.81f })
    }

    @Test
    fun aClosedCurveHasNoEdgeFromItsEndToItsStartOfZeroLength() {
        val lid = closedBezierEllipse(0f, 0f, 2f, 2f)
        val loops = clipClosed(lid, lid)
        assertEquals(1, loops.size)
        val loop = loops.single()
        assertTrue(abs(area(loop)) > 0f)
        assertEquals(loop.size, loop.toSet().size)
    }

    private fun closedBezierEllipse(cx: Float, cy: Float, rx: Float, ry: Float): BezierPath {
        val k = 0.5523f
        val e = Vec2(cx + rx, cy)
        val n = Vec2(cx, cy + ry)
        val w = Vec2(cx - rx, cy)
        val s = Vec2(cx, cy - ry)
        return BezierPath(
            listOf(
                CubicBezier(e, Vec2(cx + rx, cy + k * ry), Vec2(cx + k * rx, cy + ry), n),
                CubicBezier(n, Vec2(cx - k * rx, cy + ry), Vec2(cx - rx, cy + k * ry), w),
                CubicBezier(w, Vec2(cx - rx, cy - k * ry), Vec2(cx - k * rx, cy - ry), s),
                CubicBezier(s, Vec2(cx + k * rx, cy - ry), Vec2(cx + rx, cy - k * ry), e),
            ),
        )
    }
}
