package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileKeelTest {
    /** A profile 1 long, its back flat at -0.5, its front at 0.3 but where [bump] raises it, as (from, to, height), flat over [top] of its middle. */
    private fun profile(bump: Triple<Float, Float, Float>, top: Float = 0f): OutlineProfile {
        val (from, to, height) = bump
        val middle = (from + to) / 2f
        val front = listOf(0f to 0.3f, from to 0.3f, middle - top / 2f to 0.3f + height, middle + top / 2f to 0.3f + height, to to 0.3f, 1f to 0.3f)
        val points = front + listOf(1f to -0.5f, 0f to -0.5f)
        return assertNotNull(OutlineProfile.of(Polyline2(FloatArray(points.size) { points[it].first }, FloatArray(points.size) { points[it].second })))
    }

    private val round = SectionProfile.CIRCLE.spline(64)

    @Test
    fun aShortBumpStandsOutAsANarrowRidge() {
        val keel = assertNotNull(ProfileKeel.of(profile(Triple(0.4f, 0.55f, 0.1f)), span = 0.3f, keel = 0.3f))
        assertTrue(keel.depthAt(0.475f) > 0.06f, "the bump stands out most of its height: ${keel.depthAt(0.475f)}")
        assertEquals(0f, keel.depthAt(0.1f), 1e-3f)
        val shaped = keel.shape(round, keel.depthAt(0.475f), 0.9f).sample(64)
        val top = shaped.maxOf { it.y }
        val ridge = shaped.filter { it.y > top - 0.05f }
        assertTrue(ridge.all { abs(it.x) < 0.3f }, "the ridge stands only near the middle: ${ridge.map { it.x }}")
        val flank = shaped.filter { abs(it.x) in 0.5f..0.7f && it.y > 0f }
        assertTrue(flank.all { it.y < top - 0.15f }, "the face beside the ridge stays back")
    }

    @Test
    fun aLongSwellingIsTheRingsOwnShapeNotARidge() {
        val keel = assertNotNull(ProfileKeel.of(profile(Triple(0.1f, 0.9f, 0.1f), top = 0.6f), span = 0.3f, keel = 0.3f))
        assertTrue(keel.depthAt(0.5f) < 0.02f, "a muzzle as long as the head swells the ring: ${keel.depthAt(0.5f)}")
    }

    @Test
    fun aRoundFaceStandsOutNowhere() {
        val points = List(65) { i -> (PI * i / 64).let { a -> (0.5f - 0.5f * cos(a).toFloat()) to 0.45f * sin(a).toFloat() } } + listOf(1f to -0.3f, 0f to -0.3f)
        val circle = assertNotNull(OutlineProfile.of(Polyline2(FloatArray(points.size) { points[it].first }, FloatArray(points.size) { points[it].second })))
        val keel = assertNotNull(ProfileKeel.of(circle, span = 0.3f, keel = 0.3f))
        for (a in listOf(0.2f, 0.35f, 0.5f, 0.65f, 0.8f)) assertEquals(0f, keel.depthAt(a), 2e-3f, "at $a")
    }

    @Test
    fun aRingWithNoRidgeKeepsItsSection() {
        val keel = assertNotNull(ProfileKeel.of(profile(Triple(0.4f, 0.55f, 0.1f)), span = 0.3f, keel = 0.3f))
        assertEquals(round, keel.shape(round, 0f, 0.8f))
    }

    @Test
    fun aRidgeKeepsTheRingsDrawnSpan() {
        val sizing = assertNotNull(
            ViewSizing.of(PartAxes.of(Vec3.Y), profile(Triple(0.4f, 0.55f, 0.1f)), null, openStart = false, openEnd = false, scale = 1f) {
                ProfileKeel.of(it, 0.3f, 0.3f)
            },
        )
        val ring = sizing.slice(0.475f, round, 1f)
        val forward = (0 until 64).map { (ring.pointAt(it / 64f) dot Vec3.X) }
        assertEquals(0.4f, forward.max(), 0.01f)
        assertEquals(-0.5f, forward.min(), 0.01f)
    }
}

class RingFocusTest {
    @Test
    fun theFocusHoldsItsShareOfTheRings() {
        val focus = RingFocus(0f, 0.5f, 0.8f)
        val us = List(101) { focus.at(it / 100f) }
        assertEquals(0f, us.first(), 1e-6f)
        assertEquals(1f, us.last(), 1e-6f)
        assertEquals(81, us.count { it <= 0.5f + 1e-6f })
        assertTrue(us.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun aFocusInTheMiddleSpacesEitherSideEvenly() {
        val focus = RingFocus(0.25f, 0.75f, 0.5f)
        for (t in listOf(0f, 0.1f, 0.5f, 0.9f, 1f)) assertEquals(t, focus.at(t), 1e-5f)
    }
}
