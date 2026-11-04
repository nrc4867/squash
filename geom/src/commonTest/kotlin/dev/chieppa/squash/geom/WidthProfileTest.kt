package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PIXEL = 0.002f

class WidthProfileTest {
    @Test
    fun pressureSamplesSetTheHalfWidthWhereTheyWereTaken() {
        val samples = List(101) { WidthSample(it / 100f, 0.1f + 0.2f * (it / 100f)) }
        val profile = assertNotNull(pressureProfile(samples, fromPlane = false))
        for (at in listOf(0.2f, 0.5f, 0.8f)) assertEquals(0.1f + 0.2f * at, profile.at(at), 1e-3f)
    }

    @Test
    fun outOfOrderSamplesAndPlacesPastTheEndsHold() {
        val profile = assertNotNull(pressureProfile(listOf(WidthSample(0.8f, 0.2f), WidthSample(0.2f, 0.2f)), fromPlane = false))
        assertEquals(0.2f, profile.at(-1f), 1e-4f)
        assertEquals(0.2f, profile.at(2f), 1e-4f)
    }

    @Test
    fun aPenLandingLightDoesNotPinchTheSocket() {
        val samples = List(101) { i -> WidthSample(i / 100f, (i / 100f / 0.1f).coerceAtMost(1f) * 0.2f) }
        val onPlane = assertNotNull(pressureProfile(samples, fromPlane = true))
        val free = assertNotNull(pressureProfile(samples, fromPlane = false))
        assertTrue(onPlane.at(0f) > 0.15f, "held at ${onPlane.at(0f)}")
        assertTrue(free.at(0f) < 0.01f, "a free end is left at ${free.at(0f)}")
    }

    @Test
    fun jitterInPressureIsSmoothedAway() {
        val samples = List(65) { WidthSample(it / 64f, if (it % 2 == 0) 0.25f else 0.15f) }
        val profile = assertNotNull(pressureProfile(samples, fromPlane = false))
        val inner = (10..54).map { profile.at(it / 64f) }
        assertTrue(inner.max() - inner.min() < 0.02f, "still ${inner.max() - inner.min()}")
    }

    @Test
    fun aDragHoldsExactlyWhatItAskedAndClosesAtTheFarEnd() {
        val profile = assertNotNull(dragProfile(listOf(WidthSample(0.5f, 0.3f)), fromPlane = true))
        assertEquals(0.3f, profile.at(0.5f), 1e-4f)
        assertEquals(0f, profile.at(1f), 1e-4f)
        assertEquals(0.18f, profile.at(0f), 1e-4f)
    }

    @Test
    fun aDragOffThePlaneClosesAtBothEnds() {
        val profile = assertNotNull(dragProfile(listOf(WidthSample(0.5f, 0.3f)), fromPlane = false))
        assertEquals(0f, profile.at(0f), 1e-4f)
        assertEquals(0f, profile.at(1f), 1e-4f)
    }

    @Test
    fun aDragAlongTheSpinePassesThroughEverySample() {
        val samples = listOf(WidthSample(0.25f, 0.1f), WidthSample(0.5f, 0.3f), WidthSample(0.75f, 0.2f))
        val profile = assertNotNull(dragProfile(samples, fromPlane = true))
        for (sample in samples) assertEquals(sample.halfWidth, profile.at(sample.at), 2e-2f)
    }

    @Test
    fun noSamplesMakeNoProfile() {
        assertNull(pressureProfile(emptyList(), fromPlane = true))
        assertNull(dragProfile(emptyList(), fromPlane = true))
    }

    @Test
    fun pressuresReachTheSpineAtThePlacesTheyWereDrawnHoweverItRan() {
        val samples = List(101) { SpineSample(Vec2(1f - it / 100f, 0f), it / 100f) }
        val line = assertNotNull(spineLine(samples, StrokeEnds.CutAtPlane(), PIXEL))
        val widths = line.pressureWidths(samples, full = 1f)
        assertEquals(1f, widths.first().at, 1e-4f)
        assertEquals(0f, widths.first().halfWidth, 1e-4f)
        assertEquals(0f, widths.last().at, 1e-4f)
        assertEquals(1f, widths.last().halfWidth, 1e-4f)
    }

    @Test
    fun aMouseAsksNoWidth() {
        val samples = List(101) { SpineSample(Vec2(it / 100f, 0f)) }
        val line = assertNotNull(spineLine(samples, StrokeEnds.CutAtPlane(), PIXEL))
        assertEquals(emptyList(), line.pressureWidths(samples, full = 1f))
    }
}
