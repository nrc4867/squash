package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spirals as places for a curved axis, and how far an axis turns. See HLD: surface, Curved axes. */
class AxisCurlTest {
    @Test
    fun aSpiralLeavesAlongItsRestAndCoilsItsTurns() {
        val places = spiralPlaces(turns = 2f, pitch = 0f, shrink = 1f)
        val axis = arcLengthAxis(listOf(Vec3.ZERO) + places, 1f)
        assertTrue(axis.tangentAt(0f).x > 0.9f, "it leaves along x: ${axis.tangentAt(0f)}")
        assertEquals(720f, turningDegrees(axis), 720f * 0.08f, "two turns turn about 720 degrees")
        assertTrue(places.all { it.z == 0f }, "no pitch, a flat coil")
    }

    @Test
    fun negativeTurnsCurlDownAndPitchMovesTheCoilSideways() {
        val down = spiralPlaces(turns = -1f, pitch = 0.5f, shrink = 1f)
        assertTrue(down[down.size / 2].y < 0f, "negative turns curl toward -y")
        assertEquals(1f, down.last().z, 1e-4f, "half a diameter a turn moves one radius in one turn")
    }

    @Test
    fun shrinkTightensTheCoilTowardItsEnd() {
        val tight = spiralPlaces(turns = 1f, pitch = 0f, shrink = 0.25f)
        val centre = Vec3(0f, 1f, 0f)
        assertEquals(0.25f, (tight.last() - centre).length, 1e-4f, "the last place lies a quarter radius from the coil's centre")
    }

    @Test
    fun aStraightAxisDoesNotTurn() {
        assertEquals(0f, turningDegrees(AxisSpline(listOf(Vec3.ZERO, Vec3(1f, 0f, 0f)))), 1e-3f)
    }
}
