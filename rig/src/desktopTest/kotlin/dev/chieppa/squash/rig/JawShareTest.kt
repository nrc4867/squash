package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How far across the mouth its stations run either way, how tall its slit is at the middle and how deep its pocket goes. */
private const val HALF_WIDTH = 0.5f
private const val SLIT = 0.02f
private const val POCKET = 0.2f
private const val STATIONS = 11

/** A jaw's share of the head: the jaw takes what lies below the mouth line and the head keeps the rest, sharp where the slit parts the lips. */
class JawShareTest {
    /**
     * A mouth across the front of a head at x 1, its line along z, the slit tallest at the middle and shut at its corners, its lips
     * parting on the surface where [lipsPart].
     */
    private fun mouth(hinge: Vec3, lipsPart: Boolean = true): JawShare {
        val stations = (0 until STATIONS).map { i ->
            val across = -HALF_WIDTH + 2f * HALF_WIDTH * i / (STATIONS - 1)
            val half = SLIT * (1f - (across / HALF_WIDTH) * (across / HALF_WIDTH))
            SlitStation(Vec3(1f, 0f, across), Vec3(0f, -1f, 0f), Vec3(1f, 0f, 0f), half, POCKET * half / SLIT)
        }
        return JawShare(stations, PIVOT, hinge, 2f * HALF_WIDTH, lipsPart)
    }

    @Test
    fun theChinGoesWithTheJawAndTheNoseStaysWithTheHead() {
        val jaw = mouth(OPENS)
        assertTrue(jaw.opens)
        assertEquals(1f, jaw.at(Vec3(1f, -0.2f, 0f)), 1e-4f, "the chin")
        assertEquals(0f, jaw.at(Vec3(1f, 0.2f, 0f)), 1e-4f, "the nose")
        assertEquals(0f, jaw.at(Vec3(-1f, 0f, 0f)), 1e-4f, "the back of the head")
    }

    @Test
    fun theLipsPartAtTheSlitAndThePocketsBackStretches() {
        val jaw = mouth(OPENS)
        assertEquals(1f, jaw.at(Vec3(1f, -SLIT, 0f)), 0.05f, "the lower lip at the slit")
        assertEquals(0f, jaw.at(Vec3(1f, SLIT, 0f)), 0.05f, "the upper lip at the slit")
        assertEquals(0.5f, jaw.at(Vec3(1f - POCKET, 0f, 0f)), 0.05f, "the pocket's back, halfway between the jaws")
    }

    @Test
    fun theLowerLipTakesLessOfTheJawTowardItsCorners() {
        val jaw = mouth(OPENS)
        val middle = jaw.at(Vec3(1f, -0.03f, 0f))
        val corner = jaw.at(Vec3(1f, -0.03f, HALF_WIDTH * 0.95f))
        assertTrue(corner < middle, "the corner's lip, $corner, drops less than the middle's, $middle")
    }

    @Test
    fun paintedLipsStayWithTheHeadAndTheChinGoesWithTheJaw() {
        val jaw = mouth(OPENS, lipsPart = false)
        assertEquals(0f, jaw.at(Vec3(1f, -0.03f, 0f)), 1e-4f, "the lower lip, its opening painted")
        assertEquals(1f, jaw.at(Vec3(1f, -1f, 0f)), 1e-4f, "the chin")
        assertEquals(0f, jaw.at(Vec3(1f, 0.2f, 0f)), 1e-4f, "the nose")
    }

    @Test
    fun aJawThatWouldCloseIntoTheHeadTakesNothing() {
        val jaw = mouth(OPENS * -1f)
        assertFalse(jaw.opens)
        assertEquals(0f, jaw.at(Vec3(1f, -0.2f, 0f)))
    }

    private companion object {
        val PIVOT = Vec3(0f, 0.3f, 0f)

        /** The hinge a positive turn about which drops the front of the head below the pivot. */
        val OPENS = Vec3(0f, 0f, -1f)
    }
}
