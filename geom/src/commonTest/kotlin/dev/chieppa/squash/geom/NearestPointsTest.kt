package dev.chieppa.squash.geom

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

class NearestPointsTest {
    /** The nearest [count] of [points] to [place] by a scan in order: nearest first, the earlier of two equally near. */
    private fun scanned(points: Array<Vec3>, place: Vec3, count: Int): Pair<IntArray, FloatArray> {
        val order = points.indices.sortedWith(compareBy<Int>({ (points[it] - place).length }, { it })).take(count)
        val nearest = IntArray(count) { order.getOrElse(it) { -1 } }
        return nearest to FloatArray(count) { i -> if (nearest[i] < 0) Float.MAX_VALUE else (points[nearest[i]] - place).length }
    }

    @Test
    fun itFindsWhatAScanFindsTiesAndAll() {
        val random = Random(7)
        val ring = List(12) { Vec3(0.5f, 0.2f, -0.1f) }
        val points = (ring + List(600) { Vec3(random.nextFloat(), random.nextFloat() * 0.4f, random.nextFloat() * 2f - 1f) } + ring).toTypedArray()
        val tree = NearestPoints(points)
        val places = List(300) { Vec3(random.nextFloat() * 6f - 3f, random.nextFloat() * 6f - 3f, random.nextFloat() * 6f - 3f) } + ring + points.take(40)
        for (place in places) {
            val nearest = IntArray(4)
            val distances = FloatArray(4)
            tree.nearest(place, nearest, distances)
            val (expected, expectedDistances) = scanned(points, place, 4)
            assertContentEquals(expected, nearest, "at $place")
            assertContentEquals(expectedDistances, distances, "at $place")
        }
    }

    @Test
    fun fewerPointsThanAskedLeaveTheRestEmpty() {
        val nearest = IntArray(4)
        val distances = FloatArray(4)
        NearestPoints(arrayOf(Vec3.X, Vec3.Y)).nearest(Vec3.ZERO, nearest, distances)
        assertContentEquals(intArrayOf(0, 1, -1, -1), nearest)
    }
}
