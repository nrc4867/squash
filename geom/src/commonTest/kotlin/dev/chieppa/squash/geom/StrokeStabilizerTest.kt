package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StrokeStabilizerTest {
    @Test
    fun theLineTrailsOnAStringAndHoldsWhileItIsSlack() {
        assertEquals(Vec2(0f, 0f), trailed(Vec2(0f, 0f), Vec2(0.01f, 0f), 0.02f))
        assertEquals(Vec2(0.08f, 0f), trailed(Vec2(0f, 0f), Vec2(0.1f, 0f), 0.02f))
        assertEquals(Vec2(0.1f, 0f), trailed(Vec2(0f, 0f), Vec2(0.1f, 0f), 0f), "no string, no trail")
        assertEquals(4.8f * TEST_PIXEL, stabilizerString(DEFAULT_STROKE_STABILIZER, TEST_PIXEL), 1e-6f)
    }

    private fun stabilized(raw: List<Vec2>, string: Float): List<Vec2> =
        raw.drop(1).runningFold(raw.first()) { line, pointer -> trailed(line, pointer, string) }

    @Test
    fun aStabilizedStrokeShakesLess() {
        val raw = List(600) { Vec2(it * TEST_PIXEL, 0.5f + (if (it % 2 == 0) 1f else -1f) * 2f * TEST_PIXEL) }
        fun shake(line: List<Vec2>) = (1 until line.size).sumOf { abs(line[it].y - line[it - 1].y).toDouble() }
        assertTrue(shake(stabilized(raw, 12f * TEST_PIXEL)) < shake(raw) / 3.0)
    }

    @Test
    fun aHandsCornerSurvivesTheDefaultStabilizer() {
        val raw = through(0.2f to 0.5f, 1f to 0.5f, 1f to -0.5f, 0.2f to -0.5f)
        val line = stabilized(raw, stabilizerString(DEFAULT_STROKE_STABILIZER, TEST_PIXEL)) + raw.last()
        val stroke = assertNotNull(cleanStroke(line, StrokeEnds.CutAtPlane(), TEST_PIXEL))
        val anchors = stroke.path.segments.map { it.p0 }
        assertTrue(anchors.any { (it - Vec2(1f, 0.5f)).magnitude < 6f * TEST_PIXEL }, "a corner near the first turn: $anchors")
        assertTrue(anchors.any { (it - Vec2(1f, -0.5f)).magnitude < 6f * TEST_PIXEL }, "a corner near the second turn: $anchors")
    }

    @Test
    fun aSpineTrailsItsSamplesAndKeepsThePressureThatDrewEachPoint() {
        val samples = List(11) { SpineSample(Vec2(it * 0.1f, 0f), it / 10f) }
        val line = trailedSpine(samples, 0.25f)
        assertEquals(samples.first(), line.first())
        assertEquals(0.75f, line.last().point.x, 1e-4f, "the string's length behind the pointer")
        assertEquals(1f, line.last().pressure)
        assertEquals(samples.drop(3).map { it.pressure }, line.drop(1).map { it.pressure })
    }

    @Test
    fun noStringLeavesASpineAsDrawn() {
        val samples = List(11) { SpineSample(Vec2(it * 0.1f, 0f)) }
        assertEquals(samples, trailedSpine(samples, 0f))
    }

    @Test
    fun aSpinesFarEndIsTheOneAwayFromThePlane() {
        val out = listOf(Vec2(0f, 0f), Vec2(1f, 0f))
        assertEquals(out.last(), spineFarEnd(out, StrokeEnds.CutAtPlane()))
        assertEquals(out.last(), spineFarEnd(out.reversed(), StrokeEnds.CutAtPlane()))
        assertEquals(out.first(), spineFarEnd(out.reversed(), StrokeEnds.Closed))
    }
}
