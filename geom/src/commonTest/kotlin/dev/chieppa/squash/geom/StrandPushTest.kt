package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RINGS = 12
private const val AROUND = 8
private const val RADIUS = 0.02f
private const val LENGTH = 0.6f
private const val CLEARANCE = 0.01f
private const val REACH = 0.2f
private const val CLOSE = 1e-5f

/**
 * A strand hanging straight down from y = 0, and a flat body under the floor y = [floor] facing up through the strand's lower half: the
 * strand is pushed up out of it whole, ring by ring, its root held.
 */
class StrandPushTest {
    private fun strand(): PointGrid = PointGrid(
        FloatArray(RINGS * AROUND * 3).also { out ->
            for (ring in 0 until RINGS) for (k in 0 until AROUND) {
                val a = 2 * PI * k / AROUND
                val v = ring * AROUND + k
                out[v * 3] = (RADIUS * cos(a)).toFloat()
                out[v * 3 + 1] = -LENGTH * ring / (RINGS - 1)
                out[v * 3 + 2] = (RADIUS * sin(a)).toFloat()
            }
        },
        RINGS, AROUND, closedV = true,
    )

    /** A grid of points on the plane y = [floor] facing +x, the side the strand is pushed to, spread round the strand. */
    private fun wall(at: Float): Pair<Array<Vec3>, Array<Vec3>> {
        val points = ArrayList<Vec3>()
        for (i in 0..40) for (j in -10..10) points += Vec3(at, -i * LENGTH / 40f, j * 0.01f)
        return points.toTypedArray() to Array(points.size) { Vec3.X }
    }

    @Test
    fun aStrandClearOfTheBodyStays() {
        val (points, normals) = wall(-0.2f)
        assertNull(StrandPush.push(strand(), points, normals, CLEARANCE, REACH))
    }

    @Test
    fun aStrandThroughTheBodyIsPushedOutWholeWithItsRootHeld() {
        val grid = strand()
        val (points, normals) = wall(0.01f)
        val shift = assertNotNull(StrandPush.push(grid, points, normals, CLEARANCE, REACH))
        for (k in 0 until AROUND) assertEquals(Vec3.ZERO, shift[k], "the root ring stays")
        for (ring in 1 until RINGS) {
            val first = shift[ring * AROUND]
            for (k in 0 until AROUND) {
                val v = ring * AROUND + k
                assertEquals(first, shift[v], "ring $ring moves whole")
                val x = grid.point(ring, k).x + shift[v].x
                assertTrue(x >= 0.01f + CLEARANCE - CLOSE, "vertex $v ends clear of the body: $x")
            }
        }
        for (ring in 2 until RINGS) assertTrue(shift[ring * AROUND].x >= shift[(ring - 1) * AROUND].x - CLOSE, "ring $ring is carried at least as far as the ring above it")
    }
}
