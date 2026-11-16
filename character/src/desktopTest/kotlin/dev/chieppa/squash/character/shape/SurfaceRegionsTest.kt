package dev.chieppa.squash.character.shape

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SurfaceCurve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SurfaceRegionsTest {
    private val body = PartId("body")
    private val limb = PartId("limb")
    private val other = PartId("limb-r")
    private val chart = BodyChart(
        listOf(ChartPart(body, PATCH, null, 0f, 0f, 1f), ChartPart(limb, PATCH, body, 0.5f, 0.5f, 1f)),
        leaders = mapOf(other to limb),
    )

    private fun ring(part: PartId, u: Float) = ringRound(Anchor(part, PATCH, u, 0f))

    @Test
    fun aRingRoundALimbCutsItsEndOffAsTheSmallerRegion() {
        val regions = SurfaceRegions(chart, listOf(ring(limb, 0.5f)))
        assertEquals(2, regions.count)
        val end = assertNotNull(regions.regionAt(Anchor(limb, PATCH, 0.9f, 0.3f)))
        val rest = assertNotNull(regions.regionAt(Anchor(body, PATCH, 0.2f, 0.3f)))
        assertNotEquals(end, rest)
        assertEquals(rest, regions.regionAt(Anchor(limb, PATCH, 0.1f, 0.3f)), "the limb's root joins the body it sits on")
        assertEquals(end, regions.smallest(regions.sides.single()))
        assertEquals(setOf(rest), regions.across(end))
        val box = assertNotNull(regions.boxes(setOf(end))[limb])
        assertTrue(box.to == 1f && box.allRound && box.from in 0.48f..0.53f, "the end's box runs from the ring, not $box")
        assertEquals(null, regions.boxes(setOf(end))[body])
    }

    @Test
    fun theOtherSideOfAPairReadsAsItsLeader() {
        val regions = SurfaceRegions(chart, listOf(ring(other, 0.5f)))
        assertEquals(2, regions.count)
        assertEquals(regions.regionAt(Anchor(limb, PATCH, 0.9f, 0f)), regions.regionAt(Anchor(other, PATCH, 0.9f, 0f)))
    }

    @Test
    fun aBoxPartOfTheWayRoundStartsBelowZeroWhereItCrossesIt() {
        val open = SurfaceCurve(listOf(Anchor(body, PATCH, 0f, 0.2f), Anchor(body, PATCH, 1f, 0.2f)))
        val back = SurfaceCurve(listOf(Anchor(body, PATCH, 0f, 0.8f), Anchor(body, PATCH, 1f, 0.8f)))
        val regions = SurfaceRegions(BodyChart(listOf(ChartPart(body, PATCH, null, 0f, 0f, 1f))), listOf(open, back))
        val top = assertNotNull(regions.regionAt(Anchor(body, PATCH, 0.5f, 0f)))
        val box = assertNotNull(regions.boxes(setOf(top))[body])
        assertFalse(box.allRound)
        assertTrue(box.fromV < 0f && box.toV > 0f, "the top's box crosses v 0: $box")
    }

    @Test
    fun aStrokeAcrossALimbClosesRoundItsFarSideAndOneThatMeetsClosesAsDrawn() {
        val across = List(5) { Anchor(limb, PATCH, 0.6f, 0.3f + 0.1f * it) }
        val ringed = strokeCurve(across, meets = false)
        assertTrue(ringed.closed)
        assertTrue(ringed.points.containsAll(across))
        assertTrue(ringed.points.all { it.u == 0.6f }, "it closes the far way round at the stroke's height")
        val round = ringed.points.map { it.v }.sorted()
        val gaps = round.zipWithNext { a, b -> b - a } + (round.first() + 1f - round.last())
        assertTrue(gaps.all { it <= 1f / POINTS_ROUND + 1e-4f }, "with no gap round the limb wider than a step: ${gaps.max()}")
        val short = strokeCurve(listOf(Anchor(limb, PATCH, 0.6f, 0.5f), Anchor(limb, PATCH, 0.7f, 0.52f)), meets = false)
        assertFalse(short.closed, "a stroke along a limb stays open")
        assertTrue(strokeCurve(across, meets = true).closed)
        assertTrue(strokeCurve(across, meets = true).points.containsAll(across))
    }

    @Test
    fun twoPlacesFarApartRoundALimbAreJoinedAlongItsSurfaceNotAcrossIt() {
        val chord = strokeCurve(listOf(Anchor(limb, PATCH, 0.4f, 0.1f), Anchor(limb, PATCH, 0.6f, 0.24f)), meets = false)
        assertFalse(chord.closed)
        assertEquals(Anchor(limb, PATCH, 0.4f, 0.1f), chord.points.first())
        assertEquals(Anchor(limb, PATCH, 0.6f, 0.24f), chord.points.last())
        val steps = chord.points.zipWithNext { a, b -> b.v - a.v }
        assertTrue(steps.all { it > 0f && it <= 1f / POINTS_ROUND + 1e-4f }, "the points step round the limb, a little at a time: $steps")
        assertTrue(chord.points.zipWithNext().all { (a, b) -> b.u >= a.u }, "and along it")
    }

    private companion object {
        const val PATCH = "body"
    }
}
