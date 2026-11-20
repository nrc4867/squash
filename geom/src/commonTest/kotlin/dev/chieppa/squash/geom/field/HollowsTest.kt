package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A hollow cut into a solid and lined again: a recess with a rolled rim, and its lining placing points on the hollow. */
class HollowsTest {
    /** A ball of [radius] about [centre] that places a point by its height and its angle round the y axis. */
    private class Ball(val centre: Vec3, val radius: Float) : PlacingSolid {
        override fun distance(p: Vec3): Float = (p - centre).length - radius

        override fun place(p: Vec3, out: FloatArray) {
            out[PLACE_FIELD] = distance(p)
            out[PLACE_U] = (centre.y + radius - p.y) / (2f * radius)
            out[PLACE_V] = atan2(p.z - centre.z, p.x - centre.x)
        }

        override val low: Vec3 get() = centre - Vec3(radius, radius, radius)
        override val high: Vec3 get() = centre + Vec3(radius, radius, radius)
    }

    private val host = Ball(Vec3(0f, -1f, 0f), 1f)
    private val hollow = Ball(Vec3(0f, -0.1f, 0f), 0.2f)
    private val wall = 0.1f
    private val rim = 0.03f
    private val carved = CarvedSolid(host, listOf(Hollow(hollow, wall)), Vec3.ZERO, false)
    private val lining = HollowLining(hollow, listOf(host), wall, rim, Vec3.ZERO, false)

    private fun joined(p: Vec3) = minOf(carved.distance(p), lining.distance(p))

    @Test
    fun theHollowIsEmptyAndItsWallIsSurface() {
        assertTrue(joined(Vec3(0f, -0.15f, 0f)) > 0f)
        assertEquals(0f, joined(Vec3(0f, -0.3f, 0f)), 1e-4f)
        assertTrue(joined(Vec3(0f, -0.4f, 0f)) < 0f)
    }

    @Test
    fun theSolidAwayFromTheHollowIsAsItWas() {
        val side = Vec3(sqrt(0.5f), -1f + sqrt(0.5f), 0f)
        assertEquals(host.distance(side), joined(side), 1e-5f)
        assertEquals(host.distance(Vec3(0f, -1.5f, 0f)), joined(Vec3(0f, -1.5f, 0f)), 1e-5f)
    }

    @Test
    fun theRimStandsAboveTheSurfaceJustOutsideTheMouth() {
        val mouth = sqrt(0.2f * 0.2f - 0.1f * 0.1f)
        val beside = Vec3(mouth + 0.02f, 0f, 0f)
        val surface = Vec3(beside.x, -1f + sqrt(1f - beside.x * beside.x), 0f)
        assertTrue(joined(surface + Vec3(0f, 0.01f, 0f)) < 0f, "the rim rises over the surface")
        val far = Vec3(0.5f, -1f + sqrt(0.75f), 0f)
        assertEquals(0f, joined(far), 1e-5f)
    }

    @Test
    fun theLiningPlacesPointsOnTheHollow() {
        val out = FloatArray(PLACE_ARRAY)
        lining.place(Vec3(0f, -0.3f, 0f), out)
        assertEquals(1f, out[PLACE_U], 1e-5f)
        assertEquals(lining.distance(Vec3(0f, -0.3f, 0f)), out[PLACE_FIELD], 1e-6f)
    }

    @Test
    fun aFloorAcrossTheAxisCutsWhatLiesPastItAlong() {
        val field = BlendedField(listOf(Blended(Ellipsoid(Vec3.ZERO, Vec3(1f, 1f, 1f)))), Floor(0.5f, 0f, across = true))
        assertEquals(0f, field.value(Vec3(0.5f, 0f, 0f)), 1e-5f)
        assertTrue(field.value(Vec3(0.7f, 0f, 0f)) > 0f)
        assertEquals(-0.5f, field.value(Vec3(0f, 0f, 0f)), 0.05f)
    }
}
