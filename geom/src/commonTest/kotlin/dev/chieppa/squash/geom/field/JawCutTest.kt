package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A snout split along a level mouth line about a hinge across it, its jaw built open, and the two halves shut onto each other again. */
class JawCutTest {
    /** A long box standing in for a snout: from the hinge forward along x, a half height and depth of 0.2. */
    private class Snout : PlacingSolid {
        override fun distance(p: Vec3): Float = maxOf(-0.2f - p.x, p.x - 1f, kotlin.math.abs(p.y) - 0.2f, kotlin.math.abs(p.z) - 0.2f)

        override fun place(p: Vec3, out: FloatArray) {
            out[PLACE_FIELD] = distance(p)
            out[PLACE_U] = p.x
            out[PLACE_V] = p.y
        }

        override val low: Vec3 get() = Vec3(-0.2f, -0.2f, -0.2f)
        override val high: Vec3 get() = Vec3(1f, 0.2f, 0.2f)
    }

    private val built = (30f * PI / 180f).toFloat()
    private val cut = JawCut(Vec3.ZERO, Vec3(0f, 0f, -1f), Vec3.X, floatArrayOf(0.1f, 1.2f), floatArrayOf(0f, 0f), 0.1f, (-PI / 2).toFloat(), built)
    private val snout = Snout()
    private val upper = JawHalfSolid(snout, cut, upper = true, rounding = 0.02f)
    private val jaw = JawHalfSolid(snout, cut, upper = false, rounding = 0.02f)

    @Test
    fun upIsTheWayTheCutAngleGrowsAndOpeningTurnsTheFrontDown() {
        assertEquals(1f, cut.up.y, 1e-5f)
        assertTrue(cut.open(Vec3(1f, 0f, 0f)).y < 0f)
    }

    @Test
    fun shuttingUndoesOpening() {
        val p = Vec3(0.7f, -0.1f, 0.05f)
        val back = cut.shut(cut.open(p))
        assertEquals(0f, (back - p).length, 1e-5f)
    }

    @Test
    fun theJawRegionLiesBelowTheCutPastTheCorner() {
        assertTrue(cut.region(Vec3(0.6f, -0.1f, 0f)) < 0f)
        assertTrue(cut.region(Vec3(0.6f, 0.1f, 0f)) > 0f)
        assertTrue(cut.region(Vec3(0.05f, -0.05f, 0f)) > 0f)
    }

    @Test
    fun theJawHalfStandsOpenAndKeepsTheRunsPlace() {
        val shutPoint = Vec3(0.8f, -0.2f, 0f)
        val open = cut.open(shutPoint)
        assertEquals(0f, jaw.distance(open), 1e-3f)
        assertTrue(jaw.distance(shutPoint) > 0f)
        val out = FloatArray(3)
        jaw.place(open, out)
        assertEquals(0.8f, out[PLACE_U], 1e-4f)
    }

    @Test
    fun theHalvesShutMakeTheSnoutAgainAwayFromTheCut() {
        for (p in listOf(Vec3(0.5f, 0.2f, 0f), Vec3(0.5f, -0.2f, 0f), Vec3(1f, 0.1f, 0f), Vec3(0.5f, 0.1f, 0.2f))) {
            val joined = minOf(upper.distance(p), jaw.distance(cut.open(p)))
            assertEquals(snout.distance(p), joined, 1e-3f, "at $p")
        }
    }

    @Test
    fun theLiningFillsTheGapSetBackFromTheLips() {
        val lining = MouthLiningSolid(listOf(snout), cut, inset = 0.05f, faceU = 50f)
        val middle = cut.turnedInPhi(Vec3(0.5f, 0f, 0f), -built / 2f)
        assertTrue(lining.distance(middle) < 0f)
        assertTrue(lining.distance(cut.turnedInPhi(Vec3(0.98f, 0f, 0f), -built / 2f)) > 0f)
        val out = FloatArray(3)
        lining.place(middle, out)
        assertEquals(0.5f, out[PLACE_V], 0.05f)
    }
}
