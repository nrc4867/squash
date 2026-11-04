package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PIXEL = 0.002f
private val OPEN = StrokeEnds.CutAtPlane()

class SpineShapeTest {
    private fun samples(count: Int = 200, at: (Float) -> Vec2) = List(count) { SpineSample(at(it.toFloat() / (count - 1))) }

    private fun line(ends: StrokeEnds = OPEN, at: (Float) -> Vec2) = assertNotNull(spineLine(samples(at = at), ends, PIXEL, smoothing = 0f))

    private fun even(half: Float) = assertNotNull(pressureProfile(listOf(WidthSample(0f, half), WidthSample(1f, half)), fromPlane = false))

    private fun shape(line: SpineLine, half: Float, ends: StrokeEnds = OPEN) = assertNotNull(spineShape(line, even(half), ends))

    private val straight: (Float) -> Vec2 = { Vec2(it, 0f) }

    private fun distanceToLine(p: Vec2, line: List<Vec2>) = line.zipWithNext().minOf { (a, b) -> distanceToSegment(p, a, b) }

    @Test
    fun theOffsetsLieHalfAWidthEitherSideOfTheSpine() {
        val shape = shape(line { Vec2(it, 0.2f * sin(it)) }, 0.05f)
        val spine = shape.line.points
        val ends = 0.1f
        val inner = shape.side.filter { it.x > ends && it.x < 1f - ends }
        assertTrue(inner.isNotEmpty())
        for (point in inner) assertEquals(0.05f, distanceToLine(point, spine), 0.002f, "at $point")
    }

    @Test
    fun anOpenOutlineStartsAndEndsOnTheSocketPlaneAndMeetsAtTheSpinesEnd() {
        val shape = shape(line { Vec2(it * 0.7f + 0.1f, it * 0.7f) }, 0.05f)
        assertEquals(false, shape.closed)
        assertEquals(0f, shape.side.first().x)
        assertEquals(0f, shape.side.last().x)
        assertEquals(0f, shape.top.first().x)
        assertEquals(0f, shape.top.last().x)
        assertTrue(shape.line.points.last() in shape.side, "both offsets meet at the spine's end")
    }

    @Test
    fun aSpineThatStopsShortOfThePlaneReachesItAlongX() {
        val line = line { Vec2(0.2f + it, 0.1f) }
        assertEquals(Vec2(0f, 0.1f), line.points.first())
        assertEquals(0.1f, line.points.first { it.x > 0f && it.x < 0.2f }.y)
    }

    @Test
    fun aSpineDrawnTowardsThePlaneRunsFromIt() {
        val line = line { Vec2(1f - it, 0f) }
        assertEquals(0f, line.points.first().x, 1e-4f)
        assertEquals(1f, line.points.last().x, 1e-4f)
    }

    @Test
    fun whatPassesThePlaneIsCut() {
        val line = line { Vec2(it * 1.2f - 0.2f, 0f) }
        assertEquals(0f, line.points.first().x)
        assertTrue(line.points.all { it.x >= 0f })
    }

    @Test
    fun aPartThatOpensAtItsEndRunsFromThePlaneAgainstIt() {
        val ends = StrokeEnds.CutAtPlane(above = false)
        val shape = shape(line(ends) { Vec2(-it, 0f) }, 0.05f, ends)
        assertEquals(0f, shape.side.first().x)
        assertEquals(0f, shape.side.last().x)
        assertEquals(0f, shape.top.first().x)
        assertEquals(-1f, shape.top.minOf { it.x }, 0.01f)
    }

    @Test
    fun aClosedEndRoundsOffWithinItsOwnReach() {
        val shape = shape(line(at = straight), 0.1f)
        val tip = shape.line.points.last()
        assertEquals(0f, shape.halfWidths.last())
        assertEquals(tip.x, shape.side.maxOf { it.x }, 1e-4f)
        val halfwayIn = shape.line.points.indexOfFirst { it.x > 1f - 0.05f }
        assertEquals(0.1f * sqrtOfThreeQuarters, shape.halfWidths[halfwayIn], 0.003f)
        assertEquals(0.1f, shape.halfWidths[shape.line.points.indexOfFirst { it.x > 0.85f }], 1e-3f)
    }

    @Test
    fun aPartClosedAtBothEndsIsAClosedLoopWithNothingAtEitherEnd() {
        val closed = StrokeEnds.Closed
        val shape = shape(line(closed, straight), 0.1f, closed)
        assertTrue(shape.closed)
        assertEquals(shape.side.first(), shape.side.last())
        assertEquals(shape.top.first(), shape.top.last())
        assertEquals(0f, shape.halfWidths.first())
        assertEquals(0f, shape.halfWidths.last())
    }

    @Test
    fun theTopOutlineHasTheWidthAlongAEvenlyAboutTheAxis() {
        val shape = shape(line { Vec2(it, 0.3f * it) }, 0.05f)
        assertEquals(0.05f, shape.top.filter { it.x in 0.2f..0.8f }.maxOf { it.y }, 1e-3f)
        assertEquals(-0.05f, shape.top.filter { it.x in 0.2f..0.8f }.minOf { it.y }, 1e-3f)
        val xs = shape.top.map { it.x }.distinct()
        for (x in xs) {
            val at = shape.top.filter { it.x == x }.map { it.y }
            assertEquals(0f, at.max() + at.min(), 1e-5f)
        }
    }

    @Test
    fun sideAndTopCoverTheSameReachEvenWhereAnOffsetPassesTheSpinesEnd() {
        val hook: (Float) -> Vec2 = { Vec2(sin(it * PI.toFloat() * 0.5f), 1f - cos(it * PI.toFloat() * 0.5f)) }
        val line = line(at = hook)
        val width = assertNotNull(dragProfile(listOf(WidthSample(0.6f, 0.15f)), fromPlane = true))
        val shape = assertNotNull(spineShape(line, width, OPEN))
        assertEquals(shape.side.maxOf { it.x }, shape.top.maxOf { it.x }, 1e-5f)
        assertEquals(shape.side.minOf { it.x }, shape.top.minOf { it.x }, 1e-5f)
    }

    @Test
    fun theFittedOutlinesFollowWhatThePreviewShows() {
        val shape = assertNotNull(spineShape(line { Vec2(it, 0.25f * sin(it * 3f)) }, assertNotNull(dragProfile(listOf(WidthSample(0.4f, 0.08f)), true)), OPEN))
        val fit = shape.fitted(PIXEL)
        assertTrue(fit.sideError <= 4f * PIXEL, "side strays ${fit.sideError}")
        assertTrue(fit.topError <= 4f * PIXEL, "top strays ${fit.topError}")
        val side = fit.side.flatten()
        assertEquals(shape.side.first().x, side.xs.first(), 1e-5f)
        assertEquals(shape.side.last().y, side.ys.last(), 1e-5f)
        val flat = side.xs.indices.map { Vec2(side.xs[it], side.ys[it]) }
        val worst = shape.side.maxOf { distanceToLine(it, flat) }
        assertTrue(worst <= 4f * PIXEL, "release strays $worst from the preview")
        assertEquals(shape.top.first().y, fit.top.flatten().ys.first(), 1e-5f)
    }

    @Test
    fun aClosedShapeFitsToAClosedCurve() {
        val closed = StrokeEnds.Closed
        val fit = shape(line(closed, straight), 0.1f, closed).fitted(PIXEL)
        assertEquals(fit.side.segments.first().p0, fit.side.segments.last().p3)
        assertEquals(fit.top.segments.first().p0, fit.top.segments.last().p3)
    }

    @Test
    fun aGentleSpineIsReadWhole() {
        val shape = shape(line { Vec2(it, 0.2f * sin(it * 2f)) }, 0.05f)
        assertEquals(emptyList(), unreadStretches(shape.side, 2f * PIXEL))
    }

    @Test
    fun aMonotonicSpineBentHardDoublesBackAndThatPartIsUnread() {
        // Steep and always moving forward in a; where the width grows or falls faster than the spine advances, an offset falls back across the planes it has passed.
        val steep: (Float) -> Vec2 = { t -> Vec2(t * 0.3f, 1.5f * t + 0.05f * sin(t * 3f)) }
        val line = line(at = steep)
        assertTrue(line.points.zipWithNext().all { (a, b) -> b.x >= a.x - 1e-4f }, "the spine itself never turns back")
        val shape = assertNotNull(spineShape(line, assertNotNull(dragProfile(listOf(WidthSample(0.3f, 0.3f)), fromPlane = true)), OPEN))
        val unread = unreadStretches(shape.side, 2f * PIXEL)
        assertTrue(unread.isNotEmpty(), "an offset doubles back")
        val xs = shape.side.zipWithNext().map { (a, b) -> b.x - a.x }
        assertTrue(xs.any { it > 0f } && xs.any { it < 0f })
        val spans = readSpans(shape.side, 0.01f)
        for (stretch in unread) {
            for (point in stretch.drop(1).dropLast(1)) {
                val span = spans.minBy { abs(it.at - point.x) }
                assertTrue(point.y >= span.low - 0.02f && point.y <= span.high + 0.02f, "$point lies within what the build reads")
            }
        }
    }

    @Test
    fun aHardBendFoldsItsInnerOffsetAwayAndKeepsAnOutlineThatDoesNotCrossItself() {
        val bent: (Float) -> Vec2 = { t -> Vec2(t, if (t < 0.5f) 0f else (t - 0.5f) * 1.2f) }
        val shape = shape(line(at = bent), 0.1f)
        val edges = shape.side.zipWithNext()
        val crossings = edges.indices.count { i -> (i + 2 until edges.size).any { j -> segmentCrossing(edges[i].first, edges[i].second, edges[j].first, edges[j].second) != null } }
        assertTrue(crossings <= 1, "$crossings edges cross another")
    }

    @Test
    fun aSpineThatTurnsBackHasUnreadOffsetsAndTopTakesTheWidest() {
        val turning: (Float) -> Vec2 = { t -> Vec2(sin(t * PI.toFloat()) * 0.5f + t * 0.1f, t) }
        val shape = shape(line(at = turning), 0.03f)
        assertTrue(unreadStretches(shape.side, 2f * PIXEL).isNotEmpty())
        assertEquals(0.03f, shape.top.filter { it.x in 0.2f..0.4f }.maxOf { it.y }, 2e-3f)
    }

    @Test
    fun aTightInnerBendFoldsAwayAtItsCrossing() {
        val hairpin = listOf(Vec2(0f, 0f), Vec2(1f, 0f), Vec2(1f, 0.1f), Vec2(0.5f, -0.2f), Vec2(0.5f, -0.5f))
        val kept = withoutLoops(hairpin, 10f)
        assertEquals(Vec2(0f, 0f), kept.first())
        assertEquals(Vec2(0.5f, -0.5f), kept.last())
        assertTrue(kept.none { it == Vec2(1f, 0f) }, "the loop is gone: $kept")
    }

    @Test
    fun aLoopFurtherThanItsReachStays() {
        val hairpin = listOf(Vec2(0f, 0f), Vec2(1f, 0f), Vec2(1f, 0.1f), Vec2(0.9f, 0.1f), Vec2(0.5f, -0.2f), Vec2(0.5f, -0.5f))
        assertEquals(hairpin, withoutLoops(hairpin, 0.01f))
    }

    @Test
    fun nothingAtAllMakesNoShapeAndAShortStrokeNoLine() {
        val none = assertNotNull(pressureProfile(listOf(WidthSample(0.5f, 0f)), fromPlane = false))
        assertNull(spineShape(line(at = straight), none, OPEN))
        assertNull(spineLine(samples { Vec2(it * PIXEL, 0f) }, OPEN, PIXEL))
        assertNull(spineLine(samples { Vec2(-1f - it, 0f) }, OPEN, PIXEL))
    }

    private val sqrtOfThreeQuarters = 0.8660254f
}
