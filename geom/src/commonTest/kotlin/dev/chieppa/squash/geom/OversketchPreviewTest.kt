package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** A pixel, in plane units, at 200 pixels to the unit. */
private const val PIXEL = 0.005f
private val PLANE = StrokeEnds.CutAtPlane()

/** One display frame at 120 Hz, in microseconds. */
private const val FRAME_MICROS = 8_333L
private const val ROUNDS = 200
private const val STROKE_SAMPLES = 960

class OversketchPreviewTest {
    private val leg = BezierPath(
        listOf(
            straightCubic(Vec2(0f, 0.5f), Vec2(1f, 0.5f)),
            straightCubic(Vec2(1f, 0.5f), Vec2(2f, 0.5f)),
            straightCubic(Vec2(2f, 0.5f), Vec2(2f, -0.5f)),
            straightCubic(Vec2(2f, -0.5f), Vec2(1f, -0.5f)),
            straightCubic(Vec2(1f, -0.5f), Vec2(0f, -0.5f)),
        ),
    )

    private fun line(from: Vec2, to: Vec2, count: Int = 60) = List(count) { from + (to - from) * (it.toFloat() / (count - 1)) }

    private fun bow(fromX: Float, toX: Float, height: Float, count: Int = 80) = List(count) {
        val t = it.toFloat() / (count - 1)
        Vec2(fromX + (toX - fromX) * t, 0.5f + height * sin(PI.toFloat() * t))
    }

    private fun aroundLeg(count: Int = 300) = List(count) {
        val angle = 2.0 * PI * it / (count - 1)
        Vec2(1f + 1.05f * cos(angle).toFloat(), 0.55f * sin(angle).toFloat())
    }

    /** Strokes over the curve, some joined, some ending far off, some past the plane, some redrawing it all. */
    private val strokes = listOf(
        bow(0.5f, 1.5f, 0.1f),
        bow(0.5f, 1.5f, 0.6f),
        line(Vec2(0.5f, 0.5f), Vec2(0.5f, 1.5f)),
        line(Vec2(0.5f, 1.4f), Vec2(1.5f, 1.4f)),
        line(Vec2(-0.5f, 0.45f), Vec2(1f, 0.55f)),
        line(Vec2(1f, 0.55f), Vec2(1.9f, 0.45f)) + line(Vec2(1.9f, 0.45f), Vec2(1.9f, -0.45f)),
        aroundLeg(),
    )

    @Test
    fun whetherItJoinsIsWhetherReleaseDoesAnything() {
        for (stroke in strokes) {
            val release = oversketch(leg, stroke, PLANE, PIXEL)
            val preview = oversketchPreview(leg, stroke, PLANE, PIXEL)
            assertEquals(release != null, preview?.joins == true, "release $release against the preview")
        }
    }

    @Test
    fun aStrokeTooFarToJoinShowsNoConnector() {
        val preview = oversketchPreview(leg, line(Vec2(0.5f, 0.5f), Vec2(1.2f, 1.5f)), PLANE, PIXEL)
        assertNotNull(preview)
        assertFalse(preview.joins)
        assertTrue(preview.connectors.isEmpty())
    }

    @Test
    fun aJoinedStrokeShowsARunToTheCurveFromEachEnd() {
        val preview = assertNotNull(oversketchPreview(leg, bow(0.5f, 1.5f, 0.1f), PLANE, PIXEL))
        assertTrue(preview.joins)
        assertEquals(2, preview.connectors.size)
        for (connector in preview.connectors) assertEquals(0.5f, connector.last().y, 1e-3f, "ends on the curve")
    }

    @Test
    fun whatPassesThePlaneShowsAsDropped() {
        val preview = assertNotNull(oversketchPreview(leg, line(Vec2(-0.5f, 0.45f), Vec2(1f, 0.55f)), PLANE, PIXEL))
        assertTrue(preview.dropped.flatten().minOf { it.x } < -0.4f)
        assertTrue(preview.drawn.all { it.x >= 0f })
    }

    @Test
    fun aStartTooFarFromTheCurveIsNoPreview() {
        assertNull(oversketchPreview(leg, line(Vec2(0.5f, 1.4f), Vec2(1.5f, 1.4f)), PLANE, PIXEL))
    }

    @Test
    fun aLoopOverTheCurveShowsWhatAWholeRedrawMakes() {
        val stroke = aroundLeg()
        val preview = assertNotNull(oversketchPreview(leg, stroke, PLANE, PIXEL))
        val whole = assertNotNull(preview.whole)
        val release = assertNotNull(oversketch(leg, stroke, PLANE, PIXEL))
        assertEquals(leg.segments.size, release.replaced)
        val flat = release.path.segments.flatMap { it.sample(24) }
        val worst = whole.shape.maxOf { p -> (1 until flat.size).minOf { distanceToSegment(p, flat[it - 1], flat[it]) } }
        assertTrue(worst <= 3f * PIXEL, "${worst / PIXEL} px from the release")
    }

    @Test
    fun thePreviewKeepsUpAtStrokeRate() {
        val long = aroundLeg(STROKE_SAMPLES)
        val joined = List(STROKE_SAMPLES) { Vec2(0.2f + 1.7f * it / STROKE_SAMPLES, 0.5f + 0.02f * sin(it * 0.1f)) }
        for ((name, stroke) in listOf("whole" to long, "span" to joined)) {
            repeat(ROUNDS) { oversketchPreview(leg, stroke, PLANE, PIXEL) }
            val micros = List(ROUNDS) { measureTime { oversketchPreview(leg, stroke, PLANE, PIXEL) }.inWholeMicroseconds }.sorted()
            val median = micros[micros.size / 2]
            println("oversketchPreview $name: median $median us, p99 ${micros[micros.size * 99 / 100]} us")
            assertTrue(median < FRAME_MICROS, "$name takes more than a frame: $median us")
        }
    }
}
