package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConvexHullTest {
    private val square = listOf(Vec2(0f, 0f), Vec2(1f, 0f), Vec2(1f, 1f), Vec2(0f, 1f))

    @Test
    fun theHullOfASquareWithPointsInsideIsItsCornersCounterClockwise() {
        val hull = convexHull(square + listOf(Vec2(0.5f, 0.5f), Vec2(0.2f, 0.7f), Vec2(0.5f, 0f)))
        assertEquals(listOf(Vec2(0f, 0f), Vec2(1f, 0f), Vec2(1f, 1f), Vec2(0f, 1f)), hull)
    }

    @Test
    fun aPointInsideTheHullIsNoDistanceOutAndOneBesideItIsItsDistanceToTheNearestSide() {
        val hull = convexHull(square)
        assertEquals(0f, outsideHull(hull, Vec2(0.4f, 0.6f)))
        assertEquals(0.5f, outsideHull(hull, Vec2(1.5f, 0.5f)), 1e-6f)
        assertEquals(0.25f, outsideHull(hull, Vec2(0.5f, -0.25f)), 1e-6f)
        assertTrue(outsideHull(hull, Vec2(2f, 2f)) > 1.4f)
    }
}
