package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewSizingTest {
    @Test
    fun endsAHairApartOnTheSocketPlaneStillCrossAtThePlane() {
        for (hair in listOf(0f, -3e-8f, 3e-8f, 3e-6f)) {
            val profile = checkNotNull(OutlineProfile.of(Polyline2(floatArrayOf(0f, 0.5f, 1f, 0.5f, hair), floatArrayOf(0.1f, 0.1f, 0f, -0.1f, -0.1f))))
            assertEquals(0.2f, profile.extentAt(minOf(0f, hair)), 1e-3f, "$hair")
        }
    }

    private val axes = PartAxes.of(Vec3(0f, -1f, 0f))
    private val round = SectionProfile.CIRCLE.spline(16)

    /** A strip along x whose edges pass through [tops] and mirror around [mid], closed at its far end by a tip. */
    private fun strip(tops: List<Pair<Float, Float>>, mid: Float = 0f): Polyline2 {
        val upper = tops.map { (x, half) -> Vec2(x, mid + half) }
        val lower = tops.reversed().map { (x, half) -> Vec2(x, mid - half) }
        val points = upper + lower
        return Polyline2(FloatArray(points.size) { points[it].x }, FloatArray(points.size) { points[it].y })
    }

    private val leg = listOf(0f to 0.2f, 0.25f to 0.2f, 0.5f to 0.2f, 0.75f to 0.2f, 1f to 0.1f)

    private fun sizing(outline: Polyline2, open: Boolean = true, scale: Float = 1f) = assertNotNull(
        ViewSizing.of(axes, OutlineProfile.of(outline), null, openStart = open, openEnd = false, scale = scale),
    )

    @Test
    fun anOutlineSetsExtentAndMidpoint() {
        val profile = assertNotNull(OutlineProfile.of(strip(leg, mid = 0.3f)))
        assertEquals(0.4f, profile.extentAt(0.5f), 1e-3f)
        assertEquals(0.3f, profile.midAt(0.5f), 1e-3f)
    }

    @Test
    fun anOpenOutlineIsClosedByItsChord() {
        val dome = Polyline2(floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), floatArrayOf(0f, 0.4f, 0.5f, 0.4f, 0f))
        val profile = assertNotNull(OutlineProfile.of(dome))
        assertEquals(0.5f, profile.extentAt(0.5f), 1e-3f)
        assertEquals(0.25f, profile.midAt(0.5f), 1e-3f)
    }

    @Test
    fun aLineWithNoAreaHasNoProfile() {
        assertNull(OutlineProfile.of(Polyline2(floatArrayOf(0f, 1f), floatArrayOf(0.2f, 0.2f))))
        assertNull(OutlineProfile.of(Polyline2(floatArrayOf(0.3f, 0.3f), floatArrayOf(0f, 1f))))
    }

    @Test
    fun theAxisRunsThroughTheMidline() {
        val views = sizing(strip(leg, mid = 0.3f))
        val at = views.axis.pointAt(0.5f)
        assertEquals(0.5f, at dot axes.along, 1e-3f)
        assertEquals(0.3f, at dot axes.up, 1e-3f)
        assertEquals(1f, views.length, 1e-3f)
    }

    @Test
    fun theOpenEndStaysOnTheSocketPlane() {
        val slanted = Polyline2(floatArrayOf(-0.2f, 1f, 1f, -0.2f), floatArrayOf(0f, 0.6f, 0.4f, -0.2f))
        val views = sizing(slanted)
        val socket = views.slice(0f, round, 1f)
        assertEquals(0f, socket.frame.center dot axes.along, 1e-5f)
        assertEquals(axes.along, socket.frame.tangent)
    }

    @Test
    fun aRingStandsInItsPlaneWithItsEndsOnTheCrossings() {
        val slanted = Polyline2(floatArrayOf(0f, 1f, 1f, 0f), floatArrayOf(0.2f, 1.2f, 0.8f, -0.2f))
        val slice = sizing(slanted, open = false).slice(0.5f, round, 1f)
        assertEquals(axes.along, slice.frame.tangent)
        assertEquals(axes.up, slice.frame.up)
        assertEquals(0.5f, slice.frame.center dot axes.along, 1e-5f)
        assertEquals(0.5f, slice.frame.center dot axes.up, 1e-3f)
        assertEquals(0.4f, slice.height, 1e-3f)
        assertEquals(slice.height, slice.width, 1e-5f)
    }

    @Test
    fun anOffCentreSectionStillEndsOnTheCrossings() {
        val raised = ClosedSpline(round.controlPoints.map { Vec2(it.x, it.y + 0.5f) })
        val slice = sizing(strip(leg, mid = 0.3f)).slice(0.5f, raised, 1f)
        val rises = raised.sample(16).map { (slice.frame.center dot axes.up) + it.y * slice.height / 2f }
        assertEquals(0.5f, rises.max(), 1e-3f)
        assertEquals(0.1f, rises.min(), 1e-3f)
    }

    @Test
    fun aTipKeepsItsOwnCrossing() {
        val diamond = Polyline2(floatArrayOf(0f, 0.5f, 1f, 0.5f), floatArrayOf(0.3f, 0.5f, 0.3f, 0.1f))
        val profile = assertNotNull(OutlineProfile.of(diamond))
        assertEquals(0f, profile.extentAt(0f), 1e-5f)
        assertEquals(0f, profile.extentAt(1f), 1e-5f)
        assertEquals(0.3f, profile.midAt(1f), 1e-5f)
    }

    @Test
    fun aMissingViewKeepsTheSectionsProportions() {
        val lens = SectionProfile.LENS.spline(16)
        val slice = sizing(strip(leg)).slice(0.5f, lens, 1f)
        assertEquals(0.4f, slice.height * lens.spans().y / 2f, 1e-3f)
        assertEquals(slice.height, slice.width, 1e-5f)
    }

    @Test
    fun changingASpanChangesOnlyTheSlicesNearIt() {
        val before = sizing(strip(leg))
        val bumped = listOf(0f to 0.2f, 0.25f to 0.2f, 0.45f to 0.2f, 0.5f to 0.35f, 0.55f to 0.2f, 0.75f to 0.2f, 1f to 0.1f)
        val after = sizing(strip(bumped))
        val us = List(47) { it / 46f }
        val changed = us.filter { u -> before.slice(u, round, 1f) != after.slice(u, round, 1f) }
        assertTrue(changed.isNotEmpty())
        val reach = 1f / 128f
        for (u in us.filter { it < 0.45f - reach || it > 0.55f + reach }) {
            assertEquals(before.slice(u, round, 1f), after.slice(u, round, 1f), "slice at $u")
        }
        assertNotEquals(before.slice(0.5f, round, 1f), after.slice(0.5f, round, 1f))
    }

    @Test
    fun movingTheFarEndMovesEverySlice() {
        val before = sizing(strip(leg))
        val after = sizing(strip(leg.dropLast(1) + (1.2f to 0.1f)))
        assertNotEquals(before.slice(0.25f, round, 1f).frame.center, after.slice(0.25f, round, 1f).frame.center)
    }

    @Test
    fun scaleMultipliesTheDrawing() {
        val views = sizing(strip(leg), scale = 2f)
        assertEquals(2f, views.length, 1e-3f)
        assertEquals(0.8f, assertNotNull(views.heightAt(0.5f)), 1e-3f)
    }

    @Test
    fun aDrawnSectionIsFittedToTheSegmentCountFromTheTop() {
        val square = Polyline2(floatArrayOf(-1f, 1f, 1f, -1f), floatArrayOf(2f, 2f, 0f, 0f))
        val section = assertNotNull(ClosedSpline.fromOutline(square, 16))
        assertEquals(16, section.controlPoints.size)
        assertEquals(2f, section.spans().y, 1e-3f)
        assertTrue(section.pointAt(0f).y > 0.5f)
        assertTrue(section.pointAt(0.25f).x > 0.5f, "v = 0.25 lies on +x, whichever way the outline ran")
        val backward = assertNotNull(ClosedSpline.fromOutline(square.reversed(), 16))
        assertTrue(backward.pointAt(0.25f).x > 0.5f)
    }

    @Test
    fun aCapSectionBlendsInOnlyNearAClosedEnd() {
        val keel = SectionProfile.KEEL.spline(16)
        val front = FrontSection.caps(keel, start = false, end = true)
        assertEquals(round, front.at(0.5f, round))
        assertEquals(keel, front.at(1f, round))
        assertEquals(round, front.at(0f, round))
        assertEquals(keel, FrontSection.whole(keel).at(0.5f, round))
    }
}
