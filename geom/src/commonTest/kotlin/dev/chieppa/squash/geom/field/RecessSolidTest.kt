package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A recess along a straight mouth line into the half-space behind z = 0, its opening closing at both corners. */
class RecessSolidTest {
    private val stations = List(STATIONS) { i ->
        val x = -1f + 2f * i / (STATIONS - 1)
        val open = sqrt(max(0f, 1f - x * x))
        val v = if (i % 2 == 0) 0.98f else 0.02f
        RecessStation(Vec3(x, 0f, 0f), Vec3(0f, -1f, 0f), Vec3(0f, 0f, -1f), HALF * open, DEEP * open, SurfaceUv(i.toFloat() / (STATIONS - 1), v))
    }
    private val recess = RecessSolid(stations, REACH)

    @Test
    fun `the field is a number everywhere in its box, corners included`() {
        val steps = 24
        for (i in 0..steps) for (j in 0..steps) for (k in 0..steps) {
            val p = Vec3(
                recess.low.x + (recess.high.x - recess.low.x) * i / steps,
                recess.low.y + (recess.high.y - recess.low.y) * j / steps,
                recess.low.z + (recess.high.z - recess.low.z) * k / steps,
            )
            assertTrue(recess.distance(p).isFinite(), "the field at $p")
        }
    }

    @Test
    fun `the opening is cut and the face beside it is not`() {
        assertTrue(recess.distance(Vec3(0f, 0f, -0.01f)) < 0f, "behind the middle of the opening")
        assertTrue(recess.distance(Vec3(0f, 0f, 0.05f)) < 0f, "in front of it, where the slot opens through the surface")
        assertTrue(recess.distance(Vec3(0f, 0.4f, -0.01f)) > 0f, "above the upper lip")
        assertTrue(recess.distance(Vec3(0f, 0f, -0.5f)) > 0f, "behind the back wall")
        assertTrue(recess.distance(Vec3(1.2f, 0f, -0.01f)) > 0f, "past a corner")
    }

    @Test
    fun `a point on the wall lies on the recess's surface, its normal pointing into the open`() {
        for (along in listOf(0.25f, 0.5f, 0.75f)) for (depth in listOf(0f, 0.5f, 0.9f)) for (upper in listOf(true, false)) {
            val wall = recess.wallAt(along, depth, upper)
            assertEquals(0f, recess.distance(wall.position), TOLERANCE, "on the wall at $along, $depth, upper $upper")
            assertTrue(recess.distance(wall.position + wall.normal * STEP) < 0f, "a step along the normal is inside the recess")
        }
        val top = recess.wallAt(0.5f, 0f, upper = true).position
        assertEquals(HALF, top.y, TOLERANCE, "the upper wall meets the surface at the opening's top")
    }

    @Test
    fun `a point on a wall is placed at the middle of the opening in front of it, mixed the short way round in v`() {
        val out = FloatArray(3)
        recess.placeInOpening(recess.wallAt(0.65f, 0.6f, upper = true).position, out)
        assertEquals(0.65f, out[PLACE_U], TOLERANCE, "u runs along the line as the stations' places do")
        val v = out[PLACE_V]
        assertTrue(v > 0.9f || v < 0.1f, "v stays near the seam the stations straddle, $v")
    }

    private companion object {
        const val STATIONS = 21
        const val HALF = 0.2f
        const val DEEP = 0.3f
        const val REACH = 0.6f
        const val TOLERANCE = 1e-3f
        const val STEP = 0.01f
    }
}
