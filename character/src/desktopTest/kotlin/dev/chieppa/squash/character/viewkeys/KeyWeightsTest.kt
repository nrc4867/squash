package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.document.KeyView
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How each key view's weight follows a camera round a head: whole at a key, eased between neighbours, mirrored and faded overhead. */
class KeyWeightsTest {
    private fun around(degrees: Float): FloatArray {
        val radians = degrees * PI.toFloat() / 180f
        return keyWeights(cos(radians), 0f, sin(radians))
    }

    private fun index(view: KeyView, side: Side? = Side.LEFT) = directionIndex(view, side)

    @Test
    fun aCameraAtAKeyViewTakesThatKeyWhole() {
        assertEquals(1f, around(0f)[index(KeyView.FRONT, null)], 1e-5f)
        assertEquals(1f, around(45f)[index(KeyView.THREE_QUARTER)], 1e-5f)
        assertEquals(1f, around(90f)[index(KeyView.SIDE)], 1e-5f)
        assertEquals(1f, around(135f)[index(KeyView.THREE_QUARTER_BACK)], 1e-5f)
        assertEquals(1f, around(180f)[index(KeyView.BACK, null)], 1e-5f)
        assertEquals(1f, around(-90f)[index(KeyView.SIDE, Side.RIGHT)], 1e-5f)
    }

    @Test
    fun betweenKeysOnlyTheTwoNeighboursHoldAndTheySumToOne() {
        val half = around(67.5f)
        assertEquals(0.5f, half[index(KeyView.THREE_QUARTER)], 1e-4f)
        assertEquals(0.5f, half[index(KeyView.SIDE)], 1e-4f)
        assertEquals(1f, half.sum(), 1e-4f)
        assertEquals(2, half.count { it > 0f })
    }

    @Test
    fun aSweepNeverJumps() {
        var last = around(-180f)
        var degrees = -180f
        while (degrees <= 180f) {
            val next = around(degrees)
            for (d in next.indices) assertTrue(abs(next[d] - last[d]) < 0.03f, "a jump at $degrees degrees in direction $d")
            last = next
            degrees += 0.5f
        }
    }

    @Test
    fun theRightMirrorsTheLeft() {
        for (degrees in listOf(10f, 45f, 80f, 120f, 170f)) {
            val left = around(degrees)
            val right = around(-degrees)
            for (view in KeyView.entries) assertEquals(left[index(view)], right[index(view, Side.RIGHT)], 1e-5f, "$view at $degrees")
        }
    }

    @Test
    fun keysFadeAsTheCameraRisesOverTheHead() {
        assertEquals(1f, keyWeights(1f, 0.3f, 0f).sum(), 1e-4f)
        assertEquals(0f, keyWeights(0.05f, 1f, 0f).sum(), 1e-4f)
        val between = keyWeights(1f, 1.5f, 0f).sum()
        assertTrue(between > 0f && between < 1f, "half way up: $between")
    }

    @Test
    fun aHeadPicksItsKeysByWhereItFacesNotTheBody() {
        val frame = HeadFrame.of(Vec3.X, 1f)
        val turnedLeft = Mat3(Vec3(0f, 0f, -1f), Vec3.Y, Vec3.X)
        val camera = Vec3.X
        assertEquals(1f, headWeights(frame, Mat3.IDENTITY, camera)[index(KeyView.FRONT, null)], 1e-5f)
        val weights = headWeights(frame, turnedLeft, camera)
        assertEquals(1f, weights[index(KeyView.SIDE, Side.RIGHT)], 1e-5f, "a head turned to its left sees a camera ahead of the body on its right")
    }
}
