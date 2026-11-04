package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrokeHalfTest {
    private val half = StrokeOptions(half = CentreLine.ALONG_X)

    private val halfFront = StrokeOptions(half = CentreLine.ALONG_Y)

    private fun assertExactlySymmetric(path: BezierPath, centre: CentreLine) {
        val segments = path.segments
        for (i in segments.indices) {
            val other = segments[segments.lastIndex - i]
            assertEquals(centre.mirror(segments[i].p0), other.p3)
            assertEquals(centre.mirror(segments[i].p1), other.p2)
            assertEquals(centre.mirror(segments[i].p2), other.p1)
            assertEquals(centre.mirror(segments[i].p3), other.p0)
        }
    }

    @Test
    fun oneEdgeBecomesAnExactlySymmetricOutlineOpenOnThePlane() {
        val edge = arc(0.5f, 0f, 0.5f, PI / 2.0, from = PI / 2.0 - PI / 2.0)
        val stroke = assertNotNull(cleanStroke(edge, StrokeEnds.CutAtPlane(), TEST_PIXEL, half))
        assertExactlySymmetric(stroke.path, CentreLine.ALONG_X)
        assertEquals(0f, stroke.path.segments.first().p0.x, "both ends on the plane")
        assertEquals(0f, stroke.path.segments.last().p3.x)
        assertTrue(!stroke.closed)
    }

    @Test
    fun aClosedViewClosesOnTheCentreLineWithoutACreaseWhereTheStrokeMeetsItSquare() {
        val edge = arc(0f, 0f, 0.5f, PI, from = -PI / 2.0 + PI)
        val preview = strokePreview(edge, StrokeEnds.Closed, TEST_PIXEL, halfFront)
        assertTrue(preview.closes, "both ends are on the line")
        val stroke = assertNotNull(cleanStroke(edge, StrokeEnds.Closed, TEST_PIXEL, halfFront))
        assertTrue(stroke.closed)
        assertEquals(stroke.path.segments.first().p0, stroke.path.segments.last().p3, "closed to the bit")
        assertExactlySymmetric(stroke.path, CentreLine.ALONG_Y)
        val join = stroke.path.segments.size / 2
        val incoming = (stroke.path.segments[join - 1].p3 - stroke.path.segments[join - 1].p2).unit()
        val outgoing = (stroke.path.segments[join].p1 - stroke.path.segments[join].p0).unit()
        assertTrue(acos((incoming dotWith outgoing).coerceIn(-1f, 1f)) < 0.01f, "smooth across the line")
    }

    @Test
    fun anEndShortOfTheLineCarriesSquareToIt() {
        val stroke = assertNotNull(cleanStroke(through(0f to 0.5f, 1f to 0.5f), StrokeEnds.CutAtPlane(), TEST_PIXEL, half))
        val flat = stroke.path.flatten()
        assertEquals(1f, flat.xs.max(), 2f * TEST_PIXEL)
        assertEquals(0.5f, flat.ys.max(), 2f * TEST_PIXEL)
        assertEquals(-0.5f, flat.ys.min(), 2f * TEST_PIXEL)
    }

    @Test
    fun strokingTowardThePlaneJoinsAtTheFarEndAllTheSame() {
        val out = through(0f to 0.5f, 1f to 0.1f)
        val back = out.reversed()
        val a = assertNotNull(cleanStroke(out, StrokeEnds.CutAtPlane(), TEST_PIXEL, half)).path.flatten()
        val b = assertNotNull(cleanStroke(back, StrokeEnds.CutAtPlane(), TEST_PIXEL, half)).path.flatten()
        assertEquals(a.xs.max(), b.xs.max(), TEST_PIXEL)
        assertEquals(0f, b.xs.min())
    }

    @Test
    fun theHalfPreviewIsWhatReleaseMakesForAStrokeThroughEverySetting() {
        val strokes = listOf(
            arc(0.5f, 0f, 0.5f, PI / 2.0),
            through(0f to 0.5f, 1f to 0.5f),
            through(-0.3f to 0.5f, 1f to 0.5f, 1f to -0.2f),
            arc(1f, 0.6f, 0.4f, 1.5 * PI),
            through(0.2f to 0.3f, 0.2f to 0.8f, 1.2f to 0.8f, 1.5f to 0.1f),
        )
        val settings = listOf(half, half.copy(closeEnd = false), half.copy(snapLine = false), half.copy(smoothing = 1f))
        for (samples in strokes) for (ends in listOf(StrokeEnds.Closed, StrokeEnds.CutAtPlane())) for (options in settings) {
            val preview = strokePreview(samples, ends, TEST_PIXEL, options)
            val stroke = cleanStroke(samples, ends, TEST_PIXEL, options)
            if (stroke == null) {
                assertTrue(preview.shape.isEmpty())
                continue
            }
            assertExactlySymmetric(stroke.path, CentreLine.ALONG_X)
            val released = stroke.path.segments.flatMap { it.sample(24) }
            assertTrue(preview.shape.maxOf { distanceToLine(it, released) } <= TEST_MATCH_PIXELS * TEST_PIXEL, "shown off the release, $ends $options")
            assertTrue(released.maxOf { distanceToLine(it, preview.shape) } <= TEST_MATCH_PIXELS * TEST_PIXEL, "released off what showed, $ends $options")
        }
    }

    @Test
    fun theMirrorImageShowsAsWhatReleaseAddsAndWhatCrossesTheLineIsDiscarded() {
        val past = through(0f to 0.5f, 0.8f to 0.5f, 0.8f to -0.2f)
        val preview = strokePreview(past, StrokeEnds.CutAtPlane(), TEST_PIXEL, half)
        assertTrue(preview.drawn.flatten().all { it.y >= -TEST_PIXEL }, "only the hand's side is ink")
        assertTrue(preview.connectors.flatten().any { it.y < -0.4f }, "the image is a connector")
        assertTrue(preview.dropped.flatten().minOf { it.y } <= -0.19f, "past the line is dropped")
    }

    @Test
    fun whatLiesBehindThePlaneIsDiscardedOnBothSides() {
        val past = through(-0.4f to 0.5f, 1f to 0.3f)
        val preview = strokePreview(past, StrokeEnds.CutAtPlane(), TEST_PIXEL, half)
        assertTrue(preview.dropped.flatten().minOf { it.x } <= -0.39f)
        assertEquals(0f, preview.shape.first().x)
        assertEquals(0f, preview.shape.last().x)
    }

    @Test
    fun closeEndOffJoinsOnlyTheFarEnd() {
        val edge = through(0.3f to 0.5f, 1f to 0f)
        val options = half.copy(closeEnd = false)
        val preview = strokePreview(edge, StrokeEnds.CutAtPlane(), TEST_PIXEL, options)
        val stroke = assertNotNull(cleanStroke(edge, StrokeEnds.CutAtPlane(), TEST_PIXEL, options))
        assertEquals(0.3f, stroke.path.segments.first().p0.x, TEST_PIXEL, "the near end stays off the plane")
        assertTrue(!preview.closes)
    }

    @Test
    fun anOversketchRedrawsTheSpanAndItsMirrorImageAsOneChange() {
        val outline = BezierPath(
            listOf(
                straightCubic(Vec2(0f, 0.5f), Vec2(0.5f, 0.5f)),
                straightCubic(Vec2(0.5f, 0.5f), Vec2(1f, 0.5f)),
                straightCubic(Vec2(1f, 0.5f), Vec2(1f, -0.5f)),
                straightCubic(Vec2(1f, -0.5f), Vec2(0.5f, -0.5f)),
                straightCubic(Vec2(0.5f, -0.5f), Vec2(0f, -0.5f)),
            ),
        )
        val bump = List(60) { val t = it / 59f; Vec2(0.1f + 0.3f * t, 0.5f + 0.1f * sin(PI.toFloat() * t)) }
        val options = OversketchOptions(stroke = half)
        val done = assertNotNull(oversketch(outline, bump, StrokeEnds.CutAtPlane(), TEST_PIXEL, options))
        val flat = done.path.flatten()
        assertTrue(flat.ys.max() > 0.57f && flat.ys.min() < -0.57f, "${flat.ys.max()} and ${flat.ys.min()}")
        assertEquals(2, done.replaced)
        val preview = assertNotNull(oversketchPreview(outline, bump, StrokeEnds.CutAtPlane(), TEST_PIXEL, options))
        assertNotNull(preview.mirrored, "the image shows")
        assertTrue(preview.line.maxOf { it.y } > 0.57f && preview.line.minOf { it.y } < -0.57f, "the whole curve carries both")
    }

    @Test
    fun anOversketchWhoseImageIsTooFarRedrawsOnlyTheHandsSpanAndShowsTheImageDiscarded() {
        val lopsided = BezierPath(listOf(straightCubic(Vec2(0f, 0.5f), Vec2(1f, 0.5f)), straightCubic(Vec2(1f, 0.5f), Vec2(1f, 0.1f)), straightCubic(Vec2(1f, 0.1f), Vec2(0f, 0.1f))))
        val bump = List(60) { val t = it / 59f; Vec2(0.1f + 0.3f * t, 0.5f + 0.1f * sin(PI.toFloat() * t)) }
        val options = OversketchOptions(stroke = half)
        val done = assertNotNull(oversketch(lopsided, bump, StrokeEnds.CutAtPlane(), TEST_PIXEL, options))
        assertTrue(done.path.flatten().ys.min() >= 0.09f, "nothing lands on the far side")
        val preview = assertNotNull(oversketchPreview(lopsided, bump, StrokeEnds.CutAtPlane(), TEST_PIXEL, options))
        assertNull(preview.mirrored)
        assertTrue(preview.dropped.flatten().any { it.y < -0.4f })
    }
}
