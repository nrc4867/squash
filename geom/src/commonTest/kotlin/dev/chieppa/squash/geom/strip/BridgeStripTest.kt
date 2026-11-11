package dev.chieppa.squash.geom.strip

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val COLUMNS = 16
private const val RINGS = 9

/** A strip bridges two loops tangent to the ways it is given, and a point is laid on a patch where the patch lies nearest it. */
class BridgeStripTest {
    /** A loop of [radius] round the y axis at height [y]. */
    private fun loop(radius: Float, y: Float) = List(COLUMNS) { j ->
        val angle = 2.0 * PI * j / COLUMNS
        Vec3(radius * cos(angle).toFloat(), y, radius * sin(angle).toFloat())
    }

    /** A flange: from a wide loop on the floor, leaving outward-in along the floor, up to a narrow loop rising straight up. */
    private fun flange(): Array<Vec3> {
        val foot = loop(2f, 0f)
        val inward = foot.map { Vec3(-it.x, 0f, -it.z).normalized() }
        val ring = loop(1f, 1f)
        return BridgeStrip.build(StripEnd(foot, inward), StripEnd(ring, List(COLUMNS) { Vec3.Y }), RINGS, 1f)
    }

    @Test
    fun theStripStartsAndEndsOnItsLoops() {
        val strip = flange()
        loop(2f, 0f).forEachIndexed { j, p -> assertEquals(0f, (strip[j] - p).length, 1e-5f) }
        loop(1f, 1f).forEachIndexed { j, p -> assertEquals(0f, (strip[(RINGS - 1) * COLUMNS + j] - p).length, 1e-5f) }
    }

    @Test
    fun theStripLeavesAndArrivesAlongItsWays() {
        val strip = flange()
        val leaving = (strip[COLUMNS] - strip[0]).normalized()
        assertTrue(leaving.x < -0.9f && kotlin.math.abs(leaving.y) < 0.3f, "$leaving")
        val arriving = (strip[(RINGS - 1) * COLUMNS] - strip[(RINGS - 2) * COLUMNS]).normalized()
        assertTrue(arriving.y > 0.9f, "$arriving")
    }

    @Test
    fun aRoundStripStaysRoundAfterFairing() {
        val strip = flange()
        for (k in 0 until RINGS) {
            val radii = (0 until COLUMNS).map { j -> strip[k * COLUMNS + j].let { kotlin.math.sqrt(it.x * it.x + it.z * it.z) } }
            assertEquals(radii.min(), radii.max(), 1e-3f, "ring $k")
        }
    }

    @Test
    fun fairingCalmsAColumnOutOfStep() {
        val ring = loop(1f, 1f).mapIndexed { j, p -> if (j == 5) p + Vec3(0f, 0.4f, 0f) else p }
        val ways = List(COLUMNS) { Vec3.Y }
        val faired = BridgeStrip.build(StripEnd(loop(1f, 0f), ways), StripEnd(ring, ways), RINGS, 1f)
        val middle = RINGS / 2
        val bump = faired[middle * COLUMNS + 5].y - (faired[middle * COLUMNS + 4].y + faired[middle * COLUMNS + 6].y) / 2f
        // Each column alone is a cubic whose middle lies halfway up, so the step there is half the ring's.
        assertTrue(bump < 0.2f * 0.9f, "the faired step is $bump")
    }

    @Test
    fun aPointIsLaidOnThePatchWhereItLiesNearest() {
        val points = FloatArray(RINGS * COLUMNS * Patch.COMPONENTS)
        for (k in 0 until RINGS) loop(1f, k.toFloat()).forEachIndexed { j, p ->
            val at = (k * COLUMNS + j) * Patch.COMPONENTS
            points[at] = p.x
            points[at + 1] = p.y
            points[at + 2] = p.z
        }
        val tube = Patch("tube", RINGS, COLUMNS, closedV = true, slices = null, points = points, us = FloatArray(RINGS) { it.toFloat() })
        val near = tube.nearestUv(Vec3(1.5f, 3.3f, 0.2f))
        assertEquals(3.3f, near.u, 0.02f)
        val on = tube.positionAt(near.u, near.v)
        assertEquals(0f, on.z / on.x - 0.2f / 1.5f, 0.05f)
    }
}
