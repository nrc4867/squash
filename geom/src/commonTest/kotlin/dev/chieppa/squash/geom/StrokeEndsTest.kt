package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A pixel, in plane units, at 200 pixels to the unit. */
private const val PIXEL = 0.005f
private const val RADIUS = 0.5f

class StrokeEndsTest {
    private fun arc(center: Vec2, radius: Float = RADIUS, sweep: Double = 2.0 * PI, from: Double = 0.0, samples: Int = 400) = List(samples) {
        val angle = from + sweep * it / (samples - 1)
        Vec2(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
    }

    private fun flat(stroke: CleanStroke) = stroke.path.segments.flatMap { it.sample(24) }

    private fun distanceToLine(p: Vec2, line: List<Vec2>) = (1 until line.size).minOf { distanceToSegment(p, line[it - 1], line[it]) }

    private fun neckWidth(stroke: CleanStroke) = abs(stroke.path.segments.first().p0.y - stroke.path.segments.last().p3.y)

    /** How far into a blob's near side [stroke] leaves the circle at [center], in pixels. */
    private fun chopDepth(stroke: CleanStroke, center: Vec2): Float {
        val onCircle = flat(stroke).filter { abs((it - center).magnitude - RADIUS) < 2f * PIXEL }
        return (onCircle.minOf { it.x } - (center.x - RADIUS)) / PIXEL
    }

    @Test
    fun aBlobJustShortOfThePlaneGivesUpOnlyWhatItTakesToReachIt() {
        val center = Vec2(RADIUS + 3f * PIXEL, 0f)
        val stroke = assertNotNull(cleanStroke(arc(center), StrokeEnds.CutAtPlane(), PIXEL))
        assertTrue(chopDepth(stroke, center) < 6f, "lost ${chopDepth(stroke, center)} px of its near side")
    }

    @Test
    fun aBlobEitherSideOfThePlaneOpensAboutTheSame() {
        val short = assertNotNull(cleanStroke(arc(Vec2(RADIUS + 2f * PIXEL, 0f)), StrokeEnds.CutAtPlane(), PIXEL))
        val past = assertNotNull(cleanStroke(arc(Vec2(RADIUS - 2f * PIXEL, 0f)), StrokeEnds.CutAtPlane(), PIXEL))
        assertEquals(neckWidth(past) / PIXEL, neckWidth(short) / PIXEL, 16f, "the neck jumps across the plane")
    }

    @Test
    fun theSameBlobOpensTheSameWhereverItsStrokeStarted() {
        val center = Vec2(1f, 0f)
        val necks = listOf(0.0, PI / 2.0, PI, 3.0 * PI / 2.0).map { start ->
            neckWidth(assertNotNull(cleanStroke(arc(center, from = start), StrokeEnds.CutAtPlane(), PIXEL)))
        }
        for (neck in necks) assertEquals(necks.first() / PIXEL, neck / PIXEL, 4f)
    }

    @Test
    fun aGapOnTheFarSideSendsNoLineThroughTheBody() {
        val center = Vec2(1.2f, 0f)
        val gap = 2.0 * (20f * PIXEL / RADIUS)
        val blob = arc(center, sweep = 2.0 * PI - gap, from = gap / 2.0)
        val stroke = assertNotNull(cleanStroke(blob, StrokeEnds.CutAtPlane(), PIXEL))
        val inside = flat(stroke).minOf { (it - center).magnitude }
        assertTrue(inside > RADIUS - 6f * PIXEL, "a line runs ${(RADIUS - inside) / PIXEL} px inside the body")
    }

    @Test
    fun aHorseshoeShortOfThePlaneKeepsItsInnerEdge() {
        val outer = arc(Vec2(1f, 0f), RADIUS, sweep = PI, from = -PI / 2.0, samples = 200)
        val inner = arc(Vec2(1f, 0f), RADIUS / 2f, sweep = -PI, from = PI / 2.0, samples = 200)
        val horseshoe = outer + Vec2(0.6f, 0.5f) + Vec2(0.6f, 0.25f) + inner + Vec2(0.6f, -0.25f) + Vec2(0.6f, -0.5f) + outer.first()
        val stroke = assertNotNull(cleanStroke(resample(horseshoe, PIXEL), StrokeEnds.CutAtPlane(), PIXEL))
        val innerEdge = Vec2(1f + RADIUS / 2f, 0f)
        assertTrue(distanceToLine(innerEdge, flat(stroke)) < 4f * PIXEL, "the inner edge is gone")
    }

    @Test
    fun aBlobLyingOnThePlaneOpensOnItRatherThanFromWhereTheStrokeStarted() {
        val rectangle = listOf(Vec2(1f, 0f), Vec2(1f, 0.5f), Vec2(0f, 0.5f), Vec2(0f, -0.5f), Vec2(1f, -0.5f), Vec2(1f, 0f))
        val stroke = assertNotNull(cleanStroke(resample(rectangle, PIXEL), StrokeEnds.CutAtPlane(), PIXEL))
        assertEquals(1f / PIXEL, neckWidth(stroke) / PIXEL, 8f, "the opening is the side on the plane")
    }

    @Test
    fun closingAByChordLeavesTheStrokeOnItsLine() {
        val open = arc(Vec2(1f, 1f), sweep = 1.2 * PI)
        val line = flat(assertNotNull(cleanStroke(open, StrokeEnds.Closed, PIXEL)))
        val worst = open.maxOf { distanceToLine(it, line) }
        assertTrue(worst < 3f * PIXEL, "the stroke moved ${worst / PIXEL} px")
    }

    @Test
    fun closingAcrossASmallGapLeavesTheStrokesEndsWhereTheyWere() {
        val gap = 15f * PIXEL / RADIUS
        val open = arc(Vec2(1f, 1f), sweep = 2.0 * PI - gap)
        val line = flat(assertNotNull(cleanStroke(open, StrokeEnds.Closed, PIXEL)))
        assertTrue(distanceToLine(open.first(), line) < 2.5f * PIXEL, "start moved ${distanceToLine(open.first(), line) / PIXEL} px")
    }
}
