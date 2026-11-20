package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A color shape read as a signed distance in a patch's (u, v): positive inside, zero on its edge, measured in the scales given. */
class UvShapeDistanceTest {
    private val square = ColorShape.Border(listOf(SurfaceUv(0.4f, 0.4f), SurfaceUv(0.6f, 0.4f), SurfaceUv(0.6f, 0.6f), SurfaceUv(0.4f, 0.6f)))

    @Test
    fun aBorderIsPositiveInsideAndMeasuredInTheScalesGiven() {
        val distance = UvShapeDistance(square, closedV = true)
        assertEquals(0.1f, distance.at(0.5f, 0.5f, 1f, 1f), 1e-5f)
        assertEquals(-0.2f, distance.at(0.5f, 0.8f, 1f, 1f), 1e-5f)
        assertEquals(-0.4f, distance.at(0.5f, 0.8f, 1f, 2f), 1e-5f)
    }

    @Test
    fun aBorderOnceRoundAClosedPatchHoldsTheSideTowardItsLastRing() {
        val ring = ColorShape.Border(List(8) { SurfaceUv(0.8f, it / 8f) })
        val distance = UvShapeDistance(ring, closedV = true)
        assertTrue(distance.at(0.9f, 0.31f, 1f, 1f) > 0f)
        assertTrue(distance.at(0.7f, 0.97f, 1f, 1f) < 0f)
        assertEquals(0.1f, distance.at(0.9f, 0.5f, 1f, 1f), 1e-5f)
    }

    @Test
    fun aBorderOutOnceRoundAndBackIsOneBandWithNoEdgeWhereItsEndsMeet() {
        val out = List(5) { SurfaceUv(0.3f + 0.02f * (it % 2), it / 4f) }
        val back = List(5) { SurfaceUv(0.4f + 0.02f * (it % 2), 1f - it / 4f) }
        for (curved in listOf(false, true)) {
            val band = ColorShape.Border(out + back, corners = setOf(0, 4, 5, 9), curved = curved)
            val distance = UvShapeDistance(band, closedV = true)
            assertEquals(0.05f, distance.at(0.35f, 0f, 1f, 1f), 0.005f)
            assertEquals(0.05f, distance.at(0.35f, 0.999f, 1f, 1f), 0.005f)
            assertEquals(0.05f, distance.at(0.35f, 0.001f, 1f, 1f), 0.005f)
            assertTrue(distance.at(0.5f, 0f, 1f, 1f) < 0f)
            assertTrue(distance.at(0.2f, 0.5f, 1f, 1f) < 0f)
        }
    }

    @Test
    fun aBorderClosingPastTheFirstRingHoldsWhatLiesTowardIt() {
        val cap = ColorShape.Border(listOf(SurfaceUv(0.2f, 0.3f), SurfaceUv(0.3f, 0.5f), SurfaceUv(0.2f, 0.7f)), BorderClosing.PAST_FIRST_RING)
        val distance = UvShapeDistance(cap, closedV = true)
        assertTrue(distance.at(0.05f, 0.5f, 1f, 1f) > 0f)
        assertTrue(distance.at(0.4f, 0.5f, 1f, 1f) < 0f)
        assertTrue(distance.at(0.05f, 0.1f, 1f, 1f) < 0f)
    }

    @Test
    fun aRectangleWrapsAcrossTheSeam() {
        val distance = UvShapeDistance(ColorShape.Rectangle(0.2f, 0.8f, 0.9f, 1.1f), closedV = true)
        assertTrue(distance.at(0.5f, 0.02f, 1f, 1f) > 0f)
        assertTrue(distance.at(0.5f, 0.97f, 1f, 1f) > 0f)
        assertTrue(distance.at(0.5f, 0.5f, 1f, 1f) < 0f)
    }

    @Test
    fun aLevelCutKeepsWhatIsAtLeastZeroAndSplitsCrossedTriangles() {
        val cut = LevelCut.of(floatArrayOf(1f, -1f, 1f, 1f), intArrayOf(0, 1, 2, 0, 2, 3))
        assertEquals(9, cut.triangles.size)
        val crossings = (0 until cut.vertexCount).count { cut.from[it] != cut.to[it] }
        assertEquals(2, crossings)
        assertTrue((0 until cut.vertexCount).all { cut.t[it] == 0f || cut.t[it] == 0.5f })
    }
}
