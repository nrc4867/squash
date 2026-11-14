package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cloth hanging as a tube round a leg that swings out to one side: the tube bells out over the leg, most at the hem, and only there. */
class HemFlareTest {
    private val tube = FloatArray(ROWS * AROUND * 3).also { out ->
        for (row in 0 until ROWS) for (k in 0 until AROUND) {
            val a = 2 * PI * k / AROUND
            val v = row * AROUND + k
            out[v * 3] = (RADIUS * cos(a)).toFloat()
            out[v * 3 + 1] = -LENGTH * row / (ROWS - 1)
            out[v * 3 + 2] = (RADIUS * sin(a)).toFloat()
        }
    }
    private val hangs = BooleanArray(ROWS * AROUND) { true }

    /** A leg's points along +x from the middle, reaching [reach] from the tube's axis at the hem, less higher up. */
    private fun leg(reach: Float): FloatArray = FloatArray(LEG_POINTS * 3).also { out ->
        for (i in 0 until LEG_POINTS) {
            val t = i / (LEG_POINTS - 1f)
            out[i * 3] = reach * t
            out[i * 3 + 1] = -LENGTH * t
            out[i * 3 + 2] = 0f
        }
    }

    private fun radius(points: FloatArray, v: Int) = sqrt(points[v * 3] * points[v * 3] + points[v * 3 + 2] * points[v * 3 + 2])

    @Test
    fun theClothClearsALegSwungOutUnderItAndBellsMostAtTheHem() {
        val reach = RADIUS * 1.4f
        val out = assertNotNull(hemFlare(tube, hangs, CELL, leg(reach), CLEAR))
        val hem = (ROWS - 1) * AROUND
        assertTrue(radius(out, hem) > reach, "the hem over the leg passes it: ${radius(out, hem)}")
        val far = hem + AROUND / 2
        assertTrue(radius(out, far) < radius(out, hem) - RADIUS * 0.2f, "the far side of the hem moves less than the side over the leg")
        val middle = (ROWS / 2) * AROUND
        assertTrue(radius(out, middle) - RADIUS < radius(out, hem) - RADIUS, "the cloth bells from its top: the hem moves more than higher up")
        for (v in 0 until AROUND) assertEquals(RADIUS, radius(out, v), RADIUS * 0.02f, "the top, where the cloth is held, stays")
        for (v in hangs.indices) assertEquals(tube[v * 3 + 1], out[v * 3 + 1], "vertex $v moves only across the way it hangs")
    }

    @Test
    fun aLegWellInsideTheClothMovesNothing() {
        assertNull(hemFlare(tube, hangs, CELL, leg(RADIUS * 0.3f), CLEAR))
    }

    @Test
    fun clothThatDoesNotHangStaysWhereItIs() {
        val held = BooleanArray(hangs.size) { it >= AROUND * ROWS / 2 }
        val out = assertNotNull(hemFlare(tube, held, CELL, leg(RADIUS * 1.4f), CLEAR))
        for (v in held.indices) if (!held[v]) for (k in 0 until 3) assertEquals(tube[v * 3 + k], out[v * 3 + k])
    }

    private companion object {
        const val RADIUS = 0.3f
        const val LENGTH = 0.8f
        const val ROWS = 17
        const val AROUND = 48
        const val LEG_POINTS = 40
        const val CELL = 0.025f
        const val CLEAR = 0.02f
    }
}
