package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrokeMarksTest {
    private fun anchorNear(path: BezierPath, at: Vec2, reach: Float = 5f * TEST_PIXEL) =
        path.segments.indices.firstOrNull { it > 0 && (path.segments[it].p0 - at).magnitude <= reach }

    private fun turnAt(path: BezierPath, section: Int): Float {
        val incoming = (path.segments[section - 1].p3 - path.segments[section - 1].p2).unit()
        val outgoing = (path.segments[section].p1 - path.segments[section].p0).unit()
        return acos((incoming dotWith outgoing).coerceIn(-1f, 1f))
    }

    @Test
    fun aCornerMarkPutsAnAnchorOnASmoothCurve() {
        val curve = arc(1f, 0f, 0.5f, PI, from = -PI / 2.0)
        val at = curve[150]
        assertNull(anchorNear(assertNotNull(cleanStroke(curve, StrokeEnds.CutAtPlane(), TEST_PIXEL)).path, at, TEST_PIXEL))
        val marked = cleanStroke(curve, StrokeEnds.CutAtPlane(), TEST_PIXEL, StrokeOptions(marks = listOf(StrokeMark(at, StrokeMarkKind.CORNER))))
        assertNotNull(anchorNear(assertNotNull(marked).path, at, 2f * TEST_PIXEL), "the fit cuts at the mark")
    }

    @Test
    fun aCornerMarkHoldsThePointThroughTheSmoothing() {
        val shallow = through(0.2f to 0.5f, 1f to 0.5f, 1.8f to 0.3f)
        val at = Vec2(1f, 0.5f)
        val preview = strokePreview(shallow, StrokeEnds.CutAtPlane(), TEST_PIXEL, StrokeOptions(smoothing = 1f, marks = listOf(StrokeMark(at, StrokeMarkKind.CORNER))))
        assertTrue(preview.drawn.flatten().minOf { (it - at).magnitude } < 1.5f * TEST_PIXEL)
    }

    @Test
    fun aSmoothMarkRemovesTheCornerTheHandMade() {
        val sharp = through(0.2f to 0.5f, 1f to 0.5f, 1f to -0.5f)
        val at = Vec2(1f, 0.5f)
        val plain = assertNotNull(cleanStroke(sharp, StrokeEnds.CutAtPlane(), TEST_PIXEL)).path
        val corner = assertNotNull(anchorNear(plain, at))
        assertTrue(turnAt(plain, corner) > 1f, "the hand's corner")
        val smooth = assertNotNull(cleanStroke(sharp, StrokeEnds.CutAtPlane(), TEST_PIXEL, StrokeOptions(snapLine = false, marks = listOf(StrokeMark(at, StrokeMarkKind.SMOOTH))))).path
        for (i in 1 until smooth.segments.size) {
            if ((smooth.segments[i].p0 - at).magnitude < 12f * TEST_PIXEL) assertTrue(turnAt(smooth, i) < 0.05f, "section $i turns ${turnAt(smooth, i)}")
        }
    }

    @Test
    fun aMarkOnACutOffStretchDoesNothing() {
        val curve = arc(1f, 0f, 0.5f, PI, from = -PI / 2.0)
        val plain = assertNotNull(cleanStroke(curve, StrokeEnds.CutAtPlane(), TEST_PIXEL))
        val off = StrokeOptions(marks = listOf(StrokeMark(Vec2(5f, 5f), StrokeMarkKind.CORNER)))
        assertEquals(plain.path, assertNotNull(cleanStroke(curve, StrokeEnds.CutAtPlane(), TEST_PIXEL, off)).path)
    }

    @Test
    fun aMarkHoldsOnAnOversketchAndOnBothSidesOfAHalfOutline() {
        val edge = arc(0.5f, 0f, 0.5f, PI / 2.0)
        val at = edge[150]
        val options = StrokeOptions(half = CentreLine.ALONG_X, marks = listOf(StrokeMark(at, StrokeMarkKind.CORNER)))
        val path = assertNotNull(cleanStroke(edge, StrokeEnds.CutAtPlane(), TEST_PIXEL, options)).path
        assertNotNull(anchorNear(path, at, 2f * TEST_PIXEL))
        assertNotNull(anchorNear(path, CentreLine.ALONG_X.mirror(at), 2f * TEST_PIXEL))

        val outline = BezierPath(listOf(straightCubic(Vec2(0f, 0.5f), Vec2(1f, 0.5f)), straightCubic(Vec2(1f, 0.5f), Vec2(1f, -0.5f))))
        val over = List(100) { val t = it / 99f; Vec2(0.1f + 0.7f * t, 0.5f + 0.06f * sin(PI.toFloat() * t)) }
        val mid = over[50]
        val done = assertNotNull(oversketch(outline, over, StrokeEnds.CutAtPlane(), TEST_PIXEL, OversketchOptions(stroke = StrokeOptions(marks = listOf(StrokeMark(mid, StrokeMarkKind.CORNER))))))
        assertNotNull(anchorNear(done.path, mid, 3f * TEST_PIXEL))
    }
}
